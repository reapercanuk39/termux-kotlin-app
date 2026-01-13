package com.termux.shared.data

import android.os.Bundle
import com.google.common.base.Strings
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable

object DataUtils {

    /** Max safe limit of data size to prevent TransactionTooLargeException. */
    const val TRANSACTION_SIZE_LIMIT_IN_BYTES = 100 * 1024 // 100KB

    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

    @JvmStatic
    fun getTruncatedCommandOutput(text: String?, maxLength: Int, fromEnd: Boolean, onNewline: Boolean, addPrefix: Boolean): String? {
        if (text == null) return null

        val prefix = "(truncated) "
        var effectiveMaxLength = maxLength

        if (addPrefix) {
            effectiveMaxLength = maxLength - prefix.length
        }

        if (effectiveMaxLength < 0 || text.length < effectiveMaxLength) return text

        var result = if (fromEnd) {
            text.substring(0, effectiveMaxLength)
        } else {
            var cutOffIndex = text.length - effectiveMaxLength

            if (onNewline) {
                val nextNewlineIndex = text.indexOf('\n', cutOffIndex)
                if (nextNewlineIndex != -1 && nextNewlineIndex != text.length - 1) {
                    cutOffIndex = nextNewlineIndex + 1
                }
            }
            text.substring(cutOffIndex)
        }

        if (addPrefix) {
            result = prefix + result
        }

        return result
    }

    /**
     * Replace a sub string in each item of a [Array]<[String]>.
     */
    @JvmStatic
    fun replaceSubStringsInStringArrayItems(array: Array<String>?, find: String, replace: String) {
        if (array.isNullOrEmpty()) return

        for (i in array.indices) {
            array[i] = array[i].replace(find, replace)
        }
    }

    /**
     * Get the float from a [String].
     */
    @JvmStatic
    fun getFloatFromString(value: String?, def: Float): Float {
        if (value == null) return def

        return try {
            value.toFloat()
        } catch (e: Exception) {
            def
        }
    }

    /**
     * Get the int from a [String].
     */
    @JvmStatic
    fun getIntFromString(value: String?, def: Int): Int {
        if (value == null) return def

        return try {
            value.toInt()
        } catch (e: Exception) {
            def
        }
    }

    /**
     * Get the [String] from an [Int]?.
     */
    @JvmStatic
    fun getStringFromInteger(value: Int?, def: String?): String? {
        return value?.toString() ?: def
    }

    /**
     * Get the hex string from a [ByteArray].
     */
    @JvmStatic
    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = HEX_ARRAY[v ushr 4]
            hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars)
    }

    /**
     * Get an int from [Bundle] that is stored as a [String].
     */
    @JvmStatic
    fun getIntStoredAsStringFromBundle(bundle: Bundle?, key: String?, def: Int): Int {
        if (bundle == null) return def
        return getIntFromString(bundle.getString(key, def.toString()), def)
    }

    /**
     * If value is not in the range [min, max], set it to either min or max.
     */
    @JvmStatic
    fun clamp(value: Int, min: Int, max: Int): Int {
        return value.coerceIn(min, max)
    }

    /**
     * If value is not in the range [min, max], set it to default.
     */
    @JvmStatic
    fun rangedOrDefault(value: Float, def: Float, min: Float, max: Float): Float {
        return if (value < min || value > max) def else value
    }

    /**
     * Add a space indent to a [String]. Each indent is 4 space characters long.
     */
    @JvmStatic
    fun getSpaceIndentedString(string: String?, count: Int): String? {
        return if (string.isNullOrEmpty()) string else getIndentedString(string, "    ", count)
    }

    /**
     * Add a tab indent to a [String]. Each indent is 1 tab character long.
     */
    @JvmStatic
    fun getTabIndentedString(string: String?, count: Int): String? {
        return if (string.isNullOrEmpty()) string else getIndentedString(string, "\t", count)
    }

    /**
     * Add an indent to a [String].
     */
    @JvmStatic
    fun getIndentedString(string: String?, indent: String, count: Int): String? {
        return if (string.isNullOrEmpty()) string
        else string.replace(Regex("(?m)^"), Strings.repeat(indent, count.coerceAtLeast(1)))
    }

    /**
     * Get the object itself if it is not null, otherwise default.
     */
    @JvmStatic
    fun <T> getDefaultIfNull(obj: T?, def: T?): T? {
        return obj ?: def
    }

    /**
     * Get the [String] itself if it is not null or empty, otherwise default.
     */
    @JvmStatic
    fun getDefaultIfUnset(value: String?, def: String?): String? {
        return if (value.isNullOrEmpty()) def else value
    }

    /** Check if a string is null or empty. */
    @JvmStatic
    fun isNullOrEmpty(string: String?): Boolean {
        return string.isNullOrEmpty()
    }

    /** Get size of a serializable object. */
    @JvmStatic
    fun getSerializedSize(obj: Serializable?): Long {
        if (obj == null) return 0
        return try {
            val byteOutputStream = ByteArrayOutputStream()
            val objectOutputStream = ObjectOutputStream(byteOutputStream)
            objectOutputStream.writeObject(obj)
            objectOutputStream.flush()
            objectOutputStream.close()
            byteOutputStream.toByteArray().size.toLong()
        } catch (e: Exception) {
            -1
        }
    }
}
