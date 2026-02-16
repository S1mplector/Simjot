"""
MAGI System
===========

The complete MAGI System comprising three supercomputer units working in tandem.

Located in the Central Tactical Command Room of NERV Headquarters, the MAGI
System controls, manages, and automates various critical systems. Each unit
has a distinct personality matrix, often leading to different solutions for
the same problem.

For major decisions such as self-destruction, unanimous consensus among all
three units must be reached.
"""

from dataclasses import dataclass
from typing import Dict, List, Optional, Any, Callable
from enum import Enum
from datetime import datetime
import os
import hashlib
import re
from concurrent.futures import ThreadPoolExecutor

from ..ptos.matrix import PersonalityMatrix, PersonalityAspect
from ..ptos.organic import OrganicProcessor, ProcessingMode
from ..ptos.transplant import TransplantProcedure
from ..ptos.engram import (
    EngramStore,
    MemoryEngram,
    EngramType,
    EngramStrength,
)
from ..llm.client import completion_token_kwargs, temperature_kwargs

_STOP_WORDS = {
    "a", "an", "the", "and", "or", "but", "if", "then", "else", "for", "to", "of",
    "in", "on", "at", "with", "by", "from", "as", "is", "are", "was", "were", "be",
    "been", "being", "this", "that", "these", "those", "it", "its", "my", "your",
    "our", "their", "we", "they", "you", "i", "me", "he", "she", "them", "him", "her",
    "about", "into", "over", "under", "again", "very", "just", "more", "most", "can",
    "could", "should", "would", "will", "shall", "do", "does", "did", "have", "has",
    "had", "than", "too", "also",
}

_POSITIVE_TOKENS = {
    "calm", "safe", "stable", "grateful", "hope", "hopeful", "progress", "support",
    "confidence", "clear", "grounded", "kind", "compassion", "resilient",
}

_NEGATIVE_TOKENS = {
    "unsafe", "risk", "risky", "harm", "panic", "overwhelmed", "anxious", "anger",
    "stressed", "burnout", "stuck", "hopeless", "fear", "afraid", "crisis",
}

_EMOTION_TOKEN_MAP = {
    "joy": {"joy", "grateful", "gratitude", "relief", "proud", "hopeful"},
    "calm": {"calm", "grounded", "steady", "stable", "peaceful"},
    "sadness": {"sad", "down", "grief", "lonely", "hopeless"},
    "anger": {"angry", "anger", "frustrated", "resentful", "rage"},
    "anxiety": {"anxious", "anxiety", "worry", "worried", "fear", "panic"},
    "stress": {"stress", "stressed", "overwhelmed", "burnout", "pressure"},
}


class SystemStatus(Enum):
    """Operational status of a MAGI system."""
    OFFLINE = "offline"
    INITIALIZING = "initializing"
    STANDBY = "standby"
    OPERATIONAL = "operational"
    DELIBERATING = "deliberating"
    UNDER_ATTACK = "under_attack"
    COMPROMISED = "compromised"
    EMERGENCY = "emergency"
    MAINTENANCE = "maintenance"


class AlertLevel(Enum):
    """NERV alert levels."""
    NORMAL = "normal"
    CAUTION = "caution"
    WARNING = "warning"
    DANGER = "danger"
    CRITICAL = "critical"


