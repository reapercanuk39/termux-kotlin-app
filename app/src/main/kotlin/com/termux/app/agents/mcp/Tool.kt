package com.termux.app.agents.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP Tool definition.
 * 
 * Represents a single tool that can be exposed via MCP protocol.
 * Maps to a skill.function in the agent framework.
 */
data class Tool(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    val skillName: String,
    val functionName: String
) {
    /**
     * Convert to MCP-compatible JSON schema
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
            put("description", description)
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", properties)
                if (required.length() > 0) {
                    put("required", required)
                }
            })
        }
    }
    
    companion object {
        /**
         * Create tool from skill.function notation
         */
        fun fromSkillFunction(
            skillFunction: String,
            description: String = "",
            parameters: List<ToolParameter> = emptyList()
        ): Tool {
            val parts = skillFunction.split(".")
            require(parts.size == 2) { "Invalid skill.function format: $skillFunction" }
            
            return Tool(
                name = skillFunction,
                description = description.ifEmpty { "Execute $skillFunction" },
                parameters = parameters,
                skillName = parts[0],
                functionName = parts[1]
            )
        }
    }
}

/**
 * Tool parameter definition
 */
data class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = false,
    val default: Any? = null,
    val enum: List<String>? = null
) {
    /**
     * Convert to JSON schema property
     */
    fun toJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("type", type.jsonType)
            put("description", description)
            
            default?.let { put("default", it) }
            enum?.let { put("enum", JSONArray(it)) }
        }
    }
}

/**
 * Parameter types for MCP tools
 */
enum class ParameterType(val jsonType: String) {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    ARRAY("array"),
    OBJECT("object")
}

/**
 * MCP request types
 */
sealed class McpRequest {
    data class Initialize(
        val protocolVersion: String,
        val capabilities: Map<String, Any>?,
        val clientInfo: Map<String, String>?
    ) : McpRequest()
    
    data object ListTools : McpRequest()
    
    data class CallTool(
        val name: String,
        val arguments: Map<String, Any?>
    ) : McpRequest()
    
    data object Ping : McpRequest()
    
    data class Unknown(val method: String) : McpRequest()
}

/**
 * MCP response types
 */
sealed class McpResponse {
    abstract fun toJson(): JSONObject
    
    data class InitializeResult(
        val protocolVersion: String = "2024-11-05",
        val serverInfo: Map<String, String> = mapOf(
            "name" to "termux-agent-mcp",
            "version" to "1.0.0"
        ),
        val capabilities: Map<String, Any> = mapOf(
            "tools" to emptyMap<String, Any>()
        )
    ) : McpResponse() {
        override fun toJson() = JSONObject().apply {
            put("protocolVersion", protocolVersion)
            put("serverInfo", JSONObject(serverInfo))
            put("capabilities", JSONObject(capabilities))
        }
    }
    
    data class ToolList(val tools: List<Tool>) : McpResponse() {
        override fun toJson() = JSONObject().apply {
            put("tools", JSONArray().apply {
                tools.forEach { put(it.toMcpSchema()) }
            })
        }
    }
    
    data class ToolResult(
        val content: List<ContentBlock>,
        val isError: Boolean = false
    ) : McpResponse() {
        override fun toJson() = JSONObject().apply {
            put("content", JSONArray().apply {
                content.forEach { put(it.toJson()) }
            })
            if (isError) put("isError", true)
        }
    }
    
    data class Error(
        val code: Int,
        val message: String,
        val data: Any? = null
    ) : McpResponse() {
        override fun toJson() = JSONObject().apply {
            put("code", code)
            put("message", message)
            data?.let { put("data", it) }
        }
    }
    
    data object Pong : McpResponse() {
        override fun toJson() = JSONObject()
    }
}

/**
 * Content block in tool response
 */
sealed class ContentBlock {
    abstract fun toJson(): JSONObject
    
    data class Text(val text: String) : ContentBlock() {
        override fun toJson() = JSONObject().apply {
            put("type", "text")
            put("text", text)
        }
    }
    
    data class Image(val data: String, val mimeType: String) : ContentBlock() {
        override fun toJson() = JSONObject().apply {
            put("type", "image")
            put("data", data)
            put("mimeType", mimeType)
        }
    }
    
    data class Resource(val uri: String, val mimeType: String?, val text: String?) : ContentBlock() {
        override fun toJson() = JSONObject().apply {
            put("type", "resource")
            put("resource", JSONObject().apply {
                put("uri", uri)
                mimeType?.let { put("mimeType", it) }
                text?.let { put("text", it) }
            })
        }
    }
}

/**
 * MCP error codes
 */
object McpErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
    const val TOOL_NOT_FOUND = -32000
    const val TOOL_EXECUTION_ERROR = -32001
}
