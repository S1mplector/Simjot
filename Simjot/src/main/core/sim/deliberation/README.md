## Sim Supercomputer Engine (MAGI)

This folder vendors the local MAGI engine inside Simjot so it can be used as a first-class Sim provider.

### Layout

- `python/magi/`: vendored MAGI Python package
- `python/sim_magi_bridge.py`: CLI bridge used by Java (`MagiClient`)
- `python/requirements-sc-magi.txt`: upstream Python dependencies
- `python/SC_MAGI_LICENSE.txt`: upstream license
- `python/README_SC_MAGI.md`: upstream README snapshot

### Runtime

1. Install dependencies in your Python environment:
   - `pip install -r Simjot/src/main/core/sim/supercomputer/python/requirements-sc-magi.txt`
2. In Simjot Settings:
   - Set `LLM provider` to `magi`
   - Configure `MAGI python command` (default `python3`)
   - Configure `MAGI model` (default `gpt-5`)
3. Optionally set `OpenAI API key` (if omitted, MAGI may run in mock fallback mode).

### Vendor Source

If you clone upstream separately, use any local path (for example `/tmp/sc-magi-system/`).

Copy updates into Simjot with:
- `rsync -a --delete /path/to/sc-magi-system/magi/ Simjot/src/main/core/sim/supercomputer/python/magi/`
