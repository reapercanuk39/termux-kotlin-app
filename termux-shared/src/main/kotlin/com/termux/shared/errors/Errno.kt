package com.termux.shared.errors

import android.app.Activity
import com.termux.shared.logger.Logger

/** The [Class] that defines error messages and codes. */
open class Errno(
    /** The errno type. */
    @JvmField
    protected val type: String,
    /** The errno code. */
    @JvmField
    protected val code: Int,
    /** The errno message. */
    @JvmField
    protected val message: String
) {
    init {
        map["$type:$code"] = this
    }

    override fun toString(): String {
        return "type=$type, code=$code, message=\"$message\""
    }

    fun getType(): String = type

    fun getCode(): Int = code

    fun getMessage(): String = message

    fun getError(): Error {
        return Error(getType(), getCode(), getMessage())
    }

    fun getError(vararg args: Any?): Error {
        return try {
            Error(getType(), getCode(), String.format(getMessage(), *args))
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "Exception raised while calling String.format() for error message of errno $this with args${args.contentToString()}\n${e.message}")
            // Return unformatted message as a backup
            Error(getType(), getCode(), getMessage() + ": " + args.contentToString())
        }
    }

    fun getError(throwable: Throwable?, vararg args: Any?): Error {
        return if (throwable == null) {
            getError(*args)
        } else {
            getError(listOf(throwable), *args)
        }
    }

    fun getError(throwablesList: List<Throwable>?, vararg args: Any?): Error {
        return try {
            if (throwablesList == null) {
                Error(getType(), getCode(), String.format(getMessage(), *args))
            } else {
                Error(getType(), getCode(), String.format(getMessage(), *args), throwablesList)
            }
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "Exception raised while calling String.format() for error message of errno $this with args${args.contentToString()}\n${e.message}")
            // Return unformatted message as a backup
            Error(getType(), getCode(), getMessage() + ": " + args.contentToString(), throwablesList)
        }
    }

    fun equalsErrorTypeAndCode(error: Error?): Boolean {
        if (error == null) return false
        return type == error.type && code == error.code
    }

    companion object {
        private val map = HashMap<String, Errno>()

        @JvmField
        val TYPE = "Error"

        @JvmField
        val ERRNO_SUCCESS = Errno(TYPE, Activity.RESULT_OK, "Success")
        @JvmField
        val ERRNO_CANCELLED = Errno(TYPE, Activity.RESULT_CANCELED, "Cancelled")
        @JvmField
        val ERRNO_MINOR_FAILURES = Errno(TYPE, Activity.RESULT_FIRST_USER, "Minor failure")
        @JvmField
        val ERRNO_FAILED = Errno(TYPE, Activity.RESULT_FIRST_USER + 1, "Failed")

        private const val LOG_TAG = "Errno"

        /**
         * Get the [Errno] of a specific type and code.
         *
         * @param type The unique type of the [Errno].
         * @param code The unique code of the [Errno].
         */
        @JvmStatic
        fun valueOf(type: String?, code: Int?): Errno? {
            if (type.isNullOrEmpty() || code == null) return null
            return map["$type:$code"]
        }
    }
}
