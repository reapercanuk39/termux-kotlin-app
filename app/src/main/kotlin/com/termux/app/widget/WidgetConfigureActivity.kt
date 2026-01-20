package com.termux.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.termux.shared.logger.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity for configuring a Termux widget.
 */
@AndroidEntryPoint
class WidgetConfigureActivity : ComponentActivity() {
    
    companion object {
        private const val LOG_TAG = "WidgetConfigureActivity"
    }
    
    @Inject
    lateinit var shortcutScanner: ShortcutScanner
    
    @Inject
    lateinit var widgetPreferences: WidgetPreferences
    
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set cancelled result in case user backs out
        setResult(RESULT_CANCELED)
        
        // Get widget ID from intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Logger.logError(LOG_TAG, "Invalid widget ID")
            finish()
            return
        }
        
        Logger.logInfo(LOG_TAG, "Configuring widget: $appWidgetId")
        
        // Ensure shortcuts directory exists
        shortcutScanner.ensureDirectories()
        
        setContent {
            WidgetConfigureScreen(
                shortcutScanner = shortcutScanner,
                widgetPreferences = widgetPreferences,
                widgetId = appWidgetId,
                onConfigComplete = { config ->
                    completeConfiguration(config)
                },
                onCancel = { finish() }
            )
        }
    }
    
    private fun completeConfiguration(config: WidgetConfig) {
        // Request widget update
        val appWidgetManager = AppWidgetManager.getInstance(this)
        TermuxWidgetProvider.requestUpdate(this, intArrayOf(appWidgetId))
        
        // Set result to OK
        val resultIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultIntent)
        
        Logger.logInfo(LOG_TAG, "Widget configured: ${config.shortcutName}")
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigureScreen(
    shortcutScanner: ShortcutScanner,
    widgetPreferences: WidgetPreferences,
    widgetId: Int,
    onConfigComplete: (WidgetConfig) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var shortcuts by remember { mutableStateOf(emptyList<ShortcutScanner.ShortcutInfo>()) }
    var selectedWidgetType by remember { mutableStateOf(WidgetType.SIMPLE) }
    var showTypeSelector by remember { mutableStateOf(false) }
    
    // Load shortcuts
    LaunchedEffect(Unit) {
        shortcuts = shortcutScanner.scanShortcuts()
    }
    
    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Configure Widget") },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    },
                    actions = {
                        // Widget type selector
                        IconButton(onClick = { showTypeSelector = true }) {
                            Icon(Icons.Default.Settings, "Widget Type")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Widget type dropdown
                if (showTypeSelector) {
                    WidgetTypeSelector(
                        selectedType = selectedWidgetType,
                        onTypeSelected = { 
                            selectedWidgetType = it
                            showTypeSelector = false
                            
                            // For list widget, save config immediately
                            if (it == WidgetType.LIST) {
                                scope.launch {
                                    val config = WidgetConfig(
                                        widgetId = widgetId,
                                        widgetType = WidgetType.LIST
                                    )
                                    widgetPreferences.saveWidgetConfig(config)
                                    onConfigComplete(config)
                                }
                            }
                        },
                        onDismiss = { showTypeSelector = false }
                    )
                }
                
                // Instructions
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Select a Shortcut",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Shortcuts are scripts in ~/.shortcuts/\nTasks run in the background without opening Termux.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Shortcuts list
                if (shortcuts.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No shortcuts found",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Create scripts in ~/.shortcuts/ to add them as widgets",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(shortcuts) { shortcut ->
                            ShortcutItem(
                                shortcut = shortcut,
                                onClick = {
                                    scope.launch {
                                        val config = WidgetConfig(
                                            widgetId = widgetId,
                                            widgetType = selectedWidgetType,
                                            shortcutPath = shortcut.path,
                                            shortcutName = shortcut.displayName,
                                            iconPath = shortcut.iconPath,
                                            isTask = shortcut.isTask
                                        )
                                        widgetPreferences.saveWidgetConfig(config)
                                        onConfigComplete(config)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutItem(
    shortcut: ShortcutScanner.ShortcutInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                if (shortcut.isTask) Icons.Default.Settings else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (shortcut.isTask) 
                    MaterialTheme.colorScheme.secondary 
                else 
                    MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    shortcut.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (shortcut.isTask) "Background task" else shortcut.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Arrow
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WidgetTypeSelector(
    selectedType: WidgetType,
    onTypeSelected: (WidgetType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Widget Type") },
        text = {
            Column {
                WidgetTypeOption(
                    name = "Simple (1x1)",
                    description = "Icon only",
                    type = WidgetType.SIMPLE,
                    isSelected = selectedType == WidgetType.SIMPLE,
                    onSelect = onTypeSelected
                )
                WidgetTypeOption(
                    name = "With Label (2x1)",
                    description = "Icon and name",
                    type = WidgetType.SINGLE_WITH_LABEL,
                    isSelected = selectedType == WidgetType.SINGLE_WITH_LABEL,
                    onSelect = onTypeSelected
                )
                WidgetTypeOption(
                    name = "List (4x1+)",
                    description = "All shortcuts list",
                    type = WidgetType.LIST,
                    isSelected = selectedType == WidgetType.LIST,
                    onSelect = onTypeSelected
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun WidgetTypeOption(
    name: String,
    description: String,
    type: WidgetType,
    isSelected: Boolean,
    onSelect: (WidgetType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(type) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = { onSelect(type) }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(name, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
