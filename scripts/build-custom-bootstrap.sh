#!/bin/bash
# Build custom Termux bootstrap for com.termux.kotlin
# This script automates the process of building a custom bootstrap
# with the correct package prefix baked into all binaries.
#
# Prerequisites:
# - Docker installed and running
# - ~50GB free disk space
# - Internet connection
#
# Usage: ./build-custom-bootstrap.sh [--arch aarch64|arm|x86_64|i686|all]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERMUX_PACKAGES_DIR="/root/termux-packages"
OUTPUT_DIR="/root/termux-kotlin-bootstrap"
APP_DIR="/root/termux-kotlin-app"
PACKAGE_NAME="com.termux.kotlin"

# Default to arm64 only (most common)
ARCH="${1:-aarch64}"
if [ "$1" = "--arch" ]; then
    ARCH="$2"
fi

echo "=========================================="
echo "Custom Bootstrap Build for $PACKAGE_NAME"
echo "=========================================="
echo ""
echo "Architecture: $ARCH"
echo "Packages dir: $TERMUX_PACKAGES_DIR"
echo "Output dir:   $OUTPUT_DIR"
echo ""

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed!"
    echo ""
    echo "Install Docker first:"
    echo "  apt update && apt install -y docker.io"
    echo "  systemctl start docker"
    echo ""
    exit 1
fi

echo "✓ Docker found: $(docker --version)"

# Check if Docker daemon is running
if ! docker info &> /dev/null; then
    echo "❌ Docker daemon is not running!"
    echo ""
    echo "Start Docker:"
    echo "  systemctl start docker"
    echo ""
    exit 1
fi

echo "✓ Docker daemon is running"

# Check termux-packages
if [ ! -d "$TERMUX_PACKAGES_DIR" ]; then
    echo "❌ termux-packages not found at $TERMUX_PACKAGES_DIR"
    echo ""
    echo "Clone it first:"
    echo "  git clone https://github.com/termux/termux-packages.git $TERMUX_PACKAGES_DIR"
    echo ""
    exit 1
fi

echo "✓ termux-packages found"

# Step 1: Patch properties.sh
echo ""
echo "[1/5] Patching properties.sh for $PACKAGE_NAME..."

PROPERTIES_FILE="$TERMUX_PACKAGES_DIR/scripts/properties.sh"

if grep -q "TERMUX_APP__PACKAGE_NAME=\"$PACKAGE_NAME\"" "$PROPERTIES_FILE"; then
    echo "   ✓ Already patched to $PACKAGE_NAME"
else
    # Backup original
    cp "$PROPERTIES_FILE" "$PROPERTIES_FILE.original"
    
    # Apply patch
    sed -i "s/TERMUX_APP__PACKAGE_NAME=\"com.termux\"/TERMUX_APP__PACKAGE_NAME=\"$PACKAGE_NAME\"/" "$PROPERTIES_FILE"
    
    # Verify
    if grep -q "TERMUX_APP__PACKAGE_NAME=\"$PACKAGE_NAME\"" "$PROPERTIES_FILE"; then
        echo "   ✓ Patched successfully"
    else
        echo "   ❌ Patch failed!"
        exit 1
    fi
fi

# Step 2: Build bootstrap
echo ""
echo "[2/5] Building bootstrap for $ARCH..."
echo "      This will take 2-4 hours. Go get coffee. ☕"
echo ""

cd "$TERMUX_PACKAGES_DIR"

if [ "$ARCH" = "all" ]; then
    ./scripts/run-docker.sh ./scripts/build-bootstraps.sh
else
    ./scripts/run-docker.sh ./scripts/build-bootstraps.sh --architectures "$ARCH"
fi

# Step 3: Copy output
echo ""
echo "[3/5] Copying bootstrap files..."

mkdir -p "$OUTPUT_DIR"
cp bootstrap-*.zip "$OUTPUT_DIR/" 2>/dev/null || true

echo "   Bootstrap files in $OUTPUT_DIR:"
ls -la "$OUTPUT_DIR"/*.zip 2>/dev/null || echo "   (no zip files found)"

# Step 4: Integrate with app
echo ""
echo "[4/5] Integrating with termux-kotlin-app..."

# Architecture mapping
declare -A ARCH_MAP=(
    ["aarch64"]="arm64-v8a"
    ["arm"]="armeabi-v7a"
    ["x86_64"]="x86_64"
    ["i686"]="x86"
)

for src_arch in "${!ARCH_MAP[@]}"; do
    dst_arch="${ARCH_MAP[$src_arch]}"
    src_file="$OUTPUT_DIR/bootstrap-${src_arch}.zip"
    dst_dir="$APP_DIR/app/src/main/jniLibs/$dst_arch"
    dst_file="$dst_dir/libtermux-bootstrap.so"
    
    if [ -f "$src_file" ]; then
        mkdir -p "$dst_dir"
        cp "$src_file" "$dst_file"
        echo "   ✓ Copied $src_arch → $dst_arch"
    fi
done

# Step 5: Summary
echo ""
echo "[5/5] Build Complete!"
echo ""
echo "=========================================="
echo "Next Steps:"
echo "=========================================="
echo ""
echo "1. Simplify TermuxInstaller.kt:"
echo "   - Remove createDpkgWrapper()"
echo "   - Remove createUpdateAlternativesWrapper()"
echo "   - Remove createAptWrappers()"
echo "   - Simplify isTextFileNeedingPathFix()"
echo ""
echo "2. Test the app:"
echo "   - Build APK: ./gradlew assembleRelease"
echo "   - Install and test on device"
echo ""
echo "3. Verify paths in running app:"
echo "   - pkg update"
echo "   - which bash"
echo "   - cat /data/data/$PACKAGE_NAME/files/usr/etc/apt/sources.list"
echo ""
