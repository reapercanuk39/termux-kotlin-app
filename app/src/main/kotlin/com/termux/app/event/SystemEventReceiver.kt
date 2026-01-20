package com.termux.app.event

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.termux.app.boot.BootService
import com.termux.shared.data.IntentUtils
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.shell.TermuxShellManager
import java.io.File

class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        Logger.logDebug(LOG_TAG, "Intent Received:\n${IntentUtils.getIntentString(intent)}")

        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> onActionBootCompleted(context, intent)
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> onActionPackageUpdated(context, intent)
            else -> Logger.logError(LOG_TAG, "Invalid action \"$action\" passed to $LOG_TAG")
        }
    }

    @Synchronized
    fun onActionBootCompleted(context: Context, intent: Intent) {
        Logger.logInfo(LOG_TAG, "BOOT_COMPLETED received")
        
        // Call existing shell manager hook
        TermuxShellManager.onActionBootCompleted(context, intent)
        
        // Start BootService to execute ~/.termux/boot/ scripts
        val bootDir = File("/data/data/com.termux/files/home/.termux/boot")
        if (bootDir.exists() && bootDir.isDirectory) {
            val scripts = bootDir.listFiles()?.filter { 
                it.isFile && it.canExecute() && !it.name.startsWith(".") 
            }
            if (!scripts.isNullOrEmpty()) {
                Logger.logInfo(LOG_TAG, "Found ${scripts.size} boot scripts, starting BootService")
                val bootIntent = Intent(context, BootService::class.java)
                try {
                    ContextCompat.startForegroundService(context, bootIntent)
                } catch (e: Exception) {
                    Logger.logError(LOG_TAG, "Failed to start BootService: ${e.message}")
                }
            } else {
                Logger.logDebug(LOG_TAG, "No executable boot scripts found")
            }
        } else {
            Logger.logDebug(LOG_TAG, "Boot directory does not exist: ${bootDir.absolutePath}")
        }
    }

    @Synchronized
    fun onActionPackageUpdated(context: Context, intent: Intent) {
        val data = intent.data
        if (data != null && TermuxUtils.isUriDataForTermuxPluginPackage(data)) {
            Logger.logDebug(LOG_TAG, intent.action?.replace("^android.intent.action.".toRegex(), "") +
                " event received for \"${data.toString().replace("^package:".toRegex(), "")}\"")
            if (TermuxFileUtils.isTermuxFilesDirectoryAccessible(context, false, false) == null)
                TermuxShellEnvironment.writeEnvironmentToFile(context)
        }
    }

    companion object {
        private var mInstance: SystemEventReceiver? = null
        private const val LOG_TAG = "SystemEventReceiver"

        @JvmStatic
        @Synchronized
        fun getInstance(): SystemEventReceiver {
            if (mInstance == null) {
                mInstance = SystemEventReceiver()
            }
            return mInstance!!
        }

        /**
         * Register [SystemEventReceiver] to listen to [Intent.ACTION_PACKAGE_ADDED],
         * [Intent.ACTION_PACKAGE_REMOVED] and [Intent.ACTION_PACKAGE_REPLACED] broadcasts.
         * They must be registered dynamically and cannot be registered implicitly in
         * the AndroidManifest.xml due to Android 8+ restrictions.
         *
         * https://developer.android.com/guide/components/broadcast-exceptions
         */
        @JvmStatic
        @Synchronized
        fun registerPackageUpdateEvents(context: Context) {
            val intentFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            // Android 14+ (API 34+) requires specifying export status for receivers
            // This receiver needs RECEIVER_EXPORTED to receive system package broadcasts
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(getInstance(), intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(getInstance(), intentFilter)
            }
        }

        @JvmStatic
        @Synchronized
        fun unregisterPackageUpdateEvents(context: Context) {
            context.unregisterReceiver(getInstance())
        }
    }
}
