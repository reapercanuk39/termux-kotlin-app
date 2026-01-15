# Implementation Plan: Settings UI + Package Management

This document provides detailed, actionable implementation plans for **#4 Modern Settings UI + UX Overhaul** and **#5 Package Management Enhancements**.

---

## 📋 Executive Summary

| Feature Area | Priority | Effort | Impact |
|--------------|----------|--------|--------|
| Settings Compose Migration | P0 | 2 weeks | High |
| Profile System | P1 | 1 week | Very High |
| Theme Gallery | P1 | 1 week | High |
| Package Backup/Restore | P0 | 2 weeks | Very High |
| Package Health Checks | P1 | 1 week | High |
| Repository Management UI | P2 | 1 week | Medium |

---

# 🎨 #4 — Modern Settings UI + UX Overhaul

## Current State Analysis

The existing settings architecture uses:
- `PreferenceFragmentCompat` with XML-based preferences
- `SharedPreferences` via `SharedPreferenceUtils.kt`
- Night mode support via `NightMode` class
- Fragmented preference fragments across `app/src/main/kotlin/com/termux/app/fragments/settings/`

**Key files to migrate:**
```
app/src/main/kotlin/com/termux/app/activities/SettingsActivity.kt
app/src/main/kotlin/com/termux/app/fragments/settings/TermuxPreferencesFragment.kt
app/src/main/res/xml/root_preferences.xml
termux-shared/src/main/kotlin/com/termux/shared/settings/preferences/
```

---

## A. Material 3 Settings Architecture

### Target Directory Structure

```
app/src/main/kotlin/com/termux/app/ui/settings/
├── SettingsActivity.kt              # Compose host activity
├── SettingsViewModel.kt             # ViewModel with DataStore
├── SettingsScreen.kt                # Main Compose screen
├── SettingsNavigation.kt            # Navigation graph
├── components/
│   ├── SettingsCategory.kt          # Reusable category component
│   ├── SettingsItem.kt              # Single preference item
│   ├── SettingsSwitch.kt            # Toggle preference
│   ├── SettingsSlider.kt            # Range preference
│   ├── SettingsDropdown.kt          # Selection preference
│   └── SearchBar.kt                 # Settings search
├── sections/
│   ├── AppearanceSettingsSection.kt
│   ├── BehaviorSettingsSection.kt
│   ├── KeyboardSettingsSection.kt
│   ├── TerminalSettingsSection.kt
│   ├── PluginSettingsSection.kt
│   └── ProfileSettingsSection.kt
└── data/
    ├── SettingsDataStore.kt         # DataStore wrapper
    ├── SettingsRepository.kt        # Settings abstraction
    └── SettingsState.kt             # UI state models
```

### Step 1: Create DataStore Migration Layer

**File: `app/src/main/kotlin/com/termux/app/ui/settings/data/SettingsDataStore.kt`**

```kotlin
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

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Appearance Keys
    object Keys {
        val FONT_SIZE = intPreferencesKey("font_size")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val THEME_NAME = stringPreferencesKey("theme_name")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val LIGATURES_ENABLED = booleanPreferencesKey("ligatures_enabled")
        
        // Behavior Keys
        val SOFT_KEYBOARD_ENABLED = booleanPreferencesKey("soft_keyboard_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val TOOLBAR_VISIBLE = booleanPreferencesKey("toolbar_visible")
        val BELL_ENABLED = booleanPreferencesKey("bell_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        
        // Terminal Keys
        val SCROLL_SENSITIVITY = floatPreferencesKey("scroll_sensitivity")
        val BACK_KEY_BEHAVIOR = stringPreferencesKey("back_key_behavior")
        val VOLUME_KEYS_BEHAVIOR = stringPreferencesKey("volume_keys_behavior")
        
        // Profile Keys
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    }
    
    val settingsFlow: Flow<TermuxSettings> = context.settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            TermuxSettings(
                fontSize = prefs[Keys.FONT_SIZE] ?: 14,
                fontFamily = prefs[Keys.FONT_FAMILY] ?: "default",
                themeName = prefs[Keys.THEME_NAME] ?: "dark_steel",
                dynamicColors = prefs[Keys.DYNAMIC_COLORS] ?: false,
                lineSpacing = prefs[Keys.LINE_SPACING] ?: 1.0f,
                ligaturesEnabled = prefs[Keys.LIGATURES_ENABLED] ?: false,
                softKeyboardEnabled = prefs[Keys.SOFT_KEYBOARD_ENABLED] ?: true,
                keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
                toolbarVisible = prefs[Keys.TOOLBAR_VISIBLE] ?: true,
                bellEnabled = prefs[Keys.BELL_ENABLED] ?: true,
                vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
                scrollSensitivity = prefs[Keys.SCROLL_SENSITIVITY] ?: 1.0f,
                backKeyBehavior = BackKeyBehavior.valueOf(
                    prefs[Keys.BACK_KEY_BEHAVIOR] ?: BackKeyBehavior.ESCAPE.name
                ),
                volumeKeysBehavior = VolumeKeysBehavior.valueOf(
                    prefs[Keys.VOLUME_KEYS_BEHAVIOR] ?: VolumeKeysBehavior.VOLUME.name
                ),
                activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID]
            )
        }
    
    suspend fun <T> updateSetting(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { prefs ->
            prefs[key] = value
        }
    }
}

data class TermuxSettings(
    val fontSize: Int,
    val fontFamily: String,
    val themeName: String,
    val dynamicColors: Boolean,
    val lineSpacing: Float,
    val ligaturesEnabled: Boolean,
    val softKeyboardEnabled: Boolean,
    val keepScreenOn: Boolean,
    val toolbarVisible: Boolean,
    val bellEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val scrollSensitivity: Float,
    val backKeyBehavior: BackKeyBehavior,
    val volumeKeysBehavior: VolumeKeysBehavior,
    val activeProfileId: String?
)

enum class BackKeyBehavior { BACK, ESCAPE }
enum class VolumeKeysBehavior { VOLUME, FONT_SIZE }
```

### Step 2: Create SettingsViewModel

**File: `app/src/main/kotlin/com/termux/app/ui/settings/SettingsViewModel.kt`**

