package com.termux.shared.termux.shell.command.environment

import android.content.Context
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger
import com.termux.shared.shell.command.environment.AndroidShellEnvironment
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils
import com.termux.shared.termux.TermuxBootstrap
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.shell.TermuxShellUtils
import java.nio.charset.Charset

/**
 * Environment for Termux.
 */
open class TermuxShellEnvironment : AndroidShellEnvironment() {

    init {
        shellCommandShellEnvironment = TermuxShellCommandShellEnvironment()
    }

    /** Get shell environment for Termux. */
    override fun getEnvironment(currentPackageContext: Context, isFailSafe: Boolean): HashMap<String, String> {
        // Termux environment builds upon the Android environment
        val environment = super.getEnvironment(currentPackageContext, isFailSafe)

        TermuxAppShellEnvironment.getEnvironment(currentPackageContext)?.let {
            environment.putAll(it)
        }

        TermuxAPIShellEnvironment.getEnvironment(currentPackageContext)?.let {
            environment.putAll(it)
        }

        environment[ENV_HOME] = TermuxConstants.TERMUX_HOME_DIR_PATH
        environment[ENV_PREFIX] = TermuxConstants.TERMUX_PREFIX_DIR_PATH

        // If failsafe is not enabled, then we keep default PATH and TMPDIR so that system binaries can be used
        if (!isFailSafe) {
            environment[ENV_TMPDIR] = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH
            if (TermuxBootstrap.isAppPackageVariantAPTAndroid5()) {
                // Termux in android 5/6 era shipped busybox binaries in applets directory
                environment[ENV_PATH] = "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}:${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/applets"
                environment[ENV_LD_LIBRARY_PATH] = TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH
            } else {
                // Termux binaries on Android 7+ rely on DT_RUNPATH, so LD_LIBRARY_PATH should be unset by default
                environment[ENV_PATH] = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
                environment.remove(ENV_LD_LIBRARY_PATH)
            }
        }

        return environment
    }

    override fun getDefaultWorkingDirectoryPath(): String {
        return TermuxConstants.TERMUX_HOME_DIR_PATH
    }

    override fun getDefaultBinPath(): String {
        return TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
    }

    override fun setupShellCommandArguments(executable: String, arguments: Array<String>?): Array<String> {
        return TermuxShellUtils.setupShellCommandArguments(executable, arguments)
    }

    companion object {
        private const val LOG_TAG = "TermuxShellEnvironment"

        /** Environment variable for the termux [TermuxConstants.TERMUX_PREFIX_DIR_PATH]. */
        const val ENV_PREFIX = "PREFIX"

        /** Init [TermuxShellEnvironment] constants and caches. */
        @JvmStatic
        @Synchronized
        fun init(currentPackageContext: Context) {
            TermuxAppShellEnvironment.setTermuxAppEnvironment(currentPackageContext)
        }

        /** Write environment to file. */
        @JvmStatic
        @Synchronized
        fun writeEnvironmentToFile(currentPackageContext: Context) {
            val environmentMap = TermuxShellEnvironment().getEnvironment(currentPackageContext, false)
            val environmentString = ShellEnvironmentUtils.convertEnvironmentToDotEnvFile(environmentMap)

            // Write environment string to temp file and then move to final location since otherwise
            // writing may happen while file is being sourced/read
            var error: Error? = FileUtils.writeTextToFile(
                "termux.env.tmp", TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH,
                Charset.defaultCharset(), environmentString, false
            )
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, error.toString())
                return
            }

            error = FileUtils.moveRegularFile(
                "termux.env.tmp", TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH,
                TermuxConstants.TERMUX_ENV_FILE_PATH, true
            )
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, error.toString())
            }
        }
    }
}
