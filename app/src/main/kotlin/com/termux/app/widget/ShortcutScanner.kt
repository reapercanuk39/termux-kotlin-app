package com.termux.app.widget

import android.content.Context
import com.termux.shared.logger.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans and manages shortcut scripts for widgets.
 * 
 * Looks for scripts in:
 * - ~/.shortcuts/ - Standard shortcuts
 * - ~/.shortcuts/tasks/ - Background tasks (no terminal UI)
 * - ~/.shortcuts/icons/ - Custom icons for shortcuts
 */
@Singleton
class ShortcutScanner @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val LOG_TAG = "ShortcutScanner"
        private const val SHORTCUTS_DIR = "/data/data/com.termux/files/home/.shortcuts"
        private const val TASKS_SUBDIR = "tasks"
        private const val ICONS_SUBDIR = "icons"
    }
    
    /**
     * Shortcut info data class.
     */
    data class ShortcutInfo(
        val name: String,
        val displayName: String,
        val path: String,
        val isTask: Boolean,
        val iconPath: String?,
        val isExecutable: Boolean
    ) {
        /**
         * Get the script file.
         */
        fun getFile(): File = File(path)
        
        /**
         * Get icon file if exists.
         */
        fun getIconFile(): File? = iconPath?.let { File(it) }
    }
    
    /**
     * Get shortcuts directory.
     */
    fun getShortcutsDir(): File = File(SHORTCUTS_DIR)
    
    /**
     * Get tasks subdirectory.
     */
    fun getTasksDir(): File = File(SHORTCUTS_DIR, TASKS_SUBDIR)
    
    /**
     * Get icons subdirectory.
     */
    fun getIconsDir(): File = File(SHORTCUTS_DIR, ICONS_SUBDIR)
    
    /**
     * Ensure shortcuts directories exist.
     */
    fun ensureDirectories() {
        getShortcutsDir().mkdirs()
        getTasksDir().mkdirs()
        getIconsDir().mkdirs()
    }
    
    /**
     * Scan for all available shortcuts.
     */
    fun scanShortcuts(): List<ShortcutInfo> {
        val shortcuts = mutableListOf<ShortcutInfo>()
        
        // Scan main shortcuts directory
        val shortcutsDir = getShortcutsDir()
        if (shortcutsDir.exists() && shortcutsDir.isDirectory) {
            shortcutsDir.listFiles()?.forEach { file ->
                if (file.isFile && !file.name.startsWith(".")) {
                    // Skip subdirectories like tasks/ and icons/
                    if (file.name != TASKS_SUBDIR && file.name != ICONS_SUBDIR) {
                        shortcuts.add(createShortcutInfo(file, isTask = false))
                    }
                }
            }
        }
        
        // Scan tasks directory
        val tasksDir = getTasksDir()
        if (tasksDir.exists() && tasksDir.isDirectory) {
            tasksDir.listFiles()?.forEach { file ->
                if (file.isFile && !file.name.startsWith(".")) {
                    shortcuts.add(createShortcutInfo(file, isTask = true))
                }
            }
        }
        
        Logger.logDebug(LOG_TAG, "Found ${shortcuts.size} shortcuts")
        return shortcuts.sortedBy { it.displayName }
    }
    
    /**
     * Create ShortcutInfo from a file.
     */
    private fun createShortcutInfo(file: File, isTask: Boolean): ShortcutInfo {
        val name = file.nameWithoutExtension
        val displayName = formatDisplayName(name)
        val iconPath = findIcon(name)
        
        return ShortcutInfo(
            name = name,
            displayName = displayName,
            path = file.absolutePath,
            isTask = isTask,
            iconPath = iconPath,
            isExecutable = file.canExecute()
        )
    }
    
    /**
     * Format script name for display.
     */
    private fun formatDisplayName(name: String): String {
        return name
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }
    
    /**
     * Find icon for a shortcut.
     */
    private fun findIcon(name: String): String? {
        val iconsDir = getIconsDir()
        if (!iconsDir.exists()) return null
        
        // Check for various icon formats
        val extensions = listOf("png", "jpg", "jpeg", "webp", "svg")
        for (ext in extensions) {
            val iconFile = File(iconsDir, "$name.$ext")
            if (iconFile.exists()) {
                return iconFile.absolutePath
            }
        }
        
        return null
    }
    
    /**
     * Get shortcut by name.
     */
    fun getShortcut(name: String): ShortcutInfo? {
        return scanShortcuts().find { it.name == name }
    }
    
    /**
     * Create a new shortcut script.
     */
    fun createShortcut(name: String, content: String, isTask: Boolean = false): ShortcutInfo? {
        return try {
            val dir = if (isTask) getTasksDir() else getShortcutsDir()
            dir.mkdirs()
            
            val file = File(dir, name)
            file.writeText(content)
            file.setExecutable(true)
            
            Logger.logInfo(LOG_TAG, "Created shortcut: ${file.absolutePath}")
            createShortcutInfo(file, isTask)
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to create shortcut: ${e.message}")
            null
        }
    }
    
    /**
     * Delete a shortcut.
     */
    fun deleteShortcut(name: String, isTask: Boolean = false): Boolean {
        val dir = if (isTask) getTasksDir() else getShortcutsDir()
        val file = File(dir, name)
        
        return if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                Logger.logInfo(LOG_TAG, "Deleted shortcut: $name")
                // Also delete icon if exists
                findIcon(name)?.let { File(it).delete() }
            }
            deleted
        } else {
            false
        }
    }
    
    /**
     * Set icon for a shortcut.
     */
    fun setShortcutIcon(name: String, iconFile: File): Boolean {
        return try {
            val iconsDir = getIconsDir()
            iconsDir.mkdirs()
            
            val destFile = File(iconsDir, "$name.${iconFile.extension}")
            iconFile.copyTo(destFile, overwrite = true)
            
            Logger.logInfo(LOG_TAG, "Set icon for shortcut: $name")
            true
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to set shortcut icon: ${e.message}")
            false
        }
    }
    
    /**
     * Check if shortcuts directory exists and has content.
     */
    fun hasShortcuts(): Boolean {
        return scanShortcuts().isNotEmpty()
    }
    
    /**
     * Get count of shortcuts.
     */
    fun getShortcutCount(): Int = scanShortcuts().size
}
