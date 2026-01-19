"""
Filesystem Skill
================

Provides filesystem operations with capability enforcement.
"""

import os
import shutil
from typing import Any, Dict, List, Optional
from pathlib import Path
from agents.skills.base import Skill, SkillResult


class FilesystemSkill(Skill):
    """Filesystem operations skill."""
    
    name = "fs"
    description = "Filesystem operations skill"
    provides = [
        "list_dir", "read_file", "write_file", "copy",
        "move", "delete", "exists", "get_info", "find", "grep"
    ]
    requires_capabilities = ["filesystem.read"]
    
    def get_functions(self) -> Dict[str, callable]:
        return {
            "list_dir": self.list_dir,
            "read_file": self.read_file,
            "write_file": self.write_file,
            "copy": self.copy,
            "move": self.move,
            "delete": self.delete,
            "exists": self.exists,
            "get_info": self.get_info,
            "find": self.find,
            "grep": self.grep
        }
    
    def list_dir(self, path: str = ".", **kwargs) -> Dict[str, Any]:
        """List directory contents."""
        self.log(f"Listing: {path}")
        
        p = Path(path)
        if not p.exists():
            return {"error": f"Path not found: {path}", "items": []}
        
        if not p.is_dir():
            return {"error": f"Not a directory: {path}", "items": []}
        
        items = []
        for item in sorted(p.iterdir()):
            items.append({
                "name": item.name,
                "type": "dir" if item.is_dir() else "file",
                "size": item.stat().st_size if item.is_file() else 0
            })
        
        return {
            "path": str(p.absolute()),
            "count": len(items),
            "items": items
        }
    
    def read_file(self, path: str, max_size: int = 1024 * 1024, **kwargs) -> Dict[str, Any]:
        """Read file contents."""
        self.log(f"Reading: {path}")
        
        p = Path(path)
        if not p.exists():
            return {"error": f"File not found: {path}", "content": None}
        
        if not p.is_file():
            return {"error": f"Not a file: {path}", "content": None}
        
        size = p.stat().st_size
        if size > max_size:
            return {
                "error": f"File too large: {size} bytes (max {max_size})",
                "content": None
            }
        
        try:
            content = p.read_text()
            return {
                "path": str(p.absolute()),
                "size": size,
                "content": content
            }
        except UnicodeDecodeError:
            return {
                "error": "Binary file, cannot read as text",
                "content": None
            }
    
    def write_file(self, path: str, content: str, **kwargs) -> Dict[str, Any]:
        """Write content to file."""
        # Check write capability
        if not self.executor.check_capability("filesystem.write"):
            return {"error": "filesystem.write capability required", "written": False}
        
        self.log(f"Writing: {path}")
        
        p = Path(path)
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content)
        
        return {
            "path": str(p.absolute()),
            "written": True,
            "size": len(content)
        }
    
    def copy(self, src: str, dest: str, **kwargs) -> Dict[str, Any]:
        """Copy file or directory."""
        if not self.executor.check_capability("filesystem.write"):
            return {"error": "filesystem.write capability required", "copied": False}
        
        self.log(f"Copying: {src} -> {dest}")
        
        src_p = Path(src)
        dest_p = Path(dest)
        
        if not src_p.exists():
            return {"error": f"Source not found: {src}", "copied": False}
        
        try:
            if src_p.is_dir():
                shutil.copytree(src_p, dest_p)
            else:
                dest_p.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src_p, dest_p)
            
            return {
                "src": str(src_p.absolute()),
                "dest": str(dest_p.absolute()),
                "copied": True
            }
        except Exception as e:
            return {"error": str(e), "copied": False}
    
    def move(self, src: str, dest: str, **kwargs) -> Dict[str, Any]:
        """Move file or directory."""
        if not self.executor.check_capability("filesystem.write"):
            return {"error": "filesystem.write capability required", "moved": False}
        
        self.log(f"Moving: {src} -> {dest}")
        
        src_p = Path(src)
        dest_p = Path(dest)
        
        if not src_p.exists():
            return {"error": f"Source not found: {src}", "moved": False}
        
        try:
            dest_p.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(src_p, dest_p)
            
            return {
                "src": str(src_p.absolute()),
                "dest": str(dest_p.absolute()),
                "moved": True
            }
        except Exception as e:
            return {"error": str(e), "moved": False}
    
    def delete(self, path: str, **kwargs) -> Dict[str, Any]:
        """Delete file or directory."""
        if not self.executor.check_capability("filesystem.delete"):
            return {"error": "filesystem.delete capability required", "deleted": False}
        
        self.log(f"Deleting: {path}")
        
        p = Path(path)
        
        if not p.exists():
            return {"error": f"Path not found: {path}", "deleted": False}
        
        try:
            if p.is_dir():
                shutil.rmtree(p)
            else:
                p.unlink()
            
            return {
                "path": str(p.absolute()),
                "deleted": True
            }
        except Exception as e:
            return {"error": str(e), "deleted": False}
    
    def exists(self, path: str, **kwargs) -> Dict[str, Any]:
        """Check if path exists."""
        p = Path(path)
        
        return {
            "path": str(p.absolute()),
            "exists": p.exists(),
            "is_file": p.is_file() if p.exists() else False,
            "is_dir": p.is_dir() if p.exists() else False
        }
    
    def get_info(self, path: str, **kwargs) -> Dict[str, Any]:
        """Get file/directory information."""
        self.log(f"Getting info: {path}")
        
        p = Path(path)
        
        if not p.exists():
            return {"error": f"Path not found: {path}"}
        
        stat = p.stat()
        
        return {
            "path": str(p.absolute()),
            "name": p.name,
            "type": "dir" if p.is_dir() else "file",
            "size": stat.st_size,
            "mode": oct(stat.st_mode),
            "uid": stat.st_uid,
            "gid": stat.st_gid,
            "mtime": stat.st_mtime,
            "ctime": stat.st_ctime
        }
    
    def find(self, path: str = ".", pattern: str = "*", **kwargs) -> Dict[str, Any]:
        """Find files matching pattern."""
        self.log(f"Finding: {pattern} in {path}")
        
        p = Path(path)
        
        if not p.exists():
            return {"error": f"Path not found: {path}", "files": []}
        
        files = list(p.rglob(pattern))[:1000]  # Limit results
        
        return {
            "path": str(p.absolute()),
            "pattern": pattern,
            "count": len(files),
            "files": [str(f) for f in files]
        }
    
    def grep(self, pattern: str, path: str = ".", **kwargs) -> Dict[str, Any]:
        """Search for pattern in files."""
        self.log(f"Grepping: {pattern} in {path}")
        
        result = self.executor.run(
            ["grep", "-rn", pattern, path],
            check=False,
            timeout=60
        )
        
        matches = []
        if result.stdout:
            for line in result.stdout.strip().split("\n")[:100]:  # Limit
                if ":" in line:
                    parts = line.split(":", 2)
                    if len(parts) >= 3:
                        matches.append({
                            "file": parts[0],
                            "line": int(parts[1]) if parts[1].isdigit() else 0,
                            "text": parts[2]
                        })
        
        return {
            "pattern": pattern,
            "path": path,
            "count": len(matches),
            "matches": matches
        }
    
    def self_test(self) -> SkillResult:
        """Test filesystem skill."""
        self.log("Running fs skill self-test")
        
        # Test basic operations
        test_path = self.sandbox.get_tmp_path("fs_test.txt")
        
        try:
            test_path.write_text("test")
            content = test_path.read_text()
            test_path.unlink()
            success = content == "test"
        except Exception as e:
            self.log(f"Self-test failed: {e}")
            success = False
        
        return SkillResult(
            success=success,
            data={"filesystem_working": success},
            logs=self.get_logs()
        )
