package com.termux.app.agents.mcp

import com.termux.app.agents.skills.SkillProvider
import com.termux.shared.logger.Logger
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for MCP tools.
 * 
 * Manages tool registration and lookup for the MCP server.
 * Tools are mapped from skill functions.
 */
@Singleton
class ToolRegistry @Inject constructor() {
    
    companion object {
        private const val LOG_TAG = "ToolRegistry"
        private const val CONFIG_PATH = "/data/data/com.termux/files/usr/share/agents/etc/mcp_server.yml"
    }
    
    private val tools = ConcurrentHashMap<String, Tool>()
    private var config: McpConfig? = null
    
    /**
     * Load configuration and register tools
     */
    fun initialize(skills: Map<String, SkillProvider>) {
        config = loadConfig()
        
        if (config?.enabled != true) {
            Logger.logInfo(LOG_TAG, "MCP server disabled in config")
            return
        }
        
        // Register tools from config
        val exposedTools = config?.expose ?: emptyList()
        val toolMetadata = config?.tools ?: emptyMap()
        
        exposedTools.forEach { toolName ->
            registerToolFromConfig(toolName, toolMetadata, skills)
        }
        
        Logger.logInfo(LOG_TAG, "Registered ${tools.size} MCP tools")
    }
    
    /**
     * Register a tool from configuration
     */
    private fun registerToolFromConfig(
        toolName: String,
        metadata: Map<String, Map<String, Any?>>,
        skills: Map<String, SkillProvider>
    ) {
        val parts = toolName.split(".")
        if (parts.size != 2) {
            Logger.logWarn(LOG_TAG, "Invalid tool name format: $toolName (expected skill.function)")
            return
        }
        
        val (skillName, functionName) = parts
        val skill = skills[skillName]
        
        if (skill == null) {
            Logger.logWarn(LOG_TAG, "Skill not found for tool: $toolName")
            return
        }
        
        if (!skill.provides.contains(functionName)) {
            Logger.logWarn(LOG_TAG, "Function not found in skill: $toolName")
            return
        }
        
        // Get metadata from config
        val toolMeta = metadata[toolName] ?: emptyMap()
        val description = toolMeta["description"] as? String ?: "Execute $toolName"
        
        @Suppress("UNCHECKED_CAST")
        val paramsMeta = toolMeta["parameters"] as? Map<String, Map<String, Any?>> ?: emptyMap()
        
        val parameters = paramsMeta.map { (name, props) ->
            ToolParameter(
                name = name,
                type = parseParameterType(props["type"] as? String),
                description = props["description"] as? String ?: "",
                required = props["required"] as? Boolean ?: false,
                default = props["default"],
                enum = (props["enum"] as? List<*>)?.map { it.toString() }
            )
        }
        
        val tool = Tool(
            name = toolName,
            description = description,
            parameters = parameters,
            skillName = skillName,
            functionName = functionName
        )
        
        registerTool(tool)
    }
    
    /**
     * Parse parameter type from string
     */
    private fun parseParameterType(type: String?): ParameterType {
        return when (type?.lowercase()) {
            "string" -> ParameterType.STRING
            "integer", "int" -> ParameterType.INTEGER
            "number", "float", "double" -> ParameterType.NUMBER
            "boolean", "bool" -> ParameterType.BOOLEAN
            "array", "list" -> ParameterType.ARRAY
            "object", "map" -> ParameterType.OBJECT
            else -> ParameterType.STRING
        }
    }
    
    /**
     * Register a tool
     */
    fun registerTool(tool: Tool) {
        tools[tool.name] = tool
        Logger.logDebug(LOG_TAG, "Registered tool: ${tool.name}")
    }
    
    /**
     * Unregister a tool
     */
    fun unregisterTool(name: String) {
        tools.remove(name)
    }
    
    /**
     * Get a tool by name
     */
    fun getTool(name: String): Tool? = tools[name]
    
