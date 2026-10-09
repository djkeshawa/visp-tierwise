package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.Decision;
import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.Resolved.Contribution;
import io.github.tierwise.core.Resolved.Contribution.Label;
import io.github.tierwise.core.SkippedScope;
import java.util.List;
import java.util.Optional;

/**
 * Builds the two result types in the shape of section 9. The result records validate the presence rules
 * of PROV-27, PROV-30 and PROV-32 themselves; this class only maps evaluator data onto them.
 */
final class ResultFactory {

  private ResultFactory() {
  }

  /**
   * Builds a value entry (PROV-20 to PROV-28).
   *
   * @param scope the contributing scope
   * @param value the value the scope contributes
   * @param label the label determined by the value and the strategy (PROV-37)
   * @return the entry
   */
  static Contribution valueEntry(PathScope scope, Object value, Label label) {
    return new Contribution(scope.kind(), scope.name(), scope.optionalNode(), Optional.of(value), label, Optional.empty());
  }

  /**
   * Builds a failure entry (PROV-29 to PROV-33, PROV-39 to PROV-41).
   *
   * @param scope the level whose source failed
   * @param label {@link Label#UNAVAILABLE} or {@link Label#INVALID}; the label stays whatever the
   *     behaviour (PROV-41)
   * @param behaviour {@code skip} or {@code deny}
   * @param denyValue the deny value for {@code deny}, or {@code null} for {@code skip}
   * @return the entry
   */
  static Contribution failureEntry(PathScope scope, Label label, String behaviour, Object denyValue) {
    return new Contribution(scope.kind(), scope.name(), scope.optionalNode(), Optional.ofNullable(denyValue), label,
        Optional.of(behaviour));
  }

  /**
   * Builds the {@code source} of an order-dependent result (PROV-45 to PROV-48).
   *
   * @param winner the winning scope, or empty for {@code DEFAULT}
   * @return the source
   */
  static Resolved.Source source(Optional<PathScope> winner) {
    return winner.map(scope -> Resolved.Source.of(scope.kind(), scope.name(), scope.node()))
        .orElse(Resolved.Source.DEFAULT);
  }

  /**
   * Builds a {@code Resolved} result.
   *
   * @param key the key name
   * @param strategy the key's strategy
   * @param kind the key's kind, if it opts into one
   * @param value the result, or {@code null} when absent
   * @param bundleVersion the digest of the bundle
   * @param skipped the skipped scopes of the context
   * @param contributions the contribution entries in path order
   * @param source the source, present only for an order-dependent key
   * @return the result
   */
  static Resolved resolved(
      String key,
      Strategy strategy,
      Optional<String> kind,
      Object value,
      String bundleVersion,
      List<SkippedScope> skipped,
      List<Contribution> contributions,
      Optional<Resolved.Source> source) {
    return new Resolved(key, strategy.wire(), kind, value, bundleVersion, skipped, contributions, source);
  }

  /**
   * Builds a {@code Decision} result.
   *
   * @param feature the feature name
   * @param effect the effect, unchanged
   * @param bundleVersion the digest of the bundle
   * @param skipped the skipped scopes of the context
   * @param binding the selected binding
   * @param policy the policy of the selected binding
   * @param reason why the effect was chosen
   * @param trace the rules checked, in order
   * @return the result
   */
  static Decision decision(
      String feature,
      Object effect,
      String bundleVersion,
      List<SkippedScope> skipped,
      Decision.Binding binding,
      String policy,
      Decision.Reason reason,
      List<Decision.TraceEntry> trace) {
    return new Decision(feature, effect, bundleVersion, skipped, binding, policy, reason, trace);
  }
}
