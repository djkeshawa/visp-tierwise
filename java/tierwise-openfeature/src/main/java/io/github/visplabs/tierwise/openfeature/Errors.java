package io.github.visplabs.tierwise.openfeature;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.TierwiseException;

/** Maps Tierwise errors to OpenFeature resolution errors. */
final class Errors {

  private Errors() {
  }

  /**
   * Maps a Tierwise evaluation error code to an OpenFeature error code. An unknown key or feature is a
   * missing flag, an invalid context, unknown scope or node, or invalid FactSet is an invalid context, and
   * every other code is a general error.
   *
   * @param code the code of a {@link TierwiseException}
   * @return the OpenFeature error code
   */
  static ErrorCode map(String code) {
    switch (code) {
      case Code.E_UNKNOWN_KEY:
      case Code.E_UNKNOWN_FEATURE:
        return ErrorCode.FLAG_NOT_FOUND;
      case Code.E_CONTEXT_INVALID:
      case Code.E_UNKNOWN_SCOPE:
      case Code.E_UNKNOWN_NODE:
      case Code.E_FACTS_INVALID:
        return ErrorCode.INVALID_CONTEXT;
      default:
        return ErrorCode.GENERAL;
    }
  }

  /** The resolution for a Tierwise error: the caller's default value, reason ERROR and the mapped code. */
  static <T> ProviderEvaluation<T> of(TierwiseException error, T fallback) {
    ImmutableMetadata metadata =
        ImmutableMetadata.builder().addString(TierwiseMetadata.TIERWISE_CODE, error.code()).build();
    return new ProviderEvaluation<>(
        fallback, null, Reason.ERROR.toString(), map(error.code()), error.getMessage(), metadata);
  }

  /** The resolution for a result that cannot be read as the requested type. */
  static <T> ProviderEvaluation<T> typeMismatch(
      String message, T fallback, ImmutableMetadata.ImmutableMetadataBuilder metadata) {
    return new ProviderEvaluation<>(
        fallback, null, Reason.ERROR.toString(), ErrorCode.TYPE_MISMATCH, message, metadata.build());
  }

  /** The resolution when no evaluator is available. */
  static <T> ProviderEvaluation<T> notReady(T fallback) {
    return new ProviderEvaluation<>(
        fallback,
        null,
        Reason.ERROR.toString(),
        ErrorCode.PROVIDER_NOT_READY,
        "the evaluator supplier returned no evaluator",
        ImmutableMetadata.EMPTY);
  }
}
