# Debugging Tools Registry

> Complete inventory of APK and ISO debugging tools installed on this workstation.

---

## 📱 APK Debugging Tools

### Core Decompilers

| Tool | Version | GitHub URL | Install Path | Purpose |
|------|---------|------------|--------------|---------|
| **JADX** | Latest | https://github.com/skylot/jadx | `/opt/apk-tools/jadx/` | DEX to Java decompiler with GUI |
| **JADX-GUI** | Latest | https://github.com/skylot/jadx | `/opt/apk-tools/jadx/` | Graphical interface for JADX |
| **Bytecode Viewer** | Latest | https://github.com/Konloch/bytecode-viewer | `/opt/apk-tools/lib/bytecode-viewer.jar` | Multi-tool Java/.class/.DEX viewer |

### APK Manipulation

| Tool | Version | GitHub URL | Install Path | Purpose |
|------|---------|------------|--------------|---------|
| **Apktool** | Latest | https://github.com/iBotPeaches/Apktool | `/opt/apk-tools/lib/apktool.jar` | APK disassembly and rebuild |
| **smali** | Latest | https://github.com/google/smali | `/opt/apk-tools/lib/smali.jar` | DEX assembler |
| **baksmali** | Latest | https://github.com/google/smali | `/opt/apk-tools/lib/baksmali.jar` | DEX disassembler |
| **dex2jar** | Latest | https://github.com/pxb1988/dex2jar | `/opt/apk-tools/dex2jar/` | DEX to JAR converter |
| **uber-apk-signer** | Latest | https://github.com/patrickfav/uber-apk-signer | `/opt/apk-tools/lib/uber-apk-signer.jar` | APK signing utility |

### Dynamic Analysis

| Tool | Version | GitHub URL | Install Path | Purpose |
|------|---------|------------|--------------|---------|
| **Frida** | Latest | https://github.com/frida/frida | `/opt/apk-tools/venv/bin/frida` | Dynamic instrumentation toolkit |
| **Objection** | Latest | https://github.com/sensepost/objection | `/opt/apk-tools/venv/bin/objection` | Runtime mobile exploration |
| **apk-mitm** | Latest | https://github.com/shroudedcode/apk-mitm | (via mitmproxy) | HTTPS traffic inspection |

### Static Analysis

| Tool | Version | GitHub URL | Install Path | Purpose |
|------|---------|------------|--------------|---------|
| **Androguard** | Latest | https://github.com/androguard/androguard | `/opt/apk-tools/venv/bin/androguard` | Python APK analysis library |
| **Quark Engine** | Latest | https://github.com/quark-engine/quark-engine | `/opt/apk-tools/venv/bin/quark` | Android malware scoring |
| **APKLeaks** | Latest | https://github.com/dwisiswant0/apkleaks | `/opt/apk-tools/venv/bin/apkleaks` | APK secrets/endpoints scanner |

### Reverse Engineering

| Tool | Version | GitHub URL | Install Path | Purpose |
|------|---------|------------|--------------|---------|
| **Ghidra** | Latest | https://github.com/NationalSecurityAgency/ghidra | `/opt/apk-tools/ghidra/` | NSA reverse engineering suite |

### Android SDK Tools

| Tool | Version | Source | Install Path | Purpose |
|------|---------|--------|--------------|---------|
| **aapt2** | SDK | Android SDK | `/opt/apk-tools/bin/aapt2` | Android Asset Packaging Tool |
| **apkanalyzer** | SDK | Android SDK | `/opt/apk-tools/bin/apkanalyzer` | APK analysis CLI |

---

## 💿 ISO Debugging Tools

### ISO Creation/Modification

| Tool | Version | Source | Install Path | Purpose |
|------|---------|--------|--------------|---------|
| **xorriso** | System | apt | System PATH | ISO creation and modification |
| **genisoimage** | System | apt | System PATH | ISO9660 image creation |
| **mkisofs** | System | apt | System PATH | ISO filesystem creation |

### ISO Inspection

| Tool | Version | Source | Install Path | Purpose |
|------|---------|--------|--------------|---------|
| **isoinfo** | System | cdrkit | System PATH | ISO9660 information extraction |
| **iso-info** | Custom | Local | `/opt/iso-tools/bin/iso-info` | ISO information wrapper |

### Archive Extraction

| Tool | Version | Source | Install Path | Purpose |
|------|---------|--------|--------------|---------|
| **7z** | System | p7zip | System PATH | Universal archive extraction |
| **iso-extract** | Custom | Local | `/opt/iso-tools/bin/iso-extract` | ISO content extractor |

### Filesystem Tools

| Tool | Version | Source | Install Path | Purpose |
|------|---------|--------|--------------|---------|
| **unsquashfs** | System | squashfs-tools | System PATH | SquashFS extraction |
| **mksquashfs** | System | squashfs-tools | System PATH | SquashFS creation |
| **squashfs-extract** | Custom | Local | `/opt/iso-tools/bin/squashfs-extract` | SquashFS extraction wrapper |

### Binary/Firmware Analysis

| Tool | Version | Source | Install Path | Purpose |
|------|---------|--------|--------------|---------|
| **binwalk** | System | apt | System PATH | Firmware analysis and extraction |
| **UEFITool** | Latest | GitHub | `/opt/iso-tools/bin/UEFITool` | UEFI firmware editor |

