package com.termux.app.data.model

import java.util.UUID

/**
 * Represents a terminal session with its state
 */
data class TerminalSessionState(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Terminal",
    val isActive: Boolean = false,
    val workingDirectory: String = "~",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Represents a pane in split terminal layout
 */
data class TerminalPane(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val splitRatio: Float = 0.5f
)

/**
 * Layout configuration for split terminals
 */
sealed class TerminalLayout {
    data object Single : TerminalLayout()

    data class HorizontalSplit(
        val leftPane: TerminalPane,
        val rightPane: TerminalPane,
        val splitRatio: Float = 0.5f
    ) : TerminalLayout()

    data class VerticalSplit(
        val topPane: TerminalPane,
        val bottomPane: TerminalPane,
        val splitRatio: Float = 0.5f
    ) : TerminalLayout()

    data class QuadSplit(
        val topLeft: TerminalPane,
        val topRight: TerminalPane,
        val bottomLeft: TerminalPane,
        val bottomRight: TerminalPane
    ) : TerminalLayout()
}

/**
 * Command history entry
 */
data class CommandHistoryEntry(
    val id: Long = 0,
    val command: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String? = null,
    val exitCode: Int? = null,
    val duration: Long? = null
)

/**
 * SSH connection profile
 */
data class SshProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authMethod: SshAuthMethod = SshAuthMethod.Password,
    val privateKeyPath: String? = null,
    val lastConnected: Long? = null
)

sealed class SshAuthMethod {
    data object Password : SshAuthMethod()
    data object PublicKey : SshAuthMethod()
    data object Agent : SshAuthMethod()
}
