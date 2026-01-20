"""
Agent Factory
=============

Dynamic agent creation and management system.
Allows agentd to create new agents at runtime based on task requirements.

Features:
- Create agents from templates
- Clone existing agents with modifications  
- Generate agent definitions from task descriptions
- Validate new agents against capability rules
- Register new agents with agentd
"""

import os
import json
import logging
from pathlib import Path
from datetime import datetime
from typing import Any, Dict, List, Optional
from dataclasses import dataclass, field

try:
    import yaml
    HAS_YAML = True
except ImportError:
    HAS_YAML = False

logger = logging.getLogger("agentd.factory")

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux.kotlin/files/usr')
MODELS_DIR = Path(PREFIX) / 'share' / 'agents' / 'models'
SKILLS_DIR = Path(PREFIX) / 'share' / 'agents' / 'skills'


@dataclass
class AgentBlueprint:
    """Blueprint for creating a new agent."""
    name: str
    description: str
    purpose: str
    skills: List[str]
    capabilities: List[str]
    tasks: List[Dict[str, str]]
    memory_limit: str = "1MB"
    sandbox_safe: bool = True
    created_from: Optional[str] = None
    created_at: Optional[str] = None
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "version": "1.0.0",
            "purpose": self.purpose,
            "skills": self.skills,
            "capabilities": self.capabilities,
            "tasks": self.tasks,
            "constraints": {
                "memory_limit": self.memory_limit,
                "sandbox_safe": self.sandbox_safe,
                "offline_only": True
            },
            "metadata": {
                "created_from": self.created_from,
                "created_at": self.created_at or datetime.now().isoformat(),
                "auto_generated": True
            }
        }
    
    def to_yaml(self) -> str:
        if not HAS_YAML:
            raise RuntimeError("pyyaml required")
        return yaml.dump(self.to_dict(), default_flow_style=False, sort_keys=False)


