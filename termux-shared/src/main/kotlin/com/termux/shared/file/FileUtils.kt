package com.termux.shared.file

import android.os.Build
import android.system.Os

import com.google.common.io.RecursiveDeleteOption
import com.termux.shared.file.filesystem.FileType
import com.termux.shared.file.filesystem.FileTypes
import com.termux.shared.data.DataUtils
import com.termux.shared.logger.Logger
import com.termux.shared.errors.Errno
import com.termux.shared.errors.Error
import com.termux.shared.errors.FunctionErrno

import org.apache.commons.io.filefilter.AgeFileFilter
import org.apache.commons.io.filefilter.IOFileFilter

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStreamWriter
import java.io.Serializable
import java.nio.charset.Charset
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.Calendar
import java.util.regex.Pattern

object FileUtils {

    /** Required file permissions for the executable file for app usage. */
    const val APP_EXECUTABLE_FILE_PERMISSIONS = "r-x"
    /** Required file permissions for the working directory for app usage. */
    const val APP_WORKING_DIRECTORY_PERMISSIONS = "rwx"

    private const val LOG_TAG = "FileUtils"

    /**
     * Get canonical path.
     */
    @JvmStatic
    fun getCanonicalPath(path: String?, prefixForNonAbsolutePath: String?): String {
        val p = path ?: ""

        val absolutePath = if (p.startsWith("/")) {
            p
        } else {
            if (prefixForNonAbsolutePath != null)
                "$prefixForNonAbsolutePath/$p"
            else
                "/$p"
        }

        return try {
            File(absolutePath).canonicalPath
        } catch (e: Exception) {
            absolutePath
        }
    }

    /**
     * Removes one or more forward slashes "//" with single slash "/"
     * Removes "./"
     * Removes trailing forward slash "/"
     */
    @JvmStatic
    fun normalizePath(path: String?): String? {
        if (path == null) return null

        var p = path.replace(Regex("/+"), "/")
        p = p.replace(Regex("\\./"), "")

        if (p.endsWith("/")) {
            p = p.replace(Regex("/+$"), "")
        }

        return p
    }

    /**
     * Convert special characters to underscore.
     */
    @JvmStatic
    fun sanitizeFileName(fileName: String?, sanitizeWhitespaces: Boolean, toLower: Boolean): String? {
        if (fileName == null) return null

        var name = if (sanitizeWhitespaces)
            fileName.replace(Regex("[\\\\/:*?\"<>| \t\n]"), "_")
        else
            fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        return if (toLower) name.lowercase() else name
    }

    /**
     * Determines whether path is in dirPath.
     */
    @JvmStatic
    fun isPathInDirPath(path: String, dirPath: String, ensureUnder: Boolean): Boolean {
        return isPathInDirPaths(path, listOf(dirPath), ensureUnder)
    }

    /**
     * Determines whether path is in one of the dirPaths.
     */
    @JvmStatic
    fun isPathInDirPaths(path: String?, dirPaths: List<String>?, ensureUnder: Boolean): Boolean {
        if (path.isNullOrEmpty() || dirPaths.isNullOrEmpty()) return false

        val canonicalPath = try {
            File(path).canonicalPath
        } catch (e: Exception) {
            return false
        }

        for (dirPath in dirPaths) {
            val normalizedDirPath = normalizePath(dirPath)

            val isPathInDirPaths = if (ensureUnder)
                canonicalPath != normalizedDirPath && canonicalPath.startsWith("$normalizedDirPath/")
            else
                canonicalPath.startsWith("$normalizedDirPath/")

            if (isPathInDirPaths) return true
        }

        return false
    }

    /**
     * Validate that directory is empty or contains only files in ignoredSubFilePaths.
     */
    @JvmStatic
    fun validateDirectoryFileEmptyOrOnlyContainsSpecificFiles(
        label: String?,
        filePath: String?,
        ignoredSubFilePaths: List<String>?,
        ignoreNonExistentFile: Boolean
    ): Error? {
        val lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "file path", "isDirectoryFileEmptyOrOnlyContainsSpecificFiles")

        try {
            val file = File(filePath)
            val fileType = getFileType(filePath, false)

            if (fileType != FileType.NO_EXIST && fileType != FileType.DIRECTORY) {
                return FileUtilsErrno.ERRNO_NON_DIRECTORY_FILE_FOUND.getError(lbl + "directory", filePath).setLabel(lbl + "directory")
            }

            if (fileType == FileType.NO_EXIST) {
                return if (ignoreNonExistentFile) {
                    null
                } else {
                    val l = lbl + "directory to check if is empty or only contains specific files"
                    FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(l, filePath).setLabel(l)
                }
            }

            val subFiles = file.listFiles()
            if (subFiles == null || subFiles.isEmpty()) return null

            if (ignoredSubFilePaths.isNullOrEmpty())
                return FileUtilsErrno.ERRNO_NON_EMPTY_DIRECTORY_FILE.getError(lbl, filePath)

            if (nonIgnoredSubFileExists(subFiles, ignoredSubFilePaths)) {
                return FileUtilsErrno.ERRNO_NON_EMPTY_DIRECTORY_FILE.getError(lbl, filePath)
            }

        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_VALIDATE_DIRECTORY_EMPTY_OR_ONLY_CONTAINS_SPECIFIC_FILES_FAILED_WITH_EXCEPTION.getError(e, lbl + "directory", filePath, e.message)
        }

