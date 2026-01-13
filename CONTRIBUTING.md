# Contributing to Termux Kotlin App

Thank you for your interest in contributing! This document explains how to set up development and the CI/CD pipeline.

## 🔧 Development Setup

### Prerequisites
- **JDK 17** or higher
- **Android SDK** with Build Tools
- **Android NDK** (for native components)

### Building Locally

```bash
# Clone the repository
git clone https://github.com/reapercanuk39/termux-kotlin-app.git
cd termux-kotlin-app

# Build debug APK
./gradlew assembleDebug

# Run tests
./gradlew testDebugUnitTest

# Run lint
./gradlew lint

# Run Detekt (Kotlin static analysis)
./gradlew detekt
```

## 🔄 CI/CD Pipeline

### Continuous Integration (PRs & Pushes)

Every push and PR triggers the CI pipeline (`.github/workflows/ci.yml`):

| Job | Description |
|-----|-------------|
| **Lint** | Android Lint checks |
| **Detekt** | Kotlin static analysis |
| **Unit Tests** | Run all unit tests |
| **Build Debug** | Build debug APKs (after lint & tests pass) |

PRs receive automatic comments with build status and APK download links.

### Release Builds (Tags)

Creating a version tag triggers the release pipeline (`.github/workflows/release.yml`):

```bash
# Create a release
git tag v1.1.0
git push origin v1.1.0
```

This will:
1. Run lint and tests
2. Build signed release APKs
3. Create a GitHub Release with all APKs attached

### Tag Naming Convention

| Tag Pattern | Release Type |
|-------------|--------------|
| `v1.0.0` | Stable release |
| `v1.0.0-beta.1` | Beta (pre-release) |
| `v1.0.0-alpha.1` | Alpha (pre-release) |
| `v1.0.0-rc.1` | Release candidate (pre-release) |

## 🔐 Setting Up Release Signing (Maintainers)

To enable signed release builds, add these secrets to your repository:

### Required Secrets

1. **`KEYSTORE_BASE64`** - Base64 encoded keystore file
   ```bash
   base64 -w 0 your-keystore.keystore > keystore_base64.txt
   ```

2. **`KEYSTORE_PASSWORD`** - Keystore password

3. **`KEY_ALIAS`** - Key alias name

4. **`KEY_PASSWORD`** - Key password

### Adding Secrets

1. Go to Repository → Settings → Secrets and variables → Actions
2. Click "New repository secret"
3. Add each secret listed above

## 📝 Code Style

### Kotlin Guidelines

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable/function names
- Prefer `val` over `var` (immutability)
- Use nullable types appropriately with safe calls (`?.`) or `!!` only when certain
- Add KDoc comments for public APIs

### Detekt Rules

We use Detekt for static analysis. Key rules:
- Max line length: 200 characters
- Max method length: 100 lines
- Max class size: 600 lines
- Nested block depth: 6 levels

Run locally before pushing:
```bash
./gradlew detekt
```

## 🐛 Reporting Issues

- Check existing issues first
- Include device info, Android version, and steps to reproduce
- Attach logs if possible (`adb logcat | grep -i termux`)

## 📥 Pull Request Process

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run tests and lint locally
5. Commit with clear messages
6. Push and open a PR
7. Wait for CI to pass
8. Request review

## 📜 License

By contributing, you agree that your contributions will be licensed under the GPLv3 license.
