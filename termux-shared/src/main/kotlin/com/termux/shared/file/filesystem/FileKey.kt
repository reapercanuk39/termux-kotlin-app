/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Container for device/inode to uniquely identify file.
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/sun/nio/fs/UnixFileKey.java
 */
data class FileKey internal constructor(
    private val st_dev: Long,
    private val st_ino: Long
) {
    override fun hashCode(): Int {
        return (st_dev xor (st_dev ushr 32)).toInt() +
            (st_ino xor (st_ino ushr 32)).toInt()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is FileKey) return false
        return st_dev == other.st_dev && st_ino == other.st_ino
    }

    override fun toString(): String {
        return "(dev=${st_dev.toString(16)},ino=$st_ino)"
    }
}
