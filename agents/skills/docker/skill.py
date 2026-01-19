"""
Docker Skill
============

Provides Docker/Podman container management.
"""

from typing import Any, Dict, List, Optional
from pathlib import Path
from agents.skills.base import Skill, SkillResult


class DockerSkill(Skill):
    """Docker/Podman container management skill."""
    
    name = "docker"
    description = "Docker/Podman container management skill"
    provides = [
        "list_containers", "list_images", "run_container",
        "stop_container", "pull_image", "build_image",
        "exec_command", "logs"
    ]
    requires_capabilities = ["exec.docker", "filesystem.read", "filesystem.write"]
    
    def __init__(self, executor, sandbox, memory):
        super().__init__(executor, sandbox, memory)
        # Detect docker or podman
        self._runtime = self._detect_runtime()
    
    def _detect_runtime(self) -> str:
        """Detect available container runtime."""
        # Try docker first
        if self.executor.which("docker"):
            return "docker"
        # Fall back to podman
        if self.executor.which("podman"):
            return "podman"
        return "docker"  # Default, will error if not available
    
    def get_functions(self) -> Dict[str, callable]:
        return {
            "list_containers": self.list_containers,
            "list_images": self.list_images,
            "run_container": self.run_container,
            "stop_container": self.stop_container,
            "pull_image": self.pull_image,
            "build_image": self.build_image,
            "exec_command": self.exec_command,
            "logs": self.container_logs
        }
    
    def list_containers(self, all: bool = True, **kwargs) -> Dict[str, Any]:
        """List containers."""
        self.log(f"Listing containers (all={all})")
        
        cmd = [self._runtime, "ps", "--format", "{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}"]
        if all:
            cmd.insert(2, "-a")
        
        result = self.executor.run(cmd, check=False)
        
        containers = []
        if result.stdout:
            for line in result.stdout.strip().split("\n"):
                if line:
                    parts = line.split("\t")
                    if len(parts) >= 4:
                        containers.append({
                            "id": parts[0],
                            "name": parts[1],
                            "image": parts[2],
                            "status": parts[3]
                        })
        
        return {
            "runtime": self._runtime,
            "count": len(containers),
            "containers": containers
        }
    
    def list_images(self, **kwargs) -> Dict[str, Any]:
        """List images."""
        self.log("Listing images")
        
        result = self.executor.run(
            [self._runtime, "images", "--format", "{{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.Size}}"],
            check=False
        )
        
        images = []
        if result.stdout:
            for line in result.stdout.strip().split("\n"):
                if line:
                    parts = line.split("\t")
                    if len(parts) >= 4:
                        images.append({
                            "repository": parts[0],
                            "tag": parts[1],
                            "id": parts[2],
                            "size": parts[3]
                        })
        
        return {
            "runtime": self._runtime,
            "count": len(images),
            "images": images
        }
    
    def run_container(
        self,
        image: str,
        name: Optional[str] = None,
        detach: bool = True,
        ports: Optional[List[str]] = None,
        volumes: Optional[List[str]] = None,
        env: Optional[Dict[str, str]] = None,
        command: Optional[str] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """Run a container."""
        self.log(f"Running container: {image}")
        
        cmd = [self._runtime, "run"]
        
        if detach:
            cmd.append("-d")
        
        if name:
            cmd.extend(["--name", name])
        
        if ports:
            for port in ports:
                cmd.extend(["-p", port])
        
        if volumes:
            for vol in volumes:
                cmd.extend(["-v", vol])
        
        if env:
            for key, value in env.items():
                cmd.extend(["-e", f"{key}={value}"])
        
        cmd.append(image)
        
        if command:
            cmd.extend(command.split())
        
        result = self.executor.run(cmd, check=False, timeout=300)
        
        success = result.returncode == 0
        container_id = result.stdout.strip()[:12] if success else None
        
        return {
            "image": image,
            "name": name,
            "container_id": container_id,
            "started": success,
            "errors": result.stderr if not success else None
        }
    
    def stop_container(self, container: str, timeout: int = 10, **kwargs) -> Dict[str, Any]:
        """Stop a container."""
        self.log(f"Stopping container: {container}")
        
        result = self.executor.run(
            [self._runtime, "stop", "-t", str(timeout), container],
            check=False
        )
        
        success = result.returncode == 0
        
        return {
            "container": container,
            "stopped": success,
            "errors": result.stderr if not success else None
        }
    
    def pull_image(self, image: str, **kwargs) -> Dict[str, Any]:
        """Pull an image."""
        self.log(f"Pulling image: {image}")
        
        result = self.executor.run(
            [self._runtime, "pull", image],
            check=False,
            timeout=600
        )
        
        success = result.returncode == 0
        
        return {
            "image": image,
            "pulled": success,
            "output": result.stdout,
            "errors": result.stderr if not success else None
        }
    
    def build_image(
        self,
        path: str,
        tag: str,
        dockerfile: str = "Dockerfile",
        **kwargs
    ) -> Dict[str, Any]:
        """Build an image."""
        self.log(f"Building image: {tag}")
        
        build_path = Path(path)
        if not build_path.exists():
            return {"error": f"Path not found: {path}", "built": False}
        
        result = self.executor.run(
            [self._runtime, "build", "-t", tag, "-f", dockerfile, str(build_path)],
            check=False,
            timeout=1800
        )
        
        success = result.returncode == 0
        
        return {
            "path": str(build_path),
            "tag": tag,
            "built": success,
            "output": result.stdout[-2000:] if result.stdout else None,
            "errors": result.stderr if not success else None
        }
    
    def exec_command(
        self,
        container: str,
        command: str,
        interactive: bool = False,
        **kwargs
    ) -> Dict[str, Any]:
        """Execute command in container."""
        self.log(f"Executing in {container}: {command}")
        
        cmd = [self._runtime, "exec"]
        if interactive:
            cmd.append("-it")
        cmd.append(container)
        cmd.extend(command.split())
        
        result = self.executor.run(cmd, check=False, timeout=300)
        
        return {
            "container": container,
            "command": command,
            "exit_code": result.returncode,
            "stdout": result.stdout,
            "stderr": result.stderr
        }
    
    def container_logs(
        self,
        container: str,
        tail: int = 100,
        **kwargs
    ) -> Dict[str, Any]:
        """Get container logs."""
        self.log(f"Getting logs for: {container}")
        
        result = self.executor.run(
            [self._runtime, "logs", "--tail", str(tail), container],
            check=False
        )
        
        return {
            "container": container,
            "logs": result.stdout,
            "errors": result.stderr
        }
    
    def self_test(self) -> SkillResult:
        """Test docker skill."""
        self.log("Running docker skill self-test")
        
        # Check for docker
        result = self.executor.run(["docker", "--version"], check=False)
        docker_ok = result.returncode == 0
        
        # Check for podman
        result = self.executor.run(["podman", "--version"], check=False)
        podman_ok = result.returncode == 0
        
        self.log(f"docker: {docker_ok}, podman: {podman_ok}")
        
        return SkillResult(
            success=docker_ok or podman_ok,
            data={
                "docker_available": docker_ok,
                "podman_available": podman_ok,
                "runtime": self._runtime
            },
            logs=self.get_logs()
        )
