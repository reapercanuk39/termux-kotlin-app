"""
Agent Framework Generators
==========================

Template generators for skills, agents, and tasks.
Produces production-ready code following the Unified Agent Framework.
"""

import os
import json
from pathlib import Path
from datetime import datetime
from typing import Any, Dict, List, Optional

# Try to import YAML
try:
    import yaml
    HAS_YAML = True
except ImportError:
    HAS_YAML = False


class SkillGenerator:
    """
    Generates skill templates following the Unified Agent Framework.
    
    Creates:
    - skill.yml (manifest)
    - skill.py (implementation)
    """
    
    SKILL_YML_TEMPLATE = '''name: {name}
description: "{description}"
version: "1.0.0"

provides:
{provides}

requires_capabilities:
{capabilities}

sandbox_safe: true
'''

    SKILL_PY_TEMPLATE = '''"""
{name} Skill
{underline}

{description}
"""

import os
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..base import Skill, SkillResult


class {class_name}Skill(Skill):
    """{description}"""
    
    name = "{name}"
    description = "{description}"
    provides = {provides_list}
    requires_capabilities = {capabilities_list}
    
    def get_functions(self) -> Dict[str, callable]:
        return {{
{function_map}
            "self_test": self.self_test
        }}
    
{function_implementations}
    
    def self_test(self) -> Dict[str, Any]:
        """Run self-test to verify skill is working."""
        self.log("Running {name} skill self-test")
        
        tests = []
        # Add your tests here
        tests.append({{"test": "basic", "passed": True}})
        
        all_passed = all(t["passed"] for t in tests)
        return {{"status": "passed" if all_passed else "failed", "tests": tests}}
'''

    FUNCTION_TEMPLATE = '''    def {name}(self{params}) -> Dict[str, Any]:
        """{description}"""
        self.log("Executing {name}")
        
        # Validate capabilities
        # self.executor.require_capability("{capability}")
        
        # Use sandbox for temp files
        # tmp = self.sandbox.tmp_dir / "temp_file"
        
        # Use memory for state
        # state = self.memory.load()
        
        # Execute commands
        # result = self.executor.run(["command", "arg"])
        
        return {{
            "status": "success",
            "result": None
        }}
'''

    def __init__(self, skills_dir: Path):
        self.skills_dir = Path(skills_dir)
    
    def generate(
        self,
        name: str,
        description: str,
        functions: List[Dict[str, str]],
        capabilities: List[str]
    ) -> Dict[str, Any]:
        """
        Generate a complete skill.
        
        Args:
            name: Skill name (lowercase, no spaces)
            description: What the skill does
            functions: List of {"name": str, "description": str, "params": str}
            capabilities: Required capabilities
        
        Returns:
            Dict with paths to created files
        """
        skill_dir = self.skills_dir / name
        skill_dir.mkdir(parents=True, exist_ok=True)
        
        # Generate skill.yml
        provides_str = '\n'.join(f'  - {f["name"]}' for f in functions)
        caps_str = '\n'.join(f'  - {c}' for c in capabilities)
        
        yml_content = self.SKILL_YML_TEMPLATE.format(
            name=name,
            description=description,
            provides=provides_str,
            capabilities=caps_str
        )
        
        yml_path = skill_dir / "skill.yml"
        yml_path.write_text(yml_content)
        
        # Generate skill.py
        class_name = ''.join(word.capitalize() for word in name.split('_'))
        underline = '=' * (len(name) + 6)
        
        provides_list = [f["name"] for f in functions]
        
        function_map = '\n'.join(
            f'            "{f["name"]}": self.{f["name"]},'
            for f in functions
        )
        
        function_implementations = '\n'.join(
            self.FUNCTION_TEMPLATE.format(
                name=f["name"],
                description=f.get("description", f["name"]),
                params=f.get("params", ""),
                capability=capabilities[0] if capabilities else "filesystem.read"
            )
            for f in functions
        )
        
        py_content = self.SKILL_PY_TEMPLATE.format(
            name=name,
            underline=underline,
            description=description,
            class_name=class_name,
            provides_list=repr(provides_list),
            capabilities_list=repr(capabilities),
            function_map=function_map,
            function_implementations=function_implementations
        )
        
        py_path = skill_dir / "skill.py"
        py_path.write_text(py_content)
        
        return {
            "status": "success",
            "skill_name": name,
            "files": {
                "manifest": str(yml_path),
                "implementation": str(py_path)
            }
        }


