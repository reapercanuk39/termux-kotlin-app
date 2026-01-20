package com.termux.app.agents.runtime

import com.termux.shared.logger.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Thread-safe, file-based memory storage for agents.
 * 
 * Each agent has its own memory file in the memory directory.
 * Memory structure:
 * {
 *     "agent_name": "...",
 *     "created_at": timestamp,
 *     "updated_at": timestamp,
 *     "data": { ... arbitrary agent data ... },
 *     "history": [ ... task history ... ]
 * }
 */
class AgentMemory(
    private val agentName: String,
    private val memoryDir: File
) {
    companion object {
        private const val LOG_TAG = "AgentMemory"
        private const val MAX_HISTORY_ENTRIES = 1000
    }
    
    private val memoryFile = File(memoryDir, "$agentName.json")
    private val lock = Any()
    
    init {
        memoryDir.mkdirs()
        if (!memoryFile.exists()) {
            initMemory()
        }
    }
    
    /**
     * Initialize empty memory file
     */
    private fun initMemory() {
        val initial = JSONObject().apply {
            put("agent_name", agentName)
            put("created_at", System.currentTimeMillis())
            put("updated_at", System.currentTimeMillis())
            put("data", JSONObject())
            put("history", JSONArray())
        }
        writeFile(initial)
    }
    
    /**
     * Read memory file
     */
    private fun readFile(): JSONObject {
        synchronized(lock) {
            return try {
                if (memoryFile.exists()) {
                    JSONObject(memoryFile.readText())
                } else {
                    initMemory()
                    readFile()
                }
            } catch (e: Exception) {
                Logger.logWarn(LOG_TAG, "Failed to read memory for $agentName: ${e.message}")
                initMemory()
                readFile()
            }
        }
    }
    
    /**
     * Write memory file
     */
    private fun writeFile(data: JSONObject) {
        synchronized(lock) {
            try {
                memoryFile.writeText(data.toString(2))
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to write memory for $agentName: ${e.message}")
            }
        }
    }
    
    /**
     * Load and return the agent's data section
     */
    fun load(): Map<String, Any?> {
        val memory = readFile()
        return jsonObjectToMap(memory.optJSONObject("data") ?: JSONObject())
    }
    
    /**
     * Save data to the agent's memory
     */
    fun save(data: Map<String, Any?>) {
        val memory = readFile()
        memory.put("data", mapToJsonObject(data))
        memory.put("updated_at", System.currentTimeMillis())
        writeFile(memory)
    }
    
    /**
     * Get a specific key from memory
     */
    fun get(key: String, default: Any? = null): Any? {
        val data = load()
        return data[key] ?: default
    }
    
    /**
     * Set a specific key in memory
     */
    fun set(key: String, value: Any?) {
        val data = load().toMutableMap()
        data[key] = value
        save(data)
    }
    
    /**
     * Delete a key from memory
     */
    fun delete(key: String) {
        val data = load().toMutableMap()
        data.remove(key)
        save(data)
    }
    
    /**
     * Append an entry to task history
     */
    fun appendHistory(entry: Map<String, Any?>) {
        val memory = readFile()
        val history = memory.optJSONArray("history") ?: JSONArray()
        
        val entryWithTimestamp = entry.toMutableMap()
        entryWithTimestamp["timestamp"] = System.currentTimeMillis()
        history.put(mapToJsonObject(entryWithTimestamp))
        
        // Keep only last MAX_HISTORY_ENTRIES entries
        while (history.length() > MAX_HISTORY_ENTRIES) {
            history.remove(0)
        }
        
        memory.put("history", history)
        memory.put("updated_at", System.currentTimeMillis())
        writeFile(memory)
    }
    
    /**
     * Get recent task history
     */
    fun getHistory(limit: Int = 50): List<Map<String, Any?>> {
        val memory = readFile()
        val history = memory.optJSONArray("history") ?: return emptyList()
        
        val result = mutableListOf<Map<String, Any?>>()
        val startIndex = maxOf(0, history.length() - limit)
        
        for (i in startIndex until history.length()) {
            result.add(jsonObjectToMap(history.getJSONObject(i)))
        }
        
        return result
    }
    
    /**
     * Clear task history
     */
    fun clearHistory() {
        val memory = readFile()
        memory.put("history", JSONArray())
        memory.put("updated_at", System.currentTimeMillis())
        writeFile(memory)
    }
    
    /**
     * Clear all memory and history
     */
    fun clearAll() {
        initMemory()
    }
    
    /**
     * Get memory statistics
     */
    fun getStats(): Map<String, Any?> {
        val memory = readFile()
        val data = memory.optJSONObject("data") ?: JSONObject()
        val history = memory.optJSONArray("history") ?: JSONArray()
        
        return mapOf(
            "agent_name" to agentName,
            "created_at" to memory.optLong("created_at"),
            "updated_at" to memory.optLong("updated_at"),
            "data_keys" to data.keys().asSequence().toList(),
            "history_count" to history.length(),
            "file_size_bytes" to if (memoryFile.exists()) memoryFile.length() else 0
        )
    }
    
    /**
     * Check if memory file exists
     */
    fun exists(): Boolean = memoryFile.exists()
    
    // JSON conversion utilities
    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = when (val value = json.get(key)) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }
    
    private fun jsonArrayToList(array: JSONArray): List<Any?> {
        return (0 until array.length()).map { i ->
            when (val value = array.get(i)) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
    }
    
    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) ->
            json.put(key, valueToJson(value))
        }
        return json
    }
    
    private fun valueToJson(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                mapToJsonObject(value as Map<String, Any?>)
            }
            is List<*> -> {
                val array = JSONArray()
                value.forEach { array.put(valueToJson(it)) }
                array
            }
            else -> value
        }
    }
}

/**
 * Factory for creating agent memory instances
 */
class AgentMemoryFactory(private val memoryDir: File) {
    private val memories = mutableMapOf<String, AgentMemory>()
    
    init {
        memoryDir.mkdirs()
    }
    
    /**
     * Get or create memory instance for an agent
     */
    fun getMemory(agentName: String): AgentMemory {
        return memories.getOrPut(agentName) {
            AgentMemory(agentName, memoryDir)
        }
    }
    
    /**
     * List all agents with memory
     */
    fun listAgentsWithMemory(): List<String> {
        return memoryDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }
    
    /**
     * Delete memory for an agent
     */
    fun deleteMemory(agentName: String): Boolean {
        memories.remove(agentName)
        return File(memoryDir, "$agentName.json").delete()
    }
}
