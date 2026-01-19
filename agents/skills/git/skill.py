"""
Git Skill
=========

Provides git version control functions.
"""

from typing import Any, Dict, List, Optional
from pathlib import Path
from agents.skills.base import Skill, SkillResult


class GitSkill(Skill):
    """Git version control skill."""
    
    name = "git"
    description = "Git version control skill"
    provides = [
        "clone", "pull", "push", "commit", "status",
        "diff", "log", "branch", "checkout", "add"
    ]
    requires_capabilities = ["exec.git", "filesystem.read", "filesystem.write"]
    
    def get_functions(self) -> Dict[str, callable]:
        return {
            "clone": self.clone,
            "pull": self.pull,
            "push": self.push,
            "commit": self.commit,
            "status": self.status,
            "diff": self.diff,
            "log": self.log,
            "branch": self.branch,
            "checkout": self.checkout,
            "add": self.add
        }
    
    def clone(self, url: str, dest: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Clone a repository."""
        self.log(f"Cloning: {url}")
        
        cmd = ["git", "clone", "--depth", "1", url]
        if dest:
            cmd.append(dest)
        
        result = self.executor.run(cmd, check=False, timeout=600)
        success = result.returncode == 0
        
        self.log(f"Clone {'succeeded' if success else 'failed'}")
        
        return {
            "url": url,
            "cloned": success,
            "output": result.stdout,
            "errors": result.stderr if not success else None
        }
    
    def pull(self, path: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Pull latest changes."""
        self.log("Pulling latest changes")
        
        cwd = Path(path) if path else None
        result = self.executor.run(
            ["git", "--no-pager", "pull"],
            cwd=cwd,
            check=False
        )
        
        success = result.returncode == 0
        
        return {
            "pulled": success,
            "output": result.stdout,
            "errors": result.stderr if not success else None
        }
    
    def push(self, path: Optional[str] = None, remote: str = "origin", branch: str = "main", **kwargs) -> Dict[str, Any]:
        """Push changes."""
        self.log(f"Pushing to {remote}/{branch}")
        
        cwd = Path(path) if path else None
        result = self.executor.run(
            ["git", "--no-pager", "push", remote, branch],
            cwd=cwd,
            check=False
        )
        
        success = result.returncode == 0
        
        return {
            "pushed": success,
            "remote": remote,
            "branch": branch,
            "output": result.stdout,
            "errors": result.stderr if not success else None
        }
    
    def commit(self, message: str, path: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Commit changes."""
        self.log(f"Committing: {message[:50]}...")
        
        cwd = Path(path) if path else None
        result = self.executor.run(
            ["git", "commit", "-m", message],
            cwd=cwd,
            check=False
        )
        
        success = result.returncode == 0
        
        return {
            "committed": success,
            "message": message,
            "output": result.stdout,
            "errors": result.stderr if not success else None
        }
    
    def status(self, path: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Get repository status."""
        self.log("Getting status")
        
        cwd = Path(path) if path else None
        result = self.executor.run(
            ["git", "--no-pager", "status", "--short"],
            cwd=cwd,
            check=False
        )
        
        files = []
        if result.stdout:
            for line in result.stdout.strip().split("\n"):
                if line:
                    status = line[:2].strip()
                    filename = line[3:].strip()
                    files.append({"status": status, "file": filename})
        
        return {
            "clean": len(files) == 0,
            "files": files,
            "count": len(files)
        }
    
    def diff(self, path: Optional[str] = None, file: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Get diff."""
        self.log("Getting diff")
        
        cwd = Path(path) if path else None
        cmd = ["git", "--no-pager", "diff"]
        if file:
            cmd.append(file)
        
        result = self.executor.run(cmd, cwd=cwd, check=False)
        
        return {
            "has_changes": bool(result.stdout.strip()),
            "diff": result.stdout
        }
    
    def log(self, path: Optional[str] = None, count: int = 10, **kwargs) -> Dict[str, Any]:
        """Get commit log."""
        self.log(f"Getting last {count} commits")
        
        cwd = Path(path) if path else None
        result = self.executor.run(
            ["git", "--no-pager", "log", f"-{count}", "--oneline"],
            cwd=cwd,
            check=False
        )
        
        commits = []
        if result.stdout:
            for line in result.stdout.strip().split("\n"):
                if line:
                    parts = line.split(" ", 1)
                    commits.append({
                        "hash": parts[0],
                        "message": parts[1] if len(parts) > 1 else ""
                    })
        
        return {
            "count": len(commits),
            "commits": commits
        }
    
    def branch(self, path: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """List branches."""
        self.log("Listing branches")
        
        cwd = Path(path) if path else None
        result = self.executor.run(
            ["git", "--no-pager", "branch", "-a"],
            cwd=cwd,
            check=False
        )
        
        branches = []
        current = None
        if result.stdout:
            for line in result.stdout.strip().split("\n"):
                line = line.strip()
                if line.startswith("*"):
                    current = line[2:]
                    branches.append(current)
                elif line:
                    branches.append(line)
        
        return {
            "current": current,
            "branches": branches,
            "count": len(branches)
        }
    
    def checkout(self, ref: str, path: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Checkout branch or commit."""
        self.log(f"Checking out: {ref}")
        
        cwd = Path(path) if path else None
        result = self.executor.run(
            ["git", "checkout", ref],
            cwd=cwd,
            check=False
        )
        
        success = result.returncode == 0
        
        return {
            "checked_out": success,
            "ref": ref,
            "output": result.stdout,
            "errors": result.stderr if not success else None
        }
    
    def add(self, files: str = ".", path: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Stage files."""
        self.log(f"Adding: {files}")
        
        cwd = Path(path) if path else None
        result = self.executor.run(
            ["git", "add", files],
            cwd=cwd,
            check=False
        )
        
        success = result.returncode == 0
        
        return {
            "added": success,
            "files": files
        }
    
    def self_test(self) -> SkillResult:
        """Test git skill."""
        self.log("Running git skill self-test")
        
        result = self.executor.run(["git", "--version"], check=False)
        git_ok = result.returncode == 0
        
        self.log(f"git available: {git_ok}")
        
        return SkillResult(
            success=git_ok,
            data={
                "git_available": git_ok,
                "git_version": result.stdout.strip() if git_ok else None
            },
            logs=self.get_logs()
        )
