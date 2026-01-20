"""
Diagnostic Skill
Collects system information and identifies issues.
"""

import os
import subprocess
import shutil
from pathlib import Path
from typing import Dict, Any, List
from datetime import datetime

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')


def collect_system_info() -> Dict[str, Any]:
    """Collect comprehensive system information."""
    info = {
        "timestamp": datetime.now().isoformat(),
        "environment": {},
        "storage": {},
        "packages": {},
        "processes": {}
    }
    
    # Environment
    for var in ['PREFIX', 'HOME', 'PATH', 'LD_LIBRARY_PATH', 'LD_PRELOAD', 'TERM']:
        info["environment"][var] = os.environ.get(var, 'NOT SET')
    
    # Storage
    try:
        total, used, free = shutil.disk_usage(PREFIX)
        info["storage"] = {
            "prefix": PREFIX,
            "total_mb": total // (1024 * 1024),
            "used_mb": used // (1024 * 1024),
            "free_mb": free // (1024 * 1024),
            "percent_used": round(used / total * 100, 1)
        }
    except:
        info["storage"] = {"error": "Could not get disk usage"}
    
    # Package count
    try:
        result = subprocess.run(
            ['dpkg', '-l'],
            capture_output=True, text=True
        )
        lines = [l for l in result.stdout.split('\n') if l.startswith('ii')]
        info["packages"]["installed_count"] = len(lines)
    except:
        info["packages"] = {"error": "Could not count packages"}
    
    # Process count
    try:
        result = subprocess.run(['ps', 'aux'], capture_output=True, text=True)
        info["processes"]["count"] = len(result.stdout.strip().split('\n')) - 1
    except:
        info["processes"] = {"error": "Could not count processes"}
    
    return info


def check_health() -> Dict[str, Any]:
    """Perform comprehensive system health check."""
    checks = []
    
    # Check essential binaries
    essential_bins = ['bash', 'sh', 'dpkg', 'apt', 'pkg', 'python3']
    for bin_name in essential_bins:
        bin_path = Path(PREFIX) / 'bin' / bin_name
        checks.append({
            "check": f"Binary: {bin_name}",
            "ok": bin_path.exists() or (Path(PREFIX) / 'bin' / 'applets' / bin_name).exists(),
            "path": str(bin_path)
        })
    
    # Check essential directories
    essential_dirs = ['bin', 'lib', 'etc', 'var', 'share']
    for dir_name in essential_dirs:
        dir_path = Path(PREFIX) / dir_name
        checks.append({
            "check": f"Directory: {dir_name}",
            "ok": dir_path.is_dir(),
            "path": str(dir_path)
        })
    
    # Check dpkg database
    dpkg_status = Path(PREFIX) / 'var/lib/dpkg/status'
    checks.append({
        "check": "dpkg database",
        "ok": dpkg_status.exists(),
        "path": str(dpkg_status)
    })
    
    # Check SSL certificates
    cert_file = Path(PREFIX) / 'etc/tls/cert.pem'
    checks.append({
        "check": "SSL certificates",
        "ok": cert_file.exists(),
        "path": str(cert_file)
    })
    
    # Summary
    passed = sum(1 for c in checks if c["ok"])
    total = len(checks)
    
    return {
        "checks": checks,
        "passed": passed,
        "total": total,
        "healthy": passed == total,
        "status": "healthy" if passed == total else f"{total - passed} issues found"
    }


def find_issues() -> Dict[str, Any]:
    """Identify common issues and suggest fixes."""
    issues = []
    
    # Check for old paths in environment
    for var in ['PATH', 'LD_LIBRARY_PATH', 'PYTHONPATH']:
        val = os.environ.get(var, '')
        if '/data/data/com.termux/' in val and '/data/data/com.termux/' not in val:
            issues.append({
                "severity": "high",
                "issue": f"{var} contains old com.termux path",
                "fix": "Restart your shell or source profile"
            })
    
    # Check if shim is needed but not loaded
    shim_so = Path(PREFIX) / 'lib/libtermux_compat.so'
    ld_preload = os.environ.get('LD_PRELOAD', '')
    if shim_so.exists() and str(shim_so) not in ld_preload:
        issues.append({
            "severity": "medium",
            "issue": "LD_PRELOAD shim compiled but not loaded",
            "fix": "Restart your shell"
        })
    
    # Check if clang is needed for shim
    shim_src = Path(PREFIX) / 'lib/libtermux_compat.c'
    if shim_src.exists() and not shim_so.exists():
        try:
            subprocess.run(['which', 'clang'], capture_output=True, check=True)
            issues.append({
                "severity": "low",
                "issue": "Shim source exists but not compiled (clang available)",
                "fix": "Run: termux-compat-build"
            })
        except:
            issues.append({
                "severity": "medium",
                "issue": "Shim source exists but clang not installed",
                "fix": "Run: pkg install clang"
            })
    
    # Check storage
    try:
        total, used, free = shutil.disk_usage(PREFIX)
        if free < 100 * 1024 * 1024:  # Less than 100MB
            issues.append({
                "severity": "high",
                "issue": f"Low disk space: {free // (1024*1024)}MB free",
                "fix": "Run: pkg clean"
            })
    except:
        pass
    
    return {
        "issues": issues,
        "count": len(issues),
        "has_critical": any(i["severity"] == "high" for i in issues)
    }


def generate_report() -> Dict[str, Any]:
    """Generate a comprehensive diagnostic report."""
    return {
        "generated_at": datetime.now().isoformat(),
        "system_info": collect_system_info(),
        "health_check": check_health(),
        "issues": find_issues()
    }
