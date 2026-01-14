package com.termux.app.core.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.termux.app.core.api.IpcMessage
import com.termux.app.core.api.PluginError
import com.termux.app.core.api.Result
import com.termux.app.core.logging.TermuxLogger
import com.termux.app.core.terminal.TerminalEventBus
import com.termux.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of PluginHost.
 * Provides the runtime environment for plugins to interact with Termux.
 */
class PluginHostImpl(
    private val registered: RegisteredPlugin,
    private val context: Context,
    private val logger: TermuxLogger,
    private val eventBus: TerminalEventBus,
    private val ioDispatcher: CoroutineDispatcher,
    private val commandRegistry: PluginCommandRegistry,
    private val pluginMessenger: PluginMessenger
) : PluginHost {
    
    private val log = logger.forTag("PluginHost[${registered.info.id}]")
    private val pluginId = registered.info.id
    private val capabilities = registered.info.capabilities
    
    // Termux home directory for file access
    private val termuxHome: File by lazy {
        File(context.filesDir.parentFile, "home")
    }
    
    override val apiVersion: String = PluginApiVersion.VERSION_STRING
    
    override val termuxVersion: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
    
    override suspend fun executeCommand(
        command: String,
        arguments: List<String>,
        workingDirectory: String?,
        environment: Map<String, String>,
        background: Boolean
    ): Result<CommandResult, PluginError> {
        if (!hasCapability(PluginCapability.EXECUTE_COMMAND)) {
            return Result.error(
                PluginError.SecurityViolation(pluginId, "Missing EXECUTE_COMMAND capability")
            )
        }
        
        log.d("Executing command: $command ${arguments.joinToString(" ")}")
        
        return withContext(ioDispatcher) {
            try {
                val startTime = System.currentTimeMillis()
                
                val processBuilder = ProcessBuilder(listOf(command) + arguments).apply {
                    workingDirectory?.let { directory(File(it)) }
                    environment().putAll(environment)
                    redirectErrorStream(false)
                }
                
                val process = processBuilder.start()
                
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                
                val executionTime = System.currentTimeMillis() - startTime
                
                Result.success(CommandResult(exitCode, stdout, stderr, executionTime))
            } catch (e: Exception) {
                log.e("Command execution failed: ${e.message}", e)
                Result.error(PluginError.ExecutionError(pluginId, "Command failed: ${e.message}", e))
            }
        }
    }
    
    override suspend fun createSession(
        shellPath: String?,
        initialCommand: String?,
        workingDirectory: String?,
        environment: Map<String, String>
    ): Result<String, PluginError> {
        if (!hasCapability(PluginCapability.CREATE_SESSION)) {
            return Result.error(
                PluginError.SecurityViolation(pluginId, "Missing CREATE_SESSION capability")
            )
        }
        
        // TODO: Integrate with actual TermuxService session creation
        val sessionId = UUID.randomUUID().toString()
        log.i("Created session: $sessionId")
        return Result.success(sessionId)
    }
    
    override fun getSessionOutput(sessionId: String): Flow<String> {
        if (!hasCapability(PluginCapability.READ_OUTPUT)) {
            log.w("Missing READ_OUTPUT capability")
            return emptyFlow()
        }
        
        // TODO: Integrate with actual session output stream
        return emptyFlow()
    }
    
    override suspend fun sendInput(sessionId: String, input: String): Result<Unit, PluginError> {
        if (!hasCapability(PluginCapability.SEND_INPUT)) {
            return Result.error(
                PluginError.SecurityViolation(pluginId, "Missing SEND_INPUT capability")
            )
        }
        
        // TODO: Integrate with actual session input
        log.d("Sending input to session $sessionId: ${input.take(50)}...")
        return Result.success(Unit)
    }
    
    override suspend fun closeSession(sessionId: String): Result<Unit, PluginError> {
        // TODO: Integrate with actual session management
        log.i("Closing session: $sessionId")
        return Result.success(Unit)
    }
    
    override suspend fun readFile(path: String): Result<ByteArray, PluginError> {
        if (!hasCapability(PluginCapability.FILE_ACCESS)) {
            return Result.error(
                PluginError.SecurityViolation(pluginId, "Missing FILE_ACCESS capability")
            )
        }
        
        return withContext(ioDispatcher) {
            try {
                val file = resolveSecurePath(path) ?: return@withContext Result.error(
                    PluginError.SecurityViolation(pluginId, "Path outside Termux home: $path")
                )
                
                if (!file.exists()) {
                    return@withContext Result.error(
                        PluginError.ExecutionError(pluginId, "File not found: $path")
                    )
                }
                
                Result.success(file.readBytes())
            } catch (e: Exception) {
                log.e("File read failed: $path", e)
                Result.error(PluginError.ExecutionError(pluginId, "Read failed: ${e.message}", e))
            }
        }
    }
    
    override suspend fun writeFile(path: String, content: ByteArray): Result<Unit, PluginError> {
        if (!hasCapability(PluginCapability.FILE_ACCESS)) {
            return Result.error(
                PluginError.SecurityViolation(pluginId, "Missing FILE_ACCESS capability")
            )
        }
        
        return withContext(ioDispatcher) {
            try {
                val file = resolveSecurePath(path) ?: return@withContext Result.error(
                    PluginError.SecurityViolation(pluginId, "Path outside Termux home: $path")
                )
                
                file.parentFile?.mkdirs()
                file.writeBytes(content)
                Result.success(Unit)
            } catch (e: Exception) {
                log.e("File write failed: $path", e)
                Result.error(PluginError.ExecutionError(pluginId, "Write failed: ${e.message}", e))
            }
        }
    }
    
    override suspend fun getClipboard(): Result<String?, PluginError> {
        if (!hasCapability(PluginCapability.CLIPBOARD_ACCESS)) {
            return Result.error(
                PluginError.SecurityViolation(pluginId, "Missing CLIPBOARD_ACCESS capability")
            )
        }
        
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            Result.success(text)
        } catch (e: Exception) {
            Result.error(PluginError.ExecutionError(pluginId, "Clipboard read failed: ${e.message}", e))
        }
    }
    
    override suspend fun setClipboard(text: String): Result<Unit, PluginError> {
        if (!hasCapability(PluginCapability.CLIPBOARD_ACCESS)) {
            return Result.error(
                PluginError.SecurityViolation(pluginId, "Missing CLIPBOARD_ACCESS capability")
            )
        }
        
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Termux Plugin", text))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(PluginError.ExecutionError(pluginId, "Clipboard write failed: ${e.message}", e))
        }
    }
    
    override suspend fun showNotification(
        title: String,
        content: String,
        id: Int
    ): Result<Unit, PluginError> {
        if (!hasCapability(PluginCapability.NOTIFICATIONS)) {
            return Result.error(
                PluginError.SecurityViolation(pluginId, "Missing NOTIFICATIONS capability")
            )
        }
        
        return try {
            val notification = NotificationCompat.Builder(context, "termux_plugin_$pluginId")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            
            NotificationManagerCompat.from(context).notify(id, notification)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(PluginError.ExecutionError(pluginId, "Notification failed: ${e.message}", e))
        }
    }
    
    override suspend fun getEnvironmentVariable(name: String): Result<String?, PluginError> {
        if (!hasCapability(PluginCapability.ENVIRONMENT_ACCESS)) {
            return Result.error(
                PluginError.SecurityViolation(pluginId, "Missing ENVIRONMENT_ACCESS capability")
            )
        }
        
        return Result.success(System.getenv(name))
    }
    
    override suspend fun setEnvironmentVariable(name: String, value: String): Result<Unit, PluginError> {
        if (!hasCapability(PluginCapability.ENVIRONMENT_ACCESS)) {
            return Result.error(
                PluginError.SecurityViolation(pluginId, "Missing ENVIRONMENT_ACCESS capability")
            )
        }
        
        // Note: Cannot actually set system env vars in Android, but we can store them
        // for use in spawned processes
        log.w("setEnvironmentVariable is not fully supported on Android")
        return Result.success(Unit)
    }
    
    override suspend fun registerCommand(
        name: String,
        description: String,
        handler: suspend (List<String>) -> Result<String, PluginError>
    ): Result<Unit, PluginError> {
        if (!hasCapability(PluginCapability.CUSTOM_COMMANDS)) {
            return Result.error(
                PluginError.SecurityViolation(pluginId, "Missing CUSTOM_COMMANDS capability")
            )
        }
        
        commandRegistry.register(pluginId, name, description, handler)
        log.i("Registered command: $name")
        return Result.success(Unit)
    }
    
    override suspend fun unregisterCommand(name: String): Result<Unit, PluginError> {
        commandRegistry.unregister(pluginId, name)
        log.i("Unregistered command: $name")
        return Result.success(Unit)
    }
    
    override suspend fun sendMessage(
        targetPluginId: String,
        message: IpcMessage
    ): Result<IpcMessage?, PluginError> {
        return pluginMessenger.send(pluginId, targetPluginId, message)
    }
    
    override fun log(level: PluginHost.LogLevel, message: String, throwable: Throwable?) {
        when (level) {
            PluginHost.LogLevel.DEBUG -> log.d(message)
            PluginHost.LogLevel.INFO -> log.i(message)
            PluginHost.LogLevel.WARNING -> log.w(message, throwable)
            PluginHost.LogLevel.ERROR -> log.e(message, throwable)
        }
    }
    
    private fun hasCapability(capability: PluginCapability): Boolean {
        return capabilities.contains(capability)
    }
    
    /**
     * Resolve a path ensuring it's within the Termux home directory.
     * Returns null if the path escapes the sandbox.
     */
    private fun resolveSecurePath(path: String): File? {
        val resolved = if (path.startsWith("/")) {
            File(path)
        } else {
            File(termuxHome, path)
        }
        
        val canonical = resolved.canonicalFile
        val homeCanonical = termuxHome.canonicalFile
        
        return if (canonical.path.startsWith(homeCanonical.path)) {
            canonical
        } else {
            null
        }
    }
}

