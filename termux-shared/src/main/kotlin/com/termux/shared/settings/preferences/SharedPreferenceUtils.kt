package com.termux.shared.settings.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.termux.shared.logger.Logger

object SharedPreferenceUtils {

    private const val LOG_TAG = "SharedPreferenceUtils"

    /**
     * Get [SharedPreferences] instance of the preferences file 'name' with the operating mode
     * [Context.MODE_PRIVATE]. This file will be created in the app package's default
     * shared preferences directory.
     *
     * @param context The [Context] to get the [SharedPreferences] instance.
     * @param name The preferences file basename without extension.
     * @return The single [SharedPreferences] instance that can be used to retrieve and
     * modify the preference values.
     */
    @JvmStatic
    fun getPrivateSharedPreferences(context: Context, name: String): SharedPreferences {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    /**
     * Get [SharedPreferences] instance of the preferences file 'name' with the operating mode
     * [Context.MODE_PRIVATE] and [Context.MODE_MULTI_PROCESS]. This file will be
     * created in the app package's default shared preferences directory.
     *
     * @param context The [Context] to get the [SharedPreferences] instance.
     * @param name The preferences file basename without extension.
     * @return The single [SharedPreferences] instance that can be used to retrieve and
     * modify the preference values.
     */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun getPrivateAndMultiProcessSharedPreferences(context: Context, name: String): SharedPreferences {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS)
    }

