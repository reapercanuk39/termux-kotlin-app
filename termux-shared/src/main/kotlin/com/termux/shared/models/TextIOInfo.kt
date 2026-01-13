package com.termux.shared.models

import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.Keep
import com.termux.shared.data.DataUtils
import java.io.Serializable

/**
 * An object that stored info for TextIOActivity.
 */
class TextIOInfo(
    /** The action for which TextIOActivity will be started. */
    val action: String,
    /** The internal app component that is will start the TextIOActivity. */
    val sender: String
) : Serializable {

    /** The activity title. */
    var title: String? = null

    /** If back button should be shown in ActionBar. */
    var showBackButtonInActionBar = false
        private set

    /** If label is enabled. */
    var isLabelEnabled = false
        private set

    /** The label of text input. Max allowed length is [LABEL_SIZE_LIMIT_IN_BYTES]. */
    var label: String? = null
        set(value) {
            field = DataUtils.getTruncatedCommandOutput(value, LABEL_SIZE_LIMIT_IN_BYTES, true, false, false)
        }

    /** The text size of label. Defaults to 14sp. */
    var labelSize = 14
        set(value) {
            if (value > 0) field = value
        }

    /** The text color of label. Defaults to [Color.BLACK]. */
    var labelColor = Color.BLACK

    /** The [Typeface] family of label. Defaults to "sans-serif". */
    var labelTypeFaceFamily = "sans-serif"

    /** The [Typeface] style of label. Defaults to [Typeface.BOLD]. */
    var labelTypeFaceStyle = Typeface.BOLD

    /** The text of text input. Max allowed length is [TEXT_SIZE_LIMIT_IN_BYTES]. */
    var text: String? = null
        set(value) {
            field = DataUtils.getTruncatedCommandOutput(value, TEXT_SIZE_LIMIT_IN_BYTES, true, false, false)
        }

    /** The text size for text. Defaults to 12sp. */
    var textSize = 12
        set(value) {
            if (value > 0) field = value
        }

    /** The text size for text. Defaults to [TEXT_SIZE_LIMIT_IN_BYTES]. */
    var textLengthLimit = TEXT_SIZE_LIMIT_IN_BYTES
        set(value) {
            if (value < TEXT_SIZE_LIMIT_IN_BYTES) field = value
        }

    /** The text color of text. Defaults to [Color.BLACK]. */
    var textColor = Color.BLACK

    /** The [Typeface] family for text. Defaults to "sans-serif". */
    var textTypeFaceFamily = "sans-serif"

    /** The [Typeface] style for text. Defaults to [Typeface.NORMAL]. */
    var textTypeFaceStyle = Typeface.NORMAL

    /** If horizontal scrolling should be enabled for text. */
    var isHorizontallyScrollable = false
        private set

    /** If character usage should be enabled for text. */
    var shouldShowTextCharacterUsage = false
        private set

    /** If editing text should be disabled. */
    var isEditingTextDisabled = false
        private set

    fun shouldShowBackButtonInActionBar(): Boolean = showBackButtonInActionBar

    fun setShowBackButtonInActionBar(showBackButtonInActionBar: Boolean) {
        this.showBackButtonInActionBar = showBackButtonInActionBar
    }

    fun setLabelEnabled(labelEnabled: Boolean) {
        isLabelEnabled = labelEnabled
    }

    fun setTextHorizontallyScrolling(textHorizontallyScrolling: Boolean) {
        isHorizontallyScrollable = textHorizontallyScrolling
    }

    fun shouldShowTextCharacterUsage(): Boolean = shouldShowTextCharacterUsage

    fun setShowTextCharacterUsage(showTextCharacterUsage: Boolean) {
        shouldShowTextCharacterUsage = showTextCharacterUsage
    }

    fun setEditingTextDisabled(editingTextDisabled: Boolean) {
        isEditingTextDisabled = editingTextDisabled
    }

    companion object {
        /**
         * Explicitly define `serialVersionUID` to prevent exceptions on deserialization.
         */
        @Keep
        private const val serialVersionUID = 1L

        const val GENERAL_DATA_SIZE_LIMIT_IN_BYTES = 1000
        const val LABEL_SIZE_LIMIT_IN_BYTES = 4000
        const val TEXT_SIZE_LIMIT_IN_BYTES = 100000 - GENERAL_DATA_SIZE_LIMIT_IN_BYTES - LABEL_SIZE_LIMIT_IN_BYTES // < 100KB
    }
}
