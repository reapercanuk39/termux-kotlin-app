package com.termux.app.styling

import android.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Color scheme for terminal styling.
 * 
 * Contains ANSI colors (0-15), foreground, background, and cursor colors.
 */
@Serializable
data class ColorScheme(
    val name: String,
    val description: String = "",
    val author: String = "",
    
    // Base colors
    val foreground: Int,
    val background: Int,
    val cursor: Int,
    
    // ANSI colors 0-7 (normal)
    val color0: Int,  // Black
    val color1: Int,  // Red
    val color2: Int,  // Green
    val color3: Int,  // Yellow
    val color4: Int,  // Blue
    val color5: Int,  // Magenta
    val color6: Int,  // Cyan
    val color7: Int,  // White
    
    // ANSI colors 8-15 (bright)
    val color8: Int,  // Bright Black
    val color9: Int,  // Bright Red
    val color10: Int, // Bright Green
    val color11: Int, // Bright Yellow
    val color12: Int, // Bright Blue
    val color13: Int, // Bright Magenta
    val color14: Int, // Bright Cyan
    val color15: Int  // Bright White
) {
    /**
     * Get color by index (0-15).
     */
    fun getColor(index: Int): Int = when (index) {
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
     * Get all ANSI colors as array.
     */
    fun getColors(): IntArray = intArrayOf(
        color0, color1, color2, color3, color4, color5, color6, color7,
        color8, color9, color10, color11, color12, color13, color14, color15
    )
    
    companion object {
        /**
         * Parse color from hex string (#RRGGBB or #AARRGGBB).
         */
        fun parseColor(hex: String): Int {
            return try {
                Color.parseColor(hex)
            } catch (e: Exception) {
                Color.WHITE
            }
        }
        
        /**
         * Convert color to hex string.
         */
        fun toHex(color: Int): String {
            return String.format("#%06X", 0xFFFFFF and color)
        }
    }
}

/**
 * Built-in color schemes.
 */
object BuiltInColorSchemes {
    
    val Default = ColorScheme(
        name = "Default",
        description = "Default Termux colors",
        foreground = 0xFFFFFFFF.toInt(),
        background = 0xFF000000.toInt(),
        cursor = 0xFFAAAAAA.toInt(),
        color0 = 0xFF000000.toInt(),
        color1 = 0xFFCD3131.toInt(),
        color2 = 0xFF0DBC79.toInt(),
        color3 = 0xFFE5E510.toInt(),
        color4 = 0xFF2472C8.toInt(),
        color5 = 0xFFBC3FBC.toInt(),
        color6 = 0xFF11A8CD.toInt(),
        color7 = 0xFFE5E5E5.toInt(),
        color8 = 0xFF666666.toInt(),
        color9 = 0xFFF14C4C.toInt(),
        color10 = 0xFF23D18B.toInt(),
        color11 = 0xFFF5F543.toInt(),
        color12 = 0xFF3B8EEA.toInt(),
        color13 = 0xFFD670D6.toInt(),
        color14 = 0xFF29B8DB.toInt(),
        color15 = 0xFFFFFFFF.toInt()
    )
    
    val Dracula = ColorScheme(
        name = "Dracula",
        description = "Dark theme inspired by Dracula",
        author = "Zeno Rocha",
        foreground = 0xFFF8F8F2.toInt(),
        background = 0xFF282A36.toInt(),
        cursor = 0xFFF8F8F2.toInt(),
        color0 = 0xFF21222C.toInt(),
        color1 = 0xFFFF5555.toInt(),
        color2 = 0xFF50FA7B.toInt(),
        color3 = 0xFFF1FA8C.toInt(),
        color4 = 0xFFBD93F9.toInt(),
        color5 = 0xFFFF79C6.toInt(),
        color6 = 0xFF8BE9FD.toInt(),
        color7 = 0xFFF8F8F2.toInt(),
        color8 = 0xFF6272A4.toInt(),
        color9 = 0xFFFF6E6E.toInt(),
        color10 = 0xFF69FF94.toInt(),
        color11 = 0xFFFFFFA5.toInt(),
        color12 = 0xFFD6ACFF.toInt(),
        color13 = 0xFFFF92DF.toInt(),
        color14 = 0xFFA4FFFF.toInt(),
        color15 = 0xFFFFFFFF.toInt()
    )
    
    val Monokai = ColorScheme(
        name = "Monokai",
        description = "Classic Monokai theme",
        foreground = 0xFFF8F8F2.toInt(),
        background = 0xFF272822.toInt(),
        cursor = 0xFFF8F8F0.toInt(),
        color0 = 0xFF272822.toInt(),
        color1 = 0xFFF92672.toInt(),
        color2 = 0xFFA6E22E.toInt(),
        color3 = 0xFFF4BF75.toInt(),
        color4 = 0xFF66D9EF.toInt(),
        color5 = 0xFFAE81FF.toInt(),
        color6 = 0xFFA1EFE4.toInt(),
        color7 = 0xFFF8F8F2.toInt(),
        color8 = 0xFF75715E.toInt(),
        color9 = 0xFFF92672.toInt(),
        color10 = 0xFFA6E22E.toInt(),
        color11 = 0xFFF4BF75.toInt(),
        color12 = 0xFF66D9EF.toInt(),
        color13 = 0xFFAE81FF.toInt(),
        color14 = 0xFFA1EFE4.toInt(),
        color15 = 0xFFF9F8F5.toInt()
    )
    
    val SolarizedDark = ColorScheme(
        name = "Solarized Dark",
        description = "Ethan Schoonover's Solarized (dark)",
        author = "Ethan Schoonover",
        foreground = 0xFF839496.toInt(),
        background = 0xFF002B36.toInt(),
        cursor = 0xFF93A1A1.toInt(),
        color0 = 0xFF073642.toInt(),
        color1 = 0xFFDC322F.toInt(),
        color2 = 0xFF859900.toInt(),
        color3 = 0xFFB58900.toInt(),
        color4 = 0xFF268BD2.toInt(),
        color5 = 0xFFD33682.toInt(),
        color6 = 0xFF2AA198.toInt(),
        color7 = 0xFFEEE8D5.toInt(),
        color8 = 0xFF002B36.toInt(),
        color9 = 0xFFCB4B16.toInt(),
        color10 = 0xFF586E75.toInt(),
        color11 = 0xFF657B83.toInt(),
        color12 = 0xFF839496.toInt(),
        color13 = 0xFF6C71C4.toInt(),
        color14 = 0xFF93A1A1.toInt(),
        color15 = 0xFFFDF6E3.toInt()
    )
    
    val SolarizedLight = ColorScheme(
        name = "Solarized Light",
        description = "Ethan Schoonover's Solarized (light)",
        author = "Ethan Schoonover",
        foreground = 0xFF657B83.toInt(),
        background = 0xFFFDF6E3.toInt(),
        cursor = 0xFF586E75.toInt(),
        color0 = 0xFFEEE8D5.toInt(),
        color1 = 0xFFDC322F.toInt(),
        color2 = 0xFF859900.toInt(),
        color3 = 0xFFB58900.toInt(),
        color4 = 0xFF268BD2.toInt(),
        color5 = 0xFFD33682.toInt(),
        color6 = 0xFF2AA198.toInt(),
        color7 = 0xFF073642.toInt(),
        color8 = 0xFFFDF6E3.toInt(),
        color9 = 0xFFCB4B16.toInt(),
        color10 = 0xFF93A1A1.toInt(),
        color11 = 0xFF839496.toInt(),
        color12 = 0xFF657B83.toInt(),
        color13 = 0xFF6C71C4.toInt(),
        color14 = 0xFF586E75.toInt(),
        color15 = 0xFF002B36.toInt()
    )
    
    val Nord = ColorScheme(
        name = "Nord",
        description = "Arctic, north-bluish color palette",
        author = "Arctic Ice Studio",
        foreground = 0xFFD8DEE9.toInt(),
        background = 0xFF2E3440.toInt(),
        cursor = 0xFFD8DEE9.toInt(),
        color0 = 0xFF3B4252.toInt(),
        color1 = 0xFFBF616A.toInt(),
        color2 = 0xFFA3BE8C.toInt(),
        color3 = 0xFFEBCB8B.toInt(),
        color4 = 0xFF81A1C1.toInt(),
        color5 = 0xFFB48EAD.toInt(),
        color6 = 0xFF88C0D0.toInt(),
        color7 = 0xFFE5E9F0.toInt(),
        color8 = 0xFF4C566A.toInt(),
        color9 = 0xFFBF616A.toInt(),
        color10 = 0xFFA3BE8C.toInt(),
        color11 = 0xFFEBCB8B.toInt(),
        color12 = 0xFF81A1C1.toInt(),
        color13 = 0xFFB48EAD.toInt(),
        color14 = 0xFF8FBCBB.toInt(),
        color15 = 0xFFECEFF4.toInt()
    )
    
    val OneDark = ColorScheme(
        name = "One Dark",
        description = "Atom One Dark theme",
        foreground = 0xFFABB2BF.toInt(),
        background = 0xFF282C34.toInt(),
        cursor = 0xFF528BFF.toInt(),
        color0 = 0xFF282C34.toInt(),
        color1 = 0xFFE06C75.toInt(),
        color2 = 0xFF98C379.toInt(),
        color3 = 0xFFE5C07B.toInt(),
        color4 = 0xFF61AFEF.toInt(),
        color5 = 0xFFC678DD.toInt(),
        color6 = 0xFF56B6C2.toInt(),
        color7 = 0xFFABB2BF.toInt(),
        color8 = 0xFF545862.toInt(),
        color9 = 0xFFE06C75.toInt(),
        color10 = 0xFF98C379.toInt(),
        color11 = 0xFFE5C07B.toInt(),
        color12 = 0xFF61AFEF.toInt(),
        color13 = 0xFFC678DD.toInt(),
        color14 = 0xFF56B6C2.toInt(),
        color15 = 0xFFFFFFFF.toInt()
    )
    
    val GruvboxDark = ColorScheme(
        name = "Gruvbox Dark",
        description = "Retro groove color scheme",
        foreground = 0xFFEBDBB2.toInt(),
        background = 0xFF282828.toInt(),
        cursor = 0xFFEBDBB2.toInt(),
        color0 = 0xFF282828.toInt(),
        color1 = 0xFFCC241D.toInt(),
        color2 = 0xFF98971A.toInt(),
        color3 = 0xFFD79921.toInt(),
        color4 = 0xFF458588.toInt(),
        color5 = 0xFFB16286.toInt(),
        color6 = 0xFF689D6A.toInt(),
        color7 = 0xFFA89984.toInt(),
        color8 = 0xFF928374.toInt(),
        color9 = 0xFFFB4934.toInt(),
        color10 = 0xFFB8BB26.toInt(),
        color11 = 0xFFFABD2F.toInt(),
        color12 = 0xFF83A598.toInt(),
        color13 = 0xFFD3869B.toInt(),
        color14 = 0xFF8EC07C.toInt(),
        color15 = 0xFFEBDBB2.toInt()
    )
    
    val TokyoNight = ColorScheme(
        name = "Tokyo Night",
        description = "Clean dark theme inspired by Tokyo",
        foreground = 0xFFC0CAF5.toInt(),
        background = 0xFF1A1B26.toInt(),
        cursor = 0xFFC0CAF5.toInt(),
        color0 = 0xFF15161E.toInt(),
        color1 = 0xFFF7768E.toInt(),
        color2 = 0xFF9ECE6A.toInt(),
        color3 = 0xFFE0AF68.toInt(),
        color4 = 0xFF7AA2F7.toInt(),
        color5 = 0xFFBB9AF7.toInt(),
        color6 = 0xFF7DCFFF.toInt(),
        color7 = 0xFFA9B1D6.toInt(),
        color8 = 0xFF414868.toInt(),
        color9 = 0xFFF7768E.toInt(),
        color10 = 0xFF9ECE6A.toInt(),
        color11 = 0xFFE0AF68.toInt(),
        color12 = 0xFF7AA2F7.toInt(),
        color13 = 0xFFBB9AF7.toInt(),
        color14 = 0xFF7DCFFF.toInt(),
        color15 = 0xFFC0CAF5.toInt()
    )
    
    val Catppuccin = ColorScheme(
        name = "Catppuccin Mocha",
        description = "Soothing pastel theme",
        foreground = 0xFFCDD6F4.toInt(),
        background = 0xFF1E1E2E.toInt(),
        cursor = 0xFFF5E0DC.toInt(),
        color0 = 0xFF45475A.toInt(),
        color1 = 0xFFF38BA8.toInt(),
        color2 = 0xFFA6E3A1.toInt(),
        color3 = 0xFFF9E2AF.toInt(),
        color4 = 0xFF89B4FA.toInt(),
        color5 = 0xFFF5C2E7.toInt(),
        color6 = 0xFF94E2D5.toInt(),
        color7 = 0xFFBAC2DE.toInt(),
        color8 = 0xFF585B70.toInt(),
        color9 = 0xFFF38BA8.toInt(),
        color10 = 0xFFA6E3A1.toInt(),
        color11 = 0xFFF9E2AF.toInt(),
        color12 = 0xFF89B4FA.toInt(),
        color13 = 0xFFF5C2E7.toInt(),
        color14 = 0xFF94E2D5.toInt(),
        color15 = 0xFFA6ADC8.toInt()
    )
    
    val Matrix = ColorScheme(
        name = "Matrix",
        description = "Classic green-on-black hacker theme",
        foreground = 0xFF00FF00.toInt(),
        background = 0xFF000000.toInt(),
        cursor = 0xFF00FF00.toInt(),
        color0 = 0xFF000000.toInt(),
        color1 = 0xFF008800.toInt(),
        color2 = 0xFF00FF00.toInt(),
        color3 = 0xFF00AA00.toInt(),
        color4 = 0xFF003300.toInt(),
        color5 = 0xFF00CC00.toInt(),
        color6 = 0xFF00EE00.toInt(),
        color7 = 0xFF00FF00.toInt(),
        color8 = 0xFF003300.toInt(),
        color9 = 0xFF00AA00.toInt(),
        color10 = 0xFF00FF00.toInt(),
        color11 = 0xFF00DD00.toInt(),
        color12 = 0xFF006600.toInt(),
        color13 = 0xFF00EE00.toInt(),
        color14 = 0xFF00FF00.toInt(),
        color15 = 0xFF00FF00.toInt()
    )
    
    /**
     * Get all built-in color schemes.
     */
    fun getAll(): List<ColorScheme> = listOf(
        Default,
        Dracula,
        Monokai,
        SolarizedDark,
        SolarizedLight,
        Nord,
        OneDark,
        GruvboxDark,
        TokyoNight,
        Catppuccin,
        Matrix
    )
    
    /**
     * Get scheme by name.
     */
    fun getByName(name: String): ColorScheme? = getAll().find { 
        it.name.equals(name, ignoreCase = true) 
    }
}
