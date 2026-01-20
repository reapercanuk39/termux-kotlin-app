package com.termux.app.agents.cli

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.termux.app.AgentService
import com.termux.app.agents.daemon.AgentDaemon
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CLI bridge for executing agent commands from shell.
 * 
 * Provides two communication modes:
 * 1. Intent-based IPC via BroadcastReceiver
 * 2. File-based IPC for shell scripts
 */
@Singleton
class CliBridge @Inject constructor(
    private val context: Context,
    private val agentDaemon: AgentDaemon
) {
    companion object {
        private const val CLI_DIR = "/data/data/com.termux/files/usr/share/agents/cli"
        private const val REQUEST_FILE = "$CLI_DIR/request.json"
        private const val RESPONSE_FILE = "$CLI_DIR/response.json"
        private const val LOCK_FILE = "$CLI_DIR/.lock"
        
        // Polling interval for file-based IPC
        private const val POLL_INTERVAL_MS = 100L
        private const val POLL_TIMEOUT_MS = 30_000L
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fileWatcherJob: Job? = null
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Start listening for CLI requests.
     */
    fun start() {
        ensureCliDirectory()
        startFileWatcher()
    }
    
    /**
     * Stop listening.
     */
    fun stop() {
        fileWatcherJob?.cancel()
        fileWatcherJob = null
    }
    
    private fun ensureCliDirectory() {
        val cliDir = File(CLI_DIR)
        if (!cliDir.exists()) {
            cliDir.mkdirs()
        }
    }
    
    /**
     * Watch for CLI request files.
     */
    private fun startFileWatcher() {
        fileWatcherJob = scope.launch {
            val requestFile = File(REQUEST_FILE)
            
            while (isActive) {
                try {
                    if (requestFile.exists()) {
                        val request = requestFile.readText()
                        requestFile.delete()
                        
                        val response = handleRequest(request)
                        File(RESPONSE_FILE).writeText(response)
                    }
                    
                    delay(POLL_INTERVAL_MS)
                } catch (e: Exception) {
                    delay(1000) // Back off on error
                }
            }
        }
    }
    
    /**
     * Handle a CLI request.
     */
    private suspend fun handleRequest(requestJson: String): String {
        return try {
            val request = parseRequest(requestJson)
            
            when (request.command) {
                "status" -> handleStatus()
                "agents" -> handleListAgents()
                "skills" -> handleListSkills()
                "run" -> handleRunTask(request)
                "self-test" -> handleSelfTest()
                "help" -> handleHelp()
                else -> errorResponse("Unknown command: ${request.command}")
            }
        } catch (e: Exception) {
            errorResponse("Request failed: ${e.message}")
        }
    }
    
    private suspend fun handleStatus(): String {
        val stats = agentDaemon.getStatistics()
        return successResponse(mapOf(
            "state" to stats.state.name,
            "uptime" to stats.uptimeFormatted,
            "tasks_executed" to stats.tasksExecuted,
            "tasks_succeeded" to stats.tasksSucceeded,
            "tasks_failed" to stats.tasksFailed,
            "active_tasks" to stats.activeTasks,
            "registered_agents" to stats.registeredAgents,
            "validated_agents" to stats.validatedAgents,
            "registered_skills" to stats.registeredSkills,
            "success_rate" to "%.1f%%".format(stats.successRate * 100)
        ))
    }
    
    private suspend fun handleListAgents(): String {
        val agents = agentDaemon.getAgents()
        return successResponse(mapOf(
            "count" to agents.size,
            "agents" to agents.map { agent ->
                mapOf(
                    "name" to agent.name,
                    "description" to agent.description,
                    "version" to agent.version,
                    "skills" to agent.skills,
                    "capabilities" to agent.capabilities.map { it.toString() }
                )
            }
        ))
    }
    
    private suspend fun handleListSkills(): String {
        val skills = agentDaemon.getSkills()
        return successResponse(mapOf(
            "count" to skills.size,
            "skills" to skills.toList()
        ))
    }
    
    private suspend fun handleRunTask(request: CliRequest): String {
        val agentName = request.args["agent"] ?: return errorResponse("Missing 'agent' argument")
        val skillName = request.args["skill"] ?: return errorResponse("Missing 'skill' argument")
        val function = request.args["function"] ?: return errorResponse("Missing 'function' argument")
        
        val params = request.args.filterKeys { 
            it !in listOf("agent", "skill", "function") 
        }
        
        val result = agentDaemon.runTask(agentName, skillName, function, params)
        
        return when (result) {
            is com.termux.app.agents.models.TaskResult.Success -> 
                successResponse(mapOf(
                    "success" to true,
                    "data" to result.data
                ))
            is com.termux.app.agents.models.TaskResult.Failure -> 
                errorResponse(result.error.message ?: "Task failed")
            is com.termux.app.agents.models.TaskResult.Timeout -> 
                errorResponse("Task timed out after ${result.durationMs}ms")
            is com.termux.app.agents.models.TaskResult.Cancelled -> 
                errorResponse("Task cancelled: ${result.reason}")
        }
    }
    
    private suspend fun handleSelfTest(): String {
        val results = agentDaemon.runSelfTests()
        return successResponse(results)
    }
    
    private fun handleHelp(): String {
        return successResponse(mapOf(
            "usage" to "agent <command> [args...]",
            "commands" to mapOf(
                "status" to "Show daemon status",
                "agents" to "List registered agents",
                "skills" to "List available skills",
                "run" to "Run a task: agent run --agent=<name> --skill=<skill> --function=<func> [--param=value...]",
                "self-test" to "Run self-tests on all skills",
                "help" to "Show this help"
            )
        ))
    }
    
    private fun parseRequest(json: String): CliRequest {
        val lines = json.trim().removePrefix("{").removeSuffix("}").split(",")
        var command = ""
        val args = mutableMapOf<String, String>()
        
        lines.forEach { line ->
            val parts = line.trim().split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().trim('"')
                val value = parts[1].trim().trim('"')
                
                if (key == "command") {
                    command = value
                } else if (key == "args" || key.startsWith("arg_")) {
                    // Parse nested args
                    val argKey = key.removePrefix("arg_")
                    args[argKey] = value
                } else {
                    args[key] = value
                }
            }
        }
        
        return CliRequest(command, args)
    }
    
    private fun successResponse(data: Any): String {
        return """{"success": true, "data": ${serializeData(data)}}"""
    }
    
    private fun errorResponse(message: String): String {
        return """{"success": false, "error": "$message"}"""
    }
    
    private fun serializeData(data: Any): String {
        return when (data) {
            is String -> "\"$data\""
            is Number, is Boolean -> data.toString()
            is Map<*, *> -> {
                val entries = data.entries.joinToString(",") { (k, v) ->
                    "\"$k\": ${serializeData(v ?: "null")}"
                }
                "{$entries}"
            }
            is List<*> -> {
                val items = data.joinToString(",") { serializeData(it ?: "null") }
                "[$items]"
            }
            else -> "\"$data\""
        }
    }
}

/**
 * CLI request data.
 */
data class CliRequest(
    val command: String,
    val args: Map<String, String> = emptyMap()
)
