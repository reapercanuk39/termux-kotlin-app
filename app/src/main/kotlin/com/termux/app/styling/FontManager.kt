package com.termux.app.styling

import android.content.Context
import android.graphics.Typeface
import com.termux.shared.logger.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages terminal fonts.
 * 
 * Loads fonts from:
 * 1. Built-in font resources
 * 2. ~/.termux/font.ttf (user custom font)
 * 3. Downloaded font packs
 */
@Singleton
class FontManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val LOG_TAG = "FontManager"
        private const val CUSTOM_FONT_PATH = "/data/data/com.termux/files/home/.termux/font.ttf"
        private const val FONTS_DIR = "/data/data/com.termux/files/home/.termux/fonts"
    }
    
    /**
     * Font info data class.
     */
    data class FontInfo(
        val name: String,
        val displayName: String,
        val isBuiltIn: Boolean,
        val isMonospace: Boolean = true,
        val path: String? = null
    )
    
    // Built-in font names
    private val builtInFonts = listOf(
        FontInfo("default", "Default (System)", isBuiltIn = true),
        FontInfo("monospace", "Monospace", isBuiltIn = true),
        FontInfo("fira_code", "Fira Code", isBuiltIn = true),
        FontInfo("jetbrains_mono", "JetBrains Mono", isBuiltIn = true),
        FontInfo("source_code_pro", "Source Code Pro", isBuiltIn = true),
        FontInfo("hack", "Hack", isBuiltIn = true),
        FontInfo("ubuntu_mono", "Ubuntu Mono", isBuiltIn = true),
        FontInfo("dejavu_sans_mono", "DejaVu Sans Mono", isBuiltIn = true),
        FontInfo("inconsolata", "Inconsolata", isBuiltIn = true),
        FontInfo("anonymous_pro", "Anonymous Pro", isBuiltIn = true),
        FontInfo("droid_sans_mono", "Droid Sans Mono", isBuiltIn = true)
    )
    
    private var currentFont: Typeface = Typeface.MONOSPACE
    private var currentFontName: String = "default"
    
    /**
     * Get all available fonts.
     */
    fun getAvailableFonts(): List<FontInfo> {
        val fonts = mutableListOf<FontInfo>()
        
        // Add built-in fonts
        fonts.addAll(builtInFonts)
        
        // Check for custom font
        val customFont = File(CUSTOM_FONT_PATH)
        if (customFont.exists()) {
            fonts.add(FontInfo(
                name = "custom",
                displayName = "Custom (font.ttf)",
                isBuiltIn = false,
                path = customFont.absolutePath
            ))
        }
        
        // Check fonts directory
        val fontsDir = File(FONTS_DIR)
        if (fontsDir.exists() && fontsDir.isDirectory) {
            fontsDir.listFiles()
                ?.filter { it.isFile && (it.name.endsWith(".ttf") || it.name.endsWith(".otf")) }
                ?.forEach { file ->
                    fonts.add(FontInfo(
                        name = file.nameWithoutExtension,
                        displayName = file.nameWithoutExtension.replace("_", " "),
                        isBuiltIn = false,
                        path = file.absolutePath
                    ))
                }
        }
        
        return fonts
    }
    
    /**
     * Load a font by name.
     */
    fun loadFont(name: String): Typeface {
        return try {
            when (name) {
                "default", "monospace" -> Typeface.MONOSPACE
                "custom" -> loadCustomFont()
                else -> {
                    // Try built-in font
                    val fontInfo = builtInFonts.find { it.name == name }
                    if (fontInfo != null) {
                        loadBuiltInFont(name)
                    } else {
                        // Try user font
                        val userFont = File(FONTS_DIR, "$name.ttf")
                        if (userFont.exists()) {
                            Typeface.createFromFile(userFont)
                        } else {
                            val otfFont = File(FONTS_DIR, "$name.otf")
                            if (otfFont.exists()) {
                                Typeface.createFromFile(otfFont)
                            } else {
                                Typeface.MONOSPACE
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "Failed to load font '$name': ${e.message}")
            Typeface.MONOSPACE
        }
    }
    
    /**
     * Load built-in font from assets.
     */
    private fun loadBuiltInFont(name: String): Typeface {
        return try {
            Typeface.createFromAsset(context.assets, "fonts/$name.ttf")
        } catch (e: Exception) {
            Logger.logDebug(LOG_TAG, "Built-in font not found in assets: $name")
            Typeface.MONOSPACE
        }
    }
    
    /**
     * Load custom font from ~/.termux/font.ttf.
     */
    private fun loadCustomFont(): Typeface {
        val customFont = File(CUSTOM_FONT_PATH)
        return if (customFont.exists()) {
            try {
                Typeface.createFromFile(customFont)
            } catch (e: Exception) {
                Logger.logWarn(LOG_TAG, "Failed to load custom font: ${e.message}")
                Typeface.MONOSPACE
            }
        } else {
            Typeface.MONOSPACE
        }
    }
    
    /**
     * Set the current font.
     */
    fun setFont(name: String): Typeface {
        currentFont = loadFont(name)
        currentFontName = name
        return currentFont
    }
    
    /**
     * Get the current font.
     */
    fun getCurrentFont(): Typeface = currentFont
    
    /**
     * Get the current font name.
     */
    fun getCurrentFontName(): String = currentFontName
    
    /**
     * Check if custom font exists.
     */
    fun hasCustomFont(): Boolean = File(CUSTOM_FONT_PATH).exists()
    
    /**
     * Create fonts directory if it doesn't exist.
     */
    fun ensureFontsDirectory(): File {
        val dir = File(FONTS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Copy a font file to the fonts directory.
     */
    fun installFont(sourceFile: File, name: String): Boolean {
        return try {
            val fontsDir = ensureFontsDirectory()
            val extension = sourceFile.extension.takeIf { it.isNotEmpty() } ?: "ttf"
            val destFile = File(fontsDir, "$name.$extension")
            sourceFile.copyTo(destFile, overwrite = true)
            Logger.logInfo(LOG_TAG, "Installed font: ${destFile.absolutePath}")
            true
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to install font: ${e.message}")
            false
        }
    }
    
    /**
     * Remove a font from the fonts directory.
     */
    fun removeFont(name: String): Boolean {
        val fontsDir = File(FONTS_DIR)
        val ttfFile = File(fontsDir, "$name.ttf")
        val otfFile = File(fontsDir, "$name.otf")
        
        var removed = false
        if (ttfFile.exists()) {
            ttfFile.delete()
            removed = true
        }
        if (otfFile.exists()) {
            otfFile.delete()
            removed = true
        }
        
        return removed
    }
}
