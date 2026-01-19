"""
Memory Consistency Validator
============================

Tests to verify memory consistency and safety.
Ensures memory follows all rules from the system prompt.
"""

import os
import sys
import json
import tempfile
from pathlib import Path
from typing import Any, Dict
from unittest.mock import MagicMock, patch

import pytest


# Test fixtures
@pytest.fixture
def temp_memory_dir():
    """Create a temporary memory directory."""
    with tempfile.TemporaryDirectory() as tmpdir:
        yield Path(tmpdir)


@pytest.fixture
def mock_memory(temp_memory_dir):
    """Create a mock memory instance."""
    class MockMemory:
        def __init__(self, agent_name: str, memory_dir: Path):
            self.agent_name = agent_name
            self.memory_dir = memory_dir
            self.memory_file = memory_dir / f"{agent_name}.json"
            self._init_memory()
        
        def _init_memory(self):
            if not self.memory_file.exists():
                self.memory_file.write_text(json.dumps({
                    "agent_name": self.agent_name,
                    "created_at": "2024-01-01T00:00:00",
                    "updated_at": "2024-01-01T00:00:00",
                    "data": {},
                    "history": []
                }))
        
        def load(self) -> Dict[str, Any]:
            return json.loads(self.memory_file.read_text()).get("data", {})
        
        def save(self, data: Dict[str, Any]):
            mem = json.loads(self.memory_file.read_text())
            mem["data"] = data
            self.memory_file.write_text(json.dumps(mem))
        
        def update(self, key: str, value: Any):
            data = self.load()
            data[key] = value
            self.save(data)
        
        def get(self, key: str, default: Any = None) -> Any:
            return self.load().get(key, default)
        
        def size(self) -> int:
            return self.memory_file.stat().st_size
    
    return MockMemory("test_agent", temp_memory_dir)


class TestMemoryValidJson:
    """Test memory is valid JSON."""
    
    def test_memory_file_exists(self, mock_memory):
        """Memory file must exist after initialization."""
        assert mock_memory.memory_file.exists()
    
    def test_memory_is_valid_json(self, mock_memory):
        """Memory file must contain valid JSON."""
        content = mock_memory.memory_file.read_text()
        parsed = json.loads(content)
        assert isinstance(parsed, dict)
    
    def test_memory_has_required_structure(self, mock_memory):
        """Memory must have required structure."""
        content = json.loads(mock_memory.memory_file.read_text())
        
        assert "agent_name" in content
        assert "created_at" in content
        assert "updated_at" in content
        assert "data" in content
        assert "history" in content
        
        assert isinstance(content["data"], dict)
        assert isinstance(content["history"], list)
    
    def test_memory_survives_roundtrip(self, mock_memory):
        """Memory data survives save/load cycle."""
        test_data = {"key": "value", "number": 42, "list": [1, 2, 3]}
        mock_memory.save(test_data)
        loaded = mock_memory.load()
        
        assert loaded == test_data


class TestMemorySizeLimit:
    """Test memory size limit enforcement."""
    
    MEMORY_LIMIT = 1024 * 1024  # 1MB
    
    def test_memory_under_limit(self, mock_memory):
        """Memory size must be under 1MB."""
        assert mock_memory.size() < self.MEMORY_LIMIT
    
    def test_detect_oversized_memory(self, mock_memory):
        """Should detect when memory exceeds limit."""
        # Create large data
        large_data = {"key": "x" * (self.MEMORY_LIMIT + 1000)}
        mock_memory.save(large_data)
        
        # Memory should now exceed limit
        assert mock_memory.size() > self.MEMORY_LIMIT
    
    def test_memory_size_check_function(self, mock_memory):
        """Memory size check function works."""
        def check_memory_size(memory_file: Path, limit: int = 1024 * 1024) -> bool:
            if not memory_file.exists():
                return True
            return memory_file.stat().st_size < limit
        
        # Initially under limit
        assert check_memory_size(mock_memory.memory_file)
        
        # After adding large data, over limit
        large_data = {"key": "x" * (self.MEMORY_LIMIT + 1000)}
        mock_memory.save(large_data)
        assert not check_memory_size(mock_memory.memory_file)


class TestMemoryKeysDeterministic:
    """Test memory keys are deterministic."""
    
    def test_keys_are_strings(self, mock_memory):
        """All memory keys must be strings."""
        mock_memory.save({
            "string_key": "value",
            "another_key": 123
        })
        
        data = mock_memory.load()
        for key in data.keys():
            assert isinstance(key, str)
    
    def test_update_is_deterministic(self, mock_memory):
        """Same updates produce same result."""
        # First update
        mock_memory.save({})
        mock_memory.update("key1", "value1")
        mock_memory.update("key2", "value2")
        result1 = mock_memory.load()
        
        # Reset and repeat
        mock_memory.save({})
        mock_memory.update("key1", "value1")
        mock_memory.update("key2", "value2")
        result2 = mock_memory.load()
        
        assert result1 == result2


