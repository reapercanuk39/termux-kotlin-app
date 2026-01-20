"""
Swarm Coordinator
=================

Core swarm intelligence implementation using stigmergy-based coordination.

Inspired by ant colony optimization and bee swarm behavior:
- Agents leave "pheromone" signals in shared space
- Signals decay over time unless reinforced
- Other agents sense signals to guide their behavior
- Complex emergent behavior from simple local rules
"""

import json
import os
import time
import uuid
from dataclasses import dataclass, field, asdict
from datetime import datetime
from enum import Enum
from pathlib import Path
from threading import Lock
from typing import Any, Dict, List, Optional, Set
import fcntl


class SignalType(Enum):
    """Types of swarm signals (pheromones)."""
    
    # Success signals - attract other agents
    SUCCESS = "success"           # Task completed successfully
    RESOURCE_FOUND = "resource"   # Found useful resource (package, file, etc.)
    PATH_CLEAR = "path_clear"     # This approach works
    
    # Warning signals - repel or caution
    FAILURE = "failure"           # Task failed here
    BLOCKED = "blocked"           # Path is blocked/broken
    DANGER = "danger"             # Something went wrong badly
    
    # Coordination signals - for multi-agent tasks
    WORKING = "working"           # Agent is actively working here
    CLAIMING = "claiming"         # Agent claims this task
    RELEASING = "releasing"       # Agent releasing a claim
    HELP_NEEDED = "help"          # Agent needs assistance
    
    # Discovery signals - sharing knowledge
    LEARNED = "learned"           # New pattern/skill discovered
    OPTIMIZED = "optimized"       # Found a better way
    DEPRECATED = "deprecated"     # Old approach no longer works
    
    # Lifecycle signals - daemon status (from Kotlin daemon)
    HEARTBEAT = "heartbeat"       # Daemon health check pulse
    STARTUP = "startup"           # Daemon started
    SHUTDOWN = "shutdown"         # Daemon stopping


