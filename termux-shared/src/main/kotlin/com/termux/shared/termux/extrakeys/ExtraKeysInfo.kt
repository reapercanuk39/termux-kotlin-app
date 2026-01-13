package com.termux.shared.termux.extrakeys

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A class that defines the info needed by [ExtraKeysView] to display the extra key views.
 */
class ExtraKeysInfo {

    /** Matrix of buttons to be displayed in [ExtraKeysView]. */
    private val mButtons: Array<Array<ExtraKeyButton>>

    /**
     * Initialize [ExtraKeysInfo].
     */
    @Throws(JSONException::class)
    constructor(
        propertiesInfo: String,
        style: String?,
        extraKeyAliasMap: ExtraKeysConstants.ExtraKeyDisplayMap
    ) {
        mButtons = initExtraKeysInfo(propertiesInfo, getCharDisplayMapForStyle(style), extraKeyAliasMap)
    }

    /**
     * Initialize [ExtraKeysInfo].
     */
    @Throws(JSONException::class)
    constructor(
        propertiesInfo: String,
        extraKeyDisplayMap: ExtraKeysConstants.ExtraKeyDisplayMap,
        extraKeyAliasMap: ExtraKeysConstants.ExtraKeyDisplayMap
    ) {
        mButtons = initExtraKeysInfo(propertiesInfo, extraKeyDisplayMap, extraKeyAliasMap)
    }

    @Throws(JSONException::class)
    private fun initExtraKeysInfo(
        propertiesInfo: String,
        extraKeyDisplayMap: ExtraKeysConstants.ExtraKeyDisplayMap,
        extraKeyAliasMap: ExtraKeysConstants.ExtraKeyDisplayMap
    ): Array<Array<ExtraKeyButton>> {
        // Convert String propertiesInfo to Array of Arrays
        val arr = JSONArray(propertiesInfo)
        val matrix = Array(arr.length()) { i ->
            val line = arr.getJSONArray(i)
            Array(line.length()) { j ->
                line.get(j)
            }
        }

        // convert matrix to buttons
        return Array(matrix.size) { i ->
            Array(matrix[i].size) { j ->
                val key = matrix[i][j]
                val jobject = normalizeKeyConfig(key)

                if (!jobject.has(ExtraKeyButton.KEY_POPUP)) {
                    // no popup
                    ExtraKeyButton(jobject, null, extraKeyDisplayMap, extraKeyAliasMap)
                } else {
                    // a popup
                    val popupJobject = normalizeKeyConfig(jobject.get(ExtraKeyButton.KEY_POPUP))
                    val popup = ExtraKeyButton(popupJobject, null, extraKeyDisplayMap, extraKeyAliasMap)
                    ExtraKeyButton(jobject, popup, extraKeyDisplayMap, extraKeyAliasMap)
                }
            }
        }
    }

    fun getMatrix(): Array<Array<ExtraKeyButton>> = mButtons

    companion object {
        /**
         * Convert "value" -> {"key": "value"}.
         */
        @JvmStatic
        @Throws(JSONException::class)
        private fun normalizeKeyConfig(key: Any): JSONObject {
            return when (key) {
                is String -> JSONObject().apply { put(ExtraKeyButton.KEY_KEY_NAME, key) }
                is JSONObject -> key
                else -> throw JSONException("A key in the extra-key matrix must be a string or an object")
            }
        }

        @JvmStatic
        fun getCharDisplayMapForStyle(style: String?): ExtraKeysConstants.ExtraKeyDisplayMap {
            return when (style) {
                "arrows-only" -> ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.ARROWS_ONLY_CHAR_DISPLAY
                "arrows-all" -> ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.LOTS_OF_ARROWS_CHAR_DISPLAY
                "all" -> ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.FULL_ISO_CHAR_DISPLAY
                "none" -> ExtraKeysConstants.ExtraKeyDisplayMap()
                else -> ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY
            }
        }
    }
}