```kotlin
package com.termux.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.app.ui.settings.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val profileRepository: ProfileRepository,
    private val themeRepository: ThemeRepository
) : ViewModel() {
    
    val settings: StateFlow<TermuxSettings?> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    val profiles: StateFlow<List<Profile>> = profileRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    val themes: StateFlow<List<Theme>> = themeRepository.getAllThemes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun updateFontSize(size: Int) = viewModelScope.launch {
        settingsDataStore.updateSetting(SettingsDataStore.Keys.FONT_SIZE, size)
    }
    
    fun updateTheme(themeName: String) = viewModelScope.launch {
        settingsDataStore.updateSetting(SettingsDataStore.Keys.THEME_NAME, themeName)
    }
    
    fun updateDynamicColors(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.updateSetting(SettingsDataStore.Keys.DYNAMIC_COLORS, enabled)
    }
    
    fun activateProfile(profileId: String) = viewModelScope.launch {
        profileRepository.activateProfile(profileId)
    }
    
    // Additional update methods...
}
```

### Step 3: Create Compose Settings Screen

**File: `app/src/main/kotlin/com/termux/app/ui/settings/SettingsScreen.kt`**

```kotlin
package com.termux.app.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.termux.app.ui.settings.components.*
import com.termux.app.ui.settings.sections.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            if (isSearchActive) {
                SearchTopBar(
                    query = searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    onClose = { 
                        isSearchActive = false
                        viewModel.updateSearchQuery("")
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, "Search settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        settings?.let { currentSettings ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Profiles Section
                item {
                    ProfilesSection(
                        viewModel = viewModel,
                        searchQuery = searchQuery
                    )
                }
                
                // Appearance Section
                item {
                    AppearanceSettingsSection(
                        settings = currentSettings,
                        onFontSizeChange = viewModel::updateFontSize,
                        onThemeChange = viewModel::updateTheme,
                        onDynamicColorsChange = viewModel::updateDynamicColors,
                        searchQuery = searchQuery
                    )
                }
                
                // Behavior Section
                item {
                    BehaviorSettingsSection(
                        settings = currentSettings,
                        viewModel = viewModel,
                        searchQuery = searchQuery
                    )
                }
                
                // Terminal Section
                item {
                    TerminalSettingsSection(
                        settings = currentSettings,
                        viewModel = viewModel,
                        searchQuery = searchQuery
                    )
                }
                
                // Keyboard Section
                item {
                    KeyboardSettingsSection(
                        settings = currentSettings,
                        viewModel = viewModel,
                        searchQuery = searchQuery
                    )
                }
                
                // Plugins Section
                item {
                    PluginSettingsSection(
                        viewModel = viewModel,
                        searchQuery = searchQuery
                    )
                }
            }
        } ?: LoadingIndicator()
    }
}
```

### Step 4: Create Reusable Components

**File: `app/src/main/kotlin/com/termux/app/ui/settings/components/SettingsCategory.kt`**

```kotlin
package com.termux.app.ui.settings.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SettingsCategory(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Content
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 56.dp,
                            end = 16.dp,
                            bottom = 16.dp
                        )
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface 
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun SettingsSliderItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            valueLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}
```

---

## B. Profile System

### Data Models

**File: `app/src/main/kotlin/com/termux/app/ui/settings/data/Profile.kt`**

```kotlin
package com.termux.app.ui.settings.data

import androidx.room.*
import kotlinx.serialization.Serializable

@Entity(tableName = "profiles")
@Serializable
data class Profile(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Appearance
    val fontFamily: String = "default",
    val fontSize: Int = 14,
    val themeName: String = "dark_steel",
    val lineSpacing: Float = 1.0f,
    val ligaturesEnabled: Boolean = false,
    
    // Shell
    val shell: String = "/data/data/com.termux/files/usr/bin/bash",
    val startupCommands: List<String> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    
    // Behavior
    val bellEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    
    // Keyboard
    val extraKeysStyle: String = "default",
    val softKeyboardEnabled: Boolean = true,
    
    // Plugins
    val enabledPlugins: Set<String> = emptySet()
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name ASC")
    fun getAllProfiles(): kotlinx.coroutines.flow.Flow<List<Profile>>
    
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfile(id: String): Profile?
    
    @Query("SELECT * FROM profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): Profile?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile)
    
    @Delete
    suspend fun deleteProfile(profile: Profile)
    
    @Query("UPDATE profiles SET isDefault = 0")
    suspend fun clearDefaultProfile()
    
    @Query("UPDATE profiles SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultProfile(id: String)
}
```

**File: `app/src/main/kotlin/com/termux/app/ui/settings/data/ProfileRepository.kt`**

```kotlin
package com.termux.app.ui.settings.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val settingsDataStore: SettingsDataStore
) {
    fun getAllProfiles(): Flow<List<Profile>> = profileDao.getAllProfiles()
    
    suspend fun getProfile(id: String): Profile? = profileDao.getProfile(id)
    
    suspend fun createProfile(
        name: String,
        description: String? = null,
        copyFrom: Profile? = null
    ): Profile {
        val profile = copyFrom?.copy(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            isDefault = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ) ?: Profile(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description
        )
        profileDao.insertProfile(profile)
        return profile
    }
    
    suspend fun updateProfile(profile: Profile) {
        profileDao.insertProfile(profile.copy(updatedAt = System.currentTimeMillis()))
    }
    
    suspend fun deleteProfile(profile: Profile) {
        profileDao.deleteProfile(profile)
    }
    
    suspend fun activateProfile(profileId: String) {
        settingsDataStore.updateSetting(
            SettingsDataStore.Keys.ACTIVE_PROFILE_ID, 
            profileId
        )
        // Apply profile settings to DataStore
        getProfile(profileId)?.let { profile ->
            settingsDataStore.updateSetting(SettingsDataStore.Keys.FONT_SIZE, profile.fontSize)
            settingsDataStore.updateSetting(SettingsDataStore.Keys.FONT_FAMILY, profile.fontFamily)
            settingsDataStore.updateSetting(SettingsDataStore.Keys.THEME_NAME, profile.themeName)
            settingsDataStore.updateSetting(SettingsDataStore.Keys.LINE_SPACING, profile.lineSpacing)
            settingsDataStore.updateSetting(SettingsDataStore.Keys.LIGATURES_ENABLED, profile.ligaturesEnabled)
            // Apply other settings...
        }
    }
    
    suspend fun setDefaultProfile(profileId: String) {
        profileDao.clearDefaultProfile()
        profileDao.setDefaultProfile(profileId)
    }
    
    suspend fun exportProfile(profile: Profile): String {
        return kotlinx.serialization.json.Json.encodeToString(Profile.serializer(), profile)
    }
    
    suspend fun importProfile(json: String): Profile {
        val imported = kotlinx.serialization.json.Json.decodeFromString(Profile.serializer(), json)
        val newProfile = imported.copy(
            id = UUID.randomUUID().toString(),
            name = "${imported.name} (Imported)",
            isDefault = false
        )
        profileDao.insertProfile(newProfile)
        return newProfile
    }
}
```

