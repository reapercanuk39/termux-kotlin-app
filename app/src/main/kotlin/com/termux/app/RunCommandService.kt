package com.termux.app

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder

import com.termux.R
import com.termux.shared.data.DataUtils
import com.termux.shared.data.IntentUtils
import com.termux.shared.termux.plugins.TermuxPluginUtils
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.file.filesystem.FileType
import com.termux.shared.errors.Errno
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger
import com.termux.shared.notification.NotificationUtils
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner

/**
 * A service that receives [RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND] intent from third party apps and
 * plugins that contains info on command execution and forwards the extras to [TermuxService]
 * for the actual execution.
 *
 * Check https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent for more info.
 */
class RunCommandService : Service() {

    inner class LocalBinder : Binder() {
        val service: RunCommandService = this@RunCommandService
    }

    private val mBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onCreate() {
        Logger.logVerbose(LOG_TAG, "onCreate")
        runStartForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.logDebug(LOG_TAG, "onStartCommand")

        if (intent == null) return START_NOT_STICKY

        // Run again in case service is already started and onCreate() is not called
        runStartForeground()

        Logger.logVerboseExtended(LOG_TAG, "Intent Received:\n" + IntentUtils.getIntentString(intent))

        val executionCommand = ExecutionCommand()
        executionCommand.pluginAPIHelp = this.getString(R.string.error_run_command_service_api_help, RUN_COMMAND_SERVICE.RUN_COMMAND_API_HELP_URL)

        var errmsg: String

        // If invalid action passed, then just return
        if (RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND != intent.action) {
            errmsg = this.getString(R.string.error_run_command_service_invalid_intent_action, intent.action)
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg)
            TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
            return stopService()
        }

