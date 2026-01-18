#!/bin/sh
# POSIX-compliant package change detection script
# Detects which packages need rebuilding based on git changes

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Output files
CHANGED_PACKAGES_FILE="${CHANGED_PACKAGES_FILE:-${TMPDIR:-/tmp}/changed-packages.txt}"
REBUILD_REQUIRED_FILE="${TMPDIR:-/tmp}/rebuild-required"

log_info() {
    printf "${GREEN}[INFO]${NC} %s\n" "$1" >&2
}

log_warn() {
    printf "${YELLOW}[WARN]${NC} %s\n" "$1" >&2
}

log_error() {
    printf "${RED}[ERROR]${NC} %s\n" "$1" >&2
}

log_package() {
    printf "${CYAN}[PACKAGE]${NC} %s\n" "$1" >&2
}

# Get the last successful build commit from various sources
get_last_successful_commit() {
    # Try environment variable first (set by CI)
    if [ -n "$LAST_SUCCESSFUL_COMMIT" ]; then
        echo "$LAST_SUCCESSFUL_COMMIT"
        return 0
    fi
    
    # Try reading from a marker file
    if [ -f "$ROOT_DIR/.last-successful-build" ]; then
        cat "$ROOT_DIR/.last-successful-build"
        return 0
    fi
    
    # Try GitHub API if available
    if [ -n "$GITHUB_TOKEN" ] && [ -n "$GITHUB_REPOSITORY" ]; then
        last_commit=$(curl -s -H "Authorization: token $GITHUB_TOKEN" \
            "https://api.github.com/repos/$GITHUB_REPOSITORY/actions/runs?status=success&per_page=1" | \
            grep -o '"head_sha": *"[^"]*"' | head -1 | cut -d'"' -f4)
        if [ -n "$last_commit" ]; then
            echo "$last_commit"
            return 0
        fi
    fi
    
    # Default to comparing against previous commit
    if git rev-parse HEAD~1 >/dev/null 2>&1; then
        git rev-parse HEAD~1
        return 0
    fi
    
    # If no previous commit, compare against empty tree
    echo "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
}

# Get list of changed files between two commits
get_changed_files() {
    base_commit="$1"
    head_commit="${2:-HEAD}"
    
    if [ "$base_commit" = "4b825dc642cb6eb9a060e54bf8d69288fbee4904" ]; then
        # Empty tree - list all files
        git ls-tree -r --name-only HEAD
    else
        # Get diff between commits
        git diff --name-only "$base_commit" "$head_commit" 2>/dev/null || \
        git diff --name-only HEAD~1 HEAD 2>/dev/null || \
        git ls-tree -r --name-only HEAD
    fi
}

# Extract package name from file path
extract_package_name() {
    file_path="$1"
    
    # Pattern: termux-packages/packages/<package-name>/
    case "$file_path" in
        termux-packages/packages/*/*)
            echo "$file_path" | sed 's|termux-packages/packages/||' | cut -d/ -f1
            ;;
        packages/*/*)
            echo "$file_path" | sed 's|packages/||' | cut -d/ -f1
            ;;
        *)
            # Not a package file
            echo ""
            ;;
    esac
}

# Check if bootstrap files changed
check_bootstrap_changes() {
    changed_files="$1"
    
    echo "$changed_files" | grep -qE "^(bootstrap/|scripts/build-bootstrap|scripts/build-custom-bootstrap)" && echo "yes" || echo "no"
}

# Check if core build infrastructure changed
check_infra_changes() {
    changed_files="$1"
    
    # If these change, rebuild everything
    echo "$changed_files" | grep -qE "^(termux-packages/scripts/|termux-packages/build-package\.sh|scripts/properties\.sh)" && echo "yes" || echo "no"
}

# Check for forbidden prefix in changed files
check_prefix_violations() {
    changed_files="$1"
    
    violations=""
    
    echo "$changed_files" | while IFS= read -r file; do
        [ -z "$file" ] && continue
        [ ! -f "$ROOT_DIR/$file" ] && continue
        
        # Check for forbidden com.termux (not com.termux.kotlin)
        if grep -q 'com\.termux' "$ROOT_DIR/$file" 2>/dev/null; then
            # Verify it's not just com.termux.kotlin
            cleaned=$(grep 'com\.termux' "$ROOT_DIR/$file" | sed 's/com\.termux\.kotlin/__VALID__/g')
            if echo "$cleaned" | grep -q 'com\.termux'; then
                echo "$file"
            fi
        fi
    done
}

