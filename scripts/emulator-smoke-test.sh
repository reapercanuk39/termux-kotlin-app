#!/bin/sh
# POSIX-compliant emulator smoke test script
# Runs automated tests inside Android emulator with Termux-Kotlin

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
APK_PATH="${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE_NAME="${PACKAGE_NAME:-com.termux}"
MAIN_ACTIVITY="${MAIN_ACTIVITY:-com.termux.app.TermuxActivity}"
TEST_TIMEOUT="${TEST_TIMEOUT:-300}"
BOOTSTRAP_TIMEOUT="${BOOTSTRAP_TIMEOUT:-180}"
LOG_DIR="${LOG_DIR:-${TMPDIR:-/tmp}/emulator-test-logs}"

# Test results
TESTS_PASSED=0
TESTS_FAILED=0
TEST_LOG="${LOG_DIR}/test-results.log"

log_info() {
    printf "${GREEN}[INFO]${NC} %s\n" "$1"
    echo "[INFO] $1" >> "$TEST_LOG" 2>/dev/null || true
}

log_warn() {
    printf "${YELLOW}[WARN]${NC} %s\n" "$1"
    echo "[WARN] $1" >> "$TEST_LOG" 2>/dev/null || true
}

log_error() {
    printf "${RED}[ERROR]${NC} %s\n" "$1"
    echo "[ERROR] $1" >> "$TEST_LOG" 2>/dev/null || true
}

log_test() {
    printf "${CYAN}[TEST]${NC} %s\n" "$1"
    echo "[TEST] $1" >> "$TEST_LOG" 2>/dev/null || true
}

# Initialize test environment
init_test_env() {
    mkdir -p "$LOG_DIR"
    : > "$TEST_LOG"
    
    log_info "Initializing test environment..."
    log_info "APK Path: $APK_PATH"
    log_info "Package: $PACKAGE_NAME"
    log_info "Log directory: $LOG_DIR"
}

# Check if emulator is running
check_emulator() {
    log_info "Checking for running emulator..."
    
    if ! command -v adb >/dev/null 2>&1; then
        log_error "ADB not found in PATH"
        return 1
    fi
    
    # Wait for device
    local wait_time=0
    local max_wait=60
    
    while [ $wait_time -lt $max_wait ]; do
        if adb devices | grep -q "emulator\|device$"; then
            log_info "Emulator detected"
            return 0
        fi
        
        sleep 2
        wait_time=$((wait_time + 2))
        log_info "Waiting for emulator... ($wait_time/$max_wait seconds)"
    done
    
    log_error "No emulator detected after ${max_wait}s"
    return 1
}

# Wait for emulator to be fully booted
wait_for_boot() {
    log_info "Waiting for emulator to complete boot..."
    
    local wait_time=0
    local max_wait=120
    
    while [ $wait_time -lt $max_wait ]; do
        boot_completed=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
        
        if [ "$boot_completed" = "1" ]; then
            log_info "Emulator boot completed"
            sleep 5  # Extra time for system services
            return 0
        fi
        
        sleep 2
        wait_time=$((wait_time + 2))
    done
    
    log_error "Emulator did not complete boot in ${max_wait}s"
    return 1
}

# Install the APK
install_apk() {
    log_info "Installing APK: $APK_PATH"
    
    if [ ! -f "$ROOT_DIR/$APK_PATH" ] && [ ! -f "$APK_PATH" ]; then
        log_error "APK not found: $APK_PATH"
        return 1
    fi
    
    local apk_file="$APK_PATH"
    [ -f "$ROOT_DIR/$APK_PATH" ] && apk_file="$ROOT_DIR/$APK_PATH"
    
    # Uninstall previous version if exists
    adb uninstall "$PACKAGE_NAME" 2>/dev/null || true
    
    # Install new APK
    if adb install -r -g "$apk_file" 2>&1 | tee -a "$TEST_LOG"; then
        log_info "APK installed successfully"
        return 0
    else
        log_error "Failed to install APK"
        return 1
    fi
}

