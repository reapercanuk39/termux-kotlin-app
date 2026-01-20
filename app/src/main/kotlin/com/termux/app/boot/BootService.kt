package com.termux.app.boot

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.termux.shared.logger.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Foreground service for executing boot scripts.
 * 
 * Started by SystemEventReceiver on BOOT_COMPLETED.
 * Runs scripts from ~/.termux/boot/ with a wake lock.
 */
@AndroidEntryPoint
class BootService : Service() {
    
    companion object {
        private const val LOG_TAG = "BootService"
        private const val WAKE_LOCK_TAG = "termux:boot"
        private const val WAKE_LOCK_TIMEOUT = 10 * 60 * 1000L // 10 minutes max
    }
    
    @Inject
    lateinit var bootPreferences: BootPreferences
    
    @Inject
    lateinit var bootScriptExecutor: BootScriptExecutor
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        Logger.logInfo(LOG_TAG, "BootService created")
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.logInfo(LOG_TAG, "BootService starting")
        
        serviceScope.launch {
            try {
                val settings = bootPreferences.getSettings()
                
                if (!settings.enabled) {
                    Logger.logInfo(LOG_TAG, "Boot scripts disabled, stopping service")
                    stopSelf()
                    return@launch
                }
                
                // Ensure boot directory exists
                bootPreferences.getBootScriptsDir()
                
                // Execute boot scripts
                val result = bootScriptExecutor.executeBootScripts(settings)
                
                Logger.logInfo(LOG_TAG, """
                    Boot scripts completed:
                    - Status: ${result.status}
                    - Scripts: ${result.scripts.size}
                    - Succeeded: ${result.successCount}
                    - Failed: ${result.failureCount}
                    - Duration: ${result.totalDurationMs}ms
                """.trimIndent())
                
                // Log individual script results
                result.scripts.forEach { scriptResult ->
                    if (scriptResult.success) {
                        Logger.logDebug(LOG_TAG, "✓ ${scriptResult.script.name} (${scriptResult.durationMs}ms)")
                    } else {
                        Logger.logWarn(LOG_TAG, "✗ ${scriptResult.script.name}: ${scriptResult.stderr.take(100)}")
                    }
                }
                
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Boot script execution failed: ${e.message}")
            } finally {
                releaseWakeLock()
                stopSelf()
            }
        }
        
        // Don't restart if killed
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        Logger.logInfo(LOG_TAG, "BootService destroyed")
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Acquire wake lock to keep device awake during script execution.
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                acquire(WAKE_LOCK_TIMEOUT)
            }
            Logger.logDebug(LOG_TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }
    
    /**
     * Release wake lock.
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Logger.logDebug(LOG_TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "Failed to release wake lock: ${e.message}")
        }
    }
}
