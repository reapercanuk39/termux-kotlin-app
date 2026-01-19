"""
Skill Loader
============

Dynamically loads and manages skills.
"""

import importlib
import importlib.util
import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Type

# Try to import YAML, fall back to JSON
try:
    import yaml
    HAS_YAML = True
except ImportError:
    HAS_YAML = False
    import json

from .base import Skill


class SkillLoader:
    """
    Loads and manages agent skills.
    
    Skills are discovered from the skills directory.
    Each skill must have:
        - skill.yml or skill.json: Manifest file
        - skill.py: Python module with Skill subclass
    """
    
    def __init__(self, skills_dir: Path):
        self.skills_dir = Path(skills_dir)
        self._skill_classes: Dict[str, Type[Skill]] = {}
        self._manifests: Dict[str, Dict[str, Any]] = {}
        self._loaded = False
    
    def discover_skills(self) -> List[str]:
        """Discover all available skills."""
        skills = []
        
        if not self.skills_dir.exists():
            return skills
        
        for item in self.skills_dir.iterdir():
            if item.is_dir() and not item.name.startswith("_"):
                skill_py = item / "skill.py"
                if skill_py.exists():
                    skills.append(item.name)
        
        return skills
    
    def load_manifest(self, skill_name: str) -> Optional[Dict[str, Any]]:
        """Load skill manifest from YAML or JSON."""
        skill_dir = self.skills_dir / skill_name
        
        # Try YAML first
        yml_path = skill_dir / "skill.yml"
        yaml_path = skill_dir / "skill.yaml"
        json_path = skill_dir / "skill.json"
        
        manifest = None
        
        if HAS_YAML:
            if yml_path.exists():
                with open(yml_path) as f:
                    manifest = yaml.safe_load(f)
            elif yaml_path.exists():
                with open(yaml_path) as f:
                    manifest = yaml.safe_load(f)
        
        if manifest is None and json_path.exists():
            with open(json_path) as f:
                manifest = json.load(f)
        
        if manifest:
            self._manifests[skill_name] = manifest
        
        return manifest
    
    def load_skill_class(self, skill_name: str) -> Optional[Type[Skill]]:
        """Load skill class from Python module."""
        skill_dir = self.skills_dir / skill_name
        skill_py = skill_dir / "skill.py"
        
        if not skill_py.exists():
            return None
        
        # Load module dynamically
        spec = importlib.util.spec_from_file_location(
            f"agents.skills.{skill_name}.skill",
            skill_py
        )
        
        if spec is None or spec.loader is None:
            return None
        
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        
        # Find Skill subclass in module
        for attr_name in dir(module):
            attr = getattr(module, attr_name)
            if (isinstance(attr, type) and 
                issubclass(attr, Skill) and 
                attr is not Skill):
                self._skill_classes[skill_name] = attr
                return attr
        
        return None
    
    def load_all(self) -> Dict[str, Type[Skill]]:
        """Load all available skills."""
        skills = self.discover_skills()
        
        for skill_name in skills:
            self.load_manifest(skill_name)
            self.load_skill_class(skill_name)
        
        self._loaded = True
        return self._skill_classes
    
    def get_skill_class(self, skill_name: str) -> Optional[Type[Skill]]:
        """Get skill class by name."""
        if not self._loaded:
            self.load_all()
        
        if skill_name not in self._skill_classes:
            self.load_skill_class(skill_name)
        
        return self._skill_classes.get(skill_name)
    
    def get_manifest(self, skill_name: str) -> Optional[Dict[str, Any]]:
        """Get skill manifest by name."""
        if skill_name not in self._manifests:
            self.load_manifest(skill_name)
        return self._manifests.get(skill_name)
    
    def list_skills(self) -> List[Dict[str, Any]]:
        """List all skills with their manifests."""
        if not self._loaded:
            self.load_all()
        
        result = []
        for skill_name in self.discover_skills():
            manifest = self.get_manifest(skill_name)
            if manifest:
                result.append({
                    "name": skill_name,
                    "manifest": manifest
                })
        
        return result
    
    def create_skill(
        self,
        skill_name: str,
        executor,
        sandbox,
        memory
    ) -> Optional[Skill]:
        """
        Create a skill instance.
        
        Args:
            skill_name: Name of skill to create
            executor: AgentExecutor instance
            sandbox: AgentSandbox instance
            memory: AgentMemory instance
        
        Returns:
            Skill instance or None if not found
        """
        skill_class = self.get_skill_class(skill_name)
        if skill_class is None:
            return None
        
        return skill_class(executor, sandbox, memory)


# Module-level convenience functions
_default_loader: Optional[SkillLoader] = None


def _get_loader(skills_dir: Optional[Path] = None) -> SkillLoader:
    """Get or create default skill loader."""
    global _default_loader
    
    if _default_loader is None:
        if skills_dir is None:
            # Default path
            from agents.core import AGENTS_ROOT
            skills_dir = AGENTS_ROOT / "skills"
        _default_loader = SkillLoader(skills_dir)
    
    return _default_loader


def get_skill(
    skill_name: str,
    executor,
    sandbox,
    memory,
    skills_dir: Optional[Path] = None
) -> Optional[Skill]:
    """Get a skill instance."""
    loader = _get_loader(skills_dir)
    return loader.create_skill(skill_name, executor, sandbox, memory)


def list_skills(skills_dir: Optional[Path] = None) -> List[Dict[str, Any]]:
    """List all available skills."""
    loader = _get_loader(skills_dir)
    return loader.list_skills()