    /**
     * Get all registered tools
     */
    fun getAllTools(): List<Tool> = tools.values.toList()
    
    /**
     * Check if a tool is registered
     */
    fun hasTool(name: String): Boolean = tools.containsKey(name)
    
    /**
     * Get current configuration
     */
    fun getConfig(): McpConfig? = config
    
    /**
     * Load MCP configuration from file
     */
    private fun loadConfig(): McpConfig {
        try {
            val configFile = File(CONFIG_PATH)
            if (!configFile.exists()) {
                Logger.logWarn(LOG_TAG, "MCP config not found at $CONFIG_PATH")
                return McpConfig()
            }
            
            val yaml = Yaml()
            val configMap = configFile.inputStream().use { stream ->
                @Suppress("UNCHECKED_CAST")
                yaml.load<Map<String, Any?>>(stream) ?: emptyMap()
            }
            
            return McpConfig.fromMap(configMap)
            
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to load MCP config: ${e.message}")
            return McpConfig()
        }
    }
    
    /**
     * Reload configuration
     */
    fun reload(skills: Map<String, SkillProvider>) {
        tools.clear()
        initialize(skills)
    }
    
    /**
     * Register BusyBox tools programmatically.
     * 
     * These tools wrap BusyBox Modern CLI commands for system management.
     */
    fun registerBusyBoxTools() {
        val busyboxTools = listOf(
            Tool(
                name = "busybox.install",
                description = "Install BusyBox Modern - creates symlinks for all applets in the specified directory",
                parameters = listOf(
                    ToolParameter(
                        name = "symlink_dir",
                        type = ParameterType.STRING,
                        description = "Directory to install symlinks (default: /data/local/busybox-modern)",
                        required = false,
                        default = "/data/local/busybox-modern"
                    ),
                    ToolParameter(
                        name = "force",
                        type = ParameterType.BOOLEAN,
                        description = "Force overwrite of existing symlinks",
                        required = false,
                        default = false
                    )
                ),
                skillName = "busybox",
                functionName = "install"
            ),
            Tool(
                name = "busybox.uninstall",
                description = "Uninstall BusyBox Modern - removes symlinks and cleans up PATH",
                parameters = listOf(
                    ToolParameter(
                        name = "symlink_dir",
                        type = ParameterType.STRING,
                        description = "Directory to remove symlinks from",
                        required = false,
                        default = "/data/local/busybox-modern"
                    ),
                    ToolParameter(
                        name = "clean_path",
                        type = ParameterType.BOOLEAN,
                        description = "Also remove from PATH",
                        required = false,
                        default = true
                    )
                ),
                skillName = "busybox",
                functionName = "uninstall"
            ),
            Tool(
                name = "busybox.diagnose",
                description = "Run comprehensive BusyBox diagnostics - check binary, symlinks, PATH, and applets",
                parameters = listOf(
                    ToolParameter(
                        name = "force_refresh",
                        type = ParameterType.BOOLEAN,
                        description = "Force refresh of cached diagnostics",
                        required = false,
                        default = false
                    )
                ),
                skillName = "busybox",
                functionName = "diagnose"
            ),
            Tool(
                name = "busybox.repair_symlinks",
                description = "Repair broken or missing BusyBox symlinks",
                parameters = listOf(
                    ToolParameter(
                        name = "symlink_dir",
                        type = ParameterType.STRING,
                        description = "Directory to repair symlinks in",
                        required = false,
                        default = "/data/local/busybox-modern"
                    ),
                    ToolParameter(
                        name = "dry_run",
                        type = ParameterType.BOOLEAN,
                        description = "Preview changes without making them",
                        required = false,
                        default = false
                    )
                ),
                skillName = "busybox",
                functionName = "repair_symlinks"
            ),
            Tool(
                name = "busybox.repair_path",
                description = "Repair PATH to include BusyBox directory",
                parameters = listOf(
                    ToolParameter(
                        name = "symlink_dir",
                        type = ParameterType.STRING,
                        description = "BusyBox directory to add to PATH",
                        required = false,
                        default = "/data/local/busybox-modern"
                    ),
                    ToolParameter(
                        name = "prepend",
                        type = ParameterType.BOOLEAN,
                        description = "Add to beginning of PATH (higher priority)",
                        required = false,
                        default = true
                    )
                ),
                skillName = "busybox",
                functionName = "repair_path"
            ),
            Tool(
                name = "busybox.magisk_check",
                description = "Check Magisk installation and BusyBox module status",
                parameters = emptyList(),
                skillName = "busybox",
                functionName = "magisk_check"
            ),
            Tool(
                name = "busybox.list_applets",
                description = "List all available BusyBox applets",
                parameters = emptyList(),
                skillName = "busybox",
                functionName = "list_applets"
            ),
            Tool(
                name = "busybox.run_applet",
                description = "Run a specific BusyBox applet with arguments",
                parameters = listOf(
                    ToolParameter(
                        name = "applet",
                        type = ParameterType.STRING,
                        description = "Name of the applet to run (e.g., ls, grep, awk)",
                        required = true
                    ),
                    ToolParameter(
                        name = "args",
                        type = ParameterType.ARRAY,
                        description = "Arguments to pass to the applet",
                        required = false,
                        default = emptyList<String>()
                    )
                ),
                skillName = "busybox",
                functionName = "run_applet"
            ),
            Tool(
                name = "busybox.get_version",
                description = "Get BusyBox version information",
                parameters = emptyList(),
                skillName = "busybox",
                functionName = "get_version"
            ),
            Tool(
                name = "busybox.check_binary",
                description = "Check if BusyBox binary exists and is executable",
                parameters = emptyList(),
                skillName = "busybox",
                functionName = "check_binary"
            )
        )
        
        busyboxTools.forEach { tool ->
            registerTool(tool)
        }
        
        Logger.logInfo(LOG_TAG, "Registered ${busyboxTools.size} BusyBox tools")
    }
}

