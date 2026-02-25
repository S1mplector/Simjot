"""
MAGI Network
============

The global network connecting MAGI installations worldwide.

Known installations:
- MAGI 01: Tokyo-3, Japan (Original - NERV HQ)
- MAGI 02: Matsushiro, Japan
- MAGI 03: Berlin, Germany
- MAGI 04: Massachusetts, USA
- MAGI 05: Hamburg, Germany
- MAGI 06: Beijing, China

The network allows MAGI systems to:
- Share computational load
- Coordinate on global decisions
- Provide redundancy for critical operations
- Execute coordinated attacks (as seen in the SEELE invasion)

WARNING: Network connectivity introduces vulnerability to coordinated
cyber attacks, as demonstrated by SEELE's use of replica MAGI to
attack the original Tokyo-3 system.
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Callable
from enum import Enum
from datetime import datetime
import hashlib


class ConnectionStatus(Enum):
    """Status of connection to a MAGI node."""
    DISCONNECTED = "disconnected"
    CONNECTING = "connecting"
    CONNECTED = "connected"
    AUTHENTICATED = "authenticated"
    DEGRADED = "degraded"
    HOSTILE = "hostile"  # Connection is compromised or attacking


class ThreatLevel(Enum):
    """Threat level assessment for network activity."""
    NONE = "none"
    LOW = "low"
    MODERATE = "moderate"
    HIGH = "high"
    CRITICAL = "critical"
    ACTIVE_ATTACK = "active_attack"


@dataclass
class NetworkNode:
    """A node in the MAGI network representing one installation."""
    
    installation_id: str  # e.g., "MAGI-02"
    location: str         # e.g., "Matsushiro, Japan"
    ip_address: str       # Network address
    
    # Status
    status: ConnectionStatus = ConnectionStatus.DISCONNECTED
    last_contact: Optional[datetime] = None
    latency_ms: float = 0.0
    
    # Trust
    trust_level: float = 1.0  # 0.0 (untrusted) to 1.0 (fully trusted)
    is_verified: bool = False
    authentication_key: Optional[str] = None
    
    # Threat assessment
    threat_level: ThreatLevel = ThreatLevel.NONE
    suspicious_activity_count: int = 0
    
    # Capabilities
    is_replica: bool = True  # Original MAGI-01 is False
    processing_capacity: float = 1.0
    
    def authenticate(self, key: str) -> bool:
        """Attempt to authenticate with this node."""
        if self.authentication_key and key == self.authentication_key:
            self.is_verified = True
            self.status = ConnectionStatus.AUTHENTICATED
            return True
        return False
    
    def report_suspicious_activity(self) -> None:
        """Report suspicious activity from this node."""
        self.suspicious_activity_count += 1
        
        if self.suspicious_activity_count >= 5:
            self.threat_level = ThreatLevel.HIGH
            self.trust_level = max(0.0, self.trust_level - 0.2)
        elif self.suspicious_activity_count >= 3:
            self.threat_level = ThreatLevel.MODERATE
            self.trust_level = max(0.0, self.trust_level - 0.1)
        elif self.suspicious_activity_count >= 1:
            self.threat_level = ThreatLevel.LOW
    
    def mark_hostile(self) -> None:
        """Mark this node as hostile (attacking)."""
        self.status = ConnectionStatus.HOSTILE
        self.threat_level = ThreatLevel.ACTIVE_ATTACK
        self.trust_level = 0.0


@dataclass
class NetworkMessage:
    """A message transmitted across the MAGI network."""
    
    message_id: str
    source_node: str
    target_node: str
    message_type: str  # "query", "response", "vote", "sync", "alert"
    payload: Dict[str, Any]
    timestamp: datetime = field(default_factory=datetime.now)
    is_encrypted: bool = True
    priority: int = 0  # Higher = more urgent
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "message_id": self.message_id,
            "source": self.source_node,
            "target": self.target_node,
            "type": self.message_type,
            "payload": self.payload,
            "timestamp": self.timestamp.isoformat(),
            "priority": self.priority,
        }


class IntrusionDetector:
    """
    Detects and responds to network intrusion attempts.
    
    Inspired by Ritsuko's defense against SEELE's coordinated
    attack using the replica MAGI systems.
    """
    
    def __init__(self):
        self.anomaly_threshold: float = 0.7
        self.attack_signatures: List[str] = []
        self.blocked_nodes: set = set()
        self.alert_callbacks: List[Callable] = []
    
    def analyze_message(self, message: NetworkMessage, source: NetworkNode) -> ThreatLevel:
        """Analyze an incoming message for threats."""
        
        threat_score = 0.0
        
        # Check if source is already blocked
        if source.installation_id in self.blocked_nodes:
            return ThreatLevel.ACTIVE_ATTACK
        
        # Check trust level
        if source.trust_level < 0.3:
            threat_score += 0.4
        
        # Check for known attack signatures
        payload_str = str(message.payload)
        for signature in self.attack_signatures:
            if signature in payload_str:
                threat_score += 0.5
        
        # Check for anomalous patterns
        if message.message_type == "sync" and not source.is_verified:
            threat_score += 0.3
        
        # Determine threat level
        if threat_score >= 0.8:
            return ThreatLevel.ACTIVE_ATTACK
        elif threat_score >= 0.6:
            return ThreatLevel.HIGH
        elif threat_score >= 0.4:
            return ThreatLevel.MODERATE
        elif threat_score >= 0.2:
            return ThreatLevel.LOW
        return ThreatLevel.NONE
    
    def block_node(self, node_id: str) -> None:
        """Block a hostile node."""
        self.blocked_nodes.add(node_id)
    
    def unblock_node(self, node_id: str) -> None:
        """Unblock a node."""
        self.blocked_nodes.discard(node_id)
    
    def add_attack_signature(self, signature: str) -> None:
        """Add a known attack signature."""
        self.attack_signatures.append(signature)
    
    def on_alert(self, callback: Callable) -> None:
        """Register alert callback."""
        self.alert_callbacks.append(callback)
    
    def raise_alert(self, threat_level: ThreatLevel, source: str, details: str) -> None:
        """Raise a security alert."""
        for callback in self.alert_callbacks:
            callback(threat_level, source, details)


class MAGINetwork:
    """
    The global MAGI network.
    
    Manages connections between MAGI installations and handles
    network-level operations including security.
    """
    
    # Known MAGI installations
    KNOWN_INSTALLATIONS = {
        "MAGI-01": ("Tokyo-3, Japan", "83.83.231.195", False),  # Original
        "MAGI-02": ("Matsushiro, Japan", "83.83.231.196", True),
        "MAGI-03": ("Berlin, Germany", "83.83.231.197", True),
        "MAGI-04": ("Massachusetts, USA", "83.83.231.198", True),
        "MAGI-05": ("Hamburg, Germany", "83.83.231.199", True),
        "MAGI-06": ("Beijing, China", "83.83.231.200", True),
    }
    
    def __init__(self, local_installation: str = "MAGI-01"):
        self.local_installation = local_installation
        self.nodes: Dict[str, NetworkNode] = {}
        self.intrusion_detector = IntrusionDetector()
        self.message_log: List[NetworkMessage] = []
        self.is_online = False
        
        # Initialize known nodes
        self._initialize_known_nodes()
    
    def _initialize_known_nodes(self) -> None:
        """Initialize nodes for known MAGI installations."""
        for install_id, (location, ip, is_replica) in self.KNOWN_INSTALLATIONS.items():
            if install_id != self.local_installation:
                self.nodes[install_id] = NetworkNode(
                    installation_id=install_id,
                    location=location,
                    ip_address=ip,
                    is_replica=is_replica,
                )
    
    def connect(self, node_id: str) -> bool:
        """Establish connection to a MAGI node."""
        node = self.nodes.get(node_id)
        if not node:
            return False
        
        node.status = ConnectionStatus.CONNECTING
        # Simulate connection establishment
        node.status = ConnectionStatus.CONNECTED
        node.last_contact = datetime.now()
        return True
    
    def disconnect(self, node_id: str) -> None:
        """Disconnect from a MAGI node."""
        node = self.nodes.get(node_id)
        if node:
            node.status = ConnectionStatus.DISCONNECTED
    
    def broadcast(self, message_type: str, payload: Dict[str, Any]) -> List[str]:
        """Broadcast a message to all connected nodes."""
        recipients = []
        
        for node_id, node in self.nodes.items():
            if node.status in [ConnectionStatus.CONNECTED, ConnectionStatus.AUTHENTICATED]:
                if node_id not in self.intrusion_detector.blocked_nodes:
                    message = self._create_message(node_id, message_type, payload)
                    self.message_log.append(message)
                    recipients.append(node_id)
        
        return recipients
    
    def send(self, target_node: str, message_type: str, payload: Dict[str, Any]) -> bool:
        """Send a message to a specific node."""
        node = self.nodes.get(target_node)
        if not node or node.status == ConnectionStatus.DISCONNECTED:
            return False
        
        if target_node in self.intrusion_detector.blocked_nodes:
            return False
        
        message = self._create_message(target_node, message_type, payload)
        self.message_log.append(message)
        return True
    
    def _create_message(self, target: str, msg_type: str, payload: Dict) -> NetworkMessage:
        """Create a network message."""
        msg_id = hashlib.sha256(
            f"{self.local_installation}-{target}-{datetime.now().isoformat()}".encode()
        ).hexdigest()[:12]
        
        return NetworkMessage(
            message_id=msg_id,
            source_node=self.local_installation,
            target_node=target,
            message_type=msg_type,
            payload=payload,
        )
    
    def receive(self, message: NetworkMessage) -> bool:
        """Process an incoming message."""
        source_node = self.nodes.get(message.source_node)
        if not source_node:
            return False
        
        # Check for threats
        threat = self.intrusion_detector.analyze_message(message, source_node)
        
        if threat == ThreatLevel.ACTIVE_ATTACK:
            source_node.mark_hostile()
            self.intrusion_detector.block_node(message.source_node)
            self.intrusion_detector.raise_alert(
                threat, 
                message.source_node,
                f"Active attack detected from {message.source_node}"
            )
            return False
        
        if threat in [ThreatLevel.HIGH, ThreatLevel.CRITICAL]:
            source_node.report_suspicious_activity()
        
        # Process message
        self.message_log.append(message)
        source_node.last_contact = datetime.now()
        return True
    
    def initiate_defense_mode(self) -> None:
        """
        Initiate network defense mode.
        
        Disconnects from all non-essential nodes and raises
        alert level for intrusion detection.
        """
        self.intrusion_detector.anomaly_threshold = 0.4  # More sensitive
        
        # Disconnect from untrusted nodes
        for node_id, node in self.nodes.items():
            if node.trust_level < 0.8:
                self.disconnect(node_id)
    
    def isolate(self) -> None:
        """
        Completely isolate this MAGI from the network.
        
        Used during active attacks to prevent compromise.
        """
        for node_id in self.nodes:
            self.disconnect(node_id)
        self.is_online = False
    
    def get_network_status(self) -> Dict[str, Any]:
        """Get comprehensive network status."""
        connected_count = sum(
            1 for n in self.nodes.values() 
            if n.status in [ConnectionStatus.CONNECTED, ConnectionStatus.AUTHENTICATED]
        )
        hostile_count = sum(
            1 for n in self.nodes.values()
            if n.status == ConnectionStatus.HOSTILE
        )
        
        return {
            "local_installation": self.local_installation,
            "is_online": self.is_online,
            "total_nodes": len(self.nodes),
            "connected_nodes": connected_count,
            "hostile_nodes": hostile_count,
            "blocked_nodes": list(self.intrusion_detector.blocked_nodes),
            "nodes": {
                nid: {
                    "location": n.location,
                    "status": n.status.value,
                    "trust_level": n.trust_level,
                    "threat_level": n.threat_level.value,
                }
                for nid, n in self.nodes.items()
            },
        }
