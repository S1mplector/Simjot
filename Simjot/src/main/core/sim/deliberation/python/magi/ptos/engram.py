"""
Memory Engram
=============

Engrams are the persistent memory structures within the MAGI's organic substrate.
They encode experiences, learned patterns, and accumulated wisdom that shape
how each MAGI unit processes new information.

Unlike conventional computer memory, engrams are:
- Associatively linked (memories trigger related memories)
- Emotionally colored (emotional state affects retrieval)
- Subject to consolidation (important memories strengthen over time)
- Reconstructive (memories are partially rebuilt on retrieval)

The engram system allows MAGI units to learn from experience and develop
beyond their initial personality transplant.
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Set
from enum import Enum
from datetime import datetime
import hashlib


class EngramType(Enum):
    """Types of memory engrams stored in the organic substrate."""
    EPISODIC = "episodic"       # Specific events and experiences
    SEMANTIC = "semantic"       # Facts and conceptual knowledge
    PROCEDURAL = "procedural"   # Skills and processes
    EMOTIONAL = "emotional"     # Emotional memories and associations
    PROSPECTIVE = "prospective" # Future intentions and plans


class EngramStrength(Enum):
    """Strength/durability of an engram."""
    TRANSIENT = "transient"     # Short-term, will decay
    LABILE = "labile"           # Unstable, may be modified
    STABLE = "stable"           # Consolidated, resistant to change
    PERMANENT = "permanent"     # Core memories, will not decay


@dataclass
class EngramLink:
    """Association between two engrams."""
    target_id: str
    strength: float  # 0.0 to 1.0
    link_type: str   # "causal", "temporal", "semantic", "emotional"
    bidirectional: bool = False


@dataclass
class MemoryEngram:
    """
    A single memory unit within the MAGI's organic substrate.
    
    Engrams can be retrieved through direct query or through
    spreading activation from associated engrams.
    """
    
    engram_id: str
    engram_type: EngramType
    strength: EngramStrength
    
    # Content
    content: str
    summary: str
    keywords: List[str] = field(default_factory=list)
    
    # Emotional properties
    emotional_valence: float = 0.0  # -1.0 (negative) to 1.0 (positive)
    emotional_intensity: float = 0.5  # 0.0 to 1.0
    associated_emotions: List[str] = field(default_factory=list)
    
    # Temporal properties
    formation_time: datetime = field(default_factory=datetime.now)
    last_retrieval: Optional[datetime] = None
    retrieval_count: int = 0
    
    # Consolidation
    consolidation_level: float = 0.0  # 0.0 (fresh) to 1.0 (fully consolidated)
    rehearsal_count: int = 0
    
    # Associations
    links: List[EngramLink] = field(default_factory=list)
    
    # Retrieval properties
    activation_threshold: float = 0.3
    current_activation: float = 0.0
    
    # Metadata
    source: str = ""  # Where this memory came from
    reliability: float = 1.0  # How reliable/accurate this memory is
    
    def retrieve(self) -> str:
        """Retrieve this engram's content, updating access metadata."""
        self.retrieval_count += 1
        self.last_retrieval = datetime.now()
        
        # Strengthen through retrieval
        if self.strength == EngramStrength.TRANSIENT:
            self.consolidation_level += 0.1
        
        return self.content
    
    def activate(self, amount: float) -> bool:
        """
        Activate this engram. Returns True if activation exceeds threshold.
        """
        self.current_activation += amount
        return self.current_activation >= self.activation_threshold
    
    def decay(self, amount: float = 0.01) -> None:
        """Apply decay to activation level."""
        self.current_activation = max(0.0, self.current_activation - amount)
    
    def consolidate(self) -> None:
        """Strengthen this engram through consolidation."""
        self.consolidation_level = min(1.0, self.consolidation_level + 0.1)
        self.rehearsal_count += 1
        
        # Upgrade strength if sufficiently consolidated
        if self.consolidation_level > 0.8 and self.strength == EngramStrength.TRANSIENT:
            self.strength = EngramStrength.LABILE
        elif self.consolidation_level > 0.95 and self.strength == EngramStrength.LABILE:
            self.strength = EngramStrength.STABLE
    
    def add_link(self, target_id: str, strength: float, link_type: str) -> None:
        """Add an associative link to another engram."""
        self.links.append(EngramLink(
            target_id=target_id,
            strength=strength,
            link_type=link_type
        ))
    
    def get_linked_ids(self, min_strength: float = 0.3) -> List[str]:
        """Get IDs of linked engrams above minimum strength."""
        return [link.target_id for link in self.links if link.strength >= min_strength]
    
    def to_dict(self) -> Dict[str, Any]:
        """Serialize engram to dictionary."""
        return {
            "engram_id": self.engram_id,
            "engram_type": self.engram_type.value,
            "strength": self.strength.value,
            "summary": self.summary,
            "keywords": self.keywords,
            "emotional_valence": self.emotional_valence,
            "consolidation_level": self.consolidation_level,
            "retrieval_count": self.retrieval_count,
        }


