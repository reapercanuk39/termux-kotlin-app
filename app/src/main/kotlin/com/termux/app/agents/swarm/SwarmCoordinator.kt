package com.termux.app.agents.swarm

import com.termux.shared.logger.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Central coordinator for swarm-based agent communication.
 * 
 * Uses filesystem-based stigmergy:
 * - Signals stored as JSON files in shared directory
 * - Agents read/write signals independently
 * - Decay process runs periodically to fade old signals
 * 
 * This enables emergent coordination without direct agent-to-agent communication.
 */
class SwarmCoordinator(
    private val swarmDir: File,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val LOG_TAG = "SwarmCoordinator"
        private const val DECAY_RATE = 0.05f        // 5% decay per cycle
        private const val DECAY_INTERVAL_MS = 300_000L  // Run decay every 5 minutes
        private const val WEAK_THRESHOLD = 0.1f     // Remove signals below this strength
    }
    
    private val signalsDir = File(swarmDir, "signals")
    private val indexFile = File(swarmDir, "index.json")
    
    // In-memory cache of signals
    private val signals = ConcurrentHashMap<String, Signal>()
    
    // State flow for reactive updates
    private val _signalFlow = MutableStateFlow<List<Signal>>(emptyList())
    val signalFlow: StateFlow<List<Signal>> = _signalFlow.asStateFlow()
    
    private var decayJob: Job? = null
    private var lastDecay: Long = System.currentTimeMillis()
    
    init {
        signalsDir.mkdirs()
        loadSignals()
    }
    
    /**
     * Start the decay process
     */
    fun startDecayLoop() {
        if (decayJob?.isActive == true) return
        
        decayJob = scope.launch {
            while (isActive) {
                delay(DECAY_INTERVAL_MS)
                runDecayCycle()
            }
        }
        Logger.logDebug(LOG_TAG, "Decay loop started")
    }
    
    /**
     * Stop the decay process
     */
    fun stopDecayLoop() {
        decayJob?.cancel()
        decayJob = null
        Logger.logDebug(LOG_TAG, "Decay loop stopped")
    }
    
    /**
     * Emit a new signal
     */
    fun emit(
        signalType: SignalType,
        sourceAgent: String,
        target: String,
        data: Map<String, Any?> = emptyMap(),
        strength: Float = 1.0f,
        ttl: Long = 3600_000L
    ): Signal {
        val signal = Signal(
            signalType = signalType,
            sourceAgent = sourceAgent,
            target = target,
            strength = strength,
            data = data.toMutableMap(),
            ttl = ttl
        )
        
        // Check for existing similar signal to reinforce
        val existingKey = findSimilarSignal(signalType, sourceAgent, target)
        if (existingKey != null) {
            signals[existingKey]?.let { existing ->
                existing.reinforce()
                existing.data.putAll(data)
                saveSignal(existing)
                updateFlow()
                Logger.logDebug(LOG_TAG, "Reinforced signal: $existingKey (strength=${existing.strength})")
                return existing
            }
        }
        
        // Add new signal
        signals[signal.id] = signal
        saveSignal(signal)
        updateFlow()
        
        Logger.logDebug(LOG_TAG, "Emitted signal: ${signal.signalType} from $sourceAgent -> $target")
        return signal
    }
    
    /**
     * Sense signals matching criteria
     */
    fun sense(
        signalTypes: Set<SignalType>? = null,
        target: String? = null,
        sourceAgent: String? = null,
        minStrength: Float = 0f
    ): List<Signal> {
        return signals.values.filter { signal ->
            !signal.isExpired() &&
            !signal.isWeak(minStrength) &&
            (signalTypes == null || signalTypes.contains(signal.signalType)) &&
            (target == null || signal.target == target || signal.target.contains(target)) &&
            (sourceAgent == null || signal.sourceAgent == sourceAgent)
        }.sortedByDescending { it.strength }
    }
    
    /**
     * Get consensus recommendation for a target
     */
    fun getConsensus(target: String): ConsensusResult {
        val relevantSignals = sense(target = target, minStrength = WEAK_THRESHOLD)
        
        if (relevantSignals.isEmpty()) {
            return ConsensusResult(
                proceed = true,
                confidence = 0f,
                recommendation = "No signals - proceed with caution"
            )
        }
        
        val successSignals = relevantSignals.filter { 
            it.signalType in setOf(SignalType.SUCCESS, SignalType.RESOURCE_FOUND, SignalType.PATH_CLEAR)
        }
        val warningSignals = relevantSignals.filter { 
            it.signalType in setOf(SignalType.FAILURE, SignalType.BLOCKED, SignalType.DANGER)
        }
        
        val successStrength = successSignals.sumOf { it.strength.toDouble() }.toFloat()
        val warningStrength = warningSignals.sumOf { it.strength.toDouble() }.toFloat()
        val totalStrength = successStrength + warningStrength
        
        val proceed = successStrength >= warningStrength
        val confidence = if (totalStrength > 0) {
            (maxOf(successStrength, warningStrength) / totalStrength)
        } else 0f
        
        val recommendation = when {
            warningStrength > successStrength * 2 -> "Strong warning signals - avoid"
            warningStrength > successStrength -> "More warnings than successes - caution"
            successStrength > warningStrength * 2 -> "Strong success signals - proceed"
            successStrength > warningStrength -> "More successes - likely safe"
            else -> "Mixed signals - proceed with caution"
        }
        
        return ConsensusResult(
            proceed = proceed,
            confidence = confidence,
            recommendation = recommendation,
            supportingSignals = successSignals,
            warningSignals = warningSignals
        )
    }
    
    /**
     * Run a decay cycle - reduce all signal strengths
     */
    private fun runDecayCycle() {
        var removed = 0
        var decayed = 0
        
        signals.entries.removeIf { (id, signal) ->
            if (signal.isExpired()) {
                deleteSignalFile(id)
                removed++
                true
            } else {
                signal.decay(DECAY_RATE)
                if (signal.isWeak(WEAK_THRESHOLD)) {
                    deleteSignalFile(id)
                    removed++
                    true
                } else {
                    saveSignal(signal)
                    decayed++
                    false
                }
            }
        }
        
        lastDecay = System.currentTimeMillis()
        updateFlow()
        
        if (removed > 0 || decayed > 0) {
            Logger.logDebug(LOG_TAG, "Decay cycle: removed=$removed, decayed=$decayed, remaining=${signals.size}")
        }
    }
    
    /**
     * Find a similar existing signal to reinforce
     */
    private fun findSimilarSignal(type: SignalType, source: String, target: String): String? {
        return signals.entries.find { (_, signal) ->
            signal.signalType == type &&
            signal.sourceAgent == source &&
            signal.target == target &&
            !signal.isExpired()
        }?.key
    }
    
    /**
     * Load signals from disk
     */
    private fun loadSignals() {
        signals.clear()
        
        signalsDir.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
            try {
                val json = JSONObject(file.readText())
                val signal = Signal.fromMap(jsonToMap(json))
                if (!signal.isExpired()) {
                    signals[signal.id] = signal
                } else {
                    file.delete()
                }
            } catch (e: Exception) {
                Logger.logWarn(LOG_TAG, "Failed to load signal ${file.name}: ${e.message}")
            }
        }
        
        updateFlow()
        Logger.logDebug(LOG_TAG, "Loaded ${signals.size} signals from disk")
    }
    
    /**
     * Save a signal to disk
     */
    private fun saveSignal(signal: Signal) {
        try {
            val file = File(signalsDir, "${signal.id}.json")
            file.writeText(JSONObject(signal.toMap()).toString(2))
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to save signal ${signal.id}: ${e.message}")
        }
    }
    
    /**
     * Delete a signal file
     */
    private fun deleteSignalFile(id: String) {
        try {
            File(signalsDir, "$id.json").delete()
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Update the state flow
     */
    private fun updateFlow() {
        _signalFlow.value = signals.values.toList()
    }
    
    /**
     * Get swarm status summary
     */
    fun getStatus(): Map<String, Any> {
        val signalsByType = signals.values.groupBy { it.signalType }
        val signalsByAgent = signals.values.groupBy { it.sourceAgent }
        
        return mapOf(
            "total_signals" to signals.size,
            "last_decay" to lastDecay,
            "decay_interval_ms" to DECAY_INTERVAL_MS,
            "signals_by_type" to signalsByType.mapValues { it.value.size },
            "signals_by_agent" to signalsByAgent.mapValues { it.value.size },
            "strongest_signals" to signals.values
                .sortedByDescending { it.strength }
                .take(5)
                .map { mapOf("type" to it.signalType.name, "target" to it.target, "strength" to it.strength) }
        )
    }
    
    /**
     * Clear all signals
     */
    fun clearAll() {
        signals.clear()
        signalsDir.listFiles()?.forEach { it.delete() }
        updateFlow()
        Logger.logInfo(LOG_TAG, "All signals cleared")
    }
    
    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = when (val value = json.get(key)) {
                is JSONObject -> jsonToMap(value)
                is JSONArray -> (0 until value.length()).map { value.get(it) }
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }
}

/**
 * Signal emitter helper for agents
 */
class SignalEmitter(
    private val coordinator: SwarmCoordinator,
    private val agentName: String
) {
    fun reportSuccess(target: String, data: Map<String, Any?> = emptyMap()) {
        coordinator.emit(SignalType.SUCCESS, agentName, target, data)
    }
    
    fun reportFailure(target: String, error: String, data: Map<String, Any?> = emptyMap()) {
        coordinator.emit(SignalType.FAILURE, agentName, target, data + mapOf("error" to error))
    }
    
    fun reportBlocked(target: String, reason: String) {
        coordinator.emit(SignalType.BLOCKED, agentName, target, mapOf("reason" to reason))
    }
    
    fun reportWorking(target: String) {
        coordinator.emit(SignalType.WORKING, agentName, target, ttl = 300_000L) // 5 min TTL
    }
    
    fun reportLearned(target: String, pattern: String) {
        coordinator.emit(SignalType.LEARNED, agentName, target, mapOf("pattern" to pattern))
    }
    
    fun claim(target: String) {
        coordinator.emit(SignalType.CLAIMING, agentName, target, ttl = 600_000L) // 10 min TTL
    }
    
    fun release(target: String) {
        coordinator.emit(SignalType.RELEASING, agentName, target)
    }
    
    fun requestHelp(target: String, issue: String) {
        coordinator.emit(SignalType.HELP_NEEDED, agentName, target, mapOf("issue" to issue))
    }
}

/**
 * Signal sensor helper for agents
 */
class SignalSensor(
    private val coordinator: SwarmCoordinator,
    private val agentName: String
) {
    fun shouldProceed(target: String): ConsensusResult {
        return coordinator.getConsensus(target)
    }
    
    fun isTargetClaimed(target: String): Boolean {
        val claims = coordinator.sense(
            signalTypes = setOf(SignalType.CLAIMING, SignalType.WORKING),
            target = target,
            minStrength = 0.3f
        )
        return claims.any { it.sourceAgent != agentName }
    }
    
    fun getHelpRequests(): List<Signal> {
        return coordinator.sense(
            signalTypes = setOf(SignalType.HELP_NEEDED),
            minStrength = 0.2f
        )
    }
    
    fun getLearnedPatterns(target: String? = null): List<Signal> {
        return coordinator.sense(
            signalTypes = setOf(SignalType.LEARNED, SignalType.OPTIMIZED),
            target = target
        )
    }
    
    fun getWarnings(target: String? = null): List<Signal> {
        return coordinator.sense(
            signalTypes = setOf(SignalType.FAILURE, SignalType.BLOCKED, SignalType.DANGER),
            target = target
        )
    }
}
