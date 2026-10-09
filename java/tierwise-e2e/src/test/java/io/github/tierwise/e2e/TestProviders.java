package io.github.tierwise.e2e;

import java.util.List;
import java.util.Map;

/**
 * The values of the service-sourced levels, served identically by the Java client, the server and the Node client
 * (node-client.mjs repeats this table). Without them, the three would agree trivially on {@code UNAVAILABLE}.
 */
final class TestProviders {

  private TestProviders() {
  }

  /** The level {@code account}: a narrower shipping list, a failing node, and a container-order veto. */
  static Map<String, Object> account(String nodeId) throws Exception {
    return switch (nodeId) {
      case "acct-1" -> Map.of("checkout.shipping.allowed_methods", List.of("PICKUP"));
      case "acct-2" -> throw new IllegalStateException("the account service is down");
      case "acct-3" -> Map.of("checkout.container_order.allowed", false);
      default -> Map.of();
    };
  }

  /** The level {@code user}: no key is settable there, so it never has a value. */
  static Map<String, Object> user(String nodeId) {
    return Map.of();
  }
}
