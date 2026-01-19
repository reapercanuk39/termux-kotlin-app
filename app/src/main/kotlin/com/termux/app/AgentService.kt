package com.termux.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import com.termux.R
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background service that runs the autonomous agent daemon.
 * 
 * This service starts automatically when Termux launches and keeps
 * the agent framework running in the background with full network access.
 */
class AgentService : Service() {

    companion object {
        private const val LOG_TAG = "AgentService"
        private const val NOTIFICATION_ID = 1338
        private const val CHANNEL_ID = "termux_agent_channel"
        
        private val isRunning = AtomicBoolean(false)
        
        @JvmStatic
        fun isAgentRunning(): Boolean = isRunning.get()
        
        @JvmStatic
        fun startAgentService(context: Context) {
            if (isRunning.get()) {
                Logger.logDebug(LOG_TAG, "Agent service already running")
                return
            }
            
            val intent = Intent(context, AgentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        @JvmStatic
        fun stopAgentService(context: Context) {
            val intent = Intent(context, AgentService::class.java)
            context.stopService(intent)
        }
    }
    
    private val executor = Executors.newSingleThreadExecutor()
    private var agentProcess: Process? = null
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate() {
        super.onCreate()
        Logger.logInfo(LOG_TAG, "AgentService created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.logInfo(LOG_TAG, "AgentService starting")
        
        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, buildNotification())
        
        // Start the agent daemon in background
        startAgentDaemon()
        
        isRunning.set(true)
        
        // Restart if killed
        return START_STICKY
    }
    
    override fun onDestroy() {
        Logger.logInfo(LOG_TAG, "AgentService stopping")
        isRunning.set(false)
        stopAgentDaemon()
        executor.shutdown()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Termux Agent Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Autonomous agent framework running in background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, TermuxActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        
        return builder
            .setContentTitle("Termux Agent")
            .setContentText("Autonomous agents running")
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun startAgentDaemon() {
        executor.execute {
            try {
                val prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH
                val agentsRoot = "$prefix/share/agents"
                val pythonPath = "$prefix/bin/python3"
                val agentDaemon = "$agentsRoot/bin/agentd"
                
                // Check if Python is available
                val pythonFile = File(pythonPath)
                if (!pythonFile.exists()) {
                    Logger.logWarn(LOG_TAG, "Python not installed, agent daemon will start when Python is available")
                    scheduleRetry()
                    return@execute
                }
                
                // Check if agent daemon exists
                val daemonFile = File(agentDaemon)
                if (!daemonFile.exists()) {
                    Logger.logInfo(LOG_TAG, "Creating agent daemon script")
                    createAgentDaemonScript(agentsRoot)
                }
                
                // Set environment variables
                val env = arrayOf(
                    "HOME=${TermuxConstants.TERMUX_HOME_DIR_PATH}",
                    "PREFIX=$prefix",
                    "PATH=$prefix/bin:$prefix/bin/applets",
                    "PYTHONPATH=$agentsRoot",
                    "AGENTS_ROOT=$agentsRoot",
                    "TERMUX_VERSION=${TermuxConstants.TERMUX_APP_NAME}",
                    "LD_LIBRARY_PATH=$prefix/lib",
                    "LANG=en_US.UTF-8"
                )
                
                Logger.logInfo(LOG_TAG, "Starting agent daemon")
                
                // Start the daemon process
                val processBuilder = ProcessBuilder(pythonPath, agentDaemon, "--daemon")
                    .directory(File(agentsRoot))
                    .redirectErrorStream(true)
                
                processBuilder.environment().apply {
                    put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH)
                    put("PREFIX", prefix)
                    put("PATH", "$prefix/bin:$prefix/bin/applets")
                    put("PYTHONPATH", agentsRoot)
                    put("AGENTS_ROOT", agentsRoot)
                    put("LD_LIBRARY_PATH", "$prefix/lib")
                    put("LANG", "en_US.UTF-8")
                }
                
                agentProcess = processBuilder.start()
                
                // Read output in background
                val reader = BufferedReader(InputStreamReader(agentProcess!!.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Logger.logDebug(LOG_TAG, "agentd: $line")
                }
                
                val exitCode = agentProcess?.waitFor() ?: -1
                Logger.logInfo(LOG_TAG, "Agent daemon exited with code: $exitCode")
                
                // Restart if it crashes
                if (isRunning.get() && exitCode != 0) {
                    scheduleRetry()
                }
                
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to start agent daemon: ${e.message}")
                scheduleRetry()
            }
        }
    }
    
    private fun scheduleRetry() {
        if (isRunning.get()) {
            handler.postDelayed({
                if (isRunning.get()) {
                    Logger.logInfo(LOG_TAG, "Retrying agent daemon start")
                    startAgentDaemon()
                }
            }, 30000) // Retry after 30 seconds
        }
    }
    
    private fun stopAgentDaemon() {
        try {
            agentProcess?.destroy()
            agentProcess = null
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Error stopping agent daemon: ${e.message}")
        }
    }
    
    private fun createAgentDaemonScript(agentsRoot: String) {
        val binDir = File(agentsRoot, "bin")
        binDir.mkdirs()
        
        val daemonScript = File(binDir, "agentd")
        val content = """#!/usr/bin/env python3
\"\"\"
Termux-Kotlin Agent Daemon
==========================

Background daemon that runs autonomous agents with full network access.
\"\"\"

import os
import sys
import json
import time
import signal
import logging
import argparse
from pathlib import Path
from datetime import datetime

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger('agentd')

AGENTS_ROOT = Path(os.environ.get('AGENTS_ROOT', '/data/data/com.termux.kotlin/files/usr/share/agents'))
PID_FILE = AGENTS_ROOT / 'logs' / 'agentd.pid'
LOG_FILE = AGENTS_ROOT / 'logs' / 'agentd.log'

class AgentDaemon:
    \"\"\"Main agent daemon process.\"\"\"
    
    def __init__(self):
        self.running = True
        self.agents = {}
        self.start_time = datetime.now()
        
        # Setup signal handlers
        signal.signal(signal.SIGTERM, self._handle_signal)
        signal.signal(signal.SIGINT, self._handle_signal)
    
    def _handle_signal(self, signum, frame):
        logger.info(f"Received signal {signum}, shutting down...")
        self.running = False
    
    def write_pid(self):
        \"\"\"Write PID file.\"\"\"
        PID_FILE.parent.mkdir(parents=True, exist_ok=True)
        PID_FILE.write_text(str(os.getpid()))
        logger.info(f"PID file written: {PID_FILE}")
    
    def cleanup_pid(self):
        \"\"\"Remove PID file.\"\"\"
        try:
            PID_FILE.unlink()
        except:
            pass
    
    def load_agents(self):
        \"\"\"Load agent configurations.\"\"\"
        agents_dir = AGENTS_ROOT / 'models'
        if agents_dir.exists():
            for agent_file in agents_dir.glob('*.json'):
                try:
                    agent_config = json.loads(agent_file.read_text())
                    name = agent_config.get('name', agent_file.stem)
                    self.agents[name] = agent_config
                    logger.info(f"Loaded agent: {name}")
                except Exception as e:
                    logger.error(f"Failed to load agent {agent_file}: {e}")
        
        logger.info(f"Loaded {len(self.agents)} agents")
    
    def run_autonomous_tasks(self):
        \"\"\"Run autonomous background tasks.\"\"\"
        try:
            # Import the autonomous executor
            sys.path.insert(0, str(AGENTS_ROOT))
            
            # Check for pending tasks in memory
            memory_dir = AGENTS_ROOT / 'memory'
            if memory_dir.exists():
                for mem_file in memory_dir.glob('*.json'):
                    try:
                        memory = json.loads(mem_file.read_text())
                        pending = memory.get('pending_tasks', [])
                        if pending:
                            logger.info(f"Found {len(pending)} pending tasks for {mem_file.stem}")
                            # Process tasks...
                    except:
                        pass
        except Exception as e:
            logger.debug(f"Autonomous task check: {e}")
    
    def run(self):
        \"\"\"Main daemon loop.\"\"\"
        logger.info("Agent daemon starting")
        logger.info(f"AGENTS_ROOT: {AGENTS_ROOT}")
        logger.info(f"Network: ENABLED")
        
        self.write_pid()
        self.load_agents()
        
        # Main loop
        cycle = 0
        while self.running:
            try:
                cycle += 1
                
                # Run autonomous tasks every 60 seconds
                if cycle % 60 == 0:
                    self.run_autonomous_tasks()
                
                # Health check every 10 cycles
                if cycle % 10 == 0:
                    uptime = datetime.now() - self.start_time
                    logger.debug(f"Daemon healthy, uptime: {uptime}")
                
                time.sleep(1)
                
            except Exception as e:
                logger.error(f"Error in main loop: {e}")
                time.sleep(5)
        
        self.cleanup_pid()
        logger.info("Agent daemon stopped")

def main():
    parser = argparse.ArgumentParser(description='Termux Agent Daemon')
    parser.add_argument('--daemon', action='store_true', help='Run as daemon')
    parser.add_argument('--status', action='store_true', help='Check daemon status')
    parser.add_argument('--stop', action='store_true', help='Stop daemon')
    args = parser.parse_args()
    
    if args.status:
        if PID_FILE.exists():
            pid = PID_FILE.read_text().strip()
            print(f"Agent daemon running (PID: {pid})")
        else:
            print("Agent daemon not running")
        return
    
    if args.stop:
        if PID_FILE.exists():
            pid = int(PID_FILE.read_text().strip())
            os.kill(pid, signal.SIGTERM)
            print(f"Sent SIGTERM to PID {pid}")
        else:
            print("Agent daemon not running")
        return
    
    # Run daemon
    daemon = AgentDaemon()
    daemon.run()

if __name__ == '__main__':
    main()
"""
        daemonScript.writeText(content)
        daemonScript.setExecutable(true)
        Logger.logInfo(LOG_TAG, "Created agent daemon script at ${daemonScript.absolutePath}")
    }
}
