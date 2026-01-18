#!/bin/bash
# Debug Environment Validation Script
# Verifies all APK and ISO debugging tools are properly installed

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Counters
PASSED=0
FAILED=0
WARNINGS=0

# Tool directories
APK_TOOLS_DIR="/opt/apk-tools"
ISO_TOOLS_DIR="/opt/iso-tools"

log_pass() { 
    echo -e "  ${GREEN}✅${NC} $1"
    PASSED=$((PASSED + 1))
}

log_fail() { 
    echo -e "  ${RED}❌${NC} $1"
    FAILED=$((FAILED + 1))
}

log_warn() { 
    echo -e "  ${YELLOW}⚠️${NC} $1"
    WARNINGS=$((WARNINGS + 1))
}

log_section() { 
    echo -e "\n${BLUE}━━━ $1 ━━━${NC}\n"
}

log_header() {
    echo -e "${CYAN}"
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║           Debug Environment Validation Report                ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# Check if a command exists
check_command() {
    local cmd="$1"
    local description="${2:-$cmd}"
    
    if command -v "$cmd" >/dev/null 2>&1; then
        local version=$(get_version "$cmd")
        log_pass "$description ${CYAN}($version)${NC}"
        return 0
    else
        log_fail "$description - not found"
        return 1
    fi
}

# Check if a file exists
check_file() {
    local file="$1"
    local description="${2:-$file}"
    
    if [ -f "$file" ]; then
        log_pass "$description"
        return 0
    else
        log_fail "$description - not found"
        return 1
    fi
}

# Check if a file is executable
check_executable() {
    local file="$1"
    local description="${2:-$file}"
    
    if [ -x "$file" ]; then
        log_pass "$description (executable)"
        return 0
    elif [ -f "$file" ]; then
        log_warn "$description (not executable)"
        return 1
    else
        log_fail "$description - not found"
        return 1
    fi
}

# Get version of a tool
get_version() {
    local cmd="$1"
    
    case "$cmd" in
        jadx)
            "$cmd" --version 2>/dev/null | head -1 | sed 's/jadx //' || echo "unknown"
            ;;
        apktool)
            "$cmd" --version 2>/dev/null | head -1 || echo "unknown"
            ;;
        7z)
            "$cmd" --help 2>&1 | head -2 | tail -1 | awk '{print $3}' || echo "unknown"
            ;;
        binwalk)
            "$cmd" --help 2>&1 | head -1 | awk '{print $2}' || echo "unknown"
            ;;
        xorriso)
            "$cmd" --version 2>&1 | head -1 | awk '{print $4}' || echo "unknown"
            ;;
        java)
            "$cmd" -version 2>&1 | head -1 | awk -F'"' '{print $2}' || echo "unknown"
            ;;
        python3)
            "$cmd" --version 2>&1 | awk '{print $2}' || echo "unknown"
            ;;
        *)
            echo "installed"
            ;;
    esac
}

# Validate APK Tools
validate_apk_tools() {
    log_section "APK Debugging Tools"
    
    echo "Checking Java-based tools..."
    check_command "jadx" "JADX (Java Decompiler)"
    check_command "jadx-gui" "JADX-GUI"
    check_command "apktool" "Apktool"
    check_command "smali" "smali (DEX Assembler)"
    check_command "baksmali" "baksmali (DEX Disassembler)"
    check_command "uber-apk-signer" "uber-apk-signer"
    check_command "bytecode-viewer" "Bytecode Viewer"
    
    echo ""
    echo "Checking dex2jar tools..."
    check_command "d2j-dex2jar.sh" "dex2jar" || check_file "$APK_TOOLS_DIR/dex2jar/d2j-dex2jar.sh" "dex2jar (in install dir)"
    
    echo ""
    echo "Checking Python-based tools..."
    check_command "androguard" "Androguard"
    check_command "frida" "Frida" || log_warn "Frida (optional - requires device)"
    check_command "objection" "Objection" || log_warn "Objection (optional - requires device)"
    check_command "quark" "Quark Engine"
    check_command "apkleaks" "APKLeaks"
    
    echo ""
    echo "Checking reverse engineering tools..."
    check_command "ghidra" "Ghidra" || check_file "$APK_TOOLS_DIR/ghidra/ghidraRun" "Ghidra (in install dir)"
    
    echo ""
    echo "Checking Android SDK tools..."
    check_command "aapt2" "aapt2" || check_command "aapt" "aapt"
    check_command "apkanalyzer" "apkanalyzer" || log_warn "apkanalyzer (optional - from Android SDK)"
}

