"""
Agent Memory System
===================

Provides persistent JSON-based memory storage for each agent.
Memory is stored per-agent in isolated files.
"""

import json
import os
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional
from threading import Lock
import fcntl


class AgentMemory:
    """
    Thread-safe, file-based memory storage for agents.
    
    Each agent has its own memory file:
        /usr/share/agents/memory/<agent_name>.json
    
    Memory structure:
        {
            "agent_name": "...",
            "created_at": "ISO timestamp",
            "updated_at": "ISO timestamp",
            "data": { ... arbitrary agent data ... },
            "history": [ ... task history ... ]
        }
    """
    
    def __init__(self, agent_name: str, memory_dir: Path):
        self.agent_name = agent_name
        self.memory_dir = Path(memory_dir)
        self.memory_file = self.memory_dir / f"{agent_name}.json"
        self._lock = Lock()
        
        # Ensure memory directory exists
        self.memory_dir.mkdir(parents=True, exist_ok=True)
        
        # Initialize memory file if it doesn't exist
        if not self.memory_file.exists():
            self._init_memory()
    
    def _init_memory(self) -> None:
        """Initialize empty memory file."""
        initial_data = {
            "agent_name": self.agent_name,
            "created_at": datetime.now().isoformat(),
            "updated_at": datetime.now().isoformat(),
            "data": {},
            "history": []
        }
        self._write_file(initial_data)
    
    def _read_file(self) -> Dict[str, Any]:
        """Read memory file with file locking."""
        with self._lock:
            try:
                with open(self.memory_file, 'r') as f:
                    fcntl.flock(f.fileno(), fcntl.LOCK_SH)
                    try:
                        return json.load(f)
                    finally:
                        fcntl.flock(f.fileno(), fcntl.LOCK_UN)
            except (json.JSONDecodeError, FileNotFoundError):
                self._init_memory()
                return self._read_file()
    
    def _write_file(self, data: Dict[str, Any]) -> None:
        """Write memory file with file locking."""
        with self._lock:
            with open(self.memory_file, 'w') as f:
                fcntl.flock(f.fileno(), fcntl.LOCK_EX)
                try:
                    json.dump(data, f, indent=2, default=str)
                finally:
                    fcntl.flock(f.fileno(), fcntl.LOCK_UN)
    
    def load(self) -> Dict[str, Any]:
        """Load and return the agent's data section."""
        memory = self._read_file()
        return memory.get("data", {})
    
    def save(self, data: Dict[str, Any]) -> None:
        """Save data to the agent's memory."""
        memory = self._read_file()
        memory["data"] = data
        memory["updated_at"] = datetime.now().isoformat()
        self._write_file(memory)
    
    def get(self, key: str, default: Any = None) -> Any:
        """Get a specific key from memory."""
        data = self.load()
        return data.get(key, default)
    
    def set(self, key: str, value: Any) -> None:
        """Set a specific key in memory."""
        data = self.load()
        data[key] = value
        self.save(data)
    
    def append_history(self, entry: Dict[str, Any]) -> None:
        """Append an entry to task history."""
        memory = self._read_file()
        entry["timestamp"] = datetime.now().isoformat()
        memory["history"].append(entry)
        memory["updated_at"] = datetime.now().isoformat()
        
        # Keep only last 1000 history entries
        if len(memory["history"]) > 1000:
            memory["history"] = memory["history"][-1000:]
        
        self._write_file(memory)
    
    def get_history(self, limit: int = 50) -> List[Dict[str, Any]]:
        """Get recent task history."""
        memory = self._read_file()
        history = memory.get("history", [])
        return history[-limit:] if limit else history
    
    def clear_history(self) -> None:
        """Clear task history."""
        memory = self._read_file()
        memory["history"] = []
        memory["updated_at"] = datetime.now().isoformat()
        self._write_file(memory)
    
    def clear_all(self) -> None:
        """Clear all memory and history."""
        self._init_memory()
    
    def get_stats(self) -> Dict[str, Any]:
        """Get memory statistics."""
        memory = self._read_file()
        return {
            "agent_name": self.agent_name,
            "created_at": memory.get("created_at"),
            "updated_at": memory.get("updated_at"),
            "data_keys": list(memory.get("data", {}).keys()),
            "history_count": len(memory.get("history", [])),
            "file_size_bytes": self.memory_file.stat().st_size if self.memory_file.exists() else 0
        }


def load_memory(agent_name: str, memory_dir: Path) -> AgentMemory:
    """Factory function to create AgentMemory instance."""
    return AgentMemory(agent_name, memory_dir)


def save_memory(agent_name: str, data: Dict[str, Any], memory_dir: Path) -> None:
    """Convenience function to save memory."""
    mem = AgentMemory(agent_name, memory_dir)
    mem.save(data)


def append_log(agent_name: str, entry: Dict[str, Any], memory_dir: Path) -> None:
    """Convenience function to append to history."""
    mem = AgentMemory(agent_name, memory_dir)
    mem.append_history(entry)
