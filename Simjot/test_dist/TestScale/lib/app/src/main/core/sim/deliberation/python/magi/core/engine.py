"""
MAGI Decision Engine v0.1.0
===========================

The core decision engine that orchestrates the three MAGI brains,
manages deliberation rounds, and synthesizes final decisions.
"""

from dataclasses import dataclass, field
from typing import Optional, Dict, List, Any, Callable
from concurrent.futures import ThreadPoolExecutor, as_completed
import time
import re

from .brain import Brain, BrainConfig
from .decision import (
    Decision, Verdict, VerdictType, 
    DeliberationRound, ConsensusType
)
from ..llm.client import completion_token_kwargs, temperature_kwargs


@dataclass
class EngineConfig:
    """Configuration for the MAGI engine."""
    max_deliberation_rounds: int = 2
    enable_cross_examination: bool = True
    parallel_processing: bool = True
    consensus_threshold: float = 0.7  # Confidence threshold for consensus
    deadlock_resolution: str = "majority"  # "majority", "cautious", "optimistic"
    model: str = "gpt-5"
    temperature: float = 0.7


class MAGIEngine:
    """
    The MAGI Decision Engine.
    
    Orchestrates three brain instances through a deliberation process
    to reach consensus on questions presented to the system.
    
    The deliberation process:
    1. Question classification (yes/no, open-ended, analytical, etc.)
    2. Independent analysis by each brain
    3. Initial verdicts from each brain
    4. Cross-examination (optional)
    5. Updated verdicts considering other positions
    6. Consensus synthesis
    """
    
    def __init__(
        self,
        brains: List[Brain],
        config: Optional[EngineConfig] = None,
        llm_client: Optional[Any] = None
    ):
        if len(brains) != 3:
            raise ValueError("MAGI requires exactly 3 brains")
        
        self.brains = {brain.name.lower(): brain for brain in brains}
        self.config = config or EngineConfig()
        self._llm_client = llm_client
        
        # Set LLM client on all brains
        if llm_client:
            self.set_llm_client(llm_client)
        
        # Event callbacks
        self._on_brain_verdict: Optional[Callable] = None
        self._on_round_complete: Optional[Callable] = None
        self._on_decision_complete: Optional[Callable] = None
    
    def set_llm_client(self, client: Any) -> None:
        """Set the LLM client for all brains."""
        self._llm_client = client
        for brain in self.brains.values():
            brain.set_llm_client(client)
            brain.config.model = self.config.model
            brain.config.temperature = self.config.temperature
    
    def on_brain_verdict(self, callback: Callable) -> None:
        """Register callback for when a brain produces a verdict."""
        self._on_brain_verdict = callback
    
    def on_round_complete(self, callback: Callable) -> None:
        """Register callback for when a deliberation round completes."""
        self._on_round_complete = callback
    
    def on_decision_complete(self, callback: Callable) -> None:
        """Register callback for when final decision is ready."""
        self._on_decision_complete = callback
    
    def classify_question(self, question: str) -> str:
        """
        Classify the type of question being asked.
        
        Returns: "yes_no", "open", "analytical", "ethical", "predictive"
        """
        q = (question or "").strip().lower()

        # Fast local heuristic path to reduce extra LLM calls.
        if q:
            first = re.split(r"\s+", q, maxsplit=1)[0]
            yes_no_starters = {
                "is", "are", "am", "was", "were",
                "do", "does", "did",
                "can", "could", "should", "would", "will",
                "have", "has", "had",
                "may", "might", "must",
            }
            if first in yes_no_starters:
                return "yes_no"
            if any(word in q for word in ["moral", "ethic", "right thing", "wrong", "should we"]):
                return "ethical"
            if any(word in q for word in ["predict", "forecast", "future", "probability", "likely"]):
                return "predictive"
            if any(word in q for word in ["analyze", "analyse", "compare", "tradeoff", "optimize", "evaluate"]):
                return "analytical"
        
        if not self._llm_client:
            return "open"
        
        messages = [
            {"role": "system", "content": """You classify questions by type. Answer with ONLY one of these exact words:
- yes_no (questions that can be answered with yes/no/maybe)
- open (general questions seeking information or explanation)
- analytical (questions requiring analysis of data or situations)
- ethical (questions about morality, ethics, or values)
- predictive (questions about future outcomes or probabilities)"""},
            {"role": "user", "content": f"Classify this question: {question}"}
        ]
        
        response = self._llm_client.chat.completions.create(
            model=self.config.model,
            messages=messages,
            **completion_token_kwargs(self.config.model, 20),
            **temperature_kwargs(self.config.model, 0.0),
        )
        
        result = response.choices[0].message.content.strip().lower()
        
        # Validate response
        valid_types = {"yes_no", "open", "analytical", "ethical", "predictive"}
        if result in valid_types:
            return result
        
        # Fallback: check for keywords
        if any(word in result for word in ["yes", "no"]):
            return "yes_no"
        
        return "open"
    
    def deliberate(self, question: str) -> Decision:
        """
        Main entry point: Run full deliberation process on a question.
        
        Returns a Decision object with the final outcome and all deliberation history.
        """
        start_time = time.time()
        
        # Reset all brains
        for brain in self.brains.values():
            brain.reset()
        
        # Initialize decision
        decision = Decision(question=question)
        
        # Classify question
        decision.question_type = self.classify_question(question)
        
        # Run deliberation rounds
        for round_num in range(1, self.config.max_deliberation_rounds + 1):
            round_result = self._run_deliberation_round(
                question=question,
                question_type=decision.question_type,
                round_number=round_num,
                previous_round=decision.rounds[-1] if decision.rounds else None
            )
            
            decision.rounds.append(round_result)
            
            if self._on_round_complete:
                self._on_round_complete(round_num, round_result)
            
            # Check for early consensus
            if round_result.has_consensus and round_num < self.config.max_deliberation_rounds:
                break
        
        # Synthesize final decision
        self._synthesize_decision(decision)
        
        decision.processing_time_ms = (time.time() - start_time) * 1000
        
        if self._on_decision_complete:
            self._on_decision_complete(decision)
        
        return decision
    
    def _run_deliberation_round(
        self,
        question: str,
        question_type: str,
        round_number: int,
        previous_round: Optional[DeliberationRound]
    ) -> DeliberationRound:
        """Run a single round of deliberation."""
        
        # Gather previous positions for rounds > 1
        other_positions = None
        if previous_round and round_number > 1:
            other_positions = {
                name: v.summary + " " + v.reasoning[:500]
                for name, v in previous_round.verdicts.items()
            }
        
        # Get verdicts from all brains
        if self.config.parallel_processing:
            verdicts = self._get_verdicts_parallel(
                question, question_type, round_number, other_positions
            )
        else:
            verdicts = self._get_verdicts_sequential(
                question, question_type, round_number, other_positions
            )
        
        round_result = DeliberationRound(
            round_number=round_number,
            verdicts=verdicts
        )
        
        # Cross-examination (if enabled and not last round)
        if (self.config.enable_cross_examination and 
            round_number < self.config.max_deliberation_rounds):
            round_result.cross_examinations = self._run_cross_examination(
                question, verdicts
            )
        
        return round_result
    
    def _get_verdicts_parallel(
        self,
        question: str,
        question_type: str,
        round_number: int,
        other_positions: Optional[Dict[str, str]]
    ) -> Dict[str, Verdict]:
        """Get verdicts from all brains in parallel."""
        verdicts = {}
        
        with ThreadPoolExecutor(max_workers=3) as executor:
            futures = {}
            for name, brain in self.brains.items():
                # Filter out own position from other_positions
                filtered_positions = None
                if other_positions:
                    filtered_positions = {
                        k: v for k, v in other_positions.items() 
                        if k.lower() != name.lower()
                    }
                
                future = executor.submit(
                    brain.form_verdict,
                    question=question,
                    round_number=round_number,
                    other_positions=filtered_positions
                )
                futures[future] = name
            
            for future in as_completed(futures):
                name = futures[future]
                try:
                    verdict = future.result()
                    verdicts[name] = verdict
                    
                    if self._on_brain_verdict:
                        self._on_brain_verdict(name, verdict)
                        
                except Exception as e:
                    # Create error verdict
                    verdicts[name] = Verdict(
                        brain_name=name,
                        verdict_type=VerdictType.ABSTAIN,
                        confidence=0.0,
                        summary=f"Error: {str(e)}",
                        reasoning=str(e),
                        deliberation_round=round_number
                    )
        
        return verdicts
    
    def _get_verdicts_sequential(
        self,
        question: str,
        question_type: str,
        round_number: int,
        other_positions: Optional[Dict[str, str]]
    ) -> Dict[str, Verdict]:
        """Get verdicts from all brains sequentially."""
        verdicts = {}
        
        for name, brain in self.brains.items():
            filtered_positions = None
            if other_positions:
                filtered_positions = {
                    k: v for k, v in other_positions.items()
                    if k.lower() != name.lower()
                }
            
            try:
                verdict = brain.form_verdict(
                    question=question,
                    round_number=round_number,
                    other_positions=filtered_positions
                )
                verdicts[name] = verdict
                
                if self._on_brain_verdict:
                    self._on_brain_verdict(name, verdict)
                    
            except Exception as e:
                verdicts[name] = Verdict(
                    brain_name=name,
                    verdict_type=VerdictType.ABSTAIN,
                    confidence=0.0,
                    summary=f"Error: {str(e)}",
                    reasoning=str(e),
                    deliberation_round=round_number
                )
        
        return verdicts
    
    def _run_cross_examination(
        self,
        question: str,
        verdicts: Dict[str, Verdict]
    ) -> Dict[str, Dict[str, str]]:
        """Have each brain examine the others' positions."""
        examinations = {}
        
        for examiner_name, examiner in self.brains.items():
            examinations[examiner_name] = {}
            
            for examined_name, examined_verdict in verdicts.items():
                if examiner_name != examined_name:
                    try:
                        response = examiner.cross_examine(question, examined_verdict)
                        examinations[examiner_name][examined_name] = response
                    except Exception as e:
                        examinations[examiner_name][examined_name] = f"Error: {str(e)}"
        
        return examinations
    
    def _synthesize_decision(self, decision: Decision) -> None:
        """Synthesize the final decision from all deliberation rounds."""
        if not decision.rounds:
            return
        
        final_round = decision.rounds[-1]
        decision.final_verdicts = final_round.verdicts
        
        # Calculate weighted scores
        scores = {
            name: verdict.weighted_score 
            for name, verdict in final_round.verdicts.items()
        }
        
        # Determine consensus
        verdict_types = [v.verdict_type for v in final_round.verdicts.values()]
        unique_types = set(verdict_types)
        
        # Check for informational (non-decision) questions
        if all(vt == VerdictType.INFO for vt in verdict_types):
            decision.consensus_type = ConsensusType.INFORMATIONAL
            decision.final_verdict = VerdictType.INFO
            decision.final_answer = self._synthesize_informational_response(decision)
            return
        
        # Check for unanimous agreement
        if len(unique_types) == 1:
            decision.consensus_type = ConsensusType.UNANIMOUS
            decision.final_verdict = verdict_types[0]
        
        # Check for majority
        elif final_round.majority_verdict:
            decision.consensus_type = ConsensusType.MAJORITY
            decision.final_verdict = final_round.majority_verdict
        
        # Check for conditional consensus
        elif VerdictType.CONDITIONAL in unique_types:
            affirmative = sum(1 for vt in verdict_types 
                            if vt in (VerdictType.APPROVE, VerdictType.CONDITIONAL))
            if affirmative >= 2:
                decision.consensus_type = ConsensusType.CONDITIONAL
                decision.final_verdict = VerdictType.CONDITIONAL
            else:
                decision.consensus_type = ConsensusType.DEADLOCK
                decision.final_verdict = self._resolve_deadlock(final_round)
        
        # Deadlock
        else:
            decision.consensus_type = ConsensusType.DEADLOCK
            decision.final_verdict = self._resolve_deadlock(final_round)
        
        # Collect conditions and synthesize
        for verdict in final_round.verdicts.values():
            decision.conditions.extend(verdict.conditions)
        
        decision.synthesis = self._generate_synthesis(decision)
        decision.final_answer = decision.synthesis
        
        # Identify agreements and disagreements
        self._identify_agreements_and_disagreements(decision)
    
    def _resolve_deadlock(self, round_result: DeliberationRound) -> VerdictType:
        """Resolve a deadlock based on configured strategy."""
        if self.config.deadlock_resolution == "cautious":
            # Default to rejection in deadlock
            return VerdictType.REJECT
        elif self.config.deadlock_resolution == "optimistic":
            # Default to approval in deadlock
            return VerdictType.APPROVE
        else:
            # Majority: use weighted scores
            total_score = sum(v.weighted_score for v in round_result.verdicts.values())
            if total_score > 0:
                return VerdictType.APPROVE
            elif total_score < 0:
                return VerdictType.REJECT
            else:
                return VerdictType.ABSTAIN
    
    def _synthesize_informational_response(self, decision: Decision) -> str:
        """Synthesize responses for informational (non-yes/no) questions."""
        responses = []
        for name, verdict in decision.final_verdicts.items():
            responses.append(f"**{name.upper()}**: {verdict.reasoning}")
        
        return "\n\n".join(responses)
    
    def _generate_synthesis(self, decision: Decision) -> str:
        """Generate a synthesis of all brain positions."""
        if not self._llm_client:
            # Fallback: simple concatenation
            parts = [f"{decision.consensus_type.value.upper()} decision reached."]
            for name, verdict in decision.final_verdicts.items():
                parts.append(f"{name}: {verdict.summary}")
            return " ".join(parts)
        
        # Use LLM to synthesize
        positions = "\n".join([
            f"- {name.upper()}: {v.summary} (Confidence: {v.confidence:.0%})"
            for name, v in decision.final_verdicts.items()
        ])
        
        messages = [
            {"role": "system", "content": """You synthesize MAGI deliberation outcomes.
Write a concise synthesis (2-3 sentences) that captures:
1. The overall decision/consensus
2. Key reasoning
3. Any important conditions or reservations"""},
            {"role": "user", "content": f"""Question: {decision.question}

MAGI Positions:
{positions}

Consensus: {decision.consensus_type.value}
Final verdict: {decision.final_verdict.value if decision.final_verdict else 'undetermined'}

Synthesize the outcome:"""}
        ]
        
        response = self._llm_client.chat.completions.create(
            model=self.config.model,
            messages=messages,
            **completion_token_kwargs(self.config.model, 200),
            **temperature_kwargs(self.config.model, 0.3),
        )
        
        return response.choices[0].message.content.strip()
    
    def _identify_agreements_and_disagreements(self, decision: Decision) -> None:
        """Identify key points of agreement and disagreement."""
        verdicts = list(decision.final_verdicts.values())
        
        # Simple heuristic: check verdict types
        types = [v.verdict_type for v in verdicts]
        
        if len(set(types)) == 1:
            decision.key_agreements.append(
                f"All three brains agree: {types[0].value}"
            )
        else:
            for v in verdicts:
                if v.verdict_type != decision.final_verdict:
                    decision.key_disagreements.append(
                        f"{v.brain_name} dissents with {v.verdict_type.value}: {v.summary}"
                    )
    
    def get_brain(self, name: str) -> Optional[Brain]:
        """Get a specific brain by name."""
        return self.brains.get(name.lower())
    
    def get_brain_response(self, brain_name: str, question: str) -> str:
        """Get a direct response from a specific brain."""
        brain = self.get_brain(brain_name)
        if not brain:
            raise ValueError(f"Unknown brain: {brain_name}")
        return brain.generate_response(question)
