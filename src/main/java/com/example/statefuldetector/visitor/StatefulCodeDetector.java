package com.example.statefuldetector.visitor;

import com.example.statefuldetector.IssueLevel;
import com.example.statefuldetector.StatefulIssue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/**
 * Detector that finds and reports stateful code patterns in Spring and EJB beans.
 */
public class StatefulCodeDetector extends JavaIsoVisitor<ExecutionContext> {

	private static final Set<String> BEAN_ANNOTATIONS = Set.of("Component", "Service", "Repository", "Controller",
			"RestController", "Configuration", "Bean", "Stateless", "Singleton");

	private static final Set<String> INJECTION_ANNOTATIONS = Set.of("Autowired", "Inject", "Resource", "Value",
			"Qualifier", "EJB", "PersistenceContext", "PersistenceUnit");

	private boolean isInBeanClass = false;

	private boolean isConfigurationPropertiesClass = false;

	private boolean isAllowedScope = false;

	private String currentClassName = "";

	private final Set<String> finalFields = new HashSet<>();

	private final Set<String> injectedFields = new HashSet<>();

	private final Set<String> staticFinalFields = new HashSet<>();

	private final Set<String> declaredFields = new HashSet<>();

	private final Map<String, String> fieldTypes = new HashMap<>();

	private final Map<String, List<Issue>> statefulIssues = new HashMap<>();

	private boolean inConstructor = false;

	private boolean inPostConstruct = false;

	private boolean inStaticInitializer = false;

	private String currentMethodName = "";

	private Set<String> additionalAllowedScopes;

	public boolean hasStatefulIssues() {
		return !statefulIssues.isEmpty();
	}

	public void setAllowedScopes(Set<String> allowedScopes) {
		this.additionalAllowedScopes = allowedScopes;
	}

	@Override
	public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
		// Check if class has bean annotations
		boolean wasInBeanClass = isInBeanClass;
		String previousClassName = currentClassName;

		isInBeanClass = hasAnyBeanAnnotation(classDecl);
		isConfigurationPropertiesClass = hasAnnotation(classDecl, "ConfigurationProperties");
		isAllowedScope = hasAllowedScope(classDecl);
		currentClassName = classDecl.getSimpleName();

		// Reset field tracking for this class
		finalFields.clear();
		injectedFields.clear();
		staticFinalFields.clear();
		declaredFields.clear();
		statefulIssues.clear();

		J.ClassDeclaration result = super.visitClassDeclaration(classDecl, ctx);

