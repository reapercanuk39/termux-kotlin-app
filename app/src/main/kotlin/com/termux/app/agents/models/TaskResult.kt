package com.termux.app.agents.models

/**
 * Task execution result types.
 */
sealed class TaskResult {
    abstract val agentName: String
    abstract val taskName: String
    abstract val durationMs: Long
    
    /**
     * Successful task completion
     */
    data class Success(
        override val agentName: String,
        override val taskName: String,
        override val durationMs: Long,
        val data: Map<String, Any?> = emptyMap(),
        val steps: List<TaskStep> = emptyList(),
        val logs: String? = null
    ) : TaskResult()
    
    /**
     * Task failed with error
     */
    data class Failure(
        override val agentName: String,
        override val taskName: String,
        override val durationMs: Long,
        val error: AgentError,
        val steps: List<TaskStep> = emptyList(),
        val logs: String? = null
    ) : TaskResult()
    
    /**
     * Task timed out
     */
    data class Timeout(
        override val agentName: String,
        override val taskName: String,
        override val durationMs: Long,
        val timeoutMs: Long,
        val steps: List<TaskStep> = emptyList()
    ) : TaskResult()
    
    /**
     * Task was cancelled
     */
    data class Cancelled(
        override val agentName: String,
        override val taskName: String,
        override val durationMs: Long,
        val reason: String? = null
    ) : TaskResult()
    
    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure
    
    fun toMap(): Map<String, Any?> = when (this) {
        is Success -> mapOf(
            "status" to "success",
            "agent" to agentName,
            "task" to taskName,
            "duration_ms" to durationMs,
            "data" to data,
            "steps" to steps.map { it.toMap() },
            "logs" to logs
        )
        is Failure -> mapOf(
            "status" to "error",
            "agent" to agentName,
            "task" to taskName,
            "duration_ms" to durationMs,
            "error" to error.toMap(),
            "steps" to steps.map { it.toMap() },
            "logs" to logs
        )
        is Timeout -> mapOf(
            "status" to "timeout",
            "agent" to agentName,
            "task" to taskName,
            "duration_ms" to durationMs,
            "timeout_ms" to timeoutMs,
            "steps" to steps.map { it.toMap() }
        )
        is Cancelled -> mapOf(
            "status" to "cancelled",
            "agent" to agentName,
            "task" to taskName,
            "duration_ms" to durationMs,
            "reason" to reason
        )
    }
}

/**
 * A single step in task execution
 */
data class TaskStep(
    val stepId: Int,
    val action: String,
    val status: StepStatus,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val result: Any? = null,
    val error: String? = null,
    val capabilityChecks: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "step_id" to stepId,
        "action" to action,
        "status" to status.name.lowercase(),
        "started_at" to startedAt,
        "completed_at" to completedAt,
        "result" to result,
        "error" to error,
        "capability_checks" to capabilityChecks
    )
}

enum class StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * Agent error types
 */
enum class AgentErrorType {
    CAPABILITY_DENIED,
    SKILL_NOT_ALLOWED,
    SKILL_MISSING,
    INVALID_PATH,
    SANDBOX_VIOLATION,
    EXECUTION_ERROR,
    MEMORY_ERROR,
    NETWORK_VIOLATION,
    TIMEOUT,
    UNKNOWN_ERROR
}

/**
 * Structured agent error
 */
data class AgentError(
    val type: AgentErrorType,
    val message: String,
    val agent: String,
    val required: String? = null,
    val details: Map<String, Any?>? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "error" to type.name.lowercase(),
        "message" to message,
        "agent" to agent,
        "required" to required,
        "details" to details
    )
}

/**
 * Command execution result
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
) {
    val isSuccess: Boolean get() = exitCode == 0
}
