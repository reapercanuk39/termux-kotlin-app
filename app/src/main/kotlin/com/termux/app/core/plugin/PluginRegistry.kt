package com.termux.app.core.plugin

import com.termux.app.core.api.PluginError
import com.termux.app.core.api.Result
import com.termux.app.core.logging.TermuxLogger
import com.termux.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plugin lifecycle states following the standardized lifecycle model.
 *
 * State transitions:
 * ```
 * UNREGISTERED → REGISTERED → INITIALIZING → READY → STARTING → ACTIVE
 *                                                         ↓
 *                                               STOPPING → STOPPED
 *                                                         ↓
 *                                               DESTROYING → DESTROYED
 *
 * Any state → ERROR (on failure)
 * ```
 */
enum class PluginLifecycleState {
    /** Plugin is not registered with the system */
    UNREGISTERED,
    
    /** Plugin is registered but not yet initialized */
    REGISTERED,
    
    /** Plugin is being initialized */
    INITIALIZING,
    
    /** Plugin is initialized and ready to start */
    READY,
    
    /** Plugin is starting up */
    STARTING,
    
    /** Plugin is active and running */
    ACTIVE,
    
    /** Plugin is stopping */
    STOPPING,
    
    /** Plugin is stopped but can be restarted */
    STOPPED,
    
    /** Plugin is being destroyed */
    DESTROYING,
    
    /** Plugin is destroyed and must be re-registered to use */
    DESTROYED,
    
    /** Plugin encountered an error */
    ERROR
}

/**
 * Represents a registered plugin with its runtime state.
 */
data class RegisteredPlugin(
    val plugin: TermuxPlugin,
    val info: PluginInfo,
    val registrationTime: Long = System.currentTimeMillis(),
    val scope: CoroutineScope,
    private val _state: MutableStateFlow<PluginLifecycleState> = MutableStateFlow(PluginLifecycleState.REGISTERED)
) {
    val state: StateFlow<PluginLifecycleState> = _state.asStateFlow()
    val currentState: PluginLifecycleState get() = _state.value
    
    internal fun setState(newState: PluginLifecycleState) {
        _state.value = newState
    }
}

/**
 * Event emitted when a plugin's state changes.
 */
data class PluginStateChangeEvent(
    val pluginId: String,
    val previousState: PluginLifecycleState,
    val newState: PluginLifecycleState,
    val timestamp: Long = System.currentTimeMillis(),
    val error: PluginError? = null
)

/**
 * Default implementation of PluginRegistry.
 * Manages plugin registration, lifecycle, and discovery.
 */
