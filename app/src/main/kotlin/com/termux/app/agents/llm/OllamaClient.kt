package com.termux.app.agents.llm

import com.termux.shared.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP client for communicating with Ollama server.
 * 
 * Ollama API endpoints:
 * - POST /api/generate - Text completion
 * - POST /api/chat - Chat completion
 * - GET /api/tags - List available models
 * - POST /api/pull - Pull a model
 * 
 * @param baseUrl Ollama server URL (default: http://localhost:11434)
 * @param timeoutMs Request timeout in milliseconds
 */
class OllamaClient(
    private val baseUrl: String = "http://localhost:11434",
    private val timeoutMs: Long = 30_000L
) {
    companion object {
        private const val LOG_TAG = "OllamaClient"
    }
    
    /**
     * Check if Ollama server is reachable
     */
    suspend fun isServerAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/api/tags")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            
            val responseCode = conn.responseCode
            conn.disconnect()
            
            responseCode == 200
        } catch (e: Exception) {
            Logger.logDebug(LOG_TAG, "Ollama server not available: ${e.message}")
            false
        }
    }
    
    /**
     * List available models
     */
    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/tags")
            val json = JSONObject(response)
            val models = json.optJSONArray("models") ?: JSONArray()
            
            val modelNames = (0 until models.length()).map { i ->
                models.getJSONObject(i).getString("name")
            }
            
            Result.success(modelNames)
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to list models: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Check if a specific model is available
     */
    suspend fun isModelAvailable(model: String): Boolean {
        val models = listModels().getOrNull() ?: return false
        return models.any { it == model || it.startsWith("$model:") }
    }
    
    /**
     * Generate text completion
     */
    suspend fun generate(
        model: String,
        prompt: String,
        systemPrompt: String? = null,
        options: LlmOptions = LlmOptions()
    ): Result<OllamaGenerateResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("stream", false)
                
                if (systemPrompt != null) {
                    put("system", systemPrompt)
                }
                
                put("options", JSONObject().apply {
                    put("num_predict", options.maxTokens)
                    put("temperature", options.temperature.toDouble())
                    put("top_p", options.topP.toDouble())
                })
                
                if (options.stop.isNotEmpty()) {
                    put("stop", JSONArray(options.stop))
                }
            }
            
            val startTime = System.currentTimeMillis()
            val response = post("/api/generate", requestBody.toString())
            val durationMs = System.currentTimeMillis() - startTime
            
            val json = JSONObject(response)
            
            Result.success(
                OllamaGenerateResponse(
                    model = json.optString("model", model),
                    response = json.optString("response", ""),
                    done = json.optBoolean("done", true),
                    totalDuration = json.optLong("total_duration", 0),
                    promptEvalCount = json.optInt("prompt_eval_count", 0),
                    evalCount = json.optInt("eval_count", 0),
                    durationMs = durationMs
                )
            )
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Generate failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Chat completion
     */
    suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        options: LlmOptions = LlmOptions()
    ): Result<OllamaChatResponse> = withContext(Dispatchers.IO) {
        try {
            val messagesArray = JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            }
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("stream", false)
                
                put("options", JSONObject().apply {
                    put("num_predict", options.maxTokens)
                    put("temperature", options.temperature.toDouble())
                    put("top_p", options.topP.toDouble())
                })
                
                if (options.stop.isNotEmpty()) {
                    put("stop", JSONArray(options.stop))
                }
            }
            
            val startTime = System.currentTimeMillis()
            val response = post("/api/chat", requestBody.toString())
            val durationMs = System.currentTimeMillis() - startTime
            
            val json = JSONObject(response)
            val messageJson = json.optJSONObject("message")
            
            Result.success(
                OllamaChatResponse(
                    model = json.optString("model", model),
                    message = ChatMessage(
                        role = messageJson?.optString("role") ?: "assistant",
                        content = messageJson?.optString("content") ?: ""
                    ),
                    done = json.optBoolean("done", true),
                    totalDuration = json.optLong("total_duration", 0),
                    promptEvalCount = json.optInt("prompt_eval_count", 0),
                    evalCount = json.optInt("eval_count", 0),
                    durationMs = durationMs
                )
            )
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Chat failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * HTTP GET request
     */
    private suspend fun get(path: String): String = withTimeout(timeoutMs) {
        val url = URL("$baseUrl$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs.toInt()
        conn.readTimeout = timeoutMs.toInt()
        conn.requestMethod = "GET"
        
        try {
            if (conn.responseCode != 200) {
                throw OllamaException("GET $path failed with code ${conn.responseCode}")
            }
            
            BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                reader.readText()
            }
        } finally {
            conn.disconnect()
        }
    }
    
    /**
     * HTTP POST request with JSON body
     */
    private suspend fun post(path: String, body: String): String = withTimeout(timeoutMs) {
        val url = URL("$baseUrl$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs.toInt()
        conn.readTimeout = timeoutMs.toInt()
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        
        try {
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body)
            }
            
            if (conn.responseCode != 200) {
                val errorBody = try {
                    BufferedReader(InputStreamReader(conn.errorStream)).use { it.readText() }
                } catch (e: Exception) {
                    "No error body"
                }
                throw OllamaException("POST $path failed with code ${conn.responseCode}: $errorBody")
            }
            
            BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                reader.readText()
            }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Ollama generate response
 */
data class OllamaGenerateResponse(
    val model: String,
    val response: String,
    val done: Boolean,
    val totalDuration: Long,
    val promptEvalCount: Int,
    val evalCount: Int,
    val durationMs: Long
)

/**
 * Ollama chat response
 */
data class OllamaChatResponse(
    val model: String,
    val message: ChatMessage,
    val done: Boolean,
    val totalDuration: Long,
    val promptEvalCount: Int,
    val evalCount: Int,
    val durationMs: Long
)

/**
 * Ollama-specific exception
 */
class OllamaException(message: String, cause: Throwable? = null) : Exception(message, cause)
