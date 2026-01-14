package com.termux.app.core.terminal

import com.termux.app.core.api.ClipboardEvent
import com.termux.app.core.api.GestureEvent
import com.termux.app.core.api.InputEvent
import com.termux.app.core.api.OutputEvent
import com.termux.app.core.api.SessionEvent
import com.termux.app.core.api.TerminalEvent
import com.termux.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized event bus for terminal events.
 * Replaces callback-based event handling with Flow-based reactive streams.
 *
 * Usage:
 * ```kotlin
 * // Subscribe to all session events
 * eventBus.sessionEvents.collect { event ->
 *     when (event) {
 *         is SessionEvent.Created -> handleCreated(event)
 *         is SessionEvent.Finished -> handleFinished(event)
 *         // ...
 *     }
 * }
 *
 * // Subscribe to specific session
 * eventBus.eventsForSession("session-123").collect { event ->
 *     // Handle event
 * }
 * ```
 */
@Singleton
class TerminalEventBus @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope
) {
    
    private val _events = MutableSharedFlow<TerminalEvent>(
        extraBufferCapacity = 100,
        replay = 0
    )
    
    /**
     * All terminal events.
     */
    val events: Flow<TerminalEvent> = _events.asSharedFlow()
    
    /**
     * Session lifecycle events.
     */
    val sessionEvents: Flow<SessionEvent> = _events.filterIsInstance()
    
    /**
     * Terminal output events.
     */
    val outputEvents: Flow<OutputEvent> = _events.filterIsInstance()
    
    /**
     * Clipboard events.
     */
    val clipboardEvents: Flow<ClipboardEvent> = _events.filterIsInstance()
    
    /**
     * Input events.
     */
    val inputEvents: Flow<InputEvent> = _events.filterIsInstance()
    
    /**
     * Gesture events.
     */
    val gestureEvents: Flow<GestureEvent> = _events.filterIsInstance()
    
    /**
     * Emit an event.
     */
    fun emit(event: TerminalEvent) {
        _events.tryEmit(event)
    }
    
    /**
     * Emit an event from a coroutine.
     */
    suspend fun emitSuspend(event: TerminalEvent) {
        _events.emit(event)
    }
    
    /**
     * Get events for a specific session.
     */
    fun eventsForSession(sessionId: String): Flow<TerminalEvent> =
        events.filter { it.sessionId == sessionId }
    
    /**
     * Get session events for a specific session.
     */
    fun sessionEventsFor(sessionId: String): Flow<SessionEvent> =
        sessionEvents.filter { it.sessionId == sessionId }
    
    /**
     * Get output events for a specific session.
     */
    fun outputEventsFor(sessionId: String): Flow<OutputEvent> =
        outputEvents.filter { it.sessionId == sessionId }
    
    // Convenience emit methods
    
    fun emitSessionCreated(
        sessionId: String,
        shellPath: String,
        workingDirectory: String
    ) = emit(SessionEvent.Created(sessionId, shellPath, workingDirectory))
    
    fun emitSessionStarted(
        sessionId: String,
        pid: Int
    ) = emit(SessionEvent.Started(sessionId, pid))
    
    fun emitSessionFinished(
        sessionId: String,
        exitCode: Int
    ) = emit(SessionEvent.Finished(sessionId, exitCode))
    
    fun emitSessionDestroyed(
        sessionId: String
    ) = emit(SessionEvent.Destroyed(sessionId))
    
    fun emitTitleChanged(
        sessionId: String,
        oldTitle: String?,
        newTitle: String
    ) = emit(SessionEvent.TitleChanged(sessionId, oldTitle, newTitle))
    
    fun emitTextChanged(
        sessionId: String
    ) = emit(OutputEvent.TextChanged(sessionId))
    
    fun emitBell(
        sessionId: String
    ) = emit(OutputEvent.Bell(sessionId))
    
    fun emitColorsChanged(
        sessionId: String
    ) = emit(OutputEvent.ColorsChanged(sessionId))
    
    fun emitCursorStateChanged(
        sessionId: String,
        isVisible: Boolean
    ) = emit(OutputEvent.CursorStateChanged(sessionId, isVisible))
    
    fun emitCopyRequested(
        sessionId: String,
        text: String
    ) = emit(ClipboardEvent.CopyRequested(sessionId, text))
    
    fun emitPasteRequested(
        sessionId: String
    ) = emit(ClipboardEvent.PasteRequested(sessionId))
}

