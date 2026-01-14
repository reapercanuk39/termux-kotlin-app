package com.termux.app.core.logging

import android.util.Log
import com.termux.app.core.api.TermuxError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Log level enum with priority ordering.
 */
enum class LogLevel(val priority: Int, val tag: String) {
    VERBOSE(0, "V"),
    DEBUG(1, "D"),
    INFO(2, "I"),
    WARNING(3, "W"),
    ERROR(4, "E"),
    FATAL(5, "F")
}

/**
 * Represents a single log entry.
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
    val metadata: Map<String, Any?> = emptyMap()
) {
    fun format(includeMetadata: Boolean = true): String = buildString {
        append(DATE_FORMAT.format(Date(timestamp)))
        append(" [${level.tag}] ")
        append("$tag: $message")
        if (includeMetadata && metadata.isNotEmpty()) {
            append(" | ${metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
        }
        throwable?.let {
            append("\n")
            append(it.stackTraceToString())
        }
    }
    
    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
}

/**
 * Interface for log writers that handle log output.
 */
interface LogWriter {
    fun write(entry: LogEntry)
    fun flush()
    fun close()
}

/**
 * Android Logcat writer.
 */
class LogcatWriter : LogWriter {
    override fun write(entry: LogEntry) {
        val message = if (entry.metadata.isNotEmpty()) {
            "${entry.message} | ${entry.metadata}"
        } else {
            entry.message
        }
        
        when (entry.level) {
            LogLevel.VERBOSE -> Log.v(entry.tag, message, entry.throwable)
            LogLevel.DEBUG -> Log.d(entry.tag, message, entry.throwable)
            LogLevel.INFO -> Log.i(entry.tag, message, entry.throwable)
            LogLevel.WARNING -> Log.w(entry.tag, message, entry.throwable)
            LogLevel.ERROR -> Log.e(entry.tag, message, entry.throwable)
            LogLevel.FATAL -> Log.wtf(entry.tag, message, entry.throwable)
        }
    }
    
    override fun flush() {}
    override fun close() {}
}

/**
 * File-based log writer with rotation support.
 */
