package io.github.tierwise.core.internal.eval;

import static io.github.tierwise.core.internal.eval.TestModels.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.EvaluationEvent;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Runtime values and the codes of section 4: an evaluator checks an {@code iana_tz} value by its syntax only and
 * consults no time-zone data (TYPE-37, TYPE-38), and a value received at runtime is never reported with a code
 * of section 4 (TYPE-73).
 */
class RuntimeValueCodesTest {

  /** The codes of section 4, which apply to source values only. */
  private static final List<String> SECTION_4_CODES = List.of(Code.E_TYPE_REQUIRED, Code.E_INVALID_DECLARATION,
      Code.E_TYPE_MISMATCH, Code.E_INTEGER_RANGE, Code.E_OUT_OF_RANGE, Code.E_NOT_IN_UNIVERSE, Code.E_DUPLICATE_VALUE);

  private static final Context ACCOUNT = Context.builder().level("division", "north").level("account", "a1").build();

  private final TestModels.Builder builder = TestModels.standard();

  RuntimeValueCodesTest() {
    builder.key("k.zone", TestModels.key(TestModels.stringFormat("iana_tz"), "override", "Etc/UTC",
        List.of("global", "division", "account"), null, "skip"));
    builder.value("division", "south", "k.zone", "Mars/Olympus_Mons");
  }

  @Test
  void TYPE_38_aWellFormedZoneNameIsAValidRuntimeValueWhetherOrNotATzDatabaseListsIt() {
    Evaluator evaluator = TestModels.evaluator(builder, TestModels.values(Map.of("k.zone", "Atlantis/Lost_City")),
        node -> Map.of(), null);
    Decision without = evaluator.decide(Context.empty(), "f.gate", FactSet.empty());
    for (String zone : List.of("Mars/Olympus_Mons", "Europe/London", "Etc/GMT+99", "A", "x_y/z-1/W+2")) {
      Decision decision = evaluator.decide(Context.empty(), "f.gate", FactSet.builder().fact("order.zone", zone).build());
      assertEquals(without.toJson(), decision.toJson(), zone);
    }
    for (String zone : List.of("", "Europe London", "/Europe", "Europe/", "Europe//London", "Zürich", "UTC+01:00")) {
      TierwiseException e = assertThrows(TierwiseException.class,
          () -> evaluator.decide(Context.empty(), "f.gate", FactSet.builder().fact("order.zone", zone).build()));
      assertEquals(Code.E_FACTS_INVALID, e.code(), zone);
    }
    assertEquals("Mars/Olympus_Mons",
        evaluator.resolveLocal(Context.builder().level("division", "south").build(), "k.zone").value());
    assertEquals("Atlantis/Lost_City", evaluator.resolve(ACCOUNT, "k.zone").value(),
        "a well-formed zone from a provider is a valid value");
  }

  @Test
  void TYPE_38_theEvaluatorSourcesUseNoTimeZoneFacility() throws IOException, URISyntaxException {
    Path core = mainSources();
    Pattern forbidden = Pattern.compile("\\b(ZoneId|ZoneOffset|ZoneRules\\w*|ZonedDateTime|OffsetDateTime|OffsetTime"
        + "|TimeZone|TimeZoneNames|Calendar|DateFormat|SimpleDateFormat|DateTimeFormatter)\\b|java\\.time\\.zone|tz-names");
    List<Path> files = new ArrayList<>();
    try (Stream<Path> top = Files.list(core)) {
      top.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
    }
    for (String evaluatorPackage : List.of("eval", "model", "bundle", "json")) {
      try (Stream<Path> listed = Files.list(core.resolve("internal").resolve(evaluatorPackage))) {
        listed.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
      }
    }
    assertTrue(files.size() > 20, "the evaluator sources were found under " + core);
    for (Path file : files) {
      Matcher matcher = forbidden.matcher(Files.readString(file, StandardCharsets.UTF_8));
      assertFalse(matcher.find(), () -> file + " names the time-zone facility " + matcher.group());
    }
  }

