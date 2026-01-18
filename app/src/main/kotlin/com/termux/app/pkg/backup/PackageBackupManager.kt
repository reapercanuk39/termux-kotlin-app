package com.termux.app.pkg.backup

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages package backup and restore operations.
 * 
 * This manager provides functionality to:
 * - Create backups of installed packages, repositories, and dotfiles
 * - Restore from backup with selective or full restore options
 * - Track progress during backup/restore operations
 * 
 * Usage:
 * ```
 * val result = backupManager.createBackup(
 *     config = BackupConfig(backupType = BackupType.FULL),
 *     outputPath = "/path/to/backup.json"
 * )
 * ```
 */
@Singleton
class PackageBackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { 
        prettyPrint = true 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val _progress = MutableStateFlow<BackupProgress>(BackupProgress.Idle)
    val progress: StateFlow<BackupProgress> = _progress.asStateFlow()
    
    private val termuxPrefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH
    private val termuxHome = TermuxConstants.TERMUX_HOME_DIR_PATH
    
    /**
     * Create a backup with the specified configuration.
     * 
     * @param config Backup configuration specifying what to include
     * @param outputPath Path where backup file will be written
     * @return Result containing BackupMetadata on success or exception on failure
     */
    suspend fun createBackup(
        config: BackupConfig,
        outputPath: String
    ): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        try {
            _progress.value = BackupProgress.Starting
            
            // Step 1: Collect installed packages
            _progress.value = BackupProgress.CollectingPackages
            val packages = when (config.backupType) {
                BackupType.CONFIG_ONLY -> emptyList()
                BackupType.MINIMAL -> collectManuallyInstalledPackages()
                else -> collectAllInstalledPackages()
            }
            
            // Step 2: Collect repositories
            _progress.value = BackupProgress.CollectingRepositories
            val repositories = if (config.backupType != BackupType.CONFIG_ONLY) {
                collectRepositories()
            } else {
                emptyList()
            }
            
            // Step 3: Collect held packages
            val heldPackages = if (config.backupType != BackupType.CONFIG_ONLY) {
                collectHeldPackages()
            } else {
                emptyList()
            }
            
            // Step 4: Collect dotfiles if requested
            val dotfiles = if (config.includeDotfiles) {
                _progress.value = BackupProgress.CollectingDotfiles
                config.dotfilePaths.filter { path ->
                    File("$termuxHome/$path").exists()
                }
            } else {
                emptyList()
            }
            
            // Step 5: Create metadata
            val metadata = BackupMetadata(
                termuxVersion = getTermuxVersion(),
                androidVersion = Build.VERSION.SDK_INT,
                deviceModel = Build.MODEL,
                backupType = config.backupType,
                contents = BackupContents(
                    packages = packages,
                    repositories = repositories,
                    heldPackages = heldPackages,
                    includedDotfiles = dotfiles,
                    includedUserData = config.includeUserData
                )
            )
            
            // Step 6: Write backup file
            _progress.value = BackupProgress.Writing
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            
            if (config.backupType == BackupType.FULL && config.includeUserData) {
                // For full backups with user data, create a tar.gz archive
                createFullBackupArchive(metadata, config, outputPath)
            } else {
                // JSON-only backup
                outputFile.writeText(json.encodeToString(metadata))
            }
            
            _progress.value = BackupProgress.Completed(outputPath)
            Result.success(metadata)
            
        } catch (e: Exception) {
            _progress.value = BackupProgress.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
    
    /**
     * Restore from a backup file.
     * 
     * @param backupPath Path to the backup file
     * @param options Restore options (dry-run, selective, etc.)
     * @return Result containing RestoreResult on success
     */
    suspend fun restoreBackup(
        backupPath: String,
        options: RestoreOptions
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        try {
            _progress.value = BackupProgress.Starting
            
            // Step 1: Parse backup file
            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Backup file not found: $backupPath"))
            }
            
            val metadata = json.decodeFromString<BackupMetadata>(backupFile.readText())
            
            // Step 2: Validate compatibility
            if (!options.skipCompatibilityCheck) {
                validateCompatibility(metadata)
            }
            
            // Step 3: Determine packages to install
            val packagesToInstall = if (options.selectiveRestore) {
                options.selectedPackages
            } else {
                metadata.contents.packages.map { it.name }
            }
            
            // Step 4: Dry run - just return what would happen
            if (options.dryRun) {
                _progress.value = BackupProgress.Completed(backupPath)
                return@withContext Result.success(
                    RestoreResult(
                        isDryRun = true,
                        packagesToInstall = packagesToInstall,
                        repositoriesToAdd = metadata.contents.repositories.map { it.name },
                        warnings = generateDryRunWarnings(metadata)
                    )
                )
            }
            
            // Step 5: Restore repositories
            if (options.restoreRepositories) {
                _progress.value = BackupProgress.RestoringRepositories
                metadata.contents.repositories.forEach { repo ->
                    restoreRepository(repo)
                }
            }
            
            // Step 6: Update package lists
            _progress.value = BackupProgress.UpdatingPackageLists
            runCommand("apt update")
            
            // Step 7: Install packages
            val failedPackages = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            
            packagesToInstall.forEachIndexed { index, pkg ->
                _progress.value = BackupProgress.InstallingPackages(index + 1, packagesToInstall.size)
                try {
                    val result = runCommand("apt install -y $pkg 2>&1")
                    if (result.contains("E:") || result.contains("Unable to locate")) {
                        failedPackages.add(pkg)
                        warnings.add("Failed to install $pkg: package not found or error occurred")
                    }
                } catch (e: Exception) {
                    failedPackages.add(pkg)
                    warnings.add("Failed to install $pkg: ${e.message}")
                }
            }
            
            // Step 8: Restore held packages
            metadata.contents.heldPackages.forEach { pkg ->
                runCommand("apt-mark hold $pkg")
            }
            
            // Step 9: Restore dotfiles if included
            if (metadata.contents.includedDotfiles.isNotEmpty() && options.restoreDotfiles) {
                _progress.value = BackupProgress.RestoringDotfiles
                restoreDotfiles(backupPath, metadata.contents.includedDotfiles)
            }
            
            _progress.value = BackupProgress.Completed(backupPath)
            Result.success(
                RestoreResult(
                    isDryRun = false,
                    packagesToInstall = packagesToInstall,
                    repositoriesToAdd = metadata.contents.repositories.map { it.name },
                    warnings = warnings,
                    failedPackages = failedPackages
                )
            )
            
        } catch (e: Exception) {
            _progress.value = BackupProgress.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
    
    /**
     * List available backup files in the default backup directory.
     */
    suspend fun listBackups(): List<BackupFileInfo> = withContext(Dispatchers.IO) {
        val backupDir = File(termuxHome)
        backupDir.listFiles { file ->
            file.name.startsWith("termux-backup") && 
            (file.name.endsWith(".json") || file.name.endsWith(".tar.gz"))
        }?.map { file ->
            BackupFileInfo(
                path = file.absolutePath,
                name = file.name,
                size = file.length(),
                createdAt = file.lastModified()
            )
        }?.sortedByDescending { it.createdAt } ?: emptyList()
    }
    
    /**
     * Generate a default backup filename.
     */
    fun generateBackupFilename(type: BackupType = BackupType.FULL): String {
        val timestamp = System.currentTimeMillis()
        val suffix = when (type) {
            BackupType.FULL -> "full"
            BackupType.PACKAGES_ONLY -> "packages"
            BackupType.CONFIG_ONLY -> "config"
            BackupType.MINIMAL -> "minimal"
        }
        return "$termuxHome/termux-backup-$suffix-$timestamp.json"
    }
    
    // ========== Private Helper Methods ==========
    
    private suspend fun collectAllInstalledPackages(): List<PackageInfo> {
        val output = runCommand(
            "dpkg-query -W -f='\${Package}|\${Version}|\${Architecture}|\${Status}\\n'"
        )
        val manuallyInstalled = runCommand("apt-mark showmanual").lines().toSet()
        
        return output.lines()
            .filter { it.isNotBlank() && it.contains("install ok installed") }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 3) {
                    PackageInfo(
                        name = parts[0].trim(),
                        version = parts[1].trim(),
                        architecture = parts[2].trim(),
                        isManuallyInstalled = parts[0].trim() in manuallyInstalled,
                        repository = null
                    )
                } else null
            }
    }
    
