package com.termux.app.agents.reasoning

import com.termux.app.agents.handoff.AgentContext
import com.termux.app.agents.handoff.HandoffChainResult
import com.termux.app.agents.handoff.HandoffExecutor
import com.termux.app.agents.handoff.HandoffResult
import com.termux.app.agents.handoff.system.SystemRepairChainFactory
import com.termux.app.agents.llm.ChatMessage
import com.termux.app.agents.llm.LlmOptions
import com.termux.app.agents.llm.LlmProvider
import com.termux.shared.logger.Logger
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single step in an execution plan.
 */
data class PlanStep(
    val stepNumber: Int,
    val description: String,
    val skillName: String,
    val functionName: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val condition: String? = null,
    val onFailure: FailureAction = FailureAction.CONTINUE
)

/**
 * What to do if a step fails.
 */
enum class FailureAction {
    CONTINUE,    // Continue to next step
    ABORT,       // Stop execution
    RETRY,       // Retry the step
    SKIP_NEXT    // Skip the next step
}

/**
 * Complete execution plan.
 */
data class ExecutionPlan(
    val id: String = java.util.UUID.randomUUID().toString(),
    val intent: UserIntent,
    val description: String,
    val steps: List<PlanStep>,
    val chainName: String? = null,  // If using a handoff chain
    val estimatedDurationMs: Long = 0,
    val requiresRoot: Boolean = false,
    val dryRunSupported: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("description", description)
            put("chainName", chainName)
            put("estimatedDurationMs", estimatedDurationMs)
            put("requiresRoot", requiresRoot)
            put("dryRunSupported", dryRunSupported)
            put("steps", JSONArray().apply {
                steps.forEach { step ->
                    put(JSONObject().apply {
                        put("stepNumber", step.stepNumber)
                        put("description", step.description)
                        put("skill", step.skillName)
                        put("function", step.functionName)
                        put("parameters", JSONObject(step.parameters))
                        step.condition?.let { put("condition", it) }
                        put("onFailure", step.onFailure.name)
                    })
                }
            })
        }
    }
}

/**
 * Result of plan execution.
 */
sealed class PlanExecutionResult {
    data class Success(
        val plan: ExecutionPlan,
        val results: List<StepResult>,
        val summary: String,
        val durationMs: Long
    ) : PlanExecutionResult()
    
    data class PartialSuccess(
        val plan: ExecutionPlan,
        val results: List<StepResult>,
        val failedStep: Int,
        val error: String,
        val durationMs: Long
    ) : PlanExecutionResult()
    
    data class Failure(
        val plan: ExecutionPlan,
        val error: String,
        val durationMs: Long
    ) : PlanExecutionResult()
    
    data class ChainResult(
        val plan: ExecutionPlan,
        val handoffResult: HandoffChainResult,
        val durationMs: Long
    ) : PlanExecutionResult()
}

data class StepResult(
    val step: PlanStep,
    val success: Boolean,
    val output: Map<String, Any?>,
    val error: String? = null,
    val durationMs: Long
)

/**
 * Task Planner.
 * 
 * Creates structured execution plans from user intents.
 * Uses predefined templates for known intents, LLM for complex cases.
 */
