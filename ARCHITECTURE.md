# Architecture Documentation

This document describes the architecture of the Termux Kotlin app.

## 📁 Project Structure

```
termux-kotlin-app/
├── app/                          # Main application module
│   ├── src/main/
│   │   ├── kotlin/com/termux/
│   │   │   ├── app/              # Main app components
│   │   │   │   ├── TermuxActivity.kt      # Main terminal activity
│   │   │   │   ├── TermuxService.kt       # Background terminal service
│   │   │   │   ├── TermuxApplication.kt   # Application class
│   │   │   │   ├── TermuxInstaller.kt     # Bootstrap installer
│   │   │   │   ├── activities/            # Other activities
│   │   │   │   ├── api/                   # API implementations
│   │   │   │   ├── fragments/             # UI fragments
│   │   │   │   ├── terminal/              # Terminal session clients
│   │   │   │   ├── ui/                    # Modern UI components
│   │   │   │   │   └── settings/          # Compose settings UI
│   │   │   │   │       ├── data/          # DataStore, Theme, Profile
│   │   │   │   │       ├── components/    # Reusable Compose components
│   │   │   │   │       └── sections/      # Settings sections
│   │   │   │   └── pkg/                   # Package management
│   │   │   │       ├── backup/            # Backup/restore system
│   │   │   │       ├── doctor/            # Health diagnostics
│   │   │   │       ├── repository/        # Repo management
│   │   │   │       └── cli/               # termuxctl CLI
│   │   │   └── filepicker/       # File picker components
│   │   ├── cpp/                  # Native code (bootstrap loader)
│   │   └── res/                  # Resources
│   └── build.gradle
│
├── terminal-emulator/            # Terminal emulation library
│   └── src/main/kotlin/com/termux/terminal/
│       ├── TerminalEmulator.kt   # Core emulator logic
│       ├── TerminalSession.kt    # Session management
│       ├── TerminalBuffer.kt     # Screen buffer
│       └── ...
│
├── terminal-view/                # Terminal UI view
│   └── src/main/kotlin/com/termux/view/
│       ├── TerminalView.kt       # Custom terminal view
│       ├── TerminalRenderer.kt   # Rendering logic
│       └── ...
│
├── termux-shared/                # Shared utilities
│   └── src/main/kotlin/com/termux/shared/
│       ├── activities/           # Shared activities
│       ├── data/                 # Data utilities
│       ├── file/                 # File operations
│       ├── logger/               # Logging
│       ├── models/               # Data models
│       ├── net/                  # Network utilities
│       ├── packages/             # Package management
│       ├── settings/             # Settings/preferences
│       ├── shell/                # Shell execution
│       └── ...
│
├── docs/                         # Documentation
│   └── IMPLEMENTATION_PLAN_SETTINGS_AND_PACKAGES.md
│
└── .github/workflows/            # CI/CD workflows

### Agent Framework (v2.0.5+)

```
app/src/main/kotlin/com/termux/app/agents/
├── cli/
│   └── CliBridge.kt              # File-based IPC for shell access
├── daemon/
│   ├── AgentDaemon.kt            # Core supervisor singleton
│   ├── AgentRegistry.kt          # Agent discovery and lifecycle
│   └── AgentWorker.kt            # Periodic health checks
├── di/
│   └── AgentModule.kt            # Hilt DI module
├── models/
│   ├── Agent.kt                  # Agent data model
│   ├── Capability.kt             # 45+ capability definitions
│   └── TaskResult.kt             # Sealed result types
├── runtime/
│   ├── AgentMemory.kt            # Persistent key-value storage
│   ├── AgentSandbox.kt           # Execution isolation
│   ├── CommandRunner.kt          # Process execution
│   └── SkillExecutor.kt          # Task dispatch to skills
├── skills/
│   ├── DiagnosticSkill.kt        # System diagnostics
│   ├── FsSkill.kt                # Filesystem operations
│   ├── GitSkill.kt               # Git operations
│   ├── PkgSkill.kt               # Package management
│   ├── PythonSkillBridge.kt      # Python skill fallback
│   └── SkillProvider.kt          # Skill registry
└── swarm/
    ├── Signal.kt                 # 13 signal types for stigmergy
    └── SwarmCoordinator.kt       # Multi-agent coordination
