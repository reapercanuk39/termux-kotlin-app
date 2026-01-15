package com.termux.app.core.deviceapi.models

import com.termux.app.core.api.IpcMessage
import kotlinx.serialization.Serializable

/**
 * IPC messages for Device API communication.
 * Extends the IpcMessage hierarchy for typed event handling.
 */
sealed class DeviceApiMessage : IpcMessage() {
    
    /**
     * Request for device API action.
     */
    @Serializable
    data class ApiRequest(
        override val id: String,
        val action: String,
        val parameters: Map<String, String> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis()
    ) : DeviceApiMessage()
    
    /**
     * Successful response from device API.
     */
    @Serializable
    data class ApiResponse(
        override val id: String,
        val requestId: String,
        val action: String,
        val data: String, // JSON-encoded response data
        val executionTimeMs: Long,
        override val timestamp: Long = System.currentTimeMillis()
    ) : DeviceApiMessage()
    
    /**
     * Error response from device API.
     */
    @Serializable
    data class ApiError(
        override val id: String,
        val requestId: String,
        val action: String,
        val errorCode: Int,
        val errorMessage: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : DeviceApiMessage()
    
    /**
     * Streaming data from device API (e.g., sensor updates).
     */
    @Serializable
    data class StreamData(
        override val id: String,
        val streamId: String,
        val action: String,
        val data: String, // JSON-encoded data
        val sequenceNumber: Long,
        override val timestamp: Long = System.currentTimeMillis()
    ) : DeviceApiMessage()
    
    /**
     * Stream ended notification.
     */
    @Serializable
    data class StreamEnded(
        override val id: String,
        val streamId: String,
        val action: String,
        val reason: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : DeviceApiMessage()
}

/**
 * Available device API actions.
 */
enum class DeviceApiAction(val actionName: String, val description: String) {
    // Battery
    BATTERY_STATUS("battery", "Get battery status"),
    
    // Clipboard
    CLIPBOARD_GET("clipboard-get", "Get clipboard contents"),
    CLIPBOARD_SET("clipboard-set", "Set clipboard contents"),
    
    // Location
    LOCATION_GET("location", "Get current location"),
    
    // Sensors
    SENSOR_LIST("sensor-list", "List available sensors"),
    SENSOR_READ("sensor", "Read sensor data"),
    
    // Camera
    CAMERA_INFO("camera-info", "Get camera info"),
    CAMERA_PHOTO("camera-photo", "Take a photo"),
    
    // Audio
    AUDIO_INFO("audio-info", "Get audio info"),
    VOLUME_GET("volume-get", "Get volume levels"),
    VOLUME_SET("volume-set", "Set volume level"),
    
    // Vibration
    VIBRATE("vibrate", "Vibrate device"),
    
    // Toast
    TOAST("toast", "Show toast message"),
    
    // TTS
    TTS_SPEAK("tts-speak", "Text to speech"),
    
    // Torch
    TORCH("torch", "Control flashlight"),
    
    // Wifi
    WIFI_INFO("wifi-info", "Get WiFi info"),
    WIFI_SCAN("wifi-scan", "Scan for WiFi networks"),
    
    // Telephony
    TELEPHONY_INFO("telephony-info", "Get telephony info"),
    SMS_SEND("sms-send", "Send SMS"),
    
    // Contacts
    CONTACTS_LIST("contacts-list", "List contacts"),
    
    // Notifications
    NOTIFICATION_POST("notification-post", "Post notification"),
    NOTIFICATION_REMOVE("notification-remove", "Remove notification");
    
    companion object {
        fun fromName(name: String): DeviceApiAction? = 
            entries.find { it.actionName == name }
    }
}
