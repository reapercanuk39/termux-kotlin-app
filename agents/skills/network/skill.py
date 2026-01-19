"""
Network Skill
=============

Network diagnostics capabilities (localhost only).
Checks ports, services, connections. Restricted to localhost.
"""

import os
import socket
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..base import Skill, SkillResult


class NetworkSkill(Skill):
    """Network diagnostics skill (localhost only)."""
    
    name = "network"
    description = "Network diagnostics (localhost only)"
    provides = [
        "check_ports",
        "check_services",
        "test_localhost",
        "list_connections",
        "check_dns_config",
        "ping_localhost",
        "get_network_info"
    ]
    requires_capabilities = ["filesystem.read", "network.local", "exec.shell", "system.info"]
    
    # Common localhost ports to check
    COMMON_PORTS = [
        (22, "ssh"),
        (80, "http"),
        (443, "https"),
        (3000, "dev-server"),
        (5000, "flask"),
        (8000, "django"),
        (8080, "http-alt"),
        (8888, "jupyter"),
        (9000, "php-fpm"),
        (5432, "postgresql"),
        (3306, "mysql"),
        (6379, "redis"),
        (27017, "mongodb"),
    ]
    
    def get_functions(self) -> Dict[str, callable]:
        return {
            "check_ports": self.check_ports,
            "check_services": self.check_services,
            "test_localhost": self.test_localhost,
            "list_connections": self.list_connections,
            "check_dns_config": self.check_dns_config,
            "ping_localhost": self.ping_localhost,
            "get_network_info": self.get_network_info,
            "self_test": self.self_test
        }
    
    def _is_port_open(self, host: str, port: int, timeout: float = 1.0) -> bool:
        """Check if a port is open on the given host."""
        # Only allow localhost
        if host not in ["localhost", "127.0.0.1", "::1"]:
            return False
        
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(timeout)
            result = sock.connect_ex((host, port))
            sock.close()
            return result == 0
        except:
            return False
    
    def check_ports(
        self,
        ports: List[int] = None,
        host: str = "localhost"
    ) -> Dict[str, Any]:
        """Check which ports are listening on localhost."""
        self.log(f"Checking ports on {host}")
        
        # Security: Only allow localhost
        if host not in ["localhost", "127.0.0.1", "::1"]:
            return {"error": "Only localhost access is allowed"}
        
        if ports is None:
            ports = [p[0] for p in self.COMMON_PORTS]
        
        results = []
        open_count = 0
        
        for port in ports:
            is_open = self._is_port_open(host, port)
            service_name = next(
                (name for p, name in self.COMMON_PORTS if p == port),
                "unknown"
            )
            results.append({
                "port": port,
                "status": "open" if is_open else "closed",
                "service": service_name
            })
            if is_open:
                open_count += 1
        
        return {
            "host": host,
            "checked": len(ports),
            "open": open_count,
            "closed": len(ports) - open_count,
            "ports": results
        }
    
    def check_services(self) -> Dict[str, Any]:
        """Check status of common local services."""
        self.log("Checking local services")
        
        services = []
        
        for port, name in self.COMMON_PORTS:
            is_running = self._is_port_open("localhost", port)
            services.append({
                "service": name,
                "port": port,
                "status": "running" if is_running else "stopped"
            })
        
        running = sum(1 for s in services if s["status"] == "running")
        
        return {
            "total": len(services),
            "running": running,
            "stopped": len(services) - running,
            "services": services
        }
    
    def test_localhost(self) -> Dict[str, Any]:
        """Test localhost connectivity."""
        self.log("Testing localhost connectivity")
        
        tests = []
        
        # Test IPv4 localhost
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(1)
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
            sock.close()
            tests.append({
                "test": "ipv4_localhost",
                "status": "passed",
                "details": f"Bound to port {port}"
            })
        except Exception as e:
            tests.append({
                "test": "ipv4_localhost",
                "status": "failed",
                "details": str(e)
            })
        
        # Test hostname resolution
        try:
            ip = socket.gethostbyname("localhost")
            tests.append({
                "test": "hostname_resolution",
                "status": "passed",
                "details": f"localhost -> {ip}"
            })
        except Exception as e:
            tests.append({
                "test": "hostname_resolution",
                "status": "failed",
                "details": str(e)
            })
        
        # Test loopback
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.connect(("127.0.0.1", 1))
            local_ip = sock.getsockname()[0]
            sock.close()
            tests.append({
                "test": "loopback",
                "status": "passed",
                "details": f"Local IP: {local_ip}"
            })
        except Exception as e:
            tests.append({
                "test": "loopback",
                "status": "failed",
                "details": str(e)
            })
        
        passed = sum(1 for t in tests if t["status"] == "passed")
        
        return {
            "total_tests": len(tests),
            "passed": passed,
            "failed": len(tests) - passed,
            "tests": tests
        }
    
    def list_connections(self) -> Dict[str, Any]:
        """List active network connections."""
        self.log("Listing network connections")
        
        connections = []
        
        try:
            # Try netstat
            result = self.executor.run(
                ["netstat", "-tuln"],
                check=False
            )
            
            if result.returncode == 0 and result.stdout:
                lines = result.stdout.strip().split('\n')
                for line in lines[2:20]:  # Skip headers, limit output
                    parts = line.split()
                    if len(parts) >= 4:
                        connections.append({
                            "proto": parts[0],
                            "local_addr": parts[3],
                            "state": parts[-1] if len(parts) > 5 else "LISTEN"
                        })
        except Exception as e:
            # Fallback: check common ports
            for port, name in self.COMMON_PORTS[:10]:
                if self._is_port_open("localhost", port):
                    connections.append({
                        "proto": "tcp",
                        "local_addr": f"127.0.0.1:{port}",
                        "state": "LISTEN",
                        "service": name
                    })
        
        return {
            "count": len(connections),
            "connections": connections
        }
    
    def check_dns_config(self) -> Dict[str, Any]:
        """Check DNS resolver configuration."""
        self.log("Checking DNS configuration")
        
        resolv_conf = Path("/etc/resolv.conf")
        dns_servers = []
        search_domains = []
        
        # Check Termux-specific path
        prefix = os.environ.get("PREFIX", "/data/data/com.termux.kotlin/files/usr")
        termux_resolv = Path(prefix) / "etc" / "resolv.conf"
        
        config_path = None
        if termux_resolv.exists():
            config_path = termux_resolv
        elif resolv_conf.exists():
            config_path = resolv_conf
        
        if config_path:
            try:
                content = config_path.read_text()
                for line in content.split('\n'):
                    line = line.strip()
                    if line.startswith("nameserver"):
                        parts = line.split()
                        if len(parts) >= 2:
                            dns_servers.append(parts[1])
                    elif line.startswith("search"):
                        search_domains.extend(line.split()[1:])
            except Exception as e:
                return {"error": f"Failed to read config: {e}"}
        
        # Test DNS resolution
        resolution_test = None
        try:
            ip = socket.gethostbyname("localhost")
            resolution_test = {"status": "working", "localhost": ip}
        except Exception as e:
            resolution_test = {"status": "failed", "error": str(e)}
        
        return {
            "config_path": str(config_path) if config_path else None,
            "dns_servers": dns_servers,
            "search_domains": search_domains,
            "resolution_test": resolution_test
        }
    
    def ping_localhost(self, count: int = 3) -> Dict[str, Any]:
        """Ping localhost to test network stack."""
        self.log(f"Pinging localhost {count} times")
        
        try:
            result = self.executor.run(
                ["ping", "-c", str(min(count, 5)), "127.0.0.1"],
                timeout=10,
                check=False
            )
            
            success = result.returncode == 0
            output = result.stdout if result.stdout else ""
            
            # Parse ping statistics
            stats = {}
            for line in output.split('\n'):
                if "packets transmitted" in line:
                    stats["summary"] = line.strip()
                elif "rtt" in line or "round-trip" in line:
                    stats["timing"] = line.strip()
            
            return {
                "success": success,
                "target": "127.0.0.1",
                "count": count,
                "statistics": stats
            }
        except Exception as e:
            return {"error": str(e), "success": False}
    
    def get_network_info(self) -> Dict[str, Any]:
        """Get basic network information."""
        self.log("Getting network info")
        
        info = {
            "hostname": None,
            "fqdn": None,
            "addresses": []
        }
        
        # Get hostname
        try:
            info["hostname"] = socket.gethostname()
            info["fqdn"] = socket.getfqdn()
        except:
            pass
        
        # Get local addresses
        try:
            # Get all local IPs
            for family, addr_info in [
                (socket.AF_INET, "ipv4"),
                (socket.AF_INET6, "ipv6")
            ]:
                try:
                    sock = socket.socket(family, socket.SOCK_DGRAM)
                    sock.connect(("127.0.0.1" if family == socket.AF_INET else "::1", 1))
                    ip = sock.getsockname()[0]
                    sock.close()
                    info["addresses"].append({
                        "type": addr_info,
                        "address": ip
                    })
                except:
                    pass
        except:
            pass
        
        # Check if we're in Termux
        info["environment"] = "termux" if "TERMUX_VERSION" in os.environ or "com.termux" in os.environ.get("PREFIX", "") else "standard"
        
        return info
    
    def self_test(self) -> Dict[str, Any]:
        """Run self-test."""
        self.log("Running network skill self-test")
        
        tests = []
        
        # Test 1: Localhost connectivity
        result = self.test_localhost()
        tests.append({
            "test": "localhost_connectivity",
            "passed": result.get("passed", 0) > 0
        })
        
        # Test 2: Port checking
        result = self.check_ports([80, 22])
        tests.append({
            "test": "port_check",
            "passed": "ports" in result
        })
        
        # Test 3: Network info
        result = self.get_network_info()
        tests.append({
            "test": "network_info",
            "passed": result.get("hostname") is not None
        })
        
        all_passed = all(t["passed"] for t in tests)
        return {"status": "passed" if all_passed else "partial", "tests": tests}
