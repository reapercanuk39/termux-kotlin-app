"""
Configuration Management Skill
Handles config file reading, writing, and validation.
"""

import os
import json
from pathlib import Path
from typing import Dict, Any, Optional

try:
    import yaml
    HAS_YAML = True
except ImportError:
    HAS_YAML = False

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')
CONFIG_DIR = f"{PREFIX}/etc/termux-compat"


def read_config(name: str) -> Dict[str, Any]:
    """Read a config file."""
    # Check in termux-compat dir first
    config_path = Path(CONFIG_DIR) / f"{name}.yml"
    
    if not config_path.exists():
        config_path = Path(CONFIG_DIR) / f"{name}.json"
    
    if not config_path.exists():
        config_path = Path(PREFIX) / "etc" / name
    
    if not config_path.exists():
        return {"error": f"Config not found: {name}"}
    
    try:
        with open(config_path, 'r') as f:
            content = f.read()
        
        if config_path.suffix == '.yml' or config_path.suffix == '.yaml':
            if HAS_YAML:
                return {"path": str(config_path), "config": yaml.safe_load(content)}
            else:
                return {"path": str(config_path), "raw": content, "note": "Install pyyaml for parsing"}
        elif config_path.suffix == '.json':
            return {"path": str(config_path), "config": json.loads(content)}
        else:
            return {"path": str(config_path), "raw": content}
    except Exception as e:
        return {"error": str(e)}


def write_config(name: str, config: Dict[str, Any], format: str = "yml") -> Dict[str, Any]:
    """Write a config file."""
    config_dir = Path(CONFIG_DIR)
    config_dir.mkdir(parents=True, exist_ok=True)
    
    if format == "yml":
        if not HAS_YAML:
            return {"error": "pyyaml not installed. Run: pip install pyyaml"}
        config_path = config_dir / f"{name}.yml"
        content = yaml.dump(config, default_flow_style=False)
    else:
        config_path = config_dir / f"{name}.json"
        content = json.dumps(config, indent=2)
    
    try:
        with open(config_path, 'w') as f:
            f.write(content)
        return {"success": True, "path": str(config_path)}
    except Exception as e:
        return {"error": str(e)}


def check_configs() -> Dict[str, Any]:
    """List all config files."""
    configs = []
    
    config_dir = Path(CONFIG_DIR)
    if config_dir.exists():
        for item in config_dir.iterdir():
            if item.is_file():
                configs.append({
                    "name": item.name,
                    "path": str(item),
                    "size": item.stat().st_size
                })
    
    # Check for important system configs
    important = ["profile", "bash.bashrc", "apt.conf"]
    etc_dir = Path(PREFIX) / "etc"
    
    for name in important:
        path = etc_dir / name
        if path.exists():
            configs.append({
                "name": name,
                "path": str(path),
                "size": path.stat().st_size,
                "system": True
            })
    
    return {
        "configs": configs,
        "count": len(configs),
        "config_dir": str(config_dir)
    }


def validate_config(name: str) -> Dict[str, Any]:
    """Validate a config file."""
    result = read_config(name)
    
    if "error" in result:
        return {"valid": False, "error": result["error"]}
    
    if "config" in result:
        return {"valid": True, "path": result["path"]}
    elif "raw" in result:
        return {"valid": True, "path": result["path"], "note": "Raw text, not parsed"}
    
    return {"valid": False, "error": "Unknown format"}


def reset_config(name: str) -> Dict[str, Any]:
    """Reset config to default."""
    defaults = {
        "compat": {
            "old_prefix": "/data/data/com.termux",
            "new_prefix": "/data/data/com.termux",
            "shim_enabled": True
        },
        "agents": {
            "sandbox": "$PREFIX/var/agents/sandbox",
            "memory": "$PREFIX/var/agents/memory",
            "log_level": "info"
        }
    }
    
    if name not in defaults:
        return {"error": f"No default for: {name}"}
    
    return write_config(name, defaults[name])
