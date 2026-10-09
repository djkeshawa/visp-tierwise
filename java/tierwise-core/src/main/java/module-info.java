/**
 * The Tierwise core: compiler, bundle reader and evaluator.
 *
 * <p>Only the {@code io.github.tierwise.core} package is exported. Everything under
 * {@code io.github.tierwise.core.internal} is an implementation detail.
 */
module io.github.tierwise.core {
  requires org.snakeyaml.engine;
  requires com.fasterxml.jackson.core;

  exports io.github.tierwise.core;
}
