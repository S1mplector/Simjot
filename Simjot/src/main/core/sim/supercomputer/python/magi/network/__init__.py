"""
MAGI Network
============

The global network of MAGI systems, including the original Tokyo-3 system
and replica installations worldwide.

Known MAGI installations:
- MAGI 01: Tokyo-3, Japan (Original - NERV HQ)
- MAGI 02: Matsushiro, Japan
- MAGI 03: Berlin, Germany
- MAGI 04: Massachusetts, USA
- MAGI 05: Hamburg, Germany
- MAGI 06: Beijing, China

This module also implements:
- MAGI Achiral: Modular rack-based system (Rebuild continuity)
- Consensus Protocol: Multi-unit voting mechanisms
- Protocol System: Pribnow codes, Type-666 firewall, emergency protocols
- Inter-MAGI Synchronization: Aspect conflict modeling
- SEELE Detection: Anti-intrusion and coordinated attack defense
"""

from .system import MAGISystem, MAGIUnit, SystemStatus, AlertLevel
from .consensus import ConsensusProtocol, VotingSession, ConsensusResult, DecisionCategory, VoteType
from .network import MAGINetwork, NetworkNode, ConnectionStatus, IntrusionDetector
from .achiral import MAGIAchiral, AchiralModule, AchiralBank

# Protocol System (v2.1)
from .protocols import (
    ProtocolManager, 
    Type666Firewall, 
    EmergencyProtocol,
    PribnowCode,
    AuthorizationLevel,
    EmergencyLevel,
)

# Inter-MAGI Synchronization (v2.1)
from .synchronization import (
    SynchronizationProtocol,
    AspectDynamics,
    SyncResult,
    AspectState,
    Argument,
)

# SEELE Detection (v2.1)
from .seele import (
    SEELEDetector,
    AntiHackingModule,
    AttackVector,
    AttackSeverity,
    DefenseState,
)

__all__ = [
    # Core System
    "MAGISystem",
    "MAGIUnit", 
    "SystemStatus",
    "AlertLevel",
    
    # Consensus
    "ConsensusProtocol",
    "VotingSession",
    "ConsensusResult",
    "DecisionCategory",
    "VoteType",
    
    # Network
    "MAGINetwork",
    "NetworkNode",
    "ConnectionStatus",
    "IntrusionDetector",
    
    # Achiral
    "MAGIAchiral",
    "AchiralModule",
    "AchiralBank",
    
    # Protocol System
    "ProtocolManager",
    "Type666Firewall",
    "EmergencyProtocol",
    "PribnowCode",
    "AuthorizationLevel",
    "EmergencyLevel",
    
    # Synchronization
    "SynchronizationProtocol",
    "AspectDynamics",
    "SyncResult",
    "AspectState",
    "Argument",
    
    # SEELE Defense
    "SEELEDetector",
    "AntiHackingModule",
    "AttackVector",
    "AttackSeverity",
    "DefenseState",
]
