package com.termux.app.pkg.cli

import com.termux.app.pkg.backup.*
import com.termux.app.pkg.cli.commands.device.DeviceCommands
import com.termux.app.pkg.doctor.*
import kotlinx.coroutines.runBlocking

/**
 * termuxctl - Termux Kotlin Control Utility
 * 
 * A unified CLI for managing Termux packages, backups, profiles, and health checks.
 * 
 * ## Usage
 * 
 * ```bash
 * # Package backup and restore
 * termuxctl backup create [--output <path>] [--type <full|packages|config|minimal>]
 * termuxctl backup restore <path> [--dry-run] [--selective] [--skip-repos]
 * termuxctl backup list
 * 
 * # Package health checks
 * termuxctl pkg doctor [--auto-repair]
 * termuxctl pkg upgrade [--safe]
 * 
 * # Repository management
 * termuxctl repo list
 * termuxctl repo add <name> <url>
 * termuxctl repo remove <name>
 * termuxctl repo enable <name>
 * termuxctl repo disable <name>
 * 
 * # Profile management
 * termuxctl profile list
 * termuxctl profile activate <name>
 * termuxctl profile export <name> [--output <path>]
 * termuxctl profile import <path>
 * 
 * # General
 * termuxctl --help
 * termuxctl --version
 * ```
 */
