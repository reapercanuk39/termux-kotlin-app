package com.termux.shared.termux

import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants.TERMUX_APP

object TermuxBootstrap {

    private const val LOG_TAG = "TermuxBootstrap"

    /** The field name used by Termux app to store package variant in
     * [TERMUX_APP.BUILD_CONFIG_CLASS_NAME] class. */
    const val BUILD_CONFIG_FIELD_TERMUX_PACKAGE_VARIANT = "TERMUX_PACKAGE_VARIANT"

    /** The [PackageManager] for the bootstrap in the app APK added in app/build.gradle. */
    @JvmField
    var TERMUX_APP_PACKAGE_MANAGER: PackageManager? = null

    /** The [PackageVariant] for the bootstrap in the app APK added in app/build.gradle. */
    @JvmField
    var TERMUX_APP_PACKAGE_VARIANT: PackageVariant? = null

    /** Set [TERMUX_APP_PACKAGE_VARIANT] and [TERMUX_APP_PACKAGE_MANAGER] from [packageVariantName] passed. */
    @JvmStatic
    fun setTermuxPackageManagerAndVariant(packageVariantName: String?) {
        TERMUX_APP_PACKAGE_VARIANT = PackageVariant.variantOf(packageVariantName)
        if (TERMUX_APP_PACKAGE_VARIANT == null) {
            throw RuntimeException("Unsupported TERMUX_APP_PACKAGE_VARIANT \"$packageVariantName\"")
        }

        Logger.logVerbose(LOG_TAG, "Set TERMUX_APP_PACKAGE_VARIANT to \"$TERMUX_APP_PACKAGE_VARIANT\"")

        // Set packageManagerName to substring before first dash "-" in packageVariantName
        val index = packageVariantName?.indexOf('-') ?: -1
        val packageManagerName = if (index == -1) null else packageVariantName?.substring(0, index)
        TERMUX_APP_PACKAGE_MANAGER = PackageManager.managerOf(packageManagerName)
        if (TERMUX_APP_PACKAGE_MANAGER == null) {
            throw RuntimeException("Unsupported TERMUX_APP_PACKAGE_MANAGER \"$packageManagerName\" with variant \"$packageVariantName\"")
        }

        Logger.logVerbose(LOG_TAG, "Set TERMUX_APP_PACKAGE_MANAGER to \"$TERMUX_APP_PACKAGE_MANAGER\"")
    }

    /**
     * Set [TERMUX_APP_PACKAGE_VARIANT] and [TERMUX_APP_PACKAGE_MANAGER] with the
     * [BUILD_CONFIG_FIELD_TERMUX_PACKAGE_VARIANT] field value from the
     * [TERMUX_APP.BUILD_CONFIG_CLASS_NAME] class of the Termux app APK installed on the device.
     */
    @JvmStatic
    fun setTermuxPackageManagerAndVariantFromTermuxApp(currentPackageContext: Context) {
        val packageVariantName = getTermuxAppBuildConfigPackageVariantFromTermuxApp(currentPackageContext)
        if (packageVariantName != null) {
            setTermuxPackageManagerAndVariant(packageVariantName)
        } else {
            Logger.logError(LOG_TAG, "Failed to set TERMUX_APP_PACKAGE_VARIANT and TERMUX_APP_PACKAGE_MANAGER from the termux app")
        }
    }

    /**
     * Get [BUILD_CONFIG_FIELD_TERMUX_PACKAGE_VARIANT] field value from the
     * [TERMUX_APP.BUILD_CONFIG_CLASS_NAME] class of the Termux app APK installed on the device.
     */
    @JvmStatic
    fun getTermuxAppBuildConfigPackageVariantFromTermuxApp(currentPackageContext: Context): String? {
        return try {
            TermuxUtils.getTermuxAppAPKBuildConfigClassField(currentPackageContext, BUILD_CONFIG_FIELD_TERMUX_PACKAGE_VARIANT) as? String
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get \"$BUILD_CONFIG_FIELD_TERMUX_PACKAGE_VARIANT\" value from \"${TERMUX_APP.BUILD_CONFIG_CLASS_NAME}\" class", e)
            null
        }
    }

    /** Is [PackageManager.APT] set as [TERMUX_APP_PACKAGE_MANAGER]. */
    @JvmStatic
    fun isAppPackageManagerAPT(): Boolean {
        return PackageManager.APT == TERMUX_APP_PACKAGE_MANAGER
    }

    /** Is [PackageVariant.APT_ANDROID_7] set as [TERMUX_APP_PACKAGE_VARIANT]. */
    @JvmStatic
    fun isAppPackageVariantAPTAndroid7(): Boolean {
        return PackageVariant.APT_ANDROID_7 == TERMUX_APP_PACKAGE_VARIANT
    }

    /** Is [PackageVariant.APT_ANDROID_5] set as [TERMUX_APP_PACKAGE_VARIANT]. */
    @JvmStatic
    fun isAppPackageVariantAPTAndroid5(): Boolean {
        return PackageVariant.APT_ANDROID_5 == TERMUX_APP_PACKAGE_VARIANT
    }

    /** Termux package manager. */
    enum class PackageManager(private val managerName: String) {
        /**
         * Advanced Package Tool (APT) for managing debian deb package files.
         */
        APT("apt");

        fun getName(): String = managerName

        fun equalsManager(manager: String?): Boolean {
            return manager != null && manager == managerName
        }

        companion object {
            /** Get [PackageManager] for [name] if found, otherwise null. */
            @JvmStatic
            fun managerOf(name: String?): PackageManager? {
                if (name.isNullOrEmpty()) return null
                return entries.find { it.managerName == name }
            }
        }
    }

    /** Termux package variant. The substring before first dash "-" must match one of the [PackageManager]. */
    enum class PackageVariant(private val variantName: String) {
        /** [PackageManager.APT] variant for Android 7+. */
        APT_ANDROID_7("apt-android-7"),

        /** [PackageManager.APT] variant for Android 5+. */
        APT_ANDROID_5("apt-android-5");

        fun getName(): String = variantName

        fun equalsVariant(variant: String?): Boolean {
            return variant != null && variant == variantName
        }

        companion object {
            /** Get [PackageVariant] for [name] if found, otherwise null. */
            @JvmStatic
            fun variantOf(name: String?): PackageVariant? {
                if (name.isNullOrEmpty()) return null
                return entries.find { it.variantName == name }
            }
        }
    }
}
