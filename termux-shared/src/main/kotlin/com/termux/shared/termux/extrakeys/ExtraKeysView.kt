package com.termux.shared.termux.extrakeys

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.PopupWindow
import com.google.android.material.button.MaterialButton
import com.termux.kotlin.shared.R
import com.termux.shared.theme.ThemeUtils
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * A [View] showing extra keys (such as Escape, Ctrl, Alt) not normally available on an Android soft
 * keyboards.
 *
 * To use it, add following to a layout file and import it in your activity layout file or inflate
 * it with a [androidx.viewpager.widget.ViewPager].:
 * ```
 * <?xml version="1.0" encoding="utf-8"?>
 * <com.termux.shared.termux.extrakeys.ExtraKeysView xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:id="@+id/extra_keys"
 *     style="?android:attr/buttonBarStyle"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     android:layout_alignParentBottom="true"
 *     android:orientation="horizontal" />
 * ```
 *
 * Then in your activity, get its reference by a call to [android.app.Activity.findViewById]
 * or [android.view.LayoutInflater.inflate] if using [androidx.viewpager.widget.ViewPager].
 * Then call [setExtraKeysViewClient] and pass it the implementation of
 * [IExtraKeysView] so that you can receive callbacks. You can also override other values set
 * in [ExtraKeysView] by calling the respective functions.
 * If you extend [ExtraKeysView], you can also set them in the constructor, but do call super().
 *
 * After this you will have to make a call to [reload] and pass
 * it the [ExtraKeysInfo] to load and display the extra keys. Read its class javadocs for more
 * info on how to create it.
 *
 * Termux app defines the view in res/layout/view_terminal_toolbar_extra_keys and
 * inflates it in TerminalToolbarViewPager.instantiateItem() and sets the [ExtraKeysView] client
 * and calls [reload].
 * The [ExtraKeysInfo] is created by TermuxAppSharedProperties.setExtraKeys().
 * Then its got and the view height is adjusted in TermuxActivity.setTerminalToolbarHeight().
 * The client used is TermuxTerminalExtraKeys, which extends
 * TerminalExtraKeys to handle Termux app specific logic and
 * leave the rest to the super class.
 */
class ExtraKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GridLayout(context, attrs) {

    /** The client for the [ExtraKeysView]. */
    interface IExtraKeysView {
        /**
         * This is called by [ExtraKeysView] when a button is clicked. This is also called
         * for [mRepetitiveKeys] and [ExtraKeyButton] that have a popup set.
         * However, this is not called for [mSpecialButtons], whose state can instead be read
         * via a call to [readSpecialButton].
         *
         * @param view The view that was clicked.
         * @param buttonInfo The [ExtraKeyButton] for the button that was clicked.
         *                   The button may be a [ExtraKeyButton.KEY_MACRO] set which can be
         *                   checked with a call to [ExtraKeyButton.isMacro].
         * @param button The [MaterialButton] that was clicked.
         */
        fun onExtraKeyButtonClick(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton)

        /**
         * This is called by [ExtraKeysView] when a button is clicked so that the client
         * can perform any hepatic feedback. This is only called in the [MaterialButton.OnClickListener]
         * and not for every repeat. Its also called for [mSpecialButtons].
         *
         * @param view The view that was clicked.
         * @param buttonInfo The [ExtraKeyButton] for the button that was clicked.
         * @param button The [MaterialButton] that was clicked.
         * @return Return `true` if the client handled the feedback, otherwise `false`
         * so that [performExtraKeyButtonHapticFeedback]
         * can handle it depending on system settings.
         */
        fun performExtraKeyButtonHapticFeedback(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton): Boolean
    }

