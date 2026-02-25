"""
LLM Client
==========

Abstraction layer for LLM providers with support for
modern OpenAI API (v1.0+) and future extensibility.
"""

from typing import Optional, Dict, Any, List, Protocol, runtime_checkable
from dataclasses import dataclass
import json
import os
import re


@runtime_checkable
class LLMClient(Protocol):
    """Protocol defining the LLM client interface."""

    @property
    def chat(self) -> Any:
        """Access chat completions API."""
        ...


@dataclass
class OpenAIConfig:
    """Configuration for OpenAI client."""

    api_key: Optional[str] = None
    organization: Optional[str] = None
    base_url: Optional[str] = None
    timeout: float = 60.0
    max_retries: int = 3


def completion_token_kwargs(model: str, token_limit: int) -> Dict[str, int]:
    """
    Return the correct token-limit parameter for the target model family.
    """
    if (model or "").startswith("gpt-5"):
        return {"max_completion_tokens": token_limit}
    return {"max_tokens": token_limit}


def temperature_kwargs(model: str, temperature: float) -> Dict[str, float]:
    """
    Return temperature parameter only when supported by the model.
    """
    if (model or "").startswith("gpt-5"):
        return {}
    return {"temperature": temperature}


def create_openai_client(
    api_key: Optional[str] = None,
    config: Optional[OpenAIConfig] = None,
) -> Any:
    """
    Create an OpenAI client with modern API (v1.0+).

    Args:
        api_key: OpenAI API key. Falls back to OPENAI_API_KEY env var.
        config: Optional configuration object.

    Returns:
        OpenAI client instance.
    """
    try:
        from openai import OpenAI
    except ImportError:
        raise ImportError("openai package not found. Install with: pip install openai>=1.0.0")

    if config is None:
        config = OpenAIConfig()

    # Resolve API key
    resolved_key = api_key or config.api_key or os.getenv("OPENAI_API_KEY")

    if not resolved_key:
        raise ValueError(
            "OpenAI API key required. Provide via argument, config, or OPENAI_API_KEY env var."
        )

    client_kwargs = {
        "api_key": resolved_key,
        "timeout": config.timeout,
        "max_retries": config.max_retries,
    }

    if config.organization:
        client_kwargs["organization"] = config.organization

    if config.base_url:
        client_kwargs["base_url"] = config.base_url

    return OpenAI(**client_kwargs)


