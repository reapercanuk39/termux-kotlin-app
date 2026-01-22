package com.termux.app.agents.mcp

import com.termux.app.agents.daemon.AgentDaemon
import com.termux.app.agents.models.TaskResult
import com.termux.shared.logger.Logger
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP Server implementation.
 * 
 * Exposes Termux agent skills as MCP tools for external AI assistants.
 * Implements JSON-RPC 2.0 over HTTP for compatibility with MCP clients.
 * 
 * Protocol: Model Context Protocol (MCP)
 * Transport: HTTP with JSON-RPC 2.0
 */
@Singleton
class McpServer @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val daemon: AgentDaemon
) {
    companion object {
        private const val LOG_TAG = "McpServer"
        private const val AGENT_NAME = "mcp_agent"
    }
    
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val activeConnections = AtomicInteger(0)
    private val requestCount = AtomicInteger(0)
    private val requestTimestamps = ConcurrentHashMap<Long, Long>()
    
    private var isRunning = false
    
    /**
     * Start the MCP server
     */
    fun start(): Boolean {
        val config = toolRegistry.getConfig()
        
        if (config?.enabled != true) {
            Logger.logInfo(LOG_TAG, "MCP server disabled in config")
            return false
        }
        
        if (isRunning) {
            Logger.logWarn(LOG_TAG, "MCP server already running")
            return true
        }
        
        return try {
            serverSocket = ServerSocket(config.port)
            isRunning = true
            
            serverJob = scope.launch {
                Logger.logInfo(LOG_TAG, "MCP server listening on ${config.host}:${config.port}")
                
                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch { handleConnection(clientSocket, config) }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Logger.logError(LOG_TAG, "Accept error: ${e.message}")
                        }
                    }
                }
            }
            
            true
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to start MCP server: ${e.message}")
            false
        }
    }
    
    /**
     * Stop the MCP server
     */
    fun stop() {
        isRunning = false
        serverJob?.cancel()
        serverSocket?.close()
        serverSocket = null
        Logger.logInfo(LOG_TAG, "MCP server stopped")
    }
    
    /**
     * Check if server is running
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * Get server status
     */
    fun getStatus(): Map<String, Any> {
        return mapOf(
            "running" to isRunning,
            "port" to (toolRegistry.getConfig()?.port ?: 0),
            "active_connections" to activeConnections.get(),
            "total_requests" to requestCount.get(),
            "tools_registered" to toolRegistry.getAllTools().size
        )
    }
    
    /**
     * Handle a client connection
     */
    private suspend fun handleConnection(socket: Socket, config: McpConfig) {
        activeConnections.incrementAndGet()
        
        try {
            socket.use { client ->
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = OutputStreamWriter(client.getOutputStream())
                
                // Read HTTP request
                val request = readHttpRequest(reader)
                if (request == null) {
                    sendHttpResponse(writer, 400, "Bad Request")
                    return
                }
                
                // Check authentication if enabled
                if (config.authEnabled && config.authToken != null) {
                    val authHeader = request.headers["authorization"]
                    if (authHeader != "Bearer ${config.authToken}") {
                        sendHttpResponse(writer, 401, "Unauthorized")
                        return
                    }
                }
                
                // Rate limiting
                if (config.rateLimitEnabled && !checkRateLimit(config)) {
                    sendHttpResponse(writer, 429, "Too Many Requests")
                    return
                }
                
                // Handle request
                val response = handleRequest(request.body)
                sendHttpResponse(writer, 200, response)
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Connection error: ${e.message}")
        } finally {
            activeConnections.decrementAndGet()
        }
    }
    
    /**
     * Handle MCP JSON-RPC request
     */
    private suspend fun handleRequest(body: String): String {
        requestCount.incrementAndGet()
        
        return try {
            val json = JSONObject(body)
            val id = json.opt("id")
            val method = json.optString("method", "")
            val params = json.optJSONObject("params")
            
            val request = parseRequest(method, params)
            val response = processRequest(request)
            
            // Build JSON-RPC response
            JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", id)
                put("result", response.toJson())
            }.toString()
            
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Request error: ${e.message}")
            
            JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", null)
                put("error", McpResponse.Error(
                    McpErrorCodes.INTERNAL_ERROR,
                    e.message ?: "Internal error"
                ).toJson())
            }.toString()
        }
    }
    
    /**
     * Parse MCP request
     */
    private fun parseRequest(method: String, params: JSONObject?): McpRequest {
        return when (method) {
            "initialize" -> McpRequest.Initialize(
                protocolVersion = params?.optString("protocolVersion") ?: "2024-11-05",
                capabilities = null,
                clientInfo = null
            )
            "tools/list" -> McpRequest.ListTools
            "tools/call" -> {
                val args = mutableMapOf<String, Any?>()
                params?.optJSONObject("arguments")?.let { argsJson ->
                    argsJson.keys().forEach { key ->
                        args[key] = argsJson.get(key)
                    }
                }
                McpRequest.CallTool(
                    name = params?.optString("name") ?: "",
                    arguments = args
                )
            }
            "ping" -> McpRequest.Ping
            else -> McpRequest.Unknown(method)
        }
    }
    
    /**
     * Process MCP request and return response
     */
    private suspend fun processRequest(request: McpRequest): McpResponse {
        return when (request) {
            is McpRequest.Initialize -> {
                Logger.logInfo(LOG_TAG, "Client initialized with protocol ${request.protocolVersion}")
                McpResponse.InitializeResult()
            }
            
            is McpRequest.ListTools -> {
                val tools = toolRegistry.getAllTools()
                Logger.logDebug(LOG_TAG, "Listing ${tools.size} tools")
                McpResponse.ToolList(tools)
            }
            
            is McpRequest.CallTool -> {
                Logger.logInfo(LOG_TAG, "Calling tool: ${request.name}")
                callTool(request.name, request.arguments)
            }
            
            is McpRequest.Ping -> McpResponse.Pong
            
            is McpRequest.Unknown -> {
                Logger.logWarn(LOG_TAG, "Unknown method: ${request.method}")
                McpResponse.Error(
                    McpErrorCodes.METHOD_NOT_FOUND,
                    "Method not found: ${request.method}"
                )
            }
        }
    }
    
    /**
     * Call a tool via the agent daemon
     */
    private suspend fun callTool(name: String, arguments: Map<String, Any?>): McpResponse {
        val tool = toolRegistry.getTool(name)
            ?: return McpResponse.Error(
                McpErrorCodes.TOOL_NOT_FOUND,
                "Tool not found: $name"
            )
        
        return try {
            val result = daemon.runTask(
                agentName = AGENT_NAME,
                skillName = tool.skillName,
                function = tool.functionName,
                params = arguments
            )
            
            when (result) {
                is TaskResult.Success -> {
                    val text = formatTaskResult(result)
                    McpResponse.ToolResult(
                        content = listOf(ContentBlock.Text(text)),
                        isError = false
                    )
                }
                is TaskResult.Failure -> {
                    McpResponse.ToolResult(
                        content = listOf(ContentBlock.Text("Error: ${result.error.message}")),
                        isError = true
                    )
                }
                is TaskResult.Timeout -> {
                    McpResponse.ToolResult(
                        content = listOf(ContentBlock.Text("Timeout after ${result.timeoutMs}ms")),
                        isError = true
                    )
                }
                is TaskResult.Cancelled -> {
                    McpResponse.ToolResult(
                        content = listOf(ContentBlock.Text("Cancelled: ${result.reason}")),
                        isError = true
                    )
                }
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Tool execution error: ${e.message}")
            McpResponse.Error(
                McpErrorCodes.TOOL_EXECUTION_ERROR,
                "Tool execution failed: ${e.message}"
            )
        }
    }
    
    /**
     * Format task result as text
     */
    private fun formatTaskResult(result: TaskResult.Success): String {
        val sb = StringBuilder()
        
        result.data.forEach { (key, value) ->
            sb.appendLine("$key: $value")
        }
        
        if (result.logs?.isNotEmpty() == true) {
            sb.appendLine("\nLogs:")
            sb.appendLine(result.logs)
        }
        
        return sb.toString().ifEmpty { "Success" }
    }
    
    /**
     * Check rate limit
     */
    private fun checkRateLimit(config: McpConfig): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - 60_000 // 1 minute window
        
        // Clean old entries
        requestTimestamps.entries.removeIf { it.value < windowStart }
        
        // Check limit
        if (requestTimestamps.size >= config.requestsPerMinute) {
            return false
        }
        
        requestTimestamps[now] = now
        return true
    }
    
    /**
     * Read HTTP request
     */
    private fun readHttpRequest(reader: BufferedReader): HttpRequest? {
        val requestLine = reader.readLine() ?: return null
        val parts = requestLine.split(" ")
        if (parts.size < 3) return null
        
        val method = parts[0]
        val path = parts[1]
        
        // Read headers
        val headers = mutableMapOf<String, String>()
        var line = reader.readLine()
        var contentLength = 0
        
        while (line != null && line.isNotEmpty()) {
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val headerName = line.substring(0, colonIndex).lowercase().trim()
                val headerValue = line.substring(colonIndex + 1).trim()
                headers[headerName] = headerValue
                
                if (headerName == "content-length") {
                    contentLength = headerValue.toIntOrNull() ?: 0
                }
            }
            line = reader.readLine()
        }
        
        // Read body
        val body = if (contentLength > 0) {
            val buffer = CharArray(contentLength)
            reader.read(buffer, 0, contentLength)
            String(buffer)
        } else ""
        
        return HttpRequest(method, path, headers, body)
    }
    
    /**
     * Send HTTP response
     */
    private fun sendHttpResponse(writer: OutputStreamWriter, statusCode: Int, body: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            429 -> "Too Many Requests"
            else -> "Error"
        }
        
        val response = """
            HTTP/1.1 $statusCode $statusText
            Content-Type: application/json
            Content-Length: ${body.length}
            Connection: close
            
            $body
        """.trimIndent()
        
        writer.write(response)
        writer.flush()
    }
}

/**
 * HTTP request wrapper
 */
private data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String
)
