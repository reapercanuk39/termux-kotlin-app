package com.termux.shared.termux.shell.am

import android.content.Context
import androidx.annotation.Keep
import com.termux.shared.errors.Error
import com.termux.shared.logger.Logger
import com.termux.shared.net.socket.local.LocalClientSocket
import com.termux.shared.net.socket.local.LocalServerSocket
import com.termux.shared.net.socket.local.LocalSocketManager
import com.termux.shared.net.socket.local.LocalSocketManagerClientBase
import com.termux.shared.net.socket.local.LocalSocketRunConfig
import com.termux.shared.shell.am.AmSocketServerRunConfig
import com.termux.shared.shell.am.AmSocketServer
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.crash.TermuxCrashUtils
import com.termux.shared.termux.plugins.TermuxPluginUtils
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import com.termux.shared.termux.shell.command.environment.TermuxAppShellEnvironment

/**
 * A wrapper for [AmSocketServer] for termux-app usage.
 *
 * The static [termuxAmSocketServer] variable stores the [LocalSocketManager] for the
 * [AmSocketServer].
 *
 * The [TermuxAmSocketServerClient] extends the [AmSocketServer.AmSocketServerClient]
 * class to also show plugin error notifications for errors and disallowed client connections in
 * addition to logging the messages to logcat, which are only logged by [LocalSocketManagerClientBase]
 * if log level is debug or higher for privacy issues.
 *
 * It uses a filesystem socket server with the socket file at
 * [TermuxConstants.TERMUX_APP.TERMUX_AM_SOCKET_FILE_PATH]. It would normally only allow
 * processes belonging to the termux user and root user to connect to it. If commands are sent by the
 * root user, then the am commands executed will be run as the termux user and its permissions,
 * capabilities and selinux context instead of root.
 *
 * The `$PREFIX/bin/termux-am` client connects to the server via `$PREFIX/bin/termux-am-socket` to
 * run the am commands. It provides similar functionality to "$PREFIX/bin/am"
 * (and "/system/bin/am"), but should be faster since it does not require starting a dalvik vm for
 * every command as done by "am" via termux/TermuxAm.
 *
 * The server is started by termux-app Application class but is not started if
 * [TermuxPropertyConstants.KEY_RUN_TERMUX_AM_SOCKET_SERVER] is `false` which can be done by
 * adding the prop with value "false" to the "~/.termux/termux.properties" file. Changes
 * require termux-app to be force stopped and restarted.
 *
 * The current state of the server can be checked with the
 * [TermuxAppShellEnvironment.ENV_TERMUX_APP__AM_SOCKET_SERVER_ENABLED] env variable, which is exported
 * for all shell sessions and tasks.
 *
 * https://github.com/termux/termux-am-socket
 * https://github.com/termux/TermuxAm
 */
object TermuxAmSocketServer {

    const val LOG_TAG: String = "TermuxAmSocketServer"

    const val TITLE: String = "TermuxAm"

    /** The static instance for the [TermuxAmSocketServer] [LocalSocketManager]. */
    private var termuxAmSocketServer: LocalSocketManager? = null

    /** Whether [TermuxAmSocketServer] is enabled and running or not. */
    @Keep
    @JvmField
    var TERMUX_APP_AM_SOCKET_SERVER_ENABLED: Boolean? = null

    /**
     * Setup the [AmSocketServer] [LocalServerSocket] and start listening for
     * new [LocalClientSocket] if enabled.
     *
     * @param context The [Context] for [LocalSocketManager].
     */
    @JvmStatic
    fun setupTermuxAmSocketServer(context: Context) {
        // Start termux-am-socket server if enabled by user
        var enabled = false
        if (TermuxAppSharedProperties.getProperties()?.shouldRunTermuxAmSocketServer() == true) {
            Logger.logDebug(LOG_TAG, "Starting $TITLE socket server since its enabled")
            start(context)
            termuxAmSocketServer?.let {
                if (it.isRunning) {
                    enabled = true
                    Logger.logDebug(LOG_TAG, "$TITLE socket server successfully started")
                }
            }
        } else {
            Logger.logDebug(LOG_TAG, "Not starting $TITLE socket server since its not enabled")
        }

        // Once termux-app has started, the server state must not be changed since the variable is
        // exported in shell sessions and tasks and if state is changed, then env of older shells will
        // retain invalid value. User should force stop the app to update state after changing prop.
        TERMUX_APP_AM_SOCKET_SERVER_ENABLED = enabled
        TermuxAppShellEnvironment.updateTermuxAppAMSocketServerEnabled(context)
    }

