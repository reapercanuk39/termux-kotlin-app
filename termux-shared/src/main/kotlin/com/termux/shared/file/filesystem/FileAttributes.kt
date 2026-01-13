/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.termux.shared.file.filesystem

import android.os.Build
import android.system.StructStat
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Unix implementation of PosixFileAttributes.
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/sun/nio/fs/UnixFileAttributes.java
 */
class FileAttributes private constructor(
    private val filePath: String?,
    private val fileDescriptor: FileDescriptor?
) {
    private var st_mode: Int = 0
    private var st_ino: Long = 0
    private var st_dev: Long = 0
    private var st_rdev: Long = 0
    private var st_nlink: Long = 0
    private var st_uid: Int = 0
    private var st_gid: Int = 0
    private var st_size: Long = 0
    private var st_blksize: Long = 0
    private var st_blocks: Long = 0
    private var st_atime_sec: Long = 0
    private var st_atime_nsec: Long = 0
    private var st_mtime_sec: Long = 0
    private var st_mtime_nsec: Long = 0
    private var st_ctime_sec: Long = 0
    private var st_ctime_nsec: Long = 0

    // created lazily
    @Volatile private var owner: String? = null
    @Volatile private var group: String? = null
    @Volatile private var key: FileKey? = null

    private constructor(filePath: String?) : this(filePath, null)
    private constructor(fileDescriptor: FileDescriptor?) : this(null, fileDescriptor)

    fun file(): String? = filePath ?: fileDescriptor?.toString()

    fun isSameFile(attrs: FileAttributes): Boolean =
        st_ino == attrs.st_ino && st_dev == attrs.st_dev

    fun mode(): Int = st_mode
    fun blksize(): Long = st_blksize
    fun blocks(): Long = st_blocks
    fun ino(): Long = st_ino
    fun dev(): Long = st_dev
    fun rdev(): Long = st_rdev
    fun nlink(): Long = st_nlink
    fun uid(): Int = st_uid
    fun gid(): Int = st_gid
    fun size(): Long = st_size

    fun lastAccessTime(): FileTime = toFileTime(st_atime_sec, st_atime_nsec)
    fun lastModifiedTime(): FileTime = toFileTime(st_mtime_sec, st_mtime_nsec)
    fun lastChangeTime(): FileTime = toFileTime(st_ctime_sec, st_ctime_nsec)
    fun creationTime(): FileTime = lastModifiedTime()

    val isRegularFile: Boolean
        get() = (st_mode and UnixConstants.S_IFMT) == UnixConstants.S_IFREG

    val isDirectory: Boolean
        get() = (st_mode and UnixConstants.S_IFMT) == UnixConstants.S_IFDIR

    val isSymbolicLink: Boolean
        get() = (st_mode and UnixConstants.S_IFMT) == UnixConstants.S_IFLNK

    val isCharacter: Boolean
        get() = (st_mode and UnixConstants.S_IFMT) == UnixConstants.S_IFCHR

    val isFifo: Boolean
        get() = (st_mode and UnixConstants.S_IFMT) == UnixConstants.S_IFIFO

    val isSocket: Boolean
        get() = (st_mode and UnixConstants.S_IFMT) == UnixConstants.S_IFSOCK

    val isBlock: Boolean
        get() = (st_mode and UnixConstants.S_IFMT) == UnixConstants.S_IFBLK

    val isOther: Boolean
        get() {
            val type = st_mode and UnixConstants.S_IFMT
            return type != UnixConstants.S_IFREG &&
                   type != UnixConstants.S_IFDIR &&
                   type != UnixConstants.S_IFLNK
        }

    val isDevice: Boolean
        get() {
            val type = st_mode and UnixConstants.S_IFMT
            return type == UnixConstants.S_IFCHR ||
                   type == UnixConstants.S_IFBLK ||
                   type == UnixConstants.S_IFIFO
        }

    fun fileKey(): FileKey {
        if (key == null) {
            synchronized(this) {
                if (key == null) {
                    key = FileKey(st_dev, st_ino)
                }
            }
        }
        return key!!
    }

    fun owner(): String {
        if (owner == null) {
            synchronized(this) {
                if (owner == null) {
                    owner = st_uid.toString()
                }
            }
        }
        return owner!!
    }

    fun group(): String {
        if (group == null) {
            synchronized(this) {
                if (group == null) {
                    group = st_gid.toString()
                }
            }
        }
        return group!!
    }

    fun permissions(): Set<FilePermission> {
        val bits = st_mode and UnixConstants.S_IAMB
        val perms = HashSet<FilePermission>()

        if ((bits and UnixConstants.S_IRUSR) > 0) perms.add(FilePermission.OWNER_READ)
        if ((bits and UnixConstants.S_IWUSR) > 0) perms.add(FilePermission.OWNER_WRITE)
        if ((bits and UnixConstants.S_IXUSR) > 0) perms.add(FilePermission.OWNER_EXECUTE)

        if ((bits and UnixConstants.S_IRGRP) > 0) perms.add(FilePermission.GROUP_READ)
        if ((bits and UnixConstants.S_IWGRP) > 0) perms.add(FilePermission.GROUP_WRITE)
        if ((bits and UnixConstants.S_IXGRP) > 0) perms.add(FilePermission.GROUP_EXECUTE)

        if ((bits and UnixConstants.S_IROTH) > 0) perms.add(FilePermission.OTHERS_READ)
        if ((bits and UnixConstants.S_IWOTH) > 0) perms.add(FilePermission.OTHERS_WRITE)
        if ((bits and UnixConstants.S_IXOTH) > 0) perms.add(FilePermission.OTHERS_EXECUTE)

        return perms
    }

    fun loadFromStructStat(structStat: StructStat) {
        st_mode = structStat.st_mode
        st_ino = structStat.st_ino
        st_dev = structStat.st_dev
        st_rdev = structStat.st_rdev
        st_nlink = structStat.st_nlink
        st_uid = structStat.st_uid
        st_gid = structStat.st_gid
        st_size = structStat.st_size
        st_blksize = structStat.st_blksize
        st_blocks = structStat.st_blocks

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            st_atime_sec = structStat.st_atim.tv_sec
            st_atime_nsec = structStat.st_atim.tv_nsec
            st_mtime_sec = structStat.st_mtim.tv_sec
            st_mtime_nsec = structStat.st_mtim.tv_nsec
            st_ctime_sec = structStat.st_ctim.tv_sec
            st_ctime_nsec = structStat.st_ctim.tv_nsec
        } else {
            @Suppress("DEPRECATION")
            st_atime_sec = structStat.st_atime
            st_atime_nsec = 0
            @Suppress("DEPRECATION")
            st_mtime_sec = structStat.st_mtime
            st_mtime_nsec = 0
            @Suppress("DEPRECATION")
            st_ctime_sec = structStat.st_ctime
            st_ctime_nsec = 0
        }
    }

    fun getFileString(): String = "File: `${file()}`"
    fun getTypeString(): String = "Type: `${FileTypes.getFileType(this).name}`"
    fun getSizeString(): String = "Size: `${size()}`"
    fun getBlocksString(): String = "Blocks: `${blocks()}`"
    fun getIOBlockString(): String = "IO Block: `${blksize()}`"
    fun getDeviceString(): String = "Device: `${java.lang.Long.toHexString(st_dev)}`"
    fun getInodeString(): String = "Inode: `$st_ino`"
    fun getLinksString(): String = "Links: `${nlink()}`"
    fun getDeviceTypeString(): String = "Device Type: `${rdev()}`"
    fun getOwnerString(): String = "Owner: `${owner()}`"
    fun getGroupString(): String = "Group: `${group()}`"
    fun getPermissionString(): String = "Permissions: `${FilePermissions.toString(permissions())}`"
    fun getAccessTimeString(): String = "Access Time: `${lastAccessTime()}`"
    fun getModifiedTimeString(): String = "Modified Time: `${lastModifiedTime()}`"
    fun getChangeTimeString(): String = "Change Time: `${lastChangeTime()}`"

    override fun toString(): String = getFileAttributesLogString(this)

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun get(filePath: String?, followLinks: Boolean): FileAttributes {
            val fileAttributes = if (filePath.isNullOrEmpty()) {
                FileAttributes(null as String?)
            } else {
                FileAttributes(File(filePath).absolutePath)
            }

            if (followLinks) {
                NativeDispatcher.stat(filePath, fileAttributes)
            } else {
                NativeDispatcher.lstat(filePath, fileAttributes)
            }

            return fileAttributes
        }

        @JvmStatic
        @Throws(IOException::class)
        fun get(fileDescriptor: FileDescriptor): FileAttributes {
            val fileAttributes = FileAttributes(fileDescriptor)
            NativeDispatcher.fstat(fileDescriptor, fileAttributes)
            return fileAttributes
        }

        private fun toFileTime(sec: Long, nsec: Long): FileTime {
            return if (nsec == 0L) {
                FileTime.from(sec, TimeUnit.SECONDS)
            } else {
                // truncate to microseconds to avoid overflow with timestamps
                // way out into the future. We can re-visit this if FileTime
                // is updated to define a from(secs,nsecs) method.
                val micro = sec * 1000000L + nsec / 1000L
                FileTime.from(micro, TimeUnit.MICROSECONDS)
            }
        }

        @JvmStatic
        fun getFileAttributesLogString(fileAttributes: FileAttributes?): String {
            if (fileAttributes == null) return "null"

            return buildString {
                append(fileAttributes.getFileString())
                append("\n").append(fileAttributes.getTypeString())
                append("\n").append(fileAttributes.getSizeString())
                append("\n").append(fileAttributes.getBlocksString())
                append("\n").append(fileAttributes.getIOBlockString())
                append("\n").append(fileAttributes.getDeviceString())
                append("\n").append(fileAttributes.getInodeString())
                append("\n").append(fileAttributes.getLinksString())

                if (fileAttributes.isBlock || fileAttributes.isCharacter)
                    append("\n").append(fileAttributes.getDeviceTypeString())

                append("\n").append(fileAttributes.getOwnerString())
                append("\n").append(fileAttributes.getGroupString())
                append("\n").append(fileAttributes.getPermissionString())
                append("\n").append(fileAttributes.getAccessTimeString())
                append("\n").append(fileAttributes.getModifiedTimeString())
                append("\n").append(fileAttributes.getChangeTimeString())
            }
        }
    }
}