        var executableExtra = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, null)
        executionCommand.executable = executableExtra
        executionCommand.arguments = IntentUtils.getStringArrayExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, null)

        /*
        * If intent was sent with `am` command, then normal comma characters may have been replaced
        * with alternate characters if a normal comma existed in an argument itself to prevent it
        * splitting into multiple arguments by `am` command.
        * If `tudo` or `sudo` are used, then simply using their `-r` and `--comma-alternative` command
        * options can be used without passing the below extras, but native supports is helpful if
        * they are not being used.
        * https://github.com/agnostic-apollo/tudo#passing-arguments-using-run_command-intent
        * https://android.googlesource.com/platform/frameworks/base/+/21bdaf1/cmds/am/src/com/android/commands/am/Am.java#572
        */
        val replaceCommaAlternativeCharsInArguments = intent.getBooleanExtra(RUN_COMMAND_SERVICE.EXTRA_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS, false)
        if (replaceCommaAlternativeCharsInArguments) {
            var commaAlternativeCharsInArguments = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS, null)
            if (commaAlternativeCharsInArguments == null)
                commaAlternativeCharsInArguments = TermuxConstants.COMMA_ALTERNATIVE
            // Replace any commaAlternativeCharsInArguments characters with normal commas
            DataUtils.replaceSubStringsInStringArrayItems(executionCommand.arguments, commaAlternativeCharsInArguments, TermuxConstants.COMMA_NORMAL)
        }

        executionCommand.stdin = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_STDIN, null)
        executionCommand.workingDirectory = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_WORKDIR, null)

        // If EXTRA_RUNNER is passed, use that, otherwise check EXTRA_BACKGROUND and default to Runner.TERMINAL_SESSION
        executionCommand.runner = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RUNNER,
            if (intent.getBooleanExtra(RUN_COMMAND_SERVICE.EXTRA_BACKGROUND, false)) Runner.APP_SHELL.getName() else Runner.TERMINAL_SESSION.getName())
        if (Runner.runnerOf(executionCommand.runner) == null) {
            errmsg = this.getString(R.string.error_run_command_service_invalid_execution_command_runner, executionCommand.runner)
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg)
            TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
            return stopService()
        }

        executionCommand.backgroundCustomLogLevel = IntentUtils.getIntegerExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, null)
        executionCommand.sessionAction = intent.getStringExtra(RUN_COMMAND_SERVICE.EXTRA_SESSION_ACTION)
        executionCommand.shellName = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_SHELL_NAME, null)
        executionCommand.shellCreateMode = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_SHELL_CREATE_MODE, null)
        executionCommand.commandLabel = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, "RUN_COMMAND Execution Intent Command")
        executionCommand.commandDescription = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_COMMAND_DESCRIPTION, null)
        executionCommand.commandHelp = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_COMMAND_HELP, null)
        executionCommand.isPluginExecutionCommand = true
        @Suppress("DEPRECATION")
        executionCommand.resultConfig.resultPendingIntent = intent.getParcelableExtra(RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT)
        executionCommand.resultConfig.resultDirectoryPath = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RESULT_DIRECTORY, null)
        if (executionCommand.resultConfig.resultDirectoryPath != null) {
            executionCommand.resultConfig.resultSingleFile = intent.getBooleanExtra(RUN_COMMAND_SERVICE.EXTRA_RESULT_SINGLE_FILE, false)
            executionCommand.resultConfig.resultFileBasename = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RESULT_FILE_BASENAME, null)
            executionCommand.resultConfig.resultFileOutputFormat = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RESULT_FILE_OUTPUT_FORMAT, null)
            executionCommand.resultConfig.resultFileErrorFormat = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RESULT_FILE_ERROR_FORMAT, null)
            executionCommand.resultConfig.resultFilesSuffix = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RESULT_FILES_SUFFIX, null)
        }

        // If "allow-external-apps" property to not set to "true", then just return
        // We enable force notifications if "allow-external-apps" policy is violated so that the
        // user knows someone tried to run a command in termux context, since it may be malicious
        // app or imported (tasker) plugin project and not the user himself. If a pending intent is
        // also sent, then its creator is also logged and shown.
        errmsg = TermuxPluginUtils.checkIfAllowExternalAppsPolicyIsViolated(this, LOG_TAG) ?: run {
            // Continue processing if no policy violation
            processExecutionCommand(executionCommand, intent, executableExtra)
            return stopService()
        }
        
        executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg)
        TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, true)
        return stopService()
    }

    private fun processExecutionCommand(executionCommand: ExecutionCommand, intent: Intent, executableExtra: String?) {
        var errmsg: String
        var mutableExecutableExtra = executableExtra

        // Create local copies to avoid smart cast issues with mutable properties
        val executable = executionCommand.executable
        val workingDirectory = executionCommand.workingDirectory

        // If executable is null or empty, then exit here instead of getting canonical path which would expand to "/"
        if (executable == null || executable.isEmpty()) {
            errmsg = this.getString(R.string.error_run_command_service_mandatory_extra_missing, RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH)
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg)
            TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
            return
        }

        // Get canonical path of executable
        executionCommand.executable = TermuxFileUtils.getCanonicalPath(executable, null, true)

        // If executable is not a regular file, or is not readable or executable, then just return
        // Setting of missing read and execute permissions is not done
        var error = FileUtils.validateRegularFileExistenceAndPermissions("executable", executionCommand.executable, null,
            FileUtils.APP_EXECUTABLE_FILE_PERMISSIONS, true, true,
            false)
        if (error != null) {
            executionCommand.setStateFailed(error)
            TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
            return
        }

        // If workingDirectory is not null or empty
        if (workingDirectory != null && !workingDirectory.isEmpty()) {
            // Get canonical path of workingDirectory
            executionCommand.workingDirectory = TermuxFileUtils.getCanonicalPath(workingDirectory, null, true)

            // If workingDirectory is not a directory, or is not readable or writable, then just return
            // Creation of missing directory and setting of read, write and execute permissions are only done if workingDirectory is
            // under allowed termux working directory paths.
            // We try to set execute permissions, but ignore if they are missing, since only read and write permissions are required
            // for working directories.
            error = TermuxFileUtils.validateDirectoryFileExistenceAndPermissions("working", executionCommand.workingDirectory ?: "",
                true, true, true,
                false, true)
            if (error != null) {
                executionCommand.setStateFailed(error)
                TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
                return
            }
        }

        // If the executable passed as the extra was an applet for coreutils/busybox, then we must
        // use it instead of the canonical path above since otherwise arguments would be passed to
        // coreutils/busybox instead and command would fail. Broken symlinks would already have been
        // validated so it should be fine to use it.
        mutableExecutableExtra = TermuxFileUtils.getExpandedTermuxPath(mutableExecutableExtra)
        if (mutableExecutableExtra != null && FileUtils.getFileType(mutableExecutableExtra, false) == FileType.SYMLINK) {
            Logger.logVerbose(LOG_TAG, "The executableExtra path \"$mutableExecutableExtra\" is a symlink so using it instead of the canonical path \"${executionCommand.executable}\"")
            executionCommand.executable = mutableExecutableExtra
        }

        executionCommand.executableUri = Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(executionCommand.executable).build()

        Logger.logVerboseExtended(LOG_TAG, executionCommand.toString())

        // Create execution intent with the action TERMUX_SERVICE#ACTION_SERVICE_EXECUTE to be sent to the TERMUX_SERVICE
        val execIntent = Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executionCommand.executableUri)
        execIntent.setClass(this, TermuxService::class.java)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, executionCommand.arguments)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_STDIN, executionCommand.stdin)
        val currentWorkingDirectory = executionCommand.workingDirectory
        if (currentWorkingDirectory != null && !currentWorkingDirectory.isEmpty()) execIntent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, currentWorkingDirectory)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, executionCommand.runner)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, DataUtils.getStringFromInteger(executionCommand.backgroundCustomLogLevel, null))
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION, executionCommand.sessionAction)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, executionCommand.shellName)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, executionCommand.shellCreateMode)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, executionCommand.commandLabel)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_DESCRIPTION, executionCommand.commandDescription)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_HELP, executionCommand.commandHelp)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PLUGIN_API_HELP, executionCommand.pluginAPIHelp)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, executionCommand.resultConfig.resultPendingIntent)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_DIRECTORY, executionCommand.resultConfig.resultDirectoryPath)
        if (executionCommand.resultConfig.resultDirectoryPath != null) {
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_SINGLE_FILE, executionCommand.resultConfig.resultSingleFile)
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_FILE_BASENAME, executionCommand.resultConfig.resultFileBasename)
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_FILE_OUTPUT_FORMAT, executionCommand.resultConfig.resultFileOutputFormat)
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_FILE_ERROR_FORMAT, executionCommand.resultConfig.resultFileErrorFormat)
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_FILES_SUFFIX, executionCommand.resultConfig.resultFilesSuffix)
        }

        // Start TERMUX_SERVICE and pass it execution intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.startForegroundService(execIntent)
        } else {
            this.startService(execIntent)
        }
    }

    private fun stopService(): Int {
        runStopForeground()
        return START_NOT_STICKY
    }

    private fun runStartForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupNotificationChannel()
            startForeground(TermuxConstants.TERMUX_RUN_COMMAND_NOTIFICATION_ID, buildNotification())
        }
    }

    private fun runStopForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true)
        }
    }

    private fun buildNotification(): Notification? {
        // Build the notification
        val builder = NotificationUtils.geNotificationBuilder(this,
            TermuxConstants.TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_ID, Notification.PRIORITY_LOW,
            TermuxConstants.TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_NAME, null, null,
            null, null, NotificationUtils.NOTIFICATION_MODE_SILENT)
            ?: return null

        // No need to show a timestamp:
        builder.setShowWhen(false)

        // Set notification icon
        builder.setSmallIcon(R.drawable.ic_service_notification)

        // Set background color for small notification icon
        builder.setColor(0xFF607D8B.toInt())

        return builder.build()
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        NotificationUtils.setupNotificationChannel(this, TermuxConstants.TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_ID,
            TermuxConstants.TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
    }

    companion object {
        private const val LOG_TAG = "RunCommandService"
    }
}
