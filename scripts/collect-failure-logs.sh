#!/bin/sh
# POSIX-compliant failure log collector and patch suggestion generator
# Collects logs from failed CI jobs and generates structured summaries for Copilot

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
JOB_NAME="${JOB_NAME:-unknown}"
FAILURE_DIR="${FAILURE_DIR:-artifacts/failures/$JOB_NAME}"
SUMMARY_FILE="$FAILURE_DIR/SUMMARY.md"
LOG_FILE="$FAILURE_DIR/full-logs.txt"

log_info() {
    printf "${GREEN}[INFO]${NC} %s\n" "$1"
}

log_warn() {
    printf "${YELLOW}[WARN]${NC} %s\n" "$1"
}

log_error() {
    printf "${RED}[ERROR]${NC} %s\n" "$1"
}

# Initialize failure directory
init_failure_dir() {
    log_info "Initializing failure collection for job: $JOB_NAME"
    mkdir -p "$FAILURE_DIR"
    : > "$LOG_FILE"
    : > "$SUMMARY_FILE"
}

# Collect environment info
collect_env_info() {
    log_info "Collecting environment information..."
    
    {
        echo "=== Environment Information ==="
        echo "Date: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
        echo "Job: $JOB_NAME"
        echo "Hostname: $(hostname 2>/dev/null || echo 'unknown')"
        echo "User: $(whoami 2>/dev/null || echo 'unknown')"
        echo "Working Directory: $(pwd)"
        echo ""
        echo "=== Git Information ==="
        if command -v git >/dev/null 2>&1 && git rev-parse --git-dir >/dev/null 2>&1; then
            echo "Branch: $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'unknown')"
            echo "Commit: $(git rev-parse HEAD 2>/dev/null || echo 'unknown')"
            echo "Author: $(git log -1 --format='%an <%ae>' 2>/dev/null || echo 'unknown')"
            echo "Message: $(git log -1 --format='%s' 2>/dev/null || echo 'unknown')"
        else
            echo "Not a git repository"
        fi
        echo ""
        echo "=== CI Environment ==="
        env | grep -E '^(GITHUB_|CI_|RUNNER_|BUILD_|JOB_)' | sort || echo "No CI variables found"
        echo ""
    } >> "$LOG_FILE"
}

# Collect step/command output
collect_step_output() {
    local step_name="$1"
    local output_file="$2"
    
    log_info "Collecting output from step: $step_name"
    
    {
        echo "=== Step: $step_name ==="
        if [ -f "$output_file" ]; then
            cat "$output_file"
        else
            echo "(No output file found: $output_file)"
        fi
        echo ""
    } >> "$LOG_FILE"
}

# Collect from stdin
collect_stdin() {
    local label="${1:-stdin}"
    
    log_info "Collecting output: $label"
    
    {
        echo "=== $label ==="
        cat
        echo ""
    } >> "$LOG_FILE"
}

# Parse error patterns from logs
parse_errors() {
    log_info "Parsing error patterns..."
    
    local errors_file="$FAILURE_DIR/errors.txt"
    
    # Extract common error patterns
    grep -iE "(error|failed|exception|fatal|cannot|undefined|not found|permission denied|segmentation fault)" "$LOG_FILE" 2>/dev/null | \
        head -50 > "$errors_file" || true
    
    # Count errors by type
    local compile_errors=$(grep -ciE "(compile|syntax|parse)" "$errors_file" 2>/dev/null || echo "0")
    local runtime_errors=$(grep -ciE "(runtime|exception|crash|segfault)" "$errors_file" 2>/dev/null || echo "0")
    local permission_errors=$(grep -ciE "(permission|access denied|forbidden)" "$errors_file" 2>/dev/null || echo "0")
    local not_found_errors=$(grep -ciE "(not found|missing|undefined|no such)" "$errors_file" 2>/dev/null || echo "0")
    local network_errors=$(grep -ciE "(network|connection|timeout|refused)" "$errors_file" 2>/dev/null || echo "0")
    
    echo "compile_errors=$compile_errors"
    echo "runtime_errors=$runtime_errors"
    echo "permission_errors=$permission_errors"
    echo "not_found_errors=$not_found_errors"
    echo "network_errors=$network_errors"
}

# Detect likely component from error messages
detect_component() {
    log_info "Detecting failing component..."
    
    local component="unknown"
    
    if grep -qiE "(gradle|android|kotlin|java)" "$LOG_FILE" 2>/dev/null; then
        component="android-build"
    elif grep -qiE "(bootstrap|bootstrap-)" "$LOG_FILE" 2>/dev/null; then
        component="bootstrap"
    elif grep -qiE "(apt|dpkg|package|deb)" "$LOG_FILE" 2>/dev/null; then
        component="package-manager"
    elif grep -qiE "(emulator|adb|device)" "$LOG_FILE" 2>/dev/null; then
        component="emulator"
    elif grep -qiE "(docker|container)" "$LOG_FILE" 2>/dev/null; then
        component="docker"
    elif grep -qiE "(script|bash|shell|sh:)" "$LOG_FILE" 2>/dev/null; then
        component="script"
    elif grep -qiE "(publish|deploy|pages|gh-pages)" "$LOG_FILE" 2>/dev/null; then
        component="publishing"
    elif grep -qiE "(release|tag|version)" "$LOG_FILE" 2>/dev/null; then
        component="release"
    fi
    
    echo "$component"
}

