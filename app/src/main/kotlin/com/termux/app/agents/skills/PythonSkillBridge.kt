package com.termux.app.agents.skills

import com.termux.app.agents.models.Capability
import com.termux.app.agents.runtime.CommandRunner
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Bridge to Python skills for complex operations.
 * Falls back to Python subprocess when needed for skills like apk, qemu, iso.
 */
class PythonSkillBridge(
    private val skillName: String,
    private val commandRunner: CommandRunner
) : BaseSkill() {
    
    override val name = skillName
    override val description = "Python bridge for $skillName skill"
    override val provides = listOf<String>() // Discovered from Python skill
    override val requiredCapabilities = setOf<Capability>()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    companion object {
        private const val PYTHON_SKILLS_PATH = "/data/data/com.termux/files/usr/share/agents/skills"
        private val PYTHON_COMMANDS = listOf("python3", "python")
        
        /**
         * Check if Python is available in the environment.
         */
        suspend fun isPythonAvailable(commandRunner: CommandRunner): Boolean {
            return PYTHON_COMMANDS.any { python ->
                val result = commandRunner.run(listOf("which", python))
                result.isSuccess
            }
        }
        
        /**
         * Discover available Python skills.
         */
        suspend fun discoverSkills(commandRunner: CommandRunner): List<String> {
            val skillsDir = File(PYTHON_SKILLS_PATH)
            if (!skillsDir.exists() || !skillsDir.isDirectory) {
                return emptyList()
            }
            
            return skillsDir.listFiles()
                ?.filter { it.isDirectory && File(it, "skill.py").exists() }
                ?.map { it.name }
                ?: emptyList()
        }
    }
    
    private var pythonCommand: String? = null
    private var skillPath: File? = null
    private var skillProvides: List<String> = emptyList()
    
    /**
     * Initialize the bridge, discovering Python and skill capabilities.
     */
    suspend fun initialize(): Boolean {
        // Find Python
        for (python in PYTHON_COMMANDS) {
            val result = commandRunner.run(listOf("which", python))
            if (result.isSuccess) {
                pythonCommand = python
                break
            }
        }
        
        if (pythonCommand == null) {
            return false
        }
        
        // Find skill
        skillPath = File(PYTHON_SKILLS_PATH, skillName)
        if (!skillPath!!.exists()) {
            return false
        }
        
        // Read skill.yml to get provides
        val skillYml = File(skillPath!!, "skill.yml")
        if (skillYml.exists()) {
            try {
                val content = skillYml.readText()
                // Simple YAML parsing for provides list
                val providesMatch = Regex("provides:\\s*\\n((?:\\s+-\\s*.+\\n?)+)").find(content)
                if (providesMatch != null) {
                    skillProvides = providesMatch.groupValues[1]
                        .split("\n")
                        .mapNotNull { line ->
                            val match = Regex("\\s*-\\s*(.+)").find(line)
                            match?.groupValues?.get(1)?.trim()
                        }
                }
            } catch (e: Exception) {
                // Ignore, use empty provides list
            }
        }
        
        return true
    }
    
    override suspend fun executeFunction(
        function: String,
        params: Map<String, Any?>
    ): SkillResult {
        if (pythonCommand == null || skillPath == null) {
            val initialized = initialize()
            if (!initialized) {
                return SkillResult(
                    success = false,
                    error = "Python skill bridge not available for: $skillName"
                )
            }
        }
        
        log("Calling Python skill: $skillName.$function")
        
        // Create task JSON
        val task = mapOf(
            "skill" to skillName,
            "function" to function,
            "params" to params,
            "context" to mapOf(
                "agent" to context.agentName,
                "sandbox" to context.sandbox.sandboxDir.absolutePath,
                "work_dir" to context.sandbox.workDir.absolutePath
            )
        )
        
        val taskJson = json.encodeToString(task)
        
        // Write task to temp file (for stdin)
        val taskFile = context.sandbox.getTempFile("task_${System.currentTimeMillis()}.json")
        taskFile.writeText(taskJson)
        
        try {
            // Run Python skill
            val result = commandRunner.run(
                command = listOf(
                    pythonCommand!!,
                    "-c",
                    """
                    import sys
                    import json
                    sys.path.insert(0, '${skillPath!!.absolutePath}')
                    from skill import *
                    
                    task = json.load(open('${taskFile.absolutePath}'))
                    func = task['function']
                    params = task['params']
                    
                    try:
                        result = globals()[func](**params)
                        print(json.dumps({'success': True, 'data': result}))
                    except Exception as e:
                        print(json.dumps({'success': False, 'error': str(e)}))
                    """.trimIndent()
                ),
                workingDir = skillPath!!,
                timeout = 300_000L // 5 minute timeout for complex skills
            )
            
            // Parse result
            val output = result.stdout.trim()
            
            return try {
                val jsonResult = json.parseToJsonElement(output)
                val success = jsonResult.jsonObject["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
                
                if (success) {
                    val data = jsonResult.jsonObject["data"]
                    SkillResult(
                        success = true,
                        data = parseJsonToMap(data) ?: emptyMap(),
                        logs = context.getLogs()
                    )
                } else {
                    val error = jsonResult.jsonObject["error"]?.jsonPrimitive?.content
                    SkillResult(
                        success = false,
                        error = error ?: "Unknown Python error",
                        logs = context.getLogs()
                    )
                }
            } catch (e: Exception) {
                SkillResult(
                    success = false,
                    error = "Failed to parse Python output: ${e.message}",
                    data = mapOf(
                        "stdout" to result.stdout,
                        "stderr" to result.stderr
                    ),
                    logs = context.getLogs()
                )
            }
        } finally {
            taskFile.delete()
        }
    }
    
    private fun parseJsonToMap(element: JsonElement?): Map<String, Any?>? {
        if (element == null) return null
        
        return try {
            when {
                element.jsonObject != null -> {
                    element.jsonObject.mapValues { (_, v) ->
                        when {
                            v.jsonPrimitive != null -> v.jsonPrimitive.content
                            else -> v.toString()
                        }
                    }
                }
                else -> mapOf("value" to element.toString())
            }
        } catch (e: Exception) {
            mapOf("raw" to element.toString())
        }
    }
    
    override suspend fun selfTest(context: SkillContext): SkillResult {
        this.context = context
        log("Running Python skill bridge self-test for: $skillName")
        
        val initialized = initialize()
        
        return SkillResult(
            success = initialized,
            data = mapOf(
                "python_available" to (pythonCommand != null),
                "python_command" to pythonCommand,
                "skill_path" to skillPath?.absolutePath,
                "provides" to skillProvides
            ),
            logs = context.getLogs()
        )
    }
}
