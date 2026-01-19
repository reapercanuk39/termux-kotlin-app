# Capability Guide

Capabilities restrict what agents and skills can do.

## Capability Categories

### Filesystem
| Capability | Description |
|------------|-------------|
| `filesystem.read` | Read files |
| `filesystem.write` | Write files |
| `filesystem.exec` | Execute files |
| `filesystem.delete` | Delete files |

### Network
| Capability | Description |
|------------|-------------|
| `network.none` | No network access |
| `network.local` | Localhost only (127.0.0.1) |
| `network.external` | **FORBIDDEN** - Never allowed |

### Execution
| Capability | Description |
|------------|-------------|
| `exec.pkg` | Package manager (apt, pkg) |
| `exec.git` | Git commands |
| `exec.shell` | Shell commands |
| `exec.python` | Python interpreter |
| `exec.build` | Build tools |
| `exec.analyze` | Analysis tools |
| `exec.qemu` | QEMU emulator |
| `exec.docker` | Docker commands |
| `exec.iso` | ISO tools |
| `exec.apk` | APK tools |
| `exec.compress` | Compression tools |

### Memory
| Capability | Description |
|------------|-------------|
| `memory.read` | Read agent memory |
| `memory.write` | Write agent memory |
| `memory.shared` | Access shared memory |

### System
| Capability | Description |
|------------|-------------|
| `system.info` | System information |
| `system.process` | Process management |
| `system.env` | Environment variables |

## Enforcement

```python
# In skill code
self.executor.require_capability("exec.pkg")

# In agentd
daemon.check_agent_capability("build_agent", "exec.pkg")
```

## Validation

Capabilities are validated at:
1. Agent load time
2. Skill load time  
3. Task execution time
4. Each subprocess call
