"""
Package Management Skill
Troubleshoots package installation issues.
"""

import os
import subprocess
import re
from pathlib import Path
from typing import Dict, Any, List

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')
OLD_PREFIX = "/data/data/com.termux"
NEW_PREFIX = "/data/data/com.termux"


def check_package(name: str) -> Dict[str, Any]:
    """Check package installation status."""
    try:
        result = subprocess.run(
            ['dpkg', '-s', name],
            capture_output=True,
            text=True
        )
        
        if result.returncode == 0:
            version = None
            for line in result.stdout.split('\n'):
                if line.startswith('Version:'):
                    version = line.split(':', 1)[1].strip()
                    break
            
            return {
                "package": name,
                "installed": True,
                "version": version,
                "status": "ok"
            }
        else:
            return {
                "package": name,
                "installed": False,
                "status": "not_installed"
            }
    except Exception as e:
        return {"error": str(e)}


def list_packages() -> Dict[str, Any]:
    """List installed packages."""
    try:
        result = subprocess.run(
            ['dpkg', '-l'],
            capture_output=True,
            text=True
        )
        
        packages = []
        for line in result.stdout.split('\n'):
            if line.startswith('ii'):
                parts = line.split()
                if len(parts) >= 3:
                    packages.append({
                        "name": parts[1],
                        "version": parts[2]
                    })
        
        return {
            "packages": packages,
            "count": len(packages)
        }
    except Exception as e:
        return {"error": str(e)}


def find_broken() -> Dict[str, Any]:
    """Find broken packages."""
    broken = []
    
    try:
        result = subprocess.run(
            ['dpkg', '--audit'],
            capture_output=True,
            text=True
        )
        
        if result.stdout.strip():
            broken.append({
                "type": "dpkg_audit",
                "details": result.stdout.strip()
            })
        
        result = subprocess.run(
            ['dpkg', '-l'],
            capture_output=True,
            text=True
        )
        
        for line in result.stdout.split('\n'):
            if line.startswith('iU') or line.startswith('iF'):
                parts = line.split()
                if len(parts) >= 2:
                    broken.append({
                        "type": "unconfigured",
                        "package": parts[1]
                    })
        
        return {
            "broken": broken,
            "count": len(broken),
            "status": "ok" if len(broken) == 0 else "issues_found"
        }
    except Exception as e:
        return {"error": str(e)}


def verify_paths(package: str) -> Dict[str, Any]:
    """Verify package paths are correctly rewritten."""
    issues = []
    
    try:
        result = subprocess.run(
            ['dpkg', '-L', package],
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            return {"error": f"Package not found: {package}"}
        
        files = result.stdout.strip().split('\n')
        
        for filepath in files:
            if not filepath:
                continue
            
            path = Path(filepath)
            
            if not path.exists():
                issues.append({
                    "file": filepath,
                    "issue": "missing"
                })
                continue
            
            if path.is_file() and path.stat().st_size < 1024 * 100:
                try:
                    with open(path, 'r', errors='ignore') as f:
                        content = f.read()
                    
                    if OLD_PREFIX in content:
                        issues.append({
                            "file": filepath,
                            "issue": "contains_old_path"
                        })
                except:
                    pass
        
        return {
            "package": package,
            "files_checked": len(files),
            "issues": issues,
            "status": "ok" if len(issues) == 0 else f"{len(issues)} issues"
        }
    except Exception as e:
        return {"error": str(e)}


def diagnose_install(package: str) -> Dict[str, Any]:
    """Diagnose installation issues for a package."""
    diagnosis = {
        "package": package,
        "checks": []
    }
    
    status = check_package(package)
    diagnosis["checks"].append({
        "check": "installed",
        "result": status.get("installed", False)
    })
    
    if status.get("installed"):
        paths = verify_paths(package)
        diagnosis["checks"].append({
            "check": "paths",
            "result": len(paths.get("issues", [])) == 0
        })
    
    wrapper_exists = Path(f"{PREFIX}/bin/dpkg-wrapper").exists()
    diagnosis["checks"].append({
        "check": "dpkg_wrapper",
        "result": wrapper_exists
    })
    
    shim_exists = Path(f"{PREFIX}/lib/libtermux_compat.so").exists()
    diagnosis["checks"].append({
        "check": "compat_shim",
        "result": shim_exists
    })
    
    all_ok = all(c["result"] for c in diagnosis["checks"])
    diagnosis["status"] = "ok" if all_ok else "issues_found"
    
    return diagnosis
