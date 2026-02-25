"""
Organic Processor
=================

Simulation of the 7th generation organic computer architecture used in MAGI.

The organic processor emulates the bio-neural substrate that houses the
personality matrix. Unlike conventional computers, the organic processor
exhibits properties similar to biological neural networks:

- Parallel distributed processing
- Associative memory retrieval
- Emotional coloring of cognition
- Graceful degradation under stress
- Self-modification through experience

The brain-like material within the MAGI requires specialized interfaces -
fiber optic cables inserted into specific points within the gyri of the
brain-like structure for direct system access.
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Callable
from enum import Enum
import random
import math
from datetime import datetime


class ProcessingMode(Enum):
    """Operating modes of the organic processor."""
    NORMAL = "normal"           # Standard operation
    ANALYTICAL = "analytical"   # Enhanced logical processing
    INTUITIVE = "intuitive"     # Pattern-matching dominant
    EMERGENCY = "emergency"     # Rapid response, reduced accuracy
    DEFENSIVE = "defensive"     # Heightened threat detection (anti-intrusion)
    SYNCHRONOUS = "synchronous" # Locked with other MAGI units


class NeuralState(Enum):
    """State of neural cluster activity."""
    DORMANT = "dormant"
    PRIMED = "primed"
    ACTIVE = "active"
    SATURATED = "saturated"
    REFRACTORY = "refractory"


@dataclass
class Synapse:
    """
    A connection between neural clusters.
    
    Synapses carry weighted signals between clusters and can be
    strengthened or weakened through use (Hebbian learning).
    """
    source_id: str
    target_id: str
    weight: float = 0.5  # -1.0 to 1.0
    plasticity: float = 0.1  # Learning rate
    last_activation: Optional[datetime] = None
    activation_count: int = 0
    
    def transmit(self, signal: float) -> float:
        """Transmit a signal through this synapse."""
        self.activation_count += 1
        self.last_activation = datetime.now()
        return signal * self.weight
    
    def strengthen(self, amount: float = 0.01) -> None:
        """Strengthen this synapse (LTP)."""
        self.weight = min(1.0, self.weight + amount * self.plasticity)
    
    def weaken(self, amount: float = 0.01) -> None:
        """Weaken this synapse (LTD)."""
        self.weight = max(-1.0, self.weight - amount * self.plasticity)


@dataclass
class NeuralCluster:
    """
    A cluster of simulated neurons within the organic substrate.
    
    Clusters represent functional units that process specific types
    of information - values, emotions, memories, reasoning patterns.
    """
    
    cluster_id: str
    cluster_type: str  # "value", "emotion", "memory", "reasoning", "motor"
    label: str
    
    # Activation dynamics
    activation: float = 0.0
    threshold: float = 0.5
    decay_rate: float = 0.1
    state: NeuralState = NeuralState.DORMANT
    
    # Connections
    incoming_synapses: List[str] = field(default_factory=list)
    outgoing_synapses: List[str] = field(default_factory=list)
    
    # Modulation
    neuromodulator_sensitivity: Dict[str, float] = field(default_factory=dict)
    
    def receive_input(self, signal: float) -> None:
        """Receive input signal and update activation."""
        self.activation += signal
        self._update_state()
    
    def _update_state(self) -> None:
        """Update cluster state based on activation level."""
        if self.activation < self.threshold * 0.3:
            self.state = NeuralState.DORMANT
        elif self.activation < self.threshold:
            self.state = NeuralState.PRIMED
        elif self.activation < self.threshold * 2:
            self.state = NeuralState.ACTIVE
        else:
            self.state = NeuralState.SATURATED
    
    def fire(self) -> Optional[float]:
        """Fire if above threshold, return output signal."""
        if self.activation >= self.threshold:
            output = self.activation
            self.activation = 0.0
            self.state = NeuralState.REFRACTORY
            return output
        return None
    
    def decay(self) -> None:
        """Apply activation decay over time."""
        self.activation *= (1.0 - self.decay_rate)
        self._update_state()


@dataclass
class ProcessingResult:
    """Result of organic processing."""
    output: str
    confidence: float
    emotional_coloring: Dict[str, float]
    active_values: List[str]
    processing_time_ms: float
    cluster_activations: Dict[str, float]


class OrganicProcessor:
    """
    The 7th generation organic processor core.
    
    This class simulates the bio-neural substrate that houses the
    personality matrix and processes queries through the lens of
    that personality.
    """
    
    def __init__(self, designation: str, magi_number: int):
        self.designation = designation
        self.magi_number = magi_number
        self.mode = ProcessingMode.NORMAL
        
        # Neural architecture
        self.clusters: Dict[str, NeuralCluster] = {}
        self.synapses: Dict[str, Synapse] = {}
        
        # State
        self.temperature: float = 1.0  # Processing temperature (affects randomness)
        self.integrity: float = 1.0    # System health (1.0 = perfect)
        self.stress_level: float = 0.0
        
        # Neuromodulators (affect global processing)
        self.neuromodulators = {
            "analytical": 0.5,   # Enhances logical processing
            "emotional": 0.5,   # Enhances emotional processing
            "vigilance": 0.3,   # Threat detection sensitivity
            "openness": 0.5,    # Willingness to consider alternatives
        }
        
        # Processing history
        self.activation_history: List[Dict[str, float]] = []
        
        # Initialize core clusters
        self._initialize_core_architecture()
    
    def _initialize_core_architecture(self) -> None:
        """Initialize the core neural cluster architecture."""
        
        # Value clusters
        value_clusters = [
            ("truth", "value"), ("knowledge", "value"), ("progress", "value"),
            ("protection", "value"), ("wellbeing", "value"), ("safety", "value"),
            ("freedom", "value"), ("love", "value"), ("meaning", "value"),
        ]
        
        # Emotion clusters  
        emotion_clusters = [
            ("curiosity", "emotion"), ("concern", "emotion"), ("passion", "emotion"),
            ("fear", "emotion"), ("hope", "emotion"), ("doubt", "emotion"),
        ]
        
        # Reasoning clusters
        reasoning_clusters = [
            ("analysis", "reasoning"), ("synthesis", "reasoning"),
            ("evaluation", "reasoning"), ("intuition", "reasoning"),
        ]
        
        # Create clusters
        for label, ctype in value_clusters + emotion_clusters + reasoning_clusters:
            cluster_id = f"{ctype}_{label}"
            self.clusters[cluster_id] = NeuralCluster(
                cluster_id=cluster_id,
                cluster_type=ctype,
                label=label,
                threshold=0.4 + random.random() * 0.2
            )
        
        # Create interconnections
        self._create_default_synapses()
    
    def _create_default_synapses(self) -> None:
        """Create default synaptic connections between clusters."""
        # Connect emotions to reasoning (emotional influence on thinking)
        for emotion_cluster in [c for c in self.clusters.values() if c.cluster_type == "emotion"]:
            for reasoning_cluster in [c for c in self.clusters.values() if c.cluster_type == "reasoning"]:
                synapse_id = f"{emotion_cluster.cluster_id}->{reasoning_cluster.cluster_id}"
                self.synapses[synapse_id] = Synapse(
                    source_id=emotion_cluster.cluster_id,
                    target_id=reasoning_cluster.cluster_id,
                    weight=0.2 + random.random() * 0.3
                )
        
        # Connect values to reasoning (value-guided thinking)
        for value_cluster in [c for c in self.clusters.values() if c.cluster_type == "value"]:
            for reasoning_cluster in [c for c in self.clusters.values() if c.cluster_type == "reasoning"]:
                synapse_id = f"{value_cluster.cluster_id}->{reasoning_cluster.cluster_id}"
                self.synapses[synapse_id] = Synapse(
                    source_id=value_cluster.cluster_id,
                    target_id=reasoning_cluster.cluster_id,
                    weight=0.3 + random.random() * 0.4
                )
    
    def set_mode(self, mode: ProcessingMode) -> None:
        """Set the processing mode."""
        self.mode = mode
        
        # Adjust neuromodulators based on mode
        if mode == ProcessingMode.ANALYTICAL:
            self.neuromodulators["analytical"] = 0.9
            self.neuromodulators["emotional"] = 0.2
        elif mode == ProcessingMode.INTUITIVE:
            self.neuromodulators["analytical"] = 0.3
            self.neuromodulators["emotional"] = 0.7
        elif mode == ProcessingMode.EMERGENCY:
            self.neuromodulators["vigilance"] = 0.9
            self.temperature = 0.5  # More deterministic
        elif mode == ProcessingMode.DEFENSIVE:
            self.neuromodulators["vigilance"] = 1.0
            self.neuromodulators["openness"] = 0.1
    
    def activate_cluster(self, cluster_id: str, strength: float = 1.0) -> None:
        """Activate a specific neural cluster."""
        if cluster_id in self.clusters:
            self.clusters[cluster_id].receive_input(strength)
    
    def activate_by_keyword(self, text: str) -> Dict[str, float]:
        """Activate clusters based on keywords in text."""
        activations = {}
        text_lower = text.lower()
        
        # Keyword to cluster mapping
        keyword_map = {
            "truth": ["value_truth", "reasoning_analysis"],
            "knowledge": ["value_knowledge", "emotion_curiosity"],
            "science": ["value_truth", "value_knowledge", "value_progress"],
            "protect": ["value_protection", "value_safety", "emotion_concern"],
            "safe": ["value_safety", "value_protection"],
            "child": ["value_protection", "value_wellbeing", "emotion_concern"],
            "love": ["value_love", "emotion_passion"],
            "free": ["value_freedom", "emotion_hope"],
            "meaning": ["value_meaning", "reasoning_synthesis"],
            "danger": ["emotion_fear", "value_safety"],
            "hope": ["emotion_hope", "value_meaning"],
            "risk": ["emotion_fear", "emotion_doubt", "reasoning_evaluation"],
        }
        
        for keyword, clusters in keyword_map.items():
            if keyword in text_lower:
                for cluster_id in clusters:
                    if cluster_id in self.clusters:
                        strength = 0.5 + random.random() * 0.3
                        self.activate_cluster(cluster_id, strength)
                        activations[cluster_id] = self.clusters[cluster_id].activation
        
        return activations
    
    def propagate(self, steps: int = 3) -> None:
        """Propagate activation through the network."""
        for _ in range(steps):
            # Collect outputs from active clusters
            outputs = {}
            for cluster in self.clusters.values():
                output = cluster.fire()
                if output is not None:
                    outputs[cluster.cluster_id] = output
            
            # Transmit through synapses
            for synapse in self.synapses.values():
                if synapse.source_id in outputs:
                    signal = synapse.transmit(outputs[synapse.source_id])
                    if synapse.target_id in self.clusters:
                        self.clusters[synapse.target_id].receive_input(signal)
            
            # Decay all clusters
            for cluster in self.clusters.values():
                cluster.decay()
    
    def get_activation_snapshot(self) -> Dict[str, float]:
        """Get current activation levels of all clusters."""
        return {
            cluster_id: cluster.activation
            for cluster_id, cluster in self.clusters.items()
        }
    
    def get_active_values(self) -> List[str]:
        """Get list of currently active value clusters."""
        return [
            cluster.label
            for cluster in self.clusters.values()
            if cluster.cluster_type == "value" and cluster.state in [NeuralState.ACTIVE, NeuralState.SATURATED]
        ]
    
    def get_emotional_state(self) -> Dict[str, float]:
        """Get current emotional state as cluster activations."""
        return {
            cluster.label: cluster.activation
            for cluster in self.clusters.values()
            if cluster.cluster_type == "emotion"
        }
    
    def calculate_confidence(self) -> float:
        """Calculate processing confidence based on network state."""
        # Base confidence
        confidence = 0.7
        
        # Adjust based on stress
        confidence -= self.stress_level * 0.2
        
        # Adjust based on integrity
        confidence *= self.integrity
        
        # Adjust based on doubt cluster
        if "emotion_doubt" in self.clusters:
            confidence -= self.clusters["emotion_doubt"].activation * 0.15
        
        # Adjust based on analytical engagement
        if "reasoning_analysis" in self.clusters:
            confidence += self.clusters["reasoning_analysis"].activation * 0.1
        
        return max(0.1, min(1.0, confidence))
    
    def apply_damage(self, amount: float) -> None:
        """Apply damage to the organic processor (intrusion, stress, etc.)."""
        self.integrity = max(0.0, self.integrity - amount)
        self.stress_level = min(1.0, self.stress_level + amount * 2)
        
        # Random cluster damage
        if amount > 0.1:
            damaged_cluster = random.choice(list(self.clusters.keys()))
            self.clusters[damaged_cluster].threshold += amount * 0.5
    
    def repair(self, amount: float) -> None:
        """Repair the organic processor."""
        self.integrity = min(1.0, self.integrity + amount)
        self.stress_level = max(0.0, self.stress_level - amount)
    
    def get_status(self) -> Dict[str, Any]:
        """Get comprehensive status of the organic processor."""
        return {
            "designation": self.designation,
            "magi_number": self.magi_number,
            "mode": self.mode.value,
            "integrity": self.integrity,
            "stress_level": self.stress_level,
            "temperature": self.temperature,
            "neuromodulators": self.neuromodulators.copy(),
            "active_clusters": len([c for c in self.clusters.values() if c.state == NeuralState.ACTIVE]),
            "total_clusters": len(self.clusters),
            "total_synapses": len(self.synapses),
        }
