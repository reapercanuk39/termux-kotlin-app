package com.termux.app.agents.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * Enhanced Tool definition with rich metadata.
 * 
 * Extends the basic Tool with:
 * - Tags and categories for organization
 * - Versioning for API compatibility
 * - Usage examples
 * - Cost/performance hints
 * - Deprecation status
 */
data class EnhancedTool(
    val name: String,
    val description: String,
    val parameters: List<EnhancedToolParameter>,
    val skillName: String,
    val functionName: String,
    val metadata: ToolMetadata = ToolMetadata()
) {
    /**
     * Convert to MCP-compatible JSON schema with extensions
     */
    fun toMcpSchema(): JSONObject {
        val properties = JSONObject()
        val required = JSONArray()
        
        parameters.forEach { param ->
            properties.put(param.name, param.toJsonSchema())
            if (param.required) {
                required.put(param.name)
            }
        }
        
        return JSONObject().apply {
            put("name", name)
            put("description", buildEnhancedDescription())
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", properties)
                if (required.length() > 0) {
                    put("required", required)
                }
            })
            
            // MCP extensions
            put("annotations", buildAnnotations())
        }
    }
    
    private fun buildEnhancedDescription(): String {
        val sb = StringBuilder(description)
        
        if (metadata.deprecated) {
            sb.append("\n\n⚠️ DEPRECATED: ${metadata.deprecationMessage}")
            if (metadata.replacementTool != null) {
                sb.append(" Use ${metadata.replacementTool} instead.")
            }
        }
        
        if (metadata.examples.isNotEmpty()) {
            sb.append("\n\nExamples:\n")
            metadata.examples.forEach { example ->
                sb.append("- ${example.description}\n")
            }
        }
        
        return sb.toString()
    }
    
    private fun buildAnnotations(): JSONObject {
        return JSONObject().apply {
            put("version", metadata.version)
            put("tags", JSONArray(metadata.tags))
            put("category", metadata.category)
            
            if (metadata.deprecated) {
                put("deprecated", true)
                put("deprecationMessage", metadata.deprecationMessage)
                metadata.replacementTool?.let { put("replacementTool", it) }
            }
            
            put("costHint", metadata.costHint.name.lowercase())
            put("latencyHint", metadata.latencyHint.name.lowercase())
            put("idempotent", metadata.idempotent)
            put("cacheable", metadata.cacheable)
            put("cacheableTtlSeconds", metadata.cacheableTtlSeconds)
            
            if (metadata.rateLimit != null) {
                put("rateLimit", JSONObject().apply {
                    put("requestsPerMinute", metadata.rateLimit.requestsPerMinute)
                    put("burstLimit", metadata.rateLimit.burstLimit)
                })
            }
        }
    }
    
    companion object {
        fun fromTool(tool: Tool, metadata: ToolMetadata = ToolMetadata()): EnhancedTool {
            return EnhancedTool(
                name = tool.name,
                description = tool.description,
                parameters = tool.parameters.map { EnhancedToolParameter.fromToolParameter(it) },
                skillName = tool.skillName,
                functionName = tool.functionName,
                metadata = metadata
            )
        }
    }
}

/**
 * Enhanced tool parameter with additional metadata
 */
data class EnhancedToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = false,
    val default: Any? = null,
    val enum: List<String>? = null,
    val format: String? = null,  // e.g., "uri", "email", "date-time"
    val pattern: String? = null,  // Regex pattern
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val minimum: Number? = null,
    val maximum: Number? = null,
    val examples: List<Any>? = null,
    val deprecated: Boolean = false,
    val deprecationMessage: String? = null
) {
    fun toJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("type", type.jsonType)
            put("description", buildDescription())
            
            default?.let { put("default", it) }
            enum?.let { put("enum", JSONArray(it)) }
            format?.let { put("format", it) }
            pattern?.let { put("pattern", it) }
            minLength?.let { put("minLength", it) }
            maxLength?.let { put("maxLength", it) }
            minimum?.let { put("minimum", it) }
            maximum?.let { put("maximum", it) }
            examples?.let { put("examples", JSONArray(it)) }
            
            if (deprecated) {
                put("deprecated", true)
            }
        }
    }
    
    private fun buildDescription(): String {
        return if (deprecated && deprecationMessage != null) {
            "$description\n⚠️ DEPRECATED: $deprecationMessage"
        } else {
            description
        }
    }
    
    companion object {
        fun fromToolParameter(param: ToolParameter): EnhancedToolParameter {
            return EnhancedToolParameter(
                name = param.name,
                type = param.type,
                description = param.description,
                required = param.required,
                default = param.default,
                enum = param.enum
            )
        }
    }
}

