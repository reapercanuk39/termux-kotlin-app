package com.termux.app.agents.handoff

import com.termux.app.agents.models.Agent
import com.termux.app.agents.models.TaskResult

/**
 * Result of an agent execution that may include a handoff.
 * 
 * Inspired by OpenAI Swarm's approach where functions can return:
 * - A value (complete)
 * - Another agent (handoff)
 * - Both (update context and handoff)
 */
sealed class HandoffResult {
    
    /**
     * Execution completed successfully, no handoff needed
     */
    data class Complete(
        val agentName: String,
        val result: Map<String, Any?>,
        val message: String? = null
    ) : HandoffResult() {
        override fun toString() = "Complete(agent=$agentName, message=$message)"
    }
    
    /**
     * Hand off execution to another agent
     */
    data class Handoff(
        val fromAgent: String,
        val toAgent: String,
        val reason: String? = null,
        val contextUpdates: Map<String, Any?> = emptyMap()
    ) : HandoffResult() {
        override fun toString() = "Handoff(from=$fromAgent, to=$toAgent, reason=$reason)"
    }
    
    /**
     * Execution failed, may include recovery suggestion
     */
    data class Failure(
        val agentName: String,
        val error: String,
        val recoverable: Boolean = true,
        val suggestedAgent: String? = null
    ) : HandoffResult() {
        override fun toString() = "Failure(agent=$agentName, error=$error, recoverable=$recoverable)"
    }
    
    /**
     * Need more information from user
     */
    data class NeedInput(
        val agentName: String,
        val question: String,
        val options: List<String>? = null
    ) : HandoffResult() {
        override fun toString() = "NeedInput(agent=$agentName, question=$question)"
    }
    
    companion object {
        /**
         * Create complete result
         */
        fun complete(agentName: String, result: Map<String, Any?> = emptyMap(), message: String? = null): Complete {
            return Complete(agentName, result, message)
        }
        
        /**
         * Create handoff result
         */
        fun handoffTo(
            fromAgent: String,
            toAgent: String,
            reason: String? = null,
            contextUpdates: Map<String, Any?> = emptyMap()
        ): Handoff {
            return Handoff(fromAgent, toAgent, reason, contextUpdates)
        }
        
        /**
         * Create failure result
         */
        fun fail(
            agentName: String,
            error: String,
            recoverable: Boolean = true,
            suggestedAgent: String? = null
        ): Failure {
            return Failure(agentName, error, recoverable, suggestedAgent)
        }
        
        /**
         * Create need input result
         */
        fun needInput(
            agentName: String,
            prompt: String,
            inputType: String = "text",
            options: List<String>? = null
        ): NeedInput {
            return NeedInput(agentName, prompt, options)
        }
        
        /**
         * Create from TaskResult
         */
        fun fromTaskResult(agentName: String, taskResult: TaskResult): HandoffResult {
            return when (taskResult) {
                is TaskResult.Success -> Complete(
                    agentName = agentName,
                    result = taskResult.data,
                    message = "Task completed successfully"
                )
                is TaskResult.Failure -> Failure(
                    agentName = agentName,
                    error = taskResult.error.message,
                    recoverable = true
                )
                is TaskResult.Timeout -> Failure(
                    agentName = agentName,
                    error = "Task timed out after ${taskResult.timeoutMs}ms",
                    recoverable = true
                )
                is TaskResult.Cancelled -> Failure(
                    agentName = agentName,
                    error = "Task cancelled: ${taskResult.reason}",
                    recoverable = false
                )
            }
        }
    }
}

/**
 * Interface for agents that support handoffs.
 * 
 * Agents implement this to participate in handoff chains.
 */
interface HandoffCapable {
    /**
     * Agent name for handoffs
     */
    val handoffName: String
    
    /**
     * Execute with context and potentially hand off
     */
    suspend fun executeWithContext(context: AgentContext): HandoffResult
    
    /**
     * Get list of agents this agent can hand off to
     */
    fun getHandoffTargets(): List<String> = emptyList()
    
    /**
     * Check if this agent can handle the given context
     */
    fun canHandle(context: AgentContext): Boolean = true
}

/**
 * Handoff function type.
 * 
 * Functions that return another agent's name trigger handoffs.
 */
typealias HandoffFunction = suspend (AgentContext) -> HandoffResult
