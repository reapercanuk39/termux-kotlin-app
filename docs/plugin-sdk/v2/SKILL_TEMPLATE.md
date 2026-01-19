# Skill Template

Create skills in `agents/skills/<skill_name>/` with two files.

## skill.yml

```yaml
name: my_skill
description: "What this skill does"
version: "1.0.0"
provides:
  - function_one
  - function_two
requires_capabilities:
  - filesystem.read
  - exec.shell
sandbox_safe: true
```

## skill.py

```python
from agents.skills.base import Skill, SkillResult

class MySkill(Skill):
    name = "my_skill"
    description = "What this skill does"
    requires_capabilities = ["filesystem.read", "exec.shell"]
    
    def get_functions(self):
        return {
            "function_one": self.function_one,
            "function_two": self.function_two,
            "self_test": self.self_test
        }
    
    def function_one(self, context, arg1: str) -> SkillResult:
        self.executor.require_capability("filesystem.read")
        self.log(f"Running function_one with {arg1}")
        
        # Use sandbox for temp files
        tmp = self.sandbox.tmp_dir / "work.txt"
        tmp.write_text(arg1)
        
        return SkillResult(success=True, data={"processed": arg1})
    
    def self_test(self, context) -> SkillResult:
        return SkillResult(success=True, data={"test": "passed"})
```

## Rules

- Always implement `self_test()`
- Use `self.executor.require_capability()` before actions
- Use `self.sandbox` for all temp files
- Return `SkillResult` from all functions
- Never use `network.external`
