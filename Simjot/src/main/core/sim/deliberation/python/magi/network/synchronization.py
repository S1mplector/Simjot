"""
Inter-MAGI Synchronization
==========================

Handles synchronization between MAGI units during complex deliberations.

This module implements the sophisticated back-and-forth between the three
personality aspects when facing difficult decisions. Unlike simple voting,
true synchronization involves:

1. Position sharing and mutual understanding
2. Argument construction and rebuttal
3. Value acknowledgment across aspects
4. Dynamic confidence adjustment based on other aspects' reasoning
5. Emergent synthesis through dialectical process

The synchronization process mirrors the internal dialogue Dr. Naoko Akagi
might have had between her scientist, mother, and woman selves.

"The MAGI don't simply vote. They deliberate. They argue. They synthesize."
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Tuple, Callable
from enum import Enum
from datetime import datetime
import hashlib


class SyncPhase(Enum):
    """Phases of the synchronization process."""
    INITIALIZATION = "initialization"
    POSITION_FORMATION = "position_formation"
    CROSS_EXAMINATION = "cross_examination"
    REBUTTAL = "rebuttal"
    RECONSIDERATION = "reconsideration"
    SYNTHESIS_ATTEMPT = "synthesis_attempt"
    FINAL_VERDICT = "final_verdict"


class ArgumentType(Enum):
    """Types of arguments an aspect can make."""
    ASSERTION = "assertion"           # Statement of position
    EVIDENCE = "evidence"             # Supporting data/logic
    APPEAL_TO_VALUE = "appeal_value"  # Invoking core values
    CONCESSION = "concession"         # Acknowledging other's point
    REBUTTAL = "rebuttal"             # Countering an argument
    SYNTHESIS = "synthesis"           # Proposing merged position
    ESCALATION = "escalation"         # Raising stakes of disagreement


@dataclass
class Argument:
    """An argument made by one MAGI aspect during deliberation."""
    
    argument_id: str
    source_aspect: str  # MELCHIOR, BALTHASAR, or CASPER
    argument_type: ArgumentType
    content: str
    
    # Targeting
    target_aspect: Optional[str] = None  # If directed at specific aspect
    responds_to: Optional[str] = None    # ID of argument being responded to
    
    # Strength and reception
    conviction: float = 0.7              # How strongly held (0-1)
    persuasiveness: float = 0.5          # How persuasive to others (0-1)
    
    # Value grounding
    invoked_values: List[str] = field(default_factory=list)
    
    timestamp: datetime = field(default_factory=datetime.now)


@dataclass
class AspectState:
    """Current state of a MAGI aspect during synchronization."""
    
    aspect: str
    current_position: str  # "APPROVE", "REJECT", "CONDITIONAL", "UNCERTAIN"
    confidence: float = 0.5
    
    # Core state
    active_values: List[str] = field(default_factory=list)
    emotional_state: Dict[str, float] = field(default_factory=dict)
    
    # Deliberation state
    arguments_made: List[str] = field(default_factory=list)  # Argument IDs
    arguments_received: List[str] = field(default_factory=list)
    concessions_made: List[str] = field(default_factory=list)
    
    # Openness to change
    openness: float = 0.5
    stubbornness_remaining: float = 1.0  # Decreases as good arguments land
    
    # Trust in other aspects
    trust: Dict[str, float] = field(default_factory=lambda: {
        "MELCHIOR": 0.7,
        "BALTHASAR": 0.7,
        "CASPER": 0.7,
    })


@dataclass
class SyncResult:
    """Result of a synchronization process."""
    
    sync_id: str
    question: str
    
    # Outcome
    consensus_reached: bool = False
    consensus_type: str = ""  # "unanimous", "majority", "conditional", "deadlock"
    final_position: str = ""
    
    # Aspect final states
    final_states: Dict[str, AspectState] = field(default_factory=dict)
    
    # Deliberation record
    phases_completed: List[SyncPhase] = field(default_factory=list)
    total_arguments: int = 0
    synthesis_attempts: int = 0
    
    # Quality metrics
    deliberation_depth: int = 0
    mutual_understanding: float = 0.0
    synthesis_quality: float = 0.0
    
    # Timing
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    
    @property
    def duration_ms(self) -> float:
        if self.start_time and self.end_time:
            return (self.end_time - self.start_time).total_seconds() * 1000
        return 0.0


class AspectDynamics:
    """
    Models the dynamic interactions between MAGI aspects.
    
    Each aspect has characteristic ways of arguing, conceding,
    and responding to the other aspects based on Dr. Akagi's
    internal psychological dynamics.
    """
    
    # How each aspect typically views the others
    ASPECT_RELATIONSHIPS = {
        "MELCHIOR": {
            "BALTHASAR": {
                "respect": 0.7,
                "frustration": 0.4,  # "Too emotional, ignores data"
                "common_ground": ["concern for outcomes", "long-term thinking"],
                "typical_conflict": "Evidence vs Intuition",
            },
            "CASPER": {
                "respect": 0.6,
                "frustration": 0.5,  # "Irrational, impulsive"
                "common_ground": ["openness to new ideas", "rejection of stagnation"],
                "typical_conflict": "Logic vs Passion",
            },
        },
        "BALTHASAR": {
            "MELCHIOR": {
                "respect": 0.8,
                "frustration": 0.3,  # "Cold, misses human element"
                "common_ground": ["desire for good outcomes", "careful analysis"],
                "typical_conflict": "Protection vs Progress",
            },
            "CASPER": {
                "respect": 0.6,
                "frustration": 0.5,  # "Reckless, self-focused"
                "common_ground": ["love for others", "desire for connection"],
                "typical_conflict": "Safety vs Freedom",
            },
        },
        "CASPER": {
            "MELCHIOR": {
                "respect": 0.7,
                "frustration": 0.4,  # "Lifeless, misses meaning"
                "common_ground": ["pursuit of understanding", "intellectual honesty"],
                "typical_conflict": "Meaning vs Mechanism",
            },
            "BALTHASAR": {
                "respect": 0.7,
                "frustration": 0.4,  # "Overprotective, limiting"
                "common_ground": ["love", "care for others"],
                "typical_conflict": "Freedom vs Security",
            },
        },
    }
    
    # Characteristic argument styles
    ARGUMENT_STYLES = {
        "MELCHIOR": {
            "primary_type": ArgumentType.EVIDENCE,
            "backup_type": ArgumentType.APPEAL_TO_VALUE,
            "concession_threshold": 0.75,  # High bar for conceding
            "rebuttal_tendency": 0.8,
            "synthesis_openness": 0.6,
            "emotional_appeals_effectiveness": 0.3,  # Resistant to emotion
        },
        "BALTHASAR": {
            "primary_type": ArgumentType.APPEAL_TO_VALUE,
            "backup_type": ArgumentType.EVIDENCE,
            "concession_threshold": 0.5,
            "rebuttal_tendency": 0.6,
            "synthesis_openness": 0.7,
            "emotional_appeals_effectiveness": 0.8,  # Responds to emotion
        },
        "CASPER": {
            "primary_type": ArgumentType.APPEAL_TO_VALUE,
            "backup_type": ArgumentType.ASSERTION,
            "concession_threshold": 0.6,
            "rebuttal_tendency": 0.7,
            "synthesis_openness": 0.8,  # Most open to synthesis
            "emotional_appeals_effectiveness": 0.9,
        },
    }
    
    @classmethod
    def calculate_persuasion(
        cls,
        argument: Argument,
        target_aspect: str
    ) -> float:
        """Calculate how persuasive an argument is to a target aspect."""
        source = argument.source_aspect
        
        if source == target_aspect:
            return 0.0  # Can't persuade yourself
        
        # Base persuasiveness
        persuasion = argument.conviction * 0.5
        
        # Adjust based on relationship
        relationship = cls.ASPECT_RELATIONSHIPS.get(target_aspect, {}).get(source, {})
        persuasion *= relationship.get("respect", 0.5)
        
        # Adjust based on target's style preferences
        target_style = cls.ARGUMENT_STYLES.get(target_aspect, {})
        
        if argument.argument_type == ArgumentType.APPEAL_TO_VALUE:
            persuasion *= target_style.get("emotional_appeals_effectiveness", 0.5)
        elif argument.argument_type == ArgumentType.EVIDENCE:
            # Evidence-based arguments work better on MELCHIOR
            if target_aspect == "MELCHIOR":
                persuasion *= 1.3
        
        return min(1.0, persuasion)
    
    @classmethod
    def find_common_ground(cls, aspect_a: str, aspect_b: str) -> List[str]:
        """Find common ground between two aspects."""
        relationship = cls.ASPECT_RELATIONSHIPS.get(aspect_a, {}).get(aspect_b, {})
        return relationship.get("common_ground", [])


class SynchronizationProtocol:
    """
    Manages the synchronization process between MAGI units.
    
    This protocol orchestrates deep deliberation, going beyond
    simple voting to achieve genuine synthesis where possible.
    """
    
    def __init__(self):
        self.active_syncs: Dict[str, SyncResult] = {}
        self.argument_store: Dict[str, Argument] = {}
        self.sync_history: List[SyncResult] = []
        
        # Configuration
        self.max_deliberation_rounds: int = 5
        self.synthesis_threshold: float = 0.7
        self.deadlock_threshold: int = 3  # Rounds without progress
        
        # Callbacks for aspect consultation
        self._aspect_callbacks: Dict[str, Callable] = {}
    
    def set_aspect_callback(self, aspect: str, callback: Callable) -> None:
        """Set callback for getting responses from an aspect."""
        self._aspect_callbacks[aspect.upper()] = callback
    
    def initiate_sync(
        self,
        question: str,
        initial_positions: Dict[str, Dict]
    ) -> str:
        """
        Initiate a new synchronization process.
        
        Returns the sync_id.
        """
        sync_id = hashlib.sha256(
            f"{question}{datetime.now().isoformat()}".encode()
        ).hexdigest()[:12]
        
        # Initialize aspect states from initial positions
        states = {}
        for aspect, position in initial_positions.items():
            aspect_upper = aspect.upper()
            states[aspect_upper] = AspectState(
                aspect=aspect_upper,
                current_position=position.get("verdict", "UNCERTAIN"),
                confidence=position.get("confidence", 0.5),
                active_values=position.get("key_values_applied", []),
                openness=self._get_aspect_openness(aspect_upper),
            )
        
        self.active_syncs[sync_id] = SyncResult(
            sync_id=sync_id,
            question=question,
            final_states=states,
            start_time=datetime.now(),
        )
        
        return sync_id
    
    def _get_aspect_openness(self, aspect: str) -> float:
        """Get baseline openness for an aspect."""
        openness_map = {
            "MELCHIOR": 0.6,
            "BALTHASAR": 0.5,
            "CASPER": 0.8,
        }
        return openness_map.get(aspect, 0.6)
    
    def run_phase(self, sync_id: str, phase: SyncPhase) -> Dict[str, Any]:
        """Run a specific phase of synchronization."""
        sync = self.active_syncs.get(sync_id)
        if not sync:
            return {"error": "Sync not found"}
        
        phase_handlers = {
            SyncPhase.POSITION_FORMATION: self._phase_position_formation,
            SyncPhase.CROSS_EXAMINATION: self._phase_cross_examination,
            SyncPhase.REBUTTAL: self._phase_rebuttal,
            SyncPhase.RECONSIDERATION: self._phase_reconsideration,
            SyncPhase.SYNTHESIS_ATTEMPT: self._phase_synthesis,
            SyncPhase.FINAL_VERDICT: self._phase_final_verdict,
        }
        
        handler = phase_handlers.get(phase)
        if not handler:
            return {"error": f"Unknown phase: {phase}"}
        
        result = handler(sync)
        sync.phases_completed.append(phase)
        
        return result
    
    def _phase_position_formation(self, sync: SyncResult) -> Dict[str, Any]:
        """Phase 1: Each aspect forms and articulates their initial position."""
        arguments = []
        
        for aspect, state in sync.final_states.items():
            # Create initial position argument
            arg = Argument(
                argument_id=f"{sync.sync_id}_{aspect}_init",
                source_aspect=aspect,
                argument_type=ArgumentType.ASSERTION,
                content=f"{aspect} position: {state.current_position}",
                conviction=state.confidence,
                invoked_values=state.active_values,
            )
            
            self.argument_store[arg.argument_id] = arg
            state.arguments_made.append(arg.argument_id)
            arguments.append(arg)
        
        sync.total_arguments += len(arguments)
        
        return {
            "phase": "position_formation",
            "arguments": [a.argument_id for a in arguments],
        }
    
    def _phase_cross_examination(self, sync: SyncResult) -> Dict[str, Any]:
        """Phase 2: Each aspect examines the others' positions."""
        examinations = {}
        
        for examiner, examiner_state in sync.final_states.items():
            examinations[examiner] = {}
            
            for examined, examined_state in sync.final_states.items():
                if examiner != examined:
                    # Generate examination argument
                    relationship = AspectDynamics.ASPECT_RELATIONSHIPS.get(
                        examiner, {}
                    ).get(examined, {})
                    
                    typical_conflict = relationship.get("typical_conflict", "values")
                    
                    arg = Argument(
                        argument_id=f"{sync.sync_id}_{examiner}_exam_{examined}",
                        source_aspect=examiner,
                        argument_type=ArgumentType.EVIDENCE,
                        target_aspect=examined,
                        content=f"{examiner} examines {examined}'s position through lens of {typical_conflict}",
                        conviction=examiner_state.confidence * 0.8,
                    )
                    
                    self.argument_store[arg.argument_id] = arg
                    examiner_state.arguments_made.append(arg.argument_id)
                    examined_state.arguments_received.append(arg.argument_id)
                    
                    examinations[examiner][examined] = arg.argument_id
        
        sync.total_arguments += sum(len(v) for v in examinations.values())
        
        return {
            "phase": "cross_examination",
            "examinations": examinations,
        }
    
    def _phase_rebuttal(self, sync: SyncResult) -> Dict[str, Any]:
        """Phase 3: Aspects respond to examinations with rebuttals."""
        rebuttals = {}
        
        for aspect, state in sync.final_states.items():
            aspect_style = AspectDynamics.ARGUMENT_STYLES.get(aspect, {})
            rebuttals[aspect] = []
            
            for arg_id in state.arguments_received:
                arg = self.argument_store.get(arg_id)
                if not arg:
                    continue
                
                # Decide whether to rebut
                rebuttal_tendency = aspect_style.get("rebuttal_tendency", 0.5)
                
                if state.confidence > 0.5 and rebuttal_tendency > 0.5:
                    # Create rebuttal
                    rebuttal = Argument(
                        argument_id=f"{sync.sync_id}_{aspect}_rebut_{arg.source_aspect}",
                        source_aspect=aspect,
                        argument_type=ArgumentType.REBUTTAL,
                        target_aspect=arg.source_aspect,
                        responds_to=arg_id,
                        content=f"{aspect} rebuts {arg.source_aspect}",
                        conviction=state.confidence,
                        invoked_values=state.active_values,
                    )
                    
                    self.argument_store[rebuttal.argument_id] = rebuttal
                    state.arguments_made.append(rebuttal.argument_id)
                    rebuttals[aspect].append(rebuttal.argument_id)
        
        sync.total_arguments += sum(len(v) for v in rebuttals.values())
        
        return {
            "phase": "rebuttal",
            "rebuttals": rebuttals,
        }
    
    def _phase_reconsideration(self, sync: SyncResult) -> Dict[str, Any]:
        """Phase 4: Aspects reconsider their positions based on arguments."""
        changes = {}
        
        for aspect, state in sync.final_states.items():
            original_confidence = state.confidence
            original_position = state.current_position
            
            # Calculate influence from received arguments
            total_persuasion = 0.0
            
            for arg_id in state.arguments_received:
                arg = self.argument_store.get(arg_id)
                if arg:
                    persuasion = AspectDynamics.calculate_persuasion(arg, aspect)
                    total_persuasion += persuasion
            
            # Adjust confidence based on persuasion
            if total_persuasion > 0:
                state.confidence = max(0.1, state.confidence - total_persuasion * 0.1)
                state.openness = min(1.0, state.openness + total_persuasion * 0.1)
            
            # Track stubbornness decay
            if total_persuasion > state.stubbornness_remaining * 0.3:
                state.stubbornness_remaining *= 0.8
            
            changes[aspect] = {
                "confidence_change": state.confidence - original_confidence,
                "position_changed": state.current_position != original_position,
                "openness": state.openness,
            }
        
        sync.deliberation_depth += 1
        
        return {
            "phase": "reconsideration",
            "changes": changes,
        }
    
    def _phase_synthesis(self, sync: SyncResult) -> Dict[str, Any]:
        """Phase 5: Attempt to synthesize positions."""
        sync.synthesis_attempts += 1
        
        # Find aspects with highest openness
        most_open = max(sync.final_states.values(), key=lambda s: s.openness)
        
        # Check if synthesis conditions are met
        avg_openness = sum(s.openness for s in sync.final_states.values()) / 3
        
        if avg_openness >= self.synthesis_threshold:
            # Attempt synthesis
            common_ground = []
            for aspect_a in sync.final_states:
                for aspect_b in sync.final_states:
                    if aspect_a < aspect_b:  # Avoid duplicates
                        ground = AspectDynamics.find_common_ground(aspect_a, aspect_b)
                        common_ground.extend(ground)
            
            if common_ground:
                # Create synthesis argument
                synth = Argument(
                    argument_id=f"{sync.sync_id}_synthesis",
                    source_aspect=most_open.aspect,
                    argument_type=ArgumentType.SYNTHESIS,
                    content=f"Synthesis based on: {', '.join(set(common_ground))}",
                    conviction=avg_openness,
                )
                
                self.argument_store[synth.argument_id] = synth
                sync.total_arguments += 1
                sync.synthesis_quality = avg_openness
                
                return {
                    "phase": "synthesis",
                    "synthesis_achieved": True,
                    "common_ground": list(set(common_ground)),
                    "synthesis_quality": avg_openness,
                }
        
        return {
            "phase": "synthesis",
            "synthesis_achieved": False,
            "avg_openness": avg_openness,
            "threshold": self.synthesis_threshold,
        }
    
    def _phase_final_verdict(self, sync: SyncResult) -> Dict[str, Any]:
        """Phase 6: Determine final verdict."""
        sync.end_time = datetime.now()
        
        # Count positions
        positions = [s.current_position for s in sync.final_states.values()]
        position_counts = {}
        for p in positions:
            position_counts[p] = position_counts.get(p, 0) + 1
        
        # Determine consensus
        if len(set(positions)) == 1:
            sync.consensus_reached = True
            sync.consensus_type = "unanimous"
            sync.final_position = positions[0]
        elif max(position_counts.values()) >= 2:
            sync.consensus_reached = True
            sync.consensus_type = "majority"
            sync.final_position = max(position_counts, key=position_counts.get)
        else:
            sync.consensus_reached = False
            sync.consensus_type = "deadlock"
            sync.final_position = "UNDETERMINED"
        
        # Calculate mutual understanding
        total_concessions = sum(
            len(s.concessions_made) for s in sync.final_states.values()
        )
        sync.mutual_understanding = min(1.0, total_concessions * 0.2 + 
                                        sync.synthesis_quality * 0.5)
        
        # Move to history
        self.sync_history.append(sync)
        if sync.sync_id in self.active_syncs:
            del self.active_syncs[sync.sync_id]
        
        return {
            "phase": "final_verdict",
            "consensus_reached": sync.consensus_reached,
            "consensus_type": sync.consensus_type,
            "final_position": sync.final_position,
            "mutual_understanding": sync.mutual_understanding,
            "duration_ms": sync.duration_ms,
        }
    
    def run_full_synchronization(
        self,
        question: str,
        initial_positions: Dict[str, Dict]
    ) -> SyncResult:
        """Run a complete synchronization process."""
        sync_id = self.initiate_sync(question, initial_positions)
        
        # Run all phases
        phases = [
            SyncPhase.POSITION_FORMATION,
            SyncPhase.CROSS_EXAMINATION,
            SyncPhase.REBUTTAL,
            SyncPhase.RECONSIDERATION,
            SyncPhase.SYNTHESIS_ATTEMPT,
            SyncPhase.FINAL_VERDICT,
        ]
        
        for phase in phases:
            self.run_phase(sync_id, phase)
        
        # Return from history (moved there in final_verdict phase)
        return self.sync_history[-1] if self.sync_history else None
    
    def get_sync_status(self, sync_id: str) -> Dict[str, Any]:
        """Get status of a synchronization process."""
        sync = self.active_syncs.get(sync_id)
        if sync:
            return {
                "sync_id": sync_id,
                "status": "active",
                "phases_completed": [p.value for p in sync.phases_completed],
                "total_arguments": sync.total_arguments,
            }
        
        # Check history
        for hist in self.sync_history:
            if hist.sync_id == sync_id:
                return {
                    "sync_id": sync_id,
                    "status": "complete",
                    "consensus": hist.consensus_type,
                    "final_position": hist.final_position,
                }
        
        return {"sync_id": sync_id, "status": "not_found"}
