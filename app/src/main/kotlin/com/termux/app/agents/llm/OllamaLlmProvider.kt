package com.termux.app.agents.llm

import com.termux.shared.logger.Logger

/**
 * Ollama-based LLM provider for local AI inference.
 * 
 * Connects to a local Ollama server to provide:
 * - Text completion
 * - Chat completion
 * - Model availability checking
 * 
 * Falls back gracefully if Ollama is not available.
 * 
 * @param client OllamaClient for HTTP communication
 * @param model Model name to use (e.g., "qwen2:7b", "llama3:8b")
 * @param fallbackModels Alternative models to try if primary model unavailable
 */
class OllamaLlmProvider(
    private val client: OllamaClient,
    private var model: String = "qwen2:7b",
    private val fallbackModels: List<String> = emptyList()
) : LlmProvider {
    
    companion object {
        private const val LOG_TAG = "OllamaLlmProvider"
        
        /**
         * Create provider from config
         */
        fun fromConfig(config: LlmConfig): OllamaLlmProvider {
            val client = OllamaClient(
                baseUrl = config.baseUrl,
                timeoutMs = config.timeoutMs
            )
            return OllamaLlmProvider(
                client = client,
                model = config.model,
                fallbackModels = config.fallbackModels
            )
        }
    }
    
    override val name: String = "ollama"
    
    private var serverAvailable: Boolean? = null
    private var modelAvailable: Boolean? = null
    
    override suspend fun isAvailable(): Boolean {
        // Check server availability
        if (serverAvailable == null) {
            serverAvailable = client.isServerAvailable()
        }
        
        if (serverAvailable != true) {
            Logger.logDebug(LOG_TAG, "Ollama server not available")
            return false
        }
        
        // Check model availability
        if (modelAvailable == null) {
            modelAvailable = client.isModelAvailable(model)
            
            // Try fallback models if primary not available
            if (modelAvailable != true && fallbackModels.isNotEmpty()) {
                for (fallback in fallbackModels) {
                    if (client.isModelAvailable(fallback)) {
                        Logger.logInfo(LOG_TAG, "Using fallback model: $fallback")
                        model = fallback
                        modelAvailable = true
                        break
                    }
                }
            }
        }
        
        if (modelAvailable != true) {
            Logger.logWarn(LOG_TAG, "Model not available: $model")
        }
        
        return serverAvailable == true && modelAvailable == true
    }
    
    override fun getModel(): String = model
    
    override suspend fun complete(
        prompt: String,
        systemPrompt: String?,
        options: LlmOptions
    ): Result<LlmResponse> {
        Logger.logDebug(LOG_TAG, "Completing with model=$model, prompt length=${prompt.length}")
        
        // Check availability
        if (!isAvailable()) {
            return Result.failure(
                OllamaException("Ollama not available - server or model unavailable")
            )
        }
        
        return client.generate(
            model = model,
            prompt = prompt,
            systemPrompt = systemPrompt,
            options = options
        ).map { response ->
            LlmResponse(
                content = response.response,
                model = response.model,
                tokensUsed = response.promptEvalCount + response.evalCount,
                durationMs = response.durationMs,
                finishReason = if (response.done) "stop" else "length"
            )
        }
    }
    
    override suspend fun chat(
        messages: List<ChatMessage>,
        options: LlmOptions
    ): Result<LlmResponse> {
        Logger.logDebug(LOG_TAG, "Chat with model=$model, messages=${messages.size}")
        
        // Check availability
        if (!isAvailable()) {
            return Result.failure(
                OllamaException("Ollama not available - server or model unavailable")
            )
        }
        
        return client.chat(
            model = model,
            messages = messages,
            options = options
        ).map { response ->
            LlmResponse(
                content = response.message.content,
                model = response.model,
                tokensUsed = response.promptEvalCount + response.evalCount,
                durationMs = response.durationMs,
                finishReason = if (response.done) "stop" else "length"
            )
        }
    }
    
    override fun supportsStreaming(): Boolean = false  // TODO: Add streaming support
    
    /**
     * Refresh availability status (e.g., after installing a model)
     */
    fun refreshAvailability() {
        serverAvailable = null
        modelAvailable = null
    }
    
    /**
     * Get list of available models
     */
    suspend fun listModels(): List<String> {
        return client.listModels().getOrDefault(emptyList())
    }
}

/**
 * LLM configuration loaded from config.yml
 */
data class LlmConfig(
    val provider: String = "none",
    val model: String = "qwen2:7b",
    val baseUrl: String = "http://localhost:11434",
    val timeoutMs: Long = 30_000L,
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val fallbackModels: List<String> = emptyList()
) {
    companion object {
        /**
         * Parse from config map
         */
        fun fromMap(map: Map<String, Any?>): LlmConfig {
            val llmMap = map["llm"] as? Map<*, *> ?: return LlmConfig()
            
            @Suppress("UNCHECKED_CAST")
            val fallbackRaw = llmMap["fallback"] as? List<String> ?: emptyList()
            val fallbackModels = fallbackRaw.mapNotNull { entry ->
                when {
                    entry.startsWith("ollama:") -> entry.removePrefix("ollama:")
                    entry == "skill_only" -> null  // Skip skill_only in model list
                    else -> entry
                }
            }
            
            return LlmConfig(
                provider = llmMap["provider"] as? String ?: "none",
                model = llmMap["model"] as? String ?: "qwen2:7b",
                baseUrl = llmMap["base_url"] as? String ?: "http://localhost:11434",
                timeoutMs = (llmMap["timeout_ms"] as? Number)?.toLong() ?: 30_000L,
                maxTokens = (llmMap["max_tokens"] as? Number)?.toInt() ?: 2048,
                temperature = (llmMap["temperature"] as? Number)?.toFloat() ?: 0.7f,
                fallbackModels = fallbackModels
            )
        }
        
        /**
         * Default config for skill-only mode
         */
        val SKILL_ONLY = LlmConfig(provider = "none")
    }
    
    val isEnabled: Boolean
        get() = provider != "none"
}
