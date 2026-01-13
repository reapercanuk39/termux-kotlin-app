package com.termux.shared.shell.command.environment

import android.content.Context
import com.termux.shared.shell.ShellUtils
import com.termux.shared.shell.command.ExecutionCommand

/**
 * Environment for Unix-like systems.
 *
 * https://manpages.debian.org/testing/manpages/environ.7.en.html
 * https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap08.html
 */
abstract class UnixShellEnvironment : IShellEnvironment {

    companion object {
        /** Environment variable for the terminal's colour capabilities. */
        const val ENV_COLORTERM = "COLORTERM"

        /** Environment variable for the path of the user's home directory. */
        const val ENV_HOME = "HOME"

        /** Environment variable for the locale category for native language, local customs, and coded
         * character set in the absence of the LC_ALL and other LC_* environment variables. */
        const val ENV_LANG = "LANG"

        /** Environment variable for the represent the sequence of directory paths separated with
         * colons ":" that should be searched in for dynamic shared libraries to link programs against. */
        const val ENV_LD_LIBRARY_PATH = "LD_LIBRARY_PATH"

        /** Environment variable for the represent the sequence of directory path prefixes separated with
         * colons ":" that certain functions and utilities apply in searching for an executable file
         * known only by a filename. */
        const val ENV_PATH = "PATH"

        /** Environment variable for the absolute path of the current working directory. It shall not
         * contain any components that are dot or dot-dot. The value is set by the cd utility, and by
         * the sh utility during initialization. */
        const val ENV_PWD = "PWD"

        /** Environment variable for the terminal type for which output is to be prepared. This information
         * is used by utilities and application programs wishing to exploit special capabilities specific
         * to a terminal. The format and allowable values of this environment variable are unspecified. */
        const val ENV_TERM = "TERM"

        /** Environment variable for the path of a directory made available for programs that need a place
         * to create temporary files. */
        const val ENV_TMPDIR = "TMPDIR"

        /** Names for common/supported login shell binaries. */
        @JvmField
        val LOGIN_SHELL_BINARIES = arrayOf("login", "bash", "zsh", "fish", "sh")
    }

    abstract fun getEnvironment(currentPackageContext: Context, isFailSafe: Boolean): HashMap<String, String>

    abstract override fun getDefaultWorkingDirectoryPath(): String

    abstract override fun getDefaultBinPath(): String

    override fun setupShellCommandArguments(executable: String, arguments: Array<String>?): Array<String> {
        return ShellUtils.setupShellCommandArguments(executable, arguments)
    }

    abstract override fun setupShellCommandEnvironment(
        currentPackageContext: Context,
        executionCommand: ExecutionCommand
    ): HashMap<String, String>
}
