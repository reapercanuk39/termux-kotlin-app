#!/bin/bash
# APK Debugging Tools Installation Script
# Installs open-source APK reverse engineering and debugging tools
#
# Tools installed:
#   - jadx (Java decompiler)
#   - apktool (APK disassembly/rebuild)
#   - smali/baksmali (DEX assembler/disassembler)
#   - dex2jar (DEX to JAR converter)
#   - uber-apk-signer (APK signing utility)
#   - androguard (Python APK analysis)
#   - frida-tools (Dynamic instrumentation)
#   - objection (Runtime mobile exploration)
#   - quark-engine (Android malware analysis)
#   - apkleaks (APK secrets scanner)
#   - aapt2 (Android Asset Packaging Tool)
#   - bytecode-viewer (Multi-tool GUI)
#   - apk-mitm (HTTPS inspection)
#   - ghidra (Reverse engineering suite)

set -e

# Configuration
INSTALL_DIR="${INSTALL_DIR:-/opt/apk-tools}"
BIN_DIR="$INSTALL_DIR/bin"
LIB_DIR="$INSTALL_DIR/lib"
TMP_DIR="${TMPDIR:-/tmp}/apk-tools-install"
PROFILE_SCRIPT="/etc/profile.d/apk-tools.sh"

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
    mkdir -p "$INSTALL_DIR" "$BIN_DIR" "$LIB_DIR" "$TMP_DIR"
}

# Install system dependencies
install_dependencies() {
    log_section "Installing System Dependencies"
    
    apt-get update -qq
    apt-get install -y --no-install-recommends \
        openjdk-17-jdk \
        python3 \
        python3-pip \
        python3-venv \
        git \
        curl \
        wget \
        unzip \
        zip \
        build-essential \
        libffi-dev \
        libssl-dev \
        android-sdk-libsparse-utils \
        zipalign \
        >/dev/null 2>&1 || apt-get install -y openjdk-17-jdk python3 python3-pip git curl wget unzip
    
    log_info "System dependencies installed"
}

# Get latest GitHub release version
get_latest_release() {
    local repo="$1"
    curl -s "https://api.github.com/repos/$repo/releases/latest" | grep '"tag_name"' | sed -E 's/.*"([^"]+)".*/\1/'
}

# Download with retry
download_file() {
    local url="$1"
    local output="$2"
    local retries=3
    
    for i in $(seq 1 $retries); do
        if curl -fsSL -o "$output" "$url"; then
            return 0
        fi
        log_warn "Download attempt $i failed, retrying..."
        sleep 2
    done
    
    log_error "Failed to download: $url"
    return 1
}

# Install JADX
install_jadx() {
    log_section "Installing JADX"
    
    local version=$(get_latest_release "skylot/jadx")
    local url="https://github.com/skylot/jadx/releases/download/${version}/jadx-${version#v}.zip"
    local install_path="$INSTALL_DIR/jadx"
    
    if [ -f "$BIN_DIR/jadx" ] && "$BIN_DIR/jadx" --version 2>/dev/null | grep -q "${version#v}"; then
        log_info "JADX ${version} already installed"
        return 0
    fi
    
    log_info "Downloading JADX ${version}..."
    download_file "$url" "$TMP_DIR/jadx.zip"
    
    rm -rf "$install_path"
    mkdir -p "$install_path"
    unzip -q "$TMP_DIR/jadx.zip" -d "$install_path"
    
    ln -sf "$install_path/bin/jadx" "$BIN_DIR/jadx"
    ln -sf "$install_path/bin/jadx-gui" "$BIN_DIR/jadx-gui"
    chmod +x "$install_path/bin/"*
    
    log_info "JADX ${version} installed"
}

