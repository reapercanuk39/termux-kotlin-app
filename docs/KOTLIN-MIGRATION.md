# Termux App - Kotlin Migration Guide

**Last Updated**: 2026-01-13
**Migration Status**: Phase 3 In Progress (120 Kotlin files, 56 Java files - 68%)
**Build Status**: ✅ Build passing

---

## Overview

This document tracks the Kotlin migration of the termux-app Android application.

---

## Android 12+ Compatibility Improvements

### Changes Made

#### 1. Foreground Service Type (Android 14+)
**File**: `app/src/main/AndroidManifest.xml`
- Added `android:foregroundServiceType="specialUse"` to TermuxService
- Added property explaining the special use case
- Added `FOREGROUND_SERVICE_SPECIAL_USE` permission

**File**: `app/src/main/java/com/termux/app/TermuxService.java`
- Updated `runStartForeground()` to pass service type on Android 14+

#### 2. Notification Permission (Android 13+)
**File**: `app/src/main/AndroidManifest.xml`
- Added `POST_NOTIFICATIONS` permission

**File**: `termux-shared/.../PermissionUtils.java`
- Added `checkNotificationPermission()` method
- Added `requestNotificationPermission()` method
- Added `REQUEST_POST_NOTIFICATIONS_PERMISSION` constant

#### 3. Phantom Process Killer Utilities
**File**: `termux-shared/.../PhantomProcessUtils.java`
- Added `isPhantomProcessKillingRelevant()` - checks if Android 12+
- Added `getPhantomProcessInfoMessage()` - user-friendly workaround info

### Phantom Process Issue (GitHub #2366)

Android 12+ kills processes when total phantom processes exceed 32 (all apps combined).

**Workarounds for users:**
```bash
# Disable phantom process monitoring (requires ADB)
adb shell "settings put global settings_enable_monitor_phantom_procs false"

# Increase max phantom processes (requires ADB)  
adb shell "device_config put activity_manager max_phantom_processes 2147483647"
```

---

## Quick Start for Future Sessions

### Environment Setup

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/root/android-sdk
cd /root/termux-app

# Verify build
./gradlew compileDebugJavaWithJavac --no-daemon

# Run tests
./gradlew testDebugUnitTest --no-daemon

# Build APK
./gradlew assembleDebug --no-daemon
```

### Current State (2026-01-13)

| Module | Kotlin Files | Java Files | % Complete |
|--------|--------------|------------|------------|
| app | 21 | 14 | 60% |
| terminal-emulator | 5 | 9 | 36% |
| terminal-view | 4 | 4 | 50% |
| termux-shared | 90 | 29 | 76% |
| **Total** | **120** | **56** | **68%** |

---

## Build Configuration

### Root build.gradle
Kotlin plugin added:
```groovy
buildscript {
    ext.kotlin_version = '1.9.22'
    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}
```

### Module build.gradle Pattern
Each module needs:
```groovy
apply plugin: 'org.jetbrains.kotlin.android'

android {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
}
```

---

## Migration Patterns

### 1. Data Classes
Java:
```java
public class KeyboardShortcut {
    public final int codePoint;
    public final int shortcutAction;
    public KeyboardShortcut(int codePoint, int shortcutAction) {
        this.codePoint = codePoint;
        this.shortcutAction = shortcutAction;
    }
}
```

Kotlin:
```kotlin
data class KeyboardShortcut(
    @JvmField val codePoint: Int,
    @JvmField val shortcutAction: Int
)
```

**Key Points:**
- Use `@JvmField` for fields accessed directly from Java
- Data class auto-generates `equals()`, `hashCode()`, `toString()`, `copy()`

### 2. Enums
Java:
```java
public enum FileType {
    REGULAR("regular", 1),
    DIRECTORY("directory", 2);
    
    private final String name;
    private final int value;
    
    FileType(String name, int value) { ... }
    public String getName() { return name; }
}
```

Kotlin:
```kotlin
enum class FileType(
    @JvmField val typeName: String,
    @JvmField val value: Int
) {
    REGULAR("regular", 1),
    DIRECTORY("directory", 2);

    // For Java interop if getName() is called
    fun getName(): String = typeName
}
```

### 3. Singleton/Object Classes
Java:
```java
public class ShellUtils {
    public static int getPid(Process p) { ... }
}
```

Kotlin:
```kotlin
object ShellUtils {
    @JvmStatic
    fun getPid(p: Process): Int { ... }
}
```

**Key Points:**
- Use `@JvmStatic` for methods called from Java as `ShellUtils.getPid()`
- Without `@JvmStatic`, Java would need `ShellUtils.INSTANCE.getPid()`

### 4. Interfaces
Java:
```java
public interface TerminalSessionClient {
    void onTextChanged(@NonNull TerminalSession session);
}
```

Kotlin:
```kotlin
interface TerminalSessionClient {
    fun onTextChanged(changedSession: TerminalSession)
}
```

### 5. Abstract Classes
Java:
```java
public abstract class TerminalOutput {
    public final void write(String data) { ... }
    public abstract void write(byte[] data, int offset, int count);
}
```

Kotlin:
```kotlin
abstract class TerminalOutput {
    fun write(data: String?) { ... }
    abstract fun write(data: ByteArray, offset: Int, count: Int)
}
```

### 6. Static Constants
Java:
```java
public class UriScheme {
    public static final String SCHEME_FILE = "file";
}
```

Kotlin:
```kotlin
object UriScheme {
    const val SCHEME_FILE: String = "file"
}
```

---

## Known Issues & Solutions

### Issue 1: `val` vs `var` for Fields Assigned from Java

**Problem**: Java code assigns to a field:
```java
state.buttons = new ArrayList<>();
```

**Solution**: Use `var` instead of `val`:
```kotlin
@JvmField
var buttons: MutableList<MaterialButton> = ArrayList()
```

### Issue 2: Parameter Name Mismatch Warning

**Problem**: Warning about parameter names not matching supertype:
```
The corresponding parameter in the supertype is named 'session'
```

**Solution**: Match parameter names with supertype:
```kotlin
// Wrong
override fun setTerminalShellPid(terminalSession: TerminalSession, pid: Int)

