#!/bin/sh
# POSIX-compliant prefix validator script
# Ensures com.termux is forbidden but com.termux.kotlin is allowed
#
# Regex rules:
#   FORBIDDEN: \bcom\.termux\b (exact match, no suffix)
#   ALLOWED:   com.termux.kotlin (has .kotlin suffix)
#
# IMPORTANT DISTINCTION:
#   - Java/Kotlin PACKAGE NAMES (package com.termux.app) are ALLOWED
#     These are namespace identifiers, not the Android application ID
#   - Android APPLICATION ID must be com.termux.kotlin
#   - Runtime PATHS like /data/data/com.termux/ are FORBIDDEN
#     Must use /data/data/com.termux.kotlin/
#   - Configuration files with package references must use com.termux.kotlin

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors (POSIX-safe)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Directories to scan (for critical violations only)
SCAN_DIRS="bootstrap termux-packages scripts modules tools config"

# Directories to scan for path violations only
PATH_SCAN_DIRS="app/src"

# Temporary file for results
RESULTS_FILE="${TMPDIR:-/tmp}/prefix-validation-$$"
VIOLATIONS_FILE="${TMPDIR:-/tmp}/prefix-violations-$$"

# Cleanup on exit
cleanup() {
    rm -f "$RESULTS_FILE" "$VIOLATIONS_FILE" 2>/dev/null
}
trap cleanup EXIT

log_info() {
    printf "${GREEN}[INFO]${NC} %s\n" "$1"
}

log_warn() {
    printf "${YELLOW}[WARN]${NC} %s\n" "$1"
}

log_error() {
    printf "${RED}[ERROR]${NC} %s\n" "$1"
}

# Check if a file contains the forbidden prefix
# Returns 0 if forbidden prefix found, 1 if clean
check_file() {
    file="$1"
    
    # Skip binary files, symlinks, and non-existent files
    [ ! -f "$file" ] && return 1
    [ -L "$file" ] && return 1
    
    # Skip known binary extensions
    case "$file" in
        *.zip|*.tar|*.gz|*.xz|*.bz2|*.deb|*.apk|*.so|*.a|*.o|*.jar|*.class|*.png|*.jpg|*.jpeg|*.gif|*.ico|*.pdf|*.ttf|*.woff|*.woff2|*.eot) 
            return 1
            ;;
    esac
    
    # Use file command to skip binaries if available
    if command -v file >/dev/null 2>&1; then
        file_type=$(file -b "$file" 2>/dev/null || echo "unknown")
        case "$file_type" in
            *executable*|*"shared object"*|*"ELF"*|*"compiled"*|*"data"*|*"archive"*)
                return 1
                ;;
        esac
    fi
    
    # Check for forbidden pattern: com.termux NOT followed by .kotlin
    # This regex finds "com.termux" followed by non-dot or end of word
    # We use grep with Perl-compatible regex if available, else basic grep
    
    if command -v grep >/dev/null 2>&1; then
        # First, find all lines with com.termux
        if grep -q 'com\.termux' "$file" 2>/dev/null; then
            # Now check if any of those are NOT com.termux.kotlin
            # Pattern: com.termux followed by end-of-word (not .kotlin)
            # Using negative lookahead if available, else post-process
            
            # Extract lines with com.termux
            matches=$(grep -n 'com\.termux' "$file" 2>/dev/null || true)
            
            # Check each match
            echo "$matches" | while IFS= read -r line; do
                [ -z "$line" ] && continue
                
                # Check if this line has com.termux.kotlin
                if echo "$line" | grep -q 'com\.termux\.kotlin'; then
                    # Line has valid prefix, check for bare com.termux too
                    # Pattern: com.termux not followed by .kotlin
                    # Replace com.termux.kotlin temporarily, then check for remaining com.termux
                    cleaned=$(echo "$line" | sed 's/com\.termux\.kotlin/__VALID_PREFIX__/g')
                    if echo "$cleaned" | grep -q 'com\.termux'; then
                        # Found bare com.termux alongside com.termux.kotlin
                        line_num=$(echo "$line" | cut -d: -f1)
                        echo "${file}:${line_num}:$(echo "$line" | cut -d: -f2-)"
                    fi
                else
                    # Line has com.termux but NOT com.termux.kotlin - violation!
                    line_num=$(echo "$line" | cut -d: -f1)
                    echo "${file}:${line_num}:$(echo "$line" | cut -d: -f2-)"
                fi
            done
        fi
    fi
}

