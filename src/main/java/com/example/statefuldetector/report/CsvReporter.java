package com.example.statefuldetector.report;

import com.example.statefuldetector.StatefulIssue;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reporter that outputs issues in CSV format.
 */
class CsvReporter implements StatefulIssueReporter {

	private boolean headerPrinted = false;

	private final Set<String> printedRecords = new HashSet<>();

	@Override
	public void initialize() {
		if (!headerPrinted) {
			System.out.println("File,Field,Issue,Level,Method");
			headerPrinted = true;
		}
	}

	@Override
	public void reportIssues(Path filePath, List<StatefulIssue> issues) {
		for (StatefulIssue issue : issues) {
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
		else if (message.startsWith("Increment")) {
			return "Increment operation";
		}
		else if (message.startsWith("Decrement")) {
			return "Decrement operation";
		}
		else if (message.startsWith("Compound assignment")) {
			return "Compound assignment";
		}
		else {
			// Fallback: extract first part before "to"
			int toIndex = message.indexOf(" to ");
			if (toIndex != -1) {
				return message.substring(0, toIndex);
			}
			return message;
		}
	}

	/**
	 * Escape CSV values by wrapping in quotes if they contain special characters.
	 */
	private String escapeCsv(String value) {
		if (value == null) {
			return "";
		}
		// If value contains comma, quote, or newline, wrap in quotes and escape
		// internal quotes
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

}