    companion object {
        /** Defines the default value for [mButtonTextColor] defined by current theme. */
        @JvmField val ATTR_BUTTON_TEXT_COLOR = R.attr.extraKeysButtonTextColor
        /** Defines the default value for [mButtonActiveTextColor] defined by current theme. */
        @JvmField val ATTR_BUTTON_ACTIVE_TEXT_COLOR = R.attr.extraKeysButtonActiveTextColor
        /** Defines the default value for [mButtonBackgroundColor] defined by current theme. */
        @JvmField val ATTR_BUTTON_BACKGROUND_COLOR = R.attr.extraKeysButtonBackgroundColor
        /** Defines the default value for [mButtonActiveBackgroundColor] defined by current theme. */
        @JvmField val ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR = R.attr.extraKeysButtonActiveBackgroundColor

        /** Defines the default fallback value for [mButtonTextColor] if [ATTR_BUTTON_TEXT_COLOR] is undefined. */
        const val DEFAULT_BUTTON_TEXT_COLOR = 0xFFFFFFFF.toInt()
        /** Defines the default fallback value for [mButtonActiveTextColor] if [ATTR_BUTTON_ACTIVE_TEXT_COLOR] is undefined. */
        const val DEFAULT_BUTTON_ACTIVE_TEXT_COLOR = 0xFF80DEEA.toInt()
        /** Defines the default fallback value for [mButtonBackgroundColor] if [ATTR_BUTTON_BACKGROUND_COLOR] is undefined. */
        const val DEFAULT_BUTTON_BACKGROUND_COLOR = 0x00000000
        /** Defines the default fallback value for [mButtonActiveBackgroundColor] if [ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR] is undefined. */
        const val DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR = 0xFF7F7F7F.toInt()

        /** Defines the minimum allowed duration in milliseconds for [mLongPressTimeout]. */
        const val MIN_LONG_PRESS_DURATION = 200
        /** Defines the maximum allowed duration in milliseconds for [mLongPressTimeout]. */
        const val MAX_LONG_PRESS_DURATION = 3000
        /** Defines the fallback duration in milliseconds for [mLongPressTimeout]. */
        const val FALLBACK_LONG_PRESS_DURATION = 400

        /** Defines the minimum allowed duration in milliseconds for [mLongPressRepeatDelay]. */
        const val MIN_LONG_PRESS__REPEAT_DELAY = 5
        /** Defines the maximum allowed duration in milliseconds for [mLongPressRepeatDelay]. */
        const val MAX_LONG_PRESS__REPEAT_DELAY = 2000
        /** Defines the default duration in milliseconds for [mLongPressRepeatDelay]. */
        const val DEFAULT_LONG_PRESS_REPEAT_DELAY = 80

        /**
         * General util function to compute the longest column length in a matrix.
         */
        @JvmStatic
        fun <T> maximumLength(matrix: Array<Array<T>>): Int {
            var m = 0
            for (row in matrix)
                m = maxOf(m, row.size)
            return m
        }
    }

    /** The implementation of the [IExtraKeysView] that acts as a client for the [ExtraKeysView]. */
    var extraKeysViewClient: IExtraKeysView? = null

    /** The map for the [SpecialButton] and their [SpecialButtonState]. Defaults to
     * the one returned by [getDefaultSpecialButtons]. */
    protected var mSpecialButtons: MutableMap<SpecialButton, SpecialButtonState> = mutableMapOf()

    /** The keys for the [SpecialButton] added to [mSpecialButtons]. This is automatically
     * set when the call to [setSpecialButtons] is made. */
    protected var mSpecialButtonsKeys: Set<String> = emptySet()

    /**
     * The list of keys for which auto repeat of key should be triggered if its extra keys button
     * is long pressed. This is done by calling [IExtraKeysView.onExtraKeyButtonClick]
     * every [mLongPressRepeatDelay] seconds after [mLongPressTimeout] has passed.
     * The default keys are defined by [ExtraKeysConstants.PRIMARY_REPETITIVE_KEYS].
     */
    protected var mRepetitiveKeys: List<String> = emptyList()

