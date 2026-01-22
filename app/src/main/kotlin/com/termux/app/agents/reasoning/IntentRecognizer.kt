package com.termux.app.agents.reasoning

import com.termux.app.agents.llm.ChatMessage
import com.termux.app.agents.llm.LlmOptions
import com.termux.app.agents.llm.LlmProvider
import com.termux.shared.logger.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recognized user intent.
 */
sealed class UserIntent {
    abstract val confidence: Float
    abstract val rawQuery: String
    
    /**
     * System repair intent - user wants to fix/diagnose system issues.
     */
    data class SystemRepair(
        override val confidence: Float,
        override val rawQuery: String,
        val repairType: RepairType,
        val specificIssue: String? = null
    ) : UserIntent()
    
    /**
     * Package management intent.
     */
    data class PackageManagement(
        override val confidence: Float,
        override val rawQuery: String,
        val action: String,  // install, remove, update
        val packageName: String?
    ) : UserIntent()
    
    /**
     * File operation intent.
     */
    data class FileOperation(
        override val confidence: Float,
        override val rawQuery: String,
        val action: String,  // copy, move, delete, backup
        val path: String?
    ) : UserIntent()
    
    /**
     * General query - not a specific intent.
     */
    data class General(
        override val confidence: Float,
        override val rawQuery: String
    ) : UserIntent()
    
    /**
     * Unknown/unclear intent.
     */
    data class Unknown(
        override val confidence: Float = 0f,
        override val rawQuery: String
    ) : UserIntent()
}

/**
 * Types of system repair.
 */
enum class RepairType {
    FULL_SYSTEM_CHECK,    // Complete system diagnosis
    BUSYBOX_REPAIR,       // BusyBox specific issues
    SYMLINK_REPAIR,       // Symlink issues
    PATH_REPAIR,          // PATH configuration
    MAGISK_CHECK,         // Magisk integration
    ENVIRONMENT_CHECK,    // Environment variables
    UNKNOWN
}

/**
 * Intent Recognizer.
 * 
 * Analyzes natural language queries to determine user intent.
 * Uses pattern matching first, falls back to LLM for ambiguous cases.
 */
