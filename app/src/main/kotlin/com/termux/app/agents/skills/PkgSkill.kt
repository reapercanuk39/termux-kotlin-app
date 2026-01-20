package com.termux.app.agents.skills

import com.termux.app.agents.models.Capability

/**
 * Package management skill for Termux.
 * Provides install, remove, update, search operations.
 */
class PkgSkill : BaseSkill() {
    override val name = "pkg"
    override val description = "Package manager skill for Termux"
    override val provides = listOf(
        "install_package",
        "remove_package",
        "update_packages",
        "upgrade_packages",
        "search_packages",
        "list_installed",
        "get_package_info",
        "clean_cache"
    )
    override val requiredCapabilities = setOf(
        Capability.Exec.Pkg,
        Capability.Filesystem.Read,
        Capability.Filesystem.Write
    )
    
    override suspend fun executeFunction(
        function: String,
        params: Map<String, Any?>
    ): SkillResult {
        return when (function) {
            "install_package" -> installPackage(params["package"] as? String ?: "")
            "remove_package" -> removePackage(params["package"] as? String ?: "")
            "update_packages" -> updatePackages()
            "upgrade_packages" -> upgradePackages()
            "search_packages" -> searchPackages(params["query"] as? String ?: "")
            "list_installed" -> listInstalled()
            "get_package_info" -> getPackageInfo(params["package"] as? String ?: "")
            "clean_cache" -> cleanCache()
            else -> SkillResult(success = false, error = "Unknown function: $function")
        }
    }
    
    private suspend fun installPackage(packageName: String): SkillResult {
        if (packageName.isBlank()) {
            return SkillResult(success = false, error = "Package name required")
        }
        
        log("Installing package: $packageName")
        val result = runCommand(listOf("pkg", "install", "-y", packageName), timeout = 300_000L)
        
        log("Install ${if (result.isSuccess) "succeeded" else "failed"}")
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "package" to packageName,
                "installed" to result.isSuccess,
                "output" to result.stdout
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    private suspend fun removePackage(packageName: String): SkillResult {
        if (packageName.isBlank()) {
            return SkillResult(success = false, error = "Package name required")
        }
        
        log("Removing package: $packageName")
        val result = runCommand(listOf("pkg", "uninstall", "-y", packageName))
        
        log("Remove ${if (result.isSuccess) "succeeded" else "failed"}")
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "package" to packageName,
                "removed" to result.isSuccess,
                "output" to result.stdout
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    private suspend fun updatePackages(): SkillResult {
        log("Updating package lists")
        val result = runCommand(listOf("pkg", "update", "-y"), timeout = 600_000L)
        
        log("Update ${if (result.isSuccess) "succeeded" else "failed"}")
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "updated" to result.isSuccess,
                "output" to result.stdout
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    private suspend fun upgradePackages(): SkillResult {
        log("Upgrading all packages")
        val result = runCommand(listOf("pkg", "upgrade", "-y"), timeout = 1800_000L)
        
        log("Upgrade ${if (result.isSuccess) "succeeded" else "failed"}")
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "upgraded" to result.isSuccess,
                "output" to result.stdout
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    private suspend fun searchPackages(query: String): SkillResult {
        if (query.isBlank()) {
            return SkillResult(success = false, error = "Search query required")
        }
        
        log("Searching for: $query")
        val result = runCommand(listOf("pkg", "search", query))
        
        val packages = mutableListOf<Map<String, String>>()
        if (result.stdout.isNotBlank()) {
            result.stdout.trim().split("\n").forEach { line ->
                if (line.contains("/")) {
                    val parts = line.split("/")
                    if (parts.size >= 2) {
                        val pkgInfo = parts[1].split(" ", limit = 2)
                        packages.add(mapOf(
                            "name" to pkgInfo[0],
                            "description" to (pkgInfo.getOrNull(1) ?: "")
                        ))
                    }
                }
            }
        }
        
        log("Found ${packages.size} packages")
        
        return SkillResult(
            success = true,
            data = mapOf(
                "query" to query,
                "count" to packages.size,
                "packages" to packages
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun listInstalled(): SkillResult {
        log("Listing installed packages")
        val result = runCommand(listOf("dpkg", "-l"))
        
        val packages = mutableListOf<Map<String, String>>()
        if (result.stdout.isNotBlank()) {
            result.stdout.trim().split("\n").forEach { line ->
                if (line.startsWith("ii")) {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        packages.add(mapOf(
                            "name" to parts[1],
                            "version" to parts[2]
                        ))
                    }
                }
            }
        }
        
        log("Found ${packages.size} installed packages")
        
        return SkillResult(
            success = true,
            data = mapOf(
                "count" to packages.size,
                "packages" to packages
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun getPackageInfo(packageName: String): SkillResult {
        if (packageName.isBlank()) {
            return SkillResult(success = false, error = "Package name required")
        }
        
        log("Getting info for: $packageName")
        val result = runCommand(listOf("apt-cache", "show", packageName))
        
        val info = mutableMapOf<String, String>()
        if (result.stdout.isNotBlank()) {
            var currentKey: String? = null
            result.stdout.trim().split("\n").forEach { line ->
                if (line.contains(": ") && !line.startsWith(" ")) {
                    val (key, value) = line.split(": ", limit = 2)
                    info[key.lowercase()] = value
                    currentKey = key.lowercase()
                } else if (line.startsWith(" ") && currentKey != null) {
                    info[currentKey!!] = (info[currentKey!!] ?: "") + "\n" + line
                }
            }
        }
        
        return SkillResult(
            success = info.isNotEmpty(),
            data = mapOf(
                "package" to packageName,
                "found" to info.isNotEmpty(),
                "info" to info
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun cleanCache(): SkillResult {
        log("Cleaning package cache")
        val result = runCommand(listOf("pkg", "clean"))
        
        log("Clean ${if (result.isSuccess) "succeeded" else "failed"}")
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "cleaned" to result.isSuccess,
                "output" to result.stdout
            ),
            logs = context.getLogs()
        )
    }
    
    override suspend fun selfTest(context: SkillContext): SkillResult {
        this.context = context
        log("Running pkg skill self-test")
        
        val result = runCommand(listOf("dpkg", "--version"))
        val dpkgOk = result.isSuccess
        
        log("dpkg available: $dpkgOk")
        
        return SkillResult(
            success = dpkgOk,
            data = mapOf(
                "dpkg_available" to dpkgOk,
                "dpkg_version" to if (dpkgOk) result.stdout.split("\n").firstOrNull() else null
            ),
            logs = context.getLogs()
        )
    }
}
