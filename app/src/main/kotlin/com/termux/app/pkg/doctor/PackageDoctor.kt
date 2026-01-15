package com.termux.app.pkg.doctor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Package health diagnostics and repair tool.
 * 
 * Provides comprehensive package system health checks including:
 * - Broken package detection
 * - Missing dependency identification
 * - Held package listing
 * - Version mismatch detection
 * - Orphaned package identification
 * - Repository health verification
 * 
 * Usage:
 * ```
 * val report = doctor.runFullDiagnostic()
 * if (report.needsAttention) {
 *     doctor.autoRepair(report.issues)
 * }
 * ```
 */
@Singleton
class PackageDoctor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _progress = MutableStateFlow<DiagnosticProgress>(DiagnosticProgress.Idle)
    val progress: StateFlow<DiagnosticProgress> = _progress.asStateFlow()
    
    private val termuxPrefix = "/data/data/com.termux/files/usr"
    
    /**
     * Run a full diagnostic scan of the package system.
     * 
     * @param config Configuration for which checks to run
     * @return Complete diagnostic report
     */
    suspend fun runFullDiagnostic(
        config: DoctorConfig = DoctorConfig()
    ): DiagnosticReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val issues = mutableListOf<DiagnosticIssue>()
        val recommendations = mutableListOf<String>()
        
        // Check 1: Broken packages
        if (config.checkBroken) {
            _progress.value = DiagnosticProgress.Running("Checking broken packages...")
            issues.addAll(checkBrokenPackages())
        }
        
        // Check 2: Missing dependencies
        if (config.checkDependencies) {
            _progress.value = DiagnosticProgress.Running("Checking dependencies...")
            issues.addAll(checkMissingDependencies())
        }
        
        // Check 3: Held packages
        if (config.checkHeld) {
            _progress.value = DiagnosticProgress.Running("Checking held packages...")
            issues.addAll(checkHeldPackages())
        }
        
        // Check 4: Version mismatches (upgradable packages)
        if (config.checkVersions) {
            _progress.value = DiagnosticProgress.Running("Checking version mismatches...")
            issues.addAll(checkVersionMismatches(config.maxUpgradableToReport))
        }
        
        // Check 5: Orphaned packages
        if (config.checkOrphaned) {
            _progress.value = DiagnosticProgress.Running("Checking orphaned packages...")
            issues.addAll(checkOrphanedPackages(config.maxOrphanedToReport))
        }
        
        // Check 6: Repository health
        if (config.checkRepositories) {
            _progress.value = DiagnosticProgress.Running("Checking repository health...")
            issues.addAll(checkRepositoryHealth())
        }
        
        // Generate recommendations based on issues found
        recommendations.addAll(generateRecommendations(issues))
        
        _progress.value = DiagnosticProgress.Completed
        
        val duration = System.currentTimeMillis() - startTime
        
        DiagnosticReport(
            timestamp = System.currentTimeMillis(),
            issues = issues,
            recommendations = recommendations,
            healthScore = calculateHealthScore(issues),
            scanDurationMs = duration
        )
    }
    
    /**
     * Attempt to automatically repair detected issues.
     * 
     * @param issues List of issues to repair (typically from a diagnostic report)
     * @return Result of repair attempts
     */
    suspend fun autoRepair(
        issues: List<DiagnosticIssue>
    ): RepairResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val repaired = mutableListOf<String>()
        val failed = mutableListOf<String>()
        
        // Filter to only actionable issues with suggested fixes
        // Group by fix command to avoid running the same command multiple times
        val fixes = issues
            .filter { it.severity >= IssueSeverity.MEDIUM }
            .mapNotNull { it.suggestedFix }
            .distinct()
        
        fixes.forEachIndexed { index, fix ->
            _progress.value = DiagnosticProgress.Repairing("Running fix ${index + 1}/${fixes.size}")
            try {
                val result = runCommand(fix)
                if (result.contains("E:") || result.contains("error")) {
                    failed.add("$fix: Command reported errors")
                } else {
                    repaired.add(fix)
                }
            } catch (e: Exception) {
                failed.add("$fix: ${e.message}")
            }
        }
        
        _progress.value = DiagnosticProgress.Completed
        
        RepairResult(
            repaired = repaired,
            failed = failed,
            durationMs = System.currentTimeMillis() - startTime
        )
    }
    
    /**
     * Quick health check - returns true if system is healthy.
     */
    suspend fun isHealthy(): Boolean {
        val report = runFullDiagnostic(DoctorConfig(
            checkVersions = false,  // Skip version checks for quick scan
            checkOrphaned = false   // Skip orphaned checks for quick scan
        ))
        return report.isHealthy
    }
    
    // ========== Check Implementations ==========
    
    private suspend fun checkBrokenPackages(): List<DiagnosticIssue> {
        val output = runCommand("dpkg --audit 2>&1")
        if (output.isBlank()) return emptyList()
        
        return output.lines()
            .filter { it.isNotBlank() }
            .map { line ->
                val packageName = line.split(" ").firstOrNull()?.trim()
                DiagnosticIssue(
                    type = IssueType.BROKEN_PACKAGE,
                    severity = IssueSeverity.HIGH,
                    description = "Broken package: ${packageName ?: line}",
                    affectedPackage = packageName,
                    suggestedFix = "apt --fix-broken install -y",
                    details = line
                )
            }
    }
    
    private suspend fun checkMissingDependencies(): List<DiagnosticIssue> {
        val output = runCommand("apt-get check 2>&1")
        val issues = mutableListOf<DiagnosticIssue>()
        
        if (output.contains("unmet dependencies") || output.contains("Depends:")) {
            // Parse dependency errors
            val depRegex = Regex("(\\S+)\\s*:\\s*Depends:\\s*(\\S+)")
            depRegex.findAll(output).forEach { match ->
                val pkg = match.groupValues[1]
                val dep = match.groupValues[2]
                issues.add(
                    DiagnosticIssue(
                        type = IssueType.MISSING_DEPENDENCY,
                        severity = IssueSeverity.HIGH,
                        description = "$pkg is missing dependency: $dep",
                        affectedPackage = pkg,
                        suggestedFix = "apt install -f -y",
                        details = "Required dependency '$dep' is not installed or has wrong version"
                    )
                )
            }
        }
        
        // Also check for broken installs
        if (output.contains("The following packages have unmet dependencies")) {
            issues.add(
                DiagnosticIssue(
                    type = IssueType.MISSING_DEPENDENCY,
                    severity = IssueSeverity.HIGH,
                    description = "System has unmet dependencies",
                    affectedPackage = null,
                    suggestedFix = "apt install -f -y"
                )
            )
        }
        
        return issues
    }
    
    private suspend fun checkHeldPackages(): List<DiagnosticIssue> {
        val heldPackages = runCommand("apt-mark showhold").lines().filter { it.isNotBlank() }
        
        return heldPackages.map { pkg ->
            DiagnosticIssue(
                type = IssueType.HELD_PACKAGE,
                severity = IssueSeverity.LOW,
                description = "Package '$pkg' is held back from upgrades",
                affectedPackage = pkg,
                suggestedFix = "apt-mark unhold $pkg",
                details = "This package will not be upgraded until hold is removed"
            )
        }
    }
    
    private suspend fun checkVersionMismatches(maxToReport: Int): List<DiagnosticIssue> {
        val output = runCommand("apt list --upgradable 2>/dev/null")
        
        return output.lines()
            .filter { it.contains("[upgradable from:") }
            .take(maxToReport)
            .map { line ->
                val pkg = line.split("/").firstOrNull()?.trim()
                val versionMatch = Regex("\\[upgradable from: ([^\\]]+)\\]").find(line)
                val currentVersion = versionMatch?.groupValues?.get(1) ?: "unknown"
                
                DiagnosticIssue(
                    type = IssueType.VERSION_MISMATCH,
                    severity = IssueSeverity.INFO,
                    description = "Package '$pkg' has update available",
                    affectedPackage = pkg,
                    suggestedFix = "apt upgrade $pkg -y",
                    details = "Current version: $currentVersion"
                )
            }
    }
    
    private suspend fun checkOrphanedPackages(maxToReport: Int): List<DiagnosticIssue> {
        val output = runCommand("apt-get autoremove --dry-run 2>/dev/null")
        
        val orphaned = output.lines()
            .filter { it.trim().startsWith("Remv ") }
            .take(maxToReport)
            .map { line ->
                line.trim().removePrefix("Remv ").split(" ").firstOrNull()?.trim()
            }
            .filterNotNull()
        
        if (orphaned.isEmpty()) return emptyList()
        
        return orphaned.map { pkg ->
            DiagnosticIssue(
                type = IssueType.ORPHANED_PACKAGE,
                severity = IssueSeverity.INFO,
                description = "Package '$pkg' is no longer needed",
                affectedPackage = pkg,
                suggestedFix = "apt autoremove -y"
            )
        }
    }
    
    private suspend fun checkRepositoryHealth(): List<DiagnosticIssue> {
        val issues = mutableListOf<DiagnosticIssue>()
        val output = runCommand("apt update 2>&1")
        
        // Check for failed fetches
        if (output.contains("Failed to fetch")) {
            val failedRegex = Regex("Failed to fetch (\\S+)")
            failedRegex.findAll(output).forEach { match ->
                val url = match.groupValues[1]
                issues.add(
                    DiagnosticIssue(
                        type = IssueType.REPOSITORY_ERROR,
                        severity = IssueSeverity.MEDIUM,
                        description = "Failed to fetch from repository",
                        affectedPackage = null,
                        suggestedFix = null,
                        details = "URL: $url\nCheck network connection or repository availability"
                    )
                )
            }
        }
        
        // Check for missing GPG keys
        if (output.contains("NO_PUBKEY") || output.contains("public key")) {
            val keyRegex = Regex("NO_PUBKEY\\s+(\\S+)")
            keyRegex.findAll(output).forEach { match ->
                val keyId = match.groupValues[1]
                issues.add(
                    DiagnosticIssue(
                        type = IssueType.MISSING_GPG_KEY,
                        severity = IssueSeverity.MEDIUM,
                        description = "Missing GPG key: $keyId",
                        affectedPackage = null,
                        suggestedFix = "apt-key adv --keyserver keyserver.ubuntu.com --recv-keys $keyId",
                        details = "Repository signature cannot be verified without this key"
                    )
                )
            }
        }
        
        // Check for expired keys
        if (output.contains("EXPKEYSIG")) {
            issues.add(
                DiagnosticIssue(
                    type = IssueType.MISSING_GPG_KEY,
                    severity = IssueSeverity.MEDIUM,
                    description = "Repository has expired GPG key",
                    affectedPackage = null,
                    suggestedFix = "apt update --allow-insecure-repositories",
                    details = "GPG key has expired, repository may need to update their key"
                )
            )
        }
        
        return issues
    }
    
    // ========== Helper Methods ==========
    
    private fun calculateHealthScore(issues: List<DiagnosticIssue>): Int {
        var score = 100
        issues.forEach { issue ->
            score -= issue.severity.weight
        }
        return score.coerceIn(0, 100)
    }
    
    private fun generateRecommendations(issues: List<DiagnosticIssue>): List<String> {
        val recommendations = mutableListOf<String>()
        
        val brokenCount = issues.count { it.type == IssueType.BROKEN_PACKAGE }
        if (brokenCount > 0) {
            recommendations.add("Run 'apt --fix-broken install' to repair $brokenCount broken package(s)")
        }
        
        val depCount = issues.count { it.type == IssueType.MISSING_DEPENDENCY }
        if (depCount > 0) {
            recommendations.add("Run 'apt install -f' to install missing dependencies")
        }
        
        val orphanCount = issues.count { it.type == IssueType.ORPHANED_PACKAGE }
        if (orphanCount > 10) {
            recommendations.add("Run 'apt autoremove' to clean up $orphanCount orphaned packages")
        }
        
        val repoIssues = issues.count { 
            it.type == IssueType.REPOSITORY_ERROR || it.type == IssueType.MISSING_GPG_KEY 
        }
        if (repoIssues > 0) {
            recommendations.add("Check network connection and repository configuration")
        }
        
        val upgradeCount = issues.count { it.type == IssueType.VERSION_MISMATCH }
        if (upgradeCount > 5) {
            recommendations.add("Run 'apt upgrade' to update $upgradeCount packages")
        }
        
        if (recommendations.isEmpty() && issues.isEmpty()) {
            recommendations.add("Your package system is healthy! No issues detected.")
        }
        
        return recommendations
    }
    
    private suspend fun runCommand(command: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "$termuxPrefix/bin/bash",
                "-c",
                command
            ))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            output + error
        } catch (e: Exception) {
            ""
        }
    }
}
