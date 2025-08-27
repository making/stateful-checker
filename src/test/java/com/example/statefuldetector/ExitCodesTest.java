package com.example.statefuldetector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExitCodesTest {

	@Test
	void testExitCodeValues() {
		assertThat(ExitCodes.SUCCESS).isEqualTo(0);
		assertThat(ExitCodes.ERROR).isEqualTo(1);
		assertThat(ExitCodes.STATEFUL_ISSUES_DETECTED).isEqualTo(65);
	}

	@Test
	void testAllExitCodesAreDifferent() {
		assertThat(ExitCodes.SUCCESS).isNotEqualTo(ExitCodes.ERROR);
		assertThat(ExitCodes.SUCCESS).isNotEqualTo(ExitCodes.STATEFUL_ISSUES_DETECTED);
		assertThat(ExitCodes.ERROR).isNotEqualTo(ExitCodes.STATEFUL_ISSUES_DETECTED);
	}

}