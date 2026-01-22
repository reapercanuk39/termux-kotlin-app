package com.termux.app.agents.skills.busybox

import com.termux.app.agents.models.Capability
import com.termux.app.agents.skills.BaseSkill
import com.termux.app.agents.skills.SkillContext
import com.termux.app.agents.skills.SkillResult
import com.termux.shared.logger.Logger
import java.io.File

/**
 * BusyBox Modern skill for system-level operations.
 * 
 * Provides skills that wrap BusyBox Modern CLI commands:
 * - busybox.install - Install/setup BusyBox symlinks
 * - busybox.uninstall - Remove BusyBox symlinks
 * - busybox.diagnose - Diagnose BusyBox installation
 * - busybox.repair.symlinks - Repair broken symlinks
 * - busybox.repair.path - Fix PATH issues
 * - busybox.magisk.check - Check Magisk status
 * 
 * BusyBox Modern is an EXTERNAL binary (root required).
 * This skill does NOT embed BusyBox internally.
 */
class BusyBoxSkill(
    private val config: BusyBoxConfig = BusyBoxConfig.default()
) : BaseSkill() {
    
    companion object {
        private const val LOG_TAG = "BusyBoxSkill"
        
        // Structured log event types
        const val EVENT_SKILL_START = "busybox.skill.start"
        const val EVENT_SKILL_END = "busybox.skill.end"
        const val EVENT_BINARY_NOT_FOUND = "busybox.error.missing_binary"
        const val EVENT_NO_ROOT = "busybox.error.no_root"
        const val EVENT_REPAIR_START = "busybox.repair.start"
        const val EVENT_REPAIR_END = "busybox.repair.end"
    }
    
    override val name = "busybox"
    override val description = "BusyBox Modern system integration for enhanced CLI tools"
    override val provides = listOf(
        "install",
        "uninstall",
        "diagnose",
        "repair_symlinks",
        "repair_path",
        "magisk_check",
        "list_applets",
        "run_applet",
        "get_version",
        "check_binary"
    )
    override val requiredCapabilities = setOf(
        Capability.Exec.BusyBox,
        Capability.Filesystem.Read,
        Capability.Filesystem.Write,
        Capability.System.Info
    )
    
    // Cached diagnostics
    private var cachedDiagnostics: BusyBoxDiagnostics? = null
    private var diagnosticsTimestamp: Long = 0
    private val diagnosticsCacheTtl = 30_000L  // 30 seconds
    
    override suspend fun executeFunction(
        function: String,
        params: Map<String, Any?>
    ): SkillResult {
        logEvent(EVENT_SKILL_START, mapOf("function" to function, "params" to params))
        
        val result = try {
            when (function) {
                "install" -> install(params)
                "uninstall" -> uninstall(params)
                "diagnose" -> diagnose(params)
                "repair_symlinks" -> repairSymlinks(params)
                "repair_path" -> repairPath(params)
                "magisk_check" -> magiskCheck(params)
                "list_applets" -> listApplets(params)
                "run_applet" -> runApplet(params)
                "get_version" -> getVersion(params)
                "check_binary" -> checkBinary(params)
                else -> SkillResult(success = false, error = "Unknown function: $function")
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "BusyBox skill error: ${e.message}")
            SkillResult(
                success = false,
                error = e.message ?: "Unknown error",
                logs = context.getLogs()
            )
        }
        
        logEvent(EVENT_SKILL_END, mapOf("function" to function, "success" to result.success))
        return result
    }
    
    /**
     * Find the BusyBox binary path.
     */
    private suspend fun findBusyBoxBinary(): String? {
        for (path in config.getAllPaths()) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                log("Found BusyBox at: $path")
                return path
            }
        }
        
        // Try using which command
        val whichResult = runShell("which busybox busybox-modern 2>/dev/null | head -1")
        if (whichResult.isSuccess && whichResult.stdout.trim().isNotBlank()) {
            return whichResult.stdout.trim()
        }
        
        logEvent(EVENT_BINARY_NOT_FOUND, mapOf("searched_paths" to config.getAllPaths()))
        return null
    }
    
    /**
     * Check if root access is available.
     */
    private suspend fun checkRootAccess(): Boolean {
        val result = runShell("id -u")
        return result.isSuccess && result.stdout.trim() == "0"
    }
    
    /**
     * Install/setup BusyBox symlinks.
     */
    private suspend fun install(params: Map<String, Any?>): SkillResult {
        log("Installing BusyBox symlinks")
        
        val dryRun = params["dry_run"] as? Boolean ?: config.dryRun
        val force = params["force"] as? Boolean ?: false
        
        // Check root if required
        if (config.requireRoot && !checkRootAccess()) {
            logEvent(EVENT_NO_ROOT, emptyMap())
            return SkillResult(
                success = false,
                error = "Root access required for BusyBox installation",
                data = mapOf("root_required" to true)
            )
        }
        
        // Find binary
        val binaryPath = findBusyBoxBinary()
            ?: return SkillResult(
                success = false,
                error = "BusyBox Modern binary not found",
                data = mapOf("searched_paths" to config.getAllPaths())
            )
        
        // Create symlink directory
        val symlinkDir = config.symlinkDir
        if (!dryRun) {
            val mkdirResult = runShell("mkdir -p $symlinkDir")
            if (!mkdirResult.isSuccess) {
                return SkillResult(
                    success = false,
                    error = "Failed to create symlink directory: ${mkdirResult.stderr}"
                )
            }
        }
        
        // Get list of applets
        val appletsResult = runCommand(listOf(binaryPath, "--list"))
        if (!appletsResult.isSuccess) {
            return SkillResult(
                success = false,
                error = "Failed to get applet list: ${appletsResult.stderr}"
            )
        }
        
        val applets = appletsResult.stdout.trim().split("\n").filter { it.isNotBlank() }
        log("Found ${applets.size} applets to install")
        
        // Create symlinks
        var created = 0
        var skipped = 0
        var failed = 0
        val details = mutableListOf<Map<String, Any>>()
        
        for (applet in applets) {
            val symlinkPath = "$symlinkDir/$applet"
            val symlinkFile = File(symlinkPath)
            
            if (symlinkFile.exists() && !force) {
                skipped++
                continue
            }
            
            if (dryRun) {
                details.add(mapOf(
                    "applet" to applet,
                    "action" to "would_create",
                    "path" to symlinkPath
                ))
                created++
                continue
            }
            
            val linkResult = runShell("ln -sf $binaryPath $symlinkPath")
            if (linkResult.isSuccess) {
                created++
            } else {
                failed++
                details.add(mapOf(
                    "applet" to applet,
                    "action" to "failed",
                    "error" to linkResult.stderr
                ))
            }
        }
        
        // Invalidate diagnostics cache
        cachedDiagnostics = null
        
        return SkillResult(
            success = failed == 0,
            data = mapOf(
                "binary_path" to binaryPath,
                "symlink_dir" to symlinkDir,
                "total_applets" to applets.size,
                "created" to created,
                "skipped" to skipped,
                "failed" to failed,
                "dry_run" to dryRun,
                "details" to details
            ),
            logs = context.getLogs()
        )
    }
    
    /**
     * Uninstall/remove BusyBox symlinks.
     */
    private suspend fun uninstall(params: Map<String, Any?>): SkillResult {
        log("Uninstalling BusyBox symlinks")
        
        val dryRun = params["dry_run"] as? Boolean ?: config.dryRun
        val removeDir = params["remove_dir"] as? Boolean ?: false
        
        val symlinkDir = config.symlinkDir
        val dirFile = File(symlinkDir)
        
        if (!dirFile.exists()) {
            return SkillResult(
                success = true,
                data = mapOf(
                    "symlink_dir" to symlinkDir,
                    "status" to "not_installed"
                )
            )
        }
        
        // Count symlinks
        val files = dirFile.listFiles() ?: emptyArray()
        val symlinks = files.filter { it.isFile }
        
        if (dryRun) {
            return SkillResult(
                success = true,
                data = mapOf(
                    "symlink_dir" to symlinkDir,
                    "would_remove" to symlinks.size,
                    "dry_run" to true
                )
            )
        }
        
        // Remove symlinks
        var removed = 0
        for (file in symlinks) {
            if (file.delete()) {
                removed++
            }
        }
        
        // Optionally remove directory
        if (removeDir && dirFile.listFiles()?.isEmpty() == true) {
            dirFile.delete()
        }
        
        // Invalidate diagnostics cache
        cachedDiagnostics = null
        
        return SkillResult(
            success = true,
            data = mapOf(
                "symlink_dir" to symlinkDir,
                "removed" to removed,
                "total" to symlinks.size,
                "directory_removed" to (removeDir && !dirFile.exists())
            ),
            logs = context.getLogs()
        )
    }
    
    /**
     * Diagnose BusyBox installation.
     */
    private suspend fun diagnose(params: Map<String, Any?>): SkillResult {
        log("Running BusyBox diagnostics")
        
        val forceRefresh = params["force_refresh"] as? Boolean ?: false
        
        // Check cache
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedDiagnostics != null && 
            (now - diagnosticsTimestamp) < diagnosticsCacheTtl) {
            log("Returning cached diagnostics")
            return diagnosticsToResult(cachedDiagnostics!!)
        }
        
        val issues = mutableListOf<BusyBoxIssue>()
        
        // Check binary
        val binaryPath = findBusyBoxBinary()
        val installed = binaryPath != null
        
        if (!installed) {
            issues.add(BusyBoxIssue(
                code = "BINARY_NOT_FOUND",
                message = "BusyBox Modern binary not found",
                severity = BusyBoxIssueSeverity.CRITICAL,
                suggestedFix = "Install BusyBox Modern from https://github.com/nicoulaj/busybox-modern"
            ))
        }
        
        // Get version
        var version: String? = null
        var applets = emptyList<String>()
        var appletCount = 0
        
        if (installed) {
            val versionResult = runCommand(listOf(binaryPath!!, "--help"))
            version = versionResult.stdout.split("\n").firstOrNull()?.trim()
            
            val appletsResult = runCommand(listOf(binaryPath, "--list"))
            if (appletsResult.isSuccess) {
                applets = appletsResult.stdout.trim().split("\n").filter { it.isNotBlank() }
                appletCount = applets.size
            }
        }
        
        // Check symlinks
        val symlinkDir = File(config.symlinkDir)
        val symlinksExist = symlinkDir.exists()
        val symlinkCount = symlinkDir.listFiles()?.count { it.isFile } ?: 0
        var symlinksValid = false
        
        if (symlinksExist && symlinkCount > 0) {
            // Verify a few symlinks
            val testFiles = symlinkDir.listFiles()?.take(3) ?: emptyList()
            symlinksValid = testFiles.all { file ->
                val testResult = runCommand(listOf(file.absolutePath, "--help"))
                testResult.isSuccess
            }
            
            if (!symlinksValid) {
                issues.add(BusyBoxIssue(
                    code = "SYMLINKS_BROKEN",
                    message = "Some BusyBox symlinks are broken",
                    severity = BusyBoxIssueSeverity.ERROR,
                    suggestedFix = "Run busybox.repair_symlinks to fix"
                ))
            }
        } else if (installed) {
            issues.add(BusyBoxIssue(
                code = "SYMLINKS_MISSING",
                message = "BusyBox symlinks not installed",
                severity = BusyBoxIssueSeverity.WARNING,
                suggestedFix = "Run busybox.install to create symlinks"
            ))
        }
        
        // Check PATH
        val pathResult = runShell("echo \$PATH")
        val path = pathResult.stdout.trim()
        val pathIncluded = path.contains(config.symlinkDir)
        
        if (!pathIncluded && symlinksExist) {
            issues.add(BusyBoxIssue(
                code = "PATH_MISSING",
                message = "BusyBox symlink directory not in PATH",
                severity = BusyBoxIssueSeverity.WARNING,
                suggestedFix = "Run busybox.repair_path or add ${config.symlinkDir} to PATH"
            ))
        }
        
        // Check root
        val rootAvailable = checkRootAccess()
        if (config.requireRoot && !rootAvailable) {
            issues.add(BusyBoxIssue(
                code = "ROOT_UNAVAILABLE",
                message = "Root access not available but required",
                severity = BusyBoxIssueSeverity.WARNING,
                suggestedFix = "Ensure root access is granted or use non-root configuration"
            ))
        }
        
        // Check Magisk
        val magiskInstalled = File("/data/adb/magisk").exists()
        
        val diagnostics = BusyBoxDiagnostics(
            installed = installed,
            binaryPath = binaryPath,
            version = version,
            appletCount = appletCount,
            applets = applets,
            symlinksValid = symlinksValid,
            symlinkCount = symlinkCount,
            pathIncluded = pathIncluded,
            rootAvailable = rootAvailable,
            magiskInstalled = magiskInstalled,
            issues = issues
        )
        
        // Cache diagnostics
        cachedDiagnostics = diagnostics
        diagnosticsTimestamp = now
        
        return diagnosticsToResult(diagnostics)
    }
    
    private fun diagnosticsToResult(diagnostics: BusyBoxDiagnostics): SkillResult {
        return SkillResult(
            success = diagnostics.isOperational(),
            data = mapOf(
                "installed" to diagnostics.installed,
                "binary_path" to diagnostics.binaryPath,
                "version" to diagnostics.version,
                "applet_count" to diagnostics.appletCount,
                "symlinks_valid" to diagnostics.symlinksValid,
                "symlink_count" to diagnostics.symlinkCount,
                "path_included" to diagnostics.pathIncluded,
                "root_available" to diagnostics.rootAvailable,
                "magisk_installed" to diagnostics.magiskInstalled,
                "operational" to diagnostics.isOperational(),
                "issues" to diagnostics.issues.map { issue ->
                    mapOf(
                        "code" to issue.code,
                        "message" to issue.message,
                        "severity" to issue.severity.name,
                        "suggested_fix" to issue.suggestedFix
                    )
                },
                "issue_count" to diagnostics.issues.size,
                "max_severity" to diagnostics.getMaxSeverity()?.name
            ),
            logs = context.getLogs()
        )
    }
    
    /**
     * Repair broken symlinks.
     */
    private suspend fun repairSymlinks(params: Map<String, Any?>): SkillResult {
        log("Repairing BusyBox symlinks")
        logEvent(EVENT_REPAIR_START, mapOf("type" to "symlinks"))
        
        val dryRun = params["dry_run"] as? Boolean ?: config.dryRun
        
        // Find binary first
        val binaryPath = findBusyBoxBinary()
            ?: return SkillResult(
                success = false,
                error = "BusyBox binary not found - cannot repair symlinks"
            )
        
        val symlinkDir = config.symlinkDir
        val dirFile = File(symlinkDir)
        
        // Create directory if missing
        if (!dirFile.exists()) {
            if (!dryRun) {
                dirFile.mkdirs()
            }
        }
        
        // Get applet list
        val appletsResult = runCommand(listOf(binaryPath, "--list"))
        val applets = appletsResult.stdout.trim().split("\n").filter { it.isNotBlank() }
        
        val repairs = mutableListOf<RepairDetail>()
        var attempted = 0
        var succeeded = 0
        var failed = 0
        
        for (applet in applets) {
            val symlinkPath = "$symlinkDir/$applet"
            val symlinkFile = File(symlinkPath)
            var needsRepair = false
            
            // Check if symlink exists and works
            if (!symlinkFile.exists()) {
                needsRepair = true
            } else {
                val testResult = runCommand(listOf(symlinkPath, "--help"))
                if (!testResult.isSuccess) {
                    needsRepair = true
                }
            }
            
            if (needsRepair) {
                attempted++
                
                if (dryRun) {
                    repairs.add(RepairDetail(
                        action = "would_repair",
                        target = symlinkPath,
                        success = true,
                        message = "Would create/repair symlink"
                    ))
                    succeeded++
                    continue
                }
                
                // Remove broken symlink if exists
                if (symlinkFile.exists()) {
                    symlinkFile.delete()
                }
                
                // Create new symlink
                val linkResult = runShell("ln -sf $binaryPath $symlinkPath")
                if (linkResult.isSuccess) {
                    succeeded++
                    repairs.add(RepairDetail(
                        action = "repaired",
                        target = symlinkPath,
                        success = true,
                        message = "Symlink created"
                    ))
                } else {
                    failed++
                    repairs.add(RepairDetail(
                        action = "failed",
                        target = symlinkPath,
                        success = false,
                        message = linkResult.stderr
                    ))
                }
            }
        }
        
        // Invalidate diagnostics cache
        cachedDiagnostics = null
        
        logEvent(EVENT_REPAIR_END, mapOf(
            "type" to "symlinks",
            "attempted" to attempted,
            "succeeded" to succeeded,
            "failed" to failed
        ))
        
        val result = BusyBoxRepairResult(
            success = failed == 0,
            repairsAttempted = attempted,
            repairsSucceeded = succeeded,
            repairsFailed = failed,
            details = repairs
        )
        
        return SkillResult(
            success = result.success,
            data = mapOf(
                "repairs_attempted" to result.repairsAttempted,
                "repairs_succeeded" to result.repairsSucceeded,
                "repairs_failed" to result.repairsFailed,
                "dry_run" to dryRun,
                "details" to result.details.map { detail ->
                    mapOf(
                        "action" to detail.action,
                        "target" to detail.target,
                        "success" to detail.success,
                        "message" to detail.message
                    )
                }
            ),
            logs = context.getLogs()
        )
    }
    
    /**
     * Repair PATH to include BusyBox symlinks.
     */
    private suspend fun repairPath(params: Map<String, Any?>): SkillResult {
        log("Repairing PATH for BusyBox")
        logEvent(EVENT_REPAIR_START, mapOf("type" to "path"))
        
        val dryRun = params["dry_run"] as? Boolean ?: config.dryRun
        val prepend = params["prepend"] as? Boolean ?: true  // Add to front of PATH
        
        val symlinkDir = config.symlinkDir
        
        // Check current PATH
        val pathResult = runShell("echo \$PATH")
        val currentPath = pathResult.stdout.trim()
        
        if (currentPath.contains(symlinkDir)) {
            return SkillResult(
                success = true,
                data = mapOf(
                    "status" to "already_included",
                    "path" to currentPath
                )
            )
        }
        
        // Determine profile file to modify
        val profileFiles = listOf(
            "/data/data/com.termux/files/home/.bashrc",
            "/data/data/com.termux/files/home/.profile",
            "/data/data/com.termux/files/home/.zshrc"
        )
        
        val existingProfile = profileFiles.find { File(it).exists() }
            ?: "/data/data/com.termux/files/home/.bashrc"
        
        val exportLine = if (prepend) {
            "export PATH=\"$symlinkDir:\$PATH\""
        } else {
            "export PATH=\"\$PATH:$symlinkDir\""
        }
        
        if (dryRun) {
            return SkillResult(
                success = true,
                data = mapOf(
                    "status" to "would_repair",
                    "profile_file" to existingProfile,
                    "export_line" to exportLine,
                    "dry_run" to true
                )
            )
        }
        
        // Append to profile
        val appendResult = runShell("echo '$exportLine' >> $existingProfile")
        
        if (!appendResult.isSuccess) {
            logEvent(EVENT_REPAIR_END, mapOf("type" to "path", "success" to false))
            return SkillResult(
                success = false,
                error = "Failed to update profile: ${appendResult.stderr}"
            )
        }
        
        // Invalidate diagnostics cache
        cachedDiagnostics = null
        
        logEvent(EVENT_REPAIR_END, mapOf("type" to "path", "success" to true))
        
        return SkillResult(
            success = true,
            data = mapOf(
                "status" to "repaired",
                "profile_file" to existingProfile,
                "export_line" to exportLine,
                "note" to "Run 'source $existingProfile' or restart shell to apply"
            ),
            logs = context.getLogs()
        )
    }
    
    /**
     * Check Magisk status.
     */
    private suspend fun magiskCheck(params: Map<String, Any?>): SkillResult {
        log("Checking Magisk status")
        
        // Check if Magisk is installed
        val magiskDir = File("/data/adb/magisk")
        val installed = magiskDir.exists()
        
        if (!installed) {
            return SkillResult(
                success = true,
                data = mapOf(
                    "installed" to false,
                    "version" to null,
                    "busybox_module" to false
                )
            )
        }
        
        // Get Magisk version
        val versionResult = runShell("magisk -v 2>/dev/null || cat /data/adb/magisk/magisk.apk.ver 2>/dev/null")
        val version = versionResult.stdout.trim().takeIf { it.isNotBlank() }
        
        // Check for BusyBox module
        val modulePath = config.magiskModulePath ?: BusyBoxConfig.DEFAULT_MAGISK_MODULE_PATH
        val moduleDir = File(modulePath)
        val moduleInstalled = moduleDir.exists()
        
        var moduleEnabled = false
        var systemlessMode = false
        
        if (moduleInstalled) {
            // Check if module is disabled
            val disableFile = File("$modulePath/disable")
            moduleEnabled = !disableFile.exists()
            
            // Check for system.prop
            val systemProp = File("$modulePath/system.prop")
            systemlessMode = !systemProp.exists()
        }
        
        val status = MagiskStatus(
            installed = true,
            version = version,
            modulePath = if (moduleInstalled) modulePath else null,
            moduleEnabled = moduleEnabled,
            systemlessMode = systemlessMode
        )
        
        return SkillResult(
            success = true,
            data = mapOf(
                "installed" to status.installed,
                "version" to status.version,
                "busybox_module_path" to status.modulePath,
                "busybox_module_installed" to moduleInstalled,
                "busybox_module_enabled" to status.moduleEnabled,
                "systemless_mode" to status.systemlessMode
            ),
            logs = context.getLogs()
        )
    }
    
    /**
     * List available BusyBox applets.
     */
    private suspend fun listApplets(params: Map<String, Any?>): SkillResult {
        log("Listing BusyBox applets")
        
        val binaryPath = findBusyBoxBinary()
            ?: return SkillResult(
                success = false,
                error = "BusyBox binary not found"
            )
        
        val result = runCommand(listOf(binaryPath, "--list"))
        
        if (!result.isSuccess) {
            return SkillResult(
                success = false,
                error = "Failed to list applets: ${result.stderr}"
            )
        }
        
        val applets = result.stdout.trim().split("\n").filter { it.isNotBlank() }.sorted()
        
        // Categorize applets
        val categories = mapOf(
            "file" to listOf("ls", "cp", "mv", "rm", "mkdir", "rmdir", "chmod", "chown", "cat", "head", "tail", "touch", "stat", "find"),
            "text" to listOf("grep", "sed", "awk", "sort", "uniq", "wc", "tr", "cut", "paste"),
            "archive" to listOf("tar", "gzip", "gunzip", "bzip2", "bunzip2", "xz", "unxz", "zip", "unzip"),
            "network" to listOf("ping", "wget", "nc", "netstat", "ifconfig", "ip", "route", "traceroute"),
            "process" to listOf("ps", "kill", "killall", "top", "nice", "nohup", "time"),
            "system" to listOf("mount", "umount", "df", "du", "free", "uname", "hostname", "dmesg", "sysctl")
        )
        
        val categorized = mutableMapOf<String, List<String>>()
        val uncategorized = mutableListOf<String>()
        
        for (applet in applets) {
            var found = false
            for ((category, categoryApplets) in categories) {
                if (applet in categoryApplets) {
                    categorized.getOrPut(category) { mutableListOf() }
                    (categorized[category] as MutableList).add(applet)
                    found = true
                    break
                }
            }
            if (!found) {
                uncategorized.add(applet)
            }
        }
        categorized["other"] = uncategorized
        
        return SkillResult(
            success = true,
            data = mapOf(
                "binary_path" to binaryPath,
                "total_count" to applets.size,
                "applets" to applets,
                "categorized" to categorized
            ),
            logs = context.getLogs()
        )
    }
    
    /**
     * Run a BusyBox applet directly.
     */
    private suspend fun runApplet(params: Map<String, Any?>): SkillResult {
        val applet = params["applet"] as? String
            ?: return SkillResult(success = false, error = "Applet name required")
        
        @Suppress("UNCHECKED_CAST")
        val args = (params["args"] as? List<String>) ?: emptyList()
        val timeout = (params["timeout"] as? Number)?.toLong() ?: config.commandTimeoutMs
        
        log("Running applet: $applet ${args.joinToString(" ")}")
        
        val binaryPath = findBusyBoxBinary()
            ?: return SkillResult(
                success = false,
                error = "BusyBox binary not found"
            )
        
        val command = listOf(binaryPath, applet) + args
        val result = runCommand(command, timeout)
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "applet" to applet,
                "args" to args,
                "exit_code" to result.exitCode,
                "stdout" to result.stdout,
                "stderr" to result.stderr
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    /**
     * Get BusyBox version information.
     */
    private suspend fun getVersion(params: Map<String, Any?>): SkillResult {
        log("Getting BusyBox version")
        
        val binaryPath = findBusyBoxBinary()
            ?: return SkillResult(
                success = false,
                error = "BusyBox binary not found"
            )
        
        val result = runCommand(listOf(binaryPath, "--help"))
        
        val lines = result.stdout.split("\n")
        val versionLine = lines.firstOrNull()?.trim() ?: "Unknown"
        
        // Parse version string
        val versionMatch = Regex("BusyBox v([\\d.]+)").find(versionLine)
        val version = versionMatch?.groupValues?.get(1)
        
        return SkillResult(
            success = true,
            data = mapOf(
                "binary_path" to binaryPath,
                "version" to version,
                "version_string" to versionLine,
                "full_output" to lines.take(5).joinToString("\n")
            ),
            logs = context.getLogs()
        )
    }
    
    /**
     * Check if BusyBox binary exists and is executable.
     */
    private suspend fun checkBinary(params: Map<String, Any?>): SkillResult {
        log("Checking BusyBox binary")
        
        val binaryPath = findBusyBoxBinary()
        
        return SkillResult(
            success = binaryPath != null,
            data = mapOf(
                "found" to (binaryPath != null),
                "binary_path" to binaryPath,
                "searched_paths" to config.getAllPaths(),
                "config" to mapOf(
                    "require_root" to config.requireRoot,
                    "symlink_dir" to config.symlinkDir,
                    "dry_run" to config.dryRun
                )
            ),
            logs = context.getLogs()
        )
    }
    
    /**
     * Log structured event.
     */
    private fun logEvent(event: String, data: Map<String, Any?>) {
        val message = "$event: ${data.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        Logger.logInfo(LOG_TAG, message)
        log(message)
    }
    
    override suspend fun selfTest(context: SkillContext): SkillResult {
        this.context = context
        log("Running BusyBox skill self-test")
        
        // Check if binary exists
        val binaryPath = findBusyBoxBinary()
        val binaryExists = binaryPath != null
        
        // Try to run --version if binary exists
        var canExecute = false
        if (binaryExists) {
            val result = runCommand(listOf(binaryPath!!, "--help"))
            canExecute = result.isSuccess
        }
        
        log("Self-test: binary=${binaryExists}, executable=${canExecute}")
        
        return SkillResult(
            success = true,  // Skill is always functional, even if BusyBox isn't installed
            data = mapOf(
                "busybox_available" to binaryExists,
                "busybox_executable" to canExecute,
                "binary_path" to binaryPath,
                "skill_functional" to true
            ),
            logs = context.getLogs()
        )
    }
}
