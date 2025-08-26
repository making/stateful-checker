package com.example.statefulchecker.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.example.statefulchecker.processor.SingleFileProcessor;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line interface for the Stateful Checker tool.
 */
@Command(name = "stateful-checker", mixinStandardHelpOptions = true, version = "1.0.0",
		description = "Checks for stateful code in EJB 3 (Stateless Session Bean) and Spring Beans")
public class StatefulCheckerCli implements Callable<Integer> {

	@Parameters(index = "0", description = "Input file or directory to check")
	private Path inputPath;

	@Option(names = { "-v", "--verbose" }, description = "Enable verbose output")
	private boolean verbose;

	@Override
	public Integer call() throws Exception {
		SingleFileProcessor processor = new SingleFileProcessor();

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
		int exitCode = new CommandLine(new StatefulCheckerCli()).execute(args);
		System.exit(exitCode);
	}

}