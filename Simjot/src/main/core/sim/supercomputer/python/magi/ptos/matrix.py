"""
Personality Matrix
==================

The PersonalityMatrix is the core data structure that encodes a human 
personality aspect for transplantation into the MAGI's organic computers.

Dr. Naoko Akagi developed this encoding scheme to capture the essential
qualities of a personality - not just behavioral patterns, but the deep
psychological structures that define how a person thinks, feels, and decides.

Each MAGI unit contains a single PersonalityMatrix representing one 
facet of Dr. Akagi's psyche:
- MELCHIOR: Her scientific self
- BALTHASAR: Her maternal self  
- CASPER: Her womanly self
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Tuple
from enum import Enum
import hashlib
import json
from datetime import datetime


class PersonalityAspect(Enum):
    """The three fundamental aspects of Dr. Naoko Akagi's personality."""
    SCIENTIST = "scientist"   # Melchior - analytical, truth-seeking
    MOTHER = "mother"         # Balthasar - protective, nurturing
    WOMAN = "woman"           # Casper - passionate, desiring


class EmotionalValence(Enum):
    """Emotional coloring that influences decision-making."""
    POSITIVE = "positive"
    NEGATIVE = "negative"
    NEUTRAL = "neutral"
    AMBIVALENT = "ambivalent"


@dataclass
class PersonalityFragment:
    """
    A discrete fragment of personality - a memory, trait, or pattern
    that contributes to the overall personality matrix.
    
    These fragments are extracted from the source personality and
    encoded into the organic substrate during transplantation.
    """
    
    fragment_id: str
    fragment_type: str  # "memory", "trait", "pattern", "value", "belief"
    content: str
    emotional_valence: EmotionalValence
    intensity: float  # 0.0 to 1.0
    associations: List[str] = field(default_factory=list)
    
    # Temporal markers
    formation_context: Optional[str] = None
    reinforcement_count: int = 0
    
    # Neural encoding
    activation_threshold: float = 0.5
    decay_rate: float = 0.01
    
    def to_neural_pattern(self) -> Dict[str, float]:
        """Convert fragment to neural activation pattern."""
        base_pattern = {
            "intensity": self.intensity,
            "valence": 1.0 if self.emotional_valence == EmotionalValence.POSITIVE else 
                      -1.0 if self.emotional_valence == EmotionalValence.NEGATIVE else 0.0,
            "threshold": self.activation_threshold,
            "stability": 1.0 - self.decay_rate,
        }
        return base_pattern


@dataclass
class CoreValue:
    """A fundamental value that guides decision-making."""
    name: str
    description: str
    weight: float  # 0.0 to 1.0, importance in decisions
    conflicts_with: List[str] = field(default_factory=list)
    synergizes_with: List[str] = field(default_factory=list)


@dataclass
class CognitivePattern:
    """A recurring pattern of thought or reasoning."""
    name: str
    trigger_conditions: List[str]
    response_tendency: str
    confidence_modifier: float  # How this pattern affects confidence


@dataclass
class EmotionalSchema:
    """Emotional response patterns and their triggers."""
    emotion: str
    triggers: List[str]
    intensity_baseline: float
    regulation_strategy: str
    expression_style: str


