package com.termux.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.termux.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing terminal settings using DataStore.
 * Provides reactive Flow-based access to settings.
 */
@Singleton
class TerminalSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    // Keys for terminal settings
    private object PreferencesKeys {
        val FONT_SIZE = intPreferencesKey("terminal_font_size")
        val FONT_FAMILY = stringPreferencesKey("terminal_font_family")
        val CURSOR_BLINK = booleanPreferencesKey("terminal_cursor_blink")
        val CURSOR_STYLE = stringPreferencesKey("terminal_cursor_style")
        val BELL_ENABLED = booleanPreferencesKey("terminal_bell_enabled")
        val VIBRATE_ON_KEY = booleanPreferencesKey("terminal_vibrate_on_key")
        val EXTRA_KEYS_ENABLED = booleanPreferencesKey("terminal_extra_keys_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("terminal_keep_screen_on")
        val SOFT_KEYBOARD_ENABLED = booleanPreferencesKey("terminal_soft_keyboard_enabled")
        val COLOR_SCHEME = stringPreferencesKey("terminal_color_scheme")
        val BACKGROUND_OPACITY = intPreferencesKey("terminal_background_opacity")
    }

    // Default values
    companion object {
        const val DEFAULT_FONT_SIZE = 14
        const val DEFAULT_FONT_FAMILY = "monospace"
        const val DEFAULT_CURSOR_BLINK = true
        const val DEFAULT_CURSOR_STYLE = "block"
        const val DEFAULT_BELL_ENABLED = true
        const val DEFAULT_VIBRATE_ON_KEY = false
        const val DEFAULT_EXTRA_KEYS_ENABLED = true
        const val DEFAULT_KEEP_SCREEN_ON = false
        const val DEFAULT_SOFT_KEYBOARD_ENABLED = true
        const val DEFAULT_COLOR_SCHEME = "default"
        const val DEFAULT_BACKGROUND_OPACITY = 100
    }

    // Font Size
    val fontSizeFlow: Flow<Int> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.FONT_SIZE] ?: DEFAULT_FONT_SIZE }
        .flowOn(ioDispatcher)

    suspend fun setFontSize(size: Int) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.FONT_SIZE] = size.coerceIn(6, 32)
            }
        }
    }

    // Font Family
    val fontFamilyFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.FONT_FAMILY] ?: DEFAULT_FONT_FAMILY }
        .flowOn(ioDispatcher)

    suspend fun setFontFamily(family: String) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.FONT_FAMILY] = family
            }
        }
    }

    // Cursor Blink
    val cursorBlinkFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.CURSOR_BLINK] ?: DEFAULT_CURSOR_BLINK }
        .flowOn(ioDispatcher)

    suspend fun setCursorBlink(enabled: Boolean) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.CURSOR_BLINK] = enabled
            }
        }
    }

    // Cursor Style
    val cursorStyleFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.CURSOR_STYLE] ?: DEFAULT_CURSOR_STYLE }
        .flowOn(ioDispatcher)

    suspend fun setCursorStyle(style: String) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.CURSOR_STYLE] = style
            }
        }
    }

    // Bell
    val bellEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.BELL_ENABLED] ?: DEFAULT_BELL_ENABLED }
        .flowOn(ioDispatcher)

    suspend fun setBellEnabled(enabled: Boolean) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.BELL_ENABLED] = enabled
            }
        }
    }

    // Vibrate on key
    val vibrateOnKeyFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.VIBRATE_ON_KEY] ?: DEFAULT_VIBRATE_ON_KEY }
        .flowOn(ioDispatcher)

    suspend fun setVibrateOnKey(enabled: Boolean) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.VIBRATE_ON_KEY] = enabled
            }
        }
    }

    // Extra keys row
    val extraKeysEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.EXTRA_KEYS_ENABLED] ?: DEFAULT_EXTRA_KEYS_ENABLED }
        .flowOn(ioDispatcher)

    suspend fun setExtraKeysEnabled(enabled: Boolean) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.EXTRA_KEYS_ENABLED] = enabled
            }
        }
    }

    // Keep screen on
    val keepScreenOnFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: DEFAULT_KEEP_SCREEN_ON }
        .flowOn(ioDispatcher)

    suspend fun setKeepScreenOn(enabled: Boolean) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.KEEP_SCREEN_ON] = enabled
            }
        }
    }

    // Soft keyboard
    val softKeyboardEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.SOFT_KEYBOARD_ENABLED] ?: DEFAULT_SOFT_KEYBOARD_ENABLED }
        .flowOn(ioDispatcher)

    suspend fun setSoftKeyboardEnabled(enabled: Boolean) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.SOFT_KEYBOARD_ENABLED] = enabled
            }
        }
    }

    // Color scheme
    val colorSchemeFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.COLOR_SCHEME] ?: DEFAULT_COLOR_SCHEME }
        .flowOn(ioDispatcher)

    suspend fun setColorScheme(scheme: String) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.COLOR_SCHEME] = scheme
            }
        }
    }

    // Background opacity
    val backgroundOpacityFlow: Flow<Int> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.BACKGROUND_OPACITY] ?: DEFAULT_BACKGROUND_OPACITY }
        .flowOn(ioDispatcher)

    suspend fun setBackgroundOpacity(opacity: Int) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.BACKGROUND_OPACITY] = opacity.coerceIn(0, 100)
            }
        }
    }
}