/**
 * Registry for plugin-provided commands.
 */
@Singleton
class PluginCommandRegistry @Inject constructor() {
    
    data class RegisteredCommand(
        val pluginId: String,
        val name: String,
        val description: String,
        val handler: suspend (List<String>) -> Result<String, PluginError>
    )
    
    private val commands = ConcurrentHashMap<String, RegisteredCommand>()
    
    fun register(
        pluginId: String,
        name: String,
        description: String,
        handler: suspend (List<String>) -> Result<String, PluginError>
    ) {
        commands[name] = RegisteredCommand(pluginId, name, description, handler)
    }
    
    fun unregister(pluginId: String, name: String) {
        commands.remove(name)
    }
    
    fun unregisterAll(pluginId: String) {
        commands.entries.removeIf { it.value.pluginId == pluginId }
    }
    
    fun getCommand(name: String): RegisteredCommand? = commands[name]
    
    fun getAllCommands(): List<RegisteredCommand> = commands.values.toList()
    
    fun getCommandsForPlugin(pluginId: String): List<RegisteredCommand> =
        commands.values.filter { it.pluginId == pluginId }
    
    suspend fun execute(name: String, args: List<String>): Result<String, PluginError> {
        val command = commands[name] ?: return Result.error(
            PluginError.NotFound("command:$name", "Command not found: $name")
        )
        return command.handler(args)
    }
}

