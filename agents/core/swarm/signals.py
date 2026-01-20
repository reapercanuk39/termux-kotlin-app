"""
Signal Emitter and Sensor
=========================

High-level interfaces for agents to interact with the swarm.

SignalEmitter: Convenience methods for emitting common signals
SignalSensor: Pattern-based signal detection for decision making
"""

import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from .swarm import SwarmCoordinator, Signal, SignalType


class SignalEmitter:
    """
    Convenience class for emitting swarm signals.
    
    Provides semantic methods for common signal patterns:
    - report_success/failure
    - claim/release tasks
    - share discoveries
    """
    
    def __init__(self, coordinator: SwarmCoordinator, agent_name: str):
        self.coordinator = coordinator
        self.agent_name = agent_name
    
    def report_success(self, target: str, details: Dict[str, Any] = None) -> Signal:
        """Report successful completion of a task/skill."""
        return self.coordinator.emit(
            signal_type=SignalType.SUCCESS,
            source_agent=self.agent_name,
            target=target,
            data={"details": details or {}, "timestamp": time.time()}
        )
    
    def report_failure(self, target: str, error: str = None, 
                       recoverable: bool = True) -> Signal:
        """Report failure on a task/skill."""
        return self.coordinator.emit(
            signal_type=SignalType.FAILURE,
            source_agent=self.agent_name,
            target=target,
            data={
                "error": error,
                "recoverable": recoverable,
                "timestamp": time.time()
            }
        )
    
    def report_blocked(self, target: str, reason: str = None) -> Signal:
        """Report that a path/approach is blocked."""
        return self.coordinator.emit(
            signal_type=SignalType.BLOCKED,
            source_agent=self.agent_name,
            target=target,
            data={"reason": reason, "timestamp": time.time()},
            ttl=7200  # Blocked signals last longer (2 hours)
        )
    
    def report_danger(self, target: str, severity: str = "high",
                      description: str = None) -> Signal:
        """Report dangerous condition (data loss, system instability, etc.)."""
        return self.coordinator.emit(
            signal_type=SignalType.DANGER,
            source_agent=self.agent_name,
            target=target,
            data={
                "severity": severity,
                "description": description,
                "timestamp": time.time()
            },
            strength=1.0,  # Danger always starts at max strength
            ttl=86400  # Danger signals last 24 hours
        )
    
    def claim_task(self, target: str, estimated_duration: int = 60) -> Signal:
        """Claim exclusive work on a task."""
        return self.coordinator.emit(
            signal_type=SignalType.CLAIMING,
            source_agent=self.agent_name,
            target=target,
            data={
                "estimated_duration": estimated_duration,
                "started_at": time.time()
            },
            ttl=estimated_duration * 2  # Claim expires after 2x estimated time
        )
    
    def release_task(self, target: str, reason: str = "completed") -> Signal:
        """Release claim on a task."""
        return self.coordinator.emit(
            signal_type=SignalType.RELEASING,
            source_agent=self.agent_name,
            target=target,
            data={"reason": reason, "timestamp": time.time()},
            ttl=60  # Release signals are short-lived
        )
    
    def report_working(self, target: str) -> Signal:
        """Report that agent is actively working on something."""
        return self.coordinator.emit(
            signal_type=SignalType.WORKING,
            source_agent=self.agent_name,
            target=target,
            data={"timestamp": time.time()},
            ttl=120  # Working signals expire quickly if not refreshed
        )
    
    def request_help(self, target: str, problem: str = None,
                     needed_capabilities: List[str] = None) -> Signal:
        """Request help from other agents."""
        return self.coordinator.emit(
            signal_type=SignalType.HELP_NEEDED,
            source_agent=self.agent_name,
            target=target,
            data={
                "problem": problem,
                "needed_capabilities": needed_capabilities or [],
                "timestamp": time.time()
            },
            strength=1.0,
            ttl=1800  # Help requests last 30 minutes
        )
    
    def share_discovery(self, target: str, discovery_type: str,
                        details: Dict[str, Any] = None) -> Signal:
        """Share a discovery with the swarm (new pattern, resource, etc.)."""
        return self.coordinator.emit(
            signal_type=SignalType.LEARNED,
            source_agent=self.agent_name,
            target=target,
            data={
                "discovery_type": discovery_type,
                "details": details or {},
                "timestamp": time.time()
            },
            ttl=43200  # Discoveries last 12 hours
        )
    
    def report_optimization(self, target: str, improvement: str,
                           metrics: Dict[str, Any] = None) -> Signal:
        """Report an optimization/improvement to an approach."""
        return self.coordinator.emit(
            signal_type=SignalType.OPTIMIZED,
            source_agent=self.agent_name,
            target=target,
            data={
                "improvement": improvement,
                "metrics": metrics or {},
                "timestamp": time.time()
            }
        )
    
    def mark_deprecated(self, target: str, reason: str,
                       replacement: str = None) -> Signal:
        """Mark something as deprecated (old skill, broken approach, etc.)."""
        return self.coordinator.emit(
            signal_type=SignalType.DEPRECATED,
            source_agent=self.agent_name,
            target=target,
            data={
                "reason": reason,
                "replacement": replacement,
                "timestamp": time.time()
            },
            ttl=86400  # Deprecation notices last 24 hours
        )
    
    def report_resource(self, target: str, resource_type: str,
                       location: str = None, 
                       metadata: Dict[str, Any] = None) -> Signal:
        """Report finding a useful resource."""
        return self.coordinator.emit(
            signal_type=SignalType.RESOURCE_FOUND,
            source_agent=self.agent_name,
            target=target,
            data={
                "resource_type": resource_type,
                "location": location,
                "metadata": metadata or {},
                "timestamp": time.time()
            }
        )


