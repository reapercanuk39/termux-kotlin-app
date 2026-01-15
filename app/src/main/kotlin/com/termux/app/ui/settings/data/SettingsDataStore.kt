package com.termux.app.ui.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "termux_settings"
)

/**
 * Modern DataStore-based settings management.
 * Replaces SharedPreferences with reactive, type-safe preferences.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Preference keys organized by category.
     */
    object Keys {
        // Appearance
        val FONT_SIZE = intPreferencesKey("font_size")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val THEME_NAME = stringPreferencesKey("theme_name")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val LIGATURES_ENABLED = booleanPreferencesKey("ligatures_enabled")
        
        // Behavior
        val SOFT_KEYBOARD_ENABLED = booleanPreferencesKey("soft_keyboard_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val TOOLBAR_VISIBLE = booleanPreferencesKey("toolbar_visible")
        val BELL_ENABLED = booleanPreferencesKey("bell_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        
        // Terminal
        val SCROLL_SENSITIVITY = floatPreferencesKey("scroll_sensitivity")
        val BACK_KEY_BEHAVIOR = stringPreferencesKey("back_key_behavior")
        val VOLUME_KEYS_BEHAVIOR = stringPreferencesKey("volume_keys_behavior")
        val TERMINAL_MARGIN_HORIZONTAL = intPreferencesKey("terminal_margin_horizontal")
        val TERMINAL_MARGIN_VERTICAL = intPreferencesKey("terminal_margin_vertical")
        
        // Profile
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        
        // Debugging
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val TERMINAL_VIEW_LOGGING = booleanPreferencesKey("terminal_view_logging")
        val PLUGIN_ERROR_NOTIFICATIONS = booleanPreferencesKey("plugin_error_notifications")
    }
    
    /**
     * Flow of all settings, emitting on any change.
     */
    val settingsFlow: Flow<TermuxSettings> = context.settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs.toTermuxSettings() }
    
    /**
     * Get a single preference value as a Flow.
     */
    fun <T> getPreference(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return context.settingsDataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[key] ?: defaultValue }
    }
    
    /**
     * Update a single preference value.
     */
    suspend fun <T> updateSetting(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { prefs ->
            prefs[key] = value
        }
    }
    
    /**
     * Update multiple preferences atomically.
     */
    suspend fun updateSettings(block: MutablePreferences.() -> Unit) {
        context.settingsDataStore.edit { prefs ->
            prefs.block()
        }
    }
    
    /**
     * Clear all preferences (reset to defaults).
     */
    suspend fun clearAll() {
        context.settingsDataStore.edit { it.clear() }
    }
    
    /**
     * Apply a profile's settings atomically.
     */
    suspend fun applyProfile(profile: Profile) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_PROFILE_ID] = profile.id
            prefs[Keys.FONT_SIZE] = profile.fontSize
            prefs[Keys.FONT_FAMILY] = profile.fontFamily
            prefs[Keys.THEME_NAME] = profile.themeName
            prefs[Keys.LINE_SPACING] = profile.lineSpacing
            prefs[Keys.LIGATURES_ENABLED] = profile.ligaturesEnabled
            prefs[Keys.BELL_ENABLED] = profile.bellEnabled
            prefs[Keys.VIBRATION_ENABLED] = profile.vibrationEnabled
            prefs[Keys.KEEP_SCREEN_ON] = profile.keepScreenOn
            prefs[Keys.SOFT_KEYBOARD_ENABLED] = profile.softKeyboardEnabled
        }
    }
    
    private fun Preferences.toTermuxSettings(): TermuxSettings {
        return TermuxSettings(
            // Appearance
            fontSize = this[Keys.FONT_SIZE] ?: TermuxSettings.Defaults.FONT_SIZE,
            fontFamily = this[Keys.FONT_FAMILY] ?: TermuxSettings.Defaults.FONT_FAMILY,
            themeName = this[Keys.THEME_NAME] ?: TermuxSettings.Defaults.THEME_NAME,
            dynamicColors = this[Keys.DYNAMIC_COLORS] ?: TermuxSettings.Defaults.DYNAMIC_COLORS,
            lineSpacing = this[Keys.LINE_SPACING] ?: TermuxSettings.Defaults.LINE_SPACING,
            ligaturesEnabled = this[Keys.LIGATURES_ENABLED] ?: TermuxSettings.Defaults.LIGATURES_ENABLED,
            
            // Behavior
            softKeyboardEnabled = this[Keys.SOFT_KEYBOARD_ENABLED] ?: TermuxSettings.Defaults.SOFT_KEYBOARD_ENABLED,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: TermuxSettings.Defaults.KEEP_SCREEN_ON,
            toolbarVisible = this[Keys.TOOLBAR_VISIBLE] ?: TermuxSettings.Defaults.TOOLBAR_VISIBLE,
            bellEnabled = this[Keys.BELL_ENABLED] ?: TermuxSettings.Defaults.BELL_ENABLED,
            vibrationEnabled = this[Keys.VIBRATION_ENABLED] ?: TermuxSettings.Defaults.VIBRATION_ENABLED,
            
            // Terminal
            scrollSensitivity = this[Keys.SCROLL_SENSITIVITY] ?: TermuxSettings.Defaults.SCROLL_SENSITIVITY,
            backKeyBehavior = BackKeyBehavior.fromString(this[Keys.BACK_KEY_BEHAVIOR]),
            volumeKeysBehavior = VolumeKeysBehavior.fromString(this[Keys.VOLUME_KEYS_BEHAVIOR]),
            terminalMarginHorizontal = this[Keys.TERMINAL_MARGIN_HORIZONTAL] ?: TermuxSettings.Defaults.TERMINAL_MARGIN,
            terminalMarginVertical = this[Keys.TERMINAL_MARGIN_VERTICAL] ?: TermuxSettings.Defaults.TERMINAL_MARGIN,
            
            // Profile
            activeProfileId = this[Keys.ACTIVE_PROFILE_ID],
            
            // Debugging
            logLevel = this[Keys.LOG_LEVEL] ?: TermuxSettings.Defaults.LOG_LEVEL,
            terminalViewLogging = this[Keys.TERMINAL_VIEW_LOGGING] ?: false,
            pluginErrorNotifications = this[Keys.PLUGIN_ERROR_NOTIFICATIONS] ?: true
        )
    }
}

