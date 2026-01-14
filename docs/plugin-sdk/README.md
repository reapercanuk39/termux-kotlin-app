# Termux Plugin SDK

This document describes how to create plugins for Termux Kotlin App using the Plugin API v1.0.0.

## Overview

Termux plugins extend the terminal's capabilities by:
- Adding custom commands
- Integrating with external services
- Providing UI extensions
- Automating workflows

## Plugin API Version

The plugin API follows semantic versioning:
- **v1.0.0** - Current stable API

Plugins specify their required API version, and Termux ensures compatibility.

## Creating a Plugin

### 1. Implement TermuxPlugin

```kotlin
class MyPlugin : TermuxPlugin {
    
    override val info = PluginInfo(
        id = "com.example.myplugin",
        name = "My Awesome Plugin",
        version = "1.0.0",
        apiVersion = "1.0.0",
        description = "Does awesome things",
        author = "Your Name",
        capabilities = setOf(
            PluginCapability.EXECUTE_COMMAND,
            PluginCapability.FILE_ACCESS
        )
    )
    
    private lateinit var host: PluginHost
    override var state = PluginState.UNLOADED
        private set
    
    override suspend fun onLoad(host: PluginHost): Result<Unit, PluginError> {
        this.host = host
        host.log(PluginHost.LogLevel.INFO, "Plugin loaded!")
        return Result.success(Unit)
    }
    
    override suspend fun onStart(): Result<Unit, PluginError> {
        // Register commands
        host.registerCommand("mycommand", "Does something cool") { args ->
            Result.success("Executed with args: ${args.joinToString()}")
        }
        
        state = PluginState.STARTED
        return Result.success(Unit)
    }
    
    override suspend fun onStop(): Result<Unit, PluginError> {
        host.unregisterCommand("mycommand")
        state = PluginState.STOPPED
        return Result.success(Unit)
    }
    
    override suspend fun onUnload(): Result<Unit, PluginError> {
        state = PluginState.UNLOADED
        return Result.success(Unit)
    }
    
    override suspend fun onMessage(message: IpcMessage): Result<IpcMessage?, PluginError> {
        // Handle messages from other plugins
        return Result.success(null)
    }
}
```

### 2. Define Capabilities

Plugins must declare what capabilities they need:

| Capability | Description |
|------------|-------------|
| `EXECUTE_COMMAND` | Run shell commands |
| `CREATE_SESSION` | Create terminal sessions |
| `READ_OUTPUT` | Read session output |
| `SEND_INPUT` | Send input to sessions |
| `FILE_ACCESS` | Read/write files (within Termux home) |
| `NETWORK_ACCESS` | Make network requests |
| `CLIPBOARD_ACCESS` | Access clipboard |
| `NOTIFICATIONS` | Show notifications |
| `BACKGROUND_EXECUTION` | Run in background |
| `ENVIRONMENT_ACCESS` | Access environment variables |
| `CUSTOM_COMMANDS` | Register custom commands |
| `UI_EXTENSION` | Extend the UI |

### 3. Use the PluginHost

The `PluginHost` interface provides access to Termux functionality:

```kotlin
// Execute a command
val result = host.executeCommand("ls", listOf("-la"), "/home")
when (result) {
    is Result.Success -> println(result.data.stdout)
    is Result.Error -> host.log(PluginHost.LogLevel.ERROR, result.error.message)
}

// Read a file
val content = host.readFile("myfile.txt")

// Access clipboard
val clipText = host.getClipboard()

// Show notification
host.showNotification("Title", "Content", id = 1)

// Register a command
host.registerCommand("hello", "Says hello") { args ->
    Result.success("Hello, ${args.firstOrNull() ?: "World"}!")
}
```

## Plugin Lifecycle

Plugins follow this lifecycle:

```
UNREGISTERED → REGISTERED → INITIALIZING → READY → STARTING → ACTIVE
                                                         ↓
                                               STOPPING → STOPPED
                                                         ↓
                                               DESTROYING → DESTROYED
```

### Lifecycle Methods

| Method | When Called | What to Do |
|--------|-------------|------------|
| `onLoad()` | Plugin is initialized | Set up resources |
| `onStart()` | Plugin is starting | Register commands, start services |
| `onStop()` | Plugin is stopping | Unregister commands, pause services |
| `onUnload()` | Plugin is being destroyed | Clean up all resources |

## Error Handling

All plugin operations return `Result<T, PluginError>`:

```kotlin
sealed class PluginError : TermuxError() {
    data class NotFound(val pluginId: String) : PluginError()
    data class LoadFailed(val pluginId: String, val cause: Throwable?) : PluginError()
    data class IncompatibleVersion(...) : PluginError()
    data class ExecutionError(...) : PluginError()
    data class SecurityViolation(...) : PluginError()
}
```

## Inter-Plugin Communication

Plugins can communicate via typed messages:

```kotlin
// Sending
val response = host.sendMessage(
    targetPluginId = "com.other.plugin",
    message = IpcMessage.ExecuteCommand(
        id = UUID.randomUUID().toString(),
        command = "doSomething"
    )
)

// Receiving (in onMessage)
override suspend fun onMessage(message: IpcMessage): Result<IpcMessage?, PluginError> {
    return when (message) {
        is IpcMessage.ExecuteCommand -> {
            // Handle command
            Result.success(IpcMessage.CommandResult(...))
        }
        else -> Result.success(null)
    }
}
```

## Security

### Sandboxing

- File access is restricted to Termux home directory
- Network access requires explicit capability
- Plugins cannot access other plugins' data without IPC

### Capability Enforcement

Every PluginHost method checks capabilities before execution:

```kotlin
// This will fail if FILE_ACCESS is not declared
host.readFile("/etc/passwd")  // Returns PluginError.SecurityViolation
```

## Testing Your Plugin

```kotlin
@Test
fun `plugin loads successfully`() = runTest {
    val plugin = MyPlugin()
    val mockHost = MockPluginHost()
    
    val result = plugin.onLoad(mockHost)
    
    assertTrue(result is Result.Success)
}
```

## CI Template

Add this to your plugin's `.github/workflows/ci.yml`:

```yaml
name: Plugin CI

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Run Detekt
        run: ./gradlew detekt
      
      - name: Run Tests
        run: ./gradlew test
      
      - name: Build Plugin
        run: ./gradlew assemble
      
      - name: Upload Artifact
        uses: actions/upload-artifact@v4
        with:
          name: plugin
          path: build/libs/*.jar
```

## Version Compatibility

| Plugin API | Termux Kotlin App |
|------------|-------------------|
| v1.0.0     | v1.0.0+           |

## Publishing

1. Build your plugin as an AAR/JAR
2. Host on GitHub Releases or Maven
3. Users install via Termux package manager (coming soon)

## Example Plugins

- [termux-api-kotlin](https://github.com/termux/termux-api) - Android API access
- [termux-tasker](https://github.com/termux/termux-tasker) - Tasker integration
- [termux-widget](https://github.com/termux/termux-widget) - Home screen widgets

## Support

- [GitHub Issues](https://github.com/reapercanuk39/termux-kotlin-app/issues)
- [Plugin API Reference](./api-reference.md)
