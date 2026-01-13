package com.termux.shared.net.socket.local

import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import java.io.Serializable
import java.nio.charset.StandardCharsets

/**
 * Run config for [LocalSocketManager].
 */
open class LocalSocketRunConfig(
    /** The [LocalSocketManager] title. */
    protected val mTitle: String,
    path: String,
    /** The [ILocalSocketManager] client for the [LocalSocketManager]. */
    protected val mLocalSocketManagerClient: ILocalSocketManager
) : Serializable {

    /**
     * The [LocalServerSocket] path.
     *
     * For a filesystem socket, this must be an absolute path to the socket file. Creation of a new
     * socket will fail if the server starter app process does not have write and search (execute)
     * permission on the directory in which the socket is created. The client process must have write
     * permission on the socket to connect to it. Other app will not be able to connect to socket
     * if its created in private app data directory.
     *
     * For an abstract namespace socket, the first byte must be a null `\0` character. Note that on
     * Android 9+, if server app is using `targetSdkVersion` `28`, then other apps will not be able
     * to connect to it due to selinux restrictions.
     * > Per-app SELinux domains
     * > Apps that target Android 9 or higher cannot share data with other apps using world-accessible
     * Unix permissions. This change improves the integrity of the Android Application Sandbox,
     * particularly the requirement that an app's private data is accessible only by that app.
     * https://developer.android.com/about/versions/pie/android-9.0-changes-28
     * https://github.com/android/ndk/issues/1469
     * https://stackoverflow.com/questions/63806516/avc-denied-connectto-when-using-uds-on-android-10
     *
     * Max allowed length is 108 bytes as per sun_path size (UNIX_PATH_MAX) on Linux.
     */
    protected val mPath: String

    /** If abstract namespace [LocalServerSocket] instead of filesystem. */
    protected val mAbstractNamespaceSocket: Boolean

    /**
     * The [LocalServerSocket] file descriptor.
     * Value will be `>= 0` if socket has been created successfully and `-1` if not created or closed.
     */
    protected var mFD: Int = -1

    /**
     * The [LocalClientSocket] receiving (SO_RCVTIMEO) timeout in milliseconds.
     *
     * https://manpages.debian.org/testing/manpages/socket.7.en.html
     * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/am/NativeCrashListener.java;l=55
     * Defaults to [DEFAULT_RECEIVE_TIMEOUT].
     */
    protected var mReceiveTimeout: Int? = null

    /**
     * The [LocalClientSocket] sending (SO_SNDTIMEO) timeout in milliseconds.
     *
     * https://manpages.debian.org/testing/manpages/socket.7.en.html
     * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/am/NativeCrashListener.java;l=55
     * Defaults to [DEFAULT_SEND_TIMEOUT].
     */
    protected var mSendTimeout: Int? = null

    /**
     * The [LocalClientSocket] deadline in milliseconds. When the deadline has elapsed after
     * creation time of client socket, all reads and writes will error out. Set to 0, for no
     * deadline.
     * Defaults to [DEFAULT_DEADLINE].
     */
    protected var mDeadline: Long? = null

    /**
     * The [LocalServerSocket] backlog for the maximum length to which the queue of pending connections
     * for the socket may grow. This value may be ignored or may not have one-to-one mapping
     * in kernel implementation. Value must be greater than 0.
     *
     * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/net/LocalSocketManager.java;l=31
     * Defaults to [DEFAULT_BACKLOG].
     */
    protected var mBacklog: Int? = null

    init {
        mAbstractNamespaceSocket = path.toByteArray(StandardCharsets.UTF_8)[0].toInt() == 0

        mPath = if (mAbstractNamespaceSocket) {
            path
        } else {
            FileUtils.getCanonicalPath(path, null)
        }
    }

    /** Get [mTitle]. */
    val title: String
        get() = mTitle

    /** Get log title that should be used for [LocalSocketManager]. */
    val logTitle: String
        get() = Logger.getDefaultLogTag() + "." + mTitle

    /** Get [mPath]. */
    val path: String
        get() = mPath

    /** Get [mAbstractNamespaceSocket]. */
    fun isAbstractNamespaceSocket(): Boolean {
        return mAbstractNamespaceSocket
    }

    /** Get [mLocalSocketManagerClient]. */
    val localSocketManagerClient: ILocalSocketManager
        get() = mLocalSocketManagerClient

    /** Get [mFD]. */
    fun getFD(): Int {
        return mFD
    }

    /** Set [mFD]. Value must be greater than 0 or -1. */
    fun setFD(fd: Int) {
        mFD = if (fd >= 0) fd else -1
    }

    /** Get [mReceiveTimeout] if set, otherwise [DEFAULT_RECEIVE_TIMEOUT]. */
    fun getReceiveTimeout(): Int {
        return mReceiveTimeout ?: DEFAULT_RECEIVE_TIMEOUT
    }

    /** Set [mReceiveTimeout]. */
    fun setReceiveTimeout(receiveTimeout: Int?) {
        mReceiveTimeout = receiveTimeout
    }

    /** Get [mSendTimeout] if set, otherwise [DEFAULT_SEND_TIMEOUT]. */
    fun getSendTimeout(): Int {
        return mSendTimeout ?: DEFAULT_SEND_TIMEOUT
    }

    /** Set [mSendTimeout]. */
    fun setSendTimeout(sendTimeout: Int?) {
        mSendTimeout = sendTimeout
    }

    /** Get [mDeadline] if set, otherwise [DEFAULT_DEADLINE]. */
    fun getDeadline(): Long {
        return mDeadline ?: DEFAULT_DEADLINE.toLong()
    }

    /** Set [mDeadline]. */
    fun setDeadline(deadline: Long?) {
        mDeadline = deadline
    }

    /** Get [mBacklog] if set, otherwise [DEFAULT_BACKLOG]. */
    fun getBacklog(): Int {
        return mBacklog ?: DEFAULT_BACKLOG
    }

    /** Set [mBacklog]. Value must be greater than 0. */
    fun setBacklog(backlog: Int?) {
        if (backlog != null && backlog > 0)
            mBacklog = backlog
    }

    /** Get a log [String] for the [LocalSocketRunConfig]. */
    open fun getLogString(): String {
        val logString = StringBuilder()

        logString.append(mTitle).append(" Socket Server Run Config:")
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("Path", mPath, "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("AbstractNamespaceSocket", mAbstractNamespaceSocket, "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("LocalSocketManagerClient", mLocalSocketManagerClient.javaClass.name, "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("FD", mFD, "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("ReceiveTimeout", getReceiveTimeout(), "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("SendTimeout", getSendTimeout(), "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("Deadline", getDeadline(), "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("Backlog", getBacklog(), "-"))

        return logString.toString()
    }

    /** Get a markdown [String] for the [LocalSocketRunConfig]. */
    open fun getMarkdownString(): String {
        val markdownString = StringBuilder()

        markdownString.append("## ").append(mTitle).append(" Socket Server Run Config")
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Path", mPath, "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("AbstractNamespaceSocket", mAbstractNamespaceSocket, "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("LocalSocketManagerClient", mLocalSocketManagerClient.javaClass.name, "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("FD", mFD, "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("ReceiveTimeout", getReceiveTimeout(), "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("SendTimeout", getSendTimeout(), "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Deadline", getDeadline(), "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Backlog", getBacklog(), "-"))

        return markdownString.toString()
    }

    override fun toString(): String {
        return getLogString()
    }

    companion object {
        const val DEFAULT_RECEIVE_TIMEOUT: Int = 10000
        const val DEFAULT_SEND_TIMEOUT: Int = 10000
        const val DEFAULT_DEADLINE: Int = 0
        const val DEFAULT_BACKLOG: Int = 50

        /**
         * Get a log [String] for [LocalSocketRunConfig].
         *
         * @param config The [LocalSocketRunConfig] to get info of.
         * @return Returns the log [String].
         */
        @JvmStatic
        fun getRunConfigLogString(config: LocalSocketRunConfig?): String {
            return config?.getLogString() ?: "null"
        }

        /**
         * Get a markdown [String] for [LocalSocketRunConfig].
         *
         * @param config The [LocalSocketRunConfig] to get info of.
         * @return Returns the markdown [String].
         */
        @JvmStatic
        fun getRunConfigMarkdownString(config: LocalSocketRunConfig?): String {
            return config?.getMarkdownString() ?: "null"
        }
    }
}
