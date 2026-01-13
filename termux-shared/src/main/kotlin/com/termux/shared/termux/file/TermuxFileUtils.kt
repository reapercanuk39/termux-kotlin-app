package com.termux.shared.termux.file

import android.content.Context
import android.os.Environment
import com.termux.shared.android.AndroidUtils
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.file.FileUtilsErrno
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.runner.app.AppShell
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import java.util.regex.Pattern

object TermuxFileUtils {

    private const val LOG_TAG = "TermuxFileUtils"

    /**
     * Replace "$PREFIX/" or "~/" prefix with termux absolute paths.
     *
     * @param paths The `paths` to expand.
     * @return Returns the `expand paths`.
     */
    @JvmStatic
    fun getExpandedTermuxPaths(paths: List<String>?): List<String>? {
        if (paths == null) return null
        return paths.mapNotNull { getExpandedTermuxPath(it) }
    }

    /**
     * Replace "$PREFIX/" or "~/" prefix with termux absolute paths.
     *
     * @param path The `path` to expand.
     * @return Returns the `expand path`.
     */
    @JvmStatic
    fun getExpandedTermuxPath(path: String?): String? {
        var result = path
        if (!result.isNullOrEmpty()) {
            result = result.replace(Regex("^\\\$PREFIX$"), TermuxConstants.TERMUX_PREFIX_DIR_PATH)
            result = result.replace(Regex("^\\\$PREFIX/"), TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/")
            result = result.replace(Regex("^~/$"), TermuxConstants.TERMUX_HOME_DIR_PATH)
            result = result.replace(Regex("^~/"), TermuxConstants.TERMUX_HOME_DIR_PATH + "/")
        }
        return result
    }

    /**
     * Replace termux absolute paths with "$PREFIX/" or "~/" prefix.
     *
     * @param paths The `paths` to unexpand.
     * @return Returns the `unexpand paths`.
     */
    @JvmStatic
    fun getUnExpandedTermuxPaths(paths: List<String>?): List<String>? {
        if (paths == null) return null
        return paths.mapNotNull { getUnExpandedTermuxPath(it) }
    }

    /**
     * Replace termux absolute paths with "$PREFIX/" or "~/" prefix.
     *
     * @param path The `path` to unexpand.
     * @return Returns the `unexpand path`.
     */
    @JvmStatic
    fun getUnExpandedTermuxPath(path: String?): String? {
        var result = path
        if (!result.isNullOrEmpty()) {
            result = result.replace(Regex("^${Pattern.quote(TermuxConstants.TERMUX_PREFIX_DIR_PATH)}/"), "\$PREFIX/")
            result = result.replace(Regex("^${Pattern.quote(TermuxConstants.TERMUX_HOME_DIR_PATH)}/"), "~/")
        }
        return result
    }

    /**
     * Get canonical path.
     *
     * @param path The `path` to convert.
     * @param prefixForNonAbsolutePath Optional prefix path to prefix before non-absolute paths.
     * @param expandPath The `boolean` that decides if input path is first attempted to be expanded.
     * @return Returns the `canonical path`.
     */
    @JvmStatic
    fun getCanonicalPath(path: String?, prefixForNonAbsolutePath: String?, expandPath: Boolean): String {
        var result = path ?: ""
        if (expandPath) {
            result = getExpandedTermuxPath(result) ?: ""
        }
        return FileUtils.getCanonicalPath(result, prefixForNonAbsolutePath)
    }

    /**
     * Check if `path` is under the allowed termux working directory paths.
     *
     * @param path The `path` to check.
     * @return Returns the allowed path if `path` is under it, otherwise [TermuxConstants.TERMUX_FILES_DIR_PATH].
     */
    @JvmStatic
    fun getMatchedAllowedTermuxWorkingDirectoryParentPathForPath(path: String?): String {
        if (path.isNullOrEmpty()) return TermuxConstants.TERMUX_FILES_DIR_PATH

        return when {
            path.startsWith(TermuxConstants.TERMUX_STORAGE_HOME_DIR_PATH + "/") -> 
                TermuxConstants.TERMUX_STORAGE_HOME_DIR_PATH
            path.startsWith(Environment.getExternalStorageDirectory().absolutePath + "/") -> 
                Environment.getExternalStorageDirectory().absolutePath
            path.startsWith("/sdcard/") -> "/sdcard"
            else -> TermuxConstants.TERMUX_FILES_DIR_PATH
        }
    }

    /**
     * Validate the existence and permissions of directory file at path as a working directory for termux app.
     */
    @JvmStatic
    fun validateDirectoryFileExistenceAndPermissions(
        label: String?,
        filePath: String,
        createDirectoryIfMissing: Boolean,
        setPermissions: Boolean,
        setMissingPermissionsOnly: Boolean,
        ignoreErrorsIfPathIsInParentDirPath: Boolean,
        ignoreIfNotExecutable: Boolean
    ): Error? {
        return FileUtils.validateDirectoryFileExistenceAndPermissions(
            label, filePath,
            getMatchedAllowedTermuxWorkingDirectoryParentPathForPath(filePath),
            createDirectoryIfMissing,
            FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS,
            setPermissions, setMissingPermissionsOnly,
            ignoreErrorsIfPathIsInParentDirPath, ignoreIfNotExecutable
        )
    }

    /**
     * Validate if [TermuxConstants.TERMUX_FILES_DIR_PATH] exists and has
     * [FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS] permissions.
     */
    @JvmStatic
    fun isTermuxFilesDirectoryAccessible(
        context: Context,
        createDirectoryIfMissing: Boolean,
        setMissingPermissions: Boolean
    ): Error? {
        if (createDirectoryIfMissing) context.filesDir

        if (!FileUtils.directoryFileExists(TermuxConstants.TERMUX_FILES_DIR_PATH, true))
            return FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(
                "termux files directory", TermuxConstants.TERMUX_FILES_DIR_PATH
            )

        if (setMissingPermissions)
            FileUtils.setMissingFilePermissions(
                "termux files directory", TermuxConstants.TERMUX_FILES_DIR_PATH,
                FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS
            )

        return FileUtils.checkMissingFilePermissions(
            "termux files directory", TermuxConstants.TERMUX_FILES_DIR_PATH,
            FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS, false
        )
    }

    /**
     * Validate if [TermuxConstants.TERMUX_PREFIX_DIR_PATH] exists and has
     * [FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS] permissions.
     */
    @JvmStatic
    fun isTermuxPrefixDirectoryAccessible(
        createDirectoryIfMissing: Boolean,
        setMissingPermissions: Boolean
    ): Error? {
        return FileUtils.validateDirectoryFileExistenceAndPermissions(
            "termux prefix directory", TermuxConstants.TERMUX_PREFIX_DIR_PATH,
            null, createDirectoryIfMissing,
            FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS, setMissingPermissions, true,
            false, false
        )
    }

    /**
     * Validate if [TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH] exists and has
     * [FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS] permissions.
     */
    @JvmStatic
    fun isTermuxPrefixStagingDirectoryAccessible(
        createDirectoryIfMissing: Boolean,
        setMissingPermissions: Boolean
    ): Error? {
        return FileUtils.validateDirectoryFileExistenceAndPermissions(
            "termux prefix staging directory", TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH,
            null, createDirectoryIfMissing,
            FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS, setMissingPermissions, true,
            false, false
        )
    }

    /**
     * Validate if [TermuxConstants.TERMUX_APP.APPS_DIR_PATH] exists and has
     * [FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS] permissions.
     */
    @JvmStatic
    fun isAppsTermuxAppDirectoryAccessible(
        createDirectoryIfMissing: Boolean,
        setMissingPermissions: Boolean
    ): Error? {
        return FileUtils.validateDirectoryFileExistenceAndPermissions(
            "apps/termux-app directory", TermuxConstants.TERMUX_APP.APPS_DIR_PATH,
            null, createDirectoryIfMissing,
            FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS, setMissingPermissions, true,
            false, false
        )
    }

    /**
     * If [TermuxConstants.TERMUX_PREFIX_DIR_PATH] doesn't exist, is empty or only contains
     * files in [TermuxConstants.TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY].
     */
    @JvmStatic
    fun isTermuxPrefixDirectoryEmpty(): Boolean {
        val error = FileUtils.validateDirectoryFileEmptyOrOnlyContainsSpecificFiles(
            "termux prefix",
            TermuxConstants.TERMUX_PREFIX_DIR_PATH,
            TermuxConstants.TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY,
            true
        )
        if (error == null) return true

        if (!FileUtilsErrno.ERRNO_NON_EMPTY_DIRECTORY_FILE.equalsErrorTypeAndCode(error))
            Logger.logErrorExtended(LOG_TAG, "Failed to check if termux prefix directory is empty:\n" + error.errorLogString)
        return false
    }

    /**
     * Get a markdown [String] for stat output for various Termux app files paths.
     *
     * @param context The context for operations.
     * @return Returns the markdown [String].
     */
    @JvmStatic
    fun getTermuxFilesStatMarkdownString(context: Context): String? {
        val termuxPackageContext = TermuxUtils.getTermuxPackageContext(context) ?: return null

        // Also ensures that termux files directory is created if it does not already exist
        val filesDir = termuxPackageContext.filesDir.absolutePath

        // Build script
        val statScript = StringBuilder().apply {
            append("echo 'ls info:'\n")
            append("/system/bin/ls -lhdZ")
            append(" '/data/data'")
            append(" '/data/user/0'")
            append(" '${TermuxConstants.TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH}'")
            append(" '/data/user/0/${TermuxConstants.TERMUX_PACKAGE_NAME}'")
            append(" '${TermuxConstants.TERMUX_FILES_DIR_PATH}'")
            append(" '$filesDir'")
            append(" '/data/user/0/${TermuxConstants.TERMUX_PACKAGE_NAME}/files'")
            append(" '/data/user/${TermuxConstants.TERMUX_PACKAGE_NAME}/files'")
            append(" '${TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH}'")
            append(" '${TermuxConstants.TERMUX_PREFIX_DIR_PATH}'")
            append(" '${TermuxConstants.TERMUX_HOME_DIR_PATH}'")
            append(" '${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/login'")
            append(" 2>&1")
            append("\necho; echo 'mount info:'\n")
            append("/system/bin/grep -E '( /data )|( /data/data )|( /data/user/[0-9]+ )' /proc/self/mountinfo 2>&1 | /system/bin/grep -v '/data_mirror' 2>&1")
        }

        // Run script
        val executionCommand = ExecutionCommand(
            -1, "/system/bin/sh", null,
            statScript.toString() + "\n", "/",
            ExecutionCommand.Runner.APP_SHELL.getName(), true
        ).apply {
            commandLabel = "${TermuxConstants.TERMUX_APP_NAME} Files Stat Command"
            backgroundCustomLogLevel = Logger.LOG_LEVEL_OFF
        }
        
        val appShell = AppShell.execute(context, executionCommand, null, TermuxShellEnvironment(), null, true)
        if (appShell == null || !executionCommand.isSuccessful()) {
            Logger.logErrorExtended(LOG_TAG, executionCommand.toString())
            return null
        }

        // Build script output
        val statOutput = StringBuilder().apply {
            append("$ ").append(statScript.toString())
            append("\n\n").append(executionCommand.resultData.stdout.toString())

            val stderrSet = executionCommand.resultData.stderr.toString().isNotEmpty()
            if (executionCommand.resultData.exitCode != 0 || stderrSet) {
                Logger.logErrorExtended(LOG_TAG, executionCommand.toString())
                if (stderrSet) append("\n").append(executionCommand.resultData.stderr.toString())
                append("\n").append("exit code: ").append(executionCommand.resultData.exitCode.toString())
            }
        }

        // Build markdown output
        return StringBuilder().apply {
            append("## ").append(TermuxConstants.TERMUX_APP_NAME).append(" Files Info\n\n")
            AndroidUtils.appendPropertyToMarkdown(this, "TERMUX_REQUIRED_FILES_DIR_PATH (\$PREFIX)", TermuxConstants.TERMUX_FILES_DIR_PATH)
            AndroidUtils.appendPropertyToMarkdown(this, "ANDROID_ASSIGNED_FILES_DIR_PATH", filesDir)
            append("\n\n").append(MarkdownUtils.getMarkdownCodeForString(statOutput.toString(), true))
            append("\n##\n")
        }.toString()
    }
}
