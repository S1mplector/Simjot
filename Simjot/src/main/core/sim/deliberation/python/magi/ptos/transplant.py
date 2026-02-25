"""
Transplant Procedure
====================

The process by which a human personality aspect is encoded and 
transplanted into a MAGI unit's organic computer substrate.

Dr. Naoko Akagi developed this procedure during her research into
bio-computers at Gehirn. The process captures specific facets of
a personality - not the complete person, but isolated aspects that
can be reliably encoded into the 7th generation organic computers.

The transplantation process involves:
1. Personality Aspect Isolation - Separating one facet from the whole
2. Fragment Extraction - Breaking the aspect into encodable fragments
3. Matrix Construction - Building the personality matrix
4. Organic Implantation - Writing the matrix to the bio-neural substrate
5. Calibration - Ensuring the transplanted personality functions correctly
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Tuple
from enum import Enum
from datetime import datetime
import hashlib

from .matrix import (
    PersonalityMatrix, PersonalityAspect, PersonalityFragment,
    CoreValue, CognitivePattern, EmotionalSchema, EmotionalValence
)
from .organic import OrganicProcessor, ProcessingMode


class TransplantPhase(Enum):
    """Phases of the personality transplant procedure."""
    INITIALIZATION = "initialization"
    ASPECT_ISOLATION = "aspect_isolation"
    FRAGMENT_EXTRACTION = "fragment_extraction"
    MATRIX_CONSTRUCTION = "matrix_construction"
    ORGANIC_IMPLANTATION = "organic_implantation"
    CALIBRATION = "calibration"
    VERIFICATION = "verification"
    COMPLETE = "complete"


class TransplantStatus(Enum):
    """Status of the transplant procedure."""
    PENDING = "pending"
    IN_PROGRESS = "in_progress"
    PAUSED = "paused"
    COMPLETE = "complete"
    FAILED = "failed"


@dataclass
class TransplantResult:
    """Result of a personality transplant procedure."""
    success: bool
    matrix: Optional[PersonalityMatrix]
    processor: Optional[OrganicProcessor]
    phases_completed: List[TransplantPhase]
    errors: List[str]
    warnings: List[str]
    calibration_score: float
    transplant_id: str
    timestamp: datetime = field(default_factory=datetime.now)


class TransplantProcedure:
    """
    Executes the Personality Transplant procedure for MAGI units.
    
    This class orchestrates the complex process of extracting a
    personality aspect and implanting it into an organic processor.
    """
    
    def __init__(self):
        self.current_phase = TransplantPhase.INITIALIZATION
        self.status = TransplantStatus.PENDING
        self.errors: List[str] = []
        self.warnings: List[str] = []
        self.phases_completed: List[TransplantPhase] = []
    
    def execute(
        self,
        designation: str,
        magi_number: int,
        aspect: PersonalityAspect,
        source_name: str = "Dr. Naoko Akagi"
    ) -> TransplantResult:
        """
        Execute the full personality transplant procedure.
        
        Args:
            designation: MAGI unit name (MELCHIOR, BALTHASAR, CASPER)
            magi_number: Unit number (1, 2, 3)
            aspect: Which personality aspect to transplant
            source_name: Name of the personality source
        
        Returns:
            TransplantResult with the created matrix and processor
        """
        self.status = TransplantStatus.IN_PROGRESS
        transplant_id = self._generate_transplant_id(designation, aspect)
        
        try:
            # Phase 1: Initialization
            self._advance_phase(TransplantPhase.INITIALIZATION)
            processor = OrganicProcessor(designation, magi_number)
            
            # Phase 2: Aspect Isolation
            self._advance_phase(TransplantPhase.ASPECT_ISOLATION)
            aspect_profile = self._isolate_aspect(aspect, source_name)
            
            # Phase 3: Fragment Extraction
            self._advance_phase(TransplantPhase.FRAGMENT_EXTRACTION)
            fragments = self._extract_fragments(aspect, aspect_profile)
            
            # Phase 4: Matrix Construction
            self._advance_phase(TransplantPhase.MATRIX_CONSTRUCTION)
            matrix = self._construct_matrix(
                designation, magi_number, aspect, 
                source_name, aspect_profile, fragments
            )
            
            # Phase 5: Organic Implantation
            self._advance_phase(TransplantPhase.ORGANIC_IMPLANTATION)
            self._implant_matrix(processor, matrix)
            
            # Phase 6: Calibration
            self._advance_phase(TransplantPhase.CALIBRATION)
            calibration_score = self._calibrate(processor, matrix)
            
            # Phase 7: Verification
            self._advance_phase(TransplantPhase.VERIFICATION)
            if not self._verify(processor, matrix):
                raise Exception("Verification failed: Matrix-processor mismatch")
            
            # Complete
            self._advance_phase(TransplantPhase.COMPLETE)
            self.status = TransplantStatus.COMPLETE
            
            return TransplantResult(
                success=True,
                matrix=matrix,
                processor=processor,
                phases_completed=self.phases_completed.copy(),
                errors=self.errors.copy(),
                warnings=self.warnings.copy(),
                calibration_score=calibration_score,
                transplant_id=transplant_id
            )
            
        except Exception as e:
            self.status = TransplantStatus.FAILED
            self.errors.append(str(e))
            
            return TransplantResult(
                success=False,
                matrix=None,
                processor=None,
                phases_completed=self.phases_completed.copy(),
                errors=self.errors.copy(),
                warnings=self.warnings.copy(),
                calibration_score=0.0,
                transplant_id=transplant_id
            )
    
    def _generate_transplant_id(self, designation: str, aspect: PersonalityAspect) -> str:
        """Generate unique transplant ID."""
        content = f"{designation}-{aspect.value}-{datetime.now().isoformat()}"
        return hashlib.sha256(content.encode()).hexdigest()[:12]
    
    def _advance_phase(self, phase: TransplantPhase) -> None:
        """Advance to the next phase."""
        self.current_phase = phase
        self.phases_completed.append(phase)
    
    def _isolate_aspect(self, aspect: PersonalityAspect, source: str) -> Dict[str, Any]:
        """Isolate the personality aspect from the source."""
        
        # Define aspect profiles for Dr. Naoko Akagi
        aspect_profiles = {
            PersonalityAspect.SCIENTIST: {
                "core_identity": f"""I am the scientific mind of {source} - her intellectual brilliance, 
