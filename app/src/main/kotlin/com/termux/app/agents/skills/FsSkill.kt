package com.termux.app.agents.skills

import com.termux.app.agents.models.Capability
import java.io.File

/**
 * Filesystem skill for agents.
 * Provides file and directory operations.
 */
class FsSkill : BaseSkill() {
    override val name = "fs"
    override val description = "Filesystem operations skill"
    override val provides = listOf(
        "list_dir",
        "read_file",
        "write_file",
        "delete_file",
        "copy_file",
        "move_file",
        "mkdir",
        "exists",
        "get_info",
        "find_files",
        "grep"
    )
    override val requiredCapabilities = setOf(
        Capability.Filesystem.Read,
        Capability.Filesystem.Write,
        Capability.Filesystem.Delete
    )
    
    override suspend fun executeFunction(
        function: String,
        params: Map<String, Any?>
    ): SkillResult {
        return when (function) {
            "list_dir" -> listDir(params["path"] as? String ?: ".")
            "read_file" -> readFile(params["path"] as? String ?: "")
            "write_file" -> writeFile(
                params["path"] as? String ?: "",
                params["content"] as? String ?: ""
            )
            "delete_file" -> deleteFile(params["path"] as? String ?: "")
            "copy_file" -> copyFile(
                params["source"] as? String ?: "",
                params["dest"] as? String ?: ""
            )
            "move_file" -> moveFile(
                params["source"] as? String ?: "",
                params["dest"] as? String ?: ""
            )
            "mkdir" -> mkdir(params["path"] as? String ?: "")
            "exists" -> exists(params["path"] as? String ?: "")
            "get_info" -> getInfo(params["path"] as? String ?: "")
            "find_files" -> findFiles(
                params["path"] as? String ?: ".",
                params["pattern"] as? String ?: "*"
            )
            "grep" -> grep(
                params["pattern"] as? String ?: "",
                params["path"] as? String ?: "."
            )
            else -> SkillResult(success = false, error = "Unknown function: $function")
        }
    }
    
    private fun resolvePath(path: String): File {
        return if (path.startsWith("/")) {
            File(path)
        } else {
            File(context.sandbox.workDir, path)
        }
    }
    
