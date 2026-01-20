"""
Update Management Skill
Handles pkg update/upgrade operations.
"""

import os
import subprocess
from typing import Dict, Any

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')


def check_updates() -> Dict[str, Any]:
    """Check for available updates."""
    try:
        result = subprocess.run(
            ['apt-get', 'update', '-q'],
            capture_output=True,
            text=True
        )
        
        result = subprocess.run(
            ['apt', 'list', '--upgradable'],
            capture_output=True,
            text=True
        )
        
        upgradable = []
        for line in result.stdout.split('\n'):
            if '/' in line and 'upgradable' in line:
                parts = line.split('/')
                if parts:
                    upgradable.append(parts[0])
        
        return {
            "upgradable": upgradable,
            "count": len(upgradable),
            "status": "updates_available" if upgradable else "up_to_date"
        }
    except Exception as e:
        return {"error": str(e)}


def run_update() -> Dict[str, Any]:
    """Run apt-get update."""
    try:
        result = subprocess.run(
            ['apt-get', 'update'],
            capture_output=True,
            text=True
        )
        
        return {
            "success": result.returncode == 0,
            "stdout": result.stdout[-500:] if len(result.stdout) > 500 else result.stdout,
            "stderr": result.stderr[-500:] if len(result.stderr) > 500 else result.stderr
        }
    except Exception as e:
        return {"error": str(e)}


def run_upgrade(dry_run: bool = True) -> Dict[str, Any]:
    """Run apt-get upgrade."""
    cmd = ['apt-get', 'upgrade', '-y']
    if dry_run:
        cmd.append('--dry-run')
    
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True
        )
        
        upgraded = []
        for line in result.stdout.split('\n'):
            if 'Unpacking' in line:
                parts = line.split()
                if len(parts) >= 2:
                    upgraded.append(parts[1])
        
        return {
            "success": result.returncode == 0,
            "dry_run": dry_run,
            "upgraded": upgraded,
            "count": len(upgraded)
        }
    except Exception as e:
        return {"error": str(e)}


def fix_mirrors() -> Dict[str, Any]:
    """Check and suggest mirror fixes."""
    sources_file = f"{PREFIX}/etc/apt/sources.list"
    
    try:
        with open(sources_file, 'r') as f:
            content = f.read()
        
        mirrors = []
        for line in content.split('\n'):
            if line.strip() and not line.startswith('#'):
                if 'deb' in line:
                    mirrors.append(line.strip())
        
        result = subprocess.run(
            ['ping', '-c', '1', '-W', '2', 'packages-cf.termux.dev'],
            capture_output=True
        )
        
        reachable = result.returncode == 0
        
        return {
            "sources_file": sources_file,
            "mirrors": mirrors,
            "mirror_reachable": reachable,
            "suggestion": "Run termux-change-repo if mirrors are slow" if not reachable else None
        }
    except Exception as e:
        return {"error": str(e)}
