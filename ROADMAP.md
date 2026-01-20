# Termux Kotlin App — Development Roadmap

This document outlines the strategic vision and development priorities for transforming the Termux Kotlin fork into a modern, maintainable, and future-proof terminal platform.

---

## 1. Modernize the Core Architecture (Surpass Termux's Legacy Design)

Transform the Termux Kotlin fork into a fully modern, maintainable, and future-proof terminal platform by replacing the legacy Java-era architecture with a clean, Kotlin-first design. This includes:

- **Clear module boundaries** to separate UI, terminal engine, API layers, and shared utilities.
- **Coroutines instead of callbacks** for predictable, structured concurrency across the entire app and plugin ecosystem.
- **Sealed classes and data classes** for all API responses, error types, and IPC messages, replacing brittle string-based or loosely typed structures.
- **A unified permission and capability manager** that handles Android runtime permissions, SAF access, and background execution consistently across the core app and all plugins.
- **A modern dependency injection system** (Hilt) to eliminate global state, static helpers, and hidden side effects.
- **A centralized logging and telemetry layer** for debugging, crash analysis, and plugin diagnostics.
- **A stable internal API surface** with semantic versioning, enabling plugins to target a predictable, well-documented interface.
- **Full static analysis coverage** (Detekt, Lint, Kotlin compiler checks) enforced through CI to guarantee long-term code quality.

This architectural foundation positions the project as a modern, reliable, and extensible alternative to Termux, built for long-term ecosystem health rather than legacy compatibility.

### Progress

| Component | Status |
|-----------|--------|
| Hilt Dependency Injection | ✅ Implemented |
| Coroutines + Flow Architecture | ✅ Implemented |
| DataStore Preferences | ✅ Implemented |
| Jetpack Compose UI | ✅ Implemented |
| Detekt Static Analysis | ✅ CI Enforced |
| Lint Checks | ✅ CI Enforced |
| Sealed Class Result Types | ✅ Implemented |
| Sealed Class Error Types | ✅ Implemented |
| Unified Permission Manager | ✅ Implemented |
| Centralized Logging | ✅ Implemented |
| Flow-based Event Bus | ✅ Implemented |
| Plugin API with Versioning | ✅ Implemented |
| Module Boundaries | 🔄 In Progress |
| Migrate Callbacks to Flow | 📋 Planned |

---

## 2. Enhanced Terminal Experience

- **Split terminal panes** — horizontal, vertical, and quad layouts
- **Command Palette** — VS Code-style fuzzy command search (Ctrl+Shift+P)
- **Searchable command history** with statistics
- **SSH connection manager** with saved profiles
- **Custom themes** with Material 3 dynamic color support
- **Tab management** with drag-and-drop reordering

### Progress

| Feature | Status |
|---------|--------|
| Split Terminal (Data Layer) | ✅ Implemented |
| Command Palette | ✅ Implemented |
| Command History Repository | ✅ Implemented |
| SSH Profile Manager | ✅ Implemented |
| Material 3 Theme | ✅ Implemented |
| Split Terminal (UI) | 📋 Planned |
| Tab Management | 📋 Planned |

---

## 3. Unified Plugin Ecosystem

A comprehensive plugin framework enabling third-party extensions while maintaining security and stability.

### Plugin Framework (core/plugin)

- **Stable, versioned Plugin API (v1.0.0+)** providing a predictable contract for all plugins
- **PluginHost interface** enabling structured, typed communication between plugins and the Termux core
- **PluginRegistry** for discovery, capability negotiation, and lifecycle coordination
- **PluginCapabilities model** defining what each plugin can request or provide
- **Typed IPC messages** shared across all plugins using the IpcMessage sealed hierarchy

### Shared Permission Layer (core/permissions)

- **Unified permission handling** for all plugins using the Activity Result API
- **Coroutine-based permission requests** (suspend), eliminating callback chains
- **Flow-based permission state observation** for reactive plugin behavior
- **Centralized support** for storage, notifications, battery optimizations, overlays, and background execution

### Shared Logging Layer (core/logging)

