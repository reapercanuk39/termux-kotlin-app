package com.termux.shared.logger

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.termux.shared.R
import com.termux.shared.data.DataUtils
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

object Logger {

    private var DEFAULT_LOG_TAG = "Logger"

    const val LOG_LEVEL_OFF = 0 // log nothing
    const val LOG_LEVEL_NORMAL = 1 // start logging error, warn and info messages and stacktraces
    const val LOG_LEVEL_DEBUG = 2 // start logging debug messages
    const val LOG_LEVEL_VERBOSE = 3 // start logging verbose messages

    const val DEFAULT_LOG_LEVEL = LOG_LEVEL_NORMAL
    const val MAX_LOG_LEVEL = LOG_LEVEL_VERBOSE
    private var CURRENT_LOG_LEVEL = DEFAULT_LOG_LEVEL

    /**
     * The maximum size of the log entry payload that can be written to the logger.
     */
    const val LOGGER_ENTRY_MAX_PAYLOAD = 4068 // 4068 bytes

    /**
     * The maximum safe size of the log entry payload that can be written to the logger.
     */
    const val LOGGER_ENTRY_MAX_SAFE_PAYLOAD = 4000 // 4000 bytes

    @JvmStatic
    fun logMessage(logPriority: Int, tag: String, message: String) {
        when {
            logPriority == Log.ERROR && CURRENT_LOG_LEVEL >= LOG_LEVEL_NORMAL -> Log.e(getFullTag(tag), message)
            logPriority == Log.WARN && CURRENT_LOG_LEVEL >= LOG_LEVEL_NORMAL -> Log.w(getFullTag(tag), message)
            logPriority == Log.INFO && CURRENT_LOG_LEVEL >= LOG_LEVEL_NORMAL -> Log.i(getFullTag(tag), message)
            logPriority == Log.DEBUG && CURRENT_LOG_LEVEL >= LOG_LEVEL_DEBUG -> Log.d(getFullTag(tag), message)
            logPriority == Log.VERBOSE && CURRENT_LOG_LEVEL >= LOG_LEVEL_VERBOSE -> Log.v(getFullTag(tag), message)
        }
    }

    @JvmStatic
    fun logExtendedMessage(logLevel: Int, tag: String, message: String?) {
        if (message.isNullOrEmpty()) return

        var remaining: String = message
        val prefix: String
        // -8 for prefix "(xx/xx)" (max 99 sections), - log tag length, -4 for log tag prefix "D/" and suffix ": "
        val maxEntrySize = LOGGER_ENTRY_MAX_PAYLOAD - 8 - getFullTag(tag).length - 4

        val messagesList = mutableListOf<String>()

        while (remaining.isNotEmpty()) {
            if (remaining.length > maxEntrySize) {
                var cutOffIndex = maxEntrySize
                val nextNewlineIndex = remaining.lastIndexOf('\n', cutOffIndex)
                if (nextNewlineIndex != -1) {
                    cutOffIndex = nextNewlineIndex + 1
                }
                messagesList.add(remaining.substring(0, cutOffIndex))
                remaining = remaining.substring(cutOffIndex)
            } else {
                messagesList.add(remaining)
                break
            }
        }

        for (i in messagesList.indices) {
            val currentPrefix = if (messagesList.size > 1) "(${i + 1}/${messagesList.size})\n" else ""
            logMessage(logLevel, tag, currentPrefix + messagesList[i])
        }
    }

    @JvmStatic
    fun logError(tag: String, message: String) = logMessage(Log.ERROR, tag, message)

