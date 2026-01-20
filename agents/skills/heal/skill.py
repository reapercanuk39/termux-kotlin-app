"""
Self-Healing Skill
Repairs common issues automatically.
"""

import os
import subprocess
from pathlib import Path
from typing import Dict, Any, List

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux.kotlin/files/usr')


def check_heal_needed() -> Dict[str, Any]:
    """Check if any healing is required."""
    issues = []
    
    # Check essential binaries
    essential = ['bash', 'sh', 'dpkg', 'apt']
    for bin_name in essential:
        if not (Path(PREFIX) / 'bin' / bin_name).exists():
            issues.append(f"Missing binary: {bin_name}")
    
    # Check dpkg database
    if not (Path(PREFIX) / 'var/lib/dpkg/status').exists():
        issues.append("dpkg database missing")
    
    # Check SSL certs
    if not (Path(PREFIX) / 'etc/tls/cert.pem').exists():
        issues.append("SSL certificates missing")
    
    # Check compatibility shim
    shim_src = Path(PREFIX) / 'lib/libtermux_compat.c'
    shim_so = Path(PREFIX) / 'lib/libtermux_compat.so'
    if shim_src.exists() and not shim_so.exists():
        try:
            subprocess.run(['which', 'clang'], capture_output=True, check=True)
            issues.append("Compatibility shim not compiled (clang available)")
        except:
            pass
    
    return {
        "heal_needed": len(issues) > 0,
        "issues": issues,
        "issue_count": len(issues)
    }


def heal_bootstrap() -> Dict[str, Any]:
    """Repair bootstrap issues."""
    repairs = []
    
    # Create essential directories
    essential_dirs = ['bin', 'lib', 'etc', 'var', 'share', 'tmp']
    for dir_name in essential_dirs:
        dir_path = Path(PREFIX) / dir_name
        if not dir_path.exists():
            dir_path.mkdir(parents=True, exist_ok=True)
            repairs.append(f"Created directory: {dir_path}")
    
    # Check dpkg database
    dpkg_dir = Path(PREFIX) / 'var/lib/dpkg'
    status_file = dpkg_dir / 'status'
    if not status_file.exists():
        dpkg_dir.mkdir(parents=True, exist_ok=True)
        status_file.touch()
        repairs.append("Created empty dpkg status file")
    
    # Create dpkg info directory
    info_dir = dpkg_dir / 'info'
    if not info_dir.exists():
        info_dir.mkdir(parents=True, exist_ok=True)
        repairs.append("Created dpkg info directory")
    
    return {
        "repairs": repairs,
        "repair_count": len(repairs),
        "status": "ok" if repairs else "nothing_to_repair"
    }


def heal_compat() -> Dict[str, Any]:
    """Repair compatibility layer."""
    repairs = []
    
    shim_src = Path(PREFIX) / 'lib/libtermux_compat.c'
    shim_so = Path(PREFIX) / 'lib/libtermux_compat.so'
    
    # Compile shim if source exists and clang available
    if shim_src.exists() and not shim_so.exists():
        try:
            result = subprocess.run(
                ['clang', '-shared', '-fPIC', '-O2', '-o', str(shim_so), str(shim_src), '-ldl'],
                capture_output=True,
                text=True
            )
            if result.returncode == 0:
                repairs.append("Compiled libtermux_compat.so")
            else:
                repairs.append(f"Failed to compile shim: {result.stderr}")
        except FileNotFoundError:
            repairs.append("Skipped shim compilation: clang not installed")
    
    # Check dpkg wrapper
    dpkg = Path(PREFIX) / 'bin/dpkg'
    dpkg_real = Path(PREFIX) / 'bin/dpkg.real'
    if dpkg.exists() and dpkg_real.exists():
        with open(dpkg, 'rb') as f:
            if f.read(2) != b'#!':
                repairs.append("Warning: dpkg may not be wrapper script")
    
    return {
        "repairs": repairs,
        "repair_count": len(repairs)
    }


def heal_packages() -> Dict[str, Any]:
    """Repair package database issues."""
    repairs = []
    
    # Run dpkg configure
    try:
        result = subprocess.run(
            ['dpkg', '--configure', '-a'],
            capture_output=True,
            text=True,
            timeout=60
        )
        if result.returncode == 0:
            repairs.append("Ran dpkg --configure -a")
        else:
            repairs.append(f"dpkg configure failed: {result.stderr[:200]}")
    except subprocess.TimeoutExpired:
        repairs.append("dpkg configure timed out")
    except Exception as e:
        repairs.append(f"dpkg configure error: {str(e)}")
    
    # Fix broken packages
    try:
        result = subprocess.run(
            ['apt', '--fix-broken', 'install', '-y'],
            capture_output=True,
            text=True,
            timeout=120
        )
        if result.returncode == 0:
            repairs.append("Ran apt --fix-broken install")
    except Exception as e:
        repairs.append(f"apt fix-broken error: {str(e)}")
    
    return {
        "repairs": repairs,
        "repair_count": len(repairs)
    }


def heal_permissions() -> Dict[str, Any]:
    """Fix permission issues."""
    repairs = []
    
    # Fix bin directory permissions
    bin_dir = Path(PREFIX) / 'bin'
    if bin_dir.exists():
        for binary in bin_dir.iterdir():
            if binary.is_file():
                mode = binary.stat().st_mode
                if not (mode & 0o100):  # Not executable
                    binary.chmod(mode | 0o755)
                    repairs.append(f"Fixed permissions: {binary.name}")
    
    # Fix lib directory permissions
    lib_dir = Path(PREFIX) / 'lib'
    if lib_dir.exists():
        for lib in lib_dir.glob('*.so*'):
            mode = lib.stat().st_mode
            if not (mode & 0o400):  # Not readable
                lib.chmod(0o644)
                repairs.append(f"Fixed permissions: {lib.name}")
    
    return {
        "repairs": repairs,
        "repair_count": len(repairs)
    }


def full_heal() -> Dict[str, Any]:
    """Perform comprehensive self-healing."""
    results = {
        "bootstrap": heal_bootstrap(),
        "compat": heal_compat(),
        "packages": heal_packages(),
        "permissions": heal_permissions()
    }
    
    total_repairs = sum(r.get("repair_count", 0) for r in results.values())
    
    return {
        "results": results,
        "total_repairs": total_repairs,
        "status": "healed" if total_repairs > 0 else "healthy"
    }
