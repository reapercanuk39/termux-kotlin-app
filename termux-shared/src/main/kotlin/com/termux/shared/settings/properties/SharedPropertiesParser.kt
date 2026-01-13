package com.termux.shared.settings.properties

import android.content.Context
import java.util.Properties

/**
 * An interface that must be defined by the caller of the [SharedProperties] class.
 */
interface SharedPropertiesParser {

    /**
     * Called when properties are loaded from file to allow client to update the [Properties]
     * loaded from properties file before key/value pairs are stored in the [HashMap] in-memory
     * cache.
     *
     * @param context The context for operations.
     * @param properties The properties loaded from file.
     */
    fun preProcessPropertiesOnReadFromDisk(context: Context, properties: Properties): Properties

    /**
     * A function that should return the internal [Object] to be stored for a key/value pair
     * read from properties file in the [HashMap] in-memory cache.
     *
     * @param context The context for operations.
     * @param key The key for which the internal object is required.
     * @param value The literal value for the property found in the properties file.
     * @return Returns the object to store in the [HashMap] in-memory cache.
     */
    fun getInternalPropertyValueFromValue(context: Context, key: String?, value: String?): Any?
}
