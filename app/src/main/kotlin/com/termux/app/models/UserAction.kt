package com.termux.app.models

/**
 * User actions that can be performed in the app.
 */
enum class UserAction(val actionName: String) {
    ABOUT("about"),
    REPORT_ISSUE_FROM_TRANSCRIPT("report issue from transcript");

    // For Java interop - provides getName() method
    @JvmName("getName")
    fun getActionName(): String = actionName
}