    private suspend fun listDir(path: String): SkillResult {
        log("Listing directory: $path")
        val dir = resolvePath(path)
        
        if (!dir.exists()) {
            return SkillResult(success = false, error = "Directory not found: $path")
        }
        if (!dir.isDirectory) {
            return SkillResult(success = false, error = "Not a directory: $path")
        }
        
        val entries = dir.listFiles()?.map { file ->
            mapOf(
                "name" to file.name,
                "type" to if (file.isDirectory) "directory" else "file",
                "size" to file.length(),
                "modified" to file.lastModified()
            )
        } ?: emptyList()
        
        return SkillResult(
            success = true,
            data = mapOf(
                "path" to dir.absolutePath,
                "count" to entries.size,
                "entries" to entries
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun readFile(path: String): SkillResult {
        if (path.isBlank()) {
            return SkillResult(success = false, error = "Path required")
        }
        
        log("Reading file: $path")
        val file = resolvePath(path)
        
        if (!file.exists()) {
            return SkillResult(success = false, error = "File not found: $path")
        }
        if (!file.isFile) {
            return SkillResult(success = false, error = "Not a file: $path")
        }
        
        // Limit file size to 1MB for safety
        if (file.length() > 1_000_000) {
            return SkillResult(success = false, error = "File too large (max 1MB)")
        }
        
        val content = file.readText()
        
        return SkillResult(
            success = true,
            data = mapOf(
                "path" to file.absolutePath,
                "size" to file.length(),
                "content" to content
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun writeFile(path: String, content: String): SkillResult {
        if (path.isBlank()) {
            return SkillResult(success = false, error = "Path required")
        }
        
        log("Writing file: $path")
        val file = resolvePath(path)
        
        try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            
            return SkillResult(
                success = true,
                data = mapOf(
                    "path" to file.absolutePath,
                    "size" to file.length(),
                    "written" to true
                ),
                logs = context.getLogs()
            )
        } catch (e: Exception) {
            return SkillResult(success = false, error = "Write failed: ${e.message}")
        }
    }
    
    private suspend fun deleteFile(path: String): SkillResult {
        if (path.isBlank()) {
            return SkillResult(success = false, error = "Path required")
        }
        
        log("Deleting: $path")
        val file = resolvePath(path)
        
        if (!file.exists()) {
            return SkillResult(success = false, error = "Path not found: $path")
        }
        
        val deleted = if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
        
        return SkillResult(
            success = deleted,
            data = mapOf(
                "path" to file.absolutePath,
                "deleted" to deleted
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun copyFile(source: String, dest: String): SkillResult {
        if (source.isBlank() || dest.isBlank()) {
            return SkillResult(success = false, error = "Source and dest required")
        }
        
        log("Copying: $source -> $dest")
        val srcFile = resolvePath(source)
        val destFile = resolvePath(dest)
        
        if (!srcFile.exists()) {
            return SkillResult(success = false, error = "Source not found: $source")
        }
        
        try {
            srcFile.copyTo(destFile, overwrite = true)
            
            return SkillResult(
                success = true,
                data = mapOf(
                    "source" to srcFile.absolutePath,
                    "dest" to destFile.absolutePath,
                    "copied" to true
                ),
                logs = context.getLogs()
            )
        } catch (e: Exception) {
            return SkillResult(success = false, error = "Copy failed: ${e.message}")
        }
    }
    
    private suspend fun moveFile(source: String, dest: String): SkillResult {
        if (source.isBlank() || dest.isBlank()) {
            return SkillResult(success = false, error = "Source and dest required")
        }
        
        log("Moving: $source -> $dest")
        val srcFile = resolvePath(source)
        val destFile = resolvePath(dest)
        
        if (!srcFile.exists()) {
            return SkillResult(success = false, error = "Source not found: $source")
        }
        
        try {
            srcFile.renameTo(destFile)
            
            return SkillResult(
                success = true,
                data = mapOf(
                    "source" to srcFile.absolutePath,
                    "dest" to destFile.absolutePath,
                    "moved" to true
                ),
                logs = context.getLogs()
            )
        } catch (e: Exception) {
            return SkillResult(success = false, error = "Move failed: ${e.message}")
        }
    }
    
    private suspend fun mkdir(path: String): SkillResult {
        if (path.isBlank()) {
            return SkillResult(success = false, error = "Path required")
        }
        
        log("Creating directory: $path")
        val dir = resolvePath(path)
        
        val created = dir.mkdirs()
        
        return SkillResult(
            success = created || dir.exists(),
            data = mapOf(
                "path" to dir.absolutePath,
                "created" to created
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun exists(path: String): SkillResult {
        val file = resolvePath(path)
        
        return SkillResult(
            success = true,
            data = mapOf(
                "path" to file.absolutePath,
                "exists" to file.exists(),
                "is_file" to file.isFile,
                "is_directory" to file.isDirectory
            )
        )
    }
    
    private suspend fun getInfo(path: String): SkillResult {
        if (path.isBlank()) {
            return SkillResult(success = false, error = "Path required")
        }
        
        val file = resolvePath(path)
        
        if (!file.exists()) {
            return SkillResult(success = false, error = "Path not found: $path")
        }
        
        return SkillResult(
            success = true,
            data = mapOf(
                "path" to file.absolutePath,
                "name" to file.name,
                "type" to if (file.isDirectory) "directory" else "file",
                "size" to file.length(),
                "modified" to file.lastModified(),
                "readable" to file.canRead(),
                "writable" to file.canWrite(),
                "executable" to file.canExecute()
            )
        )
    }
    
    private suspend fun findFiles(path: String, pattern: String): SkillResult {
        log("Finding files in $path matching: $pattern")
        val result = runCommand(listOf("find", path, "-name", pattern, "-type", "f"), timeout = 30_000L)
        
        val files = result.stdout.trim().split("\n").filter { it.isNotBlank() }
        
        return SkillResult(
            success = true,
            data = mapOf(
                "path" to path,
                "pattern" to pattern,
                "count" to files.size,
                "files" to files
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun grep(pattern: String, path: String): SkillResult {
        if (pattern.isBlank()) {
            return SkillResult(success = false, error = "Pattern required")
        }
        
        log("Grepping for '$pattern' in $path")
        val result = runCommand(listOf("grep", "-r", "-l", pattern, path), timeout = 30_000L)
        
        val files = result.stdout.trim().split("\n").filter { it.isNotBlank() }
        
        return SkillResult(
            success = true,
            data = mapOf(
                "pattern" to pattern,
                "path" to path,
                "count" to files.size,
                "files" to files
            ),
            logs = context.getLogs()
        )
    }
    
    override suspend fun selfTest(context: SkillContext): SkillResult {
        this.context = context
        log("Running fs skill self-test")
        
        // Test creating and reading a file
        val testFile = context.sandbox.getTempFile("fs_test.txt")
        testFile.writeText("test content")
        val content = testFile.readText()
        testFile.delete()
        
        val success = content == "test content"
        log("Filesystem test: ${if (success) "passed" else "failed"}")
        
        return SkillResult(
            success = success,
            data = mapOf(
                "test_passed" to success,
                "sandbox_path" to context.sandbox.sandboxDir.absolutePath
            ),
            logs = context.getLogs()
        )
    }
}
