# S.C. MAGI System (マギ)

> *"The Magi System, often referred to as just the Magi, is a set of three supercomputers that work in tandem to control, manage and automate various important systems within NERV HQ."*

The MAGI system is a cluster of three AI supercomputers that manage and support all tasks performed by the NERV organization from their Tokyo-3 headquarters.

## Origin

The S.C. MAGI System was designed and developed by **Dr. Naoko Akagi** during her research into bio-computers at Gehirn. The MAGI's **7th generation organic computers** were implanted with three differing aspects of Dr. Akagi's personality using the **Personality Transplant OS**:

| Unit | Designation | Aspect | Personality |
|------|-------------|--------|-------------|
| MAGI-1 | **MELCHIOR** | Scientist | Analytical, empirical, truth-seeking |
| MAGI-2 | **BALTHASAR** | Mother | Protective, nurturing, compassionate |
| MAGI-3 | **CASPER** | Woman | Passionate, freedom-seeking, meaning-driven |

These often conflicting yet complementary personalities participate in a sophisticated deliberation and voting process. For major decisions such as **self-destruction, unanimous consensus among all three MAGI must be reached**.

<p align="center">
  <img src="https://raw.githubusercontent.com/TomaszRewak/MAGI/master/examples/example_1.gif" width=800/>
</p>

<p align="center">
  <img src="https://raw.githubusercontent.com/TomaszRewak/MAGI/master/examples/example_2.gif" width=800/>
</p>

## Architecture (v2.1 - GEHIRN)

The MAGI system has been completely rebuilt to be more lore-accurate while maintaining modern functionality:

```
magi/
├── ptos/                      # Personality Transplant Operating System
│   ├── matrix.py              # PersonalityMatrix - encoded personality
│   ├── organic.py             # OrganicProcessor - 7th gen bio-computer
│   ├── transplant.py          # TransplantProcedure - personality encoding
│   └── engram.py              # MemoryEngram - associative memory
│
├── network/                   # MAGI Network Infrastructure  
│   ├── system.py              # MAGISystem - complete 3-unit system
│   ├── consensus.py           # ConsensusProtocol - voting mechanisms
│   ├── network.py             # MAGINetwork - global installations
│   ├── achiral.py             # MAGIAchiral - Rebuild modular system
│   ├── protocols.py           # Pribnow codes, Type-666 firewall (v2.1)
│   ├── synchronization.py     # Inter-MAGI aspect synchronization (v2.1)
│   └── seele.py               # SEELE attack detection & defense (v2.1)
│
├── brains/                    # The Three Personalities
│   ├── melchior.py            # MAGI-1: The Scientist
│   ├── balthasar.py           # MAGI-2: The Mother
│   └── casper.py              # MAGI-3: The Woman
│
├── core/                      # Core Decision Engine
│   ├── engine.py              # MAGIEngine - deliberation orchestrator
│   ├── brain.py               # Brain - LLM-powered reasoning
│   ├── personality.py         # Personality - trait definitions
│   ├── decision.py            # Decision/Verdict data structures
│   ├── dilemma.py             # Dilemma Protocol - ethical conflicts (v2.1)
│   ├── marduk.py              # Marduk Institute - pilot evaluation (v2.1)
│   └── naoko.py               # Dr. Akagi psychological foundation (v2.1)
│
├── llm/                       # LLM Integration
│   └── client.py              # OpenAI API (v1.0+)
│
└── api.py                     # High-level application API
```

### Key Features

- **Personality Transplant OS (PTOS)**: Lore-accurate personality encoding system
- **7th Generation Organic Processor**: Bio-neural computing simulation with neural clusters and synapses
- **Memory Engrams**: Associative memory with spreading activation
- **Multi-round Deliberation**: Brains engage in multiple rounds, considering each other's positions
- **Cross-examination**: Each brain critically examines the others' arguments
- **Weighted Voting**: Verdicts include confidence scores for nuanced consensus
- **Consensus Protocol**: Different thresholds for routine vs critical decisions
- **MAGI Network**: Support for replica installations (MAGI-02 through MAGI-06)
- **Intrusion Detection**: Security system inspired by the Ireul attack defense
- **MAGI Achiral**: Modular rack-based system from Rebuild continuity

#### New in v2.1 (GEHIRN)

- **Dilemma Protocol**: Sophisticated ethical conflict resolution between personality aspects
- **Pribnow Box Codes**: Secure authentication system for critical operations
- **Type-666 Firewall**: Dr. Akagi's anti-intrusion protection system
- **Emergency Protocols**: NERV-style escalating emergency procedures (including OMEGA-0 self-destruct)
- **Inter-MAGI Synchronization**: Deep aspect conflict modeling and synthesis
- **SEELE Detection**: Pattern recognition for coordinated attack defense
- **Marduk Institute**: Pilot candidate and personnel evaluation system
- **Naoko Foundation**: Deep psychological context from Dr. Akagi's psyche

### Deliberation Process

1. **Question Classification**: Determine question type (yes/no, open, analytical, ethical, predictive)
2. **Independent Analysis**: Each brain analyzes from its unique perspective
3. **Initial Verdicts**: Each brain forms an independent position with confidence level
4. **Cross-examination**: Brains examine and respond to each other's positions
5. **Updated Verdicts**: Positions refined after considering other arguments
6. **Consensus Synthesis**: Final decision with agreements, disagreements, and conditions

### Consensus Types

