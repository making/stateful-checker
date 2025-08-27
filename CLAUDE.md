# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Project Overview

**Stateful Detector** - A Java CLI tool built with OpenRewrite for detecting stateful code patterns in EJB 3 (Stateless Session Bean) and Spring Beans.

**Key Features:**
- Detect stateful code patterns in Spring and EJB components
- Single file and directory batch processing
- CSV output support for spreadsheet integration with duplicate suppression
- **Automatic workaround generation** - Add `@Scope` annotations to fix stateful beans
- Thread-safe collection detection (excludes `java.util.concurrent` collections)
- Smart exclusions for `@ConfigurationProperties` and allowed scopes (`prototype`, `request`)
- Cross-platform support (Windows/Unix) with Jimfs testing
- Standalone executable JAR with all dependencies

**Build Commands:**

```bash
mvn clean package                    # Build executable JAR
mvn spring-javaformat:apply          # Apply code formatting
mvn test                            # Run all tests
mvn compile                         # Compile only
```

**CLI Usage:**
```bash
# Create executable JAR
mvn clean package

# Run from JAR
java -jar target/stateful-detector.jar [options] <input-path>

# Options:
#   -h, --help                    Show help
#   -V, --version                 Show version
#   --csv                         Output results in CSV format
#   -v, --verbose                 Enable verbose output
#   --workaround-mode=<MODE>      Apply workaround by adding scope annotations (apply|diff)
#   --workaround-scope-name=<SCOPE> Scope name for workaround (default: prototype)
#   --workaround-proxy-mode=<MODE> Proxy mode for workaround (default: TARGET_CLASS)
#   --allowed-scope=<SCOPE>       Additional allowed scope (can be specified multiple times)
```

## Architecture

### Package Structure
- `com.example.statefuldetector` - Main package
- `com.example.statefuldetector.cli` - CLI interface
- `com.example.statefuldetector.processor` - Core processing logic
- `com.example.statefuldetector.visitor` - AST visitors
- `com.example.statefuldetector.util` - Utility classes
- `com.example.statefuldetector.recipe` - OpenRewrite recipes

### Key Technologies
- **OpenRewrite** - AST manipulation framework (Apache 2.0 licensed components only)
- **Picocli** - CLI framework
- **JUnit 5** - Testing framework (127 tests total)
- **AssertJ** - Fluent assertions
- **Jimfs 1.3.1** - In-memory file system for cross-platform testing
- **java-diff-utils** - Myers algorithm-based unified diffs
- **Maven Shade Plugin** - Fat JAR creation


## Development Requirements

### Prerequisites
- Java 17+
- Maven 3.6+

### Code Standards
- Spring Java Format enforced via Maven plugin
  ```xml
      <plugin>
        <groupId>io.spring.javaformat</groupId>
        <artifactId>spring-javaformat-maven-plugin</artifactId>
        <version>0.0.47</version>
        <executions>
          <execution>
            <phase>validate</phase>
            <inherited>true</inherited>
            <goals>
              <goal>validate</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
  ```
- Use modern Java features (Records, Pattern Matching, Text Blocks)
- Write javadoc and comments in English
- Avoid circular references between classes and packages
- All code must pass formatting validation before commit
- Don't use lombok and guave

### Testing Strategy
- JUnit 5 with AssertJ
- Jimfs for cross-platform file system testing
- Parameterized tests for package variants (javax/jakarta)
- Complete output verification (full file content, not fragments)
- All tests must pass before completing tasks

### OpenRewrite Constraints
- **License Compliance**: Only Apache 2.0 licensed OpenRewrite components
- **Single File Support**: No compilation context required
- **Import Management**: Manual import addition/removal via maybeAddImport/maybeRemoveImport

## Key Implementation Details

### Workaround Functionality
- **WorkaroundMode enum** - Type-safe mode management (APPLY, DIFF)
- **String-based transformation** - Simple regex-based annotation insertion
- **Unified diff generation** - Myers algorithm via java-diff-utils
- **Import management** - Automatic addition of Spring scope imports
- **Scope exclusions** - Skips files with existing @Scope annotations

### Smart Detection Features
- **Thread-safe collections** - Excludes java.util.concurrent collections from errors
- **Configuration properties** - Excludes @ConfigurationProperties classes
- **Allowed scopes** - Permits prototype/request scoped beans by default, plus custom scopes via CLI
- **CSV duplicate suppression** - Uses string concatenation for performance

### Scope Generation
- Uses `scopeName` attribute (not `value`)
- Defaults to `prototype` scope with `TARGET_CLASS` proxy mode  
- Supports custom scope names and proxy modes
- Generates: `@Scope(scopeName = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)`

## After Task Completion

- Ensure all code is formatted using `mvn spring-javaformat:apply`
- Run full test suite with `mvn test` (must pass all 127 tests)
- Verify executable JAR creation with `mvn clean package`
- Test CLI functionality with sample files including workaround modes
- For every task, notify completion with:

```bash
osascript -e 'display notification "my task completed successfully" with title "Development Complete"'
```