# Install Apktool
install_apktool() {
    log_section "Installing Apktool"
    
    local version=$(get_latest_release "iBotPeaches/Apktool")
    local url="https://github.com/iBotPeaches/Apktool/releases/download/${version}/apktool_${version#v}.jar"
    
    if [ -f "$LIB_DIR/apktool.jar" ]; then
        log_info "Apktool already installed"
        return 0
    fi
    
    log_info "Downloading Apktool ${version}..."
    download_file "$url" "$LIB_DIR/apktool.jar"
    
    # Create wrapper script
    cat > "$BIN_DIR/apktool" << 'EOF'
#!/bin/bash
java -jar /opt/apk-tools/lib/apktool.jar "$@"
EOF
    chmod +x "$BIN_DIR/apktool"
    
    log_info "Apktool ${version} installed"
}

# Install smali/baksmali
install_smali() {
    log_section "Installing smali/baksmali"
    
    # Note: smali 3.x doesn't have pre-built JARs, use 2.5.2
    local version="v2.5.2"
    local smali_url="https://bitbucket.org/JesusFreke/smali/downloads/smali-2.5.2.jar"
    local baksmali_url="https://bitbucket.org/JesusFreke/smali/downloads/baksmali-2.5.2.jar"
    
    if [ -f "$LIB_DIR/smali.jar" ] && [ -f "$LIB_DIR/baksmali.jar" ]; then
        log_info "smali/baksmali already installed"
        return 0
    fi
    
    log_info "Downloading smali/baksmali 2.5.2..."
    download_file "$smali_url" "$LIB_DIR/smali.jar"
    download_file "$baksmali_url" "$LIB_DIR/baksmali.jar"
    
    # Create wrapper scripts
    cat > "$BIN_DIR/smali" << 'EOF'
#!/bin/bash
java -jar /opt/apk-tools/lib/smali.jar "$@"
EOF
    
    cat > "$BIN_DIR/baksmali" << 'EOF'
#!/bin/bash
java -jar /opt/apk-tools/lib/baksmali.jar "$@"
EOF
    
    chmod +x "$BIN_DIR/smali" "$BIN_DIR/baksmali"
    
    log_info "smali/baksmali 2.5.2 installed"
}

