package com.termux.app.agents.handoff

import com.termux.app.agents.daemon.AgentDaemon
import com.termux.app.agents.models.TaskResult
import com.termux.shared.logger.Logger

/**
 * Package management handoff chain.
 * 
 * Chain: PackageAgent -> InstallAgent -> VerifyAgent -> CompleteAgent
 * 
 * Used for managing package installations with verification.
 */

/**
 * Package Agent - Entry point for package operations
 * 
 * Analyzes package requests and routes to appropriate handlers.
 */
class PackageHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "PackageHandoffAgent"
    }
    
    override val handoffName: String = "package_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "install_agent",
        "remove_agent",
        "update_agent"
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        val keywords = listOf("package", "pkg", "apt", "install", "remove", "update")
        return keywords.any { context.taskDescription.contains(it, ignoreCase = true) }
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "Processing package request: ${context.taskDescription}")
        
        val task = context.taskDescription.lowercase()
        
        // Determine operation type
        val operation = when {
            task.contains("install") -> "install"
            task.contains("remove") || task.contains("uninstall") -> "remove"
            task.contains("update") || task.contains("upgrade") -> "update"
            else -> "install"  // Default
        }
        
        // Extract package name(s)
        val packages = extractPackageNames(context.taskDescription)
        
        if (packages.isEmpty()) {
            return HandoffResult.needInput(
                agentName = handoffName,
                prompt = "Which package(s) would you like to $operation?",
                inputType = "text"
            )
        }
        
        // Store packages in context
        context.set("operation", operation)
        context.set("packages", packages)
        context.set("package_count", packages.size)
        
        // Hand off to appropriate agent
        val targetAgent = "${operation}_agent"
        return HandoffResult.handoffTo(
            fromAgent = handoffName,
            toAgent = targetAgent,
            reason = "Processing $operation for ${packages.size} package(s)",
            contextUpdates = mapOf(
                "packages" to packages,
                "operation" to operation
            )
        )
    }
    
    private fun extractPackageNames(input: String): List<String> {
        // Simple extraction - finds words after install/remove/update
        val pattern = Regex("""(?:install|remove|update)\s+(.+)""", RegexOption.IGNORE_CASE)
        val match = pattern.find(input)
        
        return match?.groupValues?.get(1)
            ?.split(Regex("""\s+"""))
            ?.filter { it.isNotBlank() && !it.startsWith("-") }
            ?: emptyList()
    }
}

/**
 * Install Agent - Handles package installation
 */
class InstallHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "InstallHandoffAgent"
    }
    
    override val handoffName: String = "install_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "verify_agent",
        "heal_agent"
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.get<String>("operation") == "install" ||
               context.taskDescription.contains("install", ignoreCase = true)
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val packages = context.get<List<String>>("packages") ?: emptyList()
        
        if (packages.isEmpty()) {
            return HandoffResult.fail(
                agentName = handoffName,
                error = "No packages specified for installation"
            )
        }
        
        Logger.logInfo(LOG_TAG, "Installing packages: ${packages.joinToString()}")
        
        val results = mutableMapOf<String, Any>()
        val failures = mutableListOf<String>()
        
        for (pkg in packages) {
            val result = daemon.runTask(
                agentName = "install_agent",
                skillName = "package",
                function = "install",
                params = mapOf("package" to pkg)
            )
            
            when (result) {
                is TaskResult.Success -> {
                    results[pkg] = "installed"
                }
                is TaskResult.Failure -> {
                    results[pkg] = "failed: ${result.error.message}"
                    failures.add(pkg)
                }
                else -> {
                    results[pkg] = "unknown"
                }
            }
        }
        
        context.set("install_results", results)
        
        if (failures.isNotEmpty()) {
            context.set("failed_packages", failures)
            return HandoffResult.handoffTo(
                fromAgent = handoffName,
                toAgent = "heal_agent",
                reason = "Failed to install ${failures.size} package(s)",
                contextUpdates = mapOf("failed_packages" to failures)
            )
        }
        
        // All installed, verify
        return HandoffResult.handoffTo(
            fromAgent = handoffName,
            toAgent = "verify_agent",
            reason = "Installation complete, verifying packages",
            contextUpdates = mapOf("install_results" to results)
        )
    }
}

