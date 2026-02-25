"""
Decision submodule
==================

Data structures representing the decision-making process, including
individual verdicts, deliberation rounds, and final synthesized decisions.
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any
from enum import Enum
from datetime import datetime
import uuid


class VerdictType(Enum):
    """Classification of a brain's verdict on a question."""
    APPROVE = "approve"           # Clear yes/agreement
    REJECT = "reject"             # Clear no/disagreement
    CONDITIONAL = "conditional"   # Yes with conditions
    ABSTAIN = "abstain"           # Cannot or will not decide
    DEFER = "defer"               # Needs more information
    INFO = "info"                 # Non-yes/no question - informational response


class ConsensusType(Enum):
    """Type of consensus reached by the MAGI system."""
    UNANIMOUS = "unanimous"       # All three agree
    MAJORITY = "majority"         # Two agree, one dissents
    DEADLOCK = "deadlock"         # No clear majority
    CONDITIONAL = "conditional"   # Agreement with conditions
    DEFERRED = "deferred"         # Decision postponed
    INFORMATIONAL = "informational"  # Not a decision question


@dataclass
class Argument:
    """A single logical argument or point made during deliberation."""
    claim: str
    reasoning: str
    evidence: List[str] = field(default_factory=list)
    confidence: float = 0.7
    counterpoints: List[str] = field(default_factory=list)


@dataclass 
class Verdict:
    """
    A single brain's verdict on a question.
    
    Contains the decision, reasoning, confidence level, and any conditions
    or reservations the brain has about its position.
    """
    
    brain_name: str
    verdict_type: VerdictType
    confidence: float  # 0.0 to 1.0
    
    # Core response
    summary: str  # One-line summary
    reasoning: str  # Full reasoning
    
    # Supporting details
    arguments: List[Argument] = field(default_factory=list)
    conditions: List[str] = field(default_factory=list)
    reservations: List[str] = field(default_factory=list)
    
    # Metadata
    timestamp: datetime = field(default_factory=datetime.now)
    deliberation_round: int = 1
    
    # Cross-examination responses
    responses_to_others: Dict[str, str] = field(default_factory=dict)
    
    def to_dict(self) -> Dict[str, Any]:
        """Serialize verdict to dictionary."""
        return {
            "brain_name": self.brain_name,
            "verdict_type": self.verdict_type.value,
            "confidence": self.confidence,
            "summary": self.summary,
            "reasoning": self.reasoning,
            "conditions": self.conditions,
            "reservations": self.reservations,
            "deliberation_round": self.deliberation_round,
        }
    
    @property
    def is_affirmative(self) -> bool:
        """Check if this is generally a positive/approving verdict."""
        return self.verdict_type in (VerdictType.APPROVE, VerdictType.CONDITIONAL)
    
    @property
    def is_negative(self) -> bool:
        """Check if this is a negative/rejecting verdict."""
        return self.verdict_type == VerdictType.REJECT
    
    @property
    def weighted_score(self) -> float:
        """
        Calculate a weighted score for voting.
        Positive for approve, negative for reject, scaled by confidence.
        """
        if self.verdict_type == VerdictType.APPROVE:
            return self.confidence
        elif self.verdict_type == VerdictType.REJECT:
            return -self.confidence
        elif self.verdict_type == VerdictType.CONDITIONAL:
            return self.confidence * 0.5
        else:
            return 0.0


