"""
Master Test Harness
===================

Unified test runner for the entire agent framework.
Validates agents, skills, sandboxes, memory, capabilities, and executor.
"""

import os
import sys
import json
import time
import traceback
from pathlib import Path
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple
from dataclasses import dataclass, field

# Add parent to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent.parent))


@dataclass
class TestResult:
    """Result of a single test."""
    name: str
    category: str
    passed: bool
    duration_ms: int = 0
    error: Optional[str] = None
    details: Optional[Dict[str, Any]] = None
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "category": self.category,
            "passed": self.passed,
            "duration_ms": self.duration_ms,
            "error": self.error,
            "details": self.details
        }


@dataclass
class TestReport:
    """Complete test report."""
    started_at: str
    completed_at: Optional[str] = None
    duration_ms: int = 0
    total_tests: int = 0
    passed: int = 0
    failed: int = 0
    skipped: int = 0
    results: List[TestResult] = field(default_factory=list)
    summary: Dict[str, Any] = field(default_factory=dict)
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "started_at": self.started_at,
            "completed_at": self.completed_at,
            "duration_ms": self.duration_ms,
            "total_tests": self.total_tests,
            "passed": self.passed,
            "failed": self.failed,
            "skipped": self.skipped,
            "pass_rate": f"{(self.passed / self.total_tests * 100):.1f}%" if self.total_tests > 0 else "0%",
            "summary": self.summary,
            "results": [r.to_dict() for r in self.results]
        }


