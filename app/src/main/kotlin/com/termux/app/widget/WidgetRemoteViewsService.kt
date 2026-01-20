package com.termux.app.widget

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.termux.R
import com.termux.shared.logger.Logger

/**
 * RemoteViewsService for list widget adapter.
 */
class WidgetRemoteViewsService : RemoteViewsService() {
    
    companion object {
        private const val LOG_TAG = "WidgetRemoteViewsService"
    }
    
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        Logger.logDebug(LOG_TAG, "Creating RemoteViewsFactory")
        return WidgetRemoteViewsFactory(applicationContext)
    }
}

/**
 * RemoteViewsFactory for providing list widget items.
 */
class WidgetRemoteViewsFactory(
    private val context: android.content.Context
) : RemoteViewsService.RemoteViewsFactory {
    
    companion object {
        private const val LOG_TAG = "WidgetRemoteViewsFactory"
    }
    
    private var shortcuts: List<ShortcutScanner.ShortcutInfo> = emptyList()
    private val shortcutScanner = ShortcutScanner(context)
    
    override fun onCreate() {
        Logger.logDebug(LOG_TAG, "Factory created")
        loadShortcuts()
    }
    
    override fun onDataSetChanged() {
        Logger.logDebug(LOG_TAG, "Data set changed")
        loadShortcuts()
    }
    
    private fun loadShortcuts() {
        shortcuts = shortcutScanner.scanShortcuts()
        Logger.logDebug(LOG_TAG, "Loaded ${shortcuts.size} shortcuts")
    }
    
    override fun onDestroy() {
        Logger.logDebug(LOG_TAG, "Factory destroyed")
        shortcuts = emptyList()
    }
    
    override fun getCount(): Int = shortcuts.size
    
    override fun getViewAt(position: Int): RemoteViews? {
        if (position >= shortcuts.size) return null
        
        val shortcut = shortcuts[position]
        
        return RemoteViews(context.packageName, R.layout.widget_list_item).apply {
            // Set shortcut name
            setTextViewText(R.id.item_name, shortcut.displayName)
            
            // Set type indicator (task vs regular)
            if (shortcut.isTask) {
                setTextViewText(R.id.item_type, "Task")
            } else {
                setTextViewText(R.id.item_type, "")
            }
            
            // Set icon if available
            shortcut.iconPath?.let { iconPath ->
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(iconPath)
                    if (bitmap != null) {
                        setImageViewBitmap(R.id.item_icon, bitmap)
                    }
                } catch (e: Exception) {
                    // Use default icon
                }
            }
            
            // Set fill-in intent for click handling
            val fillIntent = Intent().apply {
                putExtra(TermuxWidgetProvider.EXTRA_SHORTCUT_PATH, shortcut.path)
                putExtra(TermuxWidgetProvider.EXTRA_SHORTCUT_NAME, shortcut.displayName)
                putExtra(TermuxWidgetProvider.EXTRA_IS_TASK, shortcut.isTask)
            }
            setOnClickFillInIntent(R.id.item_container, fillIntent)
        }
    }
    
    override fun getLoadingView(): RemoteViews? = null
    
    override fun getViewTypeCount(): Int = 1
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun hasStableIds(): Boolean = true
}
