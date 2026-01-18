#!/bin/bash
# Debug Environment Launcher
# Main menu for APK and ISO debugging tools

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS_DIR="$SCRIPT_DIR/scripts"

# Ensure scripts directory
if [ ! -d "$SCRIPTS_DIR" ]; then
    SCRIPTS_DIR="$(dirname "$SCRIPT_DIR")/scripts"
fi

show_header() {
    clear
    echo -e "${CYAN}"
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║                                                              ║"
    echo "║       🔧 Debug Environment Launcher 🔧                       ║"
    echo "║                                                              ║"
    echo "║       APK & ISO Analysis Workstation                         ║"
    echo "║                                                              ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

show_main_menu() {
    echo -e "${BLUE}Main Menu${NC}"
    echo ""
    echo -e "  ${GREEN}1)${NC} APK Tools Menu"
    echo -e "  ${GREEN}2)${NC} ISO Tools Menu"
    echo -e "  ${GREEN}3)${NC} Environment Validation"
    echo -e "  ${GREEN}4)${NC} Update All Tools"
    echo -e "  ${GREEN}5)${NC} View Tool Registry"
    echo ""
    echo -e "  ${YELLOW}q)${NC} Quit"
    echo ""
    echo -n "Select option: "
}

show_apk_menu() {
    clear
    echo -e "${CYAN}╔══════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║       📱 APK Tools Menu              ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${BLUE}Decompilers:${NC}"
    echo -e "  ${GREEN}1)${NC} JADX (CLI decompiler)"
    echo -e "  ${GREEN}2)${NC} JADX-GUI (graphical)"
    echo -e "  ${GREEN}3)${NC} Bytecode Viewer"
    echo ""
    echo -e "${BLUE}APK Manipulation:${NC}"
    echo -e "  ${GREEN}4)${NC} Apktool (disassemble/rebuild)"
    echo -e "  ${GREEN}5)${NC} smali/baksmali"
    echo -e "  ${GREEN}6)${NC} dex2jar"
    echo -e "  ${GREEN}7)${NC} uber-apk-signer"
    echo ""
    echo -e "${BLUE}Analysis:${NC}"
    echo -e "  ${GREEN}8)${NC} Androguard"
    echo -e "  ${GREEN}9)${NC} APKLeaks"
    echo -e "  ${GREEN}10)${NC} Quark Engine"
    echo ""
    echo -e "${BLUE}Dynamic (requires device):${NC}"
    echo -e "  ${GREEN}11)${NC} Frida"
    echo -e "  ${GREEN}12)${NC} Objection"
    echo ""
    echo -e "${BLUE}Reverse Engineering:${NC}"
    echo -e "  ${GREEN}13)${NC} Ghidra"
    echo ""
    echo -e "  ${YELLOW}b)${NC} Back to main menu"
    echo ""
    echo -n "Select tool: "
}

show_iso_menu() {
    clear
    echo -e "${CYAN}╔══════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║       💿 ISO Tools Menu              ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${BLUE}ISO Operations:${NC}"
    echo -e "  ${GREEN}1)${NC} iso-info (inspect ISO)"
    echo -e "  ${GREEN}2)${NC} iso-extract (extract contents)"
    echo -e "  ${GREEN}3)${NC} iso-mount (mount ISO)"
    echo -e "  ${GREEN}4)${NC} xorriso (create/modify ISO)"
    echo ""
    echo -e "${BLUE}Filesystem:${NC}"
    echo -e "  ${GREEN}5)${NC} squashfs-extract"
    echo -e "  ${GREEN}6)${NC} mksquashfs (create squashfs)"
    echo ""
    echo -e "${BLUE}Analysis:${NC}"
    echo -e "  ${GREEN}7)${NC} binwalk (firmware analysis)"
    echo -e "  ${GREEN}8)${NC} 7z (archive inspection)"
    echo -e "  ${GREEN}9)${NC} img-info (disk image info)"
    echo ""
    echo -e "${BLUE}Forensics:${NC}"
    echo -e "  ${GREEN}10)${NC} sleuthkit (fls/mmls)"
    echo -e "  ${GREEN}11)${NC} testdisk"
    echo -e "  ${GREEN}12)${NC} foremost"
    echo ""
    echo -e "  ${YELLOW}b)${NC} Back to main menu"
    echo ""
    echo -n "Select tool: "
}

run_with_args() {
    local cmd="$1"
    shift
    
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo -e "${RED}Error: $cmd not found${NC}"
        echo "Install with: sudo ./scripts/install-apk-tools.sh"
        read -p "Press Enter to continue..."
        return 1
    fi
    
    echo ""
    echo -e "${GREEN}Running: $cmd $@${NC}"
    echo -e "${YELLOW}Enter arguments (or press Enter for interactive mode):${NC}"
    read -r args
    
    if [ -n "$args" ]; then
        $cmd $args
    else
        $cmd "$@"
    fi
    
    echo ""
    read -p "Press Enter to continue..."
}

run_gui() {
    local cmd="$1"
    
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo -e "${RED}Error: $cmd not found${NC}"
        read -p "Press Enter to continue..."
        return 1
    fi
    
    echo ""
    echo -e "${GREEN}Launching: $cmd${NC}"
    echo -e "${YELLOW}(GUI will open in background)${NC}"
    
    $cmd &
    
    sleep 1
    echo ""
    read -p "Press Enter to continue..."
}

handle_apk_menu() {
    while true; do
        show_apk_menu
        read -r choice
        
        case "$choice" in
            1) run_with_args "jadx" "--help" ;;
            2) run_gui "jadx-gui" ;;
            3) run_gui "bytecode-viewer" ;;
            4) run_with_args "apktool" "--help" ;;
            5) 
                echo "Available: smali, baksmali"
                run_with_args "baksmali" "--help"
                ;;
            6) run_with_args "d2j-dex2jar.sh" "--help" ;;
            7) run_with_args "uber-apk-signer" "--help" ;;
            8) run_with_args "androguard" "--help" ;;
            9) run_with_args "apkleaks" "--help" ;;
            10) run_with_args "quark" "--help" ;;
            11) run_with_args "frida" "--help" ;;
            12) run_with_args "objection" "--help" ;;
            13) run_gui "ghidra" ;;
            b|B) return ;;
            *) echo -e "${RED}Invalid option${NC}"; sleep 1 ;;
        esac
    done
}

