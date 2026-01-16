package com.termux.app

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Build
import android.os.Environment
import android.system.Os
import android.util.Pair
import android.view.WindowManager
import com.termux.R
import com.termux.shared.android.PackageUtils
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.interact.MessageDialogUtils
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR
import com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH
import com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR
import com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.crash.TermuxCrashUtils
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Install the Termux bootstrap packages if necessary by following the below steps:
 *
 * (1) If $PREFIX already exist, assume that it is correct and be done.
 *
 * (2) A progress dialog is shown with "Installing..." message and a spinner.
 *
 * (3) A staging directory, $STAGING_PREFIX, is cleared if left over from broken installation below.
 *
 * (4) The zip file is loaded from a shared library.
 *
 * (5) The zip, containing entries relative to the $PREFIX, is downloaded and extracted.
 */
object TermuxInstaller {

    private const val LOG_TAG = "TermuxInstaller"

    /** Performs bootstrap setup if necessary. */
    @JvmStatic
    fun setupBootstrapIfNeeded(activity: Activity, whenDone: Runnable) {
        val filesDirectoryAccessibleError = TermuxFileUtils.isTermuxFilesDirectoryAccessible(activity, true, true)
        val isFilesDirectoryAccessible = filesDirectoryAccessibleError == null

        // Termux can only be run as the primary user (device owner)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !PackageUtils.isCurrentUserThePrimaryUser(activity)) {
            val bootstrapErrorMessage = activity.getString(
                R.string.bootstrap_error_not_primary_user_message,
                MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false)
            )
            Logger.logError(LOG_TAG, "isFilesDirectoryAccessible: $isFilesDirectoryAccessible")
            Logger.logError(LOG_TAG, bootstrapErrorMessage)
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage)
            MessageDialogUtils.exitAppWithErrorMessage(
                activity,
                activity.getString(R.string.bootstrap_error_title),
                bootstrapErrorMessage
            )
            return
        }

        if (!isFilesDirectoryAccessible) {
            var bootstrapErrorMessage = Error.getMinimalErrorString(filesDirectoryAccessibleError)
            if (PackageUtils.isAppInstalledOnExternalStorage(activity) &&
                TermuxConstants.TERMUX_FILES_DIR_PATH != activity.filesDir.absolutePath.replaceFirst("^/data/user/0/".toRegex(), "/data/data/")
            ) {
                bootstrapErrorMessage += "\n\n" + activity.getString(
                    R.string.bootstrap_error_installed_on_portable_sd,
                    MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false)
                )
            }

            Logger.logError(LOG_TAG, bootstrapErrorMessage)
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage)
            MessageDialogUtils.showMessage(
                activity,
                activity.getString(R.string.bootstrap_error_title),
                bootstrapErrorMessage,
                null
            )
            return
        }

        // If prefix directory exists
        if (FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true)) {
            if (TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
                Logger.logInfo(LOG_TAG, "The termux prefix directory \"$TERMUX_PREFIX_DIR_PATH\" exists but is empty or only contains specific unimportant files.")
            } else {
                whenDone.run()
                return
            }
        } else if (FileUtils.fileExists(TERMUX_PREFIX_DIR_PATH, false)) {
            Logger.logInfo(LOG_TAG, "The termux prefix directory \"$TERMUX_PREFIX_DIR_PATH\" does not exist but another file exists at its destination.")
        }

        @Suppress("DEPRECATION")
        val progress = ProgressDialog.show(activity, null, activity.getString(R.string.bootstrap_installer_body), true, false)
        Thread {
            try {
                Logger.logInfo(LOG_TAG, "Installing ${TermuxConstants.TERMUX_APP_NAME} bootstrap packages.")

                var error: Error?

                // Delete prefix staging directory or any file at its destination
                error = FileUtils.deleteFile("termux prefix staging directory", TERMUX_STAGING_PREFIX_DIR_PATH, true)
                if (error != null) {
                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error))
                    return@Thread
                }

                // Delete prefix directory or any file at its destination
                error = FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true)
                if (error != null) {
                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error))
                    return@Thread
                }

                // Create prefix staging directory if it does not already exist and set required permissions
                error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true)
                if (error != null) {
                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error))
                    return@Thread
                }

                // Create prefix directory if it does not already exist and set required permissions
                error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true)
                if (error != null) {
                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error))
                    return@Thread
                }

                Logger.logInfo(LOG_TAG, "Extracting bootstrap zip to prefix staging directory \"$TERMUX_STAGING_PREFIX_DIR_PATH\".")

                val buffer = ByteArray(8096)
                val symlinks = ArrayList<Pair<String, String>>(50)

                val zipBytes = loadZipBytes()
                
                // Build the path prefix for replacing upstream paths with our package paths
                val upstreamFilesPrefix = "/data/data/${TermuxConstants.TERMUX_UPSTREAM_PACKAGE_NAME}/files"
                val ourFilesPrefix = "/data/data/${TermuxConstants.TERMUX_PACKAGE_NAME}/files"
                
                ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipInput ->
                    var zipEntry = zipInput.nextEntry
                    while (zipEntry != null) {
                        if (zipEntry.name == "SYMLINKS.txt") {
                            val symlinksReader = BufferedReader(InputStreamReader(zipInput))
                            var line = symlinksReader.readLine()
                            while (line != null) {
                                val parts = line.split("←")
                                if (parts.size != 2) throw RuntimeException("Malformed symlink line: $line")
                                // Replace upstream package path with our package path in symlink targets
                                val oldPath = parts[0].replace(upstreamFilesPrefix, ourFilesPrefix)
                                val newPath = "$TERMUX_STAGING_PREFIX_DIR_PATH/${parts[1]}"
                                symlinks.add(Pair.create(oldPath, newPath))

                                error = ensureDirectoryExists(File(newPath).parentFile!!)
                                if (error != null) {
                                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error))
                                    return@Thread
                                }
                                line = symlinksReader.readLine()
                            }
                        } else {
                            val zipEntryName = zipEntry.name
                            val targetFile = File(TERMUX_STAGING_PREFIX_DIR_PATH, zipEntryName)
                            val isDirectory = zipEntry.isDirectory

                            error = ensureDirectoryExists(if (isDirectory) targetFile else targetFile.parentFile!!)
                            if (error != null) {
                                showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error))
                                return@Thread
                            }

                            if (!isDirectory) {
                                FileOutputStream(targetFile).use { outStream ->
                                    var readBytes = zipInput.read(buffer)
                                    while (readBytes != -1) {
                                        outStream.write(buffer, 0, readBytes)
                                        readBytes = zipInput.read(buffer)
                                    }
                                }
                                if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") ||
                                    zipEntryName.startsWith("lib/apt/apt-helper") || zipEntryName.startsWith("lib/apt/methods")
                                ) {
                                    Os.chmod(targetFile.absolutePath, 448) // 0700 octal
                                }
                                
                                // Fix hardcoded paths in shell scripts and config files
                                if (isTextFileNeedingPathFix(zipEntryName)) {
                                    fixPathsInTextFile(targetFile, upstreamFilesPrefix, ourFilesPrefix)
                                }
                            }
                        }
                        zipEntry = zipInput.nextEntry
                    }
                }

                if (symlinks.isEmpty()) throw RuntimeException("No SYMLINKS.txt encountered")
                for (symlink in symlinks) {
                    Os.symlink(symlink.first, symlink.second)
                }
                
                // Fix login script to avoid bash's hardcoded profile path issue
                // When original Termux is installed, bash finds the old /etc/profile (permission denied)
                // We fix this by using --noprofile and manually sourcing our profile
                fixLoginScript(File(TERMUX_STAGING_PREFIX_DIR_PATH, "bin/login"), ourFilesPrefix)
                
                // Create dpkg wrapper to handle hardcoded config path issues
                createDpkgWrapper(File(TERMUX_STAGING_PREFIX_DIR_PATH, "bin"), ourFilesPrefix)

                Logger.logInfo(LOG_TAG, "Moving termux prefix staging to prefix directory.")

                if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                    throw RuntimeException("Moving termux prefix staging to prefix directory failed")
                }

                Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.")

                // Recreate env file since termux prefix was wiped earlier
                TermuxShellEnvironment.writeEnvironmentToFile(activity)

                activity.runOnUiThread(whenDone)

            } catch (e: Exception) {
                showBootstrapErrorDialog(activity, whenDone, Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)))
            } finally {
                activity.runOnUiThread {
                    try {
                        progress.dismiss()
                    } catch (e: RuntimeException) {
                        // Activity already dismissed - ignore.
                    }
                }
            }
        }.start()
    }

    @JvmStatic
    fun showBootstrapErrorDialog(activity: Activity, whenDone: Runnable, message: String) {
        Logger.logErrorExtended(LOG_TAG, "Bootstrap Error:\n$message")

        // Send a notification with the exception so that the user knows why bootstrap setup failed
        sendBootstrapCrashReportNotification(activity, message)

        activity.runOnUiThread {
            try {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.bootstrap_error_title)
                    .setMessage(R.string.bootstrap_error_body)
                    .setNegativeButton(R.string.bootstrap_error_abort) { dialog, _ ->
                        dialog.dismiss()
                        activity.finish()
                    }
                    .setPositiveButton(R.string.bootstrap_error_try_again) { dialog, _ ->
                        dialog.dismiss()
                        FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true)
                        setupBootstrapIfNeeded(activity, whenDone)
                    }
                    .show()
            } catch (e1: WindowManager.BadTokenException) {
                // Activity already dismissed - ignore.
            }
        }
    }

    private fun sendBootstrapCrashReportNotification(activity: Activity, message: String) {
        val title = "${TermuxConstants.TERMUX_APP_NAME} Bootstrap Error"

        TermuxCrashUtils.sendCrashReportNotification(
            activity, LOG_TAG,
            title, null, "## $title\n\n$message\n\n${TermuxUtils.getTermuxDebugMarkdownString(activity)}",
            true, false, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES, true
        )
    }

    @JvmStatic
    fun setupStorageSymlinks(context: Context) {
        val logTag = "termux-storage"
        val title = "${TermuxConstants.TERMUX_APP_NAME} Setup Storage Error"

        Logger.logInfo(logTag, "Setting up storage symlinks.")

        Thread {
            try {
                var error: Error?
                val storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR

                error = FileUtils.clearDirectory("~/storage", storageDir.absolutePath)
                if (error != null) {
                    Logger.logErrorAndShowToast(context, logTag, error.message ?: "")
                    Logger.logErrorExtended(logTag, "Setup Storage Error\n$error")
                    TermuxCrashUtils.sendCrashReportNotification(
                        context, logTag, title, null,
                        "## $title\n\n${Error.getErrorMarkdownString(error)}",
                        true, false, TermuxUtils.AppInfoMode.TERMUX_PACKAGE, true
                    )
                    return@Thread
                }

                Logger.logInfo(logTag, "Setting up storage symlinks at ~/storage/shared, ~/storage/downloads, ~/storage/dcim, ~/storage/pictures, ~/storage/music and ~/storage/movies for directories in \"${Environment.getExternalStorageDirectory().absolutePath}\".")

                // Get primary storage root "/storage/emulated/0" symlink
                val sharedDir = Environment.getExternalStorageDirectory()
                Os.symlink(sharedDir.absolutePath, File(storageDir, "shared").absolutePath)

                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                Os.symlink(documentsDir.absolutePath, File(storageDir, "documents").absolutePath)

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                Os.symlink(downloadsDir.absolutePath, File(storageDir, "downloads").absolutePath)

                val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                Os.symlink(dcimDir.absolutePath, File(storageDir, "dcim").absolutePath)

                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                Os.symlink(picturesDir.absolutePath, File(storageDir, "pictures").absolutePath)

                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                Os.symlink(musicDir.absolutePath, File(storageDir, "music").absolutePath)

                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                Os.symlink(moviesDir.absolutePath, File(storageDir, "movies").absolutePath)

                val podcastsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS)
                Os.symlink(podcastsDir.absolutePath, File(storageDir, "podcasts").absolutePath)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val audiobooksDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS)
                    Os.symlink(audiobooksDir.absolutePath, File(storageDir, "audiobooks").absolutePath)
                }

                // Create "Android/data/com.termux" symlinks
                val externalFilesDirs = context.getExternalFilesDirs(null)
                if (externalFilesDirs != null && externalFilesDirs.isNotEmpty()) {
                    for (i in externalFilesDirs.indices) {
                        val dir = externalFilesDirs[i] ?: continue
                        val symlinkName = "external-$i"
                        Logger.logInfo(logTag, "Setting up storage symlinks at ~/storage/$symlinkName for \"${dir.absolutePath}\".")
                        Os.symlink(dir.absolutePath, File(storageDir, symlinkName).absolutePath)
                    }
                }

                // Create "Android/media/com.termux" symlinks
                @Suppress("DEPRECATION")
                val externalMediaDirs = context.externalMediaDirs
                if (externalMediaDirs != null && externalMediaDirs.isNotEmpty()) {
                    for (i in externalMediaDirs.indices) {
                        val dir = externalMediaDirs[i] ?: continue
                        val symlinkName = "media-$i"
                        Logger.logInfo(logTag, "Setting up storage symlinks at ~/storage/$symlinkName for \"${dir.absolutePath}\".")
                        Os.symlink(dir.absolutePath, File(storageDir, symlinkName).absolutePath)
                    }
                }

                Logger.logInfo(logTag, "Storage symlinks created successfully.")
            } catch (e: Exception) {
                Logger.logErrorAndShowToast(context, logTag, e.message ?: "")
                Logger.logStackTraceWithMessage(logTag, "Setup Storage Error: Error setting up link", e)
                TermuxCrashUtils.sendCrashReportNotification(
                    context, logTag, title, null,
                    "## $title\n\n${Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e))}",
                    true, false, TermuxUtils.AppInfoMode.TERMUX_PACKAGE, true
                )
            }
        }.start()
    }

    private fun ensureDirectoryExists(directory: File): Error? {
        return FileUtils.createDirectoryFile(directory.absolutePath)
    }
    
    /**
     * Check if a file needs path fixing based on its location and extension.
     * Process text files (scripts, configs) in bin/, etc/, share/, and libexec/.
     * 
     * IMPORTANT: The upstream bootstrap contains many shell scripts with hardcoded
     * paths like #!/data/data/com.termux/files/usr/bin/sh - these MUST be fixed
     * or the scripts will fail to execute on com.termux.kotlin package.
     */
    private fun isTextFileNeedingPathFix(entryName: String): Boolean {
        // Scripts in bin/ - login, chsh, su, termux-*, pkg, apt-key, etc.
        // These have shebangs pointing to /data/data/com.termux/files/usr/bin/sh or bash
        if (entryName.startsWith("bin/")) {
            // Known shell scripts that need fixing (not ELF binaries)
            val knownScripts = setOf(
                "login", "chsh", "su", "am", "pm", "cmd", "dalvikvm", "logcat", "getprop", "settings",
                "ping", "ping6", "df", "top", "red",
                "pkg", "apt-key", "dpkg-realpath", "savelog",
                "curl-config", "gpg-error-config", "gpgrt-config", "libassuan-config", 
                "libgcrypt-config", "npth-config", "pcre2-config",
                "egrep", "fgrep", "gunzip", "gzexe", "zcat", "zcmp", "zdiff", "zegrep", 
                "zfgrep", "zforce", "zgrep", "zmore", "znew", "uncompress", "zipgrep",
                "bzdiff", "bzgrep", "bzmore",
                "xzdiff", "xzgrep", "xzless", "xzmore",
                "wcurl"
            )
            val basename = entryName.removePrefix("bin/")
            if (knownScripts.contains(basename)) return true
            // All termux-* scripts
            if (basename.startsWith("termux-")) return true
            // .sh and .bash script extensions
            if (entryName.endsWith(".sh") || entryName.endsWith(".bash")) return true
        }
        
        // Shell scripts and config files in etc/
        if (entryName.startsWith("etc/")) {
            // Match shell scripts, config files, and the second-stage bootstrap script
            return entryName.endsWith(".sh") || 
                   entryName.endsWith(".bashrc") ||
                   entryName.endsWith(".profile") ||
                   entryName.endsWith("profile") ||
                   entryName.endsWith("nanorc") ||
                   entryName.endsWith("inputrc") ||
                   entryName.endsWith("termux-login.sh") ||
                   entryName.contains("termux-bootstrap")
        }
        
        // Shell scripts in share/ directories
        if (entryName.startsWith("share/") && entryName.endsWith(".sh")) {
            return true
        }
        
        // Shell scripts in libexec/
        if (entryName.startsWith("libexec/") && entryName.endsWith(".sh")) {
            return true
        }
        
        return false
    }
    
    /**
     * Replace hardcoded upstream package paths with our package paths in a text file.
     */
    private fun fixPathsInTextFile(file: File, upstreamPrefix: String, ourPrefix: String) {
        try {
            val content = file.readText()
            if (content.contains(upstreamPrefix)) {
                val fixedContent = content.replace(upstreamPrefix, ourPrefix)
                file.writeText(fixedContent)
                Logger.logDebug(LOG_TAG, "Fixed paths in ${file.name}")
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to fix paths in ${file.absolutePath}: ${e.message}")
        }
    }
    
    /**
     * Fix the login script to avoid bash's hardcoded profile path issue.
     * 
     * Problem: Upstream bash binary has /data/data/com.termux/files/usr/etc/profile hardcoded.
     * When original Termux app is installed, bash finds that profile (permission denied).
     * When it's not installed, bash can't find the file and continues normally.
     * 
     * Solution: Modify login script to use bash --noprofile and manually source our profile.
     * This ensures we always use OUR profile regardless of what other apps are installed.
     */
    private fun fixLoginScript(loginFile: File, ourFilesPrefix: String) {
        try {
            if (!loginFile.exists()) {
                Logger.logWarn(LOG_TAG, "Login script not found at ${loginFile.absolutePath}")
                return
            }
            
            // Create a login script that explicitly sources our profile
            // This avoids relying on bash's compiled-in profile path
            val fixedLoginScript = """#!/${ourFilesPrefix}/usr/bin/sh
# Termux login script - modified to avoid hardcoded bash profile path issues
# Original bash binary has /data/data/com.termux paths compiled in.
# We use --noprofile to skip that and manually source our profile.

export PREFIX="${ourFilesPrefix}/usr"
export HOME="${ourFilesPrefix}/home"

# Source our profile if it exists
if [ -f "${'$'}PREFIX/etc/profile" ]; then
    . "${'$'}PREFIX/etc/profile"
fi

# Execute bash without its built-in profile sourcing
# The '-' prefix makes it a login shell (for PS1, job control, etc.)
exec -a "-bash" "${ourFilesPrefix}/usr/bin/bash" --noprofile --norc
"""
            loginFile.writeText(fixedLoginScript)
            Logger.logInfo(LOG_TAG, "Fixed login script to avoid hardcoded profile path issues")
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to fix login script: ${e.message}")
        }
    }
    
    /**
     * Create dpkg wrapper script to handle hardcoded configuration path issues.
     * 
     * Problem: dpkg binary has /data/data/com.termux/files/usr/etc/dpkg/dpkg.cfg.d hardcoded.
     * When original Termux is installed, dpkg finds that directory but can't access it.
     * There is no environment variable to override the configuration directory path.
     * 
     * Solution: Rename original dpkg binary and create a wrapper script that:
     * 1. Sets DPKG_ADMINDIR to our path
     * 2. Uses --force-not-root and other flags if needed
     * 3. Calls the original binary
     */
    private fun createDpkgWrapper(binDir: File, ourFilesPrefix: String) {
        try {
            val dpkgFile = File(binDir, "dpkg")
            val dpkgRealFile = File(binDir, "dpkg.real")
            
            if (!dpkgFile.exists()) {
                Logger.logWarn(LOG_TAG, "dpkg not found at ${dpkgFile.absolutePath}")
                return
            }
            
            // Check if already wrapped (dpkg is a text file, not ELF)
            val firstBytes = ByteArray(4)
            dpkgFile.inputStream().use { it.read(firstBytes) }
            val isElf = firstBytes[0] == 0x7F.toByte() && 
                        firstBytes[1] == 'E'.code.toByte() && 
                        firstBytes[2] == 'L'.code.toByte() && 
                        firstBytes[3] == 'F'.code.toByte()
            
            if (!isElf) {
                Logger.logDebug(LOG_TAG, "dpkg is not an ELF binary, skipping wrapper creation")
                return
            }
            
            // Rename original dpkg to dpkg.real
            if (!dpkgFile.renameTo(dpkgRealFile)) {
                Logger.logError(LOG_TAG, "Failed to rename dpkg to dpkg.real")
                return
            }
            
            // Create wrapper script
            // Note: We can't override the config directory, but we can set admin/data dirs
            // The wrapper also provides a place to add workarounds in the future
            val wrapperScript = """#!/${ourFilesPrefix}/usr/bin/sh
# dpkg wrapper script for com.termux.kotlin
# Handles hardcoded path issues in the dpkg binary

# Set dpkg directories to our package paths
export DPKG_ADMINDIR="${ourFilesPrefix}/usr/var/lib/dpkg"
export DPKG_DATADIR="${ourFilesPrefix}/usr/share/dpkg"

# Execute the real dpkg binary
exec "${ourFilesPrefix}/usr/bin/dpkg.real" "${'$'}@"
"""
            dpkgFile.writeText(wrapperScript)
            Os.chmod(dpkgFile.absolutePath, 448) // 0700
            
            Logger.logInfo(LOG_TAG, "Created dpkg wrapper script")
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to create dpkg wrapper: ${e.message}")
        }
    }

    @JvmStatic
    fun loadZipBytes(): ByteArray {
        // Only load the shared library when necessary to save memory usage.
        try {
            System.loadLibrary("termux-bootstrap")
        } catch (e: UnsatisfiedLinkError) {
            Logger.logError(LOG_TAG, "Failed to load termux-bootstrap native library: ${e.message}")
            throw RuntimeException("Failed to load bootstrap library. Please reinstall the app.", e)
        } catch (e: SecurityException) {
            Logger.logError(LOG_TAG, "Security exception loading termux-bootstrap library: ${e.message}")
            throw RuntimeException("Security error loading bootstrap library.", e)
        }
        return getZip()
    }

    @JvmStatic
    external fun getZip(): ByteArray
}
