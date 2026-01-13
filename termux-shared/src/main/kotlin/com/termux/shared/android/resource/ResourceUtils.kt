package com.termux.shared.android.resource

import android.content.Context
import com.termux.shared.data.DataUtils
import com.termux.shared.logger.Logger

object ResourceUtils {

    const val RES_TYPE_COLOR = "color"
    const val RES_TYPE_DRAWABLE = "drawable"
    const val RES_TYPE_ID = "id"
    const val RES_TYPE_LAYOUT = "layout"
    const val RES_TYPE_STRING = "string"
    const val RES_TYPE_STYLE = "style"

    private const val LOG_TAG = "ResourceUtils"

    /** Wrapper for [getResourceId] without defPackage. */
    @JvmStatic
    fun getResourceId(
        context: Context,
        name: String?,
        defType: String?,
        logErrorMessage: Boolean
    ): Int? {
        return getResourceId(context, name, defType, null, logErrorMessage)
    }

    /**
     * Get resource identifier for the given resource name. A fully qualified resource name is of
     * the form "package:type/entry".  The first two components (package and type) are optional if
     * defType and defPackage, respectively, are specified here.
     *
     * @param context The [Context] for operations.
     * @param name The name of the desired resource.
     * @param defType Optional default resource type to find, if "type/" is not included in the name.
     *                Can be null to require an explicit type.
     * @param defPackage Optional default package to find, if "package:" is not included in the name.
     *                   Can be null to require an explicit package.
     * @param logErrorMessage If an error message should be logged if failed to find resource.
     * @return Returns the resource identifier if found. Otherwise null if an exception was
     * raised or resource was not found.
     */
    @JvmStatic
    fun getResourceId(
        context: Context,
        name: String?,
        defType: String?,
        defPackage: String?,
        logErrorMessage: Boolean
    ): Int? {
        if (DataUtils.isNullOrEmpty(name)) return null

        var resourceId: Int? = null
        try {
            val id = context.resources.getIdentifier(name, defType, defPackage)
            if (id != 0) resourceId = id
        } catch (e: Exception) {
            // Ignore
        }

        if (resourceId == null && logErrorMessage) {
            Logger.logError(LOG_TAG, "Resource id not found. name: \"$name\", type: \"$defType\", package: \"$defPackage\", component \"${context.javaClass.name}\"")
        }

        return resourceId
    }

    /**
     * Get resource identifier for the given [RES_TYPE_COLOR] resource name.
     *
     * This is a wrapper for [getResourceId].
     */
    @JvmStatic
    fun getColorResourceId(
        context: Context,
        name: String?,
        defPackage: String?,
        logErrorMessage: Boolean
    ): Int? {
        return getResourceId(context, name, RES_TYPE_COLOR, defPackage, logErrorMessage)
    }

    /**
     * Get resource identifier for the given [RES_TYPE_DRAWABLE] resource name.
     *
     * This is a wrapper for [getResourceId].
     */
    @JvmStatic
    fun getDrawableResourceId(
        context: Context,
        name: String?,
        defPackage: String?,
        logErrorMessage: Boolean
    ): Int? {
        return getResourceId(context, name, RES_TYPE_DRAWABLE, defPackage, logErrorMessage)
    }

    /**
     * Get resource identifier for the given [RES_TYPE_ID] resource name.
     *
     * This is a wrapper for [getResourceId].
     */
    @JvmStatic
    fun getIdResourceId(
        context: Context,
        name: String?,
        defPackage: String?,
        logErrorMessage: Boolean
    ): Int? {
        return getResourceId(context, name, RES_TYPE_ID, defPackage, logErrorMessage)
    }

    /**
     * Get resource identifier for the given [RES_TYPE_LAYOUT] resource name.
     *
     * This is a wrapper for [getResourceId].
     */
    @JvmStatic
    fun getLayoutResourceId(
        context: Context,
        name: String?,
        defPackage: String?,
        logErrorMessage: Boolean
    ): Int? {
        return getResourceId(context, name, RES_TYPE_LAYOUT, defPackage, logErrorMessage)
    }

    /**
     * Get resource identifier for the given [RES_TYPE_STRING] resource name.
     *
     * This is a wrapper for [getResourceId].
     */
    @JvmStatic
    fun getStringResourceId(
        context: Context,
        name: String?,
        defPackage: String?,
        logErrorMessage: Boolean
    ): Int? {
        return getResourceId(context, name, RES_TYPE_STRING, defPackage, logErrorMessage)
    }

    /**
     * Get resource identifier for the given [RES_TYPE_STYLE] resource name.
     *
     * This is a wrapper for [getResourceId].
     */
    @JvmStatic
    fun getStyleResourceId(
        context: Context,
        name: String?,
        defPackage: String?,
        logErrorMessage: Boolean
    ): Int? {
        return getResourceId(context, name, RES_TYPE_STYLE, defPackage, logErrorMessage)
    }
}
