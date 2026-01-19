"""
Agent Sandbox System
====================

Provides isolated execution environments for each agent.
Each agent has its own sandbox directory for temporary files,
scratch data, and intermediate artifacts.
"""

import os
import shutil
from pathlib import Path
from typing import Optional, List
from datetime import datetime


class AgentSandbox:
    """
    Manages isolated sandbox environments for agents.
    
    Each agent has its own sandbox:
        /usr/share/agents/sandboxes/<agent_name>/
    
    Sandbox structure:
        <agent_name>/
            tmp/        - Temporary files (cleaned on exit)
            work/       - Working directory for tasks
            output/     - Task outputs
            cache/      - Persistent cache
    """
    
    def __init__(self, agent_name: str, sandboxes_dir: Path):
        self.agent_name = agent_name
        self.sandboxes_dir = Path(sandboxes_dir)
        self.sandbox_root = self.sandboxes_dir / agent_name
        
        # Standard subdirectories
        self.tmp_dir = self.sandbox_root / "tmp"
        self.work_dir = self.sandbox_root / "work"
        self.output_dir = self.sandbox_root / "output"
        self.cache_dir = self.sandbox_root / "cache"
        
        # Initialize sandbox
        self._init_sandbox()
    
    def _init_sandbox(self) -> None:
        """Initialize sandbox directories."""
        for d in [self.tmp_dir, self.work_dir, self.output_dir, self.cache_dir]:
            d.mkdir(parents=True, exist_ok=True)
    
    def get_tmp_path(self, filename: Optional[str] = None) -> Path:
        """Get path in tmp directory."""
        if filename:
            return self.tmp_dir / filename
        return self.tmp_dir
    
    def get_work_path(self, filename: Optional[str] = None) -> Path:
        """Get path in work directory."""
        if filename:
            return self.work_dir / filename
        return self.work_dir
    
    def get_output_path(self, filename: Optional[str] = None) -> Path:
        """Get path in output directory."""
        if filename:
            return self.output_dir / filename
        return self.output_dir
    
    def get_cache_path(self, filename: Optional[str] = None) -> Path:
        """Get path in cache directory."""
        if filename:
            return self.cache_dir / filename
        return self.cache_dir
    
    def create_task_dir(self, task_id: str) -> Path:
        """Create a dedicated directory for a specific task."""
        task_dir = self.work_dir / task_id
        task_dir.mkdir(parents=True, exist_ok=True)
        return task_dir
    
    def clean_tmp(self) -> int:
        """Clean temporary files. Returns number of files removed."""
        count = 0
        if self.tmp_dir.exists():
            for item in self.tmp_dir.iterdir():
                if item.is_file():
                    item.unlink()
                    count += 1
                elif item.is_dir():
                    shutil.rmtree(item)
                    count += 1
        return count
    
    def clean_work(self) -> int:
        """Clean work directory. Returns number of items removed."""
        count = 0
        if self.work_dir.exists():
            for item in self.work_dir.iterdir():
                if item.is_file():
                    item.unlink()
                    count += 1
                elif item.is_dir():
                    shutil.rmtree(item)
                    count += 1
        return count
    
    def clean_all(self) -> None:
        """Clean entire sandbox (except cache)."""
        self.clean_tmp()
        self.clean_work()
        # Output is preserved for review
        # Cache is preserved for performance
    
    def destroy(self) -> None:
        """Completely destroy the sandbox."""
        if self.sandbox_root.exists():
            shutil.rmtree(self.sandbox_root)
    
    def list_outputs(self) -> List[Path]:
        """List all files in output directory."""
        if not self.output_dir.exists():
            return []
        return list(self.output_dir.rglob("*"))
    
    def get_disk_usage(self) -> dict:
        """Get disk usage statistics for sandbox."""
        def dir_size(path: Path) -> int:
            if not path.exists():
                return 0
            total = 0
            for item in path.rglob("*"):
                if item.is_file():
                    total += item.stat().st_size
            return total
        
        return {
            "agent_name": self.agent_name,
            "sandbox_root": str(self.sandbox_root),
            "tmp_bytes": dir_size(self.tmp_dir),
            "work_bytes": dir_size(self.work_dir),
            "output_bytes": dir_size(self.output_dir),
            "cache_bytes": dir_size(self.cache_dir),
            "total_bytes": dir_size(self.sandbox_root)
        }
    
    def write_file(self, subdir: str, filename: str, content: str) -> Path:
        """Write content to a file in the sandbox."""
        if subdir == "tmp":
            target_dir = self.tmp_dir
        elif subdir == "work":
            target_dir = self.work_dir
        elif subdir == "output":
            target_dir = self.output_dir
        elif subdir == "cache":
            target_dir = self.cache_dir
        else:
            raise ValueError(f"Invalid subdir: {subdir}")
        
        target_dir.mkdir(parents=True, exist_ok=True)
        filepath = target_dir / filename
        filepath.write_text(content)
        return filepath
    
    def read_file(self, subdir: str, filename: str) -> Optional[str]:
        """Read content from a file in the sandbox."""
        if subdir == "tmp":
            filepath = self.tmp_dir / filename
        elif subdir == "work":
            filepath = self.work_dir / filename
        elif subdir == "output":
            filepath = self.output_dir / filename
        elif subdir == "cache":
            filepath = self.cache_dir / filename
        else:
            raise ValueError(f"Invalid subdir: {subdir}")
        
        if filepath.exists():
            return filepath.read_text()
        return None
    
    def __enter__(self):
        """Context manager entry - clean tmp on entry."""
        self.clean_tmp()
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit - clean tmp on exit."""
        self.clean_tmp()
        return False
