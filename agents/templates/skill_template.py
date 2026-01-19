"""
Skill Template
==============

Copy this file to agents/skills/your_skill/skill.py and customize.
"""

from typing import Any, Dict, List
from agents.skills.base import Skill, SkillResult


class YourSkillName(Skill):
    """
    Your skill description here.
    
    This skill provides functionality for...
    """
    
    # These must match skill.yml
    name = "your_skill_name"
    description = "A brief description of what this skill provides"
    provides = ["function_one", "function_two", "function_three"]
    requires_capabilities = ["filesystem.read", "exec.shell"]
    
    def get_functions(self) -> Dict[str, callable]:
        """Return dictionary mapping function names to methods."""
        return {
            "function_one": self.function_one,
            "function_two": self.function_two,
            "function_three": self.function_three
        }
    
    def function_one(self, param1: str, **kwargs) -> Dict[str, Any]:
        """
        Description of function_one.
        
        Args:
            param1: Description of parameter
            **kwargs: Additional parameters
        
        Returns:
            Result dictionary
        """
        self.log(f"Running function_one with param1={param1}")
        
        # Your implementation here
        # Use self.executor to run commands
        # Use self.sandbox for file operations
        # Use self.memory for persistent storage
        
        result = self.executor.run(
            ["echo", param1],
            check=False
        )
        
        return {
            "success": result.returncode == 0,
            "output": result.stdout
        }
    
    def function_two(self, **kwargs) -> Dict[str, Any]:
        """Description of function_two."""
        self.log("Running function_two")
        
        # Implementation here
        
        return {"message": "function_two completed"}
    
    def function_three(self, **kwargs) -> Dict[str, Any]:
        """Description of function_three."""
        self.log("Running function_three")
        
        # Implementation here
        
        return {"message": "function_three completed"}
    
    def self_test(self) -> SkillResult:
        """
        Run self-test to verify skill is working.
        
        This is called when running 'agent run <name> your_skill.self_test'
        """
        self.log("Running self-test")
        
        # Add your test logic here
        success = True
        
        return SkillResult(
            success=success,
            data={"message": "Self-test passed"},
            logs=self.get_logs()
        )
