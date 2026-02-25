"""
MAGI Achiral
============

The modular MAGI system developed for the AAA Wunder, as seen in
the Rebuild of Evangelion films.

Unlike the original MAGI which consists of three large supercomputers,
the Achiral is a collection of countless modules, each containing
three smaller computing units. These are stored in tower structures
with elevator access for maintenance.

The Achiral provides:
- Massive parallel processing capability
- Redundancy through module replication
- Scalable architecture
- Similar functionality to the original MAGI
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Tuple
from enum import Enum
from datetime import datetime
import random
import hashlib


class ModuleStatus(Enum):
    """Status of an Achiral module."""
    OFFLINE = "offline"
    STANDBY = "standby"
    ACTIVE = "active"
    PROCESSING = "processing"
    DEGRADED = "degraded"
    FAILED = "failed"
    MAINTENANCE = "maintenance"


class BankStatus(Enum):
    """Status of an Achiral bank (collection of modules)."""
    OFFLINE = "offline"
    PARTIAL = "partial"  # Some modules active
    OPERATIONAL = "operational"
    OVERLOADED = "overloaded"


@dataclass
class AchiralCell:
    """
    A single computing cell within an Achiral module.
    
    Each module contains three cells, mirroring the three-brain
    structure of the original MAGI.
    """
    
    cell_id: str
    cell_number: int  # 1, 2, or 3 (like MAGI units)
    designation: str  # "M", "B", or "C" (Melchior/Balthasar/Casper analog)
    
    # Processing state
    is_active: bool = True
    load_percentage: float = 0.0
    temperature: float = 35.0  # Operating temperature
    
    # Health
    integrity: float = 1.0
    error_count: int = 0
    
    def process_load(self, load: float) -> float:
        """Process a computational load, return completion percentage."""
        if not self.is_active or self.integrity < 0.3:
            return 0.0
        
        capacity = self.integrity * (1.0 - self.load_percentage / 100)
        processed = min(load, capacity * 100)
        self.load_percentage += processed * 0.1
        self.temperature += processed * 0.01
        
        return processed / max(load, 0.01) * 100
    
    def cool_down(self) -> None:
        """Reduce temperature and load."""
        self.temperature = max(35.0, self.temperature - 1.0)
        self.load_percentage = max(0.0, self.load_percentage - 5.0)


@dataclass
class AchiralModule:
    """
    A single Achiral module containing three computing cells.
    
    Each module is a self-contained unit that can perform
    MAGI-like deliberation at a smaller scale.
    """
    
    module_id: str
    bank_id: str
    position: int  # Position within the bank
    
    # The three cells
    cells: List[AchiralCell] = field(default_factory=list)
    
    # Status
    status: ModuleStatus = ModuleStatus.OFFLINE
    last_activity: Optional[datetime] = None
    
    # Performance metrics
    operations_completed: int = 0
    average_response_time_ms: float = 0.0
    
    def __post_init__(self):
        """Initialize the three cells if not provided."""
        if not self.cells:
            designations = ["M", "B", "C"]  # Melchior, Balthasar, Casper
            for i, des in enumerate(designations, 1):
                self.cells.append(AchiralCell(
                    cell_id=f"{self.module_id}-{des}",
                    cell_number=i,
                    designation=des,
                ))
    
    def activate(self) -> None:
        """Activate this module."""
        self.status = ModuleStatus.ACTIVE
        for cell in self.cells:
            cell.is_active = True
    
    def deactivate(self) -> None:
        """Deactivate this module."""
        self.status = ModuleStatus.STANDBY
        for cell in self.cells:
            cell.is_active = False
    
    def vote(self, query_hash: str) -> Tuple[str, float]:
        """
        Perform a quick vote across the three cells.
        
        Returns (verdict, confidence) based on cell states.
        This is a simplified voting mechanism for load distribution.
        """
        # Use query hash to deterministically but pseudo-randomly vote
        seed = int(query_hash[:8], 16) + hash(self.module_id)
        random.seed(seed)
        
        votes = []
        for cell in self.cells:
            if cell.is_active and cell.integrity > 0.3:
                # Each cell votes based on its "personality"
                if cell.designation == "M":  # Analytical
                    vote = random.random() > 0.5
                elif cell.designation == "B":  # Protective
                    vote = random.random() > 0.6  # More cautious
                else:  # Passionate
                    vote = random.random() > 0.4  # More approving
                votes.append((vote, cell.integrity))
        
        if not votes:
            return ("ABSTAIN", 0.0)
        
        # Weighted vote
        approve_weight = sum(w for v, w in votes if v)
        reject_weight = sum(w for v, w in votes if not v)
        total = approve_weight + reject_weight
        
        if approve_weight > reject_weight:
            return ("APPROVE", approve_weight / total)
        elif reject_weight > approve_weight:
            return ("REJECT", reject_weight / total)
        return ("DEADLOCK", 0.5)
    
    def get_health(self) -> float:
        """Get overall module health."""
        if not self.cells:
            return 0.0
        return sum(c.integrity for c in self.cells) / len(self.cells)
    
    def maintenance(self) -> None:
        """Perform maintenance on this module."""
        self.status = ModuleStatus.MAINTENANCE
        for cell in self.cells:
            cell.integrity = min(1.0, cell.integrity + 0.1)
            cell.error_count = 0
            cell.cool_down()
        self.status = ModuleStatus.STANDBY


@dataclass
class AchiralBank:
    """
    A bank of Achiral modules stored in a tower structure.
    
    Banks provide redundancy and parallel processing capability.
    Multiple banks can work together on complex problems.
    """
    
    bank_id: str
    floor_number: int  # Position in the tower
    
    # Modules in this bank
    modules: List[AchiralModule] = field(default_factory=list)
    max_modules: int = 12
    
    # Status
    status: BankStatus = BankStatus.OFFLINE
    
    def __post_init__(self):
        """Initialize modules if not provided."""
        if not self.modules:
            for i in range(self.max_modules):
                self.modules.append(AchiralModule(
                    module_id=f"{self.bank_id}-M{i:02d}",
                    bank_id=self.bank_id,
                    position=i,
                ))
    
    def activate_all(self) -> int:
        """Activate all modules. Returns count of activated modules."""
        count = 0
        for module in self.modules:
            if module.status != ModuleStatus.FAILED:
                module.activate()
                count += 1
        
        self.status = BankStatus.OPERATIONAL if count == len(self.modules) else BankStatus.PARTIAL
        return count
    
    def get_active_modules(self) -> List[AchiralModule]:
        """Get list of active modules."""
        return [m for m in self.modules if m.status == ModuleStatus.ACTIVE]
    
    def aggregate_vote(self, query_hash: str) -> Tuple[str, float]:
        """
        Aggregate votes from all active modules.
        
        This provides the consensus of the entire bank.
        """
        votes = {"APPROVE": 0.0, "REJECT": 0.0, "DEADLOCK": 0.0, "ABSTAIN": 0}
        
        for module in self.get_active_modules():
            verdict, confidence = module.vote(query_hash)
            if verdict == "ABSTAIN":
                votes["ABSTAIN"] += 1
            else:
                votes[verdict] += confidence
        
        # Determine bank-level verdict
        total_weight = votes["APPROVE"] + votes["REJECT"] + votes["DEADLOCK"]
        if total_weight == 0:
            return ("ABSTAIN", 0.0)
        
        if votes["APPROVE"] > votes["REJECT"] and votes["APPROVE"] > votes["DEADLOCK"]:
            return ("APPROVE", votes["APPROVE"] / total_weight)
        elif votes["REJECT"] > votes["APPROVE"] and votes["REJECT"] > votes["DEADLOCK"]:
            return ("REJECT", votes["REJECT"] / total_weight)
        return ("DEADLOCK", votes["DEADLOCK"] / total_weight)
    
    def get_health(self) -> float:
        """Get overall bank health."""
        if not self.modules:
            return 0.0
        return sum(m.get_health() for m in self.modules) / len(self.modules)


class MAGIAchiral:
    """
    The complete MAGI Achiral system.
    
    Consists of multiple banks of modules, providing massive
    parallel processing capability for the AAA Wunder.
    """
    
    def __init__(self, installation_id: str = "ACHIRAL-WUNDER", num_banks: int = 8):
        self.installation_id = installation_id
        self.banks: List[AchiralBank] = []
        
        # Initialize banks
        for i in range(num_banks):
            self.banks.append(AchiralBank(
                bank_id=f"{installation_id}-B{i:02d}",
                floor_number=i,
            ))
        
        # Status
        self.is_active = False
        self.processing_mode = "normal"
    
    def activate(self) -> Dict[str, int]:
        """Activate all banks. Returns activation statistics."""
        stats = {"banks": 0, "modules": 0}
        
        for bank in self.banks:
            count = bank.activate_all()
            if count > 0:
                stats["banks"] += 1
                stats["modules"] += count
        
        self.is_active = True
        return stats
    
    def deliberate(self, query: str) -> Dict[str, Any]:
        """
        Run deliberation across all Achiral banks.
        
        Aggregates results from all banks to form a system-wide decision.
        """
        query_hash = hashlib.sha256(query.encode()).hexdigest()
        
        bank_results = []
        for bank in self.banks:
            if bank.status in [BankStatus.OPERATIONAL, BankStatus.PARTIAL]:
                verdict, confidence = bank.aggregate_vote(query_hash)
                bank_results.append({
                    "bank_id": bank.bank_id,
                    "verdict": verdict,
                    "confidence": confidence,
                    "health": bank.get_health(),
                })
        
        # Aggregate all banks
        total_approve = sum(r["confidence"] for r in bank_results if r["verdict"] == "APPROVE")
        total_reject = sum(r["confidence"] for r in bank_results if r["verdict"] == "REJECT")
        total_weight = total_approve + total_reject
        
        if total_weight == 0:
            final_verdict = "DEADLOCK"
            final_confidence = 0.0
        elif total_approve > total_reject:
            final_verdict = "APPROVE"
            final_confidence = total_approve / total_weight
        else:
            final_verdict = "REJECT"
            final_confidence = total_reject / total_weight
        
        return {
            "installation_id": self.installation_id,
            "query": query,
            "final_verdict": final_verdict,
            "final_confidence": final_confidence,
            "banks_consulted": len(bank_results),
            "bank_results": bank_results,
        }
    
    def get_status(self) -> Dict[str, Any]:
        """Get comprehensive system status."""
        total_modules = sum(len(b.modules) for b in self.banks)
        active_modules = sum(len(b.get_active_modules()) for b in self.banks)
        avg_health = sum(b.get_health() for b in self.banks) / max(len(self.banks), 1)
        
        return {
            "installation_id": self.installation_id,
            "is_active": self.is_active,
            "total_banks": len(self.banks),
            "total_modules": total_modules,
            "active_modules": active_modules,
            "average_health": avg_health,
            "processing_mode": self.processing_mode,
        }
    
    def allocate_for_decryption(self, num_banks: int = 3) -> List[AchiralBank]:
        """
        Allocate banks for decryption tasks.
        
        As seen during the Paris Assault Mission where Achiral
        helped decrypt the Angel-Sealing Hex Pillar.
        """
        available = [b for b in self.banks if b.status == BankStatus.OPERATIONAL]
        return available[:num_banks]
