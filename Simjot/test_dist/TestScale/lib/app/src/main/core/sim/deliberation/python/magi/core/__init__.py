"""
MAGI Core Module
================

Contains the fundamental abstractions for the MAGI decision engine:
- Brain: LLM-powered reasoning unit
- Personality: Trait definitions and value systems
- Decision/Verdict: Outcome data structures
- Dilemma Protocol: Ethical conflict resolution
- Marduk Institute: Personnel evaluation system
- Naoko Foundation: Deep psychological context
"""

from .engine import MAGIEngine
from .brain import Brain
from .personality import Personality
from .decision import Decision, Verdict, DeliberationRound

# Dilemma Protocol (v2.1)
from .dilemma import (
    DilemmaProtocol,
    DilemmaType,
    DilemmaResolution,
    ResolutionStrategy,
    ValueTension,
)

# Marduk Institute (v2.1)
from .marduk import (
    MardukInstitute,
    MardukReport,
    PsychProfile,
    PhysicalProfile,
    SubjectCategory,
    ClearanceLevel,
    ThreatRating,
    CompatibilityRating,
)

# Naoko Foundation (v2.1)
from .naoko import (
    NaokoFoundation,
    EmotionalTrigger,
    PsychologicalWound,
    AspectShadow,
    NaokoMemory,
)

__all__ = [
    # Core Engine
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
    "ResolutionStrategy",
    "ValueTension",
    
    # Marduk Institute
    "MardukInstitute",
    "MardukReport",
    "PsychProfile",
    "PhysicalProfile",
    "SubjectCategory",
    "ClearanceLevel",
    "ThreatRating",
    "CompatibilityRating",
    
    # Naoko Foundation
    "NaokoFoundation",
    "EmotionalTrigger",
    "PsychologicalWound",
    "AspectShadow",
    "NaokoMemory",
]
