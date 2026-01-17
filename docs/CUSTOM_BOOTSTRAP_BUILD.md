# Custom Bootstrap Build for com.termux.kotlin

## Overview

This document describes how to build a fully native Termux bootstrap with `com.termux.kotlin` paths compiled directly into all binaries.

## Current Approach: Build from Source (Recommended)

The recommended approach builds all critical packages from source with native paths compiled in:

### Prerequisites

- Docker installed
- termux-packages repository cloned

### Build Steps

```bash
# 1. Start Docker container
docker run -d --name termux-package-builder \
    -v ~/termux-packages:/home/builder/termux-packages \
    termux/package-builder tail -f /dev/null

# 2. Configure for com.termux.kotlin
docker exec termux-package-builder bash -c '
cd /home/builder/termux-packages
# Edit scripts/properties.sh
sed -i "s/TERMUX_APP__PACKAGE_NAME=\"com.termux\"/TERMUX_APP__PACKAGE_NAME=\"com.termux.kotlin\"/" scripts/properties.sh
'

# 3. Build critical packages for each architecture
for arch in aarch64 arm x86_64 i686; do
    docker exec termux-package-builder bash -c "
        cd /home/builder/termux-packages
        ./build-package.sh -a $arch apt
        ./build-package.sh -a $arch dpkg
        ./build-package.sh -a $arch termux-exec
        ./build-package.sh -a $arch termux-tools
        ./build-package.sh -a $arch termux-core
    "
done
```

### Packages Built with Native Paths

| Package | Version | Description |
|---------|---------|-------------|
| apt | 2.8.1-2 | APT package manager with correct methods path |
| dpkg | 1.22.6-5 | Debian package manager |
| termux-exec | 1:2.4.0-1 | LD_PRELOAD shebang fix |
| termux-tools | 1.46.0+really1.45.0-1 | Core termux utilities (pkg, termux-info, etc.) |
| termux-core | 0.4.0-1 | Core termux libraries |
| termux-api | 0.59.1-1 | Terminal API client |
| termux-am | 0.8.0-2 | Activity manager (arch-independent) |

### Integration into Bootstrap

After building, extract and integrate packages:

```bash
# Extract packages
cd /root/termux-kotlin-bootstrap/aarch64-packages
for deb in /path/to/output/*.deb; do
    dpkg-deb -x "$deb" .
done

# Copy to bootstrap
cp -a usr/* /path/to/bootstrap/usr/
cp -a var/* /path/to/bootstrap/var/ 2>/dev/null || true
```

### Path Rewriting for Text Files

Some text files still need path rewriting. Use the provided script:

```bash
./scripts/rewrite-paths.sh /path/to/bootstrap
```

This modifies ~270 text files, replacing:
- `com.termux` → `com.termux.kotlin`
- `/data/data/com.termux/files/usr` → `/data/data/com.termux.kotlin/files/usr`

## Legacy Approach: Post-Processing Only

The old approach of only post-processing the upstream bootstrap is **no longer recommended** because:

1. **APT methods path is compiled into the apt binary** - causes Error #12
2. **LD_PRELOAD paths are compiled into termux-exec** - may cause shell issues
3. **Many tools have hardcoded paths in binaries** - can't be fixed without rebuild

## Files in This Repository

### Scripts

- `scripts/build-bootstrap.sh` - Full bootstrap build script
- `scripts/rewrite-paths.sh` - Text file path rewriting
- `scripts/generate-repo.sh` - Generate APT repository indices

### Workflows

- `.github/workflows/build-termux-tools.yml` - CI workflow for rebuilding packages
- `.github/workflows/build-bootstrap.yml` - CI workflow for bootstrap generation

### Package Repository

- `repo/` - Custom package repository with rebuilt packages
  - `aarch64/`, `arm/`, `x86_64/`, `i686/`, `all/` - Architecture directories
  - `Packages`, `Packages.gz` - APT indices
  - `Release` - Repository metadata

## Verification

After building, verify paths are correct:

```bash
# Check APT methods path
strings bootstrap/usr/bin/apt | grep "com.termux"
# Should show: /data/data/com.termux.kotlin/files/usr

# Check termux-exec
strings bootstrap/usr/lib/libtermux-exec.so | grep "com.termux"
# Should show: /data/data/com.termux.kotlin/files/usr

# Test on device
adb install app.apk
# Launch app, then run:
pkg update
# Should work without Error #12
```

## Build Times

Approximate build times per architecture:
- apt: ~20-30 minutes (many dependencies)
- dpkg: ~5-10 minutes
- termux-exec: ~1 minute
- termux-tools: ~2 minutes
- termux-core: ~1 minute
- termux-api: ~2 minutes

Total: ~30-45 minutes per architecture, ~2-3 hours for all 4 architectures.

## Troubleshooting

### Build Lock Issues
```bash
# Clear build lock if builds fail
docker exec termux-package-builder rm -f /home/builder/.termux-build/_lock
```

### Package Already Built
```bash
# Force rebuild of a package
docker exec termux-package-builder rm -f /data/data/.built-packages/<package-name>
```

### Missing Dependencies
The build system handles dependencies automatically. If a build fails, check Docker logs for missing tools.