### Forensic Analysis

| Tool | Version | Source | Install Path | Purpose |
|------|---------|--------|--------------|---------|
| **sleuthkit** | System | apt | System PATH | Forensic analysis toolkit |
| **fls** | System | sleuthkit | System PATH | File listing from disk image |
| **mmls** | System | sleuthkit | System PATH | Partition table display |
| **testdisk** | System | apt | System PATH | Data recovery utility |
| **foremost** | System | apt | System PATH | File carving tool |

### Disk Image Tools

| Tool | Version | Source | Install Path | Purpose |
|------|---------|--------|--------------|---------|
| **qemu-img** | System | qemu-utils | System PATH | QEMU disk image utility |
| **img-info** | Custom | Local | `/opt/iso-tools/bin/img-info` | Disk image information |
| **fdisk** | System | apt | System PATH | Partition table manipulation |
| **gdisk** | System | apt | System PATH | GPT partition table tool |
| **parted** | System | apt | System PATH | Partition editor |

### Mount Helpers

| Tool | Version | Source | Install Path | Purpose |
|------|---------|--------|--------------|---------|
| **iso-mount** | Custom | Local | `/opt/iso-tools/bin/iso-mount` | Easy ISO mounting |
| **fuseiso** | System | apt | System PATH | FUSE ISO mounting |

---

## 🔧 Usage Examples

### APK Analysis

```bash
# Decompile APK to Java source
jadx -d output/ app.apk

# Open APK in GUI decompiler
jadx-gui app.apk

# Disassemble APK to smali
apktool d app.apk -o app_decoded/

# Rebuild APK from smali
apktool b app_decoded/ -o app_rebuilt.apk

# Sign APK
uber-apk-signer -a app_rebuilt.apk

# Convert DEX to JAR
d2j-dex2jar.sh classes.dex

# Analyze APK with androguard
androguard analyze app.apk

# Scan APK for secrets
apkleaks -f app.apk

# Analyze malware score
quark -a app.apk -s

# Dynamic instrumentation (requires device)
frida -U -f com.example.app -l script.js

# Runtime exploration (requires device)
objection -g com.example.app explore
```

### ISO Analysis

```bash
# Get ISO information
iso-info image.iso

# Extract ISO contents
iso-extract image.iso output_dir/

# Mount ISO (requires root)
sudo iso-mount image.iso /mnt/iso

# Extract SquashFS
squashfs-extract filesystem.squashfs

# Analyze ISO with binwalk
binwalk -e image.iso

# List files in ISO
isoinfo -l -i image.iso

# Show ISO metadata
xorriso -indev image.iso -pvd_info

# Analyze disk image
img-info disk.img

# List partitions
fdisk -l disk.img

# Forensic file listing
fls -r disk.img

# Carve files from image
foremost -i disk.img -o recovered/
```

### Advanced Analysis

```bash
# Open in Ghidra (GUI)
ghidra

# Analyze ELF/binary in Ghidra
ghidra-analyzeHeadless /tmp/project Project -import binary.so

# Extract UEFI firmware
UEFIExtract firmware.bin

# Deep firmware extraction
binwalk -e -M firmware.bin

# Mount partition from disk image
# (offset can be found with fdisk -l)
mount -o loop,offset=$((512*2048)) disk.img /mnt/partition
```

---

## 📁 Directory Structure

```
/opt/
├── apk-tools/
│   ├── bin/                    # Executable scripts and symlinks
│   │   ├── jadx
│   │   ├── jadx-gui
│   │   ├── apktool
│   │   ├── smali
│   │   ├── baksmali
│   │   ├── uber-apk-signer
│   │   ├── androguard
│   │   ├── frida
│   │   ├── objection
│   │   ├── quark
│   │   ├── apkleaks
│   │   ├── bytecode-viewer
│   │   └── ghidra
│   ├── lib/                    # JAR files
│   │   ├── apktool.jar
│   │   ├── smali.jar
│   │   ├── baksmali.jar
│   │   ├── uber-apk-signer.jar
│   │   └── bytecode-viewer.jar
│   ├── jadx/                   # JADX installation
│   ├── dex2jar/                # dex2jar installation
│   ├── ghidra/                 # Ghidra installation
│   └── venv/                   # Python virtual environment
│
└── iso-tools/
    └── bin/                    # Custom helper scripts
        ├── iso-mount
        ├── iso-extract
        ├── iso-info
        ├── squashfs-extract
        ├── img-info
        └── UEFITool
```

---

## 🔄 Updates

To update all tools, run:

```bash
# Update APK tools
sudo /root/termux-kotlin-app/scripts/install-apk-tools.sh

# Update ISO tools
sudo /root/termux-kotlin-app/scripts/install-iso-tools.sh

# Or use the debug launcher
./debug.sh
# Select option 4: Update All Tools
```

---

## 📝 Notes

1. **Java tools** require JDK 17+ (installed automatically)
2. **Python tools** are installed in a virtual environment at `/opt/apk-tools/venv/`
3. **Frida/Objection** require a connected Android device for dynamic analysis
4. **Root access** is required for ISO mounting and some forensic operations
5. **PATH** is configured via `/etc/profile.d/apk-tools.sh` and `/etc/profile.d/iso-tools.sh`

---

*Last updated: 2026-01-18*
