package com.example.statefuldetector;

/**
 * Standard exit codes for the Stateful Detector CLI tool. Following Unix conventions for
 * command-line tools.
 */
public final class ExitCodes {

	/**
	 * Success - no errors occurred.
	 */
	public static final int SUCCESS = 0;

	/**
	 * General error - command failed due to usage error, file not found, etc.
	 */
	public static final int ERROR = 1;

	/**
	 * Data format error - stateful issues detected when --fail-on-detection is enabled.
	 * Uses exit code 65 (EX_DATAERR) from BSD sysexits.h convention.
	 */
	public static final int STATEFUL_ISSUES_DETECTED = 65;

	private ExitCodes() {
		// Utility class - no instantiation
	}

}