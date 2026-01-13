package com.termux.app.terminal

import com.termux.app.TermuxService
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalSession

/**
 * The [com.termux.terminal.TerminalSessionClient] implementation that may require
 * a [android.app.Service] for its interface methods.
 */
class TermuxTerminalSessionServiceClient(
    private val service: TermuxService
) : TermuxTerminalSessionClientBase() {

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
        val termuxSession = service.getTermuxSessionForTerminalSession(session)
        termuxSession?.executionCommand?.mPid = pid
    }

    companion object {
        private const val LOG_TAG = "TermuxTerminalSessionServiceClient"
    }
}
