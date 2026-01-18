# AI Session Guide for Termux Kotlin App

> **CRITICAL FOR ALL AI SESSIONS**: Read this document first. Always use deep/extended thinking (UltraThink) for complex problems. Document everything you do and discover.

---

## 📚 Required Reading - START EVERY SESSION HERE

**At the start of each session, read ALL of these files to understand the full context:**

### Termux Kotlin App (Primary Project)
| File | Purpose |
|------|---------|
| `/root/termux-kotlin-app/README.md` | Project overview, features, build commands |
| `/root/termux-kotlin-app/ARCHITECTURE.md` | Module structure, data flow, environment variables |
| `/root/termux-kotlin-app/CHANGELOG.md` | Version history, recent fixes and features |
| `/root/termux-kotlin-app/ROADMAP.md` | Development priorities and progress tracking |
| `/root/termux-kotlin-app/CONTRIBUTING.md` | Build commands, CI/CD, code style rules |
| `/root/termux-kotlin-app/docs/AI_SESSION_GUIDE.md` | This file - session rules and context |
| `/root/termux-kotlin-app/docs/CUSTOM_BOOTSTRAP_BUILD.md` | Docker package rebuild instructions |
| `/root/termux-kotlin-app/docs/plugin-sdk/README.md` | Plugin API v1.0.0 documentation |
| `/root/termux-kotlin-app/error.md` | Troubleshooting history and solutions |

### Related Projects
| File | Purpose |
|------|---------|
| `/root/termux-kotlin-api/README.md` | Termux API Kotlin fork |
| `/root/obsidian-build/README.md` | Obsidian OS security-hardened Linux |
| `/root/obsidian-build/docs/USB-SSD-GUIDE.md` | USB installation guide |
| `/root/obsidian-build/docs/CHANGELOG.md` | Obsidian version history |
| `/root/MediaWriter/README.md` | Obsidian Media Writer tool |
| `/root/termux-packages/README.md` | Upstream Termux package scripts |
| `/root/iso-optimization-tools.md` | ISO build optimization tips |
| `/root/recommended-tools.md` | Tool installation status |

### Quick Read Command
```bash
# Read all essential docs in one command:
cat /root/termux-kotlin-app/README.md /root/termux-kotlin-app/ARCHITECTURE.md /root/termux-kotlin-app/CHANGELOG.md /root/termux-kotlin-app/docs/AI_SESSION_GUIDE.md
```

---

## 🧠 Session Rules

### 1. Always Use UltraThink
- For **any non-trivial problem**, use extended/deep thinking mode
- Don't make assumptions - investigate thoroughly
- Check hardcoded paths in binaries with `strings` command
- Trace issues to root cause before proposing solutions

### 2. Always Document
- Update this guide with new discoveries
- Add to CHANGELOG.md for code changes
- Update error.md with troubleshooting history
- Create session notes in docs/ if needed

### 3. Ask Before Acting
- Get user approval before making significant changes
- Present options and recommendations
- Explain trade-offs clearly

---

## 📦 Project Overview

**Termux Kotlin App** is a complete Kotlin conversion of the official Termux Android terminal emulator.

### Key Difference from Original Termux
| Aspect | Original Termux | Termux Kotlin |
|--------|-----------------|---------------|
| Package name | `com.termux` | `com.termux.kotlin` |
| Data path | `/data/data/com.termux/files/usr` | `/data/data/com.termux.kotlin/files/usr` |
| Language | Java | 100% Kotlin |

### The Path Problem
Many upstream Termux packages have **hardcoded paths** compiled into ELF binaries:
```
/data/data/com.termux/files/usr/...
```

These paths **cannot be changed** via:
- Environment variables
- Wrapper scripts
- Symlinks (in many cases)

**The ONLY solution is rebuilding packages from source** with:
```bash
TERMUX_APP__PACKAGE_NAME="com.termux.kotlin"
```

---

## 🔧 Build Environment

### Docker Setup (Already Available)
```bash
# Location: Docker container with termux-packages
docker run -d --name termux-package-builder \
    -v ~/termux-packages:/home/builder/termux-packages \
    termux/package-builder tail -f /dev/null

# Configure for com.termux.kotlin
docker exec termux-package-builder bash -c '
cd /home/builder/termux-packages
sed -i "s/TERMUX_APP__PACKAGE_NAME=\"com.termux\"/TERMUX_APP__PACKAGE_NAME=\"com.termux.kotlin\"/" scripts/properties.sh
'
```

### Building Packages
```bash
# Build a package for all architectures
for arch in aarch64 arm x86_64 i686; do
    docker exec termux-package-builder bash -c "
        cd /home/builder/termux-packages
        ./build-package.sh -a $arch <package-name>
    "
done
```

