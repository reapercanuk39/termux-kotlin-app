package com.termux.app.data.repository

import com.termux.app.data.model.CommandHistoryEntry
import com.termux.app.di.ApplicationScope
import com.termux.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing command history.
 * Provides searchable history with Flow-based access.
 */
@Singleton
class CommandHistoryRepository @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val MAX_HISTORY_SIZE = 1000
    }

    private val _history = MutableStateFlow<List<CommandHistoryEntry>>(emptyList())
    val history: Flow<List<CommandHistoryEntry>> = _history.asStateFlow()

    // Recent commands (last 50)
    val recentCommands: Flow<List<String>> = _history.map { entries ->
        entries.take(50).map { it.command }
    }

    // Unique commands for autocomplete
    val uniqueCommands: Flow<List<String>> = _history.map { entries ->
        entries.map { it.command }.distinct().take(100)
    }

    private var nextId = 1L

    /**
     * Add a command to history
     */
    fun addCommand(
        command: String,
        sessionId: String? = null,
        exitCode: Int? = null,
        duration: Long? = null
    ) {
        if (command.isBlank()) return

        scope.launch(ioDispatcher) {
            val entry = CommandHistoryEntry(
                id = nextId++,
                command = command.trim(),
                sessionId = sessionId,
                exitCode = exitCode,
                duration = duration
            )

            _history.value = listOf(entry) + _history.value.take(MAX_HISTORY_SIZE - 1)
        }
    }

    /**
     * Search command history
     */
    fun searchHistory(query: String): Flow<List<CommandHistoryEntry>> {
        return _history.map { entries ->
            if (query.isBlank()) {
                entries
            } else {
                entries.filter { entry ->
                    entry.command.contains(query, ignoreCase = true)
                }
            }
        }
    }

    /**
     * Get commands matching prefix (for autocomplete)
     */
    fun getCommandsWithPrefix(prefix: String): Flow<List<String>> {
        return _history.map { entries ->
            entries
                .map { it.command }
                .filter { it.startsWith(prefix, ignoreCase = true) }
                .distinct()
                .take(20)
        }
    }

    /**
     * Get command by ID
     */
    suspend fun getCommand(id: Long): CommandHistoryEntry? {
        return withContext(ioDispatcher) {
            _history.value.find { it.id == id }
        }
    }

    /**
     * Delete command from history
     */
    fun deleteCommand(id: Long) {
        scope.launch(ioDispatcher) {
            _history.value = _history.value.filter { it.id != id }
        }
    }

    /**
     * Clear all history
     */
    fun clearHistory() {
        scope.launch(ioDispatcher) {
            _history.value = emptyList()
        }
    }

    /**
     * Get history statistics
     */
    fun getStatistics(): Flow<HistoryStatistics> {
        return _history.map { entries ->
            HistoryStatistics(
                totalCommands = entries.size,
                uniqueCommands = entries.map { it.command }.distinct().size,
                successfulCommands = entries.count { it.exitCode == 0 },
                failedCommands = entries.count { (it.exitCode ?: 0) != 0 },
                mostUsedCommands = entries
                    .groupBy { it.command }
                    .mapValues { it.value.size }
                    .entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .map { CommandUsage(it.key, it.value) }
            )
        }
    }
}

data class HistoryStatistics(
    val totalCommands: Int,
    val uniqueCommands: Int,
    val successfulCommands: Int,
    val failedCommands: Int,
    val mostUsedCommands: List<CommandUsage>
)

data class CommandUsage(
    val command: String,
    val count: Int
)
