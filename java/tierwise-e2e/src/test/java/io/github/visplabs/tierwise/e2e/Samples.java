package io.github.visplabs.tierwise.e2e;

import io.github.visplabs.tierwise.core.CompileResult;
import io.github.visplabs.tierwise.core.Tierwise;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** The sample project and its modified copy, compiled with the core compiler. */
final class Samples {

  private Samples() {
  }

  /** Compiles a project and returns the canonical bundle; any error finding fails the test. */
  static byte[] compile(Path project) {
    CompileResult result = Tierwise.compile(project);
    if (result.hasErrors() || result.bundle().isEmpty()) {
      throw new AssertionError("the project " + project + " does not compile: " + result.findings());
    }
    return result.bundle().orElseThrow();
  }

  /**
   * Copies the sample into {@code target} and changes a value and a binding:
   *
   * <ul>
   *   <li>the value {@code checkout.pickup.lead_hours} of division {@code south} goes from 6 to 12 (it also wins over
   *       the seller value 8, so the axis comparison changes);
   *   <li>the binding of division {@code south} allows {@code DROP_SHIP} items on a credit hold, and the one example
   *       that expected the old outcome now expects the new one.
   * </ul>
   *
   * Each replacement must match exactly once, so a change to the sample makes this method fail instead of the test
   * passing on an unchanged copy.
   */
  static Path modifiedCopy(Path sample, Path target) {
    copy(sample, target);
    replace(target.resolve("values/division/south.yaml"),
        "checkout.pickup.lead_hours: 6", "checkout.pickup.lead_hours: 12");
    Path rules = target.resolve("rules/checkout.order_submission.yaml");
    replace(rules,
        "south: { policy: credit_check, allowed_on_credit_hold: [STOCKED, SPECIAL_ORDER] }",
        "south: { policy: credit_check, allowed_on_credit_hold: [STOCKED, SPECIAL_ORDER, DROP_SHIP] }");
    replace(rules,
        "  - name: Mixed basket on credit hold is denied\n"
            + "    context: { levels: { division: south } }\n"
            + "    facts: { customer.active: true, customer.credit_hold: true, item.stock_type: [STOCKED, DROP_SHIP] }\n"
            + "    expect: DENY\n",
        "  - name: Mixed basket on credit hold is allowed\n"
            + "    context: { levels: { division: south } }\n"
            + "    facts: { customer.active: true, customer.credit_hold: true, item.stock_type: [STOCKED, DROP_SHIP] }\n"
            + "    expect: ALLOW\n");
    return target;
  }

  private static void replace(Path file, String from, String to) {
    try {
      String text = Files.readString(file, StandardCharsets.UTF_8);
      int first = text.indexOf(from);
      if (first < 0 || text.indexOf(from, first + 1) >= 0) {
        throw new AssertionError("expected exactly one occurrence in " + file + " of: " + from);
      }
      Files.writeString(file, text.replace(from, to), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void copy(Path from, Path to) {
    try (Stream<Path> walk = Files.walk(from)) {
      for (Path source : (Iterable<Path>) walk::iterator) {
        Path destination = to.resolve(from.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(destination);
        } else {
          Files.copy(source, destination);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