/**
 * Tool metadata for organization and hints
 */
data class ToolMetadata(
    val version: String = "1.0.0",
    val tags: List<String> = emptyList(),
    val category: String = "general",
    val deprecated: Boolean = false,
    val deprecationMessage: String = "",
    val replacementTool: String? = null,
    val costHint: CostHint = CostHint.LOW,
    val latencyHint: LatencyHint = LatencyHint.FAST,
    val idempotent: Boolean = false,
    val cacheable: Boolean = false,
    val cacheableTtlSeconds: Int = 0,
    val examples: List<ToolExample> = emptyList(),
    val rateLimit: RateLimitSpec? = null,
    val permissions: List<String> = emptyList(),
    val changelog: List<ChangelogEntry> = emptyList()
)

/**
 * Tool usage example
 */
data class ToolExample(
    val description: String,
    val input: Map<String, Any?>,
    val expectedOutput: String? = null
)

/**
 * Rate limit specification
 */
data class RateLimitSpec(
    val requestsPerMinute: Int = 60,
    val burstLimit: Int = 10
)

/**
 * Changelog entry for tool versioning
 */
data class ChangelogEntry(
    val version: String,
    val date: String,
    val changes: List<String>
)

/**
 * Cost hint for tool execution
 */
enum class CostHint {
    LOW,      // Quick, minimal resources
    MEDIUM,   // Moderate resources
    HIGH,     // Expensive, use sparingly
    VARIABLE  // Depends on input
}

/**
 * Latency hint for tool execution
 */
enum class LatencyHint {
    FAST,      // < 100ms
    MEDIUM,    // 100ms - 1s
    SLOW,      // 1s - 10s
    VERY_SLOW  // > 10s
}

/**
 * MCP Resource definition
 */
data class Resource(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String? = null,
    val metadata: ResourceMetadata = ResourceMetadata()
) {
    fun toMcpSchema(): JSONObject {
        return JSONObject().apply {
            put("uri", uri)
            put("name", name)
            put("description", description)
            mimeType?.let { put("mimeType", it) }
            put("annotations", JSONObject().apply {
                put("audience", JSONArray(metadata.audience))
                put("priority", metadata.priority)
            })
        }
    }
}

/**
 * Resource metadata
 */
data class ResourceMetadata(
    val audience: List<String> = listOf("user"),
    val priority: Float = 0.5f
)

/**
 * MCP Prompt definition
 */
data class Prompt(
    val name: String,
    val description: String,
    val arguments: List<PromptArgument> = emptyList()
) {
    fun toMcpSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("arguments", JSONArray().apply {
                arguments.forEach { arg ->
                    put(JSONObject().apply {
                        put("name", arg.name)
                        put("description", arg.description)
                        put("required", arg.required)
                    })
                }
            })
        }
    }
}

/**
 * Prompt argument
 */
data class PromptArgument(
    val name: String,
    val description: String,
    val required: Boolean = false
)

/**
 * Enhanced MCP capabilities
 */
data class EnhancedMcpCapabilities(
    val tools: ToolsCapability = ToolsCapability(),
    val resources: ResourcesCapability = ResourcesCapability(),
    val prompts: PromptsCapability = PromptsCapability(),
    val logging: LoggingCapability = LoggingCapability()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("tools", tools.toJson())
            put("resources", resources.toJson())
            put("prompts", prompts.toJson())
            put("logging", logging.toJson())
        }
    }
}

data class ToolsCapability(
    val listChanged: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("listChanged", listChanged)
    }
}

data class ResourcesCapability(
    val subscribe: Boolean = false,
    val listChanged: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("subscribe", subscribe)
        put("listChanged", listChanged)
    }
}

