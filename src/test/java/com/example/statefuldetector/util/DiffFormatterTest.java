package com.example.statefuldetector.util;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiffFormatterTest {

	@Test
	void shouldDetectNoChanges() {
		// Given
		String original = "public class Test {}";
		String modified = "public class Test {}";

		// When
		String result = DiffFormatter.generateDiff("Test.java", original, modified);

		// Then
		assertThat(result).contains("No changes detected");
	}

	@Test
	void shouldGenerateUnifiedDiff() {
		// Given
		String original = """
				package com.example;

				import javax.ejb.Stateless;

				@Stateless
				public class Service {
				}
				""";

		String modified = """
				package com.example;

				import org.springframework.stereotype.Service;

				@Service
				public class Service {
				}
				""";

		// When
		String result = DiffFormatter.generateDiff("Service.java", original, modified);

		// Then
		assertThat(result).contains("--- Service.java");
		assertThat(result).contains("+++ Service.java");
		assertThat(result).contains("-import javax.ejb.Stateless;");
		assertThat(result).contains("+import org.springframework.stereotype.Service;");
		assertThat(result).contains("-@Stateless");
		assertThat(result).contains("+@Service");
	}

	@ParameterizedTest
	@MethodSource("provideFileSystemConfigurations")
	void shouldHandleDifferentPathFormats(Configuration fsConfig) throws IOException {
		// Given
		try (FileSystem fs = Jimfs.newFileSystem(fsConfig)) {
			Path testFile;
			if (fsConfig == Configuration.windows()) {
				testFile = fs.getPath("C:\\temp\\Test.java");
			}
			else {
				testFile = fs.getPath("/tmp/Test.java");
			}

			Files.createDirectories(testFile.getParent());

			String original = "class Test {}";
			String modified = "public class Test {}";

			// When
			String result = DiffFormatter.generateUnifiedDiff(original, modified, testFile);

			// Then
			assertThat(result).contains("class Test");
			assertThat(result).contains("public class Test");
			assertThat(result).isNotEmpty();
		}
	}

	private static Stream<Arguments> provideFileSystemConfigurations() {
		return Stream.of(Arguments.of(Configuration.unix()), Arguments.of(Configuration.windows()));
	}

}