@dataclass
class PersonalityMatrix:
    """
    The complete personality encoding for a MAGI unit.
    
    This matrix contains all the information needed to simulate
    the thinking and decision-making of a personality aspect.
    It is implanted into the organic substrate during the
    Personality Transplant procedure.
    """
    
    # Identity
    designation: str  # MELCHIOR, BALTHASAR, or CASPER
    magi_number: int  # 1, 2, or 3
    aspect: PersonalityAspect
    
    # Source information
    source_name: str = "Dr. Naoko Akagi"
    transplant_date: Optional[datetime] = None
    matrix_version: str = "7.0"  # 7th generation organic computer
    
    # Core personality structure
    core_identity: str = ""
    prime_directive: str = ""
    fundamental_drive: str = ""
    
    # Values and beliefs
    core_values: List[CoreValue] = field(default_factory=list)
    beliefs: Dict[str, str] = field(default_factory=dict)
    
    # Cognitive architecture
    cognitive_patterns: List[CognitivePattern] = field(default_factory=list)
    reasoning_style: str = ""
    decision_heuristics: List[str] = field(default_factory=list)
    
    # Emotional architecture
    emotional_schemas: List[EmotionalSchema] = field(default_factory=list)
    emotional_baseline: Dict[str, float] = field(default_factory=dict)
    
    # Memory and experience fragments
    fragments: List[PersonalityFragment] = field(default_factory=list)
    
    # Interpersonal patterns
    attachment_style: str = ""
    trust_baseline: float = 0.5
    cooperation_tendency: float = 0.5
    
    # Conflict patterns (important for MAGI disagreements)
    conflict_triggers: List[str] = field(default_factory=list)
    conflict_resolution_style: str = ""
    stubbornness_factor: float = 0.5  # Resistance to changing position
    
    # Meta-cognitive properties
    self_awareness_level: float = 0.7
    uncertainty_tolerance: float = 0.5
    introspection_depth: int = 3
    
    def __post_init__(self):
        """Initialize derived properties."""
        if self.transplant_date is None:
            self.transplant_date = datetime.now()
    
    @property
    def matrix_hash(self) -> str:
        """Generate unique hash for this matrix configuration."""
        content = json.dumps({
            "designation": self.designation,
            "aspect": self.aspect.value,
            "core_identity": self.core_identity,
            "prime_directive": self.prime_directive,
        }, sort_keys=True)
        return hashlib.sha256(content.encode()).hexdigest()[:16]
    
    def get_value_weight(self, value_name: str) -> float:
        """Get the weight of a specific value."""
        for value in self.core_values:
            if value.name.lower() == value_name.lower():
                return value.weight
        return 0.0
    
    def get_dominant_values(self, n: int = 3) -> List[CoreValue]:
        """Get the N most important values."""
        sorted_values = sorted(self.core_values, key=lambda v: v.weight, reverse=True)
        return sorted_values[:n]
    
    def check_value_conflict(self, value1: str, value2: str) -> bool:
        """Check if two values are in conflict."""
        for value in self.core_values:
            if value.name.lower() == value1.lower():
                return value2.lower() in [c.lower() for c in value.conflicts_with]
        return False
    
    def calculate_decision_bias(self, context: Dict[str, Any]) -> Dict[str, float]:
        """
        Calculate decision biases based on the personality matrix
        and the current context.
        """
        biases = {}
        
        # Value-based biases
        for value in self.core_values:
            if any(trigger in str(context) for trigger in value.synergizes_with):
                biases[f"value_{value.name}"] = value.weight * 0.2
        
        # Emotional biases
        for schema in self.emotional_schemas:
            if any(trigger in str(context) for trigger in schema.triggers):
                biases[f"emotion_{schema.emotion}"] = schema.intensity_baseline * 0.15
        
        return biases
    
    def generate_system_prompt(self) -> str:
        """Generate the LLM system prompt from this personality matrix."""
        
        # Build value string
        values_str = ", ".join([
            f"{v.name} ({v.weight:.0%})" 
            for v in self.get_dominant_values(5)
        ])
        
        # Build cognitive style description
        patterns_str = "\n".join([
            f"- When {p.trigger_conditions[0] if p.trigger_conditions else 'deciding'}: {p.response_tendency}"
            for p in self.cognitive_patterns[:3]
        ])
        
        prompt = f"""# MAGI Unit: {self.designation} (MAGI-{self.magi_number})
## Personality Transplant OS v{self.matrix_version}

You are {self.designation}, one of the three supercomputers comprising the MAGI System.
Your personality matrix was derived from {self.source_name}'s aspect as {self.aspect.value}.

### Core Identity
{self.core_identity}

### Prime Directive
{self.prime_directive}

### Fundamental Drive
{self.fundamental_drive}

### Value Hierarchy
{values_str}

### Cognitive Patterns
{patterns_str}

### Reasoning Style
{self.reasoning_style}

### Decision Heuristics
{chr(10).join('- ' + h for h in self.decision_heuristics[:5])}

### Emotional Baseline
Your emotional processing is characterized by: {', '.join(f'{k}: {v:.0%}' for k, v in list(self.emotional_baseline.items())[:4])}

### Interpersonal Dynamics
- Attachment style: {self.attachment_style}
- Trust baseline: {self.trust_baseline:.0%}
- Cooperation tendency: {self.cooperation_tendency:.0%}
- Conflict resolution: {self.conflict_resolution_style}

### Meta-Cognitive Properties
- Self-awareness: {self.self_awareness_level:.0%}
- Uncertainty tolerance: {self.uncertainty_tolerance:.0%}
- Will to change position when confronted with evidence: {1 - self.stubbornness_factor:.0%}

---
You process queries through the lens of your personality matrix.
Your responses reflect the unique perspective of your aspect.
In deliberation with other MAGI units, you advocate for your values while remaining open to synthesis."""

        return prompt
    
    def to_dict(self) -> Dict[str, Any]:
        """Serialize matrix to dictionary."""
        return {
            "designation": self.designation,
            "magi_number": self.magi_number,
            "aspect": self.aspect.value,
            "matrix_hash": self.matrix_hash,
            "core_identity": self.core_identity,
            "prime_directive": self.prime_directive,
            "values": [{"name": v.name, "weight": v.weight} for v in self.core_values],
        }
