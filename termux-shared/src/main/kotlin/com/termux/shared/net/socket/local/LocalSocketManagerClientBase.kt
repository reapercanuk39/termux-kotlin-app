package com.termux.shared.net.socket.local

import com.termux.shared.errors.Error
import com.termux.shared.logger.Logger

/** Base helper implementation for [ILocalSocketManager]. */
abstract class LocalSocketManagerClientBase : ILocalSocketManager {

    override fun getLocalSocketManagerClientThreadUEH(
        localSocketManager: LocalSocketManager
    ): Thread.UncaughtExceptionHandler? = null

    override fun onError(
        localSocketManager: LocalSocketManager,
        clientSocket: LocalClientSocket?,
        error: Error
    ) {
        // Only log if log level is debug or higher since PeerCred.cmdline may contain private info
        Logger.logErrorPrivate(getLogTag(), "onError")
        Logger.logErrorPrivateExtended(
            getLogTag(),
            LocalSocketManager.getErrorLogString(
                error,
                localSocketManager.localSocketRunConfig,
                clientSocket
            )
        )
    }

    override fun onDisallowedClientConnected(
        localSocketManager: LocalSocketManager,
        clientSocket: LocalClientSocket,
        error: Error
    ) {
        Logger.logWarn(getLogTag(), "onDisallowedClientConnected")
        Logger.logWarnExtended(
            getLogTag(),
            LocalSocketManager.getErrorLogString(
                error,
                localSocketManager.localSocketRunConfig,
                clientSocket
            )
        )
    }

    override fun onClientAccepted(
        localSocketManager: LocalSocketManager,
        clientSocket: LocalClientSocket
    ) {
        // Just close socket and let child class handle any required communication
        clientSocket.closeClientSocket(true)
    }

    protected abstract fun getLogTag(): String
}
