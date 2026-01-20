"""
Environment Variable Skill
Manages environment variables and shell configuration.
"""

import os
from pathlib import Path
from typing import Dict, Any, List

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux.kotlin/files/usr')
HOME = os.environ.get('HOME', '/data/data/com.termux.kotlin/files/home')


def check_env() -> Dict[str, Any]:
    """Check all important environment variables."""
    expected = {
        'PREFIX': f'/data/data/com.termux.kotlin/files/usr',
        'HOME': f'/data/data/com.termux.kotlin/files/home',
        'TMPDIR': f'/data/data/com.termux.kotlin/files/usr/tmp',
    }
    
    checks = []
    for var, expected_val in expected.items():
        actual = os.environ.get(var, 'NOT SET')
        ok = actual == expected_val or expected_val in actual
        checks.append({
            "variable": var,
            "expected": expected_val,
            "actual": actual,
            "ok": ok
        })
    
    # Check PATH contains our bin
    path = os.environ.get('PATH', '')
    checks.append({
        "variable": "PATH",
        "expected": f"{PREFIX}/bin in PATH",
        "actual": path[:100] + "..." if len(path) > 100 else path,
        "ok": f"{PREFIX}/bin" in path
    })
    
    # Check LD_PRELOAD
    ld_preload = os.environ.get('LD_PRELOAD', '')
    shim_path = f"{PREFIX}/lib/libtermux_compat.so"
    checks.append({
        "variable": "LD_PRELOAD",
        "expected": shim_path,
        "actual": ld_preload or "NOT SET",
        "ok": ld_preload == shim_path or not Path(shim_path).exists()
    })
    
    all_ok = all(c["ok"] for c in checks)
    
    return {
        "checks": checks,
        "all_ok": all_ok,
        "problems": [c for c in checks if not c["ok"]]
    }


def fix_path() -> Dict[str, Any]:
    """Fix PATH variable issues."""
    path = os.environ.get('PATH', '')
    new_path = path
    fixes = []
    
    # Ensure our bin is in PATH
    if f"{PREFIX}/bin" not in path:
        new_path = f"{PREFIX}/bin:{new_path}"
        fixes.append(f"Added {PREFIX}/bin to PATH")
    
    # Remove any com.termux (non-kotlin) paths
    parts = new_path.split(':')
    cleaned_parts = []
    for part in parts:
        if '/com.termux/' in part and '/com.termux.kotlin/' not in part:
            fixes.append(f"Removed old path: {part}")
        else:
            cleaned_parts.append(part)
    
    new_path = ':'.join(cleaned_parts)
    os.environ['PATH'] = new_path
    
    return {
        "fixes": fixes,
        "new_path": new_path
    }


def validate_env() -> Dict[str, Any]:
    """Validate environment against expected values."""
    issues = []
    
    # Check for old paths
    for var in ['PATH', 'LD_LIBRARY_PATH', 'PYTHONPATH', 'MANPATH']:
        val = os.environ.get(var, '')
        if '/com.termux/' in val and '/com.termux.kotlin/' not in val:
            issues.append({
                "variable": var,
                "issue": "Contains old com.termux path",
                "severity": "high"
            })
    
    # Check PREFIX is set correctly
    prefix = os.environ.get('PREFIX', '')
    if not prefix:
        issues.append({
            "variable": "PREFIX",
            "issue": "Not set",
            "severity": "high"
        })
    elif 'com.termux.kotlin' not in prefix:
        issues.append({
            "variable": "PREFIX",
            "issue": "Does not contain com.termux.kotlin",
            "severity": "high"
        })
    
    return {
        "valid": len(issues) == 0,
        "issues": issues
    }


def export_env(output_file: str = None) -> Dict[str, Any]:
    """Export current environment to file."""
    if output_file is None:
        output_file = f"{HOME}/.termux_env_backup"
    
    env_vars = [
        'PREFIX', 'HOME', 'PATH', 'LD_LIBRARY_PATH', 'LD_PRELOAD',
        'TMPDIR', 'TERM', 'COLORTERM', 'LANG', 'PYTHONPATH'
    ]
    
    lines = []
    for var in env_vars:
        val = os.environ.get(var, '')
        if val:
            lines.append(f'export {var}="{val}"')
    
    with open(output_file, 'w') as f:
        f.write('\n'.join(lines) + '\n')
    
    return {
        "file": output_file,
        "variables_exported": len(lines)
    }