/**
 * Handles inter-plugin messaging.
 */
@Singleton
class PluginMessenger @Inject constructor(
    private val logger: TermuxLogger
) {
    private val log = logger.forTag("PluginMessenger")
    
    // Reference to registry set after construction to avoid circular dependency
    private var registry: PluginRegistryImpl? = null
    
    fun setRegistry(registry: PluginRegistryImpl) {
        this.registry = registry
    }
    
    suspend fun send(
        fromPluginId: String,
        toPluginId: String,
        message: IpcMessage
    ): Result<IpcMessage?, PluginError> {
        val targetPlugin = registry?.getPlugin(toPluginId) ?: return Result.error(
            PluginError.NotFound(toPluginId)
        )
        
        log.d("Message from $fromPluginId to $toPluginId: ${message::class.simpleName}")
        
        return try {
            targetPlugin.onMessage(message)
        } catch (e: Exception) {
            log.e("Message delivery failed", e)
            Result.error(PluginError.ExecutionError(toPluginId, "Message handling failed: ${e.message}", e))
        }
    }
}

/**
 * Factory implementation for creating PluginHost instances.
 */
@Singleton
class PluginHostFactoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: TermuxLogger,
    private val eventBus: TerminalEventBus,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val commandRegistry: PluginCommandRegistry,
    private val pluginMessenger: PluginMessenger
) : PluginHostFactory {
    
    override fun create(registered: RegisteredPlugin): PluginHost {
        return PluginHostImpl(
            registered = registered,
            context = context,
            logger = logger,
            eventBus = eventBus,
            ioDispatcher = ioDispatcher,
            commandRegistry = commandRegistry,
            pluginMessenger = pluginMessenger
        )
    }
}
