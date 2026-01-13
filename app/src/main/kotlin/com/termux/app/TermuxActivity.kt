package com.termux.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager.widget.ViewPager
import com.termux.R
import com.termux.app.activities.HelpActivity
import com.termux.app.activities.SettingsActivity
import com.termux.app.api.file.FileReceiverActivity
import com.termux.app.terminal.TermuxActivityRootView
import com.termux.app.terminal.TermuxSessionsListViewController
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.termux.app.terminal.TermuxTerminalViewClient
import com.termux.app.terminal.io.TerminalToolbarViewPager
import com.termux.app.terminal.io.TermuxTerminalExtraKeys
import com.termux.shared.activities.ReportActivity
import com.termux.shared.activity.ActivityUtils
import com.termux.shared.activity.media.AppCompatActivityUtils
import com.termux.shared.android.PermissionUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.data.IntentUtils
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.crash.TermuxCrashUtils
import com.termux.shared.termux.extrakeys.ExtraKeysView
import com.termux.shared.termux.interact.TextInputDialogUtils
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
import com.termux.shared.termux.theme.TermuxThemeUtils
import com.termux.shared.theme.NightMode
import com.termux.shared.view.ViewUtils
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * A terminal emulator activity.
 */
class TermuxActivity : AppCompatActivity(), ServiceConnection {

    /** The connection to the [TermuxService]. */
    var mTermuxService: TermuxService? = null

    /** The [TerminalView] shown in [TermuxActivity] that displays the terminal. */
    lateinit var mTerminalView: TerminalView

    /** The [com.termux.view.TerminalViewClient] interface implementation. */
    var mTermuxTerminalViewClient: TermuxTerminalViewClient? = null

    /** The [com.termux.terminal.TerminalSessionClient] interface implementation. */
    var mTermuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient? = null

    /** Termux app shared preferences manager. */
    private var mPreferences: TermuxAppSharedPreferences? = null

    /** Termux app SharedProperties loaded from termux.properties */
    private var mProperties: TermuxAppSharedProperties? = null

    /** The root view of the [TermuxActivity]. */
    var mTermuxActivityRootView: TermuxActivityRootView? = null

    /** The space at the bottom of mTermuxActivityRootView of the [TermuxActivity]. */
    var mTermuxActivityBottomSpaceView: View? = null

    /** The terminal extra keys view. */
    var mExtraKeysView: ExtraKeysView? = null

    /** The client for the [mExtraKeysView]. */
    var mTermuxTerminalExtraKeys: TermuxTerminalExtraKeys? = null

    /** The termux sessions list controller. */
    private var mTermuxSessionListViewController: TermuxSessionsListViewController? = null

    /** The [TermuxActivity] broadcast receiver for various things like terminal style configuration changes. */
    private val mTermuxActivityBroadcastReceiver: BroadcastReceiver = TermuxActivityBroadcastReceiver()

    /** The last toast shown, used cancel current toast before showing new in [showToast]. */
    var mLastToast: Toast? = null

    /** If between onResume() and onStop(). */
    private var mIsVisible = false

    /** If onResume() was called after onCreate(). */
    private var mIsOnResumeAfterOnCreate = false

    /** If activity was restarted like due to call to [recreate] after receiving [TERMUX_ACTIVITY.ACTION_RELOAD_STYLE]. */
    private var mIsActivityRecreated = false

    /** The [TermuxActivity] is in an invalid state and must not be run. */
    private var mIsInvalidState = false

    private var mNavBarHeight = 0

    private var mTerminalToolbarDefaultHeight = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.logDebug(LOG_TAG, "onCreate")
        mIsOnResumeAfterOnCreate = true

