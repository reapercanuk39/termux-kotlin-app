package com.termux.app.styling

import android.content.Context
import android.graphics.Typeface
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.termux.shared.logger.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for terminal styling.
 * 
 * Manages color schemes, fonts, and other terminal appearance settings.
 * Integrates with the terminal emulator to apply changes.
 */

private val Context.stylingDataStore: DataStore<Preferences> by preferencesDataStore(name = "termux_styling")

@Singleton
class StylingManager @Inject constructor(
    private val context: Context,
    private val fontManager: FontManager
) {
    companion object {
        private const val LOG_TAG = "StylingManager"
        private const val COLORS_FILE = "/data/data/com.termux/files/home/.termux/colors.properties"
        private const val CUSTOM_SCHEMES_DIR = "/data/data/com.termux/files/home/.termux/colors"
        
        // Preference keys
        private val KEY_CURRENT_SCHEME = stringPreferencesKey("current_color_scheme")
        private val KEY_CURRENT_FONT = stringPreferencesKey("current_font")
        private val KEY_FONT_SIZE = intPreferencesKey("font_size")
        private val KEY_BOLD_TEXT = booleanPreferencesKey("bold_text")
        private val KEY_CURSOR_BLINK = booleanPreferencesKey("cursor_blink")
        private val KEY_CURSOR_STYLE = stringPreferencesKey("cursor_style")
        private val KEY_BELL_ENABLED = booleanPreferencesKey("bell_enabled")
        private val KEY_VIBRATE_ON_BELL = booleanPreferencesKey("vibrate_on_bell")
        
        const val DEFAULT_FONT_SIZE = 14
        const val MIN_FONT_SIZE = 6
        const val MAX_FONT_SIZE = 42
    }
    
    private val dataStore = context.stylingDataStore
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    
    // Current applied settings
    private var _currentScheme: ColorScheme = BuiltInColorSchemes.Default
    private var _currentFont: Typeface = Typeface.MONOSPACE
    
    // Listeners for style changes
    private val styleChangeListeners = mutableListOf<StyleChangeListener>()
    
    /**
     * Interface for style change notifications.
     */
    interface StyleChangeListener {
        fun onColorSchemeChanged(scheme: ColorScheme)
        fun onFontChanged(font: Typeface, name: String)
        fun onFontSizeChanged(size: Int)
        fun onSettingsChanged()
    }
    
    // DataStore flows
    val currentSchemeName: Flow<String> = dataStore.data.map { 
        it[KEY_CURRENT_SCHEME] ?: "Default" 
    }
    
    val currentFontName: Flow<String> = dataStore.data.map { 
        it[KEY_CURRENT_FONT] ?: "default" 
    }
    
    val fontSize: Flow<Int> = dataStore.data.map { 
        it[KEY_FONT_SIZE] ?: DEFAULT_FONT_SIZE 
    }
    
    val boldText: Flow<Boolean> = dataStore.data.map { 
        it[KEY_BOLD_TEXT] ?: false 
    }
    
    val cursorBlink: Flow<Boolean> = dataStore.data.map { 
        it[KEY_CURSOR_BLINK] ?: true 
    }
    
    val cursorStyle: Flow<String> = dataStore.data.map { 
        it[KEY_CURSOR_STYLE] ?: "block" 
    }
    
    val bellEnabled: Flow<Boolean> = dataStore.data.map { 
        it[KEY_BELL_ENABLED] ?: true 
    }
    
    val vibrateOnBell: Flow<Boolean> = dataStore.data.map { 
        it[KEY_VIBRATE_ON_BELL] ?: true 
    }
    
    /**
     * Initialize styling manager.
     */
    suspend fun initialize() {
        // Load saved scheme
        val schemeName = dataStore.data.first()[KEY_CURRENT_SCHEME] ?: "Default"
        _currentScheme = getColorScheme(schemeName) ?: BuiltInColorSchemes.Default
        
        // Load saved font
        val fontName = dataStore.data.first()[KEY_CURRENT_FONT] ?: "default"
        _currentFont = fontManager.loadFont(fontName)
        
        Logger.logInfo(LOG_TAG, "Styling initialized: scheme=$schemeName, font=$fontName")
    }
    
    /**
     * Get current color scheme.
     */
    fun getCurrentScheme(): ColorScheme = _currentScheme
    
    /**
     * Get current font.
     */
    fun getCurrentFont(): Typeface = _currentFont
    
    /**
     * Get all available color schemes.
     */
    fun getAvailableSchemes(): List<ColorScheme> {
        val schemes = mutableListOf<ColorScheme>()
        
        // Add built-in schemes
        schemes.addAll(BuiltInColorSchemes.getAll())
        
        // Add custom schemes from ~/.termux/colors/
        schemes.addAll(loadCustomSchemes())
        
        return schemes
    }
    
    /**
     * Load custom color schemes from directory.
     */
    private fun loadCustomSchemes(): List<ColorScheme> {
        val schemes = mutableListOf<ColorScheme>()
        val customDir = File(CUSTOM_SCHEMES_DIR)
        
        if (customDir.exists() && customDir.isDirectory) {
            customDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                ?.forEach { file ->
                    try {
                        val scheme = json.decodeFromString<ColorScheme>(file.readText())
                        schemes.add(scheme)
                    } catch (e: Exception) {
                        Logger.logWarn(LOG_TAG, "Failed to load color scheme: ${file.name}")
                    }
                }
        }
        
        return schemes
    }
    
    /**
     * Get a color scheme by name.
     */
    fun getColorScheme(name: String): ColorScheme? {
        // Check built-in first
        BuiltInColorSchemes.getByName(name)?.let { return it }
        
        // Check custom schemes
        return loadCustomSchemes().find { it.name.equals(name, ignoreCase = true) }
    }
    
    /**
     * Set the current color scheme.
     */
    suspend fun setColorScheme(scheme: ColorScheme) {
        _currentScheme = scheme
        
        // Save to preferences
        dataStore.edit { it[KEY_CURRENT_SCHEME] = scheme.name }
        
        // Write to colors.properties for terminal
        writeColorsFile(scheme)
        
        // Notify listeners
        styleChangeListeners.forEach { it.onColorSchemeChanged(scheme) }
        
        Logger.logInfo(LOG_TAG, "Color scheme changed to: ${scheme.name}")
    }
    
    /**
     * Set the current color scheme by name.
     */
    suspend fun setColorScheme(name: String): Boolean {
        val scheme = getColorScheme(name) ?: return false
        setColorScheme(scheme)
        return true
    }
    
    /**
     * Set the current font.
     */
    suspend fun setFont(name: String) {
        _currentFont = fontManager.setFont(name)
        
        // Save to preferences
        dataStore.edit { it[KEY_CURRENT_FONT] = name }
        
        // Notify listeners
        styleChangeListeners.forEach { it.onFontChanged(_currentFont, name) }
        
        Logger.logInfo(LOG_TAG, "Font changed to: $name")
    }
    
    /**
     * Set font size.
     */
    suspend fun setFontSize(size: Int) {
        val clampedSize = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        dataStore.edit { it[KEY_FONT_SIZE] = clampedSize }
        styleChangeListeners.forEach { it.onFontSizeChanged(clampedSize) }
    }
    
    /**
     * Set bold text enabled.
     */
    suspend fun setBoldText(enabled: Boolean) {
        dataStore.edit { it[KEY_BOLD_TEXT] = enabled }
        styleChangeListeners.forEach { it.onSettingsChanged() }
    }
    
    /**
     * Set cursor blink.
     */
    suspend fun setCursorBlink(enabled: Boolean) {
        dataStore.edit { it[KEY_CURSOR_BLINK] = enabled }
        styleChangeListeners.forEach { it.onSettingsChanged() }
    }
    
    /**
     * Set cursor style (block, underline, bar).
     */
    suspend fun setCursorStyle(style: String) {
        dataStore.edit { it[KEY_CURSOR_STYLE] = style }
        styleChangeListeners.forEach { it.onSettingsChanged() }
    }
    
    /**
     * Set bell enabled.
     */
    suspend fun setBellEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BELL_ENABLED] = enabled }
        styleChangeListeners.forEach { it.onSettingsChanged() }
    }
    
    /**
     * Set vibrate on bell.
     */
    suspend fun setVibrateOnBell(enabled: Boolean) {
        dataStore.edit { it[KEY_VIBRATE_ON_BELL] = enabled }
        styleChangeListeners.forEach { it.onSettingsChanged() }
    }
    
    /**
     * Write colors.properties file for terminal.
     */
    private fun writeColorsFile(scheme: ColorScheme) {
        try {
            val file = File(COLORS_FILE)
            file.parentFile?.mkdirs()
            
            val content = buildString {
                appendLine("# Termux color scheme: ${scheme.name}")
                appendLine("# Generated by Termux Styling")
                appendLine()
                appendLine("foreground=${ColorScheme.toHex(scheme.foreground)}")
                appendLine("background=${ColorScheme.toHex(scheme.background)}")
                appendLine("cursor=${ColorScheme.toHex(scheme.cursor)}")
                appendLine()
                for (i in 0..15) {
                    appendLine("color$i=${ColorScheme.toHex(scheme.getColor(i))}")
                }
            }
            
            file.writeText(content)
            Logger.logDebug(LOG_TAG, "Wrote colors.properties")
            
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to write colors.properties: ${e.message}")
        }
    }
    
    /**
     * Save a custom color scheme.
     */
    fun saveCustomScheme(scheme: ColorScheme): Boolean {
        return try {
            val customDir = File(CUSTOM_SCHEMES_DIR)
            if (!customDir.exists()) {
                customDir.mkdirs()
            }
            
            val file = File(customDir, "${scheme.name.lowercase().replace(" ", "_")}.json")
            file.writeText(json.encodeToString(scheme))
            
            Logger.logInfo(LOG_TAG, "Saved custom scheme: ${scheme.name}")
            true
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to save custom scheme: ${e.message}")
            false
        }
    }
    
    /**
     * Delete a custom color scheme.
     */
    fun deleteCustomScheme(name: String): Boolean {
        val customDir = File(CUSTOM_SCHEMES_DIR)
        val file = File(customDir, "${name.lowercase().replace(" ", "_")}.json")
        
        return if (file.exists()) {
            file.delete()
            Logger.logInfo(LOG_TAG, "Deleted custom scheme: $name")
            true
        } else {
            false
        }
    }
    
    /**
     * Get current settings as a data class.
     */
    suspend fun getCurrentSettings(): StylingSettings {
        val prefs = dataStore.data.first()
        return StylingSettings(
            schemeName = prefs[KEY_CURRENT_SCHEME] ?: "Default",
            fontName = prefs[KEY_CURRENT_FONT] ?: "default",
            fontSize = prefs[KEY_FONT_SIZE] ?: DEFAULT_FONT_SIZE,
            boldText = prefs[KEY_BOLD_TEXT] ?: false,
            cursorBlink = prefs[KEY_CURSOR_BLINK] ?: true,
            cursorStyle = prefs[KEY_CURSOR_STYLE] ?: "block",
            bellEnabled = prefs[KEY_BELL_ENABLED] ?: true,
            vibrateOnBell = prefs[KEY_VIBRATE_ON_BELL] ?: true
        )
    }
    
    /**
     * Add a style change listener.
     */
    fun addStyleChangeListener(listener: StyleChangeListener) {
        styleChangeListeners.add(listener)
    }
    
    /**
     * Remove a style change listener.
     */
    fun removeStyleChangeListener(listener: StyleChangeListener) {
        styleChangeListeners.remove(listener)
    }
    
    /**
     * Get available fonts.
     */
    fun getAvailableFonts(): List<FontManager.FontInfo> = fontManager.getAvailableFonts()
}

/**
 * Current styling settings.
 */
data class StylingSettings(
    val schemeName: String,
    val fontName: String,
    val fontSize: Int,
    val boldText: Boolean,
    val cursorBlink: Boolean,
    val cursorStyle: String,
    val bellEnabled: Boolean,
    val vibrateOnBell: Boolean
)
