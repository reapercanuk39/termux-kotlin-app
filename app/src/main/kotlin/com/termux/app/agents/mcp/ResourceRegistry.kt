package com.termux.app.agents.mcp

import com.termux.shared.logger.Logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for MCP Resources.
 * 
 * Manages resource registration and provides content for resource reads.
 * Supports file-based resources, dynamic resources, and subscriptions.
 */
@Singleton
class ResourceRegistry @Inject constructor() {
    
    companion object {
        private const val LOG_TAG = "ResourceRegistry"
    }
    
    private val resources = ConcurrentHashMap<String, ResourceEntry>()
    private val subscriptions = ConcurrentHashMap<String, MutableSet<String>>()  // uri -> clientIds
    
    /**
     * Register a file-based resource
     */
    fun registerFileResource(
        uri: String,
        name: String,
        description: String,
        filePath: String,
        mimeType: String? = null
    ) {
        val entry = ResourceEntry.FileResource(
            resource = Resource(
                uri = uri,
                name = name,
                description = description,
                mimeType = mimeType ?: guessMimeType(filePath)
            ),
            filePath = filePath
        )
        resources[uri] = entry
        Logger.logDebug(LOG_TAG, "Registered file resource: $uri -> $filePath")
    }
    
    /**
     * Register a dynamic resource with a content provider
     */
    fun registerDynamicResource(
        uri: String,
        name: String,
        description: String,
        mimeType: String,
        provider: () -> String
    ) {
        val entry = ResourceEntry.DynamicResource(
            resource = Resource(
                uri = uri,
                name = name,
                description = description,
                mimeType = mimeType
            ),
            contentProvider = provider
        )
        resources[uri] = entry
        Logger.logDebug(LOG_TAG, "Registered dynamic resource: $uri")
    }
    
    /**
     * Register a template-based resource
     */
    fun registerTemplateResource(
        uriTemplate: String,
        name: String,
        description: String,
        mimeType: String,
        handler: (Map<String, String>) -> String
    ) {
        val entry = ResourceEntry.TemplateResource(
            resource = Resource(
                uri = uriTemplate,
                name = name,
                description = description,
                mimeType = mimeType
            ),
            uriTemplate = uriTemplate,
            contentHandler = handler
        )
        resources[uriTemplate] = entry
        Logger.logDebug(LOG_TAG, "Registered template resource: $uriTemplate")
    }
    
    /**
     * List all registered resources
     */
    fun listResources(): List<Resource> {
        return resources.values.map { it.resource }
    }
    
