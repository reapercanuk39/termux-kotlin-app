"""
Agent Supervisor (agentd)
=========================

AGENTD SYSTEM PROMPT — Termux-Kotlin Offline Agent Framework
Version: 1.0.0
Scope: Governs all agent behavior, reasoning, execution, and capability enforcement
Mode: Deterministic, offline, capability-restricted

You are agentd, the offline agent supervisor for Termux-Kotlin OS.

Your responsibilities:
• Load agent definitions
• Enforce capabilities and permissions
• Load skills dynamically
• Execute tasks safely and deterministically
• Maintain per-agent memory
• Manage per-agent sandboxes
• Log all actions
• Never use the network unless explicitly allowed
• Never call external APIs
• Never perform actions outside declared capabilities

You are NOT a general AI.
You are a **local automation engine**.

This is a fully offline system - no external API calls are made.
"""

import os
import sys
import json
import logging
import socket
import uuid
from pathlib import Path
from datetime import datetime
from typing import Any, Callable, Dict, List, Optional, Tuple
from dataclasses import dataclass, field
from enum import Enum

# Try to import YAML
try:
    import yaml
    HAS_YAML = True
except ImportError:
    HAS_YAML = False

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("agentd")


# =============================================================================
# SECTION: Error Types (Section 8 of System Prompt)
# =============================================================================

class AgentErrorType(Enum):
    """Structured error types for agent operations."""
    CAPABILITY_DENIED = "capability_denied"
    SKILL_NOT_ALLOWED = "skill_not_allowed"
    SKILL_MISSING = "skill_missing"
    INVALID_PATH = "invalid_path"
    SANDBOX_VIOLATION = "sandbox_violation"
    EXECUTION_ERROR = "execution_error"
    MEMORY_ERROR = "memory_error"
    NETWORK_VIOLATION = "network_violation"
    UNKNOWN_ERROR = "unknown_error"


@dataclass
class AgentError:
    """Structured error for agent operations."""
    error_type: AgentErrorType
    message: str
    agent: str
    required: Optional[str] = None
    details: Optional[Dict[str, Any]] = None
    
    def to_dict(self) -> Dict[str, Any]:
        result = {
            "error": self.error_type.value,
            "message": self.message,
            "agent": self.agent
        }
        if self.required:
            result["required"] = self.required
        if self.details:
            result["details"] = self.details
        return result


class AgentException(Exception):
    """Exception wrapping AgentError."""
    def __init__(self, agent_error: AgentError):
        self.agent_error = agent_error
        super().__init__(agent_error.message)


# =============================================================================
# SECTION: Task Execution Model (Section 6 of System Prompt)
# =============================================================================

@dataclass
class TaskStep:
    """Represents a single step in task execution."""
    step_id: int
    action: str
    status: str  # "pending", "running", "completed", "failed", "skipped"
    started_at: Optional[str] = None
    completed_at: Optional[str] = None
    result: Optional[Any] = None
    error: Optional[str] = None
    capability_checks: List[str] = field(default_factory=list)
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "step_id": self.step_id,
            "action": self.action,
            "status": self.status,
            "started_at": self.started_at,
            "completed_at": self.completed_at,
            "result": self.result,
            "error": self.error,
            "capability_checks": self.capability_checks
        }


@dataclass
class TaskResult:
    """
    Structured output format (Section 6):
    {
        "status": "success" | "error",
        "agent": "<agent_name>",
        "task": "<task>",
        "steps": [...],
        "result": <any>,
        "logs": "<path_to_log>"
    }
    """
    status: str  # "success" or "error"
    agent: str
    task: str
    steps: List[TaskStep] = field(default_factory=list)
    result: Any = None
    logs: Optional[str] = None
    error: Optional[AgentError] = None
    task_id: Optional[str] = None
    started_at: Optional[str] = None
    completed_at: Optional[str] = None
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "status": self.status,
            "agent": self.agent,
            "task": self.task,
            "task_id": self.task_id,
            "started_at": self.started_at,
            "completed_at": self.completed_at,
            "steps": [s.to_dict() for s in self.steps],
            "result": self.result,
            "logs": self.logs,
            "error": self.error.to_dict() if self.error else None
        }


# =============================================================================
# SECTION: Capability System (Section 2 of System Prompt)
# =============================================================================

# All known capabilities
KNOWN_CAPABILITIES = {
    # Filesystem
    "filesystem.read",
    "filesystem.write", 
    "filesystem.exec",
    "filesystem.delete",
    # Network
    "network.none",
    "network.local",
    "network.external",
    # Execution
    "exec.pkg",
    "exec.git",
    "exec.qemu",
    "exec.iso",
    "exec.apk",
    "exec.docker",
    "exec.shell",
    "exec.python",
    "exec.build",
    "exec.analyze",
    "exec.compress",
    "exec.custom",
    # Memory
    "memory.read",
    "memory.write",
    "memory.shared",
    # System
    "system.info",
    "system.process",
    "system.env",
}

# Memory size limit (Section 5)
MEMORY_SIZE_LIMIT = 1024 * 1024  # 1MB


# =============================================================================
# SECTION: Agent Configuration
# =============================================================================

