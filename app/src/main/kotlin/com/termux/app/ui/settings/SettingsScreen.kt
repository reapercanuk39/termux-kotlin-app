package com.termux.app.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.termux.app.ui.settings.components.*
import com.termux.app.ui.settings.data.*

/**
 * Main Settings screen using Jetpack Compose.
 * 
 * Features:
 * - Material 3 design
 * - Searchable settings
 * - Collapsible categories
 * - Instant apply (no restart needed)
 * - Profile switching
 * - Theme gallery access
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToThemeGallery: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val activeTheme by viewModel.activeTheme.collectAsState()
    
    Scaffold(
        topBar = {
            SettingsTopBar(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onSearchActiveChange = viewModel::setSearchActive,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is SettingsUiState.Loading -> {
                LoadingContent(paddingValues)
            }
            is SettingsUiState.Ready -> {
                SettingsContent(
                    settings = state.settings,
                    activeProfile = activeProfile,
                    activeTheme = activeTheme,
                    searchQuery = searchQuery,
                    viewModel = viewModel,
                    onNavigateToThemeGallery = onNavigateToThemeGallery,
                    onNavigateToProfiles = onNavigateToProfiles,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is SettingsUiState.Error -> {
                ErrorContent(state.message, paddingValues)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    if (isSearchActive) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onSearch = {},
            active = true,
            onActiveChange = { if (!it) onSearchActiveChange(false) },
            placeholder = { Text("Search settings...") },
            leadingIcon = {
                IconButton(onClick = { onSearchActiveChange(false) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, "Clear search")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            // Search suggestions could go here
        }
    } else {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(Icons.Default.Search, "Search settings")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Composable
private fun SettingsContent(
    settings: TermuxSettings,
    activeProfile: Profile?,
    activeTheme: Theme?,
    searchQuery: String,
    viewModel: SettingsViewModel,
    onNavigateToThemeGallery: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Active Profile Card
        item {
            ActiveProfileCard(
                profile = activeProfile,
                onClick = onNavigateToProfiles,
                isVisible = searchQuery.isEmpty() || 
                    "profile".contains(searchQuery, ignoreCase = true)
            )
        }
        
        // Appearance Section
        item {
            AppearanceSection(
                settings = settings,
                activeTheme = activeTheme,
                searchQuery = searchQuery,
                onFontSizeChange = viewModel::updateFontSize,
                onLineSpacingChange = viewModel::updateLineSpacing,
                onLigaturesChange = viewModel::updateLigatures,
                onDynamicColorsChange = viewModel::updateDynamicColors,
                onNavigateToThemeGallery = onNavigateToThemeGallery
            )
        }
        
        // Behavior Section
        item {
            BehaviorSection(
                settings = settings,
                searchQuery = searchQuery,
                onSoftKeyboardChange = viewModel::updateSoftKeyboard,
                onKeepScreenOnChange = viewModel::updateKeepScreenOn,
                onToolbarVisibleChange = viewModel::updateToolbarVisible,
                onBellChange = viewModel::updateBell,
                onVibrationChange = viewModel::updateVibration
            )
        }
        
        // Terminal Section
        item {
            TerminalSection(
                settings = settings,
                searchQuery = searchQuery,
                onScrollSensitivityChange = viewModel::updateScrollSensitivity,
                onBackKeyBehaviorChange = viewModel::updateBackKeyBehavior,
                onVolumeKeysBehaviorChange = viewModel::updateVolumeKeysBehavior
            )
        }
        
        // About Section
        item {
            AboutSection(
                searchQuery = searchQuery,
                onResetToDefaults = viewModel::resetToDefaults
            )
        }
    }
}

@Composable
private fun ActiveProfileCard(
    profile: Profile?,
    onClick: () -> Unit,
    isVisible: Boolean
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Active Profile",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = profile?.name ?: "Default",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    profile?.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "View profiles",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String, paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// ========== Settings Sections ==========

@Composable
private fun AppearanceSection(
    settings: TermuxSettings,
    activeTheme: Theme?,
    searchQuery: String,
    onFontSizeChange: (Int) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onLigaturesChange: (Boolean) -> Unit,
    onDynamicColorsChange: (Boolean) -> Unit,
    onNavigateToThemeGallery: () -> Unit
) {
    val isVisible = searchQuery.isEmpty() ||
        listOf("appearance", "font", "theme", "color", "size", "ligature", "spacing")
            .any { it.contains(searchQuery, ignoreCase = true) }
    
    SettingsCategory(
        title = "Appearance",
        icon = Icons.Default.Palette,
        isVisible = isVisible
    ) {
        // Theme
        SettingsClickableItem(
            title = "Theme",
            description = activeTheme?.name ?: "Select a theme",
            onClick = onNavigateToThemeGallery,
            icon = Icons.Default.ColorLens
        )
        
        SettingsDivider()
        
        // Font Size
        SettingsSliderItem(
            title = "Font Size",
            value = settings.fontSize.toFloat(),
            onValueChange = { onFontSizeChange(it.toInt()) },
            valueRange = 8f..32f,
            steps = 23,
            valueLabel = "${settings.fontSize}sp"
        )
        
        // Line Spacing
        SettingsSliderItem(
            title = "Line Spacing",
            value = settings.lineSpacing,
            onValueChange = onLineSpacingChange,
            valueRange = 1.0f..2.0f,
            steps = 9,
            valueLabel = String.format("%.1fx", settings.lineSpacing)
        )
        
        SettingsDivider()
        
        // Ligatures
        SettingsSwitchItem(
            title = "Font Ligatures",
            description = "Display programming ligatures (requires compatible font)",
            checked = settings.ligaturesEnabled,
            onCheckedChange = onLigaturesChange
        )
        
        // Dynamic Colors (Android 12+)
        SettingsSwitchItem(
            title = "Dynamic Colors",
            description = "Use system wallpaper colors (Android 12+)",
            checked = settings.dynamicColors,
            onCheckedChange = onDynamicColorsChange
        )
    }
}

@Composable
private fun BehaviorSection(
    settings: TermuxSettings,
    searchQuery: String,
    onSoftKeyboardChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onToolbarVisibleChange: (Boolean) -> Unit,
    onBellChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit
) {
    val isVisible = searchQuery.isEmpty() ||
        listOf("behavior", "keyboard", "screen", "toolbar", "bell", "vibration")
            .any { it.contains(searchQuery, ignoreCase = true) }
    
    SettingsCategory(
        title = "Behavior",
        icon = Icons.Default.Tune,
        isVisible = isVisible
    ) {
        SettingsSwitchItem(
            title = "Soft Keyboard",
            description = "Show on-screen keyboard when terminal is focused",
            checked = settings.softKeyboardEnabled,
            onCheckedChange = onSoftKeyboardChange
        )
        
        SettingsSwitchItem(
            title = "Keep Screen On",
            description = "Prevent screen from turning off while terminal is active",
            checked = settings.keepScreenOn,
            onCheckedChange = onKeepScreenOnChange
        )
        
        SettingsSwitchItem(
            title = "Show Toolbar",
            description = "Display the terminal toolbar",
            checked = settings.toolbarVisible,
            onCheckedChange = onToolbarVisibleChange
        )
        
        SettingsDivider()
        
        SettingsSwitchItem(
            title = "Terminal Bell",
            description = "Play sound on terminal bell character",
            checked = settings.bellEnabled,
            onCheckedChange = onBellChange
        )
        
        SettingsSwitchItem(
            title = "Vibration",
            description = "Vibrate on keyboard input",
            checked = settings.vibrationEnabled,
            onCheckedChange = onVibrationChange
        )
    }
}

@Composable
private fun TerminalSection(
    settings: TermuxSettings,
    searchQuery: String,
    onScrollSensitivityChange: (Float) -> Unit,
    onBackKeyBehaviorChange: (BackKeyBehavior) -> Unit,
    onVolumeKeysBehaviorChange: (VolumeKeysBehavior) -> Unit
) {
    val isVisible = searchQuery.isEmpty() ||
        listOf("terminal", "scroll", "back", "volume", "key")
            .any { it.contains(searchQuery, ignoreCase = true) }
    
    SettingsCategory(
        title = "Terminal",
        icon = Icons.Default.Terminal,
        isVisible = isVisible
    ) {
        SettingsSliderItem(
            title = "Scroll Sensitivity",
            value = settings.scrollSensitivity,
            onValueChange = onScrollSensitivityChange,
            valueRange = 0.5f..3.0f,
            steps = 24,
            valueLabel = String.format("%.1fx", settings.scrollSensitivity)
        )
        
        SettingsDivider()
        
        SettingsDropdownItem(
            title = "Back Key Behavior",
            description = "Action when pressing the back key",
            selectedValue = settings.backKeyBehavior,
            options = BackKeyBehavior.entries.toList(),
            onValueChange = onBackKeyBehaviorChange,
            optionLabel = { 
                when (it) {
                    BackKeyBehavior.BACK -> "Navigate Back"
                    BackKeyBehavior.ESCAPE -> "Send Escape"
                }
            }
        )
        
        SettingsDropdownItem(
            title = "Volume Keys Behavior",
            description = "Action when pressing volume keys",
            selectedValue = settings.volumeKeysBehavior,
            options = VolumeKeysBehavior.entries.toList(),
            onValueChange = onVolumeKeysBehaviorChange,
            optionLabel = { 
                when (it) {
                    VolumeKeysBehavior.VOLUME -> "Adjust Volume"
                    VolumeKeysBehavior.FONT_SIZE -> "Adjust Font Size"
                }
            }
        )
    }
}

@Composable
private fun AboutSection(
    searchQuery: String,
    onResetToDefaults: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }
    
    val isVisible = searchQuery.isEmpty() ||
        listOf("about", "reset", "default", "version")
            .any { it.contains(searchQuery, ignoreCase = true) }
    
    SettingsCategory(
        title = "About",
        icon = Icons.Default.Info,
        isVisible = isVisible
    ) {
        SettingsClickableItem(
            title = "Version",
            value = "1.0.0", // TODO: Get from BuildConfig
            onClick = {}
        )
        
        SettingsDivider()
        
        SettingsClickableItem(
            title = "Reset to Defaults",
            description = "Restore all settings to their default values",
            onClick = { showResetDialog = true },
            icon = Icons.Default.RestartAlt
        )
    }
    
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings?") },
            text = { Text("This will restore all settings to their default values. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetToDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