# Validate ISO Tools
validate_iso_tools() {
    log_section "ISO Debugging Tools"
    
    echo "Checking ISO manipulation tools..."
    check_command "xorriso" "xorriso"
    check_command "isoinfo" "isoinfo"
    check_command "genisoimage" "genisoimage"
    check_command "7z" "7z (p7zip)"
    
    echo ""
    echo "Checking filesystem tools..."
    check_command "unsquashfs" "unsquashfs"
    check_command "mksquashfs" "mksquashfs"
    check_command "fdisk" "fdisk"
    check_command "gdisk" "gdisk"
    check_command "parted" "parted"
    
    echo ""
    echo "Checking analysis tools..."
    check_command "binwalk" "binwalk"
    check_command "file" "file (magic)"
    check_command "xxd" "xxd (hex dump)"
    
    echo ""
    echo "Checking forensic tools..."
    check_command "fls" "fls (sleuthkit)"
    check_command "mmls" "mmls (sleuthkit)"
    check_command "testdisk" "testdisk"
    check_command "foremost" "foremost" || log_warn "foremost (optional)"
    
    echo ""
    echo "Checking disk image tools..."
    check_command "qemu-img" "qemu-img"
    
    echo ""
    echo "Checking custom helpers..."
    check_executable "$ISO_TOOLS_DIR/bin/iso-mount" "iso-mount"
    check_executable "$ISO_TOOLS_DIR/bin/iso-extract" "iso-extract"
    check_executable "$ISO_TOOLS_DIR/bin/iso-info" "iso-info"
    check_executable "$ISO_TOOLS_DIR/bin/squashfs-extract" "squashfs-extract"
    check_executable "$ISO_TOOLS_DIR/bin/img-info" "img-info"
}

# Validate PATH configuration
validate_path() {
    log_section "PATH Configuration"
    
    echo "Checking PATH entries..."
    
    if echo "$PATH" | grep -q "$APK_TOOLS_DIR/bin"; then
        log_pass "APK tools in PATH"
    else
        log_fail "APK tools NOT in PATH"
        echo "       Add: export PATH=\"$APK_TOOLS_DIR/bin:\$PATH\""
    fi
    
    if echo "$PATH" | grep -q "$ISO_TOOLS_DIR/bin"; then
        log_pass "ISO tools in PATH"
    else
        log_fail "ISO tools NOT in PATH"
        echo "       Add: export PATH=\"$ISO_TOOLS_DIR/bin:\$PATH\""
    fi
    
    echo ""
    echo "Checking profile scripts..."
    check_file "/etc/profile.d/apk-tools.sh" "APK tools profile script"
    check_file "/etc/profile.d/iso-tools.sh" "ISO tools profile script"
}

# Validate dependencies
validate_dependencies() {
    log_section "Dependencies"
    
    echo "Checking runtime dependencies..."
    check_command "java" "Java Runtime"
    check_command "python3" "Python 3"
    check_command "pip3" "pip3" || check_command "pip" "pip"
    check_command "git" "Git"
    check_command "curl" "curl"
    check_command "wget" "wget"
    check_command "unzip" "unzip"
}

# Print summary
print_summary() {
    log_section "Summary"
    
    local total=$((PASSED + FAILED))
    local pass_rate=0
    if [ $total -gt 0 ]; then
        pass_rate=$((PASSED * 100 / total))
    fi
    
    echo -e "  ${GREEN}Passed:${NC}   $PASSED"
    echo -e "  ${RED}Failed:${NC}   $FAILED"
    echo -e "  ${YELLOW}Warnings:${NC} $WARNINGS"
    echo -e "  ${CYAN}Total:${NC}    $total"
    echo ""
    
    if [ $FAILED -eq 0 ]; then
        echo -e "  ${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo -e "  ${GREEN}  ✅ All required tools are installed!         ${NC}"
        echo -e "  ${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    else
        echo -e "  ${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo -e "  ${RED}  ⚠️  $FAILED tool(s) missing or misconfigured   ${NC}"
        echo -e "  ${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo ""
        echo "  To install missing tools, run:"
        echo "    sudo ./scripts/install-apk-tools.sh"
        echo "    sudo ./scripts/install-iso-tools.sh"
    fi
    
    echo ""
}

# Generate machine-readable report
generate_report() {
    local report_file="${1:-/tmp/debug-env-report.json}"
    
    cat > "$report_file" << EOF
{
    "timestamp": "$(date -u '+%Y-%m-%dT%H:%M:%SZ')",
    "passed": $PASSED,
    "failed": $FAILED,
    "warnings": $WARNINGS,
    "apk_tools_path": "$APK_TOOLS_DIR",
    "iso_tools_path": "$ISO_TOOLS_DIR",
    "java_version": "$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}')",
    "python_version": "$(python3 --version 2>&1 | awk '{print $2}')"
}
EOF
    
    echo "Report saved to: $report_file"
}

# Main
main() {
    log_header
    
    validate_dependencies
    validate_apk_tools
    validate_iso_tools
    validate_path
    print_summary
    
    # Exit with appropriate code
    if [ $FAILED -gt 0 ]; then
        exit 1
    fi
    exit 0
}

# Parse arguments
case "${1:-}" in
    --json)
        main 2>/dev/null
        generate_report "${2:-/tmp/debug-env-report.json}"
        ;;
    --help|-h)
        echo "Usage: $0 [OPTIONS]"
        echo ""
        echo "Validate the debug environment installation."
        echo ""
        echo "Options:"
        echo "  --json [FILE]   Generate JSON report"
        echo "  --help          Show this help message"
        exit 0
        ;;
    *)
        main
        ;;
esac
