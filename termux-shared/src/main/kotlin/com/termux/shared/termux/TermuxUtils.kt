package com.termux.shared.termux

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.termux.shared.R
import com.termux.shared.android.AndroidUtils
import com.termux.shared.android.PackageUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.errors.Error
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.reflection.ReflectionUtils
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.runner.app.AppShell
import com.termux.shared.termux.TermuxConstants.TERMUX_APP
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.nio.charset.Charset
import java.util.regex.Pattern

object TermuxUtils {

    /** The modes used by [getAppInfoMarkdownString]. */
    enum class AppInfoMode {
        /** Get info for Termux app only. */
        TERMUX_PACKAGE,
        /** Get info for Termux app and plugin app if context is of plugin app. */
        TERMUX_AND_PLUGIN_PACKAGE,
        /** Get info for Termux app and its plugins listed in [TermuxConstants.TERMUX_PLUGIN_APP_PACKAGE_NAMES_LIST]. */
        TERMUX_AND_PLUGIN_PACKAGES,
        /* Get info for all the Termux app plugins listed in [TermuxConstants.TERMUX_PLUGIN_APP_PACKAGE_NAMES_LIST]. */
        TERMUX_PLUGIN_PACKAGES,
        /* Get info for Termux app and the calling package that called a Termux API. */
        TERMUX_AND_CALLING_PACKAGE
    }

    private const val LOG_TAG = "TermuxUtils"

