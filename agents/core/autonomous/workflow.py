"""
Workflow Builder
================

Fluent API for building multi-agent workflows.
"""

from pathlib import Path
from typing import Any, Dict, List, Optional
from dataclasses import dataclass, field


@dataclass
class WorkflowStep:
    """A step in the workflow."""
    agent: str
    task: str
    depends_on: List[str] = field(default_factory=list)
    on_failure: str = "stop"  # stop, skip, retry


class WorkflowBuilder:
    """
    Fluent API for building workflows.
    
    Example:
        workflow = (WorkflowBuilder()
            .add("build_agent", "install vim")
            .then("system_agent", "check /usr/bin/vim")
            .then("backup_agent", "create backup")
            .on_failure("retry")
            .build())
    """
    
    def __init__(self):
        self.steps: List[WorkflowStep] = []
        self._current_failure_mode = "stop"
    
    def add(self, agent: str, task: str) -> "WorkflowBuilder":
        """Add a step with no dependencies."""
        self.steps.append(WorkflowStep(
            agent=agent,
            task=task,
            on_failure=self._current_failure_mode
        ))
        return self
    
    def then(self, agent: str, task: str) -> "WorkflowBuilder":
        """Add a step that depends on the previous step."""
        deps = [f"step_{len(self.steps) - 1}"] if self.steps else []
        self.steps.append(WorkflowStep(
            agent=agent,
            task=task,
            depends_on=deps,
            on_failure=self._current_failure_mode
        ))
        return self
    
    def parallel(self, steps: List[tuple]) -> "WorkflowBuilder":
        """Add multiple steps that run in parallel."""
        for agent, task in steps:
            self.steps.append(WorkflowStep(
                agent=agent,
                task=task,
                on_failure=self._current_failure_mode
            ))
        return self
    
    def on_failure(self, mode: str) -> "WorkflowBuilder":
        """Set failure mode for subsequent steps."""
        self._current_failure_mode = mode
        return self
    
    def build(self) -> List[Dict[str, Any]]:
        """Build the workflow definition."""
        return [
            {
                "id": f"step_{i}",
                "agent": step.agent,
                "task": step.task,
                "depends_on": step.depends_on,
                "on_failure": step.on_failure
            }
            for i, step in enumerate(self.steps)
        ]
    
    def execute(self, agents_root: Path = None) -> Dict[str, Any]:
        """Build and execute the workflow."""
        from agents.core.autonomous.autonomous import AutonomousAgent
        
        if agents_root is None:
            agents_root = Path("agents")
        
        # Use first agent to orchestrate
        if not self.steps:
            return {"status": "error", "error": "empty_workflow"}
        
        workflow = [{"agent": s.agent, "task": s.task} for s in self.steps]
        agent = AutonomousAgent(self.steps[0].agent, agents_root)
        return agent.orchestrate(workflow)


# Preset workflows
class Workflows:
    """Common workflow patterns."""
    
    @staticmethod
    def full_package_install(package: str) -> WorkflowBuilder:
        """Install package, verify, backup."""
        return (WorkflowBuilder()
            .add("build_agent", f"install {package}")
            .then("system_agent", f"check /usr/bin/{package}")
            .then("backup_agent", "create backup")
        )
    
    @staticmethod
    def security_audit() -> WorkflowBuilder:
        """Full security audit workflow."""
        return (WorkflowBuilder()
            .add("security_agent", "scan permissions")
            .then("security_agent", "check secrets")
            .then("backup_agent", "create snapshot")
        )
    
    @staticmethod
    def system_maintenance() -> WorkflowBuilder:
        """System maintenance workflow."""
        return (WorkflowBuilder()
            .add("build_agent", "update packages")
            .then("system_agent", "check disk")
            .then("backup_agent", "create backup")
            .then("security_agent", "scan")
        )
