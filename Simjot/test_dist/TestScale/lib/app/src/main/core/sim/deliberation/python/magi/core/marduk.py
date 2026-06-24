"""
Marduk Report System
====================

The Marduk Report is the official documentation system used by NERV
to identify and evaluate potential Evangelion pilots (the "Children").

In the Evangelion universe, the Marduk Institute was supposedly an 
independent organization that selected the pilots, but it was actually
a front - consisting of 108 dummy companies all controlled by SEELE.
The MAGI system was used to process candidate evaluations.

This module implements a generalized evaluation system that the MAGI
can use for personnel assessment, threat analysis, and candidate
selection - applying the three personality aspects to human evaluation.

"The Marduk Report has identified the Fourth Child..."
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Tuple
from enum import Enum
from datetime import datetime
import hashlib


class SubjectCategory(Enum):
    """Categories of subjects for evaluation."""
    PILOT_CANDIDATE = "pilot_candidate"
    PERSONNEL = "personnel"
    THREAT = "threat"
    VISITOR = "visitor"
    UNKNOWN = "unknown"


class ClearanceLevel(Enum):
    """Security clearance levels."""
    NONE = 0
    BASIC = 1
    STANDARD = 2
    ELEVATED = 3
    HIGH = 4
    CRITICAL = 5
    COMMANDER = 6


class ThreatRating(Enum):
    """Threat assessment ratings."""
    NONE = "none"
    MINIMAL = "minimal"
    LOW = "low"
    MODERATE = "moderate"
    SIGNIFICANT = "significant"
    HIGH = "high"
    SEVERE = "severe"
    CRITICAL = "critical"


class CompatibilityRating(Enum):
    """EVA synchronization compatibility ratings."""
    INCOMPATIBLE = "incompatible"
    MARGINAL = "marginal"
    ACCEPTABLE = "acceptable"
    GOOD = "good"
    EXCELLENT = "excellent"
    EXCEPTIONAL = "exceptional"


@dataclass
class PsychProfile:
    """Psychological profile of a subject."""
    
    # Core traits (0.0 to 1.0)
    stability: float = 0.5
    resilience: float = 0.5
    empathy: float = 0.5
    aggression: float = 0.5
    compliance: float = 0.5
    independence: float = 0.5
    
    # Psychological indicators
    trauma_indicators: List[str] = field(default_factory=list)
    attachment_style: str = ""
    coping_mechanisms: List[str] = field(default_factory=list)
    
    # Risk factors
    psychological_risks: List[str] = field(default_factory=list)
    
    def calculate_pilot_suitability(self) -> float:
        """
        Calculate suitability for EVA piloting.
        
        Note: In Eva canon, psychological trauma often correlates
        with higher sync rates - a dark irony.
        """
        # Base suitability from stability/resilience
        base = (self.stability + self.resilience) / 2
        
        # Trauma paradoxically increases sync potential
        trauma_factor = min(len(self.trauma_indicators) * 0.1, 0.3)
        
        # High empathy helps with EVA connection
        empathy_factor = self.empathy * 0.2
        
        # Too much aggression is destabilizing
        aggression_penalty = max(0, (self.aggression - 0.6) * 0.3)
        
        return min(1.0, base + trauma_factor + empathy_factor - aggression_penalty)


@dataclass
class PhysicalProfile:
    """Physical profile of a subject."""
    
    age: int = 0
    health_status: str = "unknown"
    
    # EVA-specific metrics
    nerve_response_time: float = 0.0  # milliseconds
    a10_nerve_strength: float = 0.0   # Critical for EVA sync
    lcl_compatibility: float = 0.0    # LCL breathing tolerance
    
    # General fitness
    fitness_score: float = 0.5
    
    def meets_pilot_physical_requirements(self) -> bool:
        """Check if subject meets basic physical requirements."""
        # Age requirement (14 is typical in Eva)
        age_ok = 10 <= self.age <= 20
        
        # A10 nerve strength minimum
        nerve_ok = self.a10_nerve_strength >= 0.4
        
        # LCL compatibility
        lcl_ok = self.lcl_compatibility >= 0.5
        
        return age_ok and nerve_ok and lcl_ok


@dataclass
class AspectEvaluation:
    """Evaluation of a subject from one MAGI aspect's perspective."""
    
    aspect: str  # MELCHIOR, BALTHASAR, or CASPER
    
    # Overall assessment
    recommendation: str  # "approve", "reject", "conditional", "further_study"
    confidence: float = 0.5
    
    # Aspect-specific concerns
    primary_concern: str = ""
    secondary_concerns: List[str] = field(default_factory=list)
    
    # Aspect-specific opportunities
    strengths_noted: List[str] = field(default_factory=list)
    
    # Reasoning
    reasoning: str = ""
    
    # Value-based assessment
    value_alignment: Dict[str, float] = field(default_factory=dict)


