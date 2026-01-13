package com.termux.shared.shell.am

import android.Manifest
import android.app.Application
import android.content.Context
import com.termux.am.Am
import com.termux.shared.R
import com.termux.shared.android.PackageUtils
import com.termux.shared.android.PermissionUtils
import com.termux.shared.errors.Error
import com.termux.shared.logger.Logger
import com.termux.shared.net.socket.local.ILocalSocketManager
import com.termux.shared.net.socket.local.LocalClientSocket
import com.termux.shared.net.socket.local.LocalServerSocket
import com.termux.shared.net.socket.local.LocalSocketManager
import com.termux.shared.net.socket.local.LocalSocketManagerClientBase
import com.termux.shared.net.socket.local.LocalSocketRunConfig
import com.termux.shared.shell.ArgumentTokenizer
import com.termux.shared.shell.command.ExecutionCommand
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * A AF_UNIX/SOCK_STREAM local server managed with [LocalSocketManager] whose
 * [LocalServerSocket] receives android activity manager (am) commands from [LocalClientSocket]
 * and runs them with termux-am-library. It would normally only allow processes belonging to the
 * server app's user and root user to connect to it.
 *
 * The client must send the am command as a string without the initial "am" arg on its output stream
 * and then wait for the result on its input stream. The result of the execution or error is sent
 * back in the format `exit_code\0stdout\0stderr\0` where `\0` represents a null character.
 * Check termux/termux-am-socket for implementation of a native c client.
 *
 * Usage:
 * 1. Optionally extend [AmSocketServerClient], the implementation for
 *    [ILocalSocketManager] that will receive call backs from the server including
 *    when client connects via [ILocalSocketManager.onClientAccepted].
 * 2. Create a [AmSocketServerRunConfig] instance which extends from [LocalSocketRunConfig]
 *    with the run config of the am server. It would  be better to use a filesystem socket instead
 *    of abstract namespace socket for security reasons.
 * 3. Call [start] to start the server and store the [LocalSocketManager]
 *    instance returned.
 * 4. Stop server if needed with a call to [LocalSocketManager.stop] on the
 *    [LocalSocketManager] instance returned by start call.
 *
 * https://github.com/termux/termux-am-library/blob/main/termux-am-library/src/main/java/com/termux/am/Am.java
 * https://github.com/termux/termux-am-socket
 * https://developer.android.com/studio/command-line/adb#am
 * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/am/ActivityManagerShellCommand.java
 */
object AmSocketServer {

    const val LOG_TAG: String = "AmSocketServer"

    /**
     * Create the [AmSocketServer] [LocalServerSocket] and start listening for new [LocalClientSocket].
     *
     * @param context The [Context] for [LocalSocketManager].
     * @param localSocketRunConfig The [LocalSocketRunConfig] for [LocalSocketManager].
     */
    @JvmStatic
    @Synchronized
    fun start(context: Context, localSocketRunConfig: LocalSocketRunConfig): LocalSocketManager? {
        val localSocketManager = LocalSocketManager(context, localSocketRunConfig)
        val error = localSocketManager.start()
        if (error != null) {
            localSocketManager.onError(error)
            return null
        }

        return localSocketManager
    }

    @JvmStatic
    fun processAmClient(localSocketManager: LocalSocketManager, clientSocket: LocalClientSocket) {
        var error: Error?

        // Read amCommandString client sent and close input stream
        val data = StringBuilder()
        error = clientSocket.readDataOnInputStream(data, true)
        if (error != null) {
            sendResultToClient(localSocketManager, clientSocket, 1, null, error.toString())
            return
        }

        val amCommandString = data.toString()

        Logger.logVerbose(LOG_TAG, "am command received from peer " + clientSocket.peerCred.getMinimalString() +
            "\nam command: `$amCommandString`")

        // Parse am command string and convert it to a list of arguments
        val amCommandList = ArrayList<String>()
        error = parseAmCommand(amCommandString, amCommandList)
        if (error != null) {
            sendResultToClient(localSocketManager, clientSocket, 1, null, error.toString())
            return
        }

        val amCommandArray = amCommandList.toTypedArray()

        Logger.logDebug(LOG_TAG, "am command received from peer " + clientSocket.peerCred.getMinimalString() +
            "\n" + ExecutionCommand.getArgumentsLogString("am command", amCommandArray))

        val amSocketServerRunConfig = localSocketManager.localSocketRunConfig as AmSocketServerRunConfig

        // Run am command and send its result to the client
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        error = runAmCommand(
            localSocketManager.context, amCommandArray, stdout, stderr,
            amSocketServerRunConfig.shouldCheckDisplayOverAppsPermission()
        )
        if (error != null) {
            sendResultToClient(
                localSocketManager, clientSocket, 1, stdout.toString(),
                if (stderr.toString().isNotEmpty()) "$stderr\n\n$error" else error.toString()
            )
        }

        sendResultToClient(localSocketManager, clientSocket, 0, stdout.toString(), stderr.toString())
    }

