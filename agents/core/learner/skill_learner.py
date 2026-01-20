"""
Skill Learner
=============

Skill acquisition and learning system for agents.
Enables agents to discover, download, and learn new skills.

Features:
- Discover available skills from local/network sources
- Download and install new skills
- Generate skill templates from examples
- Learn from task execution patterns
- Share learned skills between agents
"""

import os
import json
import logging
import shutil
from pathlib import Path
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple
from dataclasses import dataclass, field

try:
    import yaml
    HAS_YAML = True
except ImportError:
    HAS_YAML = False

logger = logging.getLogger("agentd.learner")

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')
SKILLS_DIR = Path(PREFIX) / 'share' / 'agents' / 'skills'
MEMORY_DIR = Path(PREFIX) / 'var' / 'agents' / 'memory'


@dataclass
class SkillDefinition:
    """Definition for a skill to be created."""
    name: str
    description: str
    provides: List[str]
    requires_capabilities: List[str]
    implementation: str  # Python code
    version: str = "1.0.0"
    
    def to_manifest(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "version": self.version,
            "provides": self.provides,
            "requires_capabilities": self.requires_capabilities
        }


@dataclass
class LearnedPattern:
    """A pattern learned from task execution."""
    pattern_id: str
    task_type: str
    steps: List[str]
    success_rate: float
    times_used: int = 0
    last_used: Optional[str] = None
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "pattern_id": self.pattern_id,
            "task_type": self.task_type,
            "steps": self.steps,
            "success_rate": self.success_rate,
            "times_used": self.times_used,
            "last_used": self.last_used
        }