class TermuxCtl(
    private val backupManager: PackageBackupManager,
    private val doctor: PackageDoctor,
    private val deviceCommands: DeviceCommands? = null
) {
    companion object {
        const val VERSION = "1.0.0"
        const val APP_NAME = "termuxctl"
        
        // ANSI colors for terminal output
        private const val RESET = "\u001B[0m"
        private const val RED = "\u001B[31m"
        private const val GREEN = "\u001B[32m"
        private const val YELLOW = "\u001B[33m"
        private const val BLUE = "\u001B[34m"
        private const val CYAN = "\u001B[36m"
        private const val BOLD = "\u001B[1m"
    }
    
    /**
     * Main entry point for CLI execution.
     * 
     * @param args Command line arguments
     * @return Exit code (0 = success, non-zero = error)
     */
    fun execute(args: Array<String>): Int = runBlocking {
        if (args.isEmpty()) {
            printUsage()
            return@runBlocking 1
        }
        
        when (args[0]) {
            "backup" -> handleBackupCommand(args.drop(1))
            "pkg" -> handlePackageCommand(args.drop(1))
            "repo" -> handleRepoCommand(args.drop(1))
            "profile" -> handleProfileCommand(args.drop(1))
            "device" -> handleDeviceCommand(args.drop(1))
            "--help", "-h", "help" -> { printUsage(); 0 }
            "--version", "-v", "version" -> { printVersion(); 0 }
            else -> { 
                printError("Unknown command: ${args[0]}")
                printUsage()
                1 
            }
        }
    }
    
    // ========== Backup Commands ==========
    
    private suspend fun handleBackupCommand(args: List<String>): Int {
        if (args.isEmpty()) {
            printBackupUsage()
            return 1
        }
        
        return when (args[0]) {
            "create" -> createBackup(args.drop(1))
            "restore" -> restoreBackup(args.drop(1))
            "list" -> listBackups()
            "--help", "-h" -> { printBackupUsage(); 0 }
            else -> {
                printError("Unknown backup command: ${args[0]}")
                printBackupUsage()
                1
            }
        }
    }
    
    private suspend fun createBackup(args: List<String>): Int {
        val options = parseBackupCreateOptions(args)
        
        println("${CYAN}Creating ${options.type.name.lowercase()} backup...${RESET}")
        
        val result = backupManager.createBackup(
            config = BackupConfig(backupType = options.type, includeDotfiles = options.includeDotfiles),
            outputPath = options.output
        )
        
        return result.fold(
            onSuccess = { metadata ->
                println()
                println("${GREEN}✓ Backup created successfully${RESET}")
                println()
                println("  ${BOLD}Path:${RESET} ${options.output}")
                println("  ${BOLD}Packages:${RESET} ${metadata.contents.packages.size}")
                println("  ${BOLD}Repositories:${RESET} ${metadata.contents.repositories.size}")
                println("  ${BOLD}Held packages:${RESET} ${metadata.contents.heldPackages.size}")
                if (metadata.contents.includedDotfiles.isNotEmpty()) {
                    println("  ${BOLD}Dotfiles:${RESET} ${metadata.contents.includedDotfiles.size}")
                }
                println()
                0
            },
            onFailure = { error ->
                printError("Backup failed: ${error.message}")
                1
            }
        )
    }
    
    private suspend fun restoreBackup(args: List<String>): Int {
        if (args.isEmpty()) {
            printError("Missing backup path")
            println("Usage: $APP_NAME backup restore <path> [options]")
            return 1
        }
        
        val backupPath = args[0]
        val options = parseRestoreOptions(args.drop(1))
        
        if (options.dryRun) {
            println("${CYAN}Performing dry-run restore from:${RESET} $backupPath")
        } else {
            println("${CYAN}Restoring from:${RESET} $backupPath")
        }
        
        val result = backupManager.restoreBackup(backupPath, options)
        
        return result.fold(
            onSuccess = { restoreResult ->
                println()
                if (restoreResult.isDryRun) {
                    println("${YELLOW}═══ Dry Run Results ═══${RESET}")
                    println()
                    println("  ${BOLD}Would install:${RESET} ${restoreResult.packagesToInstall.size} packages")
                    println("  ${BOLD}Would add:${RESET} ${restoreResult.repositoriesToAdd.size} repositories")
                    
                    if (restoreResult.packagesToInstall.isNotEmpty()) {
                        println()
                        println("  ${BOLD}Packages:${RESET}")
                        restoreResult.packagesToInstall.take(20).forEach { pkg ->
                            println("    • $pkg")
                        }
                        if (restoreResult.packagesToInstall.size > 20) {
                            println("    ... and ${restoreResult.packagesToInstall.size - 20} more")
                        }
                    }
                    
                    if (restoreResult.warnings.isNotEmpty()) {
                        println()
                        println("  ${YELLOW}Warnings:${RESET}")
                        restoreResult.warnings.forEach { warning ->
                            println("    ⚠ $warning")
                        }
                    }
                } else {
                    println("${GREEN}✓ Restore completed${RESET}")
                    println()
                    println("  ${BOLD}Installed:${RESET} ${restoreResult.packagesToInstall.size} packages")
                    println("  ${BOLD}Repositories:${RESET} ${restoreResult.repositoriesToAdd.size}")
                    
                    if (restoreResult.failedPackages.isNotEmpty()) {
                        println()
                        println("  ${RED}Failed packages:${RESET}")
                        restoreResult.failedPackages.forEach { pkg ->
                            println("    ✗ $pkg")
                        }
                    }
                }
                println()
                0
            },
            onFailure = { error ->
                printError("Restore failed: ${error.message}")
                1
            }
        )
    }
    
    private suspend fun listBackups(): Int {
        val backups = backupManager.listBackups()
        
        if (backups.isEmpty()) {
            println("${YELLOW}No backups found.${RESET}")
            println()
            println("Create one with: $APP_NAME backup create")
            return 0
        }
        
        println("${BOLD}Available backups:${RESET}")
        println()
        backups.forEach { backup ->
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(backup.createdAt))
            println("  ${CYAN}${backup.name}${RESET}")
            println("    Size: ${backup.sizeFormatted}")
            println("    Created: $date")
            println()
        }
        
        return 0
    }
    
    // ========== Package Commands ==========
    
    private suspend fun handlePackageCommand(args: List<String>): Int {
        if (args.isEmpty()) {
            printPackageUsage()
            return 1
        }
        
        return when (args[0]) {
            "doctor" -> runDoctor(args.drop(1))
            "upgrade" -> runUpgrade(args.drop(1))
            "--help", "-h" -> { printPackageUsage(); 0 }
            else -> {
                printError("Unknown package command: ${args[0]}")
                printPackageUsage()
                1
            }
        }
    }
    
    private suspend fun runDoctor(args: List<String>): Int {
        val autoRepair = "--auto-repair" in args || "-r" in args
        val verbose = "--verbose" in args || "-v" in args
        
        println("${CYAN}Running package diagnostics...${RESET}")
        println()
        
        val report = doctor.runFullDiagnostic()
        
        // Print header
        println(report.toCliSummary())
        
        // Color the health score
        val scoreColor = when {
            report.healthScore >= 90 -> GREEN
            report.healthScore >= 70 -> YELLOW
            else -> RED
        }
        
        // Print issues grouped by type
        if (report.issues.isNotEmpty()) {
            println()
            println("${BOLD}Issues Found:${RESET}")
            
            report.issuesByType.forEach { (type, issues) ->
                println()
                val typeColor = when (issues.first().severity) {
                    IssueSeverity.CRITICAL, IssueSeverity.HIGH -> RED
                    IssueSeverity.MEDIUM -> YELLOW
                    else -> RESET
                }
                println("  $typeColor${type.name.replace("_", " ")} (${issues.size})$RESET")
                
                val toShow = if (verbose) issues else issues.take(5)
                toShow.forEach { issue ->
                    val icon = when (issue.severity) {
                        IssueSeverity.CRITICAL -> "💀"
                        IssueSeverity.HIGH -> "✗"
                        IssueSeverity.MEDIUM -> "!"
                        IssueSeverity.LOW -> "·"
                        IssueSeverity.INFO -> "○"
                    }
                    println("    $icon ${issue.description}")
                }
                if (!verbose && issues.size > 5) {
                    println("    ${CYAN}... and ${issues.size - 5} more (use --verbose to see all)${RESET}")
                }
            }
        }
        
        // Print recommendations
        if (report.recommendations.isNotEmpty()) {
            println()
            println("${BOLD}Recommendations:${RESET}")
            report.recommendations.forEach { rec ->
                println("  → $rec")
            }
        }
        
        // Auto-repair if requested
        if (autoRepair && report.issues.any { it.severity >= IssueSeverity.MEDIUM }) {
            println()
            println("${CYAN}Attempting auto-repair...${RESET}")
            
            val repairResult = doctor.autoRepair(report.issues)
            
            println()
            if (repairResult.wasSuccessful) {
                println("${GREEN}✓ All repairs completed successfully${RESET}")
            } else if (repairResult.partialSuccess) {
                println("${YELLOW}⚠ Partial repair:${RESET}")
                println("  Succeeded: ${repairResult.repaired.size}")
                println("  Failed: ${repairResult.failed.size}")
            } else {
                println("${RED}✗ Repair failed${RESET}")
                repairResult.failed.forEach { failure ->
                    println("  • $failure")
                }
            }
        }
        
        println()
        println("Scan completed in ${report.scanDurationMs}ms")
        
        return if (report.isHealthy) 0 else 1
    }
    
    private suspend fun runUpgrade(args: List<String>): Int {
        val safeMode = "--safe" in args || "-s" in args
        
        if (safeMode) {
            println("${CYAN}Running safe upgrade (held packages preserved)...${RESET}")
            // TODO: Implement safe upgrade
            println("${YELLOW}Safe upgrade not yet implemented${RESET}")
        } else {
            println("${CYAN}Running full upgrade...${RESET}")
            // TODO: Implement full upgrade
            println("${YELLOW}Full upgrade not yet implemented${RESET}")
        }
        
        return 0
    }
    
    // ========== Repository Commands ==========
    
    private fun handleRepoCommand(args: List<String>): Int {
        if (args.isEmpty()) {
            printRepoUsage()
            return 1
        }
        
        return when (args[0]) {
            "list" -> { println("${YELLOW}Repository listing not yet implemented${RESET}"); 0 }
            "add" -> { println("${YELLOW}Repository add not yet implemented${RESET}"); 0 }
            "remove" -> { println("${YELLOW}Repository remove not yet implemented${RESET}"); 0 }
            "enable" -> { println("${YELLOW}Repository enable not yet implemented${RESET}"); 0 }
            "disable" -> { println("${YELLOW}Repository disable not yet implemented${RESET}"); 0 }
            "--help", "-h" -> { printRepoUsage(); 0 }
            else -> {
                printError("Unknown repo command: ${args[0]}")
                1
            }
        }
    }
    
    // ========== Profile Commands ==========
    
    private fun handleProfileCommand(args: List<String>): Int {
        if (args.isEmpty()) {
            printProfileUsage()
            return 1
        }
        
        return when (args[0]) {
            "list" -> { println("${YELLOW}Profile listing not yet implemented${RESET}"); 0 }
            "activate" -> { println("${YELLOW}Profile activation not yet implemented${RESET}"); 0 }
            "export" -> { println("${YELLOW}Profile export not yet implemented${RESET}"); 0 }
            "import" -> { println("${YELLOW}Profile import not yet implemented${RESET}"); 0 }
            "--help", "-h" -> { printProfileUsage(); 0 }
            else -> {
                printError("Unknown profile command: ${args[0]}")
                1
            }
        }
    }
    
    // ========== Device Commands ==========
    
    private fun handleDeviceCommand(args: List<String>): Int {
        if (deviceCommands == null) {
            printError("Device API commands not available")
            println("${YELLOW}DeviceCommands not injected. Ensure Hilt is properly configured.${RESET}")
            return 1
        }
        
        return deviceCommands.execute(args)
    }
    
    // ========== Option Parsing ==========
    
    private data class BackupCreateOptions(
        val output: String,
        val type: BackupType,
        val includeDotfiles: Boolean
    )
    
    private fun parseBackupCreateOptions(args: List<String>): BackupCreateOptions {
        var output = com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH + "/termux-backup-${System.currentTimeMillis()}.json"
        var type = BackupType.FULL
        var includeDotfiles = true
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--output", "-o" -> {
                    output = args.getOrNull(i + 1) ?: output
                    i += 2
                }
                "--type", "-t" -> {
                    type = when (args.getOrNull(i + 1)?.lowercase()) {
                        "packages", "packages-only" -> BackupType.PACKAGES_ONLY
                        "config", "config-only" -> BackupType.CONFIG_ONLY
                        "minimal" -> BackupType.MINIMAL
                        else -> BackupType.FULL
                    }
                    i += 2
                }
                "--no-dotfiles" -> {
                    includeDotfiles = false
                    i++
                }
                else -> i++
            }
        }
        
        return BackupCreateOptions(output, type, includeDotfiles)
    }
    
    private fun parseRestoreOptions(args: List<String>): RestoreOptions {
        return RestoreOptions(
            dryRun = "--dry-run" in args || "-n" in args,
            selectiveRestore = "--selective" in args || "-s" in args,
            restoreRepositories = "--skip-repos" !in args,
            restoreDotfiles = "--skip-dotfiles" !in args,
            skipCompatibilityCheck = "--force" in args || "-f" in args
        )
    }
    
    // ========== Usage Messages ==========
    
    private fun printUsage() {
        println("""
            ${BOLD}$APP_NAME${RESET} - Termux Kotlin Control Utility
            
            ${BOLD}Usage:${RESET} $APP_NAME <command> [options]
            
            ${BOLD}Commands:${RESET}
              backup    Backup and restore package configurations
              pkg       Package management and health checks
              repo      Repository management
              profile   Profile management
              device    Access device APIs (battery, sensors, etc.)
            
            ${BOLD}Options:${RESET}
              --help, -h       Show this help message
              --version, -v    Show version information
            
            Run '${CYAN}$APP_NAME <command> --help${RESET}' for command-specific help.
        """.trimIndent())
    }
    
    private fun printBackupUsage() {
        println("""
            ${BOLD}$APP_NAME backup${RESET} - Backup and restore package configurations
            
            ${BOLD}Commands:${RESET}
              create [options]    Create a new backup
              restore <path>      Restore from backup
              list                List available backups
            
            ${BOLD}Options for 'create':${RESET}
              --output, -o <path>   Output file path
              --type, -t <type>     Backup type: full, packages, config, minimal
              --no-dotfiles         Don't include dotfiles
            
            ${BOLD}Options for 'restore':${RESET}
              --dry-run, -n         Show what would be restored without changes
              --selective, -s       Interactively select packages to restore
              --skip-repos          Don't restore repository configuration
              --skip-dotfiles       Don't restore dotfiles
              --force, -f           Skip compatibility checks
            
            ${BOLD}Examples:${RESET}
              ${CYAN}$APP_NAME backup create${RESET}
              ${CYAN}$APP_NAME backup create --type minimal --output ~/backup.json${RESET}
              ${CYAN}$APP_NAME backup restore ~/backup.json --dry-run${RESET}
        """.trimIndent())
    }
    
    private fun printPackageUsage() {
        println("""
            ${BOLD}$APP_NAME pkg${RESET} - Package management and health checks
            
            ${BOLD}Commands:${RESET}
              doctor [options]    Run package health diagnostics
              upgrade [options]   Upgrade packages
            
            ${BOLD}Options for 'doctor':${RESET}
              --auto-repair, -r   Attempt to fix issues automatically
              --verbose, -v       Show all issues (not just first 5 per category)
            
            ${BOLD}Options for 'upgrade':${RESET}
              --safe, -s          Safe upgrade (preserves held packages)
            
            ${BOLD}Examples:${RESET}
              ${CYAN}$APP_NAME pkg doctor${RESET}
              ${CYAN}$APP_NAME pkg doctor --auto-repair${RESET}
              ${CYAN}$APP_NAME pkg upgrade --safe${RESET}
        """.trimIndent())
    }
    
    private fun printRepoUsage() {
        println("""
            ${BOLD}$APP_NAME repo${RESET} - Repository management
            
            ${BOLD}Commands:${RESET}
              list                List configured repositories
              add <name> <url>    Add a new repository
              remove <name>       Remove a repository
              enable <name>       Enable a disabled repository
              disable <name>      Disable a repository
            
            ${BOLD}Examples:${RESET}
              ${CYAN}$APP_NAME repo list${RESET}
              ${CYAN}$APP_NAME repo add tur-repo https://tur.tur-repo.org${RESET}
        """.trimIndent())
    }
    
    private fun printProfileUsage() {
        println("""
            ${BOLD}$APP_NAME profile${RESET} - Profile management
            
            ${BOLD}Commands:${RESET}
              list                  List available profiles
              activate <name>       Activate a profile
              export <name> [path]  Export profile to JSON
              import <path>         Import profile from JSON
            
            ${BOLD}Examples:${RESET}
              ${CYAN}$APP_NAME profile list${RESET}
              ${CYAN}$APP_NAME profile activate "Developer"${RESET}
              ${CYAN}$APP_NAME profile export "Work" --output ~/work-profile.json${RESET}
        """.trimIndent())
    }
    
    private fun printVersion() {
        println("$APP_NAME version $VERSION")
        println("Termux Kotlin App")
    }
    
    private fun printError(message: String) {
        System.err.println("${RED}Error:${RESET} $message")
    }
}