```

### Integrated Plugins (v2.0.5+)

```
app/src/main/kotlin/com/termux/app/
├── boot/
│   ├── BootModule.kt             # Hilt DI
│   ├── BootPreferences.kt        # DataStore settings
│   ├── BootScriptExecutor.kt     # Script runner
│   └── BootService.kt            # Foreground service
├── styling/
│   ├── ColorScheme.kt            # 11 built-in themes
│   ├── FontManager.kt            # Font loading
│   ├── StylingActivity.kt        # Compose settings UI
│   ├── StylingManager.kt         # Theme persistence
│   └── StylingModule.kt          # Hilt DI
└── widget/
    ├── ShortcutScanner.kt        # ~/.shortcuts/ scanner
    ├── TermuxWidgetProvider.kt   # AppWidgetProvider
    ├── WidgetConfigureActivity.kt # Compose configuration
    ├── WidgetModule.kt           # Hilt DI
    ├── WidgetPreferences.kt      # Widget settings
    └── WidgetRemoteViewsService.kt # List adapter
```

## 🏗️ Module Architecture

### Module Dependencies

```
┌─────────────────┐
│       app       │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
┌───────┐ ┌─────────────┐
│terminal│ │terminal-view│
│emulator│ └──────┬──────┘
└───┬───┘        │
    │            │
    └─────┬──────┘
          │
          ▼
   ┌─────────────┐
   │termux-shared│
   └─────────────┘
```

### Module Responsibilities

| Module | Responsibility |
|--------|---------------|
| `app` | Main application, UI, services, activities |
| `terminal-emulator` | VT100/ANSI terminal emulation logic |
| `terminal-view` | Android View for rendering terminal |
| `termux-shared` | Shared utilities, models, file operations |

## 🔧 Key Components

### TermuxActivity
The main activity that hosts the terminal interface.
- Manages terminal sessions
- Handles keyboard input
- Controls the drawer with session list

### TermuxService
Background service that keeps terminal sessions alive.
- Manages shell processes
- Handles wake locks
- Processes execution commands

### TerminalEmulator
Core terminal emulation logic.
- Parses escape sequences
- Maintains screen buffer
- Handles cursor positioning

### TerminalView
Custom Android View for terminal rendering.
- Renders terminal buffer
- Handles touch/gesture input
- Manages text selection

## 🔄 Data Flow

```
User Input → TerminalView → TerminalSession → Shell Process
                                    ↓
Shell Output ← TerminalEmulator ← TerminalSession
                    ↓
              TerminalView (render)
