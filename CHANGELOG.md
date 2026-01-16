# Changelog

All notable changes to Termux Kotlin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [v1.0.19] - 2026-01-16

### 📚 Documentation
-  Update CHANGELOG for v1.0.18 [skip ci]


## [v1.0.18] - 2026-01-16

### 📚 Documentation
-  Update CHANGELOG for v1.0.17 [skip ci]


## [v1.0.17] - 2026-01-16

### 🐛 Bug Fixes
-  Fix update-alternatives internal paths directly (Error #7 revised)

### 📚 Documentation
-  Update CHANGELOG for v1.0.16 [skip ci]


## [v1.0.16] - 2026-01-16

### 🐛 Bug Fixes
-  Add update-alternatives wrapper for hardcoded paths (Error #7)

### 📚 Documentation
-  Update error.md with v1.0.15 release status
-  Update CHANGELOG for v1.0.15 [skip ci]


## [v1.0.15] - 2026-01-16

### 🐛 Bug Fixes
-  Fix dpkg maintainer script shebang paths (Error #6)

### 📚 Documentation
-  Update CHANGELOG for v1.0.14 [skip ci]
-  Update error.md with current status


## [v1.0.14] - 2026-01-16

### 🐛 Bug Fixes
-  Login script uses bash shebang, dpkg wrapper intercepts --version

### 📚 Documentation
-  Update error.md with current status
-  Update CHANGELOG for v1.0.13 [skip ci]


## [v1.0.13] - 2026-01-16

### 🐛 Bug Fixes
-  Handle dpkg/bash hardcoded paths when original Termux is installed


## [v1.0.11] - 2026-01-16

### 🐛 Bug Fixes
-  Fix shell script shebang paths in bin/ directory

### 📚 Documentation
-  Update CHANGELOG for v1.0.10 [skip ci]


## [v1.0.10] - 2026-01-16

### 🐛 Bug Fixes
-  Fix DT_HASH/DT_GNU_HASH error by using original bootstrap

### 📚 Documentation
-  Update CHANGELOG for v1.0.9 [skip ci]


## [v1.0.9] - 2026-01-16

### 🐛 Bug Fixes
-  Update bootstrap paths for com.termux.kotlin package
-  Add tag_name to gh-release action for workflow_dispatch

### 📚 Documentation
-  Update CHANGELOG for v1.0.8 [skip ci]

### 🔧 Maintenance
-  Bump version to 1.0.9
-  Add workflow_dispatch trigger to release workflow


## [v1.0.8] - 2026-01-16

### 🐛 Bug Fixes
-  Add tag_name to gh-release action for workflow_dispatch
-  Fix release workflow issues

### 📚 Documentation
-  Update CHANGELOG for v1.0.7 [skip ci]

### 🔧 Maintenance
-  Add workflow_dispatch trigger to release workflow


## [v1.0.7] - 2026-01-15

### 🐛 Bug Fixes
-  Add RECEIVER_NOT_EXPORTED flag for Android 14+ compatibility

### 📚 Documentation
-  Update CHANGELOG for v1.0.6 [skip ci]


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
