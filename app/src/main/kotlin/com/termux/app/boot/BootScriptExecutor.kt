package com.termux.app.boot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes boot scripts from ~/.termux/boot/ directory.
 * 
 * Scripts are executed in alphabetical order by filename.
 * Each script runs with the Termux environment configured.
 */
@Singleton
class BootScriptExecutor @Inject constructor(
    private val context: Context,
    private val bootPreferences: BootPreferences
) {
    companion object {
        private const val LOG_TAG = "BootScriptExecutor"
        private const val CHANNEL_ID = "termux_boot"
        private const val NOTIFICATION_ID = 1339
        
        private val PREFIX = TermuxConstants.TERMUX_PREFIX_DIR_PATH
        private val HOME = TermuxConstants.TERMUX_HOME_DIR_PATH
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Result of a boot script execution.
     */
    data class ScriptResult(
        val script: File,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val durationMs: Long,
        val success: Boolean = exitCode == 0
    )
    
    /**
     * Result of the entire boot execution.
     */
    data class BootExecutionResult(
        val scripts: List<ScriptResult>,
        val totalDurationMs: Long,
        val successCount: Int,
        val failureCount: Int
    ) {
        val allSucceeded: Boolean get() = failureCount == 0
        val status: String get() = when {
            scripts.isEmpty() -> "no_scripts"
            allSucceeded -> "success"
            successCount == 0 -> "all_failed"
            else -> "partial_failure"
        }
    }
    
    /**
     * Execute all boot scripts.
     */
    suspend fun executeBootScripts(settings: BootSettings): BootExecutionResult {
        val startTime = System.currentTimeMillis()
        
        if (!settings.enabled || !settings.runOnBoot) {
            Logger.logInfo(LOG_TAG, "Boot scripts disabled")
            return BootExecutionResult(emptyList(), 0, 0, 0)
        }
        
        val scripts = bootPreferences.listBootScripts()
        if (scripts.isEmpty()) {
            Logger.logInfo(LOG_TAG, "No boot scripts found")
            return BootExecutionResult(emptyList(), 0, 0, 0)
        }
        
        Logger.logInfo(LOG_TAG, "Found ${scripts.size} boot scripts to execute")
        
        // Show notification if enabled
        if (settings.showNotification) {
            showProgressNotification("Running ${scripts.size} boot scripts...")
        }
        
        val results = if (settings.parallelExecution) {
            executeParallel(scripts, settings.scriptTimeout)
        } else {
            executeSequential(scripts, settings.scriptTimeout)
        }
        
        val totalDuration = System.currentTimeMillis() - startTime
        val successCount = results.count { it.success }
        val failureCount = results.size - successCount
        
        // Update notification
        if (settings.showNotification) {
            val message = when {
                failureCount == 0 -> "Executed ${scripts.size} scripts successfully"
                successCount == 0 -> "All ${scripts.size} scripts failed"
                else -> "$successCount succeeded, $failureCount failed"
            }
            showCompletionNotification(message, failureCount > 0)
        }
        
        // Record execution
        val result = BootExecutionResult(results, totalDuration, successCount, failureCount)
        bootPreferences.recordBootExecution(result.status, scripts.size)
        
        Logger.logInfo(LOG_TAG, "Boot scripts completed: ${result.status} in ${totalDuration}ms")
        
        return result
    }
    
    /**
     * Execute scripts sequentially.
     */
    private suspend fun executeSequential(
        scripts: List<File>,
        timeoutSeconds: Int
    ): List<ScriptResult> = withContext(Dispatchers.IO) {
        scripts.mapIndexed { index, script ->
            Logger.logDebug(LOG_TAG, "Executing script ${index + 1}/${scripts.size}: ${script.name}")
            executeScript(script, timeoutSeconds)
        }
    }
    
    /**
     * Execute scripts in parallel.
     */
    private suspend fun executeParallel(
        scripts: List<File>,
        timeoutSeconds: Int
    ): List<ScriptResult> = coroutineScope {
        scripts.map { script ->
            async(Dispatchers.IO) {
                Logger.logDebug(LOG_TAG, "Executing script (parallel): ${script.name}")
                executeScript(script, timeoutSeconds)
            }
        }.awaitAll()
    }
    
    /**
     * Execute a single script.
     */
    private suspend fun executeScript(script: File, timeoutSeconds: Int): ScriptResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            withTimeout(timeoutSeconds * 1000L) {
                val processBuilder = ProcessBuilder(script.absolutePath)
                    .directory(File(HOME))
                    .redirectErrorStream(false)
                
                // Set up environment
                processBuilder.environment().apply {
                    put("HOME", HOME)
                    put("PREFIX", PREFIX)
                    put("PATH", "$PREFIX/bin:$PREFIX/bin/applets:/system/bin")
                    put("LD_LIBRARY_PATH", "$PREFIX/lib")
                    put("LANG", "en_US.UTF-8")
                    put("TERM", "xterm-256color")
                    put("SHELL", "$PREFIX/bin/bash")
                    put("TERMUX_BOOT", "1") // Indicate running from boot
                }
                
                val process = processBuilder.start()
                
                // Read output
                val stdout = StringBuilder()
                val stderr = StringBuilder()
                
                val stdoutReader = async(Dispatchers.IO) {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        reader.lineSequence().forEach { stdout.appendLine(it) }
                    }
                }
                
                val stderrReader = async(Dispatchers.IO) {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        reader.lineSequence().forEach { stderr.appendLine(it) }
                    }
                }
                
                val exitCode = process.waitFor()
                stdoutReader.await()
                stderrReader.await()
                
                val duration = System.currentTimeMillis() - startTime
                
                ScriptResult(
                    script = script,
                    exitCode = exitCode,
                    stdout = stdout.toString(),
                    stderr = stderr.toString(),
                    durationMs = duration
                )
            }
        } catch (e: TimeoutCancellationException) {
            Logger.logWarn(LOG_TAG, "Script timed out: ${script.name}")
            ScriptResult(
                script = script,
                exitCode = -1,
                stdout = "",
                stderr = "Script timed out after ${timeoutSeconds}s",
                durationMs = timeoutSeconds * 1000L,
                success = false
            )
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Script execution failed: ${script.name} - ${e.message}")
            ScriptResult(
                script = script,
                exitCode = -1,
                stdout = "",
                stderr = "Execution error: ${e.message}",
                durationMs = System.currentTimeMillis() - startTime,
                success = false
            )
        }
    }
    
    /**
     * Show progress notification.
     */
    private fun showProgressNotification(message: String) {
        createNotificationChannel()
        
        val intent = Intent(context, TermuxActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Termux Boot")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setContentIntent(pendingIntent)
            .build()
        
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Show completion notification.
     */
    private fun showCompletionNotification(message: String, hasFailures: Boolean) {
        createNotificationChannel()
        
        val intent = Intent(context, TermuxActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Termux Boot Complete")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(if (hasFailures) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Create notification channel.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Termux Boot",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Boot script execution notifications"
            }
            
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create sample boot script for first-time users.
     */
    fun createSampleScript() {
        val bootDir = bootPreferences.getBootScriptsDir()
        val sampleScript = File(bootDir, "00-sample.sh")
        
        if (!sampleScript.exists()) {
            sampleScript.writeText("""
                #!/data/data/com.termux/files/usr/bin/bash
                #
                # Sample Termux Boot Script
                # =========================
                # 
                # This script runs automatically when your device boots.
                # Place your scripts in ~/.termux/boot/ to run them at boot.
                #
                # Scripts are executed in alphabetical order by filename.
                # Use numeric prefixes to control order: 00-first.sh, 10-second.sh
                #
                # Environment variable TERMUX_BOOT=1 is set when running from boot.
                #
                
                # Log boot time
                echo "[$(date)] Device booted" >> ~/boot.log
                
                # Example: Start SSH server
                # sshd
                
                # Example: Sync files
                # termux-wake-lock
                # rsync -av ~/important/ /sdcard/backup/
                # termux-wake-unlock
                
                exit 0
            """.trimIndent())
            sampleScript.setExecutable(true)
            
            Logger.logInfo(LOG_TAG, "Created sample boot script: ${sampleScript.absolutePath}")
        }
    }
    
    /**
     * Clean up resources.
     */
    fun shutdown() {
        scope.cancel()
    }
}
