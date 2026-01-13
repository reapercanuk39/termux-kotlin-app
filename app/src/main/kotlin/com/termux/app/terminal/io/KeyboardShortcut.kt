package com.termux.app.terminal.io

/**
 * Represents a keyboard shortcut with a code point and associated action.
 */
data class KeyboardShortcut(
    @JvmField val codePoint: Int,
    @JvmField val shortcutAction: Int
)
