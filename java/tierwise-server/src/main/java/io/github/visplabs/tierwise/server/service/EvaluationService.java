package io.github.visplabs.tierwise.server.service;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Decision;
import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.FactSet;
import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.core.Resolved;
import io.github.visplabs.tierwise.server.ApiException;
import io.github.visplabs.tierwise.server.ErrorCodes;
import io.github.visplabs.tierwise.core.EvaluationListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers {@code /v1/resolve}, {@code /v1/decide} and {@code /v1/schema} with {@code tierwise-core}, over the
 * latest bundle (requirements section 9).
 *
 * <p>The results are the core's results, serialised as the core's {@code toJson()} defines, so they equal
 * the results of an in-process evaluator for the same bundle, context and facts. The server calls the core
 * and nothing else: it does not interpret keys, features or facts.
 */
public final class EvaluationService {

  private static final Set<String> RESOLVE_MEMBERS = Set.of("context", "keys");
  private static final Set<String> DECIDE_MEMBERS = Set.of("context", "feature", "input");

  private final RuntimeCache runtimes;
  private final int maxKeys;

  /**
   * Creates the service.
   *
   * @param bundles the bundle service
   * @param json a mapper
   * @param providers the {@link LevelValueProvider} beans by level name (the bean name is the level)
   * @param providerTimeout the timeout of one provider call
   * @param listener receives the core's evaluation events; may be {@code null}
   * @param runtimeCacheSize how many bundle versions to keep loaded
   * @param maxKeys the largest number of keys in one resolve request
   */
  public EvaluationService(BundleService bundles, JsonMapper json, Map<String, LevelValueProvider> providers,
      Duration providerTimeout, EvaluationListener listener, int runtimeCacheSize, int maxKeys) {
    this.runtimes = new RuntimeCache(bundles, json, new ProviderWiring(providers, providerTimeout, listener),
        runtimeCacheSize);
    this.maxKeys = maxKeys;
  }

  /**
   * Resolves keys.
   *
   * @param request the body of {@code POST /v1/resolve}: {@code {context, keys[]}}
   * @return {@code {bundleVersion, results[]}}, one result for each requested key in the order of the
   *     request; a result is exactly the JSON form of the core's {@link Resolved}
   * @throws ApiException with status 400 for a malformed request; 404 when no bundle is published
   * @throws io.github.visplabs.tierwise.core.TierwiseException with a core error code, which the HTTP layer maps
   */
  public Map<String, Object> resolve(Map<String, Object> request) {
    reject(request, RESOLVE_MEMBERS);
    List<String> keys = keys(request.get("keys"));
    Context context = context(request);
    BundleRuntime runtime = runtimes.latest();
    Evaluator evaluator = runtime.forResolve();
    List<Object> results = new ArrayList<>(keys.size());
    for (String key : keys) {
      Resolved resolved = evaluator.resolve(context, key);
      results.add(resolved.toJson());
    }
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("bundleVersion", runtime.version());
    response.put("results", results);
    return response;
  }

  /**
   * Decides a feature.
   *
   * @param request the body of {@code POST /v1/decide}: {@code {context, feature, input}}, where {@code input}
   *     is the FactSet {@code {facts, children}}
   * @return the JSON form of the core's {@link Decision}
   * @throws ApiException with status 400 for a malformed request; 404 when no bundle is published
   * @throws io.github.visplabs.tierwise.core.TierwiseException with a core error code, which the HTTP layer maps
   */
  public Map<String, Object> decide(Map<String, Object> request) {
    reject(request, DECIDE_MEMBERS);
    Object feature = request.get("feature");
    if (!(feature instanceof String name)) {
      throw invalid("feature is required and must be a string");
    }
    Context context = context(request);
    FactSet facts = request.containsKey("input") ? FactSet.fromJson(request.get("input")) : FactSet.empty();
    Decision decision = runtimes.latest().forDecide().decide(context, name, facts);
    return decision.toJson();
  }

  /**
   * Returns the schema view of the latest bundle: hierarchy, entities, facts, keys, features and policies.
   *
   * @return the view
   * @throws ApiException with status 404 when no bundle is published
   */
  public Map<String, Object> schema() {
    return runtimes.latest().schema();
  }

  private static Context context(Map<String, Object> request) {
    return request.containsKey("context") ? Context.fromJson(request.get("context")) : Context.empty();
  }

  private List<String> keys(Object raw) {
    if (!(raw instanceof List<?> list)) {
      throw invalid("keys is required and must be an array of key names");
    }
    if (list.size() > maxKeys) {
      throw invalid("a request may name at most " + maxKeys + " keys");
    }
    List<String> keys = new ArrayList<>(list.size());
    for (Object element : list) {
      if (!(element instanceof String key)) {
        throw invalid("keys must contain strings only");
      }
      keys.add(key);
    }
    return keys;
  }

  private static void reject(Map<String, Object> request, Set<String> allowed) {
    for (String member : request.keySet()) {
      if (!allowed.contains(member)) {
        throw invalid("the request has the unknown member " + member);
      }
    }
  }

  private static ApiException invalid(String message) {
    return new ApiException(400, ErrorCodes.REQUEST_INVALID, message);
  }
}
