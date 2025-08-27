package com.example.statefuldetector.report;

import com.example.statefuldetector.visitor.StatefulCodeDetector;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reporter that outputs issues in human-readable format to the console.
 */
public class ConsoleReporter implements StatefulIssueReporter {

	private final Set<String> reportedFiles = new HashSet<>();

	@Override
	public void reportIssues(Path filePath, List<StatefulCodeDetector.StatefulIssue> issues) {
		if (issues.isEmpty()) {
			return;
		}

		// Group issues by field name
		Map<String, List<StatefulCodeDetector.StatefulIssue>> issuesByField = issues.stream()
			.collect(java.util.stream.Collectors.groupingBy(StatefulCodeDetector.StatefulIssue::fieldName));

		// Print file header if not already printed for this file
		String fileKey = filePath.toString();
		if (reportedFiles.add(fileKey)) {
			System.out.println("Stateful code detected in: " + filePath);
		}

		// Print issues grouped by field
		for (Map.Entry<String, List<StatefulCodeDetector.StatefulIssue>> entry : issuesByField.entrySet()) {
			String fieldName = entry.getKey();
			List<StatefulCodeDetector.StatefulIssue> fieldIssues = entry.getValue();

			System.out.println("  Field: " + fieldName);
			for (StatefulCodeDetector.StatefulIssue issue : fieldIssues) {
				System.out.println("    - " + issue.message());
			}
		}
	}

}