  private static Path mainSources() throws URISyntaxException {
    Path classes = Path.of(EvaluatorImpl.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path sources = classes.getParent().getParent().resolve(Path.of("src", "main", "java", "io", "github", "tierwise", "core"));
    assertTrue(Files.isDirectory(sources), sources.toString());
    return sources;
  }

  @Test
  void TYPE_73_aFactValueThatFailsValidationRaisesFactsInvalidAndNeverACodeOfSectionFour() {
    Evaluator evaluator = TestModels.evaluator(builder, TestModels.values(Map.of()), node -> Map.of(), null);
    List<Map<String, Object>> facts = List.of(
        mapOf("order.lines", 101L), mapOf("order.lines", -1L), mapOf("order.lines", 1L << 53),
        mapOf("order.lines", BigInteger.TWO.pow(70)), mapOf("order.lines", 1.5), mapOf("order.lines", "7"),
        mapOf("order.kind", "OTHER"), mapOf("order.total", "abc"), mapOf("order.total", 1.5),
        mapOf("order.window", "24:00"), mapOf("order.zone", "not a zone"), mapOf("order.label", "x\uD800"),
        mapOf("customer.active", "true"));
    for (Map<String, Object> set : facts) {
      TierwiseException e = assertThrows(TierwiseException.class,
          () -> evaluator.decide(Context.empty(), "f.gate", FactSet.of(set, Map.of())));
      assertEquals(Code.E_FACTS_INVALID, e.code(), set.toString());
    }
    TierwiseException child = assertThrows(TierwiseException.class,
        () -> evaluator.decide(Context.empty(), "f.gate", FactSet.builder().child("item", Map.of("item.stock", "NONE")).build()));
    assertEquals(Code.E_FACTS_INVALID, child.code());
  }

  @Test
  void TYPE_73_aProviderValueThatFailsValidationIsAnInvalidEntryAndNeverReportedWithACodeOfSectionFour() {
    builder.key("k.colours", TestModels.key(TypeDecl.set(List.of("red", "green", "blue")), "union", null,
        List.of("global", "account"), null, "skip"));
    List<Map<String, Object>> replies = List.of(
        mapOf("k.min_skip", 501L), // above the declared max
        mapOf("k.min_skip", 0L), // below the declared min
        mapOf("k.min_skip", 1L << 53), // outside the integer range
        mapOf("k.min_skip", 1.5),
        mapOf("k.min_skip", "7"),
        mapOf("k.colours", List.of("purple")), // not in the universe
        mapOf("k.colours", List.of("red", "red")), // a repeated member
        mapOf("k.colours", "red"),
        mapOf("k.deep", mapOf("cap", "x")),
        mapOf("k.deep", mapOf("nope", 1L)), // an unknown field
        mapOf("k.all_skip", null),
        mapOf("k.label", 5L),
        mapOf("k.zone", "not a zone"));
    for (Map<String, Object> reply : replies) {
      String key = reply.keySet().iterator().next();
      List<EvaluationEvent> events = new ArrayList<>();
      Evaluator evaluator = TestModels.evaluator(builder, node -> reply, node -> Map.of(), events::add);
      Resolved resolved = evaluator.resolve(ACCOUNT, key);
      Resolved.Contribution account = resolved.contributions().stream()
          .filter(entry -> entry.name().equals("account")).findFirst().orElseThrow();
      assertEquals(Resolved.Contribution.Label.INVALID, account.effect(), reply.toString());
      assertTrue(events.stream().anyMatch(event -> event.type() == EvaluationEvent.Type.SOURCE_INVALID), reply.toString());
      String reported = resolved.toJson() + " " + events;
      for (String code : SECTION_4_CODES) {
        assertFalse(reported.contains(code), code + " reported for " + reply);
      }
    }
  }

  @Test
  void TYPE_73_noCodeOfSectionFourIsAnEvaluationCode() {
    for (String code : SECTION_4_CODES) {
      assertFalse(Code.evaluation().contains(code), code);
    }
  }
}
