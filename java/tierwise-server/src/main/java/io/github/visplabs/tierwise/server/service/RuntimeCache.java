package io.github.visplabs.tierwise.server.service;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the most recently used bundle versions loaded. A version never changes, so an entry never goes
 * stale; the cache only saves loading and parsing.
 */
final class RuntimeCache {

  private final BundleService bundles;
  private final JsonMapper json;
  private final ProviderWiring providers;
  private final Map<String, BundleRuntime> entries;

  RuntimeCache(BundleService bundles, JsonMapper json, ProviderWiring providers, int capacity) {
    this.bundles = bundles;
    this.json = json;
    this.providers = providers;
    this.entries = new LinkedHashMap<>(16, 0.75f, true) {
      private static final long serialVersionUID = 1L;

      @Override
      protected boolean removeEldestEntry(Map.Entry<String, BundleRuntime> eldest) {
        return size() > capacity;
      }
    };
  }

  /** Returns the runtime of the latest version. */
  BundleRuntime latest() {
    return get(bundles.requireLatestVersion());
  }

  private BundleRuntime get(String version) {
    synchronized (entries) {
      BundleRuntime cached = entries.get(version);
      if (cached != null) {
        return cached;
      }
    }
    BundleRuntime loaded = BundleRuntime.load(bundles.bundle(version).bytes(), json, providers);
    synchronized (entries) {
      BundleRuntime raced = entries.putIfAbsent(version, loaded);
      return raced != null ? raced : loaded;
    }
  }
}
