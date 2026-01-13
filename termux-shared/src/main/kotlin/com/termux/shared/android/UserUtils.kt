package com.termux.shared.android

import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.shared.reflection.ReflectionUtils

object UserUtils {

    const val LOG_TAG = "UserUtils"

    /**
     * Get the user name for user id with a call to [getNameForUidFromPackageManager]
     * and if that fails, then a call to [getNameForUidFromLibcore].
     *
     * @param context The [Context] for operations.
     * @param uid The user id.
     * @return Returns the user name if found, otherwise null.
     */
    @JvmStatic
    fun getNameForUid(context: Context, uid: Int): String? {
        var name = getNameForUidFromPackageManager(context, uid)
        if (name == null) {
            name = getNameForUidFromLibcore(uid)
        }
        return name
    }

    /**
     * Get the user name for user id with a call to PackageManager.getNameForUid.
     *
     * This will not return user names for non app user id like for root user 0, use [getNameForUidFromLibcore]
     * to get those.
     *
     * @param context The [Context] for operations.
     * @param uid The user id.
     * @return Returns the user name if found, otherwise null.
     */
    @JvmStatic
    fun getNameForUidFromPackageManager(context: Context, uid: Int): String? {
        if (uid < 0) return null

        return try {
            var name = context.packageManager.getNameForUid(uid)
            if (name != null && name.endsWith(":$uid")) {
                name = name.replace(":$uid$".toRegex(), "") // Remove ":<uid>" suffix
            }
            name
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get name for uid \"$uid\" from package manager", e)
            null
        }
    }

    /**
     * Get the user name for user id with a call to `Libcore.os.getpwuid()`.
     *
     * This will return user names for non app user id like for root user 0 as well, but this call
     * is expensive due to usage of reflection, and requires hidden API bypass.
     *
     * @param uid The user id.
     * @return Returns the user name if found, otherwise null.
     */
    @JvmStatic
    fun getNameForUidFromLibcore(uid: Int): String? {
        if (uid < 0) return null

        ReflectionUtils.bypassHiddenAPIReflectionRestrictions()
        return try {
            val libcoreClassName = "libcore.io.Libcore"
            var clazz: Class<*>? = Class.forName(libcoreClassName)
            val os: Any? // libcore.io.BlockGuardOs
            try {
                os = ReflectionUtils.invokeField(Class.forName(libcoreClassName), "os", null).value
            } catch (e: Exception) {
                // ClassCastException may be thrown
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get \"os\" field value for $libcoreClassName class", e)
                return null
            }

            if (os == null) {
                Logger.logError(LOG_TAG, "Failed to get BlockGuardOs class obj from Libcore")
                return null
            }

            clazz = os.javaClass.superclass // libcore.io.ForwardingOs
            if (clazz == null) {
                Logger.logError(LOG_TAG, "Failed to find super class ForwardingOs from object of class ${os.javaClass.name}")
                return null
            }

            val structPasswd: Any? // android.system.StructPasswd
            try {
                val getpwuidMethod = ReflectionUtils.getDeclaredMethod(clazz, "getpwuid", Int::class.javaPrimitiveType!!)
                    ?: return null
                structPasswd = ReflectionUtils.invokeMethod(getpwuidMethod, os, uid).value
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to invoke getpwuid() method of ${clazz.name} class", e)
                return null
            }

            if (structPasswd == null) {
                Logger.logError(LOG_TAG, "Failed to get StructPasswd obj from call to ForwardingOs.getpwuid()")
                return null
            }

            try {
                clazz = structPasswd.javaClass
                ReflectionUtils.invokeField(clazz, "pw_name", structPasswd).value as? String
            } catch (e: Exception) {
                // ClassCastException may be thrown
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get \"pw_name\" field value for ${clazz.name} class", e)
                null
            }
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get name for uid \"$uid\" from Libcore", e)
            null
        }
    }
}