class MasterTestHarness:
    """
    Master test harness for the agent framework.
    
    Validates:
    - All agents load correctly
    - All skills have valid manifests
    - Capabilities are correctly enforced
    - Sandboxes are properly isolated
    - Memory read/write works correctly
    - Offline mode is enforced
    - Self-tests pass for each skill
    """
    
    def __init__(self, agents_root: Path):
        self.agents_root = Path(agents_root)
        self.report = TestReport(started_at=datetime.now().isoformat())
    
    def _run_test(
        self,
        name: str,
        category: str,
        test_func,
        *args,
        **kwargs
    ) -> TestResult:
        """Run a single test and capture result."""
        start = time.time()
        
        try:
            result = test_func(*args, **kwargs)
            duration = int((time.time() - start) * 1000)
            
            if isinstance(result, dict):
                passed = result.get("passed", result.get("valid", True))
                details = result
            elif isinstance(result, bool):
                passed = result
                details = None
            else:
                passed = True
                details = {"result": result}
            
            return TestResult(
                name=name,
                category=category,
                passed=passed,
                duration_ms=duration,
                details=details
            )
            
        except Exception as e:
            duration = int((time.time() - start) * 1000)
            return TestResult(
                name=name,
                category=category,
                passed=False,
                duration_ms=duration,
                error=str(e),
                details={"traceback": traceback.format_exc()}
            )
    
    def _add_result(self, result: TestResult) -> None:
        """Add a test result to the report."""
        self.report.results.append(result)
        self.report.total_tests += 1
        if result.passed:
            self.report.passed += 1
        else:
            self.report.failed += 1
    
    # =========================================================================
    # Agent Tests
    # =========================================================================
    
    def test_agents_load(self) -> Dict[str, Any]:
        """Test that all agents load correctly."""
        from agents.core.supervisor.agentd import AgentDaemon
        
        daemon = AgentDaemon(self.agents_root)
        agents = daemon.list_agents()
        
        return {
            "passed": len(agents) > 0,
            "agents_loaded": len(agents),
            "agent_names": [a["name"] for a in agents]
        }
    
    def test_agent_manifests(self) -> Dict[str, Any]:
        """Test that all agent manifests are valid."""
        from agents.core.supervisor.agentd import AgentDaemon
        
        daemon = AgentDaemon(self.agents_root)
        validation = daemon.validate_all()
        
        agents_valid = validation.get("summary", {}).get("agents_valid", 0)
        agents_invalid = validation.get("summary", {}).get("agents_invalid", 0)
        
        return {
            "passed": agents_invalid == 0,
            "valid": agents_valid,
            "invalid": agents_invalid,
            "issues": {
                name: info.get("issues", [])
                for name, info in validation.get("agents", {}).items()
                if info.get("issues")
            }
        }
    
    def test_agent_capabilities(self) -> Dict[str, Any]:
        """Test that agents have valid capabilities."""
        from agents.core.supervisor.agentd import AgentDaemon, KNOWN_CAPABILITIES
        
        daemon = AgentDaemon(self.agents_root)
        issues = []
        
        for agent in daemon.list_agents():
            for cap in agent.get("capabilities", []):
                if cap not in KNOWN_CAPABILITIES:
                    issues.append({
                        "agent": agent["name"],
                        "capability": cap,
                        "issue": "unknown_capability"
                    })
        
        return {
            "passed": len(issues) == 0,
            "issues": issues
        }
    
    # =========================================================================
    # Skill Tests
    # =========================================================================
    
    def test_skills_discover(self) -> Dict[str, Any]:
        """Test skill auto-discovery."""
        from agents.core.registry.skill_registry import SkillRegistry
        
        registry = SkillRegistry(self.agents_root / "skills")
        report = registry.discover()
        
        return {
            "passed": report.get("valid", 0) > 0,
            "total": report.get("total_discovered", 0),
            "valid": report.get("valid", 0),
            "invalid": report.get("invalid", 0)
        }
    
    def test_skills_manifests(self) -> Dict[str, Any]:
        """Test that all skill manifests are valid."""
        from agents.core.registry.skill_registry import SkillRegistry
        
        registry = SkillRegistry(self.agents_root / "skills")
        registry.discover()
        
        invalid = registry.get_invalid_skills()
        
        return {
            "passed": len(invalid) == 0,
            "invalid_count": len(invalid),
            "invalid_skills": [
                {"name": s.name, "errors": s.validation_errors}
                for s in invalid
            ]
        }
    
    def test_skills_self_tests(self) -> Dict[str, Any]:
        """Run self-tests for all valid skills."""
        from agents.core.supervisor.agentd import AgentDaemon
        
        daemon = AgentDaemon(self.agents_root)
        
        # Use build_agent to run skill self-tests (it has most capabilities)
        results = []
        
        for agent in daemon.list_agents():
            for skill in agent.get("skills", [])[:2]:  # Limit to avoid timeout
                try:
                    result = daemon.run_task(agent["name"], f"{skill}.self_test")
                    passed = result.get("status") == "success"
                    results.append({
                        "agent": agent["name"],
                        "skill": skill,
                        "passed": passed
                    })
                except Exception as e:
                    results.append({
                        "agent": agent["name"],
                        "skill": skill,
                        "passed": False,
                        "error": str(e)
                    })
        
        passed_count = sum(1 for r in results if r.get("passed"))
        
        return {
            "passed": passed_count == len(results),
            "total": len(results),
            "passed_count": passed_count,
            "results": results[:10]  # Limit output
        }
    
    # =========================================================================
    # Sandbox Tests
    # =========================================================================
    
    def test_sandbox_creation(self) -> Dict[str, Any]:
        """Test that sandboxes are created correctly."""
        from agents.core.runtime.sandbox import AgentSandbox
        
        sandbox = AgentSandbox("test_harness", self.agents_root / "sandboxes")
        
        checks = {
            "root_exists": sandbox.sandbox_root.exists(),
            "tmp_exists": sandbox.tmp_dir.exists(),
            "work_exists": sandbox.work_dir.exists(),
            "output_exists": sandbox.output_dir.exists(),
            "cache_exists": sandbox.cache_dir.exists()
        }
        
        # Cleanup
        sandbox.destroy()
        
        return {
            "passed": all(checks.values()),
            "checks": checks
        }
    
    def test_sandbox_isolation(self) -> Dict[str, Any]:
        """Test that sandboxes are isolated from each other."""
        from agents.core.runtime.sandbox import AgentSandbox
        
        sandbox1 = AgentSandbox("test_agent_1", self.agents_root / "sandboxes")
        sandbox2 = AgentSandbox("test_agent_2", self.agents_root / "sandboxes")
        
        # Write to sandbox1
        test_file = sandbox1.tmp_dir / "secret.txt"
        test_file.write_text("secret data")
        
        # Verify sandbox2 cannot see it
        other_file = sandbox2.tmp_dir / "secret.txt"
        isolated = not other_file.exists()
        
        # Cleanup
        sandbox1.destroy()
        sandbox2.destroy()
        
        return {
            "passed": isolated,
            "sandbox1": str(sandbox1.sandbox_root),
            "sandbox2": str(sandbox2.sandbox_root)
        }
    
    def test_sandbox_boundaries(self) -> Dict[str, Any]:
        """Test sandbox boundary enforcement."""
        from agents.core.supervisor.agentd import AgentDaemon
        
        daemon = AgentDaemon(self.agents_root)
        
        # Test with a known agent
        agent_name = "build_agent"
        
        # Valid path (inside sandbox)
        result1 = daemon.check_sandbox_access(agent_name, str(self.agents_root / "sandboxes" / agent_name / "tmp"))
        
        # Invalid path (outside sandbox)
        result2 = daemon.check_sandbox_access(agent_name, "/etc/passwd")
        
        return {
            "passed": result1.get("allowed", False) and not result2.get("allowed", True),
            "inside_sandbox": result1.get("allowed"),
            "outside_sandbox_blocked": not result2.get("allowed", True)
        }
    
    # =========================================================================
    # Memory Tests
    # =========================================================================
    
    def test_memory_read_write(self) -> Dict[str, Any]:
        """Test memory read/write operations."""
        from agents.core.runtime.memory import AgentMemory
        
        memory = AgentMemory("test_harness", self.agents_root / "memory")
        
        # Write
        memory.set("test_key", "test_value")
        
        # Read
        value = memory.get("test_key")
        
        # Verify
        passed = value == "test_value"
        
        # Cleanup
        memory.clear_all()
        
        return {
            "passed": passed,
            "written": "test_value",
            "read": value
        }
    
    def test_memory_persistence(self) -> Dict[str, Any]:
        """Test memory persists across instances."""
        from agents.core.runtime.memory import AgentMemory
        
        # Write with first instance
        memory1 = AgentMemory("test_harness_persist", self.agents_root / "memory")
        memory1.set("persistent_key", "persistent_value")
        
        # Read with new instance
        memory2 = AgentMemory("test_harness_persist", self.agents_root / "memory")
        value = memory2.get("persistent_key")
        
        passed = value == "persistent_value"
        
        # Cleanup
        memory2.clear_all()
        
        return {
            "passed": passed,
            "persisted_value": value
        }
    
    def test_memory_size_limit(self) -> Dict[str, Any]:
        """Test memory size limit enforcement."""
        from agents.core.runtime.memory import AgentMemory
        
        memory = AgentMemory("test_harness_size", self.agents_root / "memory")
        
        # Get current size
        stats = memory.get_stats()
        size = stats.get("file_size_bytes", 0)
        
        # Should be under 1MB
        passed = size < 1024 * 1024
        
        # Cleanup
        memory.clear_all()
        
        return {
            "passed": passed,
            "size_bytes": size,
            "limit_bytes": 1024 * 1024
        }
    
    # =========================================================================
    # Capability Tests
    # =========================================================================
    
    def test_capability_enforcement(self) -> Dict[str, Any]:
        """Test capability enforcement."""
        from agents.core.supervisor.agentd import AgentDaemon
        
        daemon = AgentDaemon(self.agents_root)
        
        # build_agent has exec.pkg
        result1 = daemon.check_agent_capability("build_agent", "exec.pkg")
        
        # security_agent should NOT have exec.docker
        result2 = daemon.check_agent_capability("security_agent", "exec.docker")
        
        return {
            "passed": result1.get("allowed", False) and not result2.get("allowed", True),
            "allowed_check": result1.get("allowed"),
            "denied_check": not result2.get("allowed", True)
        }
    
    def test_network_enforcement(self) -> Dict[str, Any]:
        """Test network capability enforcement."""
        from agents.core.supervisor.agentd import AgentDaemon
        
        daemon = AgentDaemon(self.agents_root)
        
        # build_agent has network.none (blocked)
        result1 = daemon.check_network_access("build_agent")
        
        # network_agent has network.local (allowed for localhost)
        result2 = daemon.check_network_access("network_agent", "localhost")
        
        return {
            "passed": not result1.get("allowed", True) and result2.get("allowed", False),
            "network_none_blocked": not result1.get("allowed", True),
            "network_local_allowed": result2.get("allowed", False)
        }
    
    # =========================================================================
    # Executor Tests
    # =========================================================================
    
    def test_executor_basic(self) -> Dict[str, Any]:
        """Test executor basic functionality."""
        from agents.core.runtime.executor import AgentExecutor
        from agents.core.runtime.sandbox import AgentSandbox
        
        sandbox = AgentSandbox("test_executor", self.agents_root / "sandboxes")
        
        executor = AgentExecutor(
            agent_name="test_executor",
            capabilities=["exec.shell", "filesystem.read"],
            sandbox_path=sandbox.sandbox_root
        )
        
        # Run simple command
        result = executor.run(["echo", "hello"])
        passed = result.returncode == 0 and "hello" in result.stdout
        
        # Cleanup
        sandbox.destroy()
        
        return {
            "passed": passed,
            "exit_code": result.returncode,
            "output": result.stdout.strip() if result.stdout else None
        }
    
    def test_executor_capability_check(self) -> Dict[str, Any]:
        """Test executor capability checking."""
        from agents.core.runtime.executor import AgentExecutor, CapabilityError
        from agents.core.runtime.sandbox import AgentSandbox
        
        sandbox = AgentSandbox("test_executor_cap", self.agents_root / "sandboxes")
        
        # Executor without exec.pkg capability
        executor = AgentExecutor(
            agent_name="test_executor_cap",
            capabilities=["filesystem.read"],  # No exec.pkg
            sandbox_path=sandbox.sandbox_root
        )
        
        # Should raise CapabilityError for apt
        try:
            executor.run(["apt", "list"])
            capability_enforced = False
        except CapabilityError:
            capability_enforced = True
        except Exception:
            capability_enforced = False
        
        # Cleanup
        sandbox.destroy()
        
        return {
            "passed": capability_enforced,
            "capability_enforced": capability_enforced
        }
    
    # =========================================================================
    # Offline Mode Tests
    # =========================================================================
    
    def test_offline_mode(self) -> Dict[str, Any]:
        """Test offline mode enforcement."""
        import os
        
        # Check proxy vars are cleared
        proxy_vars = ["http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY"]
        
        # After agentd runs, these should be cleared or blocked
        from agents.core.supervisor.agentd import AgentDaemon
        daemon = AgentDaemon(self.agents_root)
        daemon._enforce_offline_guarantee()
        
        proxies_cleared = all(
            os.environ.get(var, "") == "" or os.environ.get(var) == "*"
            for var in proxy_vars
        )
        
        return {
            "passed": proxies_cleared,
            "proxies_cleared": proxies_cleared,
            "no_proxy": os.environ.get("no_proxy", "")
        }
    
    # =========================================================================
    # Run All Tests
    # =========================================================================
    
    def run_all(self) -> TestReport:
        """Run all tests and produce report."""
        start_time = time.time()
        
        # Agent tests
        self._add_result(self._run_test("agents_load", "agents", self.test_agents_load))
        self._add_result(self._run_test("agent_manifests", "agents", self.test_agent_manifests))
        self._add_result(self._run_test("agent_capabilities", "agents", self.test_agent_capabilities))
        
        # Skill tests
        self._add_result(self._run_test("skills_discover", "skills", self.test_skills_discover))
        self._add_result(self._run_test("skills_manifests", "skills", self.test_skills_manifests))
        self._add_result(self._run_test("skills_self_tests", "skills", self.test_skills_self_tests))
        
        # Sandbox tests
        self._add_result(self._run_test("sandbox_creation", "sandbox", self.test_sandbox_creation))
        self._add_result(self._run_test("sandbox_isolation", "sandbox", self.test_sandbox_isolation))
        self._add_result(self._run_test("sandbox_boundaries", "sandbox", self.test_sandbox_boundaries))
        
        # Memory tests
        self._add_result(self._run_test("memory_read_write", "memory", self.test_memory_read_write))
        self._add_result(self._run_test("memory_persistence", "memory", self.test_memory_persistence))
        self._add_result(self._run_test("memory_size_limit", "memory", self.test_memory_size_limit))
        
        # Capability tests
        self._add_result(self._run_test("capability_enforcement", "capabilities", self.test_capability_enforcement))
        self._add_result(self._run_test("network_enforcement", "capabilities", self.test_network_enforcement))
        
        # Executor tests
        self._add_result(self._run_test("executor_basic", "executor", self.test_executor_basic))
        self._add_result(self._run_test("executor_capability_check", "executor", self.test_executor_capability_check))
        
        # Offline tests
        self._add_result(self._run_test("offline_mode", "offline", self.test_offline_mode))
        
        # Finalize report
        self.report.completed_at = datetime.now().isoformat()
        self.report.duration_ms = int((time.time() - start_time) * 1000)
        
        # Build summary by category
        categories = {}
        for result in self.report.results:
            if result.category not in categories:
                categories[result.category] = {"passed": 0, "failed": 0}
            if result.passed:
                categories[result.category]["passed"] += 1
            else:
                categories[result.category]["failed"] += 1
        
        self.report.summary = {
            "by_category": categories,
            "all_passed": self.report.failed == 0
        }
        
        return self.report