class SignalSensor:
    """
    Convenience class for sensing and interpreting swarm signals.
    
    Provides pattern-based detection:
    - Check if task is claimed
    - Get recommendations for a skill
    - Find help requests matching capabilities
    """
    
    def __init__(self, coordinator: SwarmCoordinator, agent_name: str):
        self.coordinator = coordinator
        self.agent_name = agent_name
    
    def is_task_claimed(self, target: str) -> Tuple[bool, Optional[str]]:
        """
        Check if a task is claimed by another agent.
        
        Returns:
            (is_claimed, claiming_agent) tuple
        """
        claims = self.coordinator.sense(
            signal_types=[SignalType.CLAIMING, SignalType.WORKING],
            target=target,
            min_strength=0.3
        )
        
        for claim in claims:
            if claim.source_agent != self.agent_name:
                return True, claim.source_agent
        
        return False, None
    
    def should_proceed(self, target: str) -> Dict[str, Any]:
        """
        Get recommendation on whether to proceed with a task.
        
        Combines consensus with claim checking.
        """
        # Check claims
        is_claimed, claimer = self.is_task_claimed(target)
        if is_claimed:
            return {
                "proceed": False,
                "reason": f"claimed by {claimer}",
                "action": "wait_or_help"
            }
        
        # Get consensus
        consensus = self.coordinator.get_consensus(target)
        
        if consensus["sentiment"] == "negative" and consensus["confidence"] > 0.5:
            return {
                "proceed": False,
                "reason": "swarm reports failures",
                "action": "investigate",
                "consensus": consensus
            }
        
        if consensus["sentiment"] == "unknown":
            return {
                "proceed": True,
                "reason": "unexplored territory",
                "action": "explore_cautiously",
                "consensus": consensus
            }
        
        return {
            "proceed": True,
            "reason": consensus["recommendation"],
            "action": consensus["recommendation"],
            "consensus": consensus
        }
    
    def find_help_requests(self, 
                          capabilities: List[str] = None) -> List[Signal]:
        """
        Find help requests that match agent's capabilities.
        
        Args:
            capabilities: List of capabilities this agent has
        """
        help_signals = self.coordinator.sense(
            signal_types=[SignalType.HELP_NEEDED],
            min_strength=0.2
        )
        
        if not capabilities:
            return help_signals
        
        # Filter to matching capabilities
        matching = []
        for sig in help_signals:
            needed = sig.data.get("needed_capabilities", [])
            if not needed or any(c in capabilities for c in needed):
                matching.append(sig)
        
        return matching
    
    def get_successful_approaches(self, target: str) -> List[Signal]:
        """Get signals about successful approaches for a target."""
        return self.coordinator.sense(
            signal_types=[SignalType.SUCCESS, SignalType.PATH_CLEAR, 
                         SignalType.OPTIMIZED],
            target=target,
            min_strength=0.2
        )
    
    def get_failures(self, target: str) -> List[Signal]:
        """Get signals about failures for a target."""
        return self.coordinator.sense(
            signal_types=[SignalType.FAILURE, SignalType.BLOCKED],
            target=target,
            min_strength=0.1
        )
    
    def get_dangers(self, target: str = None) -> List[Signal]:
        """Get danger signals, optionally filtered by target."""
        return self.coordinator.sense(
            signal_types=[SignalType.DANGER],
            target=target,
            min_strength=0.3
        )
    
    def get_discoveries(self, limit: int = 20) -> List[Signal]:
        """Get recent discoveries from the swarm."""
        return self.coordinator.sense(
            signal_types=[SignalType.LEARNED, SignalType.OPTIMIZED,
                         SignalType.RESOURCE_FOUND],
            min_strength=0.3,
            limit=limit
        )
    
    def get_deprecations(self) -> List[Signal]:
        """Get deprecation notices."""
        return self.coordinator.sense(
            signal_types=[SignalType.DEPRECATED],
            min_strength=0.2
        )
    
    def get_swarm_activity(self) -> Dict[str, Any]:
        """
        Get overview of current swarm activity.
        
        Returns summary of what other agents are doing.
        """
        working = self.coordinator.sense(
            signal_types=[SignalType.WORKING, SignalType.CLAIMING]
        )
        
        active_agents = {}
        active_targets = {}
        
        for sig in working:
            if sig.source_agent not in active_agents:
                active_agents[sig.source_agent] = []
            active_agents[sig.source_agent].append(sig.target)
            
            if sig.target not in active_targets:
                active_targets[sig.target] = []
            active_targets[sig.target].append(sig.source_agent)
        
        return {
            "active_agent_count": len(active_agents),
            "active_target_count": len(active_targets),
            "agents": active_agents,
            "targets": active_targets
        }


def create_swarm_interface(swarm_dir: Path, agent_name: str) -> Tuple[SignalEmitter, SignalSensor]:
    """
    Factory function to create emitter/sensor pair for an agent.
    
    Usage:
        emitter, sensor = create_swarm_interface(swarm_dir, "my_agent")
        
        # Emit signals
        emitter.report_success("pkg.install")
        
        # Sense signals
        if sensor.should_proceed("pkg.update")["proceed"]:
            ...
    """
    coordinator = SwarmCoordinator(swarm_dir)
    emitter = SignalEmitter(coordinator, agent_name)
    sensor = SignalSensor(coordinator, agent_name)
    return emitter, sensor