// Correct  
override fun setTerminalShellPid(session: TerminalSession, pid: Int)
```

### Issue 3: Nullable Parameters for Java Interop

**Problem**: Java code may pass null to parameters.

**Solution**: Use nullable types:
```kotlin
fun logError(tag: String?, message: String?)
```

### Issue 4: Property Access in Kotlin

**Problem**: Java getters become Kotlin properties.

**Solution**: Use property syntax:
```java
// Java calls
terminalEmulator.getScreen()
```
```kotlin
// Kotlin calls
terminalEmulator.screen
```

---

## Files Migrated

### app module (12 files)
- `models/UserAction.kt`
- `terminal/io/KeyboardShortcut.kt`
- `terminal/io/FullScreenWorkAround.kt`
- `terminal/TermuxTerminalSessionServiceClient.kt`
- `activities/HelpActivity.kt`
- `fragments/settings/TermuxPreferencesFragment.kt`
- `fragments/settings/TermuxAPIPreferencesFragment.kt`
- `fragments/settings/TermuxFloatPreferencesFragment.kt`
- `fragments/settings/TermuxTaskerPreferencesFragment.kt`
- `fragments/settings/TermuxWidgetPreferencesFragment.kt`
- `fragments/settings/termux/TerminalViewPreferencesFragment.kt`
- `fragments/settings/termux/TerminalIOPreferencesFragment.kt`

### terminal-emulator module (4 files)
- `JNI.kt`
- `Logger.kt`
- `TerminalOutput.kt`
- `TerminalSessionClient.kt`

### terminal-view module (3 files)
- `TerminalViewClient.kt`
- `textselection/CursorController.kt`
- `support/PopupWindowCompatGingerbread.kt`

### termux-shared module (11 files)
- `android/ProcessUtils.kt`
- `file/filesystem/FileType.kt`
- `net/uri/UriScheme.kt`
- `settings/properties/SharedPropertiesParser.kt`
- `shell/ShellUtils.kt`
- `shell/command/environment/IShellEnvironment.kt`
- `shell/command/environment/ShellEnvironmentVariable.kt`
- `termux/extrakeys/SpecialButton.kt`
- `termux/extrakeys/SpecialButtonState.kt`
- `termux/models/UserAction.kt`
- `termux/theme/TermuxThemeUtils.kt`

---

## Priority Files for Next Migration

### High Priority (small, frequently used)
1. `termux-shared/.../settings/preferences/*.java` - Preference classes
2. `termux-shared/.../shell/command/*.java` - Shell utilities
3. `app/.../api/*.java` - API handlers

### Medium Priority (medium complexity)
1. `terminal-emulator/TerminalSession.java` (166 lines)
2. `terminal-view/TextSelectionCursorController.java`
3. `app/TermuxInstaller.java`

### Lower Priority (large, complex)
1. `app/TermuxActivity.java` (1013 lines)
2. `app/TermuxService.java` (959 lines)
3. `termux-shared/TermuxConstants.java` (1338 lines)
4. `terminal-emulator/TerminalEmulator.java` (2617 lines)

---

## Testing Checklist

Before committing migration changes:

- [ ] `./gradlew compileDebugJavaWithJavac` passes
- [ ] `./gradlew testDebugUnitTest` passes  
- [ ] No new warnings about Kotlin/Java interop
- [ ] All `@JvmStatic` and `@JvmField` annotations added where needed
- [ ] Parameter names match supertypes

---

## Build APK

```bash
# Debug APK
./gradlew assembleDebug --no-daemon

# Output location
ls app/build/outputs/apk/debug/
```

---

## Resources

- [Kotlin-Java Interop Guide](https://kotlinlang.org/docs/java-interop.html)
- [Kotlin for Android Developers](https://developer.android.com/kotlin)
- [Termux App Wiki](https://github.com/termux/termux-app/wiki)

---

**Next Steps**: Continue migrating remaining 56 Java files, prioritizing small utility classes first, then moving to larger activity/service classes.
