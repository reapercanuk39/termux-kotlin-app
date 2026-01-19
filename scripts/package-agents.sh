#!/bin/bash
# Package agents into bootstrap
# This script copies the agents framework into the bootstrap directory structure
# so it gets deployed when the bootstrap is extracted.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
AGENTS_SRC="$PROJECT_ROOT/agents"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <bootstrap-extract-dir>"
    echo ""
    echo "Example:"
    echo "  $0 /tmp/bootstrap-extract"
    echo ""
    echo "This script copies the agents framework into the bootstrap"
    echo "directory so it gets deployed with the bootstrap."
    exit 1
fi

BOOTSTRAP_DIR="$1"
TARGET_DIR="$BOOTSTRAP_DIR/share/agents"

echo "=== Packaging Agents into Bootstrap ==="
echo "Source:  $AGENTS_SRC"
echo "Target:  $TARGET_DIR"
echo ""

# Verify source exists
if [ ! -d "$AGENTS_SRC" ]; then
    echo "Error: Agents source directory not found: $AGENTS_SRC"
    exit 1
fi

# Create target directory
mkdir -p "$TARGET_DIR"

# Copy core framework
echo "[1/5] Copying core framework..."
mkdir -p "$TARGET_DIR/core"
cp -r "$AGENTS_SRC/core/"* "$TARGET_DIR/core/"
echo "      Done"

# Copy skills
echo "[2/5] Copying skills..."
mkdir -p "$TARGET_DIR/skills"
cp -r "$AGENTS_SRC/skills/"* "$TARGET_DIR/skills/"
echo "      Done"

# Copy agent definitions
echo "[3/5] Copying agent definitions..."
mkdir -p "$TARGET_DIR/models"
cp -r "$AGENTS_SRC/models/"* "$TARGET_DIR/models/"
echo "      Done"

# Copy templates
echo "[4/5] Copying templates..."
mkdir -p "$TARGET_DIR/templates"
cp -r "$AGENTS_SRC/templates/"* "$TARGET_DIR/templates/"
echo "      Done"

# Copy CLI entrypoint
echo "[5/5] Copying CLI entrypoint..."
mkdir -p "$TARGET_DIR/bin"
cp "$AGENTS_SRC/bin/agent" "$TARGET_DIR/bin/agent"
chmod +x "$TARGET_DIR/bin/agent"
echo "      Done"

# Create empty directories for runtime
mkdir -p "$TARGET_DIR/sandboxes"
mkdir -p "$TARGET_DIR/memory"
mkdir -p "$TARGET_DIR/logs"

# Remove __pycache__ directories
find "$TARGET_DIR" -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true

# Count files
FILE_COUNT=$(find "$TARGET_DIR" -type f | wc -l)

echo ""
echo "=== Summary ==="
echo "Files copied: $FILE_COUNT"
echo "Target size:  $(du -sh "$TARGET_DIR" | cut -f1)"
echo ""
echo "Agent framework packaged successfully."
