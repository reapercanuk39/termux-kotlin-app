package com.termux.shared.file.filesystem

/**
 * The enum that defines file types.
 */
enum class FileType(
    @JvmField val typeName: String,
    @JvmField val value: Int
) {
    NO_EXIST("no exist", 0),       // 00000000
    REGULAR("regular", 1),         // 00000001
    DIRECTORY("directory", 2),     // 00000010
    SYMLINK("symlink", 4),         // 00000100
    SOCKET("socket", 8),           // 00001000
    CHARACTER("character", 16),    // 00010000
    FIFO("fifo", 32),              // 00100000
    BLOCK("block", 64),            // 01000000
    UNKNOWN("unknown", 128);       // 10000000

    // For Java interop
    fun getName(): String = typeName
    fun getValue(): Int = value
}
