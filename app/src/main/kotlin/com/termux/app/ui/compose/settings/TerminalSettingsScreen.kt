package com.termux.app.ui.compose.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.app.ui.viewmodel.TerminalSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: TerminalSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Font Settings Section
            SettingsSection(
                title = "Font",
                icon = Icons.Default.TextFields
            ) {
                // Font Size
                SettingsSlider(
                    title = "Font Size",
                    value = uiState.fontSize.toFloat(),
                    valueRange = 6f..32f,
                    steps = 25,
                    valueLabel = "${uiState.fontSize}sp",
                    onValueChange = { viewModel.setFontSize(it.toInt()) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keyboard Settings Section
            SettingsSection(
                title = "Keyboard",
                icon = Icons.Default.Keyboard
            ) {
                SettingsSwitch(
                    title = "Extra Keys Row",
                    subtitle = "Show additional function keys",
                    checked = uiState.extraKeysEnabled,
                    onCheckedChange = viewModel::setExtraKeysEnabled
                )

                SettingsSwitch(
                    title = "Vibrate on Key",
                    subtitle = "Haptic feedback when typing",
                    checked = uiState.vibrateOnKey,
                    onCheckedChange = viewModel::setVibrateOnKey
                )

                SettingsSwitch(
                    title = "Soft Keyboard",
                    subtitle = "Show on-screen keyboard",
                    checked = uiState.softKeyboardEnabled,
                    onCheckedChange = viewModel::setSoftKeyboardEnabled
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Appearance Settings Section
            SettingsSection(
                title = "Appearance",
                icon = Icons.Default.Palette
            ) {
                SettingsSwitch(
                    title = "Cursor Blink",
                    subtitle = "Blinking cursor animation",
                    checked = uiState.cursorBlink,
                    onCheckedChange = viewModel::setCursorBlink
                )

                SettingsSwitch(
                    title = "Bell",
                    subtitle = "Terminal bell sound",
                    checked = uiState.bellEnabled,
                    onCheckedChange = viewModel::setBellEnabled
                )

                SettingsSwitch(
                    title = "Keep Screen On",
                    subtitle = "Prevent screen from turning off",
                    checked = uiState.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}
