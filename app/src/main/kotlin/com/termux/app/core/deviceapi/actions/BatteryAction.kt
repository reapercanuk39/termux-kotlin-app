package com.termux.app.core.deviceapi.actions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.termux.app.core.api.DeviceApiError
import com.termux.app.core.api.Result
import com.termux.app.core.deviceapi.models.BatteryHealth
import com.termux.app.core.deviceapi.models.BatteryInfo
import com.termux.app.core.deviceapi.models.BatteryPlugged
import com.termux.app.core.deviceapi.models.BatteryStatus
import com.termux.app.core.logging.TermuxLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.round

/**
 * Device API action for retrieving battery status information.
 * 
 * No special permissions required - battery info is publicly available.
 * 
 * Usage:
 * ```kotlin
 * val result = batteryAction.execute()
 * when (result) {
 *     is Result.Success -> println(result.data.toTerminalOutput())
 *     is Result.Error -> logger.logError(result.error)
 * }
 * ```
 */
@Singleton
class BatteryAction @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TermuxLogger
) : DeviceApiActionBase<BatteryInfo>(logger) {
    
    override val actionName: String = "battery"
    override val description: String = "Get battery status information"
    
    // Battery info doesn't require any special permissions
    override val requiredPermissions: List<String> = emptyList()
    
    private val targetSdkVersion: Int by lazy {
        context.applicationInfo.targetSdkVersion
    }
    
    override suspend fun execute(params: Map<String, String>): Result<BatteryInfo, DeviceApiError> {
        return executeWithLogging {
            withContext(Dispatchers.IO) {
                getBatteryInfo()
            }
        }
    }
    
    /**
     * Get current battery information from the system.
     */
    private fun getBatteryInfo(): BatteryInfo {
        // Register for battery changed intent
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: Intent()
        
        // Extract battery level
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        
        // Extract health
        val healthInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val health = mapHealth(healthInt)
        
        // Extract status
        val statusInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val status = mapStatus(statusInt)
        
        // Extract plugged state
        val pluggedInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugged = mapPlugged(pluggedInt)
        
        // Extract temperature (comes in tenths of a degree)
        val tempRaw = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val temperature = if (tempRaw != Int.MIN_VALUE) {
            (round(tempRaw / 10.0 * 10.0) / 10.0).toFloat()
        } else {
            0f
        }
        
        // Extract voltage (may come in millivolts or volts depending on device)
        var voltageRaw = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        if (voltageRaw < 100) {
            log.v("Fixing voltage from $voltageRaw to ${voltageRaw * 1000}")
            voltageRaw *= 1000
        }
        val voltage = voltageRaw / 1000.0f
        
        // Extract technology
        val technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
        
        // Determine charging state
        val isCharging = status == BatteryStatus.CHARGING || status == BatteryStatus.FULL
        
        // Get extended info from BatteryManager service
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        
        return BatteryInfo(
            level = percentage,
            health = health,
            status = status,
            plugged = plugged,
            temperature = temperature,
            voltage = voltage,
            technology = technology,
            isCharging = isCharging
        )
    }
    
    /**
     * Get extended battery information (current, energy counter, etc.)
     * Available on API 21+
     */
    fun getExtendedBatteryInfo(): ExtendedBatteryInfo? {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return null
        
        val currentNow = getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentAvg = getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val chargeCounter = getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val energyCounter = getLongProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        val capacity = getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        // Fix current values if needed (some devices report in different units)
        val fixedCurrentNow = currentNow?.let { curr ->
            if (abs(curr / 1000) < 1) {
                log.v("Fixing current_now from $curr to ${curr * 1000}")
                curr * 1000
            } else curr
        }
        
        return ExtendedBatteryInfo(
            currentNow = fixedCurrentNow,
            currentAverage = currentAvg,
            chargeCounter = chargeCounter,
            energyCounter = energyCounter,
            capacity = capacity
        )
    }
    
    private fun getIntProperty(batteryManager: BatteryManager, id: Int): Int? {
        val value = batteryManager.getIntProperty(id)
        return if (targetSdkVersion < Build.VERSION_CODES.P) {
            if (value != 0) value else null
        } else {
            if (value != Int.MIN_VALUE) value else null
        }
    }
    
    private fun getLongProperty(batteryManager: BatteryManager, id: Int): Long? {
        val value = batteryManager.getLongProperty(id)
        return if (value != Long.MIN_VALUE) value else null
    }
    
    private fun mapHealth(health: Int): BatteryHealth = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
        BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.UNSPECIFIED_FAILURE
        BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
        else -> BatteryHealth.UNKNOWN
    }
    
    private fun mapStatus(status: Int): BatteryStatus = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
        BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
        BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
        else -> {
            if (status != BatteryManager.BATTERY_STATUS_UNKNOWN) {
                log.w("Unknown battery status value: $status")
            }
            BatteryStatus.UNKNOWN
        }
    }
    
    private fun mapPlugged(plugged: Int): BatteryPlugged = when (plugged) {
        0 -> BatteryPlugged.NONE
        BatteryManager.BATTERY_PLUGGED_AC -> BatteryPlugged.AC
        BatteryManager.BATTERY_PLUGGED_USB -> BatteryPlugged.USB
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> BatteryPlugged.WIRELESS
        BatteryManager.BATTERY_PLUGGED_DOCK -> BatteryPlugged.DOCK
        else -> BatteryPlugged.NONE
    }
}

/**
 * Extended battery information from BatteryManager service.
 */
data class ExtendedBatteryInfo(
    /** Instantaneous battery current in microamperes */
    val currentNow: Int?,
    /** Average battery current in microamperes */
    val currentAverage: Int?,
    /** Battery charge counter in microampere-hours */
    val chargeCounter: Int?,
    /** Battery remaining energy in nanowatt-hours */
    val energyCounter: Long?,
    /** Battery capacity percentage */
    val capacity: Int?
) {
    fun toTerminalOutput(): String = buildString {
        appendLine("Extended Battery Info")
        appendLine("====================")
        currentNow?.let { appendLine("Current:         ${it / 1000} mA") }
        currentAverage?.let { appendLine("Current (avg):   ${it / 1000} mA") }
        chargeCounter?.let { appendLine("Charge counter:  ${it / 1000} mAh") }
        energyCounter?.let { appendLine("Energy counter:  ${it / 1000000} mWh") }
        capacity?.let { appendLine("Capacity:        $it%") }
    }
}
