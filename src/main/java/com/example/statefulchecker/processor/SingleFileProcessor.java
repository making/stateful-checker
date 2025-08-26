package com.example.statefulchecker.processor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import com.example.statefulchecker.recipe.StatefulCodeRecipe;
import com.example.statefulchecker.visitor.StatefulCodeDetector;

/**
 * Processor for checking stateful code in single Java files.
 */
public class SingleFileProcessor {

	private final JavaParser javaParser;

	private final StatefulCodeRecipe recipe;

	private boolean csvOutput = false;

	private boolean csvHeaderPrinted = false;

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

		List<? extends SourceFile> compilationUnits = javaParser.parse(ctx, source).toList();

		boolean foundIssues = false;
		for (SourceFile cu : compilationUnits) {
			if (cu instanceof J.CompilationUnit) {
				J.CompilationUnit compilationUnit = (J.CompilationUnit) cu;
				StatefulCodeDetector detector = new StatefulCodeDetector();
				detector.visit(compilationUnit, ctx);

				if (detector.hasStatefulIssues()) {
					if (csvOutput) {
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

			// Escape CSV values and output
			System.out.printf("%s,%s,%s,%s,%s%n", escapeCsv(filePath.toString()), escapeCsv(issue.fieldName()),
					escapeCsv(issueType), escapeCsv(issue.level().toString()), escapeCsv(method));
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

}