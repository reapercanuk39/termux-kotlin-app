package com.termux.shared.shell.command.result

import com.termux.shared.data.DataUtils
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.errors.Errno
import com.termux.shared.errors.Error
import java.io.Serializable

open class ResultData : Serializable {

    /** The stdout of command. */
    @JvmField
    val stdout: StringBuilder = StringBuilder()

    /** The stderr of command. */
    @JvmField
    val stderr: StringBuilder = StringBuilder()

    /** The exit code of command. */
    @JvmField
    var exitCode: Int? = null

    /** The internal errors list of command. */
    @JvmField
    var errorsList: MutableList<Error>? = ArrayList()

    fun clearStdout() {
        stdout.setLength(0)
    }

    fun prependStdout(message: String): StringBuilder {
        return stdout.insert(0, message)
    }

    fun prependStdoutLn(message: String): StringBuilder {
        return stdout.insert(0, message + "\n")
    }

    fun appendStdout(message: String): StringBuilder {
        return stdout.append(message)
    }

    fun appendStdoutLn(message: String): StringBuilder {
        return stdout.append(message).append("\n")
    }

    fun clearStderr() {
        stderr.setLength(0)
    }

    fun prependStderr(message: String): StringBuilder {
        return stderr.insert(0, message)
    }

    fun prependStderrLn(message: String): StringBuilder {
        return stderr.insert(0, message + "\n")
    }

    fun appendStderr(message: String): StringBuilder {
        return stderr.append(message)
    }

    fun appendStderrLn(message: String): StringBuilder {
        return stderr.append(message).append("\n")
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
        return setStateFailed(null, code, message, null)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwable: Throwable): Boolean {
        return setStateFailed(null, code, message, listOf(throwable))
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        return setStateFailed(null, code, message, throwablesList)
    }

    @Synchronized
    fun setStateFailed(type: String?, code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        if (errorsList == null)
            errorsList = ArrayList()

        val error = Error()
        errorsList!!.add(error)

        return error.setStateFailed(type, code, message, throwablesList)
    }

    fun isStateFailed(): Boolean {
        errorsList?.let { list ->
            for (error in list) {
                if (error.isStateFailed)
                    return true
            }
        }
        return false
    }

    val errCode: Int
        get() {
            val list = errorsList
            return if (list != null && list.size > 0)
                list[list.size - 1].code
            else
                Errno.ERRNO_SUCCESS.getCode()
        }

    override fun toString(): String {
        return getResultDataLogString(this, true)
    }

    val stdoutLogString: String
        get() = if (stdout.toString().isEmpty())
            Logger.getSingleLineLogStringEntry("Stdout", null, "-")
        else
            Logger.getMultiLineLogStringEntry(
                "Stdout",
                DataUtils.getTruncatedCommandOutput(stdout.toString(), Logger.LOGGER_ENTRY_MAX_SAFE_PAYLOAD / 5, false, false, true),
                "-"
            )

    val stderrLogString: String
        get() = if (stderr.toString().isEmpty())
            Logger.getSingleLineLogStringEntry("Stderr", null, "-")
        else
            Logger.getMultiLineLogStringEntry(
                "Stderr",
                DataUtils.getTruncatedCommandOutput(stderr.toString(), Logger.LOGGER_ENTRY_MAX_SAFE_PAYLOAD / 5, false, false, true),
                "-"
            )

    val exitCodeLogString: String
        get() = Logger.getSingleLineLogStringEntry("Exit Code", exitCode, "-")

    companion object {
        /**
         * Get a log friendly [String] for [ResultData] parameters.
         *
         * @param resultData The [ResultData] to convert.
         * @param logStdoutAndStderr Set to `true` if [stdout] and [stderr] should be logged.
         * @return Returns the log friendly [String].
         */
        @JvmStatic
        fun getResultDataLogString(resultData: ResultData?, logStdoutAndStderr: Boolean): String {
            if (resultData == null) return "null"

            val logString = StringBuilder()

            if (logStdoutAndStderr) {
                logString.append("\n").append(resultData.stdoutLogString)
                logString.append("\n").append(resultData.stderrLogString)
            }
            logString.append("\n").append(resultData.exitCodeLogString)

            logString.append("\n\n").append(getErrorsListLogString(resultData))

            return logString.toString()
        }

        @JvmStatic
        fun getErrorsListLogString(resultData: ResultData?): String {
            if (resultData == null) return "null"

            val logString = StringBuilder()

            resultData.errorsList?.let { list ->
                for (error in list) {
                    if (error.isStateFailed) {
                        if (logString.toString().isNotEmpty())
                            logString.append("\n")
                        logString.append(Error.getErrorLogString(error))
                    }
                }
            }

            return logString.toString()
        }

        /**
         * Get a markdown [String] for [ResultData].
         *
         * @param resultData The [ResultData] to convert.
         * @return Returns the markdown [String].
         */
        @JvmStatic
        fun getResultDataMarkdownString(resultData: ResultData?): String {
            if (resultData == null) return "null"

            val markdownString = StringBuilder()

            if (resultData.stdout.toString().isEmpty())
                markdownString.append(MarkdownUtils.getSingleLineMarkdownStringEntry("Stdout", null, "-"))
            else
                markdownString.append(MarkdownUtils.getMultiLineMarkdownStringEntry("Stdout", resultData.stdout.toString(), "-"))

            if (resultData.stderr.toString().isEmpty())
                markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Stderr", null, "-"))
            else
                markdownString.append("\n").append(MarkdownUtils.getMultiLineMarkdownStringEntry("Stderr", resultData.stderr.toString(), "-"))

            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Exit Code", resultData.exitCode, "-"))

            markdownString.append("\n\n").append(getErrorsListMarkdownString(resultData))

            return markdownString.toString()
        }

        @JvmStatic
        fun getErrorsListMarkdownString(resultData: ResultData?): String {
            if (resultData == null) return "null"

            val markdownString = StringBuilder()

            resultData.errorsList?.let { list ->
                for (error in list) {
                    if (error.isStateFailed) {
                        if (markdownString.toString().isNotEmpty())
                            markdownString.append("\n")
                        markdownString.append(Error.getErrorMarkdownString(error))
                    }
                }
            }

            return markdownString.toString()
        }

        @JvmStatic
        fun getErrorsListMinimalString(resultData: ResultData?): String {
            if (resultData == null) return "null"

            val minimalString = StringBuilder()

            resultData.errorsList?.let { list ->
                for (error in list) {
                    if (error.isStateFailed) {
                        if (minimalString.toString().isNotEmpty())
                            minimalString.append("\n")
                        minimalString.append(Error.getMinimalErrorString(error))
                    }
                }
            }

            return minimalString.toString()
        }
    }
}
