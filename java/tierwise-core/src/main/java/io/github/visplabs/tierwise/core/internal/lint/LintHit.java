package io.github.visplabs.tierwise.core.internal.lint;

import io.github.visplabs.tierwise.core.internal.yaml.PNode;

/**
 * One place that a lint rule flags, before its severity is known (LINT-7).
 *
 * @param node the node that locates the finding (FIND-19 to FIND-23)
 * @param message a message for people; conformance vectors do not compare it (FIND-10)
 */
public record LintHit(PNode node, String message) {
}