        if (savedInstanceState != null) {
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false)
        }

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false)

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties()
        reloadProperties()

        setActivityTheme()

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_termux)

        // Load termux shared preferences
        mPreferences = TermuxAppSharedPreferences.build(this, true)
        if (mPreferences == null) {
            mIsInvalidState = true
            return
        }

        setMargins()

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view)
        mTermuxActivityRootView?.setActivity(this)
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view)
        mTermuxActivityRootView?.setOnApplyWindowInsetsListener(TermuxActivityRootView.WindowInsetsListener())

        val content = findViewById<View>(android.R.id.content)
        content.setOnApplyWindowInsetsListener { _, insets ->
            mNavBarHeight = insets.systemWindowInsetBottom
            insets
        }

        if (mProperties?.isUsingFullScreen() == true) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        setTermuxTerminalViewAndClients()
        setTerminalToolbarView(savedInstanceState)
        setSettingsButtonView()
        setNewSessionButtonView()
        setToggleKeyboardView()

        registerForContextMenu(mTerminalView)

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this)

        try {
            // Start the TermuxService and make it run regardless of who is bound to it
            val serviceIntent = Intent(this, TermuxService::class.java)
            startService(serviceIntent)

            // Attempt to bind to the service
            if (!bindService(serviceIntent, this, 0)) {
                throw RuntimeException("bindService() failed")
            }
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "TermuxActivity failed to start TermuxService", e)
            Logger.showToast(
                this,
                getString(
                    if (e.message != null && e.message!!.contains("app is in background"))
                        R.string.error_termux_service_start_failed_bg
                    else
                        R.string.error_termux_service_start_failed_general
                ),
                true
            )
            mIsInvalidState = true
            return
        }

        // Send the BROADCAST_TERMUX_OPENED broadcast to notify apps that Termux app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this)
    }

    override fun onStart() {
        super.onStart()
        Logger.logDebug(LOG_TAG, "onStart")

        if (mIsInvalidState) return

        mIsVisible = true

        mTermuxTerminalSessionActivityClient?.onStart()
        mTermuxTerminalViewClient?.onStart()

        if (mPreferences?.isTerminalMarginAdjustmentEnabled() == true) {
            addTermuxActivityRootViewGlobalLayoutListener()
        }

        registerTermuxActivityBroadcastReceiver()
    }

    override fun onResume() {
        super.onResume()
        Logger.logVerbose(LOG_TAG, "onResume")

        if (mIsInvalidState) return

        mTermuxTerminalSessionActivityClient?.onResume()
        mTermuxTerminalViewClient?.onResume()

        // Check if a crash happened on last run
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG)

        mIsOnResumeAfterOnCreate = false
    }

    override fun onStop() {
        super.onStop()
        Logger.logDebug(LOG_TAG, "onStop")

        if (mIsInvalidState) return

        mIsVisible = false

        mTermuxTerminalSessionActivityClient?.onStop()
        mTermuxTerminalViewClient?.onStop()

        removeTermuxActivityRootViewGlobalLayoutListener()
        unregisterTermuxActivityBroadcastReceiver()
        drawer.closeDrawers()
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.logDebug(LOG_TAG, "onDestroy")

        if (mIsInvalidState) return

        mTermuxService?.let {
            it.unsetTermuxTerminalSessionClient()
            mTermuxService = null
        }

        try {
            unbindService(this)
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState")

        super.onSaveInstanceState(savedInstanceState)
        saveTerminalToolbarTextInput(savedInstanceState)
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true)
    }

    /**
     * Part of the [ServiceConnection] interface. The service is bound with
     * [bindService] in [onCreate] which will cause a call to this callback method.
     */
    override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
        Logger.logDebug(LOG_TAG, "onServiceConnected")

        mTermuxService = (service as TermuxService.LocalBinder).service

        setTermuxSessionsListView()

        val intent = intent
        setIntent(null)

        if (mTermuxService!!.isTermuxSessionsEmpty) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(this@TermuxActivity) {
                    if (mTermuxService == null) return@setupBootstrapIfNeeded
                    try {
                        var launchFailsafe = false
                        if (intent?.extras != null) {
                            launchFailsafe = intent.extras!!.getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false)
                        }
                        mTermuxTerminalSessionActivityClient?.addNewSession(launchFailsafe, null)
                    } catch (e: WindowManager.BadTokenException) {
                        // Activity finished - ignore.
                    }
                }
            } else {
                finishActivityIfNotFinishing()
            }
        } else {
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN == intent.action) {
                val isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false)
                mTermuxTerminalSessionActivityClient?.addNewSession(isFailSafe, null)
            } else {
                mTermuxTerminalSessionActivityClient?.setCurrentSession(
                    mTermuxTerminalSessionActivityClient?.currentStoredSessionOrLast
                )
            }
        }

        // Update the TerminalSession and TerminalEmulator clients
        mTermuxService?.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected")
        finishActivityIfNotFinishing()
    }

    private fun reloadProperties() {
        mProperties?.loadTermuxPropertiesFromDisk()
        mTermuxTerminalViewClient?.onReloadProperties()
    }

    private fun setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties?.getNightMode())

        // Set activity night mode
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().name, true)
    }

    private fun setMargins() {
        val relativeLayout = findViewById<RelativeLayout>(R.id.activity_termux_root_relative_layout)
        val marginHorizontal = mProperties?.getTerminalMarginHorizontal() ?: 0
        val marginVertical = mProperties?.getTerminalMarginVertical() ?: 0
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical)
    }

    fun addTermuxActivityRootViewGlobalLayoutListener() {
        mTermuxActivityRootView?.viewTreeObserver?.addOnGlobalLayoutListener(mTermuxActivityRootView)
    }

    fun removeTermuxActivityRootViewGlobalLayoutListener() {
        mTermuxActivityRootView?.viewTreeObserver?.removeOnGlobalLayoutListener(mTermuxActivityRootView)
    }

    private fun setTermuxTerminalViewAndClients() {
        val termuxTerminalSessionActivityClient = TermuxTerminalSessionActivityClient(this)
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient
        val termuxTerminalViewClient = TermuxTerminalViewClient(this, termuxTerminalSessionActivityClient)
        mTermuxTerminalViewClient = termuxTerminalViewClient

        mTerminalView = findViewById(R.id.terminal_view)
        mTerminalView.setTerminalViewClient(termuxTerminalViewClient)

        termuxTerminalViewClient.onCreate()
        termuxTerminalSessionActivityClient.onCreate()
    }

    private fun setTermuxSessionsListView() {
        val termuxSessionsListView = findViewById<ListView>(R.id.terminal_sessions_list)
        mTermuxSessionListViewController = TermuxSessionsListViewController(this, mTermuxService!!.termuxSessions)
        termuxSessionsListView.adapter = mTermuxSessionListViewController
        termuxSessionsListView.onItemClickListener = mTermuxSessionListViewController
        termuxSessionsListView.onItemLongClickListener = mTermuxSessionListViewController
    }

    private fun setTerminalToolbarView(savedInstanceState: Bundle?) {
        mTermuxTerminalExtraKeys = TermuxTerminalExtraKeys(
            this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient
        )

        val terminalToolbarViewPager = terminalToolbarViewPager
        if (mPreferences?.shouldShowTerminalToolbar() == true) {
            terminalToolbarViewPager?.visibility = View.VISIBLE
        }

        val layoutParams = terminalToolbarViewPager?.layoutParams
        mTerminalToolbarDefaultHeight = layoutParams?.height?.toFloat() ?: 0f

        setTerminalToolbarHeight()

        var savedTextInput: String? = null
        if (savedInstanceState != null) {
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT)
        }

        terminalToolbarViewPager?.adapter = TerminalToolbarViewPager.PageAdapter(this, savedTextInput)
        terminalToolbarViewPager?.addOnPageChangeListener(
            TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager)
        )
    }

    private fun setTerminalToolbarHeight() {
        val terminalToolbarViewPager = terminalToolbarViewPager ?: return

        val layoutParams = terminalToolbarViewPager.layoutParams
        val extraKeysInfo = mTermuxTerminalExtraKeys?.getExtraKeysInfo()
        val matrix = extraKeysInfo?.getMatrix()
        layoutParams.height = Math.round(
            mTerminalToolbarDefaultHeight *
                (matrix?.size ?: 0) *
                (mProperties?.getTerminalToolbarHeightScaleFactor() ?: 1f)
        ).toInt()
        terminalToolbarViewPager.layoutParams = layoutParams
    }

    fun toggleTerminalToolbar() {
        val terminalToolbarViewPager = terminalToolbarViewPager ?: return

        val showNow = mPreferences?.toogleShowTerminalToolbar() ?: false
        Logger.showToast(
            this,
            if (showNow) getString(R.string.msg_enabling_terminal_toolbar)
            else getString(R.string.msg_disabling_terminal_toolbar),
            true
        )
        terminalToolbarViewPager.visibility = if (showNow) View.VISIBLE else View.GONE
        if (showNow && isTerminalToolbarTextInputViewSelected) {
            findViewById<View>(R.id.terminal_toolbar_text_input).requestFocus()
        }
    }

    private fun saveTerminalToolbarTextInput(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return

        val textInputView = findViewById<EditText>(R.id.terminal_toolbar_text_input)
        if (textInputView != null) {
            val textInput = textInputView.text.toString()
            if (textInput.isNotEmpty()) {
                savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput)
            }
        }
    }

    private fun setSettingsButtonView() {
        val settingsButton = findViewById<ImageButton>(R.id.settings_button)
        settingsButton.setOnClickListener {
            ActivityUtils.startActivity(this, Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setNewSessionButtonView() {
        val newSessionButton = findViewById<View>(R.id.new_session_button)
        newSessionButton.setOnClickListener {
            mTermuxTerminalSessionActivityClient?.addNewSession(false, null)
        }
        newSessionButton.setOnLongClickListener {
            TextInputDialogUtils.textInput(
                this@TermuxActivity, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, { text ->
                    mTermuxTerminalSessionActivityClient?.addNewSession(false, text)
                },
                R.string.action_new_session_failsafe, { text ->
                    mTermuxTerminalSessionActivityClient?.addNewSession(true, text)
                },
                -1, null, null
            )
            true
        }
    }

    private fun setToggleKeyboardView() {
        findViewById<View>(R.id.toggle_keyboard_button).setOnClickListener {
            mTermuxTerminalViewClient?.onToggleSoftKeyboardRequest()
            drawer.closeDrawers()
        }

        findViewById<View>(R.id.toggle_keyboard_button).setOnLongClickListener {
            toggleTerminalToolbar()
            true
        }
    }

    @Suppress("MissingSuperCall")
    @SuppressLint("RtlHardcoded")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(Gravity.LEFT)) {
            drawer.closeDrawers()
        } else {
            finishActivityIfNotFinishing()
        }
    }

    fun finishActivityIfNotFinishing() {
        if (!this@TermuxActivity.isFinishing) {
            finish()
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    fun showToast(text: String?, longDuration: Boolean) {
        if (text.isNullOrEmpty()) return
        mLastToast?.cancel()
        mLastToast = Toast.makeText(
            this@TermuxActivity, text,
            if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        )
        mLastToast?.setGravity(Gravity.TOP, 0, 0)
        mLastToast?.show()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        val currentSession = currentSession ?: return

        val autoFillEnabled = mTerminalView.isAutoFillEnabled

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url)
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript)
        if (!DataUtils.isNullOrEmpty(mTerminalView.storedSelectedText)) {
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text)
        }
        if (autoFillEnabled) {
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username)
        }
        if (autoFillEnabled) {
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password)
        }
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal)
        menu.add(
            Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE,
            resources.getString(R.string.action_kill_process, currentSession.getPid())
        ).isEnabled = currentSession.isRunning
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal)
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on)
            .isCheckable = true
        menu.findItem(CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON)?.isChecked = mPreferences?.shouldKeepScreenOn() == true
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help)
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings)
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue)
    }

    /** Hook system menu to show context menu instead. */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        mTerminalView.showContextMenu()
        return false
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val session = currentSession

        return when (item.itemId) {
            CONTEXT_MENU_SELECT_URL_ID -> {
                mTermuxTerminalViewClient?.showUrlSelection()
                true
            }
            CONTEXT_MENU_SHARE_TRANSCRIPT_ID -> {
                mTermuxTerminalViewClient?.shareSessionTranscript()
                true
            }
            CONTEXT_MENU_SHARE_SELECTED_TEXT -> {
                mTermuxTerminalViewClient?.shareSelectedText()
                true
            }
            CONTEXT_MENU_AUTOFILL_USERNAME -> {
                mTerminalView.requestAutoFillUsername()
                true
            }
            CONTEXT_MENU_AUTOFILL_PASSWORD -> {
                mTerminalView.requestAutoFillPassword()
                true
            }
            CONTEXT_MENU_RESET_TERMINAL_ID -> {
                onResetTerminalSession(session)
                true
            }
            CONTEXT_MENU_KILL_PROCESS_ID -> {
                showKillSessionDialog(session)
                true
            }
            CONTEXT_MENU_STYLING_ID -> {
                showStylingDialog()
                true
            }
            CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON -> {
                toggleKeepScreenOn()
                true
            }
            CONTEXT_MENU_HELP_ID -> {
                ActivityUtils.startActivity(this, Intent(this, HelpActivity::class.java))
                true
            }
            CONTEXT_MENU_SETTINGS_ID -> {
                ActivityUtils.startActivity(this, Intent(this, SettingsActivity::class.java))
                true
            }
            CONTEXT_MENU_REPORT_ID -> {
                mTermuxTerminalViewClient?.reportIssueFromTranscript()
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    override fun onContextMenuClosed(menu: Menu) {
        super.onContextMenuClosed(menu)
        mTerminalView.onContextMenuClosed(menu)
    }

    private fun showKillSessionDialog(session: TerminalSession?) {
        if (session == null) return

        val b = AlertDialog.Builder(this)
        b.setIcon(android.R.drawable.ic_dialog_alert)
        b.setMessage(R.string.title_confirm_kill_process)
        b.setPositiveButton(android.R.string.yes) { dialog, _ ->
            dialog.dismiss()
            session.finishIfRunning()
        }
        b.setNegativeButton(android.R.string.no, null)
        b.show()
    }

    private fun onResetTerminalSession(session: TerminalSession?) {
        if (session != null) {
            session.reset()
            showToast(resources.getString(R.string.msg_terminal_reset), true)
            mTermuxTerminalSessionActivityClient?.onResetTerminalSession()
        }
    }

    private fun showStylingDialog() {
        val stylingIntent = Intent()
        stylingIntent.setClassName(
            TermuxConstants.TERMUX_STYLING_PACKAGE_NAME,
            TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME
        )
        try {
            startActivity(stylingIntent)
        } catch (e: ActivityNotFoundException) {
            showStylingNotInstalledDialog()
        } catch (e: IllegalArgumentException) {
            showStylingNotInstalledDialog()
        }
    }

    private fun showStylingNotInstalledDialog() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.error_styling_not_installed))
            .setPositiveButton(R.string.action_styling_install) { _, _ ->
                ActivityUtils.startActivity(
                    this,
                    Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleKeepScreenOn() {
        if (mTerminalView.keepScreenOn) {
            mTerminalView.keepScreenOn = false
            mPreferences?.setKeepScreenOn(false)
        } else {
            mTerminalView.keepScreenOn = true
            mPreferences?.setKeepScreenOn(true)
        }
    }

    /**
     * For processes to access primary external storage, termux needs to be granted permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    fun requestStoragePermission(isPermissionCallback: Boolean) {
        Thread {
            val requestCode = if (isPermissionCallback) -1 else PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION

            if (PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    this@TermuxActivity, requestCode, !isPermissionCallback
                )
            ) {
                if (isPermissionCallback) {
                    Logger.logInfoAndShowToast(
                        this@TermuxActivity, LOG_TAG,
                        getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request)
                    )
                }
                TermuxInstaller.setupStorageSymlinks(this@TermuxActivity)
            } else {
                if (isPermissionCallback) {
                    Logger.logInfoAndShowToast(
                        this@TermuxActivity, LOG_TAG,
                        getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request)
                    )
                }
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Logger.logVerbose(
            LOG_TAG, "onActivityResult: requestCode: $requestCode, resultCode: $resultCode, " +
                "data: ${IntentUtils.getIntentString(data)}"
        )
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Logger.logVerbose(
            LOG_TAG, "onRequestPermissionsResult: requestCode: $requestCode, permissions: ${permissions.contentToString()}, " +
                "grantResults: ${grantResults.contentToString()}"
        )
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true)
        }
    }

    val navBarHeight: Int
        get() = mNavBarHeight

    val termuxActivityRootView: TermuxActivityRootView?
        get() = mTermuxActivityRootView

    val termuxActivityBottomSpaceView: View?
        get() = mTermuxActivityBottomSpaceView

    val extraKeysView: ExtraKeysView?
        get() = mExtraKeysView

    val termuxTerminalExtraKeys: TermuxTerminalExtraKeys?
        get() = mTermuxTerminalExtraKeys

    fun setExtraKeysView(extraKeysView: ExtraKeysView?) {
        mExtraKeysView = extraKeysView
    }

    val drawer: DrawerLayout
        get() = findViewById(R.id.drawer_layout)

    val terminalToolbarViewPager: ViewPager?
        get() = findViewById(R.id.terminal_toolbar_view_pager)

    val terminalToolbarDefaultHeight: Float
        get() = mTerminalToolbarDefaultHeight

    val isTerminalViewSelected: Boolean
        get() = terminalToolbarViewPager?.currentItem == 0

    val isTerminalToolbarTextInputViewSelected: Boolean
        get() = terminalToolbarViewPager?.currentItem == 1

    fun termuxSessionListNotifyUpdated() {
        mTermuxSessionListViewController?.notifyDataSetChanged()
    }

    val isVisible: Boolean
        get() = mIsVisible

    val isOnResumeAfterOnCreate: Boolean
        get() = mIsOnResumeAfterOnCreate

    val isActivityRecreated: Boolean
        get() = mIsActivityRecreated

    val termuxService: TermuxService?
        get() = mTermuxService

    val terminalView: TerminalView
        get() = mTerminalView

    val termuxTerminalViewClient: TermuxTerminalViewClient?
        get() = mTermuxTerminalViewClient

    val termuxTerminalSessionClient: TermuxTerminalSessionActivityClient?
        get() = mTermuxTerminalSessionActivityClient

    val currentSession: TerminalSession?
        get() = mTerminalView.currentSession

    val preferences: TermuxAppSharedPreferences?
        get() = mPreferences

    val properties: TermuxAppSharedProperties?
        get() = mProperties

    private fun registerTermuxActivityBroadcastReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH)
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE)
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS)

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter)
    }

    private fun unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver)
    }

    private fun fixTermuxActivityBroadcastReceiverIntent(intent: Intent?) {
        if (intent == null) return

        val extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE)
        if ("storage" == extraReloadStyle) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE)
            intent.action = TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS
        }
    }

    inner class TermuxActivityBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent == null) return

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent)

                when (intent.action) {
                    TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH -> {
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash")
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG)
                    }
                    TERMUX_ACTIVITY.ACTION_RELOAD_STYLE -> {
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling")
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true))
                    }
                    TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS -> {
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions")
                        requestStoragePermission(false)
                    }
                }
            }
        }
    }

    private fun reloadActivityStyling(recreateActivity: Boolean) {
        if (mProperties != null) {
            reloadProperties()

            mExtraKeysView?.let { view ->
                view.setButtonTextAllCaps(mProperties?.shouldExtraKeysTextBeAllCaps() == true)
                view.reload(mTermuxTerminalExtraKeys?.getExtraKeysInfo(), mTerminalToolbarDefaultHeight)
            }

            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties?.getNightMode())
        }

        setMargins()
        setTerminalToolbarHeight()

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this)

        mTermuxTerminalSessionActivityClient?.onReloadActivityStyling()
        mTermuxTerminalViewClient?.onReloadActivityStyling()

        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity")
            this@TermuxActivity.recreate()
        }
    }

    companion object {
        private const val CONTEXT_MENU_SELECT_URL_ID = 0
        private const val CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1
        private const val CONTEXT_MENU_SHARE_SELECTED_TEXT = 10
        private const val CONTEXT_MENU_AUTOFILL_USERNAME = 11
        private const val CONTEXT_MENU_AUTOFILL_PASSWORD = 2
        private const val CONTEXT_MENU_RESET_TERMINAL_ID = 3
        private const val CONTEXT_MENU_KILL_PROCESS_ID = 4
        private const val CONTEXT_MENU_STYLING_ID = 5
        private const val CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6
        private const val CONTEXT_MENU_HELP_ID = 7
        private const val CONTEXT_MENU_SETTINGS_ID = 8
        private const val CONTEXT_MENU_REPORT_ID = 9

        private const val ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input"
        private const val ARG_ACTIVITY_RECREATED = "activity_recreated"

        private const val LOG_TAG = "TermuxActivity"

        @JvmStatic
        fun updateTermuxActivityStyling(context: Context, recreateActivity: Boolean) {
            val stylingIntent = Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE)
            stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity)
            context.sendBroadcast(stylingIntent)
        }

        @JvmStatic
        fun startTermuxActivity(context: Context) {
            ActivityUtils.startActivity(context, newInstance(context))
        }

        @JvmStatic
        fun newInstance(context: Context): Intent {
            val intent = Intent(context, TermuxActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            return intent
        }
    }
}
