package com.termux.shared.termux.shell

import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.file.filesystem.FileTypes
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
import org.apache.commons.io.filefilter.TrueFileFilter
import java.io.File
import java.io.FileInputStream

object TermuxShellUtils {

    private const val LOG_TAG = "TermuxShellUtils"

    /**
     * Setup shell command arguments for the execute. The file interpreter may be prefixed to
     * command arguments if needed.
     */
    @JvmStatic
    fun setupShellCommandArguments(executable: String, arguments: Array<String>?): Array<String> {
        // The file to execute may either be:
        // - An elf file, in which we execute it directly.
        // - A script file without shebang, which we execute with our standard shell $PREFIX/bin/sh instead of the
        //   system /system/bin/sh. The system shell may vary and may not work at all due to LD_LIBRARY_PATH.
        // - A file with shebang, which we try to handle with e.g. /bin/foo -> $PREFIX/bin/foo.
        var interpreter: String? = null
        try {
            val file = File(executable)
            FileInputStream(file).use { input ->
                val buffer = ByteArray(256)
                val bytesRead = input.read(buffer)
                if (bytesRead > 4) {
                    when {
                        buffer[0] == 0x7F.toByte() && buffer[1] == 'E'.code.toByte() && 
                        buffer[2] == 'L'.code.toByte() && buffer[3] == 'F'.code.toByte() -> {
                            // Elf file, do nothing.
                        }
                        buffer[0] == '#'.code.toByte() && buffer[1] == '!'.code.toByte() -> {
                            // Try to parse shebang.
                            val builder = StringBuilder()
                            for (i in 2 until bytesRead) {
                                val c = buffer[i].toInt().toChar()
                                if (c == ' ' || c == '\n') {
                                    if (builder.isEmpty()) {
                                        // Skip whitespace after shebang.
                                    } else {
                                        // End of shebang.
                                        val shebangExecutable = builder.toString()
                                        if (shebangExecutable.startsWith("/usr") || shebangExecutable.startsWith("/bin")) {
                                            val parts = shebangExecutable.split("/")
                                            val binary = parts.last()
                                            interpreter = "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/$binary"
                                        }
                                        break
                                    }
                                } else {
                                    builder.append(c)
                                }
                            }
                        }
                        else -> {
                            // No shebang and no ELF, use standard shell.
                            interpreter = "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/sh"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore.
        }

        val result = mutableListOf<String>()
        interpreter?.let { result.add(it) }
        result.add(executable)
        arguments?.let { result.addAll(it) }
        return result.toTypedArray()
    }

    /** Clear files under [TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH]. */
    @JvmStatic
    fun clearTermuxTMPDIR(onlyIfExists: Boolean) {
        // Existence check before clearing may be required since clearDirectory() will automatically
        // re-create empty directory if doesn't exist, which should not be done for things like
        // termux-reset (d6eb5e35). Moreover, TMPDIR must be a directory and not a symlink, this can
        // also allow users who don't want TMPDIR to be cleared automatically on termux exit, since
        // it may remove files still being used by background processes (#1159).
        if (onlyIfExists && !FileUtils.directoryFileExists(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, false)) {
            return
        }

        var error: Error?

        val properties = TermuxAppSharedProperties.getProperties() ?: return
        var days = properties.getDeleteTMPDIRFilesOlderThanXDaysOnExit()

        // Disable currently until FileUtils.deleteFilesOlderThanXDays() is fixed.
        if (days > 0) {
            days = 0
        }

        when {
            days < 0 -> {
                Logger.logInfo(LOG_TAG, "Not clearing termux \$TMPDIR")
            }
            days == 0 -> {
                error = FileUtils.clearDirectory(
                    "\$TMPDIR",
                    FileUtils.getCanonicalPath(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, null)
                )
                if (error != null) {
                    Logger.logErrorExtended(LOG_TAG, "Failed to clear termux \$TMPDIR\n$error")
                }
            }
            else -> {
                error = FileUtils.deleteFilesOlderThanXDays(
                    "\$TMPDIR",
                    FileUtils.getCanonicalPath(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, null),
                    TrueFileFilter.INSTANCE, days, true, FileTypes.FILE_TYPE_ANY_FLAGS
                )
                if (error != null) {
                    Logger.logErrorExtended(LOG_TAG, "Failed to delete files from termux \$TMPDIR older than $days days\n$error")
                }
            }
        }
    }
}
