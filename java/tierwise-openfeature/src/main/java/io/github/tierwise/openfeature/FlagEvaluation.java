package io.github.tierwise.openfeature;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import io.github.tierwise.core.Code;
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.TierwiseException;

/**
 * Evaluates one flag: looks the name up as a key, then, for the types a feature can answer, as a feature,
 * and maps the result as {@code docs/openfeature-mapping.md} defines.
 */
final class FlagEvaluation {

  private FlagEvaluation() {
  }

  static <T> ProviderEvaluation<T> evaluate(
      Evaluator evaluator, String flag, T fallback, EvaluationContext source, Target<T> target) {
    try {
      Context context = ContextMapper.context(source);
      Resolved resolved;
      try {
        resolved = evaluator.resolve(context, flag);
      } catch (TierwiseException e) {
        if (!Code.E_UNKNOWN_KEY.equals(e.code())) {
          throw e;
        }
        return feature(evaluator, context, flag, fallback, source, target);
      }
      return key(resolved, fallback, target);
    } catch (TierwiseException e) {
      return Errors.of(e, fallback);
    }
  }

  // ---- Keys -----------------------------------------------------------------------------------

  private static <T> ProviderEvaluation<T> key(Resolved resolved, T fallback, Target<T> target) {
    ImmutableMetadata.ImmutableMetadataBuilder metadata = keyMetadata(resolved);
    if (!resolved.hasValue()) {
      metadata.addBoolean(TierwiseMetadata.VALUE_ABSENT, true);
      return success(fallback, Reason.DEFAULT, null, metadata);
    }
    T value;
    try {
      value = target.convert(resolved.value());
    } catch (Target.Mismatch e) {
      return Errors.typeMismatch(
          "the key '" + resolved.key() + "' cannot be read as " + target.describe() + ": " + e.getMessage(),
          fallback,
          metadata);
    }
    return success(value, keyReason(resolved), null, metadata);
  }

  /**
   * Maps a key's provenance to a reason. A key that no scope supplied a value for (an {@code override} or
   * {@code deep_merge} key at its declared default, or a commutative key at its identity) is DEFAULT. A value
   * that comes only from the root level is STATIC, because it does not depend on the context. Anything
   * else depends on a level or axis node that the context named, so it is TARGETING_MATCH.
   */
  static Reason keyReason(Resolved resolved) {
    if (resolved.source().isPresent()) {
      Resolved.Source source = resolved.source().get();
      if (source.isDefault()) {
        return Reason.DEFAULT;
      }
      return source.node().isPresent() ? Reason.TARGETING_MATCH : Reason.STATIC;
    }
    if (resolved.contributions().isEmpty()) {
      return Reason.DEFAULT;
    }
    for (Resolved.Contribution contribution : resolved.contributions()) {
      if (contribution.node().isPresent()) {
        return Reason.TARGETING_MATCH;
      }
    }
    return Reason.STATIC;
  }

  private static ImmutableMetadata.ImmutableMetadataBuilder keyMetadata(Resolved resolved) {
    ImmutableMetadata.ImmutableMetadataBuilder metadata =
        ImmutableMetadata.builder()
            .addString(TierwiseMetadata.BUNDLE_VERSION, resolved.bundleVersion())
            .addString(TierwiseMetadata.FLAG_KIND, TierwiseMetadata.KEY)
            .addString(TierwiseMetadata.STRATEGY, resolved.strategy());
    resolved.kind().ifPresent(kind -> metadata.addString(TierwiseMetadata.KIND, kind));
    resolved
        .source()
        .ifPresent(
            source -> {
              if (source.isDefault()) {
                metadata.addString(TierwiseMetadata.SOURCE, "DEFAULT");
              } else {
                metadata.addString(
                    TierwiseMetadata.SOURCE, source.scope().get().json() + ":" + source.name().get());
                source.node().ifPresent(node -> metadata.addString(TierwiseMetadata.SOURCE_NODE, node));
              }
            });
    metadata.addString(TierwiseMetadata.CONTRIBUTIONS, Json.contributions(resolved.contributions()));
    if (!resolved.skippedScopes().isEmpty()) {
      metadata.addString(TierwiseMetadata.SKIPPED_SCOPES, Json.skippedScopes(resolved.skippedScopes()));
    }
    return metadata;
  }

