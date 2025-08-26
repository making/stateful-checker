package com.example.statefulchecker.recipe;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import com.example.statefulchecker.visitor.StatefulCodeDetector;

/**
 * Recipe for detecting stateful code in Spring and EJB beans.
 */
public class StatefulCodeRecipe extends Recipe {

	@Override
	public String getDisplayName() {
		return "Check for stateful code";
	}

	@Override
	public String getDescription() {
		return "Detects stateful instance variables in Spring beans and EJB Stateless Session Beans.";
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return new StatefulCodeDetector();
	}

}