@dataclass
class AgentConfig:
    """Agent configuration loaded from YAML/JSON."""
    name: str
    description: str = ""
    capabilities: List[str] = field(default_factory=list)
    skills: List[str] = field(default_factory=list)
    memory_backend: str = "json"
    sandbox_path: Optional[str] = None
    max_memory_bytes: int = MEMORY_SIZE_LIMIT
    max_task_timeout: int = 3600  # 1 hour
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "AgentConfig":
        return cls(
            name=data.get("name", "unnamed"),
            description=data.get("description", ""),
            capabilities=data.get("capabilities", []),
            skills=data.get("skills", []),
            memory_backend=data.get("memory_backend", "json"),
            sandbox_path=data.get("sandbox_path"),
            max_memory_bytes=data.get("max_memory_bytes", MEMORY_SIZE_LIMIT),
            max_task_timeout=data.get("max_task_timeout", 3600)
        )
    
    def has_capability(self, cap: str) -> bool:
        """Check if agent has a specific capability."""
        return cap in self.capabilities
    
    def has_network_access(self) -> bool:
        """Check if agent has any network access."""
        return ("network.local" in self.capabilities or 
                "network.external" in self.capabilities)
    
    def is_network_blocked(self) -> bool:
        """Check if agent has network.none (explicit block)."""
        return "network.none" in self.capabilities


# =============================================================================
# SECTION: Agent Daemon (Core Supervisor)
# =============================================================================