### Profile UI Section

**File: `app/src/main/kotlin/com/termux/app/ui/settings/sections/ProfilesSection.kt`**

```kotlin
package com.termux.app.ui.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.termux.app.ui.settings.SettingsViewModel
import com.termux.app.ui.settings.components.SettingsCategory
import com.termux.app.ui.settings.data.Profile

@Composable
fun ProfilesSection(
    viewModel: SettingsViewModel,
    searchQuery: String
) {
    val profiles by viewModel.profiles.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    
    val isVisible = searchQuery.isEmpty() || 
        "profile".contains(searchQuery, ignoreCase = true)
    
    SettingsCategory(
        title = "Profiles",
        icon = Icons.Default.Person,
        isVisible = isVisible
    ) {
        // Active profile indicator
        settings?.activeProfileId?.let { activeId ->
            profiles.find { it.id == activeId }?.let { active ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, "Active", 
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(active.name, style = MaterialTheme.typography.titleSmall)
                            active.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        
        // Profile carousel
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(profiles) { profile ->
                ProfileCard(
                    profile = profile,
                    isActive = profile.id == settings?.activeProfileId,
                    onClick = { viewModel.activateProfile(profile.id) }
                )
            }
            item {
                // Add profile button
                OutlinedCard(
                    modifier = Modifier
                        .size(120.dp, 80.dp)
                        .clickable { showCreateDialog = true }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Add, "Add profile")
                            Text("New", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
    
    if (showCreateDialog) {
        CreateProfileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                // viewModel.createProfile(name, description)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(120.dp, 80.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                MaterialTheme.colorScheme.primaryContainer 
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Theme indicator
                Surface(
                    modifier = Modifier.size(16.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primary
                ) {}
                Text(
                    text = profile.fontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, description.ifEmpty { null }) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

---

## C. Built-In Theme Gallery

### Theme Data Model

**File: `app/src/main/kotlin/com/termux/app/ui/settings/data/Theme.kt`**

```kotlin
package com.termux.app.ui.settings.data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
data class Theme(
    val id: String,
    val name: String,
    val author: String? = null,
    val isBuiltIn: Boolean = false,
    val isPluginProvided: Boolean = false,
    val pluginId: String? = null,
    
    // Terminal colors
    val background: Long,
    val foreground: Long,
    val cursor: Long,
    val cursorForeground: Long,
    
    // ANSI colors (0-15)
    val color0: Long,   // Black
    val color1: Long,   // Red
    val color2: Long,   // Green
    val color3: Long,   // Yellow
    val color4: Long,   // Blue
    val color5: Long,   // Magenta
    val color6: Long,   // Cyan
    val color7: Long,   // White
    val color8: Long,   // Bright Black
    val color9: Long,   // Bright Red
    val color10: Long,  // Bright Green
    val color11: Long,  // Bright Yellow
    val color12: Long,  // Bright Blue
    val color13: Long,  // Bright Magenta
    val color14: Long,  // Bright Cyan
    val color15: Long   // Bright White
) {
    companion object {
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
            color4 = 0xFF0000ff,
            color5 = 0xFFff00ff,
            color6 = 0xFF00ffff,
            color7 = 0xFFffffff,
            color8 = 0xFF808080,
            color9 = 0xFFff0000,
            color10 = 0xFF00ff00,
            color11 = 0xFFffff00,
            color12 = 0xFF0000ff,
            color13 = 0xFFff00ff,
            color14 = 0xFF00ffff,
            color15 = 0xFFffffff
        )
        
        val BUILT_IN_THEMES = listOf(
            DARK_STEEL,
            MOLTEN_BLUE,
            OBSIDIAN,
            SOLARIZED_DARK,
            GRUVBOX_DARK,
            HIGH_CONTRAST
        )
    }
}
```

### Theme Gallery UI

**File: `app/src/main/kotlin/com/termux/app/ui/settings/sections/ThemeGallerySection.kt`**

```kotlin
package com.termux.app.ui.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
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
import com.termux.app.ui.settings.data.Theme

@Composable
fun ThemeGalleryScreen(
    themes: List<Theme>,
    currentThemeId: String,
    onThemeSelect: (Theme) -> Unit,
    onImportTheme: () -> Unit,
    onExportTheme: (Theme) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme Gallery") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onImportTheme) {
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
                    onClick = { onThemeSelect(theme) },
                    onExport = { onExportTheme(theme) }
                )
            }
        }
    }
}

