# Agent Framework Bootstrap Integration Plan

This document describes how to integrate the agent framework into the Termux-Kotlin bootstrap so that agents are available immediately after app installation.

## Overview

The goal is to make Python and the agent framework part of the core Termux-Kotlin environment, eliminating the need for users to run `pkg install python` after installing the APK.

## Integration Strategy

### 1. Include Python in Bootstrap

The bootstrap must include Python 3 and required dependencies:

```
bootstrap-<arch>.zip
├── bin/
│   ├── python3 -> python3.12
│   ├── python3.12
│   └── agent              # Agent CLI (Python script)
├── lib/
│   ├── python3.12/        # Python standard library
│   │   ├── ...
│   │   └── site-packages/
│   │       └── yaml/      # PyYAML package
│   └── libpython3.12.so
└── share/
    └── agents/            # Agent framework
        ├── core/
        ├── skills/
        ├── models/
        ├── templates/
        ├── sandboxes/     # Created at runtime
        ├── memory/        # Created at runtime
        └── logs/          # Created at runtime
```

### 2. Package Requirements

Include these packages in the bootstrap build:

| Package | Size (approx) | Purpose |
|---------|--------------|---------|
| python | ~50 MB | Python 3.12 interpreter |
| python-pip | ~10 MB | Package manager (optional) |
| python-yaml | ~1 MB | YAML parsing for configs |

### 3. Bootstrap Build Script Modifications

Add to the package build script (`termux-packages/scripts/build-bootstrap.sh`):

```bash
# Add Python and agent framework to bootstrap
BOOTSTRAP_PACKAGES="$BOOTSTRAP_PACKAGES python python-pip"

# Copy agent framework to bootstrap
copy_agents_framework() {
    local AGENTS_SRC="$TERMUX_KOTLIN_APP/agents"
    local AGENTS_DST="$BOOTSTRAP_DIR/share/agents"
    
    mkdir -p "$AGENTS_DST"
    
    # Copy core framework
    cp -r "$AGENTS_SRC/core" "$AGENTS_DST/"
    cp -r "$AGENTS_SRC/skills" "$AGENTS_DST/"
    cp -r "$AGENTS_SRC/models" "$AGENTS_DST/"
    cp -r "$AGENTS_SRC/templates" "$AGENTS_DST/"
    
    # Copy CLI
    cp "$AGENTS_SRC/bin/agent" "$BOOTSTRAP_DIR/bin/agent"
    chmod +x "$BOOTSTRAP_DIR/bin/agent"
    
    # Create runtime directories
    mkdir -p "$AGENTS_DST/sandboxes"
    mkdir -p "$AGENTS_DST/memory"
    mkdir -p "$AGENTS_DST/logs"
}
```

### 4. Path Rewriting

The agent framework uses dynamic path detection, but for consistency with Termux-Kotlin's path rewriting:

1. The CLI (`bin/agent`) detects its location dynamically
2. All paths use `$PREFIX/share/agents` as base
3. No hardcoded `/data/data/com.termux/` paths

### 5. SYMLINKS.txt Additions

Add to `SYMLINKS.txt` during bootstrap build:

```
agent→../share/agents/bin/agent
```

This creates the symlink: `/usr/bin/agent` → `/usr/share/agents/bin/agent`

### 6. First-Run Initialization

The agent framework creates runtime directories on first use:
- `$PREFIX/share/agents/sandboxes/`
- `$PREFIX/share/agents/memory/`
- `$PREFIX/share/agents/logs/`

No special initialization is required in `TermuxInstaller.kt`.

## CI/CD Integration

### GitHub Actions Workflow

Add to `.github/workflows/ci.yml`:

```yaml
name: CI

on: [push, pull_request]

jobs:
  build:
    # ... existing build job ...

  validate_agents:
    name: Validate Agent Framework
    runs-on: ubuntu-latest
    needs: build
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.11'
      
      - name: Install dependencies
        run: pip install pyyaml
      
      - name: Validate agent configs
        run: |
          cd agents
          python bin/agent validate
      
      - name: Run agent self-tests
        run: |
          cd agents
          for agent in build_agent system_agent repo_agent debug_agent; do
            echo "Testing $agent..."
            python bin/agent run $agent fs.self_test || true
          done
      
      - name: Test skill loading
        run: |
          cd agents
          python bin/agent skills
```

### Validation Script

Create `scripts/validate-agents.sh`:

```bash
#!/bin/bash
set -e

echo "=== Agent Framework Validation ==="

cd "$(dirname "$0")/../agents"

echo "1. Validating configurations..."
python bin/agent validate

echo "2. Listing agents..."
python bin/agent list

echo "3. Listing skills..."
python bin/agent skills

echo "4. Running self-tests..."
for agent in build_agent system_agent repo_agent debug_agent; do
    echo "   Testing $agent..."
    python bin/agent run $agent fs.self_test 2>/dev/null || echo "   (skipped - requires full environment)"
done

echo "=== Validation Complete ==="
```

## Size Considerations

Adding Python to the bootstrap increases APK size significantly:

| Component | Size Impact |
|-----------|------------|
| Python 3.12 | +50 MB |
| Python stdlib | +20 MB |
| PyYAML | +1 MB |
| Agent framework | +500 KB |
| **Total** | **~70 MB** |

### Mitigation Strategies

1. **Minimal Python**: Build Python without unnecessary modules (tkinter, test suite)
2. **Byte-compile**: Pre-compile .py to .pyc, remove .py source
3. **Compress**: Use better compression in bootstrap ZIP
4. **Optional**: Make agents a separate optional bootstrap

## Alternative: On-Demand Installation

If the size increase is unacceptable, implement on-demand installation:

1. User runs `agent` command
2. If Python not found, prompt to install:
   ```
   Agent framework requires Python.
   Install now? [Y/n]
   ```
3. Run `pkg install python python-pip python-yaml`
4. Extract agent framework from APK assets
5. Continue with agent command

## Development Testing

Test the framework locally before bootstrap integration:

```bash
# Set development environment
export AGENTS_DEV_ROOT=/tmp/test-agents
export PYTHONPATH=/root/termux-kotlin-app

# Create directories
mkdir -p $AGENTS_DEV_ROOT/{models,skills,memory,sandboxes,logs}

# Copy files
cp -r /root/termux-kotlin-app/agents/* $AGENTS_DEV_ROOT/

# Run CLI
python /root/termux-kotlin-app/agents/bin/agent list
python /root/termux-kotlin-app/agents/bin/agent validate
```

## Rollout Plan

1. **Phase 1**: Include agents in repo, test manually
2. **Phase 2**: Add CI validation
3. **Phase 3**: Include Python in x86_64 bootstrap (for emulator testing)
4. **Phase 4**: Include in all architecture bootstraps
5. **Phase 5**: Document for users

## Future Considerations

### Plugin Marketplace
- Central repository for community agents/skills
- Install via: `agent install skill-name`

### Agent Upgrades
- Version checking for agent framework
- Update via: `agent upgrade`

### Metrics
- Anonymous usage statistics (opt-in)
- Popular skills tracking

---

*Last updated: 2026-01-19*
