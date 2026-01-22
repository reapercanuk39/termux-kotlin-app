package com.termux.app.agents.llm

import android.content.Context
import com.termux.shared.logger.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.yaml.snakeyaml.Yaml
import java.io.File
import javax.inject.Singleton

/**
 * Hilt module for LLM provider dependency injection.
 * 
 * Provides LlmProvider based on configuration:
 * - NoOpLlmProvider for skill-only mode
 * - OllamaLlmProvider for Ollama integration
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmModule {
    
    private const val LOG_TAG = "LlmModule"
    private const val CONFIG_PATH = "/data/data/com.termux/files/usr/share/agents/etc/config.yml"
    
    @Provides
    @Singleton
    fun provideLlmConfig(@ApplicationContext context: Context): LlmConfig {
        return loadConfig()
    }
    
    @Provides
    @Singleton
    fun provideLlmProvider(config: LlmConfig): LlmProvider {
        return createProvider(config)
    }
    
    /**
     * Load LLM config from file
     */
    private fun loadConfig(): LlmConfig {
        try {
            val configFile = File(CONFIG_PATH)
            if (!configFile.exists()) {
                Logger.logWarn(LOG_TAG, "Config file not found at $CONFIG_PATH, using defaults")
                return LlmConfig.SKILL_ONLY
            }
            
            val yaml = Yaml()
            val configMap = configFile.inputStream().use { stream ->
                @Suppress("UNCHECKED_CAST")
                yaml.load<Map<String, Any?>>(stream) ?: emptyMap()
            }
            
            val config = LlmConfig.fromMap(configMap)
            Logger.logInfo(LOG_TAG, "Loaded LLM config: provider=${config.provider}, model=${config.model}")
            return config
            
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to load config: ${e.message}")
            return LlmConfig.SKILL_ONLY
        }
    }
    
    /**
     * Create LLM provider based on config
     */
    private fun createProvider(config: LlmConfig): LlmProvider {
        return when (config.provider.lowercase()) {
            "ollama" -> {
                Logger.logInfo(LOG_TAG, "Creating Ollama provider with model ${config.model}")
                OllamaLlmProvider.fromConfig(config)
            }
            "none", "" -> {
                Logger.logInfo(LOG_TAG, "LLM disabled - using skill-only mode")
                NoOpLlmProvider()
            }
            else -> {
                Logger.logWarn(LOG_TAG, "Unknown LLM provider '${config.provider}', using skill-only mode")
                NoOpLlmProvider()
            }
        }
    }
}

/**
 * Factory for creating LLM providers without DI.
 * Used for testing and standalone usage.
 */
object LlmProviderFactory {
    
    private const val LOG_TAG = "LlmProviderFactory"
    
    /**
     * Create default provider based on config file
     */
    fun createDefault(): LlmProvider {
        val configFile = File("/data/data/com.termux/files/usr/share/agents/etc/config.yml")
        
        if (!configFile.exists()) {
            return NoOpLlmProvider()
        }
        
        return try {
            val yaml = Yaml()
            val configMap = configFile.inputStream().use { stream ->
                @Suppress("UNCHECKED_CAST")
                yaml.load<Map<String, Any?>>(stream) ?: emptyMap()
            }
            
            val config = LlmConfig.fromMap(configMap)
            create(config)
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to create provider: ${e.message}")
            NoOpLlmProvider()
        }
    }
    
    /**
     * Create provider from config
     */
    fun create(config: LlmConfig): LlmProvider {
        return when (config.provider.lowercase()) {
            "ollama" -> OllamaLlmProvider.fromConfig(config)
            else -> NoOpLlmProvider()
        }
    }
    
    /**
     * Create Ollama provider with explicit settings
     */
    fun createOllama(
        baseUrl: String = "http://localhost:11434",
        model: String = "qwen2:7b",
        timeoutMs: Long = 30_000L
    ): OllamaLlmProvider {
        val client = OllamaClient(baseUrl, timeoutMs)
        return OllamaLlmProvider(client, model)
    }
    
    /**
     * Create no-op provider for skill-only mode
     */
    fun createNoOp(): NoOpLlmProvider = NoOpLlmProvider()
}
