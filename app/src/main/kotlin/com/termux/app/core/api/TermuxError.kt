package com.termux.app.core.api

/**
 * Base sealed class for all Termux errors.
 * Provides type-safe error handling with exhaustive when expressions.
 */
sealed class TermuxError {
    
    /**
     * Human-readable error message.
     */
    abstract val message: String
    
    /**
     * Optional error code for programmatic handling.
     */
    open val code: Int = 0
    
    /**
     * Optional underlying cause.
     */
    open val cause: Throwable? = null
    
    /**
     * Converts this error to an exception.
     */
    fun toException(): TermuxException = TermuxException(this)
    
    /**
     * Returns a formatted string for logging.
     */
    fun toLogString(): String = buildString {
        append("[${this@TermuxError::class.simpleName}]")
        if (code != 0) append(" (code: $code)")
        append(" $message")
        cause?.let { append("\nCaused by: ${it.message}") }
    }
}

/**
 * Terminal-related errors.
 */
sealed class TerminalError : TermuxError() {
    
    data class SessionCreationFailed(
        override val message: String = "Failed to create terminal session",
        override val cause: Throwable? = null
    ) : TerminalError() {
        override val code: Int = 1001
    }
    
    data class SessionNotFound(
        val sessionId: String,
        override val message: String = "Session not found: $sessionId"
    ) : TerminalError() {
        override val code: Int = 1002
    }
    
    data class SessionAlreadyExists(
        val sessionId: String,
        override val message: String = "Session already exists: $sessionId"
    ) : TerminalError() {
        override val code: Int = 1003
    }
    
    data class ExecutionFailed(
        val command: String,
        val exitCode: Int,
        override val message: String = "Command failed with exit code $exitCode: $command"
    ) : TerminalError() {
        override val code: Int = 1004
    }
    
    data class ProcessKilled(
        val signal: Int,
        override val message: String = "Process killed by signal $signal"
    ) : TerminalError() {
        override val code: Int = 1005
    }
    
    data object NotInitialized : TerminalError() {
        override val message: String = "Terminal not initialized"
        override val code: Int = 1006
    }
}

/**
 * Permission-related errors.
 */
sealed class PermissionError : TermuxError() {
    
    data class Denied(
        val permission: String,
        override val message: String = "Permission denied: $permission"
    ) : PermissionError() {
        override val code: Int = 2001
    }
    
    data class DeniedPermanently(
        val permission: String,
        override val message: String = "Permission permanently denied: $permission. Please enable in Settings."
    ) : PermissionError() {
        override val code: Int = 2002
    }
    
    data class NotRequested(
        val permission: String,
        override val message: String = "Permission not yet requested: $permission"
    ) : PermissionError() {
        override val code: Int = 2003
    }
    
    data class RationaleRequired(
        val permission: String,
        val rationale: String,
        override val message: String = rationale
    ) : PermissionError() {
        override val code: Int = 2004
    }
    
    data object StorageAccessDenied : PermissionError() {
        override val message: String = "Storage access denied"
        override val code: Int = 2010
    }
    
    data object NotificationsDenied : PermissionError() {
        override val message: String = "Notification permission denied"
        override val code: Int = 2011
    }
    
    data object BatteryOptimizationRequired : PermissionError() {
        override val message: String = "Battery optimization exemption required"
        override val code: Int = 2012
    }
    
    data object OverlayDenied : PermissionError() {
        override val message: String = "Display over other apps permission denied"
        override val code: Int = 2013
    }
}

/**
 * File system errors.
 */
sealed class FileError : TermuxError() {
    
    data class NotFound(
        val path: String,
        override val message: String = "File not found: $path"
    ) : FileError() {
        override val code: Int = 3001
    }
    
    data class AccessDenied(
        val path: String,
        override val message: String = "Access denied: $path"
    ) : FileError() {
        override val code: Int = 3002
    }
    
