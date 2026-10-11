package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.model.Returns;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A feature declaration after parsing. Each part is present when it could be read, so that the checks of
 * a rules file can use what is known about a feature that has another fault.
 *
 * @param name the feature name
 * @param node the declaration node, which locates a finding about the feature as a whole
 * @param decidedPer the decided entity, when it is a declared entity
 * @param returns the effect form, when it is valid
 * @param ifFactMissing the effect for a missing fact, when it is valid
 * @param listed the level and axis names that {@code binding_settable_at} lists, when it is a list
 * @param bindingSettableAt the levels at which a binding may be set, when the list has no fault
 * @param clean {@code true} when the declaration has no fault, so that a feature can be built from it
 */
record FeatureDecl(
    String name,
    PNode node,
    Optional<String> decidedPer,
    Optional<Returns> returns,
    Optional<FeatureEffect> ifFactMissing,
    Optional<Set<String>> listed,
    Optional<List<String>> bindingSettableAt,
    boolean clean) {
}
