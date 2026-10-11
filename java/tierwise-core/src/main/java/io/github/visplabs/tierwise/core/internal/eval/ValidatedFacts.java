package io.github.visplabs.tierwise.core.internal.eval;

import java.util.List;
import java.util.Map;

/**
 * A FactSet that passed validation, with every value in normal form (decimals in canonical form).
 *
 * @param facts the single-value facts; a {@code null} value is a missing fact (FACT-16)
 * @param children the child maps of each child entity that has an entry (FACT-17 to FACT-19)
 */
record ValidatedFacts(Map<String, Object> facts, Map<String, List<Map<String, Object>>> children) {
}
