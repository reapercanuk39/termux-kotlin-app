package com.termux.app.agents.llm

import com.termux.shared.logger.Logger

/**
 * No-op LLM provider for skill-only mode.
 * 
 * This provider is used when:
 * - LLM is disabled in configuration (provider: none)
 * - Ollama is not available or not installed
 * - Fallback when other providers fail
 * 
 * Always returns a message indicating LLM is not available,
 * allowing the agent framework to continue with skill-only execution.
 */
class NoOpLlmProvider : LlmProvider {
    
    companion object {
        private const val LOG_TAG = "NoOpLlmProvider"
    }
    
    override val name: String = "none"
    
    override suspend fun isAvailable(): Boolean {
        // Always "available" since it's the fallback
        return true
    }
    
    override fun getModel(): String = "none"
    
    override suspend fun complete(
        prompt: String,
        systemPrompt: String?,
        options: LlmOptions
    ): Result<LlmResponse> {
        Logger.logDebug(LOG_TAG, "NoOp complete called - LLM disabled")
        
        return Result.success(
            LlmResponse(
                content = "[LLM disabled - skill-only mode. Configure 'llm.provider: ollama' in config.yml to enable AI inference.]",
                model = "none",
                tokensUsed = 0,
                durationMs = 0,
                finishReason = "noop"
            )
        )
    }
    
    override suspend fun chat(
        messages: List<ChatMessage>,
        options: LlmOptions
    ): Result<LlmResponse> {
        Logger.logDebug(LOG_TAG, "NoOp chat called with ${messages.size} messages - LLM disabled")
        
        return Result.success(
            LlmResponse(
                content = "[LLM disabled - skill-only mode. Configure 'llm.provider: ollama' in config.yml to enable AI inference.]",
                model = "none",
                tokensUsed = 0,
                durationMs = 0,
                finishReason = "noop"
            )
        )
    }
    
    override fun supportsStreaming(): Boolean = false
}
