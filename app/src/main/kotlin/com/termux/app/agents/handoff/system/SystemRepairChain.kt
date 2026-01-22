package com.termux.app.agents.handoff.system

import com.termux.app.agents.daemon.AgentDaemon
import com.termux.app.agents.handoff.AgentContext
import com.termux.app.agents.handoff.HandoffCapable
import com.termux.app.agents.handoff.HandoffResult
import com.termux.app.agents.models.TaskResult
import com.termux.shared.logger.Logger

/**
 * System Repair Handoff Chain.
 * 
 * A coordinated chain of agents that perform system diagnostics and repairs:
 * 
 * Flow: SystemCheck → BusyBoxDiagnostics → BusyBoxRepair → MagiskCheck → Finalize
 * 
 * Features:
 * - Comprehensive system health verification
 * - BusyBox Modern detection and repair
 * - Magisk integration checks
 * - Automatic issue resolution
 * - Detailed repair logging
 */

/**
 * Structured log event types for system repair chain.
 */
object SystemRepairEvents {
    const val CHAIN_START = "busybox.handoff.chain_start"
    const val CHAIN_END = "busybox.handoff.chain_end"
    const val TRANSITION = "busybox.handoff.transition"
    const val CHECK_PASS = "busybox.handoff.check_pass"
    const val CHECK_FAIL = "busybox.handoff.check_fail"
    const val REPAIR_START = "busybox.handoff.repair_start"
    const val REPAIR_END = "busybox.handoff.repair_end"
    const val ERROR = "busybox.handoff.error"
}

/**
 * System Check Agent - Entry point for system repair chain.
 * 
 * Performs initial system health checks:
 * - Environment verification
 * - Essential tools check
 * - Path validation
 * - Storage space check
 * 
 * Hands off to: BusyBoxDiagnosticsAgent
 */
class SystemCheckAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "SystemCheckAgent"
    }
    
    override val handoffName: String = "system_check_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "busybox_diagnostics_agent",
        "finalize_agent"
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        val keywords = listOf("system", "check", "repair", "diagnose", "fix", "busybox")
        return keywords.any { context.taskDescription.contains(it, ignoreCase = true) }
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "${SystemRepairEvents.CHAIN_START}: Starting system repair chain")
        
        context.set("chain_start_time", System.currentTimeMillis())
        context.set("chain_type", "system_repair")
        
        // Run system diagnostics
        val result = daemon.runTask(
            agentName = "system_check_agent",
            skillName = "diagnostic",
            function = "verify_setup",
            params = emptyMap()
        )
        
        when (result) {
            is TaskResult.Success -> {
                val allPassed = result.data["all_passed"] as? Boolean ?: false
                val checks = result.data["checks"] as? Map<*, *> ?: emptyMap<String, Boolean>()
                
                context.set("system_checks", checks)
                context.set("system_check_passed", allPassed)
                
                Logger.logInfo(LOG_TAG, "${SystemRepairEvents.CHECK_PASS}: System checks completed, passed=$allPassed")
                
                // Always proceed to BusyBox diagnostics
                return HandoffResult.handoffTo(
                    fromAgent = handoffName,
                    toAgent = "busybox_diagnostics_agent",
                    reason = if (allPassed) 
                        "System checks passed, proceeding to BusyBox diagnostics" 
                    else 
                        "Some system checks failed, checking BusyBox status",
                    contextUpdates = mapOf(
                        "system_check_result" to result.data,
                        "system_issues_found" to !allPassed
                    )
                )
            }
            is TaskResult.Failure -> {
                Logger.logWarn(LOG_TAG, "${SystemRepairEvents.CHECK_FAIL}: System check failed: ${result.error.message}")
                
                context.addError(handoffName, result.error.message, recoverable = true)
                
                // Still try BusyBox diagnostics
                return HandoffResult.handoffTo(
                    fromAgent = handoffName,
                    toAgent = "busybox_diagnostics_agent",
                    reason = "System check encountered errors, attempting BusyBox diagnostics",
                    contextUpdates = mapOf(
                        "system_check_error" to result.error.message
                    )
                )
            }
            else -> {
                return HandoffResult.fail(
                    agentName = handoffName,
                    error = "Unexpected result from system check",
                    recoverable = false
                )
            }
        }
    }
}

/**
 * BusyBox Diagnostics Agent - Checks BusyBox installation status.
 * 
 * Performs:
 * - Binary detection
 * - Version verification
 * - Symlink validation
 * - PATH check
 * 
 * Hands off to: BusyBoxRepairAgent (if issues) or MagiskCheckAgent (if ok)
 */
class BusyBoxDiagnosticsAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "BusyBoxDiagnosticsAgent"
    }
    
    override val handoffName: String = "busybox_diagnostics_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "busybox_repair_agent",
        "magisk_check_agent"
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.has("system_check_result") || 
               context.taskDescription.contains("busybox", ignoreCase = true)
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "${SystemRepairEvents.TRANSITION}: Running BusyBox diagnostics")
        
        val result = daemon.runTask(
            agentName = "busybox_diagnostics_agent",
            skillName = "busybox",
            function = "diagnose",
            params = mapOf("force_refresh" to true)
        )
        
        when (result) {
            is TaskResult.Success -> {
                val installed = result.data["installed"] as? Boolean ?: false
                val operational = result.data["operational"] as? Boolean ?: false
                val issues = result.data["issues"] as? List<*> ?: emptyList<Any>()
                
                context.set("busybox_installed", installed)
                context.set("busybox_operational", operational)
                context.set("busybox_diagnostics", result.data)
                
                // Determine next step
                if (!installed) {
                    Logger.logWarn(LOG_TAG, "${SystemRepairEvents.CHECK_FAIL}: BusyBox not installed")
                    
                    // Can't repair if not installed
                    return HandoffResult.handoffTo(
                        fromAgent = handoffName,
                        toAgent = "magisk_check_agent",
                        reason = "BusyBox not installed - checking Magisk for potential module installation",
                        contextUpdates = mapOf(
                            "busybox_needs_install" to true,
                            "busybox_diagnostics" to result.data
                        )
                    )
                }
                
                if (!operational || issues.isNotEmpty()) {
                    Logger.logInfo(LOG_TAG, "${SystemRepairEvents.REPAIR_START}: BusyBox needs repair (${issues.size} issues)")
                    
                    return HandoffResult.handoffTo(
                        fromAgent = handoffName,
                        toAgent = "busybox_repair_agent",
                        reason = "BusyBox installed but has ${issues.size} issue(s) requiring repair",
                        contextUpdates = mapOf(
                            "busybox_issues" to issues,
                            "busybox_diagnostics" to result.data
                        )
                    )
                }
                
                // All good
                Logger.logInfo(LOG_TAG, "${SystemRepairEvents.CHECK_PASS}: BusyBox fully operational")
                
                return HandoffResult.handoffTo(
                    fromAgent = handoffName,
                    toAgent = "magisk_check_agent",
                    reason = "BusyBox fully operational, proceeding to Magisk check",
                    contextUpdates = mapOf(
                        "busybox_diagnostics" to result.data
                    )
                )
            }
            is TaskResult.Failure -> {
                Logger.logError(LOG_TAG, "${SystemRepairEvents.ERROR}: BusyBox diagnostics failed: ${result.error.message}")
                
                // This could mean BusyBox skill failed, not that BusyBox isn't installed
                return HandoffResult.handoffTo(
                    fromAgent = handoffName,
                    toAgent = "magisk_check_agent",
                    reason = "BusyBox diagnostics encountered error, checking Magisk",
                    contextUpdates = mapOf(
                        "busybox_error" to result.error.message
                    )
                )
            }
            else -> {
                return HandoffResult.fail(handoffName, "Unexpected result")
            }
        }
    }
}

/**
 * BusyBox Repair Agent - Repairs BusyBox installation issues.
 * 
 * Performs:
 * - Symlink repair
 * - PATH repair
 * - Permission fixes
 * 
 * Hands off to: MagiskCheckAgent
 */
class BusyBoxRepairAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "BusyBoxRepairAgent"
    }
    
    override val handoffName: String = "busybox_repair_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "magisk_check_agent",
        "busybox_diagnostics_agent"  // For re-verification
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.has("busybox_issues") || 
               (context.get<Boolean>("busybox_installed") == true && 
                context.get<Boolean>("busybox_operational") != true)
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "${SystemRepairEvents.REPAIR_START}: Starting BusyBox repairs")
        
        val issues = context.get<List<*>>("busybox_issues") ?: emptyList<Any>()
        val repairResults = mutableMapOf<String, Any>()
        var repairsAttempted = 0
        var repairsSucceeded = 0
        
        // Determine what repairs are needed based on issues
        val needsSymlinkRepair = issues.any { 
            (it as? Map<*, *>)?.get("code")?.toString() in listOf("SYMLINKS_BROKEN", "SYMLINKS_MISSING")
        }
        val needsPathRepair = issues.any {
            (it as? Map<*, *>)?.get("code")?.toString() == "PATH_MISSING"
        }
        
        // Repair symlinks if needed
        if (needsSymlinkRepair || issues.isEmpty()) {
            repairsAttempted++
            val symlinkResult = daemon.runTask(
                agentName = "busybox_repair_agent",
                skillName = "busybox",
                function = "repair_symlinks",
                params = emptyMap()
            )
            
            when (symlinkResult) {
                is TaskResult.Success -> {
                    repairsSucceeded++
                    repairResults["symlink_repair"] = symlinkResult.data
                    Logger.logInfo(LOG_TAG, "Symlink repair succeeded")
                }
                is TaskResult.Failure -> {
                    repairResults["symlink_repair_error"] = symlinkResult.error.message
                    Logger.logWarn(LOG_TAG, "Symlink repair failed: ${symlinkResult.error.message}")
                }
                else -> {}
            }
        }
        
        // Repair PATH if needed
        if (needsPathRepair) {
            repairsAttempted++
            val pathResult = daemon.runTask(
                agentName = "busybox_repair_agent",
                skillName = "busybox",
                function = "repair_path",
                params = emptyMap()
            )
            
            when (pathResult) {
                is TaskResult.Success -> {
                    repairsSucceeded++
                    repairResults["path_repair"] = pathResult.data
                    Logger.logInfo(LOG_TAG, "PATH repair succeeded")
                }
                is TaskResult.Failure -> {
                    repairResults["path_repair_error"] = pathResult.error.message
                    Logger.logWarn(LOG_TAG, "PATH repair failed: ${pathResult.error.message}")
                }
                else -> {}
            }
        }
        
        context.set("repair_results", repairResults)
        context.set("repairs_attempted", repairsAttempted)
        context.set("repairs_succeeded", repairsSucceeded)
        
        Logger.logInfo(LOG_TAG, "${SystemRepairEvents.REPAIR_END}: Repairs complete ($repairsSucceeded/$repairsAttempted succeeded)")
        
        // Hand off to Magisk check
        return HandoffResult.handoffTo(
            fromAgent = handoffName,
            toAgent = "magisk_check_agent",
            reason = "Repairs complete ($repairsSucceeded/$repairsAttempted), proceeding to Magisk check",
            contextUpdates = mapOf(
                "repair_results" to repairResults,
                "repairs_attempted" to repairsAttempted,
                "repairs_succeeded" to repairsSucceeded
            )
        )
    }
}

/**
 * Magisk Check Agent - Verifies Magisk status and BusyBox module.
 * 
 * Performs:
 * - Magisk installation check
 * - BusyBox module detection
 * - Module status verification
 * 
 * Hands off to: FinalizeAgent
 */
class MagiskCheckAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "MagiskCheckAgent"
    }
    
    override val handoffName: String = "magisk_check_agent"
    
    override fun getHandoffTargets(): List<String> = listOf("finalize_agent")
    
    override fun canHandle(context: AgentContext): Boolean {
        // Can handle if we've done BusyBox diagnostics or repairs
        return context.has("busybox_diagnostics") || context.has("repair_results")
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "${SystemRepairEvents.TRANSITION}: Checking Magisk status")
        
        val result = daemon.runTask(
            agentName = "magisk_check_agent",
            skillName = "busybox",
            function = "magisk_check",
            params = emptyMap()
        )
        
        when (result) {
            is TaskResult.Success -> {
                val installed = result.data["installed"] as? Boolean ?: false
                val moduleInstalled = result.data["busybox_module_installed"] as? Boolean ?: false
                val moduleEnabled = result.data["busybox_module_enabled"] as? Boolean ?: false
                
                context.set("magisk_installed", installed)
                context.set("magisk_busybox_module", moduleInstalled)
                context.set("magisk_status", result.data)
                
                val statusMessage = when {
                    !installed -> "Magisk not installed"
                    !moduleInstalled -> "Magisk installed, but BusyBox module not found"
                    !moduleEnabled -> "BusyBox Magisk module installed but disabled"
                    else -> "Magisk and BusyBox module fully operational"
                }
                
                Logger.logInfo(LOG_TAG, "${SystemRepairEvents.CHECK_PASS}: $statusMessage")
                
                return HandoffResult.handoffTo(
                    fromAgent = handoffName,
                    toAgent = "finalize_agent",
                    reason = statusMessage,
                    contextUpdates = mapOf(
                        "magisk_status" to result.data
                    )
                )
            }
            is TaskResult.Failure -> {
                Logger.logWarn(LOG_TAG, "${SystemRepairEvents.CHECK_FAIL}: Magisk check failed: ${result.error.message}")
                
                // Continue to finalize anyway
                return HandoffResult.handoffTo(
                    fromAgent = handoffName,
                    toAgent = "finalize_agent",
                    reason = "Magisk check failed, proceeding to finalize",
                    contextUpdates = mapOf(
                        "magisk_error" to result.error.message
                    )
                )
            }
            else -> {
                return HandoffResult.handoffTo(
                    fromAgent = handoffName,
                    toAgent = "finalize_agent",
                    reason = "Magisk check returned unexpected result"
                )
            }
        }
    }
}

