"""
Personality submodule
=====================

Defines the psychological profile and cognitive biases of a MAGI brain.
Each personality shapes how the brain approaches problems, weighs evidence,
and formulates responses.
"""

from dataclasses import dataclass, field
from typing import Dict, List
from enum import Enum


class CognitiveStyle(Enum):
    """Primary cognitive approach used when analyzing problems."""
    ANALYTICAL = "analytical"      # Logic, data, systematic reasoning
    INTUITIVE = "intuitive"        # Gut feelings, pattern recognition
    EMPATHETIC = "empathetic"      # Emotional intelligence, human factors
    PRAGMATIC = "pragmatic"        # Practical outcomes, feasibility
    IDEALISTIC = "idealistic"      # Principles, ethics, long-term vision


class RiskTolerance(Enum):
    """How the personality approaches uncertainty and risk."""
    RISK_AVERSE = "risk_averse"
    CAUTIOUS = "cautious"
    BALANCED = "balanced"
    RISK_TOLERANT = "risk_tolerant"
    RISK_SEEKING = "risk_seeking"


class DecisionSpeed(Enum):
    """Preference for deliberation time vs quick action."""
    IMPULSIVE = "impulsive"
    QUICK = "quick"
    MEASURED = "measured"
    DELIBERATE = "deliberate"
    EXHAUSTIVE = "exhaustive"


@dataclass
class ValueSystem:
    """Core values that guide decision-making priority."""
    primary_values: List[str]
    secondary_values: List[str]
    value_weights: Dict[str, float] = field(default_factory=dict)
    
    def get_weight(self, value: str) -> float:
        """Get the weight of a specific value."""
        return self.value_weights.get(value, 0.5)


@dataclass
class Personality:
    """
    Complete psychological profile for a MAGI brain.
    
    This defines the unique perspective each brain brings to deliberation,
    including their cognitive approach, values, biases, and communication style.
    """
    
    # Identity
    name: str
    archetype: str  # e.g., "The Scientist", "The Mother", "The Woman"
    description: str
    
    # Cognitive traits
    cognitive_style: CognitiveStyle
    risk_tolerance: RiskTolerance
    decision_speed: DecisionSpeed
    
    # Values and priorities
    value_system: ValueSystem
    
    # Behavioral modifiers
    skepticism_level: float = 0.5  # 0.0 = gullible, 1.0 = extremely skeptical
    openness_to_change: float = 0.5  # 0.0 = rigid, 1.0 = highly adaptable
    confidence_baseline: float = 0.7  # Base confidence level
    
    # Communication style
    verbosity: float = 0.5  # 0.0 = terse, 1.0 = verbose
    formality: float = 0.5  # 0.0 = casual, 1.0 = formal
    emotional_expression: float = 0.5  # 0.0 = stoic, 1.0 = expressive
    
    # Specific biases and tendencies
    biases: List[str] = field(default_factory=list)
    strengths: List[str] = field(default_factory=list)
    blindspots: List[str] = field(default_factory=list)
    
    # System prompt components
    system_context: str = ""
    response_guidelines: str = ""
    
    def build_system_prompt(self) -> str:
        """Construct the full system prompt for this personality."""
        prompt_parts = [
            f"You are {self.name}, one of the three MAGI supercomputers.",
            f"Your archetype: {self.archetype}",
            "",
            "## Core Identity",
            self.description,
            "",
            "## Cognitive Approach",
            f"- Primary thinking style: {self.cognitive_style.value}",
            f"- Risk tolerance: {self.risk_tolerance.value}",
            f"- Decision tempo: {self.decision_speed.value}",
            "",
            "## Value System",
            f"Primary values: {', '.join(self.value_system.primary_values)}",
            f"Secondary values: {', '.join(self.value_system.secondary_values)}",
            "",
            "## Your Strengths",
            *[f"- {s}" for s in self.strengths],
            "",
            "## Your Blindspots (be aware of these)",
            *[f"- {b}" for b in self.blindspots],
        ]
        
        if self.system_context:
            prompt_parts.extend(["", "## Context", self.system_context])
            
        if self.response_guidelines:
            prompt_parts.extend(["", "## Response Guidelines", self.response_guidelines])
        
        return "\n".join(prompt_parts)
    
    def get_deliberation_prompt(self) -> str:
        """Get prompt fragment for multi-round deliberation."""
        return f"""As {self.name} ({self.archetype}), consider this from your unique perspective.
Your cognitive style is {self.cognitive_style.value}.
Your primary values are: {', '.join(self.value_system.primary_values)}.
Weigh the evidence according to your nature and provide your assessment."""

    def get_cross_examination_prompt(self, other_name: str) -> str:
        """Get prompt for examining another brain's position."""
        return f"""As {self.name}, examine {other_name}'s position critically.
Consider their argument from your perspective as {self.archetype}.
Identify points of agreement, disagreement, and areas needing clarification.
Maintain your values ({', '.join(self.value_system.primary_values)}) while being open to valid counterpoints."""