    data class AlreadyExists(
        val path: String,
        override val message: String = "File already exists: $path"
    ) : FileError() {
        override val code: Int = 3003
    }
    
    data class InvalidPath(
        val path: String,
        override val message: String = "Invalid path: $path"
    ) : FileError() {
        override val code: Int = 3004
    }
    
    data class IoError(
        val path: String,
        override val message: String,
        override val cause: Throwable? = null
    ) : FileError() {
        override val code: Int = 3005
    }
    
    data class DirectoryNotEmpty(
        val path: String,
        override val message: String = "Directory not empty: $path"
    ) : FileError() {
        override val code: Int = 3006
    }
    
    data class IsDirectory(
        val path: String,
        override val message: String = "Path is a directory: $path"
    ) : FileError() {
        override val code: Int = 3007
    }
    
    data class NotDirectory(
        val path: String,
        override val message: String = "Path is not a directory: $path"
    ) : FileError() {
        override val code: Int = 3008
    }
}

/**
 * Network and SSH errors.
 */
sealed class NetworkError : TermuxError() {
    
    data class ConnectionFailed(
        val host: String,
        val port: Int,
        override val message: String = "Connection failed to $host:$port",
        override val cause: Throwable? = null
    ) : NetworkError() {
        override val code: Int = 4001
    }
    
    data class Timeout(
        val host: String,
        override val message: String = "Connection timeout: $host"
    ) : NetworkError() {
        override val code: Int = 4002
    }
    
    data class AuthenticationFailed(
        val host: String,
        override val message: String = "Authentication failed for $host"
    ) : NetworkError() {
        override val code: Int = 4003
    }
    
    data class HostKeyVerificationFailed(
        val host: String,
        override val message: String = "Host key verification failed for $host"
    ) : NetworkError() {
        override val code: Int = 4004
    }
    
    data class Disconnected(
        val host: String,
        val reason: String? = null,
        override val message: String = "Disconnected from $host" + (reason?.let { ": $it" } ?: "")
    ) : NetworkError() {
        override val code: Int = 4005
    }
    
    data object NoNetwork : NetworkError() {
        override val message: String = "No network connection"
        override val code: Int = 4010
    }
}

/**
 * Plugin-related errors.
 */
sealed class PluginError : TermuxError() {
    
    data class NotFound(
        val pluginId: String,
        override val message: String = "Plugin not found: $pluginId"
    ) : PluginError() {
        override val code: Int = 5001
    }
    
    data class LoadFailed(
        val pluginId: String,
        override val message: String = "Failed to load plugin: $pluginId",
        override val cause: Throwable? = null
    ) : PluginError() {
        override val code: Int = 5002
    }
    
    data class IncompatibleVersion(
        val pluginId: String,
        val requiredVersion: String,
        val actualVersion: String,
        override val message: String = "Plugin $pluginId requires API version $requiredVersion but $actualVersion is installed"
    ) : PluginError() {
        override val code: Int = 5003
    }
    
    data class ExecutionError(
        val pluginId: String,
        override val message: String,
        override val cause: Throwable? = null
    ) : PluginError() {
        override val code: Int = 5004
    }
    
    data class SecurityViolation(
        val pluginId: String,
        val violation: String,
        override val message: String = "Security violation in plugin $pluginId: $violation"
    ) : PluginError() {
        override val code: Int = 5005
    }
}

/**
 * System-level errors.
 */
sealed class SystemError : TermuxError() {
    
    data class Exception(
        val exception: kotlin.Exception,
        override val message: String = exception.message ?: "Unknown error",
        override val cause: Throwable? = exception
    ) : SystemError() {
        override val code: Int = 9001
    }
    
    data class OutOfMemory(
        override val message: String = "Out of memory"
    ) : SystemError() {
        override val code: Int = 9002
    }
    
    data class InternalError(
        override val message: String,
        override val cause: Throwable? = null
    ) : SystemError() {
        override val code: Int = 9003
    }
    
