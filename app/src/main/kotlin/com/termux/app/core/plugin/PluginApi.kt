package com.termux.app.core.plugin

import com.termux.app.core.api.IpcMessage
import com.termux.app.core.api.PluginError
import com.termux.app.core.api.Result
import com.termux.app.core.api.TermuxResult
import kotlinx.coroutines.flow.Flow

/**
 * Termux Plugin API Version.
 * Follows semantic versioning (MAJOR.MINOR.PATCH).
 *
 * - MAJOR: Breaking changes to the API
 * - MINOR: New features, backwards compatible
 * - PATCH: Bug fixes, backwards compatible
 */
object PluginApiVersion {
    const val MAJOR = 1
    const val MINOR = 0
    const val PATCH = 0
    
    const val VERSION_STRING = "$MAJOR.$MINOR.$PATCH"
    const val VERSION_CODE = MAJOR * 10000 + MINOR * 100 + PATCH
    
    /**
     * Check if a plugin version is compatible with this API version.
     * A plugin is compatible if:
     * - Its major version matches ours
     * - Its minor version is <= ours (we support all older minor versions)
     */
    fun isCompatible(pluginMajor: Int, pluginMinor: Int): Boolean {
        return pluginMajor == MAJOR && pluginMinor <= MINOR
    }
    
    /**
     * Parse a version string like "1.2.3" into components.
     */
    fun parse(version: String): Triple<Int, Int, Int>? {
        val parts = version.split(".")
        if (parts.size != 3) return null
        return try {
            Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        } catch (e: NumberFormatException) {
            null
        }
    }
}

/**
 * Plugin capability flags.
 * Defines what features/permissions a plugin requires.
 */
enum class PluginCapability {
    /** Can execute shell commands */
    EXECUTE_COMMAND,
    
    /** Can create terminal sessions */
    CREATE_SESSION,
    
    /** Can access session output */
    READ_OUTPUT,
    
    /** Can send input to sessions */
    SEND_INPUT,
    
    /** Can access file system (within Termux home) */
    FILE_ACCESS,
    
    /** Can access network */
    NETWORK_ACCESS,
    
    /** Can access clipboard */
    CLIPBOARD_ACCESS,
    
    /** Can show notifications */
    NOTIFICATIONS,
    
    /** Can run in background */
    BACKGROUND_EXECUTION,
    
    /** Can access environment variables */
    ENVIRONMENT_ACCESS,
    
    /** Can register custom commands */
    CUSTOM_COMMANDS,
    
    /** Can extend the UI */
    UI_EXTENSION
}

/**
 * Plugin metadata.
 */
data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: String,
    val description: String = "",
    val author: String = "",
    val homepage: String = "",
    val capabilities: Set<PluginCapability> = emptySet(),
    val minTermuxVersion: String? = null
) {
    /**
     * Check if this plugin is compatible with the current API.
     */
    fun isCompatible(): Boolean {
        val (major, minor, _) = PluginApiVersion.parse(apiVersion) ?: return false
        return PluginApiVersion.isCompatible(major, minor)
    }
}

/**
 * Plugin lifecycle state.
 */
enum class PluginState {
    UNLOADED,
    LOADING,
    LOADED,
    STARTED,
    STOPPED,
    ERROR
}

/**
 * Interface for Termux plugins.
 * Plugins must implement this interface to integrate with Termux.
 */
interface TermuxPlugin {
    
    /**
     * Plugin information.
     */
    val info: PluginInfo
    
    /**
     * Current plugin state.
     */
    val state: PluginState
    
    /**
     * Called when the plugin is loaded.
     * Initialize resources here.
     */
    suspend fun onLoad(host: PluginHost): Result<Unit, PluginError>
    
    /**
     * Called when the plugin is started.
     * Begin plugin operations here.
     */
    suspend fun onStart(): Result<Unit, PluginError>
    
    /**
     * Called when the plugin is stopped.
     * Pause operations but keep resources.
     */
    suspend fun onStop(): Result<Unit, PluginError>
    
    /**
     * Called when the plugin is unloaded.
     * Clean up all resources here.
     */
    suspend fun onUnload(): Result<Unit, PluginError>
    
    /**
     * Handle an IPC message from Termux or another plugin.
     */
    suspend fun onMessage(message: IpcMessage): Result<IpcMessage?, PluginError>
}

/**
 * Interface provided to plugins for interacting with Termux.
 */
interface PluginHost {
    
