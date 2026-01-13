package com.termux.shared.android

import android.app.ActivityManager
import android.content.Context
import com.termux.shared.logger.Logger

object ProcessUtils {

    private const val LOG_TAG = "ProcessUtils"

    /**
     * Get the app process name for a pid with a call to [ActivityManager.getRunningAppProcesses].
     *
     * This will not return child process names. Android did not keep track of them before android 12
     * phantom process addition, but there is no API via IActivityManager to get them.
     *
     * To get process name for pids of own app's child processes, check `get_process_name_from_cmdline()`
     * in `local-socket.cpp`.
     *
     * @param context The [Context] for operations.
     * @param pid The pid of the process.
     * @return Returns the app process name if found, otherwise null.
     */
    @JvmStatic
    fun getAppProcessNameForPid(context: Context, pid: Int): String? {
        if (pid < 0) return null

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return null

        return try {
            val runningApps = activityManager.runningAppProcesses ?: return null
            for (procInfo in runningApps) {
                if (procInfo.pid == pid) {
                    return procInfo.processName
                }
            }
            null
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get app process name for pid $pid", e)
            null
        }
    }
}
