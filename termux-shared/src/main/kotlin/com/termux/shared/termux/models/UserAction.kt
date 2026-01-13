package com.termux.shared.termux.models

/**
 * User actions that can be performed in termux-shared.
 */
enum class UserAction(val actionName: String) {
    CRASH_REPORT("crash report"),
    PLUGIN_EXECUTION_COMMAND("plugin execution command");

    // For Java interop
    fun getName(): String = actionName
}