/**
 * Immutable snapshot of all Termux settings.
 */
data class TermuxSettings(
    // Appearance
    val fontSize: Int,
    val fontFamily: String,
    val themeName: String,
    val dynamicColors: Boolean,
    val lineSpacing: Float,
    val ligaturesEnabled: Boolean,
    
    // Behavior
    val softKeyboardEnabled: Boolean,
    val keepScreenOn: Boolean,
    val toolbarVisible: Boolean,
    val bellEnabled: Boolean,
    val vibrationEnabled: Boolean,
    
    // Terminal
    val scrollSensitivity: Float,
    val backKeyBehavior: BackKeyBehavior,
    val volumeKeysBehavior: VolumeKeysBehavior,
    val terminalMarginHorizontal: Int,
    val terminalMarginVertical: Int,
    
    // Profile
    val activeProfileId: String?,
    
    // Debugging
    val logLevel: String,
    val terminalViewLogging: Boolean,
    val pluginErrorNotifications: Boolean
) {
    object Defaults {
        const val FONT_SIZE = 14
        const val FONT_FAMILY = "default"
        const val THEME_NAME = "dark_steel"
        const val DYNAMIC_COLORS = false
        const val LINE_SPACING = 1.0f
        const val LIGATURES_ENABLED = false
        const val SOFT_KEYBOARD_ENABLED = true
        const val KEEP_SCREEN_ON = false
        const val TOOLBAR_VISIBLE = true
        const val BELL_ENABLED = true
        const val VIBRATION_ENABLED = true
        const val SCROLL_SENSITIVITY = 1.0f
        const val TERMINAL_MARGIN = 0
        const val LOG_LEVEL = "normal"
    }
}

enum class BackKeyBehavior {
    BACK,
    ESCAPE;
    
    companion object {
        fun fromString(value: String?): BackKeyBehavior {
            return entries.find { it.name == value } ?: ESCAPE
        }
    }
}

enum class VolumeKeysBehavior {
    VOLUME,
    FONT_SIZE;
    
    companion object {
        fun fromString(value: String?): VolumeKeysBehavior {
            return entries.find { it.name == value } ?: VOLUME
        }
    }
}

/**
 * Stub for Profile - will be expanded with Room entity.
 */
data class Profile(
    val id: String,
    val name: String,
    val description: String? = null,
    val isDefault: Boolean = false,
    
    // Appearance
    val fontFamily: String = TermuxSettings.Defaults.FONT_FAMILY,
    val fontSize: Int = TermuxSettings.Defaults.FONT_SIZE,
    val themeName: String = TermuxSettings.Defaults.THEME_NAME,
    val lineSpacing: Float = TermuxSettings.Defaults.LINE_SPACING,
    val ligaturesEnabled: Boolean = TermuxSettings.Defaults.LIGATURES_ENABLED,
    
    // Behavior
    val bellEnabled: Boolean = TermuxSettings.Defaults.BELL_ENABLED,
    val vibrationEnabled: Boolean = TermuxSettings.Defaults.VIBRATION_ENABLED,
    val keepScreenOn: Boolean = TermuxSettings.Defaults.KEEP_SCREEN_ON,
    val softKeyboardEnabled: Boolean = TermuxSettings.Defaults.SOFT_KEYBOARD_ENABLED
)
