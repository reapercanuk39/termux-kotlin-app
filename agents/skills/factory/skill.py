"""
Factory Skill
=============
Skill wrapper for AgentFactory operations.
"""

import sys
import os
from pathlib import Path
from typing import Dict, Any, List

# Add core to path for imports
PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')
AGENTS_ROOT = Path(PREFIX) / 'share' / 'agents'
if str(AGENTS_ROOT) not in sys.path:
    sys.path.insert(0, str(AGENTS_ROOT))

try:
    from core.factory import get_factory, AgentFactory
except ImportError:
    # Fallback for direct execution
    AgentFactory = None
    def get_factory():
        return None


def create_agent(
    name: str,
    template: str = "custom",
    purpose: str = "",
    skills: List[str] = None,
    capabilities: List[str] = None
) -> Dict[str, Any]:
    """
    Create a new agent from template.
    
    Args:
        name: Agent name
        template: Template to use (troubleshooter, builder, monitor, installer, custom)
        purpose: Purpose description
        skills: Additional skills to include
        capabilities: Additional capabilities to grant
        
    Returns:
        Creation result
    """
    factory = get_factory()
    if factory is None:
        return {"error": "Factory not available"}
    
    return factory.create_agent(
        name=name,
        template=template,
        purpose=purpose,
        additional_skills=skills,
        additional_capabilities=capabilities
    )


def clone_agent(
    source: str,
    new_name: str,
    modifications: Dict[str, Any] = None
) -> Dict[str, Any]:
    """
    Clone an existing agent with modifications.
    
    Args:
        source: Source agent name
        new_name: New agent name
        modifications: Fields to override
        
    Returns:
        Clone result
    """
    factory = get_factory()
    if factory is None:
        return {"error": "Factory not available"}
    
    return factory.clone_agent(source, new_name, modifications)


def create_from_task(task_description: str) -> Dict[str, Any]:
    """
    Create an agent based on task description.
    
    Analyzes the task and automatically selects appropriate template and skills.
    
    Args:
        task_description: Natural language description of what the agent should do
        
    Returns:
        Creation result with auto-generated agent
    """
    factory = get_factory()
    if factory is None:
        return {"error": "Factory not available"}
    
    return factory.create_from_task(task_description)


def delete_agent(name: str, force: bool = False) -> Dict[str, Any]:
    """
    Delete an auto-generated agent.
    
    Args:
        name: Agent name
        force: Force delete even non-auto-generated agents
        
    Returns:
        Deletion result
    """
    factory = get_factory()
    if factory is None:
        return {"error": "Factory not available"}
    
    return factory.delete_agent(name, force)


def list_templates() -> List[Dict[str, Any]]:
    """
    List available agent templates.
    
    Returns:
        List of templates with name, description, and default skills
    """
    factory = get_factory()
    if factory is None:
        return []
    
    return factory.list_templates()


def list_created() -> List[str]:
    """
    List agents created in this session.
    
    Returns:
        List of agent names
    """
    factory = get_factory()
    if factory is None:
        return []
    
    return factory.list_created_agents()
