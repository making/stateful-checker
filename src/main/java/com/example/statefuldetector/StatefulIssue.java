package com.example.statefuldetector;

/**
 * Represents a stateful code issue detected in a Spring Bean or EJB.
 */
public record StatefulIssue(String fieldName, String message, IssueLevel level) {

}