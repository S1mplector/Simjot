"""
Dilemma Protocol
================

Handles ethical dilemmas and value conflicts between the three MAGI aspects.

When the three aspects of Dr. Naoko Akagi's personality encounter a decision
where their core values fundamentally conflict, the Dilemma Protocol is engaged.
This protocol:

1. Identifies the nature of the value conflict
2. Weighs the competing considerations through aspect-specific lenses
3. Attempts to find synthesis positions that honor multiple values
4. Falls back to defined precedence rules when synthesis fails

The dilemma types reflect the classic tensions in Dr. Akagi's psyche:
- Scientist vs Mother: Truth vs Protection
- Mother vs Woman: Safety vs Freedom  
- Scientist vs Woman: Logic vs Passion
- Three-way conflicts: The most complex dilemmas

"It's ironic... even after death, I'm still fighting with myself."
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Tuple
from enum import Enum
from datetime import datetime


class DilemmaType(Enum):
    """Types of value conflicts between MAGI aspects."""
    
    # Two-way conflicts
    TRUTH_VS_PROTECTION = "truth_vs_protection"      # Melchior vs Balthasar
    SAFETY_VS_FREEDOM = "safety_vs_freedom"          # Balthasar vs Casper
    LOGIC_VS_PASSION = "logic_vs_passion"            # Melchior vs Casper
    PROGRESS_VS_STABILITY = "progress_vs_stability"  # Melchior vs Balthasar
    DUTY_VS_DESIRE = "duty_vs_desire"                # Balthasar vs Casper
    OBJECTIVITY_VS_MEANING = "objectivity_vs_meaning"  # Melchior vs Casper
    
    # Three-way conflicts
    TRILEMMA = "trilemma"                            # All three in tension
    
    # Special cases
    MOTHER_DAUGHTER = "mother_daughter"              # Naoko's feelings about Ritsuko
    GENDO_COMPLEX = "gendo_complex"                  # Naoko's feelings about Gendo
    SELF_PRESERVATION = "self_preservation"          # System survival instinct


class ResolutionStrategy(Enum):
    """Strategies for resolving dilemmas."""
    
    SYNTHESIS = "synthesis"           # Find position honoring all values
    WEIGHTED_PRIORITY = "weighted"    # Use value weights to determine priority
    CONTEXTUAL = "contextual"         # Let context determine which value wins
    PROCEDURAL = "procedural"         # Follow established precedence rules
    DEFERRED = "deferred"             # Escalate to human decision-maker
    DEADLOCK = "deadlock"             # No resolution possible


@dataclass
class ValueTension:
    """Represents a tension between two values."""
    
    value_a: str
    value_b: str
    aspect_a: str  # Which MAGI aspect holds value_a
    aspect_b: str  # Which MAGI aspect holds value_b
    
    tension_intensity: float = 0.5  # 0.0 = minor, 1.0 = fundamental
    context: str = ""
    
    # Resolution attempts
    synthesis_possible: bool = True
    synthesis_conditions: List[str] = field(default_factory=list)
    
    @property
    def tension_id(self) -> str:
        return f"{self.value_a}_vs_{self.value_b}"


@dataclass
class DilemmaPosition:
    """A position taken by one MAGI aspect in a dilemma."""
    
    aspect: str  # MELCHIOR, BALTHASAR, or CASPER
    position: str  # The stance taken
    primary_value: str  # The value being defended
    confidence: float  # 0.0 to 1.0
    
    # Reasoning
    reasoning: str = ""
    concessions: List[str] = field(default_factory=list)  # What they'd give up
    red_lines: List[str] = field(default_factory=list)     # What they won't compromise
    
    # Emotional weight
    emotional_intensity: float = 0.5
    emotional_type: str = ""  # e.g., "protective_fear", "intellectual_conviction"


@dataclass
class DilemmaResolution:
    """The outcome of a dilemma resolution attempt."""
    
    dilemma_id: str
    dilemma_type: DilemmaType
    strategy_used: ResolutionStrategy
    
    # Outcome
    resolved: bool = False
    final_position: str = ""
    
    # Value accounting
    values_honored: List[str] = field(default_factory=list)
    values_compromised: List[str] = field(default_factory=list)
    
    # Aspect positions
    positions: Dict[str, DilemmaPosition] = field(default_factory=dict)
    
    # Synthesis details
    synthesis_statement: str = ""
    conditions_for_synthesis: List[str] = field(default_factory=list)
    
    # Metadata
    resolution_confidence: float = 0.0
    timestamp: datetime = field(default_factory=datetime.now)
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "dilemma_id": self.dilemma_id,
            "type": self.dilemma_type.value,
            "strategy": self.strategy_used.value,
            "resolved": self.resolved,
            "final_position": self.final_position,
            "values_honored": self.values_honored,
            "values_compromised": self.values_compromised,
            "confidence": self.resolution_confidence,
        }


class DilemmaProtocol:
    """
    The MAGI Dilemma Protocol - handling ethical conflicts.
    
    This protocol is engaged when the three MAGI aspects cannot reach
    consensus through normal deliberation due to fundamental value conflicts.
    """
    
    # Value ownership by aspect
    ASPECT_VALUES = {
        "MELCHIOR": ["truth", "knowledge", "progress", "objectivity", "precision"],
        "BALTHASAR": ["protection", "safety", "wellbeing", "compassion", "stability"],
        "CASPER": ["freedom", "meaning", "love", "passion", "authenticity"],
    }
    
    # Known dilemma patterns and their typical manifestations
    DILEMMA_PATTERNS = {
        DilemmaType.TRUTH_VS_PROTECTION: {
            "aspects": ("MELCHIOR", "BALTHASAR"),
            "description": "The truth may cause harm, but protection may require deception",
            "example": "Should we tell the pilots the true nature of the EVAs?",
            "melchior_stance": "Truth serves humanity's long-term interests",
            "balthasar_stance": "Some truths destroy those unprepared for them",
        },
        DilemmaType.SAFETY_VS_FREEDOM: {
            "aspects": ("BALTHASAR", "CASPER"),
            "description": "Safety requires restrictions that limit freedom and growth",
            "example": "Should we restrict pilot autonomy to protect them?",
            "balthasar_stance": "Safety must come first, especially for children",
            "casper_stance": "A life without freedom is not truly living",
        },
        DilemmaType.LOGIC_VS_PASSION: {
            "aspects": ("MELCHIOR", "CASPER"),
            "description": "The logical choice conflicts with what feels meaningful",
            "example": "Should we prioritize efficiency over human connection?",
            "melchior_stance": "Emotional decisions lead to suboptimal outcomes",
            "casper_stance": "A purely logical existence lacks meaning",
        },
        DilemmaType.PROGRESS_VS_STABILITY: {
            "aspects": ("MELCHIOR", "BALTHASAR"),
            "description": "Progress requires change that threatens stability",
            "example": "Should we implement untested but promising technology?",
            "melchior_stance": "Progress requires acceptable risks",
            "balthasar_stance": "Stability protects those in our care",
        },
        DilemmaType.DUTY_VS_DESIRE: {
            "aspects": ("BALTHASAR", "CASPER"),
            "description": "Duty to others conflicts with personal fulfillment",
            "example": "Should we sacrifice personal happiness for others' safety?",
            "balthasar_stance": "Duty to protect transcends personal desire",
            "casper_stance": "Self-sacrifice breeds resentment and failure",
        },
        DilemmaType.TRILEMMA: {
            "aspects": ("MELCHIOR", "BALTHASAR", "CASPER"),
            "description": "All three values in fundamental tension",
            "example": "How do we balance truth, safety, and meaning in crisis?",
        },
    }
    
    # Precedence rules for unresolvable conflicts
    PRECEDENCE_RULES = {
        # Context-dependent precedence
        "imminent_threat": ["BALTHASAR", "MELCHIOR", "CASPER"],
        "long_term_planning": ["MELCHIOR", "CASPER", "BALTHASAR"],
        "human_relationships": ["CASPER", "BALTHASAR", "MELCHIOR"],
        "crisis_response": ["BALTHASAR", "MELCHIOR", "CASPER"],
        "ethical_choice": ["BALTHASAR", "CASPER", "MELCHIOR"],
        "technical_decision": ["MELCHIOR", "BALTHASAR", "CASPER"],
        "default": ["MELCHIOR", "BALTHASAR", "CASPER"],  # Original creation order
    }
    
    def __init__(self):
        self.active_dilemmas: Dict[str, DilemmaResolution] = {}
        self.resolution_history: List[DilemmaResolution] = []
        self.tension_threshold: float = 0.6  # When to engage protocol
        
        # Callbacks for aspect consultation
        self._aspect_callbacks: Dict[str, Any] = {}
    
    def set_aspect_callback(self, aspect: str, callback: Any) -> None:
        """Set callback for consulting a specific aspect."""
        self._aspect_callbacks[aspect.upper()] = callback
    
    def detect_dilemma(
        self,
        question: str,
        verdicts: Dict[str, Dict]
    ) -> Optional[DilemmaType]:
        """
        Detect if a dilemma exists based on verdicts.
        
        Returns the type of dilemma if detected, None otherwise.
        """
        # Extract positions
        positions = {}
        for aspect, verdict in verdicts.items():
            positions[aspect.upper()] = verdict.get("verdict", "UNKNOWN")
        
        # Check for disagreement patterns
        approve_count = sum(1 for v in positions.values() if v == "APPROVE")
        reject_count = sum(1 for v in positions.values() if v == "REJECT")
        
        # No dilemma if consensus exists
        if approve_count == 3 or reject_count == 3:
            return None
        
        # Identify which aspects disagree
        disagreeing = []
        for aspect, pos in positions.items():
            for other_aspect, other_pos in positions.items():
                if aspect != other_aspect:
                    if (pos == "APPROVE" and other_pos == "REJECT") or \
                       (pos == "REJECT" and other_pos == "APPROVE"):
                        if (aspect, other_aspect) not in disagreeing and \
                           (other_aspect, aspect) not in disagreeing:
                            disagreeing.append((aspect, other_aspect))
        
        if len(disagreeing) == 0:
            return None
        
        # Determine dilemma type based on which aspects conflict
        if len(disagreeing) >= 2:
            return DilemmaType.TRILEMMA
        
        conflict_pair = tuple(sorted(disagreeing[0]))
        
        if conflict_pair == ("BALTHASAR", "MELCHIOR"):
            # Check specific conflict type via keyword analysis
            q_lower = question.lower()
            if any(word in q_lower for word in ["truth", "reveal", "tell", "honest"]):
                return DilemmaType.TRUTH_VS_PROTECTION
            elif any(word in q_lower for word in ["change", "new", "progress", "advance"]):
                return DilemmaType.PROGRESS_VS_STABILITY
            return DilemmaType.TRUTH_VS_PROTECTION  # Default
        
        elif conflict_pair == ("BALTHASAR", "CASPER"):
            q_lower = question.lower()
            if any(word in q_lower for word in ["free", "choice", "restrict", "allow"]):
                return DilemmaType.SAFETY_VS_FREEDOM
            elif any(word in q_lower for word in ["duty", "sacrifice", "want", "desire"]):
                return DilemmaType.DUTY_VS_DESIRE
            return DilemmaType.SAFETY_VS_FREEDOM  # Default
        
        elif conflict_pair == ("CASPER", "MELCHIOR"):
            return DilemmaType.LOGIC_VS_PASSION
        
        return DilemmaType.TRILEMMA  # Fallback
    
    def engage_protocol(
        self,
        question: str,
        dilemma_type: DilemmaType,
        verdicts: Dict[str, Dict],
        context: str = "default"
    ) -> DilemmaResolution:
        """
        Engage the Dilemma Protocol to resolve a value conflict.
        """
        import hashlib
        dilemma_id = hashlib.sha256(
            f"{question}{datetime.now().isoformat()}".encode()
        ).hexdigest()[:12]
        
        # Build positions from verdicts
        positions = {}
        for aspect, verdict in verdicts.items():
            aspect_upper = aspect.upper()
            primary_value = self._identify_primary_value(aspect_upper, verdict)
            
            positions[aspect_upper] = DilemmaPosition(
                aspect=aspect_upper,
                position=verdict.get("verdict", "UNKNOWN"),
                primary_value=primary_value,
                confidence=verdict.get("confidence", 0.5),
                reasoning=verdict.get("reasoning", ""),
            )
        
        # Attempt resolution strategies in order
        resolution = DilemmaResolution(
            dilemma_id=dilemma_id,
            dilemma_type=dilemma_type,
            strategy_used=ResolutionStrategy.SYNTHESIS,
            positions=positions,
        )
        
        # Try synthesis first
        synthesis_result = self._attempt_synthesis(
            question, dilemma_type, positions
        )
        
        if synthesis_result[0]:
            resolution.resolved = True
            resolution.strategy_used = ResolutionStrategy.SYNTHESIS
            resolution.synthesis_statement = synthesis_result[1]
            resolution.final_position = synthesis_result[1]
            resolution.values_honored = synthesis_result[2]
            resolution.resolution_confidence = 0.8
        else:
            # Fall back to weighted priority
            weighted_result = self._weighted_resolution(positions)
            
            if weighted_result[0]:
                resolution.resolved = True
                resolution.strategy_used = ResolutionStrategy.WEIGHTED_PRIORITY
                resolution.final_position = weighted_result[1]
                resolution.values_honored = weighted_result[2]
                resolution.values_compromised = weighted_result[3]
                resolution.resolution_confidence = 0.6
            else:
                # Fall back to precedence rules
                precedence_result = self._precedence_resolution(positions, context)
                resolution.resolved = True
                resolution.strategy_used = ResolutionStrategy.PROCEDURAL
                resolution.final_position = precedence_result[0]
                resolution.values_honored = [precedence_result[1]]
                resolution.resolution_confidence = 0.5
        
        # Store resolution
        self.active_dilemmas[dilemma_id] = resolution
        self.resolution_history.append(resolution)
        
        return resolution
    
    def _identify_primary_value(self, aspect: str, verdict: Dict) -> str:
        """Identify the primary value an aspect is defending."""
        values = self.ASPECT_VALUES.get(aspect, [])
        
        # Check if verdict mentions specific values
        reasoning = verdict.get("reasoning", "").lower()
        
        for value in values:
            if value in reasoning:
                return value
        
        # Return first value as default
        return values[0] if values else "unknown"
    
    def _attempt_synthesis(
        self,
        question: str,
        dilemma_type: DilemmaType,
        positions: Dict[str, DilemmaPosition]
    ) -> Tuple[bool, str, List[str]]:
        """
        Attempt to synthesize a position that honors multiple values.
        
        Returns: (success, synthesis_statement, values_honored)
        """
        # Define synthesis templates for known dilemma types
        synthesis_templates = {
            DilemmaType.TRUTH_VS_PROTECTION: {
                "template": "Disclose truth gradually with appropriate support systems in place",
                "conditions": [
                    "Prepare recipient for difficult information",
                    "Provide emotional support during disclosure",
                    "Maintain ongoing protection after truth is known"
                ],
                "values_honored": ["truth", "protection", "compassion"],
            },
            DilemmaType.SAFETY_VS_FREEDOM: {
                "template": "Allow informed choice-making within protective boundaries",
                "conditions": [
                    "Ensure full understanding of risks",
                    "Maintain safety nets that don't prevent action",
                    "Respect autonomy while fulfilling duty of care"
                ],
                "values_honored": ["safety", "freedom", "meaning"],
            },
            DilemmaType.LOGIC_VS_PASSION: {
                "template": "Apply rigorous analysis while honoring human significance",
                "conditions": [
                    "Factor human meaning into optimization criteria",
                    "Balance efficiency with fulfillment",
                    "Recognize that meaning has logical value"
                ],
                "values_honored": ["truth", "meaning", "progress"],
            },
            DilemmaType.PROGRESS_VS_STABILITY: {
                "template": "Implement change through careful phased approach",
                "conditions": [
                    "Test thoroughly before full deployment",
                    "Maintain fallback systems",
                    "Prioritize stability for vulnerable populations"
                ],
                "values_honored": ["progress", "safety", "wellbeing"],
            },
            DilemmaType.DUTY_VS_DESIRE: {
                "template": "Find meaning in duty while preserving space for authentic self",
                "conditions": [
                    "Acknowledge legitimacy of personal needs",
                    "Seek duty that aligns with desire where possible",
                    "Build sustainable commitment, not martyrdom"
                ],
                "values_honored": ["protection", "freedom", "authenticity"],
            },
        }
        
        if dilemma_type in synthesis_templates:
            template = synthesis_templates[dilemma_type]
            return True, template["template"], template["values_honored"]
        
        # For trilemmas and unknown types, synthesis is harder
        if dilemma_type == DilemmaType.TRILEMMA:
            return False, "", []
        
        return False, "", []
    
    def _weighted_resolution(
        self,
        positions: Dict[str, DilemmaPosition]
    ) -> Tuple[bool, str, List[str], List[str]]:
        """
        Resolve based on weighted confidence and value importance.
        
        Returns: (success, position, values_honored, values_compromised)
        """
        # Calculate weighted scores
        scores = {}
        for aspect, pos in positions.items():
            # Weight by confidence and aspect-specific modifiers
            weight = pos.confidence
            
            # Apply emotional intensity as modifier
            weight *= (1 + pos.emotional_intensity * 0.3)
            
            scores[aspect] = weight
        
        if not scores:
            return False, "", [], []
        
        # Find highest scoring aspect
        winner = max(scores, key=scores.get)
        winner_pos = positions[winner]
        
        # Identify compromised values
        compromised = []
        honored = [winner_pos.primary_value]
        
        for aspect, pos in positions.items():
            if aspect != winner:
                compromised.append(pos.primary_value)
        
        return True, winner_pos.position, honored, compromised
    
    def _precedence_resolution(
        self,
        positions: Dict[str, DilemmaPosition],
        context: str
    ) -> Tuple[str, str]:
        """
        Resolve using precedence rules based on context.
        
        Returns: (position, primary_value_of_winner)
        """
        precedence = self.PRECEDENCE_RULES.get(
            context, 
            self.PRECEDENCE_RULES["default"]
        )
        
        for aspect in precedence:
            if aspect in positions:
                return positions[aspect].position, positions[aspect].primary_value
        
        # Fallback
        first_aspect = list(positions.keys())[0]
        return positions[first_aspect].position, positions[first_aspect].primary_value
    
    def get_dilemma_context(self, question: str) -> str:
        """Determine the context of a question for precedence rules."""
        q_lower = question.lower()
        
        context_keywords = {
            "imminent_threat": ["danger", "attack", "emergency", "threat", "immediate"],
            "long_term_planning": ["future", "plan", "strategy", "years", "eventually"],
            "human_relationships": ["relationship", "feel", "love", "trust", "person"],
            "crisis_response": ["crisis", "urgent", "now", "quickly", "emergency"],
            "ethical_choice": ["right", "wrong", "should", "moral", "ethical"],
            "technical_decision": ["system", "technical", "data", "calculate", "process"],
        }
        
        for context, keywords in context_keywords.items():
            if any(keyword in q_lower for keyword in keywords):
                return context
        
        return "default"
    
    def get_conflict_analysis(self, dilemma_type: DilemmaType) -> Dict[str, Any]:
        """Get detailed analysis of a dilemma type."""
        if dilemma_type in self.DILEMMA_PATTERNS:
            pattern = self.DILEMMA_PATTERNS[dilemma_type]
            return {
                "type": dilemma_type.value,
                "aspects_involved": pattern.get("aspects", []),
                "description": pattern.get("description", ""),
                "example": pattern.get("example", ""),
                "typical_stances": {
                    k: v for k, v in pattern.items() 
                    if k.endswith("_stance")
                },
            }
        return {"type": dilemma_type.value, "description": "Unknown dilemma type"}
