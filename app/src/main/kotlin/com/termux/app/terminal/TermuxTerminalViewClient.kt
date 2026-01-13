package com.termux.app.terminal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.media.AudioManager
import android.os.Environment
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ListView
import androidx.drawerlayout.widget.DrawerLayout
import com.termux.kotlin.R
import com.termux.app.TermuxActivity
import com.termux.app.models.UserAction
import com.termux.app.terminal.io.KeyboardShortcut
import com.termux.shared.activities.ReportActivity
import com.termux.shared.android.AndroidUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.file.FileUtils
import com.termux.shared.interact.MessageDialogUtils
import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.models.ReportInfo
import com.termux.shared.shell.ShellUtils
import com.termux.shared.termux.TermuxBootstrap
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.data.TermuxUrlUtils
import com.termux.shared.termux.extrakeys.SpecialButton
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase
import com.termux.shared.view.KeyboardUtils
import com.termux.shared.view.ViewUtils
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import java.util.*

class TermuxTerminalViewClient(
    val mActivity: TermuxActivity,
    private val mTermuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient
) : TermuxTerminalViewClientBase() {

    /** Keeping track of the special keys acting as Ctrl and Fn for the soft keyboard and other hardware keys. */
    var mVirtualControlKeyDown = false
    var mVirtualFnKeyDown = false

    private var mShowSoftKeyboardRunnable: Runnable? = null
    private var mShowSoftKeyboardIgnoreOnce = false
    private var mShowSoftKeyboardWithDelayOnce = false
    private var mTerminalCursorBlinkerStateAlreadySet = false
    private var mSessionShortcuts: MutableList<KeyboardShortcut>? = null

    val activity: TermuxActivity
        get() = mActivity

    /**
     * Should be called when mActivity.onCreate() is called
     */
    fun onCreate() {
        onReloadProperties()
        mActivity.terminalView.setTextSize(mActivity.preferences?.getFontSize() ?: 14)
        mActivity.terminalView.keepScreenOn = mActivity.preferences?.shouldKeepScreenOn() == true
    }

    /**
     * Should be called when mActivity.onStart() is called
     */
    fun onStart() {
        val isTerminalViewKeyLoggingEnabled = mActivity.preferences?.isTerminalViewKeyLoggingEnabled() == true
        mActivity.terminalView.setIsTerminalViewKeyLoggingEnabled(isTerminalViewKeyLoggingEnabled)
        mActivity.termuxActivityRootView?.setIsRootViewLoggingEnabled(isTerminalViewKeyLoggingEnabled)
        ViewUtils.setIsViewUtilsLoggingEnabled(isTerminalViewKeyLoggingEnabled)
    }

    /**
     * Should be called when mActivity.onResume() is called
     */
    fun onResume() {
        setSoftKeyboardState(true, mActivity.isActivityRecreated)
        mTerminalCursorBlinkerStateAlreadySet = false

        if (mActivity.terminalView.mEmulator != null) {
            setTerminalCursorBlinkerState(true)
            mTerminalCursorBlinkerStateAlreadySet = true
        }
    }

    /**
     * Should be called when mActivity.onStop() is called
     */
    fun onStop() {
        setTerminalCursorBlinkerState(false)
    }

    /**
     * Should be called when mActivity.reloadProperties() is called
     */
    fun onReloadProperties() {
        setSessionShortcuts()
    }

    /**
     * Should be called when mActivity.reloadActivityStyling() is called
     */
    fun onReloadActivityStyling() {
        setSoftKeyboardState(false, true)
        setTerminalCursorBlinkerState(true)
    }

    /**
     * Should be called when TerminalView.mEmulator is set
     */
    override fun onEmulatorSet() {
        if (!mTerminalCursorBlinkerStateAlreadySet) {
            setTerminalCursorBlinkerState(true)
            mTerminalCursorBlinkerStateAlreadySet = true
        }
    }

    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            val increase = scale > 1f
            changeFontSize(increase)
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val term = mActivity.currentSession?.emulator ?: return

        if (mActivity.properties?.shouldOpenTerminalTranscriptURLOnClick() == true) {
            val columnAndRow = mActivity.terminalView.getColumnAndRow(e, true)
            val wordAtTap = term.getScreen().getWordAtLocation(columnAndRow[0], columnAndRow[1])
            val urlSet = TermuxUrlUtils.extractUrls(wordAtTap)

            if (urlSet.isNotEmpty()) {
                val url = urlSet.iterator().next() as String
                ShareUtils.openUrl(mActivity, url)
                return
            }
        }

        if (!term.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity))
                KeyboardUtils.showSoftKeyboard(mActivity, mActivity.terminalView)
            else
                Logger.logVerbose(LOG_TAG, "Not showing soft keyboard onSingleTapUp since its disabled")
        }
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean {
        return mActivity.properties?.isBackKeyTheEscapeKey() == true
    }

    override fun shouldEnforceCharBasedInput(): Boolean {
        return mActivity.properties?.isEnforcingCharBasedInput() == true
    }

    override fun shouldUseCtrlSpaceWorkaround(): Boolean {
        return mActivity.properties?.isUsingCtrlSpaceWorkaround() == true
    }

    override fun isTerminalViewSelected(): Boolean {
        return mActivity.terminalToolbarViewPager == null || mActivity.isTerminalViewSelected || mActivity.terminalView.hasFocus()
    }

    override fun copyModeChanged(copyMode: Boolean) {
        mActivity.drawer.setDrawerLockMode(
            if (copyMode) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED
        )
    }

    @SuppressLint("RtlHardcoded")
    override fun onKeyDown(keyCode: Int, e: KeyEvent, currentSession: TerminalSession): Boolean {
        if (handleVirtualKeys(keyCode, e, true)) return true

        if (keyCode == KeyEvent.KEYCODE_ENTER && !currentSession.isRunning) {
            mTermuxTerminalSessionActivityClient.removeFinishedSession(currentSession)
            return true
        } else if (mActivity.properties?.areHardwareKeyboardShortcutsDisabled() != true &&
            e.isCtrlPressed && e.isAltPressed) {
            val unicodeChar = e.getUnicodeChar(0)

            when {
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN || unicodeChar == 'n'.code ->
                    mTermuxTerminalSessionActivityClient.switchToSession(true)
                keyCode == KeyEvent.KEYCODE_DPAD_UP || unicodeChar == 'p'.code ->
                    mTermuxTerminalSessionActivityClient.switchToSession(false)
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ->
                    mActivity.drawer.openDrawer(Gravity.LEFT)
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ->
                    mActivity.drawer.closeDrawers()
                unicodeChar == 'k'.code ->
                    onToggleSoftKeyboardRequest()
                unicodeChar == 'm'.code ->
                    mActivity.terminalView.showContextMenu()
                unicodeChar == 'r'.code ->
                    mTermuxTerminalSessionActivityClient.renameSession(currentSession)
                unicodeChar == 'c'.code ->
                    mTermuxTerminalSessionActivityClient.addNewSession(false, null)
                unicodeChar == 'u'.code ->
                    showUrlSelection()
                unicodeChar == 'v'.code ->
                    doPaste()
                unicodeChar == '+'.code || e.getUnicodeChar(KeyEvent.META_SHIFT_ON) == '+'.code ->
                    changeFontSize(true)
                unicodeChar == '-'.code ->
                    changeFontSize(false)
                unicodeChar >= '1'.code && unicodeChar <= '9'.code -> {
                    val index = unicodeChar - '1'.code
                    mTermuxTerminalSessionActivityClient.switchToSession(index)
                }
            }
            return true
        }

        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && mActivity.terminalView.mEmulator == null) {
            mActivity.finishActivityIfNotFinishing()
            return true
        }

        return handleVirtualKeys(keyCode, e, false)
    }

    /** Handle dedicated volume buttons as virtual keys if applicable. */
    private fun handleVirtualKeys(keyCode: Int, event: KeyEvent, down: Boolean): Boolean {
        val inputDevice = event.device
        return when {
            mActivity.properties?.areVirtualVolumeKeysDisabled() == true -> false
            inputDevice != null && inputDevice.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC -> false
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> {
                mVirtualControlKeyDown = down
                true
            }
            keyCode == KeyEvent.KEYCODE_VOLUME_UP -> {
                mVirtualFnKeyDown = down
                true
            }
            else -> false
        }
    }

    override fun readControlKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.CTRL) || mVirtualControlKeyDown
    }

    override fun readAltKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.ALT)
    }

    override fun readShiftKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.SHIFT)
    }

    override fun readFnKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.FN)
    }

    fun readExtraKeysSpecialButton(specialButton: SpecialButton): Boolean {
        val extraKeysView = mActivity.extraKeysView ?: return false
        val state = extraKeysView.readSpecialButton(specialButton, true)
        if (state == null) {
            Logger.logError(LOG_TAG, "Failed to read an unregistered $specialButton special button value from extra keys.")
            return false
        }
        return state
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        return false
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        if (mVirtualFnKeyDown) {
            var resultingKeyCode = -1
            var resultingCodePoint = -1
            var altDown = false
            val lowerCase = Character.toLowerCase(codePoint)

            when (lowerCase) {
                'w'.code -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_UP
                'a'.code -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_LEFT
                's'.code -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_DOWN
                'd'.code -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT
                'p'.code -> resultingKeyCode = KeyEvent.KEYCODE_PAGE_UP
                'n'.code -> resultingKeyCode = KeyEvent.KEYCODE_PAGE_DOWN
                't'.code -> resultingKeyCode = KeyEvent.KEYCODE_TAB
                'i'.code -> resultingKeyCode = KeyEvent.KEYCODE_INSERT
                'h'.code -> resultingCodePoint = '~'.code
                'u'.code -> resultingCodePoint = '_'.code
                'l'.code -> resultingCodePoint = '|'.code
                '1'.code, '2'.code, '3'.code, '4'.code, '5'.code, '6'.code, '7'.code, '8'.code, '9'.code ->
                    resultingKeyCode = (codePoint - '1'.code) + KeyEvent.KEYCODE_F1
                '0'.code -> resultingKeyCode = KeyEvent.KEYCODE_F10
                'e'.code -> resultingCodePoint = 27 // Escape
                '.'.code -> resultingCodePoint = 28 // ^.
                'b'.code, 'f'.code, 'x'.code -> {
                    resultingCodePoint = lowerCase
                    altDown = true
                }
                'v'.code -> {
                    resultingCodePoint = -1
                    val audio = mActivity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audio.adjustSuggestedStreamVolume(
                        AudioManager.ADJUST_SAME,
                        AudioManager.USE_DEFAULT_STREAM_TYPE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                'q'.code, 'k'.code -> {
                    mActivity.toggleTerminalToolbar()
                    mVirtualFnKeyDown = false
                }
            }

            if (resultingKeyCode != -1) {
                val term = session.emulator
                session.write(KeyHandler.getCode(resultingKeyCode, 0, term!!.isCursorKeysApplicationMode(), term.isKeypadApplicationMode()))
            } else if (resultingCodePoint != -1) {
                session.writeCodePoint(altDown, resultingCodePoint)
            }
            return true
        } else if (ctrlDown) {
            if (codePoint == 106 /* Ctrl+j or \n */ && !session.isRunning) {
                mTermuxTerminalSessionActivityClient.removeFinishedSession(session)
                return true
            }

            val shortcuts = mSessionShortcuts
            if (shortcuts != null && shortcuts.isNotEmpty()) {
                val codePointLowerCase = Character.toLowerCase(codePoint)
                for (i in shortcuts.indices.reversed()) {
                    val shortcut = shortcuts[i]
                    if (codePointLowerCase == shortcut.codePoint) {
                        when (shortcut.shortcutAction) {
                            TermuxPropertyConstants.ACTION_SHORTCUT_CREATE_SESSION -> {
                                mTermuxTerminalSessionActivityClient.addNewSession(false, null)
                                return true
                            }
                            TermuxPropertyConstants.ACTION_SHORTCUT_NEXT_SESSION -> {
                                mTermuxTerminalSessionActivityClient.switchToSession(true)
                                return true
                            }
                            TermuxPropertyConstants.ACTION_SHORTCUT_PREVIOUS_SESSION -> {
                                mTermuxTerminalSessionActivityClient.switchToSession(false)
                                return true
                            }
                            TermuxPropertyConstants.ACTION_SHORTCUT_RENAME_SESSION -> {
                                mTermuxTerminalSessionActivityClient.renameSession(mActivity.currentSession)
                                return true
                            }
                        }
                    }
                }
            }
        }

        return false
    }

    /**
     * Set the terminal sessions shortcuts.
     */
    private fun setSessionShortcuts() {
        mSessionShortcuts = ArrayList()

        for ((key, value) in TermuxPropertyConstants.MAP_SESSION_SHORTCUTS) {
            val codePoint = mActivity.properties?.getInternalPropertyValue(key, true) as? Int
            if (codePoint != null)
                mSessionShortcuts!!.add(KeyboardShortcut(codePoint, value))
        }
    }

    fun changeFontSize(increase: Boolean) {
        mActivity.preferences?.changeFontSize(increase)
        mActivity.terminalView.setTextSize(mActivity.preferences?.getFontSize() ?: 14)
    }

    /**
     * Called when user requests the soft keyboard to be toggled.
     */
    fun onToggleSoftKeyboardRequest() {
        if (mActivity.properties?.shouldEnableDisableSoftKeyboardOnToggle() == true) {
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity)) {
                Logger.logVerbose(LOG_TAG, "Disabling soft keyboard on toggle")
                mActivity.preferences?.setSoftKeyboardEnabled(false)
                KeyboardUtils.disableSoftKeyboard(mActivity, mActivity.terminalView)
            } else {
                Logger.logVerbose(LOG_TAG, "Enabling soft keyboard on toggle")
                mActivity.preferences?.setSoftKeyboardEnabled(true)
                KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity)
                if (mShowSoftKeyboardWithDelayOnce) {
                    mShowSoftKeyboardWithDelayOnce = false
                    mActivity.terminalView.postDelayed(getShowSoftKeyboardRunnable(), 500)
                    mActivity.terminalView.requestFocus()
                } else
                    KeyboardUtils.showSoftKeyboard(mActivity, mActivity.terminalView)
            }
        } else {
            if (mActivity.preferences?.isSoftKeyboardEnabled() != true) {
                Logger.logVerbose(LOG_TAG, "Maintaining disabled soft keyboard on toggle")
                KeyboardUtils.disableSoftKeyboard(mActivity, mActivity.terminalView)
            } else {
                Logger.logVerbose(LOG_TAG, "Showing/Hiding soft keyboard on toggle")
                KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity)
                KeyboardUtils.toggleSoftKeyboard(mActivity)
            }
        }
    }

    fun setSoftKeyboardState(isStartup: Boolean, isReloadTermuxProperties: Boolean) {
        var noShowKeyboard = false

        if (KeyboardUtils.shouldSoftKeyboardBeDisabled(
                mActivity,
                mActivity.preferences?.isSoftKeyboardEnabled() == true,
                mActivity.preferences?.isSoftKeyboardEnabledOnlyIfNoHardware() == true
            )) {
            Logger.logVerbose(LOG_TAG, "Maintaining disabled soft keyboard")
            KeyboardUtils.disableSoftKeyboard(mActivity, mActivity.terminalView)
            mActivity.terminalView.requestFocus()
            noShowKeyboard = true
            if (isStartup && mActivity.isOnResumeAfterOnCreate)
                mShowSoftKeyboardWithDelayOnce = true
        } else {
            KeyboardUtils.setSoftInputModeAdjustResize(mActivity)
            KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity)

            if (isStartup && mActivity.properties?.shouldSoftKeyboardBeHiddenOnStartup() == true) {
                Logger.logVerbose(LOG_TAG, "Hiding soft keyboard on startup")
                KeyboardUtils.setSoftKeyboardAlwaysHiddenFlags(mActivity)
                KeyboardUtils.hideSoftKeyboard(mActivity, mActivity.terminalView)
                mActivity.terminalView.requestFocus()
                noShowKeyboard = true
                mShowSoftKeyboardIgnoreOnce = true
            }
        }

        mActivity.terminalView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            val textInputView = mActivity.findViewById<EditText>(R.id.terminal_toolbar_text_input)
            val textInputViewHasFocus = textInputView?.hasFocus() == true

            if (hasFocus || textInputViewHasFocus) {
                if (mShowSoftKeyboardIgnoreOnce) {
                    mShowSoftKeyboardIgnoreOnce = false
                    return@OnFocusChangeListener
                }
                Logger.logVerbose(LOG_TAG, "Showing soft keyboard on focus change")
            } else {
                Logger.logVerbose(LOG_TAG, "Hiding soft keyboard on focus change")
            }

            KeyboardUtils.setSoftKeyboardVisibility(
                getShowSoftKeyboardRunnable(),
                mActivity,
                mActivity.terminalView,
                hasFocus || textInputViewHasFocus
            )
        }

        if (!isReloadTermuxProperties && !noShowKeyboard) {
            Logger.logVerbose(LOG_TAG, "Requesting TerminalView focus and showing soft keyboard")
            mActivity.terminalView.requestFocus()
            mActivity.terminalView.postDelayed(getShowSoftKeyboardRunnable(), 300)
        }
    }

    private fun getShowSoftKeyboardRunnable(): Runnable {
        if (mShowSoftKeyboardRunnable == null) {
            mShowSoftKeyboardRunnable = Runnable {
                KeyboardUtils.showSoftKeyboard(mActivity, mActivity.terminalView)
            }
        }
        return mShowSoftKeyboardRunnable!!
    }

    fun setTerminalCursorBlinkerState(start: Boolean) {
        if (start) {
            if (mActivity.terminalView.setTerminalCursorBlinkerRate(mActivity.properties?.getTerminalCursorBlinkRate() ?: 0))
                mActivity.terminalView.setTerminalCursorBlinkerState(true, true)
            else
                Logger.logError(LOG_TAG, "Failed to start cursor blinker")
        } else {
            mActivity.terminalView.setTerminalCursorBlinkerState(false, true)
        }
    }

    fun shareSessionTranscript() {
        val session = mActivity.currentSession ?: return

        var transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true) ?: return

        transcriptText = DataUtils.getTruncatedCommandOutput(
            transcriptText, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, false, true, false
        )?.trim() ?: return
        ShareUtils.shareText(
            mActivity, mActivity.getString(R.string.title_share_transcript),
            transcriptText, mActivity.getString(R.string.title_share_transcript_with)
        )
    }

    fun shareSelectedText() {
        val selectedText = mActivity.terminalView.storedSelectedText
        if (DataUtils.isNullOrEmpty(selectedText)) return
        ShareUtils.shareText(
            mActivity, mActivity.getString(R.string.title_share_selected_text),
            selectedText ?: "", mActivity.getString(R.string.title_share_selected_text_with)
        )
    }

    fun showUrlSelection() {
        val session = mActivity.currentSession ?: return

        val text = ShellUtils.getTerminalSessionTranscriptText(session, true, true) ?: return

        val urlSet = TermuxUrlUtils.extractUrls(text)
        if (urlSet.isEmpty()) {
            AlertDialog.Builder(mActivity).setMessage(R.string.title_select_url_none_found).show()
            return
        }

        val urls = urlSet.toTypedArray()
        Collections.reverse(listOf(*urls))

        val dialog = AlertDialog.Builder(mActivity).setItems(urls) { _, which ->
            val url = urls[which] as String
            ShareUtils.copyTextToClipboard(mActivity, url, mActivity.getString(R.string.msg_select_url_copied_to_clipboard))
        }.setTitle(R.string.title_select_url_dialog).create()

        dialog.setOnShowListener {
            val lv: ListView = dialog.listView
            lv.setOnItemLongClickListener { _, _, position, _ ->
                dialog.dismiss()
                val url = urls[position] as String
                ShareUtils.openUrl(mActivity, url)
                true
            }
        }

        dialog.show()
    }

    fun reportIssueFromTranscript() {
        val session = mActivity.currentSession ?: return

        val transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true) ?: return

        MessageDialogUtils.showMessage(
            mActivity, "${TermuxConstants.TERMUX_APP_NAME} Report Issue",
            mActivity.getString(R.string.msg_add_termux_debug_info),
            mActivity.getString(com.termux.shared.R.string.action_yes),
            { _, _ -> reportIssueFromTranscript(transcriptText, true) },
            mActivity.getString(com.termux.shared.R.string.action_no),
            { _, _ -> reportIssueFromTranscript(transcriptText, false) },
            null
        )
    }

    private fun reportIssueFromTranscript(transcriptText: String, addTermuxDebugInfo: Boolean) {
        Logger.showToast(mActivity, mActivity.getString(R.string.msg_generating_report), true)

        Thread {
            val reportString = StringBuilder()

            val title = "${TermuxConstants.TERMUX_APP_NAME} Report Issue"

            reportString.append("## Transcript\n")
            reportString.append("\n").append(MarkdownUtils.getMarkdownCodeForString(transcriptText, true))
            reportString.append("\n##\n")

            if (addTermuxDebugInfo) {
                reportString.append("\n\n").append(
                    TermuxUtils.getAppInfoMarkdownString(mActivity, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES)
                )
            } else {
                reportString.append("\n\n").append(
                    TermuxUtils.getAppInfoMarkdownString(mActivity, TermuxUtils.AppInfoMode.TERMUX_PACKAGE)
                )
            }

            reportString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(mActivity, true))

            if (TermuxBootstrap.isAppPackageManagerAPT()) {
                val termuxAptInfo = TermuxUtils.geAPTInfoMarkdownString(mActivity)
                if (termuxAptInfo != null)
                    reportString.append("\n\n").append(termuxAptInfo)
            }

            if (addTermuxDebugInfo) {
                val termuxDebugInfo = TermuxUtils.getTermuxDebugMarkdownString(mActivity)
                if (termuxDebugInfo != null)
                    reportString.append("\n\n").append(termuxDebugInfo)
            }

            val userActionName = UserAction.REPORT_ISSUE_FROM_TRANSCRIPT.name

            val reportInfo = ReportInfo(
                userActionName,
                TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY_NAME, title
            )
            reportInfo.setReportString(reportString.toString())
            reportInfo.setReportStringSuffix("\n\n" + TermuxUtils.getReportIssueMarkdownString(mActivity))
            reportInfo.setReportSaveFileLabelAndPath(
                userActionName,
                Environment.getExternalStorageDirectory().toString() + "/" +
                    FileUtils.sanitizeFileName("${TermuxConstants.TERMUX_APP_NAME}-$userActionName.log", true, true)
            )

            ReportActivity.startReportActivity(mActivity, reportInfo)
        }.start()
    }

    fun doPaste() {
        val session = mActivity.currentSession ?: return
        if (!session.isRunning) return

        val text = ShareUtils.getTextStringFromClipboardIfSet(mActivity, true)
        if (text != null)
            session.emulator?.paste(text)
    }

    companion object {
        private const val LOG_TAG = "TermuxTerminalViewClient"
    }
}
