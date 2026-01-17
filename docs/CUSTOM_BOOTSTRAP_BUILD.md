# Custom Bootstrap Build for com.termux.kotlin

## Overview

This document describes how to build a custom Termux bootstrap with paths modified for `com.termux.kotlin`.

## Approach Used: Post-Processing

Instead of building from source (which takes 8+ hours and has dependency issues), we use a faster approach:

1. **Download prebuilt bootstrap** from Termux repo using `generate-bootstraps.sh`
2. **Post-process** the bootstrap to replace `/data/data/com.termux` → `/data/data/com.termux.kotlin`
3. **Keep ELF binaries unchanged** - wrappers in TermuxInstaller.kt handle their hardcoded paths

## Quick Start

```bash
# 1. Generate bootstrap from Termux repo
cd /root/termux-packages
./scripts/generate-bootstraps.sh --architectures x86_64

# 2. Post-process to replace paths
cd /root/termux-kotlin-bootstrap
./process-bootstrap-v3.sh \
    /root/termux-packages/bootstrap-x86_64.zip \
    /root/termux-kotlin-bootstrap/bootstrap-x86_64-kotlin.zip

# 3. Copy to app
cp bootstrap-x86_64-kotlin.zip \
   /root/termux-kotlin-app/app/src/main/jniLibs/x86_64/libtermux-bootstrap.so
```

## Files Modified by Post-Processor

The post-processor modifies ~260 text files:
- Scripts in `bin/` (pkg, login, termux-*, etc.)
- Configs in `etc/` (bash.bashrc, profile, etc.)
- PKG-CONFIG files in `lib/pkgconfig/*.pc`
- dpkg info files in `var/lib/dpkg/info/`
- SYMLINKS.txt

## Files NOT Modified (ELF Binaries)

ELF binaries (~320 files) are NOT modified to avoid corruption. These include:
- `bash`, `dash`, `grep`, `sed`, etc.
- `apt`, `apt-get`, `dpkg`, `update-alternatives`
- All shared libraries (*.so)

The app's `TermuxInstaller.kt` creates wrappers for binaries with config path issues:
- `createDpkgWrapper()` - handles dpkg config paths
- `createUpdateAlternativesWrapper()` - handles --altdir, --admindir, --log
- `createAptWrappers()` - handles apt/apt-get/etc Dir:: options

## Building for All Architectures

```bash
# Generate for all archs
cd /root/termux-packages
./scripts/generate-bootstraps.sh

# Post-process each
cd /root/termux-kotlin-bootstrap
for arch in aarch64 arm i686 x86_64; do
    ./process-bootstrap-v3.sh \
        /root/termux-packages/bootstrap-${arch}.zip \
        bootstrap-${arch}-kotlin.zip
done

# Copy to app (architecture mapping)
cp bootstrap-aarch64-kotlin.zip /root/termux-kotlin-app/app/src/main/jniLibs/arm64-v8a/libtermux-bootstrap.so
cp bootstrap-arm-kotlin.zip /root/termux-kotlin-app/app/src/main/jniLibs/armeabi-v7a/libtermux-bootstrap.so
cp bootstrap-x86_64-kotlin.zip /root/termux-kotlin-app/app/src/main/jniLibs/x86_64/libtermux-bootstrap.so
cp bootstrap-i686-kotlin.zip /root/termux-kotlin-app/app/src/main/jniLibs/x86/libtermux-bootstrap.so
```

## Repository Structure

```
/root/termux-packages/           # Cloned termux-packages repo
/root/termux-kotlin-bootstrap/   # Post-processing scripts and output
  ├── process-bootstrap-v3.sh    # The post-processor script
  └── bootstrap-*-kotlin.zip     # Processed bootstraps

/root/termux-kotlin-app/         # Our app repository
  └── app/src/main/jniLibs/
      ├── arm64-v8a/libtermux-bootstrap.so
      ├── armeabi-v7a/libtermux-bootstrap.so
      ├── x86_64/libtermux-bootstrap.so
      └── x86/libtermux-bootstrap.so
```

## Build Status

| Step | Status | Notes |
|------|--------|-------|
| Clone termux-packages | ✅ Done | `/root/termux-packages/` |
| Generate bootstrap | ✅ Done | x86_64 for emulator testing |
| Post-process bootstrap | ✅ Done | 260 text files modified |
| Integrate into app | ✅ Done | x86_64 copied to jniLibs |
| Generate all archs | ⏳ Pending | aarch64, arm, i686 remaining |
| Test on emulator | ⏳ Pending | |
| Test on real device | ⏳ Pending | |

## Alternative: Building from Source

If you need binaries with native paths (no wrappers), you must build from source:

1. Modify `TERMUX_APP__PACKAGE_NAME` in `scripts/properties.sh`
2. Run `./clean.sh` to clear cached builds
3. Run `./scripts/build-bootstraps.sh` (takes 8+ hours)

**Note:** This approach has issues with dependency resolution when package name differs.

---

## Session History

### 2026-01-17: Initial Implementation
- Installed Docker
- Tried build-bootstraps.sh with modified package name (failed - dependency issues)
- Switched to generate-bootstraps.sh + post-processing approach
- Successfully created x86_64 bootstrap with correct paths
- Integrated into app

---

## Building APT from Source (Native Paths)

### Why Build from Source?

ELF binaries (apt, dpkg, update-alternatives) have paths compiled into them:
- Cannot modify ELF binaries without corruption
- Wrapper scripts work but are a workaround
- Native paths = cleaner, more reliable solution

### Setup

1. **Configure package name:**
```bash
cd /root/termux-packages
sed -i 's/TERMUX_APP__PACKAGE_NAME="com.termux"/TERMUX_APP__PACKAGE_NAME="com.termux.kotlin"/' scripts/properties.sh
```

2. **Start Docker container:**
```bash
docker run -it --name termux-package-builder \
    -v /root/termux-packages:/home/builder/termux-packages \
    ghcr.io/termux/package-builder bash
```

3. **Build apt:**
```bash
cd /home/builder/termux-packages
./build-package.sh -a x86_64 apt
```

### Build Output

Built packages go to `/root/termux-packages/output/`:
- `apt_*.deb`
- `libapt-pkg_*.deb` (if separate)

### Extracting Binaries

```bash
cd /root/termux-packages/output
ar x apt_*.deb
tar xf data.tar.xz

# Binaries are in:
# ./data/data/com.termux.kotlin/files/usr/bin/apt*
# ./data/data/com.termux.kotlin/files/usr/lib/libapt-pkg.so*
```

### Integration

After building, replace binaries in bootstrap:
1. Unzip bootstrap
2. Replace apt binaries and libapt-pkg.so
3. Re-zip bootstrap
4. Copy to app/src/main/cpp/

### Current Build Status (2026-01-17)

- **Container:** Running (`termux-package-builder`)
- **Build:** In progress (building apt for x86_64)
- **Dependencies:** Building (libxcb, gnutls, etc.)
- **Estimated time:** Several hours

### Monitoring Build

```bash
# Watch build output
docker exec termux-package-builder tail -f /home/builder/termux-packages/build.log

# Check if apt is built
ls /root/termux-packages/output/apt_*.deb
```