# Main validation function
validate_directories() {
    log_info "Starting prefix validation..."
    log_info "Scanning for forbidden prefix: com.termux (without .kotlin suffix)"
    log_info "Allowed prefix: com.termux.kotlin"
    echo ""
    
    violation_count=0
    scanned_count=0
    
    : > "$VIOLATIONS_FILE"
    
    cd "$ROOT_DIR"
    
    for dir in $SCAN_DIRS; do
        if [ -d "$dir" ]; then
            log_info "Scanning directory: $dir"
            
            # Find all files recursively
            find "$dir" -type f 2>/dev/null | while IFS= read -r file; do
                result=$(check_file "$file")
                if [ -n "$result" ]; then
                    echo "$result" >> "$VIOLATIONS_FILE"
                fi
            done
            
            scanned_count=$((scanned_count + 1))
        else
            log_warn "Directory not found: $dir (skipping)"
        fi
    done
    
    echo ""
    
    # Count violations
    if [ -f "$VIOLATIONS_FILE" ] && [ -s "$VIOLATIONS_FILE" ]; then
        violation_count=$(wc -l < "$VIOLATIONS_FILE" | tr -d ' ')
        
        log_error "Found $violation_count forbidden prefix violation(s)!"
        echo ""
        echo "=========================================="
        echo "VIOLATIONS (com.termux without .kotlin)"
        echo "=========================================="
        
        cat "$VIOLATIONS_FILE" | while IFS= read -r violation; do
            file_path=$(echo "$violation" | cut -d: -f1)
            line_num=$(echo "$violation" | cut -d: -f2)
            content=$(echo "$violation" | cut -d: -f3-)
            
            printf "${RED}[VIOLATION]${NC} %s:%s\n" "$file_path" "$line_num"
            printf "  Content: %s\n" "$content"
            echo ""
        done
        
        echo "=========================================="
        log_error "Prefix validation FAILED"
        log_error "All occurrences of 'com.termux' must be 'com.termux.kotlin'"
        echo ""
        
        return 1
    else
        log_info "No forbidden prefixes found"
        log_info "Prefix validation PASSED"
        return 0
    fi
}

# Check if a line is a comment or documentation (allowed to mention com.termux for explanation)
is_comment_or_doc() {
    full_line="$1"
    # Extract just the content after file:linenum:
    content=$(echo "$full_line" | sed 's/^[^:]*:[0-9]*://')
    
    # Trim leading whitespace for checking
    trimmed=$(echo "$content" | sed 's/^[[:space:]]*//')
    
    # Shell comments (line starts with #)
    case "$trimmed" in
        '#'*) return 0 ;;
    esac
    
    # Kotlin/Java comments
    case "$trimmed" in
        '//'*) return 0 ;;
        '*'*) return 0 ;;
        '/*'*) return 0 ;;
    esac
    
    # Log/echo/print statements explaining the process (just documenting)
    if echo "$content" | grep -qE '^\s*(log_|echo|print|Logger\.|Log\.)[^=]*com\.termux'; then
        return 0
    fi
    
    return 1
}

# Check if a line is a Java/Kotlin package declaration or import (allowed)
is_java_package_or_import() {
    line="$1"
    
    # Match: package com.termux.* or import com.termux.*
    # These are Java/Kotlin namespace declarations, NOT application IDs
    if echo "$line" | grep -qE '^\s*(package|import)\s+com\.termux'; then
        return 0  # true - is a package/import (allowed)
    fi
    
    # Match: namespace "com.termux" in build.gradle (allowed - it's the Java namespace)
    if echo "$line" | grep -qE 'namespace\s+["\x27]com\.termux["\x27]'; then
        return 0
    fi
    
    return 1  # false - not a package/import
}

# Check if a line contains forbidden runtime path
is_forbidden_runtime_path() {
    line="$1"
    
    # Match: /data/data/com.termux/ (without .kotlin)
    # Match: /data/user/*/com.termux/ 
    if echo "$line" | grep -qE '/data/(data|user/[0-9]+)/com\.termux[^.]'; then
        return 0  # true - forbidden path found
    fi
    
    # After replacing valid paths, check for remaining forbidden references
    cleaned=$(echo "$line" | sed 's|/data/data/com\.termux\.kotlin|__VALID__|g' | sed 's|/data/user/[0-9]*/com\.termux\.kotlin|__VALID__|g')
    if echo "$cleaned" | grep -qE '/data/(data|user/[0-9]+)/com\.termux[^.]'; then
        return 0  # forbidden path remains
    fi
    
    return 1
}

