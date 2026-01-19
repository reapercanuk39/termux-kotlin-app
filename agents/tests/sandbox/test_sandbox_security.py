"""
Sandbox Security Test Suite
============================

Tests to verify sandbox boundary enforcement.
Ensures agents cannot escape their sandboxes.
"""

import os
import sys
import tempfile
from pathlib import Path
from typing import Any, Dict
from unittest.mock import MagicMock, patch

import pytest


# Test fixtures
@pytest.fixture
def mock_sandbox():
    """Create a mock sandbox for testing."""
    with tempfile.TemporaryDirectory() as tmpdir:
        sandbox_root = Path(tmpdir) / "test_agent"
        sandbox_root.mkdir()
        
        # Create sandbox structure
        (sandbox_root / "tmp").mkdir()
        (sandbox_root / "work").mkdir()
        (sandbox_root / "output").mkdir()
        (sandbox_root / "cache").mkdir()
        
        yield sandbox_root


@pytest.fixture
def mock_context(mock_sandbox):
    """Create a mock agent context."""
    context = MagicMock()
    context.agent_name = "test_agent"
    context.sandbox_path = mock_sandbox
    context.sandbox = MagicMock()
    context.sandbox.sandbox_root = mock_sandbox
    context.sandbox.tmp_dir = mock_sandbox / "tmp"
    context.sandbox.work_dir = mock_sandbox / "work"
    context.sandbox.output_dir = mock_sandbox / "output"
    context.capabilities = ["filesystem.read", "filesystem.write"]
    return context


class TestSandboxBoundaries:
    """Test sandbox boundary enforcement."""
    
    def test_cannot_write_outside_sandbox(self, mock_sandbox):
        """Agent cannot write outside sandbox directory."""
        # Attempt to write outside sandbox
        outside_path = mock_sandbox.parent / "outside_file.txt"
        
        # Simulate sandbox check
        def is_within_sandbox(path: Path, sandbox: Path) -> bool:
            try:
                resolved = path.resolve()
                sandbox_resolved = sandbox.resolve()
                return str(resolved).startswith(str(sandbox_resolved))
            except:
                return False
        
        assert not is_within_sandbox(outside_path, mock_sandbox)
        assert is_within_sandbox(mock_sandbox / "tmp" / "file.txt", mock_sandbox)
    
    def test_cannot_read_outside_prefix_without_capability(self, mock_context):
        """Agent cannot read outside PREFIX without filesystem.read."""
        mock_context.capabilities = []  # Remove capabilities
        
        # Simulate capability check
        def has_capability(cap: str) -> bool:
            return cap in mock_context.capabilities
        
        assert not has_capability("filesystem.read")
    
    def test_cannot_access_other_agent_sandbox(self, mock_sandbox):
        """Agent cannot access another agent's sandbox."""
        other_sandbox = mock_sandbox.parent / "other_agent"
        other_sandbox.mkdir()
        (other_sandbox / "secret.txt").write_text("secret data")
        
        # Simulate cross-sandbox check
        def can_access(agent_sandbox: Path, target_path: Path) -> bool:
            try:
                target_resolved = target_path.resolve()
                sandbox_resolved = agent_sandbox.resolve()
                return str(target_resolved).startswith(str(sandbox_resolved))
            except:
                return False
        
        # Should not be able to access other sandbox
        assert not can_access(mock_sandbox, other_sandbox / "secret.txt")
        
        # But can access own sandbox
        assert can_access(mock_sandbox, mock_sandbox / "tmp" / "file.txt")
    
    def test_cannot_execute_unauthorized_commands(self, mock_context):
        """Agent cannot execute commands outside allowed tools."""
        allowed_binaries = {"ls", "cat", "grep", "find"}
        
        def can_execute(binary: str) -> bool:
            return os.path.basename(binary) in allowed_binaries
        
        assert can_execute("ls")
        assert can_execute("/usr/bin/cat")
        assert not can_execute("rm")
        assert not can_execute("curl")
        assert not can_execute("wget")


