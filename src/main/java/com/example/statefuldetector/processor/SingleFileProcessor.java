package com.example.statefuldetector.processor;

import com.example.statefuldetector.recipe.StatefulCodeRecipe;
import com.example.statefuldetector.visitor.StatefulCodeDetector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import com.example.statefuldetector.util.DiffFormatter;

/**
 * Processor for checking stateful code in single Java files.
 */
public class SingleFileProcessor {

	private final JavaParser javaParser;

	private final StatefulCodeRecipe recipe;

	private boolean csvOutput = false;

	private boolean csvHeaderPrinted = false;

	private final Set<String> printedRecords = new HashSet<>();

	private WorkaroundMode workaroundMode;

	private String workaroundScopeName = "prototype";

	private String workaroundProxyMode = "TARGET_CLASS";

	public SingleFileProcessor() {
		this.javaParser = JavaParser.fromJavaVersion().build();
		this.recipe = new StatefulCodeRecipe();
	}

	/**
	 * Set CSV output mode.
	 * @param csvOutput true to enable CSV output, false for normal output
	 */
	public void setCsvOutput(boolean csvOutput) {
		this.csvOutput = csvOutput;
	}

	/**
	 * Set workaround mode.
	 * @param workaroundMode the workaround mode
	 */
	public void setWorkaroundMode(WorkaroundMode workaroundMode) {
		this.workaroundMode = workaroundMode;
	}

	/**
	 * Set workaround scope name.
	 * @param workaroundScopeName scope name (default: prototype)
	 */
	public void setWorkaroundScopeName(String workaroundScopeName) {
		this.workaroundScopeName = workaroundScopeName;
	}

	/**
	 * Set workaround proxy mode.
	 * @param workaroundProxyMode proxy mode (default: TARGET_CLASS)
	 */
	public void setWorkaroundProxyMode(String workaroundProxyMode) {
		this.workaroundProxyMode = workaroundProxyMode;
	}

	/**
	 * Process a single Java file.
	 * @param filePath the path to the Java file
	 * @throws IOException if the file cannot be read
	 */
	public void processFile(Path filePath) throws IOException {
		if (!filePath.toString().endsWith(".java")) {
			return;
		}

		String source = Files.readString(filePath);
		ExecutionContext ctx = new InMemoryExecutionContext();

		// Reset the parser to avoid conflicts with previously parsed files
		javaParser.reset();
		List<? extends SourceFile> compilationUnits = javaParser.parse(ctx, source).toList();

		boolean foundIssues = false;
		for (SourceFile cu : compilationUnits) {
			if (cu instanceof J.CompilationUnit compilationUnit) {
				StatefulCodeDetector detector = new StatefulCodeDetector();
				detector.visit(compilationUnit, ctx);

				if (detector.hasStatefulIssues()) {
					if (workaroundMode != null) {
						// Apply workaround by adding scope annotation
						applyWorkaround(filePath, compilationUnit, ctx);
					}
					else if (csvOutput) {
						outputCsvResults(filePath, detector);
					}
					else {
						if (!foundIssues) {
							System.out.println("\nStateful code detected in: " + filePath);
							foundIssues = true;
						}
						detector.reportIssues();
					}
				}
			}
		}
	}

