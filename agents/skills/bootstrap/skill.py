"""
Bootstrap Verification Skill
Verifies and repairs bootstrap installation.
"""

import os
from pathlib import Path
from typing import Dict, Any, List

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux.kotlin/files/usr')


ESSENTIAL_BINARIES = [
    'bash', 'sh', 'ls', 'cat', 'cp', 'mv', 'rm', 'mkdir',
    'chmod', 'chown', 'grep', 'sed', 'awk', 'tar', 'gzip',
    'dpkg', 'apt', 'apt-get', 'apt-cache', 'pkg'
]

ESSENTIAL_LIBS = [
    'libc.so', 'libdl.so', 'libm.so', 'libz.so',
    'libncurses.so', 'libreadline.so'
]


def verify_bootstrap() -> Dict[str, Any]:
    """Verify bootstrap files are intact."""
    issues = []
    
    # Check essential directories
    for dir_name in ['bin', 'lib', 'etc', 'var', 'share']:
        dir_path = Path(PREFIX) / dir_name
        if not dir_path.is_dir():
            issues.append(f"Missing directory: {dir_name}")
    
    # Check binaries
    bin_result = check_binaries()
    issues.extend([f"Missing binary: {b}" for b in bin_result.get("missing", [])])
    
    # Check libs
    lib_result = check_libs()
    issues.extend([f"Missing library: {l}" for l in lib_result.get("missing", [])])
    
    # Check dpkg database
    dpkg_status = Path(PREFIX) / 'var/lib/dpkg/status'
    if not dpkg_status.exists():
        issues.append("dpkg status file missing")
    
    return {
        "valid": len(issues) == 0,
        "issues": issues,
        "issue_count": len(issues)
    }


def check_binaries() -> Dict[str, Any]:
    """Check essential binaries exist."""
    bin_dir = Path(PREFIX) / 'bin'
    applets_dir = Path(PREFIX) / 'bin/applets'
    
    found = []
    missing = []
    
    for binary in ESSENTIAL_BINARIES:
        if (bin_dir / binary).exists() or (applets_dir / binary).exists():
            found.append(binary)
        else:
            missing.append(binary)
    
    return {
        "found": found,
        "missing": missing,
        "total": len(ESSENTIAL_BINARIES),
        "complete": len(missing) == 0
    }


def check_libs() -> Dict[str, Any]:
    """Check essential libraries exist."""
    lib_dir = Path(PREFIX) / 'lib'
    
    found = []
    missing = []
    
    for lib in ESSENTIAL_LIBS:
        # Check with version numbers too
        matches = list(lib_dir.glob(f"{lib}*"))
        if matches:
            found.append(lib)
        else:
            missing.append(lib)
    
    return {
        "found": found,
        "missing": missing,
        "total": len(ESSENTIAL_LIBS),
        "complete": len(missing) == 0
    }


def repair_symlinks() -> Dict[str, Any]:
    """Repair broken symlinks."""
    repaired = []
    broken = []
    
    for directory in ['bin', 'lib', 'etc']:
        dir_path = Path(PREFIX) / directory
        if not dir_path.exists():
            continue
        
        for item in dir_path.iterdir():
            if item.is_symlink():
                target = item.resolve()
                if not target.exists():
                    broken.append({
                        "link": str(item),
                        "target": str(item.readlink())
                    })
                    # Try to fix by removing broken link
                    try:
                        item.unlink()
                        repaired.append(str(item))
                    except:
                        pass
    
    return {
        "broken_found": len(broken),
        "repaired": repaired,
        "still_broken": [b for b in broken if b["link"] not in repaired]
    }


def list_missing() -> Dict[str, Any]:
    """List all missing bootstrap files."""
    missing = {
        "binaries": check_binaries().get("missing", []),
        "libraries": check_libs().get("missing", []),
        "directories": []
    }
    
    for dir_name in ['bin', 'lib', 'etc', 'var', 'share', 'tmp']:
        if not (Path(PREFIX) / dir_name).is_dir():
            missing["directories"].append(dir_name)
    
    total = sum(len(v) for v in missing.values())
    
    return {
        "missing": missing,
        "total_missing": total
    }
