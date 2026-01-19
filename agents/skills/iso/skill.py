"""
ISO Skill
=========

Provides ISO image manipulation.
Uses xorriso, isoinfo, binwalk.
"""

from typing import Any, Dict, List, Optional
from pathlib import Path
from agents.skills.base import Skill, SkillResult


class IsoSkill(Skill):
    """ISO image manipulation skill."""
    
    name = "iso"
    description = "ISO image manipulation skill"
    provides = ["extract", "create", "analyze", "list_contents", "get_bootloader_info"]
    requires_capabilities = ["exec.iso", "filesystem.read", "filesystem.write"]
    
    def get_functions(self) -> Dict[str, callable]:
        return {
            "extract": self.extract,
            "create": self.create,
            "analyze": self.analyze,
            "list_contents": self.list_contents,
            "get_bootloader_info": self.get_bootloader_info
        }
    
    def extract(self, iso_path: str, output_dir: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Extract ISO contents."""
        self.log(f"Extracting: {iso_path}")
        
        iso = Path(iso_path)
        if not iso.exists():
            return {"error": f"ISO not found: {iso_path}", "extracted": False}
        
        if output_dir is None:
            output_dir = self.sandbox.get_work_path(iso.stem)
        
        Path(output_dir).mkdir(parents=True, exist_ok=True)
        
        # Use xorriso to extract
        result = self.executor.run(
            ["xorriso", "-osirrox", "on", "-indev", str(iso), 
             "-extract", "/", str(output_dir)],
            check=False,
            timeout=300
        )
        
        success = result.returncode == 0
        
        return {
            "iso": str(iso),
            "output_dir": str(output_dir),
            "extracted": success,
            "errors": result.stderr if not success else None
        }
    
    def create(
        self,
        source_dir: str,
        output_iso: str,
        label: str = "DATA",
        bootable: bool = False,
        **kwargs
    ) -> Dict[str, Any]:
        """Create ISO from directory."""
        self.log(f"Creating ISO: {output_iso}")
        
        src = Path(source_dir)
        if not src.exists():
            return {"error": f"Source not found: {source_dir}", "created": False}
        
        cmd = [
            "xorriso", "-as", "mkisofs",
            "-o", output_iso,
            "-V", label,
            "-J", "-R"  # Joliet and Rock Ridge extensions
        ]
        
        if bootable:
            # Add bootable options (assumes EFI)
            efi_boot = src / "EFI" / "BOOT" / "BOOTX64.EFI"
            if efi_boot.exists():
                cmd.extend([
                    "-e", "EFI/BOOT/BOOTX64.EFI",
                    "-no-emul-boot"
                ])
        
        cmd.append(str(src))
        
        result = self.executor.run(cmd, check=False, timeout=600)
        
        success = result.returncode == 0
        
        return {
            "source_dir": str(src),
            "output_iso": output_iso,
            "label": label,
            "bootable": bootable,
            "created": success,
            "errors": result.stderr if not success else None
        }
    
    def analyze(self, iso_path: str, **kwargs) -> Dict[str, Any]:
        """Analyze ISO structure."""
        self.log(f"Analyzing: {iso_path}")
        
        iso = Path(iso_path)
        if not iso.exists():
            return {"error": f"ISO not found: {iso_path}"}
        
        info = {
            "path": str(iso),
            "size": iso.stat().st_size
        }
        
        # Get volume info
        result = self.executor.run(
            ["isoinfo", "-d", "-i", str(iso)],
            check=False
        )
        
        if result.returncode == 0:
            for line in result.stdout.split("\n"):
                if ":" in line:
                    key, value = line.split(":", 1)
                    key = key.strip().lower().replace(" ", "_")
                    info[key] = value.strip()
        
        # Use binwalk for deeper analysis if available
        result = self.executor.run(
            ["binwalk", "-B", str(iso)],
            check=False
        )
        
        if result.returncode == 0:
            info["binwalk_analysis"] = result.stdout
        
        return info
    
    def list_contents(self, iso_path: str, path: str = "/", **kwargs) -> Dict[str, Any]:
        """List ISO contents."""
        self.log(f"Listing: {iso_path}:{path}")
        
        iso = Path(iso_path)
        if not iso.exists():
            return {"error": f"ISO not found: {iso_path}"}
        
        result = self.executor.run(
            ["isoinfo", "-l", "-i", str(iso)],
            check=False
        )
        
        files = []
        current_dir = ""
        
        if result.returncode == 0:
            for line in result.stdout.split("\n"):
                if line.startswith("Directory listing of"):
                    current_dir = line.split("of")[-1].strip()
                elif line.strip() and not line.startswith("-"):
                    parts = line.split()
                    if len(parts) >= 2:
                        name = parts[-1]
                        if name not in [".", ".."]:
                            files.append({
                                "dir": current_dir,
                                "name": name,
                                "full_path": f"{current_dir}{name}"
                            })
        
        return {
            "iso": str(iso),
            "path": path,
            "count": len(files),
            "files": files[:200]  # Limit output
        }
    
    def get_bootloader_info(self, iso_path: str, **kwargs) -> Dict[str, Any]:
        """Get bootloader information from ISO."""
        self.log(f"Getting bootloader info: {iso_path}")
        
        iso = Path(iso_path)
        if not iso.exists():
            return {"error": f"ISO not found: {iso_path}"}
        
        info = {
            "iso": str(iso),
            "bootable": False,
            "boot_type": None
        }
        
        # Check for El Torito boot record
        result = self.executor.run(
            ["isoinfo", "-d", "-i", str(iso)],
            check=False
        )
        
        if "El Torito" in result.stdout:
            info["bootable"] = True
            info["boot_type"] = "El Torito"
        
        # Extract and check for common bootloaders
        tmp_dir = self.sandbox.get_tmp_path("iso_boot_check")
        Path(tmp_dir).mkdir(parents=True, exist_ok=True)
        
        # Check for GRUB
        result = self.executor.run(
            ["xorriso", "-osirrox", "on", "-indev", str(iso),
             "-extract", "/boot/grub", f"{tmp_dir}/grub"],
            check=False
        )
        if Path(f"{tmp_dir}/grub").exists():
            info["grub_found"] = True
            
            # Read grub.cfg if exists
            grub_cfg = Path(f"{tmp_dir}/grub/grub.cfg")
            if grub_cfg.exists():
                info["grub_cfg"] = grub_cfg.read_text()[:2000]
        
        # Check for syslinux/isolinux
        result = self.executor.run(
            ["xorriso", "-osirrox", "on", "-indev", str(iso),
             "-extract", "/isolinux", f"{tmp_dir}/isolinux"],
            check=False
        )
        if Path(f"{tmp_dir}/isolinux").exists():
            info["isolinux_found"] = True
            
            cfg = Path(f"{tmp_dir}/isolinux/isolinux.cfg")
            if cfg.exists():
                info["isolinux_cfg"] = cfg.read_text()[:2000]
        
        # Check for EFI
        result = self.executor.run(
            ["xorriso", "-osirrox", "on", "-indev", str(iso),
             "-extract", "/EFI", f"{tmp_dir}/EFI"],
            check=False
        )
        if Path(f"{tmp_dir}/EFI").exists():
            info["efi_found"] = True
            efi_files = list(Path(f"{tmp_dir}/EFI").rglob("*"))
            info["efi_files"] = [str(f.relative_to(tmp_dir)) for f in efi_files[:20]]
        
        return info
    
    def self_test(self) -> SkillResult:
        """Test ISO skill."""
        self.log("Running iso skill self-test")
        
        # Check for xorriso
        result = self.executor.run(["xorriso", "--version"], check=False)
        xorriso_ok = result.returncode == 0
        
        # Check for isoinfo
        result = self.executor.run(["isoinfo", "-version"], check=False)
        isoinfo_ok = "isoinfo" in result.stdout.lower() or result.returncode == 0
        
        self.log(f"xorriso: {xorriso_ok}, isoinfo: {isoinfo_ok}")
        
        return SkillResult(
            success=xorriso_ok or isoinfo_ok,
            data={
                "xorriso_available": xorriso_ok,
                "isoinfo_available": isoinfo_ok
            },
            logs=self.get_logs()
        )
