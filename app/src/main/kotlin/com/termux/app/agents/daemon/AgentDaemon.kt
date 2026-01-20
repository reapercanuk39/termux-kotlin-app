package com.termux.app.agents.daemon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.termux.app.agents.models.Agent
import com.termux.app.agents.models.TaskResult
import com.termux.app.agents.runtime.SkillExecutor
import com.termux.app.agents.runtime.TaskRequest
import com.termux.app.agents.swarm.SignalType
import com.termux.app.agents.swarm.SwarmCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core agent daemon that manages all agent operations.
 * Singleton that survives activity lifecycle, initialized by AgentService.
 */
@Singleton
class AgentDaemon @Inject constructor(
    private val context: Context,
    private val registry: AgentRegistry,
    private val skillExecutor: SkillExecutor,
    private val swarmCoordinator: SwarmCoordinator
) {
    companion object {
        const val CHANNEL_ID = "agent_daemon"
        const val CHANNEL_NAME = "Agent Daemon"
        const val NOTIFICATION_ID = 1001
        
        private const val DATA_DIR = "/data/data/com.termux/files/usr/share/termux-agents"
        private const val LOG_DIR = "$DATA_DIR/logs"
    }
    
    // Daemon state
    private val _state = MutableStateFlow(DaemonState.STOPPED)
    val state: StateFlow<DaemonState> = _state.asStateFlow()
    
    // Active tasks
    private val activeTasks = ConcurrentHashMap<String, Job>()
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var healthCheckJob: Job? = null
    
    // Statistics
    private var tasksExecuted = 0
    private var tasksSucceeded = 0
    private var tasksFailed = 0
    private var startTime: Long = 0
    
    /**
     * Daemon states.
     */
    enum class DaemonState {
        STOPPED,
        STARTING,
        RUNNING,
        PAUSED,
        ERROR
    }
    
    /**
     * Start the agent daemon.
     */
    suspend fun start(): Boolean {
        if (_state.value == DaemonState.RUNNING) {
            return true
        }
        
        _state.value = DaemonState.STARTING
        startTime = System.currentTimeMillis()
        
        try {
            // Ensure directories exist
            ensureDirectories()
            
            // Discover agents
            val agents = registry.discoverAgents()
            log("Discovered ${agents.size} agents")
            
            // Discover Python skills
            val pythonSkills = skillExecutor.discoverPythonSkills()
            log("Discovered ${pythonSkills.size} Python skill bridges")
            
            // Validate all agents
            val availableSkills = skillExecutor.getSkillNames()
            var validCount = 0
            for (agent in agents) {
                val result = registry.validateAgent(agent.name, availableSkills)
                if (result.valid) {
                    validCount++
                    log("Agent validated: ${agent.name}")
                } else {
                    log("Agent validation failed: ${agent.name} - ${result.errors.joinToString(", ")}")
                }
            }
            log("$validCount/${agents.size} agents validated")
            
            // Start swarm coordinator
            swarmCoordinator.startDecayLoop()
            log("Swarm coordinator started")
            
            // Start health check loop
            startHealthCheckLoop()
            
            _state.value = DaemonState.RUNNING
            
            // Emit startup signal
            swarmCoordinator.emit(
                signalType = SignalType.SUCCESS,
                sourceAgent = "daemon",
                target = "startup",
                data = mapOf("message" to "Agent daemon started with $validCount agents")
            )
            
            log("Agent daemon started successfully")
            return true
            
        } catch (e: Exception) {
            log("Failed to start daemon: ${e.message}")
            _state.value = DaemonState.ERROR
            return false
        }
    }
    
    /**
     * Stop the daemon.
     */
    fun stop() {
        log("Stopping agent daemon")
        
        // Cancel all active tasks
        activeTasks.forEach { (_, job) ->
            job.cancel()
        }
        activeTasks.clear()
        
        // Stop health check
        healthCheckJob?.cancel()
        healthCheckJob = null
        
        // Stop swarm coordinator
        swarmCoordinator.stopDecayLoop()
        
        _state.value = DaemonState.STOPPED
        
        log("Agent daemon stopped")
    }
    
    /**
     * Pause the daemon.
     */
    fun pause() {
        if (_state.value == DaemonState.RUNNING) {
            _state.value = DaemonState.PAUSED
            log("Agent daemon paused")
        }
    }
    
    /**
     * Resume the daemon.
     */
    fun resume() {
        if (_state.value == DaemonState.PAUSED) {
            _state.value = DaemonState.RUNNING
            log("Agent daemon resumed")
        }
    }
    
    /**
     * Execute a task for an agent.
     */
    suspend fun runTask(
        agentName: String,
        skillName: String,
        function: String,
        params: Map<String, Any?> = emptyMap(),
        timeout: Long = 60_000L
    ): TaskResult {
        val taskName = "$skillName.$function"
        
        if (_state.value != DaemonState.RUNNING) {
            return TaskResult.Failure(
                agentName = agentName,
                taskName = taskName,
                durationMs = 0,
                error = com.termux.app.agents.models.AgentError(
                    type = com.termux.app.agents.models.AgentErrorType.EXECUTION_ERROR,
                    message = "Daemon not running",
                    agent = agentName,
                    details = mapOf("state" to _state.value.name)
                )
            )
        }
        
        val agent = registry.getAgent(agentName)
            ?: return TaskResult.Failure(
                agentName = agentName,
                taskName = taskName,
                durationMs = 0,
                error = com.termux.app.agents.models.AgentError(
                    type = com.termux.app.agents.models.AgentErrorType.UNKNOWN_ERROR,
                    message = "Agent not found: $agentName",
                    agent = agentName,
                    details = mapOf("available_agents" to registry.getAllAgents().map { it.name })
                )
            )
        
        // Set agent as active
        registry.setAgentState(agentName, AgentRegistry.AgentState.ACTIVE)
        
        val result = try {
            skillExecutor.execute(agent, skillName, function, params, timeout)
        } finally {
            // Reset agent state
            registry.setAgentState(agentName, AgentRegistry.AgentState.VALIDATED)
        }
        
        // Update statistics
        tasksExecuted++
        when (result) {
            is TaskResult.Success -> tasksSucceeded++
            else -> tasksFailed++
        }
        
        return result
    }
    
    /**
     * Execute a batch of tasks.
     */
    suspend fun runBatch(
        agentName: String,
        tasks: List<TaskRequest>,
        parallelism: Int = 1
    ): List<TaskResult> {
        if (_state.value != DaemonState.RUNNING) {
            return tasks.map {
                TaskResult.Failure(
                    agentName = agentName,
                    taskName = "${it.skill}.${it.function}",
                    durationMs = 0,
                    error = com.termux.app.agents.models.AgentError(
                        type = com.termux.app.agents.models.AgentErrorType.EXECUTION_ERROR,
                        message = "Daemon not running",
                        agent = agentName,
                        details = mapOf("state" to _state.value.name)
                    )
                )
            }
        }
        
        val agent = registry.getAgent(agentName)
            ?: return tasks.map {
                TaskResult.Failure(
                    agentName = agentName,
                    taskName = "${it.skill}.${it.function}",
                    durationMs = 0,
                    error = com.termux.app.agents.models.AgentError(
                        type = com.termux.app.agents.models.AgentErrorType.UNKNOWN_ERROR,
                        message = "Agent not found: $agentName",
                        agent = agentName
                    )
                )
            }
        
        registry.setAgentState(agentName, AgentRegistry.AgentState.ACTIVE)
        
        return try {
            skillExecutor.executeBatch(agent, tasks, parallelism)
        } finally {
            registry.setAgentState(agentName, AgentRegistry.AgentState.VALIDATED)
        }
    }
    
    /**
     * Run a task asynchronously and return immediately.
     */
    fun runTaskAsync(
        agentName: String,
        skillName: String,
        function: String,
        params: Map<String, Any?> = emptyMap(),
        timeout: Long = 60_000L,
        callback: ((TaskResult) -> Unit)? = null
    ): String {
        val taskId = "task_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
        
        val job = scope.launch {
            val result = runTask(agentName, skillName, function, params, timeout)
            callback?.invoke(result)
        }
        
        activeTasks[taskId] = job
        job.invokeOnCompletion {
            activeTasks.remove(taskId)
        }
        
        return taskId
    }
    
    /**
     * Cancel an async task.
     */
    fun cancelTask(taskId: String): Boolean {
        val job = activeTasks[taskId] ?: return false
        job.cancel()
        activeTasks.remove(taskId)
        return true
    }
    
    /**
     * Get daemon statistics.
     */
    fun getStatistics(): DaemonStatistics {
        return DaemonStatistics(
            state = _state.value,
            uptime = if (startTime > 0) System.currentTimeMillis() - startTime else 0,
            tasksExecuted = tasksExecuted,
            tasksSucceeded = tasksSucceeded,
            tasksFailed = tasksFailed,
            activeTasks = activeTasks.size,
            registeredAgents = scope.run {
                runBlocking { registry.getAllAgents().size }
            },
            validatedAgents = scope.run {
                runBlocking { registry.getValidatedAgents().size }
            },
            registeredSkills = skillExecutor.getSkillNames().size
        )
    }
    
    /**
     * Get all registered agents.
     */
    suspend fun getAgents(): List<Agent> = registry.getAllAgents()
    
    /**
     * Get a specific agent.
     */
    suspend fun getAgent(name: String): Agent? = registry.getAgent(name)
    
    /**
     * Get available skills.
     */
    fun getSkills(): Set<String> = skillExecutor.getSkillNames()
    
    /**
     * Run self-tests on all skills.
     */
    suspend fun runSelfTests(agentName: String = "daemon"): Map<String, Any> {
        val agent = registry.getAgent(agentName) ?: createTestAgent()
        val results = skillExecutor.selfTestAll(agent)
        
        return mapOf(
            "passed" to results.count { it.value.success },
            "failed" to results.count { !it.value.success },
            "results" to results.mapValues { 
                mapOf(
                    "success" to it.value.success,
                    "error" to it.value.error,
                    "data" to it.value.data
                )
            }
        )
    }
    
    /**
     * Create a test agent with all capabilities.
     */
    private fun createTestAgent(): Agent = Agent(
        name = "test",
        description = "Test agent for self-tests",
        version = "1.0.0",
        capabilities = com.termux.app.agents.models.Capability.ALL,
        skills = emptyList()
    )
    
    /**
     * Build notification for foreground service.
     */
    fun buildNotification(context: Context): Notification {
        createNotificationChannel(context)
        
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stats = getStatistics()
        val statusText = when (stats.state) {
            DaemonState.RUNNING -> "Running • ${stats.registeredAgents} agents"
            DaemonState.PAUSED -> "Paused"
            DaemonState.ERROR -> "Error"
            else -> "Stopped"
        }
        
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Termux Agent Daemon")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Agent daemon status"
                setShowBadge(false)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Ensure required directories exist.
     */
    private fun ensureDirectories() {
        listOf(DATA_DIR, LOG_DIR).forEach { path ->
            val dir = File(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }
    
    /**
     * Start periodic health check.
     */
    private fun startHealthCheckLoop() {
        healthCheckJob = scope.launch {
            while (isActive) {
                delay(60_000) // Check every minute
                performHealthCheck()
            }
        }
    }
    
    /**
     * Perform health check on all agents and skills.
     */
    private suspend fun performHealthCheck() {
        if (_state.value != DaemonState.RUNNING) return
        
        try {
            val agents = registry.getValidatedAgents()
            var healthyCount = 0
            
            for (agent in agents) {
                val state = registry.getAgentState(agent.name)
                if (state != AgentRegistry.AgentState.ERROR) {
                    healthyCount++
                }
            }
            
            // Emit health signal
            if (healthyCount == agents.size) {
                swarmCoordinator.emit(
                    signalType = SignalType.SUCCESS,
                    sourceAgent = "daemon",
                    target = "health_check",
                    data = mapOf("message" to "Health check: all $healthyCount agents healthy")
                )
            } else {
                swarmCoordinator.emit(
                    signalType = SignalType.DANGER,
                    sourceAgent = "daemon",
                    target = "health_check",
                    data = mapOf("message" to "Health check: $healthyCount/${agents.size} agents healthy")
                )
            }
            
        } catch (e: Exception) {
            log("Health check failed: ${e.message}")
        }
    }
    
    /**
     * Log a message.
     */
    private fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        println("[$timestamp] AgentDaemon: $message")
        
        // Also write to log file
        try {
            val logFile = File(LOG_DIR, "daemon.log")
            logFile.appendText("[$timestamp] $message\n")
            
            // Rotate log if too large (> 1MB)
            if (logFile.length() > 1_000_000) {
                val rotated = File(LOG_DIR, "daemon.log.1")
                logFile.renameTo(rotated)
            }
        } catch (e: Exception) {
            // Ignore log write failures
        }
    }
}

/**
 * Daemon statistics.
 */
data class DaemonStatistics(
    val state: AgentDaemon.DaemonState,
    val uptime: Long,
    val tasksExecuted: Int,
    val tasksSucceeded: Int,
    val tasksFailed: Int,
    val activeTasks: Int,
    val registeredAgents: Int,
    val validatedAgents: Int,
    val registeredSkills: Int
) {
    val successRate: Double
        get() = if (tasksExecuted > 0) {
            tasksSucceeded.toDouble() / tasksExecuted
        } else 0.0
    
    val uptimeFormatted: String
        get() {
            val seconds = uptime / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            
            return when {
                days > 0 -> "${days}d ${hours % 24}h"
                hours > 0 -> "${hours}h ${minutes % 60}m"
                minutes > 0 -> "${minutes}m ${seconds % 60}s"
                else -> "${seconds}s"
            }
        }
}
