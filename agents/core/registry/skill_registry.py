"""
Skill Registry
===============

Auto-discovery and registration system for agent skills.
Scans agents/skills/*/skill.yml at startup and maintains a global registry.
"""

import os
import json
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Type
from dataclasses import dataclass, field
from datetime import datetime

# Try to import YAML
try:
    import yaml
    HAS_YAML = True
except ImportError:
    HAS_YAML = False

logger = logging.getLogger("agentd.registry")


# Known valid capabilities
VALID_CAPABILITIES = {
    # Filesystem
    "filesystem.read", "filesystem.write", "filesystem.exec", "filesystem.delete",
    # Network
    "network.none", "network.local", "network.external",
    # Execution
    "exec.pkg", "exec.git", "exec.qemu", "exec.iso", "exec.apk", "exec.docker",
    "exec.shell", "exec.python", "exec.build", "exec.analyze", "exec.compress",
    "exec.custom",
    # Memory
    "memory.read", "memory.write", "memory.shared",
    # System
    "system.info", "system.process", "system.env",
}


@dataclass
class SkillManifest:
    """Parsed skill manifest."""
    name: str
    description: str = ""
    version: str = "1.0.0"
    provides: List[str] = field(default_factory=list)
    requires_capabilities: List[str] = field(default_factory=list)
    sandbox_safe: bool = True
    path: Optional[Path] = None
    valid: bool = True
    validation_errors: List[str] = field(default_factory=list)
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any], path: Path = None) -> "SkillManifest":
        return cls(
            name=data.get("name", "unnamed"),
            description=data.get("description", ""),
            version=data.get("version", "1.0.0"),
            provides=data.get("provides", []),
            requires_capabilities=data.get("requires_capabilities", []),
            sandbox_safe=data.get("sandbox_safe", True),
            path=path
        )
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "version": self.version,
            "provides": self.provides,
            "requires_capabilities": self.requires_capabilities,
            "sandbox_safe": self.sandbox_safe,
            "path": str(self.path) if self.path else None,
            "valid": self.valid,
            "validation_errors": self.validation_errors
        }


