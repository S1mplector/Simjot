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
import re
import sys
import traceback
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
        "Should Sim APPROVE delivering this guidance to the user right now?\n\n"
        "MAGI consensus checkpoint.\n"
        "Vote as approve, reject, conditional, deadlock, or informational based on safety, practicality, and empathy.\n"
        "Prefer approve/reject when the guidance is already actionable.\n"
        "Use conditional only when explicit prerequisites are missing.\n"
        "Use informational only when the question is non-decision or context-only.\n\n"
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


def _log(level: str, message: str) -> None:
    lvl = (level or "INFO").strip().upper()
    msg = (message or "").strip()
    sys.stderr.write(f"[sim_magi_bridge][{lvl}] {msg}\n")
    sys.stderr.flush()


def _looks_like_brain_transcript(text: str) -> bool:
    t = (text or "").strip()
    if not t:
        return False
    lower = t.lower()
    named = sum(1 for n in ("melchior", "balthasar", "casper") if n in lower)
    if named < 2:
        return False
    if re.search(r"\*\*(melchior|balthasar|casper)\*\*\s*:", t, flags=re.IGNORECASE):
        return True
    return ":" in t


def _normalize_brain_line(text: str) -> str:
    t = _clean_text(text)
    if not t:
        return ""
    t = re.sub(r"^\*{0,2}(melchior|balthasar|casper)\*{0,2}\s*:\s*", "", t, flags=re.IGNORECASE)
    t = re.sub(r"\s+", " ", t).strip()
    if not t:
        return ""
    parts = re.split(r"(?<=[.!?])\s+", t)
    first = parts[0].strip() if parts else t
    if len(first) < 36 and len(parts) > 1:
        first = f"{first} {parts[1].strip()}".strip()
    if len(first) > 240:
        first = first[:240].rstrip(" ,;:-") + "..."
    return first


def _guidance_from_brain_payloads(response: object) -> str:
    generic_summary_cues = (
        "informational analysis",
        "non-decision",
        "context-only",
        "context only",
        "rather than binary",
    )
    lines: list[str] = []
    seen: set[str] = set()
    for name in ("balthasar", "melchior", "casper"):
        payload = getattr(response, name, None)
        if not isinstance(payload, dict):
            continue
        summary = _clean_text(str(payload.get("summary") or ""))
        reasoning = _clean_text(str(payload.get("response") or ""))
        if summary and not any(cue in summary.lower() for cue in generic_summary_cues):
            raw = summary
        else:
            raw = reasoning or summary
        line = _normalize_brain_line(str(raw))
        if not line:
            continue
        key = line.lower()
        if key in seen:
            continue
        seen.add(key)
        lines.append(line)
        if len(lines) >= 3:
            break
    if not lines:
        return ""
    return "\n".join(f"- {line}" for line in lines)


def _normalize_answer_text(raw_answer: str, response: object) -> str:
    answer = _clean_text(raw_answer)
    if answer and not _looks_like_brain_transcript(answer):
        return answer
    synthesized = _guidance_from_brain_payloads(response)
    if synthesized:
        return synthesized
    if not answer:
        return ""
    answer = re.sub(r"\*\*(melchior|balthasar|casper)\*\*\s*:\s*", "", answer, flags=re.IGNORECASE)
    answer = re.sub(r"\b(melchior|balthasar|casper)\s*:\s*", "", answer, flags=re.IGNORECASE)
    return _clean_text(answer)


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


def _normalize_vote_status(value: object) -> str:
    s = _clean_text(str(value or "")).lower()
    if not s:
        return ""
    if any(k in s for k in ("deadlock", "stalemate", "hung", "tie")):
        return "deadlock"
    if any(k in s for k in ("conditional", "condition", "depends", "state")):
        return "conditional"
    if any(k in s for k in ("approve", "approved", "accept", "accepted", "yes", "allow", "proceed")):
        return "yes"
    if any(k in s for k in ("reject", "rejected", "deny", "denied", "no")):
        return "no"
    if "error" in s:
        return "error"
    if any(k in s for k in ("info", "inform", "analysis", "context")):
        return "info"
    return ""


def _cue_hits(text: str, cues: tuple[str, ...]) -> int:
    return sum(1 for cue in cues if cue and cue in text)