handle_iso_menu() {
    while true; do
        show_iso_menu
        read -r choice
        
        case "$choice" in
            1) run_with_args "iso-info" "" ;;
            2) run_with_args "iso-extract" "" ;;
            3) run_with_args "sudo" "iso-mount" ;;
            4) run_with_args "xorriso" "--help" ;;
            5) run_with_args "squashfs-extract" "" ;;
            6) run_with_args "mksquashfs" "--help" ;;
            7) run_with_args "binwalk" "--help" ;;
            8) run_with_args "7z" "--help" ;;
            9) run_with_args "img-info" "" ;;
            10) 
                echo "Available: fls, mmls, fsstat, icat, istat"
                run_with_args "fls" "--help"
                ;;
            11) run_with_args "testdisk" "" ;;
            12) run_with_args "foremost" "-h" ;;
            b|B) return ;;
            *) echo -e "${RED}Invalid option${NC}"; sleep 1 ;;
        esac
    done
}

run_validation() {
    clear
    echo -e "${CYAN}Running Environment Validation...${NC}"
    echo ""
    
    if [ -f "$SCRIPTS_DIR/validate-debug-env.sh" ]; then
        bash "$SCRIPTS_DIR/validate-debug-env.sh"
    else
        echo -e "${RED}Validation script not found${NC}"
    fi
    
    echo ""
    read -p "Press Enter to continue..."
}

update_tools() {
    clear
    echo -e "${CYAN}╔══════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║       🔄 Update All Tools            ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════╝${NC}"
    echo ""
    
    echo -e "${YELLOW}This will reinstall/update all debugging tools.${NC}"
    echo -e "${YELLOW}Root access is required.${NC}"
    echo ""
    read -p "Continue? (y/N): " confirm
    
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        echo ""
        echo -e "${GREEN}Updating APK tools...${NC}"
        if [ -f "$SCRIPTS_DIR/install-apk-tools.sh" ]; then
            sudo bash "$SCRIPTS_DIR/install-apk-tools.sh"
        else
            echo -e "${RED}APK tools installer not found${NC}"
        fi
        
        echo ""
        echo -e "${GREEN}Updating ISO tools...${NC}"
        if [ -f "$SCRIPTS_DIR/install-iso-tools.sh" ]; then
            sudo bash "$SCRIPTS_DIR/install-iso-tools.sh"
        else
            echo -e "${RED}ISO tools installer not found${NC}"
        fi
        
        echo ""
        echo -e "${GREEN}Update complete!${NC}"
    else
        echo "Update cancelled."
    fi
    
    echo ""
    read -p "Press Enter to continue..."
}

view_registry() {
    clear
    
    local registry="$SCRIPT_DIR/tools/registry.md"
    if [ ! -f "$registry" ]; then
        registry="$(dirname "$SCRIPT_DIR")/tools/registry.md"
    fi
    
    if [ -f "$registry" ]; then
        if command -v less >/dev/null 2>&1; then
            less "$registry"
        elif command -v more >/dev/null 2>&1; then
            more "$registry"
        else
            cat "$registry"
            read -p "Press Enter to continue..."
        fi
    else
        echo -e "${RED}Registry file not found${NC}"
        read -p "Press Enter to continue..."
    fi
}

# Main loop
main() {
    # Source PATH configurations
    [ -f /etc/profile.d/apk-tools.sh ] && source /etc/profile.d/apk-tools.sh
    [ -f /etc/profile.d/iso-tools.sh ] && source /etc/profile.d/iso-tools.sh
    
    while true; do
        show_header
        show_main_menu
        read -r choice
        
        case "$choice" in
            1) handle_apk_menu ;;
            2) handle_iso_menu ;;
            3) run_validation ;;
            4) update_tools ;;
            5) view_registry ;;
            q|Q) 
                clear
                echo -e "${GREEN}Goodbye!${NC}"
                exit 0
                ;;
            *) 
                echo -e "${RED}Invalid option${NC}"
                sleep 1
                ;;
        esac
    done
}

# Quick actions via arguments
case "${1:-}" in
    --validate)
        bash "$SCRIPTS_DIR/validate-debug-env.sh"
        ;;
    --install-apk)
        sudo bash "$SCRIPTS_DIR/install-apk-tools.sh"
        ;;
    --install-iso)
        sudo bash "$SCRIPTS_DIR/install-iso-tools.sh"
        ;;
    --install-all)
        sudo bash "$SCRIPTS_DIR/install-apk-tools.sh"
        sudo bash "$SCRIPTS_DIR/install-iso-tools.sh"
        ;;
    --help|-h)
        echo "Usage: $0 [OPTION]"
        echo ""
        echo "Debug Environment Launcher"
        echo ""
        echo "Options:"
        echo "  (none)          Interactive menu"
        echo "  --validate      Run environment validation"
        echo "  --install-apk   Install APK tools"
        echo "  --install-iso   Install ISO tools"
        echo "  --install-all   Install all tools"
        echo "  --help          Show this help"
        ;;
    *)
        main
        ;;
esac
