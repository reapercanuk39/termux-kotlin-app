"""
Backup Skill
============

Backup, restore, and snapshot management capabilities.
Creates backups, manages snapshots, exports/imports package lists.
"""

import os
import json
import shutil
import tarfile
from pathlib import Path
from datetime import datetime
from typing import Any, Dict, List, Optional

from ..base import Skill, SkillResult


class BackupSkill(Skill):
    """Backup and restore skill."""
    
    name = "backup"
    description = "Backup, restore, and snapshot management"
    provides = [
        "create_backup",
        "restore_backup",
        "list_backups",
        "delete_backup",
        "export_package_list",
        "import_package_list",
        "create_snapshot",
        "verify_backup"
    ]
    requires_capabilities = ["filesystem.read", "filesystem.write", "exec.compress", "exec.shell"]
    
    def get_functions(self) -> Dict[str, callable]:
        return {
            "create_backup": self.create_backup,
            "restore_backup": self.restore_backup,
            "list_backups": self.list_backups,
            "delete_backup": self.delete_backup,
            "export_package_list": self.export_package_list,
            "import_package_list": self.import_package_list,
            "create_snapshot": self.create_snapshot,
            "verify_backup": self.verify_backup,
            "self_test": self.self_test
        }
    
    def _get_backup_dir(self) -> Path:
        """Get backup directory in sandbox."""
        backup_dir = self.sandbox.output_dir / "backups"
        backup_dir.mkdir(parents=True, exist_ok=True)
        return backup_dir
    
    def _get_timestamp(self) -> str:
        """Get timestamp for backup names."""
        return datetime.now().strftime("%Y%m%d_%H%M%S")
    
    def create_backup(
        self,
        source_path: str,
        backup_name: str = None,
        compress: bool = True
    ) -> Dict[str, Any]:
        """Create a backup of a directory or file."""
        self.log(f"Creating backup of: {source_path}")
        
        source = Path(source_path)
        if not source.exists():
            return {"error": f"Source not found: {source_path}"}
        
        if backup_name is None:
            backup_name = f"{source.name}_{self._get_timestamp()}"
        
        backup_dir = self._get_backup_dir()
        
        if compress:
            backup_file = backup_dir / f"{backup_name}.tar.gz"
            try:
                with tarfile.open(backup_file, "w:gz") as tar:
                    tar.add(source, arcname=source.name)
                
                size = backup_file.stat().st_size
                self.log(f"Backup created: {backup_file} ({size} bytes)")
                
                # Update memory with backup info
                backups = self.memory.get("backups", [])
                backups.append({
                    "name": backup_name,
                    "source": source_path,
                    "path": str(backup_file),
                    "size": size,
                    "created": datetime.now().isoformat(),
                    "compressed": True
                })
                self.memory.set("backups", backups)
                
                return {
                    "status": "success",
                    "backup_name": backup_name,
                    "path": str(backup_file),
                    "size_bytes": size
                }
            except Exception as e:
                return {"error": f"Backup failed: {e}"}
        else:
            backup_dest = backup_dir / backup_name
            try:
                if source.is_dir():
                    shutil.copytree(source, backup_dest)
                else:
                    shutil.copy2(source, backup_dest)
                
                self.log(f"Backup created: {backup_dest}")
                return {
                    "status": "success",
                    "backup_name": backup_name,
                    "path": str(backup_dest)
                }
            except Exception as e:
                return {"error": f"Backup failed: {e}"}
    
    def restore_backup(
        self,
        backup_name: str,
        destination: str = None
    ) -> Dict[str, Any]:
        """Restore a backup."""
        self.log(f"Restoring backup: {backup_name}")
        
        backup_dir = self._get_backup_dir()
        
        # Try compressed first
        backup_file = backup_dir / f"{backup_name}.tar.gz"
        if not backup_file.exists():
            backup_file = backup_dir / backup_name
        
        if not backup_file.exists():
            return {"error": f"Backup not found: {backup_name}"}
        
        if destination is None:
            destination = str(self.sandbox.work_dir / "restored")
        
        dest = Path(destination)
        dest.mkdir(parents=True, exist_ok=True)
        
        try:
            if backup_file.suffix == ".gz" or str(backup_file).endswith(".tar.gz"):
                with tarfile.open(backup_file, "r:gz") as tar:
                    tar.extractall(dest)
            else:
                if backup_file.is_dir():
                    shutil.copytree(backup_file, dest / backup_file.name)
                else:
                    shutil.copy2(backup_file, dest)
            
            self.log(f"Restored to: {dest}")
            return {
                "status": "success",
                "backup_name": backup_name,
                "destination": str(dest)
            }
        except Exception as e:
            return {"error": f"Restore failed: {e}"}
    
    def list_backups(self) -> Dict[str, Any]:
        """List all available backups."""
        self.log("Listing backups")
        
        backup_dir = self._get_backup_dir()
        backups = []
        
        for item in backup_dir.iterdir():
            try:
                stat = item.stat()
                backups.append({
                    "name": item.name,
                    "path": str(item),
                    "size_bytes": stat.st_size,
                    "modified": datetime.fromtimestamp(stat.st_mtime).isoformat(),
                    "is_compressed": item.suffix == ".gz" or str(item).endswith(".tar.gz")
                })
            except:
                pass
        
        # Also get from memory
        memory_backups = self.memory.get("backups", [])
        
        return {
            "count": len(backups),
            "backups": backups,
            "memory_records": len(memory_backups)
        }
    
    def delete_backup(self, backup_name: str) -> Dict[str, Any]:
        """Delete a backup."""
        self.log(f"Deleting backup: {backup_name}")
        
        backup_dir = self._get_backup_dir()
        
        # Try various extensions
        for suffix in [".tar.gz", ".tar", ".zip", ""]:
            backup_file = backup_dir / f"{backup_name}{suffix}"
            if backup_file.exists():
                try:
                    if backup_file.is_dir():
                        shutil.rmtree(backup_file)
                    else:
                        backup_file.unlink()
                    
                    # Remove from memory
                    backups = self.memory.get("backups", [])
                    backups = [b for b in backups if b.get("name") != backup_name]
                    self.memory.set("backups", backups)
                    
                    return {"status": "deleted", "backup_name": backup_name}
                except Exception as e:
                    return {"error": f"Delete failed: {e}"}
        
        return {"error": f"Backup not found: {backup_name}"}
    
    def export_package_list(self, output_file: str = None) -> Dict[str, Any]:
        """Export list of installed packages."""
        self.log("Exporting package list")
        
        if output_file is None:
            output_file = str(self.sandbox.output_dir / f"packages_{self._get_timestamp()}.txt")
        
        try:
            # Run dpkg to get package list
            result = self.executor.run(
                ["dpkg", "--get-selections"],
                check=False
            )
            
            if result.returncode != 0:
                # Fallback to apt list
                result = self.executor.run(
                    ["apt", "list", "--installed"],
                    check=False
                )
            
            output = result.stdout if result.stdout else ""
            packages = [line.split()[0] for line in output.strip().split('\n') if line]
            
            Path(output_file).write_text('\n'.join(packages))
            
            return {
                "status": "success",
                "count": len(packages),
                "path": output_file
            }
        except Exception as e:
            return {"error": f"Export failed: {e}"}
    
    def import_package_list(self, package_file: str, dry_run: bool = True) -> Dict[str, Any]:
        """Import and optionally install packages from a list."""
        self.log(f"Importing package list: {package_file}")
        
        pkg_file = Path(package_file)
        if not pkg_file.exists():
            return {"error": f"Package list not found: {package_file}"}
        
        try:
            packages = [
                line.strip() for line in pkg_file.read_text().split('\n')
                if line.strip() and not line.startswith('#')
            ]
            
            result = {
                "packages": packages,
                "count": len(packages),
                "dry_run": dry_run
            }
            
            if not dry_run:
                # Would install packages here
                result["note"] = "Package installation not implemented in skill"
            
            return result
        except Exception as e:
            return {"error": f"Import failed: {e}"}
    
    def create_snapshot(
        self,
        name: str = None,
        include_home: bool = True,
        include_config: bool = True
    ) -> Dict[str, Any]:
        """Create a full environment snapshot."""
        self.log("Creating snapshot")
        
        if name is None:
            name = f"snapshot_{self._get_timestamp()}"
        
        prefix = Path(os.environ.get("PREFIX", "/data/data/com.termux/files/usr"))
        home = Path(os.environ.get("HOME", "/data/data/com.termux/files/home"))
        
        snapshot_dir = self._get_backup_dir() / name
        snapshot_dir.mkdir(parents=True, exist_ok=True)
        
        created = []
        errors = []
        
        # Export package list
        pkg_result = self.export_package_list(str(snapshot_dir / "packages.txt"))
        if "error" not in pkg_result:
            created.append("packages.txt")
        else:
            errors.append(f"packages: {pkg_result['error']}")
        
        # Backup config
        if include_config:
            etc_dir = prefix / "etc"
            if etc_dir.exists():
                try:
                    with tarfile.open(snapshot_dir / "etc.tar.gz", "w:gz") as tar:
                        tar.add(etc_dir, arcname="etc")
                    created.append("etc.tar.gz")
                except Exception as e:
                    errors.append(f"etc: {e}")
        
        # Backup home (limited)
        if include_home:
            try:
                home_files = list(home.glob(".*"))[:20]  # Limit dotfiles
                with tarfile.open(snapshot_dir / "home_dotfiles.tar.gz", "w:gz") as tar:
                    for f in home_files:
                        if f.is_file() and f.stat().st_size < 1024 * 1024:  # <1MB
                            tar.add(f, arcname=f.name)
                created.append("home_dotfiles.tar.gz")
            except Exception as e:
                errors.append(f"home: {e}")
        
        # Write manifest
        manifest = {
            "name": name,
            "created": datetime.now().isoformat(),
            "files": created,
            "errors": errors
        }
        (snapshot_dir / "manifest.json").write_text(json.dumps(manifest, indent=2))
        
        return {
            "status": "success",
            "snapshot": name,
            "path": str(snapshot_dir),
            "files_created": created,
            "errors": errors if errors else None
        }
    
    def verify_backup(self, backup_name: str) -> Dict[str, Any]:
        """Verify a backup is valid and readable."""
        self.log(f"Verifying backup: {backup_name}")
        
        backup_dir = self._get_backup_dir()
        backup_file = backup_dir / f"{backup_name}.tar.gz"
        
        if not backup_file.exists():
            backup_file = backup_dir / backup_name
        
        if not backup_file.exists():
            return {"error": f"Backup not found: {backup_name}", "valid": False}
        
        try:
            if str(backup_file).endswith(".tar.gz"):
                with tarfile.open(backup_file, "r:gz") as tar:
                    members = tar.getnames()
                return {
                    "valid": True,
                    "backup_name": backup_name,
                    "format": "tar.gz",
                    "files_count": len(members),
                    "size_bytes": backup_file.stat().st_size
                }
            else:
                return {
                    "valid": True,
                    "backup_name": backup_name,
                    "format": "directory" if backup_file.is_dir() else "file",
                    "size_bytes": backup_file.stat().st_size
                }
        except Exception as e:
            return {"valid": False, "error": str(e)}
    
    def self_test(self) -> Dict[str, Any]:
        """Run self-test."""
        self.log("Running backup skill self-test")
        
        tests = []
        
        # Test 1: Create backup
        test_file = self.sandbox.tmp_dir / "test_backup_source.txt"
        test_file.write_text("test content for backup")
        
        result = self.create_backup(str(test_file), "test_backup")
        tests.append({"test": "create_backup", "passed": result.get("status") == "success"})
        
        # Test 2: List backups
        result = self.list_backups()
        tests.append({"test": "list_backups", "passed": "backups" in result})
        
        # Test 3: Verify backup
        result = self.verify_backup("test_backup")
        tests.append({"test": "verify_backup", "passed": result.get("valid", False)})
        
        # Test 4: Delete backup
        result = self.delete_backup("test_backup")
        tests.append({"test": "delete_backup", "passed": result.get("status") == "deleted"})
        
        # Cleanup
        test_file.unlink(missing_ok=True)
        
        all_passed = all(t["passed"] for t in tests)
        return {"status": "passed" if all_passed else "failed", "tests": tests}
