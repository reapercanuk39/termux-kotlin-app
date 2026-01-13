package com.termux.shared.settings.properties

import android.content.Context
import android.widget.Toast
import com.google.common.collect.BiMap
import com.google.common.collect.ImmutableBiMap
import com.google.common.primitives.Primitives
import com.termux.shared.file.FileUtils
import com.termux.shared.file.filesystem.FileType
import com.termux.shared.logger.Logger
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

/**
 * An implementation similar to android's [android.content.SharedPreferences] interface for
 * reading and writing to and from ".properties" files which also maintains an in-memory cache for
 * the key/value pairs when an instance object is used. Operations are done under
 * synchronization locks and should be thread safe.
 *
 * If [SharedProperties] instance object is used, then two types of in-memory cache maps are
 * maintained, one for the literal [String] values found in the file for the keys and an
 * additional one that stores (near) primitive [Object] values for internal use by the caller.
 *
 * The [SharedProperties] also provides static functions that can be used to read properties
 * from files or individual key values or even their internal values. An automatic mapping to a
 * boolean as internal value can also be done. An in-memory cache is not maintained, nor are locks used.
 *
 * This currently only has read support, write support can/will be added later if needed. Check android's
 * SharedPreferencesImpl class for reference implementation.
 *
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:frameworks/base/core/java/android/app/SharedPreferencesImpl.java
 */
