package com.termux.app.agents.daemon

import android.content.Context
import com.termux.app.agents.models.Agent
import com.termux.app.agents.models.Capability
import com.termux.app.agents.models.MemoryBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent registry that discovers, validates, and manages agent lifecycle.
 * Agents are defined in YAML files in assets/agents/models/ directory.
 */
@Singleton
class AgentRegistry @Inject constructor(
    private val context: Context
) {
    private val agents = mutableMapOf<String, Agent>()
    private val agentStates = mutableMapOf<String, AgentState>()
    private val mutex = Mutex()
    
    // Standard asset paths
    companion object {
        const val AGENTS_ASSET_PATH = "agents/models"
        const val AGENTS_DATA_PATH = "/data/data/com.termux/files/usr/share/termux-agents/models"
    }
    
    /**
     * State of a registered agent.
     */
    enum class AgentState {
        REGISTERED,  // Agent is known but not validated
        VALIDATED,   // Agent has been validated and is ready
        ACTIVE,      // Agent is currently executing tasks
        SUSPENDED,   // Agent is temporarily suspended
        ERROR        // Agent has encountered an error
    }
    
    /**
     * Discover and load all agents from both assets and data directories.
     */
    suspend fun discoverAgents(): List<Agent> = mutex.withLock {
        val discovered = mutableListOf<Agent>()
        
        // Load from assets
        discovered.addAll(loadFromAssets())
        
        // Load from data directory (user-defined agents)
        discovered.addAll(loadFromDataDir())
        
        // Register all discovered agents
        discovered.forEach { agent ->
            agents[agent.name] = agent
            agentStates[agent.name] = AgentState.REGISTERED
        }
        
        discovered
    }
    
    /**
     * Load agents from assets directory.
     */
    private suspend fun loadFromAssets(): List<Agent> = withContext(Dispatchers.IO) {
        val agents = mutableListOf<Agent>()
        
        try {
            val assetManager = context.assets
            val files = assetManager.list(AGENTS_ASSET_PATH) ?: emptyArray()
            
            files.filter { it.endsWith(".yml") || it.endsWith(".yaml") }
                .forEach { filename ->
                    try {
                        val content = assetManager.open("$AGENTS_ASSET_PATH/$filename")
                            .bufferedReader()
                            .use { it.readText() }
                        
                        val agent = parseAgentYaml(content, filename)
                        if (agent != null) {
                            agents.add(agent)
                        }
                    } catch (e: Exception) {
                        // Log but continue loading other agents
                        e.printStackTrace()
                    }
                }
        } catch (e: Exception) {
            // Assets directory may not exist
            e.printStackTrace()
        }
        
        agents
    }
    
    /**
     * Load agents from data directory.
     */
    private suspend fun loadFromDataDir(): List<Agent> = withContext(Dispatchers.IO) {
        val agents = mutableListOf<Agent>()
        val dataDir = File(AGENTS_DATA_PATH)
        
        if (!dataDir.exists() || !dataDir.isDirectory) {
            return@withContext agents
        }
        
        dataDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".yml") || it.name.endsWith(".yaml")) }
            ?.forEach { file ->
                try {
                    val content = file.readText()
                    val agent = parseAgentYaml(content, file.name)
                    if (agent != null) {
                        agents.add(agent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        
        agents
    }
    
    /**
     * Parse agent YAML content.
     * Simple parser that handles the agent definition format.
     */
    private fun parseAgentYaml(content: String, filename: String): Agent? {
        val lines = content.split("\n")
        
        var name: String? = null
        var description = ""
        var version = "1.0.0"
        val capabilities = mutableSetOf<Capability>()
        val skills = mutableListOf<String>()
        var memoryBackend = MemoryBackend.JSON
        
        var currentSection: String? = null
        
        for (line in lines) {
            val trimmed = line.trim()
            
            // Skip comments and empty lines
            if (trimmed.startsWith("#") || trimmed.isEmpty()) continue
            
            // Check for section headers
            when {
                trimmed.startsWith("name:") -> {
                    name = trimmed.removePrefix("name:").trim().trim('"', '\'')
                }
                trimmed.startsWith("description:") -> {
                    description = trimmed.removePrefix("description:").trim().trim('"', '\'')
                }
                trimmed.startsWith("version:") -> {
                    version = trimmed.removePrefix("version:").trim().trim('"', '\'')
                }
                trimmed.startsWith("memory_backend:") -> {
                    val backend = trimmed.removePrefix("memory_backend:").trim().lowercase()
                    memoryBackend = when (backend) {
                        "datastore" -> MemoryBackend.DATASTORE
                        else -> MemoryBackend.JSON
                    }
                }
                trimmed == "capabilities:" -> {
                    currentSection = "capabilities"
                }
                trimmed == "skills:" -> {
                    currentSection = "skills"
                }
                trimmed.startsWith("-") && currentSection != null -> {
                    val item = trimmed.removePrefix("-").trim().trim('"', '\'')
                    when (currentSection) {
                        "capabilities" -> {
                            parseCapability(item)?.let { capabilities.add(it) }
                        }
                        "skills" -> {
                            skills.add(item)
                        }
                    }
                }
                else -> {
                    // Non-list item, reset section
                    if (!line.startsWith(" ") && !line.startsWith("\t")) {
                        currentSection = null
                    }
                }
            }
        }
        
        if (name == null) {
            // Try to derive name from filename
            name = filename.removeSuffix(".yml").removeSuffix(".yaml")
        }
        
        return Agent(
            name = name,
            description = description,
            version = version,
            capabilities = capabilities,
            skills = skills,
            memoryBackend = memoryBackend
        )
    }
    
    /**
     * Parse a capability string into a Capability object.
     */
    private fun parseCapability(capString: String): Capability? {
        val normalized = capString.lowercase().trim()
        
        return when {
            // Filesystem capabilities
            normalized == "fs.read" || normalized == "filesystem.read" -> Capability.Filesystem.Read
            normalized == "fs.write" || normalized == "filesystem.write" -> Capability.Filesystem.Write
            normalized == "fs.exec" || normalized == "filesystem.exec" -> Capability.Filesystem.Exec
            normalized == "fs.delete" || normalized == "filesystem.delete" -> Capability.Filesystem.Delete
            
            // Network capabilities
            normalized == "net.none" || normalized == "network.none" -> Capability.Network.None
            normalized == "net.local" || normalized == "network.local" -> Capability.Network.Local
            normalized == "net.external" || normalized == "network.external" -> Capability.Network.External
            
            // Exec capabilities
            normalized == "exec.pkg" -> Capability.Exec.Pkg
            normalized == "exec.git" -> Capability.Exec.Git
            normalized == "exec.shell" -> Capability.Exec.Shell
            normalized == "exec.python" -> Capability.Exec.Python
            normalized == "exec.curl" -> Capability.Exec.Curl
            normalized == "exec.wget" -> Capability.Exec.Wget
            normalized == "exec.ssh" -> Capability.Exec.Ssh
            normalized == "exec.tar" -> Capability.Exec.Tar
            normalized == "exec.unzip" -> Capability.Exec.Unzip
            
            // Memory capabilities
            normalized == "mem.read" || normalized == "memory.read" -> Capability.Memory.Read
            normalized == "mem.write" || normalized == "memory.write" -> Capability.Memory.Write
            normalized == "mem.shared" || normalized == "memory.shared" -> Capability.Memory.Shared
            
            // System capabilities
            normalized == "sys.info" || normalized == "system.info" -> Capability.System.Info
            normalized == "sys.proc" || normalized == "system.proc" -> Capability.System.Proc
            normalized == "sys.env" || normalized == "system.env" -> Capability.System.Env
            
            else -> null
        }
    }
    
    /**
     * Get a registered agent by name.
     */
    suspend fun getAgent(name: String): Agent? = mutex.withLock {
        agents[name]
    }
    
    /**
     * Get all registered agents.
     */
    suspend fun getAllAgents(): List<Agent> = mutex.withLock {
        agents.values.toList()
    }
    
    /**
     * Get agent state.
     */
    suspend fun getAgentState(name: String): AgentState? = mutex.withLock {
        agentStates[name]
    }
    
    /**
     * Update agent state.
     */
    suspend fun setAgentState(name: String, state: AgentState) = mutex.withLock {
        if (agents.containsKey(name)) {
            agentStates[name] = state
        }
    }
    
    /**
     * Validate an agent's configuration.
     * Checks that all required skills are available.
     */
    suspend fun validateAgent(
        name: String,
        availableSkills: Set<String>
    ): ValidationResult = mutex.withLock {
        val agent = agents[name]
            ?: return@withLock ValidationResult(
                valid = false,
                errors = listOf("Agent not found: $name")
            )
        
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Check required skills
        val missingSkills = agent.skills.filter { !availableSkills.contains(it) }
        if (missingSkills.isNotEmpty()) {
            errors.add("Missing skills: ${missingSkills.joinToString(", ")}")
        }
        
        // Check capabilities are valid
        if (agent.capabilities.isEmpty()) {
            warnings.add("Agent has no capabilities defined")
        }
        
        val valid = errors.isEmpty()
        if (valid) {
            agentStates[name] = AgentState.VALIDATED
        } else {
            agentStates[name] = AgentState.ERROR
        }
        
        ValidationResult(
            valid = valid,
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Register a new agent dynamically.
     */
    suspend fun registerAgent(agent: Agent) = mutex.withLock {
        agents[agent.name] = agent
        agentStates[agent.name] = AgentState.REGISTERED
    }
    
    /**
     * Unregister an agent.
     */
    suspend fun unregisterAgent(name: String) = mutex.withLock {
        agents.remove(name)
        agentStates.remove(name)
    }
    
    /**
     * Get active agents.
     */
    suspend fun getActiveAgents(): List<Agent> = mutex.withLock {
        agents.filter { agentStates[it.key] == AgentState.ACTIVE }
            .values.toList()
    }
    
    /**
     * Get validated agents.
     */
    suspend fun getValidatedAgents(): List<Agent> = mutex.withLock {
        agents.filter { 
            agentStates[it.key] == AgentState.VALIDATED || 
            agentStates[it.key] == AgentState.ACTIVE 
        }.values.toList()
    }
}

/**
 * Result of agent validation.
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
