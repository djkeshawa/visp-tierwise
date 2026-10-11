package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.model.Values;
import java.util.Optional;

/**
 * The values stage: every authored value of the values documents (section 5, KEY-10 to KEY-13, MERGE-10 to
 * MERGE-12, MERGE-26, KIND-6; section 2, SCOPE-38 to SCOPE-46 through SRC-32).
 *
 * <p>For each entry of each group of the values documents, in definition order (FIND-32), the stage
 * reports a scope that is not declared ({@code E_UNKNOWN_SCOPE}), a node ID that breaks the structural
 * contract ({@code E_INVALID_NODE_ID}) or is not in a closed catalog ({@code E_UNKNOWN_NODE}), a key that
 * is not declared ({@code E_UNKNOWN_KEY}, with a suggestion), a key not settable at the scope
 * ({@code E_NOT_SETTABLE_HERE}), a second value for one key at one scope and node
 * ({@code E_DUPLICATE_VALUE}), a value that does not fit the key's type (through {@link ValueValidator};
 * a {@code deep_merge} key accepts partial objects, MERGE-11 and MERGE-12), an optional object field that
 * the starting value does not set and that is not given complete (MERGE-26, {@code E_TYPE_MISMATCH}), and
 * an identity value under a {@code narrowing_only} kind ({@code E_KIND_VIOLATION}). A key whose type or
 * strategy is invalid receives no value check (FIND-35).
 *
 * <p>Produces: {@link ModelParts#values(Values)}, the normalised values, only when no fault was reported
 * and the hierarchy and the {@link KeyTable} are available.
 *
 * <p>Reads: the {@code ValueGroup}s of the values documents, {@link ModelParts#hierarchy()}, the
 * {@link KeyTable} of {@link KeyStage} and the {@link KindTable} of {@link KindStage}.
 */
public final class ValueStage implements Stage {

  @Override
  public String name() {
    return "values";
  }

  @Override
  public void run(CheckContext context) {
    ValueChecks checks = new ValueChecks(context.findings(), context.parts().hierarchy(),
        context.find(KeyTable.class), context.find(KindTable.class));
    Optional<Values> values = checks.run(context.sources().values());
    values.ifPresent(context.parts()::values);
  }
}
