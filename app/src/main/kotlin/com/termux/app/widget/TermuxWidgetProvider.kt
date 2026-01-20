package com.termux.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.shared.logger.Logger
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

/**
 * Widget provider for Termux shortcuts.
 * 
 * Supports multiple widget sizes:
 * - 1x1: Single shortcut button
 * - 2x1: Shortcut with name
 * - 4x1: List of shortcuts
 */
@AndroidEntryPoint
class TermuxWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val LOG_TAG = "TermuxWidgetProvider"
        
        // Intent actions
        const val ACTION_EXECUTE_SHORTCUT = "com.termux.widget.ACTION_EXECUTE_SHORTCUT"
        const val ACTION_REFRESH_WIDGET = "com.termux.widget.ACTION_REFRESH_WIDGET"
        const val ACTION_OPEN_TERMUX = "com.termux.widget.ACTION_OPEN_TERMUX"
        
        // Intent extras
        const val EXTRA_SHORTCUT_PATH = "com.termux.widget.EXTRA_SHORTCUT_PATH"
        const val EXTRA_SHORTCUT_NAME = "com.termux.widget.EXTRA_SHORTCUT_NAME"
        const val EXTRA_IS_TASK = "com.termux.widget.EXTRA_IS_TASK"
        const val EXTRA_WIDGET_ID = "com.termux.widget.EXTRA_WIDGET_ID"
        
        /**
         * Request widget update.
         */
        fun requestUpdate(context: Context, widgetIds: IntArray? = null) {
            val intent = Intent(context, TermuxWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                widgetIds?.let { putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, it) }
            }
            context.sendBroadcast(intent)
        }
    }
    
    @Inject
    lateinit var shortcutScanner: ShortcutScanner
    
    @Inject
    lateinit var widgetPreferences: WidgetPreferences
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Logger.logDebug(LOG_TAG, "Updating ${appWidgetIds.size} widgets")
        
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_EXECUTE_SHORTCUT -> {
                val shortcutPath = intent.getStringExtra(EXTRA_SHORTCUT_PATH)
                val shortcutName = intent.getStringExtra(EXTRA_SHORTCUT_NAME)
                val isTask = intent.getBooleanExtra(EXTRA_IS_TASK, false)
                
                if (shortcutPath != null) {
                    executeShortcut(context, shortcutPath, shortcutName ?: "shortcut", isTask)
                }
            }
            ACTION_REFRESH_WIDGET -> {
                val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateWidget(context, appWidgetManager, widgetId)
                }
            }
            ACTION_OPEN_TERMUX -> {
                val termuxIntent = Intent(context, TermuxActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(termuxIntent)
            }
        }
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Logger.logDebug(LOG_TAG, "Deleted ${appWidgetIds.size} widgets")
        // Clean up widget preferences if needed
    }
    
    override fun onEnabled(context: Context) {
        Logger.logInfo(LOG_TAG, "First widget enabled")
        // Ensure shortcuts directory exists
        shortcutScanner.ensureDirectories()
    }
    
    override fun onDisabled(context: Context) {
        Logger.logInfo(LOG_TAG, "Last widget disabled")
    }
    
    /**
     * Update a single widget.
     */
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        try {
            // Get widget configuration
            val config = widgetPreferences.getWidgetConfig(widgetId)
            
            // Create RemoteViews based on widget type
            val views = when (config?.widgetType) {
                WidgetType.LIST -> createListWidgetViews(context, widgetId)
                WidgetType.SINGLE_WITH_LABEL -> createLabelWidgetViews(context, widgetId, config)
                else -> createSimpleWidgetViews(context, widgetId, config)
            }
            
            appWidgetManager.updateAppWidget(widgetId, views)
            
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to update widget $widgetId: ${e.message}")
        }
    }
    
    /**
     * Create simple 1x1 widget views.
     */
    private fun createSimpleWidgetViews(
        context: Context,
        widgetId: Int,
        config: WidgetConfig?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_shortcut_simple)
        
        if (config?.shortcutPath != null) {
            // Set icon if available
            config.iconPath?.let { iconPath ->
                val bitmap = BitmapFactory.decodeFile(iconPath)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon, bitmap)
                }
            }
            
            // Set click handler to execute shortcut
            val executeIntent = Intent(context, TermuxWidgetProvider::class.java).apply {
                action = ACTION_EXECUTE_SHORTCUT
                putExtra(EXTRA_SHORTCUT_PATH, config.shortcutPath)
                putExtra(EXTRA_SHORTCUT_NAME, config.shortcutName)
                putExtra(EXTRA_IS_TASK, config.isTask)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId,
                executeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        } else {
            // No shortcut configured - open configuration activity
            val configIntent = Intent(context, WidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        }
        
        return views
    }
    
    /**
     * Create widget with label.
     */
    private fun createLabelWidgetViews(
        context: Context,
        widgetId: Int,
        config: WidgetConfig?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_shortcut_label)
        
        if (config?.shortcutPath != null) {
            // Set name
            views.setTextViewText(R.id.widget_label, config.shortcutName ?: "Shortcut")
            
            // Set icon if available
            config.iconPath?.let { iconPath ->
                val bitmap = BitmapFactory.decodeFile(iconPath)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon, bitmap)
                }
            }
            
            // Set click handler
            val executeIntent = Intent(context, TermuxWidgetProvider::class.java).apply {
                action = ACTION_EXECUTE_SHORTCUT
                putExtra(EXTRA_SHORTCUT_PATH, config.shortcutPath)
                putExtra(EXTRA_SHORTCUT_NAME, config.shortcutName)
                putExtra(EXTRA_IS_TASK, config.isTask)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId,
                executeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        } else {
            views.setTextViewText(R.id.widget_label, "Tap to configure")
            
            val configIntent = Intent(context, WidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        }
        
        return views
    }
    
    /**
     * Create list widget views.
     */
    private fun createListWidgetViews(
        context: Context,
        widgetId: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_shortcut_list)
        
        // Set up RemoteViews service for list adapter
        val serviceIntent = Intent(context, WidgetRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setRemoteAdapter(R.id.widget_list, serviceIntent)
        
        // Set empty view
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        
        // Set up click intent template
        val clickIntent = Intent(context, TermuxWidgetProvider::class.java).apply {
            action = ACTION_EXECUTE_SHORTCUT
            putExtra(EXTRA_WIDGET_ID, widgetId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_list, pendingIntent)
        
        // Set up header click (opens Termux)
        val termuxIntent = Intent(context, TermuxWidgetProvider::class.java).apply {
            action = ACTION_OPEN_TERMUX
        }
        val termuxPendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId + 10000,
            termuxIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_header, termuxPendingIntent)
        
        // Set up refresh button
        val refreshIntent = Intent(context, TermuxWidgetProvider::class.java).apply {
            action = ACTION_REFRESH_WIDGET
            putExtra(EXTRA_WIDGET_ID, widgetId)
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId + 20000,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)
        
        return views
    }
    
    /**
     * Execute a shortcut script.
     */
    private fun executeShortcut(
        context: Context,
        shortcutPath: String,
        shortcutName: String,
        isTask: Boolean
    ) {
        Logger.logInfo(LOG_TAG, "Executing shortcut: $shortcutName (task=$isTask)")
        
        val file = File(shortcutPath)
        if (!file.exists()) {
            Logger.logWarn(LOG_TAG, "Shortcut file not found: $shortcutPath")
            return
        }
        
        if (isTask) {
            // Execute in background without terminal UI
            executeBackgroundTask(context, shortcutPath, shortcutName)
        } else {
            // Launch Termux with script
            launchTermuxWithScript(context, shortcutPath)
        }
    }
    
    /**
     * Execute script in background (for tasks).
     */
    private fun executeBackgroundTask(
        context: Context,
        shortcutPath: String,
        shortcutName: String
    ) {
        // Send broadcast to TermuxService
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setPackage("com.termux")
            putExtra("com.termux.RUN_COMMAND_PATH", shortcutPath)
            putExtra("com.termux.RUN_COMMAND_LABEL", shortcutName)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        context.sendBroadcast(intent)
    }
    
    /**
     * Launch Termux with a script.
     */
    private fun launchTermuxWithScript(context: Context, shortcutPath: String) {
        val intent = Intent(context, TermuxActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("com.termux.SCRIPT_PATH", shortcutPath)
        }
        context.startActivity(intent)
    }
}
