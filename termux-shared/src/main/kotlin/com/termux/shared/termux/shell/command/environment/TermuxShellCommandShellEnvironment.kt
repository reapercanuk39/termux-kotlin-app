package com.termux.shared.termux.shell.command.environment

import android.content.Context
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.environment.ShellCommandShellEnvironment
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.shell.TermuxShellManager

/**
 * Environment for Termux [ExecutionCommand].
 */
class TermuxShellCommandShellEnvironment : ShellCommandShellEnvironment() {

    /** Get shell environment containing info for Termux [ExecutionCommand]. */
    override fun getEnvironment(
        currentPackageContext: Context,
        executionCommand: ExecutionCommand
    ): HashMap<String, String> {
        val environment = super.getEnvironment(currentPackageContext, executionCommand)

        val preferences = TermuxAppSharedPreferences.build(currentPackageContext) ?: return environment

        when {
            ExecutionCommand.Runner.APP_SHELL.equalsRunner(executionCommand.runner) -> {
                ShellEnvironmentUtils.putToEnvIfSet(
                    environment,
                    ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_BOOT,
                    preferences.getAndIncrementAppShellNumberSinceBoot().toString()
                )
                ShellEnvironmentUtils.putToEnvIfSet(
                    environment,
                    ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_APP_START,
                    TermuxShellManager.getAndIncrementAppShellNumberSinceAppStart().toString()
                )
            }
            ExecutionCommand.Runner.TERMINAL_SESSION.equalsRunner(executionCommand.runner) -> {
                ShellEnvironmentUtils.putToEnvIfSet(
                    environment,
                    ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_BOOT,
                    preferences.getAndIncrementTerminalSessionNumberSinceBoot().toString()
                )
                ShellEnvironmentUtils.putToEnvIfSet(
                    environment,
                    ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_APP_START,
                    TermuxShellManager.getAndIncrementTerminalSessionNumberSinceAppStart().toString()
                )
            }
            else -> return environment
        }

        return environment
    }
}