@Singleton
class IntentRecognizer @Inject constructor(
    private val llmProvider: LlmProvider
) {
    companion object {
        private const val LOG_TAG = "IntentRecognizer"
        
        // System repair keywords and patterns
        private val SYSTEM_REPAIR_KEYWORDS = listOf(
            "fix", "repair", "diagnose", "check", "broken", "issue", "problem",
            "not working", "doesn't work", "error", "fail", "crash"
        )
        
        private val BUSYBOX_KEYWORDS = listOf(
            "busybox", "applet", "busybox-modern", "busy box"
        )
        
        private val SYMLINK_KEYWORDS = listOf(
            "symlink", "link", "symbolic link", "ln"
        )
        
        private val PATH_KEYWORDS = listOf(
            "path", "environment", "env", "PATH", "\$PATH"
        )
        
        private val MAGISK_KEYWORDS = listOf(
            "magisk", "root", "su", "superuser", "module"
        )
        
        private val PACKAGE_KEYWORDS = listOf(
            "install", "uninstall", "remove", "update", "upgrade", "package", "pkg"
        )
        
        private val FILE_KEYWORDS = listOf(
            "copy", "move", "delete", "backup", "restore", "file", "directory", "folder"
        )
    }
    
    /**
     * Recognize intent from natural language query.
     * 
     * @param query User's natural language request
     * @param useLlm Whether to use LLM for ambiguous cases
     * @return Recognized intent with confidence score
     */
    suspend fun recognize(query: String, useLlm: Boolean = true): UserIntent {
        val normalizedQuery = query.lowercase().trim()
        
        Logger.logDebug(LOG_TAG, "Recognizing intent for: $query")
        
        // Try pattern-based recognition first (fast, no LLM needed)
        val patternIntent = recognizeByPatterns(normalizedQuery, query)
        
        // If confidence is high enough, return pattern-based result
        if (patternIntent.confidence >= 0.7f) {
            Logger.logInfo(LOG_TAG, "Pattern match: ${patternIntent::class.simpleName} (${patternIntent.confidence})")
            return patternIntent
        }
        
        // For low-confidence or ambiguous cases, use LLM if available
        if (useLlm && llmProvider.isAvailable()) {
            val llmIntent = recognizeByLlm(query)
            if (llmIntent.confidence > patternIntent.confidence) {
                Logger.logInfo(LOG_TAG, "LLM match: ${llmIntent::class.simpleName} (${llmIntent.confidence})")
                return llmIntent
            }
        }
        
        return patternIntent
    }
    
    /**
     * Pattern-based intent recognition (fast, no LLM).
     */
    private fun recognizeByPatterns(normalizedQuery: String, originalQuery: String): UserIntent {
        // Check for system repair intent
        val repairScore = calculateKeywordScore(normalizedQuery, SYSTEM_REPAIR_KEYWORDS)
        val busyboxScore = calculateKeywordScore(normalizedQuery, BUSYBOX_KEYWORDS)
        val symlinkScore = calculateKeywordScore(normalizedQuery, SYMLINK_KEYWORDS)
        val pathScore = calculateKeywordScore(normalizedQuery, PATH_KEYWORDS)
        val magiskScore = calculateKeywordScore(normalizedQuery, MAGISK_KEYWORDS)
        
        // System repair detection
        val systemRepairScore = repairScore + (busyboxScore * 0.5f) + (symlinkScore * 0.3f) + 
                                (pathScore * 0.3f) + (magiskScore * 0.3f)
        
        if (systemRepairScore >= 0.5f) {
            val repairType = when {
                busyboxScore >= 0.5f -> RepairType.BUSYBOX_REPAIR
                symlinkScore >= 0.5f -> RepairType.SYMLINK_REPAIR
                pathScore >= 0.5f -> RepairType.PATH_REPAIR
                magiskScore >= 0.5f -> RepairType.MAGISK_CHECK
                else -> RepairType.FULL_SYSTEM_CHECK
            }
            
            return UserIntent.SystemRepair(
                confidence = (systemRepairScore).coerceAtMost(1.0f),
                rawQuery = originalQuery,
                repairType = repairType,
                specificIssue = extractSpecificIssue(normalizedQuery)
            )
        }
        
        // Check for package management
        val packageScore = calculateKeywordScore(normalizedQuery, PACKAGE_KEYWORDS)
        if (packageScore >= 0.4f) {
            val action = when {
                normalizedQuery.contains("install") -> "install"
                normalizedQuery.contains("uninstall") || normalizedQuery.contains("remove") -> "remove"
                normalizedQuery.contains("update") || normalizedQuery.contains("upgrade") -> "update"
                else -> "install"
            }
            return UserIntent.PackageManagement(
                confidence = packageScore,
                rawQuery = originalQuery,
                action = action,
                packageName = extractPackageName(normalizedQuery)
            )
        }
        
        // Check for file operations
        val fileScore = calculateKeywordScore(normalizedQuery, FILE_KEYWORDS)
        if (fileScore >= 0.4f) {
            val action = when {
                normalizedQuery.contains("copy") -> "copy"
                normalizedQuery.contains("move") -> "move"
                normalizedQuery.contains("delete") -> "delete"
                normalizedQuery.contains("backup") -> "backup"
                normalizedQuery.contains("restore") -> "restore"
                else -> "copy"
            }
            return UserIntent.FileOperation(
                confidence = fileScore,
                rawQuery = originalQuery,
                action = action,
                path = extractPath(normalizedQuery)
            )
        }
        
        // Low confidence - general intent
        return UserIntent.General(
            confidence = 0.3f,
            rawQuery = originalQuery
        )
    }
    
    /**
     * LLM-based intent recognition.
     */
    private suspend fun recognizeByLlm(query: String): UserIntent {
        val systemPrompt = """You are an intent classifier for a terminal/shell environment.
Classify the user's request into one of these categories:
1. SYSTEM_REPAIR - fixing system issues, diagnosing problems, repairing busybox/symlinks/path
2. PACKAGE_MANAGEMENT - installing, removing, or updating packages
3. FILE_OPERATION - copying, moving, deleting, or backing up files
4. GENERAL - general questions or commands

Respond with ONLY a JSON object:
{
  "intent": "SYSTEM_REPAIR|PACKAGE_MANAGEMENT|FILE_OPERATION|GENERAL",
  "confidence": 0.0-1.0,
  "details": {
    "action": "specific action if applicable",
    "target": "target package/file/issue if mentioned"
  }
}"""

        val result = llmProvider.complete(
            prompt = "Classify this request: \"$query\"",
            systemPrompt = systemPrompt,
            options = LlmOptions(maxTokens = 150, temperature = 0.1f)
        )
        
        return result.fold(
            onSuccess = { response ->
                parseIntentFromLlm(response.content, query)
            },
            onFailure = { error ->
                Logger.logWarn(LOG_TAG, "LLM intent recognition failed: ${error.message}")
                UserIntent.Unknown(rawQuery = query)
            }
        )
    }
    
    /**
     * Parse LLM response into UserIntent.
     */
    private fun parseIntentFromLlm(response: String, query: String): UserIntent {
        try {
            // Extract JSON from response
            val jsonMatch = Regex("\\{[^}]+\\}").find(response)
            val json = jsonMatch?.value ?: return UserIntent.Unknown(rawQuery = query)
            
            // Simple JSON parsing
            val intentMatch = Regex("\"intent\"\\s*:\\s*\"([^\"]+)\"").find(json)
            val confidenceMatch = Regex("\"confidence\"\\s*:\\s*([0-9.]+)").find(json)
            val actionMatch = Regex("\"action\"\\s*:\\s*\"([^\"]+)\"").find(json)
            val targetMatch = Regex("\"target\"\\s*:\\s*\"([^\"]+)\"").find(json)
            
            val intentType = intentMatch?.groupValues?.get(1) ?: return UserIntent.Unknown(rawQuery = query)
            val confidence = confidenceMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0.5f
            val action = actionMatch?.groupValues?.get(1)
            val target = targetMatch?.groupValues?.get(1)
            
            return when (intentType.uppercase()) {
                "SYSTEM_REPAIR" -> UserIntent.SystemRepair(
                    confidence = confidence,
                    rawQuery = query,
                    repairType = parseRepairType(action ?: target),
                    specificIssue = target
                )
                "PACKAGE_MANAGEMENT" -> UserIntent.PackageManagement(
                    confidence = confidence,
                    rawQuery = query,
                    action = action ?: "install",
                    packageName = target
                )
                "FILE_OPERATION" -> UserIntent.FileOperation(
                    confidence = confidence,
                    rawQuery = query,
                    action = action ?: "copy",
                    path = target
                )
                else -> UserIntent.General(
                    confidence = confidence,
                    rawQuery = query
                )
            }
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "Failed to parse LLM response: ${e.message}")
            return UserIntent.Unknown(rawQuery = query)
        }
    }
    
    private fun parseRepairType(hint: String?): RepairType {
        if (hint == null) return RepairType.FULL_SYSTEM_CHECK
        
        val lower = hint.lowercase()
        return when {
            lower.contains("busybox") -> RepairType.BUSYBOX_REPAIR
            lower.contains("symlink") -> RepairType.SYMLINK_REPAIR
            lower.contains("path") -> RepairType.PATH_REPAIR
            lower.contains("magisk") || lower.contains("root") -> RepairType.MAGISK_CHECK
            lower.contains("env") -> RepairType.ENVIRONMENT_CHECK
            else -> RepairType.FULL_SYSTEM_CHECK
        }
    }
    
    private fun calculateKeywordScore(text: String, keywords: List<String>): Float {
        val matches = keywords.count { text.contains(it) }
        return (matches.toFloat() / keywords.size.toFloat().coerceAtLeast(1f)).coerceAtMost(1.0f)
    }
    
    private fun extractSpecificIssue(text: String): String? {
        // Try to extract what's broken
        val patterns = listOf(
            Regex("(\\w+)\\s+(is|are)\\s+(broken|missing|not working)"),
            Regex("(fix|repair)\\s+(the\\s+)?(\\w+)"),
            Regex("(\\w+)\\s+issue"),
            Regex("(\\w+)\\s+problem")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues.last()
            }
        }
        return null
    }
    
    private fun extractPackageName(text: String): String? {
        // Simple extraction after "install" or "remove"
        val pattern = Regex("(install|uninstall|remove|update)\\s+(\\S+)")
        val match = pattern.find(text)
        return match?.groupValues?.get(2)
    }
    
    private fun extractPath(text: String): String? {
        // Look for path-like patterns
        val pattern = Regex("(/[\\w/.-]+|~/[\\w/.-]+)")
        val match = pattern.find(text)
        return match?.value
    }
    
    /**
     * Check if a query is a system repair request (fast check, no LLM).
     */
    fun isSystemRepairRequest(query: String): Boolean {
        val lower = query.lowercase()
        val hasRepairKeyword = SYSTEM_REPAIR_KEYWORDS.any { lower.contains(it) }
        val hasSystemKeyword = BUSYBOX_KEYWORDS.any { lower.contains(it) } ||
                               SYMLINK_KEYWORDS.any { lower.contains(it) } ||
                               PATH_KEYWORDS.any { lower.contains(it) } ||
                               MAGISK_KEYWORDS.any { lower.contains(it) } ||
                               lower.contains("system") ||
                               lower.contains("environment")
        return hasRepairKeyword && hasSystemKeyword
    }
}
