#!/bin/bash
# ISO Debugging Tools Installation Script
# Installs open-source ISO analysis and debugging tools
#
# Tools installed:
#   - xorriso (ISO creation/modification)
#   - isoinfo (ISO inspection from cdrkit)
#   - 7z/p7zip (Archive extraction)
#   - binwalk (Firmware/binary analysis)
#   - sleuthkit (Forensic analysis)
#   - squashfs-tools (SquashFS extraction)
#   - qemu-img (Disk image tools)
#   - genisoimage (ISO creation)
#   - cdrtools (CD/DVD tools)
#   - fatcat (FAT filesystem tools)
#   - testdisk (Data recovery)
#   - foremost (File carving)

set -e

# Configuration
INSTALL_DIR="${INSTALL_DIR:-/opt/iso-tools}"
BIN_DIR="$INSTALL_DIR/bin"
TMP_DIR="${TMPDIR:-/tmp}/iso-tools-install"
PROFILE_SCRIPT="/etc/profile.d/iso-tools.sh"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_section() { echo -e "\n${BLUE}=== $1 ===${NC}\n"; }

# Check if running as root
check_root() {
    if [ "$EUID" -ne 0 ]; then
        log_error "Please run as root (sudo)"
        exit 1
    fi
}

# Create directories
setup_dirs() {
    log_info "Setting up directories..."
    mkdir -p "$INSTALL_DIR" "$BIN_DIR" "$TMP_DIR"
}

# Install system packages
install_system_packages() {
    log_section "Installing System ISO Tools"
    
    apt-get update -qq
    
    # Core ISO tools
    log_info "Installing xorriso..."
    apt-get install -y xorriso >/dev/null 2>&1 || log_warn "xorriso not available"
    
    log_info "Installing cdrkit (isoinfo)..."
    apt-get install -y genisoimage >/dev/null 2>&1 || log_warn "genisoimage not available"
    
    log_info "Installing cdrtools..."
    apt-get install -y cdrtools 2>/dev/null || apt-get install -y wodim >/dev/null 2>&1 || log_warn "cdrtools not available"
    
    log_info "Installing p7zip..."
    apt-get install -y p7zip-full p7zip-rar >/dev/null 2>&1 || apt-get install -y p7zip >/dev/null 2>&1
    
    log_info "Installing squashfs-tools..."
    apt-get install -y squashfs-tools >/dev/null 2>&1
    
    log_info "Installing qemu-utils..."
    apt-get install -y qemu-utils >/dev/null 2>&1 || log_warn "qemu-utils not available"
    
    log_info "Installing binwalk..."
    apt-get install -y binwalk >/dev/null 2>&1
    
    log_info "Installing sleuthkit..."
    apt-get install -y sleuthkit >/dev/null 2>&1
    
    log_info "Installing testdisk..."
    apt-get install -y testdisk >/dev/null 2>&1
    
    log_info "Installing foremost..."
    apt-get install -y foremost >/dev/null 2>&1 || log_warn "foremost not available"
    
    log_info "Installing fatcat..."
    apt-get install -y fatcat 2>/dev/null || log_warn "fatcat not available"
    
    log_info "Installing fdisk/gdisk..."
    apt-get install -y fdisk gdisk >/dev/null 2>&1
    
    log_info "Installing parted..."
    apt-get install -y parted >/dev/null 2>&1
    
    log_info "Installing dosfstools..."
    apt-get install -y dosfstools >/dev/null 2>&1
    
    log_info "Installing ntfs-3g..."
    apt-get install -y ntfs-3g >/dev/null 2>&1
    
    log_info "Installing e2fsprogs..."
    apt-get install -y e2fsprogs >/dev/null 2>&1
    
    log_info "Installing btrfs-progs..."
    apt-get install -y btrfs-progs >/dev/null 2>&1 || log_warn "btrfs-progs not available"
    
    log_info "Installing fuse tools..."
    apt-get install -y fuse fuseiso >/dev/null 2>&1 || log_warn "fuse tools not available"
    
    log_info "Installing file (magic)..."
    apt-get install -y file >/dev/null 2>&1
    
    log_info "Installing hexdump/xxd..."
    apt-get install -y xxd bsdmainutils >/dev/null 2>&1
    
    log_info "System ISO tools installed"
}

# Install additional binwalk dependencies
install_binwalk_extras() {
    log_section "Installing Binwalk Extras"
    
    # Install extraction dependencies
    apt-get install -y --no-install-recommends \
        mtd-utils \
        gzip \
        bzip2 \
        tar \
        arj \
        lhasa \
        cabextract \
        cramfsswap \
        lzop \
        srecord \
        >/dev/null 2>&1 || log_warn "Some binwalk extras not available"
    
    # Install Python binwalk modules
    if command -v pip3 >/dev/null 2>&1; then
        pip3 install python-lzo >/dev/null 2>&1 || true
    fi
    
    log_info "Binwalk extras installed"
}

