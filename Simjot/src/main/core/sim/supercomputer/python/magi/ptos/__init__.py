"""
Personality Transplant Operating System
=======================================

The core technology behind the MAGI System, developed by Dr. Naoko Akagi
during her research into bio-computers at Gehirn.

The PTOS enables the transplantation of human personality aspects into
7th generation organic computers, creating systems that think and reason
with human-like characteristics while maintaining computational precision.

This module implements:
- PersonalityMatrix: The core personality encoding structure
- OrganicProcessor: Simulation of bio-neural processing
- TransplantProcedure: The process of encoding personality into a matrix
- MemoryEngram: Persistent memory structures within the organic substrate
"""

from .matrix import PersonalityMatrix, PersonalityFragment
from .organic import OrganicProcessor, NeuralCluster, Synapse
from .transplant import TransplantProcedure, TransplantResult
from .engram import MemoryEngram, EngramType

__all__ = [
    "PersonalityMatrix",
    "PersonalityFragment", 
    "OrganicProcessor",
    "NeuralCluster",
    "Synapse",
    "TransplantProcedure",
    "TransplantResult",
    "MemoryEngram",
    "EngramType",
]
