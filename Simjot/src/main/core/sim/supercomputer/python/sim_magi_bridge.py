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
        consensus = _clean_text(getattr(response, "consensus", "")) or status
        emotions = _detect_emotions(f"{user_text}\n{answer}")

        _write_json({
            "ok": True,
            "text": answer,
            "status": status,
            "consensus": consensus,
            "emotions": emotions,
            "model": model
        })
        return 0
    except Exception as exc:
        _write_json({"ok": False, "error": str(exc)})
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