# Extract key error messages
extract_key_errors() {
    log_info "Extracting key error messages..."
    
    local key_errors=""
    
    # Get first 5 unique error lines
    grep -iE "^.*error.*$|^.*failed.*$|^.*exception.*$" "$LOG_FILE" 2>/dev/null | \
        sort -u | head -5 || true
}

# Suggest potential fixes based on error patterns
suggest_fixes() {
    log_info "Generating fix suggestions..."
    
    local suggestions=""
    
    # Check for common patterns and suggest fixes
    if grep -qi "permission denied" "$LOG_FILE" 2>/dev/null; then
        suggestions="${suggestions}
- Check file permissions (chmod +x on scripts)
- Verify the user has necessary access rights"
    fi
    
    if grep -qi "command not found" "$LOG_FILE" 2>/dev/null; then
        suggestions="${suggestions}
- Install missing dependencies
- Check PATH environment variable
- Verify required tools are installed"
    fi
    
    if grep -qi "no such file or directory" "$LOG_FILE" 2>/dev/null; then
        suggestions="${suggestions}
- Verify file paths are correct
- Check if files exist before accessing
- Ensure files are checked into git"
    fi
    
    if grep -qi "network\|connection\|timeout" "$LOG_FILE" 2>/dev/null; then
        suggestions="${suggestions}
- Check network connectivity
- Verify URLs are accessible
- Consider adding retry logic"
    fi
    
    if grep -qiE "com\.termux[^.]" "$LOG_FILE" 2>/dev/null; then
        suggestions="${suggestions}
- **PREFIX VIOLATION DETECTED**
- Replace 'com.termux' with 'com.termux.kotlin'
- Run scripts/validate-prefix.sh to find all violations"
    fi
    
    if grep -qi "out of memory\|OOM\|heap" "$LOG_FILE" 2>/dev/null; then
        suggestions="${suggestions}
- Increase memory limits
- Optimize memory usage
- Consider splitting tasks"
    fi
    
    if grep -qi "docker\|container" "$LOG_FILE" 2>/dev/null; then
        suggestions="${suggestions}
- Check Docker daemon is running
- Verify Docker image exists
- Check container permissions"
    fi
    
    echo "$suggestions"
}

