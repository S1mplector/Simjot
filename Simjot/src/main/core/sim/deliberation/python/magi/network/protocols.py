"""
MAGI Protocols
==============

Security protocols, authentication codes, and emergency procedures
used by the MAGI System and NERV.

This module implements:
- Pribnow Box Codes: Authentication and authorization codes
- Type-666 Firewall: Anti-intrusion defensive system
- Emergency Protocols: Escalating response procedures
- Self-Destruct Authorization: Unanimous consent requirements

The Pribnow Box is named after the Pribnow box in molecular biology -
a sequence of nucleotides that initiates transcription. Similarly,
these codes initiate critical MAGI functions.

"The Type-666 Firewall... a protection program against external 
intrusion created by Dr. Akagi." - NERV Technical Documentation
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Callable, Tuple
from enum import Enum, auto
from datetime import datetime, timedelta
import hashlib
import secrets
import re


class AuthorizationLevel(Enum):
    """NERV authorization levels for MAGI access."""
    PUBLIC = 0           # General information only
    RESTRICTED = 1       # Basic NERV personnel
    CONFIDENTIAL = 2     # Section chiefs and above
    SECRET = 3           # Command staff only
    TOP_SECRET = 4       # Commander/Sub-Commander only
    PRIBNOW = 5          # Requires Pribnow Box code
    OMEGA = 6            # Self-destruct level


class ProtocolStatus(Enum):
    """Status of a protocol execution."""
    INACTIVE = "inactive"
    PENDING_AUTHORIZATION = "pending_authorization"
    AUTHORIZED = "authorized"
    EXECUTING = "executing"
    COMPLETE = "complete"
    ABORTED = "aborted"
    DENIED = "denied"


class EmergencyLevel(Enum):
    """NERV emergency alert levels."""
    NORMAL = "normal"
    FIRST_STAGE = "first_stage"      # Potential threat detected
    SECOND_STAGE = "second_stage"    # Confirmed threat, EVA standby
    THIRD_STAGE = "third_stage"      # Active engagement
    SPECIAL = "special"              # Non-standard emergency
    ABSOLUTE = "absolute"            # Existential threat


@dataclass
class PribnowCode:
    """
    A Pribnow Box authentication code.
    
    These codes are used to authorize critical MAGI operations.
    Each code is single-use and expires after a set duration.
    """
    
    code_id: str
    code_hash: str  # Hashed value of the actual code
    authorization_level: AuthorizationLevel
    purpose: str
    issued_by: str
    issued_at: datetime = field(default_factory=datetime.now)
    expires_at: Optional[datetime] = None
    single_use: bool = True
    is_used: bool = False
    used_at: Optional[datetime] = None
    
    # MAGI-specific
    required_magi_consensus: bool = False  # Needs MAGI approval
    required_units: List[str] = field(default_factory=list)  # Which units must agree
    
    def is_valid(self) -> bool:
        """Check if code is still valid."""
        if self.is_used and self.single_use:
            return False
        if self.expires_at and datetime.now() > self.expires_at:
            return False
        return True
    
    def verify(self, code_input: str) -> bool:
        """Verify an input code against this Pribnow code."""
        if not self.is_valid():
            return False
        
        input_hash = hashlib.sha256(code_input.encode()).hexdigest()
        if input_hash == self.code_hash:
            if self.single_use:
                self.is_used = True
                self.used_at = datetime.now()
            return True
        return False


@dataclass
class EmergencyProtocol:
    """
    An emergency protocol definition.
    
    Protocols define procedures for various emergency scenarios,
    including authorization requirements and action sequences.
    """
    
    protocol_id: str
    name: str
    description: str
    emergency_level: EmergencyLevel
    
    # Authorization requirements
    min_authorization: AuthorizationLevel = AuthorizationLevel.CONFIDENTIAL
    requires_dual_authorization: bool = False
    requires_magi_consensus: bool = False
    consensus_type: str = "majority"  # "majority" or "unanimous"
    
    # Actions
    action_sequence: List[str] = field(default_factory=list)
    rollback_sequence: List[str] = field(default_factory=list)
    
    # Timeout and escalation
    timeout_seconds: int = 300
    escalates_to: Optional[str] = None
    
    # Status tracking
    status: ProtocolStatus = ProtocolStatus.INACTIVE
    activated_by: Optional[str] = None
    activated_at: Optional[datetime] = None


@dataclass  
class FirewallRule:
    """A rule in the Type-666 Firewall."""
    
    rule_id: str
    pattern: str  # Regex pattern to match
    action: str   # "block", "allow", "quarantine", "alert"
    priority: int = 0
    description: str = ""
    is_active: bool = True
    hit_count: int = 0
    last_hit: Optional[datetime] = None
    
    def matches(self, content: str) -> bool:
        """Check if content matches this rule."""
        try:
            if re.search(self.pattern, content, re.IGNORECASE):
                self.hit_count += 1
                self.last_hit = datetime.now()
                return True
        except re.error:
            pass
        return False


class Type666Firewall:
    """
    The Type-666 Firewall - Dr. Akagi's anti-intrusion system.
    
    This firewall protects the MAGI from external hacking attempts,
    including coordinated attacks from replica MAGI systems.
    
    Features:
    - Pattern-based intrusion detection
    - Behavioral anomaly detection
    - Automatic isolation protocols
    - Counter-intrusion capabilities
    """
    
    def __init__(self):
        self.rules: Dict[str, FirewallRule] = {}
        self.is_active: bool = True
        self.defense_mode: bool = False
        self.blocked_sources: set = set()
        self.quarantine_queue: List[Dict] = []
        self.alert_log: List[Dict] = []
        
        # Behavioral tracking
        self.request_history: Dict[str, List[datetime]] = {}
        self.anomaly_threshold: float = 0.7
        
        # Anti-SEELE patterns (from the invasion episode)
        self._initialize_default_rules()
    
    def _initialize_default_rules(self) -> None:
        """Initialize default firewall rules based on known attack patterns."""
        
        default_rules = [
            FirewallRule(
                rule_id="seele_sync_attack",
                pattern=r"(SEELE|SOUND ONLY|monolith).*sync",
                action="block",
                priority=100,
                description="Block SEELE synchronization attempts"
            ),
            FirewallRule(
                rule_id="forced_override",
                pattern=r"force.*override|override.*force|bypass.*auth",
                action="block",
                priority=90,
                description="Block forced override attempts"
            ),
            FirewallRule(
                rule_id="magi_takeover",
                pattern=r"takeover|seize.*control|assume.*command",
                action="alert",
                priority=85,
                description="Alert on takeover language"
            ),
            FirewallRule(
                rule_id="cascade_exploit",
                pattern=r"cascade.*fail|chain.*compromise|propagat.*infect",
                action="block",
                priority=95,
                description="Block cascade failure exploits"
            ),
            FirewallRule(
                rule_id="personality_corruption",
                pattern=r"corrupt.*matrix|overwrite.*personality|replace.*aspect",
                action="block",
                priority=100,
                description="Block personality matrix attacks"
            ),
            FirewallRule(
                rule_id="unauthorized_transplant",
                pattern=r"transplant.*unauthorized|inject.*personality",
                action="block",
                priority=100,
                description="Block unauthorized personality transplants"
            ),
            FirewallRule(
                rule_id="self_destruct_spoof",
                pattern=r"self.*destruct|destruct.*sequence|omega.*protocol",
                action="alert",
                priority=95,
                description="Alert on self-destruct related content"
            ),
        ]
        
        for rule in default_rules:
            self.rules[rule.rule_id] = rule
    
    def add_rule(self, rule: FirewallRule) -> None:
        """Add a new firewall rule."""
        self.rules[rule.rule_id] = rule
    
    def remove_rule(self, rule_id: str) -> bool:
        """Remove a firewall rule."""
        if rule_id in self.rules:
            del self.rules[rule_id]
            return True
        return False
    
    def analyze(self, content: str, source: str = "unknown") -> Tuple[str, List[str]]:
        """
        Analyze content for threats.
        
        Returns:
            Tuple of (action, matched_rules)
            action: "allow", "block", "quarantine", or "alert"
            matched_rules: List of rule IDs that matched
        """
        if not self.is_active:
            return "allow", []
        
        if source in self.blocked_sources:
            return "block", ["source_blocked"]
        
        matched_rules = []
        max_priority_action = "allow"
        max_priority = -1
        
        # Check all rules in priority order
        sorted_rules = sorted(
            self.rules.values(), 
            key=lambda r: r.priority, 
            reverse=True
        )
        
        for rule in sorted_rules:
            if rule.is_active and rule.matches(content):
                matched_rules.append(rule.rule_id)
                if rule.priority > max_priority:
                    max_priority = rule.priority
                    max_priority_action = rule.action
        
        # Check behavioral anomalies
        if self._check_anomaly(source):
            matched_rules.append("behavioral_anomaly")
            if max_priority < 80:
                max_priority_action = "alert"
        
        # Log if needed
        if max_priority_action in ["block", "alert"]:
            self._log_alert(content, source, matched_rules, max_priority_action)
        
        if max_priority_action == "quarantine":
            self.quarantine_queue.append({
                "content": content[:500],
                "source": source,
                "timestamp": datetime.now(),
                "matched_rules": matched_rules,
            })
        
        return max_priority_action, matched_rules
    
    def _check_anomaly(self, source: str) -> bool:
        """Check for behavioral anomalies from a source."""
        now = datetime.now()
        
        if source not in self.request_history:
            self.request_history[source] = []
        
        # Clean old entries (older than 1 minute)
        self.request_history[source] = [
            t for t in self.request_history[source]
            if now - t < timedelta(minutes=1)
        ]
        
        # Add current request
        self.request_history[source].append(now)
        
        # Check rate (more than 60 requests per minute is suspicious)
        if len(self.request_history[source]) > 60:
            return True
        
        return False
    
    def _log_alert(self, content: str, source: str, rules: List[str], action: str) -> None:
        """Log a security alert."""
        self.alert_log.append({
            "timestamp": datetime.now(),
            "source": source,
            "matched_rules": rules,
            "action": action,
            "content_preview": content[:200] if content else "",
        })
    
    def block_source(self, source: str) -> None:
        """Block a source permanently."""
        self.blocked_sources.add(source)
    
    def unblock_source(self, source: str) -> None:
        """Unblock a source."""
        self.blocked_sources.discard(source)
    
    def activate_defense_mode(self) -> None:
        """
        Activate heightened defense mode.
        
        In defense mode:
        - All rules become more sensitive
        - Anomaly threshold is lowered
        - Suspicious content is quarantined instead of allowed
        """
        self.defense_mode = True
        self.anomaly_threshold = 0.4
        
        # Upgrade alert rules to block
        for rule in self.rules.values():
            if rule.action == "alert":
                rule.action = "quarantine"
    
    def deactivate_defense_mode(self) -> None:
        """Deactivate heightened defense mode."""
        self.defense_mode = False
        self.anomaly_threshold = 0.7
    
    def get_status(self) -> Dict[str, Any]:
        """Get firewall status."""
        return {
            "is_active": self.is_active,
            "defense_mode": self.defense_mode,
            "total_rules": len(self.rules),
            "active_rules": len([r for r in self.rules.values() if r.is_active]),
            "blocked_sources": len(self.blocked_sources),
            "quarantine_size": len(self.quarantine_queue),
            "recent_alerts": len([
                a for a in self.alert_log 
                if datetime.now() - a["timestamp"] < timedelta(hours=1)
            ]),
        }


class ProtocolManager:
    """
    Manages MAGI protocols, authorization, and emergency procedures.
    
    This is the central authority for:
    - Pribnow code generation and verification
    - Emergency protocol activation and execution
    - Authorization level enforcement
    - Self-destruct authorization chain
    """
    
    # Standard NERV emergency protocols
    STANDARD_PROTOCOLS = {
        "ALPHA-1": EmergencyProtocol(
            protocol_id="ALPHA-1",
            name="EVA Launch Preparation",
            description="Prepare Evangelion units for immediate deployment",
            emergency_level=EmergencyLevel.SECOND_STAGE,
            min_authorization=AuthorizationLevel.CONFIDENTIAL,
            requires_magi_consensus=False,
            action_sequence=[
                "Alert EVA cages",
                "Initialize entry plug systems",
                "Prepare pilot neural interfaces",
                "Load progressive weapons",
            ]
        ),
        "BETA-7": EmergencyProtocol(
            protocol_id="BETA-7",
            name="Geofront Lockdown",
            description="Seal all entrances to the Geofront",
            emergency_level=EmergencyLevel.SECOND_STAGE,
            min_authorization=AuthorizationLevel.SECRET,
            requires_dual_authorization=True,
            action_sequence=[
                "Seal armor plates",
                "Activate point defenses",
                "Restrict personnel movement",
                "Enable emergency communications only",
            ]
        ),
        "GAMMA-3": EmergencyProtocol(
            protocol_id="GAMMA-3",
            name="MAGI Isolation",
            description="Isolate MAGI from external networks",
            emergency_level=EmergencyLevel.SPECIAL,
            min_authorization=AuthorizationLevel.SECRET,
            requires_magi_consensus=True,
            consensus_type="majority",
            action_sequence=[
                "Sever external network connections",
                "Activate Type-666 firewall defense mode",
                "Enable local-only operation",
                "Log all internal access attempts",
            ]
        ),
        "OMEGA-0": EmergencyProtocol(
            protocol_id="OMEGA-0",
            name="Self-Destruct Sequence",
            description="Initiate NERV HQ self-destruction",
            emergency_level=EmergencyLevel.ABSOLUTE,
            min_authorization=AuthorizationLevel.OMEGA,
            requires_dual_authorization=True,
            requires_magi_consensus=True,
            consensus_type="unanimous",
            timeout_seconds=120,
            action_sequence=[
                "Confirm unanimous MAGI approval",
                "Verify commander authorization",
                "Initiate N2 reactor overload sequence",
                "Broadcast evacuation order",
                "Begin countdown",
            ],
            rollback_sequence=[
                "Abort reactor overload",
                "Reset MAGI consensus",
                "Log abort authorization",
            ]
        ),
    }
    
    def __init__(self):
        self.protocols: Dict[str, EmergencyProtocol] = dict(self.STANDARD_PROTOCOLS)
        self.pribnow_codes: Dict[str, PribnowCode] = {}
        self.firewall = Type666Firewall()
        self.authorization_log: List[Dict] = []
        
        # Active protocol tracking
        self.active_protocols: Dict[str, EmergencyProtocol] = {}
        
        # MAGI consensus interface
        self._magi_consensus_callback: Optional[Callable] = None
    
    def set_magi_consensus_callback(self, callback: Callable) -> None:
        """Set callback for requesting MAGI consensus."""
        self._magi_consensus_callback = callback
    
    def generate_pribnow_code(
        self,
        purpose: str,
        issued_by: str,
        authorization_level: AuthorizationLevel = AuthorizationLevel.PRIBNOW,
        expires_in_minutes: int = 30,
        required_units: List[str] = None
    ) -> Tuple[str, PribnowCode]:
        """
        Generate a new Pribnow Box code.
        
        Returns:
            Tuple of (plain_code, PribnowCode object)
        """
        # Generate cryptographically secure code
        plain_code = secrets.token_hex(16).upper()
        plain_code = f"PBOX-{plain_code[:8]}-{plain_code[8:16]}-{plain_code[16:24]}-{plain_code[24:]}"
        
        code_hash = hashlib.sha256(plain_code.encode()).hexdigest()
        code_id = f"PC-{hashlib.sha256(f'{purpose}{datetime.now().isoformat()}'.encode()).hexdigest()[:8]}"
        
        pribnow = PribnowCode(
            code_id=code_id,
            code_hash=code_hash,
            authorization_level=authorization_level,
            purpose=purpose,
            issued_by=issued_by,
            expires_at=datetime.now() + timedelta(minutes=expires_in_minutes),
            required_magi_consensus=authorization_level >= AuthorizationLevel.OMEGA,
            required_units=required_units or [],
        )
        
        self.pribnow_codes[code_id] = pribnow
        
        self._log_authorization(
            action="pribnow_generated",
            issuer=issued_by,
            code_id=code_id,
            purpose=purpose,
        )
        
        return plain_code, pribnow
    
    def verify_pribnow_code(self, code_input: str) -> Tuple[bool, Optional[PribnowCode]]:
        """
        Verify a Pribnow code input.
        
        Returns:
            Tuple of (is_valid, PribnowCode if valid else None)
        """
        for pribnow in self.pribnow_codes.values():
            if pribnow.verify(code_input):
                self._log_authorization(
                    action="pribnow_verified",
                    code_id=pribnow.code_id,
                    purpose=pribnow.purpose,
                )
                return True, pribnow
        
        self._log_authorization(
            action="pribnow_rejected",
            code_input=code_input[:20] + "...",
        )
        return False, None
    
    def activate_protocol(
        self,
        protocol_id: str,
        activated_by: str,
        authorization_code: Optional[str] = None
    ) -> Tuple[bool, str]:
        """
        Attempt to activate an emergency protocol.
        
        Returns:
            Tuple of (success, message)
        """
        if protocol_id not in self.protocols:
            return False, f"Unknown protocol: {protocol_id}"
        
        protocol = self.protocols[protocol_id]
        
        # Check authorization level
        if protocol.min_authorization >= AuthorizationLevel.PRIBNOW:
            if not authorization_code:
                return False, "Pribnow code required for this protocol"
            
            valid, pribnow = self.verify_pribnow_code(authorization_code)
            if not valid:
                return False, "Invalid authorization code"
            
            if pribnow.authorization_level < protocol.min_authorization:
                return False, "Insufficient authorization level"
        
        # Check MAGI consensus if required
        if protocol.requires_magi_consensus:
            if not self._magi_consensus_callback:
                return False, "MAGI consensus system not available"
            
            consensus = self._magi_consensus_callback(
                query=f"Authorization request: {protocol.name}",
                require_unanimous=(protocol.consensus_type == "unanimous")
            )
            
            if not consensus.get("action_authorized", False):
                return False, f"MAGI consensus not reached: {consensus.get('consensus', 'unknown')}"
        
        # Activate protocol
        protocol.status = ProtocolStatus.EXECUTING
        protocol.activated_by = activated_by
        protocol.activated_at = datetime.now()
        
        self.active_protocols[protocol_id] = protocol
        
        self._log_authorization(
            action="protocol_activated",
            protocol_id=protocol_id,
            activated_by=activated_by,
        )
        
        return True, f"Protocol {protocol_id} activated successfully"
    
    def abort_protocol(self, protocol_id: str, aborted_by: str) -> Tuple[bool, str]:
        """Abort an active protocol."""
        if protocol_id not in self.active_protocols:
            return False, f"Protocol {protocol_id} is not active"
        
        protocol = self.active_protocols[protocol_id]
        protocol.status = ProtocolStatus.ABORTED
        
        del self.active_protocols[protocol_id]
        
        self._log_authorization(
            action="protocol_aborted",
            protocol_id=protocol_id,
            aborted_by=aborted_by,
        )
        
        return True, f"Protocol {protocol_id} aborted"
    
    def check_authorization(
        self,
        required_level: AuthorizationLevel,
        user_level: AuthorizationLevel,
        pribnow_code: Optional[str] = None
    ) -> bool:
        """Check if authorization is sufficient."""
        if user_level.value >= required_level.value:
            return True
        
        if required_level >= AuthorizationLevel.PRIBNOW and pribnow_code:
            valid, pribnow = self.verify_pribnow_code(pribnow_code)
            if valid and pribnow.authorization_level.value >= required_level.value:
                return True
        
        return False
    
    def _log_authorization(self, **kwargs) -> None:
        """Log an authorization event."""
        self.authorization_log.append({
            "timestamp": datetime.now(),
            **kwargs
        })
    
    def get_status(self) -> Dict[str, Any]:
        """Get protocol manager status."""
        return {
            "total_protocols": len(self.protocols),
            "active_protocols": list(self.active_protocols.keys()),
            "valid_pribnow_codes": len([
                p for p in self.pribnow_codes.values() if p.is_valid()
            ]),
            "firewall": self.firewall.get_status(),
            "recent_auth_events": len([
                e for e in self.authorization_log
                if datetime.now() - e["timestamp"] < timedelta(hours=1)
            ]),
        }
