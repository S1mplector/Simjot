# Testing Simjot (Lightweight JUnit 5)

This project uses a minimal JUnit 5 setup via the JUnit Platform ConsoleLauncher, without changing the existing custom build.

## Prerequisites
- JDK 17+
- curl (to fetch the JUnit standalone jar on first run)
- macOS/Linux shell

## Directory layout
- Main sources: `Simjot/src/...`
- Tests: `Simjot/tests/...` (mirror main packages)
- Script: `scripts/run_tests.sh`
- Build outputs: `build/classes`, `build/test-classes`, and `build/libs`

## Running tests
```bash
bash scripts/run_tests.sh
```
The script will:
- Download `junit-platform-console-standalone` locally into `build/libs/` if missing.
- Compile main sources (excluding `module-info.java`) into `build/classes`.
- Compile tests into `build/test-classes` with the proper classpath.
- Execute all tests discovered on the classpath.

## Writing tests
- Place test classes under `Simjot/tests/` and match the package of the class under test.
- Use JUnit 5 annotations (e.g., `@Test`) from `org.junit.jupiter.api`.

Example: `Simjot/tests/main/core/sim/llm/prompt/PromptBuilderTest.java` tests `PromptBuilder` logic.

## Notes
- This path uses the classpath only (not the Java module system) for simplicity. This is fine for unit tests.
- The script assumes a Unix-like environment. Adjust paths and commands for Windows if needed.