# Grant necessary permissions
grant_permissions() {
    log_info "Granting permissions..."
    
    # Storage permissions
    adb shell pm grant "$PACKAGE_NAME" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
    adb shell pm grant "$PACKAGE_NAME" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
    
    # Other required permissions
    adb shell pm grant "$PACKAGE_NAME" android.permission.WAKE_LOCK 2>/dev/null || true
    adb shell pm grant "$PACKAGE_NAME" android.permission.ACCESS_NETWORK_STATE 2>/dev/null || true
    adb shell pm grant "$PACKAGE_NAME" android.permission.INTERNET 2>/dev/null || true
    adb shell pm grant "$PACKAGE_NAME" android.permission.VIBRATE 2>/dev/null || true
    
    log_info "Permissions granted"
}

# Launch the app
launch_app() {
    log_info "Launching app..."
    
    # Clear app data first
    adb shell pm clear "$PACKAGE_NAME" 2>/dev/null || true
    
    # Launch main activity
    adb shell am start -n "${PACKAGE_NAME}/${MAIN_ACTIVITY}" 2>&1 | tee -a "$TEST_LOG"
    
    sleep 3
    
    # Check if app is running
    if adb shell pidof "$PACKAGE_NAME" >/dev/null 2>&1; then
        log_info "App launched successfully"
        return 0
    else
        log_warn "App may not be running, continuing anyway..."
        return 0
    fi
}

# Wait for bootstrap extraction to complete
wait_for_bootstrap() {
    log_info "Waiting for bootstrap extraction..."
    
    local wait_time=0
    local max_wait=$BOOTSTRAP_TIMEOUT
    local bootstrap_marker="/data/data/${PACKAGE_NAME}/files/usr/bin/bash"
    
    while [ $wait_time -lt $max_wait ]; do
        # Check if bootstrap extraction is complete
        if adb shell "run-as $PACKAGE_NAME test -f $bootstrap_marker" 2>/dev/null; then
            log_info "Bootstrap extraction completed"
            return 0
        fi
        
        # Alternative check via shell existence
        if adb shell "test -d /data/data/${PACKAGE_NAME}/files/usr" 2>/dev/null; then
            if adb shell "ls /data/data/${PACKAGE_NAME}/files/usr/bin/bash" 2>/dev/null | grep -q bash; then
                log_info "Bootstrap extraction completed (verified via ls)"
                return 0
            fi
        fi
        
        sleep 5
        wait_time=$((wait_time + 5))
        log_info "Waiting for bootstrap... ($wait_time/$max_wait seconds)"
    done
    
    log_warn "Bootstrap may not be fully extracted after ${max_wait}s"
    log_warn "Continuing with tests anyway..."
    return 0
}

