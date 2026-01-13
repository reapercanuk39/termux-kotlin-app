/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
 *
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
 *
 */
// AUTOMATICALLY GENERATED FILE - DO NOT EDIT
package com.termux.shared.file.filesystem

// BEGIN Android-changed: Use constants from android.system.OsConstants. http://b/32203242
// Those constants are initialized by native code to ensure correctness on different architectures.
// AT_SYMLINK_NOFOLLOW (used by fstatat) and AT_REMOVEDIR (used by unlinkat) as of July 2018 do not
// have equivalents in android.system.OsConstants so left unchanged.
import android.system.OsConstants

/**
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/sun/nio/fs/UnixConstants.java
 */
object UnixConstants {

    @JvmField val O_RDONLY = OsConstants.O_RDONLY
    @JvmField val O_WRONLY = OsConstants.O_WRONLY
    @JvmField val O_RDWR = OsConstants.O_RDWR
    @JvmField val O_APPEND = OsConstants.O_APPEND
    @JvmField val O_CREAT = OsConstants.O_CREAT
    @JvmField val O_EXCL = OsConstants.O_EXCL
    @JvmField val O_TRUNC = OsConstants.O_TRUNC
    @JvmField val O_SYNC = OsConstants.O_SYNC

    // Crash on Android 5.
    // No static field O_DSYNC of type I in class Landroid/system/OsConstants; or its superclasses
    // (declaration of 'android.system.OsConstants' appears in /system/framework/core-libart.jar)
    //@JvmField val O_DSYNC = OsConstants.O_DSYNC

    @JvmField val O_NOFOLLOW = OsConstants.O_NOFOLLOW

    @JvmField val S_IAMB = get_S_IAMB()

    @JvmField val S_IRUSR = OsConstants.S_IRUSR
    @JvmField val S_IWUSR = OsConstants.S_IWUSR
    @JvmField val S_IXUSR = OsConstants.S_IXUSR

    @JvmField val S_IRGRP = OsConstants.S_IRGRP
    @JvmField val S_IWGRP = OsConstants.S_IWGRP
    @JvmField val S_IXGRP = OsConstants.S_IXGRP

    @JvmField val S_IROTH = OsConstants.S_IROTH
    @JvmField val S_IWOTH = OsConstants.S_IWOTH
    @JvmField val S_IXOTH = OsConstants.S_IXOTH

    @JvmField val S_IFMT = OsConstants.S_IFMT
    @JvmField val S_IFREG = OsConstants.S_IFREG
    @JvmField val S_IFDIR = OsConstants.S_IFDIR
    @JvmField val S_IFLNK = OsConstants.S_IFLNK
    @JvmField val S_IFSOCK = OsConstants.S_IFSOCK
    @JvmField val S_IFCHR = OsConstants.S_IFCHR
    @JvmField val S_IFBLK = OsConstants.S_IFBLK
    @JvmField val S_IFIFO = OsConstants.S_IFIFO

    @JvmField val R_OK = OsConstants.R_OK
    @JvmField val W_OK = OsConstants.W_OK
    @JvmField val X_OK = OsConstants.X_OK
    @JvmField val F_OK = OsConstants.F_OK

    @JvmField val ENOENT = OsConstants.ENOENT
    @JvmField val EACCES = OsConstants.EACCES
    @JvmField val EEXIST = OsConstants.EEXIST
    @JvmField val ENOTDIR = OsConstants.ENOTDIR
    @JvmField val EINVAL = OsConstants.EINVAL
    @JvmField val EXDEV = OsConstants.EXDEV
    @JvmField val EISDIR = OsConstants.EISDIR
    @JvmField val ENOTEMPTY = OsConstants.ENOTEMPTY
    @JvmField val ENOSPC = OsConstants.ENOSPC
    @JvmField val EAGAIN = OsConstants.EAGAIN
    @JvmField val ENOSYS = OsConstants.ENOSYS
    @JvmField val ELOOP = OsConstants.ELOOP
    @JvmField val EROFS = OsConstants.EROFS
    @JvmField val ENODATA = OsConstants.ENODATA
    @JvmField val ERANGE = OsConstants.ERANGE
    @JvmField val EMFILE = OsConstants.EMFILE

    // S_IAMB are access mode bits, therefore, calculated by taking OR of all the read, write and
    // execute permissions bits for owner, group and other.
    private fun get_S_IAMB(): Int {
        return (OsConstants.S_IRUSR or OsConstants.S_IWUSR or OsConstants.S_IXUSR or
            OsConstants.S_IRGRP or OsConstants.S_IWGRP or OsConstants.S_IXGRP or
            OsConstants.S_IROTH or OsConstants.S_IWOTH or OsConstants.S_IXOTH)
    }
    // END Android-changed: Use constants from android.system.OsConstants. http://b/32203242

    @JvmField val AT_SYMLINK_NOFOLLOW = 0x100
    @JvmField val AT_REMOVEDIR = 0x200
}