    /**
     * Get a `boolean` from [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to get the value from.
     * @param key The key for the value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `boolean` value stored in [SharedPreferences], otherwise returns
     * default if failed to read a valid value, like in case of an exception.
     */
    @JvmStatic
    fun getBoolean(sharedPreferences: SharedPreferences?, key: String, def: Boolean): Boolean {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Error getting boolean value for the \"$key\" key from null shared preferences. Returning default value \"$def\".")
            return def
        }

        return try {
            sharedPreferences.getBoolean(key, def)
        } catch (e: ClassCastException) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error getting boolean value for the \"$key\" key from shared preferences. Returning default value \"$def\".", e)
            def
        }
    }

    /**
     * Set a `boolean` in [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to set the value in.
     * @param key The key for the value.
     * @param value The value to store.
     * @param commitToFile If set to `true`, then value will be set to shared preferences
     *                     in-memory cache and the file synchronously. Ideally, only to be used for
     *                     multi-process use-cases.
     */
    @JvmStatic
    @SuppressLint("ApplySharedPref")
    fun setBoolean(sharedPreferences: SharedPreferences?, key: String, value: Boolean, commitToFile: Boolean) {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Ignoring setting boolean value \"$value\" for the \"$key\" key into null shared preferences.")
            return
        }

        if (commitToFile)
            sharedPreferences.edit().putBoolean(key, value).commit()
        else
            sharedPreferences.edit().putBoolean(key, value).apply()
    }

    /**
     * Get a `float` from [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to get the value from.
     * @param key The key for the value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `float` value stored in [SharedPreferences], otherwise returns
     * default if failed to read a valid value, like in case of an exception.
     */
    @JvmStatic
    fun getFloat(sharedPreferences: SharedPreferences?, key: String, def: Float): Float {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Error getting float value for the \"$key\" key from null shared preferences. Returning default value \"$def\".")
            return def
        }

        return try {
            sharedPreferences.getFloat(key, def)
        } catch (e: ClassCastException) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error getting float value for the \"$key\" key from shared preferences. Returning default value \"$def\".", e)
            def
        }
    }

    /**
     * Set a `float` in [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to set the value in.
     * @param key The key for the value.
     * @param value The value to store.
     * @param commitToFile If set to `true`, then value will be set to shared preferences
     *                     in-memory cache and the file synchronously. Ideally, only to be used for
     *                     multi-process use-cases.
     */
    @JvmStatic
    @SuppressLint("ApplySharedPref")
    fun setFloat(sharedPreferences: SharedPreferences?, key: String, value: Float, commitToFile: Boolean) {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Ignoring setting float value \"$value\" for the \"$key\" key into null shared preferences.")
            return
        }

        if (commitToFile)
            sharedPreferences.edit().putFloat(key, value).commit()
        else
            sharedPreferences.edit().putFloat(key, value).apply()
    }

    /**
     * Get an `int` from [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to get the value from.
     * @param key The key for the value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `int` value stored in [SharedPreferences], otherwise returns
     * default if failed to read a valid value, like in case of an exception.
     */
    @JvmStatic
    fun getInt(sharedPreferences: SharedPreferences?, key: String, def: Int): Int {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Error getting int value for the \"$key\" key from null shared preferences. Returning default value \"$def\".")
            return def
        }

        return try {
            sharedPreferences.getInt(key, def)
        } catch (e: ClassCastException) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error getting int value for the \"$key\" key from shared preferences. Returning default value \"$def\".", e)
            def
        }
    }

    /**
     * Set an `int` in [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to set the value in.
     * @param key The key for the value.
     * @param value The value to store.
     * @param commitToFile If set to `true`, then value will be set to shared preferences
     *                     in-memory cache and the file synchronously. Ideally, only to be used for
     *                     multi-process use-cases.
     */
    @JvmStatic
    @SuppressLint("ApplySharedPref")
    fun setInt(sharedPreferences: SharedPreferences?, key: String, value: Int, commitToFile: Boolean) {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Ignoring setting int value \"$value\" for the \"$key\" key into null shared preferences.")
            return
        }

        if (commitToFile)
            sharedPreferences.edit().putInt(key, value).commit()
        else
            sharedPreferences.edit().putInt(key, value).apply()
    }

    /**
     * Set an `int` in [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to set the value in.
     * @param key The key for the value.
     * @param def The default value if failed to read a valid value.
     * @param commitToFile If set to `true`, then value will be set to shared preferences
     *                     in-memory cache and the file synchronously. Ideally, only to be used for
     *                     multi-process use-cases.
     * @param resetValue The value if not `null` that should be set if current or incremented
     *                   value is less than 0.
     * @return Returns the `int` value stored in [SharedPreferences] before increment,
     * otherwise returns default if failed to read a valid value, like in case of an exception.
     */
    @JvmStatic
    @SuppressLint("ApplySharedPref")
    fun getAndIncrementInt(sharedPreferences: SharedPreferences?, key: String, def: Int,
                           commitToFile: Boolean, resetValue: Int?): Int {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Ignoring incrementing int value for the \"$key\" key into null shared preferences.")
            return def
        }

        var curValue = getInt(sharedPreferences, key, def)
        if (resetValue != null && curValue < 0) curValue = resetValue

        var newValue = curValue + 1
        if (resetValue != null && newValue < 0) newValue = resetValue

        setInt(sharedPreferences, key, newValue, commitToFile)
        return curValue
    }

    /**
     * Get a `long` from [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to get the value from.
     * @param key The key for the value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `long` value stored in [SharedPreferences], otherwise returns
     * default if failed to read a valid value, like in case of an exception.
     */
    @JvmStatic
    fun getLong(sharedPreferences: SharedPreferences?, key: String, def: Long): Long {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Error getting long value for the \"$key\" key from null shared preferences. Returning default value \"$def\".")
            return def
        }

        return try {
            sharedPreferences.getLong(key, def)
        } catch (e: ClassCastException) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error getting long value for the \"$key\" key from shared preferences. Returning default value \"$def\".", e)
            def
        }
    }

    /**
     * Set a `long` in [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to set the value in.
     * @param key The key for the value.
     * @param value The value to store.
     * @param commitToFile If set to `true`, then value will be set to shared preferences
     *                     in-memory cache and the file synchronously. Ideally, only to be used for
     *                     multi-process use-cases.
     */
    @JvmStatic
    @SuppressLint("ApplySharedPref")
    fun setLong(sharedPreferences: SharedPreferences?, key: String, value: Long, commitToFile: Boolean) {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Ignoring setting long value \"$value\" for the \"$key\" key into null shared preferences.")
            return
        }

        if (commitToFile)
            sharedPreferences.edit().putLong(key, value).commit()
        else
            sharedPreferences.edit().putLong(key, value).apply()
    }

    /**
     * Get a `String` from [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to get the value from.
     * @param key The key for the value.
     * @param def The default value if failed to read a valid value.
     * @param defIfEmpty If set to `true`, then `def` will be returned if value is empty.
     * @return Returns the `String` value stored in [SharedPreferences], otherwise returns
     * default if failed to read a valid value, like in case of an exception.
     */
    @JvmStatic
    fun getString(sharedPreferences: SharedPreferences?, key: String, def: String?, defIfEmpty: Boolean): String? {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Error getting String value for the \"$key\" key from null shared preferences. Returning default value \"$def\".")
            return def
        }

        return try {
            val value = sharedPreferences.getString(key, def)
            if (defIfEmpty && (value == null || value.isEmpty()))
                def
            else
                value
        } catch (e: ClassCastException) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error getting String value for the \"$key\" key from shared preferences. Returning default value \"$def\".", e)
            def
        }
    }

    /**
     * Set a `String` in [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to set the value in.
     * @param key The key for the value.
     * @param value The value to store.
     * @param commitToFile If set to `true`, then value will be set to shared preferences
     *                     in-memory cache and the file synchronously. Ideally, only to be used for
     *                     multi-process use-cases.
     */
    @JvmStatic
    @SuppressLint("ApplySharedPref")
    fun setString(sharedPreferences: SharedPreferences?, key: String, value: String?, commitToFile: Boolean) {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Ignoring setting String value \"$value\" for the \"$key\" key into null shared preferences.")
            return
        }

        if (commitToFile)
            sharedPreferences.edit().putString(key, value).commit()
        else
            sharedPreferences.edit().putString(key, value).apply()
    }

    /**
     * Get a `Set<String>` from [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to get the value from.
     * @param key The key for the value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `Set<String>` value stored in [SharedPreferences], otherwise returns
     * default if failed to read a valid value, like in case of an exception.
     */
    @JvmStatic
    fun getStringSet(sharedPreferences: SharedPreferences?, key: String, def: Set<String>?): Set<String>? {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Error getting Set<String> value for the \"$key\" key from null shared preferences. Returning default value \"$def\".")
            return def
        }

        return try {
            sharedPreferences.getStringSet(key, def)
        } catch (e: ClassCastException) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error getting Set<String> value for the \"$key\" key from shared preferences. Returning default value \"$def\".", e)
            def
        }
    }

    /**
     * Set a `Set<String>` in [SharedPreferences].
     *
     * @param sharedPreferences The [SharedPreferences] to set the value in.
     * @param key The key for the value.
     * @param value The value to store.
     * @param commitToFile If set to `true`, then value will be set to shared preferences
     *                     in-memory cache and the file synchronously. Ideally, only to be used for
     *                     multi-process use-cases.
     */
    @JvmStatic
    @SuppressLint("ApplySharedPref")
    fun setStringSet(sharedPreferences: SharedPreferences?, key: String, value: Set<String>?, commitToFile: Boolean) {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Ignoring setting Set<String> value \"$value\" for the \"$key\" key into null shared preferences.")
            return
        }

        if (commitToFile)
            sharedPreferences.edit().putStringSet(key, value).commit()
        else
            sharedPreferences.edit().putStringSet(key, value).apply()
    }

    /**
     * Get an `int` from [SharedPreferences] that is stored as a [String].
     *
     * @param sharedPreferences The [SharedPreferences] to get the value from.
     * @param key The key for the value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `int` value after parsing the [String] value stored in
     * [SharedPreferences], otherwise returns default if failed to read a valid value,
     * like in case of an exception.
     */
    @JvmStatic
    fun getIntStoredAsString(sharedPreferences: SharedPreferences?, key: String, def: Int): Int {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Error getting int value for the \"$key\" key from null shared preferences. Returning default value \"$def\".")
            return def
        }

        return try {
            val stringValue = sharedPreferences.getString(key, def.toString())
            stringValue?.toIntOrNull() ?: def
        } catch (e: ClassCastException) {
            def
        }
    }

    /**
     * Set an `int` into [SharedPreferences] that is stored as a [String].
     *
     * @param sharedPreferences The [SharedPreferences] to set the value in.
     * @param key The key for the value.
     * @param value The value to store.
     * @param commitToFile If set to `true`, then value will be set to shared preferences
     *                     in-memory cache and the file synchronously. Ideally, only to be used for
     *                     multi-process use-cases.
     */
    @JvmStatic
    @SuppressLint("ApplySharedPref")
    fun setIntStoredAsString(sharedPreferences: SharedPreferences?, key: String, value: Int, commitToFile: Boolean) {
        if (sharedPreferences == null) {
            Logger.logError(LOG_TAG, "Ignoring setting int value \"$value\" for the \"$key\" key into null shared preferences.")
            return
        }

        if (commitToFile)
            sharedPreferences.edit().putString(key, value.toString()).commit()
        else
            sharedPreferences.edit().putString(key, value.toString()).apply()
    }
}
