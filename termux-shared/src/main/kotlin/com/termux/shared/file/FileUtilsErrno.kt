package com.termux.shared.file

import com.termux.shared.errors.Errno

/** The [Class] that defines FileUtils error messages and codes. */
open class FileUtilsErrno(type: String, code: Int, message: String) : Errno(type, code, message) {

    companion object {
        const val TYPE = "FileUtils Error"

        /* Errors for null or empty paths (100-150) */
        @JvmField val ERRNO_EXECUTABLE_REQUIRED = Errno(TYPE, 100, "Executable required.")
        @JvmField val ERRNO_NULL_OR_EMPTY_REGULAR_FILE_PATH = Errno(TYPE, 101, "The regular file path is null or empty.")
        @JvmField val ERRNO_NULL_OR_EMPTY_REGULAR_FILE = Errno(TYPE, 102, "The regular file is null or empty.")
        @JvmField val ERRNO_NULL_OR_EMPTY_EXECUTABLE_FILE_PATH = Errno(TYPE, 103, "The executable file path is null or empty.")
        @JvmField val ERRNO_NULL_OR_EMPTY_EXECUTABLE_FILE = Errno(TYPE, 104, "The executable file is null or empty.")
        @JvmField val ERRNO_NULL_OR_EMPTY_DIRECTORY_FILE_PATH = Errno(TYPE, 105, "The directory file path is null or empty.")
        @JvmField val ERRNO_NULL_OR_EMPTY_DIRECTORY_FILE = Errno(TYPE, 106, "The directory file is null or empty.")

        /* Errors for invalid or not found files at path (150-200) */
        @JvmField val ERRNO_FILE_NOT_FOUND_AT_PATH = Errno(TYPE, 150, "The %1\$s not found at path \"%2\$s\".")
        @JvmField val ERRNO_FILE_NOT_FOUND_AT_PATH_SHORT = Errno(TYPE, 151, "The %1\$s not found at path.")

        @JvmField val ERRNO_NON_REGULAR_FILE_FOUND = Errno(TYPE, 152, "Non-regular file found at %1\$s path \"%2\$s\".")
        @JvmField val ERRNO_NON_REGULAR_FILE_FOUND_SHORT = Errno(TYPE, 153, "Non-regular file found at %1\$s path.")
        @JvmField val ERRNO_NON_DIRECTORY_FILE_FOUND = Errno(TYPE, 154, "Non-directory file found at %1\$s path \"%2\$s\".")
        @JvmField val ERRNO_NON_DIRECTORY_FILE_FOUND_SHORT = Errno(TYPE, 155, "Non-directory file found at %1\$s path.")
        @JvmField val ERRNO_NON_SYMLINK_FILE_FOUND = Errno(TYPE, 156, "Non-symlink file found at %1\$s path \"%2\$s\".")
        @JvmField val ERRNO_NON_SYMLINK_FILE_FOUND_SHORT = Errno(TYPE, 157, "Non-symlink file found at %1\$s path.")

        @JvmField val ERRNO_FILE_NOT_AN_ALLOWED_FILE_TYPE = Errno(TYPE, 158, "The %1\$s found at path \"%2\$s\" of type \"%3\$s\" is not one of allowed file types \"%4\$s\".")
        @JvmField val ERRNO_NON_EMPTY_DIRECTORY_FILE = Errno(TYPE, 159, "The %1\$s directory at path \"%2\$s\" is not empty.")

        @JvmField val ERRNO_VALIDATE_FILE_EXISTENCE_AND_PERMISSIONS_FAILED_WITH_EXCEPTION = Errno(TYPE, 160, "Validating file existence and permissions of %1\$s at path \"%2\$s\" failed.\nException: %3\$s")
        @JvmField val ERRNO_VALIDATE_DIRECTORY_EXISTENCE_AND_PERMISSIONS_FAILED_WITH_EXCEPTION = Errno(TYPE, 161, "Validating directory existence and permissions of %1\$s at path \"%2\$s\" failed.\nException: %3\$s")
        @JvmField val ERRNO_VALIDATE_DIRECTORY_EMPTY_OR_ONLY_CONTAINS_SPECIFIC_FILES_FAILED_WITH_EXCEPTION = Errno(TYPE, 162, "Validating directory is empty or only contains specific files of %1\$s at path \"%2\$s\" failed.\nException: %3\$s")

        /* Errors for file creation (200-250) */
        @JvmField val ERRNO_CREATING_FILE_FAILED = Errno(TYPE, 200, "Creating %1\$s at path \"%2\$s\" failed.")
        @JvmField val ERRNO_CREATING_FILE_FAILED_WITH_EXCEPTION = Errno(TYPE, 201, "Creating %1\$s at path \"%2\$s\" failed.\nException: %3\$s")

        @JvmField val ERRNO_CANNOT_OVERWRITE_A_NON_SYMLINK_FILE_TYPE = Errno(TYPE, 202, "Cannot overwrite %1\$s while creating symlink at \"%2\$s\" to \"%3\$s\" since destination file type \"%4\$s\" is not a symlink.")
        @JvmField val ERRNO_CREATING_SYMLINK_FILE_FAILED_WITH_EXCEPTION = Errno(TYPE, 203, "Creating %1\$s at path \"%2\$s\" to \"%3\$s\" failed.\nException: %4\$s")

        /* Errors for file copying and moving (250-300) */
        @JvmField val ERRNO_COPYING_OR_MOVING_FILE_FAILED_WITH_EXCEPTION = Errno(TYPE, 250, "%1\$s from \"%2\$s\" to \"%3\$s\" failed.\nException: %4\$s")
        @JvmField val ERRNO_COPYING_OR_MOVING_FILE_TO_SAME_PATH = Errno(TYPE, 251, "%1\$s from \"%2\$s\" to \"%3\$s\" cannot be done since they point to the same path.")
        @JvmField val ERRNO_CANNOT_OVERWRITE_A_DIFFERENT_FILE_TYPE = Errno(TYPE, 252, "Cannot overwrite %1\$s while %2\$s it from \"%3\$s\" to \"%4\$s\" since destination file type \"%5\$s\" is different from source file type \"%6\$s\".")
        @JvmField val ERRNO_CANNOT_MOVE_DIRECTORY_TO_SUB_DIRECTORY_OF_ITSELF = Errno(TYPE, 253, "Cannot move %1\$s from \"%2\$s\" to \"%3\$s\" since destination is a subdirectory of the source.")

        /* Errors for file deletion (300-350) */
        @JvmField val ERRNO_DELETING_FILE_FAILED = Errno(TYPE, 300, "Deleting %1\$s at path \"%2\$s\" failed.")
        @JvmField val ERRNO_DELETING_FILE_FAILED_WITH_EXCEPTION = Errno(TYPE, 301, "Deleting %1\$s at path \"%2\$s\" failed.\nException: %3\$s")
        @JvmField val ERRNO_CLEARING_DIRECTORY_FAILED_WITH_EXCEPTION = Errno(TYPE, 302, "Clearing %1\$s at path \"%2\$s\" failed.\nException: %3\$s")
        @JvmField val ERRNO_FILE_STILL_EXISTS_AFTER_DELETING = Errno(TYPE, 303, "The %1\$s still exists after deleting it from \"%2\$s\".")
        @JvmField val ERRNO_DELETING_FILES_OLDER_THAN_X_DAYS_FAILED_WITH_EXCEPTION = Errno(TYPE, 304, "Deleting %1\$s under directory at path \"%2\$s\" old than %3\$s days failed.\nException: %4\$s")

        /* Errors for file reading and writing (350-400) */
        @JvmField val ERRNO_READING_TEXT_FROM_FILE_FAILED_WITH_EXCEPTION = Errno(TYPE, 350, "Reading text from %1\$s at path \"%2\$s\" failed.\nException: %3\$s")
        @JvmField val ERRNO_WRITING_TEXT_TO_FILE_FAILED_WITH_EXCEPTION = Errno(TYPE, 351, "Writing text to %1\$s at path \"%2\$s\" failed.\nException: %3\$s")
        @JvmField val ERRNO_UNSUPPORTED_CHARSET = Errno(TYPE, 352, "Unsupported charset \"%1\$s\"")
        @JvmField val ERRNO_CHECKING_IF_CHARSET_SUPPORTED_FAILED = Errno(TYPE, 353, "Checking if charset \"%1\$s\" is supported failed.\nException: %2\$s")
        @JvmField val ERRNO_GET_CHARSET_FOR_NAME_FAILED = Errno(TYPE, 354, "The \"%1\$s\" charset is not supported.\nException: %2\$s")
        @JvmField val ERRNO_READING_SERIALIZABLE_OBJECT_TO_FILE_FAILED_WITH_EXCEPTION = Errno(TYPE, 355, "Reading serializable object from %1\$s at path \"%2\$s\" failed.\nException: %3\$s")
        @JvmField val ERRNO_WRITING_SERIALIZABLE_OBJECT_TO_FILE_FAILED_WITH_EXCEPTION = Errno(TYPE, 356, "Writing serializable object to %1\$s at path \"%2\$s\" failed.\nException: %3\$s")

        /* Errors for invalid file permissions (400-450) */
        @JvmField val ERRNO_INVALID_FILE_PERMISSIONS_STRING_TO_CHECK = Errno(TYPE, 400, "The file permission string to check is invalid.")
        @JvmField val ERRNO_FILE_NOT_READABLE = Errno(TYPE, 401, "The %1\$s at path \"%2\$s\" is not readable. Permission Denied.")
        @JvmField val ERRNO_FILE_NOT_READABLE_SHORT = Errno(TYPE, 402, "The %1\$s at path is not readable. Permission Denied.")
        @JvmField val ERRNO_FILE_NOT_WRITABLE = Errno(TYPE, 403, "The %1\$s at path \"%2\$s\" is not writable. Permission Denied.")
        @JvmField val ERRNO_FILE_NOT_WRITABLE_SHORT = Errno(TYPE, 404, "The %1\$s at path is not writable. Permission Denied.")
        @JvmField val ERRNO_FILE_NOT_EXECUTABLE = Errno(TYPE, 405, "The %1\$s at path \"%2\$s\" is not executable. Permission Denied.")
        @JvmField val ERRNO_FILE_NOT_EXECUTABLE_SHORT = Errno(TYPE, 406, "The %1\$s at path is not executable. Permission Denied.")

        /** Defines the [Errno] mapping to get a shorter version of [FileUtilsErrno]. */
        @JvmField
        val ERRNO_SHORT_MAPPING: Map<Errno, Errno> = hashMapOf(
            ERRNO_FILE_NOT_FOUND_AT_PATH to ERRNO_FILE_NOT_FOUND_AT_PATH_SHORT,
            ERRNO_NON_REGULAR_FILE_FOUND to ERRNO_NON_REGULAR_FILE_FOUND_SHORT,
            ERRNO_NON_DIRECTORY_FILE_FOUND to ERRNO_NON_DIRECTORY_FILE_FOUND_SHORT,
            ERRNO_NON_SYMLINK_FILE_FOUND to ERRNO_NON_SYMLINK_FILE_FOUND_SHORT,
            ERRNO_FILE_NOT_READABLE to ERRNO_FILE_NOT_READABLE_SHORT,
            ERRNO_FILE_NOT_WRITABLE to ERRNO_FILE_NOT_WRITABLE_SHORT,
            ERRNO_FILE_NOT_EXECUTABLE to ERRNO_FILE_NOT_EXECUTABLE_SHORT
        )
    }
}