@dataclass
class MAGIUnit:
    """
    A single MAGI supercomputer unit.
    
    Each unit contains:
    - A personality matrix (transplanted from Dr. Naoko Akagi)
    - An organic processor (7th generation bio-computer)
    - An engram store (memory system)
    """
    
    designation: str  # MELCHIOR, BALTHASAR, CASPER
    magi_number: int  # 1, 2, 3
    aspect: PersonalityAspect
    
    # Core components
    matrix: Optional[PersonalityMatrix] = None
    processor: Optional[OrganicProcessor] = None
    memory: Optional[EngramStore] = None
    
    # Status
    status: SystemStatus = SystemStatus.OFFLINE
    integrity: float = 1.0  # System health
    last_diagnostic: Optional[datetime] = None
    
    # LLM interface
    _llm_client: Optional[Any] = None
    
    def __post_init__(self):
        """Initialize unit components."""
        if self.memory is None:
            self.memory = EngramStore(self.designation)
    
    def initialize(self) -> bool:
        """Initialize the unit through personality transplant."""
        self.status = SystemStatus.INITIALIZING
        
        # Execute transplant procedure
        procedure = TransplantProcedure()
        result = procedure.execute(
            designation=self.designation,
            magi_number=self.magi_number,
            aspect=self.aspect
        )
        
        if result.success:
            self.matrix = result.matrix
            self.processor = result.processor
            self.status = SystemStatus.STANDBY
            self.last_diagnostic = datetime.now()
            return True
        else:
            self.status = SystemStatus.OFFLINE
            return False

    def _extract_keywords(self, text: str, limit: int = 12) -> List[str]:
        tokens = re.findall(r"[a-zA-Z][a-zA-Z'-]*", (text or "").lower())
        out: List[str] = []
        seen = set()
        for token in tokens:
            if len(token) < 3 or token in _STOP_WORDS:
                continue
            if token in seen:
                continue
            seen.add(token)
            out.append(token)
            if len(out) >= max(1, limit):
                break
        return out

    def _estimate_valence(self, text: str) -> float:
        tokens = re.findall(r"[a-zA-Z][a-zA-Z'-]*", (text or "").lower())
        pos = sum(1 for t in tokens if t in _POSITIVE_TOKENS)
        neg = sum(1 for t in tokens if t in _NEGATIVE_TOKENS)
        total = max(1, pos + neg)
        return max(-1.0, min(1.0, (pos - neg) / float(total)))

    def _estimate_emotions(self, text: str, limit: int = 3) -> List[str]:
        tokens = set(re.findall(r"[a-zA-Z][a-zA-Z'-]*", (text or "").lower()))
        scored: List[tuple[str, int]] = []
        for emotion, vocab in _EMOTION_TOKEN_MAP.items():
            hit_count = len(tokens & vocab)
            if hit_count > 0:
                scored.append((emotion, hit_count))
        scored.sort(key=lambda x: x[1], reverse=True)
        return [e for e, _ in scored[:max(1, limit)]]

    def _relevant_engrams(self, query: str, top_k: int = 5) -> List[MemoryEngram]:
        if not self.memory:
            return []
        keywords = self._extract_keywords(query)
        if not keywords:
            return []

        primary = self.memory.search_by_keywords(keywords, top_k=max(3, top_k))
        seed_ids = [eng.engram_id for eng in primary[:3]]
        spread = self.memory.spreading_activation(seed_ids, depth=2, activation_decay=0.55) if seed_ids else []

        merged: Dict[str, MemoryEngram] = {}
        for eng in primary + spread:
            merged[eng.engram_id] = eng

        ranked = sorted(
            merged.values(),
            key=lambda e: (e.retrieval_count, e.consolidation_level, e.reliability),
            reverse=True,
        )
        return ranked[:top_k]

    def _memory_context_block(self, engrams: List[MemoryEngram], max_items: int = 4) -> str:
        if not engrams:
            return ""
        lines: List[str] = []
        for eng in engrams[:max_items]:
            try:
                eng.retrieve()
            except Exception:
                pass
            summary = (eng.summary or eng.content or "").strip()
            if len(summary) > 180:
                summary = summary[:177].rstrip() + "..."
            if not summary:
                continue
            lines.append(
                f"- [{eng.engram_type.value}|{eng.strength.value}|rel={eng.reliability:.2f}] {summary}"
            )
        return "\n".join(lines)

    def _store_engram(
        self,
        content: str,
        summary: str,
        engram_type: EngramType,
        source: str,
        strength: EngramStrength = EngramStrength.LABILE,
        related: Optional[List[MemoryEngram]] = None,
    ) -> Optional[MemoryEngram]:
        if not self.memory:
            return None

        body = (content or "").strip()
        if not body:
            return None

        now = datetime.now()
        digest = hashlib.sha256(
            f"{self.designation}|{source}|{now.isoformat()}|{body}".encode("utf-8")
        ).hexdigest()[:14]
        engram_id = f"{self.designation.lower()}-{engram_type.value}-{digest}"

        valence = self._estimate_valence(body)
        emotions = self._estimate_emotions(body)
        keywords = self._extract_keywords(f"{summary} {body}", limit=14)

        engram = MemoryEngram(
            engram_id=engram_id,
            engram_type=engram_type,
            strength=strength,
            content=body[:3500],
            summary=(summary or body)[:400],
            keywords=keywords,
            emotional_valence=valence,
            emotional_intensity=min(1.0, 0.35 + (0.08 * len(emotions))),
            associated_emotions=emotions,
            source=source,
            reliability=0.85 if engram_type == EngramType.EPISODIC else 0.9,
        )

        for rel in (related or [])[:3]:
            try:
                engram.add_link(rel.engram_id, 0.45, "semantic")
                rel.add_link(engram.engram_id, 0.35, "semantic")
            except Exception:
                continue

        self.memory.store(engram)
        if len(self.memory.engrams) % 5 == 0:
            self.memory.consolidation_pass()
        return engram
    
    def set_llm_client(self, client: Any) -> None:
        """Set the LLM client for this unit."""
        self._llm_client = client
    
    def activate(self) -> None:
        """Bring unit to operational status."""
        if self.status == SystemStatus.STANDBY:
            self.status = SystemStatus.OPERATIONAL
            if self.processor:
                self.processor.set_mode(ProcessingMode.NORMAL)
    
    def process_query(self, query: str) -> Dict[str, Any]:
        """Process a query through this unit's personality matrix."""
        if not self._llm_client or not self.matrix:
            return {"error": "Unit not properly initialized"}
        
        # Activate relevant neural clusters
        if self.processor:
            activations = self.processor.activate_by_keyword(query)
            self.processor.propagate(steps=2)
        
        # Build system prompt from matrix and memory context
        system_prompt = self.matrix.generate_system_prompt()
        retrieved = self._relevant_engrams(query)
        memory_context = self._memory_context_block(retrieved)
        query_payload = query
        if memory_context:
            query_payload = (
                "Relevant engram recalls from your memory substrate:\n"
                f"{memory_context}\n\n"
                "Current query:\n"
                f"{query}"
            )
        
        # Call LLM
        try:
            model = os.getenv("MAGI_MODEL", "gpt-5")
            response = self._llm_client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": query_payload}
                ],
                **temperature_kwargs(model, 0.7),
                **completion_token_kwargs(model, 1500)
            )
            
            content = response.choices[0].message.content
            
            # Calculate confidence from processor state
            confidence = self.processor.calculate_confidence() if self.processor else 0.7
            self._store_engram(
                content=f"Query: {query}\nResponse: {content}",
                summary=f"{self.designation} handled query: {query[:120]}",
                engram_type=EngramType.SEMANTIC,
                source="process_query",
                strength=EngramStrength.LABILE,
                related=retrieved,
            )
            
            return {
                "designation": self.designation,
                "response": content,
                "confidence": confidence,
                "active_values": self.processor.get_active_values() if self.processor else [],
                "emotional_state": self.processor.get_emotional_state() if self.processor else {},
                "engram_context": [e.to_dict() for e in retrieved[:3]],
            }
            
        except Exception as e:
            return {"error": str(e)}
    
    def form_verdict(self, query: str, query_type: str = "yes_no") -> Dict[str, Any]:
        """Form a verdict on a yes/no question."""
        if not self._llm_client or not self.matrix:
            return {"error": "Unit not properly initialized"}
        
        system_prompt = self.matrix.generate_system_prompt()
        recalled = self._relevant_engrams(query, top_k=6)
        memory_context = self._memory_context_block(recalled, max_items=5)
        query_kind = (query_type or "decision").strip().lower()
        
        verdict_prompt = f"""As {self.designation}, consider this question and form your verdict.

Question type: {query_kind}
Question: {query}

Relevant memory engrams:
{memory_context if memory_context else "- none"}

Respond in JSON format:
{{
    "verdict": "APPROVE" | "REJECT" | "CONDITIONAL" | "DEADLOCK" | "INFORMATIONAL",
    "confidence": 0.0-1.0,
    "summary": "One sentence position statement",
    "reasoning": "Your detailed reasoning (2-3 paragraphs)",
    "conditions": ["List conditions if CONDITIONAL"],
    "key_values_applied": ["Which of your core values influenced this decision"],
    "reservations": ["Key cautions or dissent points, if any"]
}}"""

        try:
            model = os.getenv("MAGI_MODEL", "gpt-5")
            response = self._llm_client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": verdict_prompt}
                ],
                response_format={"type": "json_object"},
                **temperature_kwargs(model, 0.7),
                **completion_token_kwargs(model, 1000)
            )
            
            import json
            result = json.loads(response.choices[0].message.content)
            verdict_raw = str(result.get("verdict", "")).strip().upper()
            if verdict_raw in {"INFO", "INFORM"}:
                verdict_raw = "INFORMATIONAL"
            if verdict_raw not in {"APPROVE", "REJECT", "CONDITIONAL", "DEADLOCK", "INFORMATIONAL"}:
                verdict_raw = "INFORMATIONAL"
            result["verdict"] = verdict_raw
            result["designation"] = self.designation
            result["magi_number"] = self.magi_number
            summary = str(result.get("summary") or "").strip()
            reasoning = str(result.get("reasoning") or "").strip()
            self._store_engram(
                content=(
                    f"Query: {query}\n"
                    f"Verdict: {result['verdict']}\n"
                    f"Summary: {summary}\n"
                    f"Reasoning: {reasoning}"
                ),
                summary=f"{self.designation} verdict {result['verdict']}: {summary[:120]}",
                engram_type=EngramType.EPISODIC,
                source="verdict",
                strength=EngramStrength.STABLE,
                related=recalled,
            )
            result["engram_context"] = [e.to_dict() for e in recalled[:3]]
            return result
            
        except Exception as e:
            return {
                "designation": self.designation,
                "verdict": "ERROR",
                "error": str(e)
            }
    
    def get_status(self) -> Dict[str, Any]:
        """Get comprehensive unit status."""
        return {
            "designation": self.designation,
            "magi_number": self.magi_number,
            "aspect": self.aspect.value,
            "status": self.status.value,
            "integrity": self.integrity,
            "processor": self.processor.get_status() if self.processor else None,
            "matrix_hash": self.matrix.matrix_hash if self.matrix else None,
            "memory_stats": self.memory.get_statistics() if self.memory else None,
        }