# Install dex2jar
install_dex2jar() {
    log_section "Installing dex2jar"
    
    local version=$(get_latest_release "pxb1988/dex2jar")
    local url="https://github.com/pxb1988/dex2jar/releases/download/${version}/dex-tools-${version}.zip"
    local install_path="$INSTALL_DIR/dex2jar"
    
    if [ -f "$BIN_DIR/d2j-dex2jar.sh" ]; then
        log_info "dex2jar already installed"
        return 0
    fi
    
    log_info "Downloading dex2jar ${version}..."
    download_file "$url" "$TMP_DIR/dex2jar.zip"
    
    rm -rf "$install_path"
    mkdir -p "$install_path"
    unzip -q "$TMP_DIR/dex2jar.zip" -d "$TMP_DIR"
    mv "$TMP_DIR/dex-tools-${version}"/* "$install_path/" 2>/dev/null || mv "$TMP_DIR/dex2jar-${version}"/* "$install_path/" 2>/dev/null || true
    
    chmod +x "$install_path"/*.sh 2>/dev/null || true
    
    for script in "$install_path"/*.sh; do
        [ -f "$script" ] && ln -sf "$script" "$BIN_DIR/$(basename "$script")"
    done
    
    log_info "dex2jar ${version} installed"
}

# Install uber-apk-signer
install_uber_apk_signer() {
    log_section "Installing uber-apk-signer"
    
    local version=$(get_latest_release "patrickfav/uber-apk-signer")
    local url="https://github.com/patrickfav/uber-apk-signer/releases/download/${version}/uber-apk-signer-${version#v}.jar"
    
    if [ -f "$LIB_DIR/uber-apk-signer.jar" ]; then
        log_info "uber-apk-signer already installed"
        return 0
    fi
    
    log_info "Downloading uber-apk-signer ${version}..."
    download_file "$url" "$LIB_DIR/uber-apk-signer.jar"
    
    cat > "$BIN_DIR/uber-apk-signer" << 'EOF'
#!/bin/bash
java -jar /opt/apk-tools/lib/uber-apk-signer.jar "$@"
EOF
    chmod +x "$BIN_DIR/uber-apk-signer"
    
    log_info "uber-apk-signer ${version} installed"
}

# Install Python-based tools
install_python_tools() {
    log_section "Installing Python APK Tools"
    
    # Create virtual environment
    local venv_path="$INSTALL_DIR/venv"
    if [ ! -d "$venv_path" ]; then
        python3 -m venv "$venv_path"
    fi
    
    source "$venv_path/bin/activate"
    
    # Upgrade pip
    pip install --upgrade pip >/dev/null 2>&1
    
    # Install androguard
    log_info "Installing androguard..."
    pip install androguard >/dev/null 2>&1
    
    # Install frida-tools
    log_info "Installing frida-tools..."
    pip install frida-tools >/dev/null 2>&1 || log_warn "frida-tools installation failed (may need specific platform)"
    
    # Install objection
    log_info "Installing objection..."
    pip install objection >/dev/null 2>&1 || log_warn "objection installation failed"
    
    # Install quark-engine
    log_info "Installing quark-engine..."
    pip install quark-engine >/dev/null 2>&1
    
    # Install apkleaks
    log_info "Installing apkleaks..."
    pip install apkleaks >/dev/null 2>&1
    
    # Install apk-mitm dependencies
    log_info "Installing apk-mitm..."
    pip install mitmproxy >/dev/null 2>&1
    
    deactivate
    
    # Create wrapper scripts for Python tools
    for tool in androguard frida frida-ps objection quark apkleaks; do
        if [ -f "$venv_path/bin/$tool" ]; then
            cat > "$BIN_DIR/$tool" << EOF
#!/bin/bash
source $venv_path/bin/activate
$tool "\$@"
EOF
            chmod +x "$BIN_DIR/$tool"
        fi
    done
    
    log_info "Python APK tools installed"
}

# Install bytecode-viewer
install_bytecode_viewer() {
    log_section "Installing Bytecode Viewer"
    
    local version=$(get_latest_release "Konloch/bytecode-viewer")
    local url="https://github.com/Konloch/bytecode-viewer/releases/download/${version}/Bytecode-Viewer-${version#v}.jar"
    
    if [ -f "$LIB_DIR/bytecode-viewer.jar" ]; then
        log_info "Bytecode Viewer already installed"
        return 0
    fi
    
    log_info "Downloading Bytecode Viewer ${version}..."
    download_file "$url" "$LIB_DIR/bytecode-viewer.jar" || {
        # Try alternative naming
        url="https://github.com/Konloch/bytecode-viewer/releases/download/${version}/Bytecode-Viewer-${version}.jar"
        download_file "$url" "$LIB_DIR/bytecode-viewer.jar"
    }
    
    cat > "$BIN_DIR/bytecode-viewer" << 'EOF'
#!/bin/bash
java -jar /opt/apk-tools/lib/bytecode-viewer.jar "$@"
EOF
    chmod +x "$BIN_DIR/bytecode-viewer"
    
    log_info "Bytecode Viewer installed"
}

# Install Ghidra
install_ghidra() {
    log_section "Installing Ghidra"
    
    local install_path="$INSTALL_DIR/ghidra"
    
    if [ -d "$install_path" ] && [ -f "$install_path/ghidraRun" ]; then
        log_info "Ghidra already installed"
        return 0
    fi
    
    # Get latest Ghidra release
    local version=$(curl -s "https://api.github.com/repos/NationalSecurityAgency/ghidra/releases/latest" | grep '"tag_name"' | sed -E 's/.*"Ghidra_([^"]+)".*/\1/' | head -1)
    
    if [ -z "$version" ]; then
        version="11.0.1"  # Fallback version
    fi
    
    local url="https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_${version}_build/ghidra_${version}_PUBLIC_$(date +%Y%m%d).zip"
    
    log_info "Downloading Ghidra ${version}..."
    # Try to find the actual release URL
    local release_url=$(curl -s "https://api.github.com/repos/NationalSecurityAgency/ghidra/releases/latest" | grep "browser_download_url.*\.zip" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
    
    if [ -n "$release_url" ]; then
        download_file "$release_url" "$TMP_DIR/ghidra.zip" || {
            log_warn "Ghidra download failed, skipping..."
            return 0
        }
        
        rm -rf "$install_path"
        mkdir -p "$install_path"
        unzip -q "$TMP_DIR/ghidra.zip" -d "$TMP_DIR"
        mv "$TMP_DIR"/ghidra_*/* "$install_path/" 2>/dev/null || true
        
        ln -sf "$install_path/ghidraRun" "$BIN_DIR/ghidra"
        chmod +x "$install_path/ghidraRun"
        
        log_info "Ghidra installed"
    else
        log_warn "Could not find Ghidra release URL, skipping..."
    fi
}

# Install aapt2 from Android SDK
install_aapt2() {
    log_section "Installing aapt2"
    
    if command -v aapt2 >/dev/null 2>&1; then
        log_info "aapt2 already available in PATH"
        return 0
    fi
    
    # Check if Android SDK is available
    if [ -d "/root/android-sdk" ]; then
        local aapt2_path=$(find /root/android-sdk -name "aapt2" -type f 2>/dev/null | head -1)
        if [ -n "$aapt2_path" ]; then
            ln -sf "$aapt2_path" "$BIN_DIR/aapt2"
            log_info "aapt2 linked from Android SDK"
            return 0
        fi
    fi
    
    # Try to install from apt
    apt-get install -y aapt 2>/dev/null || log_warn "aapt2 not available via apt"
    
    log_info "aapt2 setup complete"
}

# Install APK Analyzer (from Android SDK command line tools)
install_apk_analyzer() {
    log_section "Installing APK Analyzer"
    
    if [ -d "/root/android-sdk/cmdline-tools" ]; then
        local analyzer=$(find /root/android-sdk -name "apkanalyzer" -type f 2>/dev/null | head -1)
        if [ -n "$analyzer" ]; then
            ln -sf "$analyzer" "$BIN_DIR/apkanalyzer"
            log_info "apkanalyzer linked from Android SDK"
        fi
    else
        log_warn "Android SDK not found, apkanalyzer not available"
    fi
}

# Setup PATH
setup_path() {
    log_section "Setting up PATH"
    
    cat > "$PROFILE_SCRIPT" << EOF
# APK Debugging Tools
export APK_TOOLS_HOME="$INSTALL_DIR"
export PATH="\$APK_TOOLS_HOME/bin:\$PATH"
EOF
    
    chmod +x "$PROFILE_SCRIPT"
    
    # Also add to bashrc if not already present
    if ! grep -q "APK_TOOLS_HOME" ~/.bashrc 2>/dev/null; then
        echo "" >> ~/.bashrc
        echo "# APK Debugging Tools" >> ~/.bashrc
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
    
    echo "APK Tools installed in: $INSTALL_DIR"
    echo ""
    echo "Tools available:"
    
    for tool in jadx jadx-gui apktool smali baksmali d2j-dex2jar.sh uber-apk-signer \
                androguard frida objection quark apkleaks bytecode-viewer ghidra aapt2 apkanalyzer; do
        if [ -f "$BIN_DIR/$tool" ] || command -v "$tool" >/dev/null 2>&1; then
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
    log_section "APK Debugging Tools Installer"
    
    check_root
    setup_dirs
    install_dependencies
    
    install_jadx
    install_apktool
    install_smali
    install_dex2jar
    install_uber_apk_signer
    install_python_tools
    install_bytecode_viewer
    install_ghidra
    install_aapt2
    install_apk_analyzer
    
    setup_path
    cleanup
    print_summary
    
    log_info "APK tools installation complete!"
}

# Run
main "$@"
