package com.termux.shared.reflection

import android.os.Build
import com.termux.shared.logger.Logger
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Arrays

object ReflectionUtils {

    private var HIDDEN_API_REFLECTION_RESTRICTIONS_BYPASSED = Build.VERSION.SDK_INT < Build.VERSION_CODES.P

    private const val LOG_TAG = "ReflectionUtils"

    /**
     * Bypass android hidden API reflection restrictions.
     * https://github.com/LSPosed/AndroidHiddenApiBypass
     * https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
     */
    @JvmStatic
    fun bypassHiddenAPIReflectionRestrictions() {
        if (!HIDDEN_API_REFLECTION_RESTRICTIONS_BYPASSED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Logger.logDebug(LOG_TAG, "Bypassing android hidden api reflection restrictions")
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
            } catch (t: Throwable) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to bypass hidden API reflection restrictions", t)
            }

            HIDDEN_API_REFLECTION_RESTRICTIONS_BYPASSED = true
        }
    }

    /** Check if android hidden API reflection restrictions are bypassed. */
    @JvmStatic
    fun areHiddenAPIReflectionRestrictionsBypassed(): Boolean {
        return HIDDEN_API_REFLECTION_RESTRICTIONS_BYPASSED
    }

    /**
     * Get a [Field] for the specified class.
     *
     * @param clazz The [Class] for which to return the field.
     * @param fieldName The name of the [Field].
     * @return Returns the [Field] if getting the it was successful, otherwise `null`.
     */
    @JvmStatic
    fun getDeclaredField(clazz: Class<*>, fieldName: String): Field? {
        return try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get \"$fieldName\" field for \"${clazz.name}\" class", e)
            null
        }
    }

    /** Class that represents result of invoking a field. */
    class FieldInvokeResult(
        @JvmField var success: Boolean,
        @JvmField var value: Any?
    )

    /**
     * Get a value for a [Field] of an object for the specified class.
     *
     * Trying to access `null` fields will result in [NoSuchFieldException].
     *
     * @param clazz The [Class] to which the object belongs to.
     * @param fieldName The name of the [Field].
     * @param object The [Object] instance from which to get the field value.
     * @return Returns the [FieldInvokeResult] of invoking the field. The
     * [FieldInvokeResult.success] will be `true` if invoking the field was successful,
     * otherwise `false`. The [FieldInvokeResult.value] will contain the field
     * [Object] value.
     */
    @JvmStatic
    fun <T> invokeField(clazz: Class<out T>, fieldName: String, obj: T): FieldInvokeResult {
        return try {
            val field = getDeclaredField(clazz, fieldName) ?: return FieldInvokeResult(false, null)
            FieldInvokeResult(true, field.get(obj))
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get \"$fieldName\" field value for \"${clazz.name}\" class", e)
            FieldInvokeResult(false, null)
        }
    }

    /**
     * Wrapper for [getDeclaredMethod] without parameters.
     */
    @JvmStatic
    fun getDeclaredMethod(clazz: Class<*>, methodName: String): Method? {
        return getDeclaredMethod(clazz, methodName, *arrayOf<Class<*>>())
    }

    /**
     * Get a [Method] for the specified class with the specified parameters.
     *
     * @param clazz The [Class] for which to return the method.
     * @param methodName The name of the [Method].
     * @param parameterTypes The parameter types of the method.
     * @return Returns the [Method] if getting the it was successful, otherwise `null`.
     */
    @JvmStatic
    fun getDeclaredMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method? {
        return try {
            val method = clazz.getDeclaredMethod(methodName, *parameterTypes)
            method.isAccessible = true
            method
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get \"$methodName\" method for \"${clazz.name}\" class with parameter types: ${Arrays.toString(parameterTypes)}", e)
            null
        }
    }

    /**
     * Wrapper for [invokeVoidMethod] without arguments.
     */
    @JvmStatic
    fun invokeVoidMethod(method: Method, obj: Any?): Boolean {
        return invokeVoidMethod(method, obj, *arrayOf<Any>())
    }

    /**
     * Invoke a [Method] on the specified object with the specified arguments that returns
     * `void`.
     *
     * @param method The [Method] to invoke.
     * @param obj The [Object] the method should be invoked from.
     * @param args The arguments to pass to the method.
     * @return Returns `true` if invoking the method was successful, otherwise `false`.
     */
    @JvmStatic
    fun invokeVoidMethod(method: Method, obj: Any?, vararg args: Any?): Boolean {
        return try {
            method.isAccessible = true
            method.invoke(obj, *args)
            true
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to invoke \"${method.name}\" method with object \"$obj\" and args: ${Arrays.toString(args)}", e)
            false
        }
    }

    /** Class that represents result of invoking a method that has a non-void return type. */
    class MethodInvokeResult(
        @JvmField var success: Boolean,
        @JvmField var value: Any?
    )

    /**
     * Wrapper for [invokeMethod] without arguments.
     */
    @JvmStatic
    fun invokeMethod(method: Method, obj: Any?): MethodInvokeResult {
        return invokeMethod(method, obj, *arrayOf<Any>())
    }

    /**
     * Invoke a [Method] on the specified object with the specified arguments.
     *
     * @param method The [Method] to invoke.
     * @param obj The [Object] the method should be invoked from.
     * @param args The arguments to pass to the method.
     * @return Returns the [MethodInvokeResult] of invoking the method. The
     * [MethodInvokeResult.success] will be `true` if invoking the method was successful,
     * otherwise `false`. The [MethodInvokeResult.value] will contain the [Object]
     * returned by the method.
     */
    @JvmStatic
    fun invokeMethod(method: Method, obj: Any?, vararg args: Any?): MethodInvokeResult {
        return try {
            method.isAccessible = true
            MethodInvokeResult(true, method.invoke(obj, *args))
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to invoke \"${method.name}\" method with object \"$obj\" and args: ${Arrays.toString(args)}", e)
            MethodInvokeResult(false, null)
        }
    }

    /**
     * Wrapper for [getConstructor] without parameters.
     */
    @JvmStatic
    fun getConstructor(className: String): Constructor<*>? {
        return getConstructor(className, *arrayOf<Class<*>>())
    }

    /**
     * Wrapper for [getConstructor] to get a [Constructor] for the
     * `className`.
     */
    @JvmStatic
    fun getConstructor(className: String, vararg parameterTypes: Class<*>): Constructor<*>? {
        return try {
            getConstructor(Class.forName(className), *parameterTypes)
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get constructor for \"$className\" class with parameter types: ${Arrays.toString(parameterTypes)}", e)
            null
        }
    }

    /**
     * Get a [Constructor] for the specified class with the specified parameters.
     *
     * @param clazz The [Class] for which to return the constructor.
     * @param parameterTypes The parameter types of the constructor.
     * @return Returns the [Constructor] if getting the it was successful, otherwise `null`.
     */
    @JvmStatic
    fun getConstructor(clazz: Class<*>, vararg parameterTypes: Class<*>): Constructor<*>? {
        return try {
            val constructor = clazz.getConstructor(*parameterTypes)
            constructor.isAccessible = true
            constructor
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get constructor for \"${clazz.name}\" class with parameter types: ${Arrays.toString(parameterTypes)}", e)
            null
        }
    }

    /**
     * Wrapper for [invokeConstructor] without arguments.
     */
    @JvmStatic
    fun invokeConstructor(constructor: Constructor<*>): Any? {
        return invokeConstructor(constructor, *arrayOf<Any>())
    }

    /**
     * Invoke a [Constructor] with the specified arguments.
     *
     * @param constructor The [Constructor] to invoke.
     * @param args The arguments to pass to the constructor.
     * @return Returns the new instance if invoking the constructor was successful, otherwise `null`.
     */
    @JvmStatic
    fun invokeConstructor(constructor: Constructor<*>, vararg args: Any?): Any? {
        return try {
            constructor.isAccessible = true
            constructor.newInstance(*args)
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to invoke \"${constructor.name}\" constructor with args: ${Arrays.toString(args)}", e)
            null
        }
    }
}
