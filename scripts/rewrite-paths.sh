#!/bin/bash
# Termux Kotlin - Path and Shebang Rewriting Script
# Rewrites all hardcoded com.termux paths to com.termux
#
# Usage: ./rewrite-paths.sh <directory> [--dry-run]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Path patterns
OLD_PREFIX="/data/data/com.termux/files/usr"
NEW_PREFIX="/data/data/com.termux/files/usr"
OLD_HOME="/data/data/com.termux/files/home"
NEW_HOME="/data/data/com.termux/files/home"
OLD_CACHE="/data/data/com.termux/cache"
NEW_CACHE="/data/data/com.termux/cache"
OLD_PKG="com.termux"
NEW_PKG="com.termux"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_debug() { echo -e "${BLUE}[DEBUG]${NC} $1"; }

DRY_RUN=false
VERBOSE=false
STATS_TEXT=0
STATS_BINARY=0
STATS_SYMLINKS=0
STATS_SKIPPED=0

usage() {
    cat << EOF
Usage: $0 <directory> [options]

Rewrite hardcoded com.termux paths to com.termux

Options:
    --dry-run       Show what would be changed without modifying files
    --verbose       Show detailed output
    -h, --help      Show this help message

Transformations applied:
    /data/data/com.termux/files/usr  ->  /data/data/com.termux/files/usr
    /data/data/com.termux/files/home ->  /data/data/com.termux/files/home
    /data/data/com.termux/cache      ->  /data/data/com.termux/cache
    com.termux (package refs)        ->  com.termux

EOF
    exit 0
}

# Check if file is a text file
is_text_file() {
    local file="$1"
    # Use file command to detect type
    local file_type
    file_type=$(file -b --mime-type "$file" 2>/dev/null)
    
    case "$file_type" in
        text/*|application/x-shellscript|application/javascript|application/json|application/xml)
            return 0
            ;;
        *)
            # Also check by content for unknown types
            if file "$file" 2>/dev/null | grep -qiE "text|script|ascii|utf-8"; then
                return 0
            fi
            return 1
            ;;
    esac
}

# Rewrite text file
rewrite_text_file() {
    local file="$1"
    local modified=false
    
    # Check if file contains any old patterns
    if grep -qE "(com\.termux[^.]|/data/data/com\.termux/)" "$file" 2>/dev/null; then
        if $DRY_RUN; then
            log_info "[DRY-RUN] Would rewrite: $file"
            grep -nE "(com\.termux[^.]|/data/data/com\.termux/)" "$file" 2>/dev/null | head -3
            modified=true
        else
            # Perform replacements
            # Order matters: do more specific patterns first
            
            # Replace prefix paths
            sed -i "s|${OLD_PREFIX}|${NEW_PREFIX}|g" "$file"
            
            # Replace home paths
            sed -i "s|${OLD_HOME}|${NEW_HOME}|g" "$file"
            
            # Replace cache paths
            sed -i "s|${OLD_CACHE}|${NEW_CACHE}|g" "$file"
            
            # Replace package name (careful not to match com.termux)
            # Match com.termux followed by non-dot or end of line
            sed -i 's/com\.termux\([^.a-zA-Z]\)/com.termux\1/g' "$file"
            sed -i 's/com\.termux$/com.termux/g' "$file"
            
            if $VERBOSE; then
                log_info "Rewrote: $file"
            fi
            modified=true
        fi
    fi
    
    if $modified; then
        ((STATS_TEXT++)) || true
        return 0
    fi
    return 1
}

# Rewrite shebang in script
rewrite_shebang() {
    local file="$1"
    
    # Check first line for shebang
    local first_line
    first_line=$(head -1 "$file" 2>/dev/null)
    
    if [[ "$first_line" =~ ^#!.*com\.termux/files/usr ]]; then
        if $DRY_RUN; then
            log_info "[DRY-RUN] Would fix shebang in: $file"
            echo "  Old: $first_line"
            echo "  New: ${first_line//com.termux/com.termux}"
        else
            sed -i "1s|com\.termux/files/usr|com.termux/files/usr|g" "$file"
            if $VERBOSE; then
                log_info "Fixed shebang: $file"
            fi
        fi
        return 0
    fi
    return 1
}

# Process a single file
process_file() {
    local file="$1"
    
    # Skip if not a regular file
    if [[ ! -f "$file" ]]; then
        return
    fi
    
    # Handle symlinks
    if [[ -L "$file" ]]; then
        local target
        target=$(readlink "$file")
        if [[ "$target" == *"com.termux"* ]] && [[ "$target" != *"com.termux"* ]]; then
            local new_target="${target//com.termux/com.termux}"
            if $DRY_RUN; then
                log_info "[DRY-RUN] Would update symlink: $file -> $new_target"
            else
                ln -snf "$new_target" "$file"
                if $VERBOSE; then
                    log_info "Updated symlink: $file -> $new_target"
                fi
            fi
            ((STATS_SYMLINKS++)) || true
        fi
        return
    fi
    
    # Skip binary files (but note: we now have native builds so shouldn't need to patch these)
    if ! is_text_file "$file"; then
        ((STATS_SKIPPED++)) || true
        return
    fi
    
    # Process text files
    rewrite_shebang "$file" || true
    rewrite_text_file "$file" || true
}

# Process directory recursively
process_directory() {
    local dir="$1"
    
    log_info "Processing directory: $dir"
    
    # Find all files
    while IFS= read -r -d '' file; do
        process_file "$file"
    done < <(find "$dir" -type f -print0 2>/dev/null)
    
    # Find all symlinks
    while IFS= read -r -d '' link; do
        process_file "$link"
    done < <(find "$dir" -type l -print0 2>/dev/null)
}

# Main
main() {
    local target_dir=""
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --verbose|-v)
                VERBOSE=true
                shift
                ;;
            -h|--help)
                usage
                ;;
            *)
                if [[ -z "$target_dir" ]]; then
                    target_dir="$1"
                else
                    log_error "Unknown argument: $1"
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    if [[ -z "$target_dir" ]]; then
        log_error "No directory specified"
        usage
    fi
    
    if [[ ! -d "$target_dir" ]]; then
        log_error "Directory does not exist: $target_dir"
        exit 1
    fi
    
    log_info "Starting path rewrite for: $target_dir"
    if $DRY_RUN; then
        log_warn "DRY RUN MODE - No files will be modified"
    fi
    
    process_directory "$target_dir"
    
    echo ""
    log_info "=== Summary ==="
    log_info "Text files modified: $STATS_TEXT"
    log_info "Symlinks updated: $STATS_SYMLINKS"
    log_info "Binary files skipped: $STATS_SKIPPED"
    
    if $DRY_RUN; then
        log_warn "DRY RUN - No actual changes made"
    fi
}

main "$@"
