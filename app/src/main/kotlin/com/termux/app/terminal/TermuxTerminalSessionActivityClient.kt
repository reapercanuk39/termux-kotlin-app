package com.termux.app.terminal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.text.TextUtils
import android.widget.ListView
import com.termux.kotlin.R
import com.termux.app.TermuxActivity
import com.termux.app.TermuxService
import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.interact.TextInputDialogUtils
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.shared.termux.terminal.io.BellHandler
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/** The [TerminalSessionClient] implementation that may require an [Activity] for its interface methods. */
class TermuxTerminalSessionActivityClient(private val mActivity: TermuxActivity) : TermuxTerminalSessionClientBase() {

    private var mBellSoundPool: SoundPool? = null
    private var mBellSoundId: Int = 0

    /**
     * Should be called when mActivity.onCreate() is called
     */
    fun onCreate() {
        // Set terminal fonts and colors
        checkForFontAndColors()
    }

    /**
     * Should be called when mActivity.onStart() is called
     */
    fun onStart() {
        // The service has connected, but data may have changed since we were last in the foreground.
        if (mActivity.termuxService != null) {
            setCurrentSession(currentStoredSessionOrLast)
            termuxSessionListNotifyUpdated()
        }

        // The current terminal session may have changed while being away
        mActivity.terminalView.onScreenUpdated()
    }

    /**
     * Should be called when mActivity.onResume() is called
     */
    fun onResume() {
        loadBellSoundPool()
    }

    /**
     * Should be called when mActivity.onStop() is called
     */
    fun onStop() {
        setCurrentStoredSession()
        releaseBellSoundPool()
    }

    /**
     * Should be called when mActivity.reloadActivityStyling() is called
     */
    fun onReloadActivityStyling() {
        checkForFontAndColors()
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        if (!mActivity.isVisible) return

        if (mActivity.currentSession === changedSession) mActivity.terminalView.onScreenUpdated()
    }

