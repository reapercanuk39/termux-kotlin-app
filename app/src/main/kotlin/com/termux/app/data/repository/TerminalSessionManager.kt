package com.termux.app.data.repository

import com.termux.app.data.model.TerminalLayout
import com.termux.app.data.model.TerminalPane
import com.termux.app.data.model.TerminalSessionState
import com.termux.app.di.ApplicationScope
import com.termux.app.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages terminal sessions and layout state using StateFlow.
 * Supports split terminal panes.
 */
@Singleton
class TerminalSessionManager @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    // All terminal sessions
    private val _sessions = MutableStateFlow<List<TerminalSessionState>>(emptyList())
    val sessions: StateFlow<List<TerminalSessionState>> = _sessions.asStateFlow()

    // Active session ID
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    // Current layout
    private val _layout = MutableStateFlow<TerminalLayout>(TerminalLayout.Single)
    val layout: StateFlow<TerminalLayout> = _layout.asStateFlow()

    // Focused pane ID (for split layouts)
    private val _focusedPaneId = MutableStateFlow<String?>(null)
    val focusedPaneId: StateFlow<String?> = _focusedPaneId.asStateFlow()

    init {
        // Create initial session
        scope.launch(dispatcher) {
            createSession()
        }
    }

    /**
     * Create a new terminal session
     */
    fun createSession(title: String = "Terminal"): String {
        val session = TerminalSessionState(
            title = title,
            isActive = _sessions.value.isEmpty()
        )

        _sessions.update { currentSessions ->
            currentSessions + session
        }

        if (_activeSessionId.value == null) {
            _activeSessionId.value = session.id
        }

        return session.id
    }

    /**
     * Close a terminal session
     */
    fun closeSession(sessionId: String) {
        _sessions.update { currentSessions ->
            val remaining = currentSessions.filter { it.id != sessionId }

            // If closing active session, switch to another one
            if (_activeSessionId.value == sessionId && remaining.isNotEmpty()) {
                _activeSessionId.value = remaining.last().id
            } else if (remaining.isEmpty()) {
                _activeSessionId.value = null
            }

            remaining
        }

        // Reset to single layout if we have 1 or fewer sessions
        if (_sessions.value.size <= 1) {
            _layout.value = TerminalLayout.Single
        }
    }

    /**
     * Set the active session
     */
    fun setActiveSession(sessionId: String) {
        if (_sessions.value.any { it.id == sessionId }) {
            _activeSessionId.value = sessionId
            _sessions.update { sessions ->
                sessions.map { session ->
                    session.copy(isActive = session.id == sessionId)
                }
            }
        }
    }

    /**
     * Update session title
     */
    fun updateSessionTitle(sessionId: String, title: String) {
        _sessions.update { sessions ->
            sessions.map { session ->
                if (session.id == sessionId) {
                    session.copy(title = title)
                } else {
                    session
                }
            }
        }
    }

    /**
     * Update session working directory
     */
    fun updateWorkingDirectory(sessionId: String, directory: String) {
        _sessions.update { sessions ->
            sessions.map { session ->
                if (session.id == sessionId) {
                    session.copy(workingDirectory = directory)
                } else {
                    session
                }
            }
        }
    }

    /**
     * Split terminal horizontally (side by side)
     */
    fun splitHorizontal() {
        val activeId = _activeSessionId.value ?: return
        val newSessionId = createSession("Terminal ${_sessions.value.size}")

        val leftPane = TerminalPane(
            sessionId = activeId,
            splitRatio = 0.5f
        )
        val rightPane = TerminalPane(
            sessionId = newSessionId,
            splitRatio = 0.5f
        )

        _layout.value = TerminalLayout.HorizontalSplit(
            leftPane = leftPane,
            rightPane = rightPane
        )
        _focusedPaneId.value = rightPane.id
    }

    /**
     * Split terminal vertically (top and bottom)
     */
    fun splitVertical() {
        val activeId = _activeSessionId.value ?: return
        val newSessionId = createSession("Terminal ${_sessions.value.size}")

        val topPane = TerminalPane(
            sessionId = activeId,
            splitRatio = 0.5f
        )
        val bottomPane = TerminalPane(
            sessionId = newSessionId,
            splitRatio = 0.5f
        )

        _layout.value = TerminalLayout.VerticalSplit(
            topPane = topPane,
            bottomPane = bottomPane
        )
        _focusedPaneId.value = bottomPane.id
    }

    /**
     * Close split and return to single pane
     */
    fun closeSplit() {
        val activeId = _activeSessionId.value
        _layout.value = TerminalLayout.Single
        _focusedPaneId.value = null

        // Keep only the active session in view
        if (activeId != null) {
            setActiveSession(activeId)
        }
    }

    /**
     * Set focused pane
     */
    fun setFocusedPane(paneId: String) {
        _focusedPaneId.value = paneId

        // Update active session based on focused pane
        when (val layout = _layout.value) {
            is TerminalLayout.HorizontalSplit -> {
                val sessionId = if (layout.leftPane.id == paneId) {
                    layout.leftPane.sessionId
                } else {
                    layout.rightPane.sessionId
                }
                setActiveSession(sessionId)
            }
            is TerminalLayout.VerticalSplit -> {
                val sessionId = if (layout.topPane.id == paneId) {
                    layout.topPane.sessionId
                } else {
                    layout.bottomPane.sessionId
                }
                setActiveSession(sessionId)
            }
            is TerminalLayout.QuadSplit -> {
                val sessionId = when (paneId) {
                    layout.topLeft.id -> layout.topLeft.sessionId
                    layout.topRight.id -> layout.topRight.sessionId
                    layout.bottomLeft.id -> layout.bottomLeft.sessionId
                    else -> layout.bottomRight.sessionId
                }
                setActiveSession(sessionId)
            }
            TerminalLayout.Single -> { /* No panes in single mode */ }
        }
    }

    /**
     * Adjust split ratio
     */
    fun adjustSplitRatio(ratio: Float) {
        val clampedRatio = ratio.coerceIn(0.2f, 0.8f)

        _layout.update { current ->
            when (current) {
                is TerminalLayout.HorizontalSplit -> current.copy(splitRatio = clampedRatio)
                is TerminalLayout.VerticalSplit -> current.copy(splitRatio = clampedRatio)
                else -> current
            }
        }
    }

    /**
     * Get session by ID
     */
    fun getSession(sessionId: String): TerminalSessionState? {
        return _sessions.value.find { it.id == sessionId }
    }

    /**
     * Get active session
     */
    fun getActiveSession(): TerminalSessionState? {
        val activeId = _activeSessionId.value ?: return null
        return getSession(activeId)
    }
}
