"""
Skill Base Class
================

All skills inherit from this base class.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
from pathlib import Path
from datetime import datetime


@dataclass
class SkillResult:
    """Result from a skill function call."""
    success: bool
    data: Any = None
    error: Optional[str] = None
    logs: List[str] = field(default_factory=list)
    duration_ms: int = 0
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "success": self.success,
            "data": self.data,
            "error": self.error,
            "logs": self.logs,
            "duration_ms": self.duration_ms
        }


class Skill(ABC):
    """
    Base class for all agent skills.
    
    Skills are modular capabilities that provide specific functions.
    Each skill declares:
        - name: Unique identifier
        - description: What the skill does
        - provides: List of function names it provides
        - requires_capabilities: Capabilities needed to use this skill
    """
    
    # Subclasses must define these
    name: str = "base"
    description: str = "Base skill"
    provides: List[str] = []
    requires_capabilities: List[str] = []
    
    def __init__(self, executor, sandbox, memory):
        """
        Initialize skill with agent context.
        
        Args:
            executor: AgentExecutor for running commands
            sandbox: AgentSandbox for file operations
            memory: AgentMemory for persistent storage
        """
        self.executor = executor
        self.sandbox = sandbox
        self.memory = memory
        self._logs: List[str] = []
    
    def log(self, message: str) -> None:
        """Log a message."""
        timestamp = datetime.now().strftime("%H:%M:%S")
        entry = f"[{timestamp}] [{self.name}] {message}"
        self._logs.append(entry)
    
    def clear_logs(self) -> None:
        """Clear log buffer."""
        self._logs = []
    
    def get_logs(self) -> List[str]:
        """Get log buffer."""
        return self._logs.copy()
    
    @abstractmethod
    def get_functions(self) -> Dict[str, callable]:
        """
        Return dictionary of callable functions this skill provides.
        
        Returns:
            Dict mapping function name to callable
        """
        pass
    
    def call(self, function_name: str, **kwargs) -> SkillResult:
        """
        Call a skill function by name.
        
        Args:
            function_name: Name of function to call
            **kwargs: Arguments to pass to function
        
        Returns:
            SkillResult with success/failure and data
        """
        functions = self.get_functions()
        
        if function_name not in functions:
            return SkillResult(
                success=False,
                error=f"Unknown function: {function_name}. Available: {list(functions.keys())}"
            )
        
        self.clear_logs()
        start_time = datetime.now()
        
        try:
            result = functions[function_name](**kwargs)
            duration = int((datetime.now() - start_time).total_seconds() * 1000)
            
            return SkillResult(
                success=True,
                data=result,
                logs=self.get_logs(),
                duration_ms=duration
            )
        except Exception as e:
            duration = int((datetime.now() - start_time).total_seconds() * 1000)
            self.log(f"ERROR: {str(e)}")
            
            return SkillResult(
                success=False,
                error=str(e),
                logs=self.get_logs(),
                duration_ms=duration
            )
    
    def get_manifest(self) -> Dict[str, Any]:
        """Get skill manifest for documentation."""
        return {
            "name": self.name,
            "description": self.description,
            "provides": self.provides,
            "requires_capabilities": self.requires_capabilities,
            "functions": list(self.get_functions().keys())
        }
    
    def self_test(self) -> SkillResult:
        """
        Run self-test to verify skill is working.
        Override in subclasses for skill-specific tests.
        """
        self.log("Running base self-test")
        return SkillResult(
            success=True,
            data={"message": "Skill loaded successfully"},
            logs=self.get_logs()
        )
