package com.termux.shared.termux.terminal

import com.termux.shared.logger.Logger
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

open class TermuxTerminalSessionClientBase : TerminalSessionClient {

    override fun onTextChanged(changedSession: TerminalSession) {
    }

    override fun onTitleChanged(updatedSession: TerminalSession) {
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
    }

    override fun onBell(session: TerminalSession) {
    }

    override fun onColorsChanged(changedSession: TerminalSession) {
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
    }

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
    }

    override fun getTerminalCursorStyle(): Int? {
        return null
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
        private const val LOG_TAG = "TermuxTerminalSessionClientBase"
    }
}