class SkillLearner:
    """
    Skill acquisition and learning system.
    
    Enables agents to:
    - Create new skills from templates
    - Learn patterns from successful task executions
    - Share knowledge between agents
    """
    
    # Skill templates for common patterns
    SKILL_TEMPLATES = {
        "checker": {
            "description": "Check/verify something",
            "provides": ["check", "verify", "validate"],
            "capabilities": ["filesystem.read", "system.info"],
            "template": '''"""
{name} Skill - Checker Pattern
"""

import os
from pathlib import Path
from typing import Dict, Any

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')

def check() -> Dict[str, Any]:
    """Check status."""
    return {{"status": "ok", "message": "Check passed"}}

def verify(target: str = None) -> Dict[str, Any]:
    """Verify a target."""
    if target and Path(target).exists():
        return {{"verified": True, "target": target}}
    return {{"verified": False, "error": "Target not found"}}

def validate() -> Dict[str, Any]:
    """Validate configuration."""
    return {{"valid": True}}
'''
        },
        "manager": {
            "description": "Manage/control something",
            "provides": ["start", "stop", "status", "restart"],
            "capabilities": ["filesystem.read", "filesystem.write", "exec.shell"],
            "template": '''"""
{name} Skill - Manager Pattern
"""

import os
import subprocess
from typing import Dict, Any

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')

def start(target: str = None) -> Dict[str, Any]:
    """Start a service/process."""
    return {{"started": True, "target": target}}

def stop(target: str = None) -> Dict[str, Any]:
    """Stop a service/process."""
    return {{"stopped": True, "target": target}}

def status(target: str = None) -> Dict[str, Any]:
    """Get status."""
    return {{"running": False, "target": target}}

def restart(target: str = None) -> Dict[str, Any]:
    """Restart a service/process."""
    stop(target)
    return start(target)
'''
        },
        "analyzer": {
            "description": "Analyze/process data",
            "provides": ["analyze", "parse", "summarize", "report"],
            "capabilities": ["filesystem.read"],
            "template": '''"""
{name} Skill - Analyzer Pattern
"""

import os
from pathlib import Path
from typing import Dict, Any, List

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')

def analyze(data: str = None) -> Dict[str, Any]:
    """Analyze data."""
    return {{"analysis": "complete", "data": data}}

def parse(content: str = None) -> Dict[str, Any]:
    """Parse content."""
    return {{"parsed": True}}

def summarize(items: List = None) -> Dict[str, Any]:
    """Summarize items."""
    return {{"summary": f"{{len(items or [])}} items"}}

def report() -> Dict[str, Any]:
    """Generate report."""
    return {{"report": "generated"}}
'''
        },
        "fixer": {
            "description": "Fix/repair issues",
            "provides": ["diagnose", "fix", "heal", "verify_fix"],
            "capabilities": ["filesystem.read", "filesystem.write", "exec.shell"],
            "template": '''"""
{name} Skill - Fixer Pattern
"""

import os
from pathlib import Path
from typing import Dict, Any

PREFIX = os.environ.get('PREFIX', '/data/data/com.termux/files/usr')

def diagnose() -> Dict[str, Any]:
    """Diagnose issues."""
    return {{"issues": [], "healthy": True}}

def fix(issue: str = None) -> Dict[str, Any]:
    """Fix an issue."""
    return {{"fixed": True, "issue": issue}}

def heal() -> Dict[str, Any]:
    """Auto-heal common issues."""
    issues = diagnose()
    fixed = []
    for issue in issues.get("issues", []):
        fix(issue)
        fixed.append(issue)
    return {{"healed": fixed}}

def verify_fix(issue: str = None) -> Dict[str, Any]:
    """Verify a fix was successful."""
    return {{"verified": True}}
'''
        }
    }
    
    def __init__(self, skills_dir: Path = None, memory_dir: Path = None):
        self.skills_dir = skills_dir or SKILLS_DIR
        self.memory_dir = memory_dir or MEMORY_DIR
        self.learned_patterns: Dict[str, LearnedPattern] = {}
        self._load_patterns()
    
    def _load_patterns(self):
        """Load learned patterns from memory."""
        patterns_file = self.memory_dir / "learned_patterns.json"
        if patterns_file.exists():
            try:
                with open(patterns_file) as f:
                    data = json.load(f)
                for p in data.get("patterns", []):
                    pattern = LearnedPattern(**p)
                    self.learned_patterns[pattern.pattern_id] = pattern
            except Exception as e:
                logger.warning(f"Failed to load patterns: {e}")
    
    def _save_patterns(self):
        """Save learned patterns to memory."""
        self.memory_dir.mkdir(parents=True, exist_ok=True)
        patterns_file = self.memory_dir / "learned_patterns.json"
        try:
            with open(patterns_file, 'w') as f:
                json.dump({
                    "patterns": [p.to_dict() for p in self.learned_patterns.values()],
                    "updated_at": datetime.now().isoformat()
                }, f, indent=2)
        except Exception as e:
            logger.error(f"Failed to save patterns: {e}")
    
    def create_skill(
        self,
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
            additional_provides: Extra functions to add
            additional_capabilities: Extra capabilities
            
        Returns:
            Creation result
        """
        skill_dir = self.skills_dir / name
        
        if skill_dir.exists():
            return {"success": False, "error": f"Skill exists: {name}"}
        
        base = self.SKILL_TEMPLATES.get(template)
        if not base:
            return {"success": False, "error": f"Unknown template: {template}"}
        
        provides = list(base["provides"])
        if additional_provides:
            provides.extend(additional_provides)
        
        capabilities = list(base["capabilities"])
        if additional_capabilities:
            capabilities.extend(additional_capabilities)
        
        try:
            skill_dir.mkdir(parents=True, exist_ok=True)
            
            # Write skill.yml
            manifest = {
                "name": name,
                "description": description or base["description"],
                "version": "1.0.0",
                "provides": provides,
                "requires_capabilities": capabilities
            }
            
            if HAS_YAML:
                with open(skill_dir / "skill.yml", 'w') as f:
                    yaml.dump(manifest, f, default_flow_style=False)
            else:
                with open(skill_dir / "skill.json", 'w') as f:
                    json.dump(manifest, f, indent=2)
            
            # Write skill.py
            code = base["template"].format(name=name)
            with open(skill_dir / "skill.py", 'w') as f:
                f.write(code)
            
            logger.info(f"Created skill: {name}")
            
            return {
                "success": True,
                "skill": name,
                "path": str(skill_dir),
                "template": template,
                "provides": provides
            }
            
        except Exception as e:
            if skill_dir.exists():
                shutil.rmtree(skill_dir)
            return {"success": False, "error": str(e)}
    
    def learn_from_execution(
        self,
        task_type: str,
        steps: List[str],
        success: bool
    ) -> Dict[str, Any]:
        """
        Learn from a task execution.
        
        Args:
            task_type: Type of task executed
            steps: Steps taken
            success: Whether the task succeeded
            
        Returns:
            Learning result
        """
        # Create pattern ID
        pattern_id = f"{task_type}_{hash(tuple(steps)) % 10000:04d}"
        
        if pattern_id in self.learned_patterns:
            pattern = self.learned_patterns[pattern_id]
            # Update success rate
            total = pattern.times_used + 1
            pattern.success_rate = (pattern.success_rate * pattern.times_used + (1 if success else 0)) / total
            pattern.times_used = total
            pattern.last_used = datetime.now().isoformat()
        else:
            pattern = LearnedPattern(
                pattern_id=pattern_id,
                task_type=task_type,
                steps=steps,
                success_rate=1.0 if success else 0.0,
                times_used=1,
                last_used=datetime.now().isoformat()
            )
            self.learned_patterns[pattern_id] = pattern
        
        self._save_patterns()
        
        return {
            "learned": True,
            "pattern_id": pattern_id,
            "task_type": task_type,
            "success_rate": pattern.success_rate,
            "times_used": pattern.times_used
        }
    
    def get_best_pattern(self, task_type: str) -> Optional[Dict[str, Any]]:
        """
        Get the best pattern for a task type.
        
        Args:
            task_type: Type of task
            
        Returns:
            Best matching pattern or None
        """
        matching = [
            p for p in self.learned_patterns.values()
            if p.task_type == task_type and p.success_rate > 0.5
        ]
        
        if not matching:
            return None
        
        # Sort by success rate * times_used
        best = max(matching, key=lambda p: p.success_rate * min(p.times_used, 10))
        
        return {
            "pattern_id": best.pattern_id,
            "steps": best.steps,
            "success_rate": best.success_rate,
            "confidence": min(best.times_used / 10, 1.0)
        }
    
    def suggest_skill(self, task_description: str) -> Dict[str, Any]:
        """
        Suggest a skill to create based on task description.
        
        Args:
            task_description: Description of what the skill should do
            
        Returns:
            Skill suggestion with template recommendation
        """
        task_lower = task_description.lower()
        
        # Pattern matching
        if any(w in task_lower for w in ["check", "verify", "validate", "test"]):
            template = "checker"
        elif any(w in task_lower for w in ["manage", "start", "stop", "control"]):
            template = "manager"
        elif any(w in task_lower for w in ["analyze", "parse", "report", "summarize"]):
            template = "analyzer"
        elif any(w in task_lower for w in ["fix", "repair", "heal", "diagnose"]):
            template = "fixer"
        else:
            template = "checker"
        
        # Extract potential name
        words = task_lower.replace("_", " ").split()
        name = words[0] if words else "custom"
        
        return {
            "suggested_name": name,
            "suggested_template": template,
            "template_provides": self.SKILL_TEMPLATES[template]["provides"],
            "template_description": self.SKILL_TEMPLATES[template]["description"]
        }
    
    def list_templates(self) -> List[Dict[str, Any]]:
        """List available skill templates."""
        return [
            {"name": n, "description": t["description"], "provides": t["provides"]}
            for n, t in self.SKILL_TEMPLATES.items()
        ]
    
    def list_patterns(self, min_success_rate: float = 0.0) -> List[Dict[str, Any]]:
        """List learned patterns."""
        return [
            p.to_dict() for p in self.learned_patterns.values()
            if p.success_rate >= min_success_rate
        ]
    
    def delete_skill(self, name: str) -> Dict[str, Any]:
        """Delete a skill."""
        skill_dir = self.skills_dir / name
        if not skill_dir.exists():
            return {"success": False, "error": f"Not found: {name}"}
        
        try:
            shutil.rmtree(skill_dir)
            return {"success": True, "deleted": name}
        except Exception as e:
            return {"success": False, "error": str(e)}
    
    def export_patterns(self) -> Dict[str, Any]:
        """Export all learned patterns."""
        return {
            "patterns": [p.to_dict() for p in self.learned_patterns.values()],
            "exported_at": datetime.now().isoformat()
        }
    
    def import_patterns(self, patterns: List[Dict]) -> Dict[str, Any]:
        """Import patterns from another agent."""
        imported = 0
        for p in patterns:
            try:
                pattern = LearnedPattern(**p)
                if pattern.pattern_id not in self.learned_patterns:
                    self.learned_patterns[pattern.pattern_id] = pattern
                    imported += 1
            except Exception as e:
                logger.warning(f"Failed to import pattern: {e}")
        
        self._save_patterns()
        return {"imported": imported, "total": len(patterns)}


_learner: Optional[SkillLearner] = None

def get_learner() -> SkillLearner:
    global _learner
    if _learner is None:
        _learner = SkillLearner()
    return _learner

def create_skill(**kwargs) -> Dict[str, Any]:
    return get_learner().create_skill(**kwargs)

def learn_from_execution(**kwargs) -> Dict[str, Any]:
    return get_learner().learn_from_execution(**kwargs)

def get_best_pattern(task_type: str) -> Optional[Dict[str, Any]]:
    return get_learner().get_best_pattern(task_type)