@Composable
fun ThemePreviewCard(
    theme: Theme,
    isSelected: Boolean,
    onClick: () -> Unit,
    onExport: () -> Unit
) {
    val bgColor = Color(theme.background)
    val fgColor = Color(theme.foreground)
    val cursorColor = Color(theme.cursor)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
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
            // Terminal preview
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
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row {
                        Text("drwxr-xr-x ", color = Color(theme.color4), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("home", color = Color(theme.color6), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Row {
                        Text("-rw-r--r-- ", color = Color(theme.color2), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("file.txt", color = fgColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    // Cursor
                    Row {
                        Text("$ ", color = fgColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Box(
                            modifier = Modifier
                                .size(6.dp, 12.dp)
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
                            .height(8.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(Color(color))
                    )
                }
            }
            
            Spacer(Modifier.height(4.dp))
        }
    }
}
```

---

# 📦 #5 — Package Management Enhancements

## Current State Analysis

The existing package infrastructure includes:
- `TermuxBootstrap.kt` - Package manager abstraction (APT only)
- `TermuxInstaller.kt` - Bootstrap installation
- `apt_info_script.sh` - APT repository querying
- `ExecutionCommand.kt` - Command execution abstraction
- `AppShell.kt` - Background process execution

**No existing backup/restore functionality.**

---

## A. Package Backup & Restore System

### Architecture Overview

```
app/src/main/kotlin/com/termux/app/pkg/
├── backup/
│   ├── PackageBackupManager.kt      # Orchestrates backup/restore
│   ├── BackupConfig.kt              # Backup configuration
│   ├── BackupMetadata.kt            # Backup file metadata
│   ├── BackupWorker.kt              # WorkManager background task
│   └── serializers/
│       ├── PackageListSerializer.kt
│       ├── RepositorySerializer.kt
│       └── DotfilesSerializer.kt
├── doctor/
│   ├── PackageDoctor.kt             # Health check orchestrator
│   ├── checks/
│   │   ├── DependencyCheck.kt
│   │   ├── BrokenPackageCheck.kt
│   │   ├── VersionMismatchCheck.kt
│   │   └── OrphanedPackageCheck.kt
│   └── DiagnosticResult.kt
├── repository/
│   ├── RepositoryManager.kt
│   ├── Repository.kt
│   └── RepositoryValidator.kt
└── cli/
    ├── TermuxCtl.kt                 # CLI entry point
    └── commands/
        ├── BackupCommand.kt
        ├── RestoreCommand.kt
        ├── DoctorCommand.kt
        └── RepoCommand.kt
```

### Backup Data Model

**File: `app/src/main/kotlin/com/termux/app/pkg/backup/BackupMetadata.kt`**

```kotlin
package com.termux.app.pkg.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupMetadata(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val termuxVersion: String,
    val androidVersion: Int,
    val deviceModel: String,
    val backupType: BackupType,
    val contents: BackupContents
)

@Serializable
data class BackupContents(
    val packages: List<PackageInfo>,
    val repositories: List<RepositoryInfo>,
    val heldPackages: List<String>,
    val includedDotfiles: List<String> = emptyList(),
    val includedUserData: Boolean = false
)

@Serializable
data class PackageInfo(
    val name: String,
    val version: String,
    val architecture: String,
    val isManuallyInstalled: Boolean,
    val repository: String?
)

@Serializable
data class RepositoryInfo(
    val name: String,
    val url: String,
    val components: List<String>,
    val isEnabled: Boolean,
    val fingerprint: String?
)

enum class BackupType {
    FULL,           // All packages + config
    PACKAGES_ONLY,  // Only package list
    CONFIG_ONLY,    // Only dotfiles and settings
    MINIMAL         // Only manually installed packages
}
```

### Backup Manager

**File: `app/src/main/kotlin/com/termux/app/pkg/backup/PackageBackupManager.kt`**

```kotlin
package com.termux.app.pkg.backup

import android.content.Context
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.runner.app.AppShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageBackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { 
        prettyPrint = true 
        ignoreUnknownKeys = true
    }
    
    private val _backupProgress = MutableStateFlow<BackupProgress>(BackupProgress.Idle)
    val backupProgress: StateFlow<BackupProgress> = _backupProgress.asStateFlow()
    
    suspend fun createBackup(
        config: BackupConfig,
        outputPath: String
    ): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        try {
            _backupProgress.value = BackupProgress.Starting
            
            // Step 1: Get installed packages
            _backupProgress.value = BackupProgress.CollectingPackages
            val packages = collectInstalledPackages()
            
            // Step 2: Get repositories
            _backupProgress.value = BackupProgress.CollectingRepositories
            val repositories = collectRepositories()
            
            // Step 3: Get held packages
            val heldPackages = collectHeldPackages()
            
            // Step 4: Collect dotfiles if requested
            val dotfiles = if (config.includeDotfiles) {
                _backupProgress.value = BackupProgress.CollectingDotfiles
                collectDotfiles(config.dotfilePaths)
            } else emptyList()
            
            // Step 5: Create metadata
            val metadata = BackupMetadata(
                termuxVersion = getTermuxVersion(),
                androidVersion = android.os.Build.VERSION.SDK_INT,
                deviceModel = android.os.Build.MODEL,
                backupType = config.backupType,
                contents = BackupContents(
                    packages = packages,
                    repositories = repositories,
                    heldPackages = heldPackages,
                    includedDotfiles = dotfiles,
                    includedUserData = config.includeUserData
                )
            )
            
            // Step 6: Write backup file
            _backupProgress.value = BackupProgress.Writing
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            
            if (config.backupType == BackupType.FULL && config.includeUserData) {
                // Create tar.gz with metadata + data
                createFullBackupArchive(metadata, config, outputPath)
            } else {
                // JSON-only backup
                outputFile.writeText(json.encodeToString(metadata))
            }
            
            _backupProgress.value = BackupProgress.Completed(outputPath)
            Result.success(metadata)
            
        } catch (e: Exception) {
            _backupProgress.value = BackupProgress.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
    
    suspend fun restoreBackup(
        backupPath: String,
        options: RestoreOptions
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        try {
            _backupProgress.value = BackupProgress.Starting
            
            // Step 1: Parse backup
            val metadata = parseBackup(backupPath)
            
            // Step 2: Validate compatibility
            if (!options.skipCompatibilityCheck) {
                validateCompatibility(metadata)
            }
            
            // Step 3: Dry run if requested
            if (options.dryRun) {
                return@withContext Result.success(
                    RestoreResult(
                        isDryRun = true,
                        packagesToInstall = metadata.contents.packages.map { it.name },
                        repositoriesToAdd = metadata.contents.repositories.map { it.name },
                        warnings = emptyList()
                    )
                )
            }
            
            // Step 4: Restore repositories
            _backupProgress.value = BackupProgress.RestoringRepositories
            metadata.contents.repositories
                .filter { options.restoreRepositories }
                .forEach { repo -> addRepository(repo) }
            
            // Step 5: Update package lists
            _backupProgress.value = BackupProgress.UpdatingPackageLists
            runCommand("apt update")
            
            // Step 6: Install packages
            _backupProgress.value = BackupProgress.InstallingPackages(0, metadata.contents.packages.size)
            val packagesToInstall = if (options.selectiveRestore) {
                options.selectedPackages
            } else {
                metadata.contents.packages.map { it.name }
            }
            
            val installResult = installPackages(packagesToInstall) { current, total ->
                _backupProgress.value = BackupProgress.InstallingPackages(current, total)
            }
            
            // Step 7: Restore held packages
            metadata.contents.heldPackages.forEach { pkg ->
                runCommand("apt-mark hold $pkg")
            }
            
            // Step 8: Restore dotfiles if included
            if (metadata.contents.includedDotfiles.isNotEmpty() && options.restoreDotfiles) {
                _backupProgress.value = BackupProgress.RestoringDotfiles
                restoreDotfiles(backupPath, metadata.contents.includedDotfiles)
            }
            
            _backupProgress.value = BackupProgress.Completed(backupPath)
            Result.success(
                RestoreResult(
                    isDryRun = false,
                    packagesToInstall = packagesToInstall,
                    repositoriesToAdd = metadata.contents.repositories.map { it.name },
                    warnings = installResult.warnings
                )
            )
            
        } catch (e: Exception) {
            _backupProgress.value = BackupProgress.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
    
    private suspend fun collectInstalledPackages(): List<PackageInfo> {
        val output = runCommand("dpkg-query -W -f='\${Package}|\${Version}|\${Architecture}|\${Status}\\n'")
        val manuallyInstalled = runCommand("apt-mark showmanual").lines().toSet()
        
        return output.lines()
            .filter { it.isNotBlank() && it.contains("install ok installed") }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 3) {
                    PackageInfo(
                        name = parts[0],
                        version = parts[1],
                        architecture = parts[2],
                        isManuallyInstalled = parts[0] in manuallyInstalled,
                        repository = null
                    )
                } else null
            }
    }
    
    private suspend fun collectRepositories(): List<RepositoryInfo> {
        val repoList = mutableListOf<RepositoryInfo>()
        
        // Parse sources.list
        val sourcesListContent = runCommand("cat /data/data/com.termux/files/usr/etc/apt/sources.list 2>/dev/null || true")
        repoList.addAll(parseSourcesList(sourcesListContent, "main"))
        
        // Parse sources.list.d
        val sourcesListD = runCommand("ls /data/data/com.termux/files/usr/etc/apt/sources.list.d/*.list 2>/dev/null || true")
        sourcesListD.lines().filter { it.endsWith(".list") }.forEach { file ->
            val content = runCommand("cat $file")
            val name = File(file).nameWithoutExtension
            repoList.addAll(parseSourcesList(content, name))
        }
        
        return repoList
    }
    
    private fun parseSourcesList(content: String, sourceName: String): List<RepositoryInfo> {
        return content.lines()
            .filter { it.startsWith("deb ") && !it.startsWith("#") }
            .map { line ->
                val parts = line.removePrefix("deb ").split(" ")
                RepositoryInfo(
                    name = sourceName,
                    url = parts.getOrNull(0) ?: "",
                    components = parts.drop(1),
                    isEnabled = true,
                    fingerprint = null
                )
            }
    }
    
    private suspend fun collectHeldPackages(): List<String> {
        return runCommand("apt-mark showhold").lines().filter { it.isNotBlank() }
    }
    
    private suspend fun runCommand(command: String): String {
        return withContext(Dispatchers.IO) {
            val process = Runtime.getRuntime().exec(arrayOf(
                "/data/data/com.termux/files/usr/bin/bash",
                "-c",
                command
            ))
            process.inputStream.bufferedReader().readText().also {
                process.waitFor()
            }
        }
    }
    
    private fun getTermuxVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    // Additional helper methods...
}

sealed class BackupProgress {
    object Idle : BackupProgress()
    object Starting : BackupProgress()
    object CollectingPackages : BackupProgress()
    object CollectingRepositories : BackupProgress()
    object CollectingDotfiles : BackupProgress()
    object Writing : BackupProgress()
    object RestoringRepositories : BackupProgress()
    object UpdatingPackageLists : BackupProgress()
    data class InstallingPackages(val current: Int, val total: Int) : BackupProgress()
    object RestoringDotfiles : BackupProgress()
    data class Completed(val path: String) : BackupProgress()
    data class Failed(val error: String) : BackupProgress()
}

data class BackupConfig(
    val backupType: BackupType = BackupType.FULL,
    val includeDotfiles: Boolean = true,
    val dotfilePaths: List<String> = listOf(
        ".bashrc", ".zshrc", ".profile", ".vimrc", ".gitconfig",
        ".config/nvim", ".tmux.conf", ".ssh/config"
    ),
    val includeUserData: Boolean = false
)

data class RestoreOptions(
    val dryRun: Boolean = false,
    val skipCompatibilityCheck: Boolean = false,
    val restoreRepositories: Boolean = true,
    val restoreDotfiles: Boolean = true,
    val selectiveRestore: Boolean = false,
    val selectedPackages: List<String> = emptyList()
)

data class RestoreResult(
    val isDryRun: Boolean,
    val packagesToInstall: List<String>,
    val repositoriesToAdd: List<String>,
    val warnings: List<String>
)
```

---

## B. Package Health Checks (Doctor)

**File: `app/src/main/kotlin/com/termux/app/pkg/doctor/PackageDoctor.kt`**

```kotlin
package com.termux.app.pkg.doctor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageDoctor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _diagnosticProgress = MutableStateFlow<DiagnosticProgress>(DiagnosticProgress.Idle)
    val diagnosticProgress: StateFlow<DiagnosticProgress> = _diagnosticProgress.asStateFlow()
    
    suspend fun runFullDiagnostic(): DiagnosticReport = withContext(Dispatchers.IO) {
        val issues = mutableListOf<DiagnosticIssue>()
        val recommendations = mutableListOf<String>()
        
        _diagnosticProgress.value = DiagnosticProgress.Running("Checking broken packages...")
        
        // Check 1: Broken packages
        val brokenPackages = checkBrokenPackages()
        issues.addAll(brokenPackages)
        
        _diagnosticProgress.value = DiagnosticProgress.Running("Checking dependencies...")
        
        // Check 2: Missing dependencies
        val missingDeps = checkMissingDependencies()
        issues.addAll(missingDeps)
        
        _diagnosticProgress.value = DiagnosticProgress.Running("Checking held packages...")
        
        // Check 3: Held packages
        val heldPackages = checkHeldPackages()
        issues.addAll(heldPackages)
        
        _diagnosticProgress.value = DiagnosticProgress.Running("Checking version mismatches...")
        
        // Check 4: Version mismatches
        val versionIssues = checkVersionMismatches()
        issues.addAll(versionIssues)
        
        _diagnosticProgress.value = DiagnosticProgress.Running("Checking orphaned packages...")
        
        // Check 5: Orphaned packages
        val orphanedPackages = checkOrphanedPackages()
        issues.addAll(orphanedPackages)
        
        _diagnosticProgress.value = DiagnosticProgress.Running("Checking repository health...")
        
        // Check 6: Repository health
        val repoIssues = checkRepositoryHealth()
        issues.addAll(repoIssues)
        
        // Generate recommendations
        if (brokenPackages.isNotEmpty()) {
            recommendations.add("Run 'apt --fix-broken install' to repair broken packages")
        }
        if (missingDeps.isNotEmpty()) {
            recommendations.add("Run 'apt install -f' to install missing dependencies")
        }
        if (orphanedPackages.size > 10) {
            recommendations.add("Run 'apt autoremove' to clean up orphaned packages")
        }
        
        _diagnosticProgress.value = DiagnosticProgress.Completed
        
        DiagnosticReport(
            timestamp = System.currentTimeMillis(),
            issues = issues,
            recommendations = recommendations,
            healthScore = calculateHealthScore(issues)
        )
    }
    
    private suspend fun checkBrokenPackages(): List<DiagnosticIssue> {
        val output = runCommand("dpkg --audit")
        if (output.isBlank()) return emptyList()
        
        return output.lines()
            .filter { it.isNotBlank() }
            .map { line ->
                DiagnosticIssue(
                    type = IssueType.BROKEN_PACKAGE,
                    severity = IssueSeverity.HIGH,
                    description = "Broken package detected: $line",
                    affectedPackage = line.split(" ").firstOrNull(),
                    suggestedFix = "apt --fix-broken install"
                )
            }
    }
    
    private suspend fun checkMissingDependencies(): List<DiagnosticIssue> {
        val output = runCommand("apt-get check 2>&1")
        val issues = mutableListOf<DiagnosticIssue>()
        
        if (output.contains("unmet dependencies")) {
            val depRegex = Regex("(\\S+) : Depends: (\\S+)")
            depRegex.findAll(output).forEach { match ->
                issues.add(
                    DiagnosticIssue(
                        type = IssueType.MISSING_DEPENDENCY,
                        severity = IssueSeverity.HIGH,
                        description = "${match.groupValues[1]} is missing dependency ${match.groupValues[2]}",
                        affectedPackage = match.groupValues[1],
                        suggestedFix = "apt install ${match.groupValues[2]}"
                    )
                )
            }
        }
        
        return issues
    }
    
    private suspend fun checkHeldPackages(): List<DiagnosticIssue> {
        val heldPackages = runCommand("apt-mark showhold").lines().filter { it.isNotBlank() }
        
        return heldPackages.map { pkg ->
            DiagnosticIssue(
                type = IssueType.HELD_PACKAGE,
                severity = IssueSeverity.LOW,
                description = "Package '$pkg' is held back from upgrades",
                affectedPackage = pkg,
                suggestedFix = "apt-mark unhold $pkg"
            )
        }
    }
    
    private suspend fun checkVersionMismatches(): List<DiagnosticIssue> {
        val output = runCommand("apt list --upgradable 2>/dev/null")
        val issues = mutableListOf<DiagnosticIssue>()
        
        output.lines()
            .filter { it.contains("[upgradable from:") }
            .take(20) // Limit to prevent noise
            .forEach { line ->
                val pkg = line.split("/").firstOrNull()
                if (pkg != null) {
                    issues.add(
                        DiagnosticIssue(
                            type = IssueType.VERSION_MISMATCH,
                            severity = IssueSeverity.LOW,
                            description = "Package '$pkg' has an update available",
                            affectedPackage = pkg,
                            suggestedFix = "apt upgrade $pkg"
                        )
                    )
                }
            }
        
        return issues
    }
    
    private suspend fun checkOrphanedPackages(): List<DiagnosticIssue> {
        val output = runCommand("apt-get autoremove --dry-run 2>/dev/null | grep 'Remv' || true")
        
        return output.lines()
            .filter { it.startsWith("Remv ") }
            .map { line ->
                val pkg = line.removePrefix("Remv ").split(" ").firstOrNull()
                DiagnosticIssue(
                    type = IssueType.ORPHANED_PACKAGE,
                    severity = IssueSeverity.INFO,
                    description = "Package '$pkg' is no longer needed",
                    affectedPackage = pkg,
                    suggestedFix = "apt autoremove"
                )
            }
    }
    
    private suspend fun checkRepositoryHealth(): List<DiagnosticIssue> {
        val output = runCommand("apt update 2>&1")
        val issues = mutableListOf<DiagnosticIssue>()
        
        if (output.contains("Failed to fetch")) {
            val failedRegex = Regex("Failed to fetch (\\S+)")
            failedRegex.findAll(output).forEach { match ->
                issues.add(
                    DiagnosticIssue(
                        type = IssueType.REPOSITORY_ERROR,
                        severity = IssueSeverity.MEDIUM,
                        description = "Failed to fetch from repository: ${match.groupValues[1]}",
                        affectedPackage = null,
                        suggestedFix = "Check network connection or remove invalid repository"
                    )
                )
            }
        }
        
        if (output.contains("NO_PUBKEY")) {
            val keyRegex = Regex("NO_PUBKEY (\\S+)")
            keyRegex.findAll(output).forEach { match ->
                issues.add(
                    DiagnosticIssue(
                        type = IssueType.MISSING_GPG_KEY,
                        severity = IssueSeverity.MEDIUM,
                        description = "Missing GPG key: ${match.groupValues[1]}",
                        affectedPackage = null,
                        suggestedFix = "apt-key adv --keyserver keyserver.ubuntu.com --recv-keys ${match.groupValues[1]}"
                    )
                )
            }
        }
        
        return issues
    }
    
    private fun calculateHealthScore(issues: List<DiagnosticIssue>): Int {
        var score = 100
        issues.forEach { issue ->
            score -= when (issue.severity) {
                IssueSeverity.CRITICAL -> 25
                IssueSeverity.HIGH -> 15
                IssueSeverity.MEDIUM -> 10
                IssueSeverity.LOW -> 5
                IssueSeverity.INFO -> 1
            }
        }
        return score.coerceIn(0, 100)
    }
    
    suspend fun autoRepair(issues: List<DiagnosticIssue>): RepairResult {
        val repaired = mutableListOf<String>()
        val failed = mutableListOf<String>()
        
        // Group by fix to avoid duplicate commands
        val fixes = issues
            .filter { it.severity >= IssueSeverity.MEDIUM }
            .mapNotNull { it.suggestedFix }
            .distinct()
        
        fixes.forEach { fix ->
            try {
                runCommand(fix)
                repaired.add(fix)
            } catch (e: Exception) {
                failed.add("$fix: ${e.message}")
            }
        }
        
        return RepairResult(repaired, failed)
    }
    
    private suspend fun runCommand(command: String): String = withContext(Dispatchers.IO) {
        val process = Runtime.getRuntime().exec(arrayOf(
            "/data/data/com.termux/files/usr/bin/bash",
            "-c",
            command
        ))
        process.inputStream.bufferedReader().readText().also {
            process.waitFor()
        }
    }
}

data class DiagnosticReport(
    val timestamp: Long,
    val issues: List<DiagnosticIssue>,
    val recommendations: List<String>,
    val healthScore: Int
)

data class DiagnosticIssue(
    val type: IssueType,
    val severity: IssueSeverity,
    val description: String,
    val affectedPackage: String?,
    val suggestedFix: String?
)

enum class IssueType {
    BROKEN_PACKAGE,
    MISSING_DEPENDENCY,
    HELD_PACKAGE,
    VERSION_MISMATCH,
    ORPHANED_PACKAGE,
    REPOSITORY_ERROR,
    MISSING_GPG_KEY
}

enum class IssueSeverity {
    INFO, LOW, MEDIUM, HIGH, CRITICAL
}

data class RepairResult(
    val repaired: List<String>,
    val failed: List<String>
)

sealed class DiagnosticProgress {
    object Idle : DiagnosticProgress()
    data class Running(val step: String) : DiagnosticProgress()
    object Completed : DiagnosticProgress()
}
```

---

## C. termuxctl CLI Interface

**File: `app/src/main/kotlin/com/termux/app/pkg/cli/TermuxCtl.kt`**

```kotlin
package com.termux.app.pkg.cli

import com.termux.app.pkg.backup.*
import com.termux.app.pkg.doctor.*
import kotlinx.coroutines.runBlocking

/**
 * CLI entry point for termuxctl commands.
 * 
 * Usage:
 *   termuxctl backup create [--output <path>] [--type <full|packages|config|minimal>]
 *   termuxctl backup restore <path> [--dry-run] [--selective]
 *   termuxctl backup list
 *   
 *   termuxctl pkg doctor [--auto-repair]
 *   termuxctl pkg upgrade [--safe]
 *   
 *   termuxctl repo list
 *   termuxctl repo add <name> <url>
 *   termuxctl repo remove <name>
 *   termuxctl repo enable <name>
 *   termuxctl repo disable <name>
 *   
 *   termuxctl profile list
 *   termuxctl profile activate <name>
 *   termuxctl profile export <name> [--output <path>]
 *   termuxctl profile import <path>
 */
class TermuxCtl(
    private val backupManager: PackageBackupManager,
    private val doctor: PackageDoctor
) {
    fun execute(args: Array<String>): Int = runBlocking {
        if (args.isEmpty()) {
            printUsage()
            return@runBlocking 1
        }
        
        when (args[0]) {
            "backup" -> handleBackupCommand(args.drop(1))
            "pkg" -> handlePackageCommand(args.drop(1))
            "repo" -> handleRepoCommand(args.drop(1))
            "profile" -> handleProfileCommand(args.drop(1))
            "--help", "-h" -> { printUsage(); 0 }
            "--version", "-v" -> { println("termuxctl v1.0.0"); 0 }
            else -> { printError("Unknown command: ${args[0]}"); 1 }
        }
    }
    
    private suspend fun handleBackupCommand(args: List<String>): Int {
        if (args.isEmpty()) {
            printBackupUsage()
            return 1
        }
        
        return when (args[0]) {
            "create" -> {
                val options = parseBackupOptions(args.drop(1))
                val result = backupManager.createBackup(
                    config = BackupConfig(backupType = options.type),
                    outputPath = options.output
                )
                result.fold(
                    onSuccess = { 
                        println("✓ Backup created: ${options.output}")
                        println("  Packages: ${it.contents.packages.size}")
                        println("  Repositories: ${it.contents.repositories.size}")
                        0
                    },
                    onFailure = { 
                        printError("Backup failed: ${it.message}")
                        1
                    }
                )
            }
            "restore" -> {
                if (args.size < 2) {
                    printError("Missing backup path")
                    return 1
                }
                val options = parseRestoreOptions(args.drop(2))
                val result = backupManager.restoreBackup(args[1], options)
                result.fold(
                    onSuccess = {
                        if (it.isDryRun) {
                            println("Dry run results:")
                            println("  Would install ${it.packagesToInstall.size} packages")
                            println("  Would add ${it.repositoriesToAdd.size} repositories")
                        } else {
                            println("✓ Restore completed")
                            println("  Installed: ${it.packagesToInstall.size} packages")
                        }
                        0
                    },
                    onFailure = {
                        printError("Restore failed: ${it.message}")
                        1
                    }
                )
            }
            "list" -> {
                // List available backups
                listBackups()
                0
            }
            else -> {
                printError("Unknown backup command: ${args[0]}")
                1
            }
        }
    }
    
    private suspend fun handlePackageCommand(args: List<String>): Int {
        if (args.isEmpty()) {
            printPackageUsage()
            return 1
        }
        
        return when (args[0]) {
            "doctor" -> {
                val autoRepair = args.contains("--auto-repair")
                println("Running package diagnostics...")
                
                val report = doctor.runFullDiagnostic()
                
                println("\n╔══════════════════════════════════════╗")
                println("║      Package Health Report           ║")
                println("╠══════════════════════════════════════╣")
                println("║  Health Score: ${report.healthScore.toString().padStart(3)}%                  ║")
                println("║  Issues Found: ${report.issues.size.toString().padStart(3)}                   ║")
                println("╚══════════════════════════════════════╝\n")
                
                if (report.issues.isNotEmpty()) {
                    println("Issues:")
                    report.issues.groupBy { it.type }.forEach { (type, issues) ->
                        println("\n  ${type.name.replace("_", " ")} (${issues.size}):")
                        issues.take(5).forEach { issue ->
                            val icon = when (issue.severity) {
                                IssueSeverity.CRITICAL, IssueSeverity.HIGH -> "✗"
                                IssueSeverity.MEDIUM -> "!"
                                else -> "·"
                            }
                            println("    $icon ${issue.description}")
                        }
                        if (issues.size > 5) {
                            println("    ... and ${issues.size - 5} more")
                        }
                    }
                }
                
                if (report.recommendations.isNotEmpty()) {
                    println("\nRecommendations:")
                    report.recommendations.forEach { println("  → $it") }
                }
                
                if (autoRepair && report.issues.any { it.severity >= IssueSeverity.MEDIUM }) {
                    println("\nAttempting auto-repair...")
                    val repairResult = doctor.autoRepair(report.issues)
                    println("  Repaired: ${repairResult.repaired.size}")
                    println("  Failed: ${repairResult.failed.size}")
                }
                
                if (report.healthScore >= 90) 0 else 1
            }
            "upgrade" -> {
                val safeMode = args.contains("--safe")
                if (safeMode) {
                    println("Running safe upgrade (held packages preserved)...")
                    // Implement safe upgrade logic
                } else {
                    println("Running full upgrade...")
                }
                0
            }
            else -> {
                printError("Unknown package command: ${args[0]}")
                1
            }
        }
    }
    
    private fun handleRepoCommand(args: List<String>): Int {
        // Repository management commands
        return 0
    }
    
    private fun handleProfileCommand(args: List<String>): Int {
        // Profile management commands
        return 0
    }
    
    private fun printUsage() {
        println("""
            termuxctl - Termux Kotlin Control Utility
            
            Usage: termuxctl <command> [options]
            
            Commands:
              backup    Backup and restore package configurations
              pkg       Package management and health checks
              repo      Repository management
              profile   Profile management
            
            Run 'termuxctl <command> --help' for command-specific help.
        """.trimIndent())
    }
    
    private fun printBackupUsage() {
        println("""
            termuxctl backup - Backup and restore
            
            Commands:
              create [options]    Create a new backup
              restore <path>      Restore from backup
              list                List available backups
            
            Options for 'create':
              --output <path>     Output file path
              --type <type>       Backup type: full, packages, config, minimal
              --include-data      Include user data (larger backup)
            
            Options for 'restore':
              --dry-run           Show what would be restored without making changes
              --selective         Interactively select packages to restore
              --skip-repos        Don't restore repository configuration
        """.trimIndent())
    }
    
    private fun printPackageUsage() {
        println("""
            termuxctl pkg - Package management
            
            Commands:
              doctor [options]    Run package health diagnostics
              upgrade [options]   Upgrade packages
            
            Options for 'doctor':
              --auto-repair       Attempt to fix issues automatically
            
            Options for 'upgrade':
              --safe              Safe upgrade (preserves held packages)
        """.trimIndent())
    }
    
    private fun printError(message: String) {
        System.err.println("Error: $message")
    }
    
    // Helper methods for option parsing...
    private data class BackupOptions(val output: String, val type: BackupType)
    private fun parseBackupOptions(args: List<String>): BackupOptions {
        var output = "/data/data/com.termux/files/home/termux-backup-${System.currentTimeMillis()}.json"
        var type = BackupType.FULL
        
        args.forEachIndexed { index, arg ->
            when (arg) {
                "--output", "-o" -> output = args.getOrNull(index + 1) ?: output
                "--type", "-t" -> type = when (args.getOrNull(index + 1)) {
                    "packages" -> BackupType.PACKAGES_ONLY
                    "config" -> BackupType.CONFIG_ONLY
                    "minimal" -> BackupType.MINIMAL
                    else -> BackupType.FULL
                }
            }
        }
        
        return BackupOptions(output, type)
    }
    
    private fun parseRestoreOptions(args: List<String>): RestoreOptions {
        return RestoreOptions(
            dryRun = "--dry-run" in args,
            selectiveRestore = "--selective" in args,
            restoreRepositories = "--skip-repos" !in args
        )
    }
    
    private fun listBackups() {
        val backupDir = java.io.File("/data/data/com.termux/files/home")
        val backups = backupDir.listFiles { file -> 
            file.name.startsWith("termux-backup") && file.name.endsWith(".json")
        } ?: emptyArray()
        
        if (backups.isEmpty()) {
            println("No backups found.")
        } else {
            println("Available backups:")
            backups.sortedByDescending { it.lastModified() }.forEach { file ->
                println("  ${file.name} (${formatFileSize(file.length())})")
            }
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
```

---

## D. Repository Management UI

**File: `app/src/main/kotlin/com/termux/app/ui/settings/sections/RepositorySection.kt`**

```kotlin
package com.termux.app.ui.settings.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.termux.app.pkg.backup.RepositoryInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryManagementScreen(
    repositories: List<RepositoryInfo>,
    onAddRepository: (String, String) -> Unit,
    onRemoveRepository: (RepositoryInfo) -> Unit,
    onToggleRepository: (RepositoryInfo, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repositories") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add repository")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Package Sources",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
            }
            
            items(repositories) { repo ->
                RepositoryCard(
                    repository = repo,
                    onToggle = { enabled -> onToggleRepository(repo, enabled) },
                    onRemove = { onRemoveRepository(repo) }
                )
            }
            
            item {
                Spacer(Modifier.height(16.dp))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Repository Tips",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "• The main repository contains core Termux packages\n" +
                            "• Root repos require a rooted device\n" +
                            "• Science repos contain scientific computing packages\n" +
                            "• X11 repos contain graphical application packages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddRepositoryDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url ->
                onAddRepository(name, url)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun RepositoryCard(
    repository: RepositoryInfo,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (repository.isEnabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repository.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = repository.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (repository.components.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repository.components.forEach { component ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(component, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
            
            Switch(
                checked = repository.isEnabled,
                onCheckedChange = onToggle
            )
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("View details") },
                        onClick = { showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Info, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = {
                            showMenu = false
                            onRemove()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Repository") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Repository Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://example.com/termux main") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Format: deb [arch=...] <url> <suite> [components]",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

---

## 📋 Implementation Priority

### Phase 1 (Week 1-2) — Foundation
1. ✅ Create `SettingsDataStore.kt` - DataStore migration layer
2. ✅ Create `SettingsViewModel.kt` - ViewModel architecture
3. ✅ Create base Compose components
4. ✅ Implement `PackageBackupManager.kt` core

### Phase 2 (Week 3-4) — Core Features
1. ✅ Complete Settings Compose migration
2. ✅ Implement Profile system
3. ✅ Implement backup/restore CLI
4. ✅ Implement `PackageDoctor.kt`

### Phase 3 (Week 5-6) — Polish
1. ✅ Theme Gallery with live preview
2. ✅ Repository Management UI
3. ✅ termuxctl CLI completion
4. ✅ Integration testing

### Phase 4 (Week 7-8) — Advanced
1. ✅ Parallel downloads
2. ✅ Package event hooks
3. ✅ GPU-accelerated rendering research
4. ✅ Documentation

---

## 🔗 Integration Points

### Existing Code to Modify

| File | Change |
|------|--------|
| `SettingsActivity.kt` | Replace with Compose host |
| `TermuxPreferencesFragment.kt` | Deprecate, redirect to new UI |
| `SharedPreferenceUtils.kt` | Add DataStore migration helper |
| `TermuxBootstrap.kt` | Extend for backup/restore |
| `root_preferences.xml` | Keep for fallback, mark deprecated |

### New Dependencies

```gradle
// build.gradle (app)
dependencies {
    // DataStore
    implementation "androidx.datastore:datastore-preferences:1.1.0"
    
    // Compose
    implementation "androidx.compose.material3:material3:1.2.0"
    implementation "androidx.hilt:hilt-navigation-compose:1.2.0"
    
    // Room for profiles
    implementation "androidx.room:room-runtime:2.6.1"
    implementation "androidx.room:room-ktx:2.6.1"
    kapt "androidx.room:room-compiler:2.6.1"
    
    // Serialization
    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2"
    
    // WorkManager for background backups
    implementation "androidx.work:work-runtime-ktx:2.9.0"
}
```

---

## ✅ Success Metrics

| Metric | Target |
|--------|--------|
| Settings screen load time | < 200ms |
| Backup creation (packages only) | < 5s |
| Full restore (100 packages) | < 2min |
| Doctor scan time | < 10s |
| Profile switch latency | < 100ms |
| Theme preview FPS | 60fps |

---

This implementation plan provides everything needed to build both #4 and #5 from scratch. Each component is designed to integrate with the existing codebase while modernizing the architecture.
