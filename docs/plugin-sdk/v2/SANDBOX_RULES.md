# Sandbox Rules

All agents operate in isolated sandboxes.

## Sandbox Structure

```
agents/sandboxes/<agent_name>/
├── tmp/      # Temporary files (cleaned between runs)
├── work/     # Working directory
├── output/   # Task outputs
└── cache/    # Persistent cache
```

## Access Rules

| Rule | Description |
|------|-------------|
| No escape | Cannot write outside sandbox |
| No traversal | Path traversal blocked (../) |
| No symlinks | Symlinks to outside blocked |
| No absolute | Absolute paths outside PREFIX blocked |
| Isolation | Cannot access other agent sandboxes |

## Usage in Skills

```python
from agents.core.runtime.sandbox import AgentSandbox

# Sandbox is auto-created
sandbox = self.sandbox

# Use tmp for ephemeral files
tmp_file = sandbox.tmp_dir / "data.txt"
tmp_file.write_text("temporary data")

# Use work for processing
work_dir = sandbox.work_dir / "project"
work_dir.mkdir(exist_ok=True)

# Use output for results
output = sandbox.output_dir / "result.json"
output.write_text(json.dumps(result))

# Use cache for persistence
cache = sandbox.cache_dir / "state.json"
```

## Validation

```python
# Check if path is inside sandbox
if sandbox.is_safe_path(some_path):
    # Safe to use
    pass
```

## Cleanup

```python
# Clean tmp directory
sandbox.clean_tmp()

# Destroy entire sandbox
sandbox.destroy()
```
