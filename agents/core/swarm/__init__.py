"""
Swarm Intelligence Module
=========================

Emergent multi-agent coordination through stigmergy (indirect communication).

Key concepts:
- Signals: Messages left in shared space (like ant pheromones)
- Decay: Signals fade over time unless reinforced
- Sensing: Agents detect nearby signals to inform decisions
- Emergence: Complex behavior from simple local rules
"""

from .swarm import SwarmCoordinator, Signal, SignalType
from .signals import SignalEmitter, SignalSensor

__all__ = [
    'SwarmCoordinator',
    'Signal', 
    'SignalType',
    'SignalEmitter',
    'SignalSensor'
]
