package com.termux.shared.tools

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Kotlin interface for invoking Termux userland tools.
 * Uses paths from TermuxConstants for consistency.
 */
object TermuxTools {
    
    private val TERMUX_PREFIX: String get() = TermuxConstants.TERMUX_PREFIX_DIR_PATH
    private val TERMUX_BIN: String get() = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
    private val TERMUX_HOME: String get() = TermuxConstants.TERMUX_HOME_DIR_PATH
    
    /**
     * Execute a termux tool by name using ProcessBuilder
     */
    fun executeTool(toolName: String, args: List<String> = emptyList()): ToolResult {
        val toolPath = "$TERMUX_BIN/$toolName"
        val toolFile = File(toolPath)
        
        if (!toolFile.exists()) {
            return ToolResult(
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = "Tool not found: $toolPath"
            )
        }
        
        return try {
            val command = mutableListOf(toolPath)
            command.addAll(args)
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(File(TERMUX_HOME))
            processBuilder.environment()["HOME"] = TERMUX_HOME
            processBuilder.environment()["PATH"] = TERMUX_BIN
            processBuilder.environment()["PREFIX"] = TERMUX_PREFIX
            processBuilder.environment()["TERMUX_PREFIX"] = TERMUX_PREFIX
            
            val process = processBuilder.start()
            
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()
            
            ToolResult(
                success = exitCode == 0,
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * termux-info: Get system and Termux information
     */
    fun info(): ToolResult {
        return executeTool("termux-info", listOf("--no-set-clipboard"))
    }
    
    /**
     * termux-setup-storage: Request storage permissions
     */
    fun setupStorage(): ToolResult {
        return executeTool("termux-setup-storage")
    }
    
    /**
     * termux-change-repo: Change package repository
     */
    fun changeRepo(): ToolResult {
        return executeTool("termux-change-repo")
    }
    
    /**
     * termux-open: Open a file or URL using Android intents
     */
    fun open(path: String, sendIntent: Boolean = false, chooser: Boolean = false): ToolResult {
        val args = mutableListOf(path)
        if (sendIntent) args.add("--send")
        if (chooser) args.add("--chooser")
        return executeTool("termux-open", args)
    }
    
    /**
     * termux-open-url: Open a URL in the default browser
     */
    fun openUrl(url: String): ToolResult {
        return executeTool("termux-open-url", listOf(url))
    }
    
    /**
     * termux-reload-settings: Reload Termux settings
     */
    fun reloadSettings(): ToolResult {
        return executeTool("termux-reload-settings")
    }
    
    /**
     * termux-reset: Reset Termux to default state
     */
    fun reset(): ToolResult {
        return executeTool("termux-reset")
    }
    
    /**
     * termux-wake-lock: Acquire wake lock
     */
    fun wakeLock(): ToolResult {
        return executeTool("termux-wake-lock")
    }
    
    /**
     * termux-wake-unlock: Release wake lock
     */
    fun wakeUnlock(): ToolResult {
        return executeTool("termux-wake-unlock")
    }
    
    /**
     * termux-fix-shebang: Fix script shebangs to use Termux paths
     */
    fun fixShebang(file: String): ToolResult {
        return executeTool("termux-fix-shebang", listOf(file))
    }
    
    /**
     * termux-backup: Create backup of Termux home directory
     */
    fun backup(outputPath: String): ToolResult {
        return executeTool("termux-backup", listOf(outputPath))
    }
    
    /**
     * termux-restore: Restore Termux from backup
     */
    fun restore(backupPath: String): ToolResult {
        return executeTool("termux-restore", listOf(backupPath))
    }
    
    /**
     * Check if a tool exists
     */
    fun isToolAvailable(toolName: String): Boolean {
        return File("$TERMUX_BIN/$toolName").exists()
    }
    
    /**
     * Get all available termux-* tools
     */
    fun getAvailableTools(): List<String> {
        val binDir = File(TERMUX_BIN)
        if (!binDir.exists()) return emptyList()
        
        return binDir.listFiles()
            ?.filter { it.name.startsWith("termux-") && it.canExecute() }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }
    
    /**
     * Get Termux prefix path
     */
    fun getPrefix(): String = TERMUX_PREFIX
    
    /**
     * Get Termux home path
     */
    fun getHome(): String = TERMUX_HOME
}

/**
 * Result of executing a Termux tool
 */
data class ToolResult(
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    override fun toString(): String {
        return if (success) stdout else "Error: $stderr (exit code: $exitCode)"
    }
}