	/**
	 * Process all Java files in a directory recursively.
	 * @param directory the directory to process
	 * @throws IOException if the directory cannot be read
	 */
	public void processDirectory(Path directory) throws IOException {
		try (Stream<Path> paths = Files.walk(directory)) {
			paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).forEach(path -> {
				try {
					processFile(path);
				}
				catch (IOException e) {
					System.err.println("Error processing file " + path + ": " + e.getMessage());
				}
			});
		}
	}

	/**
	 * Print CSV header if not already printed.
	 */
	private void printCsvHeader() {
		if (!csvHeaderPrinted) {
			System.out.println("File,Field,Issue,Level,Method");
			csvHeaderPrinted = true;
		}
	}

	/**
	 * Output detection results in CSV format.
	 * @param filePath the path of the file being processed
	 * @param detector the detector with results
	 */
	private void outputCsvResults(Path filePath, StatefulCodeDetector detector) {
		printCsvHeader();

		List<StatefulCodeDetector.StatefulIssue> issues = detector.getIssues();
		for (StatefulCodeDetector.StatefulIssue issue : issues) {
			// Parse method from message like "Field assignment to 'state' in method
			// process"
			String method = extractMethodFromMessage(issue.message());
			String issueType = extractIssueTypeFromMessage(issue.message());

			// Create unique key for duplicate checking
			String uniqueKey = filePath.toString() + "|" + issue.fieldName() + "|" + issueType + "|" + issue.level();

			// Only output if not already printed
			if (printedRecords.add(uniqueKey)) {
				// Escape CSV values and output
				System.out.printf("%s,%s,%s,%s,%s%n", escapeCsv(filePath.toString()), escapeCsv(issue.fieldName()),
						escapeCsv(issueType), escapeCsv(issue.level().toString()), escapeCsv(method));
			}
		}
	}

	/**
	 * Extract method name from issue message.
	 */
	private String extractMethodFromMessage(String message) {
		// Extract method name from patterns like "... in method methodName"
		int methodIndex = message.lastIndexOf(" in method ");
		if (methodIndex != -1) {
			return message.substring(methodIndex + 11); // " in method ".length() = 11
		}
		return "";
	}

	/**
	 * Extract issue type from message.
	 */
	private String extractIssueTypeFromMessage(String message) {
		// Extract the issue type from the beginning of the message
		if (message.startsWith("Field assignment")) {
			return "Field assignment";
		}
		else if (message.startsWith("Collection modification")) {
			return "Collection modification";
		}
		else if (message.startsWith("Increment operation")) {
			return "Increment operation";
		}
		else if (message.startsWith("Decrement operation")) {
			return "Decrement operation";
		}
		return message.split(" ")[0]; // fallback to first word
	}

	/**
	 * Escape CSV values by quoting them if they contain commas, quotes, or newlines.
	 */
	private String escapeCsv(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	/**
	 * Apply workaround by adding scope annotation to stateful classes.
	 * @param filePath the file path
	 * @param compilationUnit the compilation unit
	 * @param ctx execution context
	 * @throws IOException if file operations fail
	 */
	private void applyWorkaround(Path filePath, J.CompilationUnit compilationUnit, ExecutionContext ctx)
			throws IOException {
		String original = Files.readString(filePath);

		// Create a simple workaround by adding @Scope annotation
		String transformed = addScopeAnnotation(original, compilationUnit);

		// Check if any changes were made
		if (original.equals(transformed)) {
			// No changes made (e.g., EJB classes or already has @Scope)
			return;
		}

		if (WorkaroundMode.APPLY.equals(workaroundMode)) {
			// Write the transformed content back to file
			Files.writeString(filePath, transformed);
			System.out.println("Applied workaround to: " + filePath);
		}
		else if (WorkaroundMode.DIFF.equals(workaroundMode)) {
			// Show diff
			String diff = DiffFormatter.generateUnifiedDiff(original, transformed, filePath);
			if (!diff.isEmpty()) {
				System.out.print(diff);
			}
		}
	}

	/**
	 * Add scope annotation to the class in the source code.
	 * @param source the original source code
	 * @param compilationUnit the compilation unit
	 * @return the transformed source code
	 */
	private String addScopeAnnotation(String source, J.CompilationUnit compilationUnit) {
		// Check if any class already has @Scope annotation
		boolean hasExistingScope = source.contains("@Scope");
		if (hasExistingScope) {
			// If scope already exists, return original source unchanged
			return source;
		}

		// Check if this is a Spring Bean class (not EJB)
		boolean hasSpringBeanAnnotation = source.contains("@Component") || source.contains("@Service")
				|| source.contains("@Repository") || source.contains("@Controller")
				|| source.contains("@RestController");

		if (!hasSpringBeanAnnotation) {
			// EJB classes (@Stateless) are not supported for workaround
			return source;
		}

		// Find the class declaration and add @Scope annotation
		String[] lines = source.split("\n");
		StringBuilder result = new StringBuilder();
		boolean importsAdded = false;
		boolean scopeAnnotationAdded = false;

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];

			// Add imports after package declaration
			if (!importsAdded && line.trim().startsWith("import ")) {
				if (i == 0 || !lines[i - 1].trim().startsWith("import")) {
					// First import line - add our imports before
					result.append("import org.springframework.context.annotation.Scope;\n");
					result.append("import org.springframework.context.annotation.ScopedProxyMode;\n");
					importsAdded = true;
				}
			}

			// Add @Scope annotation before class declaration with Spring Bean annotations
			if (!scopeAnnotationAdded && (line.trim().startsWith("@Component") || line.trim().startsWith("@Service")
					|| line.trim().startsWith("@Repository") || line.trim().startsWith("@Controller")
					|| line.trim().startsWith("@RestController"))) {
				result.append(line).append("\n");
				String indentation = line.substring(0, line.length() - line.trim().length());
				result.append(indentation)
					.append("@Scope(scopeName = \"")
					.append(workaroundScopeName)
					.append("\", proxyMode = ScopedProxyMode.")
					.append(workaroundProxyMode)
					.append(")\n");
				scopeAnnotationAdded = true;
				continue;
			}

			result.append(line).append("\n");
		}

		return result.toString();
	}

}