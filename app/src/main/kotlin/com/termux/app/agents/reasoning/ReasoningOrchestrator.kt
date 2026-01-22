package com.termux.app.agents.reasoning

import com.termux.app.agents.daemon.AgentDaemon
import com.termux.app.agents.handoff.AgentContext
import com.termux.app.agents.handoff.HandoffChainResult
import com.termux.app.agents.handoff.HandoffExecutor
import com.termux.app.agents.handoff.HandoffResult
import com.termux.app.agents.handoff.system.SystemRepairChainFactory
import com.termux.app.agents.llm.LlmOptions
import com.termux.app.agents.llm.LlmProvider
import com.termux.app.agents.mcp.PromptRegistry
import com.termux.shared.logger.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event emitted during reasoning process.
 */
sealed class ReasoningEvent {
    data class IntentRecognized(val intent: UserIntent) : ReasoningEvent()
    data class PlanCreated(val plan: ExecutionPlan) : ReasoningEvent()
    data class StepStarted(val stepNumber: Int, val description: String) : ReasoningEvent()
    data class StepCompleted(val stepNumber: Int, val success: Boolean) : ReasoningEvent()
    data class ChainStarted(val chainName: String) : ReasoningEvent()
    data class ChainProgress(val agentName: String, val status: String) : ReasoningEvent()
    data class Completed(val result: ReasoningResult) : ReasoningEvent()
    data class Error(val message: String, val recoverable: Boolean) : ReasoningEvent()
}

/**
 * Final result of reasoning process.
 */
data class ReasoningResult(
    val success: Boolean,
    val intent: UserIntent,
    val plan: ExecutionPlan?,
    val executionResult: PlanExecutionResult?,
    val summary: String,
    val durationMs: Long,
    val llmSummary: String? = null
)

/**
 * Reasoning Orchestrator.
 * 
 * Coordinates the full natural language → intent → plan → execution pipeline.
 * This is the main entry point for processing user requests.
 * 
 * Flow:
 * 1. User provides natural language request
 * 2. IntentRecognizer identifies intent
 * 3. TaskPlanner creates execution plan
 * 4. Plan is executed via HandoffExecutor or step-by-step
 * 5. Results are optionally summarized by LLM
 */