class FileLogWriter(
    private val logDir: File,
    private val maxFileSize: Long = 5 * 1024 * 1024, // 5MB
    private val maxFiles: Int = 5
) : LogWriter {
    
    private var currentFile: File? = null
    private var writer: PrintWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    init {
        logDir.mkdirs()
        rotateIfNeeded()
    }
    
    @Synchronized
    override fun write(entry: LogEntry) {
        rotateIfNeeded()
        writer?.println(entry.format())
    }
    
    @Synchronized
    override fun flush() {
        writer?.flush()
    }
    
    @Synchronized
    override fun close() {
        writer?.close()
        writer = null
    }
    
    private fun rotateIfNeeded() {
        val today = dateFormat.format(Date())
        val expectedFile = File(logDir, "termux-$today.log")
        
        // Check if we need to switch to a new file
        if (currentFile != expectedFile) {
            writer?.close()
            currentFile = expectedFile
            writer = PrintWriter(FileWriter(expectedFile, true), true)
        }
        
        // Check file size
        if ((currentFile?.length() ?: 0) > maxFileSize) {
            writer?.close()
            val rotatedFile = File(logDir, "termux-$today-${System.currentTimeMillis()}.log")
            currentFile?.renameTo(rotatedFile)
            currentFile = expectedFile
            writer = PrintWriter(FileWriter(expectedFile, true), true)
            
            // Clean up old files
            cleanupOldFiles()
        }
    }
    
    private fun cleanupOldFiles() {
        logDir.listFiles { file -> file.name.startsWith("termux-") && file.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(maxFiles)
            ?.forEach { it.delete() }
    }
}

/**
 * In-memory ring buffer writer for diagnostics.
 */
class MemoryLogWriter(private val maxEntries: Int = 1000) : LogWriter {
    
    private val buffer = ConcurrentLinkedQueue<LogEntry>()
    
    override fun write(entry: LogEntry) {
        buffer.offer(entry)
        while (buffer.size > maxEntries) {
            buffer.poll()
        }
    }
    
    override fun flush() {}
    override fun close() {}
    
    fun getEntries(): List<LogEntry> = buffer.toList()
    
    fun getEntries(level: LogLevel): List<LogEntry> = buffer.filter { it.level >= level }
    
    fun getEntries(tag: String): List<LogEntry> = buffer.filter { it.tag == tag }
    
    fun clear() = buffer.clear()
}

/**
 * Centralized logging system for Termux.
 * Provides structured logging with multiple outputs and plugin support.
 */
@Singleton
class TermuxLogger @Inject constructor() {
    
    private val writers = mutableListOf<LogWriter>()
    private var minLevel: LogLevel = LogLevel.DEBUG
    
    private val _logFlow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 100)
    
    /**
     * Flow of log entries for real-time monitoring.
     */
    val logFlow: Flow<LogEntry> = _logFlow.asSharedFlow()
    
    private val memoryWriter = MemoryLogWriter()
    
    init {
        // Default writers
        addWriter(LogcatWriter())
        addWriter(memoryWriter)
    }
    
    /**
     * Set minimum log level.
     */
    fun setMinLevel(level: LogLevel) {
        minLevel = level
    }
    
    /**
     * Add a log writer.
     */
    fun addWriter(writer: LogWriter) {
        writers.add(writer)
    }
    
    /**
     * Remove a log writer.
     */
    fun removeWriter(writer: LogWriter) {
        writers.remove(writer)
        writer.close()
    }
    
    /**
     * Enable file logging.
     */
    fun enableFileLogging(logDir: File) {
        addWriter(FileLogWriter(logDir))
    }
    
    /**
     * Log a message.
     */
    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        if (level.priority < minLevel.priority) return
        
        val entry = LogEntry(
            level = level,
            tag = tag,
            message = message,
            throwable = throwable,
            metadata = metadata
        )
        
        writers.forEach { it.write(entry) }
        _logFlow.tryEmit(entry)
    }
    
    // Convenience methods
    
    fun v(tag: String, message: String, metadata: Map<String, Any?> = emptyMap()) =
        log(LogLevel.VERBOSE, tag, message, metadata = metadata)
    
    fun d(tag: String, message: String, metadata: Map<String, Any?> = emptyMap()) =
        log(LogLevel.DEBUG, tag, message, metadata = metadata)
    
    fun i(tag: String, message: String, metadata: Map<String, Any?> = emptyMap()) =
        log(LogLevel.INFO, tag, message, metadata = metadata)
    
    fun w(tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any?> = emptyMap()) =
        log(LogLevel.WARNING, tag, message, throwable, metadata)
    
    fun e(tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any?> = emptyMap()) =
        log(LogLevel.ERROR, tag, message, throwable, metadata)
    
    fun f(tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any?> = emptyMap()) =
        log(LogLevel.FATAL, tag, message, throwable, metadata)
    
    /**
     * Log a TermuxError.
     */
    fun logError(tag: String, error: TermuxError, metadata: Map<String, Any?> = emptyMap()) {
        log(
            level = LogLevel.ERROR,
            tag = tag,
            message = error.message,
            throwable = error.cause,
            metadata = metadata + mapOf(
                "errorCode" to error.code,
                "errorType" to error::class.simpleName
            )
        )
    }
    
    /**
     * Get recent log entries from memory.
     */
    fun getRecentLogs(count: Int = 100): List<LogEntry> =
        memoryWriter.getEntries().takeLast(count)
    
    /**
     * Get recent errors from memory.
     */
    fun getRecentErrors(count: Int = 50): List<LogEntry> =
        memoryWriter.getEntries(LogLevel.ERROR).takeLast(count)
    
    /**
     * Export logs to a string for crash reports.
     */
    fun exportLogs(level: LogLevel = LogLevel.DEBUG): String =
        memoryWriter.getEntries(level).joinToString("\n") { it.format() }
    
    /**
     * Clear memory logs.
     */
    fun clearMemoryLogs() = memoryWriter.clear()
    
    /**
     * Flush all writers.
     */
    fun flush() = writers.forEach { it.flush() }
    
    /**
     * Close all writers.
     */
    fun close() {
        writers.forEach { it.close() }
        writers.clear()
    }
    
    /**
     * Create a tagged logger for a specific component.
     */
    fun forTag(tag: String): TaggedLogger = TaggedLogger(this, tag)
}

/**
 * Logger scoped to a specific tag.
 */
class TaggedLogger(
    private val logger: TermuxLogger,
    private val tag: String
) {
    fun v(message: String, metadata: Map<String, Any?> = emptyMap()) = logger.v(tag, message, metadata)
    fun d(message: String, metadata: Map<String, Any?> = emptyMap()) = logger.d(tag, message, metadata)
    fun i(message: String, metadata: Map<String, Any?> = emptyMap()) = logger.i(tag, message, metadata)
    fun w(message: String, throwable: Throwable? = null, metadata: Map<String, Any?> = emptyMap()) = logger.w(tag, message, throwable, metadata)
    fun e(message: String, throwable: Throwable? = null, metadata: Map<String, Any?> = emptyMap()) = logger.e(tag, message, throwable, metadata)
    fun f(message: String, throwable: Throwable? = null, metadata: Map<String, Any?> = emptyMap()) = logger.f(tag, message, throwable, metadata)
    fun logError(error: TermuxError, metadata: Map<String, Any?> = emptyMap()) = logger.logError(tag, error, metadata)
}

/**
 * Extension function for scoped logging.
 */
inline fun <reified T> TermuxLogger.forClass(): TaggedLogger = forTag(T::class.java.simpleName)