/**
 * Finalize Agent - Generates final report and completes the chain.
 * 
 * Performs:
 * - Compilation of all check results
 * - Summary generation
 * - Recommendations
 * 
 * Terminal agent - no further handoffs.
 */
class FinalizeAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "FinalizeAgent"
    }
    
    override val handoffName: String = "finalize_agent"
    
    override fun getHandoffTargets(): List<String> = emptyList()  // Terminal agent
    
    override fun canHandle(context: AgentContext): Boolean = true  // Always can handle
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "${SystemRepairEvents.TRANSITION}: Finalizing system repair chain")
        
        val chainStartTime = context.get<Long>("chain_start_time") ?: System.currentTimeMillis()
        val chainDuration = System.currentTimeMillis() - chainStartTime
        
        // Compile results
        val systemCheckPassed = context.get<Boolean>("system_check_passed") ?: false
        val busyboxInstalled = context.get<Boolean>("busybox_installed") ?: false
        val busyboxOperational = context.get<Boolean>("busybox_operational") ?: false
        val magiskInstalled = context.get<Boolean>("magisk_installed") ?: false
        val repairsAttempted = context.get<Int>("repairs_attempted") ?: 0
        val repairsSucceeded = context.get<Int>("repairs_succeeded") ?: 0
        
        // Generate recommendations
        val recommendations = mutableListOf<String>()
        
        if (!busyboxInstalled) {
            recommendations.add("Install BusyBox Modern for enhanced system tools")
            if (magiskInstalled) {
                recommendations.add("Consider installing BusyBox Magisk module for systemless integration")
            }
        } else if (!busyboxOperational) {
            recommendations.add("Run 'busybox.install' to set up BusyBox symlinks")
        }
        
        if (!systemCheckPassed) {
            recommendations.add("Review system check results and fix reported issues")
        }
        
        // Determine overall status
        val overallSuccess = systemCheckPassed || (busyboxInstalled && busyboxOperational)
        val overallStatus = when {
            systemCheckPassed && busyboxOperational -> "HEALTHY"
            busyboxInstalled && !busyboxOperational -> "DEGRADED"
            repairsSucceeded > 0 -> "REPAIRED"
            !busyboxInstalled -> "INCOMPLETE"
            else -> "NEEDS_ATTENTION"
        }
        
        // Build final report
        val report = mapOf(
            "status" to overallStatus,
            "success" to overallSuccess,
            "duration_ms" to chainDuration,
            "checks" to mapOf(
                "system_check_passed" to systemCheckPassed,
                "busybox_installed" to busyboxInstalled,
                "busybox_operational" to busyboxOperational,
                "magisk_installed" to magiskInstalled
            ),
            "repairs" to mapOf(
                "attempted" to repairsAttempted,
                "succeeded" to repairsSucceeded
            ),
            "recommendations" to recommendations,
            "chain_depth" to context.depth,
            "errors" to context.errorHistory.map { 
                mapOf("agent" to it.agentName, "error" to it.error)
            }
        )
        
        Logger.logInfo(LOG_TAG, "${SystemRepairEvents.CHAIN_END}: Chain complete - status=$overallStatus, duration=${chainDuration}ms")
        
        return HandoffResult.complete(
            agentName = handoffName,
            result = report,
            message = "System repair chain complete: $overallStatus"
        )
    }
}

/**
 * Factory for creating all system repair chain agents.
 */
object SystemRepairChainFactory {
    
    /**
     * Create all agents for the system repair chain.
     */
    fun createAll(daemon: AgentDaemon): List<HandoffCapable> {
        return listOf(
            SystemCheckAgent(daemon),
            BusyBoxDiagnosticsAgent(daemon),
            BusyBoxRepairAgent(daemon),
            MagiskCheckAgent(daemon),
            FinalizeAgent(daemon)
        )
    }
    
    /**
     * Get the entry point agent name for this chain.
     */
    fun getEntryPoint(): String = "system_check_agent"
    
    /**
     * Get all agent names in this chain.
     */
    fun getAgentNames(): List<String> = listOf(
        "system_check_agent",
        "busybox_diagnostics_agent",
        "busybox_repair_agent",
        "magisk_check_agent",
        "finalize_agent"
    )
}