    /**
     * Get the [Context] for [TermuxConstants.TERMUX_PACKAGE_NAME] package with the
     * [Context.CONTEXT_RESTRICTED] flag.
     *
     * @param context The [Context] to use to get the [Context] of the package.
     * @return Returns the [Context]. This will `null` if an exception is raised.
     */
    @JvmStatic
    fun getTermuxPackageContext(context: Context): Context? {
        return PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_PACKAGE_NAME)
    }

    /**
     * Get the [Context] for [TermuxConstants.TERMUX_PACKAGE_NAME] package with the
     * [Context.CONTEXT_INCLUDE_CODE] flag.
     *
     * @param context The [Context] to use to get the [Context] of the package.
     * @return Returns the [Context]. This will `null` if an exception is raised.
     */
    @JvmStatic
    fun getTermuxPackageContextWithCode(context: Context): Context? {
        return PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_PACKAGE_NAME, Context.CONTEXT_INCLUDE_CODE)
    }

    /**
     * Get the [Context] for [TermuxConstants.TERMUX_API_PACKAGE_NAME] package.
     *
     * @param context The [Context] to use to get the [Context] of the package.
     * @return Returns the [Context]. This will `null` if an exception is raised.
     */
    @JvmStatic
    fun getTermuxAPIPackageContext(context: Context): Context? {
        return PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_API_PACKAGE_NAME)
    }

    /**
     * Get the [Context] for [TermuxConstants.TERMUX_BOOT_PACKAGE_NAME] package.
     *
     * @param context The [Context] to use to get the [Context] of the package.
     * @return Returns the [Context]. This will `null` if an exception is raised.
     */
    @JvmStatic
    fun getTermuxBootPackageContext(context: Context): Context? {
        return PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_BOOT_PACKAGE_NAME)
    }

    /**
     * Get the [Context] for [TermuxConstants.TERMUX_FLOAT_PACKAGE_NAME] package.
     *
     * @param context The [Context] to use to get the [Context] of the package.
     * @return Returns the [Context]. This will `null` if an exception is raised.
     */
    @JvmStatic
    fun getTermuxFloatPackageContext(context: Context): Context? {
        return PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_FLOAT_PACKAGE_NAME)
    }

    /**
     * Get the [Context] for [TermuxConstants.TERMUX_STYLING_PACKAGE_NAME] package.
     *
     * @param context The [Context] to use to get the [Context] of the package.
     * @return Returns the [Context]. This will `null` if an exception is raised.
     */
    @JvmStatic
    fun getTermuxStylingPackageContext(context: Context): Context? {
        return PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_STYLING_PACKAGE_NAME)
    }

    /**
     * Get the [Context] for [TermuxConstants.TERMUX_TASKER_PACKAGE_NAME] package.
     *
     * @param context The [Context] to use to get the [Context] of the package.
     * @return Returns the [Context]. This will `null` if an exception is raised.
     */
    @JvmStatic
    fun getTermuxTaskerPackageContext(context: Context): Context? {
        return PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_TASKER_PACKAGE_NAME)
    }

    /**
     * Get the [Context] for [TermuxConstants.TERMUX_WIDGET_PACKAGE_NAME] package.
     *
     * @param context The [Context] to use to get the [Context] of the package.
     * @return Returns the [Context]. This will `null` if an exception is raised.
     */
    @JvmStatic
    fun getTermuxWidgetPackageContext(context: Context): Context? {
        return PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_WIDGET_PACKAGE_NAME)
    }

    /** Wrapper for [PackageUtils.getContextForPackageOrExitApp]. */
    @JvmStatic
    fun getContextForPackageOrExitApp(context: Context, packageName: String?, exitAppOnError: Boolean): Context? {
        return PackageUtils.getContextForPackageOrExitApp(context, packageName, exitAppOnError, TermuxConstants.TERMUX_GITHUB_REPO_URL)
    }

    /**
     * Check if Termux app is installed and enabled. This can be used by external apps that don't
     * share `sharedUserId` with the Termux app.
     *
     * If your third-party app is targeting sdk `30` (android `11`), then it needs to add `com.termux`
     * package to the `queries` element or request `QUERY_ALL_PACKAGES` permission in its
     * `AndroidManifest.xml`. Otherwise it will get `PackageSetting{...... com.termux/......} BLOCKED`
     * errors in `logcat` and `RUN_COMMAND` won't work.
     *
     * @param context The context for operations.
     * @return Returns `errmsg` if [TermuxConstants.TERMUX_PACKAGE_NAME] is not installed
     * or disabled, otherwise `null`.
     */
    @JvmStatic
    fun isTermuxAppInstalled(context: Context): String? {
        return PackageUtils.isAppInstalled(context, TermuxConstants.TERMUX_APP_NAME, TermuxConstants.TERMUX_PACKAGE_NAME)
    }

    /**
     * Check if Termux:API app is installed and enabled. This can be used by external apps that don't
     * share `sharedUserId` with the Termux:API app.
     *
     * @param context The context for operations.
     * @return Returns `errmsg` if [TermuxConstants.TERMUX_API_PACKAGE_NAME] is not installed
     * or disabled, otherwise `null`.
     */
    @JvmStatic
    fun isTermuxAPIAppInstalled(context: Context): String? {
        return PackageUtils.isAppInstalled(context, TermuxConstants.TERMUX_API_APP_NAME, TermuxConstants.TERMUX_API_PACKAGE_NAME)
    }

    /**
     * Check if Termux app is installed and accessible. This can only be used by apps that share
     * `sharedUserId` with the Termux app.
     *
     * @param currentPackageContext The context of current package.
     * @return Returns `errmsg` if failed to get termux package [Context] or
     * [TermuxConstants.TERMUX_PREFIX_DIR_PATH] is accessible, otherwise `null`.
     */
    @JvmStatic
    fun isTermuxAppAccessible(currentPackageContext: Context): String? {
        var errmsg = isTermuxAppInstalled(currentPackageContext)
        if (errmsg == null) {
            val termuxPackageContext = getTermuxPackageContext(currentPackageContext)
            // If failed to get Termux app package context
            if (termuxPackageContext == null)
                errmsg = currentPackageContext.getString(R.string.error_termux_app_package_context_not_accessible)

            if (errmsg == null) {
                // If TermuxConstants.TERMUX_PREFIX_DIR_PATH is not a directory or does not have required permissions
                val error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(false, false)
                if (error != null)
                    errmsg = currentPackageContext.getString(
                        R.string.error_termux_prefix_dir_path_not_accessible,
                        PackageUtils.getAppNameForPackage(currentPackageContext)
                    )
            }
        }

        return if (errmsg != null)
            "$errmsg " + currentPackageContext.getString(
                R.string.msg_termux_app_required_by_app,
                PackageUtils.getAppNameForPackage(currentPackageContext)
            )
        else
            null
    }

    /**
     * Get a field value from the [TERMUX_APP.BUILD_CONFIG_CLASS_NAME] class of the Termux app
     * APK installed on the device.
     * This can only be used by apps that share `sharedUserId` with the Termux app.
     *
     * @param currentPackageContext The context of current package.
     * @param fieldName The name of the field to get.
     * @return Returns the field value, otherwise `null` if an exception was raised or failed
     * to get termux app package context.
     */
    @JvmStatic
    fun getTermuxAppAPKBuildConfigClassField(currentPackageContext: Context, fieldName: String): Any? {
        return getTermuxAppAPKClassField(currentPackageContext, TERMUX_APP.BUILD_CONFIG_CLASS_NAME, fieldName)
    }

    /**
     * Get a field value from a class of the Termux app APK installed on the device.
     * This can only be used by apps that share `sharedUserId` with the Termux app.
     *
     * @param currentPackageContext The context of current package.
     * @param clazzName The name of the class from which to get the field.
     * @param fieldName The name of the field to get.
     * @return Returns the field value, otherwise `null` if an exception was raised or failed
     * to get termux app package context.
     */
    @JvmStatic
    fun getTermuxAppAPKClassField(currentPackageContext: Context, clazzName: String, fieldName: String): Any? {
        return try {
            val termuxPackageContext = getTermuxPackageContextWithCode(currentPackageContext) ?: return null
            val clazz = termuxPackageContext.classLoader?.loadClass(clazzName) ?: return null
            ReflectionUtils.invokeField(clazz, fieldName, null).value
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get \"$fieldName\" value from \"$clazzName\" class", e)
            null
        }
    }

    /** Returns `true` if [Uri] has `package:` scheme for [TermuxConstants.TERMUX_PACKAGE_NAME] or its sub plugin package. */
    @JvmStatic
    fun isUriDataForTermuxOrPluginPackage(data: Uri): Boolean {
        return data.toString() == "package:${TermuxConstants.TERMUX_PACKAGE_NAME}" ||
            data.toString().startsWith("package:${TermuxConstants.TERMUX_PACKAGE_NAME}.")
    }

    /** Returns `true` if [Uri] has `package:` scheme for [TermuxConstants.TERMUX_PACKAGE_NAME] sub plugin package. */
    @JvmStatic
    fun isUriDataForTermuxPluginPackage(data: Uri): Boolean {
        return data.toString().startsWith("package:${TermuxConstants.TERMUX_PACKAGE_NAME}.")
    }

    /**
     * Send the [TermuxConstants.BROADCAST_TERMUX_OPENED] broadcast to notify apps that Termux
     * app has been opened.
     *
     * @param context The Context to send the broadcast.
     */
    @JvmStatic
    fun sendTermuxOpenedBroadcast(context: Context) {
        val broadcast = Intent(TermuxConstants.BROADCAST_TERMUX_OPENED)
        val matches = context.packageManager.queryBroadcastReceivers(broadcast, 0)

        // send broadcast to registered Termux receivers
        // this technique is needed to work around broadcast changes that Oreo introduced
        for (info in matches) {
            val explicitBroadcast = Intent(broadcast)
            val cname = ComponentName(
                info.activityInfo.applicationInfo.packageName,
                info.activityInfo.name
            )
            explicitBroadcast.component = cname
            context.sendBroadcast(explicitBroadcast)
        }
    }

    /**
     * Wrapper for [getAppInfoMarkdownString].
     *
     * @param currentPackageContext The context of current package.
     * @param appInfoMode The [AppInfoMode] to decide the app info required.
     * @return Returns the markdown [String].
     */
    @JvmStatic
    fun getAppInfoMarkdownString(currentPackageContext: Context, appInfoMode: AppInfoMode): String? {
        return getAppInfoMarkdownString(currentPackageContext, appInfoMode, null)
    }

    /**
     * Get a markdown [String] for the apps info of termux app, its installed plugin apps or
     * external apps that called a Termux API depending on [AppInfoMode] passed.
     *
     * @param currentPackageContext The context of current package.
     * @param appInfoMode The [AppInfoMode] to decide the app info required.
     * @param callingPackageName The optional package name for a plugin or external app.
     * @return Returns the markdown [String].
     */
    @JvmStatic
    fun getAppInfoMarkdownString(currentPackageContext: Context, appInfoMode: AppInfoMode?, callingPackageName: String?): String? {
        if (appInfoMode == null) return null

        val appInfo = StringBuilder()
        return when (appInfoMode) {
            AppInfoMode.TERMUX_PACKAGE -> getAppInfoMarkdownString(currentPackageContext, false)

            AppInfoMode.TERMUX_AND_PLUGIN_PACKAGE -> getAppInfoMarkdownString(currentPackageContext, true)

            AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES -> {
                appInfo.append(getAppInfoMarkdownString(currentPackageContext, false))
                val termuxPluginAppsInfo = getTermuxPluginAppsInfoMarkdownString(currentPackageContext)
                if (termuxPluginAppsInfo != null)
                    appInfo.append("\n\n").append(termuxPluginAppsInfo)
                appInfo.toString()
            }

            AppInfoMode.TERMUX_PLUGIN_PACKAGES -> getTermuxPluginAppsInfoMarkdownString(currentPackageContext)

            AppInfoMode.TERMUX_AND_CALLING_PACKAGE -> {
                appInfo.append(getAppInfoMarkdownString(currentPackageContext, false))
                if (!DataUtils.isNullOrEmpty(callingPackageName)) {
                    var callingPackageAppInfo: String? = null
                    if (TermuxConstants.TERMUX_PLUGIN_APP_PACKAGE_NAMES_LIST.contains(callingPackageName)) {
                        val termuxPluginAppContext = PackageUtils.getContextForPackage(currentPackageContext, callingPackageName!!)
                        if (termuxPluginAppContext != null)
                            appInfo.append(getAppInfoMarkdownString(termuxPluginAppContext, false))
                        else
                            callingPackageAppInfo = AndroidUtils.getAppInfoMarkdownString(currentPackageContext, callingPackageName) ?: ""
                    } else {
                        callingPackageAppInfo = AndroidUtils.getAppInfoMarkdownString(currentPackageContext, callingPackageName!!) ?: ""
                    }

                    if (callingPackageAppInfo != null && callingPackageAppInfo.isNotEmpty()) {
                        val applicationInfo = PackageUtils.getApplicationInfoForPackage(currentPackageContext, callingPackageName!!)
                        if (applicationInfo != null) {
                            appInfo.append("\n\n## ").append(PackageUtils.getAppNameForPackage(currentPackageContext, applicationInfo)).append(" App Info\n")
                            appInfo.append(callingPackageAppInfo)
                            appInfo.append("\n##\n")
                        }
                    }
                }
                appInfo.toString()
            }
        }
    }

    /**
     * Get a markdown [String] for the apps info of all/any termux plugin apps installed.
     *
     * @param currentPackageContext The context of current package.
     * @return Returns the markdown [String].
     */
    @JvmStatic
    fun getTermuxPluginAppsInfoMarkdownString(currentPackageContext: Context?): String? {
        if (currentPackageContext == null) return "null"

        val markdownString = StringBuilder()

        val termuxPluginAppPackageNamesList = TermuxConstants.TERMUX_PLUGIN_APP_PACKAGE_NAMES_LIST

        for (i in termuxPluginAppPackageNamesList.indices) {
            val termuxPluginAppPackageName = termuxPluginAppPackageNamesList[i]
            val termuxPluginAppContext = PackageUtils.getContextForPackage(currentPackageContext, termuxPluginAppPackageName)
            // If the package context for the plugin app is not null, then assume its installed and get its info
            if (termuxPluginAppContext != null) {
                if (i != 0)
                    markdownString.append("\n\n")
                markdownString.append(getAppInfoMarkdownString(termuxPluginAppContext, false))
            }
        }

        return if (markdownString.isEmpty()) null else markdownString.toString()
    }

    /**
     * Get a markdown [String] for the app info. If the `context` passed is different
     * from the [TermuxConstants.TERMUX_PACKAGE_NAME] package context, then this function
     * must have been called by a different package like a plugin, so we return info for both packages
     * if `returnTermuxPackageInfoToo` is `true`.
     *
     * @param currentPackageContext The context of current package.
     * @param returnTermuxPackageInfoToo If set to `true`, then will return info of the
     * [TermuxConstants.TERMUX_PACKAGE_NAME] package as well if its different from current package.
     * @return Returns the markdown [String].
     */
    @JvmStatic
    fun getAppInfoMarkdownString(currentPackageContext: Context?, returnTermuxPackageInfoToo: Boolean): String? {
        if (currentPackageContext == null) return "null"

        val markdownString = StringBuilder()

        val termuxPackageContext = getTermuxPackageContext(currentPackageContext)

        var termuxPackageName: String? = null
        var termuxAppName: String? = null
        if (termuxPackageContext != null) {
            termuxPackageName = PackageUtils.getPackageNameForPackage(termuxPackageContext)
            termuxAppName = PackageUtils.getAppNameForPackage(termuxPackageContext)
        }

        val currentPackageName = PackageUtils.getPackageNameForPackage(currentPackageContext)
        val currentAppName = PackageUtils.getAppNameForPackage(currentPackageContext)

        val isTermuxPackage = termuxPackageName != null && termuxPackageName == currentPackageName

        if (returnTermuxPackageInfoToo && !isTermuxPackage)
            markdownString.append("## ").append(currentAppName).append(" App Info (Current)\n")
        else
            markdownString.append("## ").append(currentAppName).append(" App Info\n")
        markdownString.append(getAppInfoMarkdownStringInner(currentPackageContext))
        markdownString.append("\n##\n")

        if (returnTermuxPackageInfoToo && termuxPackageContext != null && !isTermuxPackage) {
            markdownString.append("\n\n## ").append(termuxAppName).append(" App Info\n")
            markdownString.append(getAppInfoMarkdownStringInner(termuxPackageContext))
            markdownString.append("\n##\n")
        }

        return markdownString.toString()
    }

    /**
     * Get a markdown [String] for the app info for the package associated with the `context`.
     *
     * @param context The context for operations for the package.
     * @return Returns the markdown [String].
     */
    @JvmStatic
    fun getAppInfoMarkdownStringInner(context: Context): String {
        val markdownString = StringBuilder()

        markdownString.append(AndroidUtils.getAppInfoMarkdownString(context))

        if (context.packageName == TermuxConstants.TERMUX_PACKAGE_NAME) {
            AndroidUtils.appendPropertyToMarkdown(markdownString, "TERMUX_APP_PACKAGE_MANAGER", TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER)
            AndroidUtils.appendPropertyToMarkdown(markdownString, "TERMUX_APP_PACKAGE_VARIANT", TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT)
        }

        val error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(context, true, true)
        if (error != null) {
            AndroidUtils.appendPropertyToMarkdown(markdownString, "TERMUX_FILES_DIR", TermuxConstants.TERMUX_FILES_DIR_PATH)
            AndroidUtils.appendPropertyToMarkdown(markdownString, "IS_TERMUX_FILES_DIR_ACCESSIBLE", "false - " + Error.getMinimalErrorString(error))
        }

        val signingCertificateSHA256Digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context)
        if (signingCertificateSHA256Digest != null) {
            AndroidUtils.appendPropertyToMarkdown(markdownString, "APK_RELEASE", getAPKRelease(signingCertificateSHA256Digest))
            AndroidUtils.appendPropertyToMarkdown(markdownString, "SIGNING_CERTIFICATE_SHA256_DIGEST", signingCertificateSHA256Digest)
        }

        return markdownString.toString()
    }

    /**
     * Get a markdown [String] for reporting an issue.
     *
     * @param context The context for operations.
     * @return Returns the markdown [String].
     */
    @JvmStatic
    fun getReportIssueMarkdownString(context: Context): String {
        val markdownString = StringBuilder()

        markdownString.append("## Where To Report An Issue")

        markdownString.append("\n\n").append(context.getString(R.string.msg_report_issue, TermuxConstants.TERMUX_WIKI_URL)).append("\n")

        markdownString.append("\n\n### Email\n")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_SUPPORT_EMAIL_URL, TermuxConstants.TERMUX_SUPPORT_EMAIL_MAILTO_URL)).append("  ")

        markdownString.append("\n\n### Reddit\n")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_REDDIT_SUBREDDIT, TermuxConstants.TERMUX_REDDIT_SUBREDDIT_URL)).append("  ")

        markdownString.append("\n\n### GitHub Issues for Termux apps\n")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_APP_NAME, TermuxConstants.TERMUX_GITHUB_ISSUES_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_API_APP_NAME, TermuxConstants.TERMUX_API_GITHUB_ISSUES_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_BOOT_APP_NAME, TermuxConstants.TERMUX_BOOT_GITHUB_ISSUES_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_FLOAT_APP_NAME, TermuxConstants.TERMUX_FLOAT_GITHUB_ISSUES_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_STYLING_APP_NAME, TermuxConstants.TERMUX_STYLING_GITHUB_ISSUES_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_TASKER_APP_NAME, TermuxConstants.TERMUX_TASKER_GITHUB_ISSUES_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_WIDGET_APP_NAME, TermuxConstants.TERMUX_WIDGET_GITHUB_ISSUES_REPO_URL)).append("  ")

        markdownString.append("\n\n### GitHub Issues for Termux packages\n")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_PACKAGES_GITHUB_REPO_NAME, TermuxConstants.TERMUX_PACKAGES_GITHUB_ISSUES_REPO_URL)).append("  ")

        markdownString.append("\n##\n")

        return markdownString.toString()
    }

    /**
     * Get a markdown [String] for important links.
     *
     * @param context The context for operations.
     * @return Returns the markdown [String].
     */
    @JvmStatic
    fun getImportantLinksMarkdownString(context: Context): String {
        val markdownString = StringBuilder()

        markdownString.append("## Important Links")

        markdownString.append("\n\n### GitHub\n")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_APP_NAME, TermuxConstants.TERMUX_GITHUB_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_API_APP_NAME, TermuxConstants.TERMUX_API_GITHUB_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_BOOT_APP_NAME, TermuxConstants.TERMUX_BOOT_GITHUB_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_FLOAT_APP_NAME, TermuxConstants.TERMUX_FLOAT_GITHUB_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_STYLING_APP_NAME, TermuxConstants.TERMUX_STYLING_GITHUB_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_TASKER_APP_NAME, TermuxConstants.TERMUX_TASKER_GITHUB_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_WIDGET_APP_NAME, TermuxConstants.TERMUX_WIDGET_GITHUB_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_PACKAGES_GITHUB_REPO_NAME, TermuxConstants.TERMUX_PACKAGES_GITHUB_REPO_URL)).append("  ")

        markdownString.append("\n\n### Email\n")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_SUPPORT_EMAIL_URL, TermuxConstants.TERMUX_SUPPORT_EMAIL_MAILTO_URL)).append("  ")

        markdownString.append("\n\n### Reddit\n")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_REDDIT_SUBREDDIT, TermuxConstants.TERMUX_REDDIT_SUBREDDIT_URL)).append("  ")

        markdownString.append("\n\n### Wiki\n")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_WIKI, TermuxConstants.TERMUX_WIKI_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_APP_NAME, TermuxConstants.TERMUX_GITHUB_WIKI_REPO_URL)).append("  ")
        markdownString.append("\n").append(MarkdownUtils.getLinkMarkdownString(TermuxConstants.TERMUX_PACKAGES_GITHUB_REPO_NAME, TermuxConstants.TERMUX_PACKAGES_GITHUB_WIKI_REPO_URL)).append("  ")

        markdownString.append("\n##\n")

        return markdownString.toString()
    }

    /**
     * Get a markdown [String] for APT info of the app.
     *
     * This will take a few seconds to run due to running `apt update` command.
     *
     * @param context The context for operations.
     * @return Returns the markdown [String].
     */
    @JvmStatic
    fun geAPTInfoMarkdownString(context: Context): String? {
        val inputStream = context.resources.openRawResource(R.raw.apt_info_script)
        val aptInfoScript: String
        try {
            aptInfoScript = IOUtils.toString(inputStream, Charset.defaultCharset())
        } catch (e: IOException) {
            Logger.logError(LOG_TAG, "Failed to get APT info script: ${e.message}")
            return null
        }

        IOUtils.closeQuietly(inputStream)

        if (aptInfoScript.isEmpty()) {
            Logger.logError(LOG_TAG, "The APT info script is null or empty")
            return null
        }

        val processedScript = aptInfoScript.replace(Pattern.quote("@TERMUX_PREFIX@").toRegex(), TermuxConstants.TERMUX_PREFIX_DIR_PATH)

        val executionCommand = ExecutionCommand(
            -1,
            "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/bash",
            null,
            processedScript,
            null,
            ExecutionCommand.Runner.APP_SHELL.getName(),
            false
        )
        executionCommand.commandLabel = "APT Info Command"
        executionCommand.backgroundCustomLogLevel = Logger.LOG_LEVEL_OFF
        val appShell = AppShell.execute(context, executionCommand, null, TermuxShellEnvironment(), null, true)
        if (appShell == null || !executionCommand.isSuccessful() || executionCommand.resultData.exitCode != 0) {
            Logger.logErrorExtended(LOG_TAG, executionCommand.toString())
            return null
        }

        if (executionCommand.resultData.stderr.toString().isNotEmpty())
            Logger.logErrorExtended(LOG_TAG, executionCommand.toString())

        val markdownString = StringBuilder()

        markdownString.append("## ").append(TermuxConstants.TERMUX_APP_NAME).append(" APT Info\n\n")
        markdownString.append(executionCommand.resultData.stdout.toString())
        markdownString.append("\n##\n")

        return markdownString.toString()
    }

    /**
     * Get a markdown [String] for info for termux debugging.
     *
     * @param context The context for operations.
     * @return Returns the markdown [String].
     */
    @JvmStatic
    fun getTermuxDebugMarkdownString(context: Context): String? {
        val statInfo = TermuxFileUtils.getTermuxFilesStatMarkdownString(context)
        val logcatInfo = getLogcatDumpMarkdownString(context)

        return when {
            statInfo != null && logcatInfo != null -> "$statInfo\n\n$logcatInfo"
            statInfo != null -> statInfo
            else -> logcatInfo
        }
    }

    /**
     * Get a markdown [String] for logcat command dump.
     *
     * @param context The context for operations.
     * @return Returns the markdown [String].
     */
    @JvmStatic
    fun getLogcatDumpMarkdownString(context: Context): String? {
        // Build script
        val logcatScript = "/system/bin/logcat -d -t 3000 2>&1"

        // Run script
        val executionCommand = ExecutionCommand(
            -1, "/system/bin/sh",
            null, "$logcatScript\n", "/", ExecutionCommand.Runner.APP_SHELL.getName(), true
        )
        executionCommand.commandLabel = "Logcat dump command"
        executionCommand.backgroundCustomLogLevel = Logger.LOG_LEVEL_OFF
        val appShell = AppShell.execute(context, executionCommand, null, TermuxShellEnvironment(), null, true)
        if (appShell == null || !executionCommand.isSuccessful()) {
            Logger.logErrorExtended(LOG_TAG, executionCommand.toString())
            return null
        }

        // Build script output
        val logcatOutput = StringBuilder()
        logcatOutput.append("$ ").append(logcatScript)
        logcatOutput.append("\n").append(executionCommand.resultData.stdout.toString())

        val stderrSet = executionCommand.resultData.stderr.toString().isNotEmpty()
        if (executionCommand.resultData.exitCode != 0 || stderrSet) {
            Logger.logErrorExtended(LOG_TAG, executionCommand.toString())
            if (stderrSet)
                logcatOutput.append("\n").append(executionCommand.resultData.stderr.toString())
            logcatOutput.append("\n").append("exit code: ").append(executionCommand.resultData.exitCode.toString())
        }

        // Build markdown output
        val markdownString = StringBuilder()
        markdownString.append("## Logcat Dump\n\n")
        markdownString.append("\n\n").append(MarkdownUtils.getMarkdownCodeForString(logcatOutput.toString(), true))
        markdownString.append("\n##\n")

        return markdownString.toString()
    }

    @JvmStatic
    fun getAPKRelease(signingCertificateSHA256Digest: String?): String {
        if (signingCertificateSHA256Digest == null) return "null"

        return when (signingCertificateSHA256Digest.uppercase()) {
            TermuxConstants.APK_RELEASE_FDROID_SIGNING_CERTIFICATE_SHA256_DIGEST -> TermuxConstants.APK_RELEASE_FDROID
            TermuxConstants.APK_RELEASE_GITHUB_SIGNING_CERTIFICATE_SHA256_DIGEST -> TermuxConstants.APK_RELEASE_GITHUB
            TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST -> TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE
            TermuxConstants.APK_RELEASE_TERMUX_DEVS_SIGNING_CERTIFICATE_SHA256_DIGEST -> TermuxConstants.APK_RELEASE_TERMUX_DEVS
            else -> "Unknown"
        }
    }

    /**
     * Get a process id of the main app process of the [TermuxConstants.TERMUX_PACKAGE_NAME]
     * package.
     *
     * @param context The context for operations.
     * @return Returns the process if found and running, otherwise `null`.
     */
    @JvmStatic
    fun getTermuxAppPID(context: Context): String? {
        return PackageUtils.getPackagePID(context, TermuxConstants.TERMUX_PACKAGE_NAME)
    }
}