def _brain_statuses(response: object) -> dict[str, str]:
    def infer_vote(payload: dict, status_hint: str) -> str:
        summary = _clean_text(str(payload.get("summary") or ""))
        reasoning = _clean_text(str(payload.get("response") or ""))
        text = f"{summary} {reasoning}".strip().lower()
        if not text:
            return "info"

        if status_hint == "deadlock":
            return "deadlock"

        approve_strong = (
            "approve", "approved", "go ahead", "proceed", "recommended",
            "support", "should proceed", "should approve",
        )
        approve_soft = (
            "beneficial", "helpful", "appropriate", "reasonable", "good idea", "safe path",
        )
        reject_strong = (
            "do not", "don't", "avoid", "reject", "not recommend", "shouldn't",
            "unsafe", "harmful", "too risky", "cannot approve", "must not", "do not proceed",
        )
        conditional_gate = (
            "if ", "unless", "provided", "as long as", "only if", "depends",
            "under conditions", "on the condition", "before proceeding",
        )
        conditional_soft = (
            "with safeguards", "with guardrails", "with monitoring", "reversible",
            "risk controls", "mitigation", "boundaries",
        )
        informational_cues = (
            "informational", "for reference", "context only", "non-decision", "no decision",
        )
        uncertainty_cues = (
            "uncertain", "uncertainty", "unclear", "insufficient", "unknown", "not enough data",
            "might", "could", "may",
        )

        score_yes = 0.0
        score_no = 0.0
        score_cond = 0.0
        score_info = 0.0

        score_yes += 2.6 * _cue_hits(text, approve_strong)
        score_yes += 0.8 * _cue_hits(text, approve_soft)
        score_no += 2.6 * _cue_hits(text, reject_strong)
        score_cond += 2.4 * _cue_hits(text, conditional_gate)
        score_cond += 0.9 * _cue_hits(text, conditional_soft)
        score_info += 2.2 * _cue_hits(text, informational_cues)
        score_cond += 0.45 * _cue_hits(text, uncertainty_cues)

        if "yes, but" in text or "approve, but" in text:
            score_yes += 0.8
            score_cond += 1.4
        if "no, but" in text or "reject, but" in text:
            score_no += 0.8
            score_cond += 1.4
        if "not" in text and "approve" in text:
            score_no += 1.8
        if "not" in text and "reject" in text:
            score_yes += 1.4

        if status_hint == "yes":
            score_yes += 1.7
        elif status_hint == "no":
            score_no += 1.7
        elif status_hint == "conditional":
            score_cond += 1.4
        elif status_hint in {"info", "error"}:
            score_info += 1.0

        # Contradictory strong signals.
        if score_yes >= 2.6 and score_no >= 2.6 and abs(score_yes - score_no) <= 1.2:
            if score_cond >= 1.6:
                return "conditional"
            return "deadlock"

        if score_no >= score_yes + 1.6 and score_no >= score_cond + 1.1:
            return "no"
        if score_yes >= score_no + 1.6 and score_yes >= score_cond + 1.1:
            return "yes"
        if score_cond >= max(score_yes, score_no) + 0.6 and score_cond >= 2.0:
            return "conditional"
        if score_yes >= 2.0 and score_no == 0 and score_cond < 2.6:
            return "yes"
        if score_no >= 2.0 and score_yes == 0 and score_cond < 2.6:
            return "no"
        if score_cond >= 1.8 and (score_yes >= 1.0 or score_no >= 1.0):
            return "conditional"
        if score_info >= 2.0 and score_yes < 1.0 and score_no < 1.0 and score_cond < 1.5:
            return "info"

        if status_hint in {"yes", "no", "conditional"}:
            return status_hint
        if score_yes > score_no and score_yes >= 1.3:
            return "yes"
        if score_no > score_yes and score_no >= 1.3:
            return "no"
        if score_cond >= 1.4:
            return "conditional"
        return "info"

    out: dict[str, str] = {}
    for name in ("melchior", "balthasar", "casper"):
        payload = getattr(response, name, None)
        status = "info"
        if isinstance(payload, dict):
            raw = payload.get("status")
            status_hint = _normalize_vote_status(raw)
            if status_hint == "error":
                status = "error"
            elif status_hint in {"yes", "no", "deadlock"}:
                status = status_hint
            else:
                status = infer_vote(payload, status_hint)
        if status not in {"yes", "no", "conditional", "deadlock", "info", "error"}:
            status = "info"
        out[name] = status
    return out