    data object NotImplemented : SystemError() {
        override val message: String = "Feature not implemented"
        override val code: Int = 9004
    }
    
    data class InvalidState(
        val state: String,
        override val message: String = "Invalid state: $state"
    ) : SystemError() {
        override val code: Int = 9005
    }
    
    data class InvalidArgument(
        val argument: String,
        override val message: String = "Invalid argument: $argument"
    ) : SystemError() {
        override val code: Int = 9006
    }
}

/**
 * Configuration errors.
 */
sealed class ConfigError : TermuxError() {
    
    data class ParseError(
        val file: String,
        override val message: String,
        override val cause: Throwable? = null
    ) : ConfigError() {
        override val code: Int = 6001
    }
    
    data class ValidationError(
        val property: String,
        override val message: String
    ) : ConfigError() {
        override val code: Int = 6002
    }
    
    data class MissingProperty(
        val property: String,
        override val message: String = "Missing required property: $property"
    ) : ConfigError() {
        override val code: Int = 6003
    }
}

/**
 * Errors specific to Device API operations.
 */
sealed class DeviceApiError : TermuxError() {
    
    /**
     * Permission required for API action was not granted.
     */
    data class PermissionRequired(
        val permission: String,
        val apiAction: String,
        override val message: String = "Permission '$permission' required for $apiAction"
    ) : DeviceApiError() {
        override val code: Int = 7001
    }
    
    /**
     * The requested API feature is not available on this device.
     */
    data class FeatureNotAvailable(
        val feature: String,
        override val message: String = "Feature not available: $feature"
    ) : DeviceApiError() {
        override val code: Int = 7002
    }
    
    /**
     * Hardware sensor or component not found.
     */
    data class HardwareNotFound(
        val hardware: String,
        override val message: String = "Hardware not found: $hardware"
    ) : DeviceApiError() {
        override val code: Int = 7003
    }
    
    /**
     * Timeout waiting for API response.
     */
    data class Timeout(
        val operation: String,
        val timeoutMs: Long,
        override val message: String = "Timeout after ${timeoutMs}ms: $operation"
    ) : DeviceApiError() {
        override val code: Int = 7004
    }
    
    /**
     * API operation was cancelled.
     */
    data class Cancelled(
        val operation: String,
        override val message: String = "Operation cancelled: $operation"
    ) : DeviceApiError() {
        override val code: Int = 7005
    }
    
    /**
     * Invalid arguments provided to API action.
     */
    data class InvalidArguments(
        val details: String,
        override val message: String = "Invalid arguments: $details"
    ) : DeviceApiError() {
        override val code: Int = 7006
    }
    
    /**
     * Service not available (e.g., location services disabled).
     */
    data class ServiceUnavailable(
        val service: String,
        override val message: String = "Service unavailable: $service"
    ) : DeviceApiError() {
        override val code: Int = 7007
    }
    
    /**
     * API call failed with system exception.
     */
    data class SystemException(
        val operation: String,
        override val cause: Throwable,
        override val message: String = "System error in $operation: ${cause.message}"
    ) : DeviceApiError() {
        override val code: Int = 7008
    }
    
    /**
     * Rate limit exceeded for API calls.
     */
    data class RateLimited(
        val apiAction: String,
        val retryAfterMs: Long,
        override val message: String = "Rate limited: $apiAction. Retry after ${retryAfterMs}ms"
    ) : DeviceApiError() {
        override val code: Int = 7009
    }
    
    /**
     * API action is not supported on this Android version.
     */
    data class UnsupportedApiLevel(
        val feature: String,
        val requiredLevel: Int,
        val currentLevel: Int,
        override val message: String = "$feature requires API level $requiredLevel (current: $currentLevel)"
    ) : DeviceApiError() {
        override val code: Int = 7010
    }
}

/**
 * Exception wrapper for TermuxError.
 */
class TermuxException(val error: TermuxError) : Exception(error.message, error.cause)
