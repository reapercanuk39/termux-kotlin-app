"""
Agent Executor
==============

Safe command execution with capability enforcement.
All subprocess calls go through this module.
"""

import os
import subprocess
import shlex
import shutil
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from datetime import datetime
import logging

# Import YAML parser (fallback to json for simple cases)
try:
    import yaml
except ImportError:
    yaml = None


logger = logging.getLogger("agentd.executor")


class CapabilityError(Exception):
    """Raised when an agent lacks required capability."""
    pass


class ExecutionError(Exception):
    """Raised when command execution fails."""
    pass


class AgentExecutor:
    """
    Safe command executor with capability enforcement.
    
    All commands are checked against the agent's declared capabilities
    before execution. Network access is blocked by default.
    """
    
    # Binary to capability mapping
    BINARY_CAPABILITIES = {
        # Package management
        "pkg": "exec.pkg",
        "apt": "exec.pkg",
        "apt-get": "exec.pkg",
        "apt-cache": "exec.pkg",
        "dpkg": "exec.pkg",
        "dpkg-deb": "exec.pkg",
        
        # Git
        "git": "exec.git",
        
        # QEMU
        "qemu-system-x86_64": "exec.qemu",
        "qemu-system-aarch64": "exec.qemu",
        "qemu-system-arm": "exec.qemu",
        "qemu-img": "exec.qemu",
        
        # ISO tools
        "xorriso": "exec.iso",
        "mkisofs": "exec.iso",
        "isoinfo": "exec.iso",
        "genisoimage": "exec.iso",
        
        # APK tools
        "apktool": "exec.apk",
        "jadx": "exec.apk",
        "aapt": "exec.apk",
        "aapt2": "exec.apk",
        "zipalign": "exec.apk",
        "apksigner": "exec.apk",
        
        # Docker
        "docker": "exec.docker",
        "podman": "exec.docker",
        
        # Shell
        "bash": "exec.shell",
        "sh": "exec.shell",
        "zsh": "exec.shell",
        
        # Python
        "python": "exec.python",
        "python3": "exec.python",
        "pip": "exec.python",
        "pip3": "exec.python",
        
        # Build tools
        "make": "exec.build",
        "cmake": "exec.build",
        "gradle": "exec.build",
        "gradlew": "exec.build",
        "ninja": "exec.build",
        "meson": "exec.build",
        
        # Analysis tools
        "binwalk": "exec.analyze",
        "file": "exec.analyze",
        "strings": "exec.analyze",
        "hexdump": "exec.analyze",
        "objdump": "exec.analyze",
        "readelf": "exec.analyze",
        "nm": "exec.analyze",
        "ldd": "exec.analyze",
        
        # Compression
        "tar": "exec.compress",
        "gzip": "exec.compress",
        "bzip2": "exec.compress",
        "xz": "exec.compress",
        "zip": "exec.compress",
        "unzip": "exec.compress",
        "7z": "exec.compress",
    }
    
    # Commands that require network capability
    NETWORK_COMMANDS = {
        "curl", "wget", "ssh", "scp", "rsync", "nc", "netcat",
        "ping", "traceroute", "nmap", "telnet", "ftp", "sftp"
    }
    
    def __init__(
        self,
        agent_name: str,
        capabilities: List[str],
        sandbox_path: Path,
        log_callback: Optional[callable] = None
    ):
        self.agent_name = agent_name
        self.capabilities = set(capabilities)
        self.sandbox_path = Path(sandbox_path)
        self.log_callback = log_callback
        
        # Parse capability categories
        self._can_read = "filesystem.read" in self.capabilities
        self._can_write = "filesystem.write" in self.capabilities
        self._can_exec = "filesystem.exec" in self.capabilities
        self._can_delete = "filesystem.delete" in self.capabilities
        self._network_none = "network.none" in self.capabilities
        self._network_local = "network.local" in self.capabilities
        self._network_external = "network.external" in self.capabilities
    
    def _log(self, level: str, message: str, **kwargs) -> None:
        """Log action with optional callback."""
        entry = {
            "timestamp": datetime.now().isoformat(),
            "agent": self.agent_name,
            "level": level,
            "message": message,
            **kwargs
        }
        
        if level == "ERROR":
            logger.error(message)
        elif level == "WARNING":
            logger.warning(message)
        else:
            logger.info(message)
        
        if self.log_callback:
            self.log_callback(entry)
    
    def check_capability(self, capability: str) -> bool:
        """Check if agent has a specific capability."""
        return capability in self.capabilities
    
    def require_capability(self, capability: str) -> None:
        """Require a capability or raise error."""
        if not self.check_capability(capability):
            self._log("ERROR", f"Capability denied: {capability}")
            raise CapabilityError(
                f"Agent '{self.agent_name}' lacks capability: {capability}"
            )
    
    def _get_binary_capability(self, binary: str) -> Optional[str]:
        """Get required capability for a binary."""
        # Extract binary name from path
        binary_name = os.path.basename(binary)
        return self.BINARY_CAPABILITIES.get(binary_name)
    
    def _check_network_command(self, command: List[str]) -> bool:
        """Check if command requires network access."""
        if not command:
            return False
        binary = os.path.basename(command[0])
        return binary in self.NETWORK_COMMANDS
    
    def _validate_command(self, command: List[str]) -> None:
        """Validate command against capabilities."""
        if not command:
            raise ExecutionError("Empty command")
        
        binary = command[0]
        binary_name = os.path.basename(binary)
        
        # Check network commands
        if self._check_network_command(command):
            if self._network_none:
                raise CapabilityError(
                    f"Network access denied for agent '{self.agent_name}' "
                    f"(network.none). Command: {binary_name}"
                )
            if not self._network_external and not self._network_local:
                raise CapabilityError(
                    f"No network capability for agent '{self.agent_name}'. "
                    f"Command: {binary_name}"
                )
        
        # Check binary capability
        required_cap = self._get_binary_capability(binary_name)
        if required_cap and not self.check_capability(required_cap):
            raise CapabilityError(
                f"Agent '{self.agent_name}' lacks capability '{required_cap}' "
                f"required for binary: {binary_name}"
            )
    
    def run(
        self,
        command: List[str],
        cwd: Optional[Path] = None,
        env: Optional[Dict[str, str]] = None,
        timeout: int = 300,
        capture_output: bool = True,
        check: bool = True
    ) -> subprocess.CompletedProcess:
        """
        Execute a command with capability enforcement.
        
        Args:
            command: Command and arguments as list
            cwd: Working directory (defaults to sandbox work dir)
            env: Environment variables (merged with current env)
            timeout: Timeout in seconds
            capture_output: Capture stdout/stderr
            check: Raise on non-zero exit
        
        Returns:
            CompletedProcess instance
        
        Raises:
            CapabilityError: If agent lacks required capability
            ExecutionError: If command fails
        """
        # Validate command
        self._validate_command(command)
        
        # Set working directory
        if cwd is None:
            cwd = self.sandbox_path / "work"
        cwd = Path(cwd)
        cwd.mkdir(parents=True, exist_ok=True)
        
        # Prepare environment
        run_env = os.environ.copy()
        if env:
            run_env.update(env)
        
        # Block network if required
        if self._network_none:
            # Set environment to discourage network access
            run_env["http_proxy"] = ""
            run_env["https_proxy"] = ""
            run_env["HTTP_PROXY"] = ""
            run_env["HTTPS_PROXY"] = ""
            run_env["no_proxy"] = "*"
        
        # Log command
        self._log("INFO", f"Executing: {' '.join(command)}", cwd=str(cwd))
        
        try:
            result = subprocess.run(
                command,
                cwd=cwd,
                env=run_env,
                timeout=timeout,
                capture_output=capture_output,
                text=True
            )
            
            if check and result.returncode != 0:
                self._log(
                    "ERROR",
                    f"Command failed with code {result.returncode}",
                    stdout=result.stdout[:500] if result.stdout else "",
                    stderr=result.stderr[:500] if result.stderr else ""
                )
                raise ExecutionError(
                    f"Command failed: {' '.join(command)}\n"
                    f"Exit code: {result.returncode}\n"
                    f"Stderr: {result.stderr[:500] if result.stderr else 'N/A'}"
                )
            
            self._log(
                "INFO",
                f"Command completed with code {result.returncode}",
                exit_code=result.returncode
            )
            
            return result
            
        except subprocess.TimeoutExpired:
            self._log("ERROR", f"Command timed out after {timeout}s")
            raise ExecutionError(f"Command timed out after {timeout}s")
        except FileNotFoundError:
            self._log("ERROR", f"Binary not found: {command[0]}")
            raise ExecutionError(f"Binary not found: {command[0]}")
    
    def run_shell(
        self,
        script: str,
        cwd: Optional[Path] = None,
        env: Optional[Dict[str, str]] = None,
        timeout: int = 300
    ) -> subprocess.CompletedProcess:
        """
        Execute a shell script with capability enforcement.
        
        Requires: exec.shell capability
        """
        self.require_capability("exec.shell")
        
        # Find bash or sh
        shell = shutil.which("bash") or shutil.which("sh")
        if not shell:
            raise ExecutionError("No shell available")
        
        return self.run(
            [shell, "-c", script],
            cwd=cwd,
            env=env,
            timeout=timeout
        )
    
    def run_python(
        self,
        script: str,
        cwd: Optional[Path] = None,
        env: Optional[Dict[str, str]] = None,
        timeout: int = 300
    ) -> subprocess.CompletedProcess:
        """
        Execute a Python script with capability enforcement.
        
        Requires: exec.python capability
        """
        self.require_capability("exec.python")
        
        python = shutil.which("python3") or shutil.which("python")
        if not python:
            raise ExecutionError("No Python interpreter available")
        
        return self.run(
            [python, "-c", script],
            cwd=cwd,
            env=env,
            timeout=timeout
        )
    
    def which(self, binary: str) -> Optional[str]:
        """Find binary path, checking capability."""
        required_cap = self._get_binary_capability(binary)
        if required_cap and not self.check_capability(required_cap):
            return None
        return shutil.which(binary)
    
    def can_run(self, binary: str) -> bool:
        """Check if agent can run a specific binary."""
        required_cap = self._get_binary_capability(binary)
        if required_cap:
            return self.check_capability(required_cap)
        return True  # Allow unlisted binaries if no specific cap required
