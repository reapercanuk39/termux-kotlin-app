package com.termux.shared.file.filesystem

import android.system.Os
import android.system.ErrnoException
import java.io.File
import java.io.FileDescriptor
import java.io.IOException

object NativeDispatcher {

    @JvmStatic
    @Throws(IOException::class)
    fun stat(filePath: String?, fileAttributes: FileAttributes) {
        validateFileExistence(filePath)
        try {
            fileAttributes.loadFromStructStat(Os.stat(filePath))
        } catch (e: ErrnoException) {
            throw IOException("Failed to run Os.stat() on file at path \"$filePath\": ${e.message}")
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun lstat(filePath: String?, fileAttributes: FileAttributes) {
        validateFileExistence(filePath)
        try {
            fileAttributes.loadFromStructStat(Os.lstat(filePath))
        } catch (e: ErrnoException) {
            throw IOException("Failed to run Os.lstat() on file at path \"$filePath\": ${e.message}")
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fstat(fileDescriptor: FileDescriptor?, fileAttributes: FileAttributes) {
        validateFileDescriptor(fileDescriptor)
        try {
            fileAttributes.loadFromStructStat(Os.fstat(fileDescriptor))
        } catch (e: ErrnoException) {
            throw IOException("Failed to run Os.fstat() on file descriptor \"$fileDescriptor\": ${e.message}")
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun validateFileExistence(filePath: String?) {
        if (filePath.isNullOrEmpty()) throw IOException("The path is null or empty")
        // File existence check commented out in original
    }

    @JvmStatic
    @Throws(IOException::class)
    fun validateFileDescriptor(fileDescriptor: FileDescriptor?) {
        if (fileDescriptor == null) throw IOException("The file descriptor is null")
        if (!fileDescriptor.valid()) {
            throw IOException("No such file descriptor: \"$fileDescriptor\"")
        }
    }
}
