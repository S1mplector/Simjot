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
from .network.system import MAGISystem as PTOSMAGISystem


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
        self._ptos_system: Optional[PTOSMAGISystem] = None
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

        # Primary runtime path: full PTOS system with engram-backed MAGI units.
        installation_id = os.getenv("MAGI_INSTALLATION_ID", "MAGI-01")
        location = os.getenv("MAGI_INSTALLATION_LOCATION", "Tokyo-3")
        ptos_system = PTOSMAGISystem(installation_id=installation_id, location=location)
        if not ptos_system.initialize():
            raise RuntimeError("PTOS MAGI initialization failed")
        ptos_system.set_llm_client(client)
        ptos_system.activate()
        self._ptos_system = ptos_system

        # Optional fallback legacy engine for local diagnostics.
        self._engine = None
        if _env_flag("MAGI_ENABLE_LEGACY_ENGINE", False):
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
        return self._initialized and (self._ptos_system is not None or self._engine is not None)

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
            if self._ptos_system is not None:
                require_unanimous = self._requires_unanimous(question)
                result = self._ptos_system.deliberate(
                    query=question,
                    require_unanimous=require_unanimous,
                )
                return self._network_to_response(question, result)
            if self._engine is not None:
                decision = self._engine.deliberate(question)
                return self._decision_to_response(decision)
            raise RuntimeError("MAGI runtime unavailable")
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

    def _requires_unanimous(self, question: str) -> bool:
        q = (question or "").lower()
        critical_cues = (
            "self-destruct",
            "self destruct",
            "irreversible",
            "point of no return",
            "delete everything",
            "shutdown permanently",
        )
        return any(cue in q for cue in critical_cues)

    @staticmethod
    def _network_consensus(raw: str) -> str:
        value = (raw or "").strip().upper()
        if value.startswith("UNANIMOUS_"):
            return "unanimous"
        if value.startswith("MAJORITY_"):
            return "majority"
        if value == "CONDITIONAL":
            return "conditional"
        if value == "DEADLOCK":
            return "deadlock"
        if value == "INFORMATIONAL":
            return "informational"
        if value == "ERROR":
            return "error"
        return "informational"

    @staticmethod
    def _vote_to_status(vote: str) -> str:
        v = (vote or "").strip().upper()
        if v == "APPROVE":
            return "yes"
        if v == "REJECT":
            return "no"
        if v == "CONDITIONAL":
            return "conditional"
        if v == "DEADLOCK":
            return "deadlock"
        if v in {"INFORMATIONAL", "INFO"}:
            return "info"
        if v == "ERROR":
            return "error"
        return "info"

    def _network_status(self, consensus: str, final_verdict: Optional[str]) -> str:
        vote_status = self._vote_to_status(final_verdict or "")
        if vote_status in {"yes", "no", "conditional", "deadlock", "info", "error"}:
            return vote_status
        if consensus == "deadlock":
            return "deadlock"
        if consensus == "conditional":
            return "conditional"
        if consensus == "error":
            return "error"
        return "info"

    @staticmethod
    def _first_sentence(text: str) -> str:
        raw = (text or "").strip()
        if not raw:
            return ""
        parts = re.split(r"(?<=[.!?])\s+", raw, maxsplit=1)
        return parts[0].strip() if parts else raw

    def _brain_payload_from_network(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        verdict = str(payload.get("verdict") or "INFORMATIONAL")
        response_text = str(payload.get("reasoning") or payload.get("response") or "")
        summary = str(payload.get("summary") or self._first_sentence(response_text))
        conditions_raw = payload.get("conditions")
        if isinstance(conditions_raw, list):
            conditions = [str(x).strip() for x in conditions_raw if str(x).strip()]
        elif isinstance(conditions_raw, str) and conditions_raw.strip():
            conditions = [conditions_raw.strip()]
        else:
            conditions = []

        reservations_raw = payload.get("reservations")
        if isinstance(reservations_raw, list):
            reservations = [str(x).strip() for x in reservations_raw if str(x).strip()]
        elif isinstance(reservations_raw, str) and reservations_raw.strip():
            reservations = [reservations_raw.strip()]
        else:
            reservations = []

        confidence = payload.get("confidence", 0.0)
        try:
            confidence = float(confidence)
        except Exception:
            confidence = 0.0

        return {
            "status": self._vote_to_status(verdict),
            "response": response_text.strip(),
            "summary": summary.strip(),
            "confidence": max(0.0, min(1.0, confidence)),
            "conditions": conditions or None,
            "reservations": reservations or None,
        }

    def _synthesize_network_answer(self, result: Dict[str, Any]) -> str:
        verdicts = result.get("verdicts", {})
        lines: List[str] = []
        for brain_name in ("balthasar", "melchior", "casper"):
            brain = verdicts.get(brain_name, {}) if isinstance(verdicts, dict) else {}
            if not isinstance(brain, dict):
                continue
            summary = str(brain.get("summary") or "").strip()
            reasoning = str(brain.get("reasoning") or brain.get("response") or "").strip()
            line = summary or self._first_sentence(reasoning)
            if not line:
                continue
            if len(line) > 260:
                line = line[:257].rstrip() + "..."
            lines.append(f"- {line}")

        if not lines:
            return "No guidance generated."

        answer = "\n".join(lines[:3])
        if str(result.get("final_verdict") or "").upper() == "CONDITIONAL":
            raw_conditions = result.get("conditions")
            conditions = raw_conditions if isinstance(raw_conditions, list) else []
            cleaned = [str(c).strip() for c in conditions if str(c).strip()]
            if cleaned:
                answer += "\n\nConditions:\n" + "\n".join(f"- {c}" for c in cleaned[:4])
        return answer

    def _network_to_response(self, question: str, result: Dict[str, Any]) -> MAGIResponse:
        consensus = self._network_consensus(str(result.get("consensus") or ""))
        final_verdict = str(result.get("final_verdict") or "")
        status = self._network_status(consensus, final_verdict)
        verdicts = result.get("verdicts", {}) if isinstance(result.get("verdicts"), dict) else {}

        melchior = self._brain_payload_from_network(verdicts.get("melchior", {}))
        balthasar = self._brain_payload_from_network(verdicts.get("balthasar", {}))
        casper = self._brain_payload_from_network(verdicts.get("casper", {}))

        try:
            processing_time_ms = float(result.get("processing_time_ms", 0.0))
        except Exception:
            processing_time_ms = 0.0

        decision_id = hashlib.sha256(
            f"{question}|{consensus}|{time.time_ns()}".encode("utf-8")
        ).hexdigest()[:8]
        answer = self._synthesize_network_answer(result)
        question_type = "yes_no" if _question_looks_yes_no(question) else "open"
        if consensus == "informational":
            question_type = "analytical"

        return MAGIResponse(
            status=status,
            answer=answer,
            question_type=question_type,
            consensus=consensus,
            melchior=melchior,
            balthasar=balthasar,
            casper=casper,
            decision_id=decision_id,
            processing_time_ms=processing_time_ms,
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
        if self._ptos_system is not None:
            result = self._ptos_system.query_unit(brain_name, question)
            if isinstance(result, dict):
                return str(result.get("response") or result.get("error") or "").strip()
            return ""
        if self._engine is not None:
            return self._engine.get_brain_response(brain_name, question)
        raise RuntimeError("MAGI runtime unavailable")


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