class AgentFactory:
    """Factory for creating new agents dynamically."""
    
    VALID_CAPABILITIES = {
        "filesystem.read", "filesystem.write", "filesystem.exec", "filesystem.delete",
        "network.none", "network.local",
        "exec.pkg", "exec.git", "exec.shell", "exec.python", "exec.build",
        "memory.read", "memory.write", "memory.shared",
        "system.info", "system.process", "system.env",
    }
    
    AGENT_TEMPLATES = {
        "troubleshooter": {
            "description": "Troubleshooting agent for system issues",
            "skills": ["diagnostic", "log", "heal"],
            "capabilities": ["filesystem.read", "exec.shell", "system.info"],
            "tasks": [
                {"name": "diagnose", "description": "Run diagnostics"},
                {"name": "analyze", "description": "Analyze issues"},
                {"name": "fix", "description": "Apply fixes"}
            ]
        },
        "builder": {
            "description": "Build and compilation agent",
            "skills": ["pkg", "git", "fs"],
            "capabilities": ["filesystem.read", "filesystem.write", "exec.build", "exec.pkg"],
            "tasks": [
                {"name": "build", "description": "Build project"},
                {"name": "test", "description": "Run tests"},
                {"name": "package", "description": "Create package"}
            ]
        },
        "monitor": {
            "description": "System monitoring agent",
            "skills": ["diagnostic", "log", "env"],
            "capabilities": ["filesystem.read", "system.info", "system.process"],
            "tasks": [
                {"name": "watch", "description": "Monitor system"},
                {"name": "alert", "description": "Generate alerts"},
                {"name": "report", "description": "Create reports"}
            ]
        },
        "installer": {
            "description": "Package installation agent",
            "skills": ["pkg", "compat", "permission"],
            "capabilities": ["filesystem.read", "filesystem.write", "exec.pkg", "network.local"],
            "tasks": [
                {"name": "install", "description": "Install packages"},
                {"name": "configure", "description": "Configure packages"},
                {"name": "verify", "description": "Verify installation"}
            ]
        },
        "custom": {
            "description": "Custom agent",
            "skills": [],
            "capabilities": ["filesystem.read"],
            "tasks": []
        }
    }
    
    def __init__(self, models_dir: Path = None, skills_dir: Path = None):
        self.models_dir = models_dir or MODELS_DIR
        self.skills_dir = skills_dir or SKILLS_DIR
        self.created_agents: List[str] = []
        
    def create_agent(
        self,
        name: str,
        template: str = "custom",
        purpose: str = "",
        additional_skills: List[str] = None,
        additional_capabilities: List[str] = None,
        additional_tasks: List[Dict[str, str]] = None
    ) -> Dict[str, Any]:
        """Create a new agent from template."""
        if not name.endswith("_agent"):
            name = f"{name}_agent"
        
        agent_file = self.models_dir / f"{name}.yml"
        if agent_file.exists():
            return {"success": False, "error": f"Agent exists: {name}"}
        
        base = self.AGENT_TEMPLATES.get(template, self.AGENT_TEMPLATES["custom"])
        
        skills = list(base["skills"])
        if additional_skills:
            for skill in additional_skills:
                if skill not in skills:
                    skills.append(skill)
        
        missing = self._check_skills_exist(skills)
        if missing:
            return {"success": False, "error": f"Skills not found: {missing}"}
        
        capabilities = list(base["capabilities"])
        if additional_capabilities:
            for cap in additional_capabilities:
                if cap in self.VALID_CAPABILITIES and cap not in capabilities:
                    capabilities.append(cap)
        
        tasks = list(base["tasks"])
        if additional_tasks:
            tasks.extend(additional_tasks)
        
        blueprint = AgentBlueprint(
            name=name,
            description=purpose or base["description"],
            purpose=purpose or base["description"],
            skills=skills,
            capabilities=capabilities,
            tasks=tasks,
            created_from=template
        )
        
        try:
            self.models_dir.mkdir(parents=True, exist_ok=True)
            with open(agent_file, 'w') as f:
                f.write(blueprint.to_yaml())
            
            self.created_agents.append(name)
            logger.info(f"Created agent: {name}")
            
            return {
                "success": True,
                "agent": name,
                "path": str(agent_file),
                "template": template,
                "skills": skills,
                "capabilities": capabilities
            }
        except Exception as e:
            return {"success": False, "error": str(e)}
    
    def clone_agent(self, source: str, new_name: str, mods: Dict = None) -> Dict[str, Any]:
        """Clone an existing agent with modifications."""
        source_file = self.models_dir / f"{source}.yml"
        if not source_file.exists():
            return {"success": False, "error": f"Source not found: {source}"}
        
        if not HAS_YAML:
            return {"success": False, "error": "pyyaml required"}
        
        try:
            with open(source_file) as f:
                agent_def = yaml.safe_load(f)
            
            if mods:
                for key, value in mods.items():
                    if isinstance(value, list) and key in agent_def:
                        agent_def[key] = list(set(agent_def.get(key, []) + value))
                    else:
                        agent_def[key] = value
            
            agent_def["name"] = new_name
            agent_def.setdefault("metadata", {})["cloned_from"] = source
            agent_def["metadata"]["cloned_at"] = datetime.now().isoformat()
            
            new_file = self.models_dir / f"{new_name}.yml"
            with open(new_file, 'w') as f:
                yaml.dump(agent_def, f, default_flow_style=False)
            
            self.created_agents.append(new_name)
            return {"success": True, "agent": new_name, "path": str(new_file)}
        except Exception as e:
            return {"success": False, "error": str(e)}
    
    def create_from_task(self, task_description: str) -> Dict[str, Any]:
        """Create agent based on task requirements."""
        task_lower = task_description.lower()
        
        template = "custom"
        skills = []
        
        if any(w in task_lower for w in ["fix", "repair", "heal", "diagnose"]):
            template = "troubleshooter"
            skills = ["heal", "diagnostic"]
        elif any(w in task_lower for w in ["build", "compile", "make", "test"]):
            template = "builder"
            skills = ["pkg", "git"]
        elif any(w in task_lower for w in ["monitor", "watch", "alert"]):
            template = "monitor"
            skills = ["diagnostic", "log"]
        elif any(w in task_lower for w in ["install", "package", "setup"]):
            template = "installer"
            skills = ["pkg", "compat"]
        
        if "log" in task_lower: skills.append("log")
        if "path" in task_lower: skills.append("path")
        if "permission" in task_lower: skills.append("permission")
        if "config" in task_lower: skills.append("config")
        if "env" in task_lower: skills.append("env")
        if "shim" in task_lower: skills.append("shim")
        
        base_name = task_lower.split()[0] if task_lower else "auto"
        name = f"{base_name}_{datetime.now().strftime('%H%M%S')}_agent"
        
        return self.create_agent(name=name, template=template, purpose=task_description, additional_skills=skills)
    
    def delete_agent(self, name: str, force: bool = False) -> Dict[str, Any]:
        """Delete an auto-generated agent."""
        agent_file = self.models_dir / f"{name}.yml"
        if not agent_file.exists():
            return {"success": False, "error": f"Not found: {name}"}
        
        if not force and HAS_YAML:
            try:
                with open(agent_file) as f:
                    agent_def = yaml.safe_load(f)
                    if not agent_def.get("metadata", {}).get("auto_generated"):
                        return {"success": False, "error": "Cannot delete non-auto-generated agent"}
            except:
                pass
        
        try:
            agent_file.unlink()
            if name in self.created_agents:
                self.created_agents.remove(name)
            return {"success": True, "deleted": name}
        except Exception as e:
            return {"success": False, "error": str(e)}
    
    def list_templates(self) -> List[Dict[str, Any]]:
        """List available templates."""
        return [{"name": n, "description": t["description"], "skills": t["skills"]} 
                for n, t in self.AGENT_TEMPLATES.items()]
    
    def _check_skills_exist(self, skills: List[str]) -> List[str]:
        """Check which skills don't exist."""
        return [s for s in skills if not (self.skills_dir / s / "skill.py").exists()]
    
    def _list_available_skills(self) -> List[str]:
        """List available skills."""
        if not self.skills_dir.exists():
            return []
        return [d.name for d in self.skills_dir.iterdir() if d.is_dir() and (d / "skill.py").exists()]


_factory: Optional[AgentFactory] = None

def get_factory() -> AgentFactory:
    global _factory
    if _factory is None:
        _factory = AgentFactory()
    return _factory

def create_agent(**kwargs) -> Dict[str, Any]:
    return get_factory().create_agent(**kwargs)

def create_from_task(task: str) -> Dict[str, Any]:
    return get_factory().create_from_task(task)