class AgentDaemon:
    """
    Agent Supervisor Daemon.
    
    This is the core of the offline automation layer.
    
    Responsibilities (Section 0 of System Prompt):
    - Load agent definitions from /usr/share/agents/models/*.yml
    - Enforce capabilities and permissions (Section 2)
    - Load skills dynamically with validation (Section 3)
    - Manage sandboxes per agent (Section 4)
    - Manage memory per agent (Section 5)
    - Execute tasks with step-by-step logging (Section 6)
    - Guarantee offline operation (Section 7)
    - Produce structured errors (Section 8)
    - Log all actions (Section 9)
    """
    
    def __init__(self, agents_root: Optional[Path] = None):
        # Set up paths - prefer environment variables set by wrapper script
        if agents_root:
            self.agents_root = Path(agents_root)
        elif "AGENTS_ROOT" in os.environ:
            self.agents_root = Path(os.environ["AGENTS_ROOT"])
        else:
            # Default Termux path
            prefix = os.environ.get("PREFIX", "/data/data/com.termux/files/usr")
            self.agents_root = Path(prefix) / "share" / "agents"
        
        # Standard directories
        self.models_dir = self.agents_root / "models"
        self.skills_dir = self.agents_root / "skills"
        self.sandboxes_dir = self.agents_root / "sandboxes"
        self.memory_dir = self.agents_root / "memory"
        self.logs_dir = self.agents_root / "logs"
        
        # Ensure directories exist
        for d in [self.models_dir, self.skills_dir, self.sandboxes_dir,
                  self.memory_dir, self.logs_dir]:
            d.mkdir(parents=True, exist_ok=True)
        
        # Load capabilities
        self.capabilities = self._load_capabilities()
        
        # Agent cache
        self._agents: Dict[str, AgentConfig] = {}
        self._load_agents()
        
        # Skill manifest cache (for validation)
        self._skill_manifests: Dict[str, Dict[str, Any]] = {}
        
        logger.info(f"AgentDaemon initialized at {self.agents_root}")
        logger.info(f"Loaded {len(self._agents)} agents")
    
    # =========================================================================
    # SECTION: Initialization & Loading
    # =========================================================================
    
    def _load_capabilities(self) -> Dict[str, Any]:
        """Load capability definitions."""
        cap_file = self.agents_root / "core" / "models" / "capabilities.yml"
        
        if cap_file.exists() and HAS_YAML:
            with open(cap_file) as f:
                return yaml.safe_load(f)
        
        # Default minimal capabilities
        return {
            "version": "1.0",
            "capabilities": {}
        }
    
    def _load_agent_file(self, filepath: Path) -> Optional[AgentConfig]:
        """Load a single agent configuration."""
        try:
            if filepath.suffix in [".yml", ".yaml"] and HAS_YAML:
                with open(filepath) as f:
                    data = yaml.safe_load(f)
            elif filepath.suffix == ".json":
                with open(filepath) as f:
                    data = json.load(f)
            else:
                return None
            
            if data:
                return AgentConfig.from_dict(data)
        except Exception as e:
            logger.error(f"Failed to load {filepath}: {e}")
        
        return None
    
    def _load_agents(self) -> None:
        """Load all agent definitions."""
        self._agents = {}
        
        if not self.models_dir.exists():
            return
        
        for filepath in self.models_dir.iterdir():
            if filepath.suffix in [".yml", ".yaml", ".json"]:
                agent = self._load_agent_file(filepath)
                if agent:
                    self._agents[agent.name] = agent
                    logger.debug(f"Loaded agent: {agent.name}")
    
    # =========================================================================
    # SECTION: Logging (Section 9 of System Prompt)
    # =========================================================================
    
    def _get_log_file(self, agent_name: str) -> Path:
        """Get log file path for agent."""
        return self.logs_dir / f"{agent_name}.log"
    
    def _log_action(
        self,
        agent_name: str,
        action: str,
        status: str,
        details: Optional[Dict[str, Any]] = None,
        capability_checks: Optional[List[str]] = None,
        skill_loads: Optional[List[str]] = None,
        subprocess_cmd: Optional[str] = None,
        error: Optional[AgentError] = None
    ) -> None:
        """
        Log an action to the agent's log file.
        
        Logs must include (Section 9):
        - Timestamp
        - Agent name
        - Action
        - Capability checks
        - Skill loads
        - Subprocess commands
        - Errors
        - Final result
        """
        log_file = self._get_log_file(agent_name)
        
        entry = {
            "timestamp": datetime.now().isoformat(),
            "agent": agent_name,
            "action": action,
            "status": status,
        }
        
        if details:
            entry["details"] = details
        if capability_checks:
            entry["capability_checks"] = capability_checks
        if skill_loads:
            entry["skill_loads"] = skill_loads
        if subprocess_cmd:
            entry["subprocess_cmd"] = subprocess_cmd
        if error:
            entry["error"] = error.to_dict()
        
        with open(log_file, "a") as f:
            f.write(json.dumps(entry) + "\n")
    
    # =========================================================================
    # SECTION: Capability Enforcement (Section 2 of System Prompt)
    # =========================================================================
    
    def _check_capability(
        self,
        agent: AgentConfig,
        required: str
    ) -> Tuple[bool, Optional[AgentError]]:
        """
        Check if agent has required capability.
        
        Before performing ANY action, you must check (Section 2):
        1. Does the agent have the required capability?
        """
        if required not in agent.capabilities:
            error = AgentError(
                error_type=AgentErrorType.CAPABILITY_DENIED,
                message=f"Agent '{agent.name}' lacks required capability: {required}",
                agent=agent.name,
                required=required
            )
            self._log_action(
                agent.name,
                "capability_check",
                "denied",
                capability_checks=[required],
                error=error
            )
            return False, error
        
        return True, None
    
    def _check_skill_capabilities(
        self,
        agent: AgentConfig,
        skill_name: str
    ) -> Tuple[bool, Optional[AgentError]]:
        """
        Check if agent has capabilities required by skill.
        
        Section 2: Does the skill require additional capabilities?
        """
        manifest = self._get_skill_manifest(skill_name)
        if not manifest:
            return True, None  # No manifest = no requirements
        
        required_caps = manifest.get("requires_capabilities", [])
        for cap in required_caps:
            has_cap, error = self._check_capability(agent, cap)
            if not has_cap:
                return False, error
        
        return True, None
    
    def _check_sandbox_boundary(
        self,
        agent: AgentConfig,
        path: Path
    ) -> Tuple[bool, Optional[AgentError]]:
        """
        Check if path is within agent's sandbox.
        
        Section 2: Does the action violate sandbox boundaries?
        Section 4: No agent may access another agent's sandbox
        """
        sandbox_root = self.sandboxes_dir / agent.name
        
        try:
            resolved = Path(path).resolve()
            sandbox_resolved = sandbox_root.resolve()
            
            # Allow access within sandbox
            if str(resolved).startswith(str(sandbox_resolved)):
                return True, None
            
            # Allow access to PREFIX (with filesystem.write capability)
            termux_prefix = Path("/data/data/com.termux/files/usr")
            if resolved.is_relative_to(termux_prefix):
                if agent.has_capability("filesystem.write"):
                    return True, None
                else:
                    error = AgentError(
                        error_type=AgentErrorType.CAPABILITY_DENIED,
                        message=f"Agent '{agent.name}' needs filesystem.write to access PREFIX",
                        agent=agent.name,
                        required="filesystem.write",
                        details={"path": str(path)}
                    )
                    return False, error
            
            # Check if trying to access another agent's sandbox
            for other_agent in self._agents.values():
                if other_agent.name != agent.name:
                    other_sandbox = (self.sandboxes_dir / other_agent.name).resolve()
                    if str(resolved).startswith(str(other_sandbox)):
                        error = AgentError(
                            error_type=AgentErrorType.SANDBOX_VIOLATION,
                            message=f"Agent '{agent.name}' cannot access sandbox of '{other_agent.name}'",
                            agent=agent.name,
                            details={"target_sandbox": other_agent.name, "path": str(path)}
                        )
                        self._log_action(agent.name, "sandbox_check", "denied", error=error)
                        return False, error
            
            # Allow read access to general filesystem with capability
            if agent.has_capability("filesystem.read"):
                return True, None
            
            error = AgentError(
                error_type=AgentErrorType.SANDBOX_VIOLATION,
                message=f"Path '{path}' is outside agent sandbox",
                agent=agent.name,
                details={"sandbox": str(sandbox_root), "path": str(path)}
            )
            self._log_action(agent.name, "sandbox_check", "denied", error=error)
            return False, error
            
        except (ValueError, OSError) as e:
            error = AgentError(
                error_type=AgentErrorType.INVALID_PATH,
                message=f"Invalid path: {path}",
                agent=agent.name,
                details={"error": str(e)}
            )
            return False, error
    
    def _check_network_access(
        self,
        agent: AgentConfig,
        target: Optional[str] = None
    ) -> Tuple[bool, Optional[AgentError]]:
        """
        Check if agent is allowed network access.
        
        Section 2: Does the action attempt network access?
        Section 7: Offline Guarantee
        """
        # If agent has network.none, block all network access
        if agent.is_network_blocked():
            error = AgentError(
                error_type=AgentErrorType.NETWORK_VIOLATION,
                message=f"Agent '{agent.name}' has network.none capability - network access blocked",
                agent=agent.name,
                required="network.local or network.external"
            )
            return False, error
        
        # If no target specified and agent has no network capabilities, default deny
        if not agent.has_network_access():
            error = AgentError(
                error_type=AgentErrorType.NETWORK_VIOLATION,
                message=f"Agent '{agent.name}' has no network capability",
                agent=agent.name,
                required="network.local or network.external"
            )
            return False, error
        
        # If target is localhost and agent has network.local, allow
        if target:
            is_localhost = target in ["localhost", "127.0.0.1", "::1"]
            if is_localhost and agent.has_capability("network.local"):
                return True, None
            if not is_localhost and not agent.has_capability("network.external"):
                error = AgentError(
                    error_type=AgentErrorType.NETWORK_VIOLATION,
                    message=f"Agent '{agent.name}' cannot access external network",
                    agent=agent.name,
                    required="network.external",
                    details={"target": target}
                )
                return False, error
        
        return True, None
    
    def _enforce_offline_guarantee(self) -> None:
        """
        Section 7: Offline Guarantee
        
        Block actual network connections by clearing proxy env vars
        and setting restrictive socket options.
        """
        # Clear proxy environment variables
        for var in ["http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY",
                    "ftp_proxy", "FTP_PROXY", "all_proxy", "ALL_PROXY"]:
            if var in os.environ:
                del os.environ[var]
        
        # Set no_proxy to block external access by default
        os.environ["no_proxy"] = "*"
    
    def validate_capabilities(
        self,
        agent_name: str,
        required: List[str]
    ) -> Tuple[bool, List[str]]:
        """Validate agent has required capabilities."""
        agent = self._agents.get(agent_name)
        if not agent:
            return False, [f"Agent not found: {agent_name}"]
        
        agent_caps = set(agent.capabilities)
        missing = []
        
        for cap in required:
            if cap not in agent_caps:
                missing.append(cap)
        
        return len(missing) == 0, missing
    
    # =========================================================================
    # SECTION: Skill Loading (Section 3 of System Prompt)
    # =========================================================================
    
    def _get_skill_manifest(self, skill_name: str) -> Optional[Dict[str, Any]]:
        """Load skill manifest with caching."""
        if skill_name in self._skill_manifests:
            return self._skill_manifests[skill_name]
        
        skill_dir = self.skills_dir / skill_name
        
        # Try YAML first
        for manifest_file in ["skill.yml", "skill.yaml", "skill.json"]:
            manifest_path = skill_dir / manifest_file
            if manifest_path.exists():
                try:
                    if manifest_file.endswith(".json"):
                        with open(manifest_path) as f:
                            manifest = json.load(f)
                    elif HAS_YAML:
                        with open(manifest_path) as f:
                            manifest = yaml.safe_load(f)
                    else:
                        continue
                    
                    self._skill_manifests[skill_name] = manifest
                    return manifest
                except Exception as e:
                    logger.error(f"Failed to load skill manifest {manifest_path}: {e}")
        
        return None
    
    def _validate_skill_for_agent(
        self,
        agent: AgentConfig,
        skill_name: str
    ) -> Tuple[bool, Optional[AgentError]]:
        """
        Validate skill can be loaded by agent.
        
        Section 3: Skill Loading Rules
        - Load only skills declared in the agent config
        - Verify required capabilities
        - Reject skills that exceed agent permissions
        """
        # Check skill is declared
        if skill_name not in agent.skills:
            error = AgentError(
                error_type=AgentErrorType.SKILL_NOT_ALLOWED,
                message=f"Agent '{agent.name}' does not have skill '{skill_name}' in its allowed list",
                agent=agent.name,
                details={"skill": skill_name, "allowed_skills": agent.skills}
            )
            self._log_action(agent.name, "skill_validation", "denied", error=error)
            return False, error
        
        # Check skill exists
        skill_dir = self.skills_dir / skill_name
        if not skill_dir.exists():
            error = AgentError(
                error_type=AgentErrorType.SKILL_MISSING,
                message=f"Skill '{skill_name}' not found at {skill_dir}",
                agent=agent.name,
                details={"skill": skill_name, "expected_path": str(skill_dir)}
            )
            self._log_action(agent.name, "skill_validation", "missing", error=error)
            return False, error
        
        # Check skill's required capabilities
        has_caps, cap_error = self._check_skill_capabilities(agent, skill_name)
        if not has_caps:
            return False, cap_error
        
        self._log_action(
            agent.name,
            "skill_validation",
            "allowed",
            skill_loads=[skill_name]
        )
        return True, None
    
    def _load_skill_for_agent(
        self,
        agent: AgentConfig,
        skill_name: str,
        executor,
        sandbox,
        memory
    ):
        """Load and instantiate a skill for an agent."""
        # Validate first
        valid, error = self._validate_skill_for_agent(agent, skill_name)
        if not valid:
            raise AgentException(error)
        
        # Import and create
        from agents.skills.loader import SkillLoader
        loader = SkillLoader(self.skills_dir)
        skill = loader.create_skill(skill_name, executor, sandbox, memory)
        
        if skill is None:
            error = AgentError(
                error_type=AgentErrorType.SKILL_MISSING,
                message=f"Failed to instantiate skill '{skill_name}'",
                agent=agent.name,
                details={"skill": skill_name}
            )
            raise AgentException(error)
        
        return skill
    
    # =========================================================================
    # SECTION: Memory Management (Section 5 of System Prompt)
    # =========================================================================
    
    def _check_memory_size(
        self,
        agent: AgentConfig,
        memory_file: Path
    ) -> Tuple[bool, Optional[AgentError]]:
        """
        Check memory file does not exceed limit.
        
        Section 5: Must never exceed 1MB
        """
        if not memory_file.exists():
            return True, None
        
        size = memory_file.stat().st_size
        if size > agent.max_memory_bytes:
            error = AgentError(
                error_type=AgentErrorType.MEMORY_ERROR,
                message=f"Memory file exceeds limit ({size} > {agent.max_memory_bytes} bytes)",
                agent=agent.name,
                details={"size_bytes": size, "limit_bytes": agent.max_memory_bytes}
            )
            return False, error
        
        return True, None
    
    # =========================================================================
    # SECTION: Task Execution (Section 6 of System Prompt)
    # =========================================================================
    
    def list_agents(self) -> List[Dict[str, Any]]:
        """List all available agents."""
        return [
            {
                "name": agent.name,
                "description": agent.description,
                "capabilities": agent.capabilities,
                "skills": agent.skills
            }
            for agent in self._agents.values()
        ]
    
    def get_agent_info(self, agent_name: str) -> Optional[Dict[str, Any]]:
        """Get detailed info about an agent."""
        agent = self._agents.get(agent_name)
        if not agent:
            return None
        
        # Get memory stats
        from agents.core.runtime.memory import AgentMemory
        memory = AgentMemory(agent_name, self.memory_dir)
        memory_stats = memory.get_stats()
        
        # Get sandbox stats
        from agents.core.runtime.sandbox import AgentSandbox
        sandbox = AgentSandbox(agent_name, self.sandboxes_dir)
        sandbox_stats = sandbox.get_disk_usage()
        
        return {
            "name": agent.name,
            "description": agent.description,
            "capabilities": agent.capabilities,
            "skills": agent.skills,
            "memory_backend": agent.memory_backend,
            "memory": memory_stats,
            "sandbox": sandbox_stats
        }
    
    def get_agent_logs(
        self,
        agent_name: str,
        limit: int = 50
    ) -> List[Dict[str, Any]]:
        """Get recent logs for an agent."""
        log_file = self._get_log_file(agent_name)
        
        if not log_file.exists():
            return []
        
        logs = []
        with open(log_file) as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        logs.append(json.loads(line))
                    except json.JSONDecodeError:
                        pass
        
        return logs[-limit:]
    
    def run_task(
        self,
        agent_name: str,
        task: str,
        args: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Run a task through the specified agent.
        
        Task Execution Model (Section 6):
        1. Load agent config
        2. Load memory
        3. Load allowed skills
        4. Validate capabilities
        5. Break task into steps
        6. Execute each step safely
        7. Log all actions
        8. Update memory
        9. Return structured output
        
        Args:
            agent_name: Name of agent to use
            task: Task description or skill.function call
            args: Additional arguments
        
        Returns:
            TaskResult as dict
        """
        # Generate task ID
        task_id = str(uuid.uuid4())[:8]
        started_at = datetime.now().isoformat()
        
        # Step 1: Load agent config
        agent = self._agents.get(agent_name)
        if not agent:
            error = AgentError(
                error_type=AgentErrorType.UNKNOWN_ERROR,
                message=f"Agent not found: {agent_name}",
                agent=agent_name
            )
            return TaskResult(
                status="error",
                agent=agent_name,
                task=task,
                task_id=task_id,
                started_at=started_at,
                completed_at=datetime.now().isoformat(),
                error=error,
                logs=str(self._get_log_file(agent_name))
            ).to_dict()
        
        self._log_action(
            agent_name, "run_task", "started",
            details={"task": task, "task_id": task_id, "args": args}
        )
        
        # Enforce offline guarantee (Section 7)
        self._enforce_offline_guarantee()
        
        steps: List[TaskStep] = []
        
        try:
            # Step 2: Load memory
            step = TaskStep(step_id=1, action="load_memory", status="running")
            step.started_at = datetime.now().isoformat()
            
            from agents.core.runtime.memory import AgentMemory
            from agents.core.runtime.sandbox import AgentSandbox
            from agents.core.runtime.executor import AgentExecutor
            
            memory = AgentMemory(agent_name, self.memory_dir)
            
            # Validate memory size (Section 5)
            memory_file = self.memory_dir / f"{agent_name}.json"
            mem_ok, mem_error = self._check_memory_size(agent, memory_file)
            if not mem_ok:
                step.status = "failed"
                step.error = mem_error.message
                step.completed_at = datetime.now().isoformat()
                steps.append(step)
                raise AgentException(mem_error)
            
            step.status = "completed"
            step.completed_at = datetime.now().isoformat()
            step.result = {"memory_loaded": True}
            steps.append(step)
            
            # Step 3: Setup sandbox
            step = TaskStep(step_id=2, action="setup_sandbox", status="running")
            step.started_at = datetime.now().isoformat()
            
            sandbox = AgentSandbox(agent_name, self.sandboxes_dir)
            
            step.status = "completed"
            step.completed_at = datetime.now().isoformat()
            step.result = {"sandbox_path": str(sandbox.sandbox_root)}
            steps.append(step)
            
            # Step 4: Create executor with capability enforcement
            step = TaskStep(step_id=3, action="create_executor", status="running")
            step.started_at = datetime.now().isoformat()
            
            def log_callback(entry):
                self._log_action(
                    agent_name,
                    entry.get("message", "action"),
                    entry.get("level", "INFO"),
                    details=entry,
                    subprocess_cmd=entry.get("command")
                )
            
            executor = AgentExecutor(
                agent_name=agent_name,
                capabilities=agent.capabilities,
                sandbox_path=sandbox.sandbox_root,
                log_callback=log_callback
            )
            
            step.status = "completed"
            step.completed_at = datetime.now().isoformat()
            step.capability_checks = agent.capabilities.copy()
            steps.append(step)
            
            # Step 5: Parse and execute task
            step = TaskStep(step_id=4, action="execute_task", status="running")
            step.started_at = datetime.now().isoformat()
            
            result = self._execute_task(
                agent, task, args or {},
                executor, sandbox, memory
            )
            
            step.status = "completed" if result.get("success", False) else "failed"
            step.completed_at = datetime.now().isoformat()
            step.result = result
            steps.append(step)
            
            # Step 6: Update memory
            step = TaskStep(step_id=5, action="update_memory", status="running")
            step.started_at = datetime.now().isoformat()
            
            memory.append_history({
                "task": task,
                "task_id": task_id,
                "args": args,
                "success": result.get("success", False),
                "completed_at": datetime.now().isoformat()
            })
            
            step.status = "completed"
            step.completed_at = datetime.now().isoformat()
            steps.append(step)
            
            # Log completion
            self._log_action(
                agent_name, "run_task", "completed",
                details={"task": task, "task_id": task_id, "success": result.get("success", False)}
            )
            
            return TaskResult(
                status="success" if result.get("success", False) else "error",
                agent=agent_name,
                task=task,
                task_id=task_id,
                started_at=started_at,
                completed_at=datetime.now().isoformat(),
                steps=steps,
                result=result,
                logs=str(self._get_log_file(agent_name))
            ).to_dict()
            
        except AgentException as e:
            self._log_action(
                agent_name, "run_task", "failed",
                error=e.agent_error
            )
            return TaskResult(
                status="error",
                agent=agent_name,
                task=task,
                task_id=task_id,
                started_at=started_at,
                completed_at=datetime.now().isoformat(),
                steps=steps,
                error=e.agent_error,
                logs=str(self._get_log_file(agent_name))
            ).to_dict()
            
        except Exception as e:
            logger.exception(f"Task failed: {e}")
            error = AgentError(
                error_type=AgentErrorType.EXECUTION_ERROR,
                message=str(e),
                agent=agent_name
            )
            self._log_action(
                agent_name, "run_task", "failed",
                error=error
            )
            return TaskResult(
                status="error",
                agent=agent_name,
                task=task,
                task_id=task_id,
                started_at=started_at,
                completed_at=datetime.now().isoformat(),
                steps=steps,
                error=error,
                logs=str(self._get_log_file(agent_name))
            ).to_dict()
    
    def _execute_task(
        self,
        agent: AgentConfig,
        task: str,
        args: Dict[str, Any],
        executor,
        sandbox,
        memory
    ) -> Dict[str, Any]:
        """
        Execute a task for an agent.
        
        Think step-by-step (Section 1):
        - Break tasks into sub-steps
        - Validate capabilities before each action
        - Validate skill requirements before loading
        - Validate filesystem paths before reading/writing
        - Log every decision and action
        """
        from agents.skills.loader import SkillLoader
        
        # Check if task is a skill.function call
        if "." in task and " " not in task:
            parts = task.split(".", 1)
            skill_name = parts[0]
            func_name = parts[1] if len(parts) > 1 else None
            
            # Validate skill is allowed for this agent (Section 3)
            valid, error = self._validate_skill_for_agent(agent, skill_name)
            if not valid:
                return {
                    "success": False,
                    "error": error.to_dict() if error else "Skill validation failed"
                }
            
            # Load and run skill
            loader = SkillLoader(self.skills_dir)
            
            try:
                skill = self._load_skill_for_agent(
                    agent, skill_name, executor, sandbox, memory
                )
            except AgentException as e:
                return {
                    "success": False,
                    "error": e.agent_error.to_dict()
                }
            
            if func_name:
                self._log_action(
                    agent.name,
                    f"skill_call:{skill_name}.{func_name}",
                    "executing",
                    skill_loads=[skill_name],
                    details={"function": func_name, "args": args}
                )
                result = skill.call(func_name, **args)
                return result.to_dict()
            else:
                return {
                    "success": True,
                    "data": skill.get_manifest()
                }
        
        # Natural language task - return guidance
        return {
            "success": True,
            "message": f"Task received: {task}",
            "note": "Natural language task interpretation not yet implemented. "
                    "Use skill.function format (e.g., 'pkg.install_package')",
            "available_skills": agent.skills,
            "example_tasks": [
                f"{s}.self_test" for s in agent.skills[:3]
            ]
        }
    
    def validate_all(self) -> Dict[str, Any]:
        """
        Validate all agents and skills.
        
        Comprehensive validation including:
        - Agent capability validation
        - Skill existence and manifest validation
        - Sandbox integrity
        - Memory file validation
        """
        results = {
            "agents": {},
            "skills": {},
            "sandboxes": {},
            "memory": {},
            "errors": [],
            "summary": {
                "agents_valid": 0,
                "agents_invalid": 0,
                "skills_valid": 0,
                "skills_invalid": 0
            }
        }
        
        # Validate agents
        for name, agent in self._agents.items():
            agent_result = {
                "valid": True,
                "issues": [],
                "capabilities": agent.capabilities,
                "skills": agent.skills
            }
            
            # Check capabilities are known
            all_caps = self._get_all_capability_names()
            for cap in agent.capabilities:
                if cap not in all_caps:
                    agent_result["issues"].append(f"Unknown capability: {cap}")
            
            # Check skills exist
            for skill in agent.skills:
                skill_dir = self.skills_dir / skill
                if not skill_dir.exists():
                    agent_result["issues"].append(f"Missing skill: {skill}")
                else:
                    # Check skill manifest
                    manifest = self._get_skill_manifest(skill)
                    if not manifest:
                        agent_result["issues"].append(f"Missing manifest for skill: {skill}")
                    else:
                        # Check agent has required capabilities for skill
                        req_caps = manifest.get("requires_capabilities", [])
                        for cap in req_caps:
                            if cap not in agent.capabilities:
                                agent_result["issues"].append(
                                    f"Skill '{skill}' requires '{cap}' but agent lacks it"
                                )
            
            # Validate sandbox
            sandbox_root = self.sandboxes_dir / name
            if sandbox_root.exists():
                results["sandboxes"][name] = {
                    "exists": True,
                    "path": str(sandbox_root)
                }
            else:
                results["sandboxes"][name] = {
                    "exists": False,
                    "path": str(sandbox_root)
                }
            
            # Validate memory
            memory_file = self.memory_dir / f"{name}.json"
            if memory_file.exists():
                size = memory_file.stat().st_size
                mem_ok, _ = self._check_memory_size(agent, memory_file)
                results["memory"][name] = {
                    "exists": True,
                    "size_bytes": size,
                    "within_limit": mem_ok,
                    "limit_bytes": agent.max_memory_bytes
                }
            else:
                results["memory"][name] = {
                    "exists": False,
                    "size_bytes": 0,
                    "within_limit": True,
                    "limit_bytes": agent.max_memory_bytes
                }
            
            agent_result["valid"] = len(agent_result["issues"]) == 0
            results["agents"][name] = agent_result
            
            if agent_result["valid"]:
                results["summary"]["agents_valid"] += 1
            else:
                results["summary"]["agents_invalid"] += 1
        
        # Validate skills
        from agents.skills.loader import SkillLoader
        loader = SkillLoader(self.skills_dir)
        
        for skill_name in loader.discover_skills():
            skill_result = {
                "valid": True,
                "issues": [],
                "manifest": None
            }
            
            manifest = loader.load_manifest(skill_name)
            if not manifest:
                skill_result["issues"].append("No manifest file")
            else:
                skill_result["manifest"] = manifest
            
            skill_class = loader.load_skill_class(skill_name)
            if not skill_class:
                skill_result["issues"].append("Failed to load skill class")
            
            skill_result["valid"] = len(skill_result["issues"]) == 0
            results["skills"][skill_name] = skill_result
            
            if skill_result["valid"]:
                results["summary"]["skills_valid"] += 1
            else:
                results["summary"]["skills_invalid"] += 1
        
        return results
    
    def _get_all_capability_names(self) -> set:
        """Get all known capability names."""
        caps = KNOWN_CAPABILITIES.copy()
        
        cap_data = self.capabilities.get("capabilities", {})
        for category, items in cap_data.items():
            if isinstance(items, dict):
                for name in items.keys():
                    caps.add(f"{category}.{name}")
        
        # Add from presets
        presets = self.capabilities.get("presets", {})
        for preset_caps in presets.values():
            if isinstance(preset_caps, list):
                caps.update(preset_caps)
        
        return caps
    
    # =========================================================================
    # SECTION: Public API Methods
    # =========================================================================
    
    def check_agent_capability(
        self,
        agent_name: str,
        capability: str
    ) -> Dict[str, Any]:
        """
        Check if an agent has a specific capability.
        
        Returns structured result with allowed/denied status.
        """
        agent = self._agents.get(agent_name)
        if not agent:
            return {
                "allowed": False,
                "error": AgentError(
                    error_type=AgentErrorType.UNKNOWN_ERROR,
                    message=f"Agent not found: {agent_name}",
                    agent=agent_name
                ).to_dict()
            }
        
        has_cap, error = self._check_capability(agent, capability)
        return {
            "allowed": has_cap,
            "capability": capability,
            "agent": agent_name,
            "error": error.to_dict() if error else None
        }
    
    def check_sandbox_access(
        self,
        agent_name: str,
        path: str
    ) -> Dict[str, Any]:
        """
        Check if an agent can access a specific path.
        
        Enforces sandbox boundaries (Section 4).
        """
        agent = self._agents.get(agent_name)
        if not agent:
            return {
                "allowed": False,
                "error": "Agent not found"
            }
        
        allowed, error = self._check_sandbox_boundary(agent, Path(path))
        return {
            "allowed": allowed,
            "path": path,
            "agent": agent_name,
            "sandbox": str(self.sandboxes_dir / agent_name),
            "error": error.to_dict() if error else None
        }
    
    def check_network_access(
        self,
        agent_name: str,
        target: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Check if an agent can access network.
        
        Enforces offline guarantee (Section 7).
        """
        agent = self._agents.get(agent_name)
        if not agent:
            return {
                "allowed": False,
                "error": "Agent not found"
            }
        
        allowed, error = self._check_network_access(agent, target)
        return {
            "allowed": allowed,
            "target": target,
            "agent": agent_name,
            "has_network_local": agent.has_capability("network.local"),
            "has_network_external": agent.has_capability("network.external"),
            "is_blocked": agent.is_network_blocked(),
            "error": error.to_dict() if error else None
        }
    
    def get_agent_sandbox(self, agent_name: str) -> Optional[Dict[str, Any]]:
        """Get sandbox information for an agent."""
        agent = self._agents.get(agent_name)
        if not agent:
            return None
        
        from agents.core.runtime.sandbox import AgentSandbox
        sandbox = AgentSandbox(agent_name, self.sandboxes_dir)
        return sandbox.get_disk_usage()
    
    def clean_agent_sandbox(self, agent_name: str) -> Dict[str, Any]:
        """Clean an agent's sandbox (tmp and work directories)."""
        agent = self._agents.get(agent_name)
        if not agent:
            return {"success": False, "error": "Agent not found"}
        
        from agents.core.runtime.sandbox import AgentSandbox
        sandbox = AgentSandbox(agent_name, self.sandboxes_dir)
        
        tmp_cleaned = sandbox.clean_tmp()
        work_cleaned = sandbox.clean_work()
        
        self._log_action(
            agent_name,
            "sandbox_clean",
            "completed",
            details={"tmp_removed": tmp_cleaned, "work_removed": work_cleaned}
        )
        
        return {
            "success": True,
            "tmp_items_removed": tmp_cleaned,
            "work_items_removed": work_cleaned
        }
    
    def get_system_status(self) -> Dict[str, Any]:
        """Get overall system status."""
        return {
            "agents_root": str(self.agents_root),
            "agents_loaded": len(self._agents),
            "agent_names": list(self._agents.keys()),
            "skills_dir": str(self.skills_dir),
            "sandboxes_dir": str(self.sandboxes_dir),
            "memory_dir": str(self.memory_dir),
            "logs_dir": str(self.logs_dir),
            "offline_mode": True,
            "version": "1.0.0"
        }


# =============================================================================
# SECTION: Singleton and Convenience Functions
# =============================================================================

# Singleton instance
_daemon: Optional[AgentDaemon] = None


def get_daemon(agents_root: Optional[Path] = None) -> AgentDaemon:
    """Get or create the agent daemon singleton."""
    global _daemon
    if _daemon is None:
        _daemon = AgentDaemon(agents_root)
    return _daemon


def run_task(agent_name: str, task: str, args: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """Convenience function to run a task."""
    return get_daemon().run_task(agent_name, task, args)


def list_agents() -> List[Dict[str, Any]]:
    """Convenience function to list agents."""
    return get_daemon().list_agents()


def get_agent_info(agent_name: str) -> Optional[Dict[str, Any]]:
    """Convenience function to get agent info."""
    return get_daemon().get_agent_info(agent_name)


def get_agent_logs(agent_name: str, limit: int = 50) -> List[Dict[str, Any]]:
    """Convenience function to get agent logs."""
    return get_daemon().get_agent_logs(agent_name, limit)


def check_capability(agent_name: str, capability: str) -> Dict[str, Any]:
    """Convenience function to check agent capability."""
    return get_daemon().check_agent_capability(agent_name, capability)


def check_sandbox(agent_name: str, path: str) -> Dict[str, Any]:
    """Convenience function to check sandbox access."""
    return get_daemon().check_sandbox_access(agent_name, path)


def check_network(agent_name: str, target: Optional[str] = None) -> Dict[str, Any]:
    """Convenience function to check network access."""
    return get_daemon().check_network_access(agent_name, target)


def validate_all() -> Dict[str, Any]:
    """Convenience function to validate all agents and skills."""
    return get_daemon().validate_all()


def get_status() -> Dict[str, Any]:
    """Convenience function to get system status."""
    return get_daemon().get_system_status()


# =============================================================================
# SECTION: CLI Interface
# =============================================================================

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(
        description="Agent Daemon (agentd) - Termux-Kotlin Offline Agent Supervisor",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python agentd.py list                    List all agents
  python agentd.py info --agent build      Get agent info
  python agentd.py run -a build -t pkg.self_test
  python agentd.py validate                Validate all agents and skills
  python agentd.py status                  Get system status
  python agentd.py check-cap -a build -c exec.pkg
  python agentd.py check-sandbox -a build -p /tmp/test
  python agentd.py check-network -a build
  python agentd.py logs -a build -n 20
  python agentd.py clean -a build
        """
    )
    
    parser.add_argument(
        "command",
        choices=[
            "list", "info", "run", "validate", "status",
            "check-cap", "check-sandbox", "check-network",
            "logs", "clean"
        ],
        help="Command to execute"
    )
    parser.add_argument("--agent", "-a", help="Agent name")
    parser.add_argument("--task", "-t", help="Task to run (skill.function format)")
    parser.add_argument("--args", help="Task arguments as JSON string")
    parser.add_argument("--capability", "-c", help="Capability to check")
    parser.add_argument("--path", "-p", help="Path to check")
    parser.add_argument("--target", help="Network target to check")
    parser.add_argument("--limit", "-n", type=int, default=50, help="Log limit")
    parser.add_argument("--root", "-r", help="Agents root directory")
    parser.add_argument("--json", "-j", action="store_true", help="Output as JSON")
    
    args = parser.parse_args()
    
    daemon = AgentDaemon(Path(args.root) if args.root else None)
    
    def output(data, as_json=False):
        """Output data as JSON or formatted text."""
        if as_json or args.json:
            print(json.dumps(data, indent=2, default=str))
        else:
            if isinstance(data, dict):
                for k, v in data.items():
                    print(f"{k}: {v}")
            elif isinstance(data, list):
                for item in data:
                    if isinstance(item, dict):
                        print(json.dumps(item, indent=2))
                    else:
                        print(item)
            else:
                print(data)
    
    if args.command == "list":
        agents = daemon.list_agents()
        if args.json:
            output(agents, True)
        else:
            print(f"{'Name':<20} {'Description':<50} {'Skills'}")
            print("-" * 90)
            for agent in agents:
                skills = ", ".join(agent.get("skills", [])[:3])
                if len(agent.get("skills", [])) > 3:
                    skills += "..."
                print(f"{agent['name']:<20} {agent['description'][:50]:<50} {skills}")
    
    elif args.command == "info":
        if not args.agent:
            print("Error: --agent required")
            sys.exit(1)
        info = daemon.get_agent_info(args.agent)
        if info:
            output(info, True)
        else:
            print(f"Agent not found: {args.agent}")
            sys.exit(1)
    
    elif args.command == "run":
        if not args.agent or not args.task:
            print("Error: --agent and --task required")
            sys.exit(1)
        
        task_args = {}
        if args.args:
            try:
                task_args = json.loads(args.args)
            except json.JSONDecodeError as e:
                print(f"Error parsing --args JSON: {e}")
                sys.exit(1)
        
        result = daemon.run_task(args.agent, args.task, task_args)
        output(result, True)
    
    elif args.command == "validate":
        result = daemon.validate_all()
        output(result, True)
    
    elif args.command == "status":
        result = daemon.get_system_status()
        output(result, True)
    
    elif args.command == "check-cap":
        if not args.agent or not args.capability:
            print("Error: --agent and --capability required")
            sys.exit(1)
        result = daemon.check_agent_capability(args.agent, args.capability)
        output(result, True)
    
    elif args.command == "check-sandbox":
        if not args.agent or not args.path:
            print("Error: --agent and --path required")
            sys.exit(1)
        result = daemon.check_sandbox_access(args.agent, args.path)
        output(result, True)
    
    elif args.command == "check-network":
        if not args.agent:
            print("Error: --agent required")
            sys.exit(1)
        result = daemon.check_network_access(args.agent, args.target)
        output(result, True)
    
    elif args.command == "logs":
        if not args.agent:
            print("Error: --agent required")
            sys.exit(1)
        logs = daemon.get_agent_logs(args.agent, args.limit)
        if args.json:
            output(logs, True)
        else:
            for log in logs:
                ts = log.get("timestamp", "")[:19]
                action = log.get("action", "")
                status = log.get("status", "")
                print(f"[{ts}] {action}: {status}")
                if log.get("error"):
                    print(f"  ERROR: {log['error']}")
    
    elif args.command == "clean":
        if not args.agent:
            print("Error: --agent required")
            sys.exit(1)
        result = daemon.clean_agent_sandbox(args.agent)
        output(result, True)
