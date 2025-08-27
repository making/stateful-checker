package com.example.statefuldetector.report;

import com.example.statefuldetector.StatefulIssue;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface for reporting stateful code detection results in different formats.
 */
public interface StatefulIssueReporter {

	/**
	 * Report issues found in a file.
	 * @param filePath the path of the file being processed
	 * @param issues the list of issues detected
	 */
	void reportIssues(Path filePath, List<StatefulIssue> issues);

	/**
	 * Called when processing starts to allow initialization.
	 */
	default void initialize() {
		// Default implementation does nothing
	}

	/**
	 * Called when all processing is complete to allow cleanup.
	 */
	default void finish() {
		// Default implementation does nothing
	}

}