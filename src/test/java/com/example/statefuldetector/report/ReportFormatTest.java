package com.example.statefuldetector.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportFormatTest {

	@Test
	void testFromString_ValidValues() {
		assertThat(ReportFormat.fromString("default")).isEqualTo(ReportFormat.DEFAULT);
		assertThat(ReportFormat.fromString("DEFAULT")).isEqualTo(ReportFormat.DEFAULT);
		assertThat(ReportFormat.fromString("csv")).isEqualTo(ReportFormat.CSV);
		assertThat(ReportFormat.fromString("CSV")).isEqualTo(ReportFormat.CSV);
	}

	@Test
	void testFromString_NullValue() {
		assertThat(ReportFormat.fromString(null)).isEqualTo(ReportFormat.DEFAULT);
	}

	@Test
	void testFromString_InvalidValue() {
		assertThatThrownBy(() -> ReportFormat.fromString("json")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Unsupported report format: json. Supported formats: default, csv");
	}

	@Test
	void testGetValue() {
		assertThat(ReportFormat.DEFAULT.getValue()).isEqualTo("default");
		assertThat(ReportFormat.CSV.getValue()).isEqualTo("csv");
	}

	@Test
	void testCreateReporter() {
		StatefulIssueReporter defaultReporter = ReportFormat.DEFAULT.createReporter();
		assertThat(defaultReporter).isInstanceOf(DefaultReporter.class);

		StatefulIssueReporter csvReporter = ReportFormat.CSV.createReporter();
		assertThat(csvReporter).isInstanceOf(CsvReporter.class);
	}

}