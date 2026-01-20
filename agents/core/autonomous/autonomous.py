"""
Autonomous Agent Runtime
=========================

High-level autonomous agent behavior with task planning,
skill selection, workflow orchestration, and self-healing.
"""

import json
import logging
import traceback
from pathlib import Path
from typing import Any, Dict, List, Optional
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum

logger = logging.getLogger("agentd.autonomous")


class StepStatus(Enum):
    PENDING = "pending"
    RUNNING = "running"
    SUCCESS = "success"
    FAILED = "failed"
    HEALED = "healed"
    SKIPPED = "skipped"


@dataclass
class ExecutionStep:
    """A single step in the execution plan."""
    id: int
    description: str
    skill: Optional[str] = None
    function: Optional[str] = None
    args: Dict[str, Any] = field(default_factory=dict)
    requires: List[str] = field(default_factory=list)
    status: StepStatus = StepStatus.PENDING
    result: Optional[Any] = None
    error: Optional[str] = None
    retries: int = 0
    max_retries: int = 2


@dataclass
class ExecutionPlan:
    """Complete execution plan for a task."""
    task: str
    goal: str
    steps: List[ExecutionStep] = field(default_factory=list)
    created_at: str = field(default_factory=lambda: datetime.now().isoformat())
    status: str = "pending"
    current_step: int = 0


class TaskParser:
    """Parse natural language tasks into structured goals."""
    
    PATTERNS = {
        "install": ("pkg", "install_package"),
        "remove": ("pkg", "remove_package"),
        "update": ("pkg", "update_packages"),
        "backup": ("backup", "create_backup"),
        "restore": ("backup", "restore_backup"),
        "scan": ("security", "audit_permissions"),
        "check": ("fs", "check_path"),
        "list": ("fs", "list_directory"),
        "build": ("pkg", "build_package"),
        "analyze": ("apk", "analyze"),
        "hash": ("security", "hash_file"),
        "compress": ("backup", "create_snapshot"),
        "port": ("network", "check_ports"),
        "service": ("network", "check_services"),
    }
    
    def parse(self, task: str) -> Dict[str, Any]:
        """Parse task into structured format."""
        task_lower = task.lower()
        
        for keyword, (skill, function) in self.PATTERNS.items():
            if keyword in task_lower:
                return {
                    "raw": task,
                    "keyword": keyword,
                    "skill": skill,
                    "function": function,
                    "args": self._extract_args(task, keyword)
                }
        
        return {
            "raw": task,
            "keyword": "execute",
            "skill": "fs",
            "function": "self_test",
            "args": {}
        }
    
    def _extract_args(self, task: str, keyword: str) -> Dict[str, Any]:
        parts = task.split()
        try:
            idx = next(i for i, p in enumerate(parts) if keyword in p.lower())
            args = parts[idx + 1:] if idx + 1 < len(parts) else []
            return {"targets": args}
        except StopIteration:
            return {}


class StepPlanner:
    """Plan execution steps for a task."""
    
    def __init__(self, agent_skills: List[str], agent_capabilities: List[str]):
        self.skills = agent_skills
        self.capabilities = agent_capabilities
    
    def plan(self, parsed_task: Dict[str, Any]) -> ExecutionPlan:
        plan = ExecutionPlan(
            task=parsed_task["raw"],
            goal=f"Execute {parsed_task['keyword']} operation"
        )
        
        plan.steps.append(ExecutionStep(
            id=1,
            description="Validate environment",
            skill="fs",
            function="self_test",
            requires=["filesystem.read"]
        ))
        
        plan.steps.append(ExecutionStep(
            id=2,
            description=f"Execute: {parsed_task['keyword']}",
            skill=parsed_task["skill"],
            function=parsed_task["function"],
            args=parsed_task.get("args", {}),
            requires=self._infer_capabilities(parsed_task["skill"])
        ))
        
        plan.steps.append(ExecutionStep(
            id=3,
            description="Verify completion",
            skill="fs",
            function="self_test",
            requires=["filesystem.read"]
        ))
        
        return plan
    
    def _infer_capabilities(self, skill: str) -> List[str]:
        skill_caps = {
            "pkg": ["exec.pkg", "filesystem.write"],
            "git": ["exec.git", "filesystem.write"],
            "fs": ["filesystem.read", "filesystem.write"],
            "backup": ["exec.compress", "filesystem.write"],
            "security": ["exec.analyze", "filesystem.read"],
            "network": ["network.local"],
        }
        return skill_caps.get(skill, ["filesystem.read"])