# Fast scan mode using grep -r
fast_scan() {
    log_info "Running fast prefix scan..."
    log_info "Note: Java/Kotlin package declarations are ALLOWED"
    log_info "Note: Only runtime paths and config files are checked"
    
    cd "$ROOT_DIR"
    
    : > "$VIOLATIONS_FILE"
    critical_violations=0
    warning_violations=0
    
    # CRITICAL SCAN: Bootstrap, scripts, config files
    # These should NEVER have bare com.termux (except .kotlin)
    # EXCEPTION: Path-rewriting scripts that intentionally reference old paths
    for dir in $SCAN_DIRS; do
        if [ -d "$dir" ]; then
            log_info "Scanning (critical): $dir"
            
            grep -r --include="*.sh" --include="*.properties" \
                 --include="*.conf" --include="*.list" --include="*.json" \
                 --include="*.yaml" --include="*.yml" \
                 -n 'com\.termux' "$dir" 2>/dev/null | while IFS= read -r line; do
                
                # Get filename
                filename=$(echo "$line" | cut -d: -f1)
                
                # Skip scripts that are intentionally dealing with path rewriting
                case "$filename" in
                    *rewrite-paths.sh|*validate-prefix.sh|*collect-failure-logs.sh|*detect-package-changes.sh)
                        continue
                        ;;
                esac
                
                # Skip comments and documentation
                if is_comment_or_doc "$line"; then
                    continue
                fi
                
                # Replace valid com.termux.kotlin references
                cleaned=$(echo "$line" | sed 's/com\.termux\.kotlin/__VALID__/g')
                
                if echo "$cleaned" | grep -q 'com\.termux'; then
                    # Also skip sed replacement patterns that are correct
                    # e.g., sed 's/com.termux/com.termux.kotlin/'
                    if echo "$line" | grep -qE 'sed.*com\.termux.*com\.termux\.kotlin'; then
                        continue
                    fi
                    # Skip sed patterns that replace com.termux with variable
                    if echo "$line" | grep -qE 'sed.*com\.termux.*\$[A-Z_]'; then
                        continue
                    fi
                    # Skip variable definitions for OLD_ paths used in rewriting
                    if echo "$line" | grep -qE 'OLD_[A-Z_]*=.*com\.termux'; then
                        continue
                    fi
                    # Skip grep patterns checking for old paths (validation code)
                    if echo "$line" | grep -qE '(grep|strings).*com\.termux\['; then
                        continue
                    fi
                    echo "[CRITICAL] $line" >> "$VIOLATIONS_FILE"
                fi
            done
        fi
    done
    
    # PATH SCAN: Java/Kotlin source files - only check for runtime paths
    # Package declarations and imports are ALLOWED
    for dir in $PATH_SCAN_DIRS; do
        if [ -d "$dir" ]; then
            log_info "Scanning (paths only): $dir"
            
            # Look specifically for runtime path patterns
            grep -r --include="*.java" --include="*.kt" \
                 -n '/data/data/com\.termux\|/data/user/.*com\.termux' "$dir" 2>/dev/null | while IFS= read -r line; do
                
                # Skip comments (lines where the path is just being described/documented)
                if is_comment_or_doc "$line"; then
                    continue
                fi
                
                # Skip if it's com.termux.kotlin
                if echo "$line" | grep -q '/data/data/com\.termux\.kotlin\|/data/user/.*com\.termux\.kotlin'; then
                    # Check if there's ALSO a bare com.termux (not in a replacement pattern)
                    cleaned=$(echo "$line" | sed 's|com\.termux\.kotlin|__VALID__|g')
                    # Skip sed/replace patterns that are fixing the path
                    if echo "$line" | grep -qE '(sed|replace|Regex|->).*com\.termux.*com\.termux\.kotlin'; then
                        continue
                    fi
                    # Skip mv commands that are moving old paths to new paths
                    if echo "$line" | grep -qE 'mv.*com\.termux.*com\.termux\.kotlin'; then
                        continue
                    fi
                    if echo "$cleaned" | grep -q '/data/data/com\.termux\|/data/user/.*com\.termux'; then
                        echo "[PATH] $line" >> "$VIOLATIONS_FILE"
                    fi
                else
                    # Skip replacement/mapping patterns
                    if echo "$line" | grep -qE '(sed|replace|Regex|->|OLD_PREFIX|upstreamPrefix|mv |rm -rf)'; then
                        continue
                    fi
                    # Skip if-tests for old paths (checking if old path exists before migration)
                    if echo "$line" | grep -qE '\[ -[def] .*com\.termux'; then
                        continue
                    fi
                    echo "[PATH] $line" >> "$VIOLATIONS_FILE"
                fi
            done
            
            # Also check for hardcoded package name strings (not imports/package declarations)
            grep -r --include="*.java" --include="*.kt" \
                 -n '"com\.termux"' "$dir" 2>/dev/null | while IFS= read -r line; do
                
                # Skip comments
                if is_comment_or_doc "$line"; then
                    continue
                fi
                
                # Skip if it's com.termux.kotlin
                cleaned=$(echo "$line" | sed 's/"com\.termux\.kotlin"/__VALID__/g')
                if echo "$cleaned" | grep -q '"com\.termux"'; then
                    # Skip replacement patterns
                    if echo "$line" | grep -qE '(replace|Regex|sed|->)'; then
                        continue
                    fi
                    echo "[STRING] $line" >> "$VIOLATIONS_FILE"
                fi
            done
        fi
    done
    
    # Count and report
    if [ -f "$VIOLATIONS_FILE" ] && [ -s "$VIOLATIONS_FILE" ]; then
        violation_count=$(wc -l < "$VIOLATIONS_FILE" | tr -d ' ')
        critical_count=$(grep -c '^\[CRITICAL\]' "$VIOLATIONS_FILE" 2>/dev/null) || critical_count=0
        path_count=$(grep -c '^\[PATH\]' "$VIOLATIONS_FILE" 2>/dev/null) || path_count=0
        string_count=$(grep -c '^\[STRING\]' "$VIOLATIONS_FILE" 2>/dev/null) || string_count=0
        
        log_error "Found $violation_count forbidden prefix violation(s)!"
        log_error "  Critical: $critical_count"
        log_error "  Path:     $path_count"
        log_error "  String:   $string_count"
        echo ""
        echo "=========================================="
        echo "VIOLATIONS (com.termux must be com.termux.kotlin)"
        echo "=========================================="
        
        # Show critical violations first
        if [ "$critical_count" -gt 0 ]; then
            echo ""
            echo "--- CRITICAL (config/scripts) ---"
            grep '^\[CRITICAL\]' "$VIOLATIONS_FILE" | head -20
        fi
        
        # Show path violations
        if [ "$path_count" -gt 0 ]; then
            echo ""
            echo "--- PATH (runtime paths) ---"
            grep '^\[PATH\]' "$VIOLATIONS_FILE" | head -20
        fi
        
        # Show string violations
        if [ "$string_count" -gt 0 ]; then
            echo ""
            echo "--- STRING (hardcoded strings) ---"
            grep '^\[STRING\]' "$VIOLATIONS_FILE" | head -20
        fi
        
        echo ""
        echo "=========================================="
        
        # Fail only on critical or path violations
        if [ "$critical_count" -gt 0 ] || [ "$path_count" -gt 0 ]; then
            log_error "Prefix validation FAILED"
            log_error "Critical config and runtime path violations must be fixed."
            return 1
        else
            log_warn "Prefix validation PASSED with warnings"
            log_warn "String violations should be reviewed but are not blocking."
            return 0
        fi
    else
        log_info "No forbidden prefixes found"
        log_info "Prefix validation PASSED"
        return 0
    fi
}

# Print usage
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --fast       Use fast grep-based scanning (default)"
    echo "  --thorough   Use thorough file-by-file scanning"
    echo "  --dir DIR    Add additional directory to scan"
    echo "  --help       Show this help message"
    echo ""
    echo "This script validates that no files contain the forbidden"
    echo "prefix 'com.termux' (must be 'com.termux.kotlin' instead)."
}

# Parse arguments
SCAN_MODE="fast"
EXTRA_DIRS=""

while [ $# -gt 0 ]; do
    case "$1" in
        --fast)
            SCAN_MODE="fast"
            shift
            ;;
        --thorough)
            SCAN_MODE="thorough"
            shift
            ;;
        --dir)
            EXTRA_DIRS="$EXTRA_DIRS $2"
            shift 2
            ;;
        --help)
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

# Add extra directories to scan
if [ -n "$EXTRA_DIRS" ]; then
    SCAN_DIRS="$SCAN_DIRS $EXTRA_DIRS"
fi

# Run validation
echo "=========================================="
echo "  Termux-Kotlin Prefix Validator"
echo "=========================================="
echo ""

if [ "$SCAN_MODE" = "fast" ]; then
    fast_scan
else
    validate_directories
fi

exit $?
