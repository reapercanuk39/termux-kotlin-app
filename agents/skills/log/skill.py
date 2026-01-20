"""
Log Analysis Skill
Analyzes log files, finds errors and patterns.
"""

import os
import re
from pathlib import Path
from typing import Dict, Any, List, Optional
from datetime import datetime

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')
TMPDIR = os.environ.get('TMPDIR', f'{PREFIX}/tmp')


def tail_log(log_path: str, lines: int = 50) -> Dict[str, Any]:
    """Show last N lines of a log file."""
    path = Path(log_path)
    if not path.exists():
        return {"error": f"Log file not found: {log_path}"}
    
    try:
        with open(path, 'r', errors='ignore') as f:
            all_lines = f.readlines()
            tail_lines = all_lines[-lines:]
        
        return {
            "file": str(path),
            "total_lines": len(all_lines),
            "showing": len(tail_lines),
            "content": tail_lines
        }
    except Exception as e:
        return {"error": str(e)}


def find_errors(log_path: str) -> Dict[str, Any]:
    """Find error messages in a log file."""
    error_patterns = [
        r'(?i)\berror\b',
        r'(?i)\bfail(ed|ure)?\b',
        r'(?i)\bcannot\b',
        r'(?i)\bunable\b',
        r'(?i)\bpermission denied\b',
        r'(?i)\bno such file\b',
        r'(?i)\bnot found\b'
    ]
    
    return _search_log(log_path, error_patterns, "errors")


def find_warnings(log_path: str) -> Dict[str, Any]:
    """Find warning messages in a log file."""
    warning_patterns = [
        r'(?i)\bwarning\b',
        r'(?i)\bwarn\b',
        r'(?i)\bdeprecated\b',
        r'(?i)\bskipping\b'
    ]
    
    return _search_log(log_path, warning_patterns, "warnings")


def _search_log(log_path: str, patterns: List[str], label: str) -> Dict[str, Any]:
    """Search log for patterns."""
    path = Path(log_path)
    if not path.exists():
        return {"error": f"Log file not found: {log_path}"}
    
    matches = []
    combined_pattern = '|'.join(patterns)
    regex = re.compile(combined_pattern)
    
    try:
        with open(path, 'r', errors='ignore') as f:
            for i, line in enumerate(f, 1):
                if regex.search(line):
                    matches.append({
                        "line_num": i,
                        "content": line.strip()
                    })
        
        return {
            "file": str(path),
            label: matches,
            "count": len(matches)
        }
    except Exception as e:
        return {"error": str(e)}


def grep_log(log_path: str, pattern: str, ignore_case: bool = True) -> Dict[str, Any]:
    """Search log for a specific pattern."""
    path = Path(log_path)
    if not path.exists():
        return {"error": f"Log file not found: {log_path}"}
    
    flags = re.IGNORECASE if ignore_case else 0
    regex = re.compile(pattern, flags)
    matches = []
    
    try:
        with open(path, 'r', errors='ignore') as f:
            for i, line in enumerate(f, 1):
                if regex.search(line):
                    matches.append({
                        "line_num": i,
                        "content": line.strip()
                    })
        
        return {
            "file": str(path),
            "pattern": pattern,
            "matches": matches,
            "count": len(matches)
        }
    except Exception as e:
        return {"error": str(e)}


def analyze_dpkg_log() -> Dict[str, Any]:
    """Analyze dpkg wrapper log for issues."""
    log_path = Path(TMPDIR) / 'dpkg_wrapper.log'
    
    if not log_path.exists():
        # Try old name
        log_path = Path(TMPDIR) / 'dpkg_rewrite.log'
    
    if not log_path.exists():
        return {"error": "dpkg wrapper log not found", "checked": [
            str(Path(TMPDIR) / 'dpkg_wrapper.log'),
            str(Path(TMPDIR) / 'dpkg_rewrite.log')
        ]}
    
    analysis = {
        "file": str(log_path),
        "packages_processed": 0,
        "packages_skipped": 0,
        "packages_rewritten": 0,
        "errors": [],
        "recent_entries": []
    }
    
    try:
        with open(log_path, 'r', errors='ignore') as f:
            lines = f.readlines()
        
        for line in lines:
            if 'SKIP' in line:
                analysis["packages_skipped"] += 1
            elif 'Rewriting' in line:
                analysis["packages_rewritten"] += 1
            elif 'FAIL' in line or 'error' in line.lower():
                analysis["errors"].append(line.strip())
            
            if 'Processing' in line or 'args:' in line:
                analysis["packages_processed"] += 1
        
        # Get recent entries
        analysis["recent_entries"] = [l.strip() for l in lines[-20:]]
        
        return analysis
    except Exception as e:
        return {"error": str(e)}


def summarize_log(log_path: str) -> Dict[str, Any]:
    """Summarize log file contents."""
    path = Path(log_path)
    if not path.exists():
        return {"error": f"Log file not found: {log_path}"}
    
    try:
        stats = path.stat()
        with open(path, 'r', errors='ignore') as f:
            lines = f.readlines()
        
        # Count errors and warnings
        error_count = sum(1 for l in lines if re.search(r'(?i)\berror\b', l))
        warning_count = sum(1 for l in lines if re.search(r'(?i)\bwarning\b', l))
        
        return {
            "file": str(path),
            "size_bytes": stats.st_size,
            "size_kb": round(stats.st_size / 1024, 2),
            "total_lines": len(lines),
            "error_count": error_count,
            "warning_count": warning_count,
            "modified": datetime.fromtimestamp(stats.st_mtime).isoformat(),
            "first_line": lines[0].strip() if lines else None,
            "last_line": lines[-1].strip() if lines else None
        }
    except Exception as e:
        return {"error": str(e)}
