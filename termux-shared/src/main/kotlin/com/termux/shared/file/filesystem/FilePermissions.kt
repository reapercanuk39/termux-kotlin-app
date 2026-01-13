/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet

/**
 * This class consists exclusively of static methods that operate on sets of
 * [FilePermission] objects.
 *
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/java/nio/file/attribute/PosixFilePermissions.java
 *
 * @since 1.7
 */
object FilePermissions {

    // Write string representation of permission bits to sb.
    private fun writeBits(sb: StringBuilder, r: Boolean, w: Boolean, x: Boolean) {
        sb.append(if (r) 'r' else '-')
        sb.append(if (w) 'w' else '-')
        sb.append(if (x) 'x' else '-')
    }

    /**
     * Returns the String representation of a set of permissions. It
     * is guaranteed that the returned String can be parsed by the
     * [fromString] method.
     *
     * If the set contains null or elements that are not of type
     * FilePermission then these elements are ignored.
     *
     * @param perms the set of permissions
     * @return the string representation of the permission set
     */
    @JvmStatic
    fun toString(perms: Set<FilePermission>): String {
        val sb = StringBuilder(9)
        writeBits(sb, perms.contains(FilePermission.OWNER_READ), perms.contains(FilePermission.OWNER_WRITE),
            perms.contains(FilePermission.OWNER_EXECUTE))
        writeBits(sb, perms.contains(FilePermission.GROUP_READ), perms.contains(FilePermission.GROUP_WRITE),
            perms.contains(FilePermission.GROUP_EXECUTE))
        writeBits(sb, perms.contains(FilePermission.OTHERS_READ), perms.contains(FilePermission.OTHERS_WRITE),
            perms.contains(FilePermission.OTHERS_EXECUTE))
        return sb.toString()
    }

    private fun isSet(c: Char, setValue: Char): Boolean {
        if (c == setValue) return true
        if (c == '-') return false
        throw IllegalArgumentException("Invalid mode")
    }

    private fun isR(c: Char): Boolean = isSet(c, 'r')
    private fun isW(c: Char): Boolean = isSet(c, 'w')
    private fun isX(c: Char): Boolean = isSet(c, 'x')

    /**
     * Returns the set of permissions corresponding to a given String representation.
     *
     * The perms parameter is a String representing the permissions. It has 9 characters
     * that are interpreted as three sets of three. The first set refers to the owner's
     * permissions; the next to the group permissions and the last to others. Within each
     * set, the first character is 'r' to indicate permission to read, the second character
     * is 'w' to indicate permission to write, and the third character is 'x' for execute
     * permission. Where a permission is not set then the corresponding character is set to '-'.
     *
     * @param perms string representing a set of permissions
     * @return the resulting set of permissions
     * @throws IllegalArgumentException if the string cannot be converted to a set of permissions
     */
    @JvmStatic
    fun fromString(perms: String): Set<FilePermission> {
        if (perms.length != 9) throw IllegalArgumentException("Invalid mode")
        val result = EnumSet.noneOf(FilePermission::class.java)
        if (isR(perms[0])) result.add(FilePermission.OWNER_READ)
        if (isW(perms[1])) result.add(FilePermission.OWNER_WRITE)
        if (isX(perms[2])) result.add(FilePermission.OWNER_EXECUTE)
        if (isR(perms[3])) result.add(FilePermission.GROUP_READ)
        if (isW(perms[4])) result.add(FilePermission.GROUP_WRITE)
        if (isX(perms[5])) result.add(FilePermission.GROUP_EXECUTE)
        if (isR(perms[6])) result.add(FilePermission.OTHERS_READ)
        if (isW(perms[7])) result.add(FilePermission.OTHERS_WRITE)
        if (isX(perms[8])) result.add(FilePermission.OTHERS_EXECUTE)
        return result
    }
}
