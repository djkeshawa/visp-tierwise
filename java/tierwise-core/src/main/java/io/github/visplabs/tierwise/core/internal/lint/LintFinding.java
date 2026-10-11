package io.github.visplabs.tierwise.core.internal.lint;

import io.github.visplabs.tierwise.core.Severity;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;

/**
 * A lint finding with the resolved severity of its rule (LINT-4, FIND-16).
 *
 * @param ruleId the lint rule identifier, which is the finding's code (FIND-13)
 * @param severity the resolved severity of the rule
 * @param node the node that locates the finding
 * @param message a message for people
 */
public record LintFinding(String ruleId, Severity severity, PNode node, String message) {
}
