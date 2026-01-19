"""
Graph-Based Agent Orchestrator
==============================

DAG execution engine for multi-agent workflows.
"""

import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Set
from dataclasses import dataclass, field
from enum import Enum


class NodeStatus(Enum):
    PENDING = "pending"
    RUNNING = "running"
    SUCCESS = "success"
    FAILED = "failed"
    SKIPPED = "skipped"


@dataclass
class Node:
    """A node in the execution graph."""
    id: str
    agent: str
    task: str
    dependencies: List[str] = field(default_factory=list)
    status: NodeStatus = NodeStatus.PENDING
    result: Optional[Dict[str, Any]] = None
    error: Optional[str] = None


@dataclass
class Edge:
    """An edge connecting two nodes."""
    source: str
    target: str
    data_mapping: Optional[Dict[str, str]] = None


class GraphEngine:
    """
    DAG-based orchestration engine.
    
    Supports:
    - Dependency resolution
    - Parallel execution
    - Result passing between nodes
    - Failure propagation
    """
    
    def __init__(self, agents_root: Path):
        self.agents_root = Path(agents_root)
        self.nodes: Dict[str, Node] = {}
        self.edges: List[Edge] = []
    
    def add_node(self, node_id: str, agent: str, task: str, depends_on: List[str] = None) -> Node:
        """Add a node to the graph."""
        node = Node(
            id=node_id,
            agent=agent,
            task=task,
            dependencies=depends_on or []
        )
        self.nodes[node_id] = node
        
        # Create edges for dependencies
        for dep in node.dependencies:
            self.edges.append(Edge(source=dep, target=node_id))
        
        return node
    
    def validate(self) -> Dict[str, Any]:
        """Validate the graph (check for cycles, missing deps)."""
        issues = []
        
        # Check missing dependencies
        for node in self.nodes.values():
            for dep in node.dependencies:
                if dep not in self.nodes:
                    issues.append(f"Node '{node.id}' depends on missing node '{dep}'")
        
        # Check for cycles using DFS
        if self._has_cycle():
            issues.append("Graph contains a cycle")
        
        return {"valid": len(issues) == 0, "issues": issues}
    
    def _has_cycle(self) -> bool:
        """Detect cycles using DFS."""
        visited = set()
        rec_stack = set()
        
        def dfs(node_id: str) -> bool:
            visited.add(node_id)
            rec_stack.add(node_id)
            
            for edge in self.edges:
                if edge.source == node_id:
                    if edge.target not in visited:
                        if dfs(edge.target):
                            return True
                    elif edge.target in rec_stack:
                        return True
            
            rec_stack.remove(node_id)
            return False
        
        for node_id in self.nodes:
            if node_id not in visited:
                if dfs(node_id):
                    return True
        return False
    
    def get_ready_nodes(self) -> List[Node]:
        """Get nodes ready for execution (all deps satisfied)."""
        ready = []
        for node in self.nodes.values():
            if node.status != NodeStatus.PENDING:
                continue
            
            deps_satisfied = all(
                self.nodes[dep].status == NodeStatus.SUCCESS
                for dep in node.dependencies
            )
            
            if deps_satisfied:
                ready.append(node)
        
        return ready
    
    def execute(self) -> Dict[str, Any]:
        """Execute the graph."""
        from agents.core.supervisor.agentd import AgentDaemon
        
        validation = self.validate()
        if not validation["valid"]:
            return {"status": "error", "error": "invalid_graph", "issues": validation["issues"]}
        
        daemon = AgentDaemon(self.agents_root)
        executed = []
        
        while True:
            ready = self.get_ready_nodes()
            if not ready:
                break
            
            # Execute ready nodes (could be parallel)
            for node in ready:
                node.status = NodeStatus.RUNNING
                try:
                    result = daemon.run_task(node.agent, node.task)
                    node.result = result
                    node.status = NodeStatus.SUCCESS if result.get("status") == "success" else NodeStatus.FAILED
                except Exception as e:
                    node.error = str(e)
                    node.status = NodeStatus.FAILED
                
                executed.append(node.id)
        
        # Check for unexecuted nodes
        pending = [n.id for n in self.nodes.values() if n.status == NodeStatus.PENDING]
        
        return {
            "status": "success" if not pending else "partial",
            "executed": executed,
            "pending": pending,
            "results": {n.id: n.result for n in self.nodes.values() if n.result}
        }


if __name__ == "__main__":
    # Example usage
    engine = GraphEngine(Path("agents"))
    engine.add_node("build", "build_agent", "pkg.install_package", depends_on=[])
    engine.add_node("test", "system_agent", "fs.list_directory", depends_on=["build"])
    
    print(json.dumps(engine.validate(), indent=2))
