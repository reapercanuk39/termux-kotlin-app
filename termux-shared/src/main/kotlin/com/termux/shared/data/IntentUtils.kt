package com.termux.shared.data

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable

object IntentUtils {

    @Suppress("unused")
    private const val LOG_TAG = "IntentUtils"

    /**
     * Get a [String] extra from an [Intent] if its not null or empty.
     *
     * @param intent The [Intent] to get the extra from.
     * @param key The [String] key name.
     * @param def The default value if extra is not set.
     * @param throwExceptionIfNotSet If set to `true`, then an exception will be thrown if extra
     *                               is not set.
     * @return Returns the [String] extra if set, otherwise null.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun getStringExtraIfSet(intent: Intent, key: String?, def: String?, throwExceptionIfNotSet: Boolean): String? {
        val value = getStringExtraIfSet(intent, key, def)
        if (value == null && throwExceptionIfNotSet) {
            throw Exception("The \"$key\" key string value is null or empty")
        }
        return value
    }

    /**
     * Get a [String] extra from an [Intent] if its not null or empty.
     *
     * @param intent The [Intent] to get the extra from.
     * @param key The [String] key name.
     * @param def The default value if extra is not set.
     * @return Returns the [String] extra if set, otherwise null.
     */
    @JvmStatic
    fun getStringExtraIfSet(intent: Intent, key: String?, def: String?): String? {
        val value = intent.getStringExtra(key)
        if (value.isNullOrEmpty()) {
            return if (!def.isNullOrEmpty()) def else null
        }
        return value
    }

    /**
     * Get an [Int] from an [Intent] stored as a [String] extra if its not
     * null or empty.
     *
     * @param intent The [Intent] to get the extra from.
     * @param key The [String] key name.
     * @param def The default value if extra is not set.
     * @return Returns the [Int] extra if set, otherwise null.
     */
    @JvmStatic
    fun getIntegerExtraIfSet(intent: Intent, key: String?, def: Int?): Int? {
        return try {
            val value = intent.getStringExtra(key)
            if (value.isNullOrEmpty()) {
                def
            } else {
                value.toInt()
            }
        } catch (e: Exception) {
            def
        }
    }

    /**
     * Get a [Array]<[String]> extra from an [Intent] if its not null or empty.
     *
     * @param intent The [Intent] to get the extra from.
     * @param key The [String] key name.
     * @param def The default value if extra is not set.
     * @param throwExceptionIfNotSet If set to `true`, then an exception will be thrown if extra
     *                               is not set.
     * @return Returns the [Array]<[String]> extra if set, otherwise null.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun getStringArrayExtraIfSet(intent: Intent, key: String?, def: Array<String>?, throwExceptionIfNotSet: Boolean): Array<String>? {
        val value = getStringArrayExtraIfSet(intent, key, def)
        if (value == null && throwExceptionIfNotSet) {
            throw Exception("The \"$key\" key string array is null or empty")
        }
        return value
    }

    /**
     * Get a [Array]<[String]> extra from an [Intent] if its not null or empty.
     *
     * @param intent The [Intent] to get the extra from.
     * @param key The [String] key name.
     * @param def The default value if extra is not set.
     * @return Returns the [Array]<[String]> extra if set, otherwise null.
     */
    @JvmStatic
    fun getStringArrayExtraIfSet(intent: Intent?, key: String?, def: Array<String>?): Array<String>? {
        val value = intent?.getStringArrayExtra(key)
        if (value == null || value.isEmpty()) {
            return if (def != null && def.isNotEmpty()) def else null
        }
        return value
    }

    @JvmStatic
    fun getIntentString(intent: Intent?): String? {
        if (intent == null) return null
        return "${intent}\n${getBundleString(intent.extras)}"
    }

    @JvmStatic
    fun getBundleString(bundle: Bundle?): String {
        if (bundle == null || bundle.size() == 0) return "Bundle[]"

        val bundleString = StringBuilder("Bundle[\n")
        var first = true
        for (key in bundle.keySet()) {
            if (!first) {
                bundleString.append("\n")
            }

            bundleString.append(key).append(": `")

            @Suppress("DEPRECATION")
            val value = bundle.get(key)
            when (value) {
                is IntArray -> bundleString.append(value.contentToString())
                is ByteArray -> bundleString.append(value.contentToString())
                is BooleanArray -> bundleString.append(value.contentToString())
                is ShortArray -> bundleString.append(value.contentToString())
                is LongArray -> bundleString.append(value.contentToString())
                is FloatArray -> bundleString.append(value.contentToString())
                is DoubleArray -> bundleString.append(value.contentToString())
                is Array<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    when {
                        value.isArrayOf<String>() -> bundleString.append((value as Array<String>).contentToString())
                        value.isArrayOf<CharSequence>() -> bundleString.append((value as Array<CharSequence>).contentToString())
                        value.isArrayOf<Parcelable>() -> bundleString.append((value as Array<Parcelable>).contentToString())
                        else -> bundleString.append(value.contentToString())
                    }
                }
                is Bundle -> bundleString.append(getBundleString(value))
                else -> bundleString.append(value)
            }

            bundleString.append("`")
            first = false
        }

        bundleString.append("\n]")
        return bundleString.toString()
    }
}
