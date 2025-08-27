# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Project Overview

**Stateful Checker** - A Java CLI tool built with OpenRewrite for detecting stateful code patterns in EJB 3 (Stateless Session Bean) and Spring Beans.

**Key Features:**
- Detect stateful code patterns in Spring and EJB components
- Single file and directory batch processing
- CSV output support for spreadsheet integration
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
java -jar target/stateful-checker.jar [options] <input-path>

# Options:
#   -h, --help        Show help
#   -V, --version     Show version
#   --csv             Output results in CSV format
#   -v, --verbose     Enable verbose output
```

## Architecture

### Package Structure
- `com.example.statefulchecker` - Main package
- `com.example.statefulchecker.cli` - CLI interface
- `com.example.statefulchecker.processor` - Core processing logic (SingleFileProcessor)
- `com.example.statefulchecker.recipe` - OpenRewrite recipes
- `com.example.statefulchecker.visitor` - AST visitors
- `com.example.statefulchecker.util` - Utility classes

### Key Technologies
- **OpenRewrite** - AST manipulation framework (Apache 2.0 licensed components only)
- **Picocli** - CLI framework
- **JUnit 5** - Testing framework
- **Jimfs 1.3.1** - In-memory file system for cross-platform testing
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

## After Task Completion

- Ensure all code is formatted using `mvn spring-javaformat:apply`
- Run full test suite with `mvn test`
- Verify executable JAR creation with `mvn clean package`
- Test CLI functionality with sample files
- For every task, notify completion with:

```bash
osascript -e 'display notification "my task completed successfully" with title "Development Complete"'
```