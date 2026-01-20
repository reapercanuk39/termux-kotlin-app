"""
Learner Skill
=============
Skill wrapper for SkillLearner operations.
"""

import sys
import os
from pathlib import Path
from typing import Dict, Any, List, Optional

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')
AGENTS_ROOT = Path(PREFIX) / 'share' / 'agents'
if str(AGENTS_ROOT) not in sys.path:
    sys.path.insert(0, str(AGENTS_ROOT))

try:
    from core.learner import get_learner, SkillLearner
except ImportError:
    SkillLearner = None
    def get_learner():
        return None


def create_skill(
    name: str,
    template: str = "checker",
    description: str = "",
    additional_provides: List[str] = None,
    additional_capabilities: List[str] = None
) -> Dict[str, Any]:
    """
    Create a new skill from template.
    
    Args:
        name: Skill name
        template: Template to use (checker, manager, analyzer, fixer)
        description: Skill description
        additional_provides: Extra functions
        additional_capabilities: Extra capabilities
        
    Returns:
        Creation result
    """
    learner = get_learner()
    if learner is None:
        return {"error": "Learner not available"}
    
    return learner.create_skill(
        name=name,
        template=template,
        description=description,
        additional_provides=additional_provides,
        additional_capabilities=additional_capabilities
    )


def learn_from_execution(
    task_type: str,
    steps: List[str],
    success: bool
) -> Dict[str, Any]:
    """
    Learn from a task execution.
    
    Records the pattern for future use.
    
    Args:
        task_type: Type of task executed
        steps: Steps taken
        success: Whether it succeeded
        
    Returns:
        Learning result with pattern stats
    """
    learner = get_learner()
    if learner is None:
        return {"error": "Learner not available"}
    
    return learner.learn_from_execution(task_type, steps, success)


def get_best_pattern(task_type: str) -> Optional[Dict[str, Any]]:
    """
    Get the best pattern for a task type.
    
    Returns the most successful pattern based on history.
    
    Args:
        task_type: Type of task
        
    Returns:
        Best pattern or None
    """
    learner = get_learner()
    if learner is None:
        return None
    
    return learner.get_best_pattern(task_type)


def suggest_skill(task_description: str) -> Dict[str, Any]:
    """
    Suggest what skill to create for a task.
    
    Args:
        task_description: Description of what the skill should do
        
    Returns:
        Suggestion with template and provides
    """
    learner = get_learner()
    if learner is None:
        return {"error": "Learner not available"}
    
    return learner.suggest_skill(task_description)


def list_templates() -> List[Dict[str, Any]]:
    """
    List available skill templates.
    
    Returns:
        List of templates
    """
    learner = get_learner()
    if learner is None:
        return []
    
    return learner.list_templates()


def list_patterns(min_success_rate: float = 0.0) -> List[Dict[str, Any]]:
    """
    List learned patterns.
    
    Args:
        min_success_rate: Minimum success rate to include
        
    Returns:
        List of patterns
    """
    learner = get_learner()
    if learner is None:
        return []
    
    return learner.list_patterns(min_success_rate)


def export_patterns() -> Dict[str, Any]:
    """
    Export all learned patterns.
    
    For sharing with other agents.
    
    Returns:
        Export data
    """
    learner = get_learner()
    if learner is None:
        return {"error": "Learner not available"}
    
    return learner.export_patterns()


def import_patterns(patterns: List[Dict]) -> Dict[str, Any]:
    """
    Import patterns from another agent.
    
    Args:
        patterns: List of pattern dicts
        
    Returns:
        Import result
    """
    learner = get_learner()
    if learner is None:
        return {"error": "Learner not available"}
    
    return learner.import_patterns(patterns)
