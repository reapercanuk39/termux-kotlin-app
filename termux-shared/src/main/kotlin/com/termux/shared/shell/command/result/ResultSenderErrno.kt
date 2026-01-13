package com.termux.shared.shell.command.result

import com.termux.shared.errors.Errno

/** The [Class] that defines ResultSender error messages and codes. */
open class ResultSenderErrno(type: String, code: Int, message: String) : Errno(type, code, message) {

    companion object {
        @JvmField
        val TYPE = "ResultSender Error"

        /* Errors for null or empty parameters (100-150) */
        @JvmField
        val ERROR_RESULT_FILE_BASENAME_NULL_OR_INVALID = Errno(TYPE, 100, "The result file basename \"%1\$s\" is null, empty or contains forward slashes \"/\".")
        @JvmField
        val ERROR_RESULT_FILES_SUFFIX_INVALID = Errno(TYPE, 101, "The result files suffix \"%1\$s\" contains forward slashes \"/\".")
        @JvmField
        val ERROR_FORMAT_RESULT_ERROR_FAILED_WITH_EXCEPTION = Errno(TYPE, 102, "Formatting result error failed.\nException: %1\$s")
        @JvmField
        val ERROR_FORMAT_RESULT_OUTPUT_FAILED_WITH_EXCEPTION = Errno(TYPE, 103, "Formatting result output failed.\nException: %1\$s")
    }
}
