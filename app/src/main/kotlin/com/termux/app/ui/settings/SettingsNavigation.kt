package com.termux.app.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint

/**
 * Compose-based Settings Activity.
 * 
 * Hosts the settings navigation graph with:
 * - Main settings screen
 * - Theme gallery
 * - Profile management
 */
@AndroidEntryPoint
class ComposeSettingsActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            TermuxSettingsTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsNavigation(
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}

/**
 * Settings navigation graph.
 */
@Composable
fun SettingsNavigation(
    onNavigateBack: () -> Unit
) {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = SettingsDestination.Main.route
    ) {
        composable(SettingsDestination.Main.route) {
            SettingsScreen(
                onNavigateBack = onNavigateBack,
                onNavigateToThemeGallery = {
                    navController.navigate(SettingsDestination.ThemeGallery.route)
                },
                onNavigateToProfiles = {
                    navController.navigate(SettingsDestination.Profiles.route)
                }
            )
        }
        
        composable(SettingsDestination.ThemeGallery.route) {
            ThemeGalleryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(SettingsDestination.Profiles.route) {
            ProfilesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Settings navigation destinations.
 */
sealed class SettingsDestination(val route: String) {
    data object Main : SettingsDestination("settings_main")
    data object ThemeGallery : SettingsDestination("settings_theme_gallery")
    data object Profiles : SettingsDestination("settings_profiles")
}

/**
 * Termux Settings Material 3 theme.
 */
@Composable
fun TermuxSettingsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF4ade80),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF1a1a2e),
            primaryContainer = androidx.compose.ui.graphics.Color(0xFF1a3a2e),
            onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF4ade80),
            secondary = androidx.compose.ui.graphics.Color(0xFF3498db),
            background = androidx.compose.ui.graphics.Color(0xFF1a1a2e),
            surface = androidx.compose.ui.graphics.Color(0xFF1a1a2e),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2a2a3e)
        )
        else -> lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF2ecc71),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFFd4f5e0),
            onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF1a3a2e)
        )
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
