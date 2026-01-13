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
import com.termux.kotlin.R
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
                ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipInput ->
                    var zipEntry = zipInput.nextEntry
                    while (zipEntry != null) {
                        if (zipEntry.name == "SYMLINKS.txt") {
                            val symlinksReader = BufferedReader(InputStreamReader(zipInput))
                            var line = symlinksReader.readLine()
                            while (line != null) {
                                val parts = line.split("←")
                                if (parts.size != 2) throw RuntimeException("Malformed symlink line: $line")
                                val oldPath = parts[0]
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
                            }
                        }
                        zipEntry = zipInput.nextEntry
                    }
                }

                if (symlinks.isEmpty()) throw RuntimeException("No SYMLINKS.txt encountered")
                for (symlink in symlinks) {
                    Os.symlink(symlink.first, symlink.second)
                }

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

    @JvmStatic
    fun loadZipBytes(): ByteArray {
        // Only load the shared library when necessary to save memory usage.
        System.loadLibrary("termux-bootstrap")
        return getZip()
    }

    @JvmStatic
    external fun getZip(): ByteArray
}
