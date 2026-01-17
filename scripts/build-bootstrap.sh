#!/bin/bash
# Build custom bootstrap with com.termux.kotlin paths
# This script builds apt and dpkg from source with native paths

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR="${ROOT_DIR}/app/src/main/cpp"
WORK_DIR="${WORK_DIR:-/tmp/termux-kotlin-bootstrap}"

# Architectures to build
ARCHITECTURES=("aarch64" "arm" "x86_64" "i686")

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check if running in Docker or has Docker available
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker is required but not installed"
        exit 1
    fi
    log_info "Docker available"
}

# Download upstream Termux bootstrap
download_upstream_bootstrap() {
    local arch=$1
    local url="https://github.com/AstroSnail/termux-bootstrap/releases/latest/download/bootstrap-${arch}.zip"
    local output="${WORK_DIR}/upstream/bootstrap-${arch}.zip"
    
    mkdir -p "${WORK_DIR}/upstream"
    
    if [[ -f "$output" ]]; then
        log_info "Bootstrap for $arch already downloaded"
        return 0
    fi
    
    log_info "Downloading bootstrap for $arch..."
    curl -L -o "$output" "$url" || {
        log_error "Failed to download bootstrap for $arch"
        return 1
    }
}

# Build apt/dpkg from source using termux-packages
build_native_packages() {
    local arch=$1
    local container_name="termux-builder-${arch}"
    
    log_info "Building native packages for $arch..."
    
    # Remove existing container if exists
    docker rm -f "$container_name" 2>/dev/null || true
    
    # Run the build
    docker run --name "$container_name" \
        termux/package-builder \
        bash -c "
            cd /home/builder/termux-packages
            
            # Set package name to com.termux.kotlin
            sed -i 's/TERMUX_APP__PACKAGE_NAME=\"com.termux\"/TERMUX_APP__PACKAGE_NAME=\"com.termux.kotlin\"/' scripts/properties.sh
            
            # Verify the change
            grep 'TERMUX_APP__PACKAGE_NAME=' scripts/properties.sh
            
            # Clean and build
            ./clean.sh 2>/dev/null || true
            ./build-package.sh -a $arch apt
            
            echo 'BUILD COMPLETE'
        "
    
    # Extract built packages
    mkdir -p "${WORK_DIR}/native-packages/${arch}"
    docker cp "${container_name}:/home/builder/termux-packages/output/." "${WORK_DIR}/native-packages/${arch}/"
    
    # Cleanup container
    docker rm -f "$container_name" 2>/dev/null || true
    
    log_info "Native packages for $arch extracted to ${WORK_DIR}/native-packages/${arch}"
}

# Process bootstrap: replace com.termux with com.termux.kotlin in text files
process_bootstrap_text() {
    local arch=$1
    local extract_dir="${WORK_DIR}/extracted/${arch}"
    
    log_info "Processing text files for $arch..."
    
    mkdir -p "$extract_dir"
    unzip -q -o "${WORK_DIR}/upstream/bootstrap-${arch}.zip" -d "$extract_dir"
    
    # Replace in text files only
    find "$extract_dir" -type f | while read -r file; do
        if file "$file" | grep -q "text\|ASCII\|script"; then
            if grep -q "com\.termux" "$file" 2>/dev/null; then
                sed -i 's/com\.termux\([^.]\)/com.termux.kotlin\1/g' "$file"
                sed -i 's/com\.termux$/com.termux.kotlin/g' "$file"
            fi
        fi
    done
    
    log_info "Text files processed for $arch"
}

