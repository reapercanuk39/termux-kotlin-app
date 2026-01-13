package com.termux.shared.errors

import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import java.io.Serializable

open class Error : Serializable {

    /** The optional error label. */
    private var label: String? = null

    /** The error type. */
    @JvmField
    var type: String = Errno.TYPE

    /** The error code. */
    @JvmField
    var code: Int = Errno.ERRNO_SUCCESS.getCode()

    /** The error message. */
    @JvmField
    var message: String? = null

    /** The error exceptions. */
    private var throwablesList: List<Throwable>? = ArrayList()

    // Getter methods for Java interop
    fun getType(): String = type
    fun getCode(): Int = code
    fun getMessage(): String? = message

    constructor() {
        initError(null, null, null, null)
    }

    constructor(type: String?, code: Int?, message: String?, throwablesList: List<Throwable>?) {
        initError(type, code, message, throwablesList)
    }

    constructor(type: String?, code: Int?, message: String?, throwable: Throwable) {
        initError(type, code, message, listOf(throwable))
    }

    constructor(type: String?, code: Int?, message: String?) {
        initError(type, code, message, null)
    }

    constructor(code: Int?, message: String?, throwablesList: List<Throwable>?) {
        initError(null, code, message, throwablesList)
    }

    constructor(code: Int?, message: String?, throwable: Throwable) {
        initError(null, code, message, listOf(throwable))
    }

    constructor(code: Int?, message: String?) {
        initError(null, code, message, null)
    }

    constructor(message: String?, throwable: Throwable) {
        initError(null, null, message, listOf(throwable))
    }

    constructor(message: String?, throwablesList: List<Throwable>?) {
        initError(null, null, message, throwablesList)
    }

    constructor(message: String?) {
        initError(null, null, message, null)
    }

    private fun initError(type: String?, code: Int?, message: String?, throwablesList: List<Throwable>?) {
        this.type = if (!type.isNullOrEmpty()) type else Errno.TYPE
        this.code = if (code != null && code > Errno.ERRNO_SUCCESS.getCode()) code else Errno.ERRNO_SUCCESS.getCode()
        this.message = message
        if (throwablesList != null)
            this.throwablesList = throwablesList
    }

    fun setLabel(label: String?): Error {
        this.label = label
        return this
    }

    fun getLabel(): String? {
        return label
    }

    fun prependMessage(message: String?) {
        if (message != null && isStateFailed)
            this.message = message + this.message
    }

    fun appendMessage(message: String?) {
        if (message != null && isStateFailed)
            this.message = this.message + message
    }

    fun getThrowablesList(): List<Throwable> {
        return throwablesList?.toList() ?: emptyList()
    }

    @Synchronized
    fun setStateFailed(error: Error): Boolean {
        return setStateFailed(error.type, error.code, error.message, null)
    }

    @Synchronized
    fun setStateFailed(error: Error, throwable: Throwable): Boolean {
        return setStateFailed(error.type, error.code, error.message, listOf(throwable))
    }

    @Synchronized
    fun setStateFailed(error: Error, throwablesList: List<Throwable>?): Boolean {
        return setStateFailed(error.type, error.code, error.message, throwablesList)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?): Boolean {
        return setStateFailed(this.type, code, message, null)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwable: Throwable): Boolean {
        return setStateFailed(this.type, code, message, listOf(throwable))
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        return setStateFailed(this.type, code, message, throwablesList)
    }

    @Synchronized
    fun setStateFailed(type: String?, code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        this.message = message
        this.throwablesList = throwablesList

        if (!type.isNullOrEmpty())
            this.type = type

        return if (code > Errno.ERRNO_SUCCESS.getCode()) {
            this.code = code
            true
        } else {
            Logger.logWarn(LOG_TAG, "Ignoring invalid error code value \"$code\". Force setting it to RESULT_CODE_FAILED \"${Errno.ERRNO_FAILED.getCode()}\"")
            this.code = Errno.ERRNO_FAILED.getCode()
            false
        }
    }

