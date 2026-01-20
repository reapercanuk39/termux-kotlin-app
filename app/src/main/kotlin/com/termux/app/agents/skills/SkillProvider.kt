package com.termux.app.agents.skills

import com.termux.app.agents.models.Capability
import com.termux.app.agents.models.CommandResult
import com.termux.app.agents.runtime.AgentMemory
import com.termux.app.agents.runtime.AgentSandbox
import com.termux.app.agents.runtime.CommandRunner
import com.termux.shared.logger.Logger

/**
 * Context provided to skills during execution
 */
data class SkillContext(
    val agentName: String,
    val sandbox: AgentSandbox,
    val memory: AgentMemory,
    val commandRunner: CommandRunner,
    val logTag: String = "Skill"
) {
    private val logs = mutableListOf<String>()
    
    fun log(message: String) {
        logs.add("[${System.currentTimeMillis()}] $message")
        Logger.logDebug(logTag, "[$agentName] $message")
    }
    
    fun getLogs(): String = logs.joinToString("\n")
}

/**
 * Result of skill execution
 */
data class SkillResult(
    val success: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val error: String? = null,
    val logs: String? = null
)

/**
 * Base interface for all skills
 */
interface SkillProvider {
    val name: String
    val description: String
    val provides: List<String>
    val requiredCapabilities: Set<Capability>
    
    /**
     * Execute a skill function
     */
    suspend fun execute(
        function: String,
        params: Map<String, Any?>,
        context: SkillContext
    ): SkillResult
    
    /**
     * Run self-test to verify skill works
     */
    suspend fun selfTest(context: SkillContext): SkillResult
    
    /**
     * Check if this skill provides a function
     */
    fun providesFunction(function: String): Boolean = provides.contains(function)
}

/**
 * Base class for skills with common functionality
 */
abstract class BaseSkill : SkillProvider {
    protected lateinit var context: SkillContext
    
    protected fun log(message: String) {
        if (::context.isInitialized) {
            context.log(message)
        }
    }
    
    protected suspend fun runCommand(
        command: List<String>,
        timeout: Long = 60_000L
    ): CommandResult {
        if (!::context.isInitialized) {
            throw IllegalStateException("Skill context not initialized")
        }
        return context.commandRunner.run(
            command = command,
            workingDir = context.sandbox.workDir,
            timeout = timeout
        )
    }
    
    protected suspend fun runShell(
        command: String,
        timeout: Long = 60_000L
    ): CommandResult {
        if (!::context.isInitialized) {
            throw IllegalStateException("Skill context not initialized")
        }
        return context.commandRunner.runShell(
            command = command,
            workingDir = context.sandbox.workDir,
            timeout = timeout
        )
    }
    
    override suspend fun execute(
        function: String,
        params: Map<String, Any?>,
        context: SkillContext
    ): SkillResult {
        this.context = context
        
        if (!providesFunction(function)) {
            return SkillResult(
                success = false,
                error = "Function '$function' not provided by skill '$name'"
            )
        }
        
        return try {
            log("Executing $name.$function")
            executeFunction(function, params)
        } catch (e: Exception) {
            log("Error in $name.$function: ${e.message}")
            SkillResult(
                success = false,
                error = e.message ?: "Unknown error",
                logs = context.getLogs()
            )
        }
    }
    
    /**
     * Subclasses implement this to dispatch to specific functions
     */
    protected abstract suspend fun executeFunction(
        function: String,
        params: Map<String, Any?>
    ): SkillResult
}
