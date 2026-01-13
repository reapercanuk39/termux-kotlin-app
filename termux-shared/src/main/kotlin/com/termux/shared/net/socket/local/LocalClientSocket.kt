package com.termux.shared.net.socket.local

import com.termux.shared.data.DataUtils
import com.termux.shared.errors.Error
import com.termux.shared.jni.models.JniResult
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

/** The client socket for [LocalSocketManager]. */
open class LocalClientSocket internal constructor(
    /** The [LocalSocketManager] instance for the local socket. */
    protected val mLocalSocketManager: LocalSocketManager,
    fd: Int,
    /** The [PeerCred] of the [LocalClientSocket] containing info of client/peer. */
    val peerCred: PeerCred
) : Closeable {

    /** The [LocalSocketRunConfig] containing run config for the [LocalClientSocket]. */
    protected val mLocalSocketRunConfig: LocalSocketRunConfig = mLocalSocketManager.localSocketRunConfig

    /**
     * The [LocalClientSocket] file descriptor.
     * Value will be `>= 0` if socket has been connected and `-1` if closed.
     */
    protected var mFD: Int = -1

    /** The creation time of [LocalClientSocket]. This is also used for deadline. */
    val creationTime: Long = System.currentTimeMillis()

    /** The [OutputStream] implementation for the [LocalClientSocket]. */
    val outputStream: OutputStream = SocketOutputStream()

    /** The [InputStream] implementation for the [LocalClientSocket]. */
    val inputStream: InputStream = SocketInputStream()

    init {
        setFD(fd)
        peerCred.fillPeerCred(mLocalSocketManager.context)
    }

    /** Close client socket. */
    @Synchronized
    fun closeClientSocket(logErrorMessage: Boolean): Error? {
        return try {
            close()
            null
        } catch (e: IOException) {
            val error = LocalSocketErrno.ERRNO_CLOSE_CLIENT_SOCKET_FAILED_WITH_EXCEPTION.getError(
                e, mLocalSocketRunConfig.title, e.message
            )
            if (logErrorMessage)
                Logger.logErrorExtended(LOG_TAG, error.errorLogString)
            error
        }
    }

    /** Implementation for [Closeable.close] to close client socket. */
    @Throws(IOException::class)
    override fun close() {
        if (mFD >= 0) {
            Logger.logVerbose(LOG_TAG, "Client socket close for \"${mLocalSocketRunConfig.title}\" server: ${peerCred.getMinimalString()}")
            val result = LocalSocketManager.closeSocket("${mLocalSocketRunConfig.logTitle} (client)", mFD)
            if (result == null || result.retval != 0) {
                throw IOException(JniResult.getErrorString(result))
            }
            // Update fd to signify that client socket has been closed
            setFD(-1)
        }
    }

    /**
     * Attempts to read up to data buffer length bytes from file descriptor into the data buffer.
     */
    fun read(data: ByteArray, bytesRead: MutableInt): Error? {
        bytesRead.value = 0

        if (mFD < 0) {
            return LocalSocketErrno.ERRNO_USING_CLIENT_SOCKET_WITH_INVALID_FD.getError(
                mFD, mLocalSocketRunConfig.title
            )
        }

        val result = LocalSocketManager.read(
            "${mLocalSocketRunConfig.logTitle} (client)",
            mFD, data,
            if (mLocalSocketRunConfig.getDeadline() > 0) creationTime + mLocalSocketRunConfig.getDeadline() else 0
        )
        if (result == null || result.retval != 0) {
            return LocalSocketErrno.ERRNO_READ_DATA_FROM_CLIENT_SOCKET_FAILED.getError(
                mLocalSocketRunConfig.title, JniResult.getErrorString(result)
            )
        }

        bytesRead.value = result.intData
        return null
    }

    /**
     * Attempts to send data buffer to the file descriptor.
     */
    fun send(data: ByteArray): Error? {
        if (mFD < 0) {
            return LocalSocketErrno.ERRNO_USING_CLIENT_SOCKET_WITH_INVALID_FD.getError(
                mFD, mLocalSocketRunConfig.title
            )
        }

        val result = LocalSocketManager.send(
            "${mLocalSocketRunConfig.logTitle} (client)",
            mFD, data,
            if (mLocalSocketRunConfig.getDeadline() > 0) creationTime + mLocalSocketRunConfig.getDeadline() else 0
        )
        if (result == null || result.retval != 0) {
            return LocalSocketErrno.ERRNO_SEND_DATA_TO_CLIENT_SOCKET_FAILED.getError(
                mLocalSocketRunConfig.title, JniResult.getErrorString(result)
            )
        }

        return null
    }

    /**
     * Attempts to read all the bytes available on [SocketInputStream] and appends them to
     * [data] [StringBuilder].
     */
    fun readDataOnInputStream(data: StringBuilder, closeStreamOnFinish: Boolean): Error? {
        val inputStreamReader = getInputStreamReader()
        try {
            var c = inputStreamReader.read()
            while (c > 0) {
                data.append(c.toChar())
                c = inputStreamReader.read()
            }
        } catch (e: IOException) {
            return LocalSocketErrno.ERRNO_READ_DATA_FROM_INPUT_STREAM_OF_CLIENT_SOCKET_FAILED_WITH_EXCEPTION.getError(
                mLocalSocketRunConfig.title, DataUtils.getSpaceIndentedString(e.message, 1)
            )
        } catch (e: Exception) {
            return LocalSocketErrno.ERRNO_READ_DATA_FROM_INPUT_STREAM_OF_CLIENT_SOCKET_FAILED_WITH_EXCEPTION.getError(
                e, mLocalSocketRunConfig.title, e.message
            )
        } finally {
            if (closeStreamOnFinish) {
                try {
                    inputStreamReader.close()
                } catch (e: IOException) {
                    // Ignore
                }
            }
        }

        return null
    }

    /**
     * Attempts to send all the bytes passed to [SocketOutputStream].
     */
    fun sendDataToOutputStream(data: String, closeStreamOnFinish: Boolean): Error? {
        val outputStreamWriter = getOutputStreamWriter()

        try {
            BufferedWriter(outputStreamWriter).use { byteStreamWriter ->
                byteStreamWriter.write(data)
                byteStreamWriter.flush()
            }
        } catch (e: IOException) {
            return LocalSocketErrno.ERRNO_SEND_DATA_TO_OUTPUT_STREAM_OF_CLIENT_SOCKET_FAILED_WITH_EXCEPTION.getError(
                mLocalSocketRunConfig.title, DataUtils.getSpaceIndentedString(e.message, 1)
            )
        } catch (e: Exception) {
            return LocalSocketErrno.ERRNO_SEND_DATA_TO_OUTPUT_STREAM_OF_CLIENT_SOCKET_FAILED_WITH_EXCEPTION.getError(
                e, mLocalSocketRunConfig.title, e.message
            )
        } finally {
            if (closeStreamOnFinish) {
                try {
                    outputStreamWriter.close()
                } catch (e: IOException) {
                    // Ignore
                }
            }
        }

        return null
    }

    /** Wrapper for [available] that checks deadline. */
    fun available(available: MutableInt): Error? {
        return available(available, true)
    }

    /**
     * Get available bytes on [inputStream] and optionally check if deadline has passed.
     */
    fun available(available: MutableInt, checkDeadline: Boolean): Error? {
        available.value = 0

        if (mFD < 0) {
            return LocalSocketErrno.ERRNO_USING_CLIENT_SOCKET_WITH_INVALID_FD.getError(
                mFD, mLocalSocketRunConfig.title
            )
        }

        if (checkDeadline && mLocalSocketRunConfig.getDeadline() > 0 &&
            System.currentTimeMillis() > (creationTime + mLocalSocketRunConfig.getDeadline())
        ) {
            return null
        }

        val result = LocalSocketManager.available(
            "${mLocalSocketRunConfig.logTitle} (client)",
            mLocalSocketRunConfig.getFD()
        )
        if (result == null || result.retval != 0) {
            return LocalSocketErrno.ERRNO_CHECK_AVAILABLE_DATA_ON_CLIENT_SOCKET_FAILED.getError(
                mLocalSocketRunConfig.title, JniResult.getErrorString(result)
            )
        }

        available.value = result.intData
        return null
    }

    /** Set [LocalClientSocket] receiving (SO_RCVTIMEO) timeout. */
    fun setReadTimeout(): Error? {
        if (mFD >= 0) {
            val result = LocalSocketManager.setSocketReadTimeout(
                "${mLocalSocketRunConfig.logTitle} (client)",
                mFD, mLocalSocketRunConfig.getReceiveTimeout()
            )
            if (result == null || result.retval != 0) {
                return LocalSocketErrno.ERRNO_SET_CLIENT_SOCKET_READ_TIMEOUT_FAILED.getError(
                    mLocalSocketRunConfig.title, mLocalSocketRunConfig.getReceiveTimeout(),
                    JniResult.getErrorString(result)
                )
            }
        }
        return null
    }

    /** Set [LocalClientSocket] sending (SO_SNDTIMEO) timeout. */
    fun setWriteTimeout(): Error? {
        if (mFD >= 0) {
            val result = LocalSocketManager.setSocketSendTimeout(
                "${mLocalSocketRunConfig.logTitle} (client)",
                mFD, mLocalSocketRunConfig.getSendTimeout()
            )
            if (result == null || result.retval != 0) {
                return LocalSocketErrno.ERRNO_SET_CLIENT_SOCKET_SEND_TIMEOUT_FAILED.getError(
                    mLocalSocketRunConfig.title, mLocalSocketRunConfig.getSendTimeout(),
                    JniResult.getErrorString(result)
                )
            }
        }
        return null
    }

    /** Get [mFD] for the client socket. */
    fun getFD(): Int = mFD

    /** Set [mFD]. Value must be greater than 0 or -1. */
    private fun setFD(fd: Int) {
        mFD = if (fd >= 0) fd else -1
    }

    /** Get [OutputStreamWriter] for [outputStream]. */
    fun getOutputStreamWriter(): OutputStreamWriter = OutputStreamWriter(outputStream)

    /** Get [InputStreamReader] for [inputStream]. */
    fun getInputStreamReader(): InputStreamReader = InputStreamReader(inputStream)

    /** Get a log [String] for the [LocalClientSocket]. */
    fun getLogString(): String {
        val logString = StringBuilder()

        logString.append("Client Socket:")
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("FD", mFD, "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("Creation Time", creationTime, "-"))
        logString.append("\n\n\n")

        logString.append(peerCred.getLogString())

        return logString.toString()
    }

    /** Get a markdown [String] for the [LocalClientSocket]. */
    fun getMarkdownString(): String {
        val markdownString = StringBuilder()

        markdownString.append("## ").append("Client Socket")
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("FD", mFD, "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Creation Time", creationTime, "-"))
        markdownString.append("\n\n\n")

        markdownString.append(peerCred.getMarkdownString())

        return markdownString.toString()
    }

    /** Wrapper class to allow pass by reference of int values. */
    class MutableInt(@JvmField var value: Int)

    /** The [InputStream] implementation for the [LocalClientSocket]. */
    protected inner class SocketInputStream : InputStream() {
        private val mBytes = ByteArray(1)

        @Throws(IOException::class)
        override fun read(): Int {
            val bytesRead = MutableInt(0)
            val error = this@LocalClientSocket.read(mBytes, bytesRead)
            if (error != null) {
                throw IOException(error.errorMarkdownString)
            }

            return if (bytesRead.value == 0) -1 else mBytes[0].toInt()
        }

        @Throws(IOException::class)
        override fun read(bytes: ByteArray?): Int {
            if (bytes == null) {
                throw NullPointerException("Read buffer can't be null")
            }

            val bytesRead = MutableInt(0)
            val error = this@LocalClientSocket.read(bytes, bytesRead)
            if (error != null) {
                throw IOException(error.errorMarkdownString)
            }

            return if (bytesRead.value == 0) -1 else bytesRead.value
        }

        @Throws(IOException::class)
        override fun available(): Int {
            val available = MutableInt(0)
            val error = this@LocalClientSocket.available(available)
            if (error != null) {
                throw IOException(error.errorMarkdownString)
            }
            return available.value
        }
    }

    /** The [OutputStream] implementation for the [LocalClientSocket]. */
    protected inner class SocketOutputStream : OutputStream() {
        private val mBytes = ByteArray(1)

        @Throws(IOException::class)
        override fun write(b: Int) {
            mBytes[0] = b.toByte()

            val error = this@LocalClientSocket.send(mBytes)
            if (error != null) {
                throw IOException(error.errorMarkdownString)
            }
        }

        @Throws(IOException::class)
        override fun write(bytes: ByteArray) {
            val error = this@LocalClientSocket.send(bytes)
            if (error != null) {
                throw IOException(error.errorMarkdownString)
            }
        }
    }

    companion object {
        const val LOG_TAG = "LocalClientSocket"

        /** Close client socket that exists at fd. */
        @JvmStatic
        fun closeClientSocket(localSocketManager: LocalSocketManager, fd: Int) {
            LocalClientSocket(localSocketManager, fd, PeerCred()).closeClientSocket(true)
        }
    }
}
