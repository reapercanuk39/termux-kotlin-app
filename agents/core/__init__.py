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

import os
from pathlib import Path

# Get paths from environment (set by wrapper script) or use defaults
_prefix = os.environ.get("PREFIX", "/data/data/com.termux/files/usr")
TERMUX_PREFIX = Path(_prefix)
AGENTS_ROOT = Path(os.environ.get("AGENTS_ROOT", str(TERMUX_PREFIX / "share" / "agents")))
AGENTS_BIN = TERMUX_PREFIX / "bin"
AGENTS_ETC = TERMUX_PREFIX / "etc" / "agents"

# Don't try to create directories at import time - let the caller handle it
# This avoids permission errors when running in restricted environments