    private suspend fun collectManuallyInstalledPackages(): List<PackageInfo> {
        return collectAllInstalledPackages().filter { it.isManuallyInstalled }
    }
    
    private suspend fun collectRepositories(): List<RepositoryInfo> {
        val repoList = mutableListOf<RepositoryInfo>()
        
        // Parse main sources.list
        val sourcesListPath = "$termuxPrefix/etc/apt/sources.list"
        if (File(sourcesListPath).exists()) {
            val content = runCommand("cat $sourcesListPath")
            repoList.addAll(parseSourcesList(content, "main"))
        }
        
        // Parse sources.list.d directory
        val sourcesListD = File("$termuxPrefix/etc/apt/sources.list.d")
        if (sourcesListD.exists() && sourcesListD.isDirectory) {
            sourcesListD.listFiles { file -> file.name.endsWith(".list") }?.forEach { file ->
                val content = file.readText()
                val name = file.nameWithoutExtension
                repoList.addAll(parseSourcesList(content, name))
            }
        }
        
        return repoList
    }
    
    private fun parseSourcesList(content: String, sourceName: String): List<RepositoryInfo> {
        return content.lines()
            .filter { it.trim().startsWith("deb ") && !it.trim().startsWith("#") }
            .map { line ->
                val parts = line.trim().removePrefix("deb ").split(" ").filter { it.isNotBlank() }
                RepositoryInfo(
                    name = sourceName,
                    url = parts.getOrNull(0) ?: "",
                    components = parts.drop(1),
                    isEnabled = true,
                    fingerprint = null
                )
            }
    }
    