class MockLLMClient:
    """Mock LLM client for testing without API calls."""

    def __init__(self, responses: Optional[Dict[str, str]] = None):
        self._responses = responses or {}
        self._call_count = 0
        self.chat = self._ChatCompletions(self)

    class _ChatCompletions:
        def __init__(self, parent: "MockLLMClient"):
            self._parent = parent
            self.completions = self

        def create(self, **kwargs) -> Any:
            self._parent._call_count += 1

            messages = kwargs.get("messages", [])
            system_message = messages[0]["content"] if messages else ""
            user_message = messages[-1]["content"] if messages else ""
            lower_user = user_message.lower()
            lower_system = system_message.lower()

            # 1) Explicit user overrides for deterministic tests
            for key, response in self._parent._responses.items():
                if key.lower() in lower_user:
                    return self._make_response(response)

            # 2) Engine question classifier
            if "you classify questions by type" in lower_system:
                q = self._extract_classify_question(user_message)
                return self._make_response(self._infer_question_type(q))

            # 3) Legacy yes/no classifier
            if "you classify questions. answer with only \"yes\" or \"no\"" in lower_system:
                return self._make_response("yes" if self._looks_yes_no(user_message) else "no")

            # 4) Synthesis prompt
            if "you synthesize magi deliberation outcomes" in lower_system:
                return self._make_response(
                    "Consensus formed by balancing evidence, safety, and human agency. "
                    "The recommendation reflects shared priorities with explicit tradeoffs."
                )

            # 5) JSON structured outputs (analysis/verdict)
            if kwargs.get("response_format", {}).get("type") == "json_object":
                if '"question_type"' in user_message and '"initial_stance"' in user_message:
                    data = self._build_analysis_payload(user_message)
                    return self._make_response(json.dumps(data))

                if '"verdict"' in user_message and '"reasoning"' in user_message:
                    data = self._build_verdict_payload(system_message, user_message)
                    return self._make_response(json.dumps(data))

                # Generic JSON fallback
                return self._make_response(
                    json.dumps(
                        {
                            "verdict": "info",
                            "confidence": 0.65,
                            "summary": "Insufficient structure; returning informational response.",
                            "reasoning": "Mock mode fallback.",
                            "key_arguments": [],
                            "conditions": [],
                            "reservations": [],
                        }
                    )
                )

            # 6) Cross-examination style prompt
            if "provide a brief (2-3 sentences) response to their position" in lower_user:
                return self._make_response(
                    "Your argument is partly sound, but it underweights counterfactual risks. "
                    "I agree on intent, though execution safeguards require refinement."
                )

            # 7) Generic direct answer generation
            return self._make_response(self._build_direct_answer(system_message, user_message))

        @staticmethod
        def _extract_classify_question(content: str) -> str:
            marker = "Classify this question:"
            if marker in content:
                return content.split(marker, 1)[1].strip()
            return content.strip()

        @staticmethod
        def _looks_yes_no(question: str) -> bool:
            q = question.strip().lower()
            if not q:
                return False
            first = re.split(r"\s+", q, maxsplit=1)[0]
            starters = {
                "is",
                "are",
                "am",
                "was",
                "were",
                "do",
                "does",
                "did",
                "can",
                "could",
                "should",
                "would",
                "will",
                "have",
                "has",
                "had",
                "may",
                "might",
                "must",
            }
            return first in starters

        def _infer_question_type(self, question: str) -> str:
            q = question.lower().strip()

            if self._looks_yes_no(q):
                return "yes_no"
            if any(k in q for k in ["moral", "ethic", "right thing", "should we"]):
                return "ethical"
            if any(k in q for k in ["predict", "forecast", "future", "probability", "likely"]):
                return "predictive"
            if any(k in q for k in ["analyze", "compare", "tradeoff", "optimiz", "evaluate"]):
                return "analytical"
            return "open"

        def _build_analysis_payload(self, prompt: str) -> Dict[str, Any]:
            question = self._extract_question(prompt)
            qtype = self._infer_question_type(question)

            stance = "neutral"
            if qtype == "yes_no":
                stance = "uncertain"

            return {
                "question_type": qtype,
                "key_considerations": [
                    "Evidence quality",
                    "Risk profile",
                    "Human impact",
                ],
                "relevant_values": ["Truth", "Safety", "Meaning"],
                "initial_stance": stance,
                "confidence": 0.68,
                "needs_clarification": [],
            }

        def _build_verdict_payload(self, system_prompt: str, user_prompt: str) -> Dict[str, Any]:
            question = self._extract_question(user_prompt)
            qtype = self._infer_question_type(question)

            archetype = "general"
            lower_system = system_prompt.lower()
            if "the scientist" in lower_system:
                archetype = "scientist"
            elif "the mother" in lower_system:
                archetype = "mother"
            elif "the woman" in lower_system:
                archetype = "woman"

            if qtype != "yes_no":
                return {
                    "verdict": "info",
                    "confidence": 0.72,
                    "summary": "Providing informational analysis rather than binary vote.",
                    "reasoning": self._build_direct_answer(system_prompt, question),
                    "key_arguments": [
                        {
                            "claim": "Question is not strictly binary.",
                            "reasoning": "A nuanced response is more accurate.",
                            "confidence": 0.72,
                        }
                    ],
                    "conditions": [],
                    "reservations": [],
                }

            risk_terms = ["risk", "danger", "unsafe", "harm", "unknown", "uncertain"]
            has_risk = any(term in question.lower() for term in risk_terms)

            if archetype == "mother":
                if has_risk:
                    verdict = "reject"
                    summary = "Reject until robust safeguards protect vulnerable people."
                    conditions = ["Demonstrate safety controls", "Establish rollback plans"]
                    reservations = ["Potential human harm remains underweighted"]
                    confidence = 0.78
                else:
                    verdict = "approve"
                    summary = "Approve with clear care-oriented monitoring."
                    conditions = []
                    reservations = ["Maintain wellbeing checkpoints"]
                    confidence = 0.73
            elif archetype == "scientist":
                if has_risk:
                    verdict = "conditional"
                    summary = "Conditional approval contingent on evidence-backed risk mitigation."
                    conditions = ["Collect additional data", "Run controlled validation"]
                    reservations = ["Evidence quality is currently limited"]
                    confidence = 0.75
                else:
                    verdict = "approve"
                    summary = "Approve based on current evidence and expected benefit."
                    conditions = []
                    reservations = ["Monitor model drift and assumptions"]
                    confidence = 0.74
            else:  # woman / general
                if "ban" in question.lower() or "forbid" in question.lower():
                    verdict = "reject"
                    summary = "Reject broad restrictions that suppress agency."
                    conditions = []
                    reservations = ["Overly rigid control harms autonomy"]
                    confidence = 0.71
                elif has_risk:
                    verdict = "conditional"
                    summary = "Conditional approval if agency is preserved with guardrails."
                    conditions = ["Protect user choice", "Add transparent consent"]
                    reservations = ["Tradeoff between freedom and safety is unresolved"]
                    confidence = 0.72
                else:
                    verdict = "approve"
                    summary = "Approve as it supports meaningful progress and autonomy."
                    conditions = []
                    reservations = ["Verify long-term quality-of-life impact"]
                    confidence = 0.73

            return {
                "verdict": verdict,
                "confidence": confidence,
                "summary": summary,
                "reasoning": self._build_reasoning(archetype, question, verdict),
                "key_arguments": [
                    {
                        "claim": summary,
                        "reasoning": "Position derived from archetype priorities and risk context.",
                        "confidence": confidence,
                    }
                ],
                "conditions": conditions,
                "reservations": reservations,
                "responses_to_others": {},
            }

        @staticmethod
        def _extract_question(prompt: str) -> str:
            markers = ["Question:", "question:"]
            for marker in markers:
                if marker in prompt:
                    return prompt.split(marker, 1)[1].strip().split("\n\n", 1)[0].strip()
            return prompt.strip()

        @staticmethod
        def _build_reasoning(archetype: str, question: str, verdict: str) -> str:
            lens = {
                "scientist": "evidence quality, falsifiability, and systemic effects",
                "mother": "human wellbeing, vulnerability, and protective duty",
                "woman": "agency, meaning, and authentic human outcomes",
                "general": "balanced MAGI priorities",
            }[archetype]

            return (
                f"Evaluating '{question}' through {lens} supports a {verdict} stance. "
                "The recommendation balances immediate feasibility with downstream consequences "
                "and includes explicit uncertainty handling."
            )

        @staticmethod
        def _build_direct_answer(system_prompt: str, prompt: str) -> str:
            if "the scientist" in system_prompt.lower():
                return (
                    "From an analytical standpoint, the decision should be grounded in evidence, "
                    "clear success metrics, and explicit risk controls."
                )
            if "the mother" in system_prompt.lower():
                return (
                    "The safest path is the one that protects vulnerable people first, "
                    "with reversible steps and active monitoring."
                )
            if "the woman" in system_prompt.lower():
                return (
                    "A strong outcome should preserve autonomy and meaning, "
                    "not just optimize technical performance."
                )
            return f"Mock MAGI response: {prompt}"

        def _make_response(self, content: str) -> Any:
            class Choice:
                def __init__(self, response_content: str):
                    self.message = type("Message", (), {"content": response_content})()

            class Response:
                def __init__(self, response_content: str):
                    self.choices = [Choice(response_content)]

            return Response(content)
