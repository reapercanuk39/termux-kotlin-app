package com.termux.app.agents.handoff

import com.termux.app.agents.daemon.AgentDaemon
import com.termux.app.agents.daemon.AgentRegistry
import com.termux.app.agents.models.Agent
import com.termux.app.agents.models.TaskResult
import com.termux.app.agents.swarm.SignalType
import com.termux.app.agents.swarm.SwarmCoordinator
import com.termux.shared.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executor for handoff chains.
 * 
 * Implements the Swarm-style execution loop:
 * 1. Execute current agent
 * 2. If result is Handoff, switch to target agent
 * 3. Repeat until Complete or Failure
 * 
 * Features:
 * - Context preservation across handoffs
 * - Loop detection (max depth)
 * - Error recovery with suggested agents
 * - Swarm signal integration for observability
 */
@Singleton
class HandoffExecutor @Inject constructor(
    private val registry: AgentRegistry,
    private val daemon: AgentDaemon,
    private val swarmCoordinator: SwarmCoordinator
) {
    companion object {
        private const val LOG_TAG = "HandoffExecutor"
        private const val DEFAULT_MAX_DEPTH = 10
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Registry of handoff-capable agents
    private val handoffAgents = mutableMapOf<String, HandoffCapable>()
    
    /**
     * Register a handoff-capable agent
     */
    fun registerHandoffAgent(agent: HandoffCapable) {
        handoffAgents[agent.handoffName] = agent
        Logger.logDebug(LOG_TAG, "Registered handoff agent: ${agent.handoffName}")
    }
    
    /**
     * Unregister a handoff agent
     */
    fun unregisterHandoffAgent(name: String) {
        handoffAgents.remove(name)
    }
    
    /**
     * Execute a handoff chain starting with the given agent.
     * 
     * @param startAgent Initial agent name
     * @param context Execution context
     * @return Final result after all handoffs complete
     */
    suspend fun execute(
        startAgent: String,
        context: AgentContext
    ): HandoffChainResult {
        Logger.logInfo(LOG_TAG, "Starting handoff chain: $startAgent, execution=${context.executionId}")
        
        val executionTrace = mutableListOf<HandoffStep>()
        var currentAgent = startAgent
        var result: HandoffResult? = null
        
        // Emit chain start signal
        swarmCoordinator.emit(
            signalType = SignalType.WORKING,
            sourceAgent = "handoff_executor",
            target = context.executionId,
            data = mapOf(
                "message" to "Starting handoff chain",
                "start_agent" to startAgent,
                "task" to context.taskDescription
            )
        )
        
        while (!context.isMaxDepthReached()) {
            context.depth++
            
            Logger.logDebug(LOG_TAG, "Executing agent: $currentAgent (depth=${context.depth})")
            
            // Get handoff-capable agent or fall back to daemon
            val stepResult = executeAgent(currentAgent, context)
            
            executionTrace.add(HandoffStep(
                agentName = currentAgent,
                result = stepResult,
                depth = context.depth,
                timestamp = System.currentTimeMillis()
            ))
            
            result = stepResult
            
            when (stepResult) {
                is HandoffResult.Complete -> {
                    Logger.logInfo(LOG_TAG, "Chain completed at $currentAgent")
                    context.addResult(currentAgent, stepResult.result, true)
                    
                    // Emit success signal
                    swarmCoordinator.emit(
                        signalType = SignalType.SUCCESS,
                        sourceAgent = currentAgent,
                        target = context.executionId,
                        data = mapOf("message" to "Chain completed: ${stepResult.message}")
                    )
                    
                    return HandoffChainResult.Success(
                        finalAgent = currentAgent,
                        result = stepResult.result,
                        trace = executionTrace,
                        context = context
                    )
                }
                
                is HandoffResult.Handoff -> {
                    Logger.logInfo(LOG_TAG, "Handoff: $currentAgent -> ${stepResult.toAgent}")
                    
                    // Apply context updates
                    stepResult.contextUpdates.forEach { (key, value) ->
                        context.set(key, value)
                    }
                    
                    // Emit handoff signal
                    swarmCoordinator.emit(
                        signalType = SignalType.WORKING,
                        sourceAgent = currentAgent,
                        target = context.executionId,
                        data = mapOf(
                            "message" to "Handoff to ${stepResult.toAgent}",
                            "reason" to (stepResult.reason ?: "")
                        )
                    )
                    
                    currentAgent = stepResult.toAgent
                }
                
                is HandoffResult.Failure -> {
                    Logger.logWarn(LOG_TAG, "Agent failed: $currentAgent - ${stepResult.error}")
                    context.addError(currentAgent, stepResult.error, stepResult.recoverable)
                    
                    // Try suggested agent if recoverable
                    if (stepResult.recoverable && stepResult.suggestedAgent != null) {
                        Logger.logInfo(LOG_TAG, "Attempting recovery with ${stepResult.suggestedAgent}")
                        currentAgent = stepResult.suggestedAgent
                        continue
                    }
                    
                    // Emit failure signal
                    swarmCoordinator.emit(
                        signalType = SignalType.FAILURE,
                        sourceAgent = currentAgent,
                        target = context.executionId,
                        data = mapOf("message" to "Chain failed: ${stepResult.error}")
                    )
                    
                    return HandoffChainResult.Failure(
                        failedAgent = currentAgent,
                        error = stepResult.error,
                        trace = executionTrace,
                        context = context
                    )
                }
                
                is HandoffResult.NeedInput -> {
                    Logger.logInfo(LOG_TAG, "Chain needs input at $currentAgent")
                    
                    return HandoffChainResult.NeedInput(
                        agent = currentAgent,
                        question = stepResult.question,
                        options = stepResult.options,
                        trace = executionTrace,
                        context = context
                    )
                }
            }
        }
        
        // Max depth reached
        Logger.logWarn(LOG_TAG, "Max depth reached at $currentAgent")
        
        swarmCoordinator.emit(
            signalType = SignalType.BLOCKED,
            sourceAgent = "handoff_executor",
            target = context.executionId,
            data = mapOf("message" to "Max handoff depth (${context.maxDepth}) reached")
        )
        
        return HandoffChainResult.MaxDepthReached(
            lastAgent = currentAgent,
            trace = executionTrace,
            context = context
        )
    }
    
    /**
     * Execute a single agent
     */
    private suspend fun executeAgent(
        agentName: String,
        context: AgentContext
    ): HandoffResult {
        // Check for registered handoff agent
        val handoffAgent = handoffAgents[agentName]
        if (handoffAgent != null) {
            return try {
                handoffAgent.executeWithContext(context)
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Handoff agent error: ${e.message}")
                HandoffResult.fail(agentName, e.message ?: "Unknown error")
            }
        }
        
        // Fall back to daemon-based execution
        return executeDaemonAgent(agentName, context)
    }
    
    /**
     * Execute agent via daemon (default path)
     */
    private suspend fun executeDaemonAgent(
        agentName: String,
        context: AgentContext
    ): HandoffResult {
        // Get agent from registry
        val agent = registry.getAgent(agentName)
            ?: return HandoffResult.fail(
                agentName,
                "Agent not found: $agentName",
                recoverable = false
            )
        
        // Determine skill and function to run
        // For now, use diagnostic.run as a default health check
        // In practice, this would be determined by task parsing
        val result = daemon.runTask(
            agentName = agentName,
            skillName = "diagnostic",
            function = "run",
            params = mapOf("context" to context.toMap())
        )
        
        return HandoffResult.fromTaskResult(agentName, result)
    }
    
    /**
     * Resume a chain that was waiting for input
     */
    suspend fun resumeWithInput(
        context: AgentContext,
        input: String,
        continueAgent: String
    ): HandoffChainResult {
        context.set("user_input", input)
        return execute(continueAgent, context)
    }
}

/**
 * Result of a complete handoff chain execution
 */
sealed class HandoffChainResult {
    abstract val trace: List<HandoffStep>
    abstract val context: AgentContext
    
    /**
     * Chain completed successfully
     */
    data class Success(
        val finalAgent: String,
        val result: Map<String, Any?>,
        override val trace: List<HandoffStep>,
        override val context: AgentContext
    ) : HandoffChainResult()
    
    /**
     * Chain failed with error
     */
    data class Failure(
        val failedAgent: String,
        val error: String,
        override val trace: List<HandoffStep>,
        override val context: AgentContext
    ) : HandoffChainResult()
    
    /**
     * Chain needs user input
     */
    data class NeedInput(
        val agent: String,
        val question: String,
        val options: List<String>?,
        override val trace: List<HandoffStep>,
        override val context: AgentContext
    ) : HandoffChainResult()
    
    /**
     * Chain reached maximum depth
     */
    data class MaxDepthReached(
        val lastAgent: String,
        override val trace: List<HandoffStep>,
        override val context: AgentContext
    ) : HandoffChainResult()
}

/**
 * Single step in handoff trace
 */
data class HandoffStep(
    val agentName: String,
    val result: HandoffResult,
    val depth: Int,
    val timestamp: Long
)