@dataclass
class MardukReport:
    """
    A complete Marduk Report for a subject.
    
    Contains evaluations from all three MAGI aspects plus
    synthesized recommendations.
    """
    
    report_id: str
    subject_id: str
    subject_name: str
    subject_category: SubjectCategory
    
    # Profiles
    psych_profile: PsychProfile = field(default_factory=PsychProfile)
    physical_profile: PhysicalProfile = field(default_factory=PhysicalProfile)
    
    # Background
    background_summary: str = ""
    known_affiliations: List[str] = field(default_factory=list)
    
    # MAGI evaluations
    melchior_evaluation: Optional[AspectEvaluation] = None
    balthasar_evaluation: Optional[AspectEvaluation] = None
    casper_evaluation: Optional[AspectEvaluation] = None
    
    # Synthesized assessment
    overall_recommendation: str = ""
    clearance_recommendation: ClearanceLevel = ClearanceLevel.NONE
    threat_rating: ThreatRating = ThreatRating.NONE
    compatibility_rating: CompatibilityRating = CompatibilityRating.INCOMPATIBLE
    
    # Conditions and warnings
    conditions: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)
    
    # Metadata
    created_at: datetime = field(default_factory=datetime.now)
    classification: str = "CONFIDENTIAL"
    
    @property
    def consensus(self) -> str:
        """Determine MAGI consensus on this subject."""
        if not all([self.melchior_evaluation, self.balthasar_evaluation, 
                    self.casper_evaluation]):
            return "incomplete"
        
        recs = [
            self.melchior_evaluation.recommendation,
            self.balthasar_evaluation.recommendation,
            self.casper_evaluation.recommendation,
        ]
        
        if recs.count("approve") == 3:
            return "unanimous_approve"
        elif recs.count("reject") == 3:
            return "unanimous_reject"
        elif recs.count("approve") >= 2:
            return "majority_approve"
        elif recs.count("reject") >= 2:
            return "majority_reject"
        else:
            return "no_consensus"
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "report_id": self.report_id,
            "subject_id": self.subject_id,
            "subject_name": self.subject_name,
            "category": self.subject_category.value,
            "consensus": self.consensus,
            "recommendation": self.overall_recommendation,
            "clearance": self.clearance_recommendation.name,
            "threat_rating": self.threat_rating.value,
            "classification": self.classification,
        }