### Package Output Location
Built packages appear in:
```
/home/builder/termux-packages/output/
```

### Integrating into Bootstrap
1. Extract .deb packages
2. Copy files to bootstrap directory
3. Update bootstrap zip files in `app/src/main/cpp/`
4. Add to custom repo in `repo/`

---

## 📊 Package Status

### ✅ v1.0.40 - Full Native Path Support Complete!

As of v1.0.40, **ALL 66 packages** with hardcoded paths have been rebuilt with native `com.termux.kotlin` paths. This includes **716+ binaries** across all 4 architectures.

#### Complete Rebuilt Package List (66 packages):

**Core System:**
- apt, bash, coreutils, curl, dash, debianutils, dpkg, grep, sed, tar, gzip, xz-utils, zstd, bzip2

**Utilities:**
- diffutils, dos2unix, ed, findutils, gawk, less, lsof, nano, patch, procps, psmisc, unzip, util-linux, xxhash

**Network:**
- inetutils, net-tools, openssl, libcurl, libssh2, libgnutls, libnghttp2, libnghttp3, libunbound

**Compression:**
- liblz4, liblzma, libbz2, zlib

**Libraries:**
- libacl, libandroid-glob, libandroid-posix-semaphore, libandroid-selinux, libandroid-support
- libassuan, libcap-ng, libevent, libgcrypt, libgmp, libgpg-error
- libiconv, libidn2, libmd, libmpfr, libnettle, libnpth
- libsmartcols, libtirpc, libunistring, ncurses, pcre2, readline

**Termux-specific:**
- termux-am-socket, termux-exec, termux-tools, termux-api

### ❌ Need Rebuilding (Have Hardcoded com.termux Paths)
| Package | Hardcoded Path | Impact |
|---------|----------------|--------|
| *(None - all packages rebuilt!)* | - | - |

### How to Check for Hardcoded Paths
```bash
# Check a library in the bootstrap
cd app/src/main/cpp
unzip -p bootstrap-aarch64.zip lib/libgnutls.so | strings | grep "com.termux"

# Check all libraries
unzip -l bootstrap-aarch64.zip | grep "\.so" | awk '{print $4}' | while read lib; do
  result=$(unzip -p bootstrap-aarch64.zip "$lib" 2>/dev/null | strings 2>/dev/null | grep "com\.termux/files")
  if [ -n "$result" ]; then
    echo "=== $lib ===" 
    echo "$result" | head -5
  fi
done
```

---

## 🐛 Known Issues & Solutions

### Issue: "Certificate verification failed" / "No system certificates available"
**Symptom:**
```
W: https://mirrors.../InRelease: No system certificates available. Try installing ca-certificates.
Certificate verification failed: The certificate is NOT trusted.
```

**Root Cause:** `libgnutls.so` has hardcoded path to `/data/data/com.termux/files/usr/etc/tls/cert.pem`

**Solution:** Rebuild `gnutls` package with `TERMUX_APP__PACKAGE_NAME="com.termux.kotlin"`

**Note:** GnuTLS does NOT support `SSL_CERT_FILE` environment variable (unlike OpenSSL)

### Issue: "Error #12" / APT methods error
**Symptom:** `pkg update` fails with method-related errors

**Root Cause:** `libapt-pkg.so` has hardcoded APT methods path

**Solution:** Already fixed - apt is rebuilt with native paths

### Issue: `clear` command fails / "terminals database is inaccessible"
**Symptom:** Terminal commands like `clear`, `tput` fail

**Root Cause:** Missing `TERMINFO` environment variable

**Solution:** Already fixed in v1.0.38 - `TERMINFO` is set in TermuxShellEnvironment.kt

---

## 📁 Key Files

### Environment Variables
```
termux-shared/src/main/kotlin/com/termux/shared/termux/shell/command/environment/TermuxShellEnvironment.kt
```
Sets: HOME, PREFIX, PATH, LD_LIBRARY_PATH, TMPDIR, TERMINFO, DPKG_ADMINDIR, DPKG_DATADIR, SSL_CERT_FILE, CURL_CA_BUNDLE

### Bootstrap Installation
```
app/src/main/kotlin/com/termux/app/TermuxInstaller.kt
```
Handles: Bootstrap extraction, symlink creation, wrapper scripts, path fixing

### Bootstrap Files
```
app/src/main/cpp/bootstrap-aarch64.zip
app/src/main/cpp/bootstrap-arm.zip
app/src/main/cpp/bootstrap-x86_64.zip
app/src/main/cpp/bootstrap-i686.zip
```

### Custom Package Repository
```
repo/
├── aarch64/     # ARM64 packages
├── arm/         # ARMv7 packages
├── x86_64/      # Intel/AMD 64-bit packages
├── i686/        # Intel/AMD 32-bit packages
├── all/         # Architecture-independent packages
└── Release      # Repository metadata
```