@Singleton
class PluginRegistryImpl @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val logger: TermuxLogger,
    private val pluginHostFactory: PluginHostFactory
) : PluginRegistry {
    
    private val log = logger.forTag("PluginRegistry")
    
    private val plugins = ConcurrentHashMap<String, RegisteredPlugin>()
    private val mutex = Mutex()
    
    private val _stateChanges = MutableSharedFlow<PluginStateChangeEvent>(extraBufferCapacity = 50)
    
    override val pluginStateChanges: Flow<Pair<String, PluginState>> = _stateChanges.asSharedFlow()
        .map { event ->
            event.pluginId to event.newState.toPluginState()
        }
    
    /**
     * Flow of detailed plugin state change events.
     */
    val detailedStateChanges: Flow<PluginStateChangeEvent> = _stateChanges.asSharedFlow()
    
    override fun getPlugins(): List<PluginInfo> = plugins.values.map { it.info }
    
    override fun getPlugin(id: String): TermuxPlugin? = plugins[id]?.plugin
    
    /**
     * Get detailed information about a registered plugin.
     */
    fun getRegisteredPlugin(id: String): RegisteredPlugin? = plugins[id]
    
    /**
     * Get all plugins in a specific state.
     */
    fun getPluginsByState(state: PluginLifecycleState): List<RegisteredPlugin> =
        plugins.values.filter { it.currentState == state }
    
    override suspend fun register(plugin: TermuxPlugin): Result<Unit, PluginError> = mutex.withLock {
        val pluginId = plugin.info.id
        
        // Check if already registered
        if (plugins.containsKey(pluginId)) {
            log.w("Plugin already registered: $pluginId")
            return Result.error(PluginError.LoadFailed(pluginId, "Plugin already registered"))
        }
        
        // Check API compatibility
        if (!plugin.info.isCompatible()) {
            log.e("Plugin API version incompatible: ${plugin.info.apiVersion}")
            return Result.error(
                PluginError.IncompatibleVersion(
                    pluginId,
                    PluginApiVersion.VERSION_STRING,
                    plugin.info.apiVersion
                )
            )
        }
        
        log.i("Registering plugin: $pluginId (${plugin.info.name} v${plugin.info.version})")
        
        // Create plugin-scoped coroutine scope
        val pluginScope = CoroutineScope(SupervisorJob() + applicationScope.coroutineContext)
        
        val registered = RegisteredPlugin(
            plugin = plugin,
            info = plugin.info,
            scope = pluginScope
        )
        
        plugins[pluginId] = registered
        emitStateChange(pluginId, PluginLifecycleState.UNREGISTERED, PluginLifecycleState.REGISTERED)
        
        log.i("Plugin registered successfully: $pluginId")
        return Result.success(Unit)
    }
    
    override suspend fun unregister(id: String): Result<Unit, PluginError> = mutex.withLock {
        val registered = plugins[id] ?: return Result.error(PluginError.NotFound(id))
        
        // Stop if running
        if (registered.currentState == PluginLifecycleState.ACTIVE) {
            stopInternal(registered)
        }
        
        // Destroy
        destroyInternal(registered)
        
        // Remove from registry
        plugins.remove(id)
        
        log.i("Plugin unregistered: $id")
        return Result.success(Unit)
    }
    
    /**
     * Initialize a plugin (REGISTERED → READY).
     */
    suspend fun initialize(id: String): Result<Unit, PluginError> = mutex.withLock {
        val registered = plugins[id] ?: return Result.error(PluginError.NotFound(id))
        
        if (registered.currentState != PluginLifecycleState.REGISTERED &&
            registered.currentState != PluginLifecycleState.STOPPED) {
            return Result.error(
                PluginError.ExecutionError(id, "Cannot initialize from state: ${registered.currentState}")
            )
        }
        
        return initializeInternal(registered)
    }
    
    override suspend fun start(id: String): Result<Unit, PluginError> = mutex.withLock {
        val registered = plugins[id] ?: return Result.error(PluginError.NotFound(id))
        
        // Auto-initialize if needed
        if (registered.currentState == PluginLifecycleState.REGISTERED) {
            val initResult = initializeInternal(registered)
            if (initResult is Result.Error) return initResult
        }
        
        if (registered.currentState != PluginLifecycleState.READY &&
            registered.currentState != PluginLifecycleState.STOPPED) {
            return Result.error(
                PluginError.ExecutionError(id, "Cannot start from state: ${registered.currentState}")
            )
        }
        
        return startInternal(registered)
    }
    
    override suspend fun stop(id: String): Result<Unit, PluginError> = mutex.withLock {
        val registered = plugins[id] ?: return Result.error(PluginError.NotFound(id))
        
        if (registered.currentState != PluginLifecycleState.ACTIVE) {
            return Result.error(
                PluginError.ExecutionError(id, "Cannot stop from state: ${registered.currentState}")
            )
        }
        
        return stopInternal(registered)
    }
    
    /**
     * Destroy a plugin, releasing all resources.
     */
    suspend fun destroy(id: String): Result<Unit, PluginError> = mutex.withLock {
        val registered = plugins[id] ?: return Result.error(PluginError.NotFound(id))
        
        if (registered.currentState == PluginLifecycleState.ACTIVE) {
            stopInternal(registered)
        }
        
        return destroyInternal(registered)
    }
    
    override fun hasCapability(id: String, capability: PluginCapability): Boolean {
        return plugins[id]?.info?.capabilities?.contains(capability) == true
    }
    
    /**
     * Start all registered plugins.
     */
    suspend fun startAll(): Map<String, Result<Unit, PluginError>> {
        return plugins.keys.associateWith { start(it) }
    }
    
    /**
     * Stop all active plugins.
     */
    suspend fun stopAll(): Map<String, Result<Unit, PluginError>> {
        return getPluginsByState(PluginLifecycleState.ACTIVE)
            .map { it.info.id }
            .associateWith { stop(it) }
    }
    
    /**
     * Destroy all plugins and clean up.
     */
    suspend fun destroyAll() {
        stopAll()
        plugins.keys.toList().forEach { destroy(it) }
        plugins.clear()
    }
    
    // Internal lifecycle methods
    
    private suspend fun initializeInternal(registered: RegisteredPlugin): Result<Unit, PluginError> {
        val pluginId = registered.info.id
        val previousState = registered.currentState
        
        log.d("Initializing plugin: $pluginId")
        registered.setState(PluginLifecycleState.INITIALIZING)
        emitStateChange(pluginId, previousState, PluginLifecycleState.INITIALIZING)
        
        return try {
            val host = pluginHostFactory.create(registered)
            val result = registered.plugin.onLoad(host)
            
            when (result) {
                is Result.Success -> {
                    registered.setState(PluginLifecycleState.READY)
                    emitStateChange(pluginId, PluginLifecycleState.INITIALIZING, PluginLifecycleState.READY)
                    log.i("Plugin initialized: $pluginId")
                    Result.success(Unit)
                }
                is Result.Error -> {
                    registered.setState(PluginLifecycleState.ERROR)
                    emitStateChange(pluginId, PluginLifecycleState.INITIALIZING, PluginLifecycleState.ERROR, result.error)
                    log.e("Plugin initialization failed: $pluginId", result.error.cause)
                    result
                }
                is Result.Loading -> {
                    Result.error(PluginError.ExecutionError(pluginId, "Unexpected loading state"))
                }
            }
        } catch (e: Exception) {
            registered.setState(PluginLifecycleState.ERROR)
            val error = PluginError.LoadFailed(pluginId, "Initialization exception: ${e.message}", e)
            emitStateChange(pluginId, PluginLifecycleState.INITIALIZING, PluginLifecycleState.ERROR, error)
            log.e("Plugin initialization exception: $pluginId", e)
            Result.error(error)
        }
    }
    
    private suspend fun startInternal(registered: RegisteredPlugin): Result<Unit, PluginError> {
        val pluginId = registered.info.id
        val previousState = registered.currentState
        
        log.d("Starting plugin: $pluginId")
        registered.setState(PluginLifecycleState.STARTING)
        emitStateChange(pluginId, previousState, PluginLifecycleState.STARTING)
        
        return try {
            val result = registered.plugin.onStart()
            
            when (result) {
                is Result.Success -> {
                    registered.setState(PluginLifecycleState.ACTIVE)
                    emitStateChange(pluginId, PluginLifecycleState.STARTING, PluginLifecycleState.ACTIVE)
                    log.i("Plugin started: $pluginId")
                    Result.success(Unit)
                }
                is Result.Error -> {
                    registered.setState(PluginLifecycleState.ERROR)
                    emitStateChange(pluginId, PluginLifecycleState.STARTING, PluginLifecycleState.ERROR, result.error)
                    log.e("Plugin start failed: $pluginId")
                    result
                }
                is Result.Loading -> {
                    Result.error(PluginError.ExecutionError(pluginId, "Unexpected loading state"))
                }
            }
        } catch (e: Exception) {
            registered.setState(PluginLifecycleState.ERROR)
            val error = PluginError.ExecutionError(pluginId, "Start exception: ${e.message}", e)
            emitStateChange(pluginId, PluginLifecycleState.STARTING, PluginLifecycleState.ERROR, error)
            log.e("Plugin start exception: $pluginId", e)
            Result.error(error)
        }
    }
    
    private suspend fun stopInternal(registered: RegisteredPlugin): Result<Unit, PluginError> {
        val pluginId = registered.info.id
        val previousState = registered.currentState
        
        log.d("Stopping plugin: $pluginId")
        registered.setState(PluginLifecycleState.STOPPING)
        emitStateChange(pluginId, previousState, PluginLifecycleState.STOPPING)
        
        return try {
            val result = registered.plugin.onStop()
            
            registered.setState(PluginLifecycleState.STOPPED)
            emitStateChange(pluginId, PluginLifecycleState.STOPPING, PluginLifecycleState.STOPPED)
            log.i("Plugin stopped: $pluginId")
            
            when (result) {
                is Result.Success -> Result.success(Unit)
                is Result.Error -> result // Log but don't fail
                is Result.Loading -> Result.success(Unit)
            }
        } catch (e: Exception) {
            // Still mark as stopped even if onStop throws
            registered.setState(PluginLifecycleState.STOPPED)
            emitStateChange(pluginId, PluginLifecycleState.STOPPING, PluginLifecycleState.STOPPED)
            log.w("Plugin stop exception (ignored): $pluginId", e)
            Result.success(Unit)
        }
    }
    
    private suspend fun destroyInternal(registered: RegisteredPlugin): Result<Unit, PluginError> {
        val pluginId = registered.info.id
        val previousState = registered.currentState
        
        log.d("Destroying plugin: $pluginId")
        registered.setState(PluginLifecycleState.DESTROYING)
        emitStateChange(pluginId, previousState, PluginLifecycleState.DESTROYING)
        
        return try {
            registered.plugin.onUnload()
            
            // Cancel the plugin's coroutine scope
            registered.scope.cancel()
            
            registered.setState(PluginLifecycleState.DESTROYED)
            emitStateChange(pluginId, PluginLifecycleState.DESTROYING, PluginLifecycleState.DESTROYED)
            log.i("Plugin destroyed: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            registered.scope.cancel()
            registered.setState(PluginLifecycleState.DESTROYED)
            emitStateChange(pluginId, PluginLifecycleState.DESTROYING, PluginLifecycleState.DESTROYED)
            log.w("Plugin destroy exception (ignored): $pluginId", e)
            Result.success(Unit)
        }
    }
    
    private fun emitStateChange(
        pluginId: String,
        previousState: PluginLifecycleState,
        newState: PluginLifecycleState,
        error: PluginError? = null
    ) {
        _stateChanges.tryEmit(PluginStateChangeEvent(pluginId, previousState, newState, error = error))
    }
    
    private fun PluginLifecycleState.toPluginState(): PluginState = when (this) {
        PluginLifecycleState.UNREGISTERED -> PluginState.UNLOADED
        PluginLifecycleState.REGISTERED -> PluginState.UNLOADED
        PluginLifecycleState.INITIALIZING -> PluginState.LOADING
        PluginLifecycleState.READY -> PluginState.LOADED
        PluginLifecycleState.STARTING -> PluginState.LOADING
        PluginLifecycleState.ACTIVE -> PluginState.STARTED
        PluginLifecycleState.STOPPING -> PluginState.STOPPED
        PluginLifecycleState.STOPPED -> PluginState.STOPPED
        PluginLifecycleState.DESTROYING -> PluginState.STOPPED
        PluginLifecycleState.DESTROYED -> PluginState.UNLOADED
        PluginLifecycleState.ERROR -> PluginState.ERROR
    }
}

/**
 * Factory for creating PluginHost instances.
 */
interface PluginHostFactory {
    fun create(registered: RegisteredPlugin): PluginHost
}
