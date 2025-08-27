# Stateful Checker

A Java CLI tool for detecting stateful code patterns in Spring Beans and EJB Stateless Session Beans. Stateful instance variables in these components can lead to thread safety issues and unexpected behavior in multi-threaded environments.

## Features

- Detects instance variable modifications in Spring and EJB components
- Supports both `javax` and `jakarta` package namespaces
- Single file and directory batch processing
- CSV output for spreadsheet integration
- Standalone executable JAR

## Detected Patterns

The tool identifies several problematic patterns:

1. **Field assignments** - Direct modification of instance variables
2. **Collection modifications** - Calls to methods like `add()`, `remove()`, `clear()`
3. **Increment/decrement operations** - `++` and `--` operators on fields
4. **Compound assignments** - `+=`, `-=`, etc. on fields

### Supported Annotations

**Spring Framework:**
- `@Component`, `@Service`, `@Repository`, `@Controller`, `@RestController`

**Jakarta/Java EE:**
- `@Stateless` (EJB Stateless Session Beans)

## Installation

### Prerequisites
- Java 17 or higher
- Maven 3.6 or higher

### Building from Source

```bash
git clone https://github.com/yourusername/stateful-checker.git
cd stateful-checker
mvn clean package
```

This creates an executable JAR in the `target` directory.

## Usage

### Basic Usage

```bash
# Check a single file
java -jar target/stateful-checker.jar MyService.java

# Check all Java files in a directory
java -jar target/stateful-checker.jar src/main/java
```

### CSV Output

Export results to CSV format for spreadsheet analysis:

```bash
java -jar target/stateful-checker.jar --csv src/main/java > results.csv
```

CSV columns:
- **File** - Source file path
- **Field** - Field name with the issue
- **Issue** - Type of stateful operation detected
- **Level** - Severity level (ERROR/WARNING)
- **Method** - Method where the issue was found

### Command Line Options

```
Usage: stateful-checker [-hV] [--csv] [-v] <inputPath>

Parameters:
  <inputPath>              Input file or directory to check

Options:
  -h, --help               Show this help message and exit
  -V, --version            Print version information and exit
  --csv                    Output results in CSV format
  -v, --verbose            Enable verbose output
```

## Examples

### Example Problematic Code

```java
@Service
public class UserService {
    private String lastUser;  // Stateful field
    private List<User> cache = new ArrayList<>();  // Stateful field
    
    public void processUser(User user) {
        lastUser = user.getName();  // ERROR: Field assignment
        cache.add(user);  // ERROR: Collection modification
    }
    
    public String getLastUser() {
        return lastUser;
    }
}
```

### Example Output

```
Stateful code detected in: src/main/java/com/example/UserService.java
  Field: lastUser
    - Field assignment to 'lastUser' in method processUser
  Field: cache
    - Collection modification (add on cache) in method processUser
```

### Example CSV Output

```csv
File,Field,Issue,Level,Method
src/main/java/com/example/UserService.java,lastUser,Field assignment,ERROR,processUser
src/main/java/com/example/UserService.java,cache,Collection modification,ERROR,processUser
```

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Run tests (`mvn test`)
4. Ensure code formatting (`mvn spring-javaformat:apply`)
5. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
6. Push to the branch (`git push origin feature/AmazingFeature`)
7. Open a Pull Request

## Acknowledgments

Built with:
- [OpenRewrite](https://docs.openrewrite.org/) - Automated source code refactoring
- [Picocli](https://picocli.info/) - Command line interface framework