@Singleton
class ReasoningOrchestrator @Inject constructor(
    private val intentRecognizer: IntentRecognizer,
    private val taskPlanner: TaskPlanner,
    private val handoffExecutor: HandoffExecutor,
    private val llmProvider: LlmProvider,
    private val promptRegistry: PromptRegistry
) {
    companion object {
        private const val LOG_TAG = "ReasoningOrchestrator"
    }
    
    /**
     * Process a natural language request.
     * 
     * Returns a Flow of events for progress tracking.
     */
    fun process(query: String, options: ReasoningOptions = ReasoningOptions()): Flow<ReasoningEvent> = flow {
        val startTime = System.currentTimeMillis()
        
        Logger.logInfo(LOG_TAG, "Processing request: ${query.take(100)}")
        
        try {
            // Step 1: Recognize intent
            val intent = intentRecognizer.recognize(query, useLlm = options.useLlmForIntent)
            emit(ReasoningEvent.IntentRecognized(intent))
            
            Logger.logDebug(LOG_TAG, "Intent: ${intent::class.simpleName} (${intent.confidence})")
            
            // Handle unknown intents
            if (intent is UserIntent.Unknown || intent.confidence < options.minConfidence) {
                emit(ReasoningEvent.Error(
                    message = "Could not understand request. Please be more specific.",
                    recoverable = true
                ))
                emit(ReasoningEvent.Completed(ReasoningResult(
                    success = false,
                    intent = intent,
                    plan = null,
                    executionResult = null,
                    summary = "Intent not recognized",
                    durationMs = System.currentTimeMillis() - startTime
                )))
                return@flow
            }
            
            // Step 2: Create plan
            val plan = taskPlanner.createPlan(intent)
            emit(ReasoningEvent.PlanCreated(plan))
            
            Logger.logDebug(LOG_TAG, "Plan created: ${plan.description}")
            
            // Step 3: Execute (if not dry run)
            if (options.dryRun) {
                emit(ReasoningEvent.Completed(ReasoningResult(
                    success = true,
                    intent = intent,
                    plan = plan,
                    executionResult = null,
                    summary = "Dry run - plan created but not executed",
                    durationMs = System.currentTimeMillis() - startTime
                )))
                return@flow
            }
            
            // Execute the plan
            val executionResult = if (plan.chainName != null) {
                // Use handoff chain
                emit(ReasoningEvent.ChainStarted(plan.chainName))
                executeChain(plan, options)
            } else {
                // Step-by-step execution
                executeSteps(plan, options) { stepNumber, description ->
                    emit(ReasoningEvent.StepStarted(stepNumber, description))
                }
            }
            
            // Step 4: Generate summary
            val summary = generateSummary(intent, plan, executionResult, options)
            
            emit(ReasoningEvent.Completed(ReasoningResult(
                success = isSuccess(executionResult),
                intent = intent,
                plan = plan,
                executionResult = executionResult,
                summary = summary.first,
                durationMs = System.currentTimeMillis() - startTime,
                llmSummary = summary.second
            )))
            
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Reasoning failed: ${e.message}")
            emit(ReasoningEvent.Error(e.message ?: "Unknown error", recoverable = false))
            emit(ReasoningEvent.Completed(ReasoningResult(
                success = false,
                intent = UserIntent.Unknown(rawQuery = query),
                plan = null,
                executionResult = null,
                summary = "Error: ${e.message}",
                durationMs = System.currentTimeMillis() - startTime
            )))
        }
    }
    
    /**
     * Process a system repair request specifically.
     * 
     * Shortcut that skips intent recognition and goes directly to repair chain.
     */
    suspend fun processSystemRepair(
        description: String,
        repairType: RepairType = RepairType.FULL_SYSTEM_CHECK
    ): ReasoningResult {
        val startTime = System.currentTimeMillis()
        
        val intent = UserIntent.SystemRepair(
            confidence = 1.0f,
            rawQuery = description,
            repairType = repairType
        )
        
        val plan = taskPlanner.createPlan(intent)
        
        val executionResult = if (plan.chainName != null) {
            executeChain(plan, ReasoningOptions())
        } else {
            taskPlanner.executePlan(plan, "system_repair_agent")
        }
        
        val summary = generateSummary(intent, plan, executionResult, ReasoningOptions())
        
        return ReasoningResult(
            success = isSuccess(executionResult),
            intent = intent,
            plan = plan,
            executionResult = executionResult,
            summary = summary.first,
            durationMs = System.currentTimeMillis() - startTime,
            llmSummary = summary.second
        )
    }
    
    /**
     * Quick check if a query is likely a system repair request.
     */
    fun isSystemRepairQuery(query: String): Boolean {
        return intentRecognizer.isSystemRepairRequest(query)
    }
    
    /**
     * Execute a handoff chain.
     */
    private suspend fun executeChain(
        plan: ExecutionPlan,
        options: ReasoningOptions
    ): PlanExecutionResult {
        val context = AgentContext(
            taskDescription = plan.description,
            metadata = mutableMapOf(
                "plan_id" to plan.id,
                "dry_run" to options.dryRun,
                "repair_type" to (plan.intent as? UserIntent.SystemRepair)?.repairType?.name
            )
        )
        
        val result = handoffExecutor.execute(plan.chainName!!, context)
        
        return PlanExecutionResult.ChainResult(
            plan = plan,
            handoffResult = result,
            durationMs = context.get<Long>("chain_duration") ?: 0
        )
    }
    
    /**
     * Execute plan step by step.
     */
    private suspend fun executeSteps(
        plan: ExecutionPlan,
        options: ReasoningOptions,
        onProgress: suspend (Int, String) -> Unit
    ): PlanExecutionResult {
        return taskPlanner.executePlan(
            plan = plan,
            agentName = "reasoning_agent",
            dryRun = options.dryRun
        ) { stepNumber, description ->
            // Can't suspend here in non-suspend lambda, so just log
            Logger.logDebug(LOG_TAG, "Step $stepNumber: $description")
        }
    }
    
    /**
     * Generate a human-readable summary.
     */
    private suspend fun generateSummary(
        intent: UserIntent,
        plan: ExecutionPlan,
        result: PlanExecutionResult?,
        options: ReasoningOptions
    ): Pair<String, String?> {
        // Basic summary
        val basicSummary = when (result) {
            is PlanExecutionResult.Success -> 
                "Completed successfully: ${result.summary}"
            is PlanExecutionResult.PartialSuccess -> 
                "Partially completed. Failed at step ${result.failedStep}: ${result.error}"
            is PlanExecutionResult.Failure -> 
                "Failed: ${result.error}"
            is PlanExecutionResult.ChainResult -> when (result.handoffResult) {
                is HandoffChainResult.Success -> 
                    "Chain completed successfully by ${result.handoffResult.finalAgent}"
                is HandoffChainResult.Failure -> 
                    "Chain failed at ${result.handoffResult.failedAgent}: ${result.handoffResult.error}"
                is HandoffChainResult.NeedInput ->
                    "Chain paused for input: ${result.handoffResult.question}"
                is HandoffChainResult.MaxDepthReached ->
                    "Chain aborted: max depth reached at ${result.handoffResult.lastAgent}"
            }
            null -> "Plan created but not executed"
        }
        
        // Optional LLM-enhanced summary
        var llmSummary: String? = null
        if (options.summarizeWithLlm && llmProvider.isAvailable() && result != null) {
            llmSummary = generateLlmSummary(intent, result)
        }
        
        return Pair(basicSummary, llmSummary)
    }
    
    /**
     * Generate LLM-enhanced summary.
     */
    private suspend fun generateLlmSummary(
        intent: UserIntent,
        result: PlanExecutionResult
    ): String? {
        if (intent !is UserIntent.SystemRepair) return null
        
        // Get the prompt template
        val promptMessages = promptRegistry.getPrompt(
            "system_repair_summary",
            mapOf(
                "results" to result.toString(),
                "original_issue" to intent.rawQuery
            )
        ) ?: return null
        
        // Generate summary
        val llmResult = llmProvider.complete(
            prompt = promptMessages.firstOrNull()?.content?.toString() ?: return null,
            options = LlmOptions(maxTokens = 300, temperature = 0.3f)
        )
        
        return llmResult.getOrNull()?.content
    }
    
    private fun isSuccess(result: PlanExecutionResult?): Boolean {
        return when (result) {
            is PlanExecutionResult.Success -> true
            is PlanExecutionResult.ChainResult -> result.handoffResult is HandoffChainResult.Success
            else -> false
        }
    }
}

/**
 * Options for the reasoning process.
 */
data class ReasoningOptions(
    val useLlmForIntent: Boolean = true,
    val minConfidence: Float = 0.5f,
    val dryRun: Boolean = false,
    val summarizeWithLlm: Boolean = true,
    val timeout: Long = 120_000L
)
