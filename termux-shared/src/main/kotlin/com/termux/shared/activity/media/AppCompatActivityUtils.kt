package com.termux.shared.activity.media

import androidx.annotation.IdRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import com.termux.shared.logger.Logger
import com.termux.shared.theme.NightMode

object AppCompatActivityUtils {

    private const val LOG_TAG = "AppCompatActivityUtils"

    /**
     * Set activity night mode.
     *
     * @param activity The host [AppCompatActivity].
     * @param name The [String] representing the name for a [NightMode].
     * @param local If set to true, then a call to [AppCompatDelegate.setLocalNightMode]
     *              will be made, otherwise to [AppCompatDelegate.setDefaultNightMode].
     */
    @JvmStatic
    fun setNightMode(activity: AppCompatActivity?, name: String?, local: Boolean) {
        if (name == null) return
        val nightMode = NightMode.modeOf(name)
        if (nightMode != null) {
            if (local) {
                activity?.delegate?.localNightMode = nightMode.mode
            } else {
                AppCompatDelegate.setDefaultNightMode(nightMode.mode)
            }
        }
    }

    /**
     * Set activity toolbar.
     *
     * @param activity The host [AppCompatActivity].
     * @param id The toolbar resource id.
     */
    @JvmStatic
    fun setToolbar(activity: AppCompatActivity, @IdRes id: Int) {
        val toolbar = activity.findViewById<Toolbar>(id)
        if (toolbar != null) {
            activity.setSupportActionBar(toolbar)
        }
    }

    /**
     * Set activity toolbar title.
     *
     * @param activity The host [AppCompatActivity].
     * @param id The toolbar resource id.
     * @param title The toolbar title [String].
     * @param titleAppearance The toolbar title TextAppearance resource id.
     */
    @JvmStatic
    fun setToolbarTitle(
        activity: AppCompatActivity,
        @IdRes id: Int,
        title: String?,
        @StyleRes titleAppearance: Int
    ) {
        val toolbar = activity.findViewById<Toolbar>(id)
        if (toolbar != null) {
            //toolbar.title = title // Does not work
            activity.supportActionBar?.title = title

            try {
                if (titleAppearance != 0) {
                    toolbar.setTitleTextAppearance(activity, titleAppearance)
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to set toolbar title appearance to style resource id $titleAppearance", e)
            }
        }
    }

    /**
     * Set activity toolbar subtitle.
     *
     * @param activity The host [AppCompatActivity].
     * @param id The toolbar resource id.
     * @param subtitle The toolbar subtitle [String].
     * @param subtitleAppearance The toolbar subtitle TextAppearance resource id.
     */
    @JvmStatic
    fun setToolbarSubtitle(
        activity: AppCompatActivity,
        @IdRes id: Int,
        subtitle: String?,
        @StyleRes subtitleAppearance: Int
    ) {
        val toolbar = activity.findViewById<Toolbar>(id)
        if (toolbar != null) {
            toolbar.subtitle = subtitle
            try {
                if (subtitleAppearance != 0) {
                    toolbar.setSubtitleTextAppearance(activity, subtitleAppearance)
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to set toolbar subtitle appearance to style resource id $subtitleAppearance", e)
            }
        }
    }

    /**
     * Set whether back button should be shown in activity toolbar.
     *
     * @param activity The host [AppCompatActivity].
     * @param showBackButtonInActionBar Set to true to enable and false to disable.
     */
    @JvmStatic
    fun setShowBackButtonInActionBar(activity: AppCompatActivity, showBackButtonInActionBar: Boolean) {
        activity.supportActionBar?.let { actionBar ->
            if (showBackButtonInActionBar) {
                actionBar.setDisplayHomeAsUpEnabled(true)
                actionBar.setDisplayShowHomeEnabled(true)
            } else {
                actionBar.setDisplayHomeAsUpEnabled(false)
                actionBar.setDisplayShowHomeEnabled(false)
            }
        }
    }
}
