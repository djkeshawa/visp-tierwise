package io.github.visplabs.tierwise.spring;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Resolved;
import io.github.visplabs.tierwise.core.TierwiseException;

/**
 * Resolves keys against the bundle currently in use. A call that is already running when the bundle is
 * swapped finishes on the bundle it started with.
 */
public interface Resolver {

  /**
   * Resolves a key, calling the providers of the service-sourced levels the context names. A provider
   * that fails or times out follows the key's {@code skip} or {@code deny} behaviour.
   *
   * @param context the context; {@code null} counts as empty
   * @param key the declared key name
   * @return the resolved value with its provenance
   * @throws TierwiseException with a code from EVAL-30 to EVAL-34
   */
  Resolved resolve(Context context, String key);

  /**
   * Resolves a key and never calls a provider.
   *
   * @param context the context; {@code null} counts as empty
   * @param key the declared key name
   * @return the resolved value with its provenance
   * @throws TierwiseException with {@code E_PROVIDER_REQUIRED} when a provider call would be needed, or
   *     any code that {@link #resolve} raises
   */
  Resolved resolveLocal(Context context, String key);
}
