package com.termux.shared.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.runner.app.AppShell
import java.io.File

/**
 * Kotlin interface for invoking Termux userland tools.
 * All tools use the com.termux.kotlin prefix path:
 * /data/data/com.termux.kotlin/files/usr
 */
object TermuxTools {
    
    private const val TERMUX_PREFIX = "/data/data/com.termux.kotlin/files/usr"
    private const val TERMUX_BIN = "$TERMUX_PREFIX/bin"
    private const val TERMUX_HOME = "/data/data/com.termux.kotlin/files/home"
    
    /**
     * Execute a termux tool by name
     */
    fun executeTool(context: Context, toolName: String, args: List<String> = emptyList()): ToolResult {
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
        
        val command = ExecutionCommand()
        command.executable = toolPath
        command.arguments = args.toTypedArray()
        command.workingDirectory = TERMUX_HOME
        
        return try {
            val shell = AppShell.execute(context, command, null, null, false)
            ToolResult(
                success = shell.mExitCode == 0,
                exitCode = shell.mExitCode,
                stdout = shell.mStdout ?: "",
                stderr = shell.mStderr ?: ""
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
    fun info(context: Context): ToolResult {
        return executeTool(context, "termux-info", listOf("--no-set-clipboard"))
    }
    
    /**
     * termux-setup-storage: Request storage permissions
     */
    fun setupStorage(context: Context): ToolResult {
        return executeTool(context, "termux-setup-storage")
    }
    
    /**
     * termux-change-repo: Change package repository
     */
    fun changeRepo(context: Context): ToolResult {
        return executeTool(context, "termux-change-repo")
    }
    
    /**
     * termux-open: Open a file or URL using Android intents
     */
    fun open(context: Context, path: String, sendIntent: Boolean = false, chooser: Boolean = false): ToolResult {
        val args = mutableListOf(path)
        if (sendIntent) args.add("--send")
        if (chooser) args.add("--chooser")
        return executeTool(context, "termux-open", args)
    }
    
    /**
     * termux-open-url: Open a URL in the default browser
     */
    fun openUrl(context: Context, url: String): ToolResult {
        return executeTool(context, "termux-open-url", listOf(url))
    }
    
    /**
     * termux-reload-settings: Reload Termux settings
     */
    fun reloadSettings(context: Context): ToolResult {
        return executeTool(context, "termux-reload-settings")
    }
    
    /**
     * termux-reset: Reset Termux to default state
     */
    fun reset(context: Context): ToolResult {
        return executeTool(context, "termux-reset")
    }
    
    /**
     * termux-wake-lock: Acquire wake lock
     */
    fun wakeLock(context: Context): ToolResult {
        return executeTool(context, "termux-wake-lock")
    }
    
    /**
     * termux-wake-unlock: Release wake lock
     */
    fun wakeUnlock(context: Context): ToolResult {
        return executeTool(context, "termux-wake-unlock")
    }
    
    /**
     * termux-fix-shebang: Fix script shebangs to use Termux paths
     */
    fun fixShebang(context: Context, file: String): ToolResult {
        return executeTool(context, "termux-fix-shebang", listOf(file))
    }
    
    /**
     * termux-backup: Create backup of Termux home directory
     */
    fun backup(context: Context, outputPath: String): ToolResult {
        return executeTool(context, "termux-backup", listOf(outputPath))
    }
    
    /**
     * termux-restore: Restore Termux from backup
     */
    fun restore(context: Context, backupPath: String): ToolResult {
        return executeTool(context, "termux-restore", listOf(backupPath))
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