```

## 🎨 Design Patterns

### Patterns Used
- **Service Pattern**: `TermuxService` for background operations
- **Observer Pattern**: Terminal session callbacks
- **Builder Pattern**: Command execution builders
- **Singleton Pattern**: Application-level managers

### Kotlin Features
- **Extension Functions**: Utility extensions throughout
- **Coroutines**: Async operations (where applicable)
- **Sealed Classes**: State management
- **Data Classes**: Model objects
- **Null Safety**: Leveraged throughout

## 📦 Build Variants

| Variant | Package Variant | Description |
|---------|----------------|-------------|
| Debug | apt-android-7 | Development build |
| Release | apt-android-7 | Production signed build |

### APK Splits
APKs are split by ABI for smaller download sizes:
- `arm64-v8a` - 64-bit ARM (most modern devices)
- `armeabi-v7a` - 32-bit ARM (older devices)
- `x86_64` - 64-bit x86 (emulators, ChromeOS)
- `x86` - 32-bit x86 (older emulators)
- `universal` - All architectures

## 🔐 Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Network access for packages |
| `WAKE_LOCK` | Keep terminal alive in background |
| `VIBRATE` | Haptic feedback |
| `FOREGROUND_SERVICE` | Background terminal service |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent battery optimization |

## 🌍 Environment Variables

The app sets these environment variables automatically for proper terminal operation:

| Variable | Value | Purpose |
|----------|-------|---------|
| `HOME` | `/data/data/com.termux.kotlin/files/home` | User home directory |
| `PREFIX` | `/data/data/com.termux.kotlin/files/usr` | Termux prefix directory |
| `PATH` | `$PREFIX/bin` | Executable search path |
| `LD_LIBRARY_PATH` | `$PREFIX/lib` | Library search path (overrides RUNPATH) |
| `TMPDIR` | `$PREFIX/tmp` | Temporary files directory |
| `TERMINFO` | `$PREFIX/share/terminfo` | Terminal capability database (for `clear`, `tput`, ncurses) |
| `LANG` | `en_US.UTF-8` | Locale setting |
| `COLORTERM` | `truecolor` | 24-bit color support |
| `TERM` | `xterm-256color` | Terminal type |

### Package Manager Variables

| Variable | Value | Purpose |
|----------|-------|---------|
| `DPKG_ADMINDIR` | `$PREFIX/var/lib/dpkg` | dpkg database location |
| `DPKG_DATADIR` | `$PREFIX/share/dpkg` | dpkg data files |

### Compatibility Layer Variables

| Variable | Value | Purpose |
|----------|-------|---------|
| `LD_PRELOAD` | `$PREFIX/lib/libtermux_compat.so` | Runtime path interception shim |

The LD_PRELOAD shim is auto-compiled when clang is installed and loaded automatically on shell startup.

### SSL/TLS Variables

| Variable | Value | Purpose |
|----------|-------|---------|
| `SSL_CERT_FILE` | `$PREFIX/etc/tls/cert.pem` | CA certificate bundle for curl/wget |
| `CURL_CA_BUNDLE` | `$PREFIX/etc/tls/cert.pem` | curl-specific CA bundle path |

These SSL variables enable HTTPS connections to package mirrors and other secure endpoints.

## 📚 Resources

- [Termux Wiki](https://wiki.termux.com/)
- [VT100 Escape Sequences](https://vt100.net/docs/)
- [Android NDK](https://developer.android.com/ndk)

## 🎨 Settings & Package Management Architecture

### Settings Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    SettingsScreen.kt                         │
│                    (Jetpack Compose)                         │
├─────────────────────────────────────────────────────────────┤
│                   SettingsViewModel.kt                       │
│              (StateFlow, UI State Management)                │
├─────────────────────────────────────────────────────────────┤
│                  SettingsDataStore.kt                        │
│          (Preferences DataStore, Type-safe Keys)             │
├─────────────────────────────────────────────────────────────┤
│                   DataStore + Room                           │
│              (Preferences + Profile Database)                │
└─────────────────────────────────────────────────────────────┘
```

### Package Management Components

```
┌─────────────────────────────────────────────────────────────┐
│                      termuxctl CLI                           │
│         (backup, restore, doctor, repo, profile)             │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────────────┐  ┌──────────────────────────────┐  │
│  │  PackageBackupManager │  │      PackageDoctor          │  │
│  │  - createBackup()     │  │  - runFullDiagnostic()      │  │
│  │  - restoreBackup()    │  │  - autoRepair()             │  │
│  │  - listBackups()      │  │  - isHealthy()              │  │
│  └──────────────────────┘  └──────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    BackupMetadata.kt                         │
│   (PackageInfo, RepositoryInfo, BackupConfig, RestoreOptions)│
├─────────────────────────────────────────────────────────────┤
│                  DiagnosticResult.kt                         │
│    (DiagnosticIssue, DiagnosticReport, IssueSeverity)        │
└─────────────────────────────────────────────────────────────┘
```

### Theme System

```kotlin
// Built-in themes available:
Theme.DARK_STEEL      // Signature Termux Kotlin theme
Theme.MOLTEN_BLUE     // GitHub-inspired
Theme.OBSIDIAN        // VS Code-inspired
Theme.DRACULA         // Popular dark theme
Theme.NORD            // Arctic palette
Theme.SOLARIZED_DARK  // Classic precision
Theme.SOLARIZED_LIGHT // Light variant
Theme.GRUVBOX_DARK    // Retro groove
Theme.GRUVBOX_LIGHT   // Light variant
Theme.HIGH_CONTRAST   // Maximum readability
```

