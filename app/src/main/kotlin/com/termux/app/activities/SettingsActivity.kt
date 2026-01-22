package com.termux.app.activities

import android.content.Context
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.termux.R
import com.termux.shared.activities.ReportActivity
import com.termux.shared.file.FileUtils
import com.termux.shared.models.ReportInfo
import com.termux.app.models.UserAction
import com.termux.shared.interact.ShareUtils
import com.termux.shared.android.PackageUtils
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences
import com.termux.shared.android.AndroidUtils
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.activity.media.AppCompatActivityUtils
import com.termux.shared.theme.NightMode

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().name, true)

        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, RootPreferencesFragment())
                .commit()
        }

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar)
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true)
    }

    @Suppress("DEPRECATION")
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    class RootPreferencesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val context = context ?: return

            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            Thread {
                configureTermuxAPIPreference(context)
                configureTermuxFloatPreference(context)
                configureTermuxTaskerPreference(context)
                configureTermuxWidgetPreference(context)
                configureAboutPreference(context)
                configureDonatePreference(context)
            }.start()
        }

        private fun configureTermuxAPIPreference(context: Context) {
            val termuxAPIPreference = findPreference<Preference>("termux_api")
            if (termuxAPIPreference != null) {
                // Termux:API is built-in since v2.0.5 - always show settings
                termuxAPIPreference.isVisible = true
            }
        }

        private fun configureTermuxFloatPreference(context: Context) {
            val termuxFloatPreference = findPreference<Preference>("termux_float")
            if (termuxFloatPreference != null) {
                // Termux:Float is still a separate app - check if installed
                val preferences = TermuxFloatAppSharedPreferences.build(context, false)
                termuxFloatPreference.isVisible = preferences != null
            }
        }

        private fun configureTermuxTaskerPreference(context: Context) {
            val termuxTaskerPreference = findPreference<Preference>("termux_tasker")
            if (termuxTaskerPreference != null) {
                // Termux:Tasker is still a separate app - check if installed
                val preferences = TermuxTaskerAppSharedPreferences.build(context, false)
                termuxTaskerPreference.isVisible = preferences != null
            }
        }

        private fun configureTermuxWidgetPreference(context: Context) {
            val termuxWidgetPreference = findPreference<Preference>("termux_widget")
            if (termuxWidgetPreference != null) {
                // Termux:Widget is built-in since v2.0.5 - always show settings
                termuxWidgetPreference.isVisible = true
            }
        }

        private fun configureAboutPreference(context: Context) {
            val aboutPreference = findPreference<Preference>("about")
            if (aboutPreference != null) {
                aboutPreference.setOnPreferenceClickListener {
                    Thread {
                        val title = "About"

                        val aboutString = StringBuilder()
                        aboutString.append(TermuxUtils.getAppInfoMarkdownString(context, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES))
                        aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true))
                        aboutString.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(context))

                        val userActionName = UserAction.ABOUT.name

                        val reportInfo = ReportInfo(
                            userActionName,
                            TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME,
                            title
                        )
                        reportInfo.setReportString(aboutString.toString())
                        reportInfo.setReportSaveFileLabelAndPath(
                            userActionName,
                            Environment.getExternalStorageDirectory().toString() + "/" +
                                FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true)
                        )

                        ReportActivity.startReportActivity(context, reportInfo)
                    }.start()

                    true
                }
            }
        }

        private fun configureDonatePreference(context: Context) {
            val donatePreference = findPreference<Preference>("donate")
            if (donatePreference != null) {
                val signingCertificateSHA256Digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context)
                if (signingCertificateSHA256Digest != null) {
                    // If APK is a Google Playstore release, then do not show the donation link
                    // since Termux isn't exempted from the playstore policy donation links restriction
                    // Check Fund solicitations: https://pay.google.com/intl/en_in/about/policy/
                    val apkRelease = TermuxUtils.getAPKRelease(signingCertificateSHA256Digest)
                    if (apkRelease == null || apkRelease == TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST) {
                        donatePreference.isVisible = false
                        return
                    } else {
                        donatePreference.isVisible = true
                    }
                }

                donatePreference.setOnPreferenceClickListener {
                    ShareUtils.openUrl(context, TermuxConstants.TERMUX_DONATE_URL)
                    true
                }
            }
        }
    }
}
