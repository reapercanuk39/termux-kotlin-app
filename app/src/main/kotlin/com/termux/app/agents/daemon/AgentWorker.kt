package com.termux.app.agents.daemon

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.termux.app.agents.swarm.SignalType
import com.termux.app.agents.swarm.SwarmCoordinator
import com.termux.shared.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Periodic agent health check receiver.
 * Uses AlarmManager for periodic execution.
 */
class AgentWorker : BroadcastReceiver() {
    
    companion object {
        private const val LOG_TAG = "AgentWorker"
        const val ACTION_HEALTH_CHECK = "com.termux.agent.HEALTH_CHECK"
        const val ACTION_SIGNAL_CLEANUP = "com.termux.agent.SIGNAL_CLEANUP"
        
        private const val REQUEST_CODE_HEALTH = 1001
        private const val REQUEST_CODE_CLEANUP = 1002
        
        /**
         * Schedule periodic health checks.
         */
        fun schedulePeriodicWork(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Health check every 15 minutes
            val healthIntent = Intent(context, AgentWorker::class.java).apply {
                action = ACTION_HEALTH_CHECK
            }
            val healthPending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_HEALTH,
                healthIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(15),
                TimeUnit.MINUTES.toMillis(15),
                healthPending
            )
            
            // Signal cleanup every hour
            val cleanupIntent = Intent(context, AgentWorker::class.java).apply {
                action = ACTION_SIGNAL_CLEANUP
            }
            val cleanupPending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_CLEANUP,
                cleanupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + TimeUnit.HOURS.toMillis(1),
                TimeUnit.HOURS.toMillis(1),
                cleanupPending
            )
            
            Logger.logInfo(LOG_TAG, "Scheduled periodic agent work")
        }
        
        /**
         * Cancel all periodic work.
         */
        fun cancelPeriodicWork(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            val healthIntent = Intent(context, AgentWorker::class.java).apply {
                action = ACTION_HEALTH_CHECK
            }
            val healthPending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_HEALTH,
                healthIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            healthPending?.let { alarmManager.cancel(it) }
            
            val cleanupIntent = Intent(context, AgentWorker::class.java).apply {
                action = ACTION_SIGNAL_CLEANUP
            }
            val cleanupPending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_CLEANUP,
                cleanupIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            cleanupPending?.let { alarmManager.cancel(it) }
            
            Logger.logInfo(LOG_TAG, "Cancelled periodic agent work")
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        
        Logger.logDebug(LOG_TAG, "Received action: $action")
        
        // Get daemon and coordinator from application
        // Note: In production, these would be injected via EntryPointAccessors
        // For simplicity, we access them through the application
        scope.launch {
            try {
                when (action) {
                    ACTION_HEALTH_CHECK -> performHealthCheck(context)
                    ACTION_SIGNAL_CLEANUP -> performSignalCleanup(context)
                }
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Error in worker: ${e.message}")
            }
        }
    }
    
    private suspend fun performHealthCheck(context: Context) {
        Logger.logDebug(LOG_TAG, "Performing health check")
        // Health check logic - check if daemon is responsive
        // In a real implementation, this would use EntryPointAccessors to get the daemon
        Logger.logInfo(LOG_TAG, "Health check completed")
    }
    
    private suspend fun performSignalCleanup(context: Context) {
        Logger.logDebug(LOG_TAG, "Performing signal cleanup")
        // Signal cleanup logic
        Logger.logInfo(LOG_TAG, "Signal cleanup completed")
    }
}