---

## 🔄 Typical Workflow for Fixing Hardcoded Paths

1. **Identify the issue** - User reports error
2. **Trace to root cause** - Use `strings` to find hardcoded paths in binaries
3. **Check if package is already rebuilt** - Look in `repo/`
4. **Rebuild package** - Use Docker termux-packages builder
5. **Extract and integrate** - Add to bootstrap zips and repo
6. **Update documentation** - CHANGELOG, this guide, error.md
7. **Test** - Build APK, install, verify fix

---

## 📝 Documentation Files

| File | Purpose |
|------|---------|
| `README.md` | Public-facing project documentation |
| `ARCHITECTURE.md` | Technical architecture details |
| `CHANGELOG.md` | Version history and changes |
| `ROADMAP.md` | Development priorities and progress |
| `CONTRIBUTING.md` | How to contribute |
| `error.md` | Troubleshooting history and solutions |
| `docs/CUSTOM_BOOTSTRAP_BUILD.md` | Package rebuild instructions |
| `docs/AI_SESSION_GUIDE.md` | **This file** - AI session continuity |

---

## 🚨 Important Reminders

1. **Binary paths cannot be environment-overridden** - Must rebuild from source
2. **GnuTLS ≠ OpenSSL** - Different cert path mechanisms, no env var override
3. **LD_LIBRARY_PATH overrides RUNPATH** - This is why most libs work despite wrong RUNPATH
4. **Test on real device** - Emulators may behave differently
5. **All 4 architectures** - aarch64, arm, x86_64, i686 must be updated together

---

## 📅 Session History

### 2026-01-17: SSL Certificate Issue (RESOLVED)
- **Problem:** `pkg update` fails with certificate verification error
- **Discovery:** libgnutls.so has hardcoded `/data/data/com.termux/files/usr/etc/tls/cert.pem`
- **Solution:** Rebuilt libgnutls, libcurl, libgpg-error with native com.termux.kotlin paths
- **Status:** ✅ Fixed in v1.0.39

### 2026-01-17: Upstream Package Compatibility (RESOLVED)
- **Problem:** `pkg install python` fails with "Permission denied" on `./data/data/com.termux`
- **Discovery:** Upstream .deb packages contain files with absolute paths inside archives
- **Solution:** Enhanced dpkg wrapper to rewrite package paths on-the-fly during installation
- **Status:** ✅ Fixed in v1.0.42

---

## 🧠 Quick Reference - Key Facts

**Commit these to memory at session start:**

### Build Commands
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew testDebugUnitTest      # Run tests
./gradlew detekt                 # Kotlin static analysis
./gradlew lint                   # Android lint
```

### Package Identity
- **Package Name:** `com.termux.kotlin`
- **Data Path:** `/data/data/com.termux.kotlin/files/usr`
- **Can coexist with original Termux**

### Critical Environment Variables (TermuxShellEnvironment.kt)
| Variable | Value |
|----------|-------|
| `HOME` | `/data/data/com.termux.kotlin/files/home` |
| `PREFIX` | `/data/data/com.termux.kotlin/files/usr` |
| `LD_LIBRARY_PATH` | `$PREFIX/lib` (overrides RUNPATH!) |
| `TERMINFO` | `$PREFIX/share/terminfo` |
| `SSL_CERT_FILE` | `$PREFIX/etc/tls/cert.pem` |
| `DPKG_ADMINDIR` | `$PREFIX/var/lib/dpkg` |

### Architecture Stack
```
Compose UI → ViewModels + StateFlow → Repositories → DataStore/Room
Core: Hilt DI, Coroutines, Flow, Sealed Result<T,E> types
Modules: app, terminal-emulator, terminal-view, termux-shared
```

### Docker Package Rebuild
```bash
docker exec termux-package-builder bash -c '
  cd /home/builder/termux-packages
  sed -i "s/com.termux/com.termux.kotlin/" scripts/properties.sh
  ./build-package.sh -a aarch64 <package-name>
'
```

### Code Style (Detekt enforced)
- Max 200 chars/line, 100 lines/method, 600 lines/class
- Prefer `val` over `var`, use `?.` safe calls
- Add KDoc for public APIs
- Follow Kotlin coding conventions

### GitHub
- **Owner:** `reapercanuk39`
- **Main Repos:** termux-kotlin-app, termux-kotlin-api, Obsidian, MediaWriter

### Obsidian OS
- **Default Login:** `obsidian` / `toor`
- **Build:** `./scripts/rebuild-iso.sh`
- **Faster compression:** Use `zstd` instead of `xz` (3-4x faster)

---

*Last updated: 2026-01-18*
