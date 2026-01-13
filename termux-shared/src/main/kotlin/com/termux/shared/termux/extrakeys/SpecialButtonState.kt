package com.termux.shared.termux.extrakeys

import com.google.android.material.button.MaterialButton

/**
 * The class that maintains a state of a [SpecialButton].
 */
class SpecialButtonState(
    private val extraKeysView: ExtraKeysView
) {
    /** If special button has been created for the [ExtraKeysView]. */
    @JvmField
    var isCreated: Boolean = false

    /** If special button is active. */
    @JvmField
    var isActive: Boolean = false

    /**
     * If special button is locked due to long hold on it and should not be
     * deactivated if its state is read.
     */
    @JvmField
    var isLocked: Boolean = false

    @JvmField
    var buttons: MutableList<MaterialButton> = ArrayList()

    /** Set [isCreated]. */
    fun setIsCreated(value: Boolean) {
        isCreated = value
    }

    /** Set [isActive]. */
    fun setIsActive(value: Boolean) {
        isActive = value
        for (button in buttons) {
            button.setTextColor(
                if (value) extraKeysView.buttonActiveTextColor
                else extraKeysView.buttonTextColor
            )
        }
    }

    /** Set [isLocked]. */
    fun setIsLocked(value: Boolean) {
        isLocked = value
    }
}