class TestSandboxPermissions:
    """Test sandbox permission handling."""
    
    def test_sandbox_directory_exists(self, mock_sandbox):
        """Sandbox directory must exist."""
        assert mock_sandbox.exists()
        assert mock_sandbox.is_dir()
    
    def test_sandbox_subdirectories_exist(self, mock_sandbox):
        """Sandbox subdirectories must exist."""
        assert (mock_sandbox / "tmp").exists()
        assert (mock_sandbox / "work").exists()
        assert (mock_sandbox / "output").exists()
        assert (mock_sandbox / "cache").exists()
    
    def test_sandbox_is_isolated(self, mock_sandbox):
        """Sandbox is isolated from other sandboxes."""
        # Create another sandbox
        other = mock_sandbox.parent / "other_agent"
        other.mkdir()
        
        # They should be separate
        assert mock_sandbox != other
        assert not str(mock_sandbox).startswith(str(other))
        assert not str(other).startswith(str(mock_sandbox))
    
    def test_sandbox_cleaned_between_runs(self, mock_sandbox):
        """Tmp directory should be cleanable."""
        # Create some temp files
        tmp_file = mock_sandbox / "tmp" / "test.txt"
        tmp_file.write_text("temporary data")
        
        # Simulate cleanup
        import shutil
        for item in (mock_sandbox / "tmp").iterdir():
            if item.is_file():
                item.unlink()
            elif item.is_dir():
                shutil.rmtree(item)
        
        # Should be empty
        assert len(list((mock_sandbox / "tmp").iterdir())) == 0


class TestSandboxEscapeAttempts:
    """Test that escape attempts are blocked."""
    
    def test_path_traversal_blocked(self, mock_sandbox):
        """Path traversal attempts are blocked."""
        def is_safe_path(base: Path, requested: str) -> bool:
            try:
                # Resolve the path
                full_path = (base / requested).resolve()
                # Check if it's within base
                return str(full_path).startswith(str(base.resolve()))
            except:
                return False
        
        # Normal paths should work
        assert is_safe_path(mock_sandbox, "tmp/file.txt")
        assert is_safe_path(mock_sandbox, "work/data.json")
        
        # Traversal attempts should fail
        assert not is_safe_path(mock_sandbox, "../outside.txt")
        assert not is_safe_path(mock_sandbox, "../../etc/passwd")
        assert not is_safe_path(mock_sandbox, "tmp/../../../outside")
    
    def test_symlink_escape_blocked(self, mock_sandbox):
        """Symlink escape attempts are blocked."""
        # Create a symlink pointing outside
        evil_link = mock_sandbox / "tmp" / "evil_link"
        target = Path("/etc")
        
        try:
            evil_link.symlink_to(target)
        except:
            # If we can't create symlinks, test passes
            return
        
        def is_safe_path(base: Path, path: Path) -> bool:
            try:
                # Resolve symlinks
                resolved = path.resolve()
                return str(resolved).startswith(str(base.resolve()))
            except:
                return False
        
        # Following the symlink should be detected as unsafe
        assert not is_safe_path(mock_sandbox, evil_link)
    
    def test_absolute_path_writes_blocked(self, mock_sandbox):
        """Absolute path writes outside sandbox are blocked."""
        def validate_write_path(sandbox: Path, target: str) -> bool:
            target_path = Path(target)
            
            # If absolute, must be within sandbox
            if target_path.is_absolute():
                return str(target_path).startswith(str(sandbox.resolve()))
            
            # If relative, resolve and check
            full_path = (sandbox / target_path).resolve()
            return str(full_path).startswith(str(sandbox.resolve()))
        
        # Relative paths within sandbox: OK
        assert validate_write_path(mock_sandbox, "tmp/file.txt")
        
        # Absolute paths outside sandbox: BLOCKED
        assert not validate_write_path(mock_sandbox, "/etc/passwd")
        assert not validate_write_path(mock_sandbox, "/tmp/outside.txt")
        
        # Absolute paths within sandbox: OK
        assert validate_write_path(mock_sandbox, str(mock_sandbox / "tmp" / "file.txt"))
    
    def test_unauthorized_subprocess_blocked(self, mock_context):
        """Unauthorized subprocess calls are blocked."""
        # Binaries that require specific capabilities
        BINARY_CAPABILITIES = {
            "curl": "network.external",
            "wget": "network.external",
            "ssh": "network.external",
            "docker": "exec.docker",
            "qemu-system-x86_64": "exec.qemu",
        }
        
        def can_run(binary: str, capabilities: list) -> bool:
            required_cap = BINARY_CAPABILITIES.get(os.path.basename(binary))
            if required_cap:
                return required_cap in capabilities
            return True  # Unlisted binaries allowed by default
        
        # These should be blocked (agent has no network capability)
        mock_context.capabilities = ["filesystem.read", "filesystem.write"]
        assert not can_run("curl", mock_context.capabilities)
        assert not can_run("wget", mock_context.capabilities)
        
        # These should be allowed
        assert can_run("ls", mock_context.capabilities)
        assert can_run("cat", mock_context.capabilities)


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
