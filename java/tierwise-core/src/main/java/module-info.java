/**
 * The Tierwise core: compiler, bundle reader and evaluator.
 *
 * <p>Only the {@code io.github.visplabs.tierwise.core} package is exported. Everything under
 * {@code io.github.visplabs.tierwise.core.internal} is an implementation detail.
 */
module io.github.visplabs.tierwise.core {
  requires org.snakeyaml.engine;
  requires com.fasterxml.jackson.core;

  exports io.github.visplabs.tierwise.core;
}
