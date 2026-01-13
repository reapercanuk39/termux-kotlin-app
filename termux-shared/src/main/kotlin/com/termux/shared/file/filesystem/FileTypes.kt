package com.termux.shared.file.filesystem

import com.termux.shared.logger.Logger

object FileTypes {

    /** Flags to represent regular, directory and symlink file types defined by [FileType] */
    @JvmField
    val FILE_TYPE_NORMAL_FLAGS = FileType.REGULAR.value or FileType.DIRECTORY.value or FileType.SYMLINK.value

    /** Flags to represent any file type defined by [FileType] */
    @JvmField
    val FILE_TYPE_ANY_FLAGS = Int.MAX_VALUE // 1111111111111111111111111111111 (31 1's)

    @JvmStatic
    fun convertFileTypeFlagsToNamesString(fileTypeFlags: Int): String {
        val fileTypeFlagsStringBuilder = StringBuilder()

        val fileTypes = arrayOf(
            FileType.REGULAR, FileType.DIRECTORY, FileType.SYMLINK,
            FileType.CHARACTER, FileType.FIFO, FileType.BLOCK, FileType.UNKNOWN
        )
        for (fileType in fileTypes) {
            if ((fileTypeFlags and fileType.value) > 0) {
                fileTypeFlagsStringBuilder.append(fileType.name).append(",")
            }
        }

        var fileTypeFlagsString = fileTypeFlagsStringBuilder.toString()

        if (fileTypeFlagsString.endsWith(",")) {
            fileTypeFlagsString = fileTypeFlagsString.substring(0, fileTypeFlagsString.lastIndexOf(","))
        }

        return fileTypeFlagsString
    }

    /**
     * Checks the type of file that exists at [filePath].
     *
     * @param filePath The path for file to check.
     * @param followLinks The boolean that decides if symlinks will be followed while
     *                    finding type. If set to true, then type of symlink target will
     *                    be returned if file at filePath is a symlink. If set to
     *                    false, then type of file at filePath itself will be returned.
     * @return Returns the [FileType] of file.
     */
    @JvmStatic
    fun getFileType(filePath: String?, followLinks: Boolean): FileType {
        if (filePath.isNullOrEmpty()) return FileType.NO_EXIST

        return try {
            val fileAttributes = FileAttributes.get(filePath, followLinks)
            getFileType(fileAttributes)
        } catch (e: Exception) {
            // If not a ENOENT (No such file or directory) exception
            if (e.message != null && !e.message!!.contains("ENOENT")) {
                Logger.logError("Failed to get file type for file at path \"$filePath\": ${e.message}")
            }
            FileType.NO_EXIST
        }
    }

    @JvmStatic
    fun getFileType(fileAttributes: FileAttributes): FileType {
        return when {
            fileAttributes.isRegularFile -> FileType.REGULAR
            fileAttributes.isDirectory -> FileType.DIRECTORY
            fileAttributes.isSymbolicLink -> FileType.SYMLINK
            fileAttributes.isSocket -> FileType.SOCKET
            fileAttributes.isCharacter -> FileType.CHARACTER
            fileAttributes.isFifo -> FileType.FIFO
            fileAttributes.isBlock -> FileType.BLOCK
            else -> FileType.UNKNOWN
        }
    }
}
