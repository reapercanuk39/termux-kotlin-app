package com.termux.shared.termux.shell

import android.content.Context
import android.content.Intent
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.runner.app.AppShell
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession

class TermuxShellManager private constructor(context: Context) {

    @JvmField
    protected val mContext: Context = context.applicationContext

    /**
     * The foreground TermuxSessions which this service manages.
     * Note that this list is observed by an activity, like TermuxActivity.mTermuxSessionListViewController,
     * so any changes must be made on the UI thread and followed by a call to
     * ArrayAdapter.notifyDataSetChanged().
     */
    @JvmField
    val mTermuxSessions: MutableList<TermuxSession> = ArrayList()

    /** The background TermuxTasks which this service manages. */
    @JvmField
    val mTermuxTasks: MutableList<AppShell> = ArrayList()

    /** The pending plugin ExecutionCommands that have yet to be processed by this service. */
    @JvmField
    val mPendingPluginExecutionCommands: MutableList<ExecutionCommand> = ArrayList()

    companion object {
        private var shellManager: TermuxShellManager? = null
        private var SHELL_ID = 0

        /** The [ExecutionCommand.Runner.APP_SHELL] number after app process was started/restarted. */
        @JvmStatic
        var APP_SHELL_NUMBER_SINCE_APP_START = 0

        /** The [ExecutionCommand.Runner.TERMINAL_SESSION] number after app process was started/restarted. */
        @JvmStatic
        var TERMINAL_SESSION_NUMBER_SINCE_APP_START = 0

        /**
         * Initialize the [shellManager].
         *
         * @param context The [Context] for operations.
         * @return Returns the [TermuxShellManager].
         */
        @JvmStatic
        fun init(context: Context): TermuxShellManager {
            if (shellManager == null) {
                shellManager = TermuxShellManager(context)
            }
            return shellManager!!
        }

        /**
         * Get the [shellManager].
         *
         * @return Returns the [TermuxShellManager].
         */
        @JvmStatic
        fun getShellManager(): TermuxShellManager? {
            return shellManager
        }

        @JvmStatic
        @Synchronized
        fun onActionBootCompleted(context: Context, intent: Intent) {
            val preferences = TermuxAppSharedPreferences.build(context) ?: return

            // Ensure any shells started after boot have valid ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_BOOT and
            // ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_BOOT exported
            preferences.resetAppShellNumberSinceBoot()
            preferences.resetTerminalSessionNumberSinceBoot()
        }

        @JvmStatic
        fun onAppExit(context: Context) {
            // Ensure any shells started after boot have valid ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_APP_START and
            // ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_APP_START exported
            APP_SHELL_NUMBER_SINCE_APP_START = 0
            TERMINAL_SESSION_NUMBER_SINCE_APP_START = 0
        }

        @JvmStatic
        @Synchronized
        fun getNextShellId(): Int {
            return SHELL_ID++
        }

        @JvmStatic
        @Synchronized
        fun getAndIncrementAppShellNumberSinceAppStart(): Int {
            // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
            var curValue = APP_SHELL_NUMBER_SINCE_APP_START
            if (curValue < 0) curValue = Int.MAX_VALUE

            APP_SHELL_NUMBER_SINCE_APP_START = curValue + 1
            if (APP_SHELL_NUMBER_SINCE_APP_START < 0) APP_SHELL_NUMBER_SINCE_APP_START = Int.MAX_VALUE
            return curValue
        }

        @JvmStatic
        @Synchronized
        fun getAndIncrementTerminalSessionNumberSinceAppStart(): Int {
            // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
            var curValue = TERMINAL_SESSION_NUMBER_SINCE_APP_START
            if (curValue < 0) curValue = Int.MAX_VALUE

            TERMINAL_SESSION_NUMBER_SINCE_APP_START = curValue + 1
            if (TERMINAL_SESSION_NUMBER_SINCE_APP_START < 0) TERMINAL_SESSION_NUMBER_SINCE_APP_START = Int.MAX_VALUE
            return curValue
        }
    }
}