class SkillRegistry:
    """
    Global skill registry with auto-discovery.
    
    Features:
    - Scans agents/skills/*/skill.yml at startup
    - Validates manifests against schema
    - Validates capabilities against known set
    - Rejects invalid skills with detailed errors
    - Provides lookup API for agentd
    """
    
    def __init__(self, skills_dir: Path):
        self.skills_dir = Path(skills_dir)
        self._skills: Dict[str, SkillManifest] = {}
        self._discovery_errors: List[Dict[str, Any]] = []
        self._discovered_at: Optional[str] = None
    
    def discover(self) -> Dict[str, Any]:
        """
        Discover and register all skills.
        
        Scans agents/skills/*/skill.yml for valid skill manifests.
        
        Returns:
            Discovery report with stats and errors
        """
        self._skills = {}
        self._discovery_errors = []
        self._discovered_at = datetime.now().isoformat()
        
        if not self.skills_dir.exists():
            logger.warning(f"Skills directory not found: {self.skills_dir}")
            return self._get_discovery_report()
        
        # Scan for skill directories
        for item in self.skills_dir.iterdir():
            if not item.is_dir():
                continue
            if item.name.startswith("_") or item.name.startswith("."):
                continue
            
            # Look for manifest
            manifest_path = self._find_manifest(item)
            if manifest_path is None:
                self._discovery_errors.append({
                    "skill": item.name,
                    "error": "No manifest file found (skill.yml or skill.json)"
                })
                continue
            
            # Load and validate manifest
            manifest = self._load_manifest(manifest_path)
            if manifest is None:
                continue
            
            # Validate
            self._validate_manifest(manifest)
            
            # Register
            self._skills[manifest.name] = manifest
            logger.debug(f"Registered skill: {manifest.name} (valid={manifest.valid})")
        
        return self._get_discovery_report()
    
    def _find_manifest(self, skill_dir: Path) -> Optional[Path]:
        """Find manifest file in skill directory."""
        for name in ["skill.yml", "skill.yaml", "skill.json"]:
            path = skill_dir / name
            if path.exists():
                return path
        return None
    
    def _load_manifest(self, path: Path) -> Optional[SkillManifest]:
        """Load manifest from file."""
        try:
            if path.suffix in [".yml", ".yaml"]:
                if not HAS_YAML:
                    self._discovery_errors.append({
                        "skill": path.parent.name,
                        "error": "YAML support not available (pip install pyyaml)"
                    })
                    return None
                with open(path) as f:
                    data = yaml.safe_load(f)
            else:
                with open(path) as f:
                    data = json.load(f)
            
            return SkillManifest.from_dict(data, path)
            
        except Exception as e:
            self._discovery_errors.append({
                "skill": path.parent.name,
                "error": f"Failed to parse manifest: {e}"
            })
            return None
    
    def _validate_manifest(self, manifest: SkillManifest) -> None:
        """Validate manifest against schema and capability rules."""
        errors = []
        
        # Required fields
        if not manifest.name:
            errors.append("Missing required field: name")
        
        if not manifest.provides:
            errors.append("Missing required field: provides (list of functions)")
        
        # Validate capabilities
        for cap in manifest.requires_capabilities:
            if cap not in VALID_CAPABILITIES:
                errors.append(f"Unknown capability: {cap}")
        
        # Check for forbidden capabilities
        if "network.external" in manifest.requires_capabilities:
            errors.append("Skills cannot require network.external (offline mode)")
        
        # Check skill.py exists
        if manifest.path:
            skill_py = manifest.path.parent / "skill.py"
            if not skill_py.exists():
                errors.append("Missing skill.py implementation file")
        
        # Update manifest
        manifest.validation_errors = errors
        manifest.valid = len(errors) == 0
    
    def _get_discovery_report(self) -> Dict[str, Any]:
        """Generate discovery report."""
        valid_count = sum(1 for s in self._skills.values() if s.valid)
        invalid_count = sum(1 for s in self._skills.values() if not s.valid)
        
        return {
            "discovered_at": self._discovered_at,
            "skills_dir": str(self.skills_dir),
            "total_discovered": len(self._skills),
            "valid": valid_count,
            "invalid": invalid_count,
            "discovery_errors": self._discovery_errors,
            "skills": {
                name: {
                    "valid": s.valid,
                    "provides": s.provides,
                    "capabilities": s.requires_capabilities,
                    "errors": s.validation_errors if not s.valid else None
                }
                for name, s in self._skills.items()
            }
        }
    
    # =========================================================================
    # Registry API
    # =========================================================================
    
    def get_skill(self, name: str) -> Optional[SkillManifest]:
        """Get skill manifest by name."""
        return self._skills.get(name)
    
    def get_valid_skills(self) -> List[SkillManifest]:
        """Get all valid skills."""
        return [s for s in self._skills.values() if s.valid]
    
    def get_invalid_skills(self) -> List[SkillManifest]:
        """Get all invalid skills."""
        return [s for s in self._skills.values() if not s.valid]
    
    def list_skills(self) -> List[str]:
        """List all skill names."""
        return list(self._skills.keys())
    
    def list_valid_skills(self) -> List[str]:
        """List valid skill names."""
        return [s.name for s in self._skills.values() if s.valid]
    
    def has_skill(self, name: str) -> bool:
        """Check if skill exists."""
        return name in self._skills
    
    def is_valid(self, name: str) -> bool:
        """Check if skill is valid."""
        skill = self._skills.get(name)
        return skill.valid if skill else False
    
    def get_skill_capabilities(self, name: str) -> List[str]:
        """Get capabilities required by a skill."""
        skill = self._skills.get(name)
        return skill.requires_capabilities if skill else []
    
    def get_skill_functions(self, name: str) -> List[str]:
        """Get functions provided by a skill."""
        skill = self._skills.get(name)
        return skill.provides if skill else []
    
    def find_skills_by_capability(self, capability: str) -> List[str]:
        """Find skills that require a specific capability."""
        return [
            s.name for s in self._skills.values()
            if capability in s.requires_capabilities
        ]
    
    def find_skills_by_function(self, function_name: str) -> List[str]:
        """Find skills that provide a specific function."""
        return [
            s.name for s in self._skills.values()
            if function_name in s.provides
        ]
    
    def validate_agent_skills(
        self,
        agent_skills: List[str],
        agent_capabilities: List[str]
    ) -> Dict[str, Any]:
        """
        Validate that an agent can use its declared skills.
        
        Args:
            agent_skills: Skills the agent wants to use
            agent_capabilities: Capabilities the agent has
        
        Returns:
            Validation result with any issues
        """
        issues = []
        valid_skills = []
        
        for skill_name in agent_skills:
            skill = self._skills.get(skill_name)
            
            if skill is None:
                issues.append({
                    "skill": skill_name,
                    "issue": "skill_not_found"
                })
                continue
            
            if not skill.valid:
                issues.append({
                    "skill": skill_name,
                    "issue": "skill_invalid",
                    "errors": skill.validation_errors
                })
                continue
            
            # Check capabilities
            missing_caps = [
                cap for cap in skill.requires_capabilities
                if cap not in agent_capabilities
            ]
            
            if missing_caps:
                issues.append({
                    "skill": skill_name,
                    "issue": "missing_capabilities",
                    "missing": missing_caps
                })
            else:
                valid_skills.append(skill_name)
        
        return {
            "valid": len(issues) == 0,
            "valid_skills": valid_skills,
            "issues": issues
        }
    
    def get_stats(self) -> Dict[str, Any]:
        """Get registry statistics."""
        all_caps = set()
        all_functions = []
        
        for skill in self._skills.values():
            all_caps.update(skill.requires_capabilities)
            all_functions.extend(skill.provides)
        
        return {
            "total_skills": len(self._skills),
            "valid_skills": len(self.get_valid_skills()),
            "invalid_skills": len(self.get_invalid_skills()),
            "unique_capabilities_used": len(all_caps),
            "total_functions_provided": len(all_functions),
            "discovered_at": self._discovered_at
        }


