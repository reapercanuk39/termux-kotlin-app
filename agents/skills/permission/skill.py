"""
Permission Management Skill
Fixes file permissions and ownership issues.
"""

import os
import stat
from pathlib import Path
from typing import Dict, Any, List

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')


def check_permissions(path: str = None) -> Dict[str, Any]:
    """Check file permissions."""
    if path is None:
        path = f"{PREFIX}/bin"
    
    dir_path = Path(path)
    if not dir_path.exists():
        return {"error": f"Path not found: {path}"}
    
    issues = []
    
    if dir_path.is_file():
        mode = dir_path.stat().st_mode
        if dir_path.suffix in ['.py', '.sh', ''] and not (mode & stat.S_IXUSR):
            issues.append({
                "file": str(dir_path),
                "issue": "Not executable",
                "current": oct(mode)[-3:]
            })
    else:
        for item in dir_path.iterdir():
            if item.is_file():
                mode = item.stat().st_mode
                # Check executables
                if item.suffix in ['', '.py', '.sh']:
                    if not (mode & stat.S_IXUSR):
                        issues.append({
                            "file": str(item),
                            "issue": "Not executable",
                            "current": oct(mode)[-3:]
                        })
    
    return {
        "path": str(dir_path),
        "issues": issues,
        "issue_count": len(issues)
    }


def fix_bin_perms() -> Dict[str, Any]:
    """Fix binary permissions (chmod +x)."""
    bin_dir = Path(PREFIX) / 'bin'
    fixed = []
    
    if not bin_dir.exists():
        return {"error": f"bin directory not found: {bin_dir}"}
    
    for item in bin_dir.iterdir():
        if item.is_file():
            mode = item.stat().st_mode
            if not (mode & stat.S_IXUSR):
                item.chmod(mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
                fixed.append(item.name)
    
    return {
        "directory": str(bin_dir),
        "fixed": fixed,
        "count": len(fixed)
    }


def fix_lib_perms() -> Dict[str, Any]:
    """Fix library permissions."""
    lib_dir = Path(PREFIX) / 'lib'
    fixed = []
    
    if not lib_dir.exists():
        return {"error": f"lib directory not found: {lib_dir}"}
    
    for item in lib_dir.glob('*.so*'):
        if item.is_file():
            mode = item.stat().st_mode
            # Libraries should be readable
            if not (mode & stat.S_IRUSR):
                item.chmod(0o644)
                fixed.append(item.name)
    
    return {
        "directory": str(lib_dir),
        "fixed": fixed,
        "count": len(fixed)
    }


def audit_permissions() -> Dict[str, Any]:
    """Full permission audit."""
    results = {
        "bin": check_permissions(f"{PREFIX}/bin"),
        "lib": check_permissions(f"{PREFIX}/lib"),
        "etc": check_permissions(f"{PREFIX}/etc")
    }
    
    total_issues = sum(r.get("issue_count", 0) for r in results.values())
    
    return {
        "results": results,
        "total_issues": total_issues,
        "status": "ok" if total_issues == 0 else f"{total_issues} issues found"
    }