- **TermuxLogger** available to all plugins with Logcat, File, and In-memory writers
- **Structured logs** with metadata (plugin ID, component, timestamp)
- **Flow-based log streaming** for debugging and plugin diagnostics
- **Tagged loggers** for plugin-scoped logging

### Plugin Lifecycle & Execution Model

- **Standardized lifecycle** (REGISTERED → INITIALIZING → READY → ACTIVE → STOPPED → DESTROYED)
- **Coroutine-based execution** model for async plugin operations
- **Graceful cancellation** and structured concurrency for long-running tasks
- **Plugin sandboxing** boundaries for safety and predictable behavior

### Plugin Distribution & Compatibility

- **Semantic versioning** for the Plugin API to ensure forward compatibility
- **CI templates** for all plugins (lint, detekt, tests, debug builds)
- **Compatibility matrix** testing against multiple Termux core versions

### Progress

| Component | Status |
|-----------|--------|
| Plugin API v1.0.0 | ✅ Implemented |
| PluginHost Interface | ✅ Implemented |
| PluginCapabilities Model | ✅ Implemented |
| IpcMessage Sealed Hierarchy | ✅ Implemented |
| PluginRegistry Implementation | ✅ Implemented |
| Plugin Lifecycle States | ✅ Implemented |
| PluginHostImpl (Full Implementation) | ✅ Implemented |
| Plugin Command Registry | ✅ Implemented |
| Inter-Plugin Messaging | ✅ Implemented |
| File Access Sandboxing | ✅ Implemented |
| Shared Permission Layer | ✅ Implemented |
| Shared Logging Layer | ✅ Implemented |
| Plugin SDK Documentation | ✅ Implemented |
| CI Templates for Plugins | ✅ Documented |
| Plugin Marketplace | 📋 Planned |

---

## 4. Modern Settings UI + UX Overhaul

Transform settings into a modern, Compose-based UI with profiles, themes, and instant-apply preferences.

### Settings Architecture
- **Material 3 Compose UI** replacing PreferenceFragmentCompat
- **DataStore Preferences** replacing SharedPreferences
- **Searchable settings** with category filtering
- **Smooth animations** and consistent typography

### Profile System
- **Named profiles** with all settings (font, theme, shell, env vars, plugins)
- **Quick profile switching** from terminal
- **Profile import/export** for sharing configurations
- **Use cases**: Work, Dev, Minimal startup profiles

### Theme Gallery
- **Built-in themes**: Dark Steel, Molten Blue, Obsidian, Solarized, Gruvbox, High Contrast
- **Live preview** with terminal sample
- **Import/export themes** in JSON format
- **Plugin-provided themes** support

### Progress

| Component | Status |
|-----------|--------|
| SettingsDataStore | ✅ Implemented |
| SettingsViewModel | ✅ Implemented |
| Compose Settings Screen | ✅ Implemented |
| Profile System (Data) | ✅ Implemented |
| Profile System (UI) | ✅ Implemented |
| Theme Gallery | ✅ Implemented |
| Settings Search | ✅ Implemented |
| Hilt DI Module | ✅ Implemented |
| Compose Navigation | ✅ Implemented |

---

## 5. Package Management Enhancements

Surpass Termux in reliability and usability with comprehensive package management tools.

### Package Backup & Restore
- **Full backup**: packages, versions, repositories, dotfiles
- **Restore options**: full, selective, dry-run mode
- **JSON export format** for portability
- **UI + CLI integration** via termuxctl

### Package Health Checks (Doctor)
- **Dependency integrity checks**
- **Broken package detection**
- **Auto-repair suggestions**
- **Health score reporting**

### termuxctl CLI
- `termuxctl backup create/restore/list`
- `termuxctl pkg doctor [--auto-repair]`
- `termuxctl repo list/add/remove`
- `termuxctl profile list/activate/export/import`

### Repository Management UI
- **Visual repository management**
- **Enable/disable sources**
- **Add custom repositories**
- **View repo metadata and signatures**

### Progress

