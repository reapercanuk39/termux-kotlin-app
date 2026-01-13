package com.termux.shared.termux.terminal

import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.shared.logger.Logger
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient

open class TermuxTerminalViewClientBase : TerminalViewClient {

    override fun onScale(scale: Float): Float {
        return 1.0f
    }

    override fun onSingleTapUp(e: MotionEvent) {
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean {
        return false
    }

    override fun shouldEnforceCharBasedInput(): Boolean {
        return false
    }

    override fun shouldUseCtrlSpaceWorkaround(): Boolean {
        return false
    }

    override fun isTerminalViewSelected(): Boolean {
        return true
    }

    override fun copyModeChanged(copyMode: Boolean) {
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        return false
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        return false
    }

    override fun readControlKey(): Boolean {
        return false
    }

    override fun readAltKey(): Boolean {
        return false
    }

    override fun readShiftKey(): Boolean {
        return false
    }

    override fun readFnKey(): Boolean {
        return false
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        return false
    }

    override fun onEmulatorSet() {
    }

    override fun logError(tag: String?, message: String?) {
        Logger.logError(tag ?: LOG_TAG, message ?: "")
    }

    override fun logWarn(tag: String?, message: String?) {
        Logger.logWarn(tag ?: LOG_TAG, message ?: "")
    }

    override fun logInfo(tag: String?, message: String?) {
        Logger.logInfo(tag ?: LOG_TAG, message ?: "")
    }

    override fun logDebug(tag: String?, message: String?) {
        Logger.logDebug(tag ?: LOG_TAG, message ?: "")
    }

    override fun logVerbose(tag: String?, message: String?) {
        Logger.logVerbose(tag ?: LOG_TAG, message ?: "")
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Logger.logStackTraceWithMessage(tag ?: LOG_TAG, message ?: "", e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Logger.logStackTrace(tag ?: LOG_TAG, e)
    }

    companion object {
        private const val LOG_TAG = "TermuxTerminalViewClientBase"
    }
}