# Generate SUMMARY.md for Copilot consumption
generate_summary() {
    log_info "Generating summary for Copilot..."
    
    local component=$(detect_component)
    local error_stats=$(parse_errors)
    local key_errors=$(extract_key_errors)
    local suggestions=$(suggest_fixes)
    
    # Get step name if available
    local failed_step="${FAILED_STEP:-unknown}"
    
    cat > "$SUMMARY_FILE" << EOF
# CI Failure Summary

## Quick Reference

| Field | Value |
|-------|-------|
| **Job** | \`$JOB_NAME\` |
| **Failed Step** | \`$failed_step\` |
| **Component** | \`$component\` |
| **Time** | $(date -u '+%Y-%m-%d %H:%M:%S UTC') |
| **Commit** | \`$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')\` |

## Error Summary

\`\`\`
$(echo "$error_stats" | head -10)
\`\`\`

## Key Error Messages

\`\`\`
$(echo "$key_errors" | head -10)
\`\`\`

## Likely Component

The failure appears to be in: **$component**

## Files to Investigate

Based on the error patterns, check these files:

EOF

    # Suggest files based on component
    case "$component" in
        android-build)
            cat >> "$SUMMARY_FILE" << EOF
- \`app/build.gradle\`
- \`build.gradle\`
- \`gradle.properties\`
- Java/Kotlin source files mentioned in errors
EOF
            ;;
        bootstrap)
            cat >> "$SUMMARY_FILE" << EOF
- \`scripts/build-bootstrap.sh\`
- \`scripts/build-custom-bootstrap.sh\`
- Bootstrap template files
EOF
            ;;
        package-manager)
            cat >> "$SUMMARY_FILE" << EOF
- \`termux-packages/\` build scripts
- Package build.sh files
- scripts/properties.sh
EOF
            ;;
        emulator)
            cat >> "$SUMMARY_FILE" << EOF
- \`scripts/emulator-smoke-test.sh\`
- APK installation and permissions
- Android emulator configuration
EOF
            ;;
        script)
            cat >> "$SUMMARY_FILE" << EOF
- \`scripts/\` directory
- Shell script syntax
- File permissions
EOF
            ;;
        *)
            cat >> "$SUMMARY_FILE" << EOF
- Check logs for specific file references
- Review recent changes
EOF
            ;;
    esac

    cat >> "$SUMMARY_FILE" << EOF

## Suggested Fixes

$suggestions

## For Copilot

To fix this failure, please:

1. Review the error messages above
2. Check the files listed under "Files to Investigate"
3. Apply the suggested fixes
4. Run the validate-prefix.sh script if prefix issues are suspected
5. Commit and push to re-trigger the pipeline

## Full Logs

See \`full-logs.txt\` in this directory for complete output.

---

*Generated by collect-failure-logs.sh*
EOF

    log_info "Summary written to: $SUMMARY_FILE"
}

# Collect Android emulator logs
collect_emulator_logs() {
    log_info "Collecting emulator logs..."
    
    if command -v adb >/dev/null 2>&1; then
        {
            echo "=== ADB Devices ==="
            adb devices 2>&1 || echo "ADB not available"
            echo ""
            echo "=== Logcat (last 200 lines) ==="
            adb logcat -d "*:W" 2>&1 | tail -200 || echo "No logcat available"
            echo ""
        } >> "$LOG_FILE"
        
        # Save full logcat separately
        adb logcat -d > "$FAILURE_DIR/logcat.txt" 2>/dev/null || true
    fi
}

# Collect Docker logs
collect_docker_logs() {
    log_info "Collecting Docker logs..."
    
    if command -v docker >/dev/null 2>&1; then
        {
            echo "=== Docker Containers ==="
            docker ps -a 2>&1 | head -20 || echo "Docker not available"
            echo ""
            echo "=== Recent Container Logs ==="
            for container in $(docker ps -aq 2>/dev/null | head -3); do
                echo "--- Container: $container ---"
                docker logs --tail 50 "$container" 2>&1 || true
            done
            echo ""
        } >> "$LOG_FILE"
    fi
}

# Collect bootstrap diff if available
collect_bootstrap_diff() {
    log_info "Collecting bootstrap diff..."
    
    if [ -f "$ROOT_DIR/bootstrap-diff-report.md" ]; then
        cp "$ROOT_DIR/bootstrap-diff-report.md" "$FAILURE_DIR/bootstrap-diff.md"
        
        {
            echo "=== Bootstrap Diff ==="
            cat "$ROOT_DIR/bootstrap-diff-report.md"
            echo ""
        } >> "$LOG_FILE"
    fi
}

# Archive all collected logs
create_archive() {
    log_info "Creating log archive..."
    
    local archive_name="failure-logs-$JOB_NAME-$(date +%Y%m%d-%H%M%S).tar.gz"
    
    if command -v tar >/dev/null 2>&1; then
        tar -czf "$FAILURE_DIR/$archive_name" -C "$FAILURE_DIR" . 2>/dev/null || true
        log_info "Archive created: $FAILURE_DIR/$archive_name"
    fi
}

# Print usage
usage() {
    echo "Usage: $0 [OPTIONS] [COMMAND]"
    echo ""
    echo "Collect failure logs and generate summaries for CI debugging."
    echo ""
    echo "Commands:"
    echo "  collect           Collect all available logs (default)"
    echo "  env               Collect environment info only"
    echo "  emulator          Collect emulator logs"
    echo "  docker            Collect Docker logs"
    echo "  summary           Generate summary only (requires existing logs)"
    echo ""
    echo "Options:"
    echo "  --job NAME        Job name for organizing logs"
    echo "  --step NAME       Failed step name"
    echo "  --dir PATH        Output directory for logs"
    echo "  --stdin           Read additional logs from stdin"
    echo "  --help            Show this help message"
    echo ""
    echo "Environment variables:"
    echo "  JOB_NAME          Name of the failed job"
    echo "  FAILED_STEP       Name of the failed step"
    echo "  FAILURE_DIR       Output directory for failure artifacts"
}

# Parse arguments
COMMAND="collect"
READ_STDIN=false

while [ $# -gt 0 ]; do
    case "$1" in
        --job)
            JOB_NAME="$2"
            FAILURE_DIR="artifacts/failures/$JOB_NAME"
            shift 2
            ;;
        --step)
            FAILED_STEP="$2"
            export FAILED_STEP
            shift 2
            ;;
        --dir)
            FAILURE_DIR="$2"
            shift 2
            ;;
        --stdin)
            READ_STDIN=true
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        collect|env|emulator|docker|summary)
            COMMAND="$1"
            shift
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
    init_failure_dir
    
    case "$COMMAND" in
        collect)
            collect_env_info
            
            if [ "$READ_STDIN" = "true" ]; then
                collect_stdin "Command Output"
            fi
            
            collect_emulator_logs
            collect_docker_logs
            collect_bootstrap_diff
            generate_summary
            create_archive
            ;;
        env)
            collect_env_info
            ;;
        emulator)
            collect_emulator_logs
            ;;
        docker)
            collect_docker_logs
            ;;
        summary)
            generate_summary
            ;;
    esac
    
    log_info "Failure logs collected in: $FAILURE_DIR"
    log_info "Summary available at: $SUMMARY_FILE"
    
    # Output for GitHub Actions
    if [ -n "$GITHUB_OUTPUT" ]; then
        echo "failure_dir=$FAILURE_DIR" >> "$GITHUB_OUTPUT"
        echo "summary_file=$SUMMARY_FILE" >> "$GITHUB_OUTPUT"
    fi
}

main
