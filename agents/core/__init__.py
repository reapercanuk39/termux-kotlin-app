"""
Termux-Kotlin Agent Framework Core
===================================

A fully offline, local agent system for Termux-Kotlin OS.
No external API calls - all processing is done locally.

Components:
- supervisor: Agent daemon (agentd) that manages agent lifecycle
- models: Agent and capability definitions
- runtime: Execution engine, memory, and sandboxing
"""

__version__ = "1.0.0"
__author__ = "Termux-Kotlin Project"

from pathlib import Path

# Termux-Kotlin paths
TERMUX_PREFIX = Path("/data/data/com.termux.kotlin/files/usr")
AGENTS_ROOT = TERMUX_PREFIX / "share" / "agents"
AGENTS_BIN = TERMUX_PREFIX / "bin"
AGENTS_ETC = TERMUX_PREFIX / "etc" / "agents"

# Development fallback paths (for testing outside Termux)
if not TERMUX_PREFIX.exists():
    import os
    _dev_root = Path(os.environ.get("AGENTS_DEV_ROOT", "/tmp/termux-agents"))
    TERMUX_PREFIX = _dev_root / "usr"
    AGENTS_ROOT = _dev_root / "agents"
    AGENTS_BIN = _dev_root / "bin"
    AGENTS_ETC = _dev_root / "etc" / "agents"

# Ensure directories exist
for _dir in [AGENTS_ROOT, AGENTS_BIN, AGENTS_ETC]:
    _dir.mkdir(parents=True, exist_ok=True)