    /** The text color for the extra keys button. Defaults to [DEFAULT_BUTTON_TEXT_COLOR]. */
    var buttonTextColor: Int = DEFAULT_BUTTON_TEXT_COLOR
        protected set

    /** The text color for the extra keys button when its active.
     * Defaults to [DEFAULT_BUTTON_ACTIVE_TEXT_COLOR]. */
    var buttonActiveTextColor: Int = DEFAULT_BUTTON_ACTIVE_TEXT_COLOR
        protected set

    /** The background color for the extra keys button. Defaults to [DEFAULT_BUTTON_BACKGROUND_COLOR]. */
    var buttonBackgroundColor: Int = DEFAULT_BUTTON_BACKGROUND_COLOR
        protected set

    /** The background color for the extra keys button when its active. Defaults to
     * [DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR]. */
    var buttonActiveBackgroundColor: Int = DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR
        protected set

    /** Defines whether text for the extra keys button should be all capitalized automatically. */
    protected var mButtonTextAllCaps: Boolean = true

    /**
     * Defines the duration in milliseconds before a press turns into a long press. The default
     * duration used is the one returned by a call to [ViewConfiguration.getLongPressTimeout]
     * which will return the system defined duration which can be changed in accessibility settings.
     * The duration must be in between [MIN_LONG_PRESS_DURATION] and [MAX_LONG_PRESS_DURATION],
     * otherwise [FALLBACK_LONG_PRESS_DURATION] is used.
     */
    var longPressTimeout: Int = FALLBACK_LONG_PRESS_DURATION
        set(value) {
            field = if (value in MIN_LONG_PRESS_DURATION..MAX_LONG_PRESS_DURATION) {
                value
            } else {
                FALLBACK_LONG_PRESS_DURATION
            }
        }

    /**
     * Defines the duration in milliseconds for the delay between trigger of each repeat of
     * [mRepetitiveKeys]. The default value is defined by [DEFAULT_LONG_PRESS_REPEAT_DELAY].
     * The duration must be in between [MIN_LONG_PRESS__REPEAT_DELAY] and
     * [MAX_LONG_PRESS__REPEAT_DELAY], otherwise [DEFAULT_LONG_PRESS_REPEAT_DELAY] is used.
     */
    var longPressRepeatDelay: Int = DEFAULT_LONG_PRESS_REPEAT_DELAY
        set(value) {
            field = if (value in MIN_LONG_PRESS__REPEAT_DELAY..MAX_LONG_PRESS__REPEAT_DELAY) {
                value
            } else {
                DEFAULT_LONG_PRESS_REPEAT_DELAY
            }
        }

    /** The popup window shown if [ExtraKeyButton.getPopup] returns a `non-null` value
     * and a swipe up action is done on an extra key. */
    protected var mPopupWindow: PopupWindow? = null

    protected var mScheduledExecutor: ScheduledExecutorService? = null
    protected var mHandler: Handler? = null
    protected var mSpecialButtonsLongHoldRunnable: SpecialButtonsLongHoldRunnable? = null
    protected var mLongPressCount: Int = 0

    init {
        setRepetitiveKeys(ExtraKeysConstants.PRIMARY_REPETITIVE_KEYS)
        setSpecialButtons(getDefaultSpecialButtons(this))

        setButtonColors(
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_TEXT_COLOR, DEFAULT_BUTTON_TEXT_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_TEXT_COLOR, DEFAULT_BUTTON_ACTIVE_TEXT_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_BACKGROUND_COLOR, DEFAULT_BUTTON_BACKGROUND_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR, DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR)
        )

