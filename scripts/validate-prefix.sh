#!/bin/sh
# Prefix validation script - simplified since we use com.termux package name
# 
# With com.termux package name (same as upstream Termux), there's no path
# conflict to validate. All upstream packages have correct paths by default.
#
# This script now just does a basic sanity check.

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors (POSIX-safe)
GREEN='\033[0;32m'
NC='\033[0m'

log_info() {
    printf "${GREEN}[INFO]${NC} %s\n" "$1"
}

echo "=========================================="
echo "  Termux-Kotlin Prefix Validator"
echo "=========================================="
echo ""

log_info "Prefix validation check"
log_info "Package name: com.termux (same as upstream - no path conflicts)"

# Verify the package name is set correctly in key files
cd "$ROOT_DIR"

# Check build.gradle applicationId
if grep -q 'applicationId "com.termux"' app/build.gradle; then
    log_info "✓ applicationId is com.termux"
else
    echo "[ERROR] applicationId should be com.termux"
    exit 1
fi

# Check TermuxConstants
if grep -q 'TERMUX_PACKAGE_NAME = "com.termux"' termux-shared/src/main/kotlin/com/termux/shared/termux/TermuxConstants.kt; then
    log_info "✓ TERMUX_PACKAGE_NAME is com.termux"
else
    echo "[ERROR] TERMUX_PACKAGE_NAME should be com.termux"
    exit 1
fi

echo ""
log_info "=========================================="
log_info "Prefix validation PASSED"
log_info "Using com.termux package name - full upstream compatibility"
log_info "=========================================="

exit 0