class AgentGenerator:
    """
    Generates agent definition templates.
    
    Creates:
    - <agent_name>.yml
    """
    
    AGENT_YML_TEMPLATE = '''name: {name}
description: "{description}"
version: "1.0.0"

capabilities:
{capabilities}

skills:
{skills}

memory_backend: json

tasks:
{tasks}
'''

    def __init__(self, models_dir: Path):
        self.models_dir = Path(models_dir)
    
    def generate(
        self,
        name: str,
        description: str,
        capabilities: List[str],
        skills: List[str],
        tasks: List[Dict[str, str]]
    ) -> Dict[str, Any]:
        """
        Generate an agent definition.
        
        Args:
            name: Agent name (e.g., "build_agent")
            description: What the agent does
            capabilities: List of capabilities
            skills: List of skill names
            tasks: List of {"name": str, "description": str}
        
        Returns:
            Dict with path to created file
        """
        self.models_dir.mkdir(parents=True, exist_ok=True)
        
        caps_str = '\n'.join(f'  - {c}' for c in capabilities)
        skills_str = '\n'.join(f'  - {s}' for s in skills)
        tasks_str = '\n'.join(
            f'  - {t["name"]}: "{t["description"]}"'
            for t in tasks
        )
        
        yml_content = self.AGENT_YML_TEMPLATE.format(
            name=name,
            description=description,
            capabilities=caps_str,
            skills=skills_str,
            tasks=tasks_str
        )
        
        yml_path = self.models_dir / f"{name}.yml"
        yml_path.write_text(yml_content)
        
        return {
            "status": "success",
            "agent_name": name,
            "file": str(yml_path)
        }


class TaskGenerator:
    """
    Generates task implementation templates.
    """
    
    TASK_TEMPLATE = '''"""
Task: {name}
Agent: {agent_name}
Generated: {timestamp}
"""

from typing import Any, Dict, List


def run(context, task: str) -> Dict[str, Any]:
    """
    Execute the {name} task.
    
    Args:
        context: Agent context with executor, sandbox, memory
        task: Task description
    
    Returns:
        Structured result dict
    """
    steps = []
    context.log(f"Task received: {{task}}")
    
    # Step 1: Validate capabilities
    required_caps = {capabilities}
    for cap in required_caps:
        if not context.has_capability(cap):
            return {{
                "status": "error",
                "error": "capability_denied",
                "required": cap
            }}
    steps.append("Validated capabilities")
    
    # Step 2: Setup sandbox
    work_dir = context.sandbox_path / "task_{name}"
    work_dir.mkdir(parents=True, exist_ok=True)
    steps.append(f"Using work directory: {{work_dir}}")
    
    # Step 3: Load memory state
    state = context.memory.load()
    steps.append("Loaded memory state")
    
    # Step 4: Execute task logic
    try:
{task_logic}
        steps.append("Executed task logic")
    except Exception as e:
        return {{
            "status": "error",
            "error": "execution_error",
            "details": str(e),
            "steps": steps
        }}
    
    # Step 5: Update memory
    context.memory.update("last_task", "{name}")
    context.memory.update("last_run", context.timestamp())
    steps.append("Updated memory")
    
    return {{
        "status": "success",
        "steps": steps,
        "result": result
    }}
'''

    def generate(
        self,
        name: str,
        agent_name: str,
        capabilities: List[str],
        task_logic: str = None
    ) -> str:
        """
        Generate a task implementation.
        
        Args:
            name: Task name
            agent_name: Agent this task belongs to
            capabilities: Required capabilities
            task_logic: Optional custom logic (Python code)
        
        Returns:
            Generated Python code as string
        """
        if task_logic is None:
            task_logic = '''        # TODO: Implement task logic
        result = {"message": "Task not implemented"}'''
        
        # Indent task logic
        task_logic = '\n'.join(
            '        ' + line if line.strip() else ''
            for line in task_logic.split('\n')
        )
        
        code = self.TASK_TEMPLATE.format(
            name=name,
            agent_name=agent_name,
            timestamp=datetime.now().isoformat(),
            capabilities=repr(capabilities),
            task_logic=task_logic
        )
        
        return code


# Convenience functions
def generate_skill(
    skills_dir: str,
    name: str,
    description: str,
    functions: List[Dict[str, str]],
    capabilities: List[str]
) -> Dict[str, Any]:
    """Generate a new skill."""
    gen = SkillGenerator(Path(skills_dir))
    return gen.generate(name, description, functions, capabilities)


def generate_agent(
    models_dir: str,
    name: str,
    description: str,
    capabilities: List[str],
    skills: List[str],
    tasks: List[Dict[str, str]]
) -> Dict[str, Any]:
    """Generate a new agent."""
    gen = AgentGenerator(Path(models_dir))
    return gen.generate(name, description, capabilities, skills, tasks)


def generate_task(
    name: str,
    agent_name: str,
    capabilities: List[str],
    task_logic: str = None
) -> str:
    """Generate task code."""
    gen = TaskGenerator()
    return gen.generate(name, agent_name, capabilities, task_logic)


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="Agent Framework Generator")
    parser.add_argument("type", choices=["skill", "agent", "task"])
    parser.add_argument("--name", "-n", required=True)
    parser.add_argument("--description", "-d", default="")
    parser.add_argument("--output", "-o", default=".")
    
    args = parser.parse_args()
    
    if args.type == "skill":
        result = generate_skill(
            args.output,
            args.name,
            args.description or f"{args.name} skill",
            [{"name": "example_function", "description": "Example function"}],
            ["filesystem.read"]
        )
        print(json.dumps(result, indent=2))
    
    elif args.type == "agent":
        result = generate_agent(
            args.output,
            args.name,
            args.description or f"{args.name} agent",
            ["filesystem.read", "memory.read", "memory.write"],
            ["fs"],
            [{"name": "example_task", "description": "Example task"}]
        )
        print(json.dumps(result, indent=2))
    
    elif args.type == "task":
        code = generate_task(
            args.name,
            "example_agent",
            ["filesystem.read"]
        )
        print(code)
