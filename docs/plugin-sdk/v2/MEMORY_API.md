# Memory API

Agent memory provides persistent state storage.

## Location

```
agents/memory/<agent_name>.json
```

## Size Limit

- Maximum: 1MB per agent
- Entries: Max 1000 history items

## API

```python
from agents.core.runtime.memory import AgentMemory

memory = AgentMemory("my_agent", Path("agents/memory"))

# Read
value = memory.get("key")
all_data = memory.load()

# Write
memory.set("key", "value")
memory.update("key", new_value)

# Delete
memory.delete("key")
memory.clear_all()

# Stats
stats = memory.get_stats()
```

## In Skills

```python
def my_function(self, context):
    # Memory is available via self.memory
    state = self.memory.load()
    
    # Update state
    self.memory.set("last_run", datetime.now().isoformat())
    
    # Read previous state
    previous = self.memory.get("previous_result")
```

## Rules

1. **JSON only** - All values must be JSON-serializable
2. **No secrets** - Never store passwords or keys
3. **No logs** - Don't store raw log output
4. **Atomic** - Updates are atomic (file locking)
5. **Deterministic** - Keys should be predictable

## Schema Example

```json
{
  "last_task": "pkg.install_package",
  "last_run": "2026-01-19T20:00:00Z",
  "task_count": 42,
  "cache": {
    "packages_checked": ["vim", "git"]
  }
}
```
