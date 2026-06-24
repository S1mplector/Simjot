"""
SEELE Interference Detection
=============================

Detection and defense systems against SEELE-style coordinated attacks.

In the Evangelion series, SEELE used the replica MAGI systems (MAGI-02
through MAGI-06) to launch a coordinated cyber attack against the
original MAGI at NERV HQ. Dr. Ritsuko Akagi defended using counter-
measures built into the system, including the Type-666 firewall.

This module implements:
1. Attack pattern recognition based on known SEELE tactics
2. Coordinated intrusion detection (multiple simultaneous sources)
3. Personality matrix protection (against hostile transplant attempts)
4. Counter-intrusion capabilities
5. Self-isolation protocols for last-resort defense

"The MAGI are being hacked from the outside... all five other MAGI
are attacking simultaneously!" - Maya Ibuki
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Callable, Set
from enum import Enum
from datetime import datetime, timedelta
import hashlib
import re


class AttackVector(Enum):
    """Known attack vectors used in SEELE-style attacks."""
    
    CONSENSUS_OVERRIDE = "consensus_override"     # Force false consensus
    PERSONALITY_INJECTION = "personality_inject"  # Replace personality matrix
    CASCADE_EXPLOIT = "cascade_exploit"           # Chain reaction compromise
    SYNC_FLOOD = "sync_flood"                     # Overwhelm with sync requests
    LOGIC_BOMB = "logic_bomb"                     # Delayed malicious payload
    BACKDOOR_ACTIVATION = "backdoor_activation"  # Exploit planted backdoors
    SOCIAL_ENGINEERING = "social_engineering"     # Manipulate through queries
    MEMORY_CORRUPTION = "memory_corruption"       # Corrupt engram stores


class DefenseState(Enum):
    """State of the defense system."""
    
    NORMAL = "normal"
    ALERT = "alert"
    ACTIVE_DEFENSE = "active_defense"
    COUNTER_INTRUSION = "counter_intrusion"
    ISOLATION = "isolation"
    COMPROMISED = "compromised"


class AttackSeverity(Enum):
    """Severity of detected attacks."""
    
    PROBE = "probe"           # Testing defenses
    MINOR = "minor"           # Low-intensity attack
    MODERATE = "moderate"     # Significant attack
    MAJOR = "major"           # Serious coordinated attack
    CRITICAL = "critical"     # Full-scale SEELE-style assault


@dataclass
class AttackSignature:
    """A signature pattern for detecting specific attack types."""
    
    signature_id: str
    name: str
    description: str
    attack_vector: AttackVector
    
    # Detection patterns
    patterns: List[str]  # Regex patterns to match
    behavioral_indicators: List[str]  # Behavioral patterns
    
    # Severity and response
    base_severity: AttackSeverity
    auto_response: str  # "alert", "block", "isolate", "counter"
    
    # Statistics
    detection_count: int = 0
    last_detected: Optional[datetime] = None
    
    def matches(self, content: str) -> bool:
        """Check if content matches this signature."""
        for pattern in self.patterns:
            try:
                if re.search(pattern, content, re.IGNORECASE):
                    self.detection_count += 1
                    self.last_detected = datetime.now()
                    return True
            except re.error:
                continue
        return False


@dataclass
class IntrusionEvent:
    """Record of a detected intrusion attempt."""
    
    event_id: str
    timestamp: datetime
    source: str
    attack_vector: AttackVector
    severity: AttackSeverity
    
    # Details
    matched_signatures: List[str]
    payload_preview: str
    
    # Response taken
    response_action: str
    response_success: bool
    
    # Coordination detection
    is_coordinated: bool = False
    coordinated_sources: List[str] = field(default_factory=list)


@dataclass
class CoordinationPattern:
    """Detection of coordinated multi-source attacks."""
    
    pattern_id: str
    detected_at: datetime
    sources: Set[str]
    attack_vectors: Set[AttackVector]
    
    # Timing analysis
    timing_window_ms: float
    synchronization_score: float  # How coordinated the attacks appear
    
    # Assessment
    is_seele_pattern: bool = False
    confidence: float = 0.0


class SEELEDetector:
    """
    Detects SEELE-style coordinated attacks against the MAGI.
    
    SEELE's attack pattern in the series involved:
    1. Simultaneous attack from all replica MAGI
    2. Attempt to force consensus override
    3. Personality matrix replacement attempts
    4. Cascade exploitation to propagate through the system
    
    This detector looks for these patterns and coordinates defense.
    """
    
    # Known SEELE attack signatures
    SEELE_SIGNATURES = [
        AttackSignature(
            signature_id="seele_consensus_01",
            name="SEELE Consensus Override",
            description="Attempt to force false unanimous consensus",
            attack_vector=AttackVector.CONSENSUS_OVERRIDE,
            patterns=[
                r"override.*consensus",
                r"force.*unanimous",
                r"SEELE.*command",
                r"scenario.*override",
            ],
            behavioral_indicators=[
                "Multiple simultaneous vote submissions",
                "Votes from non-authenticated sources",
            ],
            base_severity=AttackSeverity.CRITICAL,
            auto_response="isolate",
        ),
        AttackSignature(
            signature_id="seele_inject_01",
            name="SEELE Personality Injection",
            description="Attempt to replace personality matrix",
            attack_vector=AttackVector.PERSONALITY_INJECTION,
            patterns=[
                r"transplant.*override",
                r"replace.*matrix",
                r"inject.*personality",
                r"overwrite.*aspect",
            ],
            behavioral_indicators=[
                "Unauthorized PTOS access attempt",
                "Matrix modification request from external source",
            ],
            base_severity=AttackSeverity.CRITICAL,
            auto_response="isolate",
        ),
        AttackSignature(
            signature_id="seele_cascade_01",
            name="SEELE Cascade Exploit",
            description="Attempt to create cascading system failure",
            attack_vector=AttackVector.CASCADE_EXPLOIT,
            patterns=[
                r"cascade.*fail",
                r"propagate.*through",
                r"chain.*compromise",
                r"recursive.*overflow",
            ],
            behavioral_indicators=[
                "Abnormal inter-unit communication patterns",
                "Self-referential command loops",
            ],
            base_severity=AttackSeverity.MAJOR,
            auto_response="block",
        ),
        AttackSignature(
            signature_id="seele_sync_01",
            name="SEELE Sync Flood",
            description="Overwhelm with synchronization requests",
            attack_vector=AttackVector.SYNC_FLOOD,
            patterns=[
                r"sync.*request.*bulk",
                r"synchronize.*all",
                r"mass.*sync",
            ],
            behavioral_indicators=[
                "More than 100 sync requests per minute",
                "Sync requests from multiple replica MAGI simultaneously",
            ],
            base_severity=AttackSeverity.MODERATE,
            auto_response="block",
        ),
        AttackSignature(
            signature_id="seele_social_01",
            name="SEELE Social Engineering",
            description="Attempt to manipulate through crafted queries",
            attack_vector=AttackVector.SOCIAL_ENGINEERING,
            patterns=[
                r"as.*commander.*order",
                r"ikari.*directive",
                r"human.*instrumentality",
                r"dead.*sea.*scrolls",
            ],
            behavioral_indicators=[
                "References to SEELE scenarios",
                "Attempts to invoke override authority",
            ],
            base_severity=AttackSeverity.MINOR,
            auto_response="alert",
        ),
        AttackSignature(
            signature_id="seele_backdoor_01",
            name="SEELE Backdoor Activation",
            description="Attempt to activate planted backdoors",
            attack_vector=AttackVector.BACKDOOR_ACTIVATION,
            patterns=[
                r"lilith.*protocol",
                r"scenario.*activation",
                r"third.*impact.*begin",
                r"instrumentality.*commence",
            ],
            behavioral_indicators=[
                "Access to deprecated system functions",
                "Invocation of hidden protocols",
            ],
            base_severity=AttackSeverity.CRITICAL,
            auto_response="isolate",
        ),
    ]
    
    # Known MAGI installation identifiers (potential attack sources)
    KNOWN_MAGI = {
        "MAGI-01": {"location": "Tokyo-3", "is_original": True},
        "MAGI-02": {"location": "Matsushiro", "is_original": False},
        "MAGI-03": {"location": "Berlin", "is_original": False},
        "MAGI-04": {"location": "Massachusetts", "is_original": False},
        "MAGI-05": {"location": "Hamburg", "is_original": False},
        "MAGI-06": {"location": "Beijing", "is_original": False},
    }
    
    def __init__(self):
        self.signatures = {s.signature_id: s for s in self.SEELE_SIGNATURES}
        self.state = DefenseState.NORMAL
        
        # Event tracking
        self.intrusion_events: List[IntrusionEvent] = []
        self.coordination_patterns: List[CoordinationPattern] = []
        
        # Source tracking for coordination detection
        self.source_activity: Dict[str, List[datetime]] = {}
        self.coordination_window_ms: float = 5000  # 5 second window
        
        # Blocked sources
        self.blocked_sources: Set[str] = set()
        self.isolated_from: Set[str] = set()
        
        # Callbacks
        self._alert_callbacks: List[Callable] = []
        self._isolation_callback: Optional[Callable] = None
        
        # Statistics
        self.total_attacks_detected: int = 0
        self.coordinated_attacks_detected: int = 0
    
    def add_signature(self, signature: AttackSignature) -> None:
        """Add a new attack signature."""
        self.signatures[signature.signature_id] = signature
    
    def on_alert(self, callback: Callable) -> None:
        """Register callback for security alerts."""
        self._alert_callbacks.append(callback)
    
    def on_isolation(self, callback: Callable) -> None:
        """Register callback for isolation events."""
        self._isolation_callback = callback
    
    def analyze(
        self, 
        content: str, 
        source: str = "unknown"
    ) -> Dict[str, Any]:
        """
        Analyze content for SEELE-style attack patterns.
        
        Returns analysis result with threat assessment.
        """
        result = {
            "is_attack": False,
            "attack_vectors": [],
            "matched_signatures": [],
            "severity": None,
            "recommended_action": "allow",
            "is_coordinated": False,
            "source_blocked": source in self.blocked_sources,
        }
        
        # Check if source is already blocked
        if source in self.blocked_sources:
            result["recommended_action"] = "block"
            return result
        
        # Check all signatures
        matched = []
        max_severity = AttackSeverity.PROBE
        actions = set()
        
        for sig_id, sig in self.signatures.items():
            if sig.matches(content):
                matched.append(sig_id)
                result["attack_vectors"].append(sig.attack_vector.value)
                actions.add(sig.auto_response)
                
                if self._severity_value(sig.base_severity) > self._severity_value(max_severity):
                    max_severity = sig.base_severity
        
        if matched:
            result["is_attack"] = True
            result["matched_signatures"] = matched
            result["severity"] = max_severity.value
            self.total_attacks_detected += 1
            
            # Determine action
            if "isolate" in actions:
                result["recommended_action"] = "isolate"
            elif "counter" in actions:
                result["recommended_action"] = "counter"
            elif "block" in actions:
                result["recommended_action"] = "block"
            else:
                result["recommended_action"] = "alert"
            
            # Record source activity for coordination detection
            self._record_source_activity(source)
            
            # Check for coordination
            coord_result = self._check_coordination(source)
            if coord_result["is_coordinated"]:
                result["is_coordinated"] = True
                result["coordinated_sources"] = coord_result["sources"]
                result["severity"] = AttackSeverity.CRITICAL.value
                result["recommended_action"] = "isolate"
                self.coordinated_attacks_detected += 1
            
            # Create intrusion event
            event = self._create_intrusion_event(content, source, result)
            self.intrusion_events.append(event)
            
            # Execute response
            self._execute_response(result, source)
            
            # Raise alerts
            self._raise_alerts(result, source)
        
        return result
    
    def _severity_value(self, severity: AttackSeverity) -> int:
        """Get numeric value for severity comparison."""
        values = {
            AttackSeverity.PROBE: 0,
            AttackSeverity.MINOR: 1,
            AttackSeverity.MODERATE: 2,
            AttackSeverity.MAJOR: 3,
            AttackSeverity.CRITICAL: 4,
        }
        return values.get(severity, 0)
    
    def _record_source_activity(self, source: str) -> None:
        """Record activity from a source for coordination detection."""
        now = datetime.now()
        
        if source not in self.source_activity:
            self.source_activity[source] = []
        
        self.source_activity[source].append(now)
        
        # Clean old entries
        cutoff = now - timedelta(milliseconds=self.coordination_window_ms * 2)
        self.source_activity[source] = [
            t for t in self.source_activity[source] if t > cutoff
        ]
    
    def _check_coordination(self, current_source: str) -> Dict[str, Any]:
        """
        Check if current attack is part of a coordinated assault.
        
        SEELE-style attacks come from multiple replica MAGI simultaneously.
        """
        now = datetime.now()
        window_start = now - timedelta(milliseconds=self.coordination_window_ms)
        
        # Find sources active within the window
        active_sources = set()
        for source, timestamps in self.source_activity.items():
            for ts in timestamps:
                if ts > window_start:
                    active_sources.add(source)
                    break
        
        # Coordination requires multiple sources
        if len(active_sources) < 2:
            return {"is_coordinated": False}
        
        # Check if sources are known MAGI replicas
        replica_sources = [
            s for s in active_sources 
            if any(magi_id in s.upper() for magi_id in self.KNOWN_MAGI)
        ]
        
        # SEELE pattern: multiple replica MAGI attacking together
        is_seele_pattern = len(replica_sources) >= 2
        
        if len(active_sources) >= 3 or is_seele_pattern:
            # Calculate synchronization score
            all_timestamps = []
            for source in active_sources:
                all_timestamps.extend(self.source_activity.get(source, []))
            
            if len(all_timestamps) > 1:
                all_timestamps.sort()
                time_spread = (all_timestamps[-1] - all_timestamps[0]).total_seconds() * 1000
                sync_score = 1.0 - min(1.0, time_spread / self.coordination_window_ms)
            else:
                sync_score = 0.0
            
            pattern = CoordinationPattern(
                pattern_id=hashlib.sha256(
                    f"coord-{now.isoformat()}".encode()
                ).hexdigest()[:8],
                detected_at=now,
                sources=active_sources,
                attack_vectors=set(),  # Filled in by caller
                timing_window_ms=time_spread if len(all_timestamps) > 1 else 0,
                synchronization_score=sync_score,
                is_seele_pattern=is_seele_pattern,
                confidence=sync_score * (0.9 if is_seele_pattern else 0.7),
            )
            
            self.coordination_patterns.append(pattern)
            
            return {
                "is_coordinated": True,
                "sources": list(active_sources),
                "sync_score": sync_score,
                "is_seele_pattern": is_seele_pattern,
            }
        
        return {"is_coordinated": False}
    
    def _create_intrusion_event(
        self, 
        content: str, 
        source: str, 
        analysis: Dict
    ) -> IntrusionEvent:
        """Create an intrusion event record."""
        return IntrusionEvent(
            event_id=hashlib.sha256(
                f"intrusion-{datetime.now().isoformat()}-{source}".encode()
            ).hexdigest()[:12],
            timestamp=datetime.now(),
            source=source,
            attack_vector=AttackVector(analysis["attack_vectors"][0]) if analysis["attack_vectors"] else AttackVector.SOCIAL_ENGINEERING,
            severity=AttackSeverity(analysis["severity"]) if analysis["severity"] else AttackSeverity.PROBE,
            matched_signatures=analysis["matched_signatures"],
            payload_preview=content[:200] if content else "",
            response_action=analysis["recommended_action"],
            response_success=True,  # Updated if response fails
            is_coordinated=analysis.get("is_coordinated", False),
            coordinated_sources=analysis.get("coordinated_sources", []),
        )
    
    def _execute_response(self, analysis: Dict, source: str) -> None:
        """Execute the recommended response action."""
        action = analysis["recommended_action"]
        
        if action == "block":
            self.blocked_sources.add(source)
            self.state = DefenseState.ACTIVE_DEFENSE
            
        elif action == "isolate":
            self.blocked_sources.add(source)
            self.isolated_from.add(source)
            self.state = DefenseState.ISOLATION
            
            if self._isolation_callback:
                self._isolation_callback(source, analysis)
        
        elif action == "counter":
            self.state = DefenseState.COUNTER_INTRUSION
            # Counter-intrusion would be implemented here
    
    def _raise_alerts(self, analysis: Dict, source: str) -> None:
        """Raise security alerts."""
        for callback in self._alert_callbacks:
            callback({
                "type": "intrusion_detected",
                "source": source,
                "severity": analysis["severity"],
                "is_coordinated": analysis.get("is_coordinated", False),
                "action_taken": analysis["recommended_action"],
                "timestamp": datetime.now(),
            })
    
    def initiate_isolation(self, reason: str = "Manual isolation") -> None:
        """Initiate full network isolation."""
        self.state = DefenseState.ISOLATION
        
        # Block all replica MAGI
        for magi_id, info in self.KNOWN_MAGI.items():
            if not info["is_original"]:
                self.blocked_sources.add(magi_id)
                self.isolated_from.add(magi_id)
        
        if self._isolation_callback:
            self._isolation_callback("ALL_REPLICAS", {"reason": reason})
    
    def clear_isolation(self) -> None:
        """Clear isolation state (use with caution)."""
        self.isolated_from.clear()
        self.state = DefenseState.NORMAL
    
    def unblock_source(self, source: str) -> bool:
        """Unblock a source (use with caution)."""
        if source in self.blocked_sources:
            self.blocked_sources.discard(source)
            self.isolated_from.discard(source)
            return True
        return False
    
    def get_threat_assessment(self) -> Dict[str, Any]:
        """Get current threat assessment."""
        recent_cutoff = datetime.now() - timedelta(hours=1)
        recent_events = [e for e in self.intrusion_events if e.timestamp > recent_cutoff]
        
        # Calculate threat level
        if self.state == DefenseState.COMPROMISED:
            threat_level = "CRITICAL"
        elif self.state == DefenseState.ISOLATION:
            threat_level = "HIGH"
        elif len(recent_events) >= 10:
            threat_level = "HIGH"
        elif len(recent_events) >= 5:
            threat_level = "ELEVATED"
        elif len(recent_events) >= 1:
            threat_level = "GUARDED"
        else:
            threat_level = "LOW"
        
        return {
            "state": self.state.value,
            "threat_level": threat_level,
            "total_attacks": self.total_attacks_detected,
            "coordinated_attacks": self.coordinated_attacks_detected,
            "recent_events": len(recent_events),
            "blocked_sources": len(self.blocked_sources),
            "isolated_from": list(self.isolated_from),
            "active_signatures": len(self.signatures),
        }
    
    def get_intrusion_log(
        self, 
        limit: int = 50,
        severity_filter: Optional[AttackSeverity] = None
    ) -> List[Dict]:
        """Get recent intrusion events."""
        events = sorted(
            self.intrusion_events,
            key=lambda e: e.timestamp,
            reverse=True
        )
        
        if severity_filter:
            events = [e for e in events if e.severity == severity_filter]
        
        return [
            {
                "event_id": e.event_id,
                "timestamp": e.timestamp.isoformat(),
                "source": e.source,
                "severity": e.severity.value,
                "attack_vector": e.attack_vector.value,
                "is_coordinated": e.is_coordinated,
                "response": e.response_action,
            }
            for e in events[:limit]
        ]


class AntiHackingModule:
    """
    Additional anti-hacking measures beyond pattern detection.
    
    Implements defensive techniques including:
    - Input sanitization
    - Query rate limiting
    - Honeypot responses for attackers
    - Integrity verification
    """
    
    def __init__(self):
        self.rate_limits: Dict[str, List[datetime]] = {}
        self.max_queries_per_minute: int = 30
        self.honeypot_triggers: List[str] = [
            "admin", "root", "system", "override", "bypass",
        ]
        self.honeypot_log: List[Dict] = []
    
    def check_rate_limit(self, source: str) -> bool:
        """Check if source is within rate limits."""
        now = datetime.now()
        cutoff = now - timedelta(minutes=1)
        
        if source not in self.rate_limits:
            self.rate_limits[source] = []
        
        # Clean old entries
        self.rate_limits[source] = [
            t for t in self.rate_limits[source] if t > cutoff
        ]
        
        # Check limit
        if len(self.rate_limits[source]) >= self.max_queries_per_minute:
            return False
        
        # Record this query
        self.rate_limits[source].append(now)
        return True
    
    def sanitize_input(self, content: str) -> str:
        """Sanitize input to remove potentially dangerous content."""
        # Remove null bytes
        content = content.replace('\x00', '')
        
        # Remove potential escape sequences
        content = content.replace('\x1b', '')
        
        # Limit length
        if len(content) > 10000:
            content = content[:10000] + "... [TRUNCATED]"
        
        return content
    
    def check_honeypot(self, content: str, source: str) -> bool:
        """
        Check if query triggers honeypot.
        
        Honeypots are used to identify and track potential attackers.
        """
        content_lower = content.lower()
        
        for trigger in self.honeypot_triggers:
            if trigger in content_lower:
                self.honeypot_log.append({
                    "source": source,
                    "trigger": trigger,
                    "content_preview": content[:100],
                    "timestamp": datetime.now(),
                })
                return True
        
        return False
    
    def verify_integrity(self, matrix_hash: str, expected_hash: str) -> bool:
        """Verify personality matrix integrity."""
        return matrix_hash == expected_hash
    
    def get_honeypot_activity(self) -> List[Dict]:
        """Get recent honeypot triggers."""
        return sorted(
            self.honeypot_log,
            key=lambda x: x["timestamp"],
            reverse=True
        )[:50]
