package com.termux.app.agents.swarm

import java.util.UUID

/**
 * Types of swarm signals (pheromones).
 * 
 * Inspired by ant colony optimization and bee swarm behavior.
 */
enum class SignalType(val displayName: String) {
    // Success signals - attract other agents
    SUCCESS("success"),
    RESOURCE_FOUND("resource"),
    PATH_CLEAR("path_clear"),
    
    // Warning signals - repel or caution
    FAILURE("failure"),
    BLOCKED("blocked"),
    DANGER("danger"),
    
    // Coordination signals - for multi-agent tasks
    WORKING("working"),
    CLAIMING("claiming"),
    RELEASING("releasing"),
    HELP_NEEDED("help"),
    
    // Discovery signals - sharing knowledge
    LEARNED("learned"),
    OPTIMIZED("optimized"),
    DEPRECATED("deprecated"),
    
    // Lifecycle signals - daemon status
    HEARTBEAT("heartbeat"),
    STARTUP("startup"),
    SHUTDOWN("shutdown");
    
    companion object {
        fun fromString(value: String): SignalType? {
            return entries.find { it.displayName == value || it.name.equals(value, ignoreCase = true) }
        }
    }
}

/**
 * A pheromone-like signal in the swarm space.
 */
data class Signal(
    val id: String = UUID.randomUUID().toString(),
    val signalType: SignalType,
    val sourceAgent: String,
    val target: String,  // What this signal is about (skill, task, path, etc.)
    var strength: Float = 1.0f,
    val data: MutableMap<String, Any?> = mutableMapOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var ttl: Long = 3600_000L,  // Time to live in milliseconds (default 1 hour)
    var reinforcementCount: Int = 0
) {
    /**
     * Check if signal has expired
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() > (createdAt + ttl)
    }
    
    /**
     * Check if signal strength is below threshold
     */
    fun isWeak(threshold: Float = 0.1f): Boolean {
        return strength < threshold
    }
    
    /**
     * Apply decay to signal strength
     */
    fun decay(rate: Float = 0.1f): Float {
        strength = maxOf(0f, strength - rate)
        updatedAt = System.currentTimeMillis()
        return strength
    }
    
    /**
     * Reinforce signal strength
     */
    fun reinforce(amount: Float = 0.3f): Float {
        strength = minOf(1f, strength + amount)
        reinforcementCount++
        updatedAt = System.currentTimeMillis()
        // Extend TTL on reinforcement (add 10 min, max 24 hours)
        ttl = minOf(ttl + 600_000L, 86_400_000L)
        return strength
    }
    
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "signal_type" to signalType.displayName,
        "source_agent" to sourceAgent,
        "target" to target,
        "strength" to strength,
        "data" to data,
        "created_at" to createdAt,
        "updated_at" to updatedAt,
        "ttl" to ttl,
        "reinforcement_count" to reinforcementCount
    )
    
    companion object {
        fun fromMap(map: Map<String, Any?>): Signal {
            return Signal(
                id = map["id"] as? String ?: UUID.randomUUID().toString(),
                signalType = SignalType.fromString(map["signal_type"] as? String ?: "success") 
                    ?: SignalType.SUCCESS,
                sourceAgent = map["source_agent"] as? String ?: "unknown",
                target = map["target"] as? String ?: "",
                strength = (map["strength"] as? Number)?.toFloat() ?: 1.0f,
                data = (map["data"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                    ?.mapValues { it.value }?.toMutableMap() ?: mutableMapOf(),
                createdAt = (map["created_at"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updated_at"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                ttl = (map["ttl"] as? Number)?.toLong() ?: 3600_000L,
                reinforcementCount = (map["reinforcement_count"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

/**
 * Consensus recommendation from swarm analysis
 */
data class ConsensusResult(
    val proceed: Boolean,
    val confidence: Float,
    val recommendation: String,
    val supportingSignals: List<Signal> = emptyList(),
    val warningSignals: List<Signal> = emptyList()
)
