# Testing Simjot

This project uses JUnit 5 for unit testing. Tests can be run via Maven or the standalone test script.

## Quick Start

### Using Maven (Recommended)
```bash
cd Simjot
mvn test
```

### Using Test Script
```bash
bash scripts/run_tests.sh
```

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK | 17+ | Required for compilation and execution |
| Maven | 3.8+ | For Maven-based testing |
| curl | Any | For standalone script (downloads JUnit JAR) |

## Directory Layout

```
Simjot/
├── Simjot/
│   ├── src/main/         # Production source code
│   └── tests/            # Test source code (mirrors main packages)
├── scripts/
│   └── run_tests.sh      # Standalone test runner
└── build/
    ├── classes/          # Compiled main classes
    ├── test-classes/     # Compiled test classes
    └── libs/             # Downloaded JUnit JAR
```

## Writing Tests

### Test Location
Place test classes under `Simjot/tests/` mirroring the package structure of the class under test.

**Example:**
- Source: `Simjot/src/main/core/sim/llm/prompt/PromptBuilder.java`
- Test: `Simjot/tests/main/core/sim/llm/prompt/PromptBuilderTest.java`

### Test Structure
```java
package main.core.sim.llm.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {
    
    private PromptBuilder builder;
    
    @BeforeEach
    void setUp() {
        builder = new PromptBuilder();
    }
    
    @Test
    void testBuildPrompt_withValidInput_returnsExpectedOutput() {
        String result = builder.build("test input");
        assertNotNull(result);
        assertTrue(result.contains("test input"));
    }
}
```

### Naming Conventions
- Test classes: `*Test.java`
- Test methods: `test<Method>_<scenario>_<expectedResult>()`

## Test Categories

### Unit Tests
Located in `Simjot/tests/`, these test individual components in isolation:
- **Core logic**: Poetry analysis, spelling, services
- **Sim AI**: Prompt building, memory management, reasoning
- **Infrastructure**: Backup, file I/O, utilities

### Integration Tests
For testing component interactions (run manually or via specific profiles):
- File system operations
- Settings persistence
- Backup/restore workflows

## Running Specific Tests

### Maven
```bash
# Run a specific test class
mvn test -Dtest=PromptBuilderTest

# Run tests matching a pattern
mvn test -Dtest="*SimTest"

# Run with verbose output
mvn test -X
```

### Standalone Script
The script runs all discovered tests. To filter, modify the JUnit console launcher arguments in `scripts/run_tests.sh`.

## Test Script Details

The `scripts/run_tests.sh` script:
1. Downloads `junit-platform-console-standalone-1.10.2.jar` if not present
2. Compiles main sources (excluding `module-info.java`) to `build/classes/`
3. Compiles test sources to `build/test-classes/`
4. Executes all tests via JUnit ConsoleLauncher

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Tests not found | Ensure test files end with `Test.java` |
| Compilation errors | Check JDK version is 17+ |
| Missing JUnit JAR | Script auto-downloads; check network connection |
| Classpath issues | Run `mvn clean` then retry |

## Notes

- Tests run in classpath mode (not modular) for simplicity
- The standalone script requires a Unix-like shell (macOS/Linux/WSL)
- UI tests are limited due to Swing's nature; focus is on core logic testing