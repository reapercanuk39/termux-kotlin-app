package com.termux.shared.errors

/** The [Class] that defines function error messages and codes. */
open class FunctionErrno(type: String, code: Int, message: String) : Errno(type, code, message) {

    companion object {
        @JvmField
        val TYPE = "Function Error"

        /* Errors for null or empty parameters (100-150) */
        @JvmField
        val ERRNO_NULL_OR_EMPTY_PARAMETER = Errno(TYPE, 100, "The %1\$s parameter passed to \"%2\$s\" is null or empty.")
        @JvmField
        val ERRNO_NULL_OR_EMPTY_PARAMETERS = Errno(TYPE, 101, "The %1\$s parameters passed to \"%2\$s\" are null or empty.")
        @JvmField
        val ERRNO_UNSET_PARAMETER = Errno(TYPE, 102, "The %1\$s parameter passed to \"%2\$s\" must be set.")
        @JvmField
        val ERRNO_UNSET_PARAMETERS = Errno(TYPE, 103, "The %1\$s parameters passed to \"%2\$s\" must be set.")
        @JvmField
        val ERRNO_INVALID_PARAMETER = Errno(TYPE, 104, "The %1\$s parameter passed to \"%2\$s\" is invalid.\"%3\$s\"")
        @JvmField
        val ERRNO_PARAMETER_NOT_INSTANCE_OF = Errno(TYPE, 104, "The %1\$s parameter passed to \"%2\$s\" is not an instance of %3\$s.")
    }
}