class AutonomousExecutor:
    """Execute plans with self-healing and retry logic."""
    
    def __init__(self, agent_name: str, daemon, memory, sandbox):
        self.agent_name = agent_name
        self.daemon = daemon
        self.memory = memory
        self.sandbox = sandbox
        self.logs = []
    
    def log(self, message: str):
        entry = f"[{datetime.now().isoformat()}] {message}"
        self.logs.append(entry)
        logger.info(f"[{self.agent_name}] {message}")
    
    def execute_plan(self, plan: ExecutionPlan) -> Dict[str, Any]:
        self.log(f"Starting: {plan.goal}")
        plan.status = "running"
        results = []
        
        for step in plan.steps:
            plan.current_step = step.id
            step.status = StepStatus.RUNNING
            self.log(f"Step {step.id}: {step.description}")
            
            success = self._execute_step_with_retry(step)
            
            if success:
                step.status = StepStatus.SUCCESS
                results.append({"step": step.id, "status": "success"})
            else:
                step.status = StepStatus.FAILED
                results.append({"step": step.id, "status": "failed", "error": step.error})
                
                if self._attempt_healing(step):
                    step.status = StepStatus.HEALED
                    results[-1]["status"] = "healed"
                else:
                    plan.status = "failed"
                    break
        
        if plan.status != "failed":
            plan.status = "success"
        
        self._update_memory(plan)
        
        return {
            "status": plan.status,
            "task": plan.task,
            "steps_completed": sum(1 for s in plan.steps if s.status in [StepStatus.SUCCESS, StepStatus.HEALED]),
            "steps_total": len(plan.steps),
            "results": results,
            "logs": self.logs[-10:]
        }
    
    def _execute_step_with_retry(self, step: ExecutionStep) -> bool:
        while step.retries <= step.max_retries:
            try:
                if step.skill and step.function:
                    task_str = f"{step.skill}.{step.function}"
                    result = self.daemon.run_task(self.agent_name, task_str)
                    if result.get("status") == "success":
                        step.result = result.get("result")
                        return True
                    step.error = result.get("error", "Unknown error")
                else:
                    return True
            except Exception as e:
                step.error = str(e)
            
            step.retries += 1
            if step.retries <= step.max_retries:
                self.log(f"Retry {step.retries} for step {step.id}")
        
        return False
    
    def _attempt_healing(self, step: ExecutionStep) -> bool:
        self.log(f"Self-healing step {step.id}")
        try:
            from agents.self_healing.healer import SelfHealingMode
            healer = SelfHealingMode(self.daemon.agents_root)
            result = healer.heal()
            if result.get("healed"):
                self.log(f"Healed: {result['healed']}")
                return True
        except Exception as e:
            self.log(f"Healing failed: {e}")
        return False
    
    def _update_memory(self, plan: ExecutionPlan):
        try:
            self.memory.set("last_task", plan.task)
            self.memory.set("last_status", plan.status)
            self.memory.set("last_run", datetime.now().isoformat())
        except:
            pass


class AutonomousAgent:
    """High-level autonomous agent."""
    
    def __init__(self, agent_name: str, agents_root: Path):
        self.agent_name = agent_name
        self.agents_root = Path(agents_root)
        
        from agents.core.supervisor.agentd import AgentDaemon
        from agents.core.registry.skill_registry import SkillRegistry
        from agents.core.runtime.memory import AgentMemory
        from agents.core.runtime.sandbox import AgentSandbox
        
        self.daemon = AgentDaemon(agents_root)
        self.registry = SkillRegistry(agents_root / "skills")
        self.registry.discover()
        
        agent_info = self.daemon.get_agent_info(agent_name)
        if not agent_info:
            raise ValueError(f"Agent not found: {agent_name}")
        
        self.capabilities = agent_info.get("capabilities", [])
        self.skills = agent_info.get("skills", [])
        
        self.memory = AgentMemory(agent_name, agents_root / "memory")
        self.sandbox = AgentSandbox(agent_name, agents_root / "sandboxes")
        
        self.parser = TaskParser()
        self.planner = StepPlanner(self.skills, self.capabilities)
        self.executor = AutonomousExecutor(agent_name, self.daemon, self.memory, self.sandbox)
    
    def run(self, task: str) -> Dict[str, Any]:
        """Run task autonomously."""
        self.executor.log(f"Task: {task}")
        
        parsed = self.parser.parse(task)
        self.executor.log(f"Parsed: {parsed['skill']}.{parsed['function']}")
        
        plan = self.planner.plan(parsed)
        self.executor.log(f"Plan: {len(plan.steps)} steps")
        
        return self.executor.execute_plan(plan)
    
    def orchestrate(self, workflow: List[Dict[str, str]]) -> Dict[str, Any]:
        """Orchestrate multi-agent workflow."""
        from agents.orchestrator.graph_engine import GraphEngine
        
        engine = GraphEngine(self.agents_root)
        prev = None
        for i, item in enumerate(workflow):
            node_id = f"step_{i}"
            engine.add_node(node_id, item["agent"], item["task"], depends_on=[prev] if prev else [])
            prev = node_id
        
        return engine.execute()


def run_autonomous(agent_name: str, task: str, agents_root: Path = None) -> Dict[str, Any]:
    if agents_root is None:
        agents_root = Path("agents")
    return AutonomousAgent(agent_name, agents_root).run(task)


if __name__ == "__main__":
    import sys
    if len(sys.argv) < 3:
        print("Usage: autonomous.py <agent> <task>")
        sys.exit(1)
    result = run_autonomous(sys.argv[1], " ".join(sys.argv[2:]))
    print(json.dumps(result, indent=2))
