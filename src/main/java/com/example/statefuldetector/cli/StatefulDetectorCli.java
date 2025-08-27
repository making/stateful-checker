package com.example.statefuldetector.cli;

import com.example.statefuldetector.processor.SingleFileProcessor;
import com.example.statefuldetector.processor.WorkaroundMode;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line interface for the Stateful Detector tool.
 */
@Command(name = "stateful-detector", mixinStandardHelpOptions = true, version = Version.VERSION_AS_JSON,
		description = "Detects stateful code in EJB 3 (Stateless Session Bean) and Spring Beans")
public class StatefulDetectorCli implements Callable<Integer> {

	@Parameters(index = "0", description = "Input file or directory to detect")
	Path inputPath;

	@Option(names = { "-v", "--verbose" }, description = "Enable verbose output")
	boolean verbose;

	@Option(names = { "--csv" }, description = "Output results in CSV format")
	boolean csvOutput;

	@Option(names = { "--workaround-mode" }, description = "Apply workaround by adding scope annotations (apply|diff)")
	String workaroundMode;

	@Option(names = { "--workaround-scope-name" }, description = "Scope name for workaround (default: prototype)")
	String workaroundScopeName = "prototype";

	@Option(names = { "--workaround-proxy-mode" }, description = "Proxy mode for workaround (default: TARGET_CLASS)")
	String workaroundProxyMode = "TARGET_CLASS";

	@Option(names = { "--allowed-scope" }, description = "Additional allowed scope (can be specified multiple times)")
	Set<String> allowedScopes;

	@Override
	public Integer call() throws Exception {
		// Parse and validate workaround mode
		WorkaroundMode parsedWorkaroundMode = null;
		if (workaroundMode != null) {
			try {
				parsedWorkaroundMode = WorkaroundMode.fromString(workaroundMode);
			}
			catch (IllegalArgumentException e) {
				System.err.println("Error: " + e.getMessage());
				return 1;
			}
		}

		SingleFileProcessor processor = new SingleFileProcessor();
		processor.setCsvOutput(csvOutput);

		if (parsedWorkaroundMode != null) {
			processor.setWorkaroundMode(parsedWorkaroundMode);
			processor.setWorkaroundScopeName(workaroundScopeName);
			processor.setWorkaroundProxyMode(workaroundProxyMode);
		}

		if (allowedScopes != null && !allowedScopes.isEmpty()) {
			processor.setAllowedScopes(allowedScopes);
		}

		try {
			if (inputPath.toFile().isFile()) {
				// Process single file
				processor.processFile(inputPath);
			}
			else if (inputPath.toFile().isDirectory()) {
				// Process directory
				processor.processDirectory(inputPath);
			}
			else {
				System.err.println("Error: Input path does not exist: " + inputPath);
				return 1;
			}
			return 0;
		}
		catch (Exception e) {
			if (verbose) {
				e.printStackTrace();
			}
			else {
				System.err.println("Error: " + e.getMessage());
			}
			return 1;
		}
	}

	public static void main(String[] args) {
		int exitCode = new CommandLine(new StatefulDetectorCli()).execute(args);
		System.exit(exitCode);
	}

}