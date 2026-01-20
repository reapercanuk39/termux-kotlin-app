package com.termux.app.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "termux_widgets")

/**
 * Widget type enum.
 */
enum class WidgetType {
    SIMPLE,           // 1x1 icon only
    SINGLE_WITH_LABEL, // 2x1 icon + label
    LIST              // 4x1 or larger, shows list of shortcuts
}

/**
 * Widget configuration.
 */
@Serializable
data class WidgetConfig(
    val widgetId: Int,
    val widgetType: WidgetType = WidgetType.SIMPLE,
    val shortcutPath: String? = null,
    val shortcutName: String? = null,
    val iconPath: String? = null,
    val isTask: Boolean = false,
    val backgroundColor: String? = null,
    val textColor: String? = null
)

/**
 * Manages widget preferences.
 */
@Singleton
class WidgetPreferences @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.widgetDataStore
    private val json = Json { ignoreUnknownKeys = true }
    
    private fun widgetKey(widgetId: Int) = stringPreferencesKey("widget_$widgetId")
    
    /**
     * Get widget configuration.
     */
    fun getWidgetConfig(widgetId: Int): WidgetConfig? {
        return runBlocking {
            val prefs = dataStore.data.first()
            val jsonString = prefs[widgetKey(widgetId)]
            jsonString?.let {
                try {
                    json.decodeFromString<WidgetConfig>(it)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    /**
     * Save widget configuration.
     */
    suspend fun saveWidgetConfig(config: WidgetConfig) {
        dataStore.edit { prefs ->
            prefs[widgetKey(config.widgetId)] = json.encodeToString(config)
        }
    }
    
    /**
     * Delete widget configuration.
     */
    suspend fun deleteWidgetConfig(widgetId: Int) {
        dataStore.edit { prefs ->
            prefs.remove(widgetKey(widgetId))
        }
    }
    
    /**
     * Get all widget IDs.
     */
    suspend fun getAllWidgetIds(): List<Int> {
        val prefs = dataStore.data.first()
        return prefs.asMap().keys
            .mapNotNull { key ->
                val name = key.name
                if (name.startsWith("widget_")) {
                    name.removePrefix("widget_").toIntOrNull()
                } else null
            }
    }
}