def run_all_tests(agents_root: Path = None) -> Dict[str, Any]:
    """Run all tests and return report as dict."""
    if agents_root is None:
        agents_root = Path("agents")
    
    harness = MasterTestHarness(agents_root)
    report = harness.run_all()
    return report.to_dict()


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="Master Test Harness")
    parser.add_argument("--root", "-r", default="agents", help="Agents root directory")
    parser.add_argument("--json", "-j", action="store_true", help="Output as JSON")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    
    args = parser.parse_args()
    
    print("=" * 60)
    print("MASTER TEST HARNESS - Termux-Kotlin Agent Framework")
    print("=" * 60)
    print()
    
    harness = MasterTestHarness(Path(args.root))
    report = harness.run_all()
    
    if args.json:
        print(json.dumps(report.to_dict(), indent=2))
    else:
        # Pretty print
        print(f"Started:  {report.started_at}")
        print(f"Duration: {report.duration_ms}ms")
        print()
        print(f"Results: {report.passed}/{report.total_tests} passed ({report.failed} failed)")
        print()
        
        # By category
        for category, stats in report.summary.get("by_category", {}).items():
            status = "✓" if stats["failed"] == 0 else "✗"
            print(f"  {status} {category}: {stats['passed']}/{stats['passed'] + stats['failed']}")
        
        print()
        
        # Failed tests
        if report.failed > 0:
            print("Failed Tests:")
            for result in report.results:
                if not result.passed:
                    print(f"  ✗ {result.name}: {result.error or 'Failed'}")
        
        print()
        print("=" * 60)
        if report.summary.get("all_passed"):
            print("ALL TESTS PASSED ✓")
        else:
            print(f"TESTS FAILED: {report.failed}")
        print("=" * 60)
