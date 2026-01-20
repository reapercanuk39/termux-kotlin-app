package com.termux.app

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Build
import android.os.Environment
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
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
                    try {
                        val targetFile = File(symlink.second)
                        // Delete existing file/symlink if present (zip might contain placeholder files)
                        if (targetFile.exists()) {
                            targetFile.delete()
                            Logger.logDebug(LOG_TAG, "Deleted existing file before creating symlink: ${symlink.second}")
                        }
                        Os.symlink(symlink.first, symlink.second)
                    } catch (e: Exception) {
                        Logger.logError(LOG_TAG, "Failed to create symlink ${symlink.second} -> ${symlink.first}: ${e.message}")
                        throw e
                    }
                }
                
                // Fix login script to avoid bash's hardcoded profile path issue
                // When original Termux is installed, bash finds the old /etc/profile (permission denied)
                // We fix this by using --noprofile and manually sourcing our profile
                fixLoginScript(File(TERMUX_STAGING_PREFIX_DIR_PATH, "bin/login"), ourFilesPrefix)
                
                // Create dpkg wrapper to handle hardcoded config path issues
                createDpkgWrapper(File(TERMUX_STAGING_PREFIX_DIR_PATH, "bin"), ourFilesPrefix)
                
                // Create update-alternatives wrapper to handle hardcoded paths
                // The binary has paths like /data/data/com.termux/files/usr/var/log compiled in
                createUpdateAlternativesWrapper(File(TERMUX_STAGING_PREFIX_DIR_PATH, "bin"), ourFilesPrefix)
                
                // Create apt wrappers to handle hardcoded paths in libapt-pkg.so
                // The library has /data/data/com.termux/files/usr/etc/apt etc. compiled in
                // Cache dir is at /data/data/com.termux.kotlin/cache (parallel to /files)
                val ourCacheDir = ourFilesPrefix.replace("/files/", "/cache/").replace("/files", "/cache")
                createAptWrappers(File(TERMUX_STAGING_PREFIX_DIR_PATH, "bin"), ourFilesPrefix, ourCacheDir)
                
                // Setup LD_PRELOAD compatibility layer
                setupCompatLayer(File(TERMUX_STAGING_PREFIX_DIR_PATH, "bin"), ourFilesPrefix, activity.applicationContext)
                
                // Setup the agent framework CLI symlink
                setupAgentFramework(File(TERMUX_STAGING_PREFIX_DIR_PATH, "bin"), ourFilesPrefix, activity.applicationContext)

                Logger.logInfo(LOG_TAG, "Moving termux prefix staging to prefix directory.")

                if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                    throw RuntimeException("Moving termux prefix staging to prefix directory failed")
                }

                Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.")

                // Recreate env file since termux prefix was wiped earlier
                TermuxShellEnvironment.writeEnvironmentToFile(activity)
                
                // Start the autonomous agent service now that bootstrap is complete
                try {
                    Logger.logInfo(LOG_TAG, "Starting autonomous agent service after bootstrap")
                    AgentService.startAgentService(activity.applicationContext)
                } catch (e: Exception) {
                    Logger.logWarn(LOG_TAG, "Could not start agent service: ${e.message}")
                }

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
            // Known shell/perl scripts that need fixing (not ELF binaries)
            // NOTE: update-alternatives is an ELF BINARY, not a script!
            // Do NOT add it here - modifying binaries corrupts them.
            val knownScripts = setOf(
                "login", "chsh", "su", "am", "pm", "cmd", "dalvikvm", "logcat", "getprop", "settings",
                "ping", "ping6", "df", "top", "red",
                "pkg", "apt-key", "dpkg-realpath", "savelog",
                "curl-config", "gpg-error-config", "gpgrt-config", "libassuan-config", 
                "libgcrypt-config", "npth-config", "pcre2-config", "ncursesw6-config",
                "egrep", "fgrep", "gunzip", "gzexe", "zcat", "zcmp", "zdiff", "zegrep", 
                "zfgrep", "zforce", "zgrep", "zmore", "znew", "uncompress", "zipgrep",
                "bzdiff", "bzgrep", "bzmore",
                "xzdiff", "xzgrep", "xzless", "xzmore",
                "wcurl", "zstdgrep", "zstdless",
                // dpkg Perl scripts (NOT ELF: dpkg-divert, dpkg-trigger, dpkg-query, dpkg-split, dpkg-deb)
                "dpkg-buildapi", "dpkg-buildtree", "dpkg-fsys-usrunmess"
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
        
        // Shell scripts and examples in share/ directories
        if (entryName.startsWith("share/")) {
            if (entryName.endsWith(".sh") || entryName.endsWith(".bash") || entryName.endsWith(".tcsh")) return true
            // termux.properties example
            if (entryName.contains("termux.properties")) return true
            // awk scripts
            if (entryName.endsWith(".awk")) return true
        }
        
        // dpkg-related scripts in share/dpkg/ 
        if (entryName.startsWith("share/dpkg/")) {
            return true
        }
        
        // All scripts in libexec/ (many have hardcoded paths)
        // This includes coreutils/cat, dpkg/*, installed-tests/*, etc.
        if (entryName.startsWith("libexec/")) {
            // Match any script-like file (no extension or script extension)
            val basename = entryName.substringAfterLast("/")
            // Scripts without extension (like 'cat', 'dpkg-db-keeper', etc.)
            if (!basename.contains(".") || 
                entryName.endsWith(".sh") || 
                entryName.endsWith(".bash")) return true
        }
        
        // dpkg maintainer scripts and metadata in var/lib/dpkg/info/
        if (entryName.startsWith("var/lib/dpkg/info/")) {
            // Shell scripts
            val scriptExtensions = listOf(".postinst", ".preinst", ".postrm", ".prerm", ".config", ".triggers")
            if (scriptExtensions.any { entryName.endsWith(it) }) return true
            // Metadata files with paths (.list, .conffiles)
            val metaExtensions = listOf(".list", ".conffiles")
            if (metaExtensions.any { entryName.endsWith(it) }) return true
        }
        
        // pkg-config files (.pc) contain hardcoded paths for development
        if (entryName.startsWith("lib/pkgconfig/") && entryName.endsWith(".pc")) {
            return true
        }
        
        // CMake config files contain hardcoded paths
        if (entryName.startsWith("lib/cmake/") && entryName.endsWith(".cmake")) {
            return true
        }
        
        // Bash loadable builtins Makefiles
        if (entryName.startsWith("lib/bash/") && 
            (entryName.endsWith("Makefile.inc") || entryName.endsWith("Makefile.sample"))) {
            return true
        }
        
        // Header files with hardcoded paths (for development)
        if (entryName.startsWith("include/") && entryName.endsWith(".h")) {
            // Only fix specific headers known to have hardcoded paths
            val headersWithPaths = listOf(
                "include/bash/pathnames.h",
                "include/bash/config-top.h", 
                "include/readline/rlconf.h",
                "include/termux-exec/"  // All termux-exec headers
            )
            if (headersWithPaths.any { entryName.startsWith(it) || entryName == it }) return true
        }
        
        return false
    }
    
    /**
     * Replace hardcoded upstream package paths with our package paths in a text file.
     * 
     * This handles BOTH path patterns:
     * - /data/data/com.termux/files/usr/... -> /data/data/com.termux.kotlin/files/usr/...
     * - /data/data/com.termux/cache/... -> /data/data/com.termux.kotlin/cache/...
     * - /data/data/com.termux (at end of string or before non-alphanum) -> /data/data/com.termux.kotlin
     * 
     * The upstreamPrefix is typically "/data/data/com.termux/files" but we also need to
     * handle "/data/data/com.termux/cache" and other paths.
     */
    private fun fixPathsInTextFile(file: File, upstreamPrefix: String, ourPrefix: String) {
        try {
            val content = file.readText()
            
            // Also fix the base package path (without /files suffix)
            // e.g. /data/data/com.termux/cache -> /data/data/com.termux.kotlin/cache
            val upstreamBase = upstreamPrefix.removeSuffix("/files")
            val ourBase = ourPrefix.removeSuffix("/files")
            
            var fixedContent = content
            var modified = false
            
            // IMPORTANT: Use regex with negative lookahead to avoid double-replacement
            // This ensures we don't replace "com.termux.kotlin" -> "com.termux.kotlin.kotlin"
            // Only replace "com.termux" when NOT followed by ".kotlin"
            val upstreamPattern = Regex(Regex.escape(upstreamBase) + "(?!\\.kotlin)")
            if (upstreamPattern.containsMatchIn(fixedContent)) {
                fixedContent = upstreamPattern.replace(fixedContent, ourBase)
                modified = true
            }
            
            if (modified) {
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
            // IMPORTANT: Use bash shebang because we use exec -a (a bash extension)
            val fixedLoginScript = """#!/${ourFilesPrefix}/usr/bin/bash
# Termux login script - modified to avoid hardcoded bash profile path issues
# Original bash binary has /data/data/com.termux paths compiled in.
# We use --noprofile to skip that and manually source our profile.
# NOTE: This script MUST use bash (not sh) because exec -a is a bash extension.

export PREFIX="${ourFilesPrefix}/usr"
export HOME="${ourFilesPrefix}/home"

# Source our profile if it exists
if [ -f "${'$'}PREFIX/etc/profile" ]; then
    . "${'$'}PREFIX/etc/profile"
fi

# Execute bash without its built-in profile sourcing
# The '-' prefix (via exec -a) makes it a login shell (for PS1, job control, etc.)
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
     * Solution: 
     * 1. Rename original dpkg binary and create a wrapper script that sets env vars
     * 2. Ensure our config directory exists to satisfy dpkg's check
     * 3. The wrapper also provides a place to add future workarounds
     */
    private fun createDpkgWrapper(binDir: File, ourFilesPrefix: String) {
        try {
            val dpkgFile = File(binDir, "dpkg")
            val dpkgRealFile = File(binDir, "dpkg.real")
            
            if (!dpkgFile.exists()) {
                Logger.logWarn(LOG_TAG, "dpkg not found at ${dpkgFile.absolutePath}")
                return
            }
            
            // Create our dpkg config directory to prevent access to wrong paths
            val dpkgConfigDir = File(binDir.parentFile, "etc/dpkg/dpkg.cfg.d")
            if (!dpkgConfigDir.exists()) {
                dpkgConfigDir.mkdirs()
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
            
            // =============================================================================
            // DPKG WRAPPER v3.0 - Hybrid Compatibility Layer
            // =============================================================================
            // This wrapper works with the LD_PRELOAD shim (libtermux_compat.so) to provide
            // full compatibility with upstream Termux packages.
            //
            // DPKG-WRAPPER handles:
            //   - DEBIAN control scripts (postinst, prerm, etc.) - rewritten at install time
            //   - Directory structure in packages - paths rewritten at install time
            //
            // LD_PRELOAD SHIM handles:
            //   - Runtime path lookups by compiled binaries
            //   - Text files that reference old paths (intercepted at open/read time)
            //
            // This hybrid approach minimizes install-time rewriting while ensuring
            // full runtime compatibility.
            // =============================================================================
            val wrapperScript = """#!/${ourFilesPrefix}/usr/bin/bash
# dpkg wrapper v3.0 - Hybrid Compatibility Layer
# Only rewrites DEBIAN scripts; runtime handled by LD_PRELOAD shim

PREFIX="${ourFilesPrefix}/usr"
export TMPDIR="${ourFilesPrefix}/usr/tmp"
export HOME="${ourFilesPrefix}"
export DPKG_ADMINDIR="${ourFilesPrefix}/usr/var/lib/dpkg"
export DPKG_DATADIR="${ourFilesPrefix}/usr/share/dpkg"

mkdir -p "${'$'}TMPDIR" 2>/dev/null || true

LOG_FILE="${'$'}TMPDIR/dpkg_wrapper.log"
echo "[dpkg-wrapper-v3] === ${'$'}(date) ===" >> "${'$'}LOG_FILE" 2>/dev/null || true
echo "[dpkg-wrapper-v3] args: ${'$'}@" >> "${'$'}LOG_FILE"

OLD_PREFIX="/data/data/com.termux/"
NEW_PREFIX="/data/data/com.termux.kotlin/"

# =============================================================================
# REWRITE FUNCTION - Only handles DEBIAN scripts and directory structure
# =============================================================================
rewrite_deb() {
    local deb_file="${'$'}1"
    local deb_name="${'$'}(basename "${'$'}deb_file")"
    local rewritten_deb="${'$'}TMPDIR/rewritten_${'$'}deb_name"
    local work_dir="${'$'}TMPDIR/deb_work_${'$'}${'$'}_${'$'}RANDOM"
    
    # FAST CHECK: Already has correct paths?
    local path_listing
    path_listing=${'$'}("${'$'}PREFIX/bin/dpkg-deb" --contents "${'$'}deb_file" 2>/dev/null | head -50)
    
    if echo "${'$'}path_listing" | grep -q "com\.termux\.kotlin/"; then
        echo "[dpkg-wrapper-v3] SKIP (correct paths): ${'$'}deb_name" >> "${'$'}LOG_FILE"
        echo "${'$'}deb_file"
        return 0
    fi
    
    # FAST CHECK: No old paths at all?
    if ! echo "${'$'}path_listing" | grep -q "com\.termux/"; then
        local ctrl_temp="${'$'}TMPDIR/ctrl_${'$'}${'$'}"
        mkdir -p "${'$'}ctrl_temp" 2>/dev/null
        "${'$'}PREFIX/bin/dpkg-deb" --control "${'$'}deb_file" "${'$'}ctrl_temp" 2>/dev/null
        if ! grep -rq "com\.termux/" "${'$'}ctrl_temp" 2>/dev/null; then
            rm -rf "${'$'}ctrl_temp"
            echo "[dpkg-wrapper-v3] SKIP (no old paths): ${'$'}deb_name" >> "${'$'}LOG_FILE"
            echo "${'$'}deb_file"
            return 0
        fi
        rm -rf "${'$'}ctrl_temp"
    fi
    
    # NEEDS REWRITING
    echo "  Patching: ${'$'}deb_name" >&2
    echo "[dpkg-wrapper-v3] Rewriting: ${'$'}deb_name" >> "${'$'}LOG_FILE"
    
    mkdir -p "${'$'}work_dir/pkg_root/DEBIAN" || { echo "${'$'}deb_file"; return 1; }
    cd "${'$'}work_dir" || { echo "${'$'}deb_file"; return 1; }
    
    # Extract control and data
    "${'$'}PREFIX/bin/dpkg-deb" --control "${'$'}deb_file" pkg_root/DEBIAN 2>>"${'$'}LOG_FILE" || {
        cd /; rm -rf "${'$'}work_dir"; echo "${'$'}deb_file"; return 1
    }
    "${'$'}PREFIX/bin/dpkg-deb" --extract "${'$'}deb_file" pkg_root 2>>"${'$'}LOG_FILE" || {
        cd /; rm -rf "${'$'}work_dir"; echo "${'$'}deb_file"; return 1
    }
    
    # FIX DIRECTORY STRUCTURE
    if [ -d "pkg_root/data/data/com.termux" ]; then
        mkdir -p "pkg_root/data/data/com.termux.kotlin"
        for item in pkg_root/data/data/com.termux/*; do
            [ -e "${'$'}item" ] && mv "${'$'}item" "pkg_root/data/data/com.termux.kotlin/" 2>/dev/null
        done
        rmdir "pkg_root/data/data/com.termux" 2>/dev/null || rm -rf "pkg_root/data/data/com.termux"
    fi
    
    # FIX DEBIAN CONTROL SCRIPTS (postinst, prerm, etc.)
    for script in pkg_root/DEBIAN/postinst pkg_root/DEBIAN/preinst pkg_root/DEBIAN/prerm pkg_root/DEBIAN/postrm pkg_root/DEBIAN/config; do
        if [ -f "${'$'}script" ]; then
            sed -i "s|${'$'}OLD_PREFIX|${'$'}NEW_PREFIX|g" "${'$'}script" 2>/dev/null
        fi
    done
    
    # FIX SHEBANGS in DEBIAN scripts
    for script in pkg_root/DEBIAN/*; do
        if [ -f "${'$'}script" ]; then
            head_bytes=${'$'}(head -c 2 "${'$'}script" 2>/dev/null || echo "")
            if [ "${'$'}head_bytes" = "#!" ]; then
                sed -i "1s|${'$'}OLD_PREFIX|${'$'}NEW_PREFIX|g" "${'$'}script" 2>/dev/null
            fi
        fi
    done
    
    # FIX SHEBANGS in data files (critical for executables)
    find pkg_root/data -type f 2>/dev/null | while read -r file; do
        head_bytes=${'$'}(head -c 2 "${'$'}file" 2>/dev/null || echo "")
        if [ "${'$'}head_bytes" = "#!" ]; then
            first_line=${'$'}(head -1 "${'$'}file" 2>/dev/null)
            if echo "${'$'}first_line" | grep -q "${'$'}OLD_PREFIX"; then
                sed -i "1s|${'$'}OLD_PREFIX|${'$'}NEW_PREFIX|" "${'$'}file" 2>/dev/null
            fi
        fi
    done
    
    # REBUILD with fast compression
    "${'$'}PREFIX/bin/dpkg-deb" -Zgzip -z1 --build pkg_root "${'$'}rewritten_deb" 2>>"${'$'}LOG_FILE" || {
        cd /; rm -rf "${'$'}work_dir"; echo "${'$'}deb_file"; return 1
    }
    
    cd /
    rm -rf "${'$'}work_dir"
    echo "${'$'}rewritten_deb"
}

# =============================================================================
# MAIN LOGIC
# =============================================================================
is_install=false
for arg in "${'$'}@"; do
    case "${'$'}arg" in
        -i|--install|-R|--recursive|--unpack) is_install=true ;;
    esac
done

if ${'$'}is_install; then
    new_args=()
    for arg in "${'$'}@"; do
        if [ -f "${'$'}arg" ] && [[ "${'$'}arg" == *.deb ]]; then
            rewritten=${'$'}(rewrite_deb "${'$'}arg") || rewritten="${'$'}arg"
            new_args+=("${'$'}rewritten")
        elif [ -d "${'$'}arg" ]; then
            for deb in "${'$'}arg"/*.deb; do
                if [ -f "${'$'}deb" ]; then
                    rewritten=${'$'}(rewrite_deb "${'$'}deb") || rewritten="${'$'}deb"
                    if [ "${'$'}rewritten" != "${'$'}deb" ] && [ -f "${'$'}rewritten" ]; then
                        mv "${'$'}rewritten" "${'$'}deb" 2>/dev/null
                    fi
                fi
            done
            new_args+=("${'$'}arg")
        else
            new_args+=("${'$'}arg")
        fi
    done
    
    # Run dpkg
    "${ourFilesPrefix}/usr/bin/dpkg.real" "${'$'}{new_args[@]}"
    dpkg_exit=${'$'}?
    
    # Auto-compile compat shim after clang is installed
    if [ -f "${'$'}PREFIX/bin/clang" ] && [ ! -f "${'$'}PREFIX/lib/libtermux_compat.so" ]; then
        if [ -f "${'$'}PREFIX/lib/libtermux_compat.c" ]; then
            echo "[termux-compat] Clang detected, auto-compiling compatibility shim..."
            "${'$'}PREFIX/bin/clang" -shared -fPIC -O2 -o "${'$'}PREFIX/lib/libtermux_compat.so" "${'$'}PREFIX/lib/libtermux_compat.c" -ldl 2>/dev/null && \
                echo "[termux-compat] Success! Restart your shell to enable LD_PRELOAD."
        fi
    fi
    
    exit ${'$'}dpkg_exit
else
    exec "${ourFilesPrefix}/usr/bin/dpkg.real" "${'$'}@"
fi
"""
            dpkgFile.writeText(wrapperScript)
            Os.chmod(dpkgFile.absolutePath, 448) // 0700
            
            Logger.logInfo(LOG_TAG, "Created dpkg wrapper v3.0")
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to create dpkg wrapper: ${e.message}")
        }
    }
    
    /**
     * Create a wrapper script for update-alternatives to handle hardcoded paths.
     * 
     * The update-alternatives binary has paths like /data/data/com.termux/files/usr/var/log,
     * /data/data/com.termux/files/usr/etc/alternatives, etc. compiled in. These can be
     * overridden with --altdir, --admindir, and --log flags.
     */
    private fun createUpdateAlternativesWrapper(binDir: File, ourFilesPrefix: String) {
        try {
            val updateAltFile = File(binDir, "update-alternatives")
            val updateAltRealFile = File(binDir, "update-alternatives.real")
            
            if (!updateAltFile.exists()) {
                Logger.logWarn(LOG_TAG, "update-alternatives not found, skipping wrapper creation")
                return
            }
            
            // Check if it's an ELF binary (not a script)
            val firstBytes = ByteArray(4)
            updateAltFile.inputStream().use { it.read(firstBytes) }
            val isElf = firstBytes[0] == 0x7F.toByte() && 
                        firstBytes[1] == 'E'.code.toByte() && 
                        firstBytes[2] == 'L'.code.toByte() && 
                        firstBytes[3] == 'F'.code.toByte()
            
            if (!isElf) {
                Logger.logDebug(LOG_TAG, "update-alternatives is not an ELF binary, skipping wrapper creation")
                return
            }
            
            // Rename original to .real
            if (!updateAltFile.renameTo(updateAltRealFile)) {
                Logger.logError(LOG_TAG, "Failed to rename update-alternatives to update-alternatives.real")
                return
            }
            
            // Create wrapper script that passes correct paths
            // --altdir: where alternative links are stored (default: /etc/alternatives)
            // --admindir: where administrative data is stored (default: /var/lib/dpkg/alternatives)
            // --log: log file location (default: /var/log/alternatives.log)
            val wrapperScript = """#!/${ourFilesPrefix}/usr/bin/bash
# update-alternatives wrapper for com.termux.kotlin
# Handles hardcoded path issues in the update-alternatives binary
#
# The binary has /data/data/com.termux/files/usr/... paths compiled in.
# We override these with command-line flags.

PREFIX="${ourFilesPrefix}/usr"

# Ensure required directories exist
mkdir -p "${'$'}PREFIX/etc/alternatives" 2>/dev/null
mkdir -p "${'$'}PREFIX/var/lib/dpkg/alternatives" 2>/dev/null
mkdir -p "${'$'}PREFIX/var/log" 2>/dev/null

# Call real binary with overridden paths
exec "${ourFilesPrefix}/usr/bin/update-alternatives.real" \
    --altdir "${'$'}PREFIX/etc/alternatives" \
    --admindir "${'$'}PREFIX/var/lib/dpkg/alternatives" \
    --log "${'$'}PREFIX/var/log/alternatives.log" \
    "${'$'}@"
"""
            updateAltFile.writeText(wrapperScript)
            Os.chmod(updateAltFile.absolutePath, 493) // 0755 - executable by all
            
            Logger.logInfo(LOG_TAG, "Created update-alternatives wrapper script")
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to create update-alternatives wrapper: ${e.message}")
        }
    }
    
    /**
     * Create wrapper scripts for apt, apt-get, apt-cache, apt-config, apt-mark.
     * 
     * The libapt-pkg.so library has hardcoded paths like:
     * - /data/data/com.termux/files/usr/etc/apt
     * - /data/data/com.termux/cache/apt  
     * - /data/data/com.termux/files/usr/var/lib/apt
     * - /data/data/com.termux/files/usr/var/log/apt
     * 
     * These can be overridden with -o Dir::* options.
     */
    private fun createAptWrappers(binDir: File, ourFilesPrefix: String, cacheDir: String) {
        val aptCommands = listOf("apt", "apt-get", "apt-cache", "apt-config", "apt-mark")
        
        for (cmd in aptCommands) {
            try {
                val aptFile = File(binDir, cmd)
                val aptRealFile = File(binDir, "$cmd.real")
                
                if (!aptFile.exists()) {
                    Logger.logDebug(LOG_TAG, "$cmd not found, skipping wrapper creation")
                    continue
                }
                
                // Check if it's an ELF binary
                val firstBytes = ByteArray(4)
                aptFile.inputStream().use { it.read(firstBytes) }
                val isElf = firstBytes[0] == 0x7F.toByte() && 
                            firstBytes[1] == 'E'.code.toByte() && 
                            firstBytes[2] == 'L'.code.toByte() && 
                            firstBytes[3] == 'F'.code.toByte()
                
                if (!isElf) {
                    Logger.logDebug(LOG_TAG, "$cmd is not an ELF binary, skipping wrapper creation")
                    continue
                }
                
                // Rename original to .real
                if (!aptFile.renameTo(aptRealFile)) {
                    Logger.logError(LOG_TAG, "Failed to rename $cmd to $cmd.real")
                    continue
                }
                
                // Create wrapper script that overrides hardcoded paths
                val wrapperScript = """#!/system/bin/sh
# $cmd wrapper for com.termux.kotlin
# Handles hardcoded path issues in libapt-pkg.so
#
# The library has /data/data/com.termux/files/usr/... paths compiled in.
# We override these with -o Dir::* options.

PREFIX="${ourFilesPrefix}/usr"
CACHE="${cacheDir}"

# Ensure required directories exist
mkdir -p "${'$'}PREFIX/etc/apt/apt.conf.d" 2>/dev/null
mkdir -p "${'$'}PREFIX/etc/apt/preferences.d" 2>/dev/null
mkdir -p "${'$'}PREFIX/etc/apt/trusted.gpg.d" 2>/dev/null
mkdir -p "${'$'}PREFIX/var/lib/apt/lists/partial" 2>/dev/null
mkdir -p "${'$'}PREFIX/var/lib/apt/periodic" 2>/dev/null
mkdir -p "${'$'}PREFIX/var/log/apt" 2>/dev/null
mkdir -p "${'$'}CACHE/apt/archives/partial" 2>/dev/null

# Call real binary with overridden paths
# NOTE: Acquire::https::CaInfo points to the SSL CA bundle for HTTPS verification
exec "${ourFilesPrefix}/usr/bin/$cmd.real" \
    -o Dir::Etc="${'$'}PREFIX/etc/apt" \
    -o Dir::Etc::sourcelist="${'$'}PREFIX/etc/apt/sources.list" \
    -o Dir::Etc::sourceparts="${'$'}PREFIX/etc/apt/sources.list.d" \
    -o Dir::Etc::main="${'$'}PREFIX/etc/apt/apt.conf" \
    -o Dir::Etc::parts="${'$'}PREFIX/etc/apt/apt.conf.d" \
    -o Dir::Etc::preferences="${'$'}PREFIX/etc/apt/preferences" \
    -o Dir::Etc::preferencesparts="${'$'}PREFIX/etc/apt/preferences.d" \
    -o Dir::Etc::trusted="${'$'}PREFIX/etc/apt/trusted.gpg" \
    -o Dir::Etc::trustedparts="${'$'}PREFIX/etc/apt/trusted.gpg.d" \
    -o Dir::State="${'$'}PREFIX/var/lib/apt" \
    -o Dir::State::lists="${'$'}PREFIX/var/lib/apt/lists" \
    -o Dir::State::status="${'$'}PREFIX/var/lib/dpkg/status" \
    -o Dir::Cache="${'$'}CACHE/apt" \
    -o Dir::Cache::archives="${'$'}CACHE/apt/archives" \
    -o Dir::Log="${'$'}PREFIX/var/log/apt" \
    -o Dir::Bin::dpkg="${'$'}PREFIX/bin/dpkg" \
    -o Dir::Bin::Methods="${'$'}PREFIX/lib/apt/methods" \
    -o Acquire::https::CaInfo="${'$'}PREFIX/etc/tls/cert.pem" \
    -o Acquire::http::CaInfo="${'$'}PREFIX/etc/tls/cert.pem" \
    "${'$'}@"
"""
                aptFile.writeText(wrapperScript)
                Os.chmod(aptFile.absolutePath, 493) // 0755 - executable by all
                
                Logger.logInfo(LOG_TAG, "Created $cmd wrapper script")
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to create $cmd wrapper: ${e.message}")
            }
        }
    }

    /**
     * Setup the LD_PRELOAD compatibility layer.
     * 
     * This installs libtermux_compat.so and configures the shell profile
     * to automatically load it. The shim intercepts filesystem syscalls
     * and redirects paths from com.termux to com.termux.kotlin.
     * 
     * This works in combination with the dpkg-wrapper to provide full
     * compatibility with upstream Termux packages.
     */
    private fun setupCompatLayer(binDir: File, ourFilesPrefix: String, context: Context) {
        try {
            val usrDir = binDir.parentFile ?: return
            val libDir = File(usrDir, "lib")
            val etcDir = File(usrDir, "etc")
            
            // Create lib directory if needed
            if (!libDir.exists()) {
                libDir.mkdirs()
            }
            
            // Extract libtermux_compat.c from assets (source code)
            // We'll also create a compiled placeholder that can be built later
            // For now, create a shell script that will compile it on first use
            val compatSrcFile = File(libDir, "libtermux_compat.c")
            val compatLibFile = File(libDir, "libtermux_compat.so")
            val compatBuildScript = File(binDir, "termux-compat-build")
            
            // Copy C source from assets
            try {
                val assetManager = context.assets
                assetManager.open("libtermux_compat.c").use { input ->
                    compatSrcFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Logger.logInfo(LOG_TAG, "Installed libtermux_compat.c")
            } catch (e: Exception) {
                Logger.logWarn(LOG_TAG, "Could not extract libtermux_compat.c: ${e.message}")
            }
            
            // Create build script that compiles the shim when clang is available
            val buildScriptContent = """#!/${ourFilesPrefix}/usr/bin/bash
# Build script for libtermux_compat.so
# Run this after: pkg install clang

PREFIX="${ourFilesPrefix}/usr"
SRC="${'$'}PREFIX/lib/libtermux_compat.c"
OUT="${'$'}PREFIX/lib/libtermux_compat.so"

if [ ! -f "${'$'}SRC" ]; then
    echo "Error: Source file not found: ${'$'}SRC"
    exit 1
fi

if ! command -v clang >/dev/null 2>&1; then
    echo "Error: clang not found. Run: pkg install clang"
    exit 1
fi

echo "Building libtermux_compat.so..."
clang -shared -fPIC -O2 -o "${'$'}OUT" "${'$'}SRC" -ldl

if [ -f "${'$'}OUT" ]; then
    echo "Success: ${'$'}OUT created"
    echo ""
    echo "To enable, add to your shell profile:"
    echo "  export LD_PRELOAD=${'$'}PREFIX/lib/libtermux_compat.so"
else
    echo "Build failed"
    exit 1
fi
"""
            compatBuildScript.writeText(buildScriptContent)
            Os.chmod(compatBuildScript.absolutePath, 493) // 0755
            
            // Update profile to enable LD_PRELOAD when shim exists
            // Also auto-compile if clang is available but shim isn't built yet
            val profileFile = File(etcDir, "profile")
            if (profileFile.exists()) {
                val profileContent = profileFile.readText()
                if (!profileContent.contains("libtermux_compat")) {
                    val appendContent = """

# Termux-Kotlin compatibility layer
# Redirects /data/data/com.termux/ paths to /data/data/com.termux.kotlin/
_termux_compat_init() {
    local PREFIX="${ourFilesPrefix}/usr"
    local COMPAT_SO="${'$'}PREFIX/lib/libtermux_compat.so"
    local COMPAT_SRC="${'$'}PREFIX/lib/libtermux_compat.c"
    
    # If shim already exists, load it
    if [ -f "${'$'}COMPAT_SO" ]; then
        export LD_PRELOAD="${'$'}COMPAT_SO"
        return
    fi
    
    # Auto-compile if clang is available and source exists
    if [ -f "${'$'}COMPAT_SRC" ] && command -v clang >/dev/null 2>&1; then
        echo "[termux-compat] Auto-compiling compatibility shim..."
        if clang -shared -fPIC -O2 -o "${'$'}COMPAT_SO" "${'$'}COMPAT_SRC" -ldl 2>/dev/null; then
            echo "[termux-compat] Success! LD_PRELOAD shim enabled."
            export LD_PRELOAD="${'$'}COMPAT_SO"
        fi
    fi
}
_termux_compat_init
unset -f _termux_compat_init
"""
                    profileFile.appendText(appendContent)
                    Logger.logInfo(LOG_TAG, "Updated profile with auto-compile LD_PRELOAD")
                }
            }
            
            // Create config file documenting the compat layer
            val compatConfigDir = File(etcDir, "termux-compat")
            compatConfigDir.mkdirs()
            val configFile = File(compatConfigDir, "config.yml")
            configFile.writeText("""# Termux-Kotlin Compatibility Layer Configuration
# 
# This system provides compatibility with upstream Termux packages
# that have hardcoded paths to /data/data/com.termux/

compat_layer:
  version: "3.0"
  
  # Paths
  real_prefix: "${ourFilesPrefix}/usr"
  upstream_prefix: "/data/data/com.termux/files/usr"
  
  # Components
  dpkg_wrapper: "${ourFilesPrefix}/usr/bin/dpkg"
  compat_shim: "${ourFilesPrefix}/usr/lib/libtermux_compat.so"
  compat_source: "${ourFilesPrefix}/usr/lib/libtermux_compat.c"
  build_script: "${ourFilesPrefix}/usr/bin/termux-compat-build"
  
  # Status
  # shim_compiled: false  # Set to true after running termux-compat-build
  
# How it works:
# 1. dpkg-wrapper (install-time):
#    - Rewrites directory structure in packages
#    - Fixes DEBIAN control scripts (postinst, etc.)
#    - Fixes shebangs in executables
#
# 2. LD_PRELOAD shim (runtime):
#    - Intercepts open(), stat(), access(), etc.
#    - Redirects paths from com.termux to com.termux.kotlin
#    - Handles binaries with hardcoded paths
#
# To enable full compatibility:
# 1. pkg install clang
# 2. termux-compat-build
# 3. Restart your shell
""")
            Logger.logInfo(LOG_TAG, "Created compatibility layer config")
            
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to setup compat layer: ${e.message}")
        }
    }

    /**
     * Setup the agent framework for Termux-Kotlin.
     * 
     * The agent framework provides a Python-based agent system with:
     * - A supervisor daemon (agentd) that manages agent lifecycle
     * - A capability/permission system for secure agent execution
     * - A plugin/skill system for extensibility
     * - Memory and sandboxing for each agent
     * 
     * This function:
     * 1. Creates the agents directory structure under share/agents
     * 2. Installs the agent CLI as /usr/bin/agent
     * 3. Sets up proper permissions
     * 
     * Note: The agent framework is fully offline - no external API calls.
     * It depends on Python being installed (pkg install python).
     */
    private fun setupAgentFramework(binDir: File, ourFilesPrefix: String, context: Context) {
        try {
            val usrDir = binDir.parentFile ?: return  // bin's parent is usr (staging)
            val shareDir = File(usrDir, "share")
            val agentsDir = File(shareDir, "agents")
            // Create etc/agents in the staging usr directory, not the final location
            val etcAgentsDir = File(usrDir, "etc/agents")
            
            // Extract agent framework from assets
            try {
                val assetManager = context.assets
                val zipStream = assetManager.open("agents.zip")
                val zipFile = java.util.zip.ZipInputStream(zipStream)
                
                var entry = zipFile.nextEntry
                while (entry != null) {
                    // agents/bin/agent -> share/agents/bin/agent
                    val destPath = if (entry.name.startsWith("agents/")) {
                        entry.name.replaceFirst("agents/", "")
                    } else {
                        entry.name
                    }
                    val destFile = File(agentsDir, destPath)
                    
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        destFile.outputStream().use { output ->
                            zipFile.copyTo(output)
                        }
                        // Make Python files executable
                        if (destPath.endsWith(".py") || destPath == "bin/agent") {
                            Os.chmod(destFile.absolutePath, 493) // 0755
                        }
                    }
                    zipFile.closeEntry()
                    entry = zipFile.nextEntry
                }
                zipFile.close()
                Logger.logInfo(LOG_TAG, "Extracted agent framework to ${agentsDir.absolutePath}")
            } catch (e: Exception) {
                Logger.logWarn(LOG_TAG, "Could not extract agents.zip: ${e.message}")
            }
            
            // Create additional required directories (may already exist from zip)
            val dirs = listOf(
                File(agentsDir, "sandboxes"),
                File(agentsDir, "memory"),
                File(agentsDir, "logs"),
                etcAgentsDir
            )
            
            for (dir in dirs) {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
            
            // Create the agent CLI wrapper script in bin/
            val agentScript = File(binDir, "agent")
            val wrapperContent = """#!/data/data/com.termux.kotlin/files/usr/bin/bash
# Termux-Kotlin Agent CLI
# This is the main entrypoint for the agent framework.
# Requires Python to be installed: pkg install python

PREFIX="${ourFilesPrefix}/usr"
AGENTS_ROOT="${'$'}PREFIX/share/agents"
export AGENTS_ROOT

# Check if Python is available
if ! command -v python3 &> /dev/null; then
    echo "Error: Python is not installed."
    echo "Install with: pkg install python"
    exit 1
fi

# Check for PyYAML (recommended)
if ! python3 -c "import yaml" 2>/dev/null; then
    echo "Warning: PyYAML not installed. Some features may be limited."
    echo "Install with: pip install pyyaml"
fi

# Set Python path to include agents
export PYTHONPATH="${'$'}AGENTS_ROOT:${'$'}PYTHONPATH"

# Run the agent CLI
exec python3 "${'$'}AGENTS_ROOT/bin/agent" "${'$'}@"
"""
            agentScript.writeText(wrapperContent)
            Os.chmod(agentScript.absolutePath, 493) // 0755
            
            Logger.logInfo(LOG_TAG, "Agent framework CLI installed at ${agentScript.absolutePath}")
            
            // Create agent framework config file
            val configFile = File(etcAgentsDir, "config.yml")
            val configContent = """# Termux-Kotlin Agent Framework Configuration
# This file configures the agent framework.

version: "1.0"

# Agents root directory
agents_root: ${ourFilesPrefix}/usr/share/agents

# Default settings
defaults:
  memory_backend: json
  network: full  # Network enabled for autonomous operation

# Autonomous mode - agents run in background
autonomous:
  enabled: true
  auto_start: true
  background_service: true

# Logging
logging:
  level: INFO
  file: ${ourFilesPrefix}/usr/share/agents/logs/agentd.log

# Security
security:
  # All agents are sandboxed by default
  sandbox_enabled: true
  # Network enabled for autonomous agents
  default_network_capability: full
  allowed_hosts:
    - "*"  # Allow all hosts for autonomous operation
"""
            configFile.writeText(configContent)
            
            Logger.logInfo(LOG_TAG, "Agent framework configuration created")
            
        } catch (e: Exception) {
            // Non-fatal - agent framework is optional
            Logger.logError(LOG_TAG, "Failed to setup agent framework: ${e.message}")
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