    val isStateFailed: Boolean
        get() = code > Errno.ERRNO_SUCCESS.getCode()

    override fun toString(): String {
        return getErrorLogString(this)
    }

    fun logErrorAndShowToast(context: Context?, logTag: String?) {
        Logger.logErrorExtended(logTag ?: LOG_TAG, errorLogString)
        Logger.showToast(context, minimalErrorLogString, true)
    }

    val errorLogString: String
        get() {
            val logString = StringBuilder()

            logString.append(codeString)
            logString.append("\n").append(typeAndMessageLogString)
            if (!throwablesList.isNullOrEmpty())
                logString.append("\n").append(stackTracesLogString)

            return logString.toString()
        }

    val minimalErrorLogString: String
        get() {
            val logString = StringBuilder()

            logString.append(codeString)
            logString.append(typeAndMessageLogString)

            return logString.toString()
        }

    val minimalErrorString: String
        get() {
            val logString = StringBuilder()

            logString.append("(").append(code).append(") ")
            logString.append(type).append(": ").append(message)

            return logString.toString()
        }

    val errorMarkdownString: String
        get() {
            val markdownString = StringBuilder()

            markdownString.append(MarkdownUtils.getSingleLineMarkdownStringEntry("Error Code", code, "-"))
            markdownString.append("\n").append(
                MarkdownUtils.getMultiLineMarkdownStringEntry(
                    if (Errno.TYPE == type) "Error Message" else "Error Message ($type)", message, "-"
                )
            )
            if (!throwablesList.isNullOrEmpty())
                markdownString.append("\n\n").append(stackTracesMarkdownString)

            return markdownString.toString()
        }

    val codeString: String
        get() = Logger.getSingleLineLogStringEntry("Error Code", code, "-")

    val typeAndMessageLogString: String
        get() = Logger.getMultiLineLogStringEntry(
            if (Errno.TYPE == type) "Error Message" else "Error Message ($type)", message, "-"
        )

    val stackTracesLogString: String
        get() = Logger.getStackTracesString("StackTraces:", Logger.getStackTracesStringArray(throwablesList))

    val stackTracesMarkdownString: String
        get() = Logger.getStackTracesMarkdownString("StackTraces", Logger.getStackTracesStringArray(throwablesList))

    companion object {
        private const val LOG_TAG = "Error"

        /**
         * Log the [Error] and show a toast for the minimal [String] for the [Error].
         *
         * @param context The [Context] for operations.
         * @param logTag The log tag to use for logging.
         * @param error The [Error] to convert.
         */
        @JvmStatic
        fun logErrorAndShowToast(context: Context?, logTag: String?, error: Error?) {
            error?.logErrorAndShowToast(context, logTag)
        }

        /**
         * Get a log friendly [String] for [Error] error parameters.
         *
         * @param error The [Error] to convert.
         * @return Returns the log friendly [String].
         */
        @JvmStatic
        fun getErrorLogString(error: Error?): String {
            return error?.errorLogString ?: "null"
        }

        /**
         * Get a minimal log friendly [String] for [Error] error parameters.
         *
         * @param error The [Error] to convert.
         * @return Returns the log friendly [String].
         */
        @JvmStatic
        fun getMinimalErrorLogString(error: Error?): String {
            return error?.minimalErrorLogString ?: "null"
        }

        /**
         * Get a minimal [String] for [Error] error parameters.
         *
         * @param error The [Error] to convert.
         * @return Returns the [String].
         */
        @JvmStatic
        fun getMinimalErrorString(error: Error?): String {
            return error?.minimalErrorString ?: "null"
        }

        /**
         * Get a markdown [String] for [Error].
         *
         * @param error The [Error] to convert.
         * @return Returns the markdown [String].
         */
        @JvmStatic
        fun getErrorMarkdownString(error: Error?): String {
            return error?.errorMarkdownString ?: "null"
        }
    }
}
