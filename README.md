# 🚀 Termux Kotlin App

<div align="center">

[![CI](https://github.com/reapercanuk39/termux-kotlin-app/actions/workflows/ci.yml/badge.svg)](https://github.com/reapercanuk39/termux-kotlin-app/actions/workflows/ci.yml)
[![Release](https://github.com/reapercanuk39/termux-kotlin-app/actions/workflows/release.yml/badge.svg)](https://github.com/reapercanuk39/termux-kotlin-app/actions/workflows/release.yml)
[![GitHub Downloads](https://img.shields.io/github/downloads/reapercanuk39/termux-kotlin-app/total?style=for-the-badge&logo=github&label=Downloads&color=success)](https://github.com/reapercanuk39/termux-kotlin-app/releases)
[![Latest Release](https://img.shields.io/github/v/release/reapercanuk39/termux-kotlin-app?style=for-the-badge&logo=android&label=Latest&color=blue)](https://github.com/reapercanuk39/termux-kotlin-app/releases/latest)

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-7.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![License](https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge)](LICENSE.md)
[![Fork](https://img.shields.io/badge/Fork%20of-termux%2Ftermux--app-orange?style=for-the-badge&logo=github)](https://github.com/termux/termux-app)

**The official Termux Android terminal emulator, fully converted to Kotlin!**

<a href="https://github.com/reapercanuk39/termux-kotlin-app/releases/latest">
  <img src="https://img.shields.io/badge/⬇️_Download_APK-Latest_Release-brightgreen?style=for-the-badge&logo=android" alt="Download APK">
</a>

[Features](#features) • [Installation](#installation) • [Building](#building) • [Contributing](#contributing) • [Original Project](#original-project)

</div>

---

## 📱 What is Termux?

**Termux** is a powerful **Android terminal emulator** and **Linux environment** app that works directly with no rooting or setup required. It provides a complete Linux environment on your Android device with access to:

- 🐧 **Linux shell** (bash, zsh, fish)
- 📦 **Package manager** (apt/pkg) with thousands of packages
- 🐍 **Programming languages** (Python, Node.js, Ruby, Go, Rust, C/C++)
- 🔧 **Development tools** (git, vim, nano, ssh, rsync)
- 🌐 **Networking utilities** (curl, wget, nmap, netcat)

## ✨ What is Termux Kotlin App?

This repository is a **complete Kotlin conversion** of the official [termux-app](https://github.com/termux/termux-app). Every Java file has been meticulously converted to idiomatic Kotlin while maintaining 100% compatibility with the original app.

### 🎯 Why Kotlin?

| Feature | Benefit |
|---------|---------|
| **Null Safety** | Compile-time null checks prevent NullPointerExceptions |
| **Concise Syntax** | ~40% less boilerplate code |
| **Type Inference** | Cleaner, more readable code |
| **Extension Functions** | Enhanced API without inheritance |
| **Coroutines Ready** | Modern async programming support |
| **Interoperability** | Seamless Java library compatibility |

## 🔄 Conversion Statistics

| Component | Java Files Converted | Kotlin Files Created |
|-----------|---------------------|---------------------|
| **app** | 40+ | 40+ |
| **terminal-emulator** | 15+ | 15+ |
| **terminal-view** | 10+ | 10+ |
| **termux-shared** | 80+ | 80+ |
| **Total** | **145+** | **145+** |

## 🚀 Features

All original Termux features are preserved:

- ✅ **Full Linux terminal** with touch/keyboard support
- ✅ **Package management** via apt (pkg)
- ✅ **Session management** with multiple terminal tabs
- ✅ **Customizable** extra keys row
- ✅ **Styling support** via Termux:Styling
- ✅ **Plugin ecosystem** (Termux:API, Termux:Boot, Termux:Widget, etc.)
- ✅ **Hardware keyboard** support with shortcuts
- ✅ **Background execution** via Termux:Tasker
- ✅ **URL handling** and file sharing

### 🆕 Kotlin-Exclusive Features

New features only available in the Kotlin version:

| Feature | Description |
|---------|-------------|
| 🎨 **Jetpack Compose UI** | Modern declarative UI for settings and dialogs |
| 🔍 **Command Palette** | VS Code-style fuzzy command search (Ctrl+Shift+P) |
| 📐 **Split Terminal** | Side-by-side or top/bottom terminal panes |
| 🔑 **SSH Manager** | Save and manage SSH connection profiles |
| 📜 **Command History** | Searchable command history with statistics |
| ⚡ **Kotlin Coroutines** | Efficient async operations with Flow |
| 💉 **Dependency Injection** | Hilt for clean architecture |
| 💾 **DataStore** | Modern preferences with reactive updates |
| 🎭 **Profile System** | Named profiles with theme, font, shell, and env vars |
| 🖌️ **Theme Gallery** | 10+ built-in themes with live preview |
| 💾 **Package Backup** | Full backup/restore of packages, repos, and dotfiles |
| 🩺 **Package Doctor** | Health checks with auto-repair suggestions |
| 🛠️ **termuxctl CLI** | Unified CLI for backup, doctor, and profile management |

### 🏗️ Modern Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       Compose UI Layer                       │
│      (Settings, Command Palette, SSH Manager, Dialogs)       │
├─────────────────────────────────────────────────────────────┤
│                  ViewModels + StateFlow                      │
├─────────────────────────────────────────────────────────────┤
│                      Repositories                            │
│   (Settings, Sessions, History, SSH Profiles, Permissions)   │
├─────────────────────────────────────────────────────────────┤
│                      Core Modules                            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐  │
│  │   core/api   │ │ core/logging │ │   core/permissions   │  │
│  │ Sealed Types │ │TermuxLogger  │ │  PermissionManager   │  │
│  │ Result<T,E>  │ │ File Logging │ │  Activity Result API │  │
│  └──────────────┘ └──────────────┘ └──────────────────────┘  │
│  ┌──────────────┐ ┌──────────────┐                           │
│  │core/terminal │ │ core/plugin  │                           │
│  │  EventBus    │ │  Plugin API  │                           │
│  │ Flow Events  │ │ Versioning   │                           │
│  └──────────────┘ └──────────────┘                           │
├─────────────────────────────────────────────────────────────┤
│              DataStore / Coroutines / Hilt DI                │
└─────────────────────────────────────────────────────────────┘
```

### Core Modules

| Module | Description |
|--------|-------------|
| `core/api` | Type-safe `Result<T,E>` and sealed error hierarchies |
| `core/logging` | Centralized logging with file output and Flow |
| `core/permissions` | Unified permission handling with coroutines |
| `core/terminal` | Flow-based event bus replacing callbacks |
| `core/plugin` | Stable plugin API with semantic versioning |
| `ui/settings` | Material 3 Compose settings with DataStore |
| `pkg/backup` | Package backup/restore manager |
| `pkg/doctor` | Package health diagnostics and auto-repair |

### 🎨 Built-in Themes

10 beautiful themes included out of the box:

| Theme | Author | Description |
|-------|--------|-------------|
| **Dark Steel** | Termux Kotlin | Signature dark theme with steel blue accents |
| **Molten Blue** | Termux Kotlin | GitHub-inspired dark theme |
| **Obsidian** | Termux Kotlin | VS Code-inspired dark theme |
| **Dracula** | Zeno Rocha | Popular dark theme |
| **Nord** | Arctic Ice Studio | Arctic north-bluish palette |
| **Solarized Dark** | Ethan Schoonover | Classic precision colors |
| **Solarized Light** | Ethan Schoonover | Light variant |
| **Gruvbox Dark** | morhetz | Retro groove palette |
| **Gruvbox Light** | morhetz | Light variant |
| **High Contrast** | Termux Kotlin | Maximum readability |

### 💾 Package Management

Advanced package management features that surpass standard Termux:

```bash
# Create a full backup
termuxctl backup create --type full

# Restore with dry-run preview
termuxctl backup restore backup.json --dry-run

# Run package health diagnostics
termuxctl pkg doctor

# Auto-repair issues
termuxctl pkg doctor --auto-repair
```

## 📥 Installation

### Download APK

Download the latest release APK from the [Releases](https://github.com/reapercanuk39/termux-kotlin-app/releases) page.

Choose the appropriate variant for your device:
- `arm64-v8a` - Modern 64-bit phones (most devices)
- `armeabi-v7a` - Older 32-bit phones
- `x86_64` - 64-bit emulators/ChromeOS
- `x86` - 32-bit emulators
- `universal` - Works on all (larger file size)

### Build from Source

See [Building](#building) section below.

## 🔨 Building

### Prerequisites

- **JDK 17** or higher
- **Android SDK** with Build Tools
- **Android NDK** (for native components)

### Build Commands

```bash
# Clone the repository
git clone https://github.com/reapercanuk39/termux-kotlin-app.git
cd termux-kotlin-app

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing)
./gradlew assembleRelease

# APKs will be in app/build/outputs/apk/
```

### Build Variants

| Variant | Description |
|---------|-------------|
| `debug` | Development build with debugging enabled |
| `release` | Production build (requires signing) |

## 🔗 Related Repositories

| Repository | Description |
|------------|-------------|
| [termux/termux-app](https://github.com/termux/termux-app) | 🏠 Original Termux app (Java) |
| [reapercanuk39/termux-app](https://github.com/reapercanuk39/termux-app) | 🍴 My fork of official repo |
| [termux/termux-packages](https://github.com/termux/termux-packages) | 📦 Package build scripts |
| [termux/termux-api](https://github.com/termux/termux-api) | 🔌 Android API access plugin |

## 🤝 Contributing

Contributions are welcome! This project follows the same contribution guidelines as the original Termux project.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable/function names
- Add KDoc comments for public APIs
- Prefer immutable (`val`) over mutable (`var`)

## 📋 Original Project

This is a Kotlin conversion of the official **Termux** project:

- **Original Repository**: [github.com/termux/termux-app](https://github.com/termux/termux-app)
- **Original Authors**: [Termux Developers](https://github.com/termux)
- **License**: [GPLv3](LICENSE.md)

All credit for the original implementation goes to the Termux team. This conversion aims to modernize the codebase while maintaining full compatibility.

## 📄 License

```
Termux Kotlin App - Android terminal emulator (Kotlin version)
Copyright (C) 2024 Termux Developers & Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```

---

<div align="center">

**Keywords**: `termux` `termux-app` `termux-kotlin` `android-terminal` `terminal-emulator` `linux-android` `kotlin-android` `android-app` `terminal` `shell` `bash` `linux` `android-terminal-emulator` `termux-android` `kotlin-conversion`

Made with ❤️ by [reapercanuk39](https://github.com/reapercanuk39)

⭐ **Star this repo** if you find it useful!

</div>