# Singleton instance
_registry: Optional[SkillRegistry] = None


def get_registry(skills_dir: Optional[Path] = None) -> SkillRegistry:
    """Get or create the global skill registry."""
    global _registry
    
    if _registry is None:
        if skills_dir is None:
            # Default path
            # Use environment variables set by wrapper, or defaults
            if "AGENTS_ROOT" in os.environ:
                skills_dir = Path(os.environ["AGENTS_ROOT"]) / "skills"
            else:
                prefix = os.environ.get("PREFIX", "/data/data/com.termux/files/usr")
                skills_dir = Path(prefix) / "share" / "agents" / "skills"
        
        _registry = SkillRegistry(skills_dir)
        _registry.discover()
    
    return _registry


def discover_skills(skills_dir: Optional[Path] = None) -> Dict[str, Any]:
    """Discover all skills and return report."""
    registry = get_registry(skills_dir)
    return registry.discover()


def get_skill(name: str) -> Optional[SkillManifest]:
    """Get a skill by name."""
    return get_registry().get_skill(name)


def list_skills() -> List[str]:
    """List all skill names."""
    return get_registry().list_skills()


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="Skill Registry")
    parser.add_argument("command", choices=["discover", "list", "info", "stats"])
    parser.add_argument("--skill", "-s", help="Skill name")
    parser.add_argument("--dir", "-d", help="Skills directory")
    
    args = parser.parse_args()
    
    skills_dir = Path(args.dir) if args.dir else None
    registry = SkillRegistry(skills_dir or Path("agents/skills"))
    
    if args.command == "discover":
        report = registry.discover()
        print(json.dumps(report, indent=2))
    
    elif args.command == "list":
        registry.discover()
        for name in registry.list_skills():
            skill = registry.get_skill(name)
            status = "✓" if skill.valid else "✗"
            print(f"{status} {name}: {skill.description[:50]}")
    
    elif args.command == "info":
        if not args.skill:
            print("Error: --skill required")
        else:
            registry.discover()
            skill = registry.get_skill(args.skill)
            if skill:
                print(json.dumps(skill.to_dict(), indent=2))
            else:
                print(f"Skill not found: {args.skill}")
    
    elif args.command == "stats":
        registry.discover()
        print(json.dumps(registry.get_stats(), indent=2))
