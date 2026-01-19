# Executor API

The executor runs subprocesses with capability enforcement.

## Initialization

```python
from agents.core.runtime.executor import AgentExecutor

executor = AgentExecutor(
    agent_name="my_agent",
    capabilities=["exec.shell", "exec.pkg"],
    sandbox_path=Path("agents/sandboxes/my_agent")
)
```

## Methods

### run(command)

Run a command with capability check.

```python
result = executor.run(["echo", "hello"])
# result.returncode, result.stdout, result.stderr
```

### run_shell(script)

Run a shell script.

```python
result = executor.run_shell("echo $HOME && pwd")
```

### run_python(code)

Run Python code.

```python
result = executor.run_python("print(1 + 1)")
```

### require_capability(cap)

Check capability before action.

```python
executor.require_capability("exec.pkg")
# Raises CapabilityError if not allowed
```

## Binary Capabilities

| Binary | Required Capability |
|--------|---------------------|
| apt, pkg, dpkg | exec.pkg |
| git | exec.git |
| python, python3 | exec.python |
| qemu-* | exec.qemu |
| docker | exec.docker |
| jadx, apktool | exec.apk |
| xorriso, binwalk | exec.iso |
| tar, gzip, zip | exec.compress |

## Blocked Commands

Network commands are blocked:
- curl, wget, nc, ssh, scp
- ping, traceroute, dig, nslookup

## Error Handling

```python
from agents.core.runtime.executor import CapabilityError

try:
    executor.run(["apt", "install", "vim"])
except CapabilityError as e:
    print(f"Denied: {e.capability}")
```
