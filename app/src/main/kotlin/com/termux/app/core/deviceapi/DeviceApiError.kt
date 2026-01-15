package com.termux.app.core.deviceapi

import com.termux.app.core.api.TermuxError

/**
 * Errors specific to Device API operations.
 * Integrates with the TermuxError hierarchy for unified error handling.
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
