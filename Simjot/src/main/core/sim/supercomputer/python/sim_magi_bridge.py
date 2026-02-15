#!/usr/bin/env python3
"""
Simjot MAGI bridge.

Reads one JSON payload from stdin:
{
  "system_prompt": "...",
  "user_text": "...",
  "max_tokens": 220,
  "temperature": 0.7,
  "model": "gpt-5",
  "openai_api_key": "..."
}

Writes one JSON object to stdout:
{
  "ok": true,
  "text": "...",
  "status": "info"
}
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent
if str(BASE_DIR) not in sys.path:
    sys.path.insert(0, str(BASE_DIR))


def _read_payload() -> dict:
    raw = sys.stdin.read()
    if not raw or not raw.strip():
        return {}
    data = json.loads(raw)
    return data if isinstance(data, dict) else {}


def _clean_text(value: str) -> str:
    text = (value or "").replace("\r", " ").strip()
    text = " ".join(text.split())
    return text


def _compose_question(system_prompt: str, user_text: str, max_tokens: int, temperature: float) -> str:
    sys_txt = (system_prompt or "").strip()
    usr_txt = (user_text or "").strip()
    token_hint = max(32, int(max_tokens) if isinstance(max_tokens, int) else 220)
    temp_hint = float(temperature) if isinstance(temperature, (int, float)) else 0.7

    parts = []
    if sys_txt:
        parts.append("System guidance:")
        parts.append(sys_txt)
    if usr_txt:
        parts.append("User request:")
        parts.append(usr_txt)
    parts.append(f"Response constraints: plain text only, concise, target tokens <= {token_hint}, temperature hint {temp_hint:.2f}.")
    return "\n\n".join(parts).strip()


def _compose_consensus_probe_question(user_text: str, guidance_text: str) -> str:
    journal = (user_text or "").strip()
    guidance = (guidance_text or "").strip()
    if len(journal) > 1800:
        journal = journal[-1800:]
    if len(guidance) > 900:
        guidance = guidance[:900]
    return (
        "MAGI consensus checkpoint.\n\n"
        "Decision question: Should Sim APPROVE delivering this guidance to the user right now?\n"
        "Vote as approve, reject, or conditional based on safety, practicality, and empathy.\n"
        "If uncertain, prefer conditional rather than informational.\n\n"
        f"Journal context:\n{journal}\n\n"
        f"Draft guidance:\n{guidance}"
    ).strip()


_JOY_WORDS = {
    "happy", "happiness", "joy", "joyful", "grateful", "gratitude",
    "excited", "proud", "hopeful", "hope", "optimistic", "love", "relieved",
}
_CALM_WORDS = {
    "calm", "peace", "peaceful", "grounded", "stable", "steady",
    "centered", "content", "balanced", "breathe", "breathing",
}
_LOW_WORDS = {
    "sad", "down", "low", "lonely", "grief", "hurt", "empty",
    "hopeless", "depressed", "tired", "exhausted", "anxious", "anxiety",
    "worried", "worry", "afraid", "fear", "stuck",
}
_STRESS_WORDS = {
    "angry", "anger", "mad", "frustrated", "frustrating", "annoyed", "irritated",
    "resentful", "rage", "stress", "stressed", "overwhelmed", "panic", "panicked",
    "tense", "pressure", "burnout",
}
_NEUTRAL_WORDS = {"okay", "ok", "fine", "normal", "meh", "neutral"}


def _detect_emotions(text: str) -> list[str]:
    scores: dict[str, float] = {}
    raw = (text or "").lower()
    if not raw:
        return []
    import re

    for tok in re.split(r"[^a-z]+", raw):
        if not tok:
            continue
        if tok in _JOY_WORDS:
            scores["joy"] = scores.get("joy", 0.0) + 1.0
        if tok in _CALM_WORDS:
            scores["calm"] = scores.get("calm", 0.0) + 1.0
        if tok in _LOW_WORDS:
            scores["sad"] = scores.get("sad", 0.0) + 1.0
        if tok in _STRESS_WORDS:
            scores["anger"] = scores.get("anger", 0.0) + 1.0
        if tok in _NEUTRAL_WORDS:
            scores["neutral"] = scores.get("neutral", 0.0) + 0.8

    ranked = sorted(scores.items(), key=lambda kv: kv[1], reverse=True)
    return [k for k, v in ranked if v > 0.0][:3]


def _write_json(payload: dict) -> None:
    sys.stdout.write(json.dumps(payload, ensure_ascii=False))
    sys.stdout.flush()


def _canonical_consensus(value: str) -> str:
    c = _clean_text(value).lower()
    if not c:
        return ""
    if "unanim" in c or "合意" in c:
        return "unanimous"
    if "major" in c:
        return "majority"
    if "condition" in c or "状態" in c:
        return "conditional"
    if "deadlock" in c or "stalemate" in c:
        return "deadlock"
    if "inform" in c or "info" in c or "情報" in c:
        return "informational"
    return ""


def _brain_statuses(response: object) -> dict[str, str]:
    def infer_vote(payload: dict) -> str:
        summary = _clean_text(str(payload.get("summary") or ""))
        reasoning = _clean_text(str(payload.get("response") or ""))
        text = f"{summary} {reasoning}".strip().lower()
        if not text:
            return "info"
        reject_cues = (
            "do not", "don't", "avoid", "reject", "not recommend", "shouldn't",
            "unsafe", "harmful", "too risky", "cannot approve",
        )
        conditional_cues = (
            "if ", "unless", "provided", "as long as", "only if", "depends",
            "with safeguards", "with guardrails", "with monitoring", "reversible",
            "under conditions", "cautious", "risk controls", "boundaries",
        )
        approve_cues = (
            "yes", "approve", "recommended", "go ahead", "proceed",
            "support", "beneficial", "good idea",
        )
        if any(c in text for c in reject_cues):
            return "no"
        if any(c in text for c in conditional_cues):
            return "conditional"
        if any(c in text for c in approve_cues):
            return "yes"
        if " should " in f" {text} ":
            return "conditional"
        return "conditional"

    out: dict[str, str] = {}
    for name in ("melchior", "balthasar", "casper"):
        payload = getattr(response, name, None)
        status = "info"
        if isinstance(payload, dict):
            raw = payload.get("status")
            status = _clean_text(str(raw if raw is not None else "info")).lower() or "info"
            if status in {"info", "error"}:
                status = infer_vote(payload)
        if status not in {"yes", "no", "conditional", "deadlock", "info", "error"}:
            status = "info"
        out[name] = status
    return out


def _derive_consensus(consensus_raw: str, status_raw: str, brains: dict[str, str]) -> str:
    by_name = _canonical_consensus(consensus_raw)
    if by_name and by_name != "informational":
        return by_name

    status = _clean_text(status_raw).lower()
    if status == "deadlock":
        return "deadlock"
    if status == "conditional":
        return "conditional"

    vals = [v for v in brains.values() if v]
    yes = vals.count("yes")
    no = vals.count("no")
    cond = vals.count("conditional")
    info = vals.count("info")

    if yes == 3 or no == 3:
        return "unanimous"
    if yes >= 2 or no >= 2:
        return "majority"
    if cond >= 2 or (cond >= 1 and (yes >= 1 or no >= 1)):
        return "conditional"
    if status in {"yes", "no"}:
        return "majority"
    if by_name == "informational":
        return "informational"
    if info >= 2:
        return "informational"
    return "informational"


def main() -> int:
    try:
        payload = _read_payload()
        system_prompt = str(payload.get("system_prompt") or "")
        user_text = str(payload.get("user_text") or "")
        model = str(payload.get("model") or os.getenv("SIM_MAGI_MODEL") or "gpt-5").strip() or "gpt-5"
        openai_api_key = str(payload.get("openai_api_key") or os.getenv("OPENAI_API_KEY") or "").strip()
        max_tokens = payload.get("max_tokens")
        temperature = payload.get("temperature")

        # Keep MAGI responsive for UI-triggered calls.
        os.environ.setdefault("MAGI_FAST_MODE", "1")
        os.environ.setdefault("MAGI_MAX_DELIBERATION_ROUNDS", "1")
        os.environ.setdefault("MAGI_ENABLE_CROSS_EXAMINATION", "0")
        os.environ["MAGI_MODEL"] = model
        if openai_api_key:
            os.environ["OPENAI_API_KEY"] = openai_api_key

        from magi.api import MAGISystem  # pylint: disable=import-outside-toplevel

        question = _compose_question(system_prompt, user_text, int(max_tokens or 220), float(temperature or 0.7))
        if not question:
            _write_json({"ok": False, "error": "Empty prompt."})
            return 0

        magi = MAGISystem.get_instance()
        try:
            magi.initialize(api_key=openai_api_key or None, model=model, use_mock_on_failure=True)
        except TypeError:
            # Backward compatibility if MAGI initialize signature changes.
            magi.initialize(api_key=openai_api_key or None, model=model)

        response = magi.deliberate(question)
        answer = _clean_text(getattr(response, "answer", ""))
        status = _clean_text(getattr(response, "status", "info")) or "info"
        primary_consensus_raw = _clean_text(getattr(response, "consensus", ""))
        brain_states = _brain_statuses(response)
        consensus = _derive_consensus(primary_consensus_raw, status, brain_states)

        if consensus == "informational":
            # Run a dedicated decision-style checkpoint so MAGI can surface
            # non-informational consensus types for guidance workflows.
            probe_q = _compose_consensus_probe_question(user_text, answer)
            try:
                probe = magi.deliberate(probe_q)
                probe_status = _clean_text(getattr(probe, "status", "info")) or "info"
                probe_consensus_raw = _clean_text(getattr(probe, "consensus", ""))
                probe_brains = _brain_statuses(probe)
                probe_consensus = _derive_consensus(probe_consensus_raw, probe_status, probe_brains)
                if probe_consensus != "informational":
                    consensus = probe_consensus
                    brain_states = probe_brains
                    status = probe_status
            except Exception:
                pass

        emotions = _detect_emotions(f"{user_text}\n{answer}")

        _write_json({
            "ok": True,
            "text": answer,
            "status": status,
            "consensus": consensus,
            "brain_statuses": brain_states,
            "emotions": emotions,
            "model": model
        })
        return 0
    except Exception as exc:
        _write_json({"ok": False, "error": str(exc)})
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