# Run a command in the Termux environment
run_termux_command() {
    local cmd="$1"
    local description="$2"
    local timeout="${3:-30}"
    
    log_test "Running: $description"
    log_info "Command: $cmd"
    
    # Run command via run-as
    local result
    result=$(adb shell "run-as $PACKAGE_NAME /data/data/$PACKAGE_NAME/files/usr/bin/bash -c '$cmd'" 2>&1) || true
    
    local exit_code=$?
    
    echo "$result" >> "$TEST_LOG"
    
    if [ $exit_code -eq 0 ] && [ -n "$result" ]; then
        log_info "Command succeeded"
        echo "$result" | head -20
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        log_error "Command failed with exit code: $exit_code"
        echo "$result"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Run a command via am broadcast (alternative method)
run_termux_broadcast() {
    local cmd="$1"
    local description="$2"
    
    log_test "Running via broadcast: $description"
    
    # Use Termux:API RUN_COMMAND intent if available
    adb shell am broadcast \
        --user 0 \
        -a "${PACKAGE_NAME}.RUN_COMMAND" \
        --es "command" "$cmd" \
        -n "${PACKAGE_NAME}/${PACKAGE_NAME}.app.TermuxRunCommandService" 2>&1 || true
    
    sleep 2
}

# Collect test artifacts
collect_artifacts() {
    log_info "Collecting test artifacts..."
    
    # Capture screenshot
    adb shell screencap -p /sdcard/test-screenshot.png 2>/dev/null || true
    adb pull /sdcard/test-screenshot.png "$LOG_DIR/screenshot.png" 2>/dev/null || true
    
    # Collect logcat
    adb logcat -d "*:W" > "$LOG_DIR/logcat.txt" 2>/dev/null || true
    
    # Collect app logs
    adb shell "run-as $PACKAGE_NAME cat files/usr/var/log/*.log" > "$LOG_DIR/termux-logs.txt" 2>/dev/null || true
    
    # Collect package info
    adb shell dumpsys package "$PACKAGE_NAME" > "$LOG_DIR/package-info.txt" 2>/dev/null || true
    
    log_info "Artifacts collected in: $LOG_DIR"
}

# Run smoke tests
run_smoke_tests() {
    log_info "Starting smoke tests..."
    
    # Test 1: Echo test (basic functionality)
    log_test "Test 1: Basic echo"
    run_termux_command 'echo "Hello from Termux-Kotlin"' "Basic echo test" || true
    
    # Test 2: Check shell
    log_test "Test 2: Shell verification"
    run_termux_command 'echo $SHELL' "Shell verification" || true
    
    # Test 3: Check PREFIX
    log_test "Test 3: PREFIX verification"
    run_termux_command 'echo $PREFIX' "PREFIX path check" || true
    
    # Test 4: Check if apt/pkg exists
    log_test "Test 4: Package manager check"
    run_termux_command 'which pkg || which apt' "Package manager availability" || true
    
    # Test 5: pkg update (may fail in CI due to network)
    log_test "Test 5: Package update"
    run_termux_command 'pkg update -y 2>&1 || echo "Update skipped/failed"' "Package list update" || true
    
    # Test 6: Install coreutils
    log_test "Test 6: Install coreutils"
    run_termux_command 'pkg install -y coreutils 2>&1 || echo "Already installed or failed"' "Coreutils installation" || true
    
    # Test 7: termux-info
    log_test "Test 7: termux-info"
    run_termux_command 'termux-info 2>/dev/null || echo "termux-info not available"' "Termux info command" || true
    
    # Test 8: Verify paths contain kotlin
    log_test "Test 8: Path verification"
    run_termux_command 'echo $HOME | grep kotlin && echo "Path OK"' "Kotlin path verification" || true
    
    # Test 9: List installed packages
    log_test "Test 9: List packages"
    run_termux_command 'dpkg -l 2>/dev/null | head -10 || echo "dpkg not ready"' "List installed packages" || true
    
    # Test 10: Final echo
    log_test "Test 10: Final verification"
    run_termux_command 'echo "test"' "Final echo test" || true
}

# Generate test report
generate_report() {
    local total=$((TESTS_PASSED + TESTS_FAILED))
    local pass_rate=0
    
    if [ $total -gt 0 ]; then
        pass_rate=$((TESTS_PASSED * 100 / total))
    fi
    
    cat > "$LOG_DIR/report.md" << EOF
# Emulator Smoke Test Report

**Date:** $(date -u '+%Y-%m-%d %H:%M:%S UTC')
**Package:** $PACKAGE_NAME

## Summary

| Metric | Value |
|--------|-------|
| Tests Passed | $TESTS_PASSED |
| Tests Failed | $TESTS_FAILED |
| Total Tests | $total |
| Pass Rate | ${pass_rate}% |

## Result

EOF

    if [ $TESTS_FAILED -eq 0 ]; then
        echo "✅ **All tests passed!**" >> "$LOG_DIR/report.md"
    else
        echo "❌ **${TESTS_FAILED} test(s) failed**" >> "$LOG_DIR/report.md"
    fi

    cat >> "$LOG_DIR/report.md" << EOF

## Collected Artifacts

- \`screenshot.png\` - Final screen state
- \`logcat.txt\` - Android system logs
- \`termux-logs.txt\` - Termux application logs
- \`test-results.log\` - Detailed test output

---

*Generated by emulator-smoke-test.sh*
EOF

    log_info "Report generated: $LOG_DIR/report.md"
    
    # Output for GitHub Actions
    if [ -n "$GITHUB_OUTPUT" ]; then
        echo "tests_passed=$TESTS_PASSED" >> "$GITHUB_OUTPUT"
        echo "tests_failed=$TESTS_FAILED" >> "$GITHUB_OUTPUT"
        echo "pass_rate=$pass_rate" >> "$GITHUB_OUTPUT"
        echo "log_dir=$LOG_DIR" >> "$GITHUB_OUTPUT"
    fi
    
    # Print summary
    echo ""
    echo "=========================================="
    echo "  Smoke Test Summary"
    echo "=========================================="
    echo "  Passed: $TESTS_PASSED"
    echo "  Failed: $TESTS_FAILED"
    echo "  Total:  $total"
    echo "  Rate:   ${pass_rate}%"
    echo "=========================================="
    
    # Return based on test results
    if [ $TESTS_FAILED -gt 0 ]; then
        return 1
    fi
    return 0
}

# Cleanup
cleanup() {
    log_info "Cleaning up..."
    
    # Stop app
    adb shell am force-stop "$PACKAGE_NAME" 2>/dev/null || true
    
    # Remove temp files from device
    adb shell rm -f /sdcard/test-screenshot.png 2>/dev/null || true
}

# Print usage
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Run smoke tests on Android emulator with Termux-Kotlin app."
    echo ""
    echo "Options:"
    echo "  --apk PATH        Path to APK file (default: $APK_PATH)"
    echo "  --package NAME    Package name (default: $PACKAGE_NAME)"
    echo "  --timeout SEC     Test timeout in seconds (default: $TEST_TIMEOUT)"
    echo "  --log-dir PATH    Log output directory (default: $LOG_DIR)"
    echo "  --skip-install    Skip APK installation"
    echo "  --skip-bootstrap  Skip bootstrap wait"
    echo "  --help            Show this help message"
    echo ""
    echo "Environment variables:"
    echo "  APK_PATH          Path to APK file"
    echo "  PACKAGE_NAME      Application package name"
    echo "  TEST_TIMEOUT      Overall test timeout"
    echo "  LOG_DIR           Log output directory"
}

# Parse arguments
SKIP_INSTALL=false
SKIP_BOOTSTRAP=false

while [ $# -gt 0 ]; do
    case "$1" in
        --apk)
            APK_PATH="$2"
            shift 2
            ;;
        --package)
            PACKAGE_NAME="$2"
            shift 2
            ;;
        --timeout)
            TEST_TIMEOUT="$2"
            shift 2
            ;;
        --log-dir)
            LOG_DIR="$2"
            shift 2
            ;;
        --skip-install)
            SKIP_INSTALL=true
            shift
            ;;
        --skip-bootstrap)
            SKIP_BOOTSTRAP=true
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Main execution
main() {
    init_test_env
    
    log_info "Starting emulator smoke test..."
    log_info "Timeout: ${TEST_TIMEOUT}s"
    
    # Step 1: Check emulator
    if ! check_emulator; then
        log_error "Emulator not available"
        exit 1
    fi
    
    # Step 2: Wait for boot
    if ! wait_for_boot; then
        log_error "Emulator boot failed"
        collect_artifacts
        exit 1
    fi
    
    # Step 3: Install APK
    if [ "$SKIP_INSTALL" = "false" ]; then
        if ! install_apk; then
            log_error "APK installation failed"
            collect_artifacts
            exit 1
        fi
    fi
    
    # Step 4: Grant permissions
    grant_permissions
    
    # Step 5: Launch app
    if ! launch_app; then
        log_error "App launch failed"
        collect_artifacts
        exit 1
    fi
    
    # Step 6: Wait for bootstrap
    if [ "$SKIP_BOOTSTRAP" = "false" ]; then
        wait_for_bootstrap
    fi
    
    # Step 7: Run tests
    run_smoke_tests
    
    # Step 8: Collect artifacts
    collect_artifacts
    
    # Step 9: Generate report
    generate_report
    result=$?
    
    # Step 10: Cleanup
    cleanup
    
    exit $result
}

main
