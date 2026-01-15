package com.termux.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.app.ui.settings.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 * 
 * Manages UI state for settings, profiles, and themes with reactive updates.
 * All changes are automatically persisted via DataStore.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val profileRepository: ProfileRepository,
    private val themeRepository: ThemeRepository
) : ViewModel() {
    
    // ========== Settings State ==========
    
    /**
     * Current settings, emits on every change.
     */
    val settings: StateFlow<TermuxSettings?> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    // ========== Profile State ==========
    
    /**
     * All available profiles.
     */
    val profiles: StateFlow<List<Profile>> = profileRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    /**
     * Currently active profile.
     */
    val activeProfile: StateFlow<Profile?> = combine(
        settings.filterNotNull(),
        profiles
    ) { settings, profiles ->
        settings.activeProfileId?.let { id ->
            profiles.find { it.id == id }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    // ========== Theme State ==========
    
    /**
     * All available themes (built-in + custom + plugin).
     */
    val themes: StateFlow<List<Theme>> = themeRepository.getAllThemes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Theme.BUILT_IN_THEMES)
    
    /**
     * Currently active theme.
     */
    val activeTheme: StateFlow<Theme?> = combine(
        settings.filterNotNull(),
        themes
    ) { settings, themes ->
        themes.find { it.id == settings.themeName }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    // ========== Search State ==========
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()
    
    // ========== UI State ==========
    
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        // Update UI state when settings load
        viewModelScope.launch {
            settings.collect { s ->
                _uiState.value = if (s != null) {
                    SettingsUiState.Ready(s)
                } else {
                    SettingsUiState.Loading
                }
            }
        }
    }
    
    // ========== Search Actions ==========
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }
    
    // ========== Appearance Actions ==========
    
    fun updateFontSize(size: Int) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.FONT_SIZE, size)
        }
    }
    
    fun updateFontFamily(family: String) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.FONT_FAMILY, family)
        }
    }
    
    fun updateTheme(themeName: String) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.THEME_NAME, themeName)
        }
    }
    
    fun updateDynamicColors(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.DYNAMIC_COLORS, enabled)
        }
    }
    
    fun updateLineSpacing(spacing: Float) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.LINE_SPACING, spacing)
        }
    }
    
    fun updateLigatures(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.LIGATURES_ENABLED, enabled)
        }
    }
    
    // ========== Behavior Actions ==========
    
    fun updateSoftKeyboard(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.SOFT_KEYBOARD_ENABLED, enabled)
        }
    }
    
    fun updateKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.KEEP_SCREEN_ON, enabled)
        }
    }
    
    fun updateToolbarVisible(visible: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.TOOLBAR_VISIBLE, visible)
        }
    }
    
    fun updateBell(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.BELL_ENABLED, enabled)
        }
    }
    
    fun updateVibration(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.VIBRATION_ENABLED, enabled)
        }
    }
    
    // ========== Terminal Actions ==========
    
    fun updateScrollSensitivity(sensitivity: Float) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.SCROLL_SENSITIVITY, sensitivity)
        }
    }
    
    fun updateBackKeyBehavior(behavior: BackKeyBehavior) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.BACK_KEY_BEHAVIOR, behavior.name)
        }
    }
    
    fun updateVolumeKeysBehavior(behavior: VolumeKeysBehavior) {
        viewModelScope.launch {
            settingsDataStore.updateSetting(SettingsDataStore.Keys.VOLUME_KEYS_BEHAVIOR, behavior.name)
        }
    }
    
    fun updateTerminalMargins(horizontal: Int, vertical: Int) {
        viewModelScope.launch {
            settingsDataStore.updateSettings {
                this[SettingsDataStore.Keys.TERMINAL_MARGIN_HORIZONTAL] = horizontal
                this[SettingsDataStore.Keys.TERMINAL_MARGIN_VERTICAL] = vertical
            }
        }
    }
    
    // ========== Profile Actions ==========
    
    fun activateProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.activateProfile(profileId)
        }
    }
    
    fun createProfile(name: String, description: String? = null, copyFromId: String? = null) {
        viewModelScope.launch {
            val copyFrom = copyFromId?.let { profileRepository.getProfile(it) }
            profileRepository.createProfile(name, description, copyFrom)
        }
    }
    
    fun updateProfile(profile: Profile) {
        viewModelScope.launch {
            profileRepository.updateProfile(profile)
        }
    }
    
    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.getProfile(profileId)?.let {
                profileRepository.deleteProfile(it)
            }
        }
    }
    
    fun setDefaultProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.setDefaultProfile(profileId)
        }
    }
    
    fun exportProfile(profileId: String): Flow<String> = flow {
        val profile = profileRepository.getProfile(profileId)
        if (profile != null) {
            emit(profileRepository.exportProfile(profile))
        }
    }
    
    fun importProfile(json: String) {
        viewModelScope.launch {
            try {
                profileRepository.importProfile(json)
            } catch (e: Exception) {
                // Handle import error
            }
        }
    }
    
    // ========== Theme Actions ==========
    
    fun saveCustomTheme(theme: Theme) {
        viewModelScope.launch {
            themeRepository.saveTheme(theme)
        }
    }
    
    fun deleteCustomTheme(themeId: String) {
        viewModelScope.launch {
            themeRepository.deleteTheme(themeId)
        }
    }
    
    fun exportTheme(theme: Theme): Flow<String> = flow {
        emit(themeRepository.exportTheme(theme))
    }
    
    fun importTheme(json: String) {
        viewModelScope.launch {
            try {
                val theme = themeRepository.importTheme(json)
                // Optionally activate the imported theme
                updateTheme(theme.id)
            } catch (e: Exception) {
                // Handle import error
            }
        }
    }
    
    // ========== Reset Actions ==========
    
    fun resetToDefaults() {
        viewModelScope.launch {
            settingsDataStore.clearAll()
        }
    }
}

/**
 * UI state for the settings screen.
 */
sealed class SettingsUiState {
    data object Loading : SettingsUiState()
    data class Ready(val settings: TermuxSettings) : SettingsUiState()
    data class Error(val message: String) : SettingsUiState()
}
