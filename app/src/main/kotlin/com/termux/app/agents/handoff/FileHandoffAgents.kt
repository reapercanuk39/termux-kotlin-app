package com.termux.app.agents.handoff

import com.termux.app.agents.daemon.AgentDaemon
import com.termux.app.agents.models.TaskResult
import com.termux.shared.logger.Logger

/**
 * File operation handoff chain.
 * 
 * Chain: FileAgent -> BackupAgent -> OperationAgent -> VerifyAgent
 * 
 * Used for safe file operations with automatic backup.
 */

/**
 * File Agent - Entry point for file operations
 * 
 * Routes file requests to appropriate handlers with safety checks.
 */
class FileHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "FileHandoffAgent"
    }
    
    override val handoffName: String = "file_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "backup_agent",
        "copy_agent",
        "move_agent",
        "delete_agent"
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        val keywords = listOf("file", "copy", "move", "delete", "backup", "restore")
        return keywords.any { context.taskDescription.contains(it, ignoreCase = true) }
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "Processing file request: ${context.taskDescription}")
        
        val task = context.taskDescription.lowercase()
        
        // Determine operation
        val operation = when {
            task.contains("copy") || task.contains("duplicate") -> "copy"
            task.contains("move") || task.contains("rename") -> "move"
            task.contains("delete") || task.contains("remove") -> "delete"
            task.contains("backup") -> "backup"
            task.contains("restore") -> "restore"
            else -> "copy"  // Safe default
        }
        
        // Extract paths
        val paths = extractPaths(context.taskDescription)
        
        if (paths.source == null) {
            return HandoffResult.needInput(
                agentName = handoffName,
                prompt = "Please specify the source file/directory path:",
                inputType = "text"
            )
        }
        
        context.set("operation", operation)
        context.set("source_path", paths.source)
        context.set("dest_path", paths.destination)
        
        // For destructive operations, backup first
        val needsBackup = operation in listOf("delete", "move")
        
        return if (needsBackup) {
            HandoffResult.handoffTo(
                fromAgent = handoffName,
                toAgent = "backup_agent",
                reason = "Creating backup before $operation operation",
                contextUpdates = mapOf(
                    "original_operation" to operation,
                    "source_path" to paths.source
                )
            )
        } else {
            HandoffResult.handoffTo(
                fromAgent = handoffName,
                toAgent = "${operation}_agent",
                reason = "Executing $operation operation",
                contextUpdates = mapOf(
                    "source_path" to paths.source,
                    "dest_path" to paths.destination
                )
            )
        }
    }
    
    private data class PathInfo(val source: String?, val destination: String?)
    
    private fun extractPaths(input: String): PathInfo {
        // Simple path extraction - looks for paths starting with / or ~
        val pathPattern = Regex("""[~/][^\s]+""")
        val paths = pathPattern.findAll(input).map { it.value }.toList()
        
        return PathInfo(
            source = paths.getOrNull(0),
            destination = paths.getOrNull(1)
        )
    }
}

/**
 * Backup Agent - Creates backups before operations
 */
class BackupHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "BackupHandoffAgent"
        private const val BACKUP_DIR = "/data/data/com.termux/files/home/.agent_backups"
    }
    
    override val handoffName: String = "backup_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "copy_agent",
        "move_agent",
        "delete_agent",
        "restore_agent"
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.has("source_path") || 
               context.taskDescription.contains("backup", ignoreCase = true)
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val sourcePath = context.get<String>("source_path")
            ?: return HandoffResult.fail(handoffName, "No source path specified")
        
        val originalOperation = context.get<String>("original_operation") ?: "unknown"
        
        Logger.logInfo(LOG_TAG, "Creating backup of: $sourcePath")
        
        // Create backup
        val backupPath = "$BACKUP_DIR/${System.currentTimeMillis()}_${sourcePath.substringAfterLast('/')}"
        
        val result = daemon.runTask(
            agentName = "backup_agent",
            skillName = "file",
            function = "copy",
            params = mapOf(
                "source" to sourcePath,
                "destination" to backupPath,
                "preserve" to true
            )
        )
        
        return when (result) {
            is TaskResult.Success -> {
                context.set("backup_path", backupPath)
                context.set("backup_created", true)
                
                // Continue to original operation
                val targetAgent = if (originalOperation != "unknown") {
                    "${originalOperation}_agent"
                } else {
                    "file_agent"
                }
                
                HandoffResult.handoffTo(
                    fromAgent = handoffName,
                    toAgent = targetAgent,
                    reason = "Backup created, proceeding with $originalOperation",
                    contextUpdates = mapOf("backup_path" to backupPath)
                )
            }
            is TaskResult.Failure -> {
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Backup failed: ${result.error.message}",
                    recoverable = false  // Don't proceed without backup
                )
            }
            else -> {
                HandoffResult.fail(handoffName, "Unexpected result")
            }
        }
    }
}

/**
 * Copy Agent - Handles file copying
 */
class CopyHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "CopyHandoffAgent"
    }
    
    override val handoffName: String = "copy_agent"
    
    override fun getHandoffTargets(): List<String> = emptyList()
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.get<String>("operation") == "copy"
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val source = context.get<String>("source_path")
            ?: return HandoffResult.fail(handoffName, "No source path")
        val dest = context.get<String>("dest_path")
            ?: return HandoffResult.fail(handoffName, "No destination path")
        
        Logger.logInfo(LOG_TAG, "Copying: $source -> $dest")
        
        val result = daemon.runTask(
            agentName = "copy_agent",
            skillName = "file",
            function = "copy",
            params = mapOf(
                "source" to source,
                "destination" to dest
            )
        )
        
        return when (result) {
            is TaskResult.Success -> {
                HandoffResult.complete(
                    agentName = handoffName,
                    result = mapOf(
                        "source" to source,
                        "destination" to dest,
                        "bytes_copied" to result.data["bytes"]
                    ),
                    message = "Successfully copied $source to $dest"
                )
            }
            is TaskResult.Failure -> {
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Copy failed: ${result.error.message}",
                    recoverable = false
                )
            }
            else -> HandoffResult.fail(handoffName, "Unexpected result")
        }
    }
}

/**
 * Move Agent - Handles file moving/renaming
 */
class MoveHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "MoveHandoffAgent"
    }
    
    override val handoffName: String = "move_agent"
    
    override fun getHandoffTargets(): List<String> = listOf("restore_agent")
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.get<String>("operation") == "move"
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val source = context.get<String>("source_path")
            ?: return HandoffResult.fail(handoffName, "No source path")
        val dest = context.get<String>("dest_path")
            ?: return HandoffResult.fail(handoffName, "No destination path")
        
        Logger.logInfo(LOG_TAG, "Moving: $source -> $dest")
        
        val result = daemon.runTask(
            agentName = "move_agent",
            skillName = "file",
            function = "move",
            params = mapOf(
                "source" to source,
                "destination" to dest
            )
        )
        
        return when (result) {
            is TaskResult.Success -> {
                HandoffResult.complete(
                    agentName = handoffName,
                    result = mapOf(
                        "source" to source,
                        "destination" to dest,
                        "backup_path" to context.get<String>("backup_path")
                    ),
                    message = "Successfully moved $source to $dest"
                )
            }
            is TaskResult.Failure -> {
                // Move failed, might need to restore
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Move failed: ${result.error.message}",
                    recoverable = true,
                    suggestedAgent = "restore_agent"
                )
            }
            else -> HandoffResult.fail(handoffName, "Unexpected result")
        }
    }
}

/**
 * Delete Agent - Handles file deletion
 */
class DeleteHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "DeleteHandoffAgent"
    }
    
    override val handoffName: String = "delete_agent"
    
    override fun getHandoffTargets(): List<String> = listOf("restore_agent")
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.get<String>("operation") == "delete"
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val source = context.get<String>("source_path")
            ?: return HandoffResult.fail(handoffName, "No path to delete")
        
        // Safety check - require backup
        if (!context.has("backup_path")) {
            return HandoffResult.fail(
                agentName = handoffName,
                error = "Cannot delete without backup",
                recoverable = false
            )
        }
        
        Logger.logInfo(LOG_TAG, "Deleting: $source")
        
        val result = daemon.runTask(
            agentName = "delete_agent",
            skillName = "file",
            function = "delete",
            params = mapOf("path" to source)
        )
        
        return when (result) {
            is TaskResult.Success -> {
                HandoffResult.complete(
                    agentName = handoffName,
                    result = mapOf(
                        "deleted" to source,
                        "backup_path" to context.get<String>("backup_path"),
                        "recoverable" to true
                    ),
                    message = "Deleted $source (backup available at ${context.get<String>("backup_path")})"
                )
            }
            is TaskResult.Failure -> {
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Delete failed: ${result.error.message}",
                    recoverable = true,
                    suggestedAgent = "restore_agent"
                )
            }
            else -> HandoffResult.fail(handoffName, "Unexpected result")
        }
    }
}

/**
 * Restore Agent - Restores from backup
 */
class RestoreHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "RestoreHandoffAgent"
    }
    
    override val handoffName: String = "restore_agent"
    
    override fun getHandoffTargets(): List<String> = emptyList()
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.has("backup_path") || 
               context.taskDescription.contains("restore", ignoreCase = true)
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val backupPath = context.get<String>("backup_path")
            ?: return HandoffResult.fail(handoffName, "No backup path available")
        
        val originalPath = context.get<String>("source_path")
            ?: return HandoffResult.fail(handoffName, "No original path to restore to")
        
        Logger.logInfo(LOG_TAG, "Restoring: $backupPath -> $originalPath")
        
        val result = daemon.runTask(
            agentName = "restore_agent",
            skillName = "file",
            function = "copy",
            params = mapOf(
                "source" to backupPath,
                "destination" to originalPath,
                "overwrite" to true
            )
        )
        
        return when (result) {
            is TaskResult.Success -> {
                HandoffResult.complete(
                    agentName = handoffName,
                    result = mapOf(
                        "restored_from" to backupPath,
                        "restored_to" to originalPath
                    ),
                    message = "Successfully restored from backup"
                )
            }
            is TaskResult.Failure -> {
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Restore failed: ${result.error.message}",
                    recoverable = false
                )
            }
            else -> HandoffResult.fail(handoffName, "Unexpected result")
        }
    }
}
