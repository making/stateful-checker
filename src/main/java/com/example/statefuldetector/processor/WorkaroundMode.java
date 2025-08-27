package com.example.statefuldetector.processor;

/**
 * Enum representing the different modes for workaround functionality.
 */
public enum WorkaroundMode {

	/**
	 * Apply workaround by modifying files directly.
	 */
	APPLY("apply"),

	/**
	 * Show unified diff of proposed changes without modifying files.
	 */
	DIFF("diff");

	private final String value;

	WorkaroundMode(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	/**
	 * Parse workaround mode from string value.
	 * @param value the string value
	 * @return the corresponding WorkaroundMode
	 * @throws IllegalArgumentException if the value is not valid
	 */
	public static WorkaroundMode fromString(String value) {
		if (value == null) {
			return null;
		}
		for (WorkaroundMode mode : values()) {
			if (mode.value.equals(value)) {
				return mode;
			}
		}
		throw new IllegalArgumentException("Invalid workaround mode: " + value + ". Valid values are: apply, diff");
	}

}