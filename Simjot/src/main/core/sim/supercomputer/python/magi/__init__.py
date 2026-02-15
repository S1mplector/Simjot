"""
MAGI Supercomputer System
=========================

S.C. MAGI (マギ) System - A trio of multipurpose supercomputers designed by 
Dr. Naoko Akagi during her research into bio-computers at Gehirn.

The MAGI's 7th generation organic computers are implanted with three differing 
aspects of Dr. Naoko Akagi's personality using the Personality Transplant OS:

- MELCHIOR (MAGI-1): Her persona as a scientist
- BALTHASAR (MAGI-2): Her persona as a mother  
- CASPER (MAGI-3): Her persona as a woman

Architecture:
- Personality Transplant OS (PTOS): Core personality encoding technology
- Organic Processor: 7th generation bio-neural computing substrate
- Memory Engrams: Persistent associative memory structures
- Consensus Protocol: Multi-unit voting and deliberation system
- Dilemma Protocol: Ethical conflict resolution between aspects
- MAGI Network: Global network connecting replica installations
- MAGI Achiral: Modular rack-based system (Rebuild continuity)
- Protocol System: Pribnow codes, Type-666 firewall, emergency protocols
- SEELE Detection: Anti-intrusion and coordinated attack defense
- Marduk Institute: Personnel and pilot candidate evaluation

Known MAGI Installations:
- MAGI-01: Tokyo-3, Japan (Original - NERV HQ)
- MAGI-02: Matsushiro, Japan
- MAGI-03: Berlin, Germany
- MAGI-04: Massachusetts, USA
- MAGI-05: Hamburg, Germany
- MAGI-06: Beijing, China

"The MAGI don't simply vote. They deliberate. They argue. They synthesize."
"""

# Core decision engine
from .core.engine import MAGIEngine
from .core.brain import Brain
from .core.personality import Personality
from .core.decision import Decision, Verdict, DeliberationRound

# Core v2.1 - Dilemma Protocol & Marduk
from .core.dilemma import DilemmaProtocol, DilemmaType, DilemmaResolution
from .core.marduk import MardukInstitute, MardukReport, PsychProfile, SubjectCategory
from .core.naoko import NaokoFoundation, EmotionalTrigger

# PTOS - Personality Transplant Operating System
from .ptos.matrix import PersonalityMatrix, PersonalityAspect, PersonalityFragment
from .ptos.organic import OrganicProcessor, ProcessingMode
from .ptos.transplant import TransplantProcedure, TransplantResult
from .ptos.engram import MemoryEngram, EngramStore, EngramType

# The Three MAGI Units
from .brains import MELCHIOR, BALTHASAR, CASPER

# Network and Consensus
from .network.system import MAGISystem, MAGIUnit, SystemStatus, AlertLevel
from .network.consensus import ConsensusProtocol, VotingSession, ConsensusResult, DecisionCategory
from .network.network import MAGINetwork, NetworkNode, IntrusionDetector
from .network.achiral import MAGIAchiral, AchiralBank, AchiralModule

# Network v2.1 - Protocols, Sync, SEELE Detection
from .network.protocols import ProtocolManager, Type666Firewall, PribnowCode, AuthorizationLevel
from .network.synchronization import SynchronizationProtocol, AspectDynamics, SyncResult
from .network.seele import SEELEDetector, AntiHackingModule, AttackVector, DefenseState

# LLM Integration
from .llm.client import create_openai_client

__version__ = "2.1.0"
__codename__ = "GEHIRN"

__all__ = [
    # Core
    "MAGIEngine",
    "Brain", 
    "Personality",
    "Decision",
    "Verdict",
    "DeliberationRound",
    
    # Dilemma Protocol
    "DilemmaProtocol",
    "DilemmaType",
    "DilemmaResolution",
    
    # Marduk Institute
    "MardukInstitute",
    "MardukReport",
    "PsychProfile",
    "SubjectCategory",
    
    # Naoko Foundation
    "NaokoFoundation",
    "EmotionalTrigger",
    
    # PTOS
    "PersonalityMatrix",
    "PersonalityAspect",
    "PersonalityFragment",
    "OrganicProcessor",
    "ProcessingMode",
    "TransplantProcedure",
    "TransplantResult",
    "MemoryEngram",
    "EngramStore",
    "EngramType",
    
    # Brains
    "MELCHIOR",
    "BALTHASAR", 
    "CASPER",
    
    # System
    "MAGISystem",
    "MAGIUnit",
    "SystemStatus",
    "AlertLevel",
    
    # Consensus
    "ConsensusProtocol",
    "VotingSession",
    "ConsensusResult",
    "DecisionCategory",
    
    # Network
    "MAGINetwork",
    "NetworkNode",
    "IntrusionDetector",
    
    # Achiral
    "MAGIAchiral",
    "AchiralBank",
    "AchiralModule",
    
    # Protocol System
    "ProtocolManager",
    "Type666Firewall",
    "PribnowCode",
    "AuthorizationLevel",
    
    # Synchronization
    "SynchronizationProtocol",
    "AspectDynamics",
    "SyncResult",
    
    # SEELE Defense
    "SEELEDetector",
    "AntiHackingModule",
    "AttackVector",
    "DefenseState",
    
    # LLM
    "create_openai_client",
]
