package com.termux.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.termux.app.ui.settings.data.Theme

/**
 * Theme Gallery screen for browsing and selecting terminal themes.
 * 
 * Features:
 * - Grid view of all available themes
 * - Live terminal preview
 * - Theme import/export
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeGalleryScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val themes by viewModel.themes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentThemeId = settings?.themeName ?: Theme.DEFAULT_THEME_ID
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme Gallery") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Import theme */ }) {
                        Icon(Icons.Default.FileOpen, "Import theme")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)
        ) {
            items(themes) { theme ->
                ThemePreviewCard(
                    theme = theme,
                    isSelected = theme.id == currentThemeId,
                    onClick = { viewModel.updateTheme(theme.id) }
                )
            }
        }
    }
}

/**
 * Card showing a theme preview with terminal sample.
 */
@Composable
fun ThemePreviewCard(
    theme: Theme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = Color(theme.background)
    val fgColor = Color(theme.foreground)
    val cursorColor = Color(theme.cursor)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.0f)
            .then(
                if (isSelected) Modifier.border(
                    3.dp,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.shapes.medium
                ) else Modifier
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // Terminal preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(bgColor)
                    .padding(8.dp)
            ) {
                Column {
                    // Fake terminal content
                    Text(
                        text = "$ ls -la",
                        color = fgColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row {
                        Text(
                            "drwxr-xr-x ",
                            color = Color(theme.color4),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "home",
                            color = Color(theme.color6),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row {
                        Text(
                            "-rw-r--r-- ",
                            color = Color(theme.color2),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "file.txt",
                            color = fgColor,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    // Cursor line
                    Row {
                        Text(
                            "$ ",
                            color = fgColor,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp, 10.dp)
                                .background(cursorColor)
                        )
                    }
                }
            }
            
            // Theme info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = theme.name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                    theme.author?.let {
                        Text(
                            text = "by $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Color palette preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    theme.color1, theme.color2, theme.color3, theme.color4,
                    theme.color5, theme.color6, theme.color9, theme.color10
                ).forEach { color ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(Color(color))
                    )
                }
            }
            
            Spacer(Modifier.height(4.dp))
        }
    }
}
