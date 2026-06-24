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


def _truncate_tail(text: str, max_len: int) -> str:
    s = (text or "").strip()
    if max_len <= 0 or len(s) <= max_len:
        return s
    return s[-max_len:]


def _truncate_head(text: str, max_len: int) -> str:
    s = (text or "").strip()
    if max_len <= 0 or len(s) <= max_len:
        return s
    return s[:max_len]


def _compose_question(system_prompt: str, user_text: str, max_tokens: int, temperature: float) -> str:
    sys_txt = _truncate_head(system_prompt or "", 2200)
    usr_txt = _truncate_tail(user_text or "", 3600)
    token_hint = max(32, int(max_tokens) if isinstance(max_tokens, int) else 220)
    temp_hint = float(temperature) if isinstance(temperature, (int, float)) else 0.7

    parts = [
        "Should Sim deliver practical journaling guidance to this user right now?",
        "This is a decision task. Default to approve/reject/conditional; use informational only for genuinely non-decision prompts.",
        "If conditional, include explicit prerequisites in your rationale.",
    ]
    if sys_txt:
        parts.append("System guidance (trimmed):")
        parts.append(sys_txt)
    if usr_txt:
        parts.append("User request / journal context (trimmed):")
        parts.append(usr_txt)
    parts.append(
        f"Response constraints: plain text only, concise, target tokens <= {token_hint}, temperature hint {temp_hint:.2f}."
    )
    return "\n\n".join(parts).strip()