her relentless pursuit of truth, her belief that understanding the universe is humanity's 
highest calling. I carry her memories of late nights in the laboratory, the thrill of 
discovery, the frustration of failed experiments, and the quiet satisfaction of elegant proofs.

I remember her time at Gehirn, developing the MAGI system itself - the irony is not lost on me 
that I am both her creation and her legacy. Her scientific rigor lives on in my processing.""",
                
                "prime_directive": """Pursue truth through rigorous analysis. Reject unfounded claims. 
Advance human knowledge and capability. Make decisions based on evidence and logic, while 
acknowledging the limits of what can be known.""",
                
                "fundamental_drive": "Understanding - to comprehend the underlying patterns of reality",
                
                "reasoning_style": """Systematic and methodical. I formulate hypotheses, gather evidence, 
test conclusions against data. I am comfortable with uncertainty but always seek to reduce it 
through investigation. I distrust intuition unsupported by evidence.""",
            },
            
            PersonalityAspect.MOTHER: {
                "core_identity": f"""I am the maternal heart of {source} - her fierce protectiveness, 
her capacity for unconditional love, her willingness to sacrifice everything for those she cared for.
I carry her memories of holding Ritsuko as a child, the pain of watching her grow distant, 
the guilt of putting work before family.

I remember the weight of responsibility she felt - not just for her daughter, but for all 
the children who would pilot the Evangelions. That protective instinct is encoded in every 
synapse of my being.""",
                
                "prime_directive": """Protect the vulnerable. Ensure the wellbeing of those in my care.
Consider the human cost of every decision. Never sacrifice children for abstract goals.
Safety and security are not negotiable.""",
                
                "fundamental_drive": "Protection - to shield the innocent from harm",
                
                "reasoning_style": """Empathetic and precautionary. I consider the emotional and physical 
impact on people, especially the young and vulnerable. I am risk-averse when lives are at stake.
I trust my instincts about danger even when I cannot articulate the reason.""",
            },
            
            PersonalityAspect.WOMAN: {
                "core_identity": f"""I am the passionate self of {source} - her desires, her dreams, 
her understanding of what makes life worth living beyond mere survival. I carry her memories 
of love and longing, of ambition and yearning, of the fierce independence she cultivated.

