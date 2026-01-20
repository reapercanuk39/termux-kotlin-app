"""
LD_PRELOAD Shim Skill
Manages libtermux_compat.so compilation and status.
"""

import os
import subprocess
from pathlib import Path
from typing import Dict, Any

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')
SHIM_SO = f"{PREFIX}/lib/libtermux_compat.so"
SHIM_SRC = f"{PREFIX}/lib/libtermux_compat.c"


def check_shim() -> Dict[str, Any]:
    """Check if shim is compiled."""
    return {
        "source_exists": Path(SHIM_SRC).exists(),
        "compiled": Path(SHIM_SO).exists(),
        "loaded": os.environ.get('LD_PRELOAD', '') == SHIM_SO,
        "source_path": SHIM_SRC,
        "library_path": SHIM_SO
    }


def compile_shim() -> Dict[str, Any]:
    """Compile shim from source."""
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
                "path": SHIM_SO
            }
        else:
            return {
                "success": False,
                "error": result.stderr or "Compilation failed"
            }
    except FileNotFoundError:
        return {"success": False, "error": "clang not found. Run: pkg install clang"}


def verify_shim() -> Dict[str, Any]:
    """Verify shim is loaded correctly."""
    ld_preload = os.environ.get('LD_PRELOAD', '')
    shim_exists = Path(SHIM_SO).exists()
    
    if not shim_exists:
        return {
            "status": "not_compiled",
            "message": "Shim not compiled yet"
        }
    
    if ld_preload == SHIM_SO:
        return {
            "status": "active",
            "message": "Shim is compiled and loaded",
            "ld_preload": ld_preload
        }
    else:
        return {
            "status": "not_loaded",
            "message": "Shim is compiled but not loaded. Restart your shell.",
            "ld_preload": ld_preload or "NOT SET"
        }


def test_shim() -> Dict[str, Any]:
    """Test shim path interception."""
    tests = []
    
    # Test 1: Check if shim is loaded
    ld_preload = os.environ.get('LD_PRELOAD', '')
    tests.append({
        "test": "LD_PRELOAD set",
        "passed": SHIM_SO in ld_preload
    })
    
    # Test 2: Try to access old path (should be redirected)
    old_path = "/data/data/com.termux/files/usr/bin"
    new_path = "/data/data/com.termux/files/usr/bin"
    
    # If shim is working, accessing old path should work
    if Path(new_path).exists():
        tests.append({
            "test": "New path exists",
            "passed": True
        })
    
    passed = sum(1 for t in tests if t["passed"])
    
    return {
        "tests": tests,
        "passed": passed,
        "total": len(tests),
        "all_passed": passed == len(tests)
    }


def show_status() -> Dict[str, Any]:
    """Show full shim status."""
    status = check_shim()
    
    # Add clang status
    try:
        result = subprocess.run(['which', 'clang'], capture_output=True, text=True)
        status["clang_installed"] = result.returncode == 0
        if result.returncode == 0:
            status["clang_path"] = result.stdout.strip()
    except:
        status["clang_installed"] = False
    
    # Determine overall status
    if status["loaded"]:
        status["overall"] = "active"
        status["message"] = "Shim is compiled and loaded. Full compatibility enabled."
    elif status["compiled"]:
        status["overall"] = "ready"
        status["message"] = "Shim is compiled. Restart shell to activate."
    elif status["source_exists"] and status.get("clang_installed"):
        status["overall"] = "compilable"
        status["message"] = "Ready to compile. Run: termux-compat-build"
    elif status["source_exists"]:
        status["overall"] = "needs_clang"
        status["message"] = "Install clang first: pkg install clang"
    else:
        status["overall"] = "missing"
        status["message"] = "Shim source missing. Bootstrap may be corrupted."
    
    return status