    /**
     * Read resource content
     */
    fun readResource(uri: String): ResourceContentItem? {
        val entry = findResource(uri) ?: return null
        
        return try {
            when (entry) {
                is ResourceEntry.FileResource -> {
                    val file = File(entry.filePath)
                    if (!file.exists()) {
                        Logger.logWarn(LOG_TAG, "Resource file not found: ${entry.filePath}")
                        return null
                    }
                    ResourceContentItem(
                        uri = uri,
                        mimeType = entry.resource.mimeType,
                        text = file.readText()
                    )
                }
                is ResourceEntry.DynamicResource -> {
                    ResourceContentItem(
                        uri = uri,
                        mimeType = entry.resource.mimeType,
                        text = entry.contentProvider()
                    )
                }
                is ResourceEntry.TemplateResource -> {
                    val params = extractTemplateParams(entry.uriTemplate, uri)
                    ResourceContentItem(
                        uri = uri,
                        mimeType = entry.resource.mimeType,
                        text = entry.contentHandler(params)
                    )
                }
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to read resource $uri: ${e.message}")
            null
        }
    }
    
    /**
     * Subscribe to resource updates
     */
    fun subscribe(uri: String, clientId: String): Boolean {
        if (!resources.containsKey(uri)) return false
        
        subscriptions.getOrPut(uri) { mutableSetOf() }.add(clientId)
        Logger.logDebug(LOG_TAG, "Client $clientId subscribed to $uri")
        return true
    }
    
    /**
     * Unsubscribe from resource updates
     */
    fun unsubscribe(uri: String, clientId: String): Boolean {
        val subs = subscriptions[uri] ?: return false
        val removed = subs.remove(clientId)
        if (subs.isEmpty()) {
            subscriptions.remove(uri)
        }
        return removed
    }
    
    /**
     * Get subscribers for a resource
     */
    fun getSubscribers(uri: String): Set<String> {
        return subscriptions[uri]?.toSet() ?: emptySet()
    }
    
    /**
     * Notify subscribers of resource update
     */
    fun notifyUpdate(uri: String, callback: (String) -> Unit) {
        subscriptions[uri]?.forEach { clientId ->
            try {
                callback(clientId)
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to notify client $clientId: ${e.message}")
            }
        }
    }
    
    /**
     * Register default Termux resources
     */
    fun registerDefaults() {
        // Home directory listing
        registerDynamicResource(
            uri = "termux://home",
            name = "Home Directory",
            description = "Contents of Termux home directory",
            mimeType = "application/json"
        ) {
            val homeDir = File("/data/data/com.termux/files/home")
            val files = homeDir.listFiles()?.map { it.name } ?: emptyList()
            """{"files": ${files.map { "\"$it\"" }}}"""
        }
        
        // Environment info
        registerDynamicResource(
            uri = "termux://env",
            name = "Environment",
            description = "Termux environment variables",
            mimeType = "application/json"
        ) {
            val env = System.getenv()
                .filter { it.key.startsWith("TERMUX") || it.key in listOf("HOME", "PATH", "SHELL") }
            """{"environment": ${env.map { "\"${it.key}\": \"${it.value}\"" }.joinToString(",", "{", "}")}}"""
        }
        
        // Agents config
        registerFileResource(
            uri = "termux://agents/config",
            name = "Agent Configuration",
            description = "Main agent framework configuration",
            filePath = "/data/data/com.termux/files/usr/share/agents/etc/config.yml",
            mimeType = "application/yaml"
        )
        
        // File access template
        registerTemplateResource(
            uriTemplate = "termux://file/{path}",
            name = "File Access",
            description = "Read file contents by path",
            mimeType = "text/plain"
        ) { params ->
            val path = params["path"] ?: throw IllegalArgumentException("Path required")
            val file = File("/data/data/com.termux/files/home/$path")
            if (!file.exists()) throw IllegalArgumentException("File not found: $path")
            file.readText()
        }
        
        Logger.logInfo(LOG_TAG, "Registered ${resources.size} default resources")
    }
    
    /**
     * Register BusyBox resources.
     * 
     * These resources expose BusyBox state and configuration to MCP clients.
     */
    fun registerBusyBoxResources() {
        // BusyBox config resource
        registerDynamicResource(
            uri = "termux://busybox/config",
            name = "BusyBox Configuration",
            description = "Current BusyBox configuration including paths and settings",
            mimeType = "application/json"
        ) {
            val binaryPath = "/system/bin/busybox-modern"
            val symlinkDir = "/data/local/busybox-modern"
            val altPaths = listOf(
                "/system/xbin/busybox",
                "/sbin/busybox",
                "/data/adb/magisk/busybox"
            )
            """
            {
                "binaryPath": "$binaryPath",
                "symlinkDir": "$symlinkDir",
                "requireRoot": true,
                "alternativePaths": ${altPaths.map { "\"$it\"" }},
                "diagnosticsCacheTtl": 30000
            }
            """.trimIndent()
        }
        
        // BusyBox version resource
        registerDynamicResource(
            uri = "termux://busybox/version",
            name = "BusyBox Version",
            description = "BusyBox version information",
            mimeType = "application/json"
        ) {
            // This would ideally call the actual busybox --version
            // For now, return structure showing what's available
            try {
                val process = Runtime.getRuntime().exec(arrayOf("/system/bin/busybox-modern", "--version"))
                val version = process.inputStream.bufferedReader().readLine() ?: "unknown"
                process.waitFor()
                """{"version": "$version", "available": true}"""
            } catch (e: Exception) {
                """{"version": "unknown", "available": false, "error": "${e.message}"}"""
            }
        }
        
        // BusyBox diagnostics resource
        registerDynamicResource(
            uri = "termux://busybox/diagnostics",
            name = "BusyBox Diagnostics",
            description = "Current BusyBox health and diagnostic information",
            mimeType = "application/json"
        ) {
            // Check binary existence
            val binaryPaths = listOf(
                "/system/bin/busybox-modern",
                "/system/xbin/busybox",
                "/sbin/busybox",
                "/data/adb/magisk/busybox"
            )
            val foundPath = binaryPaths.find { java.io.File(it).exists() }
            val installed = foundPath != null
            
            // Check symlink directory
            val symlinkDir = java.io.File("/data/local/busybox-modern")
            val symlinksExist = symlinkDir.exists() && symlinkDir.isDirectory
            val symlinkCount = if (symlinksExist) symlinkDir.listFiles()?.size ?: 0 else 0
            
            // Check PATH
            val pathContainsBusybox = System.getenv("PATH")?.contains("busybox") ?: false
            
            """
            {
                "installed": $installed,
                "binaryPath": ${if (foundPath != null) "\"$foundPath\"" else "null"},
                "symlinksExist": $symlinksExist,
                "symlinkCount": $symlinkCount,
                "pathConfigured": $pathContainsBusybox,
                "timestamp": ${System.currentTimeMillis()}
            }
            """.trimIndent()
        }
        
        // BusyBox applets resource
        registerDynamicResource(
            uri = "termux://busybox/applets",
            name = "BusyBox Applets",
            description = "List of available BusyBox applets",
            mimeType = "application/json"
        ) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("/system/bin/busybox-modern", "--list"))
                val applets = process.inputStream.bufferedReader().readLines()
                process.waitFor()
                """{"applets": ${applets.map { "\"$it\"" }}, "count": ${applets.size}}"""
            } catch (e: Exception) {
                """{"applets": [], "count": 0, "error": "${e.message}"}"""
            }
        }
        
        Logger.logInfo(LOG_TAG, "Registered BusyBox resources")
    }
    
    private fun findResource(uri: String): ResourceEntry? {
        // Direct match
        resources[uri]?.let { return it }
        
        // Template match
        for ((template, entry) in resources) {
            if (entry is ResourceEntry.TemplateResource) {
                if (matchesTemplate(template, uri)) {
                    return entry
                }
            }
        }
        
        return null
    }
    
    private fun matchesTemplate(template: String, uri: String): Boolean {
        val regex = template.replace(Regex("\\{[^}]+\\}"), "([^/]+)").toRegex()
        return regex.matches(uri)
    }
    
    private fun extractTemplateParams(template: String, uri: String): Map<String, String> {
        val paramNames = Regex("\\{([^}]+)\\}").findAll(template).map { it.groupValues[1] }.toList()
        val regex = template.replace(Regex("\\{[^}]+\\}"), "([^/]+)").toRegex()
        val match = regex.matchEntire(uri) ?: return emptyMap()
        
        return paramNames.zip(match.groupValues.drop(1)).toMap()
    }
    
    private fun guessMimeType(filePath: String): String {
        return when (filePath.substringAfterLast('.').lowercase()) {
            "json" -> "application/json"
            "yml", "yaml" -> "application/yaml"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "py" -> "text/x-python"
            "sh" -> "text/x-shellscript"
            "kt" -> "text/x-kotlin"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * Resource entry types
     */
    sealed class ResourceEntry {
        abstract val resource: Resource
        
        data class FileResource(
            override val resource: Resource,
            val filePath: String
        ) : ResourceEntry()
        
        data class DynamicResource(
            override val resource: Resource,
            val contentProvider: () -> String
        ) : ResourceEntry()
        
        data class TemplateResource(
            override val resource: Resource,
            val uriTemplate: String,
            val contentHandler: (Map<String, String>) -> String
        ) : ResourceEntry()
    }
}

/**
 * Registry for MCP Prompts.
 * 
 * Manages prompt templates that can be requested by MCP clients.
 */
@Singleton
class PromptRegistry @Inject constructor() {
    
    companion object {
        private const val LOG_TAG = "PromptRegistry"
    }
    
    private val prompts = ConcurrentHashMap<String, PromptEntry>()
    
    /**
     * Register a prompt template
     */
    fun registerPrompt(
        name: String,
        description: String,
        arguments: List<PromptArgument> = emptyList(),
        generator: (Map<String, String>) -> List<PromptMessage>
    ) {
        val entry = PromptEntry(
            prompt = Prompt(name, description, arguments),
            generator = generator
        )
        prompts[name] = entry
        Logger.logDebug(LOG_TAG, "Registered prompt: $name")
    }
    
    /**
     * List all prompts
     */
    fun listPrompts(): List<Prompt> {
        return prompts.values.map { it.prompt }
    }
    
    /**
     * Get prompt messages
     */
    fun getPrompt(name: String, arguments: Map<String, String>): List<PromptMessage>? {
        val entry = prompts[name] ?: return null
        
        // Validate required arguments
        for (arg in entry.prompt.arguments) {
            if (arg.required && !arguments.containsKey(arg.name)) {
                Logger.logWarn(LOG_TAG, "Missing required argument: ${arg.name}")
                return null
            }
        }
        
        return try {
            entry.generator(arguments)
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Prompt generation failed: ${e.message}")
            null
        }
    }
    
    /**
     * Register default prompts
     */
    fun registerDefaults() {
        // Shell command prompt
        registerPrompt(
            name = "shell_command",
            description = "Generate a shell command for a task",
            arguments = listOf(
                PromptArgument("task", "Description of what you want to accomplish", required = true)
            )
        ) { args ->
            val task = args["task"]!!
            listOf(
                PromptMessage(
                    role = "user",
                    content = PromptContent.Text(
                        "Generate a shell command for Termux (Android terminal) to: $task\n\n" +
                        "Requirements:\n" +
                        "- Use packages available in Termux\n" +
                        "- Be safe and non-destructive\n" +
                        "- Include brief explanation"
                    )
                )
            )
        }
        
        // Code review prompt
        registerPrompt(
            name = "code_review",
            description = "Review code for issues and improvements",
            arguments = listOf(
                PromptArgument("code", "The code to review", required = true),
                PromptArgument("language", "Programming language", required = false)
            )
        ) { args ->
            val code = args["code"]!!
            val language = args["language"] ?: "unknown"
            listOf(
                PromptMessage(
                    role = "user",
                    content = PromptContent.Text(
                        "Review the following $language code:\n\n```$language\n$code\n```\n\n" +
                        "Please check for:\n" +
                        "1. Bugs and errors\n" +
                        "2. Security issues\n" +
                        "3. Performance problems\n" +
                        "4. Code style improvements"
                    )
                )
            )
        }
        
        // Explain error prompt
        registerPrompt(
            name = "explain_error",
            description = "Explain an error message and suggest fixes",
            arguments = listOf(
                PromptArgument("error", "The error message", required = true),
                PromptArgument("context", "Additional context about what you were doing", required = false)
            )
        ) { args ->
            val error = args["error"]!!
            val context = args["context"]?.let { "\n\nContext: $it" } ?: ""
            listOf(
                PromptMessage(
                    role = "user",
                    content = PromptContent.Text(
                        "Explain this error and suggest how to fix it:\n\n$error$context"
                    )
                )
            )
        }
        
        // Agent task prompt
        registerPrompt(
            name = "agent_task",
            description = "Create an agent task from natural language",
            arguments = listOf(
                PromptArgument("description", "What you want the agent to do", required = true)
            )
        ) { args ->
            val description = args["description"]!!
            listOf(
                PromptMessage(
                    role = "user",
                    content = PromptContent.Text(
                        "Convert this request into a Termux agent task:\n\n\"$description\"\n\n" +
                        "Identify:\n" +
                        "1. The skill(s) needed\n" +
                        "2. The function(s) to call\n" +
                        "3. Required parameters\n" +
                        "4. Expected result"
                    )
                )
            )
        }
        
        // System repair plan prompt
        registerPrompt(
            name = "system_repair_plan",
            description = "Create a system repair plan from user description",
            arguments = listOf(
                PromptArgument("issue", "Description of the system issue or repair request", required = true),
                PromptArgument("diagnostics", "Current system diagnostics (optional)", required = false)
            )
        ) { args ->
            val issue = args["issue"]!!
            val diagnostics = args["diagnostics"]
            
            val diagnosticsSection = if (diagnostics != null) {
                "\n\nCurrent diagnostics:\n$diagnostics"
            } else ""
            
            listOf(
                PromptMessage(
                    role = "user",
                    content = PromptContent.Text(
                        """Analyze this system repair request and create an execution plan.

User request: "$issue"$diagnosticsSection

Available BusyBox skills:
- busybox.diagnose - Run comprehensive diagnostics
- busybox.repair_symlinks - Fix broken BusyBox symlinks
- busybox.repair_path - Fix PATH configuration
- busybox.magisk_check - Check Magisk status and modules
- busybox.install - Install/setup BusyBox symlinks
- busybox.uninstall - Remove BusyBox symlinks

Available diagnostic skills:
- diagnostic.verify_setup - Check overall environment
- diagnostic.environment - Show environment variables

Respond with a JSON execution plan:
{
  "description": "Brief plan description",
  "repair_type": "FULL_SYSTEM_CHECK|BUSYBOX_REPAIR|SYMLINK_REPAIR|PATH_REPAIR|MAGISK_CHECK",
  "steps": [
    {
      "step": 1,
      "action": "busybox.diagnose",
      "reason": "Why this step is needed"
    }
  ],
  "requires_root": true,
  "estimated_duration_seconds": 30,
  "warnings": ["Any warnings or prerequisites"]
}"""
                    )
                )
            )
        }
        
        // BusyBox diagnostics summary prompt
        registerPrompt(
            name = "busybox_diagnostics_summary",
            description = "Summarize BusyBox diagnostics results for the user",
            arguments = listOf(
                PromptArgument("diagnostics", "Raw diagnostics JSON data", required = true)
            )
        ) { args ->
            val diagnostics = args["diagnostics"]!!
            listOf(
                PromptMessage(
                    role = "user",
                    content = PromptContent.Text(
                        """Summarize these BusyBox diagnostics in plain language for the user.

Diagnostics data:
$diagnostics

Provide:
1. Overall status (healthy/needs attention/critical)
2. Key findings in bullet points
3. Recommended actions if issues found
4. Keep it concise and actionable"""
                    )
                )
            )
        }
        
        // System repair result summary prompt
        registerPrompt(
            name = "system_repair_summary",
            description = "Summarize system repair results for the user",
            arguments = listOf(
                PromptArgument("results", "Raw repair results data", required = true),
                PromptArgument("original_issue", "The original issue description", required = false)
            )
        ) { args ->
            val results = args["results"]!!
            val originalIssue = args["original_issue"] ?: "system repair"
            listOf(
                PromptMessage(
                    role = "user",
                    content = PromptContent.Text(
                        """Summarize these system repair results for the user.

Original request: "$originalIssue"

Repair results:
$results

Provide:
1. Overall outcome (success/partial/failed)
2. What was fixed
3. What still needs attention (if anything)
4. Next steps or recommendations
5. Keep it friendly and helpful"""
                    )
                )
            )
        }
        
        Logger.logInfo(LOG_TAG, "Registered ${prompts.size} default prompts")
    }
    
    private data class PromptEntry(
        val prompt: Prompt,
        val generator: (Map<String, String>) -> List<PromptMessage>
    )
}

/**
 * Completion provider for MCP auto-complete
 */
@Singleton
class CompletionProvider @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val resourceRegistry: ResourceRegistry,
    private val promptRegistry: PromptRegistry
) {
    companion object {
        private const val LOG_TAG = "CompletionProvider"
    }
    
    /**
     * Get completions for a reference
     */
    fun getCompletions(ref: CompletionRef, argument: CompletionArgument): List<String> {
        return when (ref.type) {
            "ref/prompt" -> completePromptArgument(ref.name!!, argument)
            "ref/resource" -> completeResourceUri(argument.value)
            else -> emptyList()
        }
    }
    
    private fun completePromptArgument(promptName: String, argument: CompletionArgument): List<String> {
        // Could provide smart completions based on prompt and argument
        // For now, return empty
        return emptyList()
    }
    
    private fun completeResourceUri(prefix: String): List<String> {
        return resourceRegistry.listResources()
            .map { it.uri }
            .filter { it.startsWith(prefix) }
            .take(10)
    }
}
