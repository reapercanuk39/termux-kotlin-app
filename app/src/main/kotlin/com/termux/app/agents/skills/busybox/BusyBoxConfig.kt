package com.termux.app.agents.skills.busybox

/**
 * Configuration for BusyBox Modern integration.
 * 
 * BusyBox Modern is an external system binary that requires root access.
 * This configuration controls how the agent framework interacts with it.
 */
data class BusyBoxConfig(
    /**
     * Path to the BusyBox Modern binary.
     * Default: /system/bin/busybox-modern
     */
    val binaryPath: String = DEFAULT_BINARY_PATH,
    
    /**
     * Alternative paths to check if primary path doesn't exist.
     */
    val fallbackPaths: List<String> = DEFAULT_FALLBACK_PATHS,
    
    /**
     * Whether root access is required for BusyBox operations.
     */
    val requireRoot: Boolean = true,
    
    /**
     * Directory for BusyBox symlinks.
     */
    val symlinkDir: String = DEFAULT_SYMLINK_DIR,
    
    /**
     * Timeout for BusyBox commands in milliseconds.
     */
    val commandTimeoutMs: Long = 60_000L,
    
    /**
     * Enable dry-run mode (show commands without executing).
     */
    val dryRun: Boolean = false,
    
    /**
     * Enable verbose logging for BusyBox operations.
     */
    val verbose: Boolean = false,
    
    /**
     * Magisk module path (if using Magisk installation).
     */
    val magiskModulePath: String? = DEFAULT_MAGISK_MODULE_PATH
) {
    companion object {
        const val DEFAULT_BINARY_PATH = "/system/bin/busybox-modern"
        const val DEFAULT_SYMLINK_DIR = "/data/local/busybox-modern"
        const val DEFAULT_MAGISK_MODULE_PATH = "/data/adb/modules/busybox-modern"
        
        val DEFAULT_FALLBACK_PATHS = listOf(
            "/system/xbin/busybox",
            "/data/local/busybox-modern/busybox",
            "/data/adb/modules/busybox-modern/system/bin/busybox",
            "/system/bin/busybox"
        )
        
        /**
         * Create default configuration.
         */
        fun default() = BusyBoxConfig()
        
        /**
         * Create configuration for Magisk-based installation.
         */
        fun forMagisk() = BusyBoxConfig(
            binaryPath = "/data/adb/modules/busybox-modern/system/bin/busybox",
            magiskModulePath = DEFAULT_MAGISK_MODULE_PATH
        )
        
        /**
         * Create configuration for non-root usage (limited functionality).
         */
        fun nonRoot() = BusyBoxConfig(
            binaryPath = "/data/local/busybox-modern/busybox",
            requireRoot = false,
            symlinkDir = "/data/local/tmp/busybox-symlinks"
        )
        
        /**
         * Create from map (for YAML config loading).
         */
        fun fromMap(map: Map<String, Any?>): BusyBoxConfig {
            return BusyBoxConfig(
                binaryPath = map["binary_path"] as? String ?: DEFAULT_BINARY_PATH,
                fallbackPaths = (map["fallback_paths"] as? List<*>)?.filterIsInstance<String>() 
                    ?: DEFAULT_FALLBACK_PATHS,
                requireRoot = map["require_root"] as? Boolean ?: true,
                symlinkDir = map["symlink_dir"] as? String ?: DEFAULT_SYMLINK_DIR,
                commandTimeoutMs = (map["command_timeout_ms"] as? Number)?.toLong() ?: 60_000L,
                dryRun = map["dry_run"] as? Boolean ?: false,
                verbose = map["verbose"] as? Boolean ?: false,
                magiskModulePath = map["magisk_module_path"] as? String
            )
        }
    }
    
    /**
     * Get all paths to check for BusyBox binary.
     */
    fun getAllPaths(): List<String> = listOf(binaryPath) + fallbackPaths
    
    /**
     * Check if this is a Magisk-based installation.
     */
    fun isMagiskInstall(): Boolean = magiskModulePath != null
}

/**
 * BusyBox diagnostic information.
 */
data class BusyBoxDiagnostics(
    val installed: Boolean,
    val binaryPath: String?,
    val version: String?,
    val appletCount: Int,
    val applets: List<String>,
    val symlinksValid: Boolean,
    val symlinkCount: Int,
    val pathIncluded: Boolean,
    val rootAvailable: Boolean,
    val magiskInstalled: Boolean,
    val issues: List<BusyBoxIssue>
) {
    /**
     * Check if BusyBox is fully operational.
     */
    fun isOperational(): Boolean = installed && symlinksValid && pathIncluded
    
    /**
     * Get severity of issues.
     */
    fun getMaxSeverity(): BusyBoxIssueSeverity? = issues.maxByOrNull { it.severity.ordinal }?.severity
}

/**
 * BusyBox issue detected during diagnostics.
 */
data class BusyBoxIssue(
    val code: String,
    val message: String,
    val severity: BusyBoxIssueSeverity,
    val suggestedFix: String?
)

/**
 * Issue severity levels.
 */
enum class BusyBoxIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Result of BusyBox repair operation.
 */
data class BusyBoxRepairResult(
    val success: Boolean,
    val repairsAttempted: Int,
    val repairsSucceeded: Int,
    val repairsFailed: Int,
    val details: List<RepairDetail>
)

/**
 * Detail of a single repair action.
 */
data class RepairDetail(
    val action: String,
    val target: String,
    val success: Boolean,
    val message: String
)

/**
 * Magisk module status.
 */
data class MagiskStatus(
    val installed: Boolean,
    val version: String?,
    val modulePath: String?,
    val moduleEnabled: Boolean,
    val systemlessMode: Boolean
)