@Singleton
class TaskPlanner @Inject constructor(
    private val llmProvider: LlmProvider,
    private val handoffExecutor: HandoffExecutor
) {
    companion object {
        private const val LOG_TAG = "TaskPlanner"
    }
    
    /**
     * Create an execution plan for a user intent.
     */
    suspend fun createPlan(intent: UserIntent): ExecutionPlan {
        Logger.logInfo(LOG_TAG, "Creating plan for intent: ${intent::class.simpleName}")
        
        return when (intent) {
            is UserIntent.SystemRepair -> createSystemRepairPlan(intent)
            is UserIntent.PackageManagement -> createPackageManagementPlan(intent)
            is UserIntent.FileOperation -> createFileOperationPlan(intent)
            is UserIntent.General -> createGeneralPlan(intent)
            is UserIntent.Unknown -> createFallbackPlan(intent)
        }
    }
    
    /**
     * Create system repair plan.
     * Uses the SystemRepairChain for full repairs, or targeted skills for specific issues.
     */
    private suspend fun createSystemRepairPlan(intent: UserIntent.SystemRepair): ExecutionPlan {
        val steps = mutableListOf<PlanStep>()
        
        when (intent.repairType) {
            RepairType.FULL_SYSTEM_CHECK -> {
                // Use the full handoff chain
                return ExecutionPlan(
                    intent = intent,
                    description = "Full system repair using handoff chain",
                    steps = emptyList(),
                    chainName = SystemRepairChainFactory.getEntryPoint(),
                    estimatedDurationMs = 30_000,
                    requiresRoot = true,
                    dryRunSupported = false
                )
            }
            
            RepairType.BUSYBOX_REPAIR -> {
                steps.add(PlanStep(
                    stepNumber = 1,
                    description = "Check BusyBox binary",
                    skillName = "busybox",
                    functionName = "check_binary"
                ))
                steps.add(PlanStep(
                    stepNumber = 2,
                    description = "Run BusyBox diagnostics",
                    skillName = "busybox",
                    functionName = "diagnose",
                    parameters = mapOf("force_refresh" to true)
                ))
                steps.add(PlanStep(
                    stepNumber = 3,
                    description = "Repair symlinks if needed",
                    skillName = "busybox",
                    functionName = "repair_symlinks",
                    condition = "diagnostics.issues.contains('SYMLINKS')"
                ))
                steps.add(PlanStep(
                    stepNumber = 4,
                    description = "Repair PATH if needed",
                    skillName = "busybox",
                    functionName = "repair_path",
                    condition = "diagnostics.issues.contains('PATH')"
                ))
            }
            
            RepairType.SYMLINK_REPAIR -> {
                steps.add(PlanStep(
                    stepNumber = 1,
                    description = "Diagnose symlink issues",
                    skillName = "busybox",
                    functionName = "diagnose"
                ))
                steps.add(PlanStep(
                    stepNumber = 2,
                    description = "Repair symlinks",
                    skillName = "busybox",
                    functionName = "repair_symlinks"
                ))
            }
            
            RepairType.PATH_REPAIR -> {
                steps.add(PlanStep(
                    stepNumber = 1,
                    description = "Check current PATH",
                    skillName = "diagnostic",
                    functionName = "environment"
                ))
                steps.add(PlanStep(
                    stepNumber = 2,
                    description = "Repair PATH configuration",
                    skillName = "busybox",
                    functionName = "repair_path"
                ))
            }
            
            RepairType.MAGISK_CHECK -> {
                steps.add(PlanStep(
                    stepNumber = 1,
                    description = "Check Magisk status",
                    skillName = "busybox",
                    functionName = "magisk_check"
                ))
            }
            
            RepairType.ENVIRONMENT_CHECK -> {
                steps.add(PlanStep(
                    stepNumber = 1,
                    description = "Verify environment setup",
                    skillName = "diagnostic",
                    functionName = "verify_setup"
                ))
            }
            
            RepairType.UNKNOWN -> {
                // Full system check as fallback
                return createSystemRepairPlan(
                    intent.copy(repairType = RepairType.FULL_SYSTEM_CHECK)
                )
            }
        }
        
        return ExecutionPlan(
            intent = intent,
            description = "System repair: ${intent.repairType.name}",
            steps = steps,
            estimatedDurationMs = steps.size * 5000L,
            requiresRoot = true,
            dryRunSupported = true
        )
    }
    
    /**
     * Create package management plan.
     */
    private fun createPackageManagementPlan(intent: UserIntent.PackageManagement): ExecutionPlan {
        val steps = mutableListOf<PlanStep>()
        
        when (intent.action) {
            "install" -> {
                if (intent.packageName != null) {
                    steps.add(PlanStep(
                        stepNumber = 1,
                        description = "Install package: ${intent.packageName}",
                        skillName = "pkg",
                        functionName = "install",
                        parameters = mapOf("packages" to listOf(intent.packageName))
                    ))
                }
            }
            "remove" -> {
                if (intent.packageName != null) {
                    steps.add(PlanStep(
                        stepNumber = 1,
                        description = "Remove package: ${intent.packageName}",
                        skillName = "pkg",
                        functionName = "uninstall",
                        parameters = mapOf("packages" to listOf(intent.packageName))
                    ))
                }
            }
            "update" -> {
                steps.add(PlanStep(
                    stepNumber = 1,
                    description = "Update package database",
                    skillName = "pkg",
                    functionName = "update"
                ))
                if (intent.packageName != null) {
                    steps.add(PlanStep(
                        stepNumber = 2,
                        description = "Upgrade package: ${intent.packageName}",
                        skillName = "pkg",
                        functionName = "upgrade",
                        parameters = mapOf("packages" to listOf(intent.packageName))
                    ))
                } else {
                    steps.add(PlanStep(
                        stepNumber = 2,
                        description = "Upgrade all packages",
                        skillName = "pkg",
                        functionName = "upgrade"
                    ))
                }
            }
        }
        
        return ExecutionPlan(
            intent = intent,
            description = "Package ${intent.action}: ${intent.packageName ?: "all"}",
            steps = steps,
            estimatedDurationMs = steps.size * 10000L,
            requiresRoot = false
        )
    }
    
    /**
     * Create file operation plan.
     */
    private fun createFileOperationPlan(intent: UserIntent.FileOperation): ExecutionPlan {
        val steps = mutableListOf<PlanStep>()
        
        when (intent.action) {
            "copy" -> {
                if (intent.path != null) {
                    steps.add(PlanStep(
                        stepNumber = 1,
                        description = "Copy file: ${intent.path}",
                        skillName = "fs",
                        functionName = "copy",
                        parameters = mapOf("source" to intent.path)
                    ))
                }
            }
            "move" -> {
                if (intent.path != null) {
                    steps.add(PlanStep(
                        stepNumber = 1,
                        description = "Move file: ${intent.path}",
                        skillName = "fs",
                        functionName = "move",
                        parameters = mapOf("source" to intent.path)
                    ))
                }
            }
            "delete" -> {
                if (intent.path != null) {
                    // Backup before delete
                    steps.add(PlanStep(
                        stepNumber = 1,
                        description = "Backup file before deletion",
                        skillName = "fs",
                        functionName = "backup",
                        parameters = mapOf("path" to intent.path)
                    ))
                    steps.add(PlanStep(
                        stepNumber = 2,
                        description = "Delete file: ${intent.path}",
                        skillName = "fs",
                        functionName = "delete",
                        parameters = mapOf("path" to intent.path)
                    ))
                }
            }
            "backup" -> {
                if (intent.path != null) {
                    steps.add(PlanStep(
                        stepNumber = 1,
                        description = "Backup: ${intent.path}",
                        skillName = "fs",
                        functionName = "backup",
                        parameters = mapOf("path" to intent.path)
                    ))
                }
            }
        }
        
        return ExecutionPlan(
            intent = intent,
            description = "File ${intent.action}: ${intent.path ?: "unknown"}",
            steps = steps,
            estimatedDurationMs = steps.size * 3000L,
            requiresRoot = false,
            dryRunSupported = true
        )
    }
    
    /**
     * Create plan for general queries - may use LLM.
     */
    private suspend fun createGeneralPlan(intent: UserIntent.General): ExecutionPlan {
        // Try to use LLM to understand and create a plan
        if (llmProvider.isAvailable()) {
            val llmPlan = createPlanWithLlm(intent.rawQuery)
            if (llmPlan != null) {
                return llmPlan.copy(intent = intent)
            }
        }
        
        // Fallback: just run the query as a shell command
        return ExecutionPlan(
            intent = intent,
            description = "Execute: ${intent.rawQuery.take(50)}...",
            steps = listOf(
                PlanStep(
                    stepNumber = 1,
                    description = "Execute command",
                    skillName = "shell",
                    functionName = "exec",
                    parameters = mapOf("command" to intent.rawQuery)
                )
            ),
            estimatedDurationMs = 5000
        )
    }
    
    /**
     * Create fallback plan for unknown intents.
     */
    private fun createFallbackPlan(intent: UserIntent.Unknown): ExecutionPlan {
        return ExecutionPlan(
            intent = intent,
            description = "Unable to understand request",
            steps = emptyList(),
            estimatedDurationMs = 0
        )
    }
    
    /**
     * Use LLM to create a plan from natural language.
     */
    private suspend fun createPlanWithLlm(query: String): ExecutionPlan? {
        val systemPrompt = """You are a task planner for a Termux terminal environment.
Convert the user's request into a structured execution plan.

Available skills and functions:
- pkg: install, uninstall, update, upgrade, search, list
- fs: read, write, copy, move, delete, backup, list
- busybox: install, uninstall, diagnose, repair_symlinks, repair_path, magisk_check
- diagnostic: verify_setup, environment, disk_space
- git: clone, pull, push, status, commit
- shell: exec

Respond with ONLY a JSON object:
{
  "description": "Brief description of the plan",
  "steps": [
    {
      "stepNumber": 1,
      "description": "What this step does",
      "skill": "skill_name",
      "function": "function_name",
      "parameters": {}
    }
  ],
  "estimatedDurationMs": 5000,
  "requiresRoot": false
}"""

        val result = llmProvider.complete(
            prompt = "Create an execution plan for: \"$query\"",
            systemPrompt = systemPrompt,
            options = LlmOptions(maxTokens = 500, temperature = 0.2f)
        )
        
        return result.fold(
            onSuccess = { response ->
                try {
                    parseLlmPlan(response.content)
                } catch (e: Exception) {
                    Logger.logWarn(LOG_TAG, "Failed to parse LLM plan: ${e.message}")
                    null
                }
            },
            onFailure = { error ->
                Logger.logWarn(LOG_TAG, "LLM planning failed: ${error.message}")
                null
            }
        )
    }
    
    /**
     * Parse LLM-generated plan JSON.
     */
    private fun parseLlmPlan(response: String): ExecutionPlan? {
        // Find JSON in response
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd < 0) return null
        
        val jsonStr = response.substring(jsonStart, jsonEnd + 1)
        val json = JSONObject(jsonStr)
        
        val steps = mutableListOf<PlanStep>()
        val stepsArray = json.optJSONArray("steps") ?: return null
        
        for (i in 0 until stepsArray.length()) {
            val stepJson = stepsArray.getJSONObject(i)
            val params = mutableMapOf<String, Any?>()
            
            val paramsJson = stepJson.optJSONObject("parameters")
            if (paramsJson != null) {
                for (key in paramsJson.keys()) {
                    params[key] = paramsJson.get(key)
                }
            }
            
            steps.add(PlanStep(
                stepNumber = stepJson.optInt("stepNumber", i + 1),
                description = stepJson.optString("description", ""),
                skillName = stepJson.optString("skill", ""),
                functionName = stepJson.optString("function", ""),
                parameters = params
            ))
        }
        
        return ExecutionPlan(
            intent = UserIntent.Unknown(rawQuery = ""),  // Will be replaced by caller
            description = json.optString("description", "LLM-generated plan"),
            steps = steps,
            estimatedDurationMs = json.optLong("estimatedDurationMs", 5000),
            requiresRoot = json.optBoolean("requiresRoot", false)
        )
    }
    
    /**
     * Execute a plan using the handoff chain or step-by-step execution.
     */
    suspend fun executePlan(
        plan: ExecutionPlan,
        agentName: String,
        dryRun: Boolean = false,
        onProgress: ((Int, String) -> Unit)? = null
    ): PlanExecutionResult {
        val startTime = System.currentTimeMillis()
        
        Logger.logInfo(LOG_TAG, "Executing plan: ${plan.description}")
        
        // If plan uses a handoff chain, execute through HandoffExecutor
        if (plan.chainName != null) {
            val context = AgentContext(
                taskDescription = plan.description,
                metadata = mutableMapOf(
                    "plan_id" to plan.id,
                    "dry_run" to dryRun
                )
            )
            
            val result = handoffExecutor.execute(plan.chainName, context)
            val duration = System.currentTimeMillis() - startTime
            
            return PlanExecutionResult.ChainResult(
                plan = plan,
                handoffResult = result,
                durationMs = duration
            )
        }
        
        // Step-by-step execution
        if (plan.steps.isEmpty()) {
            return PlanExecutionResult.Failure(
                plan = plan,
                error = "No steps to execute",
                durationMs = System.currentTimeMillis() - startTime
            )
        }
        
        val results = mutableListOf<StepResult>()
        
        for ((index, step) in plan.steps.withIndex()) {
            onProgress?.invoke(index + 1, step.description)
            Logger.logDebug(LOG_TAG, "Executing step ${step.stepNumber}: ${step.description}")
            
            val stepStart = System.currentTimeMillis()
            
            // TODO: Actually execute the step through SkillExecutor
            // For now, just simulate success
            val stepResult = StepResult(
                step = step,
                success = true,
                output = mapOf("status" to "completed"),
                durationMs = System.currentTimeMillis() - stepStart
            )
            
            results.add(stepResult)
            
            if (!stepResult.success && step.onFailure == FailureAction.ABORT) {
                return PlanExecutionResult.PartialSuccess(
                    plan = plan,
                    results = results,
                    failedStep = step.stepNumber,
                    error = stepResult.error ?: "Step failed",
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        }
        
        return PlanExecutionResult.Success(
            plan = plan,
            results = results,
            summary = "Completed ${results.size} steps successfully",
            durationMs = System.currentTimeMillis() - startTime
        )
    }
}
