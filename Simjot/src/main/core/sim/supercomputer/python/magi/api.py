"""
MAGI API
========

High-level API for the MAGI system, providing simple functions
for the Dash application to interact with the decision engine.

This module bridges the PTOS-based architecture with
the Dash frontend, maintaining backward compatibility while exposing
the full power of the Personality Transplant Operating System.
"""

from typing import Optional, Dict, Any, Tuple
from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import re
import threading
import time

from .core.engine import MAGIEngine, EngineConfig
from .core.brain import BrainConfig
from .core.decision import Decision
from .brains import create_melchior, create_balthasar, create_casper
from .llm.client import create_openai_client, MockLLMClient


def _env_flag(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None:
        return default
    try:
        return int(value)
    except ValueError:
        return default


@dataclass
class MAGIResponse:
    """Response from the MAGI system for UI consumption."""

    status: str  # "yes", "no", "conditional", "info", "error", "deadlock"
    answer: str
    question_type: str
    consensus: str

    # Individual brain responses
    melchior: Dict[str, Any]
    balthasar: Dict[str, Any]
    casper: Dict[str, Any]

    # Metadata
    decision_id: str
    processing_time_ms: float


class MAGISystem:
    """
    Singleton-style MAGI system manager.

    Provides a simple interface for the Dash application while
    managing the underlying engine and brains.
    """

    _instance: Optional["MAGISystem"] = None
    _lock = threading.Lock()

    def __init__(self, api_key: Optional[str] = None, model: str = "gpt-5"):
        self._api_key = api_key
        self._model = model or os.getenv("MAGI_MODEL", "gpt-5")
        self._engine: Optional[MAGIEngine] = None
        self._initialized = False
        self._mode = "uninitialized"  # "openai" | "mock" | "uninitialized"
        self._last_init_error: Optional[str] = None

    @classmethod
    def get_instance(cls) -> "MAGISystem":
        """Get or create the singleton instance."""
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = cls()
        return cls._instance

    def initialize(
        self,
        api_key: Optional[str] = None,
        model: str = "gpt-5",
        use_mock_on_failure: bool = True,
    ) -> None:
        """
        Initialize or reinitialize the MAGI system.

        If OpenAI client init fails and `use_mock_on_failure` is True,
        MAGI automatically falls back to mock mode so the UI remains usable.
        """
        self._api_key = api_key
        self._model = model or os.getenv("MAGI_MODEL", "gpt-5")
        self._last_init_error = None

        client = None
        mode = "openai"

        try:
            client = create_openai_client(api_key=api_key)
        except Exception as exc:
            if not use_mock_on_failure:
                raise
            client = MockLLMClient()
            mode = "mock"
            self._last_init_error = str(exc)

        brain_config = BrainConfig(model=self._model)

        melchior = create_melchior(brain_config)
        balthasar = create_balthasar(brain_config)
        casper = create_casper(brain_config)

        fast_mode = _env_flag("MAGI_FAST_MODE", True)
        max_rounds = _env_int("MAGI_MAX_DELIBERATION_ROUNDS", 1 if fast_mode else 2)
        enable_cross_exam = _env_flag("MAGI_ENABLE_CROSS_EXAMINATION", not fast_mode)

        engine_config = EngineConfig(
            model=self._model,
            max_deliberation_rounds=max(1, max_rounds),
            enable_cross_examination=enable_cross_exam,
            parallel_processing=True,
        )

        self._engine = MAGIEngine(
            brains=[melchior, balthasar, casper],
            config=engine_config,
            llm_client=client,
        )

        self._mode = mode
        self._initialized = True

    @property
    def is_initialized(self) -> bool:
        return self._initialized and self._engine is not None

    @property
    def mode(self) -> str:
        return self._mode

    @property
    def last_init_error(self) -> Optional[str]:
        return self._last_init_error

    def deliberate(self, question: str) -> MAGIResponse:
        """
        Run full MAGI deliberation on a question.

        Returns a MAGIResponse suitable for UI consumption.
        """
        if not self.is_initialized:
            raise RuntimeError("MAGI system not initialized. Call initialize() first.")

        try:
            decision = self._engine.deliberate(question)
            return self._decision_to_response(decision)
        except Exception as exc:
            return MAGIResponse(
                status="error",
                answer=str(exc),
                question_type="unknown",
                consensus="error",
                melchior={"status": "error", "response": str(exc), "conditions": None},
                balthasar={"status": "error", "response": str(exc), "conditions": None},
                casper={"status": "error", "response": str(exc), "conditions": None},
                decision_id="error",
                processing_time_ms=0.0,
            )

    def _decision_to_response(self, decision: Decision) -> MAGIResponse:
        """Convert a Decision to a MAGIResponse."""

        def brain_to_dict(name: str) -> Dict[str, Any]:
            if name not in decision.final_verdicts:
                return {
                    "status": "info",
                    "response": "No response",
                    "conditions": None,
                }

            verdict = decision.final_verdicts[name]
            return {
                "status": decision.get_brain_status(name),
                "response": verdict.reasoning,
                "summary": verdict.summary,
                "confidence": verdict.confidence,
                "conditions": verdict.conditions if verdict.conditions else None,
                "reservations": verdict.reservations if verdict.reservations else None,
            }

        return MAGIResponse(
            status=decision.status,
            answer=decision.final_answer,
            question_type=decision.question_type,
            consensus=decision.consensus_type.value,
            melchior=brain_to_dict("melchior"),
            balthasar=brain_to_dict("balthasar"),
            casper=brain_to_dict("casper"),
            decision_id=decision.id,
            processing_time_ms=decision.processing_time_ms,
        )

    def get_brain_response(self, brain_name: str, question: str) -> str:
        """Get a direct response from a specific brain."""
        if not self.is_initialized:
            raise RuntimeError("MAGI system not initialized.")

        return self._engine.get_brain_response(brain_name, question)


# Convenience functions for backward compatibility with old ai.py interface

_magi_system = MAGISystem.get_instance()
_cache_lock = threading.Lock()
_legacy_cache: Dict[str, Tuple[MAGIResponse, float]] = {}
_inflight_requests: Dict[str, threading.Event] = {}
_init_lock = threading.Lock()
_CACHE_TTL_SECONDS = 120.0


def _normalize_question(question: str) -> str:
    return (question or "").strip()


def _resolve_runtime_key(key: Optional[str]) -> Optional[str]:
    """
    Resolve API key for runtime use.

    `.env` is authoritative for local runs and overrides shell/UI values.
    """
    # Read .env from current directory or project root first.
    candidate_paths = [
        Path(".env"),
        Path(__file__).resolve().parents[1] / ".env",
    ]

    for env_path in candidate_paths:
        if not env_path.exists():
            continue

        for raw_line in env_path.read_text().splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue

            k, v = line.split("=", 1)
            if k.strip() == "OPENAI_API_KEY":
                parsed = v.strip().strip('"').strip("'")
                if parsed:
                    os.environ["OPENAI_API_KEY"] = parsed
                    return parsed

    # Fallback to process env, then callback-provided key.
    env_key = os.getenv("OPENAI_API_KEY")
    if env_key:
        return env_key

    return key


def _key_fingerprint(key: Optional[str]) -> str:
    # Never store raw key in cache keys.
    value = key or ""
    return hashlib.sha256(value.encode()).hexdigest()[:12]


def _cache_key(question: str, key: Optional[str]) -> str:
    return f"{_normalize_question(question).lower()}::{_key_fingerprint(key)}::{_magi_system.mode}"


def _map_personality_to_brain(personality: str) -> Optional[str]:
    personality_lower = (personality or "").lower()

    if "scientist" in personality_lower or "melchior" in personality_lower:
        return "melchior"
    if "mother" in personality_lower or "balthasar" in personality_lower:
        return "balthasar"
    if "woman" in personality_lower or "casper" in personality_lower:
        return "casper"

    return None


def _question_looks_yes_no(question: str) -> bool:
    q = _normalize_question(question)
    if not q:
        return False

    first_token = re.split(r"\s+", q.lower(), maxsplit=1)[0]
    yes_no_starters = {
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
    return first_token in yes_no_starters


def _ensure_initialized(key: Optional[str]) -> None:
    resolved_key = _resolve_runtime_key(key)

    # Reinitialize when:
    # - system is not initialized
    # - system is in mock mode and a key is provided
    # - key has changed since the last initialization
    with _init_lock:
        should_reinitialize = not _magi_system.is_initialized
        if not should_reinitialize and _magi_system.mode == "mock" and bool(resolved_key):
            should_reinitialize = True
        if (
            not should_reinitialize
            and bool(resolved_key)
            and resolved_key != _magi_system._api_key
        ):
            should_reinitialize = True

        if should_reinitialize:
            _magi_system.initialize(api_key=resolved_key)


def _get_or_run_deliberation(question: str, key: Optional[str]) -> MAGIResponse:
    resolved_key = _resolve_runtime_key(key)
    normalized = _normalize_question(question)
    if not normalized:
        return MAGIResponse(
            status="error",
            answer="Empty question.",
            question_type="unknown",
            consensus="error",
            melchior={"status": "error", "response": "Empty question.", "conditions": None},
            balthasar={"status": "error", "response": "Empty question.", "conditions": None},
            casper={"status": "error", "response": "Empty question.", "conditions": None},
            decision_id="error",
            processing_time_ms=0.0,
        )

    _ensure_initialized(resolved_key)

    k = _cache_key(normalized, resolved_key)
    now = time.time()

    wait_event: Optional[threading.Event] = None
    is_owner = False

    with _cache_lock:
        cached = _legacy_cache.get(k)
        if cached and (now - cached[1]) <= _CACHE_TTL_SECONDS:
            return cached[0]

        wait_event = _inflight_requests.get(k)
        if wait_event is None:
            wait_event = threading.Event()
            _inflight_requests[k] = wait_event
            is_owner = True

    # A different thread is already computing this question; wait for it.
    if not is_owner:
        wait_event.wait(timeout=120.0)
        with _cache_lock:
            cached = _legacy_cache.get(k)
            if cached:
                return cached[0]

        # If cache still missing after wait, run directly as fail-safe.
        return _magi_system.deliberate(normalized)

    # Owner thread computes and publishes result.
    try:
        response = _magi_system.deliberate(normalized)
        completed_at = time.time()
        with _cache_lock:
            _legacy_cache[k] = (response, completed_at)

            # Keep cache bounded.
            if len(_legacy_cache) > 64:
                oldest = min(_legacy_cache.items(), key=lambda kv: kv[1][1])[0]
                _legacy_cache.pop(oldest, None)
        return response
    finally:
        with _cache_lock:
            event = _inflight_requests.pop(k, None)
            if event:
                event.set()


def _extract_brain_payload(response: MAGIResponse, brain_name: str) -> Dict[str, Any]:
    if brain_name == "melchior":
        return response.melchior
    if brain_name == "balthasar":
        return response.balthasar
    if brain_name == "casper":
        return response.casper
    return {"status": "info", "response": response.answer, "conditions": None}


def is_yes_or_no_question(question: str, key: str) -> bool:
    """
    Determine if a question is a yes/no question.

    Backward compatible with old ai.py interface.
    """
    try:
        response = _get_or_run_deliberation(question, key)
        if response.question_type in {"yes_no", "analytical", "ethical", "predictive"}:
            return response.question_type == "yes_no"
    except Exception:
        pass

    # Fallback heuristic when backend is unavailable.
    return _question_looks_yes_no(question)


def get_answer(question: str, personality: str, key: str) -> str:
    """
    Get an answer from a MAGI brain based on personality description.

    Backward compatible with old ai.py interface.
    """
    brain_name = _map_personality_to_brain(personality)

    response = _get_or_run_deliberation(question, key)

    if brain_name:
        payload = _extract_brain_payload(response, brain_name)
        return payload.get("response") or response.answer

    return response.answer


def classify_answer(question: str, personality: str, answer: str, key: str) -> Dict[str, Any]:
    """
    Classify an answer as yes/no/conditional.

    Backward compatible with old ai.py interface.
    """
    try:
        response = _get_or_run_deliberation(question, key)
        brain_name = _map_personality_to_brain(personality)

        if brain_name:
            payload = _extract_brain_payload(response, brain_name)
            status = payload.get("status", "info")
            conditions = payload.get("conditions")

            if status in {"yes", "no", "conditional", "info", "error"}:
                return {"status": status, "conditions": conditions}

        if response.status in {"yes", "no", "conditional"}:
            return {
                "status": response.status,
                "conditions": None if response.status != "conditional" else response.answer,
            }
    except Exception:
        pass

    # Last-resort parser based on already-generated answer text.
    content = (answer or "").strip().lower()
    if re.match(r"^\W*yes\W*$", content, re.IGNORECASE):
        return {"status": "yes", "conditions": None}
    if re.match(r"^\W*no\W*$", content, re.IGNORECASE):
        return {"status": "no", "conditions": None}

    return {"status": "conditional", "conditions": content or None}
