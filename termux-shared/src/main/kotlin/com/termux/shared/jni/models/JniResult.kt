package com.termux.shared.jni.models

import androidx.annotation.Keep
import com.termux.shared.logger.Logger

/**
 * A class that can be used to return result for JNI calls with support for multiple fields to easily
 * return success and error states.
 *
 * https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.html
 * https://developer.android.com/training/articles/perf-jni
 */
@Keep
class JniResult {

    /** The return value for the JNI call. This should be 0 for success. */
    @JvmField
    var retval: Int

    /**
     * The errno value for any failed native system or library calls if [retval] does not equal 0.
     * This should be 0 if no errno was set.
     *
     * https://manpages.debian.org/testing/manpages-dev/errno.3.en.html
     */
    @JvmField
    var errno: Int

    /**
     * The error message for the failure if [retval] does not equal 0.
     * The message will contain errno message returned by strerror() if errno was set.
     *
     * https://manpages.debian.org/testing/manpages-dev/strerror.3.en.html
     */
    @JvmField
    var errmsg: String?

    /** Optional additional int data that needs to be returned by JNI call, like bytes read on success. */
    @JvmField
    var intData: Int = 0

    /**
     * Create a new instance of [JniResult].
     *
     * @param retval The [retval] value.
     * @param errno The [errno] value.
     * @param errmsg The [errmsg] value.
     */
    constructor(retval: Int, errno: Int, errmsg: String?) {
        this.retval = retval
        this.errno = errno
        this.errmsg = errmsg
    }

    /**
     * Create a new instance of [JniResult].
     *
     * @param retval The [retval] value.
     * @param errno The [errno] value.
     * @param errmsg The [errmsg] value.
     * @param intData The [intData] value.
     */
    constructor(retval: Int, errno: Int, errmsg: String?, intData: Int) : this(retval, errno, errmsg) {
        this.intData = intData
    }

    /**
     * Create a new instance of [JniResult] from a [Throwable] with [retval] -1.
     *
     * @param message The error message.
     * @param throwable The [Throwable] value.
     */
    constructor(message: String?, throwable: Throwable?) : this(-1, 0, Logger.getMessageAndStackTraceString(message, throwable))

    /** Get error [String] for [JniResult]. */
    fun getErrorString(): String {
        val logString = StringBuilder()

        logString.append(Logger.getSingleLineLogStringEntry("Retval", retval, "-"))

        if (errno != 0) {
            logString.append("\n").append(Logger.getSingleLineLogStringEntry("Errno", errno, "-"))
        }

        if (!errmsg.isNullOrEmpty()) {
            logString.append("\n").append(Logger.getMultiLineLogStringEntry("Errmsg", errmsg, "-"))
        }

        return logString.toString()
    }

    companion object {
        /**
         * Get error [String] for [JniResult].
         *
         * @param result The [JniResult] to get error from.
         * @return Returns the error [String].
         */
        @JvmStatic
        fun getErrorString(result: JniResult?): String {
            return result?.getErrorString() ?: "null"
        }
    }
}
