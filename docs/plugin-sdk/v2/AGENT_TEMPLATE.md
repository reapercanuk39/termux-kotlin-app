# Agent Template

Create agents in `agents/models/<agent_name>.yml`.

## Template

```yaml
name: my_agent
description: "What this agent does"
version: "1.0.0"

capabilities:
  - filesystem.read
  - filesystem.write
  - exec.shell
  - memory.read
  - memory.write

skills:
  - fs
  - pkg

memory_backend: json
sandbox_path: agents/sandboxes/my_agent/
```

## Required Fields

| Field | Description |
|-------|-------------|
| name | Unique agent identifier |
| description | What the agent does |
| capabilities | List of allowed capabilities |
| skills | List of skills this agent can use |
| memory_backend | Storage type (json) |
| sandbox_path | Isolated workspace path |

## Capability Rules

- Only declare capabilities the agent actually needs
- Skills must have their required capabilities covered
- `network.external` is never allowed
- Use `network.local` only for localhost diagnostics

## Example Agents

- `build_agent` - Package building and installation
- `debug_agent` - APK/ISO analysis
- `system_agent` - System information and maintenance
- `security_agent` - Security scanning
