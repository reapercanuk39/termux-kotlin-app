package com.termux.app.agents.llm

import com.termux.shared.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Streaming Ollama client with support for token-by-token generation.
 * 
 * Extends OllamaClient with streaming capabilities using Kotlin Flow.
 * Enables real-time token streaming for responsive AI interactions.
 */
class StreamingOllamaClient(
    private val baseUrl: String = "http://localhost:11434",
    private val timeoutMs: Long = 120_000L  // Longer timeout for streaming
) {
    companion object {
        private const val LOG_TAG = "StreamingOllamaClient"
    }
    
    /**
     * Streaming callback interface for receiving tokens
     */
    interface StreamingCallback {
        /**
         * Called for each generated token
         */
        fun onToken(token: String)
        
        /**
         * Called when generation is complete
         */
        fun onComplete(response: StreamingResponse)
        
        /**
         * Called on error
         */
        fun onError(error: Throwable)
        
        /**
         * Called with progress updates
         */
        fun onProgress(tokensGenerated: Int, elapsedMs: Long) {}
    }
    
    /**
     * Streaming response data
     */
    data class StreamingResponse(
        val model: String,
        val fullResponse: String,
        val tokenCount: Int,
        val totalDurationNs: Long,
        val promptEvalCount: Int,
        val evalCount: Int,
        val tokensPerSecond: Double
    )
    
    /**
     * Token event for Flow-based streaming
     */
    sealed class TokenEvent {
        data class Token(val text: String, val index: Int) : TokenEvent()
        data class Progress(val tokensGenerated: Int, val elapsedMs: Long) : TokenEvent()
        data class Complete(val response: StreamingResponse) : TokenEvent()
        data class Error(val error: Throwable) : TokenEvent()
    }
    
    /**
     * Stream text generation as Flow
     */
    fun generateStream(
        model: String,
        prompt: String,
        systemPrompt: String? = null,
        options: LlmOptions = LlmOptions()
    ): Flow<TokenEvent> = callbackFlow {
        val requestBody = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("stream", true)
            
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
        
        try {
            val url = URL("$baseUrl/api/generate")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs.toInt()
            conn.readTimeout = timeoutMs.toInt()
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            
            withContext(Dispatchers.IO) {
                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(requestBody.toString())
                }
                
                if (conn.responseCode != 200) {
                    throw OllamaException("Stream request failed with code ${conn.responseCode}")
                }
                
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val startTime = System.currentTimeMillis()
                val responseBuilder = StringBuilder()
                var tokenIndex = 0
                var lastProgressTime = startTime
                
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val json = try {
                        JSONObject(line)
                    } catch (e: Exception) {
                        continue
                    }
                    
                    val token = json.optString("response", "")
                    if (token.isNotEmpty()) {
                        responseBuilder.append(token)
                        trySend(TokenEvent.Token(token, tokenIndex++))
                    }
                    
                    // Send progress every 500ms
                    val now = System.currentTimeMillis()
                    if (now - lastProgressTime > 500) {
                        trySend(TokenEvent.Progress(tokenIndex, now - startTime))
                        lastProgressTime = now
                    }
                    
                    // Check if done
                    if (json.optBoolean("done", false)) {
                        val totalDuration = json.optLong("total_duration", 0)
                        val evalCount = json.optInt("eval_count", tokenIndex)
                        val evalDuration = json.optLong("eval_duration", 1)
                        
                        val tokensPerSecond = if (evalDuration > 0) {
                            evalCount.toDouble() / (evalDuration.toDouble() / 1_000_000_000.0)
                        } else 0.0
                        
                        val response = StreamingResponse(
                            model = json.optString("model", model),
                            fullResponse = responseBuilder.toString(),
                            tokenCount = tokenIndex,
                            totalDurationNs = totalDuration,
                            promptEvalCount = json.optInt("prompt_eval_count", 0),
                            evalCount = evalCount,
                            tokensPerSecond = tokensPerSecond
                        )
                        
                        trySend(TokenEvent.Complete(response))
                        break
                    }
                }
                
                reader.close()
                conn.disconnect()
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Streaming error: ${e.message}")
            trySend(TokenEvent.Error(e))
        }
        
        awaitClose { }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Stream chat completion as Flow
     */
    fun chatStream(
        model: String,
        messages: List<ChatMessage>,
        options: LlmOptions = LlmOptions()
    ): Flow<TokenEvent> = callbackFlow {
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
            put("stream", true)
            
            put("options", JSONObject().apply {
                put("num_predict", options.maxTokens)
                put("temperature", options.temperature.toDouble())
                put("top_p", options.topP.toDouble())
            })
            
            if (options.stop.isNotEmpty()) {
                put("stop", JSONArray(options.stop))
            }
        }
        
        try {
            val url = URL("$baseUrl/api/chat")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs.toInt()
            conn.readTimeout = timeoutMs.toInt()
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            
            withContext(Dispatchers.IO) {
                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(requestBody.toString())
                }
                
                if (conn.responseCode != 200) {
                    throw OllamaException("Chat stream failed with code ${conn.responseCode}")
                }
                
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val startTime = System.currentTimeMillis()
                val responseBuilder = StringBuilder()
                var tokenIndex = 0
                var lastProgressTime = startTime
                
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val json = try {
                        JSONObject(line)
                    } catch (e: Exception) {
                        continue
                    }
                    
                    val messageJson = json.optJSONObject("message")
                    val token = messageJson?.optString("content", "") ?: ""
                    
                    if (token.isNotEmpty()) {
                        responseBuilder.append(token)
                        trySend(TokenEvent.Token(token, tokenIndex++))
                    }
                    
                    // Send progress every 500ms
                    val now = System.currentTimeMillis()
                    if (now - lastProgressTime > 500) {
                        trySend(TokenEvent.Progress(tokenIndex, now - startTime))
                        lastProgressTime = now
                    }
                    
                    if (json.optBoolean("done", false)) {
                        val totalDuration = json.optLong("total_duration", 0)
                        val evalCount = json.optInt("eval_count", tokenIndex)
                        val evalDuration = json.optLong("eval_duration", 1)
                        
                        val tokensPerSecond = if (evalDuration > 0) {
                            evalCount.toDouble() / (evalDuration.toDouble() / 1_000_000_000.0)
                        } else 0.0
                        
                        val response = StreamingResponse(
                            model = json.optString("model", model),
                            fullResponse = responseBuilder.toString(),
                            tokenCount = tokenIndex,
                            totalDurationNs = totalDuration,
                            promptEvalCount = json.optInt("prompt_eval_count", 0),
                            evalCount = evalCount,
                            tokensPerSecond = tokensPerSecond
                        )
                        
                        trySend(TokenEvent.Complete(response))
                        break
                    }
                }
                
                reader.close()
                conn.disconnect()
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Chat streaming error: ${e.message}")
            trySend(TokenEvent.Error(e))
        }
        
        awaitClose { }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Stream generation with callback (for Java interop)
     */
    suspend fun generateWithCallback(
        model: String,
        prompt: String,
        callback: StreamingCallback,
        systemPrompt: String? = null,
        options: LlmOptions = LlmOptions()
    ) {
        generateStream(model, prompt, systemPrompt, options).collect { event ->
            when (event) {
                is TokenEvent.Token -> callback.onToken(event.text)
                is TokenEvent.Progress -> callback.onProgress(event.tokensGenerated, event.elapsedMs)
                is TokenEvent.Complete -> callback.onComplete(event.response)
                is TokenEvent.Error -> callback.onError(event.error)
            }
        }
    }
    
    /**
     * Stream chat with callback (for Java interop)
     */
    suspend fun chatWithCallback(
        model: String,
        messages: List<ChatMessage>,
        callback: StreamingCallback,
        options: LlmOptions = LlmOptions()
    ) {
        chatStream(model, messages, options).collect { event ->
            when (event) {
                is TokenEvent.Token -> callback.onToken(event.text)
                is TokenEvent.Progress -> callback.onProgress(event.tokensGenerated, event.elapsedMs)
                is TokenEvent.Complete -> callback.onComplete(event.response)
                is TokenEvent.Error -> callback.onError(event.error)
            }
        }
    }
}

/**
 * Builder for creating streaming Ollama clients
 */
class StreamingOllamaClientBuilder {
    private var baseUrl: String = "http://localhost:11434"
    private var timeoutMs: Long = 120_000L
    
    fun baseUrl(url: String) = apply { this.baseUrl = url }
    fun timeout(ms: Long) = apply { this.timeoutMs = ms }
    
    fun build() = StreamingOllamaClient(baseUrl, timeoutMs)
}

/**
 * Extension to convert regular OllamaClient to streaming
 */
fun OllamaClient.toStreaming(): StreamingOllamaClient {
    // Note: This creates a new client with default settings
    // In production, you'd want to share configuration
    return StreamingOllamaClient()
}
