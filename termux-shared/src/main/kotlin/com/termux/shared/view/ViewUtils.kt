package com.termux.shared.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.termux.shared.logger.Logger

object ViewUtils {

    /** Log root view events. */
    @JvmField
    var VIEW_UTILS_LOGGING_ENABLED = false

    private const val LOG_TAG = "ViewUtils"

    /**
     * Sets whether view utils logging is enabled or not.
     */
    @JvmStatic
    fun setIsViewUtilsLoggingEnabled(value: Boolean) {
        VIEW_UTILS_LOGGING_ENABLED = value
    }

    /**
     * Check if a [View] is fully visible and not hidden or partially covered by another view.
     */
    @JvmStatic
    fun isViewFullyVisible(view: View?, statusBarHeight: Int): Boolean {
        val windowAndViewRects = getWindowAndViewRects(view, statusBarHeight) ?: return false
        return windowAndViewRects[0].contains(windowAndViewRects[1])
    }

    /**
     * Get the [Rect] of a [View] and the [Rect] of the window inside which it exists.
     */
    @JvmStatic
    fun getWindowAndViewRects(view: View?, statusBarHeight: Int): Array<Rect>? {
        if (view == null || !view.isShown) return null

        val viewUtilsLoggingEnabled = VIEW_UTILS_LOGGING_ENABLED

        // windowRect - will hold available area where content remain visible to users
        val windowRect = Rect()
        view.getWindowVisibleDisplayFrame(windowRect)

        // If there is actionbar, get his height
        var actionBarHeight = 0
        var isInMultiWindowMode = false
        val context = view.context
        when (context) {
            is AppCompatActivity -> {
                val actionBar = context.supportActionBar
                if (actionBar != null) actionBarHeight = actionBar.height
                isInMultiWindowMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && context.isInMultiWindowMode
            }
            is Activity -> {
                val actionBar = context.actionBar
                if (actionBar != null) actionBarHeight = actionBar.height
                isInMultiWindowMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && context.isInMultiWindowMode
            }
        }

        val displayOrientation = getDisplayOrientation(context)

        // windowAvailableRect - takes into account actionbar and statusbar height
        val windowAvailableRect = Rect(windowRect.left, windowRect.top + actionBarHeight, windowRect.right, windowRect.bottom)

        // viewRect - holds position of the view in window
        val viewsLocationInWindow = IntArray(2)
        view.getLocationInWindow(viewsLocationInWindow)
        var viewLeft = viewsLocationInWindow[0]
        var viewTop = viewsLocationInWindow[1]

        if (viewUtilsLoggingEnabled) {
            Logger.logVerbose(LOG_TAG, "getWindowAndViewRects:")
            Logger.logVerbose(LOG_TAG, "windowRect: ${toRectString(windowRect)}, windowAvailableRect: ${toRectString(windowAvailableRect)}")
            Logger.logVerbose(LOG_TAG, "viewsLocationInWindow: ${toPointString(Point(viewLeft, viewTop))}")
            Logger.logVerbose(LOG_TAG, "activitySize: ${toPointString(getDisplaySize(context, true))}, displaySize: ${toPointString(getDisplaySize(context, false))}, displayOrientation=$displayOrientation")
        }

        if (isInMultiWindowMode) {
            if (displayOrientation == Configuration.ORIENTATION_PORTRAIT) {
                if (statusBarHeight != windowRect.top) {
                    if (viewUtilsLoggingEnabled)
                        Logger.logVerbose(LOG_TAG, "Window top does not equal statusBarHeight $statusBarHeight in multi-window portrait mode. Window is possibly bottom app in split screen mode. Adding windowRect.top ${windowRect.top} to viewTop.")
                    viewTop += windowRect.top
                } else {
                    if (viewUtilsLoggingEnabled)
                        Logger.logVerbose(LOG_TAG, "windowRect.top equals statusBarHeight $statusBarHeight in multi-window portrait mode. Window is possibly top app in split screen mode.")
                }
            } else if (displayOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                viewLeft += windowRect.left
            }
        }

        val viewRight = viewLeft + view.width
        val viewBottom = viewTop + view.height
        val viewRect = Rect(viewLeft, viewTop, viewRight, viewBottom)

        if (displayOrientation == Configuration.ORIENTATION_LANDSCAPE && viewRight > windowAvailableRect.right) {
            if (viewUtilsLoggingEnabled)
                Logger.logVerbose(LOG_TAG, "viewRight $viewRight is greater than windowAvailableRect.right ${windowAvailableRect.right} in landscape mode. Setting windowAvailableRect.right to viewRight since it may not include navbar height.")
            windowAvailableRect.right = viewRight
        }

        return arrayOf(windowAvailableRect, viewRect)
    }

    /**
     * Check if [Rect] r2 is above r1. An empty rectangle never contains another rectangle.
     */
    @JvmStatic
    fun isRectAbove(r1: Rect, r2: Rect): Boolean {
        // check for empty first
        return r1.left < r1.right && r1.top < r1.bottom
            // now check if above
            && r1.left <= r2.left && r1.bottom >= r2.bottom
    }

    /**
     * Get device orientation.
     */
    @JvmStatic
    fun getDisplayOrientation(context: Context): Int {
        val size = getDisplaySize(context, false)
        return if (size.x < size.y) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * Get device display size.
     */
    @JvmStatic
    fun getDisplaySize(context: Context, activitySize: Boolean): Point {
        val windowMetrics = if (activitySize) {
            androidx.window.layout.WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(context)
        } else {
            androidx.window.layout.WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(context)
        }
        return Point(windowMetrics.bounds.width(), windowMetrics.bounds.height())
    }

    /** Convert [Rect] to [String]. */
    @JvmStatic
    fun toRectString(rect: Rect?): String {
        if (rect == null) return "null"
        return "(${rect.left},${rect.top}), (${rect.right},${rect.bottom})"
    }

    /** Convert [Point] to [String]. */
    @JvmStatic
    fun toPointString(point: Point?): String {
        if (point == null) return "null"
        return "(${point.x},${point.y})"
    }

    /** Get the [Activity] associated with the [Context] if available. */
    @JvmStatic
    fun getActivity(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    /** Convert value in device independent pixels (dp) to pixels (px) units. */
    @JvmStatic
    fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
    }

    /** Convert value in pixels (px) to device independent pixels (dp) units. */
    @JvmStatic
    fun pxToDp(context: Context, px: Float): Float {
        return px / context.resources.displayMetrics.density
    }

    @JvmStatic
    fun setLayoutMarginsInDp(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        val context = view.context
        setLayoutMarginsInPixels(view, dpToPx(context, left.toFloat()).toInt(), dpToPx(context, top.toFloat()).toInt(),
            dpToPx(context, right.toFloat()).toInt(), dpToPx(context, bottom.toFloat()).toInt())
    }

    @JvmStatic
    fun setLayoutMarginsInPixels(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        val layoutParams = view.layoutParams
        if (layoutParams is ViewGroup.MarginLayoutParams) {
            layoutParams.setMargins(left, top, right, bottom)
            view.layoutParams = layoutParams
        }
    }
}
