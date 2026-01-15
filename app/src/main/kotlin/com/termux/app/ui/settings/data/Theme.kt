package com.termux.app.ui.settings.data

import kotlinx.serialization.Serializable

/**
 * Terminal color theme definition.
 * All colors are stored as ARGB Long values.
 */
@Serializable
data class Theme(
    /** Unique identifier */
    val id: String,
    
    /** Display name */
    val name: String,
    
    /** Theme author */
    val author: String? = null,
    
    /** True if bundled with the app */
    val isBuiltIn: Boolean = false,
    
    /** True if provided by a plugin */
    val isPluginProvided: Boolean = false,
    
    /** Plugin ID if plugin-provided */
    val pluginId: String? = null,
    
    // Terminal colors
    val background: Long,
    val foreground: Long,
    val cursor: Long,
    val cursorForeground: Long,
    
    // ANSI 16-color palette
    val color0: Long,   // Black
    val color1: Long,   // Red
    val color2: Long,   // Green
    val color3: Long,   // Yellow
    val color4: Long,   // Blue
    val color5: Long,   // Magenta
    val color6: Long,   // Cyan
    val color7: Long,   // White
    val color8: Long,   // Bright Black (Gray)
    val color9: Long,   // Bright Red
    val color10: Long,  // Bright Green
    val color11: Long,  // Bright Yellow
    val color12: Long,  // Bright Blue
    val color13: Long,  // Bright Magenta
    val color14: Long,  // Bright Cyan
    val color15: Long   // Bright White
) {
    /**
     * Get color by ANSI index (0-15).
     */
    fun getAnsiColor(index: Int): Long = when (index) {
        0 -> color0
        1 -> color1
        2 -> color2
        3 -> color3
        4 -> color4
        5 -> color5
        6 -> color6
        7 -> color7
        8 -> color8
        9 -> color9
        10 -> color10
        11 -> color11
        12 -> color12
        13 -> color13
        14 -> color14
        15 -> color15
        else -> foreground
    }
    
    /**
     * Check if this is a dark theme (based on background luminance).
     */
    val isDark: Boolean
        get() {
            val r = ((background shr 16) and 0xFF) / 255.0
            val g = ((background shr 8) and 0xFF) / 255.0
            val b = (background and 0xFF) / 255.0
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            return luminance < 0.5
        }
    
    companion object {
        /**
         * Dark Steel - Termux Kotlin signature theme.
         * A modern dark theme with steel blue accents.
         */
        val DARK_STEEL = Theme(
            id = "dark_steel",
            name = "Dark Steel",
            author = "Termux Kotlin",
            isBuiltIn = true,
            background = 0xFF1a1a2e,
            foreground = 0xFFc0c0c0,
            cursor = 0xFF4ade80,
            cursorForeground = 0xFF1a1a2e,
            color0 = 0xFF1a1a2e,
            color1 = 0xFFe74c3c,
            color2 = 0xFF4ade80,
            color3 = 0xFFf1c40f,
            color4 = 0xFF3498db,
            color5 = 0xFF9b59b6,
            color6 = 0xFF1abc9c,
            color7 = 0xFFbdc3c7,
            color8 = 0xFF34495e,
            color9 = 0xFFe74c3c,
            color10 = 0xFF2ecc71,
            color11 = 0xFFf39c12,
            color12 = 0xFF2980b9,
            color13 = 0xFF8e44ad,
            color14 = 0xFF16a085,
            color15 = 0xFFecf0f1
        )
        
        /**
         * Molten Blue - GitHub-inspired dark theme.
         */
        val MOLTEN_BLUE = Theme(
            id = "molten_blue",
            name = "Molten Blue",
            author = "Termux Kotlin",
            isBuiltIn = true,
            background = 0xFF0d1117,
            foreground = 0xFFc9d1d9,
            cursor = 0xFF58a6ff,
            cursorForeground = 0xFF0d1117,
            color0 = 0xFF0d1117,
            color1 = 0xFFff7b72,
            color2 = 0xFF7ee787,
            color3 = 0xFFd29922,
            color4 = 0xFF58a6ff,
            color5 = 0xFFbc8cff,
            color6 = 0xFF39c5cf,
            color7 = 0xFFb1bac4,
            color8 = 0xFF484f58,
            color9 = 0xFFff7b72,
            color10 = 0xFF7ee787,
            color11 = 0xFFd29922,
            color12 = 0xFF58a6ff,
            color13 = 0xFFbc8cff,
            color14 = 0xFF39c5cf,
            color15 = 0xFFf0f6fc
        )
        
        /**
         * Obsidian - VS Code-inspired dark theme.
         */
        val OBSIDIAN = Theme(
            id = "obsidian",
            name = "Obsidian",
            author = "Termux Kotlin",
            isBuiltIn = true,
            background = 0xFF1e1e1e,
            foreground = 0xFFd4d4d4,
            cursor = 0xFFaeafad,
            cursorForeground = 0xFF1e1e1e,
            color0 = 0xFF1e1e1e,
            color1 = 0xFFf44747,
            color2 = 0xFF608b4e,
            color3 = 0xFFdcdcaa,
            color4 = 0xFF569cd6,
            color5 = 0xFFc586c0,
            color6 = 0xFF4ec9b0,
            color7 = 0xFFd4d4d4,
            color8 = 0xFF808080,
            color9 = 0xFFf44747,
            color10 = 0xFF608b4e,
            color11 = 0xFFdcdcaa,
            color12 = 0xFF569cd6,
            color13 = 0xFFc586c0,
            color14 = 0xFF4ec9b0,
            color15 = 0xFFffffff
        )
        
        /**
         * Solarized Dark - Classic Ethan Schoonover theme.
         */
        val SOLARIZED_DARK = Theme(
            id = "solarized_dark",
            name = "Solarized Dark",
            author = "Ethan Schoonover",
            isBuiltIn = true,
            background = 0xFF002b36,
            foreground = 0xFF839496,
            cursor = 0xFF93a1a1,
            cursorForeground = 0xFF002b36,
            color0 = 0xFF073642,
            color1 = 0xFFdc322f,
            color2 = 0xFF859900,
            color3 = 0xFFb58900,
            color4 = 0xFF268bd2,
            color5 = 0xFFd33682,
            color6 = 0xFF2aa198,
            color7 = 0xFFeee8d5,
            color8 = 0xFF002b36,
            color9 = 0xFFcb4b16,
            color10 = 0xFF586e75,
            color11 = 0xFF657b83,
            color12 = 0xFF839496,
            color13 = 0xFF6c71c4,
            color14 = 0xFF93a1a1,
            color15 = 0xFFfdf6e3
        )
        
        /**
         * Solarized Light - Light variant of Solarized.
         */
        val SOLARIZED_LIGHT = Theme(
            id = "solarized_light",
            name = "Solarized Light",
            author = "Ethan Schoonover",
            isBuiltIn = true,
            background = 0xFFfdf6e3,
            foreground = 0xFF657b83,
            cursor = 0xFF586e75,
            cursorForeground = 0xFFfdf6e3,
            color0 = 0xFFeee8d5,
            color1 = 0xFFdc322f,
            color2 = 0xFF859900,
            color3 = 0xFFb58900,
            color4 = 0xFF268bd2,
            color5 = 0xFFd33682,
            color6 = 0xFF2aa198,
            color7 = 0xFF073642,
            color8 = 0xFFfdf6e3,
            color9 = 0xFFcb4b16,
            color10 = 0xFF93a1a1,
            color11 = 0xFF839496,
            color12 = 0xFF657b83,
            color13 = 0xFF6c71c4,
            color14 = 0xFF586e75,
            color15 = 0xFF002b36
        )
        
        /**
         * Gruvbox Dark - Retro groove color scheme.
         */
        val GRUVBOX_DARK = Theme(
            id = "gruvbox_dark",
            name = "Gruvbox Dark",
            author = "morhetz",
            isBuiltIn = true,
            background = 0xFF282828,
            foreground = 0xFFebdbb2,
            cursor = 0xFFebdbb2,
            cursorForeground = 0xFF282828,
            color0 = 0xFF282828,
            color1 = 0xFFcc241d,
            color2 = 0xFF98971a,
            color3 = 0xFFd79921,
            color4 = 0xFF458588,
            color5 = 0xFFb16286,
            color6 = 0xFF689d6a,
            color7 = 0xFFa89984,
            color8 = 0xFF928374,
            color9 = 0xFFfb4934,
            color10 = 0xFFb8bb26,
            color11 = 0xFFfabd2f,
            color12 = 0xFF83a598,
            color13 = 0xFFd3869b,
            color14 = 0xFF8ec07c,
            color15 = 0xFFebdbb2
        )
        
        /**
         * Gruvbox Light - Light variant of Gruvbox.
         */
        val GRUVBOX_LIGHT = Theme(
            id = "gruvbox_light",
            name = "Gruvbox Light",
            author = "morhetz",
            isBuiltIn = true,
            background = 0xFFfbf1c7,
            foreground = 0xFF3c3836,
            cursor = 0xFF3c3836,
            cursorForeground = 0xFFfbf1c7,
            color0 = 0xFFfbf1c7,
            color1 = 0xFFcc241d,
            color2 = 0xFF98971a,
            color3 = 0xFFd79921,
            color4 = 0xFF458588,
            color5 = 0xFFb16286,
            color6 = 0xFF689d6a,
            color7 = 0xFF7c6f64,
            color8 = 0xFF928374,
            color9 = 0xFF9d0006,
            color10 = 0xFF79740e,
            color11 = 0xFFb57614,
            color12 = 0xFF076678,
            color13 = 0xFF8f3f71,
            color14 = 0xFF427b58,
            color15 = 0xFF3c3836
        )
        
        /**
         * Dracula - Popular dark theme.
         */
        val DRACULA = Theme(
            id = "dracula",
            name = "Dracula",
            author = "Zeno Rocha",
            isBuiltIn = true,
            background = 0xFF282a36,
            foreground = 0xFFf8f8f2,
            cursor = 0xFFf8f8f2,
            cursorForeground = 0xFF282a36,
            color0 = 0xFF21222c,
            color1 = 0xFFff5555,
            color2 = 0xFF50fa7b,
            color3 = 0xFFf1fa8c,
            color4 = 0xFFbd93f9,
            color5 = 0xFFff79c6,
            color6 = 0xFF8be9fd,
            color7 = 0xFFf8f8f2,
            color8 = 0xFF6272a4,
            color9 = 0xFFff6e6e,
            color10 = 0xFF69ff94,
            color11 = 0xFFffffa5,
            color12 = 0xFFd6acff,
            color13 = 0xFFff92df,
            color14 = 0xFFa4ffff,
            color15 = 0xFFffffff
        )
        
        /**
         * Nord - Arctic, north-bluish color palette.
         */
        val NORD = Theme(
            id = "nord",
            name = "Nord",
            author = "Arctic Ice Studio",
            isBuiltIn = true,
            background = 0xFF2e3440,
            foreground = 0xFFd8dee9,
            cursor = 0xFFd8dee9,
            cursorForeground = 0xFF2e3440,
            color0 = 0xFF3b4252,
            color1 = 0xFFbf616a,
            color2 = 0xFFa3be8c,
            color3 = 0xFFebcb8b,
            color4 = 0xFF81a1c1,
            color5 = 0xFFb48ead,
            color6 = 0xFF88c0d0,
            color7 = 0xFFe5e9f0,
            color8 = 0xFF4c566a,
            color9 = 0xFFbf616a,
            color10 = 0xFFa3be8c,
            color11 = 0xFFebcb8b,
            color12 = 0xFF81a1c1,
            color13 = 0xFFb48ead,
            color14 = 0xFF8fbcbb,
            color15 = 0xFFeceff4
        )
        
        /**
         * High Contrast - Maximum readability theme.
         */
        val HIGH_CONTRAST = Theme(
            id = "high_contrast",
            name = "High Contrast",
            author = "Termux Kotlin",
            isBuiltIn = true,
            background = 0xFF000000,
            foreground = 0xFFffffff,
            cursor = 0xFFffffff,
            cursorForeground = 0xFF000000,
            color0 = 0xFF000000,
            color1 = 0xFFff0000,
            color2 = 0xFF00ff00,
            color3 = 0xFFffff00,
            color4 = 0xFF0080ff,
            color5 = 0xFFff00ff,
            color6 = 0xFF00ffff,
            color7 = 0xFFffffff,
            color8 = 0xFF808080,
            color9 = 0xFFff0000,
            color10 = 0xFF00ff00,
            color11 = 0xFFffff00,
            color12 = 0xFF0080ff,
            color13 = 0xFFff00ff,
            color14 = 0xFF00ffff,
            color15 = 0xFFffffff
        )
        
        /**
         * All built-in themes.
         */
        val BUILT_IN_THEMES = listOf(
            DARK_STEEL,
            MOLTEN_BLUE,
            OBSIDIAN,
            DRACULA,
            NORD,
            SOLARIZED_DARK,
            SOLARIZED_LIGHT,
            GRUVBOX_DARK,
            GRUVBOX_LIGHT,
            HIGH_CONTRAST
        )
        
        /**
         * Default theme ID.
         */
        const val DEFAULT_THEME_ID = "dark_steel"
    }
}

/**
 * Repository for managing themes.
 */
interface ThemeRepository {
    /** Get all available themes (built-in + custom + plugin) */
    fun getAllThemes(): kotlinx.coroutines.flow.Flow<List<Theme>>
    
    /** Get a theme by ID */
    suspend fun getTheme(id: String): Theme?
    
    /** Save a custom theme */
    suspend fun saveTheme(theme: Theme)
    
    /** Delete a custom theme */
    suspend fun deleteTheme(id: String)
    
    /** Import a theme from JSON */
    suspend fun importTheme(json: String): Theme
    
    /** Export a theme to JSON */
    suspend fun exportTheme(theme: Theme): String
}
