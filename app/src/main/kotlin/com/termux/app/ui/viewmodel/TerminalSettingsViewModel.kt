package com.termux.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.app.data.repository.TerminalSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for terminal settings
 */
data class TerminalSettingsUiState(
    val fontSize: Int = TerminalSettingsRepository.DEFAULT_FONT_SIZE,
    val fontFamily: String = TerminalSettingsRepository.DEFAULT_FONT_FAMILY,
    val cursorBlink: Boolean = TerminalSettingsRepository.DEFAULT_CURSOR_BLINK,
    val cursorStyle: String = TerminalSettingsRepository.DEFAULT_CURSOR_STYLE,
    val bellEnabled: Boolean = TerminalSettingsRepository.DEFAULT_BELL_ENABLED,
    val vibrateOnKey: Boolean = TerminalSettingsRepository.DEFAULT_VIBRATE_ON_KEY,
    val extraKeysEnabled: Boolean = TerminalSettingsRepository.DEFAULT_EXTRA_KEYS_ENABLED,
    val keepScreenOn: Boolean = TerminalSettingsRepository.DEFAULT_KEEP_SCREEN_ON,
    val softKeyboardEnabled: Boolean = TerminalSettingsRepository.DEFAULT_SOFT_KEYBOARD_ENABLED,
    val colorScheme: String = TerminalSettingsRepository.DEFAULT_COLOR_SCHEME,
    val backgroundOpacity: Int = TerminalSettingsRepository.DEFAULT_BACKGROUND_OPACITY
)

/**
 * ViewModel for terminal settings using StateFlow
 */
@HiltViewModel
class TerminalSettingsViewModel @Inject constructor(
    private val settingsRepository: TerminalSettingsRepository
) : ViewModel() {

    val uiState: StateFlow<TerminalSettingsUiState> = combine(
        settingsRepository.fontSizeFlow,
        settingsRepository.fontFamilyFlow,
        settingsRepository.cursorBlinkFlow,
        settingsRepository.cursorStyleFlow,
        settingsRepository.bellEnabledFlow,
        settingsRepository.vibrateOnKeyFlow,
        settingsRepository.extraKeysEnabledFlow,
        settingsRepository.keepScreenOnFlow,
        settingsRepository.softKeyboardEnabledFlow,
    ) { values ->
        TerminalSettingsUiState(
            fontSize = values[0] as Int,
            fontFamily = values[1] as String,
            cursorBlink = values[2] as Boolean,
            cursorStyle = values[3] as String,
            bellEnabled = values[4] as Boolean,
            vibrateOnKey = values[5] as Boolean,
            extraKeysEnabled = values[6] as Boolean,
            keepScreenOn = values[7] as Boolean,
            softKeyboardEnabled = values[8] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TerminalSettingsUiState()
    )

    fun setFontSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.setFontSize(size)
        }
    }

    fun setFontFamily(family: String) {
        viewModelScope.launch {
            settingsRepository.setFontFamily(family)
        }
    }

    fun setCursorBlink(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCursorBlink(enabled)
        }
    }

    fun setCursorStyle(style: String) {
        viewModelScope.launch {
            settingsRepository.setCursorStyle(style)
        }
    }

    fun setBellEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBellEnabled(enabled)
        }
    }

    fun setVibrateOnKey(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVibrateOnKey(enabled)
        }
    }

    fun setExtraKeysEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setExtraKeysEnabled(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepScreenOn(enabled)
        }
    }

    fun setSoftKeyboardEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSoftKeyboardEnabled(enabled)
        }
    }

    fun setColorScheme(scheme: String) {
        viewModelScope.launch {
            settingsRepository.setColorScheme(scheme)
        }
    }

    fun setBackgroundOpacity(opacity: Int) {
        viewModelScope.launch {
            settingsRepository.setBackgroundOpacity(opacity)
        }
    }
}
