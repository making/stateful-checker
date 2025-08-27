package com.example.statefuldetector.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkaroundModeTest {

	@Test
	void fromStringApply() {
		WorkaroundMode mode = WorkaroundMode.fromString("apply");
		assertThat(mode).isEqualTo(WorkaroundMode.APPLY);
		assertThat(mode.getValue()).isEqualTo("apply");
	}

	@Test
	void fromStringDiff() {
		WorkaroundMode mode = WorkaroundMode.fromString("diff");
		assertThat(mode).isEqualTo(WorkaroundMode.DIFF);
		assertThat(mode.getValue()).isEqualTo("diff");
	}

	@Test
	void fromStringNull() {
		WorkaroundMode mode = WorkaroundMode.fromString(null);
		assertThat(mode).isNull();
	}

	@Test
	void fromStringInvalid() {
		assertThatThrownBy(() -> WorkaroundMode.fromString("invalid")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Invalid workaround mode: invalid. Valid values are: apply, diff");
	}

	@Test
	void fromStringCaseSensitive() {
		assertThatThrownBy(() -> WorkaroundMode.fromString("APPLY")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Invalid workaround mode: APPLY. Valid values are: apply, diff");
	}

	@Test
	void getAllValues() {
		WorkaroundMode[] values = WorkaroundMode.values();
		assertThat(values).hasSize(2);
		assertThat(values).contains(WorkaroundMode.APPLY, WorkaroundMode.DIFF);
	}

}