class MAGISystem:
    """
    The complete MAGI System - three supercomputers working in tandem.
    
    The system manages deliberation between the three units and
    synthesizes their outputs into unified decisions.
    """
    
    def __init__(self, installation_id: str = "MAGI-01", location: str = "Tokyo-3"):
        self.installation_id = installation_id
        self.location = location
        self.status = SystemStatus.OFFLINE
        self.alert_level = AlertLevel.NORMAL
        
        # The three MAGI units
        self.melchior = MAGIUnit(
            designation="MELCHIOR",
            magi_number=1,
            aspect=PersonalityAspect.SCIENTIST
        )
        self.balthasar = MAGIUnit(
            designation="BALTHASAR", 
            magi_number=2,
            aspect=PersonalityAspect.MOTHER
        )
        self.casper = MAGIUnit(
            designation="CASPER",
            magi_number=3,
            aspect=PersonalityAspect.WOMAN
        )
        
        self.units = {
            "melchior": self.melchior,
            "balthasar": self.balthasar,
            "casper": self.casper,
        }
        
        # Event callbacks
        self._on_unit_response: Optional[Callable] = None
        self._on_consensus_reached: Optional[Callable] = None
        
        # Processing
        self._executor = ThreadPoolExecutor(max_workers=3)
    
    def initialize(self) -> bool:
        """Initialize all three MAGI units."""
        self.status = SystemStatus.INITIALIZING
        
        success = True
        for unit in self.units.values():
            if not unit.initialize():
                success = False
        
        if success:
            self.status = SystemStatus.STANDBY
        
        return success
    
    def set_llm_client(self, client: Any) -> None:
        """Set LLM client for all units."""
        for unit in self.units.values():
            unit.set_llm_client(client)
    
    def activate(self) -> None:
        """Bring all units to operational status."""
        for unit in self.units.values():
            unit.activate()
        self.status = SystemStatus.OPERATIONAL
    
    def deliberate(self, query: str, require_unanimous: bool = False) -> Dict[str, Any]:
        """
        Run a full deliberation across all three MAGI units.
        
        For critical decisions (require_unanimous=True), all three
        units must agree for action to be taken.
        """
        self.status = SystemStatus.DELIBERATING
        start_time = datetime.now()
        
        # Get verdicts from all units in parallel
        futures = {}
        for name, unit in self.units.items():
            future = self._executor.submit(unit.form_verdict, query)
            futures[name] = future
        
        verdicts = {}
        for name, future in futures.items():
            try:
                verdicts[name] = future.result(timeout=60)
                if self._on_unit_response:
                    self._on_unit_response(name, verdicts[name])
            except Exception as e:
                verdicts[name] = {"designation": name.upper(), "verdict": "ERROR", "error": str(e)}
        
        # Analyze verdicts
        result = self._analyze_verdicts(verdicts, require_unanimous)
        result["query"] = query
        result["processing_time_ms"] = (datetime.now() - start_time).total_seconds() * 1000
        result["require_unanimous"] = require_unanimous
        
        if self._on_consensus_reached:
            self._on_consensus_reached(result)
        
        self.status = SystemStatus.OPERATIONAL
        return result
    
    def _analyze_verdicts(self, verdicts: Dict[str, Dict], require_unanimous: bool) -> Dict[str, Any]:
        """Analyze verdicts and determine consensus."""
        
        # Count verdict types
        counts = {
            "APPROVE": 0,
            "REJECT": 0,
            "CONDITIONAL": 0,
            "DEADLOCK": 0,
            "INFORMATIONAL": 0,
            "ERROR": 0,
        }
        for v in verdicts.values():
            verdict_type = str(v.get("verdict", "ERROR")).strip().upper()
            if verdict_type in {"INFO", "INFORM"}:
                verdict_type = "INFORMATIONAL"
            if verdict_type not in counts:
                verdict_type = "ERROR"
            counts[verdict_type] = counts.get(verdict_type, 0) + 1
        
        # Determine consensus
        if counts["ERROR"] > 0:
            consensus = "ERROR"
            final_verdict = None
        elif counts["INFORMATIONAL"] >= 2 and counts["APPROVE"] == 0 and counts["REJECT"] == 0:
            consensus = "INFORMATIONAL"
            final_verdict = "INFORMATIONAL"
        elif counts["APPROVE"] == 3:
            consensus = "UNANIMOUS_APPROVE"
            final_verdict = "APPROVE"
        elif counts["REJECT"] == 3:
            consensus = "UNANIMOUS_REJECT"
            final_verdict = "REJECT"
        elif counts["DEADLOCK"] >= 2:
            consensus = "DEADLOCK"
            final_verdict = None
        elif counts["APPROVE"] >= 2:
            consensus = "MAJORITY_APPROVE"
            final_verdict = "APPROVE" if not require_unanimous else None
        elif counts["REJECT"] >= 2:
            consensus = "MAJORITY_REJECT"
            final_verdict = "REJECT" if not require_unanimous else None
        elif counts["CONDITIONAL"] >= 2:
            consensus = "CONDITIONAL"
            final_verdict = "CONDITIONAL"
        else:
            consensus = "DEADLOCK"
            final_verdict = None
        
        # Collect conditions
        all_conditions = []
        for v in verdicts.values():
            conditions = v.get("conditions", [])
            if conditions:
                all_conditions.extend(conditions)
        
        # Build result
        return {
            "consensus": consensus,
            "final_verdict": final_verdict,
            "action_authorized": final_verdict is not None and consensus != "ERROR",
            "verdicts": verdicts,
            "vote_counts": counts,
            "conditions": list(set(all_conditions)),
            "dissenting_units": [
                v["designation"] for v in verdicts.values()
                if v.get("verdict") != final_verdict and final_verdict
            ],
        }
    
    def query_unit(self, unit_name: str, query: str) -> Dict[str, Any]:
        """Query a specific MAGI unit directly."""
        unit = self.units.get(unit_name.lower())
        if not unit:
            return {"error": f"Unknown unit: {unit_name}"}
        return unit.process_query(query)
    
    def get_status(self) -> Dict[str, Any]:
        """Get comprehensive system status."""
        return {
            "installation_id": self.installation_id,
            "location": self.location,
            "status": self.status.value,
            "alert_level": self.alert_level.value,
            "units": {name: unit.get_status() for name, unit in self.units.items()},
        }
    
    def set_alert_level(self, level: AlertLevel) -> None:
        """Set the NERV alert level."""
        self.alert_level = level
        
        # Adjust processing modes based on alert
        if level in [AlertLevel.DANGER, AlertLevel.CRITICAL]:
            for unit in self.units.values():
                if unit.processor:
                    unit.processor.set_mode(ProcessingMode.EMERGENCY)
    
    def on_unit_response(self, callback: Callable) -> None:
        """Register callback for unit responses."""
        self._on_unit_response = callback
    
    def on_consensus_reached(self, callback: Callable) -> None:
        """Register callback for consensus events."""
        self._on_consensus_reached = callback
