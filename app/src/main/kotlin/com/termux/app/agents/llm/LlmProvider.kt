package com.termux.app.agents.llm

/**
 * Message in a chat conversation
 */
data class ChatMessage(
    val role: String,  // "system", "user", "assistant"
    val content: String
)

/**
 * Response from LLM completion
 */
data class LlmResponse(
    val content: String,
    val model: String,
    val tokensUsed: Int = 0,
    val durationMs: Long = 0,
    val finishReason: String = "stop"
)

/**
 * LLM generation options
 */
data class LlmOptions(
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val stop: List<String> = emptyList()
)

/**
 * LLM Provider interface.
 * 
 * Abstraction layer for different LLM backends.
 * Implementations:
 * - NoOpLlmProvider: Skill-only mode (no LLM)
 * - OllamaLlmProvider: Local Ollama server
 */
interface LlmProvider {
    /**
     * Provider name for logging
     */
    val name: String
    
    /**
     * Check if the provider is available and ready
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * Get current model name
     */
    fun getModel(): String
    
    /**
     * Simple text completion
     */
    suspend fun complete(
        prompt: String,
        systemPrompt: String? = null,
        options: LlmOptions = LlmOptions()
    ): Result<LlmResponse>
    
    /**
     * Chat completion with message history
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        options: LlmOptions = LlmOptions()
    ): Result<LlmResponse>
    
    /**
     * Check if provider supports streaming
     */
    fun supportsStreaming(): Boolean = false
    
    /**
     * Stream completion (optional, returns immediately for non-streaming providers)
     */
    suspend fun streamComplete(
        prompt: String,
        systemPrompt: String? = null,
        options: LlmOptions = LlmOptions(),
        onToken: (String) -> Unit
    ): Result<LlmResponse> {
        // Default: non-streaming fallback
        val result = complete(prompt, systemPrompt, options)
        result.onSuccess { response ->
            onToken(response.content)
        }
        return result
    }
}

/**
 * Result wrapper for LLM operations
 */
sealed class LlmResult<out T> {
    data class Success<T>(val value: T) : LlmResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : LlmResult<Nothing>()
    
    inline fun <R> map(transform: (T) -> R): LlmResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Error -> this
    }
    
    inline fun onSuccess(action: (T) -> Unit): LlmResult<T> {
        if (this is Success) action(value)
        return this
    }
    
    inline fun onError(action: (String, Throwable?) -> Unit): LlmResult<T> {
        if (this is Error) action(message, cause)
        return this
    }
    
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Error -> null
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Error -> throw cause ?: RuntimeException(message)
    }
}
