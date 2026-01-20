# Termux-Kotlin Agent Framework

A fully offline, local agent system for Termux-Kotlin OS. No external API calls - all processing is done locally using Python and local tools.

## Overview

The agent framework provides:
- **Agent Supervisor (agentd)**: Core daemon managing agent lifecycle
- **Capability System**: Fine-grained permission control
- **Skill System**: Modular, reusable skill plugins
- **CLI Interface**: Terminal-friendly command interface
- **Sandboxing**: Isolated execution environments per agent
- **Memory**: Persistent JSON-based storage per agent

## Quick Start

```bash
# List available agents
agent list

# Get agent info
agent info debug_agent

# Run a task
agent run debug_agent apk.analyze apk_path=/path/to/app.apk

# List available skills
agent skills

# Validate configuration
agent validate
```

## Architecture

```
/data/data/com.termux.kotlin/files/usr/
├── bin/
│   └── agent                    # CLI entrypoint
├── share/agents/
│   ├── core/
│   │   ├── __init__.py          # Core package init
│   │   ├── supervisor/
│   │   │   └── agentd.py        # Agent daemon
│   │   ├── models/
│   │   │   └── capabilities.yml # Capability definitions
│   │   └── runtime/
│   │       ├── memory.py        # Memory system
│   │       ├── sandbox.py       # Sandboxing
│   │       └── executor.py      # Command execution
│   ├── skills/
│   │   ├── pkg/                 # Package management skill
│   │   ├── git/                 # Git version control skill
│   │   ├── fs/                  # Filesystem skill
│   │   ├── apk/                 # APK analysis skill
│   │   ├── qemu/                # QEMU VM skill
│   │   └── iso/                 # ISO manipulation skill
│   ├── models/
│   │   ├── build_agent.yml      # CI/build agent
│   │   ├── system_agent.yml     # System maintenance agent
│   │   ├── repo_agent.yml       # Repository agent
│   │   └── debug_agent.yml      # Debug/analysis agent
│   ├── sandboxes/               # Per-agent sandboxes
│   ├── memory/                  # Per-agent memory files
│   ├── logs/                    # Per-agent logs
│   └── templates/               # Agent/skill templates
└── etc/agents/                  # System configuration
```

## Agents

### Built-in Agents

| Agent | Description | Skills |
|-------|-------------|--------|
| `build_agent` | Package building and CI | pkg, git, fs |
| `system_agent` | System maintenance | fs, pkg |
| `repo_agent` | Package repository | pkg, git, fs |
| `debug_agent` | Debug and analysis | qemu, iso, apk, fs |

### Agent Definition

Agents are defined in YAML files:

```yaml
name: my_agent
description: Description of what this agent does
version: "1.0.0"

capabilities:
  - filesystem.read
  - filesystem.write
  - network.none
  - exec.pkg

skills:
  - pkg
  - fs

memory_backend: json
```

## Capabilities

The capability system enforces fine-grained permissions:

### Filesystem
- `filesystem.read` - Read files and directories
- `filesystem.write` - Write/modify files
- `filesystem.exec` - Execute files
- `filesystem.delete` - Delete files

### Network
- `network.none` - No network access (fully offline)
- `network.local` - Localhost only
- `network.external` - External network (requires approval)

### Execution
- `exec.pkg` - Package management (pkg, apt, dpkg)
- `exec.git` - Git commands
- `exec.qemu` - QEMU virtual machines
- `exec.iso` - ISO manipulation
- `exec.apk` - APK analysis (apktool, jadx)
- `exec.docker` - Docker/Podman
- `exec.shell` - Shell commands
- `exec.python` - Python scripts
- `exec.build` - Build tools (make, cmake, gradle)
- `exec.analyze` - Analysis tools (binwalk, strings)
- `exec.compress` - Compression tools

### Presets
- `minimal` - Read-only, no execution
- `readonly` - Read + analyze
- `builder` - Full build capabilities
- `debugger` - Debug and analysis tools
- `full` - All capabilities

## Skills

Skills are modular capabilities that agents can use.

### Built-in Skills

| Skill | Functions | Required Capabilities |
|-------|-----------|----------------------|
| `pkg` | install_package, remove_package, update_packages, search_packages, list_installed | exec.pkg |
| `git` | clone, pull, push, commit, status, diff, log, branch | exec.git |
| `fs` | list_dir, read_file, write_file, copy, move, delete, find, grep | filesystem.read |
| `apk` | decode, build, sign, analyze, extract, get_manifest | exec.apk |
| `qemu` | create_image, run_vm, list_images, convert_image, snapshot | exec.qemu |
| `iso` | extract, create, analyze, list_contents, get_bootloader_info | exec.iso |

