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

	public SingleFileProcessor() {
		this.javaParser = JavaParser.fromJavaVersion().build();
		this.recipe = new StatefulCodeRecipe();
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
					if (!foundIssues) {
						System.out.println("\nStateful code detected in: " + filePath);
						foundIssues = true;
					}
					detector.reportIssues();
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

}