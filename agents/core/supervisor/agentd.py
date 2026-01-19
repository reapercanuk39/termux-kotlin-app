"""
Agent Supervisor (agentd)
=========================

The core daemon that manages agent lifecycle, enforces capabilities,
and provides the internal API for running tasks.

This is a fully offline system - no external API calls are made.
"""

import os
import sys
import json
import logging
from pathlib import Path
from datetime import datetime
from typing import Any, Dict, List, Optional
from dataclasses import dataclass, field

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


@dataclass
class AgentConfig:
    """Agent configuration loaded from YAML/JSON."""
    name: str
    description: str = ""
    capabilities: List[str] = field(default_factory=list)
    skills: List[str] = field(default_factory=list)
    memory_backend: str = "json"
    sandbox_path: Optional[str] = None
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "AgentConfig":
        return cls(
            name=data.get("name", "unnamed"),
            description=data.get("description", ""),
            capabilities=data.get("capabilities", []),
            skills=data.get("skills", []),
            memory_backend=data.get("memory_backend", "json"),
            sandbox_path=data.get("sandbox_path")
        )


class AgentDaemon:
    """
    Agent Supervisor Daemon.
    
    Responsibilities:
    - Load agent definitions from /usr/share/agents/models/*.yml
    - Enforce capabilities and permissions
    - Manage sandboxes per agent
    - Provide internal API for running tasks
    - Log all actions
    - Never perform network calls outside localhost
    """
    
    def __init__(self, agents_root: Optional[Path] = None):
        # Set up paths
        if agents_root:
            self.agents_root = Path(agents_root)
        else:
            # Default Termux-Kotlin path
            termux_prefix = Path("/data/data/com.termux.kotlin/files/usr")
            if termux_prefix.exists():
                self.agents_root = termux_prefix / "share" / "agents"
            else:
                # Development fallback
                self.agents_root = Path(os.environ.get(
                    "AGENTS_ROOT",
                    "/tmp/termux-agents"
                ))
        
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
        
        logger.info(f"AgentDaemon initialized at {self.agents_root}")
        logger.info(f"Loaded {len(self._agents)} agents")
    
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
    
    def _get_log_file(self, agent_name: str) -> Path:
        """Get log file path for agent."""
        return self.logs_dir / f"{agent_name}.log"
    
    def _log_action(
        self,
        agent_name: str,
        action: str,
        status: str,
        details: Optional[Dict[str, Any]] = None
    ) -> None:
        """Log an action to the agent's log file."""
        log_file = self._get_log_file(agent_name)
        
        entry = {
            "timestamp": datetime.now().isoformat(),
            "agent": agent_name,
            "action": action,
            "status": status,
            "details": details or {}
        }
        
        with open(log_file, "a") as f:
            f.write(json.dumps(entry) + "\n")
    
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
    
    def validate_capabilities(
        self,
        agent_name: str,
        required: List[str]
    ) -> tuple[bool, List[str]]:
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
    
    def run_task(
        self,
        agent_name: str,
        task: str,
        args: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Run a task through the specified agent.
        
        Args:
            agent_name: Name of agent to use
            task: Task description or skill.function call
            args: Additional arguments
        
        Returns:
            Task result dict
        """
        agent = self._agents.get(agent_name)
        if not agent:
            return {
                "success": False,
                "error": f"Agent not found: {agent_name}"
            }
        
        self._log_action(agent_name, "run_task", "started", {"task": task})
        
        try:
            # Import runtime components
            from agents.core.runtime.memory import AgentMemory
            from agents.core.runtime.sandbox import AgentSandbox
            from agents.core.runtime.executor import AgentExecutor
            from agents.skills.loader import SkillLoader
            
            # Set up agent context
            memory = AgentMemory(agent_name, self.memory_dir)
            sandbox = AgentSandbox(agent_name, self.sandboxes_dir)
            
            def log_callback(entry):
                self._log_action(
                    agent_name,
                    entry.get("message", "action"),
                    entry.get("level", "INFO"),
                    entry
                )
            
            executor = AgentExecutor(
                agent_name=agent_name,
                capabilities=agent.capabilities,
                sandbox_path=sandbox.sandbox_root,
                log_callback=log_callback
            )
            
            # Parse task
            result = self._execute_task(
                agent, task, args or {},
                executor, sandbox, memory
            )
            
            # Log completion
            self._log_action(
                agent_name, "run_task", "completed",
                {"task": task, "success": result.get("success", False)}
            )
            
            # Save to history
            memory.append_history({
                "task": task,
                "args": args,
                "result": result
            })
            
            return result
            
        except Exception as e:
            logger.exception(f"Task failed: {e}")
            self._log_action(agent_name, "run_task", "failed", {"error": str(e)})
            return {
                "success": False,
                "error": str(e)
            }
    
    def _execute_task(
        self,
        agent: AgentConfig,
        task: str,
        args: Dict[str, Any],
        executor,
        sandbox,
        memory
    ) -> Dict[str, Any]:
        """Execute a task for an agent."""
        from agents.skills.loader import SkillLoader
        
        # Check if task is a skill.function call
        if "." in task and not " " in task:
            parts = task.split(".", 1)
            skill_name = parts[0]
            func_name = parts[1] if len(parts) > 1 else None
            
            # Check if agent has this skill
            if skill_name not in agent.skills:
                return {
                    "success": False,
                    "error": f"Agent '{agent.name}' doesn't have skill: {skill_name}"
                }
            
            # Load and run skill
            loader = SkillLoader(self.skills_dir)
            skill = loader.create_skill(skill_name, executor, sandbox, memory)
            
            if skill is None:
                return {
                    "success": False,
                    "error": f"Skill not found: {skill_name}"
                }
            
            if func_name:
                result = skill.call(func_name, **args)
                return result.to_dict()
            else:
                return {
                    "success": True,
                    "data": skill.get_manifest()
                }
        
        # Natural language task - requires task interpreter
        # For now, return info about what could be done
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
        """Validate all agents and skills."""
        results = {
            "agents": {},
            "skills": {},
            "errors": []
        }
        
        # Validate agents
        for name, agent in self._agents.items():
            agent_result = {
                "valid": True,
                "issues": []
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
            
            agent_result["valid"] = len(agent_result["issues"]) == 0
            results["agents"][name] = agent_result
        
        # Validate skills
        from agents.skills.loader import SkillLoader
        loader = SkillLoader(self.skills_dir)
        
        for skill_name in loader.discover_skills():
            skill_result = {
                "valid": True,
                "issues": []
            }
            
            manifest = loader.load_manifest(skill_name)
            if not manifest:
                skill_result["issues"].append("No manifest file")
            
            skill_class = loader.load_skill_class(skill_name)
            if not skill_class:
                skill_result["issues"].append("Failed to load skill class")
            
            skill_result["valid"] = len(skill_result["issues"]) == 0
            results["skills"][skill_name] = skill_result
        
        return results
    
    def _get_all_capability_names(self) -> set:
        """Get all known capability names."""
        caps = set()
        
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


if __name__ == "__main__":
    # Simple CLI for testing
    import argparse
    
    parser = argparse.ArgumentParser(description="Agent Daemon")
    parser.add_argument("command", choices=["list", "info", "run", "validate"])
    parser.add_argument("--agent", "-a", help="Agent name")
    parser.add_argument("--task", "-t", help="Task to run")
    parser.add_argument("--root", "-r", help="Agents root directory")
    
    args = parser.parse_args()
    
    daemon = AgentDaemon(Path(args.root) if args.root else None)
    
    if args.command == "list":
        for agent in daemon.list_agents():
            print(f"{agent['name']}: {agent['description']}")
    
    elif args.command == "info":
        if not args.agent:
            print("Error: --agent required")
            sys.exit(1)
        info = daemon.get_agent_info(args.agent)
        print(json.dumps(info, indent=2))
    
    elif args.command == "run":
        if not args.agent or not args.task:
            print("Error: --agent and --task required")
            sys.exit(1)
        result = daemon.run_task(args.agent, args.task)
        print(json.dumps(result, indent=2))
    
    elif args.command == "validate":
        result = daemon.validate_all()
        print(json.dumps(result, indent=2))
