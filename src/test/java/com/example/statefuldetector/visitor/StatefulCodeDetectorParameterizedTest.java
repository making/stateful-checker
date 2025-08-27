package com.example.statefuldetector.visitor;

import com.example.statefuldetector.StatefulIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StatefulCodeDetectorParameterizedTest {

	private static final String[] SPRING_ANNOTATIONS = { "@Component", "@Service", "@Repository", "@Controller",
			"@RestController" };

	private static final String[] INJECTION_ANNOTATIONS = { "@Autowired", "@Inject" };

	private static final String[] PACKAGES = { "javax", "jakarta" };

	@ParameterizedTest
	@MethodSource("springAnnotationCombinations")
	void detectStatefulFieldInSpringBeans(String springAnnotation, String injectionAnnotation, String pkg) {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.%s;
				import %s.inject.Inject;
				import org.springframework.beans.factory.annotation.Autowired;

				%s
				public class TestService {
				    private String state;

				    %s
				    private OtherService injected;

				    public void process(String data) {
				        this.state = data; // Problem: state assignment
				    }

				    public String getState() {
				        return this.state;
				    }
				}
				""".formatted(springAnnotation.substring(1), // Remove @ for import
				pkg, springAnnotation, injectionAnnotation);

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("Field assignment to 'state' in method process");
	}

	@ParameterizedTest
	@MethodSource("springAnnotationCombinations")
	void allowReadOnlyFieldsInSpringBeans(String springAnnotation, String injectionAnnotation, String pkg) {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.%s;
				import %s.inject.Inject;
				import org.springframework.beans.factory.annotation.Autowired;
				import %s.annotation.PostConstruct;
				import java.util.Map;
				import java.util.HashMap;

				%s
				public class TestService {
				    private Map<String, String> config;

				    private final String constant = "CONST";

				    %s
				    private OtherService injected;

				    @PostConstruct
				    public void init() {
				        this.config = new HashMap<>(); // OK: initialization
				    }

				    public String getConfig(String key) {
				        return config.get(key); // OK: read-only
				    }
				}
				""".formatted(springAnnotation.substring(1), pkg, pkg, springAnnotation, injectionAnnotation);

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("springAnnotationCombinations")
	void detectSameMethodStateUsage(String springAnnotation, String injectionAnnotation, String pkg) {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.%s;
				import %s.inject.Inject;
				import org.springframework.beans.factory.annotation.Autowired;

				%s
				public class TestService {
				    private String temp;

				    %s
				    private OtherService injected;

				    public String process(String input) {
				        this.temp = input.toUpperCase(); // Problem: temporary state
				        // Other processing
				        return this.temp + "_PROCESSED"; // Problem: same method usage
				    }
				}
				""".formatted(springAnnotation.substring(1), pkg, springAnnotation, injectionAnnotation);

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("Field assignment to 'temp' in method process");
	}

	@ParameterizedTest
	@MethodSource("ejbAnnotationCombinations")
	void detectStatefulFieldInEjbBeans(String injectionAnnotation, String pkg) {
		String sourceCode = """
				package com.example;

				import %s.ejb.Stateless;
				import %s.ejb.EJB;
				import %s.inject.Inject;

				@Stateless
				public class TestEjbService {
				    private int counter;

				    %s
				    private OtherService injected;

				    public void increment() {
				        counter++; // Problem: state modification
				    }

				    public int getCount() {
				        return counter;
				    }
				}
				""".formatted(pkg, pkg, pkg, injectionAnnotation);

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("Increment operation to 'counter'");
	}

	@ParameterizedTest
	@MethodSource("ejbAnnotationCombinations")
	void detectCollectionModificationInEjb(String injectionAnnotation, String pkg) {
		String sourceCode = """
				package com.example;

				import %s.ejb.Stateless;
				import %s.ejb.EJB;
				import %s.inject.Inject;
				import java.util.List;
				import java.util.ArrayList;

				@Stateless
				public class TestEjbService {
				    private List<String> results = new ArrayList<>();

				    %s
				    private OtherService injected;

				    public void addResult(String result) {
				        results.add(result); // Problem: collection modification
				    }

				    public List<String> getResults() {
				        return results;
				    }
				}
				""".formatted(pkg, pkg, pkg, injectionAnnotation);

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("Collection modification 'add' to 'results'");
	}

	@ParameterizedTest
	@MethodSource("ejbAnnotationCombinations")
	void allowFinalFieldsInEjb(String injectionAnnotation, String pkg) {
		String sourceCode = """
				package com.example;

				import %s.ejb.Stateless;
				import %s.ejb.EJB;
				import %s.inject.Inject;
				import java.util.Set;

				@Stateless
				public class TestEjbService {
				    private static final String CONSTANT = "VALUE";
				    private final Set<String> allowedValues;

				    %s
				    private OtherService injected;

				    public TestEjbService() {
				        this.allowedValues = Set.of("A", "B", "C");
				    }

				    public boolean isAllowed(String value) {
				        return allowedValues.contains(value);
				    }
				}
				""".formatted(pkg, pkg, pkg, injectionAnnotation);

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).isEmpty();
	}

	private static Stream<Arguments> springAnnotationCombinations() {
		return Stream.of(SPRING_ANNOTATIONS)
			.flatMap(springAnnotation -> Stream.of(INJECTION_ANNOTATIONS)
				.flatMap(injectionAnnotation -> Stream.of(PACKAGES)
					.map(pkg -> Arguments.of(springAnnotation, injectionAnnotation, pkg))));
	}

	private static Stream<Arguments> ejbAnnotationCombinations() {
		return Stream.of("@EJB", "@Inject")
			.flatMap(injectionAnnotation -> Stream.of(PACKAGES).map(pkg -> Arguments.of(injectionAnnotation, pkg)));
	}

	// Additional test cases for all patterns in usecase.md

	@Test
	void detectPostConstructConfigUpdate() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Component;
				import javax.annotation.PostConstruct;

				@Component
				public class BadConfigService {
				    private String config;

				    @PostConstruct
				    public void init() {
				        config = "initial"; // OK: initialization
				    }

				    public void updateConfig(String newConfig) {
				        this.config = newConfig; // Problem: runtime modification
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("Field assignment to 'config' in method updateConfig");
	}

	@Test
	void allowConstantFields() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Service;

				@Service
				public class GoodConstantService {
				    private static final String CONSTANT = "value";
				    private final String instanceConstant;

				    public GoodConstantService() {
				        this.instanceConstant = "initialized"; // OK: final field initialization
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).isEmpty();
	}

	@Test
	void allowConstructorInjection() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Component;
				import org.springframework.beans.factory.annotation.Autowired;

				@Component
				public class GoodInjectionService {
				    @Autowired
				    private UserRepository userRepository;

				    private final OrderService orderService;

				    public GoodInjectionService(OrderService orderService) {
				        this.orderService = orderService; // OK: constructor injection
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).isEmpty();
	}

	@Test
	void allowLocalVariablesOnly() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Service;
				import java.util.List;
				import java.util.ArrayList;

				@Service
				public class GoodStatelessService {

				    public String process(String input) {
				        String result = input.toUpperCase(); // OK: local variable
				        return result;
				    }

				    public List<String> batchProcess(List<String> inputs) {
				        List<String> results = new ArrayList<>(); // OK: local variable
				        for (String input : inputs) {
				            results.add(process(input));
				        }
				        return results;
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);
		if (!issues.isEmpty()) {
			System.out.println("Unexpected issues in allowLocalVariablesOnly:");
			issues.forEach(issue -> System.out.println("  " + issue.message()));
		}
		assertThat(issues).isEmpty();
	}

	@Test
	void allowThreadLocalUsage() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Component;

				@Component
				public class GoodThreadLocalService {
				    private static final ThreadLocal<String> context = new ThreadLocal<>();

				    public void withContext(String ctx, Runnable task) {
				        try {
				            context.set(ctx); // OK: ThreadLocal usage
				            task.run();
				        } finally {
				            context.remove();
				        }
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).isEmpty();
	}

	@Test
	void allowFinalCollectionFields() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Service;
				import java.util.Set;

				@Service
				public class GoodReadOnlyService {
				    private final Set<String> allowedValues;

				    public GoodReadOnlyService() {
				        this.allowedValues = Set.of("A", "B", "C"); // OK: immutable collection
				    }

				    public boolean isAllowed(String value) {
				        return allowedValues.contains(value); // OK: read-only access
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).isEmpty();
	}

	@Test
	void allowReadOnlyNonFinalFields() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Component;
				import javax.annotation.PostConstruct;
				import java.util.Map;
				import java.util.HashMap;

				@Component
				public class GoodReadOnlyNonFinalService {
				    private Map<String, String> config;

				    @PostConstruct
				    public void init() {
				        config = new HashMap<>(); // OK: initialization only
				        config.put("key", "value"); // OK: initialization
				    }

				    public String getConfig(String key) {
				        return config.get(key); // OK: read-only access
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);
		assertThat(issues).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("threadSafeCollectionCombinations")
	void allowThreadSafeCollectionModifications(String springAnnotation, String collectionType, String methodCall) {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.%s;
				import java.util.concurrent.*;
				import java.util.*;

				%s
				public class TestService {
				    private %s collection = new %s();

				    public void modifyCollection() {
				        %s; // Should NOT be flagged as error for thread-safe collections
				    }
				}
				""".formatted(springAnnotation.replace("@", ""), springAnnotation, collectionType,
				getConstructorCall(collectionType), methodCall);

		List<StatefulIssue> issues = detectIssues(sourceCode);

		// Thread-safe collections should not generate errors
		assertThat(issues).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("nonThreadSafeCollectionCombinations")
	void detectNonThreadSafeCollectionModifications(String springAnnotation, String collectionType, String methodCall) {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.%s;
				import java.util.*;

				%s
				public class TestService {
				    private %s collection = new %s();

				    public void modifyCollection() {
				        %s; // Should be flagged as error for non-thread-safe collections
				    }
				}
				""".formatted(springAnnotation.replace("@", ""), springAnnotation, collectionType,
				getConstructorCall(collectionType), methodCall);

		List<StatefulIssue> issues = detectIssues(sourceCode);

		// Non-thread-safe collections should generate errors
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("Collection modification");
	}

	private static Stream<Arguments> threadSafeCollectionCombinations() {
		return Stream.of(
				Arguments.of("@Service", "ConcurrentHashMap<String, String>", "collection.put(\"key\", \"value\")"),
				Arguments.of("@Component", "ConcurrentLinkedQueue<String>", "collection.add(\"item\")"),
				Arguments.of("@Service", "CopyOnWriteArrayList<String>", "collection.add(\"item\")"),
				Arguments.of("@Repository", "CopyOnWriteArraySet<String>", "collection.add(\"item\")"),
				Arguments.of("@Controller", "LinkedBlockingQueue<String>", "collection.add(\"item\")"),
				Arguments.of("@RestController", "ConcurrentSkipListMap<String, String>",
						"collection.put(\"key\", \"value\")"),
				Arguments.of("@Service", "ConcurrentSkipListSet<String>", "collection.add(\"item\")"),
				Arguments.of("@Component", "ArrayBlockingQueue<String>", "collection.add(\"item\")"),
				Arguments.of("@Service", "PriorityBlockingQueue<String>", "collection.add(\"item\")"));
	}

	private static Stream<Arguments> nonThreadSafeCollectionCombinations() {
		return Stream.of(Arguments.of("@Service", "HashMap<String, String>", "collection.put(\"key\", \"value\")"),
				Arguments.of("@Component", "ArrayList<String>", "collection.add(\"item\")"),
				Arguments.of("@Service", "LinkedList<String>", "collection.add(\"item\")"),
				Arguments.of("@Repository", "HashSet<String>", "collection.add(\"item\")"),
				Arguments.of("@Controller", "TreeMap<String, String>", "collection.put(\"key\", \"value\")"),
				Arguments.of("@RestController", "TreeSet<String>", "collection.add(\"item\")"));
	}

	@Test
	void allowFieldAssignmentInConfigurationPropertiesClass() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Component;
				import org.springframework.boot.context.properties.ConfigurationProperties;

				@Component
				@ConfigurationProperties(prefix = "app")
				public class AppProperties {
				    private String name;
				    private int port;

				    public void setName(String name) {
				        this.name = name; // Should NOT be flagged in @ConfigurationProperties class
				    }

				    public void setPort(int port) {
				        this.port = port; // Should NOT be flagged in @ConfigurationProperties class
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);

		// @ConfigurationProperties classes should not generate field assignment errors
		assertThat(issues).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("postConstructPackages")
	void allowFieldAssignmentInPostConstructMethod(String postConstructPackage) {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Service;
				import %s.PostConstruct;
				import java.util.ArrayList;
				import java.util.List;

				@Service
				public class InitializationService {
				    private String config;
				    private List<String> cache;
				    private int maxRetries;

				    @PostConstruct
				    public void init() {
				        this.config = "default-config"; // Should be allowed in @PostConstruct
				        this.cache = new ArrayList<>(); // Should be allowed in @PostConstruct
				        this.maxRetries = 3; // Should be allowed in @PostConstruct
				    }

				    public void updateConfig(String newConfig) {
				        this.config = newConfig; // Should be flagged - not in @PostConstruct
				    }
				}
				""".formatted(postConstructPackage);

		List<StatefulIssue> issues = detectIssues(sourceCode);

		// Only updateConfig method assignment should be flagged
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).fieldName()).isEqualTo("config");
		assertThat(issues.get(0).message()).contains("Field assignment");
		assertThat(issues.get(0).message()).contains("updateConfig");
	}

	private static Stream<Arguments> postConstructPackages() {
		return Stream.of(Arguments.of("javax.annotation"), Arguments.of("jakarta.annotation"));
	}

	@Test
	void allowFieldAssignmentInPostConstructWithAnonymousClass() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Service;
				import jakarta.annotation.PostConstruct;
				import org.springframework.transaction.PlatformTransactionManager;
				import org.springframework.transaction.TransactionStatus;
				import org.springframework.transaction.support.TransactionCallbackWithoutResult;
				import org.springframework.transaction.support.TransactionTemplate;

				@Service
				public class TransactionalInitService {
				    private final PlatformTransactionManager transactionManager;
				    private String config;
				    private Object resource;

				    public TransactionalInitService(PlatformTransactionManager transactionManager) {
				        this.transactionManager = transactionManager;
				    }

				    @PostConstruct
				    public void init() {
				        TransactionTemplate template = new TransactionTemplate(transactionManager);
				        template.execute(new TransactionCallbackWithoutResult() {
				            @Override
				            protected void doInTransactionWithoutResult(TransactionStatus status) {
				                // These assignments should be allowed in anonymous class within @PostConstruct
				                config = "initialized";
				                resource = new Object();
				            }
				        });
				    }

				    public void updateConfig(String newConfig) {
				        this.config = newConfig; // Should be flagged - not in @PostConstruct
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);

		// Only updateConfig method assignment should be flagged
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).fieldName()).isEqualTo("config");
		assertThat(issues.get(0).message()).contains("Field assignment");
		assertThat(issues.get(0).message()).contains("updateConfig");
	}

	@Test
	void allowFieldAssignmentInPostConstructWithLambda() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Service;
				import jakarta.annotation.PostConstruct;
				import java.util.function.Supplier;

				@Service
				public class LambdaInitService {
				    private String state;
				    private int counter;

				    @PostConstruct
				    public void init() {
				        Supplier<Void> initializer = () -> {
				            // These assignments should be allowed in lambda within @PostConstruct
				            this.state = "initialized";
				            this.counter = 0;
				            return null;
				        };
				        initializer.get();
				    }

				    public void updateState(String newState) {
				        this.state = newState; // Should be flagged - not in @PostConstruct
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);

		// Only updateState method assignment should be flagged
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).fieldName()).isEqualTo("state");
		assertThat(issues.get(0).message()).contains("Field assignment");
		assertThat(issues.get(0).message()).contains("updateState");
	}

	@Test
	void allowFieldAssignmentInAfterPropertiesSetMethod() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Component;
				import org.springframework.beans.factory.InitializingBean;
				import java.util.HashMap;
				import java.util.Map;

				@Component
				public class InitializingComponent implements InitializingBean {
				    private Map<String, Object> cache;
				    private String serviceName;

				    @Override
				    public void afterPropertiesSet() {
				        this.cache = new HashMap<>(); // Should be allowed in afterPropertiesSet
				        this.serviceName = "MyService"; // Should be allowed in afterPropertiesSet
				    }

				    public void updateCache(String key, Object value) {
				        cache.put(key, value); // Should be flagged - collection modification
				    }

				    public void changeServiceName(String name) {
				        this.serviceName = name; // Should be flagged - field assignment
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);

		// updateCache and changeServiceName should be flagged
		assertThat(issues).hasSize(2);
		assertThat(issues).anyMatch(
				issue -> issue.fieldName().equals("cache") && issue.message().contains("Collection modification"));
		assertThat(issues)
			.anyMatch(issue -> issue.fieldName().equals("serviceName") && issue.message().contains("Field assignment"));
	}

	@Test
	void detectFieldAssignmentInRegularComponentClass() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Component;

				@Component
				public class RegularComponent {
				    private String state;

				    public void setState(String state) {
				        this.state = state; // Should be flagged in regular @Component class
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);

		// Regular component classes should generate field assignment errors
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("Field assignment");
		assertThat(issues.get(0).fieldName()).isEqualTo("state");
	}

	private String getConstructorCall(String collectionType) {
		if (collectionType.contains("ArrayBlockingQueue")) {
			return "ArrayBlockingQueue<>(10)";
		}
		if (collectionType.contains("<")) {
			return collectionType.substring(0, collectionType.indexOf('<')) + "<>()";
		}
		return collectionType + "()";
	}

	@ParameterizedTest
	@MethodSource("allowedScopeCombinations")
	void allowStatefulCodeInAllowedScopes(String springAnnotation, String scopeAnnotation) {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.%s;
				import org.springframework.context.annotation.Scope;
				import org.springframework.web.context.annotation.RequestScope;
				import org.springframework.web.context.WebApplicationContext;
				import java.util.*;

				%s
				%s
				public class ScopedService {
				    private String state;
				    private int counter;
				    private List<String> items = new ArrayList<>();

				    public void setState(String state) {
				        this.state = state; // Should NOT be flagged in allowed scopes
				    }

				    public void increment() {
				        counter++; // Should NOT be flagged in allowed scopes
				    }

				    public void addItem(String item) {
				        items.add(item); // Should NOT be flagged in allowed scopes
				    }
				}
				""".formatted(springAnnotation.replace("@", ""), springAnnotation, scopeAnnotation);

		List<StatefulIssue> issues = detectIssues(sourceCode);

		// Allowed scope classes should not generate any errors
		assertThat(issues).isEmpty();
	}

	private static Stream<Arguments> allowedScopeCombinations() {
		return Stream.of(Arguments.of("@Service", "@Scope(\"prototype\")"),
				Arguments.of("@Component", "@Scope(value = \"prototype\")"),
				Arguments.of("@Repository", "@Scope(scopeName = \"prototype\")"),
				Arguments.of("@Controller", "@Scope(\"request\")"),
				Arguments.of("@RestController", "@Scope(value = \"request\")"),
				Arguments.of("@Service", "@RequestScope"),
				Arguments.of("@Component", "@Scope(WebApplicationContext.SCOPE_REQUEST)"));
	}

	@ParameterizedTest
	@MethodSource("disallowedScopeCombinations")
	void detectStatefulCodeInDisallowedScopes(String springAnnotation, String scopeAnnotation) {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.%s;
				import org.springframework.context.annotation.Scope;
				import org.springframework.web.context.WebApplicationContext;
				import java.util.*;

				%s
				%s
				public class ScopedService {
				    private String state;
				    private int counter;
				    private List<String> items = new ArrayList<>();

				    public void setState(String state) {
				        this.state = state; // Should be flagged in disallowed scopes
				    }

				    public void increment() {
				        counter++; // Should be flagged in disallowed scopes
				    }

				    public void addItem(String item) {
				        items.add(item); // Should be flagged in disallowed scopes
				    }
				}
				""".formatted(springAnnotation.replace("@", ""), springAnnotation, scopeAnnotation);

		List<StatefulIssue> issues = detectIssues(sourceCode);

		// Disallowed scope classes should generate errors
		assertThat(issues).hasSize(3); // Field assignment + increment + collection
										// modification
		assertThat(issues).extracting(StatefulIssue::fieldName).containsExactlyInAnyOrder("state", "counter", "items");
	}

	private static Stream<Arguments> disallowedScopeCombinations() {
		return Stream.of(Arguments.of("@Service", "@Scope(\"session\")"),
				Arguments.of("@Component", "@Scope(value = \"session\")"),
				Arguments.of("@Repository", "@Scope(WebApplicationContext.SCOPE_SESSION)"),
				Arguments.of("@Controller", "@Scope(\"application\")"),
				Arguments.of("@RestController", "@Scope(value = \"application\")"),
				Arguments.of("@Service", "@Scope(WebApplicationContext.SCOPE_APPLICATION)"),
				Arguments.of("@Component", "@Scope(\"singleton\")") // singleton should
																	// also be detected
		);
	}

	@ParameterizedTest
	@MethodSource("additionalAllowedScopeCombinations")
	void allowStatefulCodeWithAdditionalAllowedScopes(String springAnnotation, String scopeAnnotation) {
		// Test with additional allowed scopes like "thread"
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.%s;
				import org.springframework.context.annotation.Scope;
				import org.springframework.web.context.annotation.RequestScope;
				import org.springframework.web.context.WebApplicationContext;
				import java.util.ArrayList;
				import java.util.List;

				%s
				%s
				public class TestService {
				    private String state;
				    private int counter;
				    private List<String> items = new ArrayList<>();

				    public void setState(String state) {
				        this.state = state;
				    }

				    public void increment() {
				        counter++;
				    }

				    public void addItem(String item) {
				        items.add(item);
				    }
				}
				""".formatted(springAnnotation.replace("@", ""), springAnnotation, scopeAnnotation);

		// Detect issues with thread scope as additional allowed scope
		Set<String> additionalScopes = Set.of("thread");
		List<StatefulIssue> issues = detectIssuesWithAdditionalScopes(sourceCode, additionalScopes);

		// Additional allowed scope classes should not generate any errors
		assertThat(issues).isEmpty();
	}

	private static Stream<Arguments> additionalAllowedScopeCombinations() {
		// Thread scope variations
		return Stream.of(Arguments.of("@Service", "@Scope(\"thread\")"),
				Arguments.of("@Component", "@Scope(value = \"thread\")"),
				Arguments.of("@Repository", "@Scope(scopeName = \"thread\")"));
	}

	@Test
	void allowFieldAssignmentInAutowiredSetters() {
		String sourceCode = """
				package com.example;

				import org.springframework.stereotype.Service;
				import org.springframework.beans.factory.annotation.Autowired;

				@Service
				public class TestService {
				    private String fieldWithAutowiredSetter;
				    private String fieldWithoutAutowired;
				    private String fieldWithAutowiredNonSetter;

				    @Autowired
				    public void setFieldWithAutowiredSetter(String value) {
				        this.fieldWithAutowiredSetter = value; // Should be allowed
				    }

				    public void setFieldWithoutAutowired(String value) {
				        this.fieldWithoutAutowired = value; // Should be detected as error
				    }

				    @Autowired
				    public void configureFieldWithAutowiredNonSetter(String value) {
				        this.fieldWithAutowiredNonSetter = value; // Should be detected as error (not a setter)
				    }

				    public void businessMethod(String data) {
				        this.fieldWithAutowiredSetter = data; // Should be detected as error (not in setter)
				    }
				}
				""";

		List<StatefulIssue> issues = detectIssues(sourceCode);

		// Should detect 3 issues: non-autowired setter, autowired non-setter, business
		// method assignment
		assertThat(issues).hasSize(3);
		assertThat(issues).extracting(StatefulIssue::fieldName)
			.containsExactlyInAnyOrder("fieldWithoutAutowired", "fieldWithAutowiredNonSetter",
					"fieldWithAutowiredSetter");
	}

	private List<StatefulIssue> detectIssues(String sourceCode) {
		// Add stub class definitions
		String stubClasses = """
				package com.example;
				public class OtherService {}
				class UserRepository {}
				class OrderService {}
				""";

		JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath());

		List<J.CompilationUnit> cus = parser.build()
			.parse(stubClasses, sourceCode)
			.map(J.CompilationUnit.class::cast)
			.toList();

		// Process the main source file (second one)
		J.CompilationUnit cu = cus.get(1);

		StatefulCodeDetector detector = new StatefulCodeDetector();
		detector.visit(cu, new InMemoryExecutionContext());
		return detector.getIssues();
	}

	private List<StatefulIssue> detectIssuesWithAdditionalScopes(String sourceCode, Set<String> additionalScopes) {
		// Add stub class definitions
		String stubClasses = """
				package com.example;
				public class OtherService {}
				class UserRepository {}
				class OrderService {}
				""";

		JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath());

		List<J.CompilationUnit> cus = parser.build()
			.parse(stubClasses, sourceCode)
			.map(J.CompilationUnit.class::cast)
			.toList();

		StatefulCodeDetector detector = new StatefulCodeDetector();
		detector.setAllowedScopes(additionalScopes);

		// Find the test service class
		J.CompilationUnit testServiceCu = cus.stream()
			.filter(cu -> cu.getClasses().stream().anyMatch(clazz -> "TestService".equals(clazz.getSimpleName())))
			.findFirst()
			.orElse(null);

		if (testServiceCu != null) {
			detector.visit(testServiceCu, new InMemoryExecutionContext());
		}

		return detector.getIssues();
	}

}