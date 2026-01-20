package com.termux.app.agents.skills

import com.termux.app.agents.models.Capability
import java.io.File

/**
 * Diagnostic skill for system health checks.
 * Provides environment verification and repair suggestions.
 */
class DiagnosticSkill : BaseSkill() {
    override val name = "diagnostic"
    override val description = "System diagnostics and health checks"
    override val provides = listOf(
        "check_environment",
        "check_paths",
        "check_tools",
        "check_packages",
        "check_storage",
        "check_network",
        "get_system_info",
        "suggest_fixes",
        "verify_setup"
    )
    override val requiredCapabilities = setOf(
        Capability.Filesystem.Read,
        Capability.System.Info
    )
    
    // Expected paths for Termux
    private val requiredPaths = listOf(
        "\$PREFIX/bin",
        "\$PREFIX/lib",
        "\$PREFIX/etc",
        "\$HOME"
    )
    
    // Essential tools for Termux
    private val essentialTools = listOf(
        "sh", "bash", "ls", "cat", "echo", "mkdir", "rm", "cp", "mv",
        "chmod", "chown", "grep", "sed", "awk", "find", "which"
    )
    
    // Common optional tools
    private val optionalTools = listOf(
        "git", "python", "python3", "node", "vim", "nano", "curl", "wget",
        "pkg", "apt", "dpkg", "tar", "gzip", "unzip"
    )
    
    override suspend fun executeFunction(
        function: String,
        params: Map<String, Any?>
    ): SkillResult {
        return when (function) {
            "check_environment" -> checkEnvironment()
            "check_paths" -> checkPaths()
            "check_tools" -> checkTools(params["tools"] as? List<*>)
            "check_packages" -> checkPackages(params["packages"] as? List<*>)
            "check_storage" -> checkStorage()
            "check_network" -> checkNetwork()
            "get_system_info" -> getSystemInfo()
            "suggest_fixes" -> suggestFixes()
            "verify_setup" -> verifySetup()
            else -> SkillResult(success = false, error = "Unknown function: $function")
        }
    }
    