		isInBeanClass = wasInBeanClass;
		currentClassName = previousClassName;
		return result;
	}

	@Override
	public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
			ExecutionContext ctx) {
		if (!isInBeanClass) {
			return super.visitVariableDeclarations(multiVariable, ctx);
		}

		// Check if this is a field declaration (not in a method)
		if (getCursor().getParentTreeCursor().getValue() instanceof J.Block
				&& getCursor().getParentTreeCursor().getParentTreeCursor().getValue() instanceof J.ClassDeclaration) {

			for (J.VariableDeclarations.NamedVariable variable : multiVariable.getVariables()) {
				String fieldName = variable.getSimpleName();
				declaredFields.add(fieldName);

				// Store field type information
				if (multiVariable.getTypeExpression() != null) {
					String typeStr = multiVariable.getTypeExpression().toString();
					fieldTypes.put(fieldName, typeStr);
				}

				// Check modifiers
				boolean isFinal = multiVariable.hasModifier(J.Modifier.Type.Final);
				boolean isStatic = multiVariable.hasModifier(J.Modifier.Type.Static);

				if (isFinal) {
					finalFields.add(fieldName);
					if (isStatic) {
						staticFinalFields.add(fieldName);
					}
				}

				// Check for injection annotations
				if (hasAnyInjectionAnnotation(multiVariable)) {
					injectedFields.add(fieldName);
				}
			}
		}

		return super.visitVariableDeclarations(multiVariable, ctx);
	}

	@Override
	public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
		if (!isInBeanClass) {
			return super.visitMethodDeclaration(method, ctx);
		}

		// Track current method
		String previousMethodName = currentMethodName;
		// Don't update method name if we're in PostConstruct context (for anonymous
		// classes)
		if (!inPostConstruct) {
			currentMethodName = method.getSimpleName();
		}

		// Check if this is a constructor
		boolean wasInConstructor = inConstructor;
		boolean wasInPostConstruct = inPostConstruct;

		inConstructor = method.isConstructor();

		// Check if this is a @PostConstruct method or afterPropertiesSet
		// Don't override if we're already in a PostConstruct context (for anonymous
		// classes)
		if (!inPostConstruct) {
			inPostConstruct = hasAnnotation(method, "PostConstruct")
					|| "afterPropertiesSet".equals(method.getSimpleName());
		}

		J.MethodDeclaration result = super.visitMethodDeclaration(method, ctx);

		// Only restore if this was the method that set the flag
		if ((hasAnnotation(method, "PostConstruct") || "afterPropertiesSet".equals(method.getSimpleName()))
				&& !wasInPostConstruct) {
			inPostConstruct = wasInPostConstruct;
		}

		inConstructor = wasInConstructor;
		currentMethodName = previousMethodName;

		return result;
	}

	@Override
	public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
		if (!isInBeanClass) {
			return super.visitAssignment(assignment, ctx);
		}

		// Check if this is an instance field assignment
		if (assignment.getVariable() instanceof J.FieldAccess fieldAccess) {
			if (fieldAccess.getTarget() instanceof J.Identifier
					&& ((J.Identifier) fieldAccess.getTarget()).getSimpleName().equals("this")) {
				handleFieldAssignment(fieldAccess.getSimpleName());
			}
		}
		else if (assignment.getVariable() instanceof J.Identifier identifier) {
			// Direct field access without 'this'
			if (isInstanceField(identifier.getSimpleName())) {
				handleFieldAssignment(identifier.getSimpleName());
			}
		}

		return super.visitAssignment(assignment, ctx);
	}

	@Override
	public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
		if (!isInBeanClass) {
			return super.visitMethodInvocation(method, ctx);
		}

		// Check for collection modifications
		if (method.getSelect() != null) {
			String methodName = method.getSimpleName();
			if (isCollectionModificationMethod(methodName)) {
				String fieldName = getFieldNameFromExpression(method.getSelect());
				if (fieldName != null && isInstanceField(fieldName) && !isAllowedField(fieldName) && !inConstructor
						&& !inPostConstruct && !inStaticInitializer && !isAllowedScope) {

					// Check if this is a thread-safe collection - skip if it is
					String fieldType = fieldTypes.get(fieldName);
					if (!isThreadSafeCollection(fieldType)) {
						recordIssue(fieldName, "Collection modification '" + methodName + "'",
								"method " + currentMethodName);
					}
				}
			}
		}

		return super.visitMethodInvocation(method, ctx);
	}

	@Override
	public J.Unary visitUnary(J.Unary unary, ExecutionContext ctx) {
		if (!isInBeanClass) {
			return super.visitUnary(unary, ctx);
		}

		// Check for increment/decrement operations on fields
		if (unary.getOperator() == J.Unary.Type.PostIncrement || unary.getOperator() == J.Unary.Type.PreIncrement
				|| unary.getOperator() == J.Unary.Type.PostDecrement
				|| unary.getOperator() == J.Unary.Type.PreDecrement) {
			String fieldName = getFieldNameFromExpression(unary.getExpression());
			if (fieldName != null && isInstanceField(fieldName) && !isAllowedField(fieldName) && !inConstructor
					&& !inPostConstruct && !inStaticInitializer && !isAllowedScope) {
				String operationType = (unary.getOperator() == J.Unary.Type.PostIncrement
						|| unary.getOperator() == J.Unary.Type.PreIncrement) ? "Increment operation"
								: "Decrement operation";
				recordIssue(fieldName, operationType, "method " + currentMethodName);
			}
		}

		return super.visitUnary(unary, ctx);
	}

	@Override
	public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
		// Check if this is a static initializer block
		boolean wasInStaticInitializer = inStaticInitializer;
		if (block.isStatic()) {
			inStaticInitializer = true;
		}

		J.Block result = super.visitBlock(block, ctx);

		inStaticInitializer = wasInStaticInitializer;
		return result;
	}

	private void handleFieldAssignment(String fieldName) {
		// Skip if field is final, injected, in an allowed context, in
		// @ConfigurationProperties class, or in allowed scope
		if (!isAllowedField(fieldName) && !inConstructor && !inPostConstruct && !inStaticInitializer
				&& !isConfigurationPropertiesClass && !isAllowedScope) {
			recordIssue(fieldName, "Field assignment", "method " + currentMethodName);
		}
	}

	private boolean isAllowedField(String fieldName) {
		return finalFields.contains(fieldName) || staticFinalFields.contains(fieldName)
				|| injectedFields.contains(fieldName);
	}

	private boolean isInstanceField(String name) {
		return declaredFields.contains(name);
	}

	private boolean isCollectionModificationMethod(String methodName) {
		return Set.of("add", "remove", "addAll", "removeAll", "clear", "put", "putAll", "removeIf")
			.contains(methodName);
	}

	private boolean isThreadSafeCollection(String typeStr) {
		if (typeStr == null) {
			return false;
		}

		// Check for thread-safe collection types from java.util.concurrent
		return typeStr.contains("ConcurrentHashMap") || typeStr.contains("ConcurrentLinkedQueue")
				|| typeStr.contains("ConcurrentLinkedDeque") || typeStr.contains("ConcurrentSkipListMap")
				|| typeStr.contains("ConcurrentSkipListSet") || typeStr.contains("CopyOnWriteArrayList")
				|| typeStr.contains("CopyOnWriteArraySet") || typeStr.contains("LinkedBlockingQueue")
				|| typeStr.contains("LinkedBlockingDeque") || typeStr.contains("ArrayBlockingQueue")
				|| typeStr.contains("PriorityBlockingQueue") || typeStr.contains("SynchronousQueue")
				|| typeStr.contains("DelayQueue") || typeStr.contains("LinkedTransferQueue");
	}

	private String getFieldNameFromExpression(J expression) {
		if (expression instanceof J.Identifier) {
			return ((J.Identifier) expression).getSimpleName();
		}
		else if (expression instanceof J.FieldAccess fieldAccess) {
			if (fieldAccess.getTarget() instanceof J.Identifier
					&& ((J.Identifier) fieldAccess.getTarget()).getSimpleName().equals("this")) {
				return fieldAccess.getSimpleName();
			}
		}
		return null;
	}

	private void recordIssue(String fieldName, String description, String location) {
		statefulIssues.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(new Issue(description, location));
	}

	private boolean hasAnyBeanAnnotation(J.ClassDeclaration classDecl) {
		return classDecl.getLeadingAnnotations()
			.stream()
			.anyMatch(ann -> BEAN_ANNOTATIONS.contains(getSimpleAnnotationName(ann)));
	}

	private boolean hasAnyInjectionAnnotation(J.VariableDeclarations variableDecl) {
		return variableDecl.getLeadingAnnotations()
			.stream()
			.anyMatch(ann -> INJECTION_ANNOTATIONS.contains(getSimpleAnnotationName(ann)));
	}

	private boolean hasAnnotation(J.MethodDeclaration method, String annotationName) {
		return method.getLeadingAnnotations()
			.stream()
			.anyMatch(ann -> annotationName.equals(getSimpleAnnotationName(ann)));
	}

	private boolean hasAnnotation(J.ClassDeclaration classDecl, String annotationName) {
		return classDecl.getLeadingAnnotations()
			.stream()
			.anyMatch(ann -> annotationName.equals(getSimpleAnnotationName(ann)));
	}

	private boolean hasAllowedScope(J.ClassDeclaration classDecl) {
		// Check for @RequestScope annotation
		if (hasAnnotation(classDecl, "RequestScope")) {
			return true;
		}

		// Check for @Scope annotation with allowed values
		return classDecl.getLeadingAnnotations().stream().anyMatch(ann -> {
			if (!"Scope".equals(getSimpleAnnotationName(ann))) {
				return false;
			}

			// Check annotation arguments for prototype or request scope
			if (ann.getArguments() != null) {
				return ann.getArguments().stream().anyMatch(arg -> {
					String argStr = arg.toString();
					// Handle @Scope("prototype"), @Scope(value = "prototype"),
					// @Scope(scopeName = "prototype")
					// Also handle WebApplicationContext.SCOPE_REQUEST constant
					// Check built-in allowed scopes
					if (argStr.contains("\"prototype\"") || argStr.contains("\"request\"")
							|| argStr.contains("SCOPE_REQUEST")) {
						return true;
					}

					// Check additional allowed scopes
					if (additionalAllowedScopes != null) {
						for (String scope : additionalAllowedScopes) {
							if (argStr.contains("\"" + scope + "\"")) {
								return true;
							}
						}
					}

					return false;
				});
			}
			return false;
		});
	}

	private String getSimpleAnnotationName(J.Annotation annotation) {
		if (annotation.getAnnotationType() instanceof J.Identifier) {
			return ((J.Identifier) annotation.getAnnotationType()).getSimpleName();
		}
		else if (annotation.getAnnotationType() instanceof J.FieldAccess) {
			return ((J.FieldAccess) annotation.getAnnotationType()).getSimpleName();
		}
		return "";
	}

	private static class Issue {

		final String description;

		final String location;

		Issue(String description, String location) {
			this.description = description;
			this.location = location;
		}

	}

	public List<StatefulIssue> getIssues() {
		List<StatefulIssue> result = new ArrayList<>();
		for (Map.Entry<String, List<Issue>> entry : statefulIssues.entrySet()) {
			String fieldName = entry.getKey();
			for (Issue issue : entry.getValue()) {
				result.add(new StatefulIssue(fieldName,
						issue.description + " to '" + fieldName + "' in " + issue.location, IssueLevel.ERROR));
			}
		}

		// Add warnings for mutable collections and static non-final fields
		result.addAll(detectWarnings());

		return result;
	}

	private List<StatefulIssue> detectWarnings() {
		List<StatefulIssue> warnings = new ArrayList<>();
		// This would require analyzing field declarations during visit
		// For now, return empty list - warnings can be implemented later if needed
		return warnings;
	}

}