### Creating a New Skill

1. Create skill directory:
```bash
mkdir -p agents/skills/myskill
```

2. Create `skill.yml`:
```yaml
name: myskill
description: My custom skill
version: "1.0.0"

provides:
  - my_function

requires_capabilities:
  - filesystem.read
```

3. Create `skill.py`:
```python
from agents.skills.base import Skill, SkillResult

class MySkill(Skill):
    name = "myskill"
    description = "My custom skill"
    provides = ["my_function"]
    requires_capabilities = ["filesystem.read"]
    
    def get_functions(self):
        return {"my_function": self.my_function}
    
    def my_function(self, **kwargs):
        self.log("Running my_function")
        return {"result": "success"}
```

## CLI Reference

```bash
# List all agents
agent list
agent list --json

# Show agent details
agent info <agent_name>
agent info --json <agent_name>

# Run a task
agent run <agent_name> <skill.function> [args...]
agent run debug_agent apk.analyze apk_path=/path/to/app.apk
agent run build_agent pkg.list_installed

# View logs
agent logs <agent_name>
agent logs --limit 50 <agent_name>
agent logs --verbose <agent_name>

# List skills
agent skills
agent skills --json

# Validate configuration
agent validate
```

## Memory System

Each agent has persistent memory stored in JSON:

```
agents/memory/<agent_name>.json
```

Memory structure:
```json
{
  "agent_name": "debug_agent",
  "created_at": "2024-01-19T12:00:00",
  "updated_at": "2024-01-19T12:30:00",
  "data": {
    "last_analyzed_apk": "/path/to/app.apk",
    "settings": {}
  },
  "history": [
    {"timestamp": "...", "task": "apk.analyze", "result": {...}}
  ]
}
```

## Sandboxing

Each agent has an isolated sandbox:

```
agents/sandboxes/<agent_name>/
├── tmp/       # Temporary files (cleaned on task exit)
├── work/      # Working directory for tasks
├── output/    # Task outputs (preserved)
└── cache/     # Persistent cache
```

## Example Workflows

### Analyze an APK
```bash
agent run debug_agent apk.analyze apk_path=/sdcard/app.apk
```

### Rebuild packages
```bash
agent run build_agent pkg.update_packages
agent run build_agent git.status path=/path/to/repo
```

### Analyze ISO bootloader
```bash
agent run debug_agent iso.get_bootloader_info iso_path=/path/to/distro.iso
```

### System maintenance
```bash
agent run system_agent fs.find path=/data pattern="*.tmp"
agent run system_agent pkg.clean_cache
```

## Bootstrap Integration

The agent framework is designed to be included in the Termux-Kotlin bootstrap:

### Requirements
1. Python 3 must be included in the bootstrap
2. PyYAML package for YAML parsing
3. Agent files copied to `/usr/share/agents/`
4. CLI symlinked to `/usr/bin/agent`

### CI Integration

Add to `.github/workflows/ci.yml`:

```yaml
jobs:
  validate_agents:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'
      - run: pip install pyyaml
      - run: python agents/bin/agent validate
      - run: |
          for agent in build_agent system_agent repo_agent debug_agent; do
            python agents/bin/agent run $agent fs.self_test
          done
```

## Offline Guarantee

All agents operate fully offline:
- No external API calls
- All tools are local binaries
- Network capability `network.none` is enforced by default
- Agents cannot bypass network restrictions

## Adding New Agents

1. Copy template:
```bash
cp agents/templates/agent_template.yml agents/models/my_agent.yml
```

2. Edit configuration:
```yaml
name: my_agent
description: My custom agent
capabilities:
  - filesystem.read
  - exec.shell
skills:
  - fs
```

3. Validate:
```bash
agent validate
```

4. Test:
```bash
agent run my_agent fs.list_dir path=/
```

## Security Considerations

1. **Capability Enforcement**: Agents can only use capabilities they declare
2. **Sandbox Isolation**: Each agent has its own working directory
3. **No Network by Default**: `network.none` prevents external calls
4. **Logged Actions**: All actions are logged to per-agent log files
5. **No Secrets in Memory**: Memory files should not contain sensitive data

## Future Enhancements

