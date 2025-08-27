package com.example.statefuldetector.report;

import java.util.function.Supplier;

/**
 * Represents the format for output reports with corresponding reporter implementations.
 */
public enum ReportFormat {

	DEFAULT("default", DefaultReporter::new), CSV("csv", CsvReporter::new);

	private final String value;

	private final Supplier<StatefulIssueReporter> reporterSupplier;

	ReportFormat(String value, Supplier<StatefulIssueReporter> reporterSupplier) {
		this.value = value;
		this.reporterSupplier = reporterSupplier;
	}

	public String getValue() {
		return value;
	}

	/**
	 * Create a new reporter instance for this format.
	 * @return a new reporter instance
	 */
	public StatefulIssueReporter createReporter() {
		return reporterSupplier.get();
	}

	/**
	 * Parse string to ReportFormat enum.
	 * @param value the string value
	 * @return the corresponding ReportFormat
	 * @throws IllegalArgumentException if the value is not supported
	 */
	public static ReportFormat fromString(String value) {
		if (value == null) {
			return DEFAULT;
		}

		for (ReportFormat format : ReportFormat.values()) {
			if (format.value.equalsIgnoreCase(value)) {
				return format;
			}
		}
		throw new IllegalArgumentException("Unsupported report format: " + value + ". Supported formats: default, csv");
	}

}