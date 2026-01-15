# Changelog

All notable changes to Termux Kotlin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [v1.0.6] - 2026-01-15

### 🐛 Bug Fixes
-  Remove force unwrap (            {                 echo ___BEGIN___COMMAND_OUTPUT_MARKER___;                 PS1=;PS2=;unset HISTFILE;                 EC=0;                 echo ___BEGIN___COMMAND_DONE_MARKER___0;             }) in runStartForeground to prevent NPE crash

### 📚 Documentation
-  Update CHANGELOG for v1.0.5 [skip ci]


## [v1.0.5] - 2026-01-15

### 🐛 Bug Fixes
-  Move splits config to android level to fix universal APK crash

### 📚 Documentation
-  Add CHANGELOG.md and auto-update on releases

## [v1.0.4] - 2026-01-15

### 🐛 Bug Fixes
- Add crash resilience and null-safety improvements
- Add comprehensive ProGuard rules to preserve JNI/native methods
- Add try-catch error handling for native library loading
- Fix null-safety in TermuxService.onCreate() with graceful shutdown
- Fix service binding race condition in TermuxActivity

### 🔧 Maintenance
- Add auto-release workflow for merged PRs

## [v1.0.3] - 2026-01-15

### 🐛 Bug Fixes
- Update bootstrap to 2026.01.11-r1 to fix app crash on launch

## [v1.0.2] - 2026-01-13

### ✨ Features
- Initial Kotlin release with full codebase conversion
- 100% Kotlin implementation
- Modern architecture with Compose UI components
- Hilt dependency injection
- Coroutines for async operations

## [v1.0.1] - 2026-01-13

### 🐛 Bug Fixes
- Initial stable release fixes

## [v1.0.0-kotlin] - 2026-01-12

### ✨ Features
- Complete Kotlin conversion of Termux app
- Terminal emulator with full PTY support
- Bootstrap package installation
- Extra keys keyboard
- Session management