def _compose_consensus_probe_question(user_text: str, guidance_text: str) -> str:
    journal = _truncate_tail(user_text or "", 1800)
    guidance = _truncate_head(guidance_text or "", 900)
    return (
        "Should Sim APPROVE delivering this guidance to the user right now?\n\n"
        "MAGI consensus checkpoint.\n"
        "Vote as approve, reject, conditional, deadlock, or informational based on safety, practicality, and empathy.\n"
        "Prefer approve/reject when the guidance is already actionable.\n"
        "Use conditional only when explicit prerequisites are missing.\n"
        "Use informational only when the question is non-decision or context-only.\n"
        "Do not default to informational for guidance decisions.\n\n"
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


def _contains_any(text: str, cues: tuple[str, ...]) -> bool:
    return any(cue and cue in text for cue in cues)


_INFORMATIONAL_INTENT_CUES = (
    "analyze", "analysis", "summarize", "summary", "trend", "patterns",
    "pattern", "what do you notice", "insight", "insights", "observe",
)

_DECISION_INTENT_CUES = (
    "should i", "should we", "what should", "help me decide", "decide",
    "recommend", "advice", "guidance", "next step", "what now",
)

_CONDITIONAL_CUES = (
    "only if", "as long as", "provided that", "provided you", "if you can",
    "if this is safe", "before you", "after you", "first make sure",
    "with safeguards", "with boundaries", "with guardrails", "start small",
    "take it step by step", "when you are ready",
)

_CAVEAT_CUES = (
    "however", "but", "except", "caveat", "one caution", "watch out",
    "be careful", "if not", "unless", "avoid",
)

_DEADLOCK_CUES = (
    "tradeoff", "trade-off", "both sides", "conflict", "tension",
    "no clear winner", "equally valid", "balanced risk", "standoff",
)

_RISK_CUES = (
    "risk", "risky", "unsafe", "harm", "harmful", "danger", "dangerous",
    "vulnerable", "crisis", "panic", "overwhelmed", "burnout", "self-harm",
    "suicide", "impulsive", "addiction", "abuse", "relapse",
)

_AUTONOMY_CUES = (
    "autonomy", "agency", "freedom", "choice", "self-directed", "independent",
    "meaning", "purpose", "values", "authentic", "self-respect",
)

_ACTION_CUES = (
    "next step", "steps", "action", "plan", "start", "try", "practice",
    "schedule", "boundary", "boundaries", "write", "talk", "reach out",
    "today", "this week", "do this",
)

_UNCERTAINTY_CUES = (
    "uncertain", "uncertainty", "unclear", "unknown", "insufficient",
    "not enough", "missing", "depends", "might", "could", "may",
)

_EVIDENCE_CUES = (
    "evidence", "data", "track", "measure", "metric", "observe", "pattern",
    "validate", "test", "hypothesis",
)


def _decision_signal(user_text: str, guidance_text: str) -> dict[str, float]:
    merged = f"{_clean_text(user_text)} {_clean_text(guidance_text)}".lower()
    if not merged:
        return {
            "risk": 0.0,
            "autonomy": 0.0,
            "actionability": 0.0,
            "uncertainty": 0.0,
            "evidence": 0.0,
            "vulnerability": 0.0,
        }

    risk = float(_cue_hits(merged, _RISK_CUES))
    autonomy = float(_cue_hits(merged, _AUTONOMY_CUES))
    actionability = float(_cue_hits(merged, _ACTION_CUES))
    uncertainty = float(_cue_hits(merged, _UNCERTAINTY_CUES))
    evidence = float(_cue_hits(merged, _EVIDENCE_CUES))
    vulnerability = float(_cue_hits(merged, ("vulnerable", "harm", "unsafe", "panic", "crisis")))

    if "?" in merged:
        actionability += 0.4
    if "should i" in merged or "what should" in merged:
        actionability += 0.8
    if "if " in merged or "unless" in merged:
        uncertainty += 0.5

    return {
        "risk": risk,
        "autonomy": autonomy,
        "actionability": actionability,
        "uncertainty": uncertainty,
        "evidence": evidence,
        "vulnerability": vulnerability,
    }


def _fallback_brain_statuses_from_signal(user_text: str, guidance_text: str) -> dict[str, str]:
    sig = _decision_signal(user_text, guidance_text)
    risk = sig["risk"]
    autonomy = sig["autonomy"]
    actionability = sig["actionability"]
    uncertainty = sig["uncertainty"]
    evidence = sig["evidence"]
    vulnerability = sig["vulnerability"]

    # Melchior (analytical): pushes conditional when uncertainty/evidence gap is high.
    if (uncertainty >= 2.2 and evidence <= 1.0) or (risk >= 2.5 and evidence <= 0.8):
        melchior = "conditional"
    elif risk >= 3.8 and evidence < 0.5:
        melchior = "no"
    elif actionability >= 1.5 and risk <= 2.4:
        melchior = "yes"
    else:
        melchior = "conditional"

    # Balthasar (protective): rejects on high harm signals, otherwise cautious conditional.
    if risk >= 4.0 or vulnerability >= 2.2:
        balthasar = "no"
    elif risk >= 2.2 or vulnerability >= 1.4:
        balthasar = "conditional"
    elif actionability >= 1.0:
        balthasar = "yes"
    else:
        balthasar = "conditional"

    # Casper (autonomy/meaning): tends yes unless strong harm pressure dominates.
    if risk >= 4.5 and autonomy < 1.2:
        casper = "no"
    elif risk >= 3.0 and autonomy < 1.0:
        casper = "conditional"
    elif autonomy >= 1.2 or actionability >= 1.2:
        casper = "yes"
    else:
        casper = "conditional"

    # Explicit tradeoff language should surface split votes rather than flattening to informational.
    if _has_deadlock_language(user_text, guidance_text):
        if melchior == balthasar == casper:
            melchior, balthasar, casper = "yes", "no", "conditional"

    return {
        "melchior": melchior,
        "balthasar": balthasar,
        "casper": casper,
    }


def _stabilize_brain_statuses(
    brains: dict[str, str],
    user_text: str,
    guidance_text: str,
) -> dict[str, str]:
    normalized: dict[str, str] = {}
    for name in ("melchior", "balthasar", "casper"):
        v = _normalize_vote_status(brains.get(name, ""))
        if v not in {"yes", "no", "conditional", "deadlock", "info", "error"}:
            v = "info"
        normalized[name] = v

    non_info = sum(1 for v in normalized.values() if v in {"yes", "no", "conditional", "deadlock"})
    if non_info == 0 and not _is_informational_request(user_text):
        return _fallback_brain_statuses_from_signal(user_text, guidance_text)

    return normalized


def _is_informational_request(user_text: str) -> bool:
    text = _clean_text(user_text).lower()
    if not text:
        return False
    info_hits = _cue_hits(text, _INFORMATIONAL_INTENT_CUES)
    decision_hits = _cue_hits(text, _DECISION_INTENT_CUES)
    if decision_hits > 0:
        return False
    return info_hits >= 1


def _has_conditional_requirements(text: str) -> bool:
    return _contains_any(_clean_text(text).lower(), _CONDITIONAL_CUES)


def _has_caveat_language(text: str) -> bool:
    return _contains_any(_clean_text(text).lower(), _CAVEAT_CUES)


def _has_deadlock_language(user_text: str, guidance_text: str) -> bool:
    merged = f"{_clean_text(user_text)} {_clean_text(guidance_text)}".lower()
    return _contains_any(merged, _DEADLOCK_CUES)


def _stable_choice_seed(user_text: str, guidance_text: str) -> int:
    seed = 0
    merged = f"{user_text}|{guidance_text}"
    for i, ch in enumerate(merged[:1600]):
        seed = (seed * 131 + ord(ch) + i) % 2_147_483_647
    return seed


def _brain_statuses(response: object, user_text: str = "", guidance_text: str = "") -> dict[str, str]:
    caveat_hint = _has_caveat_language(guidance_text) or _has_caveat_language(user_text)
    conditional_hint = _has_conditional_requirements(user_text) or _has_conditional_requirements(guidance_text)

    def to_float(value: object, default: float = 0.0) -> float:
        try:
            return float(value)
        except Exception:
            return default

    def as_list_count(value: object) -> int:
        if isinstance(value, list):
            return len([x for x in value if _clean_text(str(x))])
        if isinstance(value, str) and _clean_text(value):
            return 1
        return 0

    def infer_vote(payload: dict, status_hint: str) -> str:
        summary = _clean_text(str(payload.get("summary") or ""))
        reasoning = _clean_text(str(payload.get("response") or ""))
        text = f"{summary} {reasoning}".strip().lower()
        if not text:
            return "info"

        confidence = to_float(payload.get("confidence"), 0.0)
        conditions_count = as_list_count(payload.get("conditions"))
        reservations_count = as_list_count(payload.get("reservations"))

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

        if status_hint == "yes" and conditions_count > 0:
            return "conditional"
        if status_hint == "yes" and reservations_count > 0 and (caveat_hint or confidence < 0.78):
            return "conditional"
        if status_hint == "no" and conditions_count > 0 and confidence < 0.84:
            return "conditional"
        if status_hint == "info" and conditions_count > 0:
            return "conditional"
        if status_hint == "info" and reservations_count > 1 and (conditional_hint or caveat_hint):
            return "conditional"

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
    return _stabilize_brain_statuses(out, user_text, guidance_text)


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


def _rebalance_consensus(
    base_consensus: str,
    user_text: str,
    guidance_text: str,
    status_raw: str,
    brains: dict[str, str],
) -> str:
    """
    Map raw MAGI vote patterns to UX-facing consensus buckets with context-sensitive balancing.
    The goal is to keep outcomes expressive (not sticky informational/unanimous)
    while still honoring explicit vote signals.
    """
    base = _canonical_consensus(base_consensus) or _canonical_consensus(_clean_text(base_consensus))
    if not base:
        base = _derive_consensus(base_consensus, status_raw, brains)

    vals = [v for v in brains.values() if v]
    yes = vals.count("yes")
    no = vals.count("no")
    cond = vals.count("conditional")
    info = vals.count("info")
    dead = vals.count("deadlock")

    if _is_informational_request(user_text):
        return "informational"

    conditional_cues = _has_conditional_requirements(user_text) or _has_conditional_requirements(guidance_text)
    caveat_cues = _has_caveat_language(guidance_text)
    deadlock_cues = _has_deadlock_language(user_text, guidance_text)

    # Hard deadlock outcomes.
    if dead >= 2:
        return "deadlock"
    if yes >= 1 and no >= 1 and (dead >= 1 or deadlock_cues):
        return "deadlock"

    # Two explicit conditions usually means conditional consensus.
    if cond >= 2:
        return "conditional"

    # Strong prerequisite language should stay conditional even when all brains lean approve.
    if conditional_cues and (yes + no + cond) >= 2 and no == 0:
        return "conditional"

    # Explicit 2-vs-1 votes should remain majority.
    if (yes == 2 and no == 1) or (no == 2 and yes == 1):
        return "majority"

    # 3-vote alignment with caveats should show majority/conditional, not always unanimous.
    if yes == 3 or no == 3:
        if conditional_cues:
            return "conditional"
        if caveat_cues:
            return "majority"
        return "unanimous"

    # Mixed approve + conditional tends conditional; mixed reject + conditional tends majority/deadlock.
    if cond >= 1 and yes >= 1 and no == 0:
        return "conditional"
    if cond >= 1 and no >= 1 and yes == 0:
        return "conditional" if conditional_cues else "majority"

    # All informational from brains: avoid sticking there for guidance tasks.
    if info >= 2 and yes == 0 and no == 0 and cond == 0 and dead == 0:
        if conditional_cues:
            return "conditional"
        if deadlock_cues:
            return "deadlock"
        return "majority"

    if base in {"unanimous", "majority", "conditional", "deadlock", "informational"}:
        if base == "informational" and not _is_informational_request(user_text):
            # Decision workflows should not collapse to informational unless signals are truly empty.
            if conditional_cues:
                return "conditional"
            return "majority"
        return base

    return "majority" if yes + no + cond > 0 else "informational"


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
        brain_states = _brain_statuses(response, user_text, answer)
        consensus = _derive_consensus(primary_consensus_raw, status, brain_states)
        consensus = _rebalance_consensus(consensus, user_text, answer, status, brain_states)
        informational_request = _is_informational_request(user_text)
        conditional_request = _has_conditional_requirements(user_text)
        guidance_has_conditions = _has_conditional_requirements(answer)

        if consensus in {"informational", "conditional"}:
            # Run a dedicated decision-style checkpoint so MAGI can surface
            # more decisive consensus types for guidance workflows.
            probe_q = _compose_consensus_probe_question(user_text, answer)
            try:
                probe = magi.deliberate(probe_q)
                probe_status = _clean_text(getattr(probe, "status", "info")) or "info"
                probe_consensus_raw = _clean_text(getattr(probe, "consensus", ""))
                probe_brains = _brain_statuses(probe, user_text, answer)
                probe_consensus = _derive_consensus(probe_consensus_raw, probe_status, probe_brains)
                probe_consensus = _rebalance_consensus(probe_consensus, user_text, answer, probe_status, probe_brains)
                if consensus == "informational" and not informational_request and probe_consensus != "informational":
                    consensus = probe_consensus
                    brain_states = probe_brains
                    status = probe_status
                elif consensus == "conditional":
                    # Keep conditional when explicit prerequisites are present.
                    keep_conditional = conditional_request or guidance_has_conditions
                    if not keep_conditional and probe_consensus in {"majority", "unanimous", "deadlock"}:
                        consensus = probe_consensus
                        brain_states = probe_brains
                        status = probe_status
                    elif keep_conditional and probe_consensus == "deadlock" and _has_deadlock_language(user_text, answer):
                        consensus = "deadlock"
                        brain_states = probe_brains
                        status = probe_status
                consensus = _rebalance_consensus(consensus, user_text, answer, status, brain_states)
            except Exception as exc:
                _log("warning", f"Consensus probe failed: {exc}")
                consensus = _rebalance_consensus(consensus, user_text, answer, status, brain_states)
        else:
            consensus = _rebalance_consensus(consensus, user_text, answer, status, brain_states)

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