| Component | Status |
|-----------|--------|
| PackageBackupManager | ✅ Implemented |
| BackupMetadata Model | ✅ Implemented |
| PackageDoctor | ✅ Implemented |
| termuxctl CLI | ✅ Implemented |
| Hilt DI Module | ✅ Implemented |
| Repository Management UI | 📋 Planned |
| Parallel Downloads | 📋 Planned |
| Package Event Hooks | 📋 Planned |

---

## 6. Performance & Reliability

- **Memory-efficient terminal buffer**
- **Background task optimization**
- **Crash recovery and session restoration**
- **Battery usage optimization**
- **Startup time improvements**

---

## 7. Accessibility & Localization

- **Screen reader support** (TalkBack)
- **High contrast themes**
- **Keyboard-only navigation**
- **RTL language support**
- **Community-driven translations**

---

## 8. Agent Framework ✅ COMPLETED

A fully offline agent system with **Kotlin-native daemon** (v2.0.5+) and Python fallback:

| Feature | Status |
|---------|--------|
| **Kotlin-Native AgentDaemon** | ✅ Implemented (v2.0.5) |
| **Auto-start with App** | ✅ Implemented (v2.0.5) |
| **45+ Capabilities System** | ✅ Implemented (v2.0.5) |
| **Swarm Intelligence (Kotlin)** | ✅ Implemented (v2.0.5) |
| Agent Supervisor (Python agentd) | ✅ Implemented |
| Plugin/Skill System | ✅ Implemented |
| Memory & Sandboxing | ✅ Implemented |
| CLI Interface (`agent` command) | ✅ Implemented |
| Built-in Agents (build, debug, system, repo) | ✅ Implemented |
| Built-in Skills (pkg, git, fs, qemu, iso, apk, docker) | ✅ Implemented |
| Bootstrap Integration | ✅ Implemented |
| Documentation (AI.md) | ✅ Complete |

**v2.0.5 Kotlin-Native Components:**
- `AgentDaemon.kt` - Core supervisor singleton
- `SkillExecutor.kt` - Task dispatch to skills
- `SwarmCoordinator.kt` - Stigmergy coordination
- `PkgSkill.kt`, `FsSkill.kt`, `GitSkill.kt`, `DiagnosticSkill.kt` - Pure Kotlin skills
- `PythonSkillBridge.kt` - Fallback for complex Python skills

**Future Enhancements:**
- Natural language task interpretation
- Agent-to-agent communication
- SQLite memory backend
- Web UI for agent management
- Local LLM integration (Ollama/llama.cpp)

---

## 9. Integrated Plugins ✅ COMPLETED (v2.0.5)

Built-in functionality that previously required separate plugin apps:

| Feature | Status |
|---------|--------|
| **Termux:Boot** | ✅ Integrated (v2.0.5) |
| **Termux:Styling** | ✅ Integrated (v2.0.5) |
| **Termux:Widget** | ✅ Integrated (v2.0.5) |
| **Termux:API** | ✅ Integrated (v2.0.5) |
| Termux:Tasker | 📋 Planned |
| Termux:Float | 📋 Planned |

### Termux:Boot Integration
- `BootService.kt` - Foreground service with wake lock
- `BootScriptExecutor.kt` - Runs `~/.termux/boot/` scripts
- `BootPreferences.kt` - DataStore settings
- Auto-runs scripts on device boot

### Termux:Styling Integration
- 11 built-in color schemes (Dracula, Monokai, Nord, etc.)
- Font picker with custom font support
- Material 3 Compose settings UI
- Real-time terminal preview

### Termux:Widget Integration
- 3 widget sizes (1x1, 2x1, 4x1)
- Scans `~/.shortcuts/` for scripts
- Configurable via Compose UI
- One-tap script execution from home screen

### Termux:API Integration
- `DeviceApiService.kt` - Background service for API operations
- `DeviceApiActionBase.kt` - Base class for all API actions
- `BatteryAction.kt` - Battery status implementation
- 20+ API actions: battery, clipboard, location, sensors, camera, audio, vibration, toast, TTS, torch, WiFi, telephony, SMS, contacts, notifications
- Typed IPC with sealed `DeviceApiMessage` classes
- No separate APK required

---

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the GPLv3. See [LICENSE.md](LICENSE.md) for details.
