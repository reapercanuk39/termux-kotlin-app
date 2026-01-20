package com.termux.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import com.termux.R
import com.termux.app.agents.daemon.AgentDaemon
import com.termux.app.agents.daemon.DaemonStatistics
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Background service that runs the autonomous agent daemon.
 * 
 * This service starts automatically when Termux launches and keeps
 * the Kotlin-native agent framework running in the background with full
 * network access. No Python dependency required for core functionality.
 */
@AndroidEntryPoint
class AgentService : Service() {

    companion object {
        private const val LOG_TAG = "AgentService"
        private const val NOTIFICATION_ID = 1338
        private const val CHANNEL_ID = "termux_agent_channel"
        
        // Intent actions for IPC
        const val ACTION_RUN_TASK = "com.termux.agent.RUN_TASK"
        const val ACTION_GET_STATUS = "com.termux.agent.GET_STATUS"
        const val ACTION_STATUS_RESPONSE = "com.termux.agent.STATUS_RESPONSE"
        const val ACTION_TASK_RESULT = "com.termux.agent.TASK_RESULT"
        
        // Intent extras
        const val EXTRA_AGENT_NAME = "agent_name"
        const val EXTRA_SKILL_NAME = "skill_name"
        const val EXTRA_FUNCTION_NAME = "function_name"
        const val EXTRA_PARAMS_JSON = "params_json"
        const val EXTRA_RESULT_JSON = "result_json"
        const val EXTRA_STATUS_JSON = "status_json"
        
        private val isRunning = AtomicBoolean(false)
        
        @JvmStatic
        fun isAgentRunning(): Boolean = isRunning.get()
        
        @JvmStatic
        fun startAgentService(context: Context) {
            if (isRunning.get()) {
                Logger.logDebug(LOG_TAG, "Agent service already running")
                return
            }
            
            val intent = Intent(context, AgentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        @JvmStatic
        fun stopAgentService(context: Context) {
            val intent = Intent(context, AgentService::class.java)
            context.stopService(intent)
        }
    }
    
    @Inject
    lateinit var agentDaemon: AgentDaemon
    
    @Inject
    lateinit var cliBridge: com.termux.app.agents.cli.CliBridge
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var commandReceiver: BroadcastReceiver? = null
    
    override fun onCreate() {
        super.onCreate()
        Logger.logInfo(LOG_TAG, "AgentService created")
        createNotificationChannel()
        registerCommandReceiver()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.logInfo(LOG_TAG, "AgentService starting with Kotlin-native daemon")
        
        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, buildNotification())
        
        // Start the Kotlin-native agent daemon
        startKotlinDaemon()
        
        isRunning.set(true)
        
        // Restart if killed
        return START_STICKY
    }
    
    override fun onDestroy() {
        Logger.logInfo(LOG_TAG, "AgentService stopping")
        isRunning.set(false)
        stopKotlinDaemon()
        unregisterCommandReceiver()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Register broadcast receiver for IPC commands.
     */
    private fun registerCommandReceiver() {
        commandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_RUN_TASK -> handleRunTask(intent)
                    ACTION_GET_STATUS -> handleGetStatus()
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(ACTION_RUN_TASK)
            addAction(ACTION_GET_STATUS)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
    }
    
    private fun unregisterCommandReceiver() {
        commandReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Error unregistering receiver: ${e.message}")
            }
        }
        commandReceiver = null
    }
    
    /**
     * Handle a task execution request.
     */
    private fun handleRunTask(intent: Intent) {
        val agentName = intent.getStringExtra(EXTRA_AGENT_NAME) ?: return
        val skillName = intent.getStringExtra(EXTRA_SKILL_NAME) ?: return
        val functionName = intent.getStringExtra(EXTRA_FUNCTION_NAME) ?: return
        val paramsJson = intent.getStringExtra(EXTRA_PARAMS_JSON) ?: "{}"
        
        serviceScope.launch {
            try {
                val params = parseParams(paramsJson)
                val result = agentDaemon.runTask(agentName, skillName, functionName, params)
                
                // Broadcast result
                val resultIntent = Intent(ACTION_TASK_RESULT).apply {
                    putExtra(EXTRA_RESULT_JSON, serializeResult(result))
                }
                sendBroadcast(resultIntent)
                
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Task execution failed: ${e.message}")
            }
        }
    }
    
    /**
     * Handle status request.
     */
    private fun handleGetStatus() {
        val stats = agentDaemon.getStatistics()
        val statusJson = serializeStatistics(stats)
        
        val intent = Intent(ACTION_STATUS_RESPONSE).apply {
            putExtra(EXTRA_STATUS_JSON, statusJson)
        }
        sendBroadcast(intent)
    }
    
