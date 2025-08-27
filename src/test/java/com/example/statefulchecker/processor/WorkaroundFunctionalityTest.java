package com.example.statefulchecker.processor;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkaroundFunctionalityTest {

	private FileSystem fileSystem;

	private Path testFile;

	private SingleFileProcessor processor;

	private ByteArrayOutputStream outputStream;

	private PrintStream originalOut;

	@BeforeEach
	void setUp() throws IOException {
		fileSystem = Jimfs.newFileSystem(Configuration.unix());
		testFile = fileSystem.getPath("/TestService.java");
		processor = new SingleFileProcessor();

		// Capture System.out
		outputStream = new ByteArrayOutputStream();
		originalOut = System.out;
		System.setOut(new PrintStream(outputStream));
	}

	@AfterEach
	void tearDown() throws IOException {
		System.setOut(originalOut);
		fileSystem.close();
	}

	@Test
	void workaroundApplyModeAddsProperScopeAnnotation() throws IOException {
		String originalSource = """
				package com.example;

				import org.springframework.stereotype.Service;

				@Service
				public class TestService {
					private String state;

					public void setState(String state) {
						this.state = state;
					}
				}
				""";

		Files.writeString(testFile, originalSource);

		processor.setWorkaroundMode(WorkaroundMode.APPLY);
		processor.setWorkaroundScopeName("prototype");
		processor.setWorkaroundProxyMode("TARGET_CLASS");

		processor.processFile(testFile);

		String modifiedSource = Files.readString(testFile);

		String expectedSource = """
				package com.example;

				import org.springframework.context.annotation.Scope;
				import org.springframework.context.annotation.ScopedProxyMode;
				import org.springframework.stereotype.Service;

				@Service
				@Scope(scopeName = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)
				public class TestService {
					private String state;

					public void setState(String state) {
						this.state = state;
					}
				}
				""";

		assertThat(modifiedSource).isEqualTo(expectedSource);

		String output = outputStream.toString();
		assertThat(output).contains("Applied workaround to: " + testFile);
	}

	@Test
	void workaroundDiffModeShowsDifference() throws IOException {
		String originalSource = """
				package com.example;

				import org.springframework.stereotype.Component;

				@Component
				public class TestComponent {
					private int counter;

					public void increment() {
						counter++;
					}
				}
				""";

		Files.writeString(testFile, originalSource);

		processor.setWorkaroundMode(WorkaroundMode.DIFF);
		processor.setWorkaroundScopeName("request");
		processor.setWorkaroundProxyMode("INTERFACES");

		processor.processFile(testFile);

		// File should remain unchanged in diff mode
		String fileContent = Files.readString(testFile);
		assertThat(fileContent).isEqualTo(originalSource);

		String expectedDiff = """
				--- %s	""".formatted(testFile) + "\n" + """
				+++ %s	""".formatted(testFile) + "\n" + """
				@@ -1,6 +1,9 @@
				 package com.example;

				+import org.springframework.context.annotation.Scope;
				+import org.springframework.context.annotation.ScopedProxyMode;
				 import org.springframework.stereotype.Component;

				 @Component
				+@Scope(scopeName = "request", proxyMode = ScopedProxyMode.INTERFACES)
				 public class TestComponent {
				 	private int counter;
				""";

		String output = outputStream.toString();
		assertThat(output).contains("--- " + testFile);
		assertThat(output).contains("+++ " + testFile);
		assertThat(output).contains("+import org.springframework.context.annotation.Scope;");
		assertThat(output).contains("+import org.springframework.context.annotation.ScopedProxyMode;");
		assertThat(output).contains("+@Scope(scopeName = \"request\", proxyMode = ScopedProxyMode.INTERFACES)");
	}

	@Test
	void workaroundSkipsClassesWithExistingScope() throws IOException {
		String originalSource = """
				package com.example;

				import org.springframework.stereotype.Service;
				import org.springframework.context.annotation.Scope;

				@Service
				@Scope("singleton")
				public class TestService {
					private String state;

					public void setState(String state) {
						this.state = state;
					}
				}
				""";

		Files.writeString(testFile, originalSource);

		processor.setWorkaroundMode(WorkaroundMode.APPLY);
		processor.setWorkaroundScopeName("prototype");

		processor.processFile(testFile);

		String modifiedSource = Files.readString(testFile);

		// Should remain unchanged since @Scope already exists
		assertThat(modifiedSource).isEqualTo(originalSource);
		assertThat(modifiedSource).contains("@Scope(\"singleton\")");
		assertThat(modifiedSource).doesNotContain("prototype");
	}

	@Test
	void workaroundHandlesMultipleSpringAnnotations() throws IOException {
		String originalSource = """
				package com.example;

				import org.springframework.stereotype.Repository;
				import org.springframework.web.bind.annotation.RestController;

				@Repository
				@RestController
				public class TestController {
					private String data;

					public void setData(String data) {
						this.data = data;
					}
				}
				""";

		Files.writeString(testFile, originalSource);

		processor.setWorkaroundMode(WorkaroundMode.APPLY);
		processor.setWorkaroundScopeName("prototype");

		processor.processFile(testFile);

		String modifiedSource = Files.readString(testFile);

		String expectedSource = """
				package com.example;

				import org.springframework.context.annotation.Scope;
				import org.springframework.context.annotation.ScopedProxyMode;
				import org.springframework.stereotype.Repository;
				import org.springframework.web.bind.annotation.RestController;

				@Repository
				@Scope(scopeName = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)
				@RestController
				public class TestController {
					private String data;

					public void setData(String data) {
						this.data = data;
					}
				}
				""";

		assertThat(modifiedSource).isEqualTo(expectedSource);
	}

	@Test
	void workaroundRespectsIndentation() throws IOException {
		String originalSource = """
				package com.example;

				import org.springframework.stereotype.Service;

				    @Service
				    public class TestService {
				        private String state;

				        public void setState(String state) {
				            this.state = state;
				        }
				    }
				""";

		Files.writeString(testFile, originalSource);

		processor.setWorkaroundMode(WorkaroundMode.APPLY);
		processor.setWorkaroundScopeName("prototype");

		processor.processFile(testFile);

		String modifiedSource = Files.readString(testFile);

		String expectedSource = """
				package com.example;

				import org.springframework.context.annotation.Scope;
				import org.springframework.context.annotation.ScopedProxyMode;
				import org.springframework.stereotype.Service;

				    @Service
				    @Scope(scopeName = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)
				    public class TestService {
				        private String state;

				        public void setState(String state) {
				            this.state = state;
				        }
				    }
				""";

		assertThat(modifiedSource).isEqualTo(expectedSource);
	}

	@Test
	void workaroundDoesNothingForNonStatefulClasses() throws IOException {
		String originalSource = """
				package com.example;

				import org.springframework.stereotype.Service;
				import org.springframework.beans.factory.annotation.Autowired;

				@Service
				public class GoodService {
					@Autowired
					private UserRepository userRepository;

					public String process(String input) {
						return userRepository.findByName(input);
					}
				}
				""";

		Files.writeString(testFile, originalSource);

		processor.setWorkaroundMode(WorkaroundMode.APPLY);
		processor.setWorkaroundScopeName("prototype");

		processor.processFile(testFile);

		String modifiedSource = Files.readString(testFile);

		// Should remain unchanged since no stateful issues
		assertThat(modifiedSource).isEqualTo(originalSource);
		assertThat(modifiedSource).doesNotContain("@Scope");
	}

	@Test
	void workaroundWithNoIssuesShowsNoDiff() throws IOException {
		String originalSource = """
				package com.example;

				import org.springframework.stereotype.Component;

				@Component
				public class GoodComponent {
					private final String constant = "VALUE";

					public String getConstant() {
						return constant;
					}
				}
				""";

		Files.writeString(testFile, originalSource);

		processor.setWorkaroundMode(WorkaroundMode.DIFF);

		processor.processFile(testFile);

		String output = outputStream.toString();
		// No diff should be shown for classes without stateful issues
		assertThat(output).isEmpty();
	}

}