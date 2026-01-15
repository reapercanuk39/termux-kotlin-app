package com.termux.app.core.deviceapi.models

import kotlinx.serialization.Serializable

/**
 * Battery information from the device.
 */
@Serializable
data class BatteryInfo(
    /** Current battery level percentage (0-100) */
    val level: Int,
    
    /** Battery health status */
    val health: BatteryHealth,
    
    /** Current charging status */
    val status: BatteryStatus,
    
    /** Power source currently used */
    val plugged: BatteryPlugged,
    
    /** Current battery temperature in Celsius */
    val temperature: Float,
    
    /** Current battery voltage in volts */
    val voltage: Float,
    
    /** Battery technology (e.g., "Li-ion") */
    val technology: String,
    
    /** Whether the device is currently charging */
    val isCharging: Boolean,
    
    /** Timestamp when this info was captured */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Format battery info for terminal output.
     */
    fun toTerminalOutput(): String = buildString {
        appendLine("Battery Status")
        appendLine("==============")
        appendLine("Level:       $level%")
        appendLine("Status:      ${status.displayName}")
        appendLine("Health:      ${health.displayName}")
        appendLine("Plugged:     ${plugged.displayName}")
        appendLine("Temperature: ${"%.1f".format(temperature)}°C")
        appendLine("Voltage:     ${"%.2f".format(voltage)}V")
        appendLine("Technology:  $technology")
    }
    
    /**
     * Format as JSON for programmatic consumption.
     */
    fun toJsonOutput(): String = """
        {
          "level": $level,
          "health": "${health.name}",
          "status": "${status.name}",
          "plugged": "${plugged.name}",
          "temperature": $temperature,
          "voltage": $voltage,
          "technology": "$technology",
          "is_charging": $isCharging,
          "timestamp": $timestamp
        }
    """.trimIndent()
}

@Serializable
enum class BatteryHealth(val displayName: String) {
    UNKNOWN("Unknown"),
    GOOD("Good"),
    OVERHEAT("Overheat"),
    DEAD("Dead"),
    OVER_VOLTAGE("Over Voltage"),
    UNSPECIFIED_FAILURE("Unspecified Failure"),
    COLD("Cold")
}

@Serializable
enum class BatteryStatus(val displayName: String) {
    UNKNOWN("Unknown"),
    CHARGING("Charging"),
    DISCHARGING("Discharging"),
    NOT_CHARGING("Not Charging"),
    FULL("Full")
}

@Serializable
enum class BatteryPlugged(val displayName: String) {
    NONE("Not Plugged"),
    AC("AC Charger"),
    USB("USB"),
    WIRELESS("Wireless"),
    DOCK("Dock")
}