  // ---- Features ---------------------------------------------------------------------------------

  private static <T> ProviderEvaluation<T> feature(
      Evaluator evaluator,
      Context context,
      String flag,
      T fallback,
      EvaluationContext source,
      Target<T> target) {
    if (!target.readsFeatures()) {
      return featureOfWrongType(evaluator, context, flag, fallback, target);
    }
    Decision decision = decide(evaluator, context, flag, ContextMapper.facts(source));
    ImmutableMetadata.ImmutableMetadataBuilder metadata = decisionMetadata(decision);
    Object effect = decision.effect();
    if (effect == null) {
      metadata.addBoolean(TierwiseMetadata.EFFECT_NULL, true);
      return success(fallback, Reason.DEFAULT, null, metadata);
    }
    T value;
    try {
      value = target.convert(effect);
    } catch (Target.Mismatch e) {
      return Errors.typeMismatch(
          "the feature '" + flag + "' cannot be read as " + target.describe() + ": " + e.getMessage(),
          fallback,
          metadata);
    }
    String variant = decision.reason().id().orElse(null);
    Reason reason =
        decision.reason().type() == Decision.Reason.Type.RULE ? Reason.TARGETING_MATCH : Reason.DEFAULT;
    return success(value, reason, variant, metadata);
  }

  /**
   * A name that is no key, asked for as a type that features cannot have. A feature of that name makes the
   * request a type mismatch. No name means the flag does not exist.
   */
  private static <T> ProviderEvaluation<T> featureOfWrongType(
      Evaluator evaluator, Context context, String flag, T fallback, Target<T> target) {
    Decision decision = decide(evaluator, context, flag, FactSet.empty());
    return Errors.typeMismatch(
        "'" + flag + "' is a feature, which cannot be read as " + target.describe() + "; read it as a string or object flag",
        fallback,
        decisionMetadata(decision));
  }

  private static Decision decide(Evaluator evaluator, Context context, String flag, FactSet facts) {
    try {
      return evaluator.decide(context, flag, facts);
    } catch (TierwiseException e) {
      if (Code.E_UNKNOWN_FEATURE.equals(e.code())) {
        throw new TierwiseException(Code.E_UNKNOWN_FEATURE, "no key or feature is named '" + flag + "'");
      }
      throw e;
    }
  }

  private static ImmutableMetadata.ImmutableMetadataBuilder decisionMetadata(Decision decision) {
    ImmutableMetadata.ImmutableMetadataBuilder metadata =
        ImmutableMetadata.builder()
            .addString(TierwiseMetadata.BUNDLE_VERSION, decision.bundleVersion())
            .addString(TierwiseMetadata.FLAG_KIND, TierwiseMetadata.FEATURE)
            .addString(TierwiseMetadata.DECISION_REASON, decision.reason().type().name())
            .addString(TierwiseMetadata.POLICY, decision.policy())
            .addString(TierwiseMetadata.BINDING_LEVEL, decision.binding().level());
    decision.binding().node().ifPresent(node -> metadata.addString(TierwiseMetadata.BINDING_NODE, node));
    decision.reason().id().ifPresent(id -> metadata.addString(TierwiseMetadata.RULE_ID, id));
    decision.reason().name().ifPresent(name -> metadata.addString(TierwiseMetadata.RULE_NAME, name));
    if (decision.reason().type() == Decision.Reason.Type.MISSING_FACT) {
      metadata.addString(TierwiseMetadata.MISSING_FACTS, Json.strings(decision.reason().facts()));
    }
    if (!decision.skippedScopes().isEmpty()) {
      metadata.addString(TierwiseMetadata.SKIPPED_SCOPES, Json.skippedScopes(decision.skippedScopes()));
    }
    return metadata;
  }

  private static <T> ProviderEvaluation<T> success(
      T value, Reason reason, String variant, ImmutableMetadata.ImmutableMetadataBuilder metadata) {
    return new ProviderEvaluation<>(value, variant, reason.toString(), null, null, metadata.build());
  }
}
