"""
Consensus Protocol
==================

The voting and consensus mechanisms used by the MAGI System.

Different types of decisions require different consensus thresholds:
- Routine operations: Simple majority (2/3)
- Important decisions: Majority with confidence weighting
- Critical decisions: Unanimous agreement required
- Self-destruct: Unanimous agreement from all MAGI + human authorization

The protocol implements multiple rounds of deliberation when initial
consensus is not reached, allowing units to refine their positions
after considering others' arguments.
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Tuple
from enum import Enum
from datetime import datetime
import hashlib


class DecisionCategory(Enum):
    """Categories of decisions with different consensus requirements."""
    ROUTINE = "routine"           # Simple majority sufficient
    IMPORTANT = "important"       # Weighted majority required  
    CRITICAL = "critical"         # Unanimous required
    SELF_DESTRUCT = "self_destruct"  # Unanimous + human authorization
    ADVISORY = "advisory"         # No binding decision, just opinions


class VoteType(Enum):
    """Types of votes a MAGI unit can cast."""
    APPROVE = "approve"
    REJECT = "reject"
    CONDITIONAL = "conditional"
    ABSTAIN = "abstain"
    DEFER = "defer"


class ConsensusState(Enum):
    """State of consensus in a voting session."""
    PENDING = "pending"
    UNANIMOUS_APPROVE = "unanimous_approve"
    UNANIMOUS_REJECT = "unanimous_reject"
    MAJORITY_APPROVE = "majority_approve"
    MAJORITY_REJECT = "majority_reject"
    CONDITIONAL = "conditional"
    DEADLOCK = "deadlock"
    DEFERRED = "deferred"


@dataclass
class Vote:
    """A single vote from a MAGI unit."""
    unit_designation: str
    vote_type: VoteType
    confidence: float  # 0.0 to 1.0
    reasoning: str
    conditions: List[str] = field(default_factory=list)
    timestamp: datetime = field(default_factory=datetime.now)
    round_number: int = 1
    
    @property
    def weighted_value(self) -> float:
        """Calculate weighted vote value."""
        if self.vote_type == VoteType.APPROVE:
            return self.confidence
        elif self.vote_type == VoteType.REJECT:
            return -self.confidence
        elif self.vote_type == VoteType.CONDITIONAL:
            return self.confidence * 0.5
        else:
            return 0.0


@dataclass
class ConsensusResult:
    """Result of a consensus voting session."""
    session_id: str
    query: str
    category: DecisionCategory
    state: ConsensusState
    
    # Vote details
    votes: Dict[str, Vote] = field(default_factory=dict)
    rounds_completed: int = 0
    
    # Outcome
    action_authorized: bool = False
    final_decision: Optional[str] = None
    
    # Analysis
    weighted_score: float = 0.0
    approval_count: int = 0
    rejection_count: int = 0
    
    # Conditions and reasoning
    unified_conditions: List[str] = field(default_factory=list)
    synthesis: str = ""
    
    # Metadata
    timestamp: datetime = field(default_factory=datetime.now)
    processing_time_ms: float = 0.0
    
    def to_dict(self) -> Dict[str, Any]:
        """Serialize to dictionary."""
        return {
            "session_id": self.session_id,
            "query": self.query,
            "category": self.category.value,
            "state": self.state.value,
            "action_authorized": self.action_authorized,
            "final_decision": self.final_decision,
            "weighted_score": self.weighted_score,
            "votes": {k: {"type": v.vote_type.value, "confidence": v.confidence} 
                     for k, v in self.votes.items()},
            "conditions": self.unified_conditions,
            "synthesis": self.synthesis,
        }


@dataclass
class VotingSession:
    """An active voting session."""
    session_id: str
    query: str
    category: DecisionCategory
    
    # Configuration
    max_rounds: int = 3
    require_unanimous: bool = False
    confidence_threshold: float = 0.6
    
    # State
    current_round: int = 0
    is_active: bool = True
    votes_by_round: Dict[int, Dict[str, Vote]] = field(default_factory=dict)
    
    # Cross-examination
    responses: Dict[str, Dict[str, str]] = field(default_factory=dict)
    
    def add_vote(self, vote: Vote) -> None:
        """Add a vote to the current round."""
        if self.current_round not in self.votes_by_round:
            self.votes_by_round[self.current_round] = {}
        self.votes_by_round[self.current_round][vote.unit_designation] = vote
    
    def get_current_votes(self) -> Dict[str, Vote]:
        """Get votes from current round."""
        return self.votes_by_round.get(self.current_round, {})
    
    def get_previous_positions(self) -> Dict[str, str]:
        """Get position summaries from previous round for deliberation."""
        if self.current_round < 1:
            return {}
        
        prev_votes = self.votes_by_round.get(self.current_round - 1, {})
        return {
            designation: f"{vote.vote_type.value}: {vote.reasoning[:200]}..."
            for designation, vote in prev_votes.items()
        }
    
    def advance_round(self) -> bool:
        """Advance to next round. Returns False if max rounds reached."""
        if self.current_round >= self.max_rounds:
            return False
        self.current_round += 1
        return True


class ConsensusProtocol:
    """
    Implements the MAGI consensus protocol.
    
    The protocol manages voting sessions, tracks consensus state,
    and determines when actionable decisions have been reached.
    """
    
    def __init__(self):
        self.active_sessions: Dict[str, VotingSession] = {}
        self.completed_sessions: Dict[str, ConsensusResult] = {}
    
    def create_session(
        self,
        query: str,
        category: DecisionCategory = DecisionCategory.IMPORTANT,
        require_unanimous: bool = False
    ) -> VotingSession:
        """Create a new voting session."""
        session_id = self._generate_session_id(query)
        
        # Override unanimous requirement for critical categories
        if category in [DecisionCategory.CRITICAL, DecisionCategory.SELF_DESTRUCT]:
            require_unanimous = True
        
        session = VotingSession(
            session_id=session_id,
            query=query,
            category=category,
            require_unanimous=require_unanimous,
            max_rounds=3 if category != DecisionCategory.ROUTINE else 1,
        )
        
        self.active_sessions[session_id] = session
        return session
    
    def _generate_session_id(self, query: str) -> str:
        """Generate unique session ID."""
        content = f"{query}-{datetime.now().isoformat()}"
        return hashlib.sha256(content.encode()).hexdigest()[:12]
    
    def submit_vote(self, session_id: str, vote: Vote) -> bool:
        """Submit a vote to an active session."""
        session = self.active_sessions.get(session_id)
        if not session or not session.is_active:
            return False
        
        vote.round_number = session.current_round
        session.add_vote(vote)
        return True
    
    def check_consensus(self, session_id: str) -> Tuple[ConsensusState, bool]:
        """
        Check if consensus has been reached in a session.
        
        Returns (consensus_state, is_final)
        """
        session = self.active_sessions.get(session_id)
        if not session:
            return ConsensusState.PENDING, False
        
        votes = session.get_current_votes()
        if len(votes) < 3:
            return ConsensusState.PENDING, False
        
        # Count vote types
        approvals = sum(1 for v in votes.values() if v.vote_type == VoteType.APPROVE)
        rejections = sum(1 for v in votes.values() if v.vote_type == VoteType.REJECT)
        conditionals = sum(1 for v in votes.values() if v.vote_type == VoteType.CONDITIONAL)
        
        # Calculate weighted score
        weighted_score = sum(v.weighted_value for v in votes.values())
        
        # Determine consensus state
        if approvals == 3:
            return ConsensusState.UNANIMOUS_APPROVE, True
        elif rejections == 3:
            return ConsensusState.UNANIMOUS_REJECT, True
        elif session.require_unanimous:
            # For unanimous requirements, anything else is a failure
            if session.current_round >= session.max_rounds:
                return ConsensusState.DEADLOCK, True
            else:
                return ConsensusState.PENDING, False
        elif approvals >= 2:
            return ConsensusState.MAJORITY_APPROVE, True
        elif rejections >= 2:
            return ConsensusState.MAJORITY_REJECT, True
        elif conditionals >= 2:
            return ConsensusState.CONDITIONAL, True
        else:
            if session.current_round >= session.max_rounds:
                return ConsensusState.DEADLOCK, True
            return ConsensusState.PENDING, False
    
    def finalize_session(self, session_id: str) -> ConsensusResult:
        """Finalize a voting session and return the result."""
        session = self.active_sessions.get(session_id)
        if not session:
            raise ValueError(f"Session not found: {session_id}")
        
        votes = session.get_current_votes()
        state, _ = self.check_consensus(session_id)
        
        # Calculate metrics
        weighted_score = sum(v.weighted_value for v in votes.values())
        approval_count = sum(1 for v in votes.values() if v.vote_type == VoteType.APPROVE)
        rejection_count = sum(1 for v in votes.values() if v.vote_type == VoteType.REJECT)
        
        # Collect conditions
        all_conditions = []
        for vote in votes.values():
            all_conditions.extend(vote.conditions)
        
        # Determine authorization
        action_authorized = state in [
            ConsensusState.UNANIMOUS_APPROVE,
            ConsensusState.MAJORITY_APPROVE,
        ]
        
        if session.category == DecisionCategory.SELF_DESTRUCT:
            action_authorized = (state == ConsensusState.UNANIMOUS_APPROVE)
        
        # Determine final decision
        if state in [ConsensusState.UNANIMOUS_APPROVE, ConsensusState.MAJORITY_APPROVE]:
            final_decision = "APPROVE"
        elif state in [ConsensusState.UNANIMOUS_REJECT, ConsensusState.MAJORITY_REJECT]:
            final_decision = "REJECT"
        elif state == ConsensusState.CONDITIONAL:
            final_decision = "CONDITIONAL"
        else:
            final_decision = None
        
        result = ConsensusResult(
            session_id=session_id,
            query=session.query,
            category=session.category,
            state=state,
            votes=votes,
            rounds_completed=session.current_round,
            action_authorized=action_authorized,
            final_decision=final_decision,
            weighted_score=weighted_score,
            approval_count=approval_count,
            rejection_count=rejection_count,
            unified_conditions=list(set(all_conditions)),
        )
        
        # Move to completed
        session.is_active = False
        self.completed_sessions[session_id] = result
        del self.active_sessions[session_id]
        
        return result
    
    def needs_another_round(self, session_id: str) -> bool:
        """Check if session needs another deliberation round."""
        session = self.active_sessions.get(session_id)
        if not session or not session.is_active:
            return False
        
        state, is_final = self.check_consensus(session_id)
        
        if is_final:
            return False
        
        if state == ConsensusState.PENDING and len(session.get_current_votes()) == 3:
            # All votes in but no consensus - need another round
            return session.current_round < session.max_rounds
        
        return False
    
    def get_dissenting_units(self, session_id: str) -> List[str]:
        """Get list of units that dissent from majority."""
        session = self.active_sessions.get(session_id) or self.completed_sessions.get(session_id)
        if not session:
            return []
        
        if isinstance(session, VotingSession):
            votes = session.get_current_votes()
        else:
            votes = session.votes
        
        # Find majority position
        approvals = [d for d, v in votes.items() if v.vote_type == VoteType.APPROVE]
        rejections = [d for d, v in votes.items() if v.vote_type == VoteType.REJECT]
        
        if len(approvals) >= 2:
            return rejections
        elif len(rejections) >= 2:
            return approvals
        return []
    
    @staticmethod
    def requires_human_authorization(category: DecisionCategory) -> bool:
        """Check if decision category requires human authorization."""
        return category == DecisionCategory.SELF_DESTRUCT