/**
 * Bridge adapter that connects legacy callback-based TerminalSessionClient
 * to the new Flow-based TerminalEventBus.
 *
 * This allows gradual migration from callbacks to Flows.
 */
class TerminalSessionClientBridge(
    private val eventBus: TerminalEventBus,
    private val sessionId: String
) {
    
    /**
     * Called when terminal text has changed.
     */
    fun onTextChanged() {
        eventBus.emitTextChanged(sessionId)
    }
    
    /**
     * Called when terminal title has changed.
     */
    fun onTitleChanged(oldTitle: String?, newTitle: String) {
        eventBus.emitTitleChanged(sessionId, oldTitle, newTitle)
    }
    
    /**
     * Called when session has finished.
     */
    fun onSessionFinished(exitCode: Int) {
        eventBus.emitSessionFinished(sessionId, exitCode)
    }
    
    /**
     * Called when terminal bell is triggered.
     */
    fun onBell() {
        eventBus.emitBell(sessionId)
    }
    
    /**
     * Called when copy to clipboard is requested.
     */
    fun onCopyTextToClipboard(text: String) {
        eventBus.emitCopyRequested(sessionId, text)
    }
    
    /**
     * Called when paste from clipboard is requested.
     */
    fun onPasteTextFromClipboard() {
        eventBus.emitPasteRequested(sessionId)
    }
    
    /**
     * Called when terminal colors have changed.
     */
    fun onColorsChanged() {
        eventBus.emitColorsChanged(sessionId)
    }
    
    /**
     * Called when cursor visibility state changes.
     */
    fun onTerminalCursorStateChange(isVisible: Boolean) {
        eventBus.emitCursorStateChanged(sessionId, isVisible)
    }
}

/**
 * Bridge adapter for TerminalViewClient callbacks.
 */
class TerminalViewClientBridge(
    private val eventBus: TerminalEventBus
) {
    
    fun onScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        eventBus.emit(GestureEvent.Scale(scaleFactor, focusX, focusY))
    }
    
    fun onSingleTapUp(x: Float, y: Float) {
        eventBus.emit(GestureEvent.SingleTap(x, y))
    }
    
    fun onDoubleTap(x: Float, y: Float) {
        eventBus.emit(GestureEvent.DoubleTap(x, y))
    }
    
    fun onLongPress(x: Float, y: Float) {
        eventBus.emit(GestureEvent.LongPress(x, y))
    }
    
    fun onScroll(distanceX: Float, distanceY: Float) {
        eventBus.emit(GestureEvent.Scroll(distanceX, distanceY))
    }
    
    fun onFling(velocityX: Float, velocityY: Float) {
        eventBus.emit(GestureEvent.Fling(velocityX, velocityY))
    }
    
    fun onKeyDown(keyCode: Int, modifiers: Int, ctrl: Boolean, alt: Boolean, shift: Boolean) {
        eventBus.emit(InputEvent.KeyDown(keyCode, modifiers, ctrl, alt, shift))
    }
    
    fun onKeyUp(keyCode: Int, modifiers: Int) {
        eventBus.emit(InputEvent.KeyUp(keyCode, modifiers))
    }
    
    fun onCodePoint(codePoint: Int, ctrlDown: Boolean) {
        eventBus.emit(InputEvent.CodePoint(codePoint, ctrlDown))
    }
}
