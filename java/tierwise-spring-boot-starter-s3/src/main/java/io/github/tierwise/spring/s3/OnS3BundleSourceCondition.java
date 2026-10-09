package io.github.tierwise.spring.s3;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Matches when {@code tierwise.source} names an {@code s3://} location (see {@link S3Location#isS3Source}). */
final class OnS3BundleSourceCondition extends SpringBootCondition {

  /** The property that selects the bundle source. */
  static final String SOURCE_PROPERTY = "tierwise.source";

  @Override
  public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String source = context.getEnvironment().getProperty(SOURCE_PROPERTY);
    if (S3Location.isS3Source(source)) {
      return ConditionOutcome.match(SOURCE_PROPERTY + " uses the s3 scheme");
    }
    return ConditionOutcome.noMatch(SOURCE_PROPERTY + " does not use the s3 scheme");
  }
}
