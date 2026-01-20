"""
Compatibility Layer Skill
Manages dpkg-wrapper and LD_PRELOAD shim for Termux-Kotlin compatibility.
"""

import os
import subprocess
from pathlib import Path
from typing import Dict, Any, Optional

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux.kotlin/files/usr')
SHIM_SO = f"{PREFIX}/lib/libtermux_compat.so"
SHIM_SRC = f"{PREFIX}/lib/libtermux_compat.c"
DPKG_WRAPPER = f"{PREFIX}/bin/dpkg"
DPKG_REAL = f"{PREFIX}/bin/dpkg.real"


def check_shim_status() -> Dict[str, Any]:
    """Check if LD_PRELOAD shim is compiled and loaded."""
    result = {
        "source_exists": Path(SHIM_SRC).exists(),
        "compiled": Path(SHIM_SO).exists(),
        "loaded": os.environ.get('LD_PRELOAD', '') == SHIM_SO,
        "clang_available": False,
    }
    
    # Check if clang is available
    try:
        subprocess.run(['which', 'clang'], capture_output=True, check=True)
        result["clang_available"] = True
    except:
        pass
    
    # Status summary
    if result["compiled"] and result["loaded"]:
        result["status"] = "active"
        result["message"] = "Shim is compiled and loaded"
    elif result["compiled"]:
        result["status"] = "compiled"
        result["message"] = "Shim is compiled but not loaded. Restart your shell."
    elif result["source_exists"] and result["clang_available"]:
        result["status"] = "ready"
        result["message"] = "Ready to compile. Run: termux-compat-build"
    elif result["source_exists"]:
        result["status"] = "needs_clang"
        result["message"] = "Need clang to compile. Run: pkg install clang"
    else:
        result["status"] = "missing"
        result["message"] = "Source file missing. Bootstrap may be corrupted."
    
    return result


def compile_shim() -> Dict[str, Any]:
    """Compile libtermux_compat.so from source."""
    if not Path(SHIM_SRC).exists():
        return {"success": False, "error": f"Source not found: {SHIM_SRC}"}
    
    try:
        result = subprocess.run(
            ['clang', '-shared', '-fPIC', '-O2', '-o', SHIM_SO, SHIM_SRC, '-ldl'],
            capture_output=True,
            text=True
        )
        
        if result.returncode == 0 and Path(SHIM_SO).exists():
            return {
                "success": True,
                "message": "Shim compiled successfully",
                "path": SHIM_SO,
                "action_required": "Restart your shell to enable LD_PRELOAD"
            }
        else:
            return {
                "success": False,
                "error": result.stderr or "Compilation failed",
                "returncode": result.returncode
            }
    except FileNotFoundError:
        return {"success": False, "error": "clang not found. Run: pkg install clang"}
    except Exception as e:
        return {"success": False, "error": str(e)}


def check_wrapper_status() -> Dict[str, Any]:
    """Verify dpkg wrapper is properly installed."""
    result = {
        "wrapper_exists": Path(DPKG_WRAPPER).exists(),
        "real_exists": Path(DPKG_REAL).exists(),
        "wrapper_is_script": False,
    }
    
    if result["wrapper_exists"]:
        # Check if wrapper is a script (not ELF)
        with open(DPKG_WRAPPER, 'rb') as f:
            magic = f.read(4)
            result["wrapper_is_script"] = magic[:2] == b'#!'
    
    if result["wrapper_is_script"] and result["real_exists"]:
        result["status"] = "ok"
        result["message"] = "dpkg wrapper is properly configured"
    elif not result["real_exists"]:
        result["status"] = "error"
        result["message"] = "dpkg.real not found - wrapper may not be set up"
    else:
        result["status"] = "warning"
        result["message"] = "dpkg appears to be original binary, not wrapper"
    
    return result


def verify_paths() -> Dict[str, Any]:
    """Verify path translation is working correctly."""
    old_prefix = "/data/data/com.termux/"
    new_prefix = "/data/data/com.termux.kotlin/"
    
    checks = []
    
    # Check PREFIX environment
    prefix_env = os.environ.get('PREFIX', '')
    checks.append({
        "check": "PREFIX environment",
        "expected": new_prefix + "files/usr",
        "actual": prefix_env,
        "ok": new_prefix in prefix_env
    })
    
    # Check HOME environment
    home_env = os.environ.get('HOME', '')
    checks.append({
        "check": "HOME environment", 
        "expected": new_prefix + "files/home",
        "actual": home_env,
        "ok": new_prefix in home_env
    })
    
    # Check LD_PRELOAD
    ld_preload = os.environ.get('LD_PRELOAD', '')
    checks.append({
        "check": "LD_PRELOAD",
        "expected": SHIM_SO,
        "actual": ld_preload,
        "ok": ld_preload == SHIM_SO
    })
    
    all_ok = all(c["ok"] for c in checks)
    
    return {
        "checks": checks,
        "all_ok": all_ok,
        "status": "ok" if all_ok else "issues_found"
    }


def diagnose_compat() -> Dict[str, Any]:
    """Full compatibility layer diagnostic."""
    return {
        "shim": check_shim_status(),
        "wrapper": check_wrapper_status(),
        "paths": verify_paths()
    }


def fix_compat() -> Dict[str, Any]:
    """Auto-fix common compatibility issues."""
    fixes = []
    
    # Check and compile shim if needed
    shim_status = check_shim_status()
    if shim_status["status"] == "ready":
        compile_result = compile_shim()
        fixes.append({
            "action": "compile_shim",
            "result": compile_result
        })
    elif shim_status["status"] == "needs_clang":
        fixes.append({
            "action": "install_clang",
            "result": {"message": "Run: pkg install clang"}
        })
    
    return {
        "fixes_applied": fixes,
        "current_status": diagnose_compat()
    }
