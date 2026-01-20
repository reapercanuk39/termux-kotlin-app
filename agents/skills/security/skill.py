"""
Security Skill
==============

Security scanning and auditing capabilities.
Audits permissions, checks for secrets, verifies file integrity.
"""

import os
import re
import stat
import hashlib
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..base import Skill, SkillResult


class SecuritySkill(Skill):
    """Security scanning and auditing skill."""
    
    name = "security"
    description = "Security scanning and auditing"
    provides = [
        "audit_permissions",
        "check_secrets",
        "verify_integrity",
        "scan_processes",
        "find_world_writable",
        "check_suid",
        "hash_file",
        "compare_hashes"
    ]
    requires_capabilities = ["filesystem.read", "exec.shell", "exec.analyze"]
    
    # Patterns that might indicate secrets
    SECRET_PATTERNS = [
        r'password\s*[=:]\s*["\']?[^\s"\']+',
        r'api[_-]?key\s*[=:]\s*["\']?[^\s"\']+',
        r'secret\s*[=:]\s*["\']?[^\s"\']+',
        r'token\s*[=:]\s*["\']?[^\s"\']+',
        r'-----BEGIN\s+(RSA\s+)?PRIVATE\s+KEY-----',
        r'aws_access_key_id\s*[=:]\s*[A-Z0-9]+',
        r'aws_secret_access_key\s*[=:]\s*[^\s]+',
    ]
    
    def get_functions(self) -> Dict[str, callable]:
        return {
            "audit_permissions": self.audit_permissions,
            "check_secrets": self.check_secrets,
            "verify_integrity": self.verify_integrity,
            "scan_processes": self.scan_processes,
            "find_world_writable": self.find_world_writable,
            "check_suid": self.check_suid,
            "hash_file": self.hash_file,
            "compare_hashes": self.compare_hashes,
            "self_test": self.self_test
        }
    
    def audit_permissions(self, path: str = None) -> Dict[str, Any]:
        """Audit file permissions in a directory."""
        self.log(f"Auditing permissions: {path or 'PREFIX'}")
        
        if path is None:
            path = os.environ.get("PREFIX", "/data/data/com.termux/files/usr")
        
        target = Path(path)
        if not target.exists():
            return {"error": f"Path not found: {path}", "issues": []}
        
        issues = []
        scanned = 0
        
        try:
            for item in target.rglob("*"):
                if scanned > 1000:  # Limit scan
                    break
                scanned += 1
                
                try:
                    mode = item.stat().st_mode
                    
                    # Check for world-writable
                    if mode & stat.S_IWOTH:
                        issues.append({
                            "path": str(item),
                            "issue": "world_writable",
                            "mode": oct(mode)
                        })
                    
                    # Check for SUID/SGID
                    if mode & (stat.S_ISUID | stat.S_ISGID):
                        issues.append({
                            "path": str(item),
                            "issue": "suid_sgid",
                            "mode": oct(mode)
                        })
                        
                except (PermissionError, OSError):
                    pass
                    
        except Exception as e:
            self.log(f"Error scanning: {e}")
        
        return {
            "scanned": scanned,
            "issues_found": len(issues),
            "issues": issues[:50]  # Limit output
        }
    
    def check_secrets(self, path: str = None, extensions: List[str] = None) -> Dict[str, Any]:
        """Scan files for exposed secrets or credentials."""
        self.log(f"Checking for secrets: {path or 'sandbox'}")
        
        if path is None:
            path = str(self.sandbox.work_dir)
        
        if extensions is None:
            extensions = [".txt", ".cfg", ".conf", ".ini", ".env", ".yml", ".yaml", ".json", ".sh"]
        
        target = Path(path)
        if not target.exists():
            return {"error": f"Path not found: {path}", "findings": []}
        
        findings = []
        scanned = 0
        
        patterns = [re.compile(p, re.IGNORECASE) for p in self.SECRET_PATTERNS]
        
        try:
            items = [target] if target.is_file() else list(target.rglob("*"))
            
            for item in items:
                if scanned > 500:
                    break
                    
                if not item.is_file():
                    continue
                    
                if extensions and item.suffix.lower() not in extensions:
                    continue
                
                scanned += 1
                
                try:
                    content = item.read_text(errors='ignore')[:10000]
                    
                    for pattern in patterns:
                        matches = pattern.findall(content)
                        if matches:
                            findings.append({
                                "file": str(item),
                                "pattern": pattern.pattern[:30],
                                "count": len(matches)
                            })
                            break
                            
                except (PermissionError, UnicodeDecodeError, OSError):
                    pass
                    
        except Exception as e:
            self.log(f"Error scanning: {e}")
        
        return {
            "scanned": scanned,
            "findings_count": len(findings),
            "findings": findings[:20]
        }
    
    def verify_integrity(self, manifest_path: str = None) -> Dict[str, Any]:
        """Verify file checksums against a manifest."""
        self.log("Verifying integrity")
        
        if manifest_path is None:
            manifest_path = str(self.sandbox.cache_dir / "integrity_manifest.json")
        
        manifest_file = Path(manifest_path)
        
        if not manifest_file.exists():
            # Create initial manifest
            return self._create_integrity_manifest(manifest_path)
        
        import json
        try:
            manifest = json.loads(manifest_file.read_text())
        except Exception as e:
            return {"error": f"Failed to load manifest: {e}"}
        
        results = {
            "verified": 0,
            "modified": [],
            "missing": [],
            "errors": []
        }
        
        for file_path, expected_hash in manifest.get("files", {}).items():
            path = Path(file_path)
            if not path.exists():
                results["missing"].append(file_path)
                continue
            
            try:
                actual_hash = self._hash_file(path)
                if actual_hash == expected_hash:
                    results["verified"] += 1
                else:
                    results["modified"].append({
                        "path": file_path,
                        "expected": expected_hash[:16],
                        "actual": actual_hash[:16]
                    })
            except Exception as e:
                results["errors"].append({"path": file_path, "error": str(e)})
        
        return results
    
    def _create_integrity_manifest(self, manifest_path: str) -> Dict[str, Any]:
        """Create initial integrity manifest."""
        import json
        
        prefix = Path(os.environ.get("PREFIX", "/data/data/com.termux/files/usr"))
        bin_dir = prefix / "bin"
        
        manifest = {"files": {}, "created": str(Path(manifest_path))}
        count = 0
        
        if bin_dir.exists():
            for item in list(bin_dir.iterdir())[:100]:
                if item.is_file():
                    try:
                        manifest["files"][str(item)] = self._hash_file(item)
                        count += 1
                    except:
                        pass
        
        Path(manifest_path).parent.mkdir(parents=True, exist_ok=True)
        Path(manifest_path).write_text(json.dumps(manifest, indent=2))
        
        return {"status": "manifest_created", "files_indexed": count, "path": manifest_path}
    
    def _hash_file(self, path: Path) -> str:
        """Calculate SHA256 hash of a file."""
        sha256 = hashlib.sha256()
        with open(path, 'rb') as f:
            for chunk in iter(lambda: f.read(8192), b''):
                sha256.update(chunk)
        return sha256.hexdigest()
    
    def scan_processes(self) -> Dict[str, Any]:
        """Check running processes."""
        self.log("Scanning processes")
        
        try:
            result = self.executor.run(["ps", "aux"], check=False)
            lines = result.stdout.strip().split('\n') if result.stdout else []
            
            processes = []
            for line in lines[1:20]:  # Skip header, limit output
                parts = line.split(None, 10)
                if len(parts) >= 11:
                    processes.append({
                        "user": parts[0],
                        "pid": parts[1],
                        "cpu": parts[2],
                        "mem": parts[3],
                        "command": parts[10][:50]
                    })
            
            return {"count": len(processes), "processes": processes}
            
        except Exception as e:
            return {"error": str(e), "processes": []}
    
    def find_world_writable(self, path: str = None) -> Dict[str, Any]:
        """Find world-writable files."""
        self.log(f"Finding world-writable files: {path or 'PREFIX'}")
        
        if path is None:
            path = os.environ.get("PREFIX", "/data/data/com.termux/files/usr")
        
        target = Path(path)
        world_writable = []
        
        try:
            for item in list(target.rglob("*"))[:500]:
                try:
                    if item.stat().st_mode & stat.S_IWOTH:
                        world_writable.append(str(item))
                except:
                    pass
        except Exception as e:
            return {"error": str(e)}
        
        return {"count": len(world_writable), "files": world_writable[:50]}
    
    def check_suid(self, path: str = None) -> Dict[str, Any]:
        """Check for SUID/SGID files."""
        self.log(f"Checking SUID/SGID: {path or 'PREFIX'}")
        
        if path is None:
            path = os.environ.get("PREFIX", "/data/data/com.termux/files/usr")
        
        target = Path(path)
        suid_files = []
        
        try:
            for item in list(target.rglob("*"))[:500]:
                try:
                    mode = item.stat().st_mode
                    if mode & (stat.S_ISUID | stat.S_ISGID):
                        suid_files.append({
                            "path": str(item),
                            "mode": oct(mode),
                            "suid": bool(mode & stat.S_ISUID),
                            "sgid": bool(mode & stat.S_ISGID)
                        })
                except:
                    pass
        except Exception as e:
            return {"error": str(e)}
        
        return {"count": len(suid_files), "files": suid_files}
    
    def hash_file(self, path: str) -> Dict[str, Any]:
        """Calculate hash of a file."""
        self.log(f"Hashing file: {path}")
        
        file_path = Path(path)
        if not file_path.exists():
            return {"error": f"File not found: {path}"}
        
        try:
            file_hash = self._hash_file(file_path)
            return {
                "path": path,
                "algorithm": "sha256",
                "hash": file_hash,
                "size": file_path.stat().st_size
            }
        except Exception as e:
            return {"error": str(e)}
    
    def compare_hashes(self, path1: str, path2: str) -> Dict[str, Any]:
        """Compare hashes of two files."""
        self.log(f"Comparing: {path1} vs {path2}")
        
        hash1 = self.hash_file(path1)
        hash2 = self.hash_file(path2)
        
        if "error" in hash1 or "error" in hash2:
            return {"error": "Hash calculation failed", "details": [hash1, hash2]}
        
        return {
            "match": hash1["hash"] == hash2["hash"],
            "file1": {"path": path1, "hash": hash1["hash"][:16]},
            "file2": {"path": path2, "hash": hash2["hash"][:16]}
        }
    
    def self_test(self) -> Dict[str, Any]:
        """Run self-test."""
        self.log("Running security skill self-test")
        
        tests = []
        
        # Test 1: Hash a known string
        test_file = self.sandbox.tmp_dir / "test_hash.txt"
        test_file.write_text("test content")
        result = self.hash_file(str(test_file))
        tests.append({"test": "hash_file", "passed": "hash" in result})
        test_file.unlink()
        
        # Test 2: Check processes
        result = self.scan_processes()
        tests.append({"test": "scan_processes", "passed": "processes" in result})
        
        all_passed = all(t["passed"] for t in tests)
        return {"status": "passed" if all_passed else "failed", "tests": tests}
