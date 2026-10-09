package io.github.tierwise.server.service;

import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.LoadedBundle;
import io.github.tierwise.core.Tierwise;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * One loaded bundle version, ready to evaluate: the bundle as the core loaded it, the view for {@code GET
 * /v1/schema}, and an evaluator. It is immutable and shared by all requests of one instance.
 */
final class BundleRuntime {

  private final String version;
  private final LoadedBundle bundle;
  private final Set<String> serviceLevels;
  private final Map<String, Object> schema;
  private final Evaluator shared;
  private final ProviderWiring providers;

  private BundleRuntime(String version, LoadedBundle bundle, Set<String> serviceLevels, Map<String, Object> schema,
      Evaluator shared, ProviderWiring providers) {
    this.version = version;
    this.bundle = bundle;
    this.serviceLevels = serviceLevels;
    this.schema = schema;
    this.shared = shared;
    this.providers = providers;
  }

  /**
   * Loads stored bytes. The bytes were verified when they were published, and the core verifies them again.
   *
   * @param bytes the exact bundle bytes
   * @param json a mapper
   * @param providers the provider wiring
   * @return the runtime
   * @throws io.github.tierwise.core.TierwiseException when the stored bytes no longer load
   */
  static BundleRuntime load(byte[] bytes, JsonMapper json, ProviderWiring providers) {
    LoadedBundle bundle = Tierwise.loadBundle(bytes);
    Map<String, Object> envelope = json.readValue(new String(bytes, StandardCharsets.UTF_8),
        new TypeReference<Map<String, Object>>() { });
    @SuppressWarnings("unchecked")
    Map<String, Object> content = (Map<String, Object>) envelope.get("content");
    Set<String> serviceLevels = SchemaView.serviceLevels(content);
    providers.warnAboutUnusedBeans(bundle.bundleVersion(), serviceLevels);
    Evaluator shared = Tierwise.evaluator(bundle, providers.shared(serviceLevels));
    return new BundleRuntime(bundle.bundleVersion(), bundle, serviceLevels,
        SchemaView.build(bundle.bundleVersion(), content), shared, providers);
  }

  String version() {
    return version;
  }

  Map<String, Object> schema() {
    return schema;
  }

  /** The evaluator for {@code decide}, which never calls a provider. */
  Evaluator forDecide() {
    return shared;
  }

  /**
   * The evaluator for one {@code resolve} request. A bundle with service-sourced levels gets an evaluator of
   * its own, whose providers are called at most once per node for the request (SVC-20).
   */
  Evaluator forResolve() {
    if (serviceLevels.isEmpty()) {
      return shared;
    }
    return Tierwise.evaluator(bundle, providers.forRequest(serviceLevels, new ProviderWiring.RequestCalls()));
  }
}
