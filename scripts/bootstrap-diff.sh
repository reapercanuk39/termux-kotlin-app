#!/bin/sh
# POSIX-compliant bootstrap diff analyzer
# Compares two bootstrap directories and generates a Markdown report

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Default directories
OLD_BOOTSTRAP="${1:-old_bootstrap}"
NEW_BOOTSTRAP="${2:-new_bootstrap}"
OUTPUT_FILE="${3:-bootstrap-diff-report.md}"

log_info() {
    printf "${GREEN}[INFO]${NC} %s\n" "$1" >&2
}

log_warn() {
    printf "${YELLOW}[WARN]${NC} %s\n" "$1" >&2
}

log_error() {
    printf "${RED}[ERROR]${NC} %s\n" "$1" >&2
}

# Format file size for human reading
format_size() {
    size="$1"
    if [ "$size" -ge 1073741824 ]; then
        printf "%.2f GB" "$(echo "scale=2; $size / 1073741824" | bc 2>/dev/null || echo "$size")"
    elif [ "$size" -ge 1048576 ]; then
        printf "%.2f MB" "$(echo "scale=2; $size / 1048576" | bc 2>/dev/null || echo "$((size / 1048576))")"
    elif [ "$size" -ge 1024 ]; then
        printf "%.2f KB" "$(echo "scale=2; $size / 1024" | bc 2>/dev/null || echo "$((size / 1024))")"
    else
        printf "%d B" "$size"
    fi
}

# Get file size in bytes
get_file_size() {
    file="$1"
    if [ -f "$file" ]; then
        stat -c%s "$file" 2>/dev/null || stat -f%z "$file" 2>/dev/null || wc -c < "$file" | tr -d ' '
    else
        echo "0"
    fi
}

# List all files in a directory recursively
list_files() {
    dir="$1"
    find "$dir" -type f 2>/dev/null | sed "s|^$dir/||" | sort
}