    private fun parseParams(json: String): Map<String, Any?> {
        // Simple JSON parsing - in production use kotlinx.serialization
        return try {
            val trimmed = json.trim().removePrefix("{").removeSuffix("}")
            if (trimmed.isBlank()) {
                emptyMap()
            } else {
                trimmed.split(",").associate { pair ->
                    val (key, value) = pair.split(":").map { it.trim().trim('"') }
                    key to value
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun serializeResult(result: com.termux.app.agents.models.TaskResult): String {
        return when (result) {
            is com.termux.app.agents.models.TaskResult.Success -> 
                """{"success":true,"data":${result.data}}"""
            is com.termux.app.agents.models.TaskResult.Failure -> 
                """{"success":false,"error":"${result.error.message}"}"""
            is com.termux.app.agents.models.TaskResult.Timeout -> 
                """{"success":false,"error":"timeout","duration":${result.durationMs}}"""
            is com.termux.app.agents.models.TaskResult.Cancelled -> 
                """{"success":false,"error":"cancelled","reason":"${result.reason}"}"""
        }
    }
    
    private fun serializeStatistics(stats: DaemonStatistics): String {
        return """{
            "state":"${stats.state}",
            "uptime":"${stats.uptimeFormatted}",
            "tasksExecuted":${stats.tasksExecuted},
            "tasksSucceeded":${stats.tasksSucceeded},
            "tasksFailed":${stats.tasksFailed},
            "activeTasks":${stats.activeTasks},
            "registeredAgents":${stats.registeredAgents},
            "registeredSkills":${stats.registeredSkills}
        }"""
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Termux Agent Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kotlin-native agent framework running in background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, TermuxActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stats = if (::agentDaemon.isInitialized) {
            agentDaemon.getStatistics()
        } else null
        
        val statusText = when {
            stats == null -> "Starting..."
            stats.state == AgentDaemon.DaemonState.RUNNING -> 
                "Running • ${stats.registeredAgents} agents • ${stats.registeredSkills} skills"
            stats.state == AgentDaemon.DaemonState.PAUSED -> "Paused"
            stats.state == AgentDaemon.DaemonState.ERROR -> "Error"
            else -> "Initializing..."
        }
        
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        
        return builder
            .setContentTitle("Termux Agent Daemon")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * Start the Kotlin-native agent daemon.
     * No Python dependency required.
     */
    private fun startKotlinDaemon() {
        serviceScope.launch {
            try {
                Logger.logInfo(LOG_TAG, "Starting Kotlin-native agent daemon")
                
                val success = agentDaemon.start()
                
                if (success) {
                    Logger.logInfo(LOG_TAG, "Kotlin agent daemon started successfully")
                    
                    // Start CLI bridge for shell command IPC
                    cliBridge.start()
                    Logger.logInfo(LOG_TAG, "CLI bridge started")
                    
                    // Schedule WorkManager periodic tasks
                    com.termux.app.agents.daemon.AgentWorker.schedulePeriodicWork(this@AgentService)
                    Logger.logInfo(LOG_TAG, "WorkManager tasks scheduled")
                    
                    // Update notification with running status
                    handler.post {
                        val notification = buildNotification()
                        val manager = getSystemService(NotificationManager::class.java)
                        manager?.notify(NOTIFICATION_ID, notification)
                    }
                    
                    // Start periodic notification updates
                    startNotificationUpdates()
                    
                } else {
                    Logger.logError(LOG_TAG, "Failed to start Kotlin agent daemon")
                    scheduleRetry()
                }
                
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Exception starting daemon: ${e.message}")
                scheduleRetry()
            }
        }
    }
    
    /**
     * Stop the Kotlin-native daemon.
     */
    private fun stopKotlinDaemon() {
        try {
            // Stop CLI bridge
            cliBridge.stop()
            
            // Stop daemon
            agentDaemon.stop()
            Logger.logInfo(LOG_TAG, "Kotlin agent daemon stopped")
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Error stopping daemon: ${e.message}")
        }
    }
    
    /**
     * Update notification periodically with daemon statistics.
     */
    private fun startNotificationUpdates() {
        serviceScope.launch {
            while (isActive && isRunning.get()) {
                delay(30_000) // Update every 30 seconds
                
                try {
                    handler.post {
                        val notification = buildNotification()
                        val manager = getSystemService(NotificationManager::class.java)
                        manager?.notify(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    // Ignore notification update errors
                }
            }
        }
    }
    
    private fun scheduleRetry() {
        if (isRunning.get()) {
            handler.postDelayed({
                if (isRunning.get()) {
                    Logger.logInfo(LOG_TAG, "Retrying Kotlin daemon start")
                    startKotlinDaemon()
                }
            }, 30000) // Retry after 30 seconds
        }
    }
}
