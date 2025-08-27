package com.example.statefuldetector.processor;

import com.example.statefuldetector.ExitCodes;
import com.example.statefuldetector.report.ReportFormat;
import com.example.statefuldetector.report.StatefulIssueReporter;
import com.example.statefuldetector.util.DiffFormatter;
import com.example.statefuldetector.visitor.StatefulCodeDetector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

/**
 * Processor for checking stateful code in single Java files.
 */
public class SingleFileProcessor {

	private final JavaParser javaParser;

	private StatefulIssueReporter reporter;

	private WorkaroundMode workaroundMode;

	private String workaroundScopeName = "prototype";

	private String workaroundProxyMode = "TARGET_CLASS";

	private Set<String> allowedScopes;

	private boolean failOnDetection = false;

	public SingleFileProcessor() {
		this.javaParser = JavaParser.fromJavaVersion().build();
		this.reporter = ReportFormat.DEFAULT.createReporter(); // Default reporter
	}

	/**
	 * Set report format.
	 * @param reportFormat the report format to use
	 */
	public void setReportFormat(ReportFormat reportFormat) {
		this.reporter = reportFormat.createReporter();
	}

	/**
	 * Set a custom reporter for output.
	 * @param reporter the reporter to use
	 */
	public void setReporter(StatefulIssueReporter reporter) {
		this.reporter = reporter;
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
	 * Set additional allowed scopes.
	 * @param allowedScopes set of additional allowed scopes
	 */
	public void setAllowedScopes(Set<String> allowedScopes) {
		this.allowedScopes = allowedScopes;
	}

	/**
	 * Set whether to fail when stateful issues are detected.
	 * @param failOnDetection true to fail on detection, false otherwise
	 */
	public void setFailOnDetection(boolean failOnDetection) {
		this.failOnDetection = failOnDetection;
	}

	/**
	 * Process a single Java file.
	 * @param filePath the path to the Java file
	 * @return exit code (0 for success, 64 if failOnDetection is true and issues found)
	 * @throws IOException if the file cannot be read
	 */
	public int processFile(Path filePath) throws IOException {
		if (!filePath.toString().endsWith(".java")) {
			return ExitCodes.SUCCESS;
		}

		// Initialize reporter for single file processing
		reporter.initialize();

		boolean hasIssues = false;
		try {
			String source = Files.readString(filePath);
			ExecutionContext ctx = new InMemoryExecutionContext();

			// Reset the parser to avoid conflicts with previously parsed files
			javaParser.reset();
			List<? extends SourceFile> compilationUnits = javaParser.parse(ctx, source).toList();

			for (SourceFile cu : compilationUnits) {
				if (cu instanceof J.CompilationUnit compilationUnit) {
					StatefulCodeDetector detector = new StatefulCodeDetector();
					if (allowedScopes != null) {
						detector.setAllowedScopes(allowedScopes);
					}
					detector.visit(compilationUnit, ctx);

					if (detector.hasStatefulIssues()) {
						hasIssues = true;
						if (workaroundMode != null) {
							// Apply workaround by adding scope annotation
							applyWorkaround(filePath, compilationUnit, ctx);
						}
						else {
							// Use reporter for output
							reporter.reportIssues(filePath, detector.getIssues());
						}
					}
				}
			}
		}
		finally {
			// Finish reporter for single file processing
			reporter.finish();
		}

		return (failOnDetection && hasIssues) ? ExitCodes.STATEFUL_ISSUES_DETECTED : ExitCodes.SUCCESS;
	}

	/**
	 * Process all Java files in a directory recursively.
	 * @param directory the directory to process
	 * @return exit code (0 for success, 64 if failOnDetection is true and issues found)
	 * @throws IOException if the directory cannot be read
	 */
	public int processDirectory(Path directory) throws IOException {
		// Initialize reporter
		reporter.initialize();

		boolean hasIssuesInDirectory = false;
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
				try {
					int result = processSingleFileInDirectory(path);
					if (result == ExitCodes.STATEFUL_ISSUES_DETECTED) {
						hasIssuesInDirectory = true;
					}
				}
				catch (IOException e) {
					System.err.println("Error processing file " + path + ": " + e.getMessage());
				}
			}
		}
		finally {
			// Finish reporter
			reporter.finish();
		}

		return (failOnDetection && hasIssuesInDirectory) ? ExitCodes.STATEFUL_ISSUES_DETECTED : ExitCodes.SUCCESS;
	}

	/**
	 * Process a single file within directory processing (without reporter lifecycle).
	 * @param filePath the path to the Java file
	 * @return exit code (0 for success, 64 if issues found)
	 * @throws IOException if the file cannot be read
	 */
	private int processSingleFileInDirectory(Path filePath) throws IOException {
		if (!filePath.toString().endsWith(".java")) {
			return ExitCodes.SUCCESS;
		}

		String source = Files.readString(filePath);
		ExecutionContext ctx = new InMemoryExecutionContext();

		// Reset the parser to avoid conflicts with previously parsed files
		javaParser.reset();
		List<? extends SourceFile> compilationUnits = javaParser.parse(ctx, source).toList();

		for (SourceFile cu : compilationUnits) {
			if (cu instanceof J.CompilationUnit compilationUnit) {
				StatefulCodeDetector detector = new StatefulCodeDetector();
				if (allowedScopes != null) {
					detector.setAllowedScopes(allowedScopes);
				}
				detector.visit(compilationUnit, ctx);

				if (detector.hasStatefulIssues()) {
					if (workaroundMode != null) {
						// Apply workaround by adding scope annotation
						applyWorkaround(filePath, compilationUnit, ctx);
					}
					else {
						// Use reporter for output
						reporter.reportIssues(filePath, detector.getIssues());
					}
					return ExitCodes.STATEFUL_ISSUES_DETECTED;
				}
			}
		}
		return ExitCodes.SUCCESS;
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