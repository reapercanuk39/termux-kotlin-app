package com.termux.shared.termux.extrakeys

import android.text.TextUtils
import org.json.JSONException
import org.json.JSONObject

class ExtraKeyButton @JvmOverloads constructor(
    config: JSONObject,
    popup: ExtraKeyButton? = null,
    extraKeyDisplayMap: ExtraKeysConstants.ExtraKeyDisplayMap,
    extraKeyAliasMap: ExtraKeysConstants.ExtraKeyDisplayMap
) {
    /**
     * The key that will be sent to the terminal, either a control character, like defined in
     * [ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS] (LEFT, RIGHT, PGUP...) or some text.
     */
    val key: String

    /**
     * If the key is a macro, i.e. a sequence of keys separated by space.
     */
    val isMacro: Boolean

    /**
     * The text that will be displayed on the button.
     */
    val display: String

    /**
     * The [ExtraKeyButton] containing the information of the popup button (triggered by swipe up).
     */
    val popup: ExtraKeyButton?

    init {
        val keyFromConfig = getStringFromJson(config, KEY_KEY_NAME)
        val macroFromConfig = getStringFromJson(config, KEY_MACRO)
        val keys: Array<String>
        
        when {
            keyFromConfig != null && macroFromConfig != null -> {
                throw JSONException("Both key and macro can't be set for the same key. key: \"$keyFromConfig\", macro: \"$macroFromConfig\"")
            }
            keyFromConfig != null -> {
                keys = arrayOf(keyFromConfig)
                isMacro = false
            }
            macroFromConfig != null -> {
                keys = macroFromConfig.split(" ").toTypedArray()
                isMacro = true
            }
            else -> {
                throw JSONException("All keys have to specify either key or macro")
            }
        }

        for (i in keys.indices) {
            keys[i] = replaceAlias(extraKeyAliasMap, keys[i])
        }

        key = TextUtils.join(" ", keys)

        val displayFromConfig = getStringFromJson(config, KEY_DISPLAY_NAME)
        display = displayFromConfig ?: keys.map { k ->
            extraKeyDisplayMap.get(k, k)
        }.joinToString(" ")

        this.popup = popup
    }

    fun getStringFromJson(config: JSONObject, key: String): String? {
        return try {
            config.getString(key)
        } catch (e: JSONException) {
            null
        }
    }

    companion object {
        /** The key name for the name of the extra key if using a dict to define the extra key. {key: name, ...} */
        const val KEY_KEY_NAME = "key"

        /** The key name for the macro value of the extra key if using a dict to define the extra key. {macro: value, ...} */
        const val KEY_MACRO = "macro"

        /** The key name for the alternate display name of the extra key if using a dict to define the extra key. {display: name, ...} */
        const val KEY_DISPLAY_NAME = "display"

        /** The key name for the nested dict to define popup extra key info if using a dict to define the extra key. {popup: {key: name, ...}, ...} */
        const val KEY_POPUP = "popup"

        /**
         * Replace the alias with its actual key name if found in extraKeyAliasMap.
         */
        @JvmStatic
        fun replaceAlias(extraKeyAliasMap: ExtraKeysConstants.ExtraKeyDisplayMap, key: String): String {
            return extraKeyAliasMap.get(key, key)
        }
    }
}