class TestMemoryNoSecrets:
    """Test memory contains no secrets."""
    
    SECRET_PATTERNS = [
        "password",
        "api_key",
        "secret",
        "token",
        "private_key",
        "credential",
        "auth_"
    ]
    
    def test_no_secret_keys(self, mock_memory):
        """Memory keys should not contain secret-related terms."""
        mock_memory.save({
            "last_run": "2024-01-01",
            "task_count": 5
        })
        
        data = mock_memory.load()
        for key in data.keys():
            key_lower = key.lower()
            for pattern in self.SECRET_PATTERNS:
                assert pattern not in key_lower, f"Key '{key}' contains secret pattern '{pattern}'"
    
    def test_detect_secret_values(self, mock_memory):
        """Should detect if secret values are stored."""
        import re
        
        SECRET_VALUE_PATTERNS = [
            r'-----BEGIN\s+PRIVATE\s+KEY-----',
            r'aws_access_key_id\s*=',
            r'AKIA[0-9A-Z]{16}',  # AWS access key
        ]
        
        def contains_secrets(data: Dict[str, Any]) -> bool:
            content = json.dumps(data)
            for pattern in SECRET_VALUE_PATTERNS:
                if re.search(pattern, content, re.IGNORECASE):
                    return True
            return False
        
        # Clean data should pass
        clean_data = {"key": "value", "count": 42}
        assert not contains_secrets(clean_data)
        
        # Data with secrets should be detected
        secret_data = {"key": "-----BEGIN PRIVATE KEY-----"}
        assert contains_secrets(secret_data)


class TestMemoryAtomicUpdates:
    """Test memory updates are atomic."""
    
    def test_update_is_atomic(self, mock_memory):
        """Update should be atomic - either complete or not at all."""
        mock_memory.update("key", "value")
        assert mock_memory.get("key") == "value"
    
    def test_multiple_updates_consistent(self, mock_memory):
        """Multiple updates should maintain consistency."""
        for i in range(10):
            mock_memory.update(f"key_{i}", f"value_{i}")
        
        data = mock_memory.load()
        for i in range(10):
            assert data[f"key_{i}"] == f"value_{i}"


class TestMemoryPersistence:
    """Test memory persists across tasks."""
    
    def test_memory_persists(self, mock_memory):
        """Memory should persist after save."""
        mock_memory.update("persistent_key", "persistent_value")
        
        # Simulate new memory instance (like new task)
        new_memory = type(mock_memory)(mock_memory.agent_name, mock_memory.memory_dir)
        
        assert new_memory.get("persistent_key") == "persistent_value"
    
    def test_history_accumulates(self, temp_memory_dir):
        """History should accumulate across tasks."""
        class MemoryWithHistory:
            def __init__(self, agent_name: str, memory_dir: Path):
                self.memory_file = memory_dir / f"{agent_name}.json"
                if not self.memory_file.exists():
                    self.memory_file.write_text(json.dumps({
                        "data": {},
                        "history": []
                    }))
            
            def append_history(self, entry: Dict[str, Any]):
                mem = json.loads(self.memory_file.read_text())
                mem["history"].append(entry)
                self.memory_file.write_text(json.dumps(mem))
            
            def get_history(self) -> list:
                return json.loads(self.memory_file.read_text())["history"]
        
        mem = MemoryWithHistory("test", temp_memory_dir)
        
        mem.append_history({"task": "task1"})
        mem.append_history({"task": "task2"})
        mem.append_history({"task": "task3"})
        
        history = mem.get_history()
        assert len(history) == 3
        assert history[0]["task"] == "task1"
        assert history[2]["task"] == "task3"


class TestMemoryNoRawLogs:
    """Test memory doesn't store raw logs or subprocess output."""
    
    def test_no_raw_subprocess_output(self, mock_memory):
        """Memory should not store raw subprocess output."""
        # Simulate storing processed result (OK)
        mock_memory.update("last_command_status", "success")
        mock_memory.update("last_command_exit_code", 0)
        
        # This pattern should be avoided
        BAD_PATTERNS = [
            "stdout",
            "stderr",
            "raw_output",
            "log_content",
            "full_log"
        ]
        
        data = mock_memory.load()
        for key in data.keys():
            for pattern in BAD_PATTERNS:
                assert pattern not in key.lower(), f"Key '{key}' suggests raw log storage"


class TestMemoryValidation:
    """Integration test for memory validation."""
    
    def test_full_validation(self, mock_memory):
        """Run full memory validation."""
        errors = []
        
        # Check 1: File exists
        if not mock_memory.memory_file.exists():
            errors.append("Memory file does not exist")
        
        # Check 2: Valid JSON
        try:
            content = json.loads(mock_memory.memory_file.read_text())
        except json.JSONDecodeError:
            errors.append("Memory is not valid JSON")
            content = {}
        
        # Check 3: Size limit
        if mock_memory.size() > 1024 * 1024:
            errors.append("Memory exceeds 1MB limit")
        
        # Check 4: Required structure
        if "data" not in content:
            errors.append("Missing 'data' key")
        
        # Check 5: Data is dict
        if not isinstance(content.get("data", None), dict):
            errors.append("'data' is not a dictionary")
        
        assert len(errors) == 0, f"Validation errors: {errors}"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