data class PromptsCapability(
    val listChanged: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("listChanged", listChanged)
    }
}

data class LoggingCapability(
    val enabled: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        if (enabled) {
            // Logging capability is empty object when enabled
        }
    }
}

/**
 * Extended MCP request types
 */
sealed class ExtendedMcpRequest {
    // Resource requests
    data object ListResources : ExtendedMcpRequest()
    data class ReadResource(val uri: String) : ExtendedMcpRequest()
    data class SubscribeResource(val uri: String) : ExtendedMcpRequest()
    data class UnsubscribeResource(val uri: String) : ExtendedMcpRequest()
    
    // Prompt requests
    data object ListPrompts : ExtendedMcpRequest()
    data class GetPrompt(val name: String, val arguments: Map<String, String>) : ExtendedMcpRequest()
    
    // Completion requests
    data class Complete(val ref: CompletionRef, val argument: CompletionArgument) : ExtendedMcpRequest()
    
    // Logging
    data class SetLoggingLevel(val level: String) : ExtendedMcpRequest()
}

data class CompletionRef(
    val type: String,  // "ref/prompt" or "ref/resource"
    val name: String? = null,
    val uri: String? = null
)

data class CompletionArgument(
    val name: String,
    val value: String
)

/**
 * Extended MCP response types
 */
sealed class ExtendedMcpResponse {
    abstract fun toJson(): JSONObject
    
    data class ResourceList(val resources: List<Resource>) : ExtendedMcpResponse() {
        override fun toJson() = JSONObject().apply {
            put("resources", JSONArray().apply {
                resources.forEach { put(it.toMcpSchema()) }
            })
        }
    }
    
    data class ResourceContent(
        val contents: List<ResourceContentItem>
    ) : ExtendedMcpResponse() {
        override fun toJson() = JSONObject().apply {
            put("contents", JSONArray().apply {
                contents.forEach { put(it.toJson()) }
            })
        }
    }
    
    data class PromptList(val prompts: List<Prompt>) : ExtendedMcpResponse() {
        override fun toJson() = JSONObject().apply {
            put("prompts", JSONArray().apply {
                prompts.forEach { put(it.toMcpSchema()) }
            })
        }
    }
    
    data class PromptResult(
        val description: String?,
        val messages: List<PromptMessage>
    ) : ExtendedMcpResponse() {
        override fun toJson() = JSONObject().apply {
            description?.let { put("description", it) }
            put("messages", JSONArray().apply {
                messages.forEach { put(it.toJson()) }
            })
        }
    }
    
    data class CompletionResult(
        val values: List<String>,
        val total: Int? = null,
        val hasMore: Boolean = false
    ) : ExtendedMcpResponse() {
        override fun toJson() = JSONObject().apply {
            put("completion", JSONObject().apply {
                put("values", JSONArray(values))
                total?.let { put("total", it) }
                put("hasMore", hasMore)
            })
        }
    }
}

data class ResourceContentItem(
    val uri: String,
    val mimeType: String?,
    val text: String? = null,
    val blob: String? = null  // Base64 encoded
) {
    fun toJson() = JSONObject().apply {
        put("uri", uri)
        mimeType?.let { put("mimeType", it) }
        text?.let { put("text", it) }
        blob?.let { put("blob", it) }
    }
}

data class PromptMessage(
    val role: String,  // "user" or "assistant"
    val content: PromptContent
) {
    fun toJson() = JSONObject().apply {
        put("role", role)
        put("content", content.toJson())
    }
}

sealed class PromptContent {
    abstract fun toJson(): JSONObject
    
    data class Text(val text: String) : PromptContent() {
        override fun toJson() = JSONObject().apply {
            put("type", "text")
            put("text", text)
        }
    }
    
    data class Image(val data: String, val mimeType: String) : PromptContent() {
        override fun toJson() = JSONObject().apply {
            put("type", "image")
            put("data", data)
            put("mimeType", mimeType)
        }
    }
    
    data class EmbeddedResource(
        val type: String,
        val resource: ResourceContentItem
    ) : PromptContent() {
        override fun toJson() = JSONObject().apply {
            put("type", "resource")
            put("resource", resource.toJson())
        }
    }
}
