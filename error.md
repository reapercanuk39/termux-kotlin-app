# Termux-Kotlin Error Log

## Purpose
This document tracks all errors encountered, their root causes, and fixes applied to prevent covering the same problems repeatedly.

---

## Error #1: Login executable not found
**Date:** 2026-01-16  
**Error Message:**
```
exec("/data/data/com.termux.kotlin/files/usr/bin/login"): No such file or directory
```

**Status:** ✅ Fixed in v1.0.11  
**Root Cause:** The `login` script exists in bootstrap but has shebang `#!/data/data/com.termux/files/usr/bin/sh` which doesn't exist because our package is `com.termux.kotlin`. When Android tries to execute the script, the interpreter path doesn't exist.

**Fix Applied:** Extended `isTextFileNeedingPathFix()` in TermuxInstaller.kt to include ALL shell scripts in `bin/` directory, not just files in `etc/` and `share/`. This includes:
- `login`, `chsh`, `su`, `am`, `pm`, `cmd`, `dalvikvm`, `logcat`, `getprop`, `settings`
- All `termux-*` scripts
- `pkg`, `apt-key`, various `-config` scripts
- Compression utilities: `gunzip`, `zcat`, `bzdiff`, `xzdiff`, etc.

---

## Error #2: Dynamic linker hash table issue
**Date:** 2026-01-16  
**Error Message:**
```
CANNOT LINK EXECUTABLE "/data/data/com.termux.kotlin/files/usr/bin/sh": empty/missing DTHASH/DTGNU_HASH in "/data/data/com.termux.kotlin/files/usr/bin/dash" (new hash type from the future?)
```

**Status:** ✅ Fixed in v1.0.10 (LD_LIBRARY_PATH approach)  
**Root Cause:** This error was caused by earlier attempts to sed-replace paths inside ELF binaries, which corrupted their structure. The "new hash type from the future" message occurs when binary format is corrupted.

**Fix Applied (v1.0.10):**
1. Use original upstream bootstrap (binaries have RUNPATH hardcoded to com.termux)
2. Set LD_LIBRARY_PATH to our package's lib dir (`/data/data/com.termux.kotlin/files/usr/lib`) to override RUNPATH at runtime
3. Only replace paths in TEXT files (scripts, configs), never in ELF binaries

**Note:** Error #1 (login not found) can appear as a secondary symptom because the shebang paths weren't being fixed.

---

## Error #3: Bootstrap symlink paths
**Date:** 2026-01-16  
**Error Message:** (Potential issue identified during audit)

**Status:** ✅ Fixed in v1.0.10  
**Root Cause:** SYMLINKS.txt contains target paths like `/data/data/com.termux/files/usr/bin/dash` which need to be replaced with our package path.

**Fix Applied:** Added symlink path replacement in TermuxInstaller.kt during bootstrap extraction.

---

## Version History & Fixes
| Version | Date | Issues Fixed | Key Changes |
|---------|------|--------------|-------------|
| 1.0.10 | 2026-01-16 | DT_HASH/DT_GNU_HASH error | LD_LIBRARY_PATH override, stopped corrupting ELF binaries |
| 1.0.11 | 2026-01-16 | Login script shebang paths | Extended path fixing to include bin/ scripts |

---

## Technical Details

### Bootstrap Package Structure
The upstream Termux bootstrap contains:
- **ELF binaries** (dash, bash, etc.) with hardcoded RUNPATH to `/data/data/com.termux/files/usr/lib`
- **Shell scripts** (login, chsh, su, termux-*, etc.) with shebangs pointing to `/data/data/com.termux/files/usr/bin/sh`
- **Config files** (bash.bashrc, profile, etc.) with hardcoded paths
- **SYMLINKS.txt** with symlink targets containing upstream paths

### Our Package Path Mapping
- **Upstream:** `/data/data/com.termux/files/...`
- **Our package:** `/data/data/com.termux.kotlin/files/...`

### What Gets Fixed at Bootstrap Time
1. **Text files** (scripts, configs) - paths replaced during extraction
2. **SYMLINKS.txt targets** - paths replaced when creating symlinks
3. **ELF binaries** - NOT modified (uses LD_LIBRARY_PATH at runtime instead)

### Files in bin/ Needing Path Fixes
Approximately 60+ shell scripts including:
- login, chsh, su, am, pm, cmd, dalvikvm, logcat, getprop, settings
- termux-backup, termux-change-repo, termux-info, termux-setup-storage, etc.
- pkg, apt-key, dpkg-realpath
- curl-config, gpg-error-config, and other *-config scripts
- Compression utils: gunzip, gzexe, bzdiff, xzdiff, zcat, etc.