    override fun onTitleChanged(updatedSession: TerminalSession) {
        if (!mActivity.isVisible) return

        if (updatedSession !== mActivity.currentSession) {
            mActivity.showToast(toToastTitle(updatedSession), true)
        }

        termuxSessionListNotifyUpdated()
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        val service = mActivity.termuxService

        if (service == null || service.wantsToStop()) {
            mActivity.finishActivityIfNotFinishing()
            return
        }

        val index = service.getIndexOfSession(finishedSession)

        var isPluginExecutionCommandWithPendingResult = false
        val termuxSession = service.getTermuxSession(index)
        if (termuxSession != null) {
            isPluginExecutionCommandWithPendingResult = termuxSession.executionCommand.isPluginExecutionCommandWithPendingResult()
            if (isPluginExecutionCommandWithPendingResult)
                Logger.logVerbose(LOG_TAG, "The \"${finishedSession.mSessionName}\" session will be force finished automatically since result in pending.")
        }

        if (mActivity.isVisible && finishedSession !== mActivity.currentSession) {
            if (index >= 0)
                mActivity.showToast(toToastTitle(finishedSession) + " - exited", true)
        }

        if (mActivity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) {
            if (service.termuxSessionsSize > 1 || isPluginExecutionCommandWithPendingResult) {
                removeFinishedSession(finishedSession)
            }
        } else {
            if (finishedSession.exitStatus == 0 || finishedSession.exitStatus == 130 || isPluginExecutionCommandWithPendingResult) {
                removeFinishedSession(finishedSession)
            }
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (!mActivity.isVisible) return

        ShareUtils.copyTextToClipboard(mActivity, text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        if (!mActivity.isVisible) return

        val text = ShareUtils.getTextStringFromClipboardIfSet(mActivity, true)
        if (text != null)
            mActivity.terminalView.mEmulator?.paste(text)
    }

    override fun onBell(session: TerminalSession) {
        if (!mActivity.isVisible) return

        when (mActivity.properties?.getBellBehaviour()) {
            TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_VIBRATE ->
                BellHandler.getInstance(mActivity).doBell()
            TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_BEEP -> {
                loadBellSoundPool()
                mBellSoundPool?.play(mBellSoundId, 1f, 1f, 1, 0, 1f)
            }
            TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_IGNORE -> {
                // Ignore the bell character.
            }
        }
    }

    override fun onColorsChanged(changedSession: TerminalSession) {
        if (mActivity.currentSession === changedSession)
            updateBackgroundColor()
    }

    override fun onTerminalCursorStateChange(enabled: Boolean) {
        if (enabled && !mActivity.isVisible) {
            Logger.logVerbose(LOG_TAG, "Ignoring call to start cursor blinking since activity is not visible")
            return
        }

        mActivity.terminalView.setTerminalCursorBlinkerState(enabled, false)
    }

    override fun setTerminalShellPid(terminalSession: TerminalSession, pid: Int) {
        val service = mActivity.termuxService ?: return

        val termuxSession = service.getTermuxSessionForTerminalSession(terminalSession)
        if (termuxSession != null)
            termuxSession.executionCommand.mPid = pid
    }

    /**
     * Should be called when mActivity.onResetTerminalSession() is called
     */
    fun onResetTerminalSession() {
        mActivity.terminalView.setTerminalCursorBlinkerState(true, true)
    }

    override fun getTerminalCursorStyle(): Int? {
        return mActivity.properties?.getTerminalCursorStyle()
    }

    /** Load mBellSoundPool */
    @Synchronized
    private fun loadBellSoundPool() {
        if (mBellSoundPool == null) {
            mBellSoundPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
                AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()
            ).build()

            try {
                mBellSoundId = mBellSoundPool!!.load(mActivity, com.termux.shared.R.raw.bell, 1)
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load bell sound pool", e)
            }
        }
    }

    /** Release mBellSoundPool resources */
    @Synchronized
    private fun releaseBellSoundPool() {
        mBellSoundPool?.release()
        mBellSoundPool = null
    }

    /** Try switching to session. */
    fun setCurrentSession(session: TerminalSession?) {
        if (session == null) return

        if (mActivity.terminalView.attachSession(session)) {
            notifyOfSessionChange()
        }

        checkAndScrollToSession(session)
        updateBackgroundColor()
    }

    fun notifyOfSessionChange() {
        if (!mActivity.isVisible) return

        if (mActivity.properties?.areTerminalSessionChangeToastsDisabled() != true) {
            val session = mActivity.currentSession
            mActivity.showToast(toToastTitle(session), false)
        }
    }

    fun switchToSession(forward: Boolean) {
        val service = mActivity.termuxService ?: return

        val currentTerminalSession = mActivity.currentSession
        var index = service.getIndexOfSession(currentTerminalSession)
        val size = service.termuxSessionsSize
        if (forward) {
            if (++index >= size) index = 0
        } else {
            if (--index < 0) index = size - 1
        }

        val termuxSession = service.getTermuxSession(index)
        if (termuxSession != null)
            setCurrentSession(termuxSession.terminalSession)
    }

    fun switchToSession(index: Int) {
        val service = mActivity.termuxService ?: return

        val termuxSession = service.getTermuxSession(index)
        if (termuxSession != null)
            setCurrentSession(termuxSession.terminalSession)
    }

    @SuppressLint("InflateParams")
    fun renameSession(sessionToRename: TerminalSession?) {
        if (sessionToRename == null) return

        TextInputDialogUtils.textInput(mActivity, R.string.title_rename_session, sessionToRename.mSessionName,
            R.string.action_rename_session_confirm, { text ->
                renameSession(sessionToRename, text)
                termuxSessionListNotifyUpdated()
            }, -1, null, -1, null, null)
    }

    private fun renameSession(sessionToRename: TerminalSession?, text: String?) {
        if (sessionToRename == null) return
        sessionToRename.mSessionName = text
        val service = mActivity.termuxService
        if (service != null) {
            val termuxSession = service.getTermuxSessionForTerminalSession(sessionToRename)
            if (termuxSession != null)
                termuxSession.executionCommand.shellName = text
        }
    }

    fun addNewSession(isFailSafe: Boolean, sessionName: String?) {
        val service = mActivity.termuxService ?: return

        if (service.termuxSessionsSize >= MAX_SESSIONS) {
            AlertDialog.Builder(mActivity).setTitle(R.string.title_max_terminals_reached)
                .setMessage(R.string.msg_max_terminals_reached)
                .setPositiveButton(android.R.string.ok, null).show()
        } else {
            val currentSession = mActivity.currentSession

            val workingDirectory = if (currentSession == null) {
                mActivity.properties?.getDefaultWorkingDirectory()
            } else {
                currentSession.getCwd()
            }

            val newTermuxSession = service.createTermuxSession(null, null, null, workingDirectory, isFailSafe, sessionName)
                ?: return

            val newTerminalSession = newTermuxSession.terminalSession
            setCurrentSession(newTerminalSession)

            mActivity.drawer.closeDrawers()
        }
    }

    fun setCurrentStoredSession() {
        val currentSession = mActivity.currentSession
        if (currentSession != null)
            mActivity.preferences?.setCurrentSession(currentSession.mHandle)
        else
            mActivity.preferences?.setCurrentSession(null)
    }

    /** The current session as stored or the last one if that does not exist. */
    val currentStoredSessionOrLast: TerminalSession?
        get() {
            val stored = currentStoredSession

            return if (stored != null) {
                stored
            } else {
                val service = mActivity.termuxService ?: return null
                val termuxSession = service.lastTermuxSession
                termuxSession?.terminalSession
            }
        }

    private val currentStoredSession: TerminalSession?
        get() {
            val sessionHandle = mActivity.preferences?.getCurrentSession() ?: return null

            val service = mActivity.termuxService ?: return null
            return service.getTerminalSessionForHandle(sessionHandle)
        }

    fun removeFinishedSession(finishedSession: TerminalSession) {
        val service = mActivity.termuxService ?: return

        val index = service.removeTermuxSession(finishedSession)

        val size = service.termuxSessionsSize
        if (size == 0) {
            mActivity.finishActivityIfNotFinishing()
        } else {
            val newIndex = if (index >= size) size - 1 else index
            val termuxSession = service.getTermuxSession(newIndex)
            if (termuxSession != null)
                setCurrentSession(termuxSession.terminalSession)
        }
    }

    fun termuxSessionListNotifyUpdated() {
        mActivity.termuxSessionListNotifyUpdated()
    }

    fun checkAndScrollToSession(session: TerminalSession) {
        if (!mActivity.isVisible) return
        val service = mActivity.termuxService ?: return

        val indexOfSession = service.getIndexOfSession(session)
        if (indexOfSession < 0) return
        val termuxSessionsListView = mActivity.findViewById<ListView>(R.id.terminal_sessions_list)
            ?: return

        termuxSessionsListView.setItemChecked(indexOfSession, true)
        termuxSessionsListView.postDelayed({ termuxSessionsListView.smoothScrollToPosition(indexOfSession) }, 1000)
    }

    fun toToastTitle(session: TerminalSession?): String? {
        val service = mActivity.termuxService ?: return null

        val indexOfSession = service.getIndexOfSession(session)
        if (indexOfSession < 0) return null
        val toastTitle = StringBuilder("[${indexOfSession + 1}]")
        if (!TextUtils.isEmpty(session?.mSessionName)) {
            toastTitle.append(" ").append(session?.mSessionName)
        }
        val title = session?.title
        if (!TextUtils.isEmpty(title)) {
            toastTitle.append(if (session?.mSessionName == null) " " else "\n")
            toastTitle.append(title)
        }
        return toastTitle.toString()
    }

    fun checkForFontAndColors() {
        try {
            val colorsFile: File = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE
            val fontFile: File = TermuxConstants.TERMUX_FONT_FILE

            val props = Properties()
            if (colorsFile.isFile) {
                FileInputStream(colorsFile).use { input ->
                    props.load(input)
                }
            }

            TerminalColors.COLOR_SCHEME.updateWith(props)
            val session = mActivity.currentSession
            session?.emulator?.mColors?.reset()
            updateBackgroundColor()

            val newTypeface = if (fontFile.exists() && fontFile.length() > 0)
                Typeface.createFromFile(fontFile)
            else
                Typeface.MONOSPACE
            mActivity.terminalView.setTypeface(newTypeface)
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in checkForFontAndColors()", e)
        }
    }

    fun updateBackgroundColor() {
        if (!mActivity.isVisible) return
        val session = mActivity.currentSession
        if (session != null && session.emulator != null) {
            mActivity.window.decorView.setBackgroundColor(
                session.emulator!!.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]
            )
        }
    }

    companion object {
        private const val MAX_SESSIONS = 8
        private const val LOG_TAG = "TermuxTerminalSessionActivityClient"
    }
}
