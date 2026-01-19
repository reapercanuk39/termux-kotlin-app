"""Self-Healing Agent Mode - Detectors and Healers."""

import os
import json
from pathlib import Path
from typing import Any, Dict, List


class PrefixDetector:
    """Detect prefix path issues."""
    
    def detect(self, agents_root: Path) -> List[Dict[str, Any]]:
        issues = []
        for f in agents_root.rglob("*.py"):
            try:
                content = f.read_text()
                if "com.termux/" in content and "com.termux.kotlin" not in content:
                    issues.append({"file": str(f), "issue": "wrong_prefix"})
            except:
                pass
        return issues


class SandboxDetector:
    """Detect sandbox corruption."""
    
    def detect(self, agents_root: Path) -> List[Dict[str, Any]]:
        issues = []
        sandboxes = agents_root / "sandboxes"
        if not sandboxes.exists():
            issues.append({"path": str(sandboxes), "issue": "missing_sandboxes_dir"})
        return issues


class MemoryDetector:
    """Detect memory corruption."""
    
    def detect(self, agents_root: Path) -> List[Dict[str, Any]]:
        issues = []
        memory_dir = agents_root / "memory"
        if memory_dir.exists():
            for f in memory_dir.glob("*.json"):
                try:
                    with open(f) as fp:
                        json.load(fp)
                except json.JSONDecodeError:
                    issues.append({"file": str(f), "issue": "invalid_json"})
        return issues


class SandboxHealer:
    """Recreate corrupted sandboxes."""
    
    def heal(self, agents_root: Path, issues: List[Dict]) -> List[str]:
        healed = []
        sandboxes = agents_root / "sandboxes"
        sandboxes.mkdir(parents=True, exist_ok=True)
        healed.append(f"Created {sandboxes}")
        return healed


class MemoryHealer:
    """Reset corrupted memory files."""
    
    def heal(self, agents_root: Path, issues: List[Dict]) -> List[str]:
        healed = []
        for issue in issues:
            if issue.get("issue") == "invalid_json":
                path = Path(issue["file"])
                path.rename(path.with_suffix(".json.bak"))
                path.write_text("{}")
                healed.append(f"Reset {path}")
        return healed


class SelfHealingMode:
    """Main self-healing orchestrator."""
    
    def __init__(self, agents_root: Path):
        self.agents_root = Path(agents_root)
        self.detectors = [PrefixDetector(), SandboxDetector(), MemoryDetector()]
        self.healers = {"sandbox": SandboxHealer(), "memory": MemoryHealer()}
    
    def diagnose(self) -> Dict[str, Any]:
        all_issues = []
        for detector in self.detectors:
            all_issues.extend(detector.detect(self.agents_root))
        return {"issues": all_issues, "count": len(all_issues)}
    
    def heal(self) -> Dict[str, Any]:
        diagnosis = self.diagnose()
        healed = []
        sandbox_issues = [i for i in diagnosis["issues"] if "sandbox" in str(i)]
        if sandbox_issues:
            healed.extend(self.healers["sandbox"].heal(self.agents_root, sandbox_issues))
        memory_issues = [i for i in diagnosis["issues"] if "json" in str(i.get("issue", ""))]
        if memory_issues:
            healed.extend(self.healers["memory"].heal(self.agents_root, memory_issues))
        return {"diagnosed": diagnosis["count"], "healed": healed}
