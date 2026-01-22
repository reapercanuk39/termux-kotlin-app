package com.termux.app.agents.handoff

import org.json.JSONObject

/**
 * Shared context passed between agents during handoffs.
 * 
 * Preserves state across agent transitions, allowing agents to:
 * - Read results from previous agents
 * - Add their own results
 * - Access shared task description and metadata
 * 
 * Pattern inspired by OpenAI Swarm's context_variables.
 */
data class AgentContext(
    /**
     * Original task description
     */
    val taskDescription: String,
    
    /**
     * Unique ID for this execution chain
     */
    val executionId: String = generateExecutionId(),
    
    /**
     * Variables shared between agents
     */
    val variables: MutableMap<String, Any?> = mutableMapOf(),
    
    /**
     * Results from each agent in the chain
     */
    val agentResults: MutableList<AgentStepResult> = mutableListOf(),
    
    /**
     * Error history (for self-healing)
     */
    val errorHistory: MutableList<HandoffError> = mutableListOf(),
    
    /**
     * Current chain depth (for loop prevention)
     */
    var depth: Int = 0,
    
    /**
     * Maximum chain depth allowed
     */
    val maxDepth: Int = 10,
    
    /**
     * Start timestamp
     */
    val startedAt: Long = System.currentTimeMillis(),
    
    /**
     * Metadata for tracing/debugging
     */
    val metadata: MutableMap<String, Any?> = mutableMapOf()
) {
    companion object {
        private var counter = 0L
        
        private fun generateExecutionId(): String {
            return "exec_${System.currentTimeMillis()}_${++counter}"
        }
        
        /**
         * Create context for a simple task
         */
        fun forTask(description: String): AgentContext {
            return AgentContext(taskDescription = description)
        }
        
        /**
         * Create context with initial variables
         */
        fun forTask(description: String, variables: Map<String, Any?>): AgentContext {
            return AgentContext(
                taskDescription = description,
                variables = variables.toMutableMap()
            )
        }
    }
    
    /**
     * Get a typed variable
     */
    inline fun <reified T> get(key: String): T? {
        return variables[key] as? T
    }
    
    /**
     * Set a variable
     */
    fun set(key: String, value: Any?) {
        variables[key] = value
    }
    
    /**
     * Check if variable exists
     */
    fun has(key: String): Boolean = variables.containsKey(key)
    
    /**
     * Add result from an agent step
     */
    fun addResult(agentName: String, result: Map<String, Any?>, success: Boolean) {
        agentResults.add(AgentStepResult(
            agentName = agentName,
            result = result,
            success = success,
            timestamp = System.currentTimeMillis(),
            depth = depth
        ))
    }
    
    /**
     * Add an error to history
     */
    fun addError(agentName: String, error: String, recoverable: Boolean = true) {
        errorHistory.add(HandoffError(
            agentName = agentName,
            error = error,
            recoverable = recoverable,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    /**
     * Check if max depth reached
     */
    fun isMaxDepthReached(): Boolean = depth >= maxDepth
    
    /**
     * Get last successful result
     */
    fun getLastSuccessfulResult(): AgentStepResult? {
        return agentResults.lastOrNull { it.success }
    }
    
    /**
     * Get total duration so far
     */
    fun getDurationMs(): Long = System.currentTimeMillis() - startedAt
    
    /**
     * Convert to map for serialization
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "execution_id" to executionId,
        "task_description" to taskDescription,
        "variables" to variables,
        "agent_results" to agentResults.map { it.toMap() },
        "error_history" to errorHistory.map { it.toMap() },
        "depth" to depth,
        "max_depth" to maxDepth,
        "started_at" to startedAt,
        "duration_ms" to getDurationMs(),
        "metadata" to metadata
    )
    
    /**
     * Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject(toMap())
}

/**
 * Result from a single agent step
 */
data class AgentStepResult(
    val agentName: String,
    val result: Map<String, Any?>,
    val success: Boolean,
    val timestamp: Long,
    val depth: Int
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "agent_name" to agentName,
        "result" to result,
        "success" to success,
        "timestamp" to timestamp,
        "depth" to depth
    )
}

/**
 * Error during handoff chain
 */
data class HandoffError(
    val agentName: String,
    val error: String,
    val recoverable: Boolean,
    val timestamp: Long
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "agent_name" to agentName,
        "error" to error,
        "recoverable" to recoverable,
        "timestamp" to timestamp
    )
}