    private suspend fun checkEnvironment(): SkillResult {
        log("Checking environment variables")
        
        val result = runCommand(listOf("env"))
        val envVars = mutableMapOf<String, String>()
        
        result.stdout.split("\n").forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                envVars[parts[0]] = parts[1]
            }
        }
        
        val requiredVars = listOf("HOME", "PREFIX", "PATH", "SHELL", "TERM")
        val missing = requiredVars.filter { !envVars.containsKey(it) }
        
        val issues = mutableListOf<String>()
        
        // Check PATH includes $PREFIX/bin
        val path = envVars["PATH"] ?: ""
        val prefix = envVars["PREFIX"] ?: "/data/data/com.termux/files/usr"
        if (!path.contains("$prefix/bin")) {
            issues.add("PATH does not include \$PREFIX/bin")
        }
        
        // Check HOME exists
        val home = envVars["HOME"]
        if (home != null && !File(home).exists()) {
            issues.add("HOME directory does not exist: $home")
        }
        
        log("Found ${envVars.size} environment variables, ${missing.size} missing")
        
        return SkillResult(
            success = missing.isEmpty() && issues.isEmpty(),
            data = mapOf(
                "variables" to envVars,
                "missing" to missing,
                "issues" to issues,
                "prefix" to prefix,
                "home" to (home ?: "unknown")
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun checkPaths(): SkillResult {
        log("Checking required paths")
        
        val prefixResult = runCommand(listOf("echo", "\$PREFIX"))
        val homeResult = runCommand(listOf("echo", "\$HOME"))
        
        val prefix = prefixResult.stdout.trim().takeIf { it.isNotBlank() }
            ?: "/data/data/com.termux/files/usr"
        val home = homeResult.stdout.trim().takeIf { it.isNotBlank() }
            ?: "/data/data/com.termux/files/home"
        
        val pathChecks = mutableMapOf<String, Map<String, Any>>()
        
        listOf(
            "$prefix/bin" to "Binaries",
            "$prefix/lib" to "Libraries",
            "$prefix/etc" to "Configuration",
            "$prefix/share" to "Shared data",
            "$prefix/var" to "Variable data",
            "$prefix/tmp" to "Temporary files",
            home to "Home directory"
        ).forEach { (path, description) ->
            val file = File(path)
            pathChecks[path] = mapOf(
                "exists" to file.exists(),
                "is_directory" to file.isDirectory,
                "readable" to file.canRead(),
                "writable" to file.canWrite(),
                "description" to description
            )
        }
        
        val missing = pathChecks.filter { !(it.value["exists"] as Boolean) }.keys
        val notWritable = pathChecks.filter { !(it.value["writable"] as Boolean) }.keys
        
        return SkillResult(
            success = missing.isEmpty(),
            data = mapOf(
                "prefix" to prefix,
                "home" to home,
                "paths" to pathChecks,
                "missing" to missing,
                "not_writable" to notWritable
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun checkTools(tools: List<*>?): SkillResult {
        log("Checking tool availability")
        
        val toolsToCheck = if (tools.isNullOrEmpty()) {
            essentialTools + optionalTools
        } else {
            tools.filterIsInstance<String>()
        }
        
        val available = mutableMapOf<String, Map<String, Any?>>()
        
        toolsToCheck.forEach { tool ->
            val result = runCommand(listOf("which", tool))
            val path = result.stdout.trim()
            
            if (result.isSuccess && path.isNotBlank()) {
                // Get version if possible
                val versionResult = runCommand(listOf(tool, "--version"))
                val version = if (versionResult.isSuccess) {
                    versionResult.stdout.split("\n").firstOrNull()?.take(100)
                } else null
                
                available[tool] = mapOf(
                    "available" to true,
                    "path" to path,
                    "version" to version
                )
            } else {
                available[tool] = mapOf(
                    "available" to false,
                    "path" to null,
                    "version" to null
                )
            }
        }
        
        val missingEssential = essentialTools.filter {
            available[it]?.get("available") != true
        }
        
        return SkillResult(
            success = missingEssential.isEmpty(),
            data = mapOf(
                "tools" to available,
                "missing_essential" to missingEssential,
                "available_count" to available.count { it.value["available"] == true },
                "missing_count" to available.count { it.value["available"] != true }
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun checkPackages(packages: List<*>?): SkillResult {
        log("Checking package status")
        
        val dpkgResult = runCommand(listOf("dpkg", "-l"))
        
        val installed = mutableMapOf<String, String>()
        dpkgResult.stdout.split("\n").forEach { line ->
            if (line.startsWith("ii")) {
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 3) {
                    installed[parts[1]] = parts[2]
                }
            }
        }
        
        val packagesToCheck = packages?.filterIsInstance<String>()
        
        val results = if (packagesToCheck != null) {
            packagesToCheck.associateWith { pkg ->
                mapOf(
                    "installed" to installed.containsKey(pkg),
                    "version" to installed[pkg]
                )
            }
        } else {
            installed.map { (pkg, version) ->
                pkg to mapOf("installed" to true, "version" to version)
            }.toMap()
        }
        
        return SkillResult(
            success = true,
            data = mapOf(
                "packages" to results,
                "total_installed" to installed.size
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun checkStorage(): SkillResult {
        log("Checking storage")
        
        val dfResult = runCommand(listOf("df", "-h", "."))
        val duResult = runCommand(listOf("du", "-sh", "\$PREFIX"))
        
        val dfLines = dfResult.stdout.trim().split("\n")
        val diskInfo = if (dfLines.size >= 2) {
            val parts = dfLines[1].split(Regex("\\s+"))
            mapOf(
                "filesystem" to (parts.getOrNull(0) ?: ""),
                "size" to (parts.getOrNull(1) ?: ""),
                "used" to (parts.getOrNull(2) ?: ""),
                "available" to (parts.getOrNull(3) ?: ""),
                "use_percent" to (parts.getOrNull(4) ?: ""),
                "mount" to (parts.getOrNull(5) ?: "")
            )
        } else {
            emptyMap()
        }
        
        val prefixSize = duResult.stdout.trim().split("\t").firstOrNull() ?: "unknown"
        
        // Check if low on space (less than 100MB)
        val available = diskInfo["available"]?.let { 
            when {
                it.endsWith("G") -> it.removeSuffix("G").toDoubleOrNull()?.times(1024) ?: 0.0
                it.endsWith("M") -> it.removeSuffix("M").toDoubleOrNull() ?: 0.0
                it.endsWith("K") -> it.removeSuffix("K").toDoubleOrNull()?.div(1024) ?: 0.0
                else -> 0.0
            }
        } ?: 0.0
        
        val lowSpace = available < 100
        
        return SkillResult(
            success = !lowSpace,
            data = mapOf(
                "disk" to diskInfo,
                "prefix_size" to prefixSize,
                "available_mb" to available,
                "low_space" to lowSpace
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun checkNetwork(): SkillResult {
        log("Checking network")
        
        // Try to ping Google DNS
        val pingResult = runCommand(
            listOf("ping", "-c", "1", "-W", "3", "8.8.8.8"),
            timeout = 10_000L
        )
        
        // Try to resolve a domain
        val dnsResult = runCommand(
            listOf("getent", "hosts", "google.com"),
            timeout = 10_000L
        )
        
        // Check if curl/wget is available
        val curlCheck = runCommand(listOf("which", "curl"))
        val wgetCheck = runCommand(listOf("which", "wget"))
        
        val connectivity = pingResult.isSuccess
        val dnsWorking = dnsResult.isSuccess
        
        return SkillResult(
            success = connectivity,
            data = mapOf(
                "connectivity" to connectivity,
                "dns_working" to dnsWorking,
                "curl_available" to curlCheck.isSuccess,
                "wget_available" to wgetCheck.isSuccess,
                "ping_output" to pingResult.stdout
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun getSystemInfo(): SkillResult {
        log("Getting system info")
        
        val unameResult = runCommand(listOf("uname", "-a"))
        val archResult = runCommand(listOf("uname", "-m"))
        
        // Get Android version if possible
        val androidVersion = try {
            val versionResult = runCommand(listOf("getprop", "ro.build.version.release"))
            versionResult.stdout.trim()
        } catch (e: Exception) {
            "unknown"
        }
        
        // Get memory info
        val memResult = runCommand(listOf("cat", "/proc/meminfo"))
        val memInfo = mutableMapOf<String, String>()
        memResult.stdout.split("\n").take(5).forEach { line ->
            val parts = line.split(":")
            if (parts.size == 2) {
                memInfo[parts[0].trim()] = parts[1].trim()
            }
        }
        
        return SkillResult(
            success = true,
            data = mapOf(
                "uname" to unameResult.stdout.trim(),
                "arch" to archResult.stdout.trim(),
                "android_version" to androidVersion,
                "memory" to memInfo
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun suggestFixes(): SkillResult {
        log("Generating fix suggestions")
        
        val suggestions = mutableListOf<Map<String, String>>()
        
        // Check environment
        val envCheck = checkEnvironment()
        if (!envCheck.success) {
            val missing = envCheck.data?.get("missing") as? List<*>
            missing?.forEach { varName ->
                suggestions.add(mapOf(
                    "issue" to "Missing environment variable: $varName",
                    "fix" to "Add 'export $varName=...' to ~/.bashrc or equivalent"
                ))
            }
        }
        
        // Check paths
        val pathCheck = checkPaths()
        val missingPaths = pathCheck.data?.get("missing") as? Collection<*>
        missingPaths?.forEach { path ->
            suggestions.add(mapOf(
                "issue" to "Missing directory: $path",
                "fix" to "Run: mkdir -p $path"
            ))
        }
        
        // Check essential tools
        val toolCheck = checkTools(essentialTools)
        val missingTools = toolCheck.data?.get("missing_essential") as? List<*>
        if (!missingTools.isNullOrEmpty()) {
            suggestions.add(mapOf(
                "issue" to "Missing essential tools: ${missingTools.joinToString(", ")}",
                "fix" to "Run: pkg install coreutils"
            ))
        }
        
        // Check storage
        val storageCheck = checkStorage()
        val lowSpace = storageCheck.data?.get("low_space") as? Boolean
        if (lowSpace == true) {
            suggestions.add(mapOf(
                "issue" to "Low disk space",
                "fix" to "Run: pkg clean && apt autoremove"
            ))
        }
        
        return SkillResult(
            success = true,
            data = mapOf(
                "suggestions" to suggestions,
                "count" to suggestions.size
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun verifySetup(): SkillResult {
        log("Verifying complete setup")
        
        val checks = mutableMapOf<String, Boolean>()
        
        // Run all checks
        val envCheck = checkEnvironment()
        checks["environment"] = envCheck.success
        
        val pathCheck = checkPaths()
        checks["paths"] = pathCheck.success
        
        val toolCheck = checkTools(essentialTools)
        checks["essential_tools"] = toolCheck.success
        
        val storageCheck = checkStorage()
        checks["storage"] = storageCheck.success
        
        val allPassed = checks.values.all { it }
        
        return SkillResult(
            success = allPassed,
            data = mapOf(
                "checks" to checks,
                "all_passed" to allPassed,
                "passed_count" to checks.values.count { it },
                "failed_count" to checks.values.count { !it }
            ),
            logs = context.getLogs()
        )
    }
    
    override suspend fun selfTest(context: SkillContext): SkillResult {
        this.context = context
        log("Running diagnostic skill self-test")
        
        // Just verify we can run basic commands
        val result = runCommand(listOf("echo", "test"))
        val success = result.isSuccess && result.stdout.trim() == "test"
        
        log("Self-test: ${if (success) "passed" else "failed"}")
        
        return SkillResult(
            success = success,
            data = mapOf(
                "test_passed" to success
            ),
            logs = context.getLogs()
        )
    }
}
