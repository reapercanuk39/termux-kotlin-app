package com.termux.app.agents.skills

import com.termux.app.agents.models.Capability

/**
 * Git operations skill for agents.
 * Provides clone, status, commit, diff, and other git operations.
 */
class GitSkill : BaseSkill() {
    override val name = "git"
    override val description = "Git version control operations"
    override val provides = listOf(
        "clone",
        "status",
        "add",
        "commit",
        "push",
        "pull",
        "diff",
        "log",
        "branch",
        "checkout",
        "init",
        "remote_info"
    )
    override val requiredCapabilities = setOf(
        Capability.Exec.Git,
        Capability.Filesystem.Read,
        Capability.Filesystem.Write,
        Capability.Network.External
    )
    
    override suspend fun executeFunction(
        function: String,
        params: Map<String, Any?>
    ): SkillResult {
        return when (function) {
            "clone" -> clone(
                params["url"] as? String ?: "",
                params["dest"] as? String
            )
            "status" -> status(params["path"] as? String ?: ".")
            "add" -> add(
                params["path"] as? String ?: ".",
                params["files"] as? List<*>
            )
            "commit" -> commit(
                params["path"] as? String ?: ".",
                params["message"] as? String ?: ""
            )
            "push" -> push(
                params["path"] as? String ?: ".",
                params["remote"] as? String ?: "origin",
                params["branch"] as? String
            )
            "pull" -> pull(
                params["path"] as? String ?: ".",
                params["remote"] as? String ?: "origin",
                params["branch"] as? String
            )
            "diff" -> diff(
                params["path"] as? String ?: ".",
                params["staged"] as? Boolean ?: false
            )
            "log" -> log(
                params["path"] as? String ?: ".",
                params["limit"] as? Int ?: 10
            )
            "branch" -> branch(
                params["path"] as? String ?: ".",
                params["name"] as? String
            )
            "checkout" -> checkout(
                params["path"] as? String ?: ".",
                params["ref"] as? String ?: ""
            )
            "init" -> init(params["path"] as? String ?: ".")
            "remote_info" -> remoteInfo(params["path"] as? String ?: ".")
            else -> SkillResult(success = false, error = "Unknown function: $function")
        }
    }
    
