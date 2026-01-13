package com.termux.terminal

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Message
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Field
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * A terminal session, consisting of a process coupled to a terminal interface.
 *
 * The subprocess will be executed by the constructor, and when the size is made known by a call to
 * [updateSize] terminal emulation will begin and threads will be spawned to handle the subprocess I/O.
 * All terminal emulation and callback methods will be performed on the main thread.
 *
 * The child process may be exited forcefully by using the [finishIfRunning] method.
 *
 * NOTE: The terminal session may outlive the EmulatorView, so be careful with callbacks!
 */
class TerminalSession(
    private val mShellPath: String,
    private val mCwd: String,
    private val mArgs: Array<String>,
    private val mEnv: Array<String>,
    private val mTranscriptRows: Int?,
    client: TerminalSessionClient
) : TerminalOutput() {

    @JvmField
    val mHandle: String = UUID.randomUUID().toString()

    @JvmField
    var mEmulator: TerminalEmulator? = null
    
    /** Property accessor for mEmulator for Kotlin interop */
    val emulator: TerminalEmulator?
        get() = mEmulator

    /** A queue written to from a separate thread when the process outputs, and read by main thread to process by terminal emulator. */
    @JvmField
    internal val mProcessToTerminalIOQueue = ByteQueue(4096)

    /** A queue written to from the main thread due to user interaction, and read by another thread which forwards by writing to the [mTerminalFileDescriptor]. */
    @JvmField
    internal val mTerminalToProcessIOQueue = ByteQueue(4096)

    /** Buffer to write translate code points into utf8 before writing to mTerminalToProcessIOQueue */
    private val mUtf8InputBuffer = ByteArray(5)

    /** Callback which gets notified when a session finishes or changes title. */
    @JvmField
    var mClient: TerminalSessionClient = client

    /** The pid of the shell process. 0 if not started and -1 if finished running. */
    @JvmField
    var mShellPid: Int = 0

    /** The exit status of the shell process. Only valid if [mShellPid] is -1. */
    @JvmField
    var mShellExitStatus: Int = 0

    /** The file descriptor referencing the master half of a pseudo-terminal pair. */
    private var mTerminalFileDescriptor: Int = 0

    /** Set by the application for user identification of session, not by terminal. */
    @JvmField
    var mSessionName: String? = null

    @JvmField
    val mMainThreadHandler: Handler = MainThreadHandler()

    /**
     * @param client The [TerminalSessionClient] interface implementation to allow
     * for communication between [TerminalSession] and its client.
     */
    fun updateTerminalSessionClient(client: TerminalSessionClient) {
        mClient = client
        mEmulator?.updateTerminalSessionClient(client)
    }

    /** Inform the attached pty of the new size and reflow or initialize the emulator. */
    fun updateSize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels)
        } else {
            JNI.setPtyWindowSize(mTerminalFileDescriptor, rows, columns, cellWidthPixels, cellHeightPixels)
            mEmulator!!.resize(columns, rows, cellWidthPixels, cellHeightPixels)
        }
    }

    /** The terminal title as set through escape sequences or null if none set. */
    val title: String?
        get() = mEmulator?.title

    /**
     * Set the terminal emulator's window size and start terminal emulation.
     */
    fun initializeEmulator(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        mEmulator = TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows ?: 0, mClient)

        val processId = IntArray(1)
        mTerminalFileDescriptor = JNI.createSubprocess(mShellPath, mCwd, mArgs, mEnv, processId, rows, columns, cellWidthPixels, cellHeightPixels)
        mShellPid = processId[0]
        mClient.setTerminalShellPid(this, mShellPid)

        val terminalFileDescriptorWrapped = wrapFileDescriptor(mTerminalFileDescriptor, mClient)

        object : Thread("TermSessionInputReader[pid=$mShellPid]") {
            override fun run() {
                try {
                    FileInputStream(terminalFileDescriptorWrapped).use { termIn ->
                        val buffer = ByteArray(4096)
                        while (true) {
                            val read = termIn.read(buffer)
                            if (read == -1) return
                            if (!mProcessToTerminalIOQueue.write(buffer, 0, read)) return
                            mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore, just shutting down.
                }
            }
        }.start()

        object : Thread("TermSessionOutputWriter[pid=$mShellPid]") {
            override fun run() {
                val buffer = ByteArray(4096)
                try {
                    FileOutputStream(terminalFileDescriptorWrapped).use { termOut ->
                        while (true) {
                            val bytesToWrite = mTerminalToProcessIOQueue.read(buffer, true)
                            if (bytesToWrite == -1) return
                            termOut.write(buffer, 0, bytesToWrite)
                        }
                    }
                } catch (e: IOException) {
                    // Ignore.
                }
            }
        }.start()

        object : Thread("TermSessionWaiter[pid=$mShellPid]") {
            override fun run() {
                val processExitCode = JNI.waitFor(mShellPid)
                mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, processExitCode))
            }
        }.start()
    }

    /** Write data to the shell process. */
    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (mShellPid > 0) mTerminalToProcessIOQueue.write(data, offset, count)
    }

    /** Write the Unicode code point to the terminal encoded in UTF-8. */
    fun writeCodePoint(prependEscape: Boolean, codePoint: Int) {
        if (codePoint > 1114111 || (codePoint in 0xD800..0xDFFF)) {
            throw IllegalArgumentException("Invalid code point: $codePoint")
        }

        var bufferPosition = 0
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27

        when {
            codePoint <= 0b1111111 -> {
                mUtf8InputBuffer[bufferPosition++] = codePoint.toByte()
            }
            codePoint <= 0b11111111111 -> {
                mUtf8InputBuffer[bufferPosition++] = (0b11000000 or (codePoint shr 6)).toByte()
                mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint and 0b111111)).toByte()
            }
            codePoint <= 0b1111111111111111 -> {
                mUtf8InputBuffer[bufferPosition++] = (0b11100000 or (codePoint shr 12)).toByte()
                mUtf8InputBuffer[bufferPosition++] = (0b10000000 or ((codePoint shr 6) and 0b111111)).toByte()
                mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint and 0b111111)).toByte()
            }
            else -> {
                mUtf8InputBuffer[bufferPosition++] = (0b11110000 or (codePoint shr 18)).toByte()
                mUtf8InputBuffer[bufferPosition++] = (0b10000000 or ((codePoint shr 12) and 0b111111)).toByte()
                mUtf8InputBuffer[bufferPosition++] = (0b10000000 or ((codePoint shr 6) and 0b111111)).toByte()
                mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint and 0b111111)).toByte()
            }
        }
        write(mUtf8InputBuffer, 0, bufferPosition)
    }

    /** Notify the [mClient] that the screen has changed. */
    fun notifyScreenUpdate() {
        mClient.onTextChanged(this)
    }

    /** Reset state for terminal emulator state. */
    fun reset() {
        mEmulator?.reset()
        notifyScreenUpdate()
    }

    /** Finish this terminal session by sending SIGKILL to the shell. */
    fun finishIfRunning() {
        if (isRunning) {
            try {
                Os.kill(mShellPid, OsConstants.SIGKILL)
            } catch (e: ErrnoException) {
                Logger.logWarn(mClient, LOG_TAG, "Failed sending SIGKILL: ${e.message}")
            }
        }
    }

    /** Cleanup resources when the process exits. */
    fun cleanupResources(exitStatus: Int) {
        synchronized(this) {
            mShellPid = -1
            mShellExitStatus = exitStatus
        }

        // Stop the reader and writer threads, and close the I/O streams
        mTerminalToProcessIOQueue.close()
        mProcessToTerminalIOQueue.close()
        JNI.close(mTerminalFileDescriptor)
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) {
        mClient.onTitleChanged(this)
    }

    val isRunning: Boolean
        @Synchronized get() = mShellPid != -1

    /** Only valid if not [isRunning]. */
    val exitStatus: Int
        @Synchronized get() = mShellExitStatus

    override fun onCopyTextToClipboard(text: String?) {
        mClient.onCopyTextToClipboard(this, text)
    }

    override fun onPasteTextFromClipboard() {
        mClient.onPasteTextFromClipboard(this)
    }

    override fun onBell() {
        mClient.onBell(this)
    }

    override fun onColorsChanged() {
        mClient.onColorsChanged(this)
    }

    fun getPid(): Int = mShellPid

    /** Returns the shell's working directory or null if it was unavailable. */
    fun getCwd(): String? {
        if (mShellPid < 1) return null
        return try {
            val cwdSymlink = "/proc/$mShellPid/cwd/"
            val outputPath = File(cwdSymlink).canonicalPath
            val outputPathWithTrailingSlash = if (outputPath.endsWith("/")) outputPath else "$outputPath/"
            if (cwdSymlink != outputPathWithTrailingSlash) outputPath else null
        } catch (e: IOException) {
            Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Error getting current directory", e)
            null
        } catch (e: SecurityException) {
            Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Error getting current directory", e)
            null
        }
    }

    @SuppressLint("HandlerLeak")
    inner class MainThreadHandler : Handler() {

        val mReceiveBuffer = ByteArray(4 * 1024)

        override fun handleMessage(msg: Message) {
            val bytesRead = mProcessToTerminalIOQueue.read(mReceiveBuffer, false)
            if (bytesRead > 0) {
                mEmulator?.append(mReceiveBuffer, bytesRead)
                notifyScreenUpdate()
            }

            if (msg.what == MSG_PROCESS_EXITED) {
                val exitCode = msg.obj as Int
                cleanupResources(exitCode)

                var exitDescription = "\r\n[Process completed"
                if (exitCode > 0) {
                    exitDescription += " (code $exitCode)"
                } else if (exitCode < 0) {
                    exitDescription += " (signal ${-exitCode})"
                }
                exitDescription += " - press Enter]"

                val bytesToWrite = exitDescription.toByteArray(StandardCharsets.UTF_8)
                mEmulator?.append(bytesToWrite, bytesToWrite.size)
                notifyScreenUpdate()

                mClient.onSessionFinished(this@TerminalSession)
            }
        }
    }

    companion object {
        private const val MSG_NEW_INPUT = 1
        private const val MSG_PROCESS_EXITED = 4
        private const val LOG_TAG = "TerminalSession"

        private fun wrapFileDescriptor(fileDescriptor: Int, client: TerminalSessionClient): FileDescriptor {
            val result = FileDescriptor()
            try {
                val descriptorField: Field = try {
                    FileDescriptor::class.java.getDeclaredField("descriptor")
                } catch (e: NoSuchFieldException) {
                    // For desktop java:
                    FileDescriptor::class.java.getDeclaredField("fd")
                }
                descriptorField.isAccessible = true
                descriptorField.set(result, fileDescriptor)
            } catch (e: NoSuchFieldException) {
                Logger.logStackTraceWithMessage(client, LOG_TAG, "Error accessing FileDescriptor#descriptor private field", e)
                System.exit(1)
            } catch (e: IllegalAccessException) {
                Logger.logStackTraceWithMessage(client, LOG_TAG, "Error accessing FileDescriptor#descriptor private field", e)
                System.exit(1)
            } catch (e: IllegalArgumentException) {
                Logger.logStackTraceWithMessage(client, LOG_TAG, "Error accessing FileDescriptor#descriptor private field", e)
                System.exit(1)
            }
            return result
        }
    }
}
