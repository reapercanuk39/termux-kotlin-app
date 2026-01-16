# Custom Bootstrap Build for com.termux.kotlin

## Overview

This document describes how to build a custom Termux bootstrap with the correct package name (`com.termux.kotlin`) and prefix (`/data/data/com.termux.kotlin/files/usr`) baked directly into all binaries and scripts.

## Why Custom Bootstrap?

The upstream Termux bootstrap has ~322 ELF binaries with hardcoded paths to `/data/data/com.termux/files/usr`. These cannot be modified post-compilation without corruption. Building from source with the correct prefix solves this permanently.

## Prerequisites

- Docker installed and running
- ~50GB free disk space
- 8GB+ RAM recommended
- Several hours for initial build

## Repository Structure

```
/root/termux-packages/          # Cloned termux-packages repo
/root/termux-kotlin-bootstrap/  # Output directory for custom bootstrap
/root/termux-kotlin-app/        # Our app repository
```

---

## Step 1: Clone termux-packages (DONE)

```bash
cd /root
git clone --depth 1 https://github.com/termux/termux-packages.git
```

Location: `/root/termux-packages/`

## Step 2: Key Configuration File

The main configuration is in `/root/termux-packages/scripts/properties.sh`

**Key variables to change (line ~467):**
```bash
# Original:
TERMUX_APP__PACKAGE_NAME="com.termux"

# Change to:
TERMUX_APP__PACKAGE_NAME="com.termux.kotlin"
```

This single change propagates to all derived paths:
- `TERMUX_APP__DATA_DIR` → `/data/data/com.termux.kotlin`
- `TERMUX__ROOTFS` → `/data/data/com.termux.kotlin/files`
- `TERMUX__PREFIX` → `/data/data/com.termux.kotlin/files/usr`
- `TERMUX__HOME` → `/data/data/com.termux.kotlin/files/home`

## Step 3: Bootstrap Package List

From `scripts/build-bootstraps.sh` (lines 427-470):

**Essential packages:**
- apt
- bash
- bzip2
- coreutils
- dash
- diffutils
- findutils
- gawk
- grep
- gzip
- less
- procps
- psmisc
- sed
- tar
- termux-core
- termux-exec
- termux-keyring
- termux-tools
- util-linux

**Additional packages:**
- ed
- debianutils
- dos2unix
- inetutils
- lsof
- nano
- net-tools
- patch
- unzip

## Step 4: Build Using Docker

### Option A: Interactive Docker Session

```bash
cd /root/termux-packages

# Start Docker container (pulls image if needed)
./scripts/run-docker.sh

# Inside Docker container:
# First, modify properties.sh
sed -i 's/TERMUX_APP__PACKAGE_NAME="com.termux"/TERMUX_APP__PACKAGE_NAME="com.termux.kotlin"/' \
    /home/builder/termux-packages/scripts/properties.sh

# Build bootstrap for arm64 (most common)
./scripts/build-bootstraps.sh --architectures aarch64

# Build for all architectures
./scripts/build-bootstraps.sh
```

### Option B: One-liner Build (after modifying properties.sh)

```bash
cd /root/termux-packages

# Modify properties.sh first
sed -i 's/TERMUX_APP__PACKAGE_NAME="com.termux"/TERMUX_APP__PACKAGE_NAME="com.termux.kotlin"/' \
    scripts/properties.sh

# Run build in Docker
./scripts/run-docker.sh ./scripts/build-bootstraps.sh --architectures aarch64
```

## Step 5: Build Output

After successful build, bootstraps are at:
```
/root/termux-packages/bootstrap-aarch64.zip
/root/termux-packages/bootstrap-arm.zip
/root/termux-packages/bootstrap-i686.zip
/root/termux-packages/bootstrap-x86_64.zip
```

## Step 6: Integration with termux-kotlin-app

