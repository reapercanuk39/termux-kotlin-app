package com.termux.app.terminal.io

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.termux.app.TermuxActivity

/**
 * Work around for fullscreen mode in Termux to fix ExtraKeysView not being visible.
 * 
 * This class is derived from:
 * https://stackoverflow.com/questions/7417123/android-how-to-adjust-layout-in-full-screen-mode-when-softkeyboard-is-visible
 * and has some additional tweaks.
 * 
 * For more information, see https://issuetracker.google.com/issues/36911528
 */
class FullScreenWorkAround private constructor(activity: TermuxActivity) {

    private val childOfContent: View
    private var usableHeightPrevious: Int = 0
    private val viewGroupLayoutParams: ViewGroup.LayoutParams
    private val navBarHeight: Int

    init {
        val content: ViewGroup = activity.findViewById(android.R.id.content)
        childOfContent = content.getChildAt(0)
        viewGroupLayoutParams = childOfContent.layoutParams
        navBarHeight = activity.navBarHeight
        childOfContent.viewTreeObserver.addOnGlobalLayoutListener { possiblyResizeChildOfContent() }
    }

    private fun possiblyResizeChildOfContent() {
        val usableHeightNow = computeUsableHeight()
        if (usableHeightNow != usableHeightPrevious) {
            val usableHeightSansKeyboard = childOfContent.rootView.height
            val heightDifference = usableHeightSansKeyboard - usableHeightNow
            if (heightDifference > usableHeightSansKeyboard / 4) {
                // keyboard probably just became visible
                // ensures that usable layout space does not extend behind the
                // soft keyboard, causing the extra keys to not be visible
                viewGroupLayoutParams.height = (usableHeightSansKeyboard - heightDifference) + navBarHeight
            } else {
                // keyboard probably just became hidden
                viewGroupLayoutParams.height = usableHeightSansKeyboard
            }
            childOfContent.requestLayout()
            usableHeightPrevious = usableHeightNow
        }
    }

    private fun computeUsableHeight(): Int {
        val r = Rect()
        childOfContent.getWindowVisibleDisplayFrame(r)
        return r.bottom - r.top
    }

    companion object {
        @JvmStatic
        fun apply(activity: TermuxActivity) {
            FullScreenWorkAround(activity)
        }
    }
}
