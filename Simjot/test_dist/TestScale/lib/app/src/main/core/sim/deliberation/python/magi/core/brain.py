"""
Brain Module
============

The Brain class represents a single MAGI supercomputer unit.
Each brain has a unique personality and provides independent analysis
of questions presented to the MAGI system.
"""

from dataclasses import dataclass, field
from typing import Optional, Dict, Any, List
import json
import re

from .personality import Personality
from .decision import Verdict, VerdictType, Argument
from ..llm.client import completion_token_kwargs, temperature_kwargs


@dataclass
class BrainConfig:
    """Configuration for brain behavior."""
    model: str = "gpt-5"
    temperature: float = 0.7
    max_tokens: int = 1500
    deliberation_rounds: int = 2
    enable_cross_examination: bool = True


class Brain:
    """
    A single MAGI supercomputer brain.
    
    Each brain maintains its own personality, processes questions independently,
    and participates in deliberation with the other brains.
    """
    
    def __init__(
        self,
        personality: Personality,
        config: Optional[BrainConfig] = None,
        llm_client: Optional[Any] = None
    ):
        self.personality = personality
        self.config = config or BrainConfig()
        self._llm_client = llm_client
        self._conversation_history: List[Dict[str, str]] = []
    
    @property
    def name(self) -> str:
        return self.personality.name
    
    @property
    def archetype(self) -> str:
        return self.personality.archetype
    
    def set_llm_client(self, client: Any) -> None:
        """Set the LLM client for API calls."""
        self._llm_client = client
    
    def _call_llm(self, messages: List[Dict[str, str]], response_format: Optional[str] = None) -> str:
        """Make a call to the LLM."""
        if not self._llm_client:
            raise RuntimeError(f"Brain {self.name}: No LLM client configured")
        
        kwargs = {
            "model": self.config.model,
            "messages": messages,
            **completion_token_kwargs(self.config.model, self.config.max_tokens),
            **temperature_kwargs(self.config.model, self.config.temperature),
        }
        
        if response_format == "json":
            kwargs["response_format"] = {"type": "json_object"}

        try:
            response = self._llm_client.chat.completions.create(**kwargs)
            return response.choices[0].message.content
        except Exception:
            # Some models/endpoints do not support response_format=json_object.
            # Retry once without the API-level constraint while preserving
            # the prompt's JSON instruction.
            if response_format == "json":
                fallback_kwargs = dict(kwargs)
                fallback_kwargs.pop("response_format", None)
                response = self._llm_client.chat.completions.create(**fallback_kwargs)
                return response.choices[0].message.content
            raise
    
    def analyze_question(self, question: str) -> Dict[str, Any]:
        """
        Initial analysis of the question before forming a verdict.
        
        Returns structured analysis including question type, key considerations,
        and initial stance.
        """
        system_prompt = self.personality.build_system_prompt()
        
        analysis_prompt = f"""Analyze this question from your unique perspective as {self.personality.archetype}.

Question: {question}

Provide your analysis in JSON format:
{{
    "question_type": "yes_no" | "open" | "analytical" | "ethical" | "predictive",
    "key_considerations": ["list", "of", "key", "factors"],
    "relevant_values": ["which", "of", "your", "values", "apply"],
    "initial_stance": "positive" | "negative" | "neutral" | "uncertain",
    "confidence": 0.0-1.0,
    "needs_clarification": ["any", "ambiguities", "or", "missing", "info"]
}}"""

        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": analysis_prompt}
        ]
        
        response = self._call_llm(messages, response_format="json")
        
        try:
            return json.loads(response)
        except json.JSONDecodeError:
            return {
                "question_type": "unknown",
                "key_considerations": [],
                "relevant_values": [],
                "initial_stance": "uncertain",
                "confidence": 0.5,
                "needs_clarification": []
            }
    
    def form_verdict(
        self, 
        question: str, 
        question_analysis: Optional[Dict[str, Any]] = None,
        round_number: int = 1,
        other_positions: Optional[Dict[str, str]] = None
    ) -> Verdict:
        """
        Form a verdict on the question.
        
        In later rounds, considers other brains' positions.
        """
        system_prompt = self.personality.build_system_prompt()
        
        # Build the prompt based on round
        if round_number == 1:
            user_prompt = self._build_initial_verdict_prompt(question, question_analysis)
        else:
            user_prompt = self._build_deliberation_verdict_prompt(
                question, question_analysis, other_positions
            )
        
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt}
        ]
        
        response = self._call_llm(messages, response_format="json")
        
        return self._parse_verdict_response(response, round_number)
    
    def _build_initial_verdict_prompt(
        self, 
        question: str, 
        analysis: Optional[Dict[str, Any]]
    ) -> str:
        """Build prompt for initial verdict (round 1)."""
        prompt = f"""Consider this question carefully from your perspective as {self.personality.archetype}.

Question: {question}

"""
        if analysis:
            prompt += f"""Your initial analysis identified:
- Question type: {analysis.get('question_type', 'unknown')}
- Key considerations: {', '.join(analysis.get('key_considerations', []))}
- Relevant values: {', '.join(analysis.get('relevant_values', []))}

"""
        
        prompt += """Provide your verdict in JSON format:
{
    "verdict": "approve" | "reject" | "conditional" | "abstain" | "defer" | "info",
    "confidence": 0.0-1.0,
    "summary": "One sentence summary of your position",
    "reasoning": "Your detailed reasoning (2-4 paragraphs)",
    "key_arguments": [
        {"claim": "Main point", "reasoning": "Why this matters", "confidence": 0.0-1.0}
    ],
    "conditions": ["Any conditions for approval (if conditional)"],
    "reservations": ["Any concerns or reservations you have"]
}"""
        
        return prompt
    
    def _build_deliberation_verdict_prompt(
        self,
        question: str,
        analysis: Optional[Dict[str, Any]],
        other_positions: Optional[Dict[str, str]]
    ) -> str:
        """Build prompt for deliberation rounds (round 2+)."""
        prompt = f"""The MAGI system is deliberating on this question:

Question: {question}

The other MAGI brains have shared their positions:

"""
        if other_positions:
            for brain_name, position in other_positions.items():
                prompt += f"**{brain_name}**: {position}\n\n"
        
        prompt += f"""As {self.personality.archetype}, consider their arguments carefully.
- Where do you agree with them?
- Where do you disagree, and why?
- Has anything changed your initial assessment?

Update or maintain your verdict. Provide in JSON format:
{{
    "verdict": "approve" | "reject" | "conditional" | "abstain" | "defer" | "info",
    "confidence": 0.0-1.0,
    "summary": "Your updated position (one sentence)",
    "reasoning": "Your reasoning, addressing the others' points",
    "key_arguments": [
        {{"claim": "Point", "reasoning": "Why", "confidence": 0.0-1.0}}
    ],
    "conditions": ["Any conditions"],
    "reservations": ["Any reservations"],
    "responses_to_others": {{
        "brain_name": "Your response to their argument"
    }}
}}"""
        
        return prompt
    
    def _parse_verdict_response(self, response: str, round_number: int) -> Verdict:
        """Parse LLM response into a Verdict object."""
        try:
            data = json.loads(response)
        except json.JSONDecodeError:
            # Fallback parsing for non-JSON responses
            return Verdict(
                brain_name=self.name,
                verdict_type=VerdictType.INFO,
                confidence=0.5,
                summary="Unable to parse response",
                reasoning=response,
                deliberation_round=round_number
            )
        
        # Map verdict string to enum
        verdict_map = {
            "approve": VerdictType.APPROVE,
            "reject": VerdictType.REJECT,
            "conditional": VerdictType.CONDITIONAL,
            "abstain": VerdictType.ABSTAIN,
            "defer": VerdictType.DEFER,
            "info": VerdictType.INFO,
            "yes": VerdictType.APPROVE,
            "no": VerdictType.REJECT,
        }
        
        verdict_str = data.get("verdict", "info").lower()
        verdict_type = verdict_map.get(verdict_str, VerdictType.INFO)
        
        # Parse arguments
        arguments = []
        for arg_data in data.get("key_arguments", []):
            arguments.append(Argument(
                claim=arg_data.get("claim", ""),
                reasoning=arg_data.get("reasoning", ""),
                confidence=arg_data.get("confidence", 0.7)
            ))
        
        return Verdict(
            brain_name=self.name,
            verdict_type=verdict_type,
            confidence=data.get("confidence", 0.7),
            summary=data.get("summary", ""),
            reasoning=data.get("reasoning", ""),
            arguments=arguments,
            conditions=data.get("conditions", []),
            reservations=data.get("reservations", []),
            deliberation_round=round_number,
            responses_to_others=data.get("responses_to_others", {})
        )
    
    def cross_examine(self, question: str, other_verdict: Verdict) -> str:
        """
        Cross-examine another brain's verdict.
        
        Returns a critique or response to their position.
        """
        system_prompt = self.personality.get_cross_examination_prompt(other_verdict.brain_name)
        
        user_prompt = f"""Original question: {question}

{other_verdict.brain_name}'s position:
- Verdict: {other_verdict.verdict_type.value}
- Confidence: {other_verdict.confidence}
- Summary: {other_verdict.summary}
- Reasoning: {other_verdict.reasoning}
- Conditions: {', '.join(other_verdict.conditions) if other_verdict.conditions else 'None'}

Provide a brief (2-3 sentences) response to their position from your perspective."""

        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt}
        ]
        
        return self._call_llm(messages)
    
    def generate_response(self, question: str) -> str:
        """
        Generate a direct response to a non-yes/no question.
        
        Used for informational queries that don't require voting.
        """
        system_prompt = self.personality.build_system_prompt()
        
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": question}
        ]
        
        return self._call_llm(messages)
    
    def reset(self) -> None:
        """Reset conversation history for a new question."""
        self._conversation_history = []
