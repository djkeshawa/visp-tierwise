package io.github.visplabs.tierwise.core;

import java.io.IOException;

/**
 * Fetches the bytes of a preset that a manifest names by URL.
 *
 * <p>The core opens no network connection. Only the command-line tool supplies an implementation that
 * does; without one, a preset URL is reported as {@code E_PRESET}.
 */
@FunctionalInterface
public interface PresetFetcher {

  /**
   * Fetches a preset.
   *
   * @param url the URL exactly as the manifest writes it
   * @return the raw bytes of the preset document
   * @throws IOException if the preset cannot be fetched
   */
  byte[] fetch(String url) throws IOException;
}
