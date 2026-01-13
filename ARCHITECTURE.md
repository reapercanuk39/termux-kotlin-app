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
│   │   │   │   └── terminal/              # Terminal session clients
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
└── .github/workflows/            # CI/CD workflows
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

## 📚 Resources

- [Termux Wiki](https://wiki.termux.com/)
- [VT100 Escape Sequences](https://vt100.net/docs/)
- [Android NDK](https://developer.android.com/ndk)
