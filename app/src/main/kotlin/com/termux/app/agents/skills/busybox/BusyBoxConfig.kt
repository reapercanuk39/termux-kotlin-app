package com.termux.app.agents.skills.busybox

import android.os.Build

/**
 * Configuration for BusyBox Modern integration.
 * 
 * BusyBox Modern is an external system binary that requires root access.
 * This configuration controls how the agent framework interacts with it.
 * 
 * ## Path Priority (based on Magisk research):
 * 
 * 1. Magisk module path (preferred for systemless install)
 * 2. /data/local/busybox-modern (user-installed, no root required for access)
 * 3. System paths (requires root to write, but readable without)
 * 
 * ## Android Version Considerations:
 * 
 * - Android 11+ (API 30+): /sbin removed, use /debug_ramdisk or /data paths
 * - Android 10 and below: /sbin available, traditional paths work
 * 
 * ## Avoiding Conflicts with Magisk's BusyBox:
 * 
 * Magisk bundles its own BusyBox at:
 * - $MAGISK_TMP/.magisk/busybox
 * - /data/adb/magisk/busybox
 * 
 * We use "busybox-modern" naming to avoid conflicts.
 */
data class BusyBoxConfig(
    /**
     * Path to the BusyBox Modern binary.
     * Default: Magisk module path for systemless installation
     */
    val binaryPath: String = DEFAULT_BINARY_PATH,
    
    /**
     * Alternative paths to check if primary path doesn't exist.
     * Ordered by preference: Magisk module > data/local > system paths
     */
    val fallbackPaths: List<String> = getDefaultFallbackPaths(),
    
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
        // Primary path - Magisk module (preferred for systemless)
        const val DEFAULT_BINARY_PATH = "/data/adb/modules/busybox-modern/system/bin/busybox"
        
        // Symlink directory for applets
        const val DEFAULT_SYMLINK_DIR = "/data/adb/modules/busybox-modern/system/xbin"
        
        // Magisk module root
        const val DEFAULT_MAGISK_MODULE_PATH = "/data/adb/modules/busybox-modern"
        
        // Magisk's own BusyBox paths (DO NOT OVERRIDE)
        val MAGISK_BUSYBOX_PATHS = listOf(
            "/data/adb/magisk/busybox",
            "/sbin/.magisk/busybox",
            "/debug_ramdisk/.magisk/busybox"
        )
        
        // SU binary paths by Android version (from Magisk research)
        val SU_PATHS_LEGACY = listOf(  // Android ≤ 10
            "/sbin/su",
            "/system/xbin/su",
            "/system/bin/su"
        )
        
        val SU_PATHS_MODERN = listOf(  // Android 11+
            "/debug_ramdisk/su",
            "/data/adb/ksu/bin/su",   // KernelSU
            "/data/adb/ap/bin/su"     // APatch
        )
        
        // Magisk daemon socket paths
        val MAGISK_SOCKET_PATHS = listOf(
            "/sbin/.magisk/device/socket",          // Android ≤ 10
            "/debug_ramdisk/.magisk/device/socket"  // Android 11+
        )
        
        /**
         * Get fallback paths based on Android version.
         */
        fun getDefaultFallbackPaths(): List<String> {
            val paths = mutableListOf<String>()
            
            // 1. Magisk module paths (highest priority)
            paths.add("/data/adb/modules/busybox-modern/system/bin/busybox")
            paths.add("/data/adb/modules/busybox-modern/system/xbin/busybox")
            
            // 2. Data local (no root needed to access, just to write)
            paths.add("/data/local/busybox-modern/busybox")
            
            // 3. Version-specific paths
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ - no /sbin
                paths.add("/debug_ramdisk/busybox-modern")
            } else {
                // Android 10 and below - /sbin exists
                paths.add("/sbin/busybox-modern")
            }
            
            // 4. System paths (lowest priority, requires system remount)
            paths.add("/system/xbin/busybox-modern")
            paths.add("/system/bin/busybox-modern")
            
            // 5. Generic busybox (not ours, but usable)
            paths.add("/system/xbin/busybox")
            paths.add("/system/bin/busybox")
            
            return paths
        }
        
        /**
         * Get SU binary paths for current Android version.
         */
        fun getSuPaths(): List<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SU_PATHS_MODERN + SU_PATHS_LEGACY  // Try modern first
            } else {
                SU_PATHS_LEGACY
            }
        }
        
        /**
         * Create default configuration.
         */
        fun default() = BusyBoxConfig()
        
        /**
         * Create configuration for Magisk-based installation (recommended).
         */
        fun forMagisk() = BusyBoxConfig(
            binaryPath = "/data/adb/modules/busybox-modern/system/bin/busybox",
            symlinkDir = "/data/adb/modules/busybox-modern/system/xbin",
            magiskModulePath = DEFAULT_MAGISK_MODULE_PATH
        )
        
        /**
         * Create configuration for KernelSU installation.
         */
        fun forKernelSu() = BusyBoxConfig(
            binaryPath = "/data/adb/ksu/modules/busybox-modern/system/bin/busybox",
            symlinkDir = "/data/adb/ksu/modules/busybox-modern/system/xbin",
            magiskModulePath = "/data/adb/ksu/modules/busybox-modern"
        )
        
        /**
         * Create configuration for non-root usage (limited functionality).
         */
        fun nonRoot() = BusyBoxConfig(
            binaryPath = "/data/local/busybox-modern/busybox",
            requireRoot = false,
            symlinkDir = "/data/local/busybox-modern/applets",
            magiskModulePath = null
        )
        
        /**
         * Create from map (for YAML config loading).
         */
        fun fromMap(map: Map<String, Any?>): BusyBoxConfig {
            return BusyBoxConfig(
                binaryPath = map["binary_path"] as? String ?: DEFAULT_BINARY_PATH,
                fallbackPaths = (map["fallback_paths"] as? List<*>)?.filterIsInstance<String>() 
                    ?: getDefaultFallbackPaths(),
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
    
    /**
     * Check if a path conflicts with Magisk's own BusyBox.
     */
    fun conflictsWithMagisk(path: String): Boolean {
        return MAGISK_BUSYBOX_PATHS.any { path.startsWith(it.substringBeforeLast('/')) }
    }
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


/**
 * Type of root solution detected.
 */
enum class RootType {
    NONE,       // No root installed
    MAGISK,     // Magisk (most common)
    KERNELSU,   // KernelSU (kernel-based)
    APATCH,     // APatch
    UNKNOWN     // Unknown root solution (su exists but type unknown)
}
