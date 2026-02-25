"""
MAGI Brains
===========

The three MAGI supercomputer personalities, each representing
a distinct aspect of Dr. Naoko Akagi's psyche.

These personalities were implanted using the Personality Transplant OS
during the creation of the MAGI system at Gehirn.

Units:
- MELCHIOR (MAGI-1): The Scientist
- BALTHASAR (MAGI-2): The Mother  
- CASPER (MAGI-3): The Woman
"""

from .melchior import MELCHIOR, MELCHIOR_PERSONALITY, create_melchior, transplant_melchior
from .balthasar import BALTHASAR, BALTHASAR_PERSONALITY, create_balthasar, transplant_balthasar
from .casper import CASPER, CASPER_PERSONALITY, create_casper, transplant_casper

__all__ = [
    # Pre-instantiated brains
    "MELCHIOR",
    "BALTHASAR", 
    "CASPER",
    
    # Personality definitions
    "MELCHIOR_PERSONALITY",
    "BALTHASAR_PERSONALITY",
    "CASPER_PERSONALITY",
    
    # Factory functions
    "create_melchior",
    "create_balthasar",
    "create_casper",
    
    # PTOS Transplant functions
    "transplant_melchior",
    "transplant_balthasar",
    "transplant_casper",
]