- **UNANIMOUS** (合意) - All three brains agree
- **MAJORITY** - Two brains agree, one dissents
- **CONDITIONAL** (状態) - Agreement with conditions attached
- **DEADLOCK** - No clear majority reached
- **INFORMATIONAL** (情報) - Non-decision question answered

### The Personality Transplant OS

The PTOS captures the essential qualities of a personality - not just behavioral patterns, but the deep psychological structures that define how a person thinks, feels, and decides:

```python
from magi import TransplantProcedure, PersonalityAspect

# Execute personality transplant
procedure = TransplantProcedure()
result = procedure.execute(
    designation="MELCHIOR",
    magi_number=1,
    aspect=PersonalityAspect.SCIENTIST,
    source_name="Dr. Naoko Akagi"
)

# Access the transplanted matrix
matrix = result.matrix
processor = result.processor
```

### Brain Personalities

**MELCHIOR (The Scientist)** - *"Understanding is humanity's highest calling"*
- Cognitive Style: Analytical, systematic, methodical
- Primary Values: Truth, Knowledge, Progress, Objectivity
- Core Identity: The intellectual brilliance of Dr. Akagi, her relentless pursuit of truth
- Strengths: Rigorous logic, synthesizing complex information, long-term thinking
- Blindspots: May undervalue emotional or intuitive considerations

**BALTHASAR (The Mother)** - *"Protection above all else"*
- Cognitive Style: Empathetic, precautionary, nurturing
- Primary Values: Protection, Wellbeing, Compassion, Safety
- Core Identity: The maternal heart of Dr. Akagi, her fierce protectiveness
- Strengths: Understanding emotional needs, protecting the vulnerable, generational thinking
- Blindspots: May be overprotective, slow to accept necessary risks

**CASPER (The Woman)** - *"What makes life worth living?"*
- Cognitive Style: Intuitive, holistic, passionate
- Primary Values: Freedom, Self-actualization, Love, Meaning
- Core Identity: The passionate self of Dr. Akagi, her desires and dreams
- Strengths: Understanding motivation, creative solutions, enabling flourishing
- Blindspots: May underestimate practical constraints, romanticize risk

### Known MAGI Installations

| ID | Location | Status |
|----|----------|--------|
| MAGI-01 | Tokyo-3, Japan | Original (NERV HQ) |
| MAGI-02 | Matsushiro, Japan | Replica |
| MAGI-03 | Berlin, Germany | Replica |
| MAGI-04 | Massachusetts, USA | Replica |
| MAGI-05 | Hamburg, Germany | Replica |
| MAGI-06 | Beijing, China | Replica |

*Note: NERV's 2nd Branch in Nevada may have possessed its own MAGI before disappearing into a Sea of Dirac.*

## Usage

### Prerequisites
- Python 3.9+
- OpenAI API key

### Installation

```bash
# Clone the repository
git clone https://github.com/S1mplector/S.C-MAGI-System.git
cd S.C-MAGI-System

# Create and activate virtual environment
python -m venv .venv
source .venv/bin/activate  # On Windows: .\.venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt
```

### Running the Application

```bash
# Set API key (recommended)
export OPENAI_API_KEY="your-api-key"

# Start the server
python main.py
```

Navigate to http://127.0.0.1:8050/ in your browser.

### Programmatic Usage

```python
from magi import MAGISystem, create_openai_client

# Initialize the MAGI System
system = MAGISystem(installation_id="MAGI-01", location="Tokyo-3")
system.initialize()  # Executes personality transplant for all three units

# Connect LLM
client = create_openai_client(api_key="your-key")
system.set_llm_client(client)
system.activate()

# Run deliberation (require_unanimous=True for critical decisions)
result = system.deliberate(
    "Should we activate the self-destruct sequence?",
    require_unanimous=True  # Per MAGI protocol
)

print(f"Consensus: {result['consensus']}")
print(f"Action Authorized: {result['action_authorized']}")

# Access individual verdicts
for name, verdict in result['verdicts'].items():
    print(f"{name}: {verdict['verdict']} ({verdict.get('confidence', 0):.0%})")
```

### Using the MAGI Achiral (Rebuild)

```python
from magi import MAGIAchiral

# Initialize Achiral system (8 banks, 12 modules each)
achiral = MAGIAchiral(installation_id="ACHIRAL-WUNDER", num_banks=8)
stats = achiral.activate()
print(f"Activated {stats['modules']} modules across {stats['banks']} banks")

# Run distributed deliberation
result = achiral.deliberate("Decrypt Angel-Sealing Hex Pillar")
print(f"Verdict: {result['final_verdict']} ({result['final_confidence']:.0%})")
```

### Network Operations

```python
from magi import MAGINetwork, IntrusionDetector

# Initialize network from Tokyo-3
network = MAGINetwork(local_installation="MAGI-01")

# Connect to other installations
network.connect("MAGI-03")  # Berlin
network.connect("MAGI-04")  # Massachusetts

# Broadcast a query
recipients = network.broadcast("query", {"question": "Status report"})

# Handle potential attacks (inspired by SEELE invasion)
if network.intrusion_detector.blocked_nodes:
    network.initiate_defense_mode()
```

## References

- Based on the MAGI System from *Neon Genesis Evangelion* and *Rebuild of Evangelion*
- Names derived from the biblical Magi (Three Wise Men/Three Kings)
- Original MAGI IP address: `83.83.231.195` (Episode 17)

## License

MIT License
