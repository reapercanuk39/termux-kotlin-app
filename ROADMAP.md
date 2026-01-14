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

## 3. Plugin Ecosystem

- **Stable Plugin API** with semantic versioning
- **Plugin SDK** with documentation and examples
- **Plugin marketplace** integration
- **Sandboxed plugin execution** for security
- **Plugin lifecycle management**

---

## 4. Performance & Reliability

- **Memory-efficient terminal buffer**
- **Background task optimization**
- **Crash recovery and session restoration**
- **Battery usage optimization**
- **Startup time improvements**

---

## 5. Accessibility & Localization

- **Screen reader support** (TalkBack)
- **High contrast themes**
- **Keyboard-only navigation**
- **RTL language support**
- **Community-driven translations**

---

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the GPLv3. See [LICENSE.md](LICENSE.md) for details.
