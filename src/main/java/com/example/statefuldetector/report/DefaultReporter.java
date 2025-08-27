package com.example.statefuldetector.report;

import com.example.statefuldetector.StatefulIssue;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;

/**
 * Default reporter that outputs issues in human-readable format to the console.
 */
class DefaultReporter implements StatefulIssueReporter {

	private final Set<String> reportedFiles = new HashSet<>();

	@Override
	public void reportIssues(Path filePath, List<StatefulIssue> issues) {
		if (issues.isEmpty()) {
			return;
		}

		// Group issues by field name
		Map<String, List<StatefulIssue>> issuesByField = issues.stream().collect(groupingBy(StatefulIssue::fieldName));

		// Print file header if not already printed for this file
		String fileKey = filePath.toString();
		if (reportedFiles.add(fileKey)) {
			System.out.println("Stateful code detected in: " + filePath);
		}

		// Print issues grouped by field
		for (Map.Entry<String, List<StatefulIssue>> entry : issuesByField.entrySet()) {
			String fieldName = entry.getKey();
			List<StatefulIssue> fieldIssues = entry.getValue();

			System.out.println("  Field: " + fieldName);
			for (StatefulIssue issue : fieldIssues) {
				System.out.println("    - " + issue.message());
			}
		}
	}

}