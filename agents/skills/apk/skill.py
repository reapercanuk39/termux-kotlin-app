"""
APK Analysis Skill
==================

Provides Android APK analysis and manipulation.
Uses apktool, jadx, aapt, etc.
"""

from typing import Any, Dict, List, Optional
from pathlib import Path
from agents.skills.base import Skill, SkillResult


class ApkSkill(Skill):
    """Android APK analysis skill."""
    
    name = "apk"
    description = "Android APK analysis and build skill"
    provides = [
        "decode", "build", "sign", "analyze",
        "extract", "get_manifest", "list_classes"
    ]
    requires_capabilities = ["exec.apk", "filesystem.read", "filesystem.write"]
    
    def get_functions(self) -> Dict[str, callable]:
        return {
            "decode": self.decode,
            "build": self.build,
            "sign": self.sign,
            "analyze": self.analyze,
            "extract": self.extract,
            "get_manifest": self.get_manifest,
            "list_classes": self.list_classes
        }
    
    def decode(self, apk_path: str, output_dir: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Decode APK using apktool."""
        self.log(f"Decoding: {apk_path}")
        
        apk = Path(apk_path)
        if not apk.exists():
            return {"error": f"APK not found: {apk_path}", "decoded": False}
        
        if output_dir is None:
            output_dir = self.sandbox.get_work_path(apk.stem)
        
        result = self.executor.run(
            ["apktool", "d", "-f", "-o", str(output_dir), str(apk)],
            check=False,
            timeout=300
        )
        
        success = result.returncode == 0
        
        return {
            "apk": str(apk),
            "output_dir": str(output_dir),
            "decoded": success,
            "errors": result.stderr if not success else None
        }
    
    def build(self, source_dir: str, output_apk: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Build APK using apktool."""
        self.log(f"Building: {source_dir}")
        
        src = Path(source_dir)
        if not src.exists():
            return {"error": f"Source not found: {source_dir}", "built": False}
        
        if output_apk is None:
            output_apk = self.sandbox.get_output_path(f"{src.name}.apk")
        
        result = self.executor.run(
            ["apktool", "b", "-f", "-o", str(output_apk), str(src)],
            check=False,
            timeout=600
        )
        
        success = result.returncode == 0
        
        return {
            "source_dir": str(src),
            "output_apk": str(output_apk),
            "built": success,
            "errors": result.stderr if not success else None
        }
    
    def sign(self, apk_path: str, keystore: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Sign APK (requires keystore or uses debug key)."""
        self.log(f"Signing: {apk_path}")
        
        apk = Path(apk_path)
        if not apk.exists():
            return {"error": f"APK not found: {apk_path}", "signed": False}
        
        # Align first
        aligned_apk = apk.with_suffix(".aligned.apk")
        result = self.executor.run(
            ["zipalign", "-f", "4", str(apk), str(aligned_apk)],
            check=False
        )
        
        if result.returncode != 0:
            return {"error": "zipalign failed", "signed": False}
        
        # Sign with apksigner (uses debug key if no keystore)
        signed_apk = apk.with_suffix(".signed.apk")
        
        if keystore:
            result = self.executor.run(
                ["apksigner", "sign", "--ks", keystore, "--out", str(signed_apk), str(aligned_apk)],
                check=False
            )
        else:
            # Debug signing - needs debug keystore
            return {
                "error": "Keystore required for signing",
                "signed": False,
                "hint": "Provide keystore path or generate debug keystore"
            }
        
        success = result.returncode == 0
        
        return {
            "apk": str(apk),
            "signed_apk": str(signed_apk),
            "signed": success,
            "errors": result.stderr if not success else None
        }
    
    def analyze(self, apk_path: str, **kwargs) -> Dict[str, Any]:
        """Analyze APK and return basic info."""
        self.log(f"Analyzing: {apk_path}")
        
        apk = Path(apk_path)
        if not apk.exists():
            return {"error": f"APK not found: {apk_path}"}
        
        # Use aapt to get basic info
        result = self.executor.run(
            ["aapt", "dump", "badging", str(apk)],
            check=False
        )
        
        info = {
            "apk": str(apk),
            "size": apk.stat().st_size
        }
        
        if result.stdout:
            for line in result.stdout.split("\n"):
                if line.startswith("package:"):
                    # Parse package info
                    import re
                    match = re.search(r"name='([^']+)'", line)
                    if match:
                        info["package"] = match.group(1)
                    match = re.search(r"versionCode='([^']+)'", line)
                    if match:
                        info["versionCode"] = match.group(1)
                    match = re.search(r"versionName='([^']+)'", line)
                    if match:
                        info["versionName"] = match.group(1)
                elif line.startswith("application-label:"):
                    info["label"] = line.split(":", 1)[1].strip().strip("'")
                elif line.startswith("sdkVersion:"):
                    info["minSdk"] = line.split(":", 1)[1].strip().strip("'")
                elif line.startswith("targetSdkVersion:"):
                    info["targetSdk"] = line.split(":", 1)[1].strip().strip("'")
        
        return info
    
    def extract(self, apk_path: str, output_dir: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """Extract APK contents (simple unzip)."""
        self.log(f"Extracting: {apk_path}")
        
        apk = Path(apk_path)
        if not apk.exists():
            return {"error": f"APK not found: {apk_path}", "extracted": False}
        
        if output_dir is None:
            output_dir = self.sandbox.get_work_path(f"{apk.stem}_extracted")
        
        result = self.executor.run(
            ["unzip", "-o", "-d", str(output_dir), str(apk)],
            check=False
        )
        
        success = result.returncode == 0
        
        return {
            "apk": str(apk),
            "output_dir": str(output_dir),
            "extracted": success
        }
    
    def get_manifest(self, apk_path: str, **kwargs) -> Dict[str, Any]:
        """Extract AndroidManifest.xml."""
        self.log(f"Getting manifest: {apk_path}")
        
        apk = Path(apk_path)
        if not apk.exists():
            return {"error": f"APK not found: {apk_path}"}
        
        result = self.executor.run(
            ["aapt", "dump", "xmltree", str(apk), "AndroidManifest.xml"],
            check=False
        )
        
        return {
            "apk": str(apk),
            "manifest": result.stdout if result.returncode == 0 else None,
            "error": result.stderr if result.returncode != 0 else None
        }
    
    def list_classes(self, apk_path: str, **kwargs) -> Dict[str, Any]:
        """List classes in APK using jadx."""
        self.log(f"Listing classes: {apk_path}")
        
        apk = Path(apk_path)
        if not apk.exists():
            return {"error": f"APK not found: {apk_path}"}
        
        # Use jadx to decompile and list
        output_dir = self.sandbox.get_tmp_path(f"{apk.stem}_jadx")
        
        result = self.executor.run(
            ["jadx", "-d", str(output_dir), str(apk)],
            check=False,
            timeout=600
        )
        
        classes = []
        if Path(output_dir).exists():
            for java_file in Path(output_dir).rglob("*.java"):
                rel_path = java_file.relative_to(output_dir)
                class_name = str(rel_path).replace("/", ".").replace(".java", "")
                classes.append(class_name)
        
        return {
            "apk": str(apk),
            "class_count": len(classes),
            "classes": classes[:100]  # Limit output
        }
    
    def self_test(self) -> SkillResult:
        """Test APK skill."""
        self.log("Running apk skill self-test")
        
        # Check for apktool
        result = self.executor.run(["apktool", "--version"], check=False)
        apktool_ok = result.returncode == 0
        
        # Check for aapt
        result = self.executor.run(["aapt", "version"], check=False)
        aapt_ok = result.returncode == 0
        
        self.log(f"apktool: {apktool_ok}, aapt: {aapt_ok}")
        
        return SkillResult(
            success=apktool_ok or aapt_ok,
            data={
                "apktool_available": apktool_ok,
                "aapt_available": aapt_ok
            },
            logs=self.get_logs()
        )
