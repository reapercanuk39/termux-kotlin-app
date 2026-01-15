package com.termux.app.pkg.cli.commands.device

import com.termux.app.core.api.Result
import com.termux.app.core.deviceapi.actions.BatteryAction
import com.termux.app.core.deviceapi.models.DeviceApiAction
import com.termux.app.core.logging.TermuxLogger
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device API CLI commands for termuxctl.
 * 
 * Usage:
 * ```bash
 * termuxctl device battery         # Get battery status
 * termuxctl device battery --json  # Get battery status as JSON
 * termuxctl device list            # List available device APIs
 * termuxctl device --help          # Show help
 * ```
 */
@Singleton
class DeviceCommands @Inject constructor(
    private val batteryAction: BatteryAction,
    private val logger: TermuxLogger
) {
    companion object {
        // ANSI colors for terminal output
        private const val RESET = "\u001B[0m"
        private const val RED = "\u001B[31m"
        private const val GREEN = "\u001B[32m"
        private const val YELLOW = "\u001B[33m"
        private const val BLUE = "\u001B[34m"
        private const val CYAN = "\u001B[36m"
        private const val BOLD = "\u001B[1m"
    }
    
    private val log = logger.forTag("DeviceCommands")
    
    /**
     * Execute device command.
     * 
     * @param args Command arguments (without "device" prefix)
     * @return Exit code (0 = success)
     */
    fun execute(args: List<String>): Int = runBlocking {
        if (args.isEmpty()) {
            printUsage()
            return@runBlocking 1
        }
        
        when (args[0]) {
            "battery" -> handleBattery(args.drop(1))
            "list" -> handleList()
            "--help", "-h", "help" -> { printUsage(); 0 }
            else -> {
                printError("Unknown device command: ${args[0]}")
                printUsage()
                1
            }
        }
    }
    
    // ========== Battery Command ==========
    
    private suspend fun handleBattery(args: List<String>): Int {
        val useJson = "--json" in args || "-j" in args
        val extended = "--extended" in args || "-e" in args
        
        log.d("Executing battery command", mapOf("json" to useJson, "extended" to extended))
        
        return when (val result = batteryAction.execute()) {
            is Result.Success -> {
                val batteryInfo = result.data
                
                if (useJson) {
                    println(batteryInfo.toJsonOutput())
                } else {
                    println(batteryInfo.toTerminalOutput())
                    
                    if (extended) {
                        batteryAction.getExtendedBatteryInfo()?.let { extInfo ->
                            println()
                            println(extInfo.toTerminalOutput())
                        }
                    }
                }
                0
            }
            is Result.Error -> {
                printError("Failed to get battery status: ${result.error.message}")
                log.logError(result.error)
                1
            }
            is Result.Loading -> {
                printError("Unexpected loading state")
                1
            }
        }
    }
    
    // ========== List Command ==========
    
    private fun handleList(): Int {
        println("${BOLD}Available Device APIs:${RESET}")
        println()
        
        DeviceApiAction.entries.groupBy { 
            it.actionName.substringBefore("-") 
        }.forEach { (category, actions) ->
            println("${CYAN}${category.replaceFirstChar { it.uppercase() }}${RESET}")
            actions.forEach { action ->
                val status = if (isActionImplemented(action)) {
                    "${GREEN}✓${RESET}"
                } else {
                    "${YELLOW}○${RESET}"
                }
                println("  $status ${action.actionName.padEnd(20)} ${action.description}")
            }
            println()
        }
        
        println("${GREEN}✓${RESET} = Implemented  ${YELLOW}○${RESET} = Coming soon")
        
        return 0
    }
    
    private fun isActionImplemented(action: DeviceApiAction): Boolean = when (action) {
        DeviceApiAction.BATTERY_STATUS -> true
        else -> false
    }
    
    // ========== Usage ==========
    
    private fun printUsage() {
        println("""
            ${BOLD}termuxctl device${RESET} - Access device APIs
            
            ${BOLD}Usage:${RESET} termuxctl device <command> [options]
            
            ${BOLD}Commands:${RESET}
              battery    Get battery status information
              list       List available device APIs
            
            ${BOLD}Options for 'battery':${RESET}
              --json, -j       Output as JSON
              --extended, -e   Include extended battery info
            
            ${BOLD}Examples:${RESET}
              ${CYAN}termuxctl device battery${RESET}
              ${CYAN}termuxctl device battery --json${RESET}
              ${CYAN}termuxctl device list${RESET}
            
            ${BOLD}Future APIs:${RESET}
              clipboard, location, sensors, camera, audio, wifi,
              vibrate, toast, tts, torch, telephony, contacts,
              notifications
        """.trimIndent())
    }
    
    private fun printError(message: String) {
        System.err.println("${RED}Error:${RESET} $message")
    }
}
