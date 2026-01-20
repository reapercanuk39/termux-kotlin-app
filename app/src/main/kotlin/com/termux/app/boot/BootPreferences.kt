package com.termux.app.boot

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences and configuration for Termux:Boot functionality.
 */

// DataStore extension
private val Context.bootDataStore: DataStore<Preferences> by preferencesDataStore(name = "termux_boot")

@Singleton
class BootPreferences @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.bootDataStore
    
    companion object {
        // Preference keys
        private val KEY_BOOT_ENABLED = booleanPreferencesKey("boot_enabled")
        private val KEY_RUN_ON_BOOT = booleanPreferencesKey("run_on_boot")
        private val KEY_SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        private val KEY_WAIT_FOR_UNLOCK = booleanPreferencesKey("wait_for_unlock")
        private val KEY_SCRIPT_TIMEOUT = intPreferencesKey("script_timeout_seconds")
        private val KEY_LAST_BOOT_TIME = longPreferencesKey("last_boot_time")
        private val KEY_LAST_BOOT_STATUS = stringPreferencesKey("last_boot_status")
        private val KEY_SCRIPTS_EXECUTED = intPreferencesKey("scripts_executed_count")
        private val KEY_RUN_IN_FOREGROUND = booleanPreferencesKey("run_in_foreground")
        private val KEY_PARALLEL_EXECUTION = booleanPreferencesKey("parallel_execution")
        
        // Boot directory paths
        const val BOOT_SCRIPTS_DIR = "/data/data/com.termux/files/home/.termux/boot"
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        
        // Defaults
        const val DEFAULT_TIMEOUT_SECONDS = 300 // 5 minutes
    }
    
    // Boot enabled
    val bootEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_BOOT_ENABLED] ?: true }
    
    suspend fun setBootEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BOOT_ENABLED] = enabled }
    }
    
    // Run on boot
    val runOnBoot: Flow<Boolean> = dataStore.data.map { it[KEY_RUN_ON_BOOT] ?: true }
    
    suspend fun setRunOnBoot(enabled: Boolean) {
        dataStore.edit { it[KEY_RUN_ON_BOOT] = enabled }
    }
    
    // Show notification during boot script execution
    val showNotification: Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_NOTIFICATION] ?: true }
    
    suspend fun setShowNotification(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_NOTIFICATION] = show }
    }
    
    // Wait for device unlock before running scripts
    val waitForUnlock: Flow<Boolean> = dataStore.data.map { it[KEY_WAIT_FOR_UNLOCK] ?: false }
    
    suspend fun setWaitForUnlock(wait: Boolean) {
        dataStore.edit { it[KEY_WAIT_FOR_UNLOCK] = wait }
    }
    
    // Script timeout
    val scriptTimeout: Flow<Int> = dataStore.data.map { it[KEY_SCRIPT_TIMEOUT] ?: DEFAULT_TIMEOUT_SECONDS }
    
    suspend fun setScriptTimeout(seconds: Int) {
        dataStore.edit { it[KEY_SCRIPT_TIMEOUT] = seconds }
    }
    
    // Last boot execution info
    val lastBootTime: Flow<Long> = dataStore.data.map { it[KEY_LAST_BOOT_TIME] ?: 0L }
    val lastBootStatus: Flow<String> = dataStore.data.map { it[KEY_LAST_BOOT_STATUS] ?: "never" }
    val scriptsExecutedCount: Flow<Int> = dataStore.data.map { it[KEY_SCRIPTS_EXECUTED] ?: 0 }
    
    suspend fun recordBootExecution(status: String, scriptsCount: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_BOOT_TIME] = System.currentTimeMillis()
            prefs[KEY_LAST_BOOT_STATUS] = status
            prefs[KEY_SCRIPTS_EXECUTED] = scriptsCount
        }
    }
    
    // Run scripts in foreground (visible terminal)
    val runInForeground: Flow<Boolean> = dataStore.data.map { it[KEY_RUN_IN_FOREGROUND] ?: false }
    
    suspend fun setRunInForeground(foreground: Boolean) {
        dataStore.edit { it[KEY_RUN_IN_FOREGROUND] = foreground }
    }
    
    // Execute scripts in parallel
    val parallelExecution: Flow<Boolean> = dataStore.data.map { it[KEY_PARALLEL_EXECUTION] ?: false }
    
    suspend fun setParallelExecution(parallel: Boolean) {
        dataStore.edit { it[KEY_PARALLEL_EXECUTION] = parallel }
    }
    
    /**
     * Get boot scripts directory, creating if necessary.
     */
    fun getBootScriptsDir(): File {
        val dir = File(BOOT_SCRIPTS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * List all boot scripts.
     */
    fun listBootScripts(): List<File> {
        val dir = getBootScriptsDir()
        return dir.listFiles()
            ?.filter { it.isFile && it.canExecute() }
            ?.sortedBy { it.name }
            ?: emptyList()
    }
    
    /**
     * Check if boot scripts directory exists and has scripts.
     */
    fun hasBootScripts(): Boolean {
        return listBootScripts().isNotEmpty()
    }
    
    /**
     * Get current settings synchronously (for boot receiver).
     */
    suspend fun getSettings(): BootSettings {
        val prefs = dataStore.data.first()
        return BootSettings(
            enabled = prefs[KEY_BOOT_ENABLED] ?: true,
            runOnBoot = prefs[KEY_RUN_ON_BOOT] ?: true,
            showNotification = prefs[KEY_SHOW_NOTIFICATION] ?: true,
            waitForUnlock = prefs[KEY_WAIT_FOR_UNLOCK] ?: false,
            scriptTimeout = prefs[KEY_SCRIPT_TIMEOUT] ?: DEFAULT_TIMEOUT_SECONDS,
            runInForeground = prefs[KEY_RUN_IN_FOREGROUND] ?: false,
            parallelExecution = prefs[KEY_PARALLEL_EXECUTION] ?: false
        )
    }
}

/**
 * Boot settings data class.
 */
data class BootSettings(
    val enabled: Boolean,
    val runOnBoot: Boolean,
    val showNotification: Boolean,
    val waitForUnlock: Boolean,
    val scriptTimeout: Int,
    val runInForeground: Boolean,
    val parallelExecution: Boolean
)
