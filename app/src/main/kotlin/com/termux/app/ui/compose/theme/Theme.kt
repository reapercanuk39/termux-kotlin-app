package com.termux.app.ui.compose.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Termux brand colors
private val TermuxGreen = Color(0xFF33B5E5)
private val TermuxDark = Color(0xFF1C1C1C)
private val TermuxBlack = Color(0xFF000000)
private val TermuxWhite = Color(0xFFFFFFFF)
private val TermuxGray = Color(0xFF2A2A2A)

private val DarkColorScheme = darkColorScheme(
    primary = TermuxGreen,
    secondary = Color(0xFF03DAC6),
    tertiary = Color(0xFF3700B3),
    background = TermuxBlack,
    surface = TermuxDark,
    surfaceVariant = TermuxGray,
    onPrimary = TermuxBlack,
    onSecondary = TermuxBlack,
    onTertiary = TermuxWhite,
    onBackground = TermuxWhite,
    onSurface = TermuxWhite,
    onSurfaceVariant = Color(0xFFCACACA)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0077B5),
    secondary = Color(0xFF018786),
    tertiary = Color(0xFF6200EE),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

@Composable
fun TermuxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