    private suspend fun collectHeldPackages(): List<String> {
        return runCommand("apt-mark showhold").lines().filter { it.isNotBlank() }
    }
    
    private fun validateCompatibility(metadata: BackupMetadata) {
        if (metadata.version > BackupMetadata.CURRENT_VERSION) {
            throw IllegalStateException(
                "Backup was created with a newer version (${metadata.version}). " +
                "Please update the app to restore this backup."
            )
        }
    }
    
    private fun generateDryRunWarnings(metadata: BackupMetadata): List<String> {
        val warnings = mutableListOf<String>()
        
        if (metadata.androidVersion != Build.VERSION.SDK_INT) {
            warnings.add(
                "Backup was created on Android ${metadata.androidVersion}, " +
                "current device is Android ${Build.VERSION.SDK_INT}"
            )
        }
        
        if (metadata.version < BackupMetadata.CURRENT_VERSION) {
            warnings.add("Backup format is older, some features may not be restored")
        }
        
        return warnings
    }
    
    private suspend fun restoreRepository(repo: RepositoryInfo) {
        // Only restore if it's a custom repo (not main Termux repo)
        if (repo.name != "main" && repo.url.isNotBlank()) {
            val sourcesListD = "$termuxPrefix/etc/apt/sources.list.d"
            val repoFile = File("$sourcesListD/${repo.name}.list")
            
            val content = buildString {
                if (!repo.isEnabled) append("# ")
                append("deb ")
                append(repo.url)
                if (repo.components.isNotEmpty()) {
                    append(" ")
                    append(repo.components.joinToString(" "))
                }
            }
            
            repoFile.parentFile?.mkdirs()
            repoFile.writeText(content)
        }
    }
    
    private suspend fun createFullBackupArchive(
        metadata: BackupMetadata,
        config: BackupConfig,
        outputPath: String
    ) {
        // Write metadata to temp file
        val metadataFile = File.createTempFile("backup-metadata", ".json")
        metadataFile.writeText(json.encodeToString(metadata))
        
        // Create tar.gz with metadata and dotfiles
        val tarPath = outputPath.replace(".json", ".tar.gz")
        val filesToBackup = mutableListOf(metadataFile.absolutePath)
        
        if (config.includeDotfiles) {
            config.dotfilePaths.forEach { path ->
                val fullPath = "$termuxHome/$path"
                if (File(fullPath).exists()) {
                    filesToBackup.add(fullPath)
                }
            }
        }
        
        runCommand("tar -czf $tarPath ${filesToBackup.joinToString(" ")}")
        
        // Clean up temp file
        metadataFile.delete()
    }
    
    private suspend fun restoreDotfiles(backupPath: String, dotfiles: List<String>) {
        if (backupPath.endsWith(".tar.gz")) {
            // Extract dotfiles from archive
            runCommand("tar -xzf $backupPath -C $termuxHome")
        }
        // For JSON-only backups, dotfiles are not included in the file
    }
    
    private suspend fun runCommand(command: String): String = withContext(Dispatchers.IO) {
        val process = Runtime.getRuntime().exec(arrayOf(
            "$termuxPrefix/bin/bash",
            "-c",
            command
        ))
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output
    }
    
    private fun getTermuxVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}

/**
 * Information about a backup file.
 */
data class BackupFileInfo(
    val path: String,
    val name: String,
    val size: Long,
    val createdAt: Long
) {
    val sizeFormatted: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
}
