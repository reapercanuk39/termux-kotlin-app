package com.termux.app.agents.handoff

import com.termux.app.agents.daemon.AgentDaemon
import com.termux.app.agents.models.TaskResult
import com.termux.shared.logger.Logger

/**
 * Network operation handoff chain.
 * 
 * Chain: NetworkAgent -> ConnectivityAgent -> DownloadAgent/UploadAgent -> VerifyAgent
 * 
 * Used for network operations with connectivity checks.
 */

/**
 * Network Agent - Entry point for network operations
 * 
 * Routes network requests with connectivity validation.
 */
class NetworkHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "NetworkHandoffAgent"
    }
    
    override val handoffName: String = "network_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "connectivity_agent",
        "download_agent",
        "upload_agent",
        "fetch_agent"
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        val keywords = listOf("download", "upload", "fetch", "curl", "wget", "http", "network")
        return keywords.any { context.taskDescription.contains(it, ignoreCase = true) }
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "Processing network request: ${context.taskDescription}")
        
        val task = context.taskDescription.lowercase()
        
        // Determine operation
        val operation = when {
            task.contains("download") || task.contains("wget") -> "download"
            task.contains("upload") -> "upload"
            task.contains("fetch") || task.contains("curl") || task.contains("get") -> "fetch"
            else -> "fetch"
        }
        
        // Extract URL
        val url = extractUrl(context.taskDescription)
        
        if (url == null) {
            return HandoffResult.needInput(
                agentName = handoffName,
                prompt = "Please provide the URL:",
                inputType = "text"
            )
        }
        
        context.set("operation", operation)
        context.set("url", url)
        
        // Always check connectivity first
        return HandoffResult.handoffTo(
            fromAgent = handoffName,
            toAgent = "connectivity_agent",
            reason = "Checking network connectivity before $operation",
            contextUpdates = mapOf(
                "url" to url,
                "original_operation" to operation
            )
        )
    }
    
    private fun extractUrl(input: String): String? {
        val urlPattern = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        return urlPattern.find(input)?.value
    }
}

/**
 * Connectivity Agent - Checks network connectivity
 */
class ConnectivityHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "ConnectivityHandoffAgent"
    }
    
    override val handoffName: String = "connectivity_agent"
    
    override fun getHandoffTargets(): List<String> = listOf(
        "download_agent",
        "upload_agent",
        "fetch_agent"
    )
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.has("url") || 
               context.taskDescription.contains("connectivity", ignoreCase = true)
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        Logger.logInfo(LOG_TAG, "Checking connectivity")
        
        val result = daemon.runTask(
            agentName = "connectivity_agent",
            skillName = "network",
            function = "check_connectivity",
            params = emptyMap()
        )
        
        return when (result) {
            is TaskResult.Success -> {
                val isOnline = result.data["online"] as? Boolean ?: false
                
                if (!isOnline) {
                    return HandoffResult.fail(
                        agentName = handoffName,
                        error = "No network connectivity",
                        recoverable = true,
                        suggestedAgent = "network_agent"  // Retry later
                    )
                }
                
                val connectionType = result.data["type"] as? String ?: "unknown"
                context.set("connection_type", connectionType)
                context.set("connectivity_checked", true)
                
                // Proceed to original operation
                val originalOp = context.get<String>("original_operation") ?: "fetch"
                val targetAgent = "${originalOp}_agent"
                
                HandoffResult.handoffTo(
                    fromAgent = handoffName,
                    toAgent = targetAgent,
                    reason = "Network available ($connectionType), proceeding with $originalOp",
                    contextUpdates = mapOf("connection_type" to connectionType)
                )
            }
            is TaskResult.Failure -> {
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Connectivity check failed: ${result.error.message}",
                    recoverable = true
                )
            }
            else -> HandoffResult.fail(handoffName, "Unexpected result")
        }
    }
}

/**
 * Download Agent - Handles file downloads
 */
class DownloadHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "DownloadHandoffAgent"
        private const val DOWNLOAD_DIR = "/data/data/com.termux/files/home/downloads"
    }
    
    override val handoffName: String = "download_agent"
    
    override fun getHandoffTargets(): List<String> = listOf("verify_download_agent")
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.get<String>("original_operation") == "download" ||
               context.taskDescription.contains("download", ignoreCase = true)
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val url = context.get<String>("url")
            ?: return HandoffResult.fail(handoffName, "No URL specified")
        
        // Determine output path
        val filename = url.substringAfterLast('/')
            .substringBefore('?')
            .ifEmpty { "download_${System.currentTimeMillis()}" }
        
        val outputPath = context.get<String>("output_path") 
            ?: "$DOWNLOAD_DIR/$filename"
        
        Logger.logInfo(LOG_TAG, "Downloading: $url -> $outputPath")
        
        context.set("output_path", outputPath)
        context.set("download_started", System.currentTimeMillis())
        
        val result = daemon.runTask(
            agentName = "download_agent",
            skillName = "network",
            function = "download",
            params = mapOf(
                "url" to url,
                "output" to outputPath,
                "resume" to true
            )
        )
        
        return when (result) {
            is TaskResult.Success -> {
                val downloadTime = System.currentTimeMillis() - 
                    (context.get<Long>("download_started") ?: System.currentTimeMillis())
                
                context.set("download_complete", true)
                context.set("download_time_ms", downloadTime)
                
                HandoffResult.handoffTo(
                    fromAgent = handoffName,
                    toAgent = "verify_download_agent",
                    reason = "Download complete, verifying file",
                    contextUpdates = mapOf(
                        "output_path" to outputPath,
                        "expected_size" to result.data["size"]
                    )
                )
            }
            is TaskResult.Failure -> {
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Download failed: ${result.error.message}",
                    recoverable = true
                )
            }
            else -> HandoffResult.fail(handoffName, "Unexpected result")
        }
    }
}

