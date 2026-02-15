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

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Callable
from enum import Enum
from datetime import datetime
import os
import asyncio
from concurrent.futures import ThreadPoolExecutor

from ..ptos.matrix import PersonalityMatrix, PersonalityAspect
from ..ptos.organic import OrganicProcessor, ProcessingMode
from ..ptos.transplant import TransplantProcedure
from ..ptos.engram import EngramStore
from ..llm.client import completion_token_kwargs, temperature_kwargs


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
        
        # Build system prompt from matrix
        system_prompt = self.matrix.generate_system_prompt()
        
        # Call LLM
        try:
            model = os.getenv("MAGI_MODEL", "gpt-5")
            response = self._llm_client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": query}
                ],
                **temperature_kwargs(model, 0.7),
                **completion_token_kwargs(model, 1500)
            )
            
            content = response.choices[0].message.content
            
            # Calculate confidence from processor state
            confidence = self.processor.calculate_confidence() if self.processor else 0.7
            
            return {
                "designation": self.designation,
                "response": content,
                "confidence": confidence,
                "active_values": self.processor.get_active_values() if self.processor else [],
                "emotional_state": self.processor.get_emotional_state() if self.processor else {},
            }
            
        except Exception as e:
            return {"error": str(e)}
    
    def form_verdict(self, query: str, query_type: str = "yes_no") -> Dict[str, Any]:
        """Form a verdict on a yes/no question."""
        if not self._llm_client or not self.matrix:
            return {"error": "Unit not properly initialized"}
        
        system_prompt = self.matrix.generate_system_prompt()
        
        verdict_prompt = f"""As {self.designation}, consider this question and form your verdict.

Question: {query}

Respond in JSON format:
{{
    "verdict": "APPROVE" | "REJECT" | "CONDITIONAL",
    "confidence": 0.0-1.0,
    "summary": "One sentence position statement",
    "reasoning": "Your detailed reasoning (2-3 paragraphs)",
    "conditions": ["List conditions if CONDITIONAL"],
    "key_values_applied": ["Which of your core values influenced this decision"]
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
            result["designation"] = self.designation
            result["magi_number"] = self.magi_number
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
        counts = {"APPROVE": 0, "REJECT": 0, "CONDITIONAL": 0, "ERROR": 0}
        for v in verdicts.values():
            verdict_type = v.get("verdict", "ERROR")
            counts[verdict_type] = counts.get(verdict_type, 0) + 1
        
        # Determine consensus
        if counts["ERROR"] > 0:
            consensus = "ERROR"
            final_verdict = None
        elif counts["APPROVE"] == 3:
            consensus = "UNANIMOUS_APPROVE"
            final_verdict = "APPROVE"
        elif counts["REJECT"] == 3:
            consensus = "UNANIMOUS_REJECT"
            final_verdict = "REJECT"
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
