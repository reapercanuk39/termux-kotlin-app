package com.termux.app.pkg.backup

import kotlinx.serialization.Serializable

/**
 * Backup metadata containing all information needed to restore a Termux environment.
 */
@Serializable
data class BackupMetadata(
    /** Backup format version for future compatibility */
    val version: Int = CURRENT_VERSION,
    
    /** Timestamp when backup was created */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** Termux app version that created this backup */
    val termuxVersion: String,
    
    /** Android SDK version */
    val androidVersion: Int,
    
    /** Device model for reference */
    val deviceModel: String,
    
    /** Type of backup (full, packages-only, etc.) */
    val backupType: BackupType,
    
    /** The actual backup contents */
    val contents: BackupContents
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Container for all backup data.
 */
@Serializable
data class BackupContents(
    /** List of installed packages with metadata */
    val packages: List<PackageInfo>,
    
    /** Configured repositories */
    val repositories: List<RepositoryInfo>,
    
    /** Packages marked as held (won't be upgraded) */
    val heldPackages: List<String> = emptyList(),
    
    /** Paths of included dotfiles (relative to $HOME) */
    val includedDotfiles: List<String> = emptyList(),
    
    /** Whether user data was included in backup */
    val includedUserData: Boolean = false
)

/**
 * Information about an installed package.
 */
@Serializable
data class PackageInfo(
    /** Package name (e.g., "vim", "python") */
    val name: String,
    
    /** Installed version */
    val version: String,
    
    /** Package architecture (e.g., "aarch64", "arm") */
    val architecture: String,
    
    /** True if package was explicitly installed (not a dependency) */
    val isManuallyInstalled: Boolean,
    
    /** Repository that provided this package */
    val repository: String? = null
)

/**
 * Information about a configured repository.
 */
@Serializable
data class RepositoryInfo(
    /** Repository name (e.g., "main", "root", "science") */
    val name: String,
    
    /** Repository URL */
    val url: String,
    
    /** Distribution components (e.g., ["stable", "main"]) */
    val components: List<String> = emptyList(),
    
    /** Whether this repository is enabled */
    val isEnabled: Boolean = true,
    
    /** GPG key fingerprint for signature verification */
    val fingerprint: String? = null
)

/**
 * Types of backups that can be created.
 */
enum class BackupType {
    /** All packages + configuration + optional user data */
    FULL,
    
    /** Only package list (for quick reinstall) */
    PACKAGES_ONLY,
    
    /** Only dotfiles and configuration */
    CONFIG_ONLY,
    
    /** Only manually installed packages (smallest backup) */
    MINIMAL
}

/**
 * Configuration for backup creation.
 */
data class BackupConfig(
    /** Type of backup to create */
    val backupType: BackupType = BackupType.FULL,
    
    /** Include dotfiles in backup */
    val includeDotfiles: Boolean = true,
    
    /** Dotfile paths to include (relative to $HOME) */
    val dotfilePaths: List<String> = DEFAULT_DOTFILES,
    
    /** Include user data (larger backup, longer time) */
    val includeUserData: Boolean = false
) {
    companion object {
        val DEFAULT_DOTFILES = listOf(
            ".bashrc",
            ".bash_profile",
            ".profile",
            ".zshrc",
            ".vimrc",
            ".gitconfig",
            ".tmux.conf",
            ".config/nvim",
            ".ssh/config"
        )
    }
}

/**
 * Options for restore operation.
 */
data class RestoreOptions(
    /** Preview what would be restored without making changes */
    val dryRun: Boolean = false,
    
    /** Skip version/compatibility checks */
    val skipCompatibilityCheck: Boolean = false,
    
    /** Restore repository configuration */
    val restoreRepositories: Boolean = true,
    
    /** Restore dotfiles */
    val restoreDotfiles: Boolean = true,
    
    /** Allow selecting specific packages to restore */
    val selectiveRestore: Boolean = false,
    
    /** Specific packages to restore (when selectiveRestore is true) */
    val selectedPackages: List<String> = emptyList()
)

/**
 * Result of a restore operation.
 */
data class RestoreResult(
    /** Whether this was a dry run */
    val isDryRun: Boolean,
    
    /** Packages that were/would be installed */
    val packagesToInstall: List<String>,
    
    /** Repositories that were/would be added */
    val repositoriesToAdd: List<String>,
    
    /** Any warnings encountered during restore */
    val warnings: List<String>,
    
    /** Packages that failed to install (if not dry run) */
    val failedPackages: List<String> = emptyList()
)

/**
 * Progress updates during backup/restore operations.
 */
sealed class BackupProgress {
    /** No operation in progress */
    data object Idle : BackupProgress()
    
    /** Operation is starting */
    data object Starting : BackupProgress()
    
    /** Collecting installed packages */
    data object CollectingPackages : BackupProgress()
    
    /** Collecting repository information */
    data object CollectingRepositories : BackupProgress()
    
    /** Collecting dotfiles */
    data object CollectingDotfiles : BackupProgress()
    
    /** Writing backup to disk */
    data object Writing : BackupProgress()
    
    /** Restoring repository configuration */
    data object RestoringRepositories : BackupProgress()
    
    /** Updating package lists (apt update) */
    data object UpdatingPackageLists : BackupProgress()
    
    /** Installing packages with progress */
    data class InstallingPackages(val current: Int, val total: Int) : BackupProgress() {
        val percent: Int get() = if (total > 0) (current * 100) / total else 0
    }
    
    /** Restoring dotfiles */
    data object RestoringDotfiles : BackupProgress()
    
    /** Operation completed successfully */
    data class Completed(val path: String) : BackupProgress()
    
    /** Operation failed */
    data class Failed(val error: String) : BackupProgress()
}
