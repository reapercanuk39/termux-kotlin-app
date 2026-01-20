"""
Path Management Skill
Handles path rewriting and verification for com.termux.
"""

import os
import subprocess
from pathlib import Path
from typing import Dict, Any, List

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')
OLD_PREFIX = '/data/data/com.termux/'
NEW_PREFIX = '/data/data/com.termux/'


def check_paths(directory: str = None) -> Dict[str, Any]:
    """Check for incorrect paths in files."""
    if directory is None:
        directory = PREFIX
    
    dir_path = Path(directory)
    if not dir_path.exists():
        return {"error": f"Directory not found: {directory}"}
    
    files_with_old_paths = []
    
    # Check text files
    for ext in ['*.py', '*.sh', '*.conf', '*.cfg']:
        for f in dir_path.rglob(ext):
            try:
                content = f.read_text(errors='ignore')
                if OLD_PREFIX in content and NEW_PREFIX not in content:
                    files_with_old_paths.append(str(f))
            except:
                pass
    
    return {
        "directory": str(dir_path),
        "files_with_old_paths": files_with_old_paths,
        "count": len(files_with_old_paths)
    }


def find_old_paths(directory: str = None, limit: int = 50) -> Dict[str, Any]:
    """Find files with old com.termux paths."""
    if directory is None:
        directory = PREFIX
    
    try:
        result = subprocess.run(
            ['grep', '-rl', '--include=*.py', '--include=*.sh', '--include=*.conf',
             OLD_PREFIX, directory],
            capture_output=True,
            text=True,
            timeout=30
        )
        
        files = result.stdout.strip().split('\n')[:limit] if result.stdout.strip() else []
        
        return {
            "directory": directory,
            "files": files,
            "count": len(files),
            "truncated": len(files) == limit
        }
    except subprocess.TimeoutExpired:
        return {"error": "Search timed out"}
    except Exception as e:
        return {"error": str(e)}


def rewrite_paths(file_path: str) -> Dict[str, Any]:
    """Rewrite paths in a text file from com.termux to com.termux."""
    path = Path(file_path)
    if not path.exists():
        return {"error": f"File not found: {file_path}"}
    
    try:
        content = path.read_text(errors='ignore')
        if OLD_PREFIX not in content:
            return {"file": file_path, "status": "no_changes_needed"}
        
        new_content = content.replace(OLD_PREFIX, NEW_PREFIX)
        path.write_text(new_content)
        
        return {
            "file": file_path,
            "status": "rewritten",
            "replacements": content.count(OLD_PREFIX)
        }
    except Exception as e:
        return {"error": str(e)}


def fix_shebangs(directory: str = None) -> Dict[str, Any]:
    """Fix shebang lines in scripts."""
    if directory is None:
        directory = f"{PREFIX}/bin"
    
    dir_path = Path(directory)
    if not dir_path.exists():
        return {"error": f"Directory not found: {directory}"}
    
    fixed = []
    
    for script in dir_path.iterdir():
        if not script.is_file():
            continue
        
        try:
            with open(script, 'rb') as f:
                first_bytes = f.read(2)
            
            if first_bytes != b'#!':
                continue
            
            with open(script, 'r', errors='ignore') as f:
                lines = f.readlines()
            
            if not lines:
                continue
            
            first_line = lines[0]
            if OLD_PREFIX in first_line:
                lines[0] = first_line.replace(OLD_PREFIX, NEW_PREFIX)
                with open(script, 'w') as f:
                    f.writelines(lines)
                fixed.append(script.name)
        except:
            pass
    
    return {
        "directory": str(dir_path),
        "fixed": fixed,
        "count": len(fixed)
    }


def verify_prefix() -> Dict[str, Any]:
    """Verify PREFIX is correct."""
    prefix = os.environ.get('PREFIX', '')
    
    checks = {
        "PREFIX_set": bool(prefix),
        "PREFIX_correct": NEW_PREFIX.rstrip('/') + '/files/usr' in prefix,
        "PREFIX_exists": Path(prefix).is_dir() if prefix else False,
        "bin_exists": (Path(prefix) / 'bin').is_dir() if prefix else False,
        "lib_exists": (Path(prefix) / 'lib').is_dir() if prefix else False
    }
    
    all_ok = all(checks.values())
    
    return {
        "prefix": prefix,
        "checks": checks,
        "valid": all_ok
    }
