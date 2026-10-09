package io.github.tierwise.e2e;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.spring.DecisionEngine;
import io.github.tierwise.spring.Resolver;
import io.github.tierwise.spring.TierwiseAutoConfiguration;
import io.github.tierwise.spring.TierwiseEvaluators;
import io.github.tierwise.spring.TierwiseProvider;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The Java service: an application context with the Spring Boot starter, reading the bundle from the server URL and
 * refreshing it on a short interval. Nothing but properties configures it, as in an application.
 */
final class JavaClient implements AutoCloseable {

  /** The starter's auto-configuration and the two providers, which an application would also declare. */
  @Configuration(proxyBeanMethods = false)
  @Import(TierwiseAutoConfiguration.class)
  static class Application {

    @Bean
    TierwiseProvider account() {
      return TierwiseProvider.of("account", TestProviders::account);
    }

    @Bean
    TierwiseProvider user() {
      return TierwiseProvider.of("user", TestProviders::user);
    }
  }

  private final ConfigurableApplicationContext context;
  private final TierwiseEvaluators evaluators;
  private final Resolver resolver;
  private final DecisionEngine decisions;

  private JavaClient(ConfigurableApplicationContext context) {
    this.context = context;
    this.evaluators = context.getBean(TierwiseEvaluators.class);
    this.resolver = context.getBean(Resolver.class);
    this.decisions = context.getBean(DecisionEngine.class);
  }

  /** Starts the context. The first bundle is loaded before this returns. */
  static JavaClient start(String serverUrl, Duration refreshInterval) {
    ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
        .web(WebApplicationType.NONE)
        .properties(
            "spring.main.banner-mode=off",
            "logging.level.root=warn",
            "tierwise.server.url=" + serverUrl,
            "tierwise.refresh-interval=" + refreshInterval.toMillis() + "ms")
        .run();
    return new JavaClient(context);
  }

  /** The {@code bundleVersion} in use. */
  String bundleVersion() {
    return evaluators.bundleVersion();
  }

  /** Runs the cases and returns the outcome of each by id, in the order of the cases. */
  Map<String, JsonNode> evaluate(List<JsonNode> cases) {
    Map<String, JsonNode> outcomes = new LinkedHashMap<>();
    for (JsonNode c : cases) {
      outcomes.put(c.get("id").asString(), evaluate(c));
    }
    return outcomes;
  }

  private JsonNode evaluate(JsonNode c) {
    ObjectNode outcome = Json.MAPPER.createObjectNode();
    outcome.put("id", c.get("id").asString());
    try {
      Context context = Context.fromJson(Json.plain(c.get("context")));
      if (c.get("call").asString().equals("resolve")) {
        outcome.set("result", Json.tree(resolver.resolve(context, c.get("key").asString()).toJson()));
      } else {
        FactSet facts = c.has("input") ? FactSet.fromJson(Json.plain(c.get("input"))) : FactSet.empty();
        outcome.set("result", Json.tree(decisions.decide(context, c.get("feature").asString(), facts).toJson()));
      }
    } catch (TierwiseException e) {
      outcome.set("error", Json.MAPPER.createObjectNode().put("code", e.code()));
    }
    return outcome;
  }

  @Override
  public void close() {
    context.close();
  }
}