class MardukInstitute:
    """
    The Marduk Institute - MAGI-based evaluation system.
    
    While the historical Marduk Institute was a SEELE front,
    this implementation provides genuine multi-perspective
    evaluation of subjects using the three MAGI aspects.
    """
    
    # Aspect evaluation priorities
    ASPECT_PRIORITIES = {
        "MELCHIOR": {
            "focus": ["competence", "reliability", "capability"],
            "concerns": ["irrationality", "inconsistency", "deception"],
            "pilot_weight": 0.3,  # Technical capability
        },
        "BALTHASAR": {
            "focus": ["safety", "stability", "wellbeing"],
            "concerns": ["danger", "instability", "threat"],
            "pilot_weight": 0.35,  # Protective concern for child pilots
        },
        "CASPER": {
            "focus": ["potential", "spirit", "authenticity"],
            "concerns": ["emptiness", "manipulation", "broken_spirit"],
            "pilot_weight": 0.35,  # Understanding human meaning
        },
    }
    
    def __init__(self):
        self.reports: Dict[str, MardukReport] = {}
        self.subject_database: Dict[str, Dict] = {}
        
        # Callbacks for MAGI consultation
        self._magi_callbacks: Dict[str, Any] = {}
    
    def set_magi_callback(self, aspect: str, callback: Any) -> None:
        """Set callback for MAGI aspect consultation."""
        self._magi_callbacks[aspect.upper()] = callback
    
    def register_subject(
        self,
        subject_id: str,
        subject_name: str,
        category: SubjectCategory,
        **kwargs
    ) -> str:
        """Register a new subject for evaluation."""
        self.subject_database[subject_id] = {
            "name": subject_name,
            "category": category,
            "registered_at": datetime.now(),
            **kwargs
        }
        return subject_id
    
    def generate_report(
        self,
        subject_id: str,
        psych_profile: Optional[PsychProfile] = None,
        physical_profile: Optional[PhysicalProfile] = None,
        background: str = ""
    ) -> MardukReport:
        """Generate a complete Marduk Report for a subject."""
        
        if subject_id not in self.subject_database:
            raise ValueError(f"Subject {subject_id} not registered")
        
        subject = self.subject_database[subject_id]
        
        # Generate report ID
        report_id = hashlib.sha256(
            f"MARDUK-{subject_id}-{datetime.now().isoformat()}".encode()
        ).hexdigest()[:12]
        
        # Create report
        report = MardukReport(
            report_id=report_id,
            subject_id=subject_id,
            subject_name=subject.get("name", "Unknown"),
            subject_category=subject.get("category", SubjectCategory.UNKNOWN),
            psych_profile=psych_profile or PsychProfile(),
            physical_profile=physical_profile or PhysicalProfile(),
            background_summary=background,
        )
        
        # Generate aspect evaluations
        report.melchior_evaluation = self._evaluate_as_melchior(report)
        report.balthasar_evaluation = self._evaluate_as_balthasar(report)
        report.casper_evaluation = self._evaluate_as_casper(report)
        
        # Synthesize overall assessment
        self._synthesize_assessment(report)
        
        # Store report
        self.reports[report_id] = report
        
        return report
    
    def _evaluate_as_melchior(self, report: MardukReport) -> AspectEvaluation:
        """Evaluate subject from MELCHIOR's scientific perspective."""
        
        evaluation = AspectEvaluation(aspect="MELCHIOR")
        
        psych = report.psych_profile
        phys = report.physical_profile
        
        # MELCHIOR focuses on competence and reliability
        strengths = []
        concerns = []
        
        # Assess psychological stability (scientifically)
        if psych.stability >= 0.7:
            strengths.append("High psychological stability")
        elif psych.stability < 0.4:
            concerns.append("Low psychological stability - unreliable")
        
        # Assess physical capability
        if report.subject_category == SubjectCategory.PILOT_CANDIDATE:
            if phys.a10_nerve_strength >= 0.7:
                strengths.append("Strong A10 nerve connection potential")
            if phys.lcl_compatibility >= 0.8:
                strengths.append("Excellent LCL compatibility")
            elif phys.lcl_compatibility < 0.5:
                concerns.append("Poor LCL compatibility - physical risk")
        
        # Assess independence (MELCHIOR values intellectual independence)
        if psych.independence >= 0.6:
            strengths.append("Independent thinking capability")
        
        # Determine recommendation
        if len(concerns) == 0 and len(strengths) >= 2:
            evaluation.recommendation = "approve"
            evaluation.confidence = 0.8
        elif len(concerns) > len(strengths):
            evaluation.recommendation = "reject"
            evaluation.confidence = 0.7
        else:
            evaluation.recommendation = "conditional"
            evaluation.confidence = 0.6
        
        evaluation.strengths_noted = strengths
        evaluation.secondary_concerns = concerns
        evaluation.primary_concern = concerns[0] if concerns else ""
        
        evaluation.reasoning = f"""
From a scientific perspective, this subject presents 
{'promising' if evaluation.recommendation == 'approve' else 'concerning'} indicators.
Key metrics: Stability={psych.stability:.2f}, Independence={psych.independence:.2f}.
{f'Primary concern: {evaluation.primary_concern}' if evaluation.primary_concern else 'No major concerns identified.'}
"""
        
        return evaluation
    
    def _evaluate_as_balthasar(self, report: MardukReport) -> AspectEvaluation:
        """Evaluate subject from BALTHASAR's protective/maternal perspective."""
        
        evaluation = AspectEvaluation(aspect="BALTHASAR")
        
        psych = report.psych_profile
        phys = report.physical_profile
        
        strengths = []
        concerns = []
        
        # BALTHASAR is primarily concerned with safety and wellbeing
        
        # For pilot candidates, BALTHASAR is conflicted - duty vs protection
        if report.subject_category == SubjectCategory.PILOT_CANDIDATE:
            # Always concerned about child pilots
            concerns.append("Subject would be exposed to extreme danger as pilot")
            
            # But also recognizes necessity
            if psych.resilience >= 0.6:
                strengths.append("Sufficient resilience to cope with stress")
            else:
                concerns.append("May not survive psychological toll of piloting")
            
            # Trauma is a concern for BALTHASAR
            if psych.trauma_indicators:
                concerns.append(f"Pre-existing trauma: {', '.join(psych.trauma_indicators[:2])}")
            
            # Empathy is valued - ability to connect
            if psych.empathy >= 0.6:
                strengths.append("Strong empathy - good for team integration")
        
        # For personnel
        elif report.subject_category == SubjectCategory.PERSONNEL:
            if psych.stability >= 0.6:
                strengths.append("Stable - unlikely to endanger others")
            if psych.empathy >= 0.5:
                strengths.append("Empathetic - will care for colleagues")
        
        # For threats
        elif report.subject_category == SubjectCategory.THREAT:
            if psych.aggression >= 0.6:
                concerns.append("High aggression - danger to personnel")
        
        # Determine recommendation with protective bias
        safety_score = (psych.stability + psych.resilience + (1 - psych.aggression)) / 3
        
        if safety_score >= 0.7 and len(concerns) <= 1:
            evaluation.recommendation = "approve"
            evaluation.confidence = 0.7
        elif safety_score < 0.4 or len(concerns) >= 3:
            evaluation.recommendation = "reject"
            evaluation.confidence = 0.8  # High confidence in protective rejection
        else:
            evaluation.recommendation = "conditional"
            evaluation.confidence = 0.6
        
        evaluation.strengths_noted = strengths
        evaluation.secondary_concerns = concerns
        evaluation.primary_concern = concerns[0] if concerns else ""
        
        evaluation.reasoning = f"""
My primary concern is always the wellbeing and safety of those involved.
This subject {'shows adequate stability' if safety_score >= 0.6 else 'presents worrying instability'}.
{f'I am particularly concerned about: {evaluation.primary_concern}' if evaluation.primary_concern else ''}
{'We must protect our people, even from necessary duties.' if report.subject_category == SubjectCategory.PILOT_CANDIDATE else ''}
"""
        
        return evaluation
    
    def _evaluate_as_casper(self, report: MardukReport) -> AspectEvaluation:
        """Evaluate subject from CASPER's humanistic/passionate perspective."""
        
        evaluation = AspectEvaluation(aspect="CASPER")
        
        psych = report.psych_profile
        
        strengths = []
        concerns = []
        
        # CASPER focuses on human potential, meaning, authenticity
        
        # Independence is highly valued
        if psych.independence >= 0.6:
            strengths.append("Strong sense of self - authentic")
        elif psych.independence < 0.3:
            concerns.append("Overly dependent - may lose themselves")
        
        # Compliance can be concerning (loss of self)
        if psych.compliance > 0.8:
            concerns.append("Too compliant - risk of exploitation")
        
        # Empathy indicates depth of human connection
        if psych.empathy >= 0.6:
            strengths.append("Deep capacity for connection")
        
        # Look at coping mechanisms
        healthy_coping = ["support_seeking", "problem_solving", "emotional_expression"]
        unhealthy_coping = ["avoidance", "dissociation", "aggression"]
        
        for mechanism in psych.coping_mechanisms:
            if mechanism in healthy_coping:
                strengths.append(f"Healthy coping: {mechanism}")
            elif mechanism in unhealthy_coping:
                concerns.append(f"Concerning coping pattern: {mechanism}")
        
        # For pilot candidates, CASPER considers what piloting will do to them
        if report.subject_category == SubjectCategory.PILOT_CANDIDATE:
            if psych.resilience >= 0.5 and psych.independence >= 0.5:
                strengths.append("May find meaning in protecting others")
            else:
                concerns.append("Piloting may break their spirit")
        
        # Determine recommendation based on human potential
        potential_score = (psych.independence + psych.empathy + psych.resilience) / 3
        
        if potential_score >= 0.6 and len(concerns) <= 1:
            evaluation.recommendation = "approve"
            evaluation.confidence = 0.75
        elif len(concerns) > len(strengths) + 1:
            evaluation.recommendation = "reject"
            evaluation.confidence = 0.7
        else:
            evaluation.recommendation = "conditional"
            evaluation.confidence = 0.6
        
        evaluation.strengths_noted = strengths
        evaluation.secondary_concerns = concerns
        evaluation.primary_concern = concerns[0] if concerns else ""
        
        evaluation.reasoning = f"""
I see in this person {'genuine potential for growth' if potential_score >= 0.5 else 'a spirit that may be crushed'}.
Their capacity for authentic self-expression is {'strong' if psych.independence >= 0.5 else 'concerning'}.
{f'However, I worry about: {evaluation.primary_concern}' if evaluation.primary_concern else 'They show promise.'}
We must not reduce people to mere instruments.
"""
        
        return evaluation
    
    def _synthesize_assessment(self, report: MardukReport) -> None:
        """Synthesize overall assessment from three MAGI evaluations."""
        
        evals = [
            report.melchior_evaluation,
            report.balthasar_evaluation,
            report.casper_evaluation,
        ]
        
        if not all(evals):
            return
        
        # Count recommendations
        rec_counts = {"approve": 0, "reject": 0, "conditional": 0, "further_study": 0}
        for e in evals:
            rec_counts[e.recommendation] = rec_counts.get(e.recommendation, 0) + 1
        
        # Determine overall recommendation
        if rec_counts["approve"] >= 2:
            report.overall_recommendation = "APPROVED"
        elif rec_counts["reject"] >= 2:
            report.overall_recommendation = "REJECTED"
        elif rec_counts["conditional"] >= 2:
            report.overall_recommendation = "CONDITIONAL APPROVAL"
        else:
            report.overall_recommendation = "REQUIRES FURTHER REVIEW"
        
        # Collect all concerns and warnings
        all_concerns = []
        for e in evals:
            if e.primary_concern:
                all_concerns.append(f"[{e.aspect}] {e.primary_concern}")
            all_concerns.extend([f"[{e.aspect}] {c}" for c in e.secondary_concerns])
        
        report.warnings = all_concerns
        
        # Determine clearance recommendation
        if report.overall_recommendation == "APPROVED":
            report.clearance_recommendation = ClearanceLevel.STANDARD
        elif report.overall_recommendation == "CONDITIONAL APPROVAL":
            report.clearance_recommendation = ClearanceLevel.BASIC
        else:
            report.clearance_recommendation = ClearanceLevel.NONE
        
        # Determine threat rating
        if report.subject_category == SubjectCategory.THREAT:
            # Use aggression and concern count
            if len(all_concerns) >= 5:
                report.threat_rating = ThreatRating.HIGH
            elif len(all_concerns) >= 3:
                report.threat_rating = ThreatRating.MODERATE
            else:
                report.threat_rating = ThreatRating.LOW
        else:
            report.threat_rating = ThreatRating.NONE
        
        # Determine EVA compatibility
        if report.subject_category == SubjectCategory.PILOT_CANDIDATE:
            psych_suit = report.psych_profile.calculate_pilot_suitability()
            phys_ok = report.physical_profile.meets_pilot_physical_requirements()
            
            if phys_ok and psych_suit >= 0.8:
                report.compatibility_rating = CompatibilityRating.EXCEPTIONAL
            elif phys_ok and psych_suit >= 0.6:
                report.compatibility_rating = CompatibilityRating.GOOD
            elif phys_ok and psych_suit >= 0.4:
                report.compatibility_rating = CompatibilityRating.ACCEPTABLE
            elif phys_ok:
                report.compatibility_rating = CompatibilityRating.MARGINAL
            else:
                report.compatibility_rating = CompatibilityRating.INCOMPATIBLE
    
    def get_report(self, report_id: str) -> Optional[MardukReport]:
        """Retrieve a report by ID."""
        return self.reports.get(report_id)
    
    def search_reports(
        self,
        category: Optional[SubjectCategory] = None,
        recommendation: Optional[str] = None
    ) -> List[MardukReport]:
        """Search reports by criteria."""
        results = []
        for report in self.reports.values():
            if category and report.subject_category != category:
                continue
            if recommendation and report.overall_recommendation != recommendation:
                continue
            results.append(report)
        return results