# Replace apt/dpkg binaries with native builds
replace_native_binaries() {
    local arch=$1
    local extract_dir="${WORK_DIR}/extracted/${arch}"
    local native_dir="${WORK_DIR}/native-packages/${arch}"
    
    log_info "Replacing binaries with native builds for $arch..."
    
    # Find and extract apt deb
    local apt_deb=$(find "$native_dir" -name "apt_*.deb" -not -name "*ftparchive*" -not -name "*static*" | head -1)
    local dpkg_deb=$(find "$native_dir" -name "dpkg_*.deb" -not -name "*perl*" -not -name "*scanpackages*" | head -1)
    
    if [[ -z "$apt_deb" ]]; then
        log_error "apt deb not found for $arch"
        return 1
    fi
    
    # Extract apt
    local apt_extract="${WORK_DIR}/apt-extract/${arch}"
    mkdir -p "$apt_extract"
    cd "$apt_extract"
    ar x "$apt_deb"
    tar -xf data.tar.xz
    
    # Find the usr directory in the extracted apt
    local apt_usr=$(find "$apt_extract" -type d -name "usr" -path "*/com.termux.kotlin/*" | head -1)
    
    if [[ -n "$apt_usr" ]]; then
        # Replace apt binaries
        cp -v "$apt_usr/bin/apt"* "$extract_dir/bin/" 2>/dev/null || true
        cp -v "$apt_usr/lib/libapt-pkg.so" "$extract_dir/lib/" 2>/dev/null || true
        cp -v "$apt_usr/lib/libapt-private.so" "$extract_dir/lib/" 2>/dev/null || true
        cp -v "$apt_usr/lib/apt/methods/"* "$extract_dir/lib/apt/methods/" 2>/dev/null || true
        log_info "APT binaries replaced for $arch"
    fi
    
    # Extract and replace dpkg if available
    if [[ -n "$dpkg_deb" ]]; then
        local dpkg_extract="${WORK_DIR}/dpkg-extract/${arch}"
        mkdir -p "$dpkg_extract"
        cd "$dpkg_extract"
        ar x "$dpkg_deb"
        tar -xf data.tar.xz
        
        local dpkg_usr=$(find "$dpkg_extract" -type d -name "usr" -path "*/com.termux.kotlin/*" | head -1)
        
        if [[ -n "$dpkg_usr" ]]; then
            cp -v "$dpkg_usr/bin/dpkg"* "$extract_dir/bin/" 2>/dev/null || true
            log_info "DPKG binaries replaced for $arch"
        fi
    fi
}

# Create final bootstrap zip
create_bootstrap_zip() {
    local arch=$1
    local extract_dir="${WORK_DIR}/extracted/${arch}"
    local output_file="${OUTPUT_DIR}/bootstrap-${arch}.zip"
    
    log_info "Creating bootstrap zip for $arch..."
    
    cd "$extract_dir"
    rm -f "$output_file"
    zip -q -r "$output_file" .
    
    log_info "Created: $output_file ($(du -h "$output_file" | cut -f1))"
}

# Verify bootstrap has correct paths
verify_bootstrap() {
    local arch=$1
    local extract_dir="${WORK_DIR}/extracted/${arch}"
    
    log_info "Verifying bootstrap for $arch..."
    
    # Check libapt-pkg.so for correct paths
    local libapt="${extract_dir}/lib/libapt-pkg.so"
    if [[ -f "$libapt" ]]; then
        if strings "$libapt" | grep -q "com.termux.kotlin"; then
            log_info "✅ $arch: libapt-pkg.so has com.termux.kotlin paths"
        else
            log_warn "⚠️  $arch: libapt-pkg.so may still have old paths"
        fi
        
        if strings "$libapt" | grep "com.termux[^.]" | grep -v "kotlin" | head -1; then
            log_warn "⚠️  $arch: Found old com.termux paths in libapt-pkg.so"
        fi
    fi
}

# Main build process
main() {
    local arch="${1:-all}"
    
    log_info "Starting bootstrap build process"
    log_info "Work directory: $WORK_DIR"
    log_info "Output directory: $OUTPUT_DIR"
    
    check_docker
    
    mkdir -p "$WORK_DIR" "$OUTPUT_DIR"
    
    if [[ "$arch" == "all" ]]; then
        for a in "${ARCHITECTURES[@]}"; do
            log_info "=== Processing $a ==="
            download_upstream_bootstrap "$a"
            build_native_packages "$a"
            process_bootstrap_text "$a"
            replace_native_binaries "$a"
            verify_bootstrap "$a"
            create_bootstrap_zip "$a"
        done
    else
        download_upstream_bootstrap "$arch"
        build_native_packages "$arch"
        process_bootstrap_text "$arch"
        replace_native_binaries "$arch"
        verify_bootstrap "$arch"
        create_bootstrap_zip "$arch"
    fi
    
    log_info "Bootstrap build complete!"
    ls -lh "$OUTPUT_DIR"/bootstrap-*.zip
}

# Run main with provided arguments
main "$@"