/**
 * Verify Agent - Verifies package installation
 */
class VerifyHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "VerifyHandoffAgent"
    }
    
    override val handoffName: String = "verify_agent"
    
    override fun getHandoffTargets(): List<String> = listOf("heal_agent")
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.has("install_results") || context.has("remove_results")
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val packages = context.get<List<String>>("packages") ?: emptyList()
        
        Logger.logInfo(LOG_TAG, "Verifying ${packages.size} packages")
        
        val verified = mutableListOf<String>()
        val failed = mutableListOf<String>()
        
        for (pkg in packages) {
            val result = daemon.runTask(
                agentName = "verify_agent",
                skillName = "package",
                function = "check",
                params = mapOf("package" to pkg)
            )
            
            when (result) {
                is TaskResult.Success -> {
                    val isInstalled = result.data["installed"] as? Boolean ?: false
                    if (isInstalled) {
                        verified.add(pkg)
                    } else {
                        failed.add(pkg)
                    }
                }
                else -> failed.add(pkg)
            }
        }
        
        val report = mapOf(
            "verified" to verified,
            "failed" to failed,
            "verification_time" to System.currentTimeMillis(),
            "total_packages" to packages.size,
            "success_rate" to if (packages.isNotEmpty()) 
                (verified.size.toFloat() / packages.size * 100) else 0f
        )
        
        return if (failed.isEmpty()) {
            HandoffResult.complete(
                agentName = handoffName,
                result = report,
                message = "All ${verified.size} packages verified successfully"
            )
        } else {
            HandoffResult.fail(
                agentName = handoffName,
                error = "Verification failed for: ${failed.joinToString()}",
                recoverable = true,
                suggestedAgent = "heal_agent"
            )
        }
    }
}

/**
 * Remove Agent - Handles package removal
 */
class RemoveHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "RemoveHandoffAgent"
    }
    
    override val handoffName: String = "remove_agent"
    
    override fun getHandoffTargets(): List<String> = listOf("verify_agent")
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.get<String>("operation") == "remove"
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val packages = context.get<List<String>>("packages") ?: emptyList()
        
        if (packages.isEmpty()) {
            return HandoffResult.fail(
                agentName = handoffName,
                error = "No packages specified for removal"
            )
        }
        
        Logger.logInfo(LOG_TAG, "Removing packages: ${packages.joinToString()}")
        
        val results = mutableMapOf<String, Any>()
        
        for (pkg in packages) {
            val result = daemon.runTask(
                agentName = "remove_agent",
                skillName = "package",
                function = "remove",
                params = mapOf("package" to pkg)
            )
            
            results[pkg] = when (result) {
                is TaskResult.Success -> "removed"
                is TaskResult.Failure -> "failed: ${result.error.message}"
                else -> "unknown"
            }
        }
        
        context.set("remove_results", results)
        
        return HandoffResult.complete(
            agentName = handoffName,
            result = mapOf("removed_packages" to results),
            message = "Removed ${packages.size} package(s)"
        )
    }
}

/**
 * Update Agent - Handles package updates
 */
class UpdateHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "UpdateHandoffAgent"
    }
    
    override val handoffName: String = "update_agent"
    
    override fun getHandoffTargets(): List<String> = listOf("verify_agent")
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.get<String>("operation") == "update"
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val packages = context.get<List<String>>("packages") ?: emptyList()
        
        Logger.logInfo(LOG_TAG, "Updating packages: ${if (packages.isEmpty()) "all" else packages.joinToString()}")
        
        // If no specific packages, update all
        val result = if (packages.isEmpty()) {
            daemon.runTask(
                agentName = "update_agent",
                skillName = "package",
                function = "update_all",
                params = emptyMap()
            )
        } else {
            daemon.runTask(
                agentName = "update_agent",
                skillName = "package",
                function = "update",
                params = mapOf("packages" to packages)
            )
        }
        
        return when (result) {
            is TaskResult.Success -> {
                HandoffResult.complete(
                    agentName = handoffName,
                    result = result.data,
                    message = "Update completed successfully"
                )
            }
            is TaskResult.Failure -> {
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Update failed: ${result.error.message}",
                    recoverable = true
                )
            }
            else -> {
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Unexpected result",
                    recoverable = false
                )
            }
        }
    }
}
