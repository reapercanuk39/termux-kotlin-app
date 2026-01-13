package com.termux.shared.net.socket.local

import android.content.Context
import com.termux.shared.errors.Error
import com.termux.shared.jni.models.JniResult
import com.termux.shared.logger.Logger

/**
 * Manager for an AF_UNIX/SOCK_STREAM local server.
 *
 * Usage:
 * 1. Implement the [ILocalSocketManager] that will receive call backs from the server including
 *    when client connects via [ILocalSocketManager.onClientAccepted].
 *    Optionally extend the [LocalSocketManagerClientBase] class that provides base implementation.
 * 2. Create a [LocalSocketRunConfig] instance with the run config of the server.
 * 3. Create a [LocalSocketManager] instance and call [start].
 * 4. Stop server if needed with a call to [stop].
 */
class LocalSocketManager(
    context: Context,
    val localSocketRunConfig: LocalSocketRunConfig
) {
    /** The [Context] that may needed for various operations. */
    val context: Context = context.applicationContext

    /** The [LocalServerSocket] for the [LocalSocketManager]. */
    val serverSocket: LocalServerSocket = LocalServerSocket(this)

    /** The [ILocalSocketManager] client for the [LocalSocketManager]. */
    val localSocketManagerClient: ILocalSocketManager = localSocketRunConfig.localSocketManagerClient

    /** The [Thread.UncaughtExceptionHandler] used for client thread started by [LocalSocketManager]. */
    val localSocketManagerClientThreadUEH: Thread.UncaughtExceptionHandler = getLocalSocketManagerClientThreadUEHOrDefault()

    /** Whether the [LocalServerSocket] managed by [LocalSocketManager] in running or not. */
    var isRunning: Boolean = false
        private set

    /**
     * Create the [LocalServerSocket] and start listening for new [LocalClientSocket].
     */
    @Synchronized
    fun start(): Error? {
        Logger.logDebugExtended(LOG_TAG, "start\n$localSocketRunConfig")

        if (!localSocketLibraryLoaded) {
            try {
                Logger.logDebug(LOG_TAG, "Loading \"$LOCAL_SOCKET_LIBRARY\" library")
                System.loadLibrary(LOCAL_SOCKET_LIBRARY)
                localSocketLibraryLoaded = true
            } catch (t: Throwable) {
                val error = LocalSocketErrno.ERRNO_START_LOCAL_SOCKET_LIB_LOAD_FAILED_WITH_EXCEPTION.getError(
                    t, LOCAL_SOCKET_LIBRARY, t.message
                )
                Logger.logErrorExtended(LOG_TAG, error.errorLogString)
                return error
            }
        }

        isRunning = true
        return serverSocket.start()
    }

    /**
     * Stop the [LocalServerSocket] and stop listening for new [LocalClientSocket].
     */
    @Synchronized
    fun stop(): Error? {
        if (isRunning) {
            Logger.logDebugExtended(LOG_TAG, "stop\n$localSocketRunConfig")
            isRunning = false
            return serverSocket.stop()
        }
        return null
    }

    /** Wrapper for [onError] for `null` [LocalClientSocket]. */
    fun onError(error: Error) {
        onError(null, error)
    }

    /** Wrapper to call [ILocalSocketManager.onError] in a new thread. */
    fun onError(clientSocket: LocalClientSocket?, error: Error) {
        startLocalSocketManagerClientThread {
            localSocketManagerClient.onError(this, clientSocket, error)
        }
    }

    /** Wrapper to call [ILocalSocketManager.onDisallowedClientConnected] in a new thread. */
    fun onDisallowedClientConnected(clientSocket: LocalClientSocket, error: Error) {
        startLocalSocketManagerClientThread {
            localSocketManagerClient.onDisallowedClientConnected(this, clientSocket, error)
        }
    }

    /** Wrapper to call [ILocalSocketManager.onClientAccepted] in a new thread. */
    fun onClientAccepted(clientSocket: LocalClientSocket) {
        startLocalSocketManagerClientThread {
            localSocketManagerClient.onClientAccepted(this, clientSocket)
        }
    }

    /** All client accept logic must be run on separate threads so that incoming client acceptance is not blocked. */
    fun startLocalSocketManagerClientThread(runnable: Runnable) {
        val thread = Thread(runnable)
        thread.uncaughtExceptionHandler = localSocketManagerClientThreadUEH
        try {
            thread.start()
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "LocalSocketManagerClientThread start failed", e)
        }
    }

    /**
     * Get [Thread.UncaughtExceptionHandler] returned by call to
     * [ILocalSocketManager.getLocalSocketManagerClientThreadUEH]
     * or the default handler that just logs the exception.
     */
    private fun getLocalSocketManagerClientThreadUEHOrDefault(): Thread.UncaughtExceptionHandler {
        return localSocketManagerClient.getLocalSocketManagerClientThreadUEH(this)
            ?: Thread.UncaughtExceptionHandler { t, e ->
                Logger.logStackTraceWithMessage(
                    LOG_TAG,
                    "Uncaught exception for $t in ${localSocketRunConfig.title} server",
                    e
                )
            }
    }

    companion object {
        const val LOG_TAG = "LocalSocketManager"

        /** The native JNI local socket library. */
        private const val LOCAL_SOCKET_LIBRARY = "local-socket"

        /** Whether [LOCAL_SOCKET_LIBRARY] has been loaded or not. */
        private var localSocketLibraryLoaded = false

        /**
         * Creates an AF_UNIX/SOCK_STREAM local server socket at `path`, with the specified backlog.
         */
        @JvmStatic
        fun createServerSocket(serverTitle: String, path: ByteArray, backlog: Int): JniResult? {
            return try {
                createServerSocketNative(serverTitle, path, backlog)
            } catch (t: Throwable) {
                val message = "Exception in createServerSocketNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Closes the socket with fd.
         */
        @JvmStatic
        fun closeSocket(serverTitle: String, fd: Int): JniResult? {
            return try {
                closeSocketNative(serverTitle, fd)
            } catch (t: Throwable) {
                val message = "Exception in closeSocketNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Accepts a connection on the supplied server socket fd.
         */
        @JvmStatic
        fun accept(serverTitle: String, fd: Int): JniResult? {
            return try {
                acceptNative(serverTitle, fd)
            } catch (t: Throwable) {
                val message = "Exception in acceptNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Attempts to read up to data buffer length bytes from file descriptor fd into the data buffer.
         */
        @JvmStatic
        fun read(serverTitle: String, fd: Int, data: ByteArray, deadline: Long): JniResult? {
            return try {
                readNative(serverTitle, fd, data, deadline)
            } catch (t: Throwable) {
                val message = "Exception in readNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Attempts to send data buffer to the file descriptor.
         */
        @JvmStatic
        fun send(serverTitle: String, fd: Int, data: ByteArray, deadline: Long): JniResult? {
            return try {
                sendNative(serverTitle, fd, data, deadline)
            } catch (t: Throwable) {
                val message = "Exception in sendNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Gets the number of bytes available to read on the socket.
         */
        @JvmStatic
        fun available(serverTitle: String, fd: Int): JniResult? {
            return try {
                availableNative(serverTitle, fd)
            } catch (t: Throwable) {
                val message = "Exception in availableNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Set receiving (SO_RCVTIMEO) timeout in milliseconds for socket.
         */
        @JvmStatic
        fun setSocketReadTimeout(serverTitle: String, fd: Int, timeout: Int): JniResult? {
            return try {
                setSocketReadTimeoutNative(serverTitle, fd, timeout)
            } catch (t: Throwable) {
                val message = "Exception in setSocketReadTimeoutNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Set sending (SO_SNDTIMEO) timeout in milliseconds for fd.
         */
        @JvmStatic
        fun setSocketSendTimeout(serverTitle: String, fd: Int, timeout: Int): JniResult? {
            return try {
                setSocketSendTimeoutNative(serverTitle, fd, timeout)
            } catch (t: Throwable) {
                val message = "Exception in setSocketSendTimeoutNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Get the [PeerCred] for the socket.
         */
        @JvmStatic
        fun getPeerCred(serverTitle: String, fd: Int, peerCred: PeerCred): JniResult? {
            return try {
                getPeerCredNative(serverTitle, fd, peerCred)
            } catch (t: Throwable) {
                val message = "Exception in getPeerCredNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /** Get an error log [String] for the [LocalSocketManager]. */
        @JvmStatic
        fun getErrorLogString(
            error: Error,
            localSocketRunConfig: LocalSocketRunConfig,
            clientSocket: LocalClientSocket?
        ): String = buildString {
            append(localSocketRunConfig.title).append(" Socket Server Error:\n")
            append(error.errorLogString)
            append("\n\n\n")
            append(localSocketRunConfig.getLogString())
            if (clientSocket != null) {
                append("\n\n\n")
                append(clientSocket.getLogString())
            }
        }

        /** Get an error markdown [String] for the [LocalSocketManager]. */
        @JvmStatic
        fun getErrorMarkdownString(
            error: Error,
            localSocketRunConfig: LocalSocketRunConfig,
            clientSocket: LocalClientSocket?
        ): String = buildString {
            append(error.errorMarkdownString)
            append("\n##\n\n\n")
            append(localSocketRunConfig.getMarkdownString())
            if (clientSocket != null) {
                append("\n\n\n")
                append(clientSocket.getMarkdownString())
            }
        }

        @JvmStatic
        private external fun createServerSocketNative(serverTitle: String, path: ByteArray, backlog: Int): JniResult?

        @JvmStatic
        private external fun closeSocketNative(serverTitle: String, fd: Int): JniResult?

        @JvmStatic
        private external fun acceptNative(serverTitle: String, fd: Int): JniResult?

        @JvmStatic
        private external fun readNative(serverTitle: String, fd: Int, data: ByteArray, deadline: Long): JniResult?

        @JvmStatic
        private external fun sendNative(serverTitle: String, fd: Int, data: ByteArray, deadline: Long): JniResult?

        @JvmStatic
        private external fun availableNative(serverTitle: String, fd: Int): JniResult?

        @JvmStatic
        private external fun setSocketReadTimeoutNative(serverTitle: String, fd: Int, timeout: Int): JniResult?

        @JvmStatic
        private external fun setSocketSendTimeoutNative(serverTitle: String, fd: Int, timeout: Int): JniResult?

        @JvmStatic
        private external fun getPeerCredNative(serverTitle: String, fd: Int, peerCred: PeerCred): JniResult?
    }
}
