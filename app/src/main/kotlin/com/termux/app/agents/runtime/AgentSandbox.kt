package com.termux.app.agents.runtime

import com.termux.shared.logger.Logger
import java.io.File

/**
 * Per-agent sandbox management.
 * 
 * Each agent gets an isolated directory for:
 * - tmp/ - temporary files (cleaned automatically)
 * - work/ - working files
 * - output/ - task outputs
 * - cache/ - cached data
 */
class AgentSandbox(
    private val agentName: String,
    private val sandboxesDir: File
) {
    companion object {
        private const val LOG_TAG = "AgentSandbox"
        private const val MAX_TMP_AGE_MS = 3600_000L  // 1 hour
    }
    
    val sandboxDir = File(sandboxesDir, agentName)
    val tmpDir = File(sandboxDir, "tmp")
    val workDir = File(sandboxDir, "work")
    val outputDir = File(sandboxDir, "output")
    val cacheDir = File(sandboxDir, "cache")
    
    init {
        ensureDirectories()
    }
    
    /**
     * Ensure all sandbox directories exist
     */
    fun ensureDirectories() {
        listOf(sandboxDir, tmpDir, workDir, outputDir, cacheDir).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
                Logger.logDebug(LOG_TAG, "Created sandbox dir: ${dir.absolutePath}")
            }
        }
    }
    
    /**
     * Get a temporary file path
     */
    fun getTempFile(name: String): File {
        return File(tmpDir, name)
    }
    
    /**
     * Get a work file path
     */
    fun getWorkFile(name: String): File {
        return File(workDir, name)
    }
    
    /**
     * Get an output file path
     */
    fun getOutputFile(name: String): File {
        return File(outputDir, name)
    }
    
    /**
     * Get a cache file path
     */
    fun getCacheFile(name: String): File {
        return File(cacheDir, name)
    }
    
    /**
     * Clean old temporary files
     */
    fun cleanTmp(maxAgeMs: Long = MAX_TMP_AGE_MS): Int {
        var cleaned = 0
        val now = System.currentTimeMillis()
        
        tmpDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > maxAgeMs) {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
                cleaned++
            }
        }
        
        if (cleaned > 0) {
            Logger.logDebug(LOG_TAG, "Cleaned $cleaned old temp files for $agentName")
        }
        
        return cleaned
    }
    
    /**
     * Clean all temporary files
     */
    fun clearTmp(): Int {
        var cleaned = 0
        tmpDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            cleaned++
        }
        return cleaned
    }
    
    /**
     * Clear work directory
     */
    fun clearWork(): Int {
        var cleaned = 0
        workDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            cleaned++
        }
        return cleaned
    }
    
    /**
     * Clear output directory
     */
    fun clearOutput(): Int {
        var cleaned = 0
        outputDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            cleaned++
        }
        return cleaned
    }
    
    /**
     * Clear cache directory
     */
    fun clearCache(): Int {
        var cleaned = 0
        cacheDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            cleaned++
        }
        return cleaned
    }
    
    /**
     * Clear entire sandbox
     */
    fun clearAll(): Boolean {
        val result = sandboxDir.deleteRecursively()
        ensureDirectories()
        return result
    }
    
    /**
     * Get sandbox statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "agent_name" to agentName,
            "sandbox_path" to sandboxDir.absolutePath,
            "tmp_files" to (tmpDir.listFiles()?.size ?: 0),
            "work_files" to (workDir.listFiles()?.size ?: 0),
            "output_files" to (outputDir.listFiles()?.size ?: 0),
            "cache_files" to (cacheDir.listFiles()?.size ?: 0),
            "total_size_bytes" to calculateSize(sandboxDir)
        )
    }
    
    /**
     * Check if a path is within the sandbox
     */
    fun isPathInSandbox(path: File): Boolean {
        return path.canonicalPath.startsWith(sandboxDir.canonicalPath)
    }
    
    /**
     * Validate that a path is within sandbox (throws if not)
     */
    fun validatePath(path: File) {
        if (!isPathInSandbox(path)) {
            throw SecurityException("Path ${path.absolutePath} is outside sandbox")
        }
    }
    
    private fun calculateSize(dir: File): Long {
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

/**
 * Factory for creating agent sandboxes
 */
class AgentSandboxFactory(private val sandboxesDir: File) {
    private val sandboxes = mutableMapOf<String, AgentSandbox>()
    
    init {
        sandboxesDir.mkdirs()
    }
    
    /**
     * Get or create sandbox for an agent
     */
    fun getSandbox(agentName: String): AgentSandbox {
        return sandboxes.getOrPut(agentName) {
            AgentSandbox(agentName, sandboxesDir)
        }
    }
    
    /**
     * List all agents with sandboxes
     */
    fun listAgentsWithSandbox(): List<String> {
        return sandboxesDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()
    }
    
    /**
     * Delete sandbox for an agent
     */
    fun deleteSandbox(agentName: String): Boolean {
        sandboxes.remove(agentName)
        return File(sandboxesDir, agentName).deleteRecursively()
    }
    
    /**
     * Clean all old temp files across all sandboxes
     */
    fun cleanAllTempFiles(maxAgeMs: Long = 3600_000L): Int {
        var total = 0
        sandboxes.values.forEach { sandbox ->
            total += sandbox.cleanTmp(maxAgeMs)
        }
        return total
    }
}
