package com.termux.shared.shell.am

import com.termux.shared.errors.Errno

open class AmSocketServerErrno(type: String, code: Int, message: String) : Errno(type, code, message) {

    companion object {
        @JvmField
        val TYPE = "AmSocketServer Error"

        /** Errors for [AmSocketServer] (100-150) */
        @JvmField
        val ERRNO_PARSE_AM_COMMAND_FAILED_WITH_EXCEPTION = Errno(TYPE, 100, "Parse am command `%1\$s` failed.\nException: %2\$s")
        @JvmField
        val ERRNO_RUN_AM_COMMAND_FAILED_WITH_EXCEPTION = Errno(TYPE, 101, "Run am command `%1\$s` failed.\nException: %2\$s")
    }
}