### Profile System

Profiles allow saving and switching between complete terminal configurations:

```kotlin
data class Profile(
    val id: String,
    val name: String,               // "Work", "Dev", "Minimal"
    
    // Appearance
    val fontFamily: String,         // "JetBrains Mono", "Fira Code"
    val fontSize: Int,              // 12-24
    val themeName: String,          // "dark_steel", "dracula"
    val lineSpacing: Float,         // 1.0-2.0
    val ligaturesEnabled: Boolean,
    
    // Shell
    val shell: String,              // "/bin/bash", "/bin/zsh"
    val startupCommands: List<String>,
    val environmentVariables: Map<String, String>,
    
    // Plugins
    val enabledPlugins: Set<String>
)
```

### Backup System

Supports multiple backup types for different use cases:

| Type | Contents | Size | Use Case |
|------|----------|------|----------|
| `FULL` | Packages + Repos + Dotfiles | Large | Complete environment restore |
| `PACKAGES_ONLY` | Package list only | Small | Quick reinstall on new device |
| `CONFIG_ONLY` | Dotfiles only | Tiny | Sync config across devices |
| `MINIMAL` | Manually installed packages | Smallest | Essential packages only |

### Package Doctor Checks

| Check | Detects | Severity | Auto-Fix |
|-------|---------|----------|----------|
| Broken | Corrupted packages | HIGH | `apt --fix-broken install` |
| Dependencies | Missing deps | HIGH | `apt install -f` |
| Held | Upgrade-blocked packages | LOW | `apt-mark unhold` |
| Versions | Upgradable packages | INFO | `apt upgrade` |
| Orphaned | Unused packages | INFO | `apt autoremove` |
| Repositories | Failed fetches, GPG issues | MEDIUM | Varies |

## 📱 Integrated Device API

The Termux:API functionality is integrated directly into the app, eliminating the need for a separate APK.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     termuxctl CLI                            │
│              termuxctl device battery                        │
│              termuxctl device location                       │
├─────────────────────────────────────────────────────────────┤
│                   DeviceCommands.kt                          │
│          (CLI command handlers for device APIs)              │
├─────────────────────────────────────────────────────────────┤
│                  DeviceApiService.kt                         │
│      (Background service for long-running operations)        │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────────────┐  ┌──────────────────────────────┐  │
│  │     BatteryAction    │  │       LocationAction         │  │
│  │     ClipboardAction  │  │       SensorAction          │  │
│  │     CameraAction     │  │       WifiAction            │  │
│  │          ...         │  │           ...               │  │
│  └──────────────────────┘  └──────────────────────────────┘  │
│                    (Device API Actions)                      │
├─────────────────────────────────────────────────────────────┤
│                  DeviceApiActionBase.kt                      │
│   (Base class with logging, permissions, error handling)     │
├─────────────────────────────────────────────────────────────┤
│                    Core Components                           │
│  ┌──────────────┐ ┌───────────────┐ ┌────────────────────┐  │
│  │ Result<T,E>  │ │ DeviceApiError │ │  PermissionManager │  │
│  │ TermuxError  │ │   hierarchy    │ │  TermuxLogger      │  │
│  └──────────────┘ └───────────────┘ └────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Module Structure

```
app/src/main/kotlin/com/termux/app/
├── core/
│   └── deviceapi/
│       ├── DeviceApiError.kt         # Error types for device APIs
│       ├── actions/
│       │   ├── DeviceApiActionBase.kt # Base class for all actions
│       │   ├── BatteryAction.kt       # Battery status API
│       │   ├── ClipboardAction.kt     # Clipboard get/set
│       │   ├── LocationAction.kt      # GPS location
│       │   ├── SensorAction.kt        # Device sensors
│       │   └── ...
│       ├── models/
│       │   ├── BatteryInfo.kt         # Battery data model
│       │   ├── DeviceApiMessage.kt    # IPC message types
│       │   └── ...
│       └── service/
│           └── DeviceApiService.kt    # Background service
├── di/
│   └── DeviceApiModule.kt             # Hilt DI module
└── pkg/cli/commands/device/
    └── DeviceCommands.kt              # CLI commands
```