@dataclass
class Signal:
    """A pheromone-like signal in the swarm space."""
    
    id: str
    signal_type: SignalType
    source_agent: str
    target: str                    # What this signal is about (skill, task, path, etc.)
    strength: float = 1.0          # 0.0 to 1.0, decays over time
    data: Dict[str, Any] = field(default_factory=dict)
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)
    ttl: int = 3600                # Time to live in seconds (default 1 hour)
    reinforcement_count: int = 0   # How many times this signal was reinforced
    
    def is_expired(self) -> bool:
        """Check if signal has expired."""
        return time.time() > (self.created_at + self.ttl)
    
    def is_weak(self, threshold: float = 0.1) -> bool:
        """Check if signal strength is below threshold."""
        return self.strength < threshold
    
    def decay(self, rate: float = 0.1) -> float:
        """Apply decay to signal strength. Returns new strength."""
        self.strength = max(0.0, self.strength - rate)
        self.updated_at = time.time()
        return self.strength
    
    def reinforce(self, amount: float = 0.3) -> float:
        """Reinforce signal strength. Returns new strength."""
        self.strength = min(1.0, self.strength + amount)
        self.reinforcement_count += 1
        self.updated_at = time.time()
        # Extend TTL on reinforcement
        self.ttl = min(self.ttl + 600, 86400)  # Add 10 min, max 24 hours
        return self.strength
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for serialization."""
        return {
            "id": self.id,
            "signal_type": self.signal_type.value,
            "source_agent": self.source_agent,
            "target": self.target,
            "strength": self.strength,
            "data": self.data,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
            "ttl": self.ttl,
            "reinforcement_count": self.reinforcement_count
        }
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'Signal':
        """Create Signal from dictionary."""
        return cls(
            id=data["id"],
            signal_type=SignalType(data["signal_type"]),
            source_agent=data["source_agent"],
            target=data["target"],
            strength=data.get("strength", 1.0),
            data=data.get("data", {}),
            created_at=data.get("created_at", time.time()),
            updated_at=data.get("updated_at", time.time()),
            ttl=data.get("ttl", 3600),
            reinforcement_count=data.get("reinforcement_count", 0)
        )


class SwarmCoordinator:
    """
    Central coordinator for swarm-based agent communication.
    
    Uses filesystem-based stigmergy:
    - Signals stored as JSON files in shared directory
    - Agents read/write signals independently
    - Decay process runs periodically to fade old signals
    
    This enables emergent coordination without direct agent-to-agent communication.
    """
    
    def __init__(self, swarm_dir: Path):
        self.swarm_dir = Path(swarm_dir)
        self.signals_dir = self.swarm_dir / "signals"
        self.index_file = self.swarm_dir / "index.json"
        self._lock = Lock()
        
        # Ensure directories exist
        self.signals_dir.mkdir(parents=True, exist_ok=True)
        
        # Initialize index if needed
        if not self.index_file.exists():
            self._write_index({"signals": {}, "last_decay": time.time()})
        
        # Decay configuration
        self.decay_rate = 0.05          # 5% decay per cycle
        self.decay_interval = 300       # Run decay every 5 minutes
        self.weak_threshold = 0.1       # Remove signals below this strength
    
    def _read_index(self) -> Dict[str, Any]:
        """Read the signal index with locking."""
        try:
            with open(self.index_file, 'r') as f:
                fcntl.flock(f.fileno(), fcntl.LOCK_SH)
                try:
                    return json.load(f)
                finally:
                    fcntl.flock(f.fileno(), fcntl.LOCK_UN)
        except (json.JSONDecodeError, FileNotFoundError):
            return {"signals": {}, "last_decay": time.time()}
    
    def _write_index(self, data: Dict[str, Any]) -> None:
        """Write the signal index with locking."""
        with open(self.index_file, 'w') as f:
            fcntl.flock(f.fileno(), fcntl.LOCK_EX)
            try:
                json.dump(data, f, indent=2)
            finally:
                fcntl.flock(f.fileno(), fcntl.LOCK_UN)
    
    def emit(self, 
             signal_type: SignalType, 
             source_agent: str, 
             target: str,
             data: Dict[str, Any] = None,
             strength: float = 1.0,
             ttl: int = 3600) -> Signal:
        """
        Emit a new signal into the swarm space.
        
        If a similar signal exists (same type, agent, target), reinforce it instead.
        """
        with self._lock:
            index = self._read_index()
            
            # Check for existing similar signal to reinforce
            existing_id = None
            for sig_id, sig_meta in index["signals"].items():
                if (sig_meta["signal_type"] == signal_type.value and
                    sig_meta["source_agent"] == source_agent and
                    sig_meta["target"] == target):
                    existing_id = sig_id
                    break
            
            if existing_id:
                # Reinforce existing signal
                signal = self._load_signal(existing_id)
                if signal:
                    signal.reinforce()
                    if data:
                        signal.data.update(data)
                    self._save_signal(signal)
                    return signal
            
            # Create new signal
            signal = Signal(
                id=str(uuid.uuid4())[:8],
                signal_type=signal_type,
                source_agent=source_agent,
                target=target,
                strength=strength,
                data=data or {},
                ttl=ttl
            )
            
            # Save signal file
            self._save_signal(signal)
            
            # Update index
            index["signals"][signal.id] = {
                "signal_type": signal_type.value,
                "source_agent": source_agent,
                "target": target,
                "created_at": signal.created_at
            }
            self._write_index(index)
            
            return signal
    
    def _save_signal(self, signal: Signal) -> None:
        """Save signal to file."""
        signal_file = self.signals_dir / f"{signal.id}.json"
        with open(signal_file, 'w') as f:
            json.dump(signal.to_dict(), f, indent=2)
    
    def _load_signal(self, signal_id: str) -> Optional[Signal]:
        """Load signal from file."""
        signal_file = self.signals_dir / f"{signal_id}.json"
        try:
            with open(signal_file, 'r') as f:
                return Signal.from_dict(json.load(f))
        except (json.JSONDecodeError, FileNotFoundError):
            return None
    
    def _delete_signal(self, signal_id: str) -> None:
        """Delete signal file."""
        signal_file = self.signals_dir / f"{signal_id}.json"
        try:
            signal_file.unlink()
        except FileNotFoundError:
            pass
    
    def sense(self,
              signal_types: List[SignalType] = None,
              target: str = None,
              min_strength: float = 0.0,
              limit: int = 50) -> List[Signal]:
        """
        Sense signals in the swarm space.
        
        Args:
            signal_types: Filter by signal types (None = all)
            target: Filter by target (None = all)
            min_strength: Minimum signal strength to return
            limit: Maximum number of signals to return
        
        Returns:
            List of signals, sorted by strength (strongest first)
        """
        signals = []
        index = self._read_index()
        
        for sig_id in list(index["signals"].keys()):
            signal = self._load_signal(sig_id)
            if not signal:
                continue
            
            # Skip expired or weak signals
            if signal.is_expired() or signal.strength < min_strength:
                continue
            
            # Apply filters
            if signal_types and signal.signal_type not in signal_types:
                continue
            if target and signal.target != target:
                continue
            
            signals.append(signal)
        
        # Sort by strength (strongest first)
        signals.sort(key=lambda s: s.strength, reverse=True)
        
        return signals[:limit]
    
    def sense_for_target(self, target: str) -> Dict[str, List[Signal]]:
        """
        Get all signals related to a target, grouped by type.
        
        Useful for making decisions about a specific task/skill/resource.
        """
        all_signals = self.sense(target=target)
        grouped = {}
        for signal in all_signals:
            type_name = signal.signal_type.value
            if type_name not in grouped:
                grouped[type_name] = []
            grouped[type_name].append(signal)
        return grouped
    
    def get_consensus(self, target: str) -> Dict[str, Any]:
        """
        Get swarm consensus about a target.
        
        Analyzes signals to determine:
        - Overall sentiment (positive/negative/neutral)
        - Confidence level (based on signal count and strength)
        - Recommended action
        """
        signals = self.sense_for_target(target)
        
        positive_types = {SignalType.SUCCESS, SignalType.RESOURCE_FOUND, 
                        SignalType.PATH_CLEAR, SignalType.OPTIMIZED}
        negative_types = {SignalType.FAILURE, SignalType.BLOCKED, 
                         SignalType.DANGER, SignalType.DEPRECATED}
        
        positive_score = 0.0
        negative_score = 0.0
        total_signals = 0
        
        for type_name, sigs in signals.items():
            sig_type = SignalType(type_name)
            for sig in sigs:
                total_signals += 1
                if sig_type in positive_types:
                    positive_score += sig.strength
                elif sig_type in negative_types:
                    negative_score += sig.strength
        
        if total_signals == 0:
            return {
                "sentiment": "unknown",
                "confidence": 0.0,
                "recommendation": "explore",
                "signals_count": 0
            }
        
        net_score = positive_score - negative_score
        confidence = min(1.0, total_signals / 10.0)  # More signals = more confident
        
        if net_score > 0.5:
            sentiment = "positive"
            recommendation = "proceed"
        elif net_score < -0.5:
            sentiment = "negative"
            recommendation = "avoid"
        else:
            sentiment = "neutral"
            recommendation = "caution"
        
        return {
            "sentiment": sentiment,
            "confidence": confidence,
            "recommendation": recommendation,
            "signals_count": total_signals,
            "positive_score": positive_score,
            "negative_score": negative_score
        }
    
    def decay_all(self) -> Dict[str, int]:
        """
        Apply decay to all signals. Remove expired/weak signals.
        
        Returns statistics about the decay operation.
        """
        with self._lock:
            index = self._read_index()
            
            decayed = 0
            removed = 0
            
            for sig_id in list(index["signals"].keys()):
                signal = self._load_signal(sig_id)
                if not signal:
                    # Signal file missing, clean up index
                    del index["signals"][sig_id]
                    removed += 1
                    continue
                
                if signal.is_expired() or signal.is_weak(self.weak_threshold):
                    # Remove expired/weak signal
                    self._delete_signal(sig_id)
                    del index["signals"][sig_id]
                    removed += 1
                else:
                    # Apply decay
                    signal.decay(self.decay_rate)
                    self._save_signal(signal)
                    decayed += 1
            
            index["last_decay"] = time.time()
            self._write_index(index)
            
            return {"decayed": decayed, "removed": removed}
    
    def maybe_decay(self) -> Optional[Dict[str, int]]:
        """Run decay if enough time has passed since last decay."""
        index = self._read_index()
        last_decay = index.get("last_decay", 0)
        
        if time.time() - last_decay > self.decay_interval:
            return self.decay_all()
        return None
    
    def get_stats(self) -> Dict[str, Any]:
        """Get swarm statistics."""
        index = self._read_index()
        signals = self.sense(limit=1000)
        
        type_counts = {}
        agent_counts = {}
        total_strength = 0.0
        
        for sig in signals:
            type_name = sig.signal_type.value
            type_counts[type_name] = type_counts.get(type_name, 0) + 1
            agent_counts[sig.source_agent] = agent_counts.get(sig.source_agent, 0) + 1
            total_strength += sig.strength
        
        return {
            "total_signals": len(signals),
            "signals_by_type": type_counts,
            "signals_by_agent": agent_counts,
            "average_strength": total_strength / max(1, len(signals)),
            "last_decay": datetime.fromtimestamp(
                index.get("last_decay", 0)
            ).isoformat() if index.get("last_decay") else None
        }
    
    def clear(self) -> None:
        """Clear all signals (for testing/reset)."""
        with self._lock:
            for signal_file in self.signals_dir.glob("*.json"):
                signal_file.unlink()
            self._write_index({"signals": {}, "last_decay": time.time()})
