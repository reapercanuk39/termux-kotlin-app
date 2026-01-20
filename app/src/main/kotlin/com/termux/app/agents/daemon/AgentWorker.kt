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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
    
    /**
     * Hilt EntryPoint to access singleton dependencies from BroadcastReceiver.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AgentWorkerEntryPoint {
        fun swarmCoordinator(): SwarmCoordinator
        fun agentDaemon(): AgentDaemon
    }
    
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
        
        // Get dependencies via Hilt EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AgentWorkerEntryPoint::class.java
        )
        
        scope.launch {
            try {
                when (action) {
                    ACTION_HEALTH_CHECK -> performHealthCheck(context, entryPoint)
                    ACTION_SIGNAL_CLEANUP -> performSignalCleanup(context, entryPoint)
                }
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Error in worker: ${e.message}")
            }
        }
    }
    
    private suspend fun performHealthCheck(context: Context, entryPoint: AgentWorkerEntryPoint) {
        Logger.logDebug(LOG_TAG, "Performing health check")
        
        val swarmCoordinator = entryPoint.swarmCoordinator()
        val agentDaemon = entryPoint.agentDaemon()
        
        // Get current statistics
        val stats = agentDaemon.getStatistics()
        
        // Emit heartbeat signal with daemon status
        swarmCoordinator.emit(
            signalType = SignalType.HEARTBEAT,
            sourceAgent = "daemon",
            target = "health_check",
            data = mapOf(
                "state" to stats.state.name,
                "uptime_ms" to stats.uptime,
                "agents" to stats.registeredAgents,
                "skills" to stats.registeredSkills,
                "tasks_executed" to stats.tasksExecuted,
                "success_rate" to stats.successRate,
                "timestamp" to System.currentTimeMillis()
            ),
            ttl = 1800_000L  // 30 minutes TTL for heartbeat
        )
        
        Logger.logInfo(LOG_TAG, "Health check completed - emitted heartbeat signal")
    }
    
    private suspend fun performSignalCleanup(context: Context, entryPoint: AgentWorkerEntryPoint) {
        Logger.logDebug(LOG_TAG, "Performing signal cleanup")
        
        val swarmCoordinator = entryPoint.swarmCoordinator()
        
        // Get status before cleanup
        val beforeStatus = swarmCoordinator.getStatus()
        val beforeCount = beforeStatus["total_signals"] as? Int ?: 0
        
        // Run decay cycle to clean up old/weak signals
        swarmCoordinator.runDecayCycle()
        
        // Get status after cleanup
        val afterStatus = swarmCoordinator.getStatus()
        val afterCount = afterStatus["total_signals"] as? Int ?: 0
        val removed = beforeCount - afterCount
        
        if (removed > 0) {
            Logger.logInfo(LOG_TAG, "Signal cleanup completed - removed $removed weak/expired signals")
        } else {
            Logger.logDebug(LOG_TAG, "Signal cleanup completed - no signals removed")
        }
    }
}
