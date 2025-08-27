package com.example.statefuldetector.util;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class DiffFormatter {

	public static String generateDiff(String filename, String original, String transformed) {
		if (original.equals(transformed)) {
			return "No changes detected in " + filename + "\n";
		}

		List<String> originalLines = Arrays.asList(original.split("\n"));
		List<String> transformedLines = Arrays.asList(transformed.split("\n"));

		return generateUnifiedDiff(originalLines, transformedLines, filename);
	}

	public static String generateUnifiedDiff(String original, String transformed, Path filePath) {
		if (original.equals(transformed)) {
			return ""; // No changes
		}

		List<String> originalLines = Arrays.asList(original.split("\n"));
		List<String> transformedLines = Arrays.asList(transformed.split("\n"));

		return generateUnifiedDiff(originalLines, transformedLines, filePath.toString());
	}

	private static String generateUnifiedDiff(List<String> originalLines, List<String> transformedLines,
			String fileName) {
		// Generate patch using java-diff-utils
		Patch<String> patch = DiffUtils.diff(originalLines, transformedLines);

		// Generate unified diff format
		String timestamp = Instant.now().toString();
		List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(fileName, fileName, originalLines, patch, 3 // context
																													// lines
		);

		// Add timestamps to headers
		if (unifiedDiff.size() >= 2) {
			unifiedDiff.set(0, "--- " + fileName + "\t" + timestamp);
			unifiedDiff.set(1, "+++ " + fileName + "\t" + timestamp);
		}

		return String.join("\n", unifiedDiff) + "\n";
	}

}