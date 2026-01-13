package com.termux.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.termux.R
import com.termux.app.event.SystemEventReceiver
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.termux.app.terminal.TermuxTerminalSessionServiceClient
import com.termux.shared.android.PermissionUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.data.IntentUtils
import com.termux.shared.errors.Errno
import com.termux.shared.logger.Logger
import com.termux.shared.net.uri.UriUtils
import com.termux.shared.notification.NotificationUtils
import com.termux.shared.shell.ShellUtils
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner
import com.termux.shared.shell.command.ExecutionCommand.ShellCreateMode
import com.termux.shared.shell.command.runner.app.AppShell
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import com.termux.shared.termux.plugins.TermuxPluginUtils
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.TermuxShellUtils
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalSession

/**
 * A service holding a list of [TermuxSession] in [TermuxShellManager.mTermuxSessions] and background [AppShell]
 * in [TermuxShellManager.mTermuxTasks], showing a foreground notification while running so that it is not terminated.
 * The user interacts with the session through [TermuxActivity], but this service may outlive
 * the activity when the user or the system disposes of the activity. In that case the user may
 * restart [TermuxActivity] later to yet again access the sessions.
 *
 * In order to keep both terminal sessions and spawned processes (who may outlive the terminal sessions) alive as long
 * as wanted by the user this service is a foreground service, [Service.startForeground].
 *
 * Optionally may hold a wake and a wifi lock, in which case that is shown in the notification - see
 * [buildNotification].
 */
class TermuxService : Service(), AppShell.AppShellClient, TermuxSession.TermuxSessionClient {

    /** This service is only bound from inside the same process and never uses IPC. */
    inner class LocalBinder : Binder() {
        val service: TermuxService = this@TermuxService
    }

    private val mBinder: IBinder = LocalBinder()
    private val mHandler = Handler(Looper.getMainLooper())

    /**
     * The full implementation of the [com.termux.terminal.TerminalSessionClient] interface to be used by [TerminalSession]
     * that holds activity references for activity related functions.
     * Note that the service may often outlive the activity, so need to clear this reference.
     */
    private var mTermuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient? = null

    /**
     * The basic implementation of the [com.termux.terminal.TerminalSessionClient] interface to be used by [TerminalSession]
     * that does not hold activity references and only a service reference.
     */
    private val mTermuxTerminalSessionServiceClient = TermuxTerminalSessionServiceClient(this)

    /** Termux app shared properties manager, loaded from termux.properties */
    private lateinit var mProperties: TermuxAppSharedProperties

    /** Termux app shell manager */
    private lateinit var mShellManager: TermuxShellManager

    /** The wake lock and wifi lock are always acquired and released together. */
    private var mWakeLock: PowerManager.WakeLock? = null
    private var mWifiLock: WifiManager.WifiLock? = null

    /** If the user has executed the [TERMUX_SERVICE.ACTION_STOP_SERVICE] intent. */
    var mWantsToStop = false

    override fun onCreate() {
        Logger.logVerbose(LOG_TAG, "onCreate")

        // Get Termux app SharedProperties without loading from disk since TermuxApplication handles
        // load and TermuxActivity handles reloads
        mProperties = TermuxAppSharedProperties.getProperties()!!
        mShellManager = TermuxShellManager.getShellManager()!!

        runStartForeground()
        SystemEventReceiver.registerPackageUpdateEvents(this)
    }

    @SuppressLint("Wakelock")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.logDebug(LOG_TAG, "onStartCommand")

        // Run again in case service is already started and onCreate() is not called
        runStartForeground()

        var action: String? = null
        if (intent != null) {
            Logger.logVerboseExtended(LOG_TAG, "Intent Received:\n" + IntentUtils.getIntentString(intent))
            action = intent.action
        }

        if (action != null) {
            when (action) {
                TERMUX_SERVICE.ACTION_STOP_SERVICE -> {
                    Logger.logDebug(LOG_TAG, "ACTION_STOP_SERVICE intent received")
                    actionStopService()
                }
                TERMUX_SERVICE.ACTION_WAKE_LOCK -> {
                    Logger.logDebug(LOG_TAG, "ACTION_WAKE_LOCK intent received")
                    actionAcquireWakeLock()
                }
                TERMUX_SERVICE.ACTION_WAKE_UNLOCK -> {
                    Logger.logDebug(LOG_TAG, "ACTION_WAKE_UNLOCK intent received")
                    actionReleaseWakeLock(true)
                }
                TERMUX_SERVICE.ACTION_SERVICE_EXECUTE -> {
                    Logger.logDebug(LOG_TAG, "ACTION_SERVICE_EXECUTE intent received")
                    actionServiceExecute(intent!!)
                }
                else -> Logger.logError(LOG_TAG, "Invalid action: \"$action\"")
            }
        }

        // If this service really do get killed, there is no point restarting it automatically
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Logger.logVerbose(LOG_TAG, "onDestroy")

        TermuxShellUtils.clearTermuxTMPDIR(true)

        actionReleaseWakeLock(false)
        if (!mWantsToStop) killAllTermuxExecutionCommands()

        TermuxShellManager.onAppExit(this)
        SystemEventReceiver.unregisterPackageUpdateEvents(this)

        runStopForeground()
    }

    override fun onBind(intent: Intent): IBinder {
        Logger.logVerbose(LOG_TAG, "onBind")
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Logger.logVerbose(LOG_TAG, "onUnbind")

        // Since we cannot rely on TermuxActivity.onDestroy() to always complete,
        // we unset clients here as well if it failed, so that we do not leave service and session
        // clients with references to the activity.
        if (mTermuxTerminalSessionActivityClient != null) unsetTermuxTerminalSessionClient()
        return false
    }

    /** Make service run in foreground mode. */
    private fun runStartForeground() {
        setupNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires foreground service type
            startForeground(
                TermuxConstants.TERMUX_APP_NOTIFICATION_ID, buildNotification()!!,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(TermuxConstants.TERMUX_APP_NOTIFICATION_ID, buildNotification()!!)
        }
    }

    /** Make service leave foreground mode. */
    private fun runStopForeground() {
        stopForeground(true)
    }

    /** Request to stop service. */
    private fun requestStopService() {
        Logger.logDebug(LOG_TAG, "Requesting to stop service")
        runStopForeground()
        stopSelf()
    }

    /** Process action to stop service. */
    private fun actionStopService() {
        mWantsToStop = true
        killAllTermuxExecutionCommands()
        requestStopService()
    }

    /**
     * Kill all TermuxSessions and TermuxTasks by sending SIGKILL to their processes.
     *
     * For TermuxSessions, all sessions will be killed, whether user manually exited Termux or if
     * onDestroy() was directly called because of unintended shutdown.
     *
     * For TermuxTasks, only tasks that were started by a plugin which expects the result
     * back via a pending intent will be killed.
     */
    @Synchronized
    private fun killAllTermuxExecutionCommands() {
        Logger.logDebug(
            LOG_TAG, "Killing TermuxSessions=${mShellManager.mTermuxSessions.size}, " +
                "TermuxTasks=${mShellManager.mTermuxTasks.size}, " +
                "PendingPluginExecutionCommands=${mShellManager.mPendingPluginExecutionCommands.size}"
        )

        val termuxSessions = ArrayList(mShellManager.mTermuxSessions)
        val termuxTasks = ArrayList(mShellManager.mTermuxTasks)
        val pendingPluginExecutionCommands = ArrayList(mShellManager.mPendingPluginExecutionCommands)

        for (session in termuxSessions) {
            val executionCommand = session.executionCommand
            val processResult = mWantsToStop || executionCommand.isPluginExecutionCommandWithPendingResult()
            session.killIfExecuting(this, processResult)
            if (!processResult) mShellManager.mTermuxSessions.remove(session)
        }

        for (task in termuxTasks) {
            val executionCommand = task.executionCommand
            if (executionCommand.isPluginExecutionCommandWithPendingResult()) {
                task.killIfExecuting(this, true)
            } else {
                mShellManager.mTermuxTasks.remove(task)
            }
        }

        for (executionCommand in pendingPluginExecutionCommands) {
            if (!executionCommand.shouldNotProcessResults() && executionCommand.isPluginExecutionCommandWithPendingResult()) {
                if (executionCommand.setStateFailed(
                        Errno.ERRNO_CANCELLED.getCode(),
                        getString(com.termux.shared.R.string.error_execution_cancelled)
                    )
                ) {
                    TermuxPluginUtils.processPluginExecutionCommandResult(this, LOG_TAG, executionCommand)
                }
            }
        }
    }

    /** Process action to acquire Power and Wi-Fi WakeLocks. */
    @SuppressLint("WakelockTimeout", "BatteryLife")
    private fun actionAcquireWakeLock() {
        if (mWakeLock != null) {
            Logger.logDebug(LOG_TAG, "Ignoring acquiring WakeLocks since they are already held")
            return
        }

        Logger.logDebug(LOG_TAG, "Acquiring WakeLocks")

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            TermuxConstants.TERMUX_APP_NAME.lowercase() + ":service-wakelock"
        )
        mWakeLock?.acquire()

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        mWifiLock = wm.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            TermuxConstants.TERMUX_APP_NAME.lowercase()
        )
        mWifiLock?.acquire()

        if (!PermissionUtils.checkIfBatteryOptimizationsDisabled(this)) {
            PermissionUtils.requestDisableBatteryOptimizations(this)
        }

        updateNotification()
        Logger.logDebug(LOG_TAG, "WakeLocks acquired successfully")
    }

    /** Process action to release Power and Wi-Fi WakeLocks. */
    private fun actionReleaseWakeLock(updateNotification: Boolean) {
        if (mWakeLock == null && mWifiLock == null) {
            Logger.logDebug(LOG_TAG, "Ignoring releasing WakeLocks since none are already held")
            return
        }

        Logger.logDebug(LOG_TAG, "Releasing WakeLocks")

        mWakeLock?.release()
        mWakeLock = null

        mWifiLock?.release()
        mWifiLock = null

        if (updateNotification) updateNotification()
        Logger.logDebug(LOG_TAG, "WakeLocks released successfully")
    }

    /**
     * Process [TERMUX_SERVICE.ACTION_SERVICE_EXECUTE] intent to execute a shell command in
     * a foreground TermuxSession or in a background TermuxTask.
     */
    private fun actionServiceExecute(intent: Intent) {
        val executionCommand = ExecutionCommand(TermuxShellManager.getNextShellId())

        executionCommand.executableUri = intent.data
        executionCommand.isPluginExecutionCommand = true

        // If EXTRA_RUNNER is passed, use that, otherwise check EXTRA_BACKGROUND and default to Runner.TERMINAL_SESSION
        executionCommand.runner = IntentUtils.getStringExtraIfSet(
            intent, TERMUX_SERVICE.EXTRA_RUNNER,
            if (intent.getBooleanExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, false))
                Runner.APP_SHELL.getName()
            else
                Runner.TERMINAL_SESSION.getName()
        )
        if (Runner.runnerOf(executionCommand.runner) == null) {
            val errmsg = getString(R.string.error_termux_service_invalid_execution_command_runner, executionCommand.runner)
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg)
            TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
            return
        }

        if (executionCommand.executableUri != null) {
            Logger.logVerbose(
                LOG_TAG,
                "uri: \"${executionCommand.executableUri}\", path: \"${executionCommand.executableUri?.path}\", fragment: \"${executionCommand.executableUri?.fragment}\""
            )

            executionCommand.executable = UriUtils.getUriFilePathWithFragment(executionCommand.executableUri)
            executionCommand.arguments = IntentUtils.getStringArrayExtraIfSet(intent, TERMUX_SERVICE.EXTRA_ARGUMENTS, null)
            if (Runner.APP_SHELL.equalsRunner(executionCommand.runner)) {
                executionCommand.stdin = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_STDIN, null)
            }
            executionCommand.backgroundCustomLogLevel = IntentUtils.getIntegerExtraIfSet(intent, TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, null)
        }

        executionCommand.workingDirectory = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_WORKDIR, null)
        executionCommand.isFailsafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false)
        executionCommand.sessionAction = intent.getStringExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION)
        executionCommand.shellName = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_SHELL_NAME, null)
        executionCommand.shellCreateMode = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, null)
        executionCommand.commandLabel = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Execution Intent Command")
        executionCommand.commandDescription = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_COMMAND_DESCRIPTION, null)
        executionCommand.commandHelp = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_COMMAND_HELP, null)
        executionCommand.pluginAPIHelp = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_PLUGIN_API_HELP, null)
        executionCommand.resultConfig.resultPendingIntent = intent.getParcelableExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT)
        executionCommand.resultConfig.resultDirectoryPath = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_DIRECTORY, null)
        if (executionCommand.resultConfig.resultDirectoryPath != null) {
            executionCommand.resultConfig.resultSingleFile = intent.getBooleanExtra(TERMUX_SERVICE.EXTRA_RESULT_SINGLE_FILE, false)
            executionCommand.resultConfig.resultFileBasename = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILE_BASENAME, null)
            executionCommand.resultConfig.resultFileOutputFormat = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILE_OUTPUT_FORMAT, null)
            executionCommand.resultConfig.resultFileErrorFormat = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILE_ERROR_FORMAT, null)
            executionCommand.resultConfig.resultFilesSuffix = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILES_SUFFIX, null)
        }

        if (executionCommand.shellCreateMode == null) {
            executionCommand.shellCreateMode = ShellCreateMode.ALWAYS.getMode()
        }

        // Add the execution command to pending plugin execution commands list
        mShellManager.mPendingPluginExecutionCommands.add(executionCommand)

        when {
            Runner.APP_SHELL.equalsRunner(executionCommand.runner) -> executeTermuxTaskCommand(executionCommand)
            Runner.TERMINAL_SESSION.equalsRunner(executionCommand.runner) -> executeTermuxSessionCommand(executionCommand)
            else -> {
                val errmsg = getString(R.string.error_termux_service_unsupported_execution_command_runner, executionCommand.runner)
                executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg)
                TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
            }
        }
    }

    /** Execute a shell command in background TermuxTask. */
    private fun executeTermuxTaskCommand(executionCommand: ExecutionCommand?) {
        if (executionCommand == null) return

        Logger.logDebug(LOG_TAG, "Executing background \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxTask command")

        // Transform executable path to shell/session name
        if (executionCommand.shellName == null && executionCommand.executable != null) {
            executionCommand.shellName = ShellUtils.getExecutableBasename(executionCommand.executable)
        }

        var newTermuxTask: AppShell? = null
        val shellCreateMode = processShellCreateMode(executionCommand) ?: return

        if (ShellCreateMode.NO_SHELL_WITH_NAME == shellCreateMode) {
            newTermuxTask = getTermuxTaskForShellName(executionCommand.shellName)
            if (newTermuxTask != null) {
                Logger.logVerbose(LOG_TAG, "Existing TermuxTask with \"${executionCommand.shellName}\" shell name found for shell create mode \"${shellCreateMode.getMode()}\"")
            } else {
                Logger.logVerbose(LOG_TAG, "No existing TermuxTask with \"${executionCommand.shellName}\" shell name found for shell create mode \"${shellCreateMode.getMode()}\"")
            }
        }

        if (newTermuxTask == null) createTermuxTask(executionCommand)
    }

    /** Create a TermuxTask. */
    fun createTermuxTask(executablePath: String?, arguments: Array<String>?, stdin: String?, workingDirectory: String?): AppShell? {
        return createTermuxTask(
            ExecutionCommand(
                TermuxShellManager.getNextShellId(), executablePath,
                arguments, stdin, workingDirectory, Runner.APP_SHELL.getName(), false
            )
        )
    }

    /** Create a TermuxTask. */
    @Synchronized
    fun createTermuxTask(executionCommand: ExecutionCommand?): AppShell? {
        if (executionCommand == null) return null

        Logger.logDebug(LOG_TAG, "Creating \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxTask")

        if (!Runner.APP_SHELL.equalsRunner(executionCommand.runner)) {
            Logger.logDebug(LOG_TAG, "Ignoring wrong runner \"${executionCommand.runner}\" command passed to createTermuxTask()")
            return null
        }

        executionCommand.setShellCommandShellEnvironment = true

        if (Logger.getLogLevel() >= Logger.LOG_LEVEL_VERBOSE) {
            Logger.logVerboseExtended(LOG_TAG, executionCommand.toString())
        }

        val newTermuxTask = AppShell.execute(
            this, executionCommand, this,
            TermuxShellEnvironment(), null, false
        )
        if (newTermuxTask == null) {
            Logger.logError(LOG_TAG, "Failed to execute new TermuxTask command for:\n${executionCommand.getCommandIdAndLabelLogString()}")
            if (executionCommand.isPluginExecutionCommand) {
                TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
            } else {
                Logger.logError(LOG_TAG, "Set log level to debug or higher to see error in logs")
                Logger.logErrorPrivateExtended(LOG_TAG, executionCommand.toString())
            }
            return null
        }

        mShellManager.mTermuxTasks.add(newTermuxTask)

        // Remove the execution command from the pending plugin execution commands list
        if (executionCommand.isPluginExecutionCommand) {
            mShellManager.mPendingPluginExecutionCommands.remove(executionCommand)
        }

        updateNotification()
        return newTermuxTask
    }

    /** Callback received when a TermuxTask finishes. */
    override fun onAppShellExited(termuxTask: AppShell) {
        mHandler.post {
            val executionCommand = termuxTask.executionCommand

            Logger.logVerbose(LOG_TAG, "The onTermuxTaskExited() callback called for \"${executionCommand?.getCommandIdAndLabelLogString()}\" TermuxTask command")

            if (executionCommand != null && executionCommand.isPluginExecutionCommand) {
                TermuxPluginUtils.processPluginExecutionCommandResult(this, LOG_TAG, executionCommand)
            }

            mShellManager.mTermuxTasks.remove(termuxTask)
            updateNotification()
        }
    }

    /** Execute a shell command in a foreground [TermuxSession]. */
    private fun executeTermuxSessionCommand(executionCommand: ExecutionCommand?) {
        if (executionCommand == null) return

        Logger.logDebug(LOG_TAG, "Executing foreground \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession command")

        // Transform executable path to shell/session name
        if (executionCommand.shellName == null && executionCommand.executable != null) {
            executionCommand.shellName = ShellUtils.getExecutableBasename(executionCommand.executable)
        }

        var newTermuxSession: TermuxSession? = null
        val shellCreateMode = processShellCreateMode(executionCommand) ?: return

        if (ShellCreateMode.NO_SHELL_WITH_NAME == shellCreateMode) {
            newTermuxSession = getTermuxSessionForShellName(executionCommand.shellName)
            if (newTermuxSession != null) {
                Logger.logVerbose(LOG_TAG, "Existing TermuxSession with \"${executionCommand.shellName}\" shell name found for shell create mode \"${shellCreateMode.getMode()}\"")
            } else {
                Logger.logVerbose(LOG_TAG, "No existing TermuxSession with \"${executionCommand.shellName}\" shell name found for shell create mode \"${shellCreateMode.getMode()}\"")
            }
        }

        if (newTermuxSession == null) {
            newTermuxSession = createTermuxSession(executionCommand)
        }
        if (newTermuxSession == null) return

        handleSessionAction(
            DataUtils.getIntFromString(
                executionCommand.sessionAction,
                TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY
            ),
            newTermuxSession.terminalSession
        )
    }

    /**
     * Create a [TermuxSession].
     * Currently called by [TermuxTerminalSessionActivityClient.addNewSession] to add a new [TermuxSession].
     */
    fun createTermuxSession(
        executablePath: String?,
        arguments: Array<String>?,
        stdin: String?,
        workingDirectory: String?,
        isFailSafe: Boolean,
        sessionName: String?
    ): TermuxSession? {
        val executionCommand = ExecutionCommand(
            TermuxShellManager.getNextShellId(),
            executablePath, arguments, stdin, workingDirectory, Runner.TERMINAL_SESSION.getName(), isFailSafe
        )
        executionCommand.shellName = sessionName
        return createTermuxSession(executionCommand)
    }

    /** Create a [TermuxSession]. */
    @Synchronized
    fun createTermuxSession(executionCommand: ExecutionCommand?): TermuxSession? {
        if (executionCommand == null) return null

        Logger.logDebug(LOG_TAG, "Creating \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession")

        if (!Runner.TERMINAL_SESSION.equalsRunner(executionCommand.runner)) {
            Logger.logDebug(LOG_TAG, "Ignoring wrong runner \"${executionCommand.runner}\" command passed to createTermuxSession()")
            return null
        }

        executionCommand.setShellCommandShellEnvironment = true
        executionCommand.terminalTranscriptRows = mProperties.getTerminalTranscriptRows()

        if (Logger.getLogLevel() >= Logger.LOG_LEVEL_VERBOSE) {
            Logger.logVerboseExtended(LOG_TAG, executionCommand.toString())
        }

        val newTermuxSession = TermuxSession.execute(
            this, executionCommand, termuxTerminalSessionClient,
            this, TermuxShellEnvironment(), null, executionCommand.isPluginExecutionCommand
        )
        if (newTermuxSession == null) {
            Logger.logError(LOG_TAG, "Failed to execute new TermuxSession command for:\n${executionCommand.getCommandIdAndLabelLogString()}")
            if (executionCommand.isPluginExecutionCommand) {
                TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
            } else {
                Logger.logError(LOG_TAG, "Set log level to debug or higher to see error in logs")
                Logger.logErrorPrivateExtended(LOG_TAG, executionCommand.toString())
            }
            return null
        }

        mShellManager.mTermuxSessions.add(newTermuxSession)

        // Remove the execution command from the pending plugin execution commands list
        if (executionCommand.isPluginExecutionCommand) {
            mShellManager.mPendingPluginExecutionCommands.remove(executionCommand)
        }

        // Notify TermuxSessionsListViewController that sessions list has been updated
        mTermuxTerminalSessionActivityClient?.termuxSessionListNotifyUpdated()

        updateNotification()

        // No need to recreate the activity since it likely just started and theme should already have applied
        TermuxActivity.updateTermuxActivityStyling(this, false)

        return newTermuxSession
    }

    /** Remove a TermuxSession. */
    @Synchronized
    fun removeTermuxSession(sessionToRemove: TerminalSession?): Int {
        val index = getIndexOfSession(sessionToRemove)
        if (index >= 0) mShellManager.mTermuxSessions[index].finish()
        return index
    }

    /** Callback received when a [TermuxSession] finishes. */
    override fun onTermuxSessionExited(termuxSession: TermuxSession) {
        val executionCommand = termuxSession.executionCommand

        Logger.logVerbose(LOG_TAG, "The onTermuxSessionExited() callback called for \"${executionCommand?.getCommandIdAndLabelLogString()}\" TermuxSession command")

        if (executionCommand != null && executionCommand.isPluginExecutionCommand) {
            TermuxPluginUtils.processPluginExecutionCommandResult(this, LOG_TAG, executionCommand)
        }

        mShellManager.mTermuxSessions.remove(termuxSession)
        mTermuxTerminalSessionActivityClient?.termuxSessionListNotifyUpdated()
        updateNotification()
    }

    private fun processShellCreateMode(executionCommand: ExecutionCommand): ShellCreateMode? {
        return when {
            ShellCreateMode.ALWAYS.equalsMode(executionCommand.shellCreateMode) -> ShellCreateMode.ALWAYS
            ShellCreateMode.NO_SHELL_WITH_NAME.equalsMode(executionCommand.shellCreateMode) -> {
                if (DataUtils.isNullOrEmpty(executionCommand.shellName)) {
                    TermuxPluginUtils.setAndProcessPluginExecutionCommandError(
                        this, LOG_TAG, executionCommand, false,
                        getString(R.string.error_termux_service_execution_command_shell_name_unset, executionCommand.shellCreateMode)
                    )
                    null
                } else {
                    ShellCreateMode.NO_SHELL_WITH_NAME
                }
            }
            else -> {
                TermuxPluginUtils.setAndProcessPluginExecutionCommandError(
                    this, LOG_TAG, executionCommand, false,
                    getString(R.string.error_termux_service_unsupported_execution_command_shell_create_mode, executionCommand.shellCreateMode)
                )
                null
            }
        }
    }

    /** Process session action for new session. */
    private fun handleSessionAction(sessionAction: Int, newTerminalSession: TerminalSession) {
        Logger.logDebug(LOG_TAG, "Processing sessionAction \"$sessionAction\" for session \"${newTerminalSession.mSessionName}\"")

        when (sessionAction) {
            TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY -> {
                setCurrentStoredTerminalSession(newTerminalSession)
                mTermuxTerminalSessionActivityClient?.setCurrentSession(newTerminalSession)
                startTermuxActivity()
            }
            TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY -> {
                if (termuxSessionsSize == 1) setCurrentStoredTerminalSession(newTerminalSession)
                startTermuxActivity()
            }
            TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY -> {
                setCurrentStoredTerminalSession(newTerminalSession)
                mTermuxTerminalSessionActivityClient?.setCurrentSession(newTerminalSession)
            }
            TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY -> {
                if (termuxSessionsSize == 1) setCurrentStoredTerminalSession(newTerminalSession)
            }
            else -> {
                Logger.logError(LOG_TAG, "Invalid sessionAction: \"$sessionAction\". Force using default sessionAction.")
                handleSessionAction(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY, newTerminalSession)
            }
        }
    }

    /** Launch the TermuxActivity to bring it to foreground. */
    private fun startTermuxActivity() {
        if (PermissionUtils.validateDisplayOverOtherAppsPermissionForPostAndroid10(this, true)) {
            TermuxActivity.startTermuxActivity(this)
        } else {
            val preferences = TermuxAppSharedPreferences.build(this) ?: return
            if (preferences.arePluginErrorNotificationsEnabled(false)) {
                Logger.showToast(this, getString(R.string.error_display_over_other_apps_permission_not_granted_to_start_terminal), true)
            }
        }
    }

    /**
     * If [TermuxActivity] has not bound to the [TermuxService] yet or is destroyed, then
     * interface functions requiring the activity should not be available to the terminal sessions.
     */
    val termuxTerminalSessionClient: TermuxTerminalSessionClientBase
        @Synchronized get() = mTermuxTerminalSessionActivityClient ?: mTermuxTerminalSessionServiceClient

    /**
     * This should be called when [TermuxActivity.onServiceConnected] is called to set the
     * [mTermuxTerminalSessionActivityClient] variable.
     */
    @Synchronized
    fun setTermuxTerminalSessionClient(termuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient?) {
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient
        for (session in mShellManager.mTermuxSessions) {
            val client = mTermuxTerminalSessionActivityClient ?: mTermuxTerminalSessionServiceClient
            session.terminalSession.updateTerminalSessionClient(client)
        }
    }

    /**
     * This should be called when [TermuxActivity] has been destroyed and in [onUnbind]
     * so that the [TermuxService] and [TerminalSession] clients do not hold activity references.
     */
    @Synchronized
    fun unsetTermuxTerminalSessionClient() {
        for (session in mShellManager.mTermuxSessions) {
            session.terminalSession.updateTerminalSessionClient(mTermuxTerminalSessionServiceClient)
        }
        mTermuxTerminalSessionActivityClient = null
    }

    private fun buildNotification(): Notification? {
        val res = resources

        // Set pending intent to be launched when notification is clicked
        val notificationIntent = TermuxActivity.newInstance(this)
        val contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        // Set notification text
        val sessionCount = termuxSessionsSize
        val taskCount = mShellManager.mTermuxTasks.size
        var notificationText = "$sessionCount session${if (sessionCount == 1) "" else "s"}"
        if (taskCount > 0) {
            notificationText += ", $taskCount task${if (taskCount == 1) "" else "s"}"
        }

        val wakeLockHeld = mWakeLock != null
        if (wakeLockHeld) notificationText += " (wake lock held)"

        // Set notification priority
        val priority = if (wakeLockHeld) Notification.PRIORITY_HIGH else Notification.PRIORITY_LOW

        // Build the notification
        val builder = NotificationUtils.geNotificationBuilder(
            this,
            TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID, priority,
            TermuxConstants.TERMUX_APP_NAME, notificationText, null,
            contentIntent, null, NotificationUtils.NOTIFICATION_MODE_SILENT
        ) ?: return null

        builder.setShowWhen(false)
        builder.setSmallIcon(R.drawable.ic_service_notification)
        builder.setColor(0xFF607D8B.toInt())
        builder.setOngoing(true)

        // Set Exit button action
        val exitIntent = Intent(this, TermuxService::class.java).setAction(TERMUX_SERVICE.ACTION_STOP_SERVICE)
        builder.addAction(
            android.R.drawable.ic_delete,
            res.getString(R.string.notification_action_exit),
            PendingIntent.getService(this, 0, exitIntent, PendingIntent.FLAG_IMMUTABLE)
        )

        // Set Wakelock button actions
        val newWakeAction = if (wakeLockHeld) TERMUX_SERVICE.ACTION_WAKE_UNLOCK else TERMUX_SERVICE.ACTION_WAKE_LOCK
        val toggleWakeLockIntent = Intent(this, TermuxService::class.java).setAction(newWakeAction)
        val actionTitle = res.getString(if (wakeLockHeld) R.string.notification_action_wake_unlock else R.string.notification_action_wake_lock)
        val actionIcon = if (wakeLockHeld) android.R.drawable.ic_lock_idle_lock else android.R.drawable.ic_lock_lock
        builder.addAction(actionIcon, actionTitle, PendingIntent.getService(this, 0, toggleWakeLockIntent, PendingIntent.FLAG_IMMUTABLE))

        return builder.build()
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        NotificationUtils.setupNotificationChannel(
            this, TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID,
            TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
        )
    }

    /** Update the shown foreground service notification after making any changes that affect it. */
    @Synchronized
    private fun updateNotification() {
        if (mWakeLock == null && mShellManager.mTermuxSessions.isEmpty() && mShellManager.mTermuxTasks.isEmpty()) {
            requestStopService()
        } else {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(TermuxConstants.TERMUX_APP_NOTIFICATION_ID, buildNotification())
        }
    }

    private fun setCurrentStoredTerminalSession(terminalSession: TerminalSession?) {
        if (terminalSession == null) return
        val preferences = TermuxAppSharedPreferences.build(this) ?: return
        preferences.setCurrentSession(terminalSession.mHandle)
    }

    val isTermuxSessionsEmpty: Boolean
        @Synchronized get() = mShellManager.mTermuxSessions.isEmpty()

    val termuxSessionsSize: Int
        @Synchronized get() = mShellManager.mTermuxSessions.size

    val termuxSessions: List<TermuxSession>
        @Synchronized get() = mShellManager.mTermuxSessions

    @Synchronized
    fun getTermuxSession(index: Int): TermuxSession? {
        return if (index >= 0 && index < mShellManager.mTermuxSessions.size) {
            mShellManager.mTermuxSessions[index]
        } else null
    }

    @Synchronized
    fun getTermuxSessionForTerminalSession(terminalSession: TerminalSession?): TermuxSession? {
        if (terminalSession == null) return null
        return mShellManager.mTermuxSessions.find { it.terminalSession == terminalSession }
    }

    val lastTermuxSession: TermuxSession?
        @Synchronized get() = mShellManager.mTermuxSessions.lastOrNull()

    @Synchronized
    fun getIndexOfSession(terminalSession: TerminalSession?): Int {
        if (terminalSession == null) return -1
        return mShellManager.mTermuxSessions.indexOfFirst { it.terminalSession == terminalSession }
    }

    @Synchronized
    fun getTerminalSessionForHandle(sessionHandle: String): TerminalSession? {
        return mShellManager.mTermuxSessions.find { it.terminalSession.mHandle == sessionHandle }?.terminalSession
    }

    @Synchronized
    fun getTermuxTaskForShellName(name: String?): AppShell? {
        if (DataUtils.isNullOrEmpty(name)) return null
        return mShellManager.mTermuxTasks.find { it.executionCommand.shellName == name }
    }

    @Synchronized
    fun getTermuxSessionForShellName(name: String?): TermuxSession? {
        if (DataUtils.isNullOrEmpty(name)) return null
        return mShellManager.mTermuxSessions.find { it.executionCommand.shellName == name }
    }

    fun wantsToStop(): Boolean = mWantsToStop

    companion object {
        private const val LOG_TAG = "TermuxService"
    }
}