class EngramStore:
    """
    Storage and retrieval system for memory engrams.
    
    Implements associative memory with spreading activation.
    """
    
    def __init__(self, designation: str):
        self.designation = designation
        self.engrams: Dict[str, MemoryEngram] = {}
        self._keyword_index: Dict[str, Set[str]] = {}
        self._type_index: Dict[EngramType, Set[str]] = {t: set() for t in EngramType}
    
    def store(self, engram: MemoryEngram) -> None:
        """Store an engram in the memory system."""
        self.engrams[engram.engram_id] = engram
        
        # Update keyword index
        for keyword in engram.keywords:
            if keyword not in self._keyword_index:
                self._keyword_index[keyword] = set()
            self._keyword_index[keyword].add(engram.engram_id)
        
        # Update type index
        self._type_index[engram.engram_type].add(engram.engram_id)
    
    def retrieve_by_id(self, engram_id: str) -> Optional[MemoryEngram]:
        """Retrieve a specific engram by ID."""
        engram = self.engrams.get(engram_id)
        if engram:
            engram.retrieve()
        return engram
    
    def search_by_keywords(self, keywords: List[str], top_k: int = 5) -> List[MemoryEngram]:
        """Search for engrams matching keywords."""
        candidate_ids: Dict[str, int] = {}
        
        for keyword in keywords:
            keyword_lower = keyword.lower()
            for idx_keyword, engram_ids in self._keyword_index.items():
                if keyword_lower in idx_keyword.lower():
                    for eid in engram_ids:
                        candidate_ids[eid] = candidate_ids.get(eid, 0) + 1
        
        # Sort by match count and return top k
        sorted_ids = sorted(candidate_ids.keys(), key=lambda x: candidate_ids[x], reverse=True)
        return [self.engrams[eid] for eid in sorted_ids[:top_k] if eid in self.engrams]
    
    def search_by_type(self, engram_type: EngramType) -> List[MemoryEngram]:
        """Get all engrams of a specific type."""
        return [self.engrams[eid] for eid in self._type_index[engram_type] if eid in self.engrams]
    
    def spreading_activation(
        self, 
        seed_ids: List[str], 
        depth: int = 2,
        activation_decay: float = 0.5
    ) -> List[MemoryEngram]:
        """
        Perform spreading activation from seed engrams.
        
        This simulates associative memory retrieval where activating
        one memory can trigger related memories.
        """
        activated: Dict[str, float] = {}
        current_level = seed_ids
        current_activation = 1.0
        
        for _ in range(depth):
            next_level = []
            
            for engram_id in current_level:
                if engram_id in self.engrams:
                    engram = self.engrams[engram_id]
                    engram.activate(current_activation)
                    activated[engram_id] = max(
                        activated.get(engram_id, 0), 
                        current_activation
                    )
                    
                    # Get linked engrams for next level
                    linked = engram.get_linked_ids()
                    next_level.extend(linked)
            
            current_level = list(set(next_level) - set(activated.keys()))
            current_activation *= activation_decay
        
        # Return activated engrams sorted by activation level
        sorted_ids = sorted(activated.keys(), key=lambda x: activated[x], reverse=True)
        return [self.engrams[eid] for eid in sorted_ids if eid in self.engrams]
    
    def emotional_filter(
        self, 
        engrams: List[MemoryEngram],
        valence: Optional[float] = None,
        min_intensity: float = 0.0
    ) -> List[MemoryEngram]:
        """Filter engrams by emotional properties."""
        result = []
        
        for engram in engrams:
            if engram.emotional_intensity < min_intensity:
                continue
            
            if valence is not None:
                # Check if valence is in the right direction
                if valence > 0 and engram.emotional_valence < 0:
                    continue
                if valence < 0 and engram.emotional_valence > 0:
                    continue
            
            result.append(engram)
        
        return result
    
    def consolidation_pass(self) -> int:
        """
        Run a consolidation pass on all engrams.
        
        This simulates the memory consolidation that happens during
        downtime, strengthening important memories and allowing
        transient ones to decay.
        """
        consolidated_count = 0
        
        for engram in self.engrams.values():
            # Consolidate frequently accessed memories
            if engram.retrieval_count > 3:
                engram.consolidate()
                consolidated_count += 1
            
            # Decay transient memories
            elif engram.strength == EngramStrength.TRANSIENT:
                engram.consolidation_level -= 0.05
                if engram.consolidation_level < -0.5:
                    # Memory fades away (but we keep it for now)
                    engram.reliability *= 0.9
        
        return consolidated_count
    
    def get_statistics(self) -> Dict[str, Any]:
        """Get statistics about the engram store."""
        return {
            "total_engrams": len(self.engrams),
            "by_type": {t.value: len(ids) for t, ids in self._type_index.items()},
            "by_strength": {
                s.value: len([e for e in self.engrams.values() if e.strength == s])
                for s in EngramStrength
            },
            "keywords_indexed": len(self._keyword_index),
            "avg_links_per_engram": sum(len(e.links) for e in self.engrams.values()) / max(len(self.engrams), 1),
        }
