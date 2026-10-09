package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.Resolved.Contribution;
import io.github.tierwise.core.Resolved.Contribution.Label;
import io.github.tierwise.core.ScopeKind;
import io.github.tierwise.core.internal.model.KeyDef;
import io.github.tierwise.core.internal.model.Model;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Combination and provenance for one key over the evaluation path (EVAL-16 step 5; MERGE-13 to MERGE-26,
 * AXIS-7, PROV-19 to PROV-53). This step raises no error.
 *
 * <p>A scope contributes only if the key is settable there (MERGE-14). The root level and file-sourced
 * scopes contribute the value the bundle holds for their node; a service-sourced level contributes what
 * its provider call produced. A source failure contributes no value under {@code skip} and the deny value
 * under {@code deny}, and its entry keeps the failure label (MERGE-15, PROV-41).
 */
final class KeyResolution {

  /** What one scope did for the key: a value, or a source failure. */
  private record Slot(PathScope scope, Object value, Label failure) {

    static Slot ofValue(PathScope scope, Object value) {
      return new Slot(scope, value, null);
    }

    static Slot ofFailure(PathScope scope, Label label) {
      return new Slot(scope, null, label);
    }

    boolean isFailure() {
      return failure != null;
    }
  }

  private final Model model;
  private final String bundleVersion;

  /**
   * Creates the resolution logic of an evaluator.
   *
   * @param model the model of the bundle
   * @param bundleVersion the digest of the bundle
   */
  KeyResolution(Model model, String bundleVersion) {
    this.model = model;
    this.bundleVersion = bundleVersion;
  }

  /**
   * Resolves a key.
   *
   * @param name the declared key name
   * @param admitted the validated context
   * @param outcomes the provider outcomes of the evaluation call
   * @return the result with its provenance
   */
  Resolved resolve(String name, AdmittedContext admitted, ProviderOutcomes outcomes) {
    KeyDef key = model.keys().get(name);
    Strategy strategy = Strategy.parse(key.combine())
        .orElseThrow(() -> new IllegalStateException("the bundle names an unknown strategy: " + key.combine()));
    List<Slot> slots = collect(name, key, admitted, outcomes);
    // A key that cannot carry a deny value (SVC-11, SVC-12) never reaches a bundle with `deny`; if one did, the
    // failure would contribute nothing, as under `skip`.
    Optional<Object> deny = "deny".equals(key.onSourceFailure().orElse("skip"))
        ? Strategies.denyValue(strategy, key.type()) : Optional.empty();
    String behaviour = deny.isPresent() ? "deny" : "skip";
    List<Contribution> entries = new ArrayList<>();
    for (Slot slot : slots) {
      entries.add(slot.isFailure()
          ? ResultFactory.failureEntry(slot.scope(), slot.failure(), behaviour, deny.orElse(null))
          : ResultFactory.valueEntry(slot.scope(), slot.value(), Strategies.label(strategy, key.type(), slot.value())));
    }
    Object value;
    Optional<Resolved.Source> source = Optional.empty();
    if (strategy.isCommutative()) {
      value = combineCommutative(strategy, key, slots, deny);
    } else {
      AxisCombination.Outcome outcome = combineOrdered(strategy, key, slots);
      value = outcome.value();
      source = Optional.of(ResultFactory.source(outcome.source()));
    }
    return ResultFactory.resolved(name, strategy, key.kind(), value, bundleVersion, admitted.skippedScopes(), entries,
        source);
  }

  private List<Slot> collect(String name, KeyDef key, AdmittedContext admitted, ProviderOutcomes outcomes) {
    List<Slot> slots = new ArrayList<>();
    for (PathScope scope : admitted.path()) {
      if (!key.settableAt().contains(scope.name())) {
        continue;
      }
      boolean service = !scope.isRoot() && isServiceSourced(scope.name());
      if (service) {
        SourceOutcome outcome = outcomes.get(scope.name(), name);
        if (outcome instanceof SourceOutcome.Supplied supplied) {
          slots.add(Slot.ofValue(scope, supplied.value()));
        } else if (outcome instanceof SourceOutcome.Failed failed) {
          slots.add(Slot.ofFailure(scope, failed.label()));
        }
      } else {
        Object authored = model.values().get(scope.name(), scope.nodeKey(), name);
        if (authored != null) {
          slots.add(Slot.ofValue(scope, authored));
        }
      }
    }
    return slots;
  }

  private boolean isServiceSourced(String scope) {
    var definition = model.hierarchy().scope(scope);
    return definition != null && definition.isServiceSourced();
  }

  private static Object combineCommutative(Strategy strategy, KeyDef key, List<Slot> slots, Optional<Object> deny) {
    List<Object> contributed = new ArrayList<>();
    for (Slot slot : slots) {
      if (!slot.isFailure()) {
        contributed.add(slot.value());
      } else if (deny.isPresent()) {
        contributed.add(deny.get());
      }
    }
    return contributed.isEmpty()
        ? Strategies.identity(strategy, key.type())
        : Strategies.combine(strategy, key.type(), contributed);
  }

  /**
   * Forms an order-dependent result. A failure never contributes a value here: {@code deny} is allowed
   * only for narrowing strategies (SVC-11, PROV-49).
   */
  private static AxisCombination.Outcome combineOrdered(Strategy strategy, KeyDef key, List<Slot> slots) {
    List<AxisCombination.Contributed> levels = new ArrayList<>();
    List<AxisCombination.Contributed> axes = new ArrayList<>();
    for (Slot slot : slots) {
      if (!slot.isFailure()) {
        (slot.scope().kind() == ScopeKind.LEVEL ? levels : axes)
            .add(new AxisCombination.Contributed(slot.scope(), slot.value()));
      }
    }
    Object defaultValue = key.defaultValue().orElse(null);
    return strategy == Strategy.OVERRIDE
        ? AxisCombination.override(defaultValue, levels, axes, key.combineAcrossAxes())
        : AxisCombination.deepMerge(key.type(), defaultValue, levels, axes, key.combineAcrossAxes());
  }
}