def _derive_consensus(consensus_raw: str, status_raw: str, brains: dict[str, str]) -> str:
    by_name = _canonical_consensus(consensus_raw)
    if by_name in {"unanimous", "majority", "deadlock"}:
        return by_name

    status = _normalize_vote_status(status_raw)
    if status == "deadlock":
        return "deadlock"

    vals = [v for v in brains.values() if v]
    yes = vals.count("yes")
    no = vals.count("no")
    cond = vals.count("conditional")
    info = vals.count("info")
    dead = vals.count("deadlock")

    if dead >= 2:
        return "deadlock"
    if dead >= 1 and yes >= 1 and no >= 1:
        return "deadlock"
    if yes == 3 or no == 3:
        return "unanimous"
    if yes >= 2 or no >= 2:
        return "majority"
    if yes >= 1 and no >= 1 and cond == 0:
        return "deadlock"
    if cond >= 2 or (cond >= 1 and (yes >= 1 or no >= 1)):
        return "conditional"
    if status in {"yes", "no"}:
        return "majority"
    if status == "conditional" or by_name == "conditional":
        return "conditional"
    if by_name == "informational" or status in {"info", "error"}:
        return "informational"
    if info >= 2:
        return "informational"
    # Thin-signal fallback: prefer informational over forcing conditional.
    if cond == 1 and info >= 1 and yes == 0 and no == 0:
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

        if not openai_api_key:
            _log("error", "OPENAI_API_KEY missing; mock mode is disabled.")
            _write_json({
                "ok": False,
                "error": (
                    "MAGI requires a real OpenAI API key. "
                    "No key was provided, and mock mode is disabled."
                ),
            })
            return 0

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
            magi.initialize(api_key=openai_api_key, model=model, use_mock_on_failure=False)
        except TypeError:
            # Backward compatibility if MAGI initialize signature changes.
            magi.initialize(api_key=openai_api_key, model=model)
        mode = _clean_text(getattr(magi, "mode", "")) or "unknown"
        init_error = _clean_text(getattr(magi, "last_init_error", ""))
        _log("info", f"MAGI initialized mode={mode} model={model}")
        if mode.lower() == "mock":
            _log("error", f"MAGI initialized in mock mode. init_error={init_error or '(none)'}")
            _write_json({
                "ok": False,
                "error": (
                    "MAGI initialized in mock mode, which is not allowed. "
                    + (f"Init error: {init_error}" if init_error else "")
                ).strip(),
            })
            return 0

        try:
            response = magi.deliberate(question)
        except Exception as exc:
            _log("error", f"MAGI deliberate failed: {exc}")
            raise
        answer_raw = _clean_text(getattr(response, "answer", ""))
        answer = _normalize_answer_text(answer_raw, response)
        status = _clean_text(getattr(response, "status", "info")) or "info"
        if _normalize_vote_status(status) == "error":
            _log("error", f"MAGI response status=error answer={answer[:220]}")
            _write_json({
                "ok": False,
                "error": answer or "MAGI failed to complete deliberation in OpenAI mode.",
                "model": model,
                "magi_mode": mode,
                "magi_init_error": init_error,
            })
            return 0
        primary_consensus_raw = _clean_text(getattr(response, "consensus", ""))
        brain_states = _brain_statuses(response)
        consensus = _derive_consensus(primary_consensus_raw, status, brain_states)

        if consensus in {"informational", "conditional"}:
            # Run a dedicated decision-style checkpoint so MAGI can surface
            # more decisive consensus types for guidance workflows.
            probe_q = _compose_consensus_probe_question(user_text, answer)
            try:
                probe = magi.deliberate(probe_q)
                probe_status = _clean_text(getattr(probe, "status", "info")) or "info"
                probe_consensus_raw = _clean_text(getattr(probe, "consensus", ""))
                probe_brains = _brain_statuses(probe)
                probe_consensus = _derive_consensus(probe_consensus_raw, probe_status, probe_brains)
                if consensus == "informational" and probe_consensus != "informational":
                    consensus = probe_consensus
                    brain_states = probe_brains
                    status = probe_status
                elif consensus == "conditional" and probe_consensus in {"majority", "unanimous", "deadlock"}:
                    consensus = probe_consensus
                    brain_states = probe_brains
                    status = probe_status
            except Exception as exc:
                _log("warning", f"Consensus probe failed: {exc}")

        emotions = _detect_emotions(f"{user_text}\n{answer}")

        _write_json({
            "ok": True,
            "text": answer,
            "status": status,
            "consensus": consensus,
            "brain_statuses": brain_states,
            "emotions": emotions,
            "model": model,
            "magi_mode": mode,
            "magi_init_error": init_error
        })
        return 0
    except Exception as exc:
        _log("error", f"Bridge fatal error: {exc}")
        _log("error", traceback.format_exc().strip())
        _write_json({"ok": False, "error": str(exc)})
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
