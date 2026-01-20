package com.termux.app.agents.runtime

import com.termux.app.agents.models.Agent
import com.termux.app.agents.models.Capability
import com.termux.app.agents.models.TaskResult
import com.termux.app.agents.models.TaskStep
import com.termux.app.agents.models.StepStatus
import com.termux.app.agents.models.AgentError
import com.termux.app.agents.models.AgentErrorType
import com.termux.app.agents.skills.*
import com.termux.app.agents.swarm.SignalType
import com.termux.app.agents.swarm.SwarmCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Skill executor that dispatches tasks to appropriate skills.
 * Handles capability enforcement, timeout, and result reporting.
 */
@Singleton
class SkillExecutor @Inject constructor(
    private val commandRunner: CommandRunner,
    private val swarmCoordinator: SwarmCoordinator,
    private val memoryFactory: AgentMemoryFactory,
    private val sandboxFactory: AgentSandboxFactory
) {
    private val skills = mutableMapOf<String, SkillProvider>()
    private val skillMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Built-in Kotlin skills
    private val builtInSkills = listOf(
        PkgSkill(),
        FsSkill(),
        GitSkill(),
        DiagnosticSkill()
    )
    
    init {
        // Register built-in skills
        builtInSkills.forEach { skill ->
            registerSkill(skill)
        }
    }
    
    /**
     * Register a skill provider.
     */
    fun registerSkill(skill: SkillProvider) {
        skills[skill.name] = skill
    }
    
    /**
     * Get all registered skill names.
     */
    fun getSkillNames(): Set<String> = skills.keys.toSet()
    
    /**
     * Get a specific skill.
     */
    fun getSkill(name: String): SkillProvider? = skills[name]
    
    /**
     * Execute a skill function for an agent.
     * Enforces capabilities and handles errors.
     */
    suspend fun execute(
        agent: Agent,
        skillName: String,
        function: String,
        params: Map<String, Any?> = emptyMap(),
        timeout: Long = 60_000L
    ): TaskResult = skillMutex.withLock {
        val startTime = System.currentTimeMillis()
        val taskName = "$skillName.$function"
        
        // Get skill
        val skill = skills[skillName]
            ?: return@withLock TaskResult.Failure(
                agentName = agent.name,
                taskName = taskName,
                durationMs = System.currentTimeMillis() - startTime,
                error = AgentError(
                    type = AgentErrorType.SKILL_MISSING,
                    message = "Skill not found: $skillName",
                    agent = agent.name,
                    details = mapOf("available_skills" to skills.keys.toList())
                )
            )
        
        // Check capabilities
        val missingCapabilities = skill.requiredCapabilities.filter { required ->
            !agent.capabilities.contains(required)
        }
        
        if (missingCapabilities.isNotEmpty()) {
            return@withLock TaskResult.Failure(
                agentName = agent.name,
                taskName = taskName,
                durationMs = System.currentTimeMillis() - startTime,
                error = AgentError(
                    type = AgentErrorType.CAPABILITY_DENIED,
                    message = "Missing capabilities: ${missingCapabilities.map { it.name }.joinToString(", ")}",
                    agent = agent.name,
                    details = mapOf(
                        "required" to skill.requiredCapabilities.map { it.name },
                        "agent_has" to agent.capabilities.map { it.name },
                        "missing" to missingCapabilities.map { it.name }
                    )
                )
            )
        }
        
        // Check if function exists in skill
        if (!skill.provides.contains(function)) {
            return@withLock TaskResult.Failure(
                agentName = agent.name,
                taskName = taskName,
                durationMs = System.currentTimeMillis() - startTime,
                error = AgentError(
                    type = AgentErrorType.SKILL_NOT_ALLOWED,
                    message = "Function not found: $function in skill $skillName",
                    agent = agent.name,
                    details = mapOf("available_functions" to skill.provides)
                )
            )
        }
        
        // Emit WORKING signal
        swarmCoordinator.emit(
            signalType = SignalType.WORKING,
            sourceAgent = agent.name,
            target = taskName,
            data = mapOf("message" to "Executing $taskName")
        )
        
        // Create context
        val memory = memoryFactory.getMemory(agent.name)
        val sandbox = sandboxFactory.getSandbox(agent.name)
        val context = SkillContext(
            agentName = agent.name,
            sandbox = sandbox,
            memory = memory,
            commandRunner = commandRunner
        )
        
        // Execute with timeout
        return@withLock try {
            val result = withTimeout(timeout) {
                skill.execute(function, params, context)
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            
            if (result.success) {
                // Emit SUCCESS signal
                swarmCoordinator.emit(
                    signalType = SignalType.SUCCESS,
                    sourceAgent = agent.name,
                    target = taskName,
                    data = mapOf("message" to "Completed $taskName in ${elapsed}ms")
                )
                
                // Record in memory
                memory.appendHistory(mapOf(
                    "skill" to skillName,
                    "function" to function,
                    "params" to params,
                    "success" to true,
                    "duration" to elapsed
                ))
                
                TaskResult.Success(
                    agentName = agent.name,
                    taskName = taskName,
                    durationMs = elapsed,
                    data = result.data ?: emptyMap(),
                    steps = listOf(
                        TaskStep(
                            stepId = 1,
                            action = taskName,
                            status = StepStatus.COMPLETED,
                            startedAt = startTime,
                            completedAt = startTime + elapsed,
                            result = result.data
                        )
                    ),
                    logs = result.logs
                )
            } else {
                // Emit FAILURE signal
                swarmCoordinator.emit(
                    signalType = SignalType.FAILURE,
                    sourceAgent = agent.name,
                    target = taskName,
                    data = mapOf("message" to "Failed $taskName: ${result.error}")
                )
                
                // Record in memory
                memory.appendHistory(mapOf(
                    "skill" to skillName,
                    "function" to function,
                    "params" to params,
                    "success" to false,
                    "duration" to elapsed,
                    "error" to result.error
                ))
                
                TaskResult.Failure(
                    agentName = agent.name,
                    taskName = taskName,
                    durationMs = elapsed,
                    error = AgentError(
                        type = AgentErrorType.EXECUTION_ERROR,
                        message = result.error ?: "Unknown error",
                        agent = agent.name,
                        details = result.data
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            val elapsed = System.currentTimeMillis() - startTime
            
            // Emit BLOCKED signal
            swarmCoordinator.emit(
                signalType = SignalType.BLOCKED,
                sourceAgent = agent.name,
                target = taskName,
                data = mapOf("message" to "Timeout on $taskName after ${elapsed}ms")
            )
            
            TaskResult.Timeout(
                agentName = agent.name,
                taskName = taskName,
                durationMs = elapsed,
                timeoutMs = timeout
            )
        } catch (e: CancellationException) {
            TaskResult.Cancelled(
                agentName = agent.name,
                taskName = taskName,
                durationMs = System.currentTimeMillis() - startTime,
                reason = "Task cancelled"
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            
            // Emit DANGER signal for unexpected errors
            swarmCoordinator.emit(
                signalType = SignalType.DANGER,
                sourceAgent = agent.name,
                target = taskName,
                data = mapOf("message" to "Error in $taskName: ${e.message}")
            )
            
            TaskResult.Failure(
                agentName = agent.name,
                taskName = taskName,
                durationMs = elapsed,
                error = AgentError(
                    type = AgentErrorType.UNKNOWN_ERROR,
                    message = e.message ?: "Unknown exception",
                    agent = agent.name,
                    details = mapOf("exception_type" to e.javaClass.simpleName)
                )
            )
        }
    }
    
    /**
     * Execute a batch of tasks for an agent.
     */
    suspend fun executeBatch(
        agent: Agent,
        tasks: List<TaskRequest>,
        parallelism: Int = 1
    ): List<TaskResult> {
        return if (parallelism == 1) {
            // Sequential execution
            tasks.map { task ->
                execute(agent, task.skill, task.function, task.params, task.timeout)
            }
        } else {
            // Parallel execution with limited concurrency
            val semaphore = kotlinx.coroutines.sync.Semaphore(parallelism)
            coroutineScope {
                tasks.map { task ->
                    async {
                        semaphore.acquire()
                        try {
                            execute(agent, task.skill, task.function, task.params, task.timeout)
                        } finally {
                            semaphore.release()
                        }
                    }
                }.awaitAll()
            }
        }
    }
    
    /**
     * Run self-tests on all registered skills.
     */
    suspend fun selfTestAll(agent: Agent): Map<String, SkillResult> {
        val results = mutableMapOf<String, SkillResult>()
        
        val memory = memoryFactory.getMemory(agent.name)
        val sandbox = sandboxFactory.getSandbox(agent.name)
        val context = SkillContext(
            agentName = agent.name,
            sandbox = sandbox,
            memory = memory,
            commandRunner = commandRunner
        )
        
        for ((name, skill) in skills) {
            try {
                results[name] = skill.selfTest(context)
            } catch (e: Exception) {
                results[name] = SkillResult(
                    success = false,
                    error = "Self-test exception: ${e.message}"
                )
            }
        }
        
        return results
    }
    
    /**
     * Discover and register Python skill bridges.
     */
    suspend fun discoverPythonSkills(): List<String> {
        if (!PythonSkillBridge.isPythonAvailable(commandRunner)) {
            return emptyList()
        }
        
        val pythonSkills = PythonSkillBridge.discoverSkills(commandRunner)
        val registered = mutableListOf<String>()
        
        pythonSkills.forEach { skillName ->
            if (!skills.containsKey(skillName)) {
                val bridge = PythonSkillBridge(skillName, commandRunner)
                if (bridge.initialize()) {
                    registerSkill(bridge)
                    registered.add(skillName)
                }
            }
        }
        
        return registered
    }
    
    /**
     * Cleanup resources.
     */
    fun shutdown() {
        scope.cancel()
    }
}

/**
 * Request for a skill task.
 */
data class TaskRequest(
    val skill: String,
    val function: String,
    val params: Map<String, Any?> = emptyMap(),
    val timeout: Long = 60_000L
)
