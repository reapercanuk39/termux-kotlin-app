package com.termux.app.agents.runtime

import com.termux.app.agents.models.CommandResult
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Safe command execution for agents.
 * All subprocess calls go through this class.
 */
class CommandRunner(
    private val prefix: String = TermuxConstants.TERMUX_PREFIX_DIR_PATH,
    private val homeDir: String = TermuxConstants.TERMUX_HOME_DIR_PATH
) {
    companion object {
        private const val LOG_TAG = "CommandRunner"
        private const val DEFAULT_TIMEOUT_MS = 60_000L  // 1 minute default
    }
    
    /**
     * Run a command with timeout and capture output
     */
    suspend fun run(
        command: List<String>,
        workingDir: File? = null,
        env: Map<String, String> = emptyMap(),
        timeout: Long = DEFAULT_TIMEOUT_MS,
        captureStderr: Boolean = true
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val processBuilder = ProcessBuilder(command)
                .directory(workingDir ?: File(homeDir))
            
            // Set up environment
            processBuilder.environment().apply {
                put("HOME", homeDir)
                put("PREFIX", prefix)
                put("PATH", "$prefix/bin:$prefix/bin/applets")
                put("LD_LIBRARY_PATH", "$prefix/lib")
                put("LANG", "en_US.UTF-8")
                put("TERM", "xterm-256color")
                put("TERMINFO", "$prefix/share/terminfo")
                putAll(env)
            }
            
            if (captureStderr) {
                processBuilder.redirectErrorStream(true)
            }
            
            Logger.logDebug(LOG_TAG, "Running: ${command.joinToString(" ")}")
            
            val process = processBuilder.start()
            
            val result = withTimeoutOrNull(timeout) {
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = if (!captureStderr) {
                    process.errorStream.bufferedReader().readText()
                } else ""
                
                val exitCode = process.waitFor()
                val duration = System.currentTimeMillis() - startTime
                
                CommandResult(
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr,
                    durationMs = duration
                )
            }
            
            if (result == null) {
                // Timeout - kill process
                process.destroyForcibly()
                val duration = System.currentTimeMillis() - startTime
                Logger.logWarn(LOG_TAG, "Command timed out after ${timeout}ms")
                CommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Command timed out after ${timeout}ms",
                    durationMs = duration
                )
            } else {
                if (result.exitCode != 0) {
                    Logger.logDebug(LOG_TAG, "Command exited with code ${result.exitCode}")
                }
                result
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Logger.logError(LOG_TAG, "Command failed: ${e.message}")
            CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "Exception: ${e.message}",
                durationMs = duration
            )
        }
    }
    
    /**
     * Run a shell command string
     */
    suspend fun runShell(
        command: String,
        workingDir: File? = null,
        env: Map<String, String> = emptyMap(),
        timeout: Long = DEFAULT_TIMEOUT_MS
    ): CommandResult {
        return run(
            command = listOf("$prefix/bin/sh", "-c", command),
            workingDir = workingDir,
            env = env,
            timeout = timeout
        )
    }
    
    /**
     * Check if a binary exists
     */
    fun binaryExists(name: String): Boolean {
        return File("$prefix/bin/$name").exists()
    }
    
    /**
     * Get the full path to a binary
     */
    fun getBinaryPath(name: String): String? {
        val path = File("$prefix/bin/$name")
        return if (path.exists()) path.absolutePath else null
    }
}
