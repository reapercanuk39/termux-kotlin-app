/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Represents the value of a file's time stamp attribute. For example, it may
 * represent the time that the file was last modified, accessed, or created.
 *
 * Instances of this class are immutable.
 *
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/java/nio/file/attribute/FileTime.java
 *
 * @since 1.7
 */
class FileTime private constructor(
    private val value: Long,
    private val unit: TimeUnit
) {

    /**
     * Returns the value at the given unit of granularity.
     *
     * Conversion from a coarser granularity that would numerically overflow
     * saturate to Long.MIN_VALUE if negative or Long.MAX_VALUE if positive.
     *
     * @param unit the unit of granularity for the return value
     * @return value in the given unit of granularity, since the epoch (1970-01-01T00:00:00Z)
     */
    fun to(unit: TimeUnit): Long {
        return unit.convert(this.value, this.unit)
    }

    /**
     * Returns the value in milliseconds.
     *
     * Conversion from a coarser granularity that would numerically overflow
     * saturate to Long.MIN_VALUE if negative or Long.MAX_VALUE if positive.
     *
     * @return the value in milliseconds, since the epoch (1970-01-01T00:00:00Z)
     */
    fun toMillis(): Long {
        return unit.toMillis(value)
    }

    override fun toString(): String {
        return getDate(toMillis(), "yyyy.MM.dd HH:mm:ss.SSS z")
    }

    companion object {
        /**
         * Returns a FileTime representing a value at the given unit of granularity.
         *
         * @param value the value since the epoch (1970-01-01T00:00:00Z); can be negative
         * @param unit the unit of granularity to interpret the value
         * @return a FileTime representing the given value
         */
        @JvmStatic
        fun from(value: Long, unit: TimeUnit): FileTime {
            return FileTime(value, unit)
        }

        /**
         * Returns a FileTime representing the given value in milliseconds.
         *
         * @param value the value, in milliseconds, since the epoch (1970-01-01T00:00:00Z); can be negative
         * @return a FileTime representing the given value
         */
        @JvmStatic
        fun fromMillis(value: Long): FileTime {
            return FileTime(value, TimeUnit.MILLISECONDS)
        }

        @JvmStatic
        fun getDate(milliSeconds: Long, format: String): String {
            return try {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = milliSeconds
                SimpleDateFormat(format).format(calendar.time)
            } catch (e: Exception) {
                milliSeconds.toString()
            }
        }
    }
}