The bootstrap is embedded in the APK as a native library (it's actually a ZIP):

```bash
# Architecture mapping:
# aarch64 → arm64-v8a
# arm     → armeabi-v7a
# x86_64  → x86_64
# i686    → x86

# Copy to app
mkdir -p /root/termux-kotlin-app/app/src/main/jniLibs/arm64-v8a/
cp /root/termux-packages/bootstrap-aarch64.zip \
   /root/termux-kotlin-app/app/src/main/jniLibs/arm64-v8a/libtermux-bootstrap.so

mkdir -p /root/termux-kotlin-app/app/src/main/jniLibs/armeabi-v7a/
cp /root/termux-packages/bootstrap-arm.zip \
   /root/termux-kotlin-app/app/src/main/jniLibs/armeabi-v7a/libtermux-bootstrap.so

mkdir -p /root/termux-kotlin-app/app/src/main/jniLibs/x86_64/
cp /root/termux-packages/bootstrap-x86_64.zip \
   /root/termux-kotlin-app/app/src/main/jniLibs/x86_64/libtermux-bootstrap.so

mkdir -p /root/termux-kotlin-app/app/src/main/jniLibs/x86/
cp /root/termux-packages/bootstrap-i686.zip \
   /root/termux-kotlin-app/app/src/main/jniLibs/x86/libtermux-bootstrap.so
```

## Step 7: Simplify TermuxInstaller.kt

After building custom bootstrap, you can REMOVE these workarounds from TermuxInstaller.kt:
- `createDpkgWrapper()` - dpkg will have correct paths
- `createUpdateAlternativesWrapper()` - update-alternatives will have correct paths
- `createAptWrappers()` - apt will have correct paths
- Complex path fixing in `isTextFileNeedingPathFix()` - scripts will have correct paths

The extraction code can be simplified to:
1. Extract ZIP
2. Create symlinks
3. Set permissions

---

## Build Time Estimates

| Phase | Time |
|-------|------|
| Docker image pull | ~5-10 min |
| First package build (bash) | ~5-10 min |
| Full bootstrap build (single arch) | ~2-4 hours |
| Full bootstrap build (all 4 archs) | ~8-16 hours |

## Troubleshooting

### Docker Permission Issues
```bash
# Add user to docker group
sudo usermod -aG docker $USER
# Or use sudo
TERMUX_DOCKER_USE_SUDO=1 ./scripts/run-docker.sh
```

### Out of Disk Space
```bash
# Clean build artifacts
./clean.sh

# Remove Docker images
docker system prune -a
```

### Build Failures
```bash
# Check logs in
ls -la ~/.termux-build/_cache/

# Rebuild specific package
./scripts/run-docker.sh ./build-package.sh -a aarch64 <package-name>
```

---

## Quick Start Script

Create and run `/root/termux-kotlin-app/scripts/build-custom-bootstrap.sh`:

```bash
#!/bin/bash
set -e

TERMUX_PACKAGES_DIR="/root/termux-packages"
OUTPUT_DIR="/root/termux-kotlin-bootstrap"
APP_DIR="/root/termux-kotlin-app"

echo "=== Building Custom Bootstrap for com.termux.kotlin ==="

# Step 1: Modify package name
echo "[1/4] Patching properties.sh..."
sed -i 's/TERMUX_APP__PACKAGE_NAME="com.termux"/TERMUX_APP__PACKAGE_NAME="com.termux.kotlin"/' \
    "$TERMUX_PACKAGES_DIR/scripts/properties.sh"

# Verify patch
if grep -q 'TERMUX_APP__PACKAGE_NAME="com.termux.kotlin"' "$TERMUX_PACKAGES_DIR/scripts/properties.sh"; then
    echo "   ✓ Package name patched to com.termux.kotlin"
else
    echo "   ✗ Failed to patch package name"
    exit 1
fi

# Step 2: Build bootstrap
echo "[2/4] Building bootstrap (this takes 2-4 hours)..."
cd "$TERMUX_PACKAGES_DIR"
./scripts/run-docker.sh ./scripts/build-bootstraps.sh --architectures aarch64

# Step 3: Copy to output
echo "[3/4] Copying bootstrap..."
mkdir -p "$OUTPUT_DIR"
cp bootstrap-*.zip "$OUTPUT_DIR/"

# Step 4: Integrate with app
echo "[4/4] Integrating with app..."
mkdir -p "$APP_DIR/app/src/main/jniLibs/arm64-v8a/"
cp "$OUTPUT_DIR/bootstrap-aarch64.zip" \
   "$APP_DIR/app/src/main/jniLibs/arm64-v8a/libtermux-bootstrap.so"

echo ""
echo "=== Build Complete ==="
echo "Bootstrap: $OUTPUT_DIR/bootstrap-aarch64.zip"
echo "Integrated: $APP_DIR/app/src/main/jniLibs/arm64-v8a/libtermux-bootstrap.so"
```

---

## Build Status Tracking

| Step | Status | Notes |
|------|--------|-------|
| Clone termux-packages | ✅ Done | `/root/termux-packages/` |
| Analyze build system | ✅ Done | properties.sh contains TERMUX_APP__PACKAGE_NAME |
| Document process | ✅ Done | This file |
| Install Docker | ⏳ Pending | Required for build |
| Modify properties.sh | ⏳ Pending | Change com.termux → com.termux.kotlin |
| Build bootstrap | ⏳ Pending | ~2-4 hours per architecture |
| Test bootstrap | ⏳ Pending | |
| Integrate into app | ⏳ Pending | |
| Simplify TermuxInstaller.kt | ⏳ Pending | Remove workarounds |

---

## Session Handoff Notes

### Current State (2026-01-17)
- v1.0.28 released with apt wrappers (workaround approach)
- termux-packages cloned to `/root/termux-packages/`
- Build system analyzed and documented
- Custom bootstrap NOT yet built

### Next Steps for Future Session
1. Check if Docker is installed: `docker --version`
2. If not, install Docker: `apt install docker.io`
3. Run the build script or follow manual steps above
4. After build completes, integrate bootstrap into app
5. Simplify TermuxInstaller.kt by removing workarounds
6. Test the new build

### Key Commands to Resume
```bash
# Check current state
ls -la /root/termux-packages/
cat /root/termux-kotlin-app/docs/CUSTOM_BOOTSTRAP_BUILD.md

# Check if Docker is available
docker --version

# Check if properties.sh is already patched
grep TERMUX_APP__PACKAGE_NAME /root/termux-packages/scripts/properties.sh

# Start build
cd /root/termux-packages
./scripts/run-docker.sh ./scripts/build-bootstraps.sh --architectures aarch64
```

---

## References

- https://github.com/termux/termux-packages
- https://github.com/termux/termux-packages/wiki/Building-packages
- https://github.com/termux/termux-packages/blob/master/scripts/build-bootstraps.sh
- https://github.com/termux/termux-packages/blob/master/scripts/properties.sh