class SharedProperties(
    context: Context,
    private val mPropertiesFile: File?,
    private val mPropertiesList: Set<String>?,
    private val mSharedPropertiesParser: SharedPropertiesParser
) {
    /**
     * The [Properties] object that maintains an in-memory cache of values loaded from the
     * [mPropertiesFile] file. The key/value pairs are of any keys that are found in the file
     * against their literal values in the file.
     */
    private var mProperties: Properties = Properties()

    /**
     * The [HashMap] object that maintains an in-memory cache of internal values for the values
     * loaded from the [mPropertiesFile] file. The key/value pairs are of any keys defined by
     * [mPropertiesList] that are found in the file against their internal [Object] values
     * returned by the call to
     * [SharedPropertiesParser.getInternalPropertyValueFromValue] interface.
     */
    private var mMap: MutableMap<String, Any?> = HashMap()

    private val mContext: Context = context.applicationContext
    private val mLock = Any()

    /**
     * Load the properties defined by [mPropertiesList] or all properties if its `null`
     * from the [mPropertiesFile] file to update the [mProperties] and [mMap]
     * in-memory cache.
     * Properties are not loading automatically when constructor is called and must be manually called.
     */
    fun loadPropertiesFromDisk() {
        synchronized(mLock) {
            // Get properties from mPropertiesFile
            var properties = getProperties(false)

            // We still need to load default values into mMap, so we assume no properties defined if
            // reading from mPropertiesFile failed
            if (properties == null)
                properties = Properties()

            val map = HashMap<String, Any?>()
            val newProperties = Properties()

            val propertiesList = mPropertiesList ?: properties.stringPropertyNames()

            for (key in propertiesList) {
                val value = properties.getProperty(key) // value will be null if key does not exist in propertiesFile

                // Call the SharedPropertiesParser.getInternalPropertyValueFromValue(Context,String,String)
                // interface method to get the internal value to store in the mMap.
                val internalValue = mSharedPropertiesParser.getInternalPropertyValueFromValue(mContext, key, value)

                // If the internal value was successfully added to map, then also add value to newProperties
                // We only store values in-memory defined by propertiesList
                if (putToMap(map, key, internalValue)) { // null internalValue will be put into map
                    putToProperties(newProperties, key, value) // null value will **not** be into properties
                }
            }

            mMap = map
            mProperties = newProperties
        }
    }

    /**
     * Get the [Properties] object for the [mPropertiesFile]. The [Properties]
     * object will also contain properties not defined by the [mPropertiesList] if cache
     * value is `false`.
     *
     * @param cached If `true`, then the [mProperties] in-memory cache is returned. Otherwise
     *               the [Properties] object is directly read from the [mPropertiesFile].
     * @return Returns the [Properties] object if read from file, otherwise a copy of [mProperties].
     */
    fun getProperties(cached: Boolean): Properties? {
        synchronized(mLock) {
            return if (cached) {
                getPropertiesCopy(mProperties)
            } else {
                getPropertiesFromFile(mContext, mPropertiesFile, mSharedPropertiesParser)
            }
        }
    }

    /**
     * Get the [String] value for the key passed from the [mPropertiesFile].
     *
     * @param key The key to read from the [Properties] object.
     * @param cached If `true`, then the value is returned from the [mProperties] in-memory cache.
     *               Otherwise the [Properties] object is read directly from the [mPropertiesFile]
     *               and value is returned from it against the key.
     * @return Returns the [String] object. This will be `null` if key is not found.
     */
    fun getProperty(key: String, cached: Boolean): String? {
        synchronized(mLock) {
            return getProperties(cached)?.get(key) as? String
        }
    }

    /**
     * Get the [mMap] object for the [mPropertiesFile]. A call to
     * [loadPropertiesFromDisk] must be made before this.
     *
     * @return Returns a copy of [mMap] object.
     */
    fun getInternalProperties(): Map<String, Any?> {
        synchronized(mLock) {
            return getMapCopy(mMap) ?: HashMap()
        }
    }

    /**
     * Get the internal [Object] value for the key passed from the [mPropertiesFile].
     * The value is returned from the [mMap] in-memory cache, so a call to
     * [loadPropertiesFromDisk] must be made before this.
     *
     * @param key The key to read from the [mMap] object.
     * @return Returns the [Object] object. This will be `null` if key is not found or
     * if object was `null`. Use [HashMap.containsKey] to detect the later situation.
     */
    fun getInternalProperty(key: String?): Any? {
        synchronized(mLock) {
            // null keys are not allowed to be stored in mMap
            return if (key != null) getInternalProperties()[key] else null
        }
    }

    companion object {
        /** Defines the bidirectional map for boolean values and their internal values */
        @JvmField
        val MAP_GENERIC_BOOLEAN: ImmutableBiMap<String, Boolean> = ImmutableBiMap.Builder<String, Boolean>()
            .put("true", true)
            .put("false", false)
            .build()

        /** Defines the bidirectional map for inverted boolean values and their internal values */
        @JvmField
        val MAP_GENERIC_INVERTED_BOOLEAN: ImmutableBiMap<String, Boolean> = ImmutableBiMap.Builder<String, Boolean>()
            .put("true", false)
            .put("false", true)
            .build()

        private const val LOG_TAG = "SharedProperties"

        /**
         * A static function to get the [Properties] object for the propertiesFile. A lock is not
         * taken when this function is called.
         *
         * @param context The [Context] to use to show a flash if an exception is raised while
         *                reading the file. If context is `null`, then flash will not be shown.
         * @param propertiesFile The [File] to read the [Properties] from.
         * @return Returns the [Properties] object. It will be `null` if an exception is
         * raised while reading the file.
         */
        @JvmStatic
        fun getPropertiesFromFile(
            context: Context?,
            propertiesFile: File?,
            sharedPropertiesParser: SharedPropertiesParser?
        ): Properties? {
            val properties = Properties()

            if (propertiesFile == null) {
                Logger.logWarn(LOG_TAG, "Not loading properties since file is null")
                return properties
            }

            try {
                FileInputStream(propertiesFile).use { input ->
                    Logger.logVerbose(LOG_TAG, "Loading properties from \"${propertiesFile.absolutePath}\" file")
                    properties.load(InputStreamReader(input, StandardCharsets.UTF_8))
                }
            } catch (e: Exception) {
                context?.let {
                    Toast.makeText(
                        it,
                        "Could not open properties file \"${propertiesFile.absolutePath}\": ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                Logger.logStackTraceWithMessage(
                    LOG_TAG,
                    "Error loading properties file \"${propertiesFile.absolutePath}\"",
                    e
                )
                return null
            }

            return if (sharedPropertiesParser != null && context != null) {
                sharedPropertiesParser.preProcessPropertiesOnReadFromDisk(context, properties)
            } else {
                properties
            }
        }

        /**
         * Returns the first [File] found in
         * `propertiesFilePaths` from which app properties can be loaded. If the [File] found
         * is not a regular file or is not readable, then `null` is returned. Symlinks **will not**
         * be followed for potential security reasons.
         *
         * @param propertiesFilePaths The [List] containing properties file paths.
         * @param logTag If log tag to use for logging errors.
         * @return Returns the [File] object for Termux:Float app properties.
         */
        @JvmStatic
        fun getPropertiesFileFromList(propertiesFilePaths: List<String>?, logTag: String): File? {
            if (propertiesFilePaths.isNullOrEmpty())
                return null

            for (propertiesFilePath in propertiesFilePaths) {
                val propertiesFile = File(propertiesFilePath)

                // Symlinks **will not** be followed.
                val fileType = FileUtils.getFileType(propertiesFilePath, false)
                when {
                    fileType == FileType.REGULAR -> {
                        if (propertiesFile.canRead())
                            return propertiesFile
                        else
                            Logger.logWarn(logTag, "Ignoring properties file at \"$propertiesFilePath\" since it is not readable")
                    }
                    fileType != FileType.NO_EXIST -> {
                        Logger.logWarn(logTag, "Ignoring properties file at \"$propertiesFilePath\" of type: \"${fileType.name}\"")
                    }
                }
            }

            Logger.logDebug(logTag, "No readable properties file found at: $propertiesFilePaths")
            return null
        }

        @JvmStatic
        fun getProperty(context: Context?, propertiesFile: File?, key: String, def: String?): String? {
            return getProperty(context, propertiesFile, key, def, null)
        }

        /**
         * A static function to get the [String] value for the [Properties] key read from
         * the propertiesFile file.
         *
         * @param context The [Context] for the [getPropertiesFromFile] call.
         * @param propertiesFile The [File] to read the [Properties] from.
         * @param key The key to read.
         * @param def The default value.
         * @param sharedPropertiesParser The implementation of the [SharedPropertiesParser] interface.
         * @return Returns the [String] object. This will be `null` if key is not found.
         */
        @JvmStatic
        fun getProperty(
            context: Context?,
            propertiesFile: File?,
            key: String,
            def: String?,
            sharedPropertiesParser: SharedPropertiesParser?
        ): String? {
            val properties = getPropertiesFromFile(context, propertiesFile, sharedPropertiesParser) ?: Properties()
            return getDefaultIfNull(properties[key] as? String, def)
        }

        /**
         * A static function to get the internal [Object] value for the [String] value for
         * the [Properties] key read from the propertiesFile file.
         *
         * @param context The [Context] for the [getPropertiesFromFile] call.
         * @param propertiesFile The [File] to read the [Properties] from.
         * @param key The key to read.
         * @param sharedPropertiesParser The implementation of the [SharedPropertiesParser] interface.
         * @return Returns the [String] Object returned by the call to
         * [SharedPropertiesParser.getInternalPropertyValueFromValue].
         */
        @JvmStatic
        fun getInternalProperty(
            context: Context?,
            propertiesFile: File?,
            key: String,
            sharedPropertiesParser: SharedPropertiesParser
        ): Any? {
            val properties = getPropertiesFromFile(context, propertiesFile, sharedPropertiesParser) ?: Properties()
            val value = properties[key] as? String

            // Call the SharedPropertiesParser.getInternalPropertyValueFromValue(Context,String,String)
            // interface method to get the internal value to return.
            return sharedPropertiesParser.getInternalPropertyValueFromValue(context!!, key, value)
        }

        @JvmStatic
        fun isPropertyValueTrue(context: Context?, propertiesFile: File?, key: String, logErrorOnInvalidValue: Boolean): Boolean {
            return isPropertyValueTrue(context, propertiesFile, key, logErrorOnInvalidValue, null)
        }

        /**
         * A static function to check if the value is `true` for [Properties] key read from
         * the propertiesFile file.
         *
         * @param context The [Context] for the [getPropertiesFromFile] call.
         * @param propertiesFile The [File] to read the [Properties] from.
         * @param key The key to read.
         * @param logErrorOnInvalidValue If `true`, then an error will be logged if key value
         *                               was found in [Properties] but was invalid.
         * @param sharedPropertiesParser The implementation of the [SharedPropertiesParser] interface.
         * @return Returns `true` if the [Properties] key [String] value equals "true",
         * regardless of case. If the key does not exist in the file or does not equal "true", then
         * `false` will be returned.
         */
        @JvmStatic
        fun isPropertyValueTrue(
            context: Context?,
            propertiesFile: File?,
            key: String,
            logErrorOnInvalidValue: Boolean,
            sharedPropertiesParser: SharedPropertiesParser?
        ): Boolean {
            return getBooleanValueForStringValue(
                key,
                getProperty(context, propertiesFile, key, null, sharedPropertiesParser),
                false,
                logErrorOnInvalidValue,
                LOG_TAG
            )
        }

        @JvmStatic
        fun isPropertyValueFalse(context: Context?, propertiesFile: File?, key: String, logErrorOnInvalidValue: Boolean): Boolean {
            return isPropertyValueFalse(context, propertiesFile, key, logErrorOnInvalidValue, null)
        }

        /**
         * A static function to check if the value is `false` for [Properties] key read from
         * the propertiesFile file.
         *
         * @param context The [Context] for the [getPropertiesFromFile] call.
         * @param propertiesFile The [File] to read the [Properties] from.
         * @param key The key to read.
         * @param logErrorOnInvalidValue If `true`, then an error will be logged if key value
         *                               was found in [Properties] but was invalid.
         * @param sharedPropertiesParser The implementation of the [SharedPropertiesParser] interface.
         * @return Returns `true` if the [Properties] key [String] value equals "false",
         * regardless of case. If the key does not exist in the file or does not equal "false", then
         * `true` will be returned.
         */
        @JvmStatic
        fun isPropertyValueFalse(
            context: Context?,
            propertiesFile: File?,
            key: String,
            logErrorOnInvalidValue: Boolean,
            sharedPropertiesParser: SharedPropertiesParser?
        ): Boolean {
            return getInvertedBooleanValueForStringValue(
                key,
                getProperty(context, propertiesFile, key, null, sharedPropertiesParser),
                true,
                logErrorOnInvalidValue,
                LOG_TAG
            )
        }

        /**
         * Put a value in a [mMap].
         * The key cannot be `null`.
         * Only `null`, primitive or their wrapper classes or String class objects are allowed to be added to
         * the map, although this limitation may be changed.
         *
         * @param map The [Map] object to add value to.
         * @param key The key for which to add the value to the map.
         * @param value The [Object] to add to the map.
         * @return Returns `true` if value was successfully added, otherwise `false`.
         */
        @JvmStatic
        fun putToMap(map: HashMap<String, Any?>?, key: String?, value: Any?): Boolean {
            if (map == null) {
                Logger.logError(LOG_TAG, "Map passed to SharedProperties.putToProperties() is null")
                return false
            }

            // null keys are not allowed to be stored in mMap
            if (key == null) {
                Logger.logError(LOG_TAG, "Cannot put a null key into properties map")
                return false
            }

            val put: Boolean
            if (value != null) {
                val clazz = value.javaClass
                put = clazz.isPrimitive || Primitives.isWrapperType(clazz) || value is String
            } else {
                put = true
            }

            return if (put) {
                map[key] = value
                true
            } else {
                Logger.logError(LOG_TAG, "Cannot put a non-primitive value for the key \"$key\" into properties map")
                false
            }
        }

        /**
         * Put a value in a [Map].
         * The key cannot be `null`.
         * Passing `null` as the value argument is equivalent to removing the key from the
         * properties.
         *
         * @param properties The [Properties] object to add value to.
         * @param key The key for which to add the value to the properties.
         * @param value The [String] to add to the properties.
         * @return Returns `true` if value was successfully added, otherwise `false`.
         */
        @JvmStatic
        fun putToProperties(properties: Properties?, key: String?, value: String?): Boolean {
            if (properties == null) {
                Logger.logError(LOG_TAG, "Properties passed to SharedProperties.putToProperties() is null")
                return false
            }

            // null keys are not allowed to be stored in mMap
            if (key == null) {
                Logger.logError(LOG_TAG, "Cannot put a null key into properties")
                return false
            }

            if (value != null) {
                properties[key] = value
            } else {
                properties.remove(key)
            }

            return true
        }

        @JvmStatic
        fun getPropertiesCopy(inputProperties: Properties?): Properties? {
            if (inputProperties == null) return null

            val outputProperties = Properties()
            for (key in inputProperties.stringPropertyNames()) {
                outputProperties[key] = inputProperties[key]
            }
            return outputProperties
        }

        @JvmStatic
        fun getMapCopy(map: Map<String, Any?>?): Map<String, Any?>? {
            return map?.let { HashMap(it) }
        }

        /**
         * Get the boolean value for the [String] value.
         *
         * @param value The [String] value to convert.
         * @return Returns `true` or `false` if value is the literal string "true" or "false" respectively,
         * regardless of case. Otherwise returns `null`.
         */
        @JvmStatic
        fun getBooleanValueForStringValue(value: String?): Boolean? {
            return MAP_GENERIC_BOOLEAN[toLowerCase(value)]
        }

        /**
         * Get the boolean value for the [String] value.
         *
         * @param value The [String] value to convert.
         * @param def The default [Boolean] value to return.
         * @param logErrorOnInvalidValue If `true`, then an error will be logged if `value`
         *                               was not `null` and was invalid.
         * @param logTag If log tag to use for logging errors.
         * @return Returns `true` or `false` if value is the literal string "true" or "false" respectively,
         * regardless of case. Otherwise returns default value.
         */
        @JvmStatic
        fun getBooleanValueForStringValue(key: String?, value: String?, def: Boolean, logErrorOnInvalidValue: Boolean, logTag: String): Boolean {
            return getDefaultIfNotInMap(key, MAP_GENERIC_BOOLEAN, toLowerCase(value), def, logErrorOnInvalidValue, logTag) as Boolean
        }

        /**
         * Get the inverted boolean value for the [String] value.
         *
         * @param value The [String] value to convert.
         * @param def The default [Boolean] value to return.
         * @param logErrorOnInvalidValue If `true`, then an error will be logged if `value`
         *                               was not `null` and was invalid.
         * @param logTag If log tag to use for logging errors.
         * @return Returns `true` or `false` if value is the literal string "false" or "true" respectively,
         * regardless of case. Otherwise returns default value.
         */
        @JvmStatic
        fun getInvertedBooleanValueForStringValue(key: String?, value: String?, def: Boolean, logErrorOnInvalidValue: Boolean, logTag: String): Boolean {
            return getDefaultIfNotInMap(key, MAP_GENERIC_INVERTED_BOOLEAN, toLowerCase(value), def, logErrorOnInvalidValue, logTag) as Boolean
        }

        /**
         * Get the value for the `inputValue` [Object] key from a [BiMap], otherwise
         * default value if key not found in `map`.
         *
         * @param key The shared properties [String] key value for which the value is being returned.
         * @param map The [BiMap] value to get the value from.
         * @param inputValue The [Object] key value of the map.
         * @param defaultOutputValue The default [Boolean] value to return if `inputValue` not found in map.
         *            The default value must exist as a value in the [BiMap] passed.
         * @param logErrorOnInvalidValue If `true`, then an error will be logged if `inputValue`
         *                               was not `null` and was not found in the map.
         * @param logTag If log tag to use for logging errors.
         * @return Returns the value for the `inputValue` key from the map if it exists. Otherwise
         * returns default value.
         */
        @JvmStatic
        fun getDefaultIfNotInMap(
            key: String?,
            map: BiMap<*, *>,
            inputValue: Any?,
            defaultOutputValue: Any?,
            logErrorOnInvalidValue: Boolean,
            logTag: String
        ): Any? {
            val outputValue = map[inputValue]
            if (outputValue == null) {
                val defaultInputValue = map.inverse()[defaultOutputValue]
                if (defaultInputValue == null)
                    Logger.logError(
                        LOG_TAG,
                        "The default output value \"$defaultOutputValue\" for the key \"$key\" does not exist as a value in the BiMap passed to getDefaultIfNotInMap(): ${map.values}"
                    )

                if (logErrorOnInvalidValue && inputValue != null) {
                    if (key != null)
                        Logger.logError(logTag, "The value \"$inputValue\" for the key \"$key\" is invalid. Using default value \"$defaultInputValue\" instead.")
                    else
                        Logger.logError(logTag, "The value \"$inputValue\" is invalid. Using default value \"$defaultInputValue\" instead.")
                }

                return defaultOutputValue
            } else {
                return outputValue
            }
        }

        /**
         * Get the `int` `value` as is if between `min` and `max` (inclusive), otherwise
         * return default value.
         *
         * @param key The shared properties [String] key value for which the value is being returned.
         * @param value The `int` value to check.
         * @param def The default `int` value if `value` not in range.
         * @param min The min allowed `int` value.
         * @param max The max allowed `int` value.
         * @param logErrorOnInvalidValue If `true`, then an error will be logged if `value`
         *                               not in range.
         * @param ignoreErrorIfValueZero If logging error should be ignored if value equals 0.
         * @param logTag If log tag to use for logging errors.
         * @return Returns the `value` as is if within range. Otherwise returns default value.
         */
        @JvmStatic
        fun getDefaultIfNotInRange(
            key: String?,
            value: Int,
            def: Int,
            min: Int,
            max: Int,
            logErrorOnInvalidValue: Boolean,
            ignoreErrorIfValueZero: Boolean,
            logTag: String
        ): Int {
            if (value < min || value > max) {
                if (logErrorOnInvalidValue && (!ignoreErrorIfValueZero || value != 0)) {
                    if (key != null)
                        Logger.logError(logTag, "The value \"$value\" for the key \"$key\" is not within the range $min-$max (inclusive). Using default value \"$def\" instead.")
                    else
                        Logger.logError(logTag, "The value \"$value\" is not within the range $min-$max (inclusive). Using default value \"$def\" instead.")
                }
                return def
            } else {
                return value
            }
        }

        /**
         * Get the `float` `value` as is if between `min` and `max` (inclusive), otherwise
         * return default value.
         *
         * @param key The shared properties [String] key value for which the value is being returned.
         * @param value The `float` value to check.
         * @param def The default `float` value if `value` not in range.
         * @param min The min allowed `float` value.
         * @param max The max allowed `float` value.
         * @param logErrorOnInvalidValue If `true`, then an error will be logged if `value`
         *                               not in range.
         * @param ignoreErrorIfValueZero If logging error should be ignored if value equals 0.
         * @param logTag If log tag to use for logging errors.
         * @return Returns the `value` as is if within range. Otherwise returns default value.
         */
        @JvmStatic
        fun getDefaultIfNotInRange(
            key: String?,
            value: Float,
            def: Float,
            min: Float,
            max: Float,
            logErrorOnInvalidValue: Boolean,
            ignoreErrorIfValueZero: Boolean,
            logTag: String
        ): Float {
            if (value < min || value > max) {
                if (logErrorOnInvalidValue && (!ignoreErrorIfValueZero || value != 0f)) {
                    if (key != null)
                        Logger.logError(logTag, "The value \"$value\" for the key \"$key\" is not within the range $min-$max (inclusive). Using default value \"$def\" instead.")
                    else
                        Logger.logError(logTag, "The value \"$value\" is not within the range $min-$max (inclusive). Using default value \"$def\" instead.")
                }
                return def
            } else {
                return value
            }
        }

        /**
         * Get the object itself if it is not `null`, otherwise default.
         *
         * @param object The [Object] to check.
         * @param def The default [Object].
         * @return Returns `object` if it is not `null`, otherwise returns `def`.
         */
        @JvmStatic
        fun <T> getDefaultIfNull(`object`: T?, def: T?): T? {
            return `object` ?: def
        }

        /**
         * Get the [String] object itself if it is not `null` or empty, otherwise default.
         *
         * @param object The [String] to check.
         * @param def The default [String].
         * @return Returns `object` if it is not `null`, otherwise returns `def`.
         */
        @JvmStatic
        fun getDefaultIfNullOrEmpty(`object`: String?, def: String?): String? {
            return if (`object`.isNullOrEmpty()) def else `object`
        }

        /**
         * Covert the [String] value to lowercase.
         *
         * @param value The [String] value to convert.
         * @return Returns the lowercased value.
         */
        @JvmStatic
        fun toLowerCase(value: String?): String? {
            return value?.lowercase()
        }
    }
}