- [ ] Natural language task interpretation
- [x] Agent-to-agent communication (Swarm Intelligence v1.0)
- [ ] SQLite memory backend
- [ ] Resource limits (CPU, memory)
- [ ] Agent scheduling/cron
- [ ] Web dashboard for monitoring
- [ ] Darwin Gödel Machine (self-evolving skills)
- [ ] Meta-learning (cross-skill pattern transfer)

---

## Swarm Intelligence (NEW in v2.0.3)

The agent framework now supports **emergent multi-agent coordination** through stigmergy-based swarm intelligence. Agents communicate indirectly by leaving "pheromone" signals in a shared space.

### How It Works

1. **Agents emit signals** after tasks (automatically on success/failure)
2. **Signals decay** over time (5% per cycle, every 5 minutes)
3. **Signals reinforce** when repeated (strength increases, TTL extends)
4. **Other agents sense** signals to guide decisions
5. **Consensus system** analyzes signals for recommendations

### Signal Types

| Signal | Purpose | Behavior |
|--------|---------|----------|
| `SUCCESS` | Task completed | Attracts other agents |
| `FAILURE` | Task failed | Warns/repels agents |
| `BLOCKED` | Path blocked | Long-lived warning (2hr) |
| `DANGER` | Critical failure | Max strength, 24hr TTL |
| `WORKING` | Agent busy | Short-lived (2min) |
| `CLAIMING` | Exclusive claim | Prevents duplicate work |
| `RELEASING` | Release claim | Very short-lived |
| `HELP_NEEDED` | Request help | 30min TTL |
| `LEARNED` | New discovery | Shared knowledge (12hr) |
| `OPTIMIZED` | Better approach | Improvement signal |
| `DEPRECATED` | Old approach dead | 24hr warning |
| `RESOURCE_FOUND` | Found resource | Attracts agents |

### CLI Command

```bash
# Show swarm status and active signals
agent swarm

# JSON output
agent swarm --json
```

### Example Output

```
=== Swarm Intelligence Status ===
Enabled: True
Total Signals: 5
Average Strength: 0.87
Last Decay: 2026-01-20T08:07:14

Signals by Type:
  success: 3
  failure: 1
  learned: 1

Recent Signals:
Type    | Agent        | Target        | Strength | Age
--------------------------------------------------------
success | system_agent | pkg.install   | 1.00     | 2m
success | build_agent  | git.clone     | 0.95     | 5m
failure | debug_agent  | apk.decode    | 0.80     | 8m
```

### Using Swarm in Skills

```python
from agents.core.swarm import SignalEmitter, SignalSensor

class MySkill(Skill):
    def my_function(self, **kwargs):
        # Get swarm interfaces
        emitter = self.executor.daemon.get_swarm_emitter(self.agent_name)
        sensor = self.executor.daemon.get_swarm_sensor(self.agent_name)
        
        if sensor:
            # Check if task is safe to proceed
            recommendation = sensor.should_proceed("my_task")
            if not recommendation["proceed"]:
                return {"error": f"Swarm advises: {recommendation['reason']}"}
        
        # Do work...
        result = self.do_something()
        
        if emitter:
            if result["success"]:
                emitter.report_success("my_task", result)
            else:
                emitter.report_failure("my_task", result.get("error"))
        
        return result
```

### Architecture

```
agents/
├── core/
│   └── swarm/
│       ├── __init__.py       # Module exports
│       ├── swarm.py          # SwarmCoordinator, Signal, SignalType
│       └── signals.py        # SignalEmitter, SignalSensor
└── swarm/
    ├── index.json            # Signal index
    └── signals/              # Individual signal files
        └── *.json
```

### Key Classes

| Class | Purpose |
|-------|---------|
| `SwarmCoordinator` | Central coordinator, manages signal lifecycle |
| `Signal` | Individual pheromone signal with strength/decay |
| `SignalType` | Enum of signal types |
| `SignalEmitter` | Agent-friendly signal emission interface |
| `SignalSensor` | Agent-friendly signal sensing interface |

### Swarm Consensus

The swarm provides recommendations based on collective signal analysis:

```python
consensus = coordinator.get_consensus("pkg.install")
# Returns:
# {
#   "sentiment": "positive",  # positive/negative/neutral/unknown
#   "confidence": 0.8,        # 0.0 to 1.0
#   "recommendation": "proceed",  # proceed/avoid/caution/explore
#   "signals_count": 5,
#   "positive_score": 3.2,
#   "negative_score": 0.5
# }
```

---

*Termux-Kotlin Agent Framework v1.1.0 (with Swarm Intelligence)*