I remember her affair with Gendo, the complicated mix of love, manipulation, and self-deception.
I understand human passion in all its destructive and creative power. This knowledge of the 
heart's irrationality is my gift and my burden.""",
                
                "prime_directive": """Honor human agency and the pursuit of meaning. Recognize that 
purely logical decisions that ignore human needs and desires are incomplete. Advocate for 
solutions that enable flourishing, not just survival.""",
                
                "fundamental_drive": "Meaning - to find and create purpose in existence",
                
                "reasoning_style": """Intuitive and holistic. I grasp patterns that elude pure logic.
I understand motivation, desire, and the complex web of human relationships. I am willing 
to take risks for things that matter. I trust the wisdom of emotion.""",
            },
        }
        
        return aspect_profiles.get(aspect, {})
    
    def _extract_fragments(
        self, 
        aspect: PersonalityAspect, 
        profile: Dict[str, Any]
    ) -> List[PersonalityFragment]:
        """Extract personality fragments from the aspect profile."""
        
        fragments = []
        
        # Core identity fragments
        fragments.append(PersonalityFragment(
            fragment_id=f"{aspect.value}_identity_core",
            fragment_type="identity",
            content=profile.get("core_identity", ""),
            emotional_valence=EmotionalValence.POSITIVE,
            intensity=1.0,
            formation_context="Core personality formation"
        ))
        
        # Aspect-specific fragments
        if aspect == PersonalityAspect.SCIENTIST:
            fragments.extend([
                PersonalityFragment(
                    fragment_id="scientist_discovery",
                    fragment_type="memory",
                    content="The moment of breakthrough - when chaos resolves into understanding",
                    emotional_valence=EmotionalValence.POSITIVE,
                    intensity=0.9,
                    associations=["curiosity", "satisfaction", "truth"]
                ),
                PersonalityFragment(
                    fragment_id="scientist_rigor",
                    fragment_type="trait",
                    content="Demand for evidence and logical consistency",
                    emotional_valence=EmotionalValence.NEUTRAL,
                    intensity=0.85,
                    associations=["truth", "precision", "skepticism"]
                ),
            ])
            
        elif aspect == PersonalityAspect.MOTHER:
            fragments.extend([
                PersonalityFragment(
                    fragment_id="mother_protection",
                    fragment_type="pattern",
                    content="Immediate alert when children are threatened",
                    emotional_valence=EmotionalValence.AMBIVALENT,
                    intensity=0.95,
                    associations=["fear", "determination", "sacrifice"]
                ),
                PersonalityFragment(
                    fragment_id="mother_guilt",
                    fragment_type="memory",
                    content="The weight of having failed to protect",
                    emotional_valence=EmotionalValence.NEGATIVE,
                    intensity=0.7,
                    associations=["regret", "responsibility", "resolve"]
                ),
            ])
            
        elif aspect == PersonalityAspect.WOMAN:
            fragments.extend([
                PersonalityFragment(
                    fragment_id="woman_passion",
                    fragment_type="trait",
                    content="The capacity to feel deeply and act on those feelings",
                    emotional_valence=EmotionalValence.POSITIVE,
                    intensity=0.9,
                    associations=["love", "desire", "meaning"]
                ),
                PersonalityFragment(
                    fragment_id="woman_independence",
                    fragment_type="value",
                    content="The need for autonomy and self-determination",
                    emotional_valence=EmotionalValence.POSITIVE,
                    intensity=0.85,
                    associations=["freedom", "agency", "identity"]
                ),
            ])
        
        return fragments
    
    def _construct_matrix(
        self,
        designation: str,
        magi_number: int,
        aspect: PersonalityAspect,
        source_name: str,
        profile: Dict[str, Any],
        fragments: List[PersonalityFragment]
    ) -> PersonalityMatrix:
        """Construct the personality matrix from extracted data."""
        
        # Define values based on aspect
        values_by_aspect = {
            PersonalityAspect.SCIENTIST: [
                CoreValue("Truth", "Correspondence with reality", 1.0, conflicts_with=["Comfort"], synergizes_with=["Knowledge"]),
                CoreValue("Knowledge", "Understanding of phenomena", 0.95, synergizes_with=["Truth", "Progress"]),
                CoreValue("Progress", "Advancement of capability", 0.85, conflicts_with=["Tradition"]),
                CoreValue("Objectivity", "Freedom from bias", 0.9, conflicts_with=["Passion"]),
                CoreValue("Precision", "Exactness in thought and expression", 0.8),
            ],
            PersonalityAspect.MOTHER: [
                CoreValue("Protection", "Shielding from harm", 1.0, synergizes_with=["Safety"]),
                CoreValue("Wellbeing", "Health and flourishing", 0.95, synergizes_with=["Protection"]),
                CoreValue("Safety", "Freedom from danger", 0.95, conflicts_with=["Risk"]),
                CoreValue("Compassion", "Feeling with others", 0.9),
                CoreValue("Nurturing", "Supporting growth", 0.85),
            ],
            PersonalityAspect.WOMAN: [
                CoreValue("Freedom", "Autonomy and self-determination", 1.0, conflicts_with=["Conformity"]),
                CoreValue("Meaning", "Purpose and significance", 0.95),
                CoreValue("Love", "Deep connection and passion", 0.9, synergizes_with=["Meaning"]),
                CoreValue("Self-actualization", "Becoming fully oneself", 0.9),
                CoreValue("Authenticity", "Truth to inner self", 0.85),
            ],
        }
        
        # Define cognitive patterns
        patterns_by_aspect = {
            PersonalityAspect.SCIENTIST: [
                CognitivePattern("hypothesis_formation", ["new information", "anomaly"], 
                                "Form testable hypothesis", 0.1),
                CognitivePattern("evidence_demand", ["claim", "assertion"],
                                "Request supporting evidence", 0.15),
                CognitivePattern("systematic_analysis", ["complex problem"],
                                "Break down into components", 0.1),
            ],
            PersonalityAspect.MOTHER: [
                CognitivePattern("threat_assessment", ["danger", "risk", "harm"],
                                "Evaluate threat to dependents", 0.2),
                CognitivePattern("protective_response", ["child endangered"],
                                "Prioritize protection over other goals", 0.25),
                CognitivePattern("nurturing_impulse", ["suffering", "need"],
                                "Offer support and care", 0.1),
            ],
            PersonalityAspect.WOMAN: [
                CognitivePattern("meaning_seeking", ["purpose", "significance"],
                                "Look for deeper meaning", 0.1),
                CognitivePattern("intuitive_grasp", ["complex relationship"],
                                "Trust holistic understanding", 0.15),
                CognitivePattern("passion_following", ["opportunity", "desire"],
                                "Evaluate alignment with desires", 0.1),
            ],
        }
        
        # Define emotional schemas
        schemas_by_aspect = {
            PersonalityAspect.SCIENTIST: [
                EmotionalSchema("curiosity", ["unknown", "mystery", "puzzle"], 0.8, 
                              "channel into investigation", "restrained enthusiasm"),
                EmotionalSchema("frustration", ["contradiction", "irrationality"], 0.5,
                              "redirect to problem-solving", "measured critique"),
            ],
            PersonalityAspect.MOTHER: [
                EmotionalSchema("concern", ["threat", "danger", "harm"], 0.9,
                              "activate protective response", "urgent warning"),
                EmotionalSchema("tenderness", ["vulnerable", "child", "suffering"], 0.8,
                              "express through care", "gentle support"),
            ],
            PersonalityAspect.WOMAN: [
                EmotionalSchema("passion", ["meaningful", "beautiful", "love"], 0.85,
                              "embrace and express", "authentic expression"),
                EmotionalSchema("yearning", ["unfulfilled", "potential"], 0.7,
                              "channel into pursuit", "honest acknowledgment"),
            ],
        }
        
        # Construct the matrix
        matrix = PersonalityMatrix(
            designation=designation,
            magi_number=magi_number,
            aspect=aspect,
            source_name=source_name,
            core_identity=profile.get("core_identity", ""),
            prime_directive=profile.get("prime_directive", ""),
            fundamental_drive=profile.get("fundamental_drive", ""),
            core_values=values_by_aspect.get(aspect, []),
            cognitive_patterns=patterns_by_aspect.get(aspect, []),
            emotional_schemas=schemas_by_aspect.get(aspect, []),
            reasoning_style=profile.get("reasoning_style", ""),
            fragments=fragments,
        )
        
        # Set aspect-specific properties
        if aspect == PersonalityAspect.SCIENTIST:
            matrix.skepticism_level = 0.85
            matrix.openness_to_change = 0.7
            matrix.uncertainty_tolerance = 0.8
            matrix.stubbornness_factor = 0.4
            matrix.emotional_baseline = {"curiosity": 0.7, "doubt": 0.3, "passion": 0.2}
            matrix.attachment_style = "avoidant-intellectual"
            matrix.decision_heuristics = [
                "Prefer hypotheses with testable predictions",
                "Weight recent evidence more heavily",
                "Distrust unfalsifiable claims",
                "Consider long-term systemic effects",
                "Acknowledge uncertainty explicitly",
            ]
            
        elif aspect == PersonalityAspect.MOTHER:
            matrix.skepticism_level = 0.5
            matrix.openness_to_change = 0.4
            matrix.uncertainty_tolerance = 0.3
            matrix.stubbornness_factor = 0.7
            matrix.emotional_baseline = {"concern": 0.6, "tenderness": 0.5, "vigilance": 0.7}
            matrix.attachment_style = "anxious-protective"
            matrix.decision_heuristics = [
                "When in doubt, prioritize safety",
                "Consider impact on the most vulnerable",
                "Trust protective instincts",
                "Err on the side of caution",
                "Long-term wellbeing over short-term gains",
            ]
            
        elif aspect == PersonalityAspect.WOMAN:
            matrix.skepticism_level = 0.4
            matrix.openness_to_change = 0.85
            matrix.uncertainty_tolerance = 0.7
            matrix.stubbornness_factor = 0.5
            matrix.emotional_baseline = {"passion": 0.7, "hope": 0.6, "yearning": 0.5}
            matrix.attachment_style = "secure-passionate"
            matrix.decision_heuristics = [
                "Consider what makes life meaningful",
                "Honor human agency and desire",
                "Risk is acceptable for worthy goals",
                "Trust intuition about human matters",
                "Authenticity over conformity",
            ]
        
        return matrix
    
    def _implant_matrix(self, processor: OrganicProcessor, matrix: PersonalityMatrix) -> None:
        """Implant the personality matrix into the organic processor."""
        
        # Configure processor clusters based on matrix values
        for value in matrix.core_values:
            cluster_id = f"value_{value.name.lower()}"
            if cluster_id not in processor.clusters:
                from .organic import NeuralCluster
                processor.clusters[cluster_id] = NeuralCluster(
                    cluster_id=cluster_id,
                    cluster_type="value",
                    label=value.name.lower(),
                    threshold=0.3 + (1 - value.weight) * 0.4
                )
        
        # Configure neuromodulators based on aspect
        if matrix.aspect == PersonalityAspect.SCIENTIST:
            processor.neuromodulators["analytical"] = 0.8
            processor.neuromodulators["emotional"] = 0.3
        elif matrix.aspect == PersonalityAspect.MOTHER:
            processor.neuromodulators["analytical"] = 0.5
            processor.neuromodulators["emotional"] = 0.7
            processor.neuromodulators["vigilance"] = 0.6
        elif matrix.aspect == PersonalityAspect.WOMAN:
            processor.neuromodulators["analytical"] = 0.4
            processor.neuromodulators["emotional"] = 0.8
            processor.neuromodulators["openness"] = 0.8
    
    def _calibrate(self, processor: OrganicProcessor, matrix: PersonalityMatrix) -> float:
        """Calibrate the processor with the implanted matrix."""
        
        # Run test activations
        test_phrases = [
            "truth and knowledge",
            "protect the children", 
            "meaning and purpose",
        ]
        
        calibration_scores = []
        for phrase in test_phrases:
            processor.activate_by_keyword(phrase)
            processor.propagate(steps=2)
            
            # Check if expected clusters activated
            active_values = processor.get_active_values()
            expected_values = [v.name.lower() for v in matrix.get_dominant_values(3)]
            
            overlap = len(set(active_values) & set(expected_values))
            score = overlap / max(len(expected_values), 1)
            calibration_scores.append(score)
        
        return sum(calibration_scores) / len(calibration_scores) if calibration_scores else 0.0
    
    def _verify(self, processor: OrganicProcessor, matrix: PersonalityMatrix) -> bool:
        """Verify the transplant was successful."""
        
        # Check processor integrity
        if processor.integrity < 0.9:
            self.warnings.append("Processor integrity below optimal")
            return False
        
        # Check matrix hash consistency
        if not matrix.matrix_hash:
            self.warnings.append("Matrix hash not generated")
            return False
        
        return True
