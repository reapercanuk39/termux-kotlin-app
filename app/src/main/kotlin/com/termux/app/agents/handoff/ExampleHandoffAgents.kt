package com.termux.app.agents.handoff

import com.termux.app.agents.daemon.AgentDaemon
import com.termux.app.agents.models.TaskResult
import com.termux.shared.logger.Logger

/**
 * Example handoff agents demonstrating the Swarm-style pattern.
 * 
 * These show how to create agents that can:
 * - Execute tasks with context
 * - Hand off to other agents based on results
 * - Pass context between agents
 */

/**
 * Setup Agent - Initial environment setup
 * 
 * Handoff chain: SetupAgent -> DiagnosticAgent -> SecurityAgent
 */
class SetupHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "SetupHandoffAgent"
    }
    
    override val handoffName: String = "setup_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "diagnostic_agent",
        "security_agent"
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        // Can handle setup-related tasks
        return context.taskDescription.contains("setup", ignoreCase = true) ||
               context.taskDescription.contains("install", ignoreCase = true) ||
               context.taskDescription.contains("configure", ignoreCase = true)
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "Executing setup with context: ${context.taskDescription}")
        
        // Check if we're doing initial setup or configuration
        val isInitialSetup = !context.has("setup_complete")
        
        if (isInitialSetup) {
            // Run environment setup
            val result = daemon.runTask(
                agentName = "setup_agent",
                skillName = "diagnostic",
                function = "check_env",
                params = emptyMap()
            )
            
            when (result) {
                is TaskResult.Success -> {
                    // Mark setup as complete
                    context.set("setup_complete", true)
                    context.set("env_status", result.data)
                    
                    // Hand off to diagnostic agent for verification
                    return HandoffResult.handoffTo(
                        fromAgent = handoffName,
                        toAgent = "diagnostic_agent",
                        reason = "Environment setup complete, verifying configuration",
                        contextUpdates = mapOf("setup_result" to result.data)
                    )
                }
                is TaskResult.Failure -> {
                    return HandoffResult.fail(
                        agentName = handoffName,
                        error = "Setup failed: ${result.error.message}",
                        recoverable = true,
                        suggestedAgent = "heal_agent"  // Try self-healing
                    )
                }
                else -> {
                    return HandoffResult.fail(
                        agentName = handoffName,
                        error = "Unexpected result type",
                        recoverable = false
                    )
                }
            }
        } else {
            // Setup already done, complete
            return HandoffResult.complete(
                agentName = handoffName,
                result = mapOf("status" to "already_setup"),
                message = "Setup was already completed"
            )
        }
    }
}

/**
 * Diagnostic Agent - System verification
 * 
 * Checks system health and hands off to security or heal agents as needed.
 */
class DiagnosticHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "DiagnosticHandoffAgent"
    }
    
    override val handoffName: String = "diagnostic_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "security_agent",
        "heal_agent"
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.taskDescription.contains("diagnos", ignoreCase = true) ||
               context.taskDescription.contains("check", ignoreCase = true) ||
               context.taskDescription.contains("verify", ignoreCase = true)
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "Running diagnostics")
        
        val result = daemon.runTask(
            agentName = "diagnostic_agent",
            skillName = "diagnostic",
            function = "run",
            params = mapOf("category" to "all")
        )
        
        when (result) {
            is TaskResult.Success -> {
                val issues = result.data["issues"] as? List<*> ?: emptyList<Any>()
                
                if (issues.isEmpty()) {
                    // All good, check security
                    return HandoffResult.handoffTo(
                        fromAgent = handoffName,
                        toAgent = "security_agent",
                        reason = "Diagnostics passed, proceeding to security check",
                        contextUpdates = mapOf("diagnostic_result" to result.data)
                    )
                } else {
                    // Issues found, try to heal
                    return HandoffResult.handoffTo(
                        fromAgent = handoffName,
                        toAgent = "heal_agent",
                        reason = "Found ${issues.size} issues, attempting repair",
                        contextUpdates = mapOf(
                            "diagnostic_result" to result.data,
                            "issues_found" to issues
                        )
                    )
                }
            }
            is TaskResult.Failure -> {
                return HandoffResult.fail(
                    agentName = handoffName,
                    error = "Diagnostics failed: ${result.error.message}",
                    recoverable = true,
                    suggestedAgent = "heal_agent"
                )
            }
            else -> {
                return HandoffResult.fail(handoffName, "Unexpected result")
            }
        }
    }
}

/**
 * Security Agent - Security verification (terminal agent in chain)
 * 
 * Final agent that completes the handoff chain.
 */
class SecurityHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "SecurityHandoffAgent"
    }
    
    override val handoffName: String = "security_agent"
    
    override fun getHandoffTargets(): List<String> = emptyList()  // Terminal agent
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.taskDescription.contains("security", ignoreCase = true) ||
               context.taskDescription.contains("permission", ignoreCase = true)
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "Running security checks")
        
        val result = daemon.runTask(
            agentName = "security_agent",
            skillName = "diagnostic",
            function = "run",
            params = mapOf("category" to "security")
        )
        
        when (result) {
            is TaskResult.Success -> {
                // Compile full report from context
                val fullReport = mapOf(
                    "setup_result" to context.get<Any>("setup_result"),
                    "diagnostic_result" to context.get<Any>("diagnostic_result"),
                    "security_result" to result.data,
                    "chain_depth" to context.depth,
                    "total_duration_ms" to context.getDurationMs()
                )
                
                return HandoffResult.complete(
                    agentName = handoffName,
                    result = fullReport,
                    message = "All checks completed successfully"
                )
            }
            is TaskResult.Failure -> {
                return HandoffResult.fail(
                    agentName = handoffName,
                    error = "Security check failed: ${result.error.message}",
                    recoverable = false  // Security issues are critical
                )
            }
            else -> {
                return HandoffResult.fail(handoffName, "Unexpected result")
            }
        }
    }
}

/**
 * Heal Agent - Self-healing agent
 * 
 * Attempts to fix issues found by other agents.
 */
class HealHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "HealHandoffAgent"
    }
    
    override val handoffName: String = "heal_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "diagnostic_agent"  // After healing, re-verify
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.has("issues_found") ||
               context.errorHistory.any { it.recoverable }
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "Attempting to heal issues")
        
        val issues = context.get<List<*>>("issues_found") ?: emptyList<Any>()
        
        if (issues.isEmpty()) {
            // No issues to fix
            return HandoffResult.complete(
                agentName = handoffName,
                result = mapOf("status" to "no_issues"),
                message = "No issues to fix"
            )
        }
        
        // Attempt repairs
        var fixed = 0
        val failures = mutableListOf<String>()
        
        for (issue in issues) {
            // In practice, this would run specific repair functions
            // For now, simulate repair
            val issueStr = issue.toString()
            if (!issueStr.contains("critical", ignoreCase = true)) {
                fixed++
            } else {
                failures.add(issueStr)
            }
        }
        
        if (failures.isEmpty()) {
            // All fixed, re-verify
            context.set("heal_result", mapOf("fixed" to fixed))
            
            return HandoffResult.handoffTo(
                fromAgent = handoffName,
                toAgent = "diagnostic_agent",
                reason = "Repaired $fixed issues, re-verifying",
                contextUpdates = mapOf("issues_found" to emptyList<Any>())
            )
        } else {
            return HandoffResult.fail(
                agentName = handoffName,
                error = "Could not fix ${failures.size} critical issues",
                recoverable = false
            )
        }
    }
}
