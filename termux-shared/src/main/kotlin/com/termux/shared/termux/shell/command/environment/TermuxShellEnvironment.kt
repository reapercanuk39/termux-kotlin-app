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
                // Termux binaries on Android 7+ normally rely on DT_RUNPATH, but upstream binaries
                // have RUNPATH hardcoded to /data/data/com.termux/files/usr/lib which doesn't work
                // for our com.termux.kotlin package. Setting LD_LIBRARY_PATH overrides RUNPATH.
                environment[ENV_PATH] = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
                environment[ENV_LD_LIBRARY_PATH] = TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH
            }
            
            // dpkg and apt have hardcoded paths to /data/data/com.termux compiled into their
            // ELF binaries. We can't modify ELF binaries without corrupting them, so we use
            // environment variables to override the paths at runtime.
            // DPKG_ADMINDIR: where dpkg stores its database (status, available, etc.)
            // DPKG_DATADIR: where dpkg data files are stored
            environment[ENV_DPKG_ADMINDIR] = "${TermuxConstants.TERMUX_PREFIX_DIR_PATH}/var/lib/dpkg"
            environment[ENV_DPKG_DATADIR] = "${TermuxConstants.TERMUX_PREFIX_DIR_PATH}/share/dpkg"
            
            // TERMINFO: path to terminal capability database (required for clear, tput, ncurses apps)
            environment[ENV_TERMINFO] = "${TermuxConstants.TERMUX_PREFIX_DIR_PATH}/share/terminfo"
            
            // SSL/TLS certificate bundle paths for curl, wget, and other SSL-enabled apps
            // This enables HTTPS connections to work properly (package mirrors, etc.)
            val caCertPath = "${TermuxConstants.TERMUX_PREFIX_DIR_PATH}/etc/tls/cert.pem"
            environment[ENV_SSL_CERT_FILE] = caCertPath
            environment[ENV_CURL_CA_BUNDLE] = caCertPath
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
        
        /** Environment variable for dpkg administrative directory (database location). */
        const val ENV_DPKG_ADMINDIR = "DPKG_ADMINDIR"
        
        /** Environment variable for dpkg data directory. */
        const val ENV_DPKG_DATADIR = "DPKG_DATADIR"
        
        /** Environment variable for terminal info database location. */
        const val ENV_TERMINFO = "TERMINFO"
        
        /** Environment variable for SSL certificate bundle (used by curl, wget, openssl). */
        const val ENV_SSL_CERT_FILE = "SSL_CERT_FILE"
        
        /** Environment variable for curl CA bundle path. */
        const val ENV_CURL_CA_BUNDLE = "CURL_CA_BUNDLE"

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