    /**
     * Send result to [LocalClientSocket] that requested the am command to be run.
     *
     * @param localSocketManager The [LocalSocketManager] instance for the local socket.
     * @param clientSocket The [LocalClientSocket] to which the result is to be sent.
     * @param exitCode The exit code value to send.
     * @param stdout The stdout value to send.
     * @param stderr The stderr value to send.
     */
    @JvmStatic
    fun sendResultToClient(
        localSocketManager: LocalSocketManager,
        clientSocket: LocalClientSocket,
        exitCode: Int,
        stdout: String?, stderr: String?
    ) {
        val result = StringBuilder()
        result.append(sanitizeExitCode(clientSocket, exitCode))
        result.append('\u0000')
        result.append(stdout ?: "")
        result.append('\u0000')
        result.append(stderr ?: "")

        // Send result to client and close output stream
        val error = clientSocket.sendDataToOutputStream(result.toString(), true)
        if (error != null) {
            localSocketManager.onError(clientSocket, error)
        }
    }

    /**
     * Sanitize exitCode to between 0-255, otherwise it may be considered invalid.
     * Out of bound exit codes would return with exit code `44` `Channel number out of range` in shell.
     *
     * @param clientSocket The [LocalClientSocket] to which the exit code will be sent.
     * @param exitCode The current exit code.
     * @return Returns the sanitized exit code.
     */
    @JvmStatic
    fun sanitizeExitCode(clientSocket: LocalClientSocket, exitCode: Int): Int {
        var sanitizedExitCode = exitCode
        if (exitCode < 0 || exitCode > 255) {
            Logger.logWarn(LOG_TAG, "Ignoring invalid peer " + clientSocket.peerCred.getMinimalString() + " result value \"$exitCode\" and force setting it to \"1\"")
            sanitizedExitCode = 1
        }

        return sanitizedExitCode
    }

    /**
     * Parse amCommandString into a list of arguments like normally done on shells like bourne shell.
     * Arguments are split on whitespaces unless quoted with single or double quotes.
     * Double quotes and backslashes can be escaped with backslashes in arguments surrounded.
     * Double quotes and backslashes can be escaped with backslashes in arguments surrounded with
     * double quotes.
     *
     * @param amCommandString The am command [String].
     * @param amCommandList The [List] to set list of arguments in.
     * @return Returns the `error` if parsing am command failed, otherwise `null`.
     */
    @JvmStatic
    fun parseAmCommand(amCommandString: String?, amCommandList: MutableList<String>): Error? {
        if (amCommandString.isNullOrEmpty()) {
            return null
        }

        try {
            amCommandList.addAll(ArgumentTokenizer.tokenize(amCommandString))
        } catch (e: Exception) {
            return AmSocketServerErrno.ERRNO_PARSE_AM_COMMAND_FAILED_WITH_EXCEPTION.getError(e, amCommandString, e.message)
        }

        return null
    }

    /**
     * Call termux-am-library to run the am command.
     *
     * @param context The [Context] to run am command with.
     * @param amCommandArray The am command array.
     * @param stdout The [StringBuilder] to set stdout in that is returned by the am command.
     * @param stderr The [StringBuilder] to set stderr in that is returned by the am command.
     * @param checkDisplayOverAppsPermission Check if [Manifest.permission.SYSTEM_ALERT_WINDOW]
     *                                       has been granted if running on Android `>= 10` and
     *                                       starting activity or service.
     * @return Returns the `error` if am command failed, otherwise `null`.
     */
    @JvmStatic
    fun runAmCommand(
        context: Context,
        amCommandArray: Array<String>,
        stdout: StringBuilder, stderr: StringBuilder,
        checkDisplayOverAppsPermission: Boolean
    ): Error? {
        try {
            ByteArrayOutputStream().use { stdoutByteStream ->
                PrintStream(stdoutByteStream).use { stdoutPrintStream ->
                    ByteArrayOutputStream().use { stderrByteStream ->
                        PrintStream(stderrByteStream).use { stderrPrintStream ->
                            if (checkDisplayOverAppsPermission && amCommandArray.isNotEmpty() &&
                                (amCommandArray[0] == "start" || amCommandArray[0] == "startservice") &&
                                !PermissionUtils.validateDisplayOverOtherAppsPermissionForPostAndroid10(context, true)
                            ) {
                                throw IllegalStateException(
                                    context.getString(
                                        R.string.error_display_over_other_apps_permission_not_granted,
                                        PackageUtils.getAppNameForPackage(context)
                                    )
                                )
                            }

                            Am(stdoutPrintStream, stderrPrintStream, context.applicationContext as Application).run(amCommandArray)

                            // Set stdout to value set by am command in stdoutPrintStream
                            stdoutPrintStream.flush()
                            stdout.append(stdoutByteStream.toString(StandardCharsets.UTF_8.name()))

                            // Set stderr to value set by am command in stderrPrintStream
                            stderrPrintStream.flush()
                            stderr.append(stderrByteStream.toString(StandardCharsets.UTF_8.name()))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return AmSocketServerErrno.ERRNO_RUN_AM_COMMAND_FAILED_WITH_EXCEPTION.getError(e, Arrays.toString(amCommandArray), e.message)
        }

        return null
    }

    /** Implementation for [ILocalSocketManager] for [AmSocketServer]. */
    abstract class AmSocketServerClient : LocalSocketManagerClientBase() {

        override fun onClientAccepted(localSocketManager: LocalSocketManager, clientSocket: LocalClientSocket) {
            processAmClient(localSocketManager, clientSocket)
            super.onClientAccepted(localSocketManager, clientSocket)
        }
    }
}
