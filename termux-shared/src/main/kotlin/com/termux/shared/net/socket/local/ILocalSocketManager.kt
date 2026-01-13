package com.termux.shared.net.socket.local

import com.termux.shared.errors.Error

/**
 * The interface for the [LocalSocketManager] for callbacks to manager client/server starter.
 */
interface ILocalSocketManager {

    /**
     * This should return the [Thread.UncaughtExceptionHandler] that should be used for the
     * client socket listener and client logic runner threads started for other interface methods.
     *
     * @param localSocketManager The [LocalSocketManager] for the server.
     * @return Should return [Thread.UncaughtExceptionHandler] or null, if default
     * handler should be used which just logs the exception.
     */
    fun getLocalSocketManagerClientThreadUEH(
        localSocketManager: LocalSocketManager
    ): Thread.UncaughtExceptionHandler?

    /**
     * This is called if any error is raised by [LocalSocketManager], [LocalServerSocket]
     * or [LocalClientSocket]. The server will automatically close the client socket
     * with a call to [LocalClientSocket.closeClientSocket] if the error occurred due
     * to the client.
     *
     * The [LocalClientSocket.getPeerCred] can be used to get the [PeerCred] object
     * containing info for the connected client/peer.
     *
     * @param localSocketManager The [LocalSocketManager] for the server.
     * @param clientSocket The [LocalClientSocket] that connected. This will be null
     *                     if error is not for a [LocalClientSocket].
     * @param error The [Error] auto generated that can be used for logging purposes.
     */
    fun onError(
        localSocketManager: LocalSocketManager,
        clientSocket: LocalClientSocket?,
        error: Error
    )

    /**
     * This is called if a [LocalServerSocket] connects to the server which **does not** have
     * the server app's user id or root user id. The server will automatically close the client socket
     * with a call to [LocalClientSocket.closeClientSocket].
     *
     * The [LocalClientSocket.getPeerCred] can be used to get the [PeerCred] object
     * containing info for the connected client/peer.
     *
     * @param localSocketManager The [LocalSocketManager] for the server.
     * @param clientSocket The [LocalClientSocket] that connected.
     * @param error The [Error] auto generated that can be used for logging purposes.
     */
    fun onDisallowedClientConnected(
        localSocketManager: LocalSocketManager,
        clientSocket: LocalClientSocket,
        error: Error
    )

    /**
     * This is called if a [LocalServerSocket] connects to the server which has the
     * the server app's user id or root user id. It is the responsibility of the interface
     * implementation to close the client socket with a call to
     * [LocalClientSocket.closeClientSocket] once its done processing.
     *
     * The [LocalClientSocket.getPeerCred] can be used to get the [PeerCred] object
     * containing info for the connected client/peer.
     *
     * @param localSocketManager The [LocalSocketManager] for the server.
     * @param clientSocket The [LocalClientSocket] that connected.
     */
    fun onClientAccepted(
        localSocketManager: LocalSocketManager,
        clientSocket: LocalClientSocket
    )
}
