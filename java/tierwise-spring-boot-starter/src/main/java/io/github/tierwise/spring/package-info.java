/**
 * Spring Boot integration for Tierwise.
 *
 * <p>The auto-configuration {@link io.github.tierwise.spring.TierwiseAutoConfiguration} loads a bundle
 * from a {@link io.github.tierwise.spring.BundleSource}, keeps it fresh with
 * {@link io.github.tierwise.spring.TierwiseEvaluators}, and exposes the {@link
 * io.github.tierwise.spring.Resolver} and {@link io.github.tierwise.spring.DecisionEngine} beans. It
 * wires {@link io.github.tierwise.spring.TierwiseProvider} beans into the evaluator and reports metrics
 * through Micrometer when Micrometer is present. The properties are under the prefix {@code tierwise}
 * (see {@link io.github.tierwise.spring.TierwiseProperties}).
 */
package io.github.tierwise.spring;
