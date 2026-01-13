package com.termux.shared.android

import android.annotation.SuppressLint
import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.shared.reflection.ReflectionUtils

/**
 * Utils for Developer Options -> Feature Flags. The page won't show in user/production builds and
 * is only shown in userdebug builds.
 *
 * The feature flags value can be modified in two ways.
 *
 * 1. sysprops with `setprop` command with root. Will be unset by default.
 *  Set value: `setprop persist.sys.fflag.override.settings_enable_monitor_phantom_procs false`
 *  Get value: `getprop persist.sys.fflag.override.settings_enable_monitor_phantom_procs`
 *  Unset value: `setprop persist.sys.fflag.override.settings_enable_monitor_phantom_procs ""`
 *
 * 2. settings global list with adb or root. Will be unset by default. This takes precedence over
 * sysprop value since `FeatureFlagUtils.isEnabled()` checks its value first.
 * Override precedence: Settings.Global -> sys.fflag.override.* -> static list.
 * Set value: `adb shell settings put global settings_enable_monitor_phantom_procs false`
 * Get value: `adb shell settings get global settings_enable_monitor_phantom_procs`
 * Unset value: `adb shell settings delete global settings_enable_monitor_phantom_procs`
 */
object FeatureFlagUtils {

    enum class FeatureFlagValue(val valueName: String) {
        /** Unknown like due to exception raised while getting value. */
        UNKNOWN("<unknown>"),

        /** Flag is unsupported on current android build. */
        UNSUPPORTED("<unsupported>"),

        /** Flag is enabled. */
        TRUE("true"),

        /** Flag is not enabled. */
        FALSE("false");

        fun getName(): String = valueName
    }

    const val FEATURE_FLAGS_CLASS = "android.util.FeatureFlagUtils"

    private const val LOG_TAG = "FeatureFlagUtils"

    /**
     * Get all feature flags in their raw form.
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun getAllFeatureFlags(): Map<String, String>? {
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions()
        return try {
            @SuppressLint("PrivateApi")
            val clazz = Class.forName(FEATURE_FLAGS_CLASS)
            val getAllFeatureFlagsMethod = ReflectionUtils.getDeclaredMethod(clazz, "getAllFeatureFlags")
                ?: return null
            ReflectionUtils.invokeMethod(getAllFeatureFlagsMethod, null).value as? Map<String, String>
        } catch (e: Exception) {
            // ClassCastException may be thrown
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get all feature flags", e)
            null
        }
    }

    /**
     * Check if a feature flag exists.
     *
     * @return Returns `true` if flag exists, otherwise `false`. This will be
     * null if an exception is raised.
     */
    @JvmStatic
    fun featureFlagExists(feature: String): Boolean? {
        val featureFlags = getAllFeatureFlags() ?: return null
        return featureFlags.containsKey(feature)
    }

    /**
     * Get [FeatureFlagValue] for a feature.
     *
     * @param context The [Context] for operations.
     * @param feature The [String] name for feature.
     * @return Returns [FeatureFlagValue].
     */
    @JvmStatic
    fun getFeatureFlagValueString(context: Context, feature: String): FeatureFlagValue {
        val featureFlagExists = featureFlagExists(feature)
        if (featureFlagExists == null) {
            Logger.logError(LOG_TAG, "Failed to get feature flags \"$feature\" value")
            return FeatureFlagValue.UNKNOWN
        } else if (!featureFlagExists) {
            return FeatureFlagValue.UNSUPPORTED
        }

        val featureFlagValue = isFeatureEnabled(context, feature)
        return if (featureFlagValue == null) {
            Logger.logError(LOG_TAG, "Failed to get feature flags \"$feature\" value")
            FeatureFlagValue.UNKNOWN
        } else {
            if (featureFlagValue) FeatureFlagValue.TRUE else FeatureFlagValue.FALSE
        }
    }

    /**
     * Check if a feature flag is enabled.
     *
     * @param context The [Context] for operations.
     * @param feature The [String] name for feature.
     * @return Returns `true` if flag is enabled, otherwise `false`. This will be
     * null if an exception is raised.
     */
    @JvmStatic
    fun isFeatureEnabled(context: Context, feature: String): Boolean? {
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions()
        return try {
            @SuppressLint("PrivateApi")
            val clazz = Class.forName(FEATURE_FLAGS_CLASS)
            val isFeatureEnabledMethod = ReflectionUtils.getDeclaredMethod(
                clazz, "isEnabled", Context::class.java, String::class.java
            )
            if (isFeatureEnabledMethod == null) {
                Logger.logError(LOG_TAG, "Failed to check if feature flag \"$feature\" is enabled")
                return null
            }

            ReflectionUtils.invokeMethod(isFeatureEnabledMethod, null, context, feature).value as Boolean
        } catch (e: Exception) {
            // ClassCastException may be thrown
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to check if feature flag \"$feature\" is enabled", e)
            null
        }
    }
}