/**
 * MCP server configuration
 */
data class McpConfig(
    val enabled: Boolean = false,
    val host: String = "127.0.0.1",
    val port: Int = 50052,
    val protocol: String = "http",
    val authEnabled: Boolean = false,
    val authToken: String? = null,
    val expose: List<String> = emptyList(),
    val tools: Map<String, Map<String, Any?>> = emptyMap(),
    val rateLimitEnabled: Boolean = true,
    val requestsPerMinute: Int = 60,
    val maxConcurrent: Int = 10
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): McpConfig {
            val serverMap = map["server"] as? Map<*, *> ?: emptyMap<String, Any?>()
            val authMap = map["auth"] as? Map<*, *> ?: emptyMap<String, Any?>()
            val rateLimitMap = map["rate_limit"] as? Map<*, *> ?: emptyMap<String, Any?>()
            
            @Suppress("UNCHECKED_CAST")
            val expose = map["expose"] as? List<String> ?: emptyList()
            
            @Suppress("UNCHECKED_CAST")
            val tools = map["tools"] as? Map<String, Map<String, Any?>> ?: emptyMap()
            
            return McpConfig(
                enabled = map["enabled"] as? Boolean ?: false,
                host = serverMap["host"] as? String ?: "127.0.0.1",
                port = (serverMap["port"] as? Number)?.toInt() ?: 50052,
                protocol = serverMap["protocol"] as? String ?: "http",
                authEnabled = authMap["enabled"] as? Boolean ?: false,
                authToken = authMap["token"] as? String,
                expose = expose,
                tools = tools,
                rateLimitEnabled = rateLimitMap["enabled"] as? Boolean ?: true,
                requestsPerMinute = (rateLimitMap["requests_per_minute"] as? Number)?.toInt() ?: 60,
                maxConcurrent = (rateLimitMap["max_concurrent"] as? Number)?.toInt() ?: 10
            )
        }
    }
}
