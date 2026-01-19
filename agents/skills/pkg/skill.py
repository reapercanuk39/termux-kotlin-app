"""
Package Manager Skill
=====================

Provides package management functions for Termux-Kotlin.
Uses pkg/apt/dpkg commands.
"""

from typing import Any, Dict, List, Optional
from agents.skills.base import Skill, SkillResult


class PkgSkill(Skill):
    """Package management skill."""
    
    name = "pkg"
    description = "Package manager skill for Termux-Kotlin"
    provides = [
        "install_package",
        "remove_package",
        "update_packages",
        "upgrade_packages",
        "search_packages",
        "list_installed",
        "get_package_info",
        "clean_cache"
    ]
    requires_capabilities = ["exec.pkg", "filesystem.read", "filesystem.write"]
    
    def get_functions(self) -> Dict[str, callable]:
        return {
            "install_package": self.install_package,
            "remove_package": self.remove_package,
            "update_packages": self.update_packages,
            "upgrade_packages": self.upgrade_packages,
            "search_packages": self.search_packages,
            "list_installed": self.list_installed,
            "get_package_info": self.get_package_info,
            "clean_cache": self.clean_cache
        }
    
    def install_package(self, package: str, **kwargs) -> Dict[str, Any]:
        """Install a package."""
        self.log(f"Installing package: {package}")
        
        result = self.executor.run(
            ["pkg", "install", "-y", package],
            check=False
        )
        
        success = result.returncode == 0
        self.log(f"Install {'succeeded' if success else 'failed'}")
        
        return {
            "package": package,
            "installed": success,
            "output": result.stdout,
            "errors": result.stderr if not success else None
        }
    
    def remove_package(self, package: str, **kwargs) -> Dict[str, Any]:
        """Remove a package."""
        self.log(f"Removing package: {package}")
        
        result = self.executor.run(
            ["pkg", "uninstall", "-y", package],
            check=False
        )
        
        success = result.returncode == 0
        self.log(f"Remove {'succeeded' if success else 'failed'}")
        
        return {
            "package": package,
            "removed": success,
            "output": result.stdout,
            "errors": result.stderr if not success else None
        }
    
    def update_packages(self, **kwargs) -> Dict[str, Any]:
        """Update package lists."""
        self.log("Updating package lists")
        
        result = self.executor.run(
            ["pkg", "update", "-y"],
            check=False,
            timeout=600
        )
        
        success = result.returncode == 0
        self.log(f"Update {'succeeded' if success else 'failed'}")
        
        return {
            "updated": success,
            "output": result.stdout,
            "errors": result.stderr if not success else None
        }
    
    def upgrade_packages(self, **kwargs) -> Dict[str, Any]:
        """Upgrade all packages."""
        self.log("Upgrading all packages")
        
        result = self.executor.run(
            ["pkg", "upgrade", "-y"],
            check=False,
            timeout=1800
        )
        
        success = result.returncode == 0
        self.log(f"Upgrade {'succeeded' if success else 'failed'}")
        
        return {
            "upgraded": success,
            "output": result.stdout,
            "errors": result.stderr if not success else None
        }
    
    def search_packages(self, query: str, **kwargs) -> Dict[str, Any]:
        """Search for packages."""
        self.log(f"Searching for: {query}")
        
        result = self.executor.run(
            ["pkg", "search", query],
            check=False
        )
        
        packages = []
        if result.stdout:
            for line in result.stdout.strip().split("\n"):
                if line and "/" in line:
                    parts = line.split("/")
                    if len(parts) >= 2:
                        pkg_info = parts[1].split()
                        if pkg_info:
                            packages.append({
                                "name": pkg_info[0],
                                "description": " ".join(pkg_info[1:]) if len(pkg_info) > 1 else ""
                            })
        
        self.log(f"Found {len(packages)} packages")
        
        return {
            "query": query,
            "count": len(packages),
            "packages": packages
        }
    
    def list_installed(self, **kwargs) -> Dict[str, Any]:
        """List installed packages."""
        self.log("Listing installed packages")
        
        result = self.executor.run(
            ["dpkg", "-l"],
            check=False
        )
        
        packages = []
        if result.stdout:
            for line in result.stdout.strip().split("\n"):
                if line.startswith("ii"):
                    parts = line.split()
                    if len(parts) >= 3:
                        packages.append({
                            "name": parts[1],
                            "version": parts[2]
                        })
        
        self.log(f"Found {len(packages)} installed packages")
        
        return {
            "count": len(packages),
            "packages": packages
        }
    
    def get_package_info(self, package: str, **kwargs) -> Dict[str, Any]:
        """Get package information."""
        self.log(f"Getting info for: {package}")
        
        result = self.executor.run(
            ["apt-cache", "show", package],
            check=False
        )
        
        info = {}
        if result.stdout:
            current_key = None
            for line in result.stdout.strip().split("\n"):
                if ": " in line and not line.startswith(" "):
                    key, value = line.split(": ", 1)
                    info[key.lower()] = value
                    current_key = key.lower()
                elif current_key and line.startswith(" "):
                    info[current_key] += "\n" + line
        
        return {
            "package": package,
            "found": bool(info),
            "info": info
        }
    
    def clean_cache(self, **kwargs) -> Dict[str, Any]:
        """Clean package cache."""
        self.log("Cleaning package cache")
        
        result = self.executor.run(
            ["pkg", "clean"],
            check=False
        )
        
        success = result.returncode == 0
        self.log(f"Clean {'succeeded' if success else 'failed'}")
        
        return {
            "cleaned": success,
            "output": result.stdout
        }
    
    def self_test(self) -> SkillResult:
        """Test package skill."""
        self.log("Running pkg skill self-test")
        
        # Test dpkg -l
        result = self.executor.run(["dpkg", "--version"], check=False)
        dpkg_ok = result.returncode == 0
        
        self.log(f"dpkg available: {dpkg_ok}")
        
        return SkillResult(
            success=dpkg_ok,
            data={
                "dpkg_available": dpkg_ok,
                "dpkg_version": result.stdout.split("\n")[0] if dpkg_ok else None
            },
            logs=self.get_logs()
        )
