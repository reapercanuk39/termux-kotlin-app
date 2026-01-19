# Task Template

Tasks are executed via `agent run <agent> "<skill.function>"`.

## Task Function Template

```python
def run_task(context, task: str) -> dict:
    steps = []
    context.log(f"Task: {task}")
    
    # Step 1: Check capabilities
    context.require("filesystem.read")
    steps.append("Verified capabilities")
    
    # Step 2: Setup sandbox
    work_dir = context.sandbox_path / "work"
    work_dir.mkdir(exist_ok=True)
    steps.append(f"Created work dir: {work_dir}")
    
    # Step 3: Load memory
    state = context.memory.load()
    steps.append("Loaded memory state")
    
    # Step 4: Execute work
    result = context.run(["echo", "hello"])
    steps.append(f"Executed command: exit={result.returncode}")
    
    # Step 5: Update memory
    context.memory.update("last_task", task)
    steps.append("Updated memory")
    
    return {
        "status": "success",
        "steps": steps,
        "result": result.stdout
    }
```

## Task Rules

1. Break into deterministic steps
2. Validate capabilities before each action
3. Use sandbox for all temp files
4. Log every step
5. Return structured output
6. Never access network
7. Never escape sandbox boundaries

## Return Format

```json
{
  "status": "success|error",
  "steps": ["step1", "step2"],
  "result": "output data",
  "error": "error message if failed"
}
```
