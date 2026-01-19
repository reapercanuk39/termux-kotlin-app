"""
QEMU Skill
==========

Provides QEMU virtual machine operations.
"""

from typing import Any, Dict, List, Optional
from pathlib import Path
from agents.skills.base import Skill, SkillResult


class QemuSkill(Skill):
    """QEMU virtual machine skill."""
    
    name = "qemu"
    description = "QEMU virtual machine skill"
    provides = ["create_image", "run_vm", "list_images", "convert_image", "snapshot"]
    requires_capabilities = ["exec.qemu", "filesystem.read", "filesystem.write"]
    
    def get_functions(self) -> Dict[str, callable]:
        return {
            "create_image": self.create_image,
            "run_vm": self.run_vm,
            "list_images": self.list_images,
            "convert_image": self.convert_image,
            "snapshot": self.snapshot
        }
    
    def create_image(
        self,
        name: str,
        size: str = "10G",
        format: str = "qcow2",
        **kwargs
    ) -> Dict[str, Any]:
        """Create a new disk image."""
        self.log(f"Creating image: {name} ({size}, {format})")
        
        output_path = self.sandbox.get_output_path(f"{name}.{format}")
        
        result = self.executor.run(
            ["qemu-img", "create", "-f", format, str(output_path), size],
            check=False
        )
        
        success = result.returncode == 0
        
        return {
            "name": name,
            "path": str(output_path),
            "size": size,
            "format": format,
            "created": success,
            "errors": result.stderr if not success else None
        }
    
    def run_vm(
        self,
        image: str,
        arch: str = "x86_64",
        memory: str = "512",
        cdrom: Optional[str] = None,
        nographic: bool = True,
        extra_args: Optional[List[str]] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """Run a virtual machine."""
        self.log(f"Starting VM: {image} (arch={arch}, mem={memory}M)")
        
        img_path = Path(image)
        if not img_path.exists():
            return {"error": f"Image not found: {image}", "started": False}
        
        # Build command
        qemu_bin = f"qemu-system-{arch}"
        cmd = [
            qemu_bin,
            "-m", memory,
            "-hda", str(img_path)
        ]
        
        if cdrom:
            cmd.extend(["-cdrom", cdrom])
        
        if nographic:
            cmd.extend(["-nographic"])
        
        if extra_args:
            cmd.extend(extra_args)
        
        # Run with timeout (VMs can run indefinitely, so limit for testing)
        result = self.executor.run(cmd, check=False, timeout=60)
        
        return {
            "image": str(img_path),
            "arch": arch,
            "memory": memory,
            "command": " ".join(cmd),
            "output": result.stdout,
            "exit_code": result.returncode
        }
    
    def list_images(self, path: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """List available disk images."""
        self.log("Listing images")
        
        search_path = Path(path) if path else self.sandbox.output_dir
        
        images = []
        for ext in ["qcow2", "raw", "img", "vmdk", "vdi"]:
            for img in search_path.rglob(f"*.{ext}"):
                # Get image info
                result = self.executor.run(
                    ["qemu-img", "info", "--output=json", str(img)],
                    check=False
                )
                
                info = {
                    "path": str(img),
                    "name": img.name,
                    "size": img.stat().st_size
                }
                
                if result.returncode == 0:
                    import json
                    try:
                        img_info = json.loads(result.stdout)
                        info["format"] = img_info.get("format")
                        info["virtual_size"] = img_info.get("virtual-size")
                    except json.JSONDecodeError:
                        pass
                
                images.append(info)
        
        return {
            "path": str(search_path),
            "count": len(images),
            "images": images
        }
    
    def convert_image(
        self,
        source: str,
        dest: str,
        format: str = "qcow2",
        **kwargs
    ) -> Dict[str, Any]:
        """Convert image format."""
        self.log(f"Converting: {source} -> {dest} ({format})")
        
        src = Path(source)
        if not src.exists():
            return {"error": f"Source not found: {source}", "converted": False}
        
        result = self.executor.run(
            ["qemu-img", "convert", "-O", format, str(src), dest],
            check=False,
            timeout=600
        )
        
        success = result.returncode == 0
        
        return {
            "source": str(src),
            "dest": dest,
            "format": format,
            "converted": success,
            "errors": result.stderr if not success else None
        }
    
    def snapshot(
        self,
        image: str,
        name: str,
        action: str = "create",
        **kwargs
    ) -> Dict[str, Any]:
        """Manage snapshots (create/list/apply/delete)."""
        self.log(f"Snapshot {action}: {name} on {image}")
        
        img = Path(image)
        if not img.exists():
            return {"error": f"Image not found: {image}", "success": False}
        
        if action == "create":
            cmd = ["qemu-img", "snapshot", "-c", name, str(img)]
        elif action == "list":
            cmd = ["qemu-img", "snapshot", "-l", str(img)]
        elif action == "apply":
            cmd = ["qemu-img", "snapshot", "-a", name, str(img)]
        elif action == "delete":
            cmd = ["qemu-img", "snapshot", "-d", name, str(img)]
        else:
            return {"error": f"Unknown action: {action}", "success": False}
        
        result = self.executor.run(cmd, check=False)
        
        success = result.returncode == 0
        
        return {
            "image": str(img),
            "action": action,
            "name": name,
            "success": success,
            "output": result.stdout,
            "errors": result.stderr if not success else None
        }
    
    def self_test(self) -> SkillResult:
        """Test QEMU skill."""
        self.log("Running qemu skill self-test")
        
        # Check for qemu-img
        result = self.executor.run(["qemu-img", "--version"], check=False)
        qemu_img_ok = result.returncode == 0
        
        # Check for qemu-system
        result = self.executor.run(["qemu-system-x86_64", "--version"], check=False)
        qemu_sys_ok = result.returncode == 0
        
        self.log(f"qemu-img: {qemu_img_ok}, qemu-system: {qemu_sys_ok}")
        
        return SkillResult(
            success=qemu_img_ok,
            data={
                "qemu_img_available": qemu_img_ok,
                "qemu_system_available": qemu_sys_ok
            },
            logs=self.get_logs()
        )