        return null
    }

    /**
     * Check if subFiles contains a file not in ignoredSubFilePaths.
     */
    @JvmStatic
    fun nonIgnoredSubFileExists(subFiles: Array<File>?, ignoredSubFilePaths: List<String>): Boolean {
        if (subFiles == null || subFiles.isEmpty()) return false

        for (subFile in subFiles) {
            val subFilePath = subFile.absolutePath
            if (!ignoredSubFilePaths.contains(subFilePath)) {
                var isParentPath = false
                for (ignoredSubFilePath in ignoredSubFilePaths) {
                    if (ignoredSubFilePath.startsWith("$subFilePath/") && fileExists(ignoredSubFilePath, false)) {
                        isParentPath = true
                        break
                    }
                }
                if (!isParentPath) {
                    return true
                }
            }

            if (getFileType(subFilePath, false) == FileType.DIRECTORY) {
                if (nonIgnoredSubFileExists(subFile.listFiles(), ignoredSubFilePaths))
                    return true
            }
        }

        return false
    }

    /**
     * Checks whether a regular file exists at filePath.
     */
    @JvmStatic
    fun regularFileExists(filePath: String, followLinks: Boolean): Boolean {
        return getFileType(filePath, followLinks) == FileType.REGULAR
    }

    /**
     * Checks whether a directory file exists at filePath.
     */
    @JvmStatic
    fun directoryFileExists(filePath: String, followLinks: Boolean): Boolean {
        return getFileType(filePath, followLinks) == FileType.DIRECTORY
    }

    /**
     * Checks whether a symlink file exists at filePath.
     */
    @JvmStatic
    fun symlinkFileExists(filePath: String): Boolean {
        return getFileType(filePath, false) == FileType.SYMLINK
    }

    /**
     * Checks whether a regular or directory file exists at filePath.
     */
    @JvmStatic
    fun regularOrDirectoryFileExists(filePath: String, followLinks: Boolean): Boolean {
        val fileType = getFileType(filePath, followLinks)
        return fileType == FileType.REGULAR || fileType == FileType.DIRECTORY
    }

    /**
     * Checks whether any file exists at filePath.
     */
    @JvmStatic
    fun fileExists(filePath: String, followLinks: Boolean): Boolean {
        return getFileType(filePath, followLinks) != FileType.NO_EXIST
    }

    /**
     * Get the type of file that exists at filePath.
     */
    @JvmStatic
    fun getFileType(filePath: String, followLinks: Boolean): FileType {
        return FileTypes.getFileType(filePath, followLinks)
    }

    /**
     * Validate the existence and permissions of regular file at path.
     */
    @JvmStatic
    fun validateRegularFileExistenceAndPermissions(
        label: String?,
        filePath: String?,
        parentDirPath: String?,
        permissionsToCheck: String?,
        setPermissions: Boolean,
        setMissingPermissionsOnly: Boolean,
        ignoreErrorsIfPathIsUnderParentDirPath: Boolean
    ): Error? {
        var lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "regular file path", "validateRegularFileExistenceAndPermissions")

        try {
            val fileType = getFileType(filePath, false)

            if (fileType != FileType.NO_EXIST && fileType != FileType.REGULAR) {
                return FileUtilsErrno.ERRNO_NON_REGULAR_FILE_FOUND.getError(lbl + "file", filePath).setLabel(lbl + "file")
            }

            var isPathUnderParentDirPath = false
            if (parentDirPath != null) {
                isPathUnderParentDirPath = isPathInDirPath(filePath, parentDirPath, true)
            }

            if (setPermissions && permissionsToCheck != null && fileType == FileType.REGULAR) {
                if (parentDirPath == null || (isPathUnderParentDirPath && getFileType(parentDirPath, false) == FileType.DIRECTORY)) {
                    if (setMissingPermissionsOnly)
                        setMissingFilePermissions(lbl + "file", filePath, permissionsToCheck)
                    else
                        setFilePermissions(lbl + "file", filePath, permissionsToCheck)
                }
            }

            if (fileType != FileType.REGULAR) {
                lbl += "regular file"
                return FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(lbl, filePath).setLabel(lbl)
            }

            if (parentDirPath == null || !isPathUnderParentDirPath || !ignoreErrorsIfPathIsUnderParentDirPath) {
                if (permissionsToCheck != null) {
                    return checkMissingFilePermissions(lbl + "regular", filePath, permissionsToCheck, false)
                }
            }
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_VALIDATE_FILE_EXISTENCE_AND_PERMISSIONS_FAILED_WITH_EXCEPTION.getError(e, lbl + "file", filePath, e.message)
        }

        return null
    }

    /**
     * Validate the existence and permissions of directory file at path.
     */
    @JvmStatic
    fun validateDirectoryFileExistenceAndPermissions(
        label: String?,
        filePath: String?,
        parentDirPath: String?,
        createDirectoryIfMissing: Boolean,
        permissionsToCheck: String?,
        setPermissions: Boolean,
        setMissingPermissionsOnly: Boolean,
        ignoreErrorsIfPathIsInParentDirPath: Boolean,
        ignoreIfNotExecutable: Boolean
    ): Error? {
        var lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "directory file path", "validateDirectoryExistenceAndPermissions")

        try {
            val file = File(filePath)
            var fileType = getFileType(filePath, false)

            if (fileType != FileType.NO_EXIST && fileType != FileType.DIRECTORY) {
                return FileUtilsErrno.ERRNO_NON_DIRECTORY_FILE_FOUND.getError(lbl + "directory", filePath).setLabel(lbl + "directory")
            }

            var isPathInParentDirPath = false
            if (parentDirPath != null) {
                isPathInParentDirPath = isPathInDirPath(filePath, parentDirPath, false)
            }

            if (createDirectoryIfMissing || setPermissions) {
                if (parentDirPath == null || (isPathInParentDirPath && getFileType(parentDirPath, false) == FileType.DIRECTORY)) {
                    if (createDirectoryIfMissing && fileType == FileType.NO_EXIST) {
                        Logger.logVerbose(LOG_TAG, "Creating $lbl" + "directory file at path \"$filePath\"")
                        val result = file.mkdirs()
                        fileType = getFileType(filePath, false)
                        if (!result && fileType != FileType.DIRECTORY)
                            return FileUtilsErrno.ERRNO_CREATING_FILE_FAILED.getError(lbl + "directory file", filePath)
                    }

                    if (setPermissions && permissionsToCheck != null && fileType == FileType.DIRECTORY) {
                        if (setMissingPermissionsOnly)
                            setMissingFilePermissions(lbl + "directory", filePath, permissionsToCheck)
                        else
                            setFilePermissions(lbl + "directory", filePath, permissionsToCheck)
                    }
                }
            }

            if (parentDirPath == null || !isPathInParentDirPath || !ignoreErrorsIfPathIsInParentDirPath) {
                if (fileType != FileType.DIRECTORY) {
                    lbl += "directory"
                    return FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(lbl, filePath).setLabel(lbl)
                }

                if (permissionsToCheck != null) {
                    return checkMissingFilePermissions(lbl + "directory", filePath, permissionsToCheck, ignoreIfNotExecutable)
                }
            }
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_VALIDATE_DIRECTORY_EXISTENCE_AND_PERMISSIONS_FAILED_WITH_EXCEPTION.getError(e, lbl + "directory file", filePath, e.message)
        }

        return null
    }

    /**
     * Create a regular file at path.
     */
    @JvmStatic
    fun createRegularFile(filePath: String): Error? {
        return createRegularFile(null, filePath)
    }

    @JvmStatic
    fun createRegularFile(label: String?, filePath: String): Error? {
        return createRegularFile(label, filePath, null, false, false)
    }

    @JvmStatic
    fun createRegularFile(
        label: String?,
        filePath: String?,
        permissionsToCheck: String?,
        setPermissions: Boolean,
        setMissingPermissionsOnly: Boolean
    ): Error? {
        val lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "file path", "createRegularFile")

        val file = File(filePath)
        val fileType = getFileType(filePath, false)

        if (fileType != FileType.NO_EXIST && fileType != FileType.REGULAR) {
            return FileUtilsErrno.ERRNO_NON_REGULAR_FILE_FOUND.getError(lbl + "file", filePath).setLabel(lbl + "file")
        }

        if (fileType == FileType.REGULAR) {
            return null
        }

        val error = createParentDirectoryFile(lbl + "regular file parent", filePath)
        if (error != null) return error

        try {
            Logger.logVerbose(LOG_TAG, "Creating $lbl" + "regular file at path \"$filePath\"")

            if (!file.createNewFile())
                return FileUtilsErrno.ERRNO_CREATING_FILE_FAILED.getError(lbl + "regular file", filePath)
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_CREATING_FILE_FAILED_WITH_EXCEPTION.getError(e, lbl + "regular file", filePath, e.message)
        }

        return validateRegularFileExistenceAndPermissions(label, filePath, null, permissionsToCheck, setPermissions, setMissingPermissionsOnly, false)
    }

    /**
     * Create parent directory of file at path.
     */
    @JvmStatic
    fun createParentDirectoryFile(label: String?, filePath: String?): Error? {
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError("$label file path", "createParentDirectoryFile")

        val file = File(filePath)
        val fileParentPath = file.parent

        return if (fileParentPath != null)
            createDirectoryFile(label, fileParentPath, null, false, false)
        else
            null
    }

    /**
     * Create a directory file at path.
     */
    @JvmStatic
    fun createDirectoryFile(filePath: String): Error? {
        return createDirectoryFile(null, filePath)
    }

    @JvmStatic
    fun createDirectoryFile(label: String?, filePath: String): Error? {
        return createDirectoryFile(label, filePath, null, false, false)
    }

    @JvmStatic
    fun createDirectoryFile(
        label: String?,
        filePath: String?,
        permissionsToCheck: String?,
        setPermissions: Boolean,
        setMissingPermissionsOnly: Boolean
    ): Error? {
        return validateDirectoryFileExistenceAndPermissions(
            label, filePath, null, true,
            permissionsToCheck, setPermissions, setMissingPermissionsOnly,
            false, false
        )
    }

    /**
     * Create a symlink file at path.
     */
    @JvmStatic
    fun createSymlinkFile(targetFilePath: String, destFilePath: String): Error? {
        return createSymlinkFile(null, targetFilePath, destFilePath, true, true, true)
    }

    @JvmStatic
    fun createSymlinkFile(label: String?, targetFilePath: String, destFilePath: String): Error? {
        return createSymlinkFile(label, targetFilePath, destFilePath, true, true, true)
    }

    @JvmStatic
    fun createSymlinkFile(
        label: String?,
        targetFilePath: String?,
        destFilePath: String?,
        allowDangling: Boolean,
        overwrite: Boolean,
        overwriteOnlyIfDestIsASymlink: Boolean
    ): Error? {
        val lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (targetFilePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "target file path", "createSymlinkFile")
        if (destFilePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "destination file path", "createSymlinkFile")

        try {
            val destFile = File(destFilePath)

            var targetFileAbsolutePath = targetFilePath
            if (!targetFilePath.startsWith("/")) {
                val destFileParentPath = destFile.parent
                if (destFileParentPath != null)
                    targetFileAbsolutePath = "$destFileParentPath/$targetFilePath"
            }

            val targetFileType = getFileType(targetFileAbsolutePath, false)
            val destFileType = getFileType(destFilePath, false)

            if (targetFileType == FileType.NO_EXIST) {
                if (!allowDangling) {
                    val l = lbl + "symlink target file"
                    return FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(l, targetFileAbsolutePath).setLabel(l)
                }
            }

            if (destFileType != FileType.NO_EXIST) {
                if (!overwrite) {
                    return null
                }

                if (overwriteOnlyIfDestIsASymlink && destFileType != FileType.SYMLINK)
                    return FileUtilsErrno.ERRNO_CANNOT_OVERWRITE_A_NON_SYMLINK_FILE_TYPE.getError(lbl + " file", destFilePath, targetFilePath, destFileType.name)

                val error = deleteFile(lbl + "symlink destination", destFilePath, true)
                if (error != null) return error
            } else {
                val error = createParentDirectoryFile(lbl + "symlink destination file parent", destFilePath)
                if (error != null) return error
            }

            Logger.logVerbose(LOG_TAG, "Creating $lbl" + "symlink file at path \"$destFilePath\" to \"$targetFilePath\"")
            Os.symlink(targetFilePath, destFilePath)
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_CREATING_SYMLINK_FILE_FAILED_WITH_EXCEPTION.getError(e, lbl + "symlink file", destFilePath, targetFilePath, e.message)
        }

        return null
    }

    /**
     * Copy a regular file.
     */
    @JvmStatic
    fun copyRegularFile(label: String?, srcFilePath: String, destFilePath: String, ignoreNonExistentSrcFile: Boolean): Error? {
        return copyOrMoveFile(label, srcFilePath, destFilePath, false, ignoreNonExistentSrcFile, FileType.REGULAR.value, true, true)
    }

    /**
     * Move a regular file.
     */
    @JvmStatic
    fun moveRegularFile(label: String?, srcFilePath: String, destFilePath: String, ignoreNonExistentSrcFile: Boolean): Error? {
        return copyOrMoveFile(label, srcFilePath, destFilePath, true, ignoreNonExistentSrcFile, FileType.REGULAR.value, true, true)
    }

    /**
     * Copy a directory file.
     */
    @JvmStatic
    fun copyDirectoryFile(label: String?, srcFilePath: String, destFilePath: String, ignoreNonExistentSrcFile: Boolean): Error? {
        return copyOrMoveFile(label, srcFilePath, destFilePath, false, ignoreNonExistentSrcFile, FileType.DIRECTORY.value, true, true)
    }

    /**
     * Move a directory file.
     */
    @JvmStatic
    fun moveDirectoryFile(label: String?, srcFilePath: String, destFilePath: String, ignoreNonExistentSrcFile: Boolean): Error? {
        return copyOrMoveFile(label, srcFilePath, destFilePath, true, ignoreNonExistentSrcFile, FileType.DIRECTORY.value, true, true)
    }

    /**
     * Copy a symlink file.
     */
    @JvmStatic
    fun copySymlinkFile(label: String?, srcFilePath: String, destFilePath: String, ignoreNonExistentSrcFile: Boolean): Error? {
        return copyOrMoveFile(label, srcFilePath, destFilePath, false, ignoreNonExistentSrcFile, FileType.SYMLINK.value, true, true)
    }

    /**
     * Move a symlink file.
     */
    @JvmStatic
    fun moveSymlinkFile(label: String?, srcFilePath: String, destFilePath: String, ignoreNonExistentSrcFile: Boolean): Error? {
        return copyOrMoveFile(label, srcFilePath, destFilePath, true, ignoreNonExistentSrcFile, FileType.SYMLINK.value, true, true)
    }

    /**
     * Copy a file.
     */
    @JvmStatic
    fun copyFile(label: String?, srcFilePath: String, destFilePath: String, ignoreNonExistentSrcFile: Boolean): Error? {
        return copyOrMoveFile(label, srcFilePath, destFilePath, false, ignoreNonExistentSrcFile, FileTypes.FILE_TYPE_NORMAL_FLAGS, true, true)
    }

    /**
     * Move a file.
     */
    @JvmStatic
    fun moveFile(label: String?, srcFilePath: String, destFilePath: String, ignoreNonExistentSrcFile: Boolean): Error? {
        return copyOrMoveFile(label, srcFilePath, destFilePath, true, ignoreNonExistentSrcFile, FileTypes.FILE_TYPE_NORMAL_FLAGS, true, true)
    }

    /**
     * Copy or move a file.
     */
    @JvmStatic
    fun copyOrMoveFile(
        label: String?,
        srcFilePath: String?,
        destFilePath: String?,
        moveFile: Boolean,
        ignoreNonExistentSrcFile: Boolean,
        allowedFileTypeFlags: Int,
        overwrite: Boolean,
        overwriteOnlyIfDestSameFileTypeAsSrc: Boolean
    ): Error? {
        var lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (srcFilePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "source file path", "copyOrMoveFile")
        if (destFilePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "destination file path", "copyOrMoveFile")

        val mode = if (moveFile) "Moving" else "Copying"
        val modePast = if (moveFile) "moved" else "copied"

        try {
            Logger.logVerbose(LOG_TAG, "$mode $lbl" + "source file from \"$srcFilePath\" to destination \"$destFilePath\"")

            val srcFile = File(srcFilePath)
            val destFile = File(destFilePath)

            var srcFileType = getFileType(srcFilePath, false)
            var destFileType = getFileType(destFilePath, false)

            val srcFileCanonicalPath = srcFile.canonicalPath
            val destFileCanonicalPath = destFile.canonicalPath

            if (srcFileType == FileType.NO_EXIST) {
                return if (ignoreNonExistentSrcFile) {
                    null
                } else {
                    lbl += "source file"
                    FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(lbl, srcFilePath).setLabel(lbl)
                }
            }

            if ((allowedFileTypeFlags and srcFileType.value) <= 0)
                return FileUtilsErrno.ERRNO_FILE_NOT_AN_ALLOWED_FILE_TYPE.getError(lbl + "source file meant to be $modePast", srcFilePath, FileTypes.convertFileTypeFlagsToNamesString(allowedFileTypeFlags))

            if (srcFileCanonicalPath == destFileCanonicalPath)
                return FileUtilsErrno.ERRNO_COPYING_OR_MOVING_FILE_TO_SAME_PATH.getError("$mode $lbl" + "source file", srcFilePath, destFilePath)

            if (destFileType != FileType.NO_EXIST) {
                if (!overwrite) {
                    return null
                }

                if (overwriteOnlyIfDestSameFileTypeAsSrc && destFileType != srcFileType)
                    return FileUtilsErrno.ERRNO_CANNOT_OVERWRITE_A_DIFFERENT_FILE_TYPE.getError(lbl + "source file", mode.lowercase(), srcFilePath, destFilePath, destFileType.name, srcFileType.name)

                val error = deleteFile(lbl + "destination", destFilePath, true)
                if (error != null) return error
            }

            var copyFileFlag = !moveFile

            if (moveFile) {
                Logger.logVerbose(LOG_TAG, "Attempting to rename source to destination.")

                if (!srcFile.renameTo(destFile)) {
                    if (srcFileType == FileType.DIRECTORY && destFileCanonicalPath.startsWith(srcFileCanonicalPath + File.separator))
                        return FileUtilsErrno.ERRNO_CANNOT_MOVE_DIRECTORY_TO_SUB_DIRECTORY_OF_ITSELF.getError(lbl + "source directory", srcFilePath, destFilePath)

                    Logger.logVerbose(LOG_TAG, "Renaming $lbl" + "source file to destination failed, attempting to copy.")
                    copyFileFlag = true
                }
            }

            if (copyFileFlag) {
                Logger.logVerbose(LOG_TAG, "Attempting to copy source to destination.")

                val error = createParentDirectoryFile(lbl + "dest file parent", destFilePath)
                if (error != null) return error

                when (srcFileType) {
                    FileType.DIRECTORY -> {
                        org.apache.commons.io.FileUtils.copyDirectory(srcFile, destFile, true)
                    }
                    FileType.SYMLINK -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            java.nio.file.Files.copy(srcFile.toPath(), destFile.toPath(), LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING)
                        } else {
                            val symlinkError = createSymlinkFile(lbl + "dest", Os.readlink(srcFilePath), destFilePath)
                            if (symlinkError != null) return symlinkError
                        }
                    }
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            java.nio.file.Files.copy(srcFile.toPath(), destFile.toPath(), LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING)
                        } else {
                            org.apache.commons.io.FileUtils.copyFile(srcFile, destFile, true)
                        }
                    }
                }
            }

            if (moveFile) {
                val error = deleteFile(lbl + "source", srcFilePath, true)
                if (error != null) return error
            }

            Logger.logVerbose(LOG_TAG, "$mode successful.")
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_COPYING_OR_MOVING_FILE_FAILED_WITH_EXCEPTION.getError(e, "$mode $lbl" + "file", srcFilePath, destFilePath, e.message)
        }

        return null
    }

    /**
     * Delete regular file at path.
     */
    @JvmStatic
    fun deleteRegularFile(label: String?, filePath: String, ignoreNonExistentFile: Boolean): Error? {
        return deleteFile(label, filePath, ignoreNonExistentFile, false, FileType.REGULAR.value)
    }

    /**
     * Delete directory file at path.
     */
    @JvmStatic
    fun deleteDirectoryFile(label: String?, filePath: String, ignoreNonExistentFile: Boolean): Error? {
        return deleteFile(label, filePath, ignoreNonExistentFile, false, FileType.DIRECTORY.value)
    }

    /**
     * Delete symlink file at path.
     */
    @JvmStatic
    fun deleteSymlinkFile(label: String?, filePath: String, ignoreNonExistentFile: Boolean): Error? {
        return deleteFile(label, filePath, ignoreNonExistentFile, false, FileType.SYMLINK.value)
    }

    /**
     * Delete socket file at path.
     */
    @JvmStatic
    fun deleteSocketFile(label: String?, filePath: String, ignoreNonExistentFile: Boolean): Error? {
        return deleteFile(label, filePath, ignoreNonExistentFile, false, FileType.SOCKET.value)
    }

    /**
     * Delete regular, directory or symlink file at path.
     */
    @JvmStatic
    fun deleteFile(label: String?, filePath: String, ignoreNonExistentFile: Boolean): Error? {
        return deleteFile(label, filePath, ignoreNonExistentFile, false, FileTypes.FILE_TYPE_NORMAL_FLAGS)
    }

    /**
     * Delete file at path.
     */
    @JvmStatic
    fun deleteFile(
        label: String?,
        filePath: String?,
        ignoreNonExistentFile: Boolean,
        ignoreWrongFileType: Boolean,
        allowedFileTypeFlags: Int
    ): Error? {
        var lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "file path", "deleteFile")

        try {
            val file = File(filePath)
            var fileType = getFileType(filePath, false)

            Logger.logVerbose(LOG_TAG, "Processing delete of $lbl" + "file at path \"$filePath\" of type \"${fileType.name}\"")

            if (fileType == FileType.NO_EXIST) {
                return if (ignoreNonExistentFile) {
                    null
                } else {
                    lbl += "file meant to be deleted"
                    FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(lbl, filePath).setLabel(lbl)
                }
            }

            if ((allowedFileTypeFlags and fileType.value) <= 0) {
                if (ignoreWrongFileType) {
                    Logger.logVerbose(LOG_TAG, "Ignoring deletion of $lbl" + "file at path \"$filePath\" of type \"${fileType.name}\" not matching allowed file types: ${FileTypes.convertFileTypeFlagsToNamesString(allowedFileTypeFlags)}")
                    return null
                }

                return FileUtilsErrno.ERRNO_FILE_NOT_AN_ALLOWED_FILE_TYPE.getError(lbl + "file meant to be deleted", filePath, fileType.name, FileTypes.convertFileTypeFlagsToNamesString(allowedFileTypeFlags))
            }

            Logger.logVerbose(LOG_TAG, "Deleting $lbl" + "file at path \"$filePath\"")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("UnstableApiUsage")
                com.google.common.io.MoreFiles.deleteRecursively(file.toPath(), RecursiveDeleteOption.ALLOW_INSECURE)
            } else {
                if (fileType == FileType.DIRECTORY) {
                    org.apache.commons.io.FileUtils.deleteDirectory(file)
                } else {
                    org.apache.commons.io.FileUtils.forceDelete(file)
                }
            }

            fileType = getFileType(filePath, false)
            if (fileType != FileType.NO_EXIST)
                return FileUtilsErrno.ERRNO_FILE_STILL_EXISTS_AFTER_DELETING.getError(lbl + "file meant to be deleted", filePath)
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_DELETING_FILE_FAILED_WITH_EXCEPTION.getError(e, lbl + "file", filePath, e.message)
        }

        return null
    }

    /**
     * Clear contents of directory at path without deleting the directory.
     */
    @JvmStatic
    fun clearDirectory(filePath: String): Error? {
        return clearDirectory(null, filePath)
    }

    @JvmStatic
    fun clearDirectory(label: String?, filePath: String?): Error? {
        val lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "file path", "clearDirectory")

        try {
            Logger.logVerbose(LOG_TAG, "Clearing $lbl" + "directory at path \"$filePath\"")

            val file = File(filePath)
            val fileType = getFileType(filePath, false)

            if (fileType != FileType.NO_EXIST && fileType != FileType.DIRECTORY) {
                return FileUtilsErrno.ERRNO_NON_DIRECTORY_FILE_FOUND.getError(lbl + "directory", filePath).setLabel(lbl + "directory")
            }

            if (fileType == FileType.DIRECTORY) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    @Suppress("UnstableApiUsage")
                    com.google.common.io.MoreFiles.deleteDirectoryContents(file.toPath(), RecursiveDeleteOption.ALLOW_INSECURE)
                } else {
                    org.apache.commons.io.FileUtils.cleanDirectory(File(filePath))
                }
            } else {
                val error = createDirectoryFile(label, filePath)
                if (error != null) return error
            }
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_CLEARING_DIRECTORY_FAILED_WITH_EXCEPTION.getError(e, lbl + "directory", filePath, e.message)
        }

        return null
    }

    /**
     * Delete files under a directory older than x days.
     */
    @JvmStatic
    fun deleteFilesOlderThanXDays(
        label: String?,
        filePath: String?,
        dirFilter: IOFileFilter?,
        days: Int,
        ignoreNonExistentFile: Boolean,
        allowedFileTypeFlags: Int
    ): Error? {
        var lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "file path", "deleteFilesOlderThanXDays")
        if (days < 0) return FunctionErrno.ERRNO_INVALID_PARAMETER.getError(lbl + "days", "deleteFilesOlderThanXDays", " It must be >= 0.")

        try {
            Logger.logVerbose(LOG_TAG, "Deleting files under $lbl" + "directory at path \"$filePath\" older than $days days")

            val file = File(filePath)
            val fileType = getFileType(filePath, false)

            if (fileType != FileType.NO_EXIST && fileType != FileType.DIRECTORY) {
                return FileUtilsErrno.ERRNO_NON_DIRECTORY_FILE_FOUND.getError(lbl + "directory", filePath).setLabel(lbl + "directory")
            }

            if (fileType == FileType.NO_EXIST) {
                return if (ignoreNonExistentFile) {
                    null
                } else {
                    lbl += "directory under which files had to be deleted"
                    FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(lbl, filePath).setLabel(lbl)
                }
            }

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DATE, -days)
            val filesToDelete = org.apache.commons.io.FileUtils.iterateFiles(file, AgeFileFilter(calendar.time), dirFilter)
            while (filesToDelete.hasNext()) {
                val subFile = filesToDelete.next()
                val error = deleteFile(lbl + " directory sub", subFile.absolutePath, true, true, allowedFileTypeFlags)
                if (error != null) return error
            }
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_DELETING_FILES_OLDER_THAN_X_DAYS_FAILED_WITH_EXCEPTION.getError(e, lbl + "directory", filePath, days, e.message)
        }

        return null
    }

    /**
     * Read a text String from file at path.
     */
    @JvmStatic
    fun readTextFromFile(
        label: String?,
        filePath: String?,
        charset: Charset?,
        dataStringBuilder: StringBuilder,
        ignoreNonExistentFile: Boolean
    ): Error? {
        var lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "file path", "readStringFromFile")

        Logger.logVerbose(LOG_TAG, "Reading text from $lbl" + "file at path \"$filePath\"")

        val fileType = getFileType(filePath, false)

        if (fileType != FileType.NO_EXIST && fileType != FileType.REGULAR) {
            return FileUtilsErrno.ERRNO_NON_REGULAR_FILE_FOUND.getError(lbl + "file", filePath).setLabel(lbl + "file")
        }

        if (fileType == FileType.NO_EXIST) {
            return if (ignoreNonExistentFile) {
                null
            } else {
                lbl += "file meant to be read"
                FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(lbl, filePath).setLabel(lbl)
            }
        }

        val cs = charset ?: Charset.defaultCharset()

        val error = isCharsetSupported(cs)
        if (error != null) return error

        var fileInputStream: FileInputStream? = null
        var bufferedReader: BufferedReader? = null
        try {
            fileInputStream = FileInputStream(filePath)
            bufferedReader = BufferedReader(InputStreamReader(fileInputStream, cs))

            var firstLine = true
            var receiveString: String?
            while (bufferedReader.readLine().also { receiveString = it } != null) {
                if (!firstLine) dataStringBuilder.append("\n") else firstLine = false
                dataStringBuilder.append(receiveString)
            }

            Logger.logVerbose(LOG_TAG, Logger.getMultiLineLogStringEntry("String", DataUtils.getTruncatedCommandOutput(dataStringBuilder.toString(), Logger.LOGGER_ENTRY_MAX_SAFE_PAYLOAD, true, false, true), "-"))
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_READING_TEXT_FROM_FILE_FAILED_WITH_EXCEPTION.getError(e, lbl + "file", filePath, e.message)
        } finally {
            closeCloseable(fileInputStream)
            closeCloseable(bufferedReader)
        }

        return null
    }

    class ReadSerializableObjectResult(
        @JvmField val error: Error?,
        @JvmField val serializableObject: Serializable?
    )

    /**
     * Read a Serializable object from file at path.
     */
    @JvmStatic
    fun <T : Serializable> readSerializableObjectFromFile(
        label: String?,
        filePath: String?,
        readObjectType: Class<T>,
        ignoreNonExistentFile: Boolean
    ): ReadSerializableObjectResult {
        var lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return ReadSerializableObjectResult(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "file path", "readSerializableObjectFromFile"), null)

        Logger.logVerbose(LOG_TAG, "Reading serializable object from $lbl" + "file at path \"$filePath\"")

        val fileType = getFileType(filePath, false)

        if (fileType != FileType.NO_EXIST && fileType != FileType.REGULAR) {
            return ReadSerializableObjectResult(FileUtilsErrno.ERRNO_NON_REGULAR_FILE_FOUND.getError(lbl + "file", filePath).setLabel(lbl + "file"), null)
        }

        if (fileType == FileType.NO_EXIST) {
            return if (ignoreNonExistentFile) {
                ReadSerializableObjectResult(null, null)
            } else {
                lbl += "file meant to be read"
                ReadSerializableObjectResult(FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(lbl, filePath).setLabel(lbl), null)
            }
        }

        var fileInputStream: FileInputStream? = null
        var objectInputStream: ObjectInputStream? = null
        val serializableObject: T
        try {
            fileInputStream = FileInputStream(filePath)
            objectInputStream = ObjectInputStream(fileInputStream)
            serializableObject = readObjectType.cast(objectInputStream.readObject())!!
        } catch (e: Exception) {
            return ReadSerializableObjectResult(FileUtilsErrno.ERRNO_READING_SERIALIZABLE_OBJECT_TO_FILE_FAILED_WITH_EXCEPTION.getError(e, lbl + "file", filePath, e.message), null)
        } finally {
            closeCloseable(fileInputStream)
            closeCloseable(objectInputStream)
        }

        return ReadSerializableObjectResult(null, serializableObject)
    }

    /**
     * Write text dataString to file at path.
     */
    @JvmStatic
    fun writeTextToFile(
        label: String?,
        filePath: String?,
        charset: Charset?,
        dataString: String?,
        append: Boolean
    ): Error? {
        val lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "file path", "writeStringToFile")

        Logger.logVerbose(LOG_TAG, Logger.getMultiLineLogStringEntry("Writing text to $lbl" + "file at path \"$filePath\"", DataUtils.getTruncatedCommandOutput(dataString, Logger.LOGGER_ENTRY_MAX_SAFE_PAYLOAD, true, false, true), "-"))

        var error = preWriteToFile(lbl, filePath)
        if (error != null) return error

        val cs = charset ?: Charset.defaultCharset()

        error = isCharsetSupported(cs)
        if (error != null) return error

        var fileOutputStream: FileOutputStream? = null
        var bufferedWriter: BufferedWriter? = null
        try {
            fileOutputStream = FileOutputStream(filePath, append)
            bufferedWriter = BufferedWriter(OutputStreamWriter(fileOutputStream, cs))

            bufferedWriter.write(dataString)
            bufferedWriter.flush()
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_WRITING_TEXT_TO_FILE_FAILED_WITH_EXCEPTION.getError(e, lbl + "file", filePath, e.message)
        } finally {
            closeCloseable(fileOutputStream)
            closeCloseable(bufferedWriter)
        }

        return null
    }

    /**
     * Write the Serializable serializableObject to file at path.
     */
    @JvmStatic
    fun <T : Serializable> writeSerializableObjectToFile(label: String?, filePath: String?, serializableObject: T): Error? {
        val lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "file path", "writeSerializableObjectToFile")

        Logger.logVerbose(LOG_TAG, "Writing serializable object to $lbl" + "file at path \"$filePath\"")

        val error = preWriteToFile(lbl, filePath)
        if (error != null) return error

        var fileOutputStream: FileOutputStream? = null
        var objectOutputStream: ObjectOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(filePath)
            objectOutputStream = ObjectOutputStream(fileOutputStream)

            objectOutputStream.writeObject(serializableObject)
            objectOutputStream.flush()
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_WRITING_SERIALIZABLE_OBJECT_TO_FILE_FAILED_WITH_EXCEPTION.getError(e, lbl + "file", filePath, e.message)
        } finally {
            closeCloseable(fileOutputStream)
            closeCloseable(objectOutputStream)
        }

        return null
    }

    private fun preWriteToFile(label: String, filePath: String): Error? {
        val fileType = getFileType(filePath, false)

        if (fileType != FileType.NO_EXIST && fileType != FileType.REGULAR) {
            return FileUtilsErrno.ERRNO_NON_REGULAR_FILE_FOUND.getError(label + "file", filePath).setLabel(label + "file")
        }

        return createParentDirectoryFile(label + "file parent", filePath)
    }

    /**
     * Check if a specific Charset is supported.
     */
    @JvmStatic
    fun isCharsetSupported(charset: Charset?): Error? {
        if (charset == null) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError("charset", "isCharsetSupported")

        try {
            if (!Charset.isSupported(charset.name())) {
                return FileUtilsErrno.ERRNO_UNSUPPORTED_CHARSET.getError(charset.name())
            }
        } catch (e: Exception) {
            return FileUtilsErrno.ERRNO_CHECKING_IF_CHARSET_SUPPORTED_FAILED.getError(e, charset.name(), e.message)
        }

        return null
    }

    /**
     * Close a Closeable object if not null and ignore any exceptions raised.
     */
    @JvmStatic
    fun closeCloseable(closeable: Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (e: IOException) {
                // ignore
            }
        }
    }

    /**
     * Set permissions for file at path.
     */
    @JvmStatic
    fun setFilePermissions(filePath: String, permissionsToSet: String) {
        setFilePermissions(null, filePath, permissionsToSet)
    }

    @JvmStatic
    fun setFilePermissions(label: String?, filePath: String?, permissionsToSet: String) {
        val lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return

        if (!isValidPermissionString(permissionsToSet)) {
            Logger.logError(LOG_TAG, "Invalid permissionsToSet passed to setFilePermissions: \"$permissionsToSet\"")
            return
        }

        val file = File(filePath)

        if (permissionsToSet.contains("r")) {
            if (!file.canRead()) {
                Logger.logVerbose(LOG_TAG, "Setting read permissions for $lbl" + "file at path \"$filePath\"")
                file.setReadable(true)
            }
        } else {
            if (file.canRead()) {
                Logger.logVerbose(LOG_TAG, "Removing read permissions for $lbl" + "file at path \"$filePath\"")
                file.setReadable(false)
            }
        }

        if (permissionsToSet.contains("w")) {
            if (!file.canWrite()) {
                Logger.logVerbose(LOG_TAG, "Setting write permissions for $lbl" + "file at path \"$filePath\"")
                file.setWritable(true)
            }
        } else {
            if (file.canWrite()) {
                Logger.logVerbose(LOG_TAG, "Removing write permissions for $lbl" + "file at path \"$filePath\"")
                file.setWritable(false)
            }
        }

        if (permissionsToSet.contains("x")) {
            if (!file.canExecute()) {
                Logger.logVerbose(LOG_TAG, "Setting execute permissions for $lbl" + "file at path \"$filePath\"")
                file.setExecutable(true)
            }
        } else {
            if (file.canExecute()) {
                Logger.logVerbose(LOG_TAG, "Removing execute permissions for $lbl" + "file at path \"$filePath\"")
                file.setExecutable(false)
            }
        }
    }

    /**
     * Set missing permissions for file at path.
     */
    @JvmStatic
    fun setMissingFilePermissions(filePath: String, permissionsToSet: String) {
        setMissingFilePermissions(null, filePath, permissionsToSet)
    }

    @JvmStatic
    fun setMissingFilePermissions(label: String?, filePath: String?, permissionsToSet: String) {
        val lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return

        if (!isValidPermissionString(permissionsToSet)) {
            Logger.logError(LOG_TAG, "Invalid permissionsToSet passed to setMissingFilePermissions: \"$permissionsToSet\"")
            return
        }

        val file = File(filePath)

        if (permissionsToSet.contains("r") && !file.canRead()) {
            Logger.logVerbose(LOG_TAG, "Setting missing read permissions for $lbl" + "file at path \"$filePath\"")
            file.setReadable(true)
        }

        if (permissionsToSet.contains("w") && !file.canWrite()) {
            Logger.logVerbose(LOG_TAG, "Setting missing write permissions for $lbl" + "file at path \"$filePath\"")
            file.setWritable(true)
        }

        if (permissionsToSet.contains("x") && !file.canExecute()) {
            Logger.logVerbose(LOG_TAG, "Setting missing execute permissions for $lbl" + "file at path \"$filePath\"")
            file.setExecutable(true)
        }
    }

    /**
     * Checking missing permissions for file at path.
     */
    @JvmStatic
    fun checkMissingFilePermissions(filePath: String, permissionsToCheck: String, ignoreIfNotExecutable: Boolean): Error? {
        return checkMissingFilePermissions(null, filePath, permissionsToCheck, ignoreIfNotExecutable)
    }

    @JvmStatic
    fun checkMissingFilePermissions(label: String?, filePath: String?, permissionsToCheck: String, ignoreIfNotExecutable: Boolean): Error? {
        val lbl = if (label.isNullOrEmpty()) "" else "$label "
        if (filePath.isNullOrEmpty()) return FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(lbl + "file path", "checkMissingFilePermissions")

        if (!isValidPermissionString(permissionsToCheck)) {
            Logger.logError(LOG_TAG, "Invalid permissionsToCheck passed to checkMissingFilePermissions: \"$permissionsToCheck\"")
            return FileUtilsErrno.ERRNO_INVALID_FILE_PERMISSIONS_STRING_TO_CHECK.getError()
        }

        val file = File(filePath)

        if (permissionsToCheck.contains("r") && !file.canRead()) {
            return FileUtilsErrno.ERRNO_FILE_NOT_READABLE.getError(lbl + "file", filePath).setLabel(lbl + "file")
        }

        if (permissionsToCheck.contains("w") && !file.canWrite()) {
            return FileUtilsErrno.ERRNO_FILE_NOT_WRITABLE.getError(lbl + "file", filePath).setLabel(lbl + "file")
        } else if (permissionsToCheck.contains("x") && !file.canExecute() && !ignoreIfNotExecutable) {
            return FileUtilsErrno.ERRNO_FILE_NOT_EXECUTABLE.getError(lbl + "file", filePath).setLabel(lbl + "file")
        }

        return null
    }

    /**
     * Checks whether string exactly matches the 3 character permission string.
     */
    @JvmStatic
    fun isValidPermissionString(string: String?): Boolean {
        if (string.isNullOrEmpty()) return false
        return Pattern.compile("^([r-])[w-][x-]$", 0).matcher(string).matches()
    }

    /**
     * Get a Error that contains a shorter version of Errno message.
     */
    @JvmStatic
    fun getShortFileUtilsError(error: Error): Error {
        val type = error.type
        if (FileUtilsErrno.TYPE != type) return error

        val shortErrno = FileUtilsErrno.ERRNO_SHORT_MAPPING[Errno.valueOf(type, error.code)]
            ?: return error

        val throwables = error.getThrowablesList()
        return if (throwables.isEmpty())
            shortErrno.getError(DataUtils.getDefaultIfNull(error.getLabel(), "file"))
        else
            shortErrno.getError(throwables, error.getLabel(), "file")
    }

    /**
     * Get file dirname for file at filePath.
     */
    @JvmStatic
    fun getFileDirname(filePath: String?): String? {
        if (DataUtils.isNullOrEmpty(filePath)) return null
        val lastSlash = filePath!!.lastIndexOf('/')
        return if (lastSlash == -1) null else filePath.substring(0, lastSlash)
    }

    /**
     * Get file basename for file at filePath.
     */
    @JvmStatic
    fun getFileBasename(filePath: String?): String? {
        if (DataUtils.isNullOrEmpty(filePath)) return null
        val lastSlash = filePath!!.lastIndexOf('/')
        return if (lastSlash == -1) filePath else filePath.substring(lastSlash + 1)
    }

    /**
     * Get file basename for file at filePath without extension.
     */
    @JvmStatic
    fun getFileBasenameWithoutExtension(filePath: String?): String? {
        val fileBasename = getFileBasename(filePath)
        if (DataUtils.isNullOrEmpty(fileBasename)) return null
        val lastDot = fileBasename!!.lastIndexOf('.')
        return if (lastDot == -1) fileBasename else fileBasename.substring(0, lastDot)
    }
}