    /**
     * Create the [AmSocketServer] [LocalServerSocket] and start listening for new [LocalClientSocket].
     */
    @JvmStatic
    @Synchronized
    fun start(context: Context) {
        stop()

        val amSocketServerRunConfig = AmSocketServerRunConfig(
            TITLE,
            TermuxConstants.TERMUX_APP.TERMUX_AM_SOCKET_FILE_PATH,
            TermuxAmSocketServerClient()
        )

        termuxAmSocketServer = AmSocketServer.start(context, amSocketServerRunConfig)
    }

    /**
     * Stop the [AmSocketServer] [LocalServerSocket] and stop listening for new [LocalClientSocket].
     */
    @JvmStatic
    @Synchronized
    fun stop() {
        termuxAmSocketServer?.let {
            val error = it.stop()
            if (error != null) {
                it.onError(error)
            }
        }
        termuxAmSocketServer = null
    }

    /**
     * Update the state of the [AmSocketServer] [LocalServerSocket] depending on current
     * value of [TermuxPropertyConstants.KEY_RUN_TERMUX_AM_SOCKET_SERVER].
     */
    @JvmStatic
    @Synchronized
    fun updateState(context: Context) {
        val properties = TermuxAppSharedProperties.getProperties()
        if (properties?.shouldRunTermuxAmSocketServer() == true) {
            if (termuxAmSocketServer == null) {
                Logger.logDebug(LOG_TAG, "updateState: Starting $TITLE socket server")
                start(context)
            }
        } else {
            if (termuxAmSocketServer != null) {
                Logger.logDebug(LOG_TAG, "updateState: Disabling $TITLE socket server")
                stop()
            }
        }
    }

    /**
     * Get [termuxAmSocketServer].
     */
    @JvmStatic
    @Synchronized
    fun getTermuxAmSocketServer(): LocalSocketManager? {
        return termuxAmSocketServer
    }

    /**
     * Show an error notification on the [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID]
     * [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME] with a call
     * to [TermuxPluginUtils.sendPluginCommandErrorNotification].
     *
     * @param context The [Context] to send the notification with.
     * @param error The [Error] generated.
     * @param localSocketRunConfig The [LocalSocketRunConfig] for [LocalSocketManager].
     * @param clientSocket The optional [LocalClientSocket] for which the error was generated.
     */
    @JvmStatic
    @Synchronized
    fun showErrorNotification(
        context: Context,
        error: Error,
        localSocketRunConfig: LocalSocketRunConfig,
        clientSocket: LocalClientSocket?
    ) {
        TermuxPluginUtils.sendPluginCommandErrorNotification(
            context, LOG_TAG,
            localSocketRunConfig.title + " Socket Server Error", error.minimalErrorString,
            LocalSocketManager.getErrorMarkdownString(error, localSocketRunConfig, clientSocket)
        )
    }

    @JvmStatic
    fun getTermuxAppAMSocketServerEnabled(currentPackageContext: Context): Boolean? {
        val isTermuxApp = TermuxConstants.TERMUX_PACKAGE_NAME == currentPackageContext.packageName
        return if (isTermuxApp) {
            TERMUX_APP_AM_SOCKET_SERVER_ENABLED
        } else {
            // Currently, unsupported since plugin app processes don't know that value is set in termux
            // app process TermuxAmSocketServer class. A binder API or a way to check if server is actually
            // running needs to be used. Long checks would also not be possible on main application thread
            null
        }
    }

    /** Enhanced implementation for [AmSocketServer.AmSocketServerClient] for [TermuxAmSocketServer]. */
    class TermuxAmSocketServerClient : AmSocketServer.AmSocketServerClient() {

        override fun getLocalSocketManagerClientThreadUEH(
            localSocketManager: LocalSocketManager
        ): Thread.UncaughtExceptionHandler? {
            // Use termux crash handler for socket listener thread just like used for main app process thread.
            return TermuxCrashUtils.getCrashHandler(localSocketManager.context)
        }

        override fun onError(
            localSocketManager: LocalSocketManager,
            clientSocket: LocalClientSocket?,
            error: Error
        ) {
            // Don't show notification if server is not running since errors may be triggered
            // when server is stopped and server and client sockets are closed.
            if (localSocketManager.isRunning) {
                showErrorNotification(
                    localSocketManager.context, error,
                    localSocketManager.localSocketRunConfig, clientSocket
                )
            }

            // But log the exception
            super.onError(localSocketManager, clientSocket, error)
        }

        override fun onDisallowedClientConnected(
            localSocketManager: LocalSocketManager,
            clientSocket: LocalClientSocket,
            error: Error
        ) {
            // Always show notification and log error regardless of if server is running or not
            showErrorNotification(
                localSocketManager.context, error,
                localSocketManager.localSocketRunConfig, clientSocket
            )
            super.onDisallowedClientConnected(localSocketManager, clientSocket, error)
        }

        override fun getLogTag(): String {
            return LOG_TAG
        }

        companion object {
            const val LOG_TAG: String = "TermuxAmSocketServerClient"
        }
    }
}