    /**
     * API version of the host.
     */
    val apiVersion: String
    
    /**
     * Termux app version.
     */
    val termuxVersion: String
    
    /**
     * Execute a shell command.
     * Requires EXECUTE_COMMAND capability.
     */
    suspend fun executeCommand(
        command: String,
        arguments: List<String> = emptyList(),
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
        background: Boolean = false
    ): Result<CommandResult, PluginError>
    
    /**
     * Create a new terminal session.
     * Requires CREATE_SESSION capability.
     */
    suspend fun createSession(
        shellPath: String? = null,
        initialCommand: String? = null,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap()
    ): Result<String, PluginError> // Returns session ID
    
    /**
     * Get output from a session.
     * Requires READ_OUTPUT capability.
     */
    fun getSessionOutput(sessionId: String): Flow<String>
    
    /**
     * Send input to a session.
     * Requires SEND_INPUT capability.
     */
    suspend fun sendInput(sessionId: String, input: String): Result<Unit, PluginError>
    
    /**
     * Close a session.
     */
    suspend fun closeSession(sessionId: String): Result<Unit, PluginError>
    
    /**
     * Read a file.
     * Requires FILE_ACCESS capability.
     * Path must be within Termux home directory.
     */
    suspend fun readFile(path: String): Result<ByteArray, PluginError>
    
    /**
     * Write a file.
     * Requires FILE_ACCESS capability.
     * Path must be within Termux home directory.
     */
    suspend fun writeFile(path: String, content: ByteArray): Result<Unit, PluginError>
    
    /**
     * Get clipboard content.
     * Requires CLIPBOARD_ACCESS capability.
     */
    suspend fun getClipboard(): Result<String?, PluginError>
    
    /**
     * Set clipboard content.
     * Requires CLIPBOARD_ACCESS capability.
     */
    suspend fun setClipboard(text: String): Result<Unit, PluginError>
    
    /**
     * Show a notification.
     * Requires NOTIFICATIONS capability.
     */
    suspend fun showNotification(
        title: String,
        content: String,
        id: Int = 0
    ): Result<Unit, PluginError>
    
    /**
     * Get an environment variable.
     * Requires ENVIRONMENT_ACCESS capability.
     */
    suspend fun getEnvironmentVariable(name: String): Result<String?, PluginError>
    
    /**
     * Set an environment variable.
     * Requires ENVIRONMENT_ACCESS capability.
     */
    suspend fun setEnvironmentVariable(name: String, value: String): Result<Unit, PluginError>
    
    /**
     * Register a custom command.
     * Requires CUSTOM_COMMANDS capability.
     */
    suspend fun registerCommand(
        name: String,
        description: String,
        handler: suspend (List<String>) -> Result<String, PluginError>
    ): Result<Unit, PluginError>
    
    /**
     * Unregister a custom command.
     */
    suspend fun unregisterCommand(name: String): Result<Unit, PluginError>
    
    /**
     * Send a message to another plugin.
     */
    suspend fun sendMessage(targetPluginId: String, message: IpcMessage): Result<IpcMessage?, PluginError>
    
    /**
     * Log a message.
     */
    fun log(level: LogLevel, message: String, throwable: Throwable? = null)
    
    enum class LogLevel { DEBUG, INFO, WARNING, ERROR }
}

/**
 * Result of a command execution.
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val executionTimeMs: Long
)

/**
 * Plugin registry for managing installed plugins.
 */
interface PluginRegistry {
    
    /**
     * Get all registered plugins.
     */
    fun getPlugins(): List<PluginInfo>
    
    /**
     * Get a plugin by ID.
     */
    fun getPlugin(id: String): TermuxPlugin?
    
    /**
     * Register a plugin.
     */
    suspend fun register(plugin: TermuxPlugin): Result<Unit, PluginError>
    
    /**
     * Unregister a plugin.
     */
    suspend fun unregister(id: String): Result<Unit, PluginError>
    
    /**
     * Start a plugin.
     */
    suspend fun start(id: String): Result<Unit, PluginError>
    
    /**
     * Stop a plugin.
     */
    suspend fun stop(id: String): Result<Unit, PluginError>
    
    /**
     * Check if a plugin has a specific capability.
     */
    fun hasCapability(id: String, capability: PluginCapability): Boolean
    
    /**
     * Flow of plugin state changes.
     */
    val pluginStateChanges: Flow<Pair<String, PluginState>>
}
