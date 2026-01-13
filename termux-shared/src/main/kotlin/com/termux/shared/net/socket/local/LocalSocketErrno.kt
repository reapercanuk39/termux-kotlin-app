package com.termux.shared.net.socket.local

import com.termux.shared.errors.Errno

open class LocalSocketErrno(type: String, code: Int, message: String) : Errno(type, code, message) {
    companion object {
        const val TYPE = "LocalSocket Error"

        // Errors for LocalSocketManager (100-150)
        @JvmField
        val ERRNO_START_LOCAL_SOCKET_LIB_LOAD_FAILED_WITH_EXCEPTION = Errno(TYPE, 100, "Failed to load \"%1\$s\" library.\nException: %2\$s")

        // Errors for LocalServerSocket (150-200)
        @JvmField
        val ERRNO_SERVER_SOCKET_PATH_NULL_OR_EMPTY = Errno(TYPE, 150, "The \"%1\$s\" server socket path is null or empty.")
        @JvmField
        val ERRNO_SERVER_SOCKET_PATH_TOO_LONG = Errno(TYPE, 151, "The \"%1\$s\" server socket path \"%2\$s\" is greater than 108 bytes.")
        @JvmField
        val ERRNO_SERVER_SOCKET_PATH_NOT_ABSOLUTE = Errno(TYPE, 152, "The \"%1\$s\" server socket path \"%2\$s\" is not an absolute file path.")
        @JvmField
        val ERRNO_SERVER_SOCKET_BACKLOG_INVALID = Errno(TYPE, 153, "The \"%1\$s\" server socket backlog \"%2\$s\" is not greater than 0.")
        @JvmField
        val ERRNO_CREATE_SERVER_SOCKET_FAILED = Errno(TYPE, 154, "Create \"%1\$s\" server socket failed.\n%2\$s")
        @JvmField
        val ERRNO_SERVER_SOCKET_FD_INVALID = Errno(TYPE, 155, "Invalid file descriptor \"%1\$s\" returned when creating \"%2\$s\" server socket.")
        @JvmField
        val ERRNO_ACCEPT_CLIENT_SOCKET_FAILED = Errno(TYPE, 156, "Accepting client socket for \"%1\$s\" server failed.\n%2\$s")
        @JvmField
        val ERRNO_CLIENT_SOCKET_FD_INVALID = Errno(TYPE, 157, "Invalid file descriptor \"%1\$s\" returned when accept new client for \"%2\$s\" server.")
        @JvmField
        val ERRNO_GET_CLIENT_SOCKET_PEER_UID_FAILED = Errno(TYPE, 158, "Getting peer uid for client socket for \"%1\$s\" server failed.\n%2\$s")
        @JvmField
        val ERRNO_CLIENT_SOCKET_PEER_UID_INVALID = Errno(TYPE, 158, "Invalid peer uid \"%1\$s\" returned for new client for \"%2\$s\" server.")
        @JvmField
        val ERRNO_CLIENT_SOCKET_PEER_UID_DISALLOWED = Errno(TYPE, 160, "Disallowed peer %1\$s tried to connect with \"%2\$s\" server.")
        @JvmField
        val ERRNO_CLOSE_SERVER_SOCKET_FAILED_WITH_EXCEPTION = Errno(TYPE, 161, "Close \"%1\$s\" server socket failed.\nException: %2\$s")
        @JvmField
        val ERRNO_CLIENT_SOCKET_LISTENER_FAILED_WITH_EXCEPTION = Errno(TYPE, 162, "Exception in client socket listener for \"%1\$s\" server.\nException: %2\$s")

        // Errors for LocalClientSocket (200-250)
        @JvmField
        val ERRNO_SET_CLIENT_SOCKET_READ_TIMEOUT_FAILED = Errno(TYPE, 200, "Set \"%1\$s\" client socket read (SO_RCVTIMEO) timeout to \"%2\$s\" failed.\n%3\$s")
        @JvmField
        val ERRNO_SET_CLIENT_SOCKET_SEND_TIMEOUT_FAILED = Errno(TYPE, 201, "Set \"%1\$s\" client socket send (SO_SNDTIMEO) timeout \"%2\$s\" failed.\n%3\$s")
        @JvmField
        val ERRNO_READ_DATA_FROM_CLIENT_SOCKET_FAILED = Errno(TYPE, 202, "Read data from \"%1\$s\" client socket failed.\n%2\$s")
        @JvmField
        val ERRNO_READ_DATA_FROM_INPUT_STREAM_OF_CLIENT_SOCKET_FAILED_WITH_EXCEPTION = Errno(TYPE, 203, "Read data from \"%1\$s\" client socket input stream failed.\n%2\$s")
        @JvmField
        val ERRNO_SEND_DATA_TO_CLIENT_SOCKET_FAILED = Errno(TYPE, 204, "Send data to \"%1\$s\" client socket failed.\n%2\$s")
        @JvmField
        val ERRNO_SEND_DATA_TO_OUTPUT_STREAM_OF_CLIENT_SOCKET_FAILED_WITH_EXCEPTION = Errno(TYPE, 205, "Send data to \"%1\$s\" client socket output stream failed.\n%2\$s")
        @JvmField
        val ERRNO_CHECK_AVAILABLE_DATA_ON_CLIENT_SOCKET_FAILED = Errno(TYPE, 206, "Check available data on \"%1\$s\" client socket failed.\n%2\$s")
        @JvmField
        val ERRNO_CLOSE_CLIENT_SOCKET_FAILED_WITH_EXCEPTION = Errno(TYPE, 207, "Close \"%1\$s\" client socket failed.\n%2\$s")
        @JvmField
        val ERRNO_USING_CLIENT_SOCKET_WITH_INVALID_FD = Errno(TYPE, 208, "Trying to use client socket with invalid file descriptor \"%1\$s\" for \"%2\$s\" server.")
    }
}