/**
 * Verify Download Agent - Verifies downloaded files
 */
class VerifyDownloadHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "VerifyDownloadHandoffAgent"
    }
    
    override val handoffName: String = "verify_download_agent"
    
    override fun getHandoffTargets(): List<String> = listOf("download_agent")
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.has("output_path") && context.get<Boolean>("download_complete") == true
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val outputPath = context.get<String>("output_path")
            ?: return HandoffResult.fail(handoffName, "No output path")
        
        Logger.logInfo(LOG_TAG, "Verifying download: $outputPath")
        
        val result = daemon.runTask(
            agentName = "verify_download_agent",
            skillName = "file",
            function = "stat",
            params = mapOf("path" to outputPath)
        )
        
        return when (result) {
            is TaskResult.Success -> {
                val exists = result.data["exists"] as? Boolean ?: false
                val size = result.data["size"] as? Long ?: 0L
                
                if (!exists || size == 0L) {
                    return HandoffResult.fail(
                        agentName = handoffName,
                        error = "Downloaded file is missing or empty",
                        recoverable = true,
                        suggestedAgent = "download_agent"
                    )
                }
                
                // Check expected size if available
                val expectedSize = context.get<Long>("expected_size")
                if (expectedSize != null && size != expectedSize) {
                    Logger.logWarn(LOG_TAG, "Size mismatch: expected $expectedSize, got $size")
                }
                
                HandoffResult.complete(
                    agentName = handoffName,
                    result = mapOf(
                        "path" to outputPath,
                        "size" to size,
                        "url" to context.get<String>("url"),
                        "download_time_ms" to context.get<Long>("download_time_ms"),
                        "verified" to true
                    ),
                    message = "Download verified: $outputPath ($size bytes)"
                )
            }
            is TaskResult.Failure -> {
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Verification failed: ${result.error.message}",
                    recoverable = true,
                    suggestedAgent = "download_agent"
                )
            }
            else -> HandoffResult.fail(handoffName, "Unexpected result")
        }
    }
}

/**
 * Fetch Agent - Handles HTTP requests
 */
class FetchHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "FetchHandoffAgent"
    }
    
    override val handoffName: String = "fetch_agent"
    
    override fun getHandoffTargets(): List<String> = emptyList()
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.get<String>("original_operation") == "fetch"
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val url = context.get<String>("url")
            ?: return HandoffResult.fail(handoffName, "No URL specified")
        
        val method = context.get<String>("http_method") ?: "GET"
        val headers = context.get<Map<String, String>>("headers") ?: emptyMap()
        
        Logger.logInfo(LOG_TAG, "Fetching: $method $url")
        
        val result = daemon.runTask(
            agentName = "fetch_agent",
            skillName = "network",
            function = "fetch",
            params = mapOf(
                "url" to url,
                "method" to method,
                "headers" to headers
            )
        )
        
        return when (result) {
            is TaskResult.Success -> {
                HandoffResult.complete(
                    agentName = handoffName,
                    result = mapOf(
                        "url" to url,
                        "status" to result.data["status"],
                        "headers" to result.data["headers"],
                        "body" to result.data["body"],
                        "time_ms" to result.data["time_ms"]
                    ),
                    message = "Fetch complete: ${result.data["status"]}"
                )
            }
            is TaskResult.Failure -> {
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Fetch failed: ${result.error.message}",
                    recoverable = true
                )
            }
            else -> HandoffResult.fail(handoffName, "Unexpected result")
        }
    }
}

/**
 * Upload Agent - Handles file uploads
 */
class UploadHandoffAgent(
    private val daemon: AgentDaemon
) : HandoffCapable {
    
    companion object {
        private const val LOG_TAG = "UploadHandoffAgent"
    }
    
    override val handoffName: String = "upload_agent"
    
    override fun getHandoffTargets(): List<String> = emptyList()
    
    override fun canHandle(context: AgentContext): Boolean {
        return context.get<String>("original_operation") == "upload"
    }
    
    override suspend fun executeWithContext(context: AgentContext): HandoffResult {
        val url = context.get<String>("url")
            ?: return HandoffResult.fail(handoffName, "No URL specified")
        
        val filePath = context.get<String>("file_path")
            ?: return HandoffResult.needInput(
                agentName = handoffName,
                prompt = "Please specify the file to upload:",
                inputType = "text"
            )
        
        Logger.logInfo(LOG_TAG, "Uploading: $filePath -> $url")
        
        val result = daemon.runTask(
            agentName = "upload_agent",
            skillName = "network",
            function = "upload",
            params = mapOf(
                "url" to url,
                "file" to filePath
            )
        )
        
        return when (result) {
            is TaskResult.Success -> {
                HandoffResult.complete(
                    agentName = handoffName,
                    result = mapOf(
                        "url" to url,
                        "file" to filePath,
                        "status" to result.data["status"],
                        "response" to result.data["response"]
                    ),
                    message = "Upload complete"
                )
            }
            is TaskResult.Failure -> {
                HandoffResult.fail(
                    agentName = handoffName,
                    error = "Upload failed: ${result.error.message}",
                    recoverable = true
                )
            }
            else -> HandoffResult.fail(handoffName, "Unexpected result")
        }
    }
}
