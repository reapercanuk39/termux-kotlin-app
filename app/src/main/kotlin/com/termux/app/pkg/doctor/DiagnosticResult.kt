package com.termux.app.pkg.doctor

/**
 * Types of issues that can be detected by the package doctor.
 */
enum class IssueType {
    /** Package is in a broken state */
    BROKEN_PACKAGE,
    
    /** Required dependency is missing */
    MISSING_DEPENDENCY,
    
    /** Package is held back from upgrades */
    HELD_PACKAGE,
    
    /** Installed version doesn't match repository version */
    VERSION_MISMATCH,
    
    /** Package is no longer needed (auto-removable) */
    ORPHANED_PACKAGE,
    
    /** Repository fetch failed */
    REPOSITORY_ERROR,
    
    /** GPG key is missing for repository */
    MISSING_GPG_KEY,
    
    /** Circular dependency detected */
    CIRCULAR_DEPENDENCY,
    
    /** Package files are missing or corrupted */
    CORRUPTED_FILES,
    
    /** Package configuration is incomplete */
    INCOMPLETE_CONFIG
}

/**
 * Severity levels for diagnostic issues.
 */
enum class IssueSeverity(val weight: Int) {
    /** Informational only, no action needed */
    INFO(1),
    
    /** Minor issue, optional fix */
    LOW(5),
    
    /** Should be fixed but not urgent */
    MEDIUM(10),
    
    /** Significant issue, should be fixed soon */
    HIGH(15),
    
    /** System may be unstable, fix immediately */
    CRITICAL(25);
    
    fun toEmoji(): String = when (this) {
        INFO -> "ℹ️"
        LOW -> "📝"
        MEDIUM -> "⚠️"
        HIGH -> "🔴"
        CRITICAL -> "💀"
    }
}

/**
 * A single diagnostic issue found by the package doctor.
 */
data class DiagnosticIssue(
    /** Type of issue */
    val type: IssueType,
    
    /** Severity level */
    val severity: IssueSeverity,
    
    /** Human-readable description */
    val description: String,
    
    /** Package affected (if applicable) */
    val affectedPackage: String? = null,
    
    /** Suggested command to fix */
    val suggestedFix: String? = null,
    
    /** Additional context or details */
    val details: String? = null
) {
    fun toCliString(): String {
        val icon = when (severity) {
            IssueSeverity.CRITICAL, IssueSeverity.HIGH -> "✗"
            IssueSeverity.MEDIUM -> "!"
            IssueSeverity.LOW -> "·"
            IssueSeverity.INFO -> "○"
        }
        return "$icon $description"
    }
}

/**
 * Complete diagnostic report from a doctor scan.
 */
data class DiagnosticReport(
    /** When the scan was performed */
    val timestamp: Long,
    
    /** All issues found */
    val issues: List<DiagnosticIssue>,
    
    /** Recommended actions */
    val recommendations: List<String>,
    
    /** Overall health score (0-100) */
    val healthScore: Int,
    
    /** Scan duration in milliseconds */
    val scanDurationMs: Long = 0
) {
    val issuesByType: Map<IssueType, List<DiagnosticIssue>>
        get() = issues.groupBy { it.type }
    
    val issuesBySeverity: Map<IssueSeverity, List<DiagnosticIssue>>
        get() = issues.groupBy { it.severity }
    
    val criticalCount: Int
        get() = issues.count { it.severity == IssueSeverity.CRITICAL }
    
    val highCount: Int
        get() = issues.count { it.severity == IssueSeverity.HIGH }
    
    val isHealthy: Boolean
        get() = healthScore >= 90
    
    val needsAttention: Boolean
        get() = criticalCount > 0 || highCount > 0
    
    fun toCliSummary(): String = buildString {
        appendLine("╔══════════════════════════════════════╗")
        appendLine("║      Package Health Report           ║")
        appendLine("╠══════════════════════════════════════╣")
        appendLine("║  Health Score: ${healthScore.toString().padStart(3)}%                  ║")
        appendLine("║  Issues Found: ${issues.size.toString().padStart(3)}                   ║")
        appendLine("╚══════════════════════════════════════╝")
    }
}

/**
 * Result of an auto-repair operation.
 */
data class RepairResult(
    /** Commands that executed successfully */
    val repaired: List<String>,
    
    /** Commands that failed with error messages */
    val failed: List<String>,
    
    /** Time taken in milliseconds */
    val durationMs: Long = 0
) {
    val wasSuccessful: Boolean
        get() = failed.isEmpty()
    
    val partialSuccess: Boolean
        get() = repaired.isNotEmpty() && failed.isNotEmpty()
}

/**
 * Progress updates during diagnostic scan.
 */
sealed class DiagnosticProgress {
    /** No scan in progress */
    data object Idle : DiagnosticProgress()
    
    /** Scan is running with current step description */
    data class Running(val step: String) : DiagnosticProgress()
    
    /** Scan completed */
    data object Completed : DiagnosticProgress()
    
    /** Repair in progress */
    data class Repairing(val step: String) : DiagnosticProgress()
}

/**
 * Configuration for doctor scan.
 */
data class DoctorConfig(
    /** Check for broken packages */
    val checkBroken: Boolean = true,
    
    /** Check for missing dependencies */
    val checkDependencies: Boolean = true,
    
    /** Check for held packages */
    val checkHeld: Boolean = true,
    
    /** Check for version mismatches (upgradable) */
    val checkVersions: Boolean = true,
    
    /** Check for orphaned packages */
    val checkOrphaned: Boolean = true,
    
    /** Check repository health */
    val checkRepositories: Boolean = true,
    
    /** Maximum orphaned packages to report (to avoid noise) */
    val maxOrphanedToReport: Int = 20,
    
    /** Maximum upgradable packages to report */
    val maxUpgradableToReport: Int = 20
)