### Available APIs

| API | Command | Permissions | Status |
|-----|---------|-------------|--------|
| Battery | `termuxctl device battery` | None | ✅ Implemented |
| Clipboard | `termuxctl device clipboard` | None | 🔜 Planned |
| Location | `termuxctl device location` | ACCESS_FINE_LOCATION | 🔜 Planned |
| Sensors | `termuxctl device sensor` | None | 🔜 Planned |
| Camera | `termuxctl device camera` | CAMERA | 🔜 Planned |
| WiFi | `termuxctl device wifi` | ACCESS_WIFI_STATE | 🔜 Planned |
| Volume | `termuxctl device volume` | None | 🔜 Planned |
| Torch | `termuxctl device torch` | CAMERA | 🔜 Planned |
| Vibrate | `termuxctl device vibrate` | VIBRATE | 🔜 Planned |
| TTS | `termuxctl device tts` | None | 🔜 Planned |
| Toast | `termuxctl device toast` | None | 🔜 Planned |

### Implementation Pattern

Each device API action follows a consistent pattern:

```kotlin
@Singleton
class ExampleAction @Inject constructor(
    @ApplicationContext private val context: Context,
    logger: TermuxLogger,
    private val permissionManager: PermissionManager  // If permissions needed
) : DeviceApiActionBase<ExampleData>(logger) {
    
    override val actionName: String = "example"
    override val description: String = "Example API action"
    override val requiredPermissions: List<String> = listOf(
        Manifest.permission.EXAMPLE_PERMISSION
    )
    
    override suspend fun execute(
        params: Map<String, String>
    ): Result<ExampleData, DeviceApiError> {
        return executeWithLogging {
            withContext(Dispatchers.IO) {
                // Implementation
                ExampleData(...)
            }
        }
    }
}
```

### Error Handling

Device API errors extend the `TermuxError` hierarchy:

```kotlin
sealed class DeviceApiError : TermuxError() {
    data class PermissionRequired(...)   // Permission not granted
    data class FeatureNotAvailable(...)  // Hardware/software not available
    data class HardwareNotFound(...)     // Sensor/camera not present
    data class Timeout(...)              // Operation timed out
    data class Cancelled(...)            // Operation cancelled
    data class InvalidArguments(...)     // Bad parameters
    data class ServiceUnavailable(...)   // Service disabled
    data class SystemException(...)      // Unexpected error
    data class RateLimited(...)          // Too many requests
    data class UnsupportedApiLevel(...)  // Android version too old
}
```

### CLI Usage

```bash
# Battery status
termuxctl device battery
termuxctl device battery --json
termuxctl device battery --extended

# List available APIs
termuxctl device list

# Future APIs
termuxctl device location --provider gps
termuxctl device sensor --list
termuxctl device sensor --name accelerometer
termuxctl device clipboard get
termuxctl device clipboard set "text"
termuxctl device wifi scan
termuxctl device volume get
termuxctl device torch on
```

### IPC Messages

Device API uses typed IPC messages for communication:

```kotlin
sealed class DeviceApiMessage : IpcMessage() {
    data class ApiRequest(...)    // Request to execute action
    data class ApiResponse(...)   // Success response with data
    data class ApiError(...)      // Error response
    data class StreamData(...)    // Streaming data (sensors, etc.)
    data class StreamEnded(...)   // Stream completed
}
```

### Dependency Injection

All device API components are provided via Hilt:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DeviceApiModule {
    @Provides @Singleton
    fun provideBatteryAction(...): BatteryAction
    
    @Provides @Singleton
    fun provideDeviceCommands(...): DeviceCommands
    
    // Add more as implemented
}
```