# Generate diff report
generate_diff_report() {
    log_info "Analyzing bootstrap differences..."
    log_info "Old bootstrap: $OLD_BOOTSTRAP"
    log_info "New bootstrap: $NEW_BOOTSTRAP"
    
    # Create temp files for file lists
    old_files_list="${TMPDIR:-/tmp}/old_files_$$"
    new_files_list="${TMPDIR:-/tmp}/new_files_$$"
    
    cleanup() {
        rm -f "$old_files_list" "$new_files_list" 2>/dev/null
    }
    trap cleanup EXIT
    
    # List files in each bootstrap
    list_files "$OLD_BOOTSTRAP" > "$old_files_list"
    list_files "$NEW_BOOTSTRAP" > "$new_files_list"
    
    old_count=$(wc -l < "$old_files_list" | tr -d ' ')
    new_count=$(wc -l < "$new_files_list" | tr -d ' ')
    
    # Find added, removed, and common files
    added_files=$(comm -13 "$old_files_list" "$new_files_list")
    removed_files=$(comm -23 "$old_files_list" "$new_files_list")
    common_files=$(comm -12 "$old_files_list" "$new_files_list")
    
    added_count=$(echo "$added_files" | grep -c . || echo "0")
    removed_count=$(echo "$removed_files" | grep -c . || echo "0")
    common_count=$(echo "$common_files" | grep -c . || echo "0")
    
    # Find changed files (same name, different content/size)
    changed_files=""
    changed_count=0
    
    echo "$common_files" | while IFS= read -r file; do
        [ -z "$file" ] && continue
        
        old_file="$OLD_BOOTSTRAP/$file"
        new_file="$NEW_BOOTSTRAP/$file"
        
        if [ -f "$old_file" ] && [ -f "$new_file" ]; then
            old_size=$(get_file_size "$old_file")
            new_size=$(get_file_size "$new_file")
            
            if [ "$old_size" != "$new_size" ]; then
                echo "$file|$old_size|$new_size"
            elif ! cmp -s "$old_file" "$new_file" 2>/dev/null; then
                echo "$file|$old_size|$new_size|content"
            fi
        fi
    done > "${TMPDIR:-/tmp}/changed_files_$$"
    
    changed_count=$(wc -l < "${TMPDIR:-/tmp}/changed_files_$$" | tr -d ' ')
    
    # Calculate total sizes
    old_total_size=0
    new_total_size=0
    
    while IFS= read -r file; do
        [ -z "$file" ] && continue
        size=$(get_file_size "$OLD_BOOTSTRAP/$file")
        old_total_size=$((old_total_size + size))
    done < "$old_files_list"
    
    while IFS= read -r file; do
        [ -z "$file" ] && continue
        size=$(get_file_size "$NEW_BOOTSTRAP/$file")
        new_total_size=$((new_total_size + size))
    done < "$new_files_list"
    
    size_diff=$((new_total_size - old_total_size))
    
    # Generate Markdown report
    cat > "$OUTPUT_FILE" << EOF
# Bootstrap Diff Report

**Generated:** $(date -u '+%Y-%m-%d %H:%M:%S UTC')

## Summary

| Metric | Old Bootstrap | New Bootstrap | Change |
|--------|---------------|---------------|--------|
| Total Files | $old_count | $new_count | $((new_count - old_count)) |
| Total Size | $(format_size $old_total_size) | $(format_size $new_total_size) | $(format_size $size_diff) |
| Added Files | - | $added_count | +$added_count |
| Removed Files | $removed_count | - | -$removed_count |
| Changed Files | - | $changed_count | $changed_count |

EOF

    # Added files section
    if [ -n "$added_files" ] && [ "$added_count" -gt 0 ]; then
        cat >> "$OUTPUT_FILE" << EOF
## Added Files ($added_count)

<details>
<summary>Click to expand</summary>

| File | Size |
|------|------|
EOF
        echo "$added_files" | head -100 | while IFS= read -r file; do
            [ -z "$file" ] && continue
            size=$(get_file_size "$NEW_BOOTSTRAP/$file")
            echo "| \`$file\` | $(format_size $size) |" >> "$OUTPUT_FILE"
        done
        
        if [ "$added_count" -gt 100 ]; then
            echo "" >> "$OUTPUT_FILE"
            echo "*... and $((added_count - 100)) more files*" >> "$OUTPUT_FILE"
        fi
        
        echo "</details>" >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
    fi
    
    # Removed files section
    if [ -n "$removed_files" ] && [ "$removed_count" -gt 0 ]; then
        cat >> "$OUTPUT_FILE" << EOF
## Removed Files ($removed_count)

<details>
<summary>Click to expand</summary>

| File | Original Size |
|------|---------------|
EOF
        echo "$removed_files" | head -100 | while IFS= read -r file; do
            [ -z "$file" ] && continue
            size=$(get_file_size "$OLD_BOOTSTRAP/$file")
            echo "| \`$file\` | $(format_size $size) |" >> "$OUTPUT_FILE"
        done
        
        if [ "$removed_count" -gt 100 ]; then
            echo "" >> "$OUTPUT_FILE"
            echo "*... and $((removed_count - 100)) more files*" >> "$OUTPUT_FILE"
        fi
        
        echo "</details>" >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
    fi
    
    # Changed files section
    if [ -s "${TMPDIR:-/tmp}/changed_files_$$" ]; then
        cat >> "$OUTPUT_FILE" << EOF
## Changed Files ($changed_count)

<details>
<summary>Click to expand</summary>

| File | Old Size | New Size | Difference |
|------|----------|----------|------------|
EOF
        head -100 "${TMPDIR:-/tmp}/changed_files_$$" | while IFS='|' read -r file old_size new_size content_flag; do
            [ -z "$file" ] && continue
            diff_size=$((new_size - old_size))
            if [ -n "$content_flag" ]; then
                echo "| \`$file\` | $(format_size $old_size) | $(format_size $new_size) | Content changed |" >> "$OUTPUT_FILE"
            elif [ "$diff_size" -ge 0 ]; then
                echo "| \`$file\` | $(format_size $old_size) | $(format_size $new_size) | +$(format_size $diff_size) |" >> "$OUTPUT_FILE"
            else
                echo "| \`$file\` | $(format_size $old_size) | $(format_size $new_size) | -$(format_size ${diff_size#-}) |" >> "$OUTPUT_FILE"
            fi
        done
        
        if [ "$changed_count" -gt 100 ]; then
            echo "" >> "$OUTPUT_FILE"
            echo "*... and $((changed_count - 100)) more files*" >> "$OUTPUT_FILE"
        fi
        
        echo "</details>" >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
    fi
    
    # Package version changes
    cat >> "$OUTPUT_FILE" << EOF
## Package Analysis

### Key Packages

| Package | Status |
|---------|--------|
EOF

    # Check key packages
    for pkg in apt dpkg coreutils bash; do
        if echo "$added_files" | grep -q "/$pkg" || echo "$added_files" | grep -q "^$pkg"; then
            echo "| \`$pkg\` | ✅ Added |" >> "$OUTPUT_FILE"
        elif echo "$removed_files" | grep -q "/$pkg" || echo "$removed_files" | grep -q "^$pkg"; then
            echo "| \`$pkg\` | ❌ Removed |" >> "$OUTPUT_FILE"
        elif grep -q "/$pkg\|^$pkg" "${TMPDIR:-/tmp}/changed_files_$$" 2>/dev/null; then
            echo "| \`$pkg\` | 🔄 Updated |" >> "$OUTPUT_FILE"
        else
            echo "| \`$pkg\` | ➖ Unchanged |" >> "$OUTPUT_FILE"
        fi
    done
    
    # Prefix validation check
    cat >> "$OUTPUT_FILE" << EOF

## Prefix Validation

EOF

    # Check for com.termux (without .kotlin) in new bootstrap
    prefix_issues=$(find "$NEW_BOOTSTRAP" -type f \( -name "*.sh" -o -name "*.conf" -o -name "*.list" -o -name "sources.list" \) 2>/dev/null | \
        xargs grep -l 'com\.termux' 2>/dev/null | \
        xargs grep -L 'com\.termux\.kotlin' 2>/dev/null || true)
    
    if [ -n "$prefix_issues" ]; then
        echo "⚠️ **Warning:** Found files with potential prefix issues:" >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
        echo '```' >> "$OUTPUT_FILE"
        echo "$prefix_issues" >> "$OUTPUT_FILE"
        echo '```' >> "$OUTPUT_FILE"
    else
        echo "✅ No prefix issues detected. All paths use \`com.termux.kotlin\`." >> "$OUTPUT_FILE"
    fi
    
    # Footer
    cat >> "$OUTPUT_FILE" << EOF

---

*Report generated by \`bootstrap-diff.sh\`*
EOF

    rm -f "${TMPDIR:-/tmp}/changed_files_$$"
    
    log_info "Report generated: $OUTPUT_FILE"
    
    # Output summary for GitHub Actions
    if [ -n "$GITHUB_OUTPUT" ]; then
        echo "added_count=$added_count" >> "$GITHUB_OUTPUT"
        echo "removed_count=$removed_count" >> "$GITHUB_OUTPUT"
        echo "changed_count=$changed_count" >> "$GITHUB_OUTPUT"
        echo "size_diff=$size_diff" >> "$GITHUB_OUTPUT"
        echo "report_file=$OUTPUT_FILE" >> "$GITHUB_OUTPUT"
    fi
    
    # Print summary to console
    echo ""
    echo "=========================================="
    echo "  Bootstrap Diff Summary"
    echo "=========================================="
    echo "  Added files:   $added_count"
    echo "  Removed files: $removed_count"
    echo "  Changed files: $changed_count"
    echo "  Size change:   $(format_size $size_diff)"
    echo "=========================================="
}

# Validate inputs
validate_inputs() {
    if [ ! -d "$OLD_BOOTSTRAP" ]; then
        log_error "Old bootstrap directory not found: $OLD_BOOTSTRAP"
        exit 1
    fi
    
    if [ ! -d "$NEW_BOOTSTRAP" ]; then
        log_error "New bootstrap directory not found: $NEW_BOOTSTRAP"
        exit 1
    fi
}

# Print usage
usage() {
    echo "Usage: $0 [OLD_BOOTSTRAP] [NEW_BOOTSTRAP] [OUTPUT_FILE]"
    echo ""
    echo "Compares two bootstrap directories and generates a Markdown diff report."
    echo ""
    echo "Arguments:"
    echo "  OLD_BOOTSTRAP   Path to old bootstrap directory (default: old_bootstrap)"
    echo "  NEW_BOOTSTRAP   Path to new bootstrap directory (default: new_bootstrap)"
    echo "  OUTPUT_FILE     Path to output Markdown file (default: bootstrap-diff-report.md)"
    echo ""
    echo "Example:"
    echo "  $0 /path/to/old /path/to/new report.md"
}

# Main
case "${1:-}" in
    --help|-h)
        usage
        exit 0
        ;;
esac

validate_inputs
generate_diff_report