    @JvmStatic
    fun logError(message: String) = logMessage(Log.ERROR, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logErrorExtended(tag: String, message: String?) = logExtendedMessage(Log.ERROR, tag, message)

    @JvmStatic
    fun logErrorExtended(message: String?) = logExtendedMessage(Log.ERROR, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logErrorPrivate(tag: String, message: String) {
        if (CURRENT_LOG_LEVEL >= LOG_LEVEL_DEBUG) logMessage(Log.ERROR, tag, message)
    }

    @JvmStatic
    fun logErrorPrivate(message: String) {
        if (CURRENT_LOG_LEVEL >= LOG_LEVEL_DEBUG) logMessage(Log.ERROR, DEFAULT_LOG_TAG, message)
    }

    @JvmStatic
    fun logErrorPrivateExtended(tag: String, message: String?) {
        if (CURRENT_LOG_LEVEL >= LOG_LEVEL_DEBUG) logExtendedMessage(Log.ERROR, tag, message)
    }

    @JvmStatic
    fun logErrorPrivateExtended(message: String?) {
        if (CURRENT_LOG_LEVEL >= LOG_LEVEL_DEBUG) logExtendedMessage(Log.ERROR, DEFAULT_LOG_TAG, message)
    }

    @JvmStatic
    fun logWarn(tag: String, message: String) = logMessage(Log.WARN, tag, message)

    @JvmStatic
    fun logWarn(message: String) = logMessage(Log.WARN, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logWarnExtended(tag: String, message: String?) = logExtendedMessage(Log.WARN, tag, message)

    @JvmStatic
    fun logWarnExtended(message: String?) = logExtendedMessage(Log.WARN, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logInfo(tag: String, message: String) = logMessage(Log.INFO, tag, message)

    @JvmStatic
    fun logInfo(message: String) = logMessage(Log.INFO, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logInfoExtended(tag: String, message: String?) = logExtendedMessage(Log.INFO, tag, message)

    @JvmStatic
    fun logInfoExtended(message: String?) = logExtendedMessage(Log.INFO, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logDebug(tag: String, message: String) = logMessage(Log.DEBUG, tag, message)

    @JvmStatic
    fun logDebug(message: String) = logMessage(Log.DEBUG, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logDebugExtended(tag: String, message: String?) = logExtendedMessage(Log.DEBUG, tag, message)

    @JvmStatic
    fun logDebugExtended(message: String?) = logExtendedMessage(Log.DEBUG, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logVerbose(tag: String, message: String) = logMessage(Log.VERBOSE, tag, message)

    @JvmStatic
    fun logVerbose(message: String) = logMessage(Log.VERBOSE, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logVerboseExtended(tag: String, message: String?) = logExtendedMessage(Log.VERBOSE, tag, message)

    @JvmStatic
    fun logVerboseExtended(message: String?) = logExtendedMessage(Log.VERBOSE, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logVerboseForce(tag: String, message: String) = Log.v(tag, message)

    @JvmStatic
    fun logInfoAndShowToast(context: Context, tag: String, message: String) {
        if (CURRENT_LOG_LEVEL >= LOG_LEVEL_NORMAL) {
            logInfo(tag, message)
            showToast(context, message, true)
        }
    }

    @JvmStatic
    fun logInfoAndShowToast(context: Context, message: String) = logInfoAndShowToast(context, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logErrorAndShowToast(context: Context, tag: String, message: String) {
        if (CURRENT_LOG_LEVEL >= LOG_LEVEL_NORMAL) {
            logError(tag, message)
            showToast(context, message, true)
        }
    }

    @JvmStatic
    fun logErrorAndShowToast(context: Context, message: String) = logErrorAndShowToast(context, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logDebugAndShowToast(context: Context, tag: String, message: String) {
        if (CURRENT_LOG_LEVEL >= LOG_LEVEL_DEBUG) {
            logDebug(tag, message)
            showToast(context, message, true)
        }
    }

    @JvmStatic
    fun logDebugAndShowToast(context: Context, message: String) = logDebugAndShowToast(context, DEFAULT_LOG_TAG, message)

    @JvmStatic
    fun logStackTraceWithMessage(tag: String, message: String?, throwable: Throwable?) {
        logErrorExtended(tag, getMessageAndStackTraceString(message, throwable))
    }

    @JvmStatic
    fun logStackTraceWithMessage(message: String?, throwable: Throwable?) = logStackTraceWithMessage(DEFAULT_LOG_TAG, message, throwable)

    @JvmStatic
    fun logStackTrace(tag: String, throwable: Throwable?) = logStackTraceWithMessage(tag, null, throwable)

    @JvmStatic
    fun logStackTrace(throwable: Throwable?) = logStackTraceWithMessage(DEFAULT_LOG_TAG, null, throwable)

    @JvmStatic
    fun logStackTracesWithMessage(tag: String, message: String?, throwablesList: List<Throwable>?) {
        logErrorExtended(tag, getMessageAndStackTracesString(message, throwablesList))
    }

    @JvmStatic
    fun getMessageAndStackTraceString(message: String?, throwable: Throwable?): String? {
        return when {
            message == null && throwable == null -> null
            message != null && throwable != null -> "$message:\n${getStackTraceString(throwable)}"
            throwable == null -> message
            else -> getStackTraceString(throwable)
        }
    }

    @JvmStatic
    fun getMessageAndStackTracesString(message: String?, throwablesList: List<Throwable>?): String? {
        return when {
            message == null && throwablesList.isNullOrEmpty() -> null
            message != null && !throwablesList.isNullOrEmpty() -> "$message:\n${getStackTracesString(null, getStackTracesStringArray(throwablesList))}"
            throwablesList.isNullOrEmpty() -> message
            else -> getStackTracesString(null, getStackTracesStringArray(throwablesList))
        }
    }

    @JvmStatic
    fun getStackTraceString(throwable: Throwable?): String? {
        if (throwable == null) return null

        return try {
            val errors = StringWriter()
            val pw = PrintWriter(errors)
            throwable.printStackTrace(pw)
            pw.close()
            val result = errors.toString()
            errors.close()
            result
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun getStackTracesStringArray(throwable: Throwable?): Array<String?>? = getStackTracesStringArray(listOfNotNull(throwable))

    @JvmStatic
    fun getStackTracesStringArray(throwablesList: List<Throwable>?): Array<String?>? {
        if (throwablesList == null) return null
        return throwablesList.map { getStackTraceString(it) }.toTypedArray()
    }

    @JvmStatic
    fun getStackTracesString(label: String?, stackTraceStringArray: Array<String?>?): String {
        val effectiveLabel = label ?: "StackTraces:"
        val stackTracesString = StringBuilder(effectiveLabel)

        if (stackTraceStringArray.isNullOrEmpty()) {
            stackTracesString.append(" -")
        } else {
            for (i in stackTraceStringArray.indices) {
                if (stackTraceStringArray.size > 1)
                    stackTracesString.append("\n\nStacktrace ").append(i + 1)
                stackTracesString.append("\n```\n").append(stackTraceStringArray[i]).append("\n```\n")
            }
        }

        return stackTracesString.toString()
    }

    @JvmStatic
    fun getStackTracesMarkdownString(label: String?, stackTraceStringArray: Array<String?>?): String {
        val effectiveLabel = label ?: "StackTraces"
        val stackTracesString = StringBuilder("### $effectiveLabel")

        if (stackTraceStringArray.isNullOrEmpty()) {
            stackTracesString.append("\n\n`-`")
        } else {
            for (i in stackTraceStringArray.indices) {
                if (stackTraceStringArray.size > 1)
                    stackTracesString.append("\n\n\n#### Stacktrace ").append(i + 1)
                stackTracesString.append("\n\n```\n").append(stackTraceStringArray[i]).append("\n```")
            }
        }

        stackTracesString.append("\n##\n")
        return stackTracesString.toString()
    }

    @JvmStatic
    fun getSingleLineLogStringEntry(label: String, obj: Any?, def: String): String {
        return if (obj != null) "$label: `$obj`" else "$label: $def"
    }

    @JvmStatic
    fun getMultiLineLogStringEntry(label: String, obj: Any?, def: String): String {
        return if (obj != null) "$label:\n```\n$obj\n```\n" else "$label: $def"
    }

    @JvmStatic
    fun showToast(context: Context?, toastText: String?, longDuration: Boolean) {
        if (context == null || DataUtils.isNullOrEmpty(toastText)) return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, toastText, if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun getLogLevelsArray(): Array<CharSequence> = arrayOf(
        LOG_LEVEL_OFF.toString(),
        LOG_LEVEL_NORMAL.toString(),
        LOG_LEVEL_DEBUG.toString(),
        LOG_LEVEL_VERBOSE.toString()
    )

    @JvmStatic
    fun getLogLevelLabelsArray(context: Context, logLevels: Array<CharSequence>?, addDefaultTag: Boolean): Array<CharSequence>? {
        if (logLevels == null) return null
        return logLevels.map { getLogLevelLabel(context, it.toString().toInt(), addDefaultTag) }.toTypedArray()
    }

    @JvmStatic
    fun getLogLevelLabel(context: Context, logLevel: Int, addDefaultTag: Boolean): String {
        val logLabel = when (logLevel) {
            LOG_LEVEL_OFF -> context.getString(R.string.log_level_off)
            LOG_LEVEL_NORMAL -> context.getString(R.string.log_level_normal)
            LOG_LEVEL_DEBUG -> context.getString(R.string.log_level_debug)
            LOG_LEVEL_VERBOSE -> context.getString(R.string.log_level_verbose)
            else -> context.getString(R.string.log_level_unknown)
        }

        return if (addDefaultTag && logLevel == DEFAULT_LOG_LEVEL) "$logLabel (default)" else logLabel
    }

    @JvmStatic
    fun getDefaultLogTag(): String = DEFAULT_LOG_TAG

    /**
     * IllegalArgumentException will be thrown if tag.length() > 23 for Nougat (7.0) and prior releases.
     */
    @JvmStatic
    fun setDefaultLogTag(defaultLogTag: String) {
        DEFAULT_LOG_TAG = if (defaultLogTag.length >= 23) defaultLogTag.substring(0, 22) else defaultLogTag
    }

    @JvmStatic
    fun getLogLevel(): Int = CURRENT_LOG_LEVEL

    @JvmStatic
    fun setLogLevel(context: Context?, logLevel: Int): Int {
        CURRENT_LOG_LEVEL = if (isLogLevelValid(logLevel)) logLevel else DEFAULT_LOG_LEVEL
        context?.let {
            showToast(it, it.getString(R.string.log_level_value, getLogLevelLabel(it, CURRENT_LOG_LEVEL, false)), true)
        }
        return CURRENT_LOG_LEVEL
    }

    /**
     * The colon character ":" must not exist inside the tag, otherwise the `logcat` command
     * filterspecs arguments `<tag>[:priority]` will not work.
     */
    @JvmStatic
    fun getFullTag(tag: String): String = if (DEFAULT_LOG_TAG == tag) tag else "$DEFAULT_LOG_TAG.$tag"

    @JvmStatic
    fun isLogLevelValid(logLevel: Int?): Boolean =
        logLevel != null && logLevel >= LOG_LEVEL_OFF && logLevel <= MAX_LOG_LEVEL

    /**
     * Check if custom log level is valid and >= [CURRENT_LOG_LEVEL].
     */
    @JvmStatic
    fun shouldEnableLoggingForCustomLogLevel(customLogLevel: Int?): Boolean {
        if (CURRENT_LOG_LEVEL <= LOG_LEVEL_OFF) return false
        if (customLogLevel == null) return CURRENT_LOG_LEVEL >= LOG_LEVEL_VERBOSE
        if (customLogLevel <= LOG_LEVEL_OFF) return false
        val effectiveLevel = if (isLogLevelValid(customLogLevel)) customLogLevel else LOG_LEVEL_VERBOSE
        return effectiveLevel >= CURRENT_LOG_LEVEL
    }
}