# Install UEFITool
install_uefitool() {
    log_section "Installing UEFITool"
    
    if [ -f "$BIN_DIR/UEFITool" ]; then
        log_info "UEFITool already installed"
        return 0
    fi
    
    local version=$(curl -s "https://api.github.com/repos/LongSoft/UEFITool/releases/latest" | grep '"tag_name"' | sed -E 's/.*"([^"]+)".*/\1/')
    
    if [ -z "$version" ]; then
        log_warn "Could not determine UEFITool version, skipping..."
        return 0
    fi
    
    local arch="x86_64"
    local url="https://github.com/LongSoft/UEFITool/releases/download/${version}/UEFITool_NE_${version#A}_linux_${arch}.zip"
    
    log_info "Downloading UEFITool ${version}..."
    if curl -fsSL -o "$TMP_DIR/uefitool.zip" "$url" 2>/dev/null; then
        unzip -q "$TMP_DIR/uefitool.zip" -d "$BIN_DIR" 2>/dev/null || true
        chmod +x "$BIN_DIR/UEFITool"* 2>/dev/null || true
        log_info "UEFITool installed"
    else
        log_warn "UEFITool download failed, skipping..."
    fi
}

# Install ISO mounting helpers
install_mount_helpers() {
    log_section "Installing Mount Helpers"
    
    # Create convenient mount wrapper
    cat > "$BIN_DIR/iso-mount" << 'EOF'
#!/bin/bash
# ISO Mount Helper
# Usage: iso-mount <iso-file> [mount-point]

if [ $# -lt 1 ]; then
    echo "Usage: $0 <iso-file> [mount-point]"
    echo ""
    echo "Examples:"
    echo "  $0 image.iso                    # Mount to /mnt/iso"
    echo "  $0 image.iso /tmp/myiso         # Mount to custom path"
    exit 1
fi

ISO_FILE="$1"
MOUNT_POINT="${2:-/mnt/iso}"

if [ ! -f "$ISO_FILE" ]; then
    echo "Error: ISO file not found: $ISO_FILE"
    exit 1
fi

mkdir -p "$MOUNT_POINT"

echo "Mounting $ISO_FILE to $MOUNT_POINT..."
mount -o loop,ro "$ISO_FILE" "$MOUNT_POINT"

echo "Mounted successfully. To unmount: umount $MOUNT_POINT"
EOF
    chmod +x "$BIN_DIR/iso-mount"
    
    # Create squashfs extractor
    cat > "$BIN_DIR/squashfs-extract" << 'EOF'
#!/bin/bash
# SquashFS Extractor
# Usage: squashfs-extract <squashfs-file> [output-dir]

if [ $# -lt 1 ]; then
    echo "Usage: $0 <squashfs-file> [output-dir]"
    exit 1
fi

SQUASHFS_FILE="$1"
OUTPUT_DIR="${2:-squashfs-root}"

if [ ! -f "$SQUASHFS_FILE" ]; then
    echo "Error: SquashFS file not found: $SQUASHFS_FILE"
    exit 1
fi

echo "Extracting $SQUASHFS_FILE to $OUTPUT_DIR..."
unsquashfs -d "$OUTPUT_DIR" "$SQUASHFS_FILE"

echo "Extraction complete."
EOF
    chmod +x "$BIN_DIR/squashfs-extract"
    
    # Create ISO extractor
    cat > "$BIN_DIR/iso-extract" << 'EOF'
#!/bin/bash
# ISO Extractor
# Usage: iso-extract <iso-file> [output-dir]

if [ $# -lt 1 ]; then
    echo "Usage: $0 <iso-file> [output-dir]"
    echo ""
    echo "Methods:"
    echo "  - 7z (default, preserves all files)"
    echo "  - xorriso (alternative)"
    echo "  - mount+copy (requires root)"
    exit 1
fi

ISO_FILE="$1"
OUTPUT_DIR="${2:-iso-contents}"

if [ ! -f "$ISO_FILE" ]; then
    echo "Error: ISO file not found: $ISO_FILE"
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

echo "Extracting $ISO_FILE to $OUTPUT_DIR..."

# Try 7z first (most compatible)
if command -v 7z >/dev/null 2>&1; then
    7z x -o"$OUTPUT_DIR" "$ISO_FILE" -y
elif command -v xorriso >/dev/null 2>&1; then
    xorriso -osirrox on -indev "$ISO_FILE" -extract / "$OUTPUT_DIR"
else
    echo "Error: No extraction tool available (install p7zip or xorriso)"
    exit 1
fi

echo "Extraction complete."
EOF
    chmod +x "$BIN_DIR/iso-extract"
    
    # Create ISO info script
    cat > "$BIN_DIR/iso-info" << 'EOF'
#!/bin/bash
# ISO Information Tool
# Usage: iso-info <iso-file>

if [ $# -lt 1 ]; then
    echo "Usage: $0 <iso-file>"
    exit 1
fi

ISO_FILE="$1"

if [ ! -f "$ISO_FILE" ]; then
    echo "Error: ISO file not found: $ISO_FILE"
    exit 1
fi

echo "=== ISO Information: $(basename "$ISO_FILE") ==="
echo ""

echo "--- File Info ---"
file "$ISO_FILE"
ls -lh "$ISO_FILE"
echo ""

echo "--- ISO9660 Info ---"
if command -v isoinfo >/dev/null 2>&1; then
    isoinfo -d -i "$ISO_FILE" 2>/dev/null || echo "(isoinfo failed)"
elif command -v xorriso >/dev/null 2>&1; then
    xorriso -indev "$ISO_FILE" -pvd_info 2>/dev/null || echo "(xorriso failed)"
fi
echo ""

echo "--- File Listing (first 50 entries) ---"
if command -v isoinfo >/dev/null 2>&1; then
    isoinfo -l -i "$ISO_FILE" 2>/dev/null | head -50
elif command -v 7z >/dev/null 2>&1; then
    7z l "$ISO_FILE" 2>/dev/null | head -60
fi
echo ""

echo "--- Boot Info ---"
if command -v xorriso >/dev/null 2>&1; then
    xorriso -indev "$ISO_FILE" -report_el_torito as_mkisofs 2>/dev/null || echo "(No El Torito boot record)"
fi
EOF
    chmod +x "$BIN_DIR/iso-info"
    
    log_info "Mount helpers installed"
}

# Install disk image tools
install_disk_image_tools() {
    log_section "Installing Disk Image Tools"
    
    # Create raw to img converter
    cat > "$BIN_DIR/img-info" << 'EOF'
#!/bin/bash
# Disk Image Info Tool
# Usage: img-info <image-file>

if [ $# -lt 1 ]; then
    echo "Usage: $0 <image-file>"
    exit 1
fi

IMG_FILE="$1"

if [ ! -f "$IMG_FILE" ]; then
    echo "Error: Image file not found: $IMG_FILE"
    exit 1
fi

echo "=== Disk Image Information ==="
echo ""

echo "--- File Info ---"
file "$IMG_FILE"
ls -lh "$IMG_FILE"
echo ""

echo "--- Partition Table ---"
if command -v fdisk >/dev/null 2>&1; then
    fdisk -l "$IMG_FILE" 2>/dev/null || echo "(fdisk failed)"
fi
echo ""

if command -v qemu-img >/dev/null 2>&1; then
    echo "--- QEMU Image Info ---"
    qemu-img info "$IMG_FILE" 2>/dev/null || echo "(qemu-img failed)"
fi
EOF
    chmod +x "$BIN_DIR/img-info"
    
    log_info "Disk image tools installed"
}

# Setup PATH
setup_path() {
    log_section "Setting up PATH"
    
    cat > "$PROFILE_SCRIPT" << EOF
# ISO Debugging Tools
export ISO_TOOLS_HOME="$INSTALL_DIR"
export PATH="\$ISO_TOOLS_HOME/bin:\$PATH"
EOF
    
    chmod +x "$PROFILE_SCRIPT"
    
    # Also add to bashrc if not already present
    if ! grep -q "ISO_TOOLS_HOME" ~/.bashrc 2>/dev/null; then
        echo "" >> ~/.bashrc
        echo "# ISO Debugging Tools" >> ~/.bashrc
        echo "source $PROFILE_SCRIPT" >> ~/.bashrc
    fi
    
    log_info "PATH configured in $PROFILE_SCRIPT"
}

# Cleanup
cleanup() {
    log_info "Cleaning up temporary files..."
    rm -rf "$TMP_DIR"
}

# Print summary
print_summary() {
    log_section "Installation Summary"
    
    echo "ISO Tools installed in: $INSTALL_DIR"
    echo ""
    echo "System tools available:"
    
    for tool in xorriso isoinfo genisoimage 7z binwalk fls mmls unsquashfs mksquashfs \
                qemu-img testdisk foremost fdisk gdisk parted file xxd hexdump; do
        if command -v "$tool" >/dev/null 2>&1; then
            echo "  ✅ $tool"
        else
            echo "  ❌ $tool (not installed)"
        fi
    done
    
    echo ""
    echo "Custom helpers available:"
    
    for tool in iso-mount iso-extract iso-info squashfs-extract img-info; do
        if [ -f "$BIN_DIR/$tool" ]; then
            echo "  ✅ $tool"
        else
            echo "  ❌ $tool (not installed)"
        fi
    done
    
    echo ""
    echo "To use tools, run: source $PROFILE_SCRIPT"
    echo "Or start a new terminal session."
}

# Main
main() {
    log_section "ISO Debugging Tools Installer"
    
    check_root
    setup_dirs
    install_system_packages
    install_binwalk_extras
    install_uefitool
    install_mount_helpers
    install_disk_image_tools
    setup_path
    cleanup
    print_summary
    
    log_info "ISO tools installation complete!"
}

# Run
main "$@"