# Main detection logic
main() {
    log_info "Detecting package changes..."
    
    cd "$ROOT_DIR"
    
    # Ensure we're in a git repository
    if ! git rev-parse --git-dir >/dev/null 2>&1; then
        log_error "Not a git repository"
        exit 1
    fi
    
    # Get comparison base
    base_commit=$(get_last_successful_commit)
    log_info "Comparing against: $base_commit"
    
    # Get changed files
    changed_files=$(get_changed_files "$base_commit")
    
    if [ -z "$changed_files" ]; then
        log_info "No files changed"
        : > "$CHANGED_PACKAGES_FILE"
        exit 0
    fi
    
    log_info "Changed files:"
    echo "$changed_files" | head -20 | while read -r f; do
        echo "  - $f" >&2
    done
    
    file_count=$(echo "$changed_files" | wc -l | tr -d ' ')
    if [ "$file_count" -gt 20 ]; then
        log_info "... and $((file_count - 20)) more files"
    fi
    
    # Check for infrastructure changes
    infra_changed=$(check_infra_changes "$changed_files")
    if [ "$infra_changed" = "yes" ]; then
        log_warn "Build infrastructure changed - full rebuild recommended"
        echo "FULL_REBUILD=true"
    fi
    
    # Check for bootstrap changes
    bootstrap_changed=$(check_bootstrap_changes "$changed_files")
    if [ "$bootstrap_changed" = "yes" ]; then
        log_warn "Bootstrap files changed - bootstrap rebuild required"
        echo "BOOTSTRAP_REBUILD=true"
    fi
    
    # Extract changed packages
    log_info "Extracting changed packages..."
    
    : > "$CHANGED_PACKAGES_FILE"
    
    echo "$changed_files" | while IFS= read -r file; do
        pkg=$(extract_package_name "$file")
        if [ -n "$pkg" ]; then
            echo "$pkg" >> "$CHANGED_PACKAGES_FILE"
        fi
    done
    
    # Remove duplicates
    if [ -f "$CHANGED_PACKAGES_FILE" ] && [ -s "$CHANGED_PACKAGES_FILE" ]; then
        sort -u "$CHANGED_PACKAGES_FILE" > "${CHANGED_PACKAGES_FILE}.tmp"
        mv "${CHANGED_PACKAGES_FILE}.tmp" "$CHANGED_PACKAGES_FILE"
    fi
    
    # Check for prefix violations
    prefix_violations=$(check_prefix_violations "$changed_files")
    if [ -n "$prefix_violations" ]; then
        log_error "Prefix violations found in changed files:"
        echo "$prefix_violations" | while read -r v; do
            log_error "  - $v"
        done
        echo "PREFIX_VIOLATIONS=true"
    fi
    
    # Output results
    if [ -f "$CHANGED_PACKAGES_FILE" ] && [ -s "$CHANGED_PACKAGES_FILE" ]; then
        pkg_count=$(wc -l < "$CHANGED_PACKAGES_FILE" | tr -d ' ')
        log_info "Packages requiring rebuild: $pkg_count"
        
        echo ""
        echo "# Packages to rebuild:"
        while IFS= read -r pkg; do
            log_package "$pkg"
            echo "$pkg"
        done < "$CHANGED_PACKAGES_FILE"
        
        # Create rebuild marker
        touch "$REBUILD_REQUIRED_FILE"
    else
        log_info "No packages require rebuild"
        rm -f "$REBUILD_REQUIRED_FILE"
    fi
    
    # GitHub Actions output format
    if [ -n "$GITHUB_OUTPUT" ]; then
        if [ -f "$CHANGED_PACKAGES_FILE" ] && [ -s "$CHANGED_PACKAGES_FILE" ]; then
            packages=$(tr '\n' ',' < "$CHANGED_PACKAGES_FILE" | sed 's/,$//')
            echo "packages=$packages" >> "$GITHUB_OUTPUT"
            echo "rebuild_required=true" >> "$GITHUB_OUTPUT"
            echo "package_count=$pkg_count" >> "$GITHUB_OUTPUT"
        else
            echo "packages=" >> "$GITHUB_OUTPUT"
            echo "rebuild_required=false" >> "$GITHUB_OUTPUT"
            echo "package_count=0" >> "$GITHUB_OUTPUT"
        fi
        
        echo "bootstrap_changed=$bootstrap_changed" >> "$GITHUB_OUTPUT"
        echo "infra_changed=$infra_changed" >> "$GITHUB_OUTPUT"
    fi
}

# Handle arguments
case "${1:-}" in
    --help|-h)
        echo "Usage: $0 [OPTIONS]"
        echo ""
        echo "Detects which packages need rebuilding based on git changes."
        echo ""
        echo "Options:"
        echo "  --help    Show this help message"
        echo ""
        echo "Environment variables:"
        echo "  LAST_SUCCESSFUL_COMMIT   Base commit to compare against"
        echo "  CHANGED_PACKAGES_FILE    Output file for package list"
        echo "  GITHUB_OUTPUT            GitHub Actions output file"
        exit 0
        ;;
    *)
        main
        ;;
esac