@dataclass
class DeliberationRound:
    """
    A single round of deliberation among the MAGI brains.
    
    Each round consists of:
    1. Initial positions from each brain
    2. Cross-examination of other positions
    3. Updated positions after considering others' arguments
    """
    
    round_number: int
    verdicts: Dict[str, Verdict]  # brain_name -> verdict
    cross_examinations: Dict[str, Dict[str, str]] = field(default_factory=dict)
    synthesis: Optional[str] = None
    
    @property
    def has_consensus(self) -> bool:
        """Check if all brains agree on verdict type."""
        types = [v.verdict_type for v in self.verdicts.values()]
        return len(set(types)) == 1
    
    @property
    def majority_verdict(self) -> Optional[VerdictType]:
        """Get the majority verdict type, if one exists."""
        from collections import Counter
        types = [v.verdict_type for v in self.verdicts.values()]
        counts = Counter(types)
        most_common = counts.most_common(1)
        if most_common and most_common[0][1] >= 2:
            return most_common[0][0]
        return None
    
    def get_dissenting_brains(self) -> List[str]:
        """Get names of brains that dissent from the majority."""
        majority = self.majority_verdict
        if not majority:
            return []
        return [name for name, v in self.verdicts.items() 
                if v.verdict_type != majority]


@dataclass
class Decision:
    """
    The final synthesized decision from the MAGI system.
    
    Represents the outcome of the full deliberation process,
    including all rounds, the final consensus, and any dissenting opinions.
    """
    
    id: str = field(default_factory=lambda: str(uuid.uuid4())[:8])
    question: str = ""
    question_type: str = "unknown"  # "yes_no", "open", "analytical", etc.
    
    # Deliberation history
    rounds: List[DeliberationRound] = field(default_factory=list)
    
    # Final outcome
    consensus_type: ConsensusType = ConsensusType.DEADLOCK
    final_verdict: Optional[VerdictType] = None
    final_answer: str = ""
    
    # Individual final positions
    final_verdicts: Dict[str, Verdict] = field(default_factory=dict)
    
    # Synthesis
    synthesis: str = ""
    key_agreements: List[str] = field(default_factory=list)
    key_disagreements: List[str] = field(default_factory=list)
    conditions: List[str] = field(default_factory=list)
    
    # Metadata
    timestamp: datetime = field(default_factory=datetime.now)
    processing_time_ms: float = 0.0
    
    def to_dict(self) -> Dict[str, Any]:
        """Serialize decision to dictionary."""
        return {
            "id": self.id,
            "question": self.question,
            "question_type": self.question_type,
            "consensus_type": self.consensus_type.value,
            "final_verdict": self.final_verdict.value if self.final_verdict else None,
            "final_answer": self.final_answer,
            "synthesis": self.synthesis,
            "key_agreements": self.key_agreements,
            "key_disagreements": self.key_disagreements,
            "conditions": self.conditions,
            "rounds": len(self.rounds),
            "verdicts": {k: v.to_dict() for k, v in self.final_verdicts.items()},
        }
    
    @property
    def status(self) -> str:
        """Get a simple status string for UI display."""
        if self.consensus_type == ConsensusType.UNANIMOUS:
            if self.final_verdict == VerdictType.APPROVE:
                return "yes"
            elif self.final_verdict == VerdictType.REJECT:
                return "no"
        elif self.consensus_type == ConsensusType.MAJORITY:
            if self.final_verdict == VerdictType.APPROVE:
                return "yes"
            elif self.final_verdict == VerdictType.REJECT:
                return "no"
        elif self.consensus_type == ConsensusType.CONDITIONAL:
            return "conditional"
        elif self.consensus_type == ConsensusType.INFORMATIONAL:
            return "info"
        elif self.consensus_type == ConsensusType.DEADLOCK:
            return "deadlock"
        return "info"
    
    def get_brain_status(self, brain_name: str) -> str:
        """Get status string for a specific brain."""
        if brain_name not in self.final_verdicts:
            return "info"
        
        verdict = self.final_verdicts[brain_name]
        if verdict.verdict_type == VerdictType.APPROVE:
            return "yes"
        elif verdict.verdict_type == VerdictType.REJECT:
            return "no"
        elif verdict.verdict_type == VerdictType.CONDITIONAL:
            return "conditional"
        elif verdict.verdict_type == VerdictType.INFO:
            return "info"
        return "info"
