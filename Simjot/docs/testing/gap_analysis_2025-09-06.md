# Simjot – Test Gap Analysis (2025-09-06)

## Overview
This document summarizes the current state of testing in the Simjot project, recent improvements, identified gaps, and a prioritized plan to strengthen confidence and prevent regressions. It focuses on testability of non-UI core logic while avoiding Swing UI automation.

Project root: `Simjot/`
Tests root: `Simjot/tests/`
Runner script: `scripts/run_tests.sh`

## Current State (as of 2025-09-06)
- No Maven/Gradle build. Custom build and a lightweight test runner via JUnit Platform ConsoleLauncher.
- Testing approach documented in `TESTING.md` and implemented:
  - `scripts/run_tests.sh` compiles main sources from `Simjot/src/main/` and tests from `Simjot/tests/**`, downloads JUnit 5 console jar if needed, and runs the suite.
- Test discovery uses classpath scanning; no JPMS module-test integration.
- Initial test suites added and passing:
  - `main.core.service.SettingsStoreTest` — preferences persistence & clamping under `settings/preferences.properties`.
  - `main.core.service.NotebookStoreTest` — create/rename/delete, persistence in `notebooks.json`, mood log cleanup in `mood/mood_log.txt`.
  - `main.infrastructure.backup.BackupManagerTest` — timestamped naming `backup_yyyyMMdd_HHmmss`, copy behavior, retention keep-N.
  - `main.infrastructure.backup.BackupServiceTest` — frequency-due checks, triggerNow/triggerOnExit behavior, updates to `backup.last.epoch`.
- All tests isolate filesystem by calling `AppDirectories.setRoot(@TempDir)`; no writes outside temp.

## What’s Covered
- __Settings persistence__ (`main.core.service.SettingsStore`): save/load, value clamping, generic flags.
- __Notebooks persistence__ (`main.core.service.NotebookStore`): create/rename/delete, on-disk effects, mood cleanup.
- __Backup mechanics__ (`main.infrastructure.backup.BackupManager`, `BackupService`): archive creation, naming, retention pruning, scheduling decisions.
- __Prompting logic__ (`main.core.sim.llm.prompt.PromptBuilder`): base/context assembly, sanitization, preview trimming. (Unit tests exist in suite.)

## Identified Gaps
- __No CI integration__: Tests not executed automatically on PRs/pushes.
- __No coverage reporting__: No baseline or trending coverage metrics (e.g., JaCoCo).
- __Limited breadth__: Many core modules lack tests:
  - Retrieval: `main.core.sim.retrieval.{RecencyBuffer, RetrievalRanker}`
  - Heuristics/engine: `main.core.sim.engine.{ReasoningHeuristics, NudgePrefaceBuilder, TurnManager, SimScheduler}`
  - Proactivity: `main.core.sim.proactive.{ProactiveTriggerEngine, TriggerStatsStore}`
  - Infrastructure I/O utils: `main.infrastructure.io.{ResourceLoader, AppDirectories, DateFormatUtil}`
  - Poetry utilities: `main.core.poetry.{PoetryUtils, MeterScanner, MeterAnalysis}`
- __No integration/contract tests__ at the boundaries where UI triggers core logic.
- __No fault-injection/negative paths__: e.g., corrupted `notebooks.json`, missing resources, IO errors.
- __No mocks/fakes for LLM clients__: calls in `main.core.sim.llm.api` and `ollama/openai` adapters are not isolated for unit testing yet.

## Risks
- __Persistence regressions__ (path handling, rename/delete side-effects) without broader scenarios.
- __Scheduling/heuristic drift__ in proactivity/engine logic not caught by tests.
- __Cross-platform path issues__ in utility classes (mac/Linux/Windows differences).

## Recent Improvements
- Added classpath-based JUnit 5 test harness with `scripts/run_tests.sh` as described in `TESTING.md`.
- Seeded high-value persistence and backup tests using `@TempDir` for isolation.
- Prompt builder behavior validated for formatting/sanitization.

## Next Steps (Prioritized)
1. __CI Pipeline (GitHub Actions)__
   - Add a workflow to run `scripts/run_tests.sh` on push/PR for macOS and Ubuntu runners.
   - Cache the JUnit console jar or rely on quick download.

2. __Coverage (JaCoCo via CLI or Gradle shim)__
   - Short-term: Add JaCoCo agent to the ConsoleLauncher run to produce coverage XML/HTML.
   - Mid-term: Consider a minimal Gradle wrapper for tests-only (keeping custom build) to simplify coverage and reporting.

3. __Broaden Unit Tests__
   - Retrieval: `RecencyBuffer`, `RetrievalRanker` boundary and scoring.
   - Utilities: `DateFormatUtil` (formats/locales), `ResourceLoader` (missing resources), `AppDirectories` behavior when uninitialized.
   - Proactivity/engine: `ProactiveTriggerEngine`, `TriggerStatsStore`, `ReasoningHeuristics` using deterministic inputs.

4. __Negative Path / Robustness__
   - Corrupted or partially written `notebooks.json` should fail safely and log.
   - Rename collisions and case-insensitive duplicates.
   - IO failures during backup copy and prune operations.

5. __Mocks/Fakes__
   - Add lightweight fakes for `SimLLMClient` to validate higher-level logic without network/LLM access.

6. __Integration/Contract Tests__
   - Exercise core flows initiated by UI seams without Swing (e.g., services and stores) to validate end-to-end behavior using temp roots.

## Short-term Roadmap
- Week 1
  - Add GitHub Actions workflow for tests.
  - Add tests for `RecencyBuffer`, `RetrievalRanker`, `DateFormatUtil`, and `ResourceLoader`.
- Week 2
  - Integrate JaCoCo coverage; set baseline threshold (e.g., 50%).
  - Add negative-path tests for `NotebookStore` and `BackupManager`.
- Week 3
  - Add fakes for `SimLLMClient`; begin engine/proactivity unit tests.
  - Add contract tests over core flows driven by services.

## Test Execution
- Command: `bash scripts/run_tests.sh`
- Artifacts:
  - Compiled classes: `build/classes/`
  - Compiled tests: `build/test-classes/`
  - JUnit jar: `build/libs/junit-platform-console-standalone-<ver>.jar`

## Notes
- Current suite relies on classpath scanning and avoids modules for tests; this is acceptable for unit tests.
- UI testing is intentionally deferred; consider presenter/controller seams for testable surface area in the future.
