package com.termux.shared.shell.command.environment

import android.content.Context
import com.termux.shared.shell.command.ExecutionCommand

/**
 * Interface for shell environment configuration.
 */
interface IShellEnvironment {

    /**
     * Get the default working directory path for the environment in case the path that was passed
     * was null or empty.
     *
     * @return Should return the default working directory path.
     */
    fun getDefaultWorkingDirectoryPath(): String

    /**
     * Get the default "/bin" path, like $PREFIX/bin.
     *
     * @return Should return the "/bin" path.
     */
    fun getDefaultBinPath(): String

    /**
     * Setup shell command arguments for the file to execute, like interpreter, etc.
     *
     * @param fileToExecute The file to execute.
     * @param arguments The arguments to pass to the executable.
     * @return Should return the final process arguments.
     */
    fun setupShellCommandArguments(fileToExecute: String, arguments: Array<String>?): Array<String>

    /**
     * Setup shell command environment to be used for commands.
     *
     * @param currentPackageContext The [Context] for the current package.
     * @param executionCommand The [ExecutionCommand] for which to set environment.
     * @return Should return the shell environment.
     */
    fun setupShellCommandEnvironment(
        currentPackageContext: Context,
        executionCommand: ExecutionCommand
    ): HashMap<String, String>
}
