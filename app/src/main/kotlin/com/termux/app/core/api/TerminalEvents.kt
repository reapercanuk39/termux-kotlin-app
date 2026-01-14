package com.termux.app.core.api

import kotlinx.serialization.Serializable

/**
 * Sealed class hierarchy for terminal events.
 * Replaces callback-based event handling with typed, Flow-compatible events.
 */
sealed class TerminalEvent {
    
    /**
     * Timestamp of the event in milliseconds.
     */
    abstract val timestamp: Long
    
    /**
     * Session ID this event belongs to, if applicable.
     */
    abstract val sessionId: String?
}

/**
 * Session lifecycle events.
 */
sealed class SessionEvent : TerminalEvent() {
    
    @Serializable
    data class Created(
        override val sessionId: String,
        val shellPath: String,
        val workingDirectory: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SessionEvent()
    
    @Serializable
    data class Started(
        override val sessionId: String,
        val pid: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SessionEvent()
    
    @Serializable
    data class TitleChanged(
        override val sessionId: String,
        val oldTitle: String?,
        val newTitle: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SessionEvent()
    
    @Serializable
    data class Finished(
        override val sessionId: String,
        val exitCode: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SessionEvent()
    
    @Serializable
    data class Destroyed(
        override val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SessionEvent()
}

/**
 * Terminal output events.
 */
sealed class OutputEvent : TerminalEvent() {
    
    @Serializable
    data class TextChanged(
        override val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : OutputEvent()
    
    @Serializable
    data class Bell(
        override val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : OutputEvent()
    
    @Serializable
    data class ColorsChanged(
        override val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : OutputEvent()
    
    @Serializable
    data class CursorStateChanged(
        override val sessionId: String,
        val isVisible: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : OutputEvent()
}

/**
 * Clipboard events.
 */
sealed class ClipboardEvent : TerminalEvent() {
    
    @Serializable
    data class CopyRequested(
        override val sessionId: String,
        val text: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ClipboardEvent()
    
    @Serializable
    data class PasteRequested(
        override val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ClipboardEvent()
}

/**
 * Input events from terminal view.
 */
sealed class InputEvent : TerminalEvent() {
    override val sessionId: String? = null
    
    @Serializable
    data class KeyDown(
        val keyCode: Int,
        val modifiers: Int,
        val isCtrlPressed: Boolean = false,
        val isAltPressed: Boolean = false,
        val isShiftPressed: Boolean = false,
        override val timestamp: Long = System.currentTimeMillis()
    ) : InputEvent()
    
    @Serializable
    data class KeyUp(
        val keyCode: Int,
        val modifiers: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : InputEvent()
    
    @Serializable
    data class CodePoint(
        val codePoint: Int,
        val ctrlDown: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : InputEvent()
    
    @Serializable
    data class TextInput(
        val text: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : InputEvent()
}

/**
 * Gesture events from terminal view.
 */
sealed class GestureEvent : TerminalEvent() {
    override val sessionId: String? = null
    
    @Serializable
    data class SingleTap(
        val x: Float,
        val y: Float,
        override val timestamp: Long = System.currentTimeMillis()
    ) : GestureEvent()
    
    @Serializable
    data class DoubleTap(
        val x: Float,
        val y: Float,
        override val timestamp: Long = System.currentTimeMillis()
    ) : GestureEvent()
    
    @Serializable
    data class LongPress(
        val x: Float,
        val y: Float,
        override val timestamp: Long = System.currentTimeMillis()
    ) : GestureEvent()
    
    @Serializable
    data class Scroll(
        val distanceX: Float,
        val distanceY: Float,
        override val timestamp: Long = System.currentTimeMillis()
    ) : GestureEvent()
    
    @Serializable
    data class Fling(
        val velocityX: Float,
        val velocityY: Float,
        override val timestamp: Long = System.currentTimeMillis()
    ) : GestureEvent()
    
    @Serializable
    data class Scale(
        val scaleFactor: Float,
        val focusX: Float,
        val focusY: Float,
        override val timestamp: Long = System.currentTimeMillis()
    ) : GestureEvent()
}

/**
 * Permission request events.
 */
sealed class PermissionEvent : TerminalEvent() {
    override val sessionId: String? = null
    
    @Serializable
    data class Requested(
        val permission: PermissionType,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PermissionEvent()
    
    @Serializable
    data class Granted(
        val permission: PermissionType,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PermissionEvent()
    
    @Serializable
    data class Denied(
        val permission: PermissionType,
        val isPermanent: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PermissionEvent()
}

/**
 * Types of permissions that Termux may request.
 */
@Serializable
enum class PermissionType {
    STORAGE,
    NOTIFICATIONS,
    BATTERY_OPTIMIZATION,
    DISPLAY_OVERLAY,
    EXTERNAL_STORAGE_MANAGE
}

/**
 * IPC message types for plugin communication.
 */
sealed class IpcMessage {
    
    abstract val id: String
    abstract val timestamp: Long
    
    /**
     * Request to execute a command.
     */
    @Serializable
    data class ExecuteCommand(
        override val id: String,
        val command: String,
        val arguments: List<String> = emptyList(),
        val workingDirectory: String? = null,
        val environment: Map<String, String> = emptyMap(),
        val background: Boolean = false,
        override val timestamp: Long = System.currentTimeMillis()
    ) : IpcMessage()
    
    /**
     * Response from command execution.
     */
    @Serializable
    data class CommandResult(
        override val id: String,
        val requestId: String,
        val exitCode: Int,
        val stdout: String?,
        val stderr: String?,
        val executionTimeMs: Long,
        override val timestamp: Long = System.currentTimeMillis()
    ) : IpcMessage()
    
    /**
     * Request to create a new session.
     */
    @Serializable
    data class CreateSession(
        override val id: String,
        val shellPath: String? = null,
        val initialCommand: String? = null,
        val workingDirectory: String? = null,
        val environment: Map<String, String> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis()
    ) : IpcMessage()
    
    /**
     * Session creation response.
     */
    @Serializable
    data class SessionCreated(
        override val id: String,
        val requestId: String,
        val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : IpcMessage()
    
    /**
     * Error response.
     */
    @Serializable
    data class Error(
        override val id: String,
        val requestId: String?,
        val errorCode: Int,
        val errorMessage: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : IpcMessage()
    
    /**
     * Plugin registration request.
     */
    @Serializable
    data class RegisterPlugin(
        override val id: String,
        val pluginId: String,
        val pluginName: String,
        val version: String,
        val apiVersion: String,
        val capabilities: List<String> = emptyList(),
        override val timestamp: Long = System.currentTimeMillis()
    ) : IpcMessage()
    
    /**
     * Plugin registration response.
     */
    @Serializable
    data class PluginRegistered(
        override val id: String,
        val requestId: String,
        val success: Boolean,
        val token: String? = null,
        val message: String? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : IpcMessage()
}