        longPressTimeout = ViewConfiguration.getLongPressTimeout()
        longPressRepeatDelay = DEFAULT_LONG_PRESS_REPEAT_DELAY
    }

    /** Get [mRepetitiveKeys]. */
    fun getRepetitiveKeys(): List<String>? {
        return mRepetitiveKeys.toList()
    }

    /** Set [mRepetitiveKeys]. Must not be `null`. */
    fun setRepetitiveKeys(repetitiveKeys: List<String>) {
        mRepetitiveKeys = repetitiveKeys
    }

    /** Get [mSpecialButtons]. */
    fun getSpecialButtons(): Map<SpecialButton, SpecialButtonState>? {
        return mSpecialButtons.toMap()
    }

    /** Get [mSpecialButtonsKeys]. */
    fun getSpecialButtonsKeys(): Set<String>? {
        return mSpecialButtonsKeys.toSet()
    }

    /** Set [mSpecialButtonsKeys]. Must not be `null`. */
    fun setSpecialButtons(specialButtons: MutableMap<SpecialButton, SpecialButtonState>) {
        mSpecialButtons = specialButtons
        mSpecialButtonsKeys = mSpecialButtons.keys.map { it.key }.toSet()
    }

    /**
     * Set the [ExtraKeysView] button colors.
     *
     * @param buttonTextColor The value for [buttonTextColor].
     * @param buttonActiveTextColor The value for [buttonActiveTextColor].
     * @param buttonBackgroundColor The value for [buttonBackgroundColor].
     * @param buttonActiveBackgroundColor The value for [buttonActiveBackgroundColor].
     */
    fun setButtonColors(buttonTextColor: Int, buttonActiveTextColor: Int, buttonBackgroundColor: Int, buttonActiveBackgroundColor: Int) {
        this.buttonTextColor = buttonTextColor
        this.buttonActiveTextColor = buttonActiveTextColor
        this.buttonBackgroundColor = buttonBackgroundColor
        this.buttonActiveBackgroundColor = buttonActiveBackgroundColor
    }

    /** Set [mButtonTextAllCaps]. */
    fun setButtonTextAllCaps(buttonTextAllCaps: Boolean) {
        mButtonTextAllCaps = buttonTextAllCaps
    }

    /** Get the default map that can be used for [mSpecialButtons]. */
    fun getDefaultSpecialButtons(extraKeysView: ExtraKeysView): MutableMap<SpecialButton, SpecialButtonState> {
        return mutableMapOf(
            SpecialButton.CTRL to SpecialButtonState(extraKeysView),
            SpecialButton.ALT to SpecialButtonState(extraKeysView),
            SpecialButton.SHIFT to SpecialButtonState(extraKeysView),
            SpecialButton.FN to SpecialButtonState(extraKeysView)
        )
    }

    /**
     * Reload this instance of [ExtraKeysView] with the info passed in `extraKeysInfo`.
     *
     * @param extraKeysInfo The [ExtraKeysInfo] that defines the necessary info for the extra keys.
     * @param heightPx The height in pixels of the parent surrounding the [ExtraKeysView]. It must
     *                 be a single child.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun reload(extraKeysInfo: ExtraKeysInfo?, heightPx: Float) {
        if (extraKeysInfo == null)
            return

        for (state in mSpecialButtons.values)
            state.buttons = ArrayList()

        removeAllViews()

        val buttons = extraKeysInfo.getMatrix()

        rowCount = buttons.size
        columnCount = maximumLength(buttons)

        for (row in buttons.indices) {
            for (col in buttons[row].indices) {
                val buttonInfo = buttons[row][col]

                val button: MaterialButton = if (isSpecialButton(buttonInfo)) {
                    createSpecialButton(buttonInfo.key, true) ?: return
                } else {
                    MaterialButton(context, null, android.R.attr.buttonBarButtonStyle)
                }

                button.text = buttonInfo.display
                button.setTextColor(buttonTextColor)
                button.isAllCaps = mButtonTextAllCaps
                button.setPadding(0, 0, 0, 0)

                button.setOnClickListener { view ->
                    performExtraKeyButtonHapticFeedback(view, buttonInfo, button)
                    onAnyExtraKeyButtonClick(view, buttonInfo, button)
                }

                button.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            view.setBackgroundColor(buttonActiveBackgroundColor)
                            // Start long press scheduled executors which will be stopped in next MotionEvent
                            startScheduledExecutors(view, buttonInfo, button)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (buttonInfo.popup != null) {
                                // Show popup on swipe up
                                if (mPopupWindow == null && event.y < 0) {
                                    stopScheduledExecutors()
                                    view.setBackgroundColor(buttonBackgroundColor)
                                    showPopup(view, buttonInfo.popup)
                                }
                                if (mPopupWindow != null && event.y > 0) {
                                    view.setBackgroundColor(buttonActiveBackgroundColor)
                                    dismissPopup()
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            view.setBackgroundColor(buttonBackgroundColor)
                            stopScheduledExecutors()
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            view.setBackgroundColor(buttonBackgroundColor)
                            stopScheduledExecutors()
                            // If ACTION_UP up was not from a repetitive key or was with a key with a popup button
                            if (mLongPressCount == 0 || mPopupWindow != null) {
                                // Trigger popup button click if swipe up complete
                                if (mPopupWindow != null) {
                                    dismissPopup()
                                    buttonInfo.popup?.let { popup: ExtraKeyButton ->
                                        onAnyExtraKeyButtonClick(view, popup, button)
                                    }
                                } else {
                                    view.performClick()
                                }
                            }
                            true
                        }
                        else -> true
                    }
                }

                val param = LayoutParams()
                param.width = 0
                param.height = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                    (heightPx + 0.5).toInt()
                } else {
                    0
                }
                param.setMargins(0, 0, 0, 0)
                param.columnSpec = spec(col, FILL, 1f)
                param.rowSpec = spec(row, FILL, 1f)
                button.layoutParams = param

                addView(button)
            }
        }
    }

    fun onExtraKeyButtonClick(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        extraKeysViewClient?.onExtraKeyButtonClick(view, buttonInfo, button)
    }

    fun performExtraKeyButtonHapticFeedback(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        extraKeysViewClient?.let { client ->
            // If client handled the feedback, then just return
            if (client.performExtraKeyButtonHapticFeedback(view, buttonInfo, button))
                return
        }

        if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {
            if (Build.VERSION.SDK_INT >= 28) {
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                // Perform haptic feedback only if no total silence mode enabled.
                if (Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 2) {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
        }
    }

    fun onAnyExtraKeyButtonClick(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        if (isSpecialButton(buttonInfo)) {
            if (mLongPressCount > 0) return
            val state = mSpecialButtons[SpecialButton.valueOf(buttonInfo.key)] ?: return

            // Toggle active state and disable lock state if new state is not active
            state.setIsActive(!state.isActive)
            if (!state.isActive)
                state.setIsLocked(false)
        } else {
            onExtraKeyButtonClick(view, buttonInfo, button)
        }
    }

    fun startScheduledExecutors(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        stopScheduledExecutors()
        mLongPressCount = 0
        if (mRepetitiveKeys.contains(buttonInfo.key)) {
            // Auto repeat key if long pressed until ACTION_UP stops it by calling stopScheduledExecutors.
            // Currently, only one (last) repeat key can run at a time. Old ones are stopped.
            mScheduledExecutor = Executors.newSingleThreadScheduledExecutor()
            mScheduledExecutor?.scheduleWithFixedDelay({
                mLongPressCount++
                onExtraKeyButtonClick(view, buttonInfo, button)
            }, longPressTimeout.toLong(), longPressRepeatDelay.toLong(), TimeUnit.MILLISECONDS)
        } else if (isSpecialButton(buttonInfo)) {
            // Lock the key if long pressed by running mSpecialButtonsLongHoldRunnable after
            // waiting for mLongPressTimeout milliseconds. If user does not long press, then the
            // ACTION_UP triggered will cancel the runnable by calling stopScheduledExecutors before
            // it has a chance to run.
            val state = mSpecialButtons[SpecialButton.valueOf(buttonInfo.key)] ?: return
            if (mHandler == null)
                mHandler = Handler(Looper.getMainLooper())
            mSpecialButtonsLongHoldRunnable = SpecialButtonsLongHoldRunnable(state)
            mHandler?.postDelayed(mSpecialButtonsLongHoldRunnable!!, longPressTimeout.toLong())
        }
    }

    fun stopScheduledExecutors() {
        mScheduledExecutor?.shutdownNow()
        mScheduledExecutor = null

        mSpecialButtonsLongHoldRunnable?.let { runnable ->
            mHandler?.removeCallbacks(runnable)
        }
        mSpecialButtonsLongHoldRunnable = null
    }

    inner class SpecialButtonsLongHoldRunnable(val state: SpecialButtonState) : Runnable {
        override fun run() {
            // Toggle active and lock state
            state.setIsLocked(!state.isActive)
            state.setIsActive(!state.isActive)
            mLongPressCount++
        }
    }

    fun showPopup(view: View, extraButton: ExtraKeyButton?) {
        if (extraButton == null) return
        val width = view.measuredWidth
        val height = view.measuredHeight
        val button: MaterialButton = if (isSpecialButton(extraButton)) {
            createSpecialButton(extraButton.key, false) ?: return
        } else {
            MaterialButton(context, null, android.R.attr.buttonBarButtonStyle).also {
                it.setTextColor(buttonTextColor)
            }
        }
        button.text = extraButton.display
        button.isAllCaps = mButtonTextAllCaps
        button.setPadding(0, 0, 0, 0)
        button.minimumHeight = 0
        button.minimumWidth = 0
        button.width = width
        button.height = height
        button.setBackgroundColor(buttonActiveBackgroundColor)
        mPopupWindow = PopupWindow(this)
        mPopupWindow?.width = ViewGroup.LayoutParams.WRAP_CONTENT
        mPopupWindow?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        mPopupWindow?.contentView = button
        mPopupWindow?.isOutsideTouchable = true
        mPopupWindow?.isFocusable = false
        mPopupWindow?.showAsDropDown(view, 0, -2 * height)
    }

    fun dismissPopup() {
        mPopupWindow?.contentView = null
        mPopupWindow?.dismiss()
        mPopupWindow = null
    }

    /** Check whether a [ExtraKeyButton] is a [SpecialButton]. */
    fun isSpecialButton(button: ExtraKeyButton): Boolean {
        return mSpecialButtonsKeys.contains(button.key)
    }

    /**
     * Read whether [SpecialButton] registered in [mSpecialButtons] is active or not.
     *
     * @param specialButton The [SpecialButton] to read.
     * @param autoSetInActive Set to `true` if [SpecialButtonState.isActive] should be
     *                        set `false` if button is not locked.
     * @return Returns `null` if button does not exist in [mSpecialButtons]. If button
     *         exists, then returns `true` if the button is created in [ExtraKeysView]
     *         and is active, otherwise `false`.
     */
    fun readSpecialButton(specialButton: SpecialButton, autoSetInActive: Boolean): Boolean? {
        val state = mSpecialButtons[specialButton] ?: return null

        if (!state.isCreated || !state.isActive)
            return false

        // Disable active state only if not locked
        if (autoSetInActive && !state.isLocked)
            state.setIsActive(false)

        return true
    }

    fun createSpecialButton(buttonKey: String, needUpdate: Boolean): MaterialButton? {
        val state = mSpecialButtons[SpecialButton.valueOf(buttonKey)] ?: return null
        state.setIsCreated(true)
        val button = MaterialButton(context, null, android.R.attr.buttonBarButtonStyle)
        button.setTextColor(if (state.isActive) buttonActiveTextColor else buttonTextColor)
        if (needUpdate) {
            state.buttons.add(button)
        }
        return button
    }
}