    private suspend fun clone(url: String, dest: String?): SkillResult {
        if (url.isBlank()) {
            return SkillResult(success = false, error = "URL required")
        }
        
        log("Cloning: $url")
        
        val args = mutableListOf("git", "clone", "--progress")
        args.add(url)
        if (dest != null) args.add(dest)
        
        val result = runCommand(args, timeout = 600_000L)
        
        log("Clone ${if (result.isSuccess) "succeeded" else "failed"}")
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "url" to url,
                "dest" to (dest ?: url.substringAfterLast("/").removeSuffix(".git")),
                "cloned" to result.isSuccess,
                "output" to result.stdout
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    private suspend fun status(path: String): SkillResult {
        log("Getting status for: $path")
        
        val result = runCommand(listOf("git", "-C", path, "status", "--porcelain", "-b"))
        
        val lines = result.stdout.trim().split("\n")
        val branchLine = lines.firstOrNull()?.removePrefix("## ") ?: ""
        
        val files = mutableListOf<Map<String, String>>()
        lines.drop(1).forEach { line ->
            if (line.length >= 3) {
                val status = line.substring(0, 2)
                val filename = line.substring(3)
                files.add(mapOf(
                    "status" to status.trim(),
                    "file" to filename
                ))
            }
        }
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "path" to path,
                "branch" to branchLine,
                "files" to files,
                "clean" to files.isEmpty()
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun add(path: String, files: List<*>?): SkillResult {
        log("Adding files in: $path")
        
        val args = mutableListOf("git", "-C", path, "add")
        if (files.isNullOrEmpty()) {
            args.add("-A")
        } else {
            args.addAll(files.filterIsInstance<String>())
        }
        
        val result = runCommand(args)
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "path" to path,
                "added" to result.isSuccess
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    private suspend fun commit(path: String, message: String): SkillResult {
        if (message.isBlank()) {
            return SkillResult(success = false, error = "Commit message required")
        }
        
        log("Committing in: $path")
        
        val result = runCommand(listOf("git", "-C", path, "commit", "-m", message))
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "path" to path,
                "message" to message,
                "committed" to result.isSuccess,
                "output" to result.stdout
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    private suspend fun push(path: String, remote: String, branch: String?): SkillResult {
        log("Pushing to $remote")
        
        val args = mutableListOf("git", "-C", path, "push", remote)
        if (branch != null) args.add(branch)
        
        val result = runCommand(args, timeout = 120_000L)
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "path" to path,
                "remote" to remote,
                "branch" to (branch ?: "current"),
                "pushed" to result.isSuccess,
                "output" to result.stdout
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    private suspend fun pull(path: String, remote: String, branch: String?): SkillResult {
        log("Pulling from $remote")
        
        val args = mutableListOf("git", "-C", path, "pull", remote)
        if (branch != null) args.add(branch)
        
        val result = runCommand(args, timeout = 120_000L)
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "path" to path,
                "remote" to remote,
                "branch" to (branch ?: "current"),
                "pulled" to result.isSuccess,
                "output" to result.stdout
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    private suspend fun diff(path: String, staged: Boolean): SkillResult {
        log("Getting diff for: $path")
        
        val args = mutableListOf("git", "-C", path, "diff")
        if (staged) args.add("--staged")
        
        val result = runCommand(args)
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "path" to path,
                "staged" to staged,
                "diff" to result.stdout
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun log(path: String, limit: Int): SkillResult {
        log("Getting log for: $path")
        
        val result = runCommand(listOf(
            "git", "-C", path, "log",
            "--pretty=format:%H|%an|%ae|%at|%s",
            "-n", limit.toString()
        ))
        
        val commits = result.stdout.trim().split("\n")
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("|")
                mapOf(
                    "hash" to (parts.getOrNull(0) ?: ""),
                    "author" to (parts.getOrNull(1) ?: ""),
                    "email" to (parts.getOrNull(2) ?: ""),
                    "timestamp" to (parts.getOrNull(3) ?: ""),
                    "message" to (parts.getOrNull(4) ?: "")
                )
            }
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "path" to path,
                "commits" to commits
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun branch(path: String, name: String?): SkillResult {
        val args = mutableListOf("git", "-C", path, "branch")
        if (name != null) args.add(name)
        
        val result = runCommand(args)
        
        if (name != null) {
            log("Created branch: $name")
            return SkillResult(
                success = result.isSuccess,
                data = mapOf(
                    "path" to path,
                    "branch" to name,
                    "created" to result.isSuccess
                ),
                error = if (!result.isSuccess) result.stderr else null,
                logs = context.getLogs()
            )
        }
        
        val branches = result.stdout.trim().split("\n").map { it.trimStart('*', ' ') }
        val current = result.stdout.split("\n")
            .find { it.startsWith("*") }
            ?.removePrefix("* ")
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "path" to path,
                "branches" to branches,
                "current" to current
            ),
            logs = context.getLogs()
        )
    }
    
    private suspend fun checkout(path: String, ref: String): SkillResult {
        if (ref.isBlank()) {
            return SkillResult(success = false, error = "Reference (branch/tag/commit) required")
        }
        
        log("Checking out: $ref")
        
        val result = runCommand(listOf("git", "-C", path, "checkout", ref))
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "path" to path,
                "ref" to ref,
                "checked_out" to result.isSuccess
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    private suspend fun init(path: String): SkillResult {
        log("Initializing repo in: $path")
        
        val result = runCommand(listOf("git", "init", path))
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "path" to path,
                "initialized" to result.isSuccess
            ),
            error = if (!result.isSuccess) result.stderr else null,
            logs = context.getLogs()
        )
    }
    
    private suspend fun remoteInfo(path: String): SkillResult {
        log("Getting remote info for: $path")
        
        val result = runCommand(listOf("git", "-C", path, "remote", "-v"))
        
        val remotes = mutableMapOf<String, Map<String, String>>()
        result.stdout.trim().split("\n").forEach { line ->
            if (line.isNotBlank()) {
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val name = parts[0]
                    val url = parts[1]
                    val type = if (parts.size >= 3) parts[2].trim('(', ')') else "unknown"
                    remotes[name] = mapOf("url" to url, "type" to type)
                }
            }
        }
        
        return SkillResult(
            success = result.isSuccess,
            data = mapOf(
                "path" to path,
                "remotes" to remotes
            ),
            logs = context.getLogs()
        )
    }
    
    override suspend fun selfTest(context: SkillContext): SkillResult {
        this.context = context
        log("Running git skill self-test")
        
        val result = runCommand(listOf("git", "--version"))
        val gitOk = result.isSuccess
        
        log("Git available: $gitOk")
        
        return SkillResult(
            success = gitOk,
            data = mapOf(
                "git_available" to gitOk,
                "git_version" to if (gitOk) result.stdout.trim() else null
            ),
            logs = context.getLogs()
        )
    }
}
