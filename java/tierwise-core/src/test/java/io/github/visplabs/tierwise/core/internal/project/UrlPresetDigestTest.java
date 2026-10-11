package io.github.visplabs.tierwise.core.internal.project;

import static io.github.visplabs.tierwise.core.internal.project.ProjectFixture.assertFindings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.CompileOptions;
import io.github.visplabs.tierwise.core.CompileResult;
import io.github.visplabs.tierwise.core.Finding;
import io.github.visplabs.tierwise.core.PresetFetcher;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.internal.json.Sha256;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * URL presets and their digest (MANIFEST-28, MANIFEST-29), through the {@link PresetFetcher} seam of the
 * compile options, which stands in for the retrieval interface of the MANIFEST-27 implementation note. The
 * conformance suite cannot serve a URL preset, so this test is the enforcer.
 */
class UrlPresetDigestTest {

  private static final String URL = "https://presets.invalid/team.yaml";

  /** A preset that declares a key and a lint severity, so that what it contributes can be observed. */
  private static final String PRESET = """
      keys:
        shop.preset.flag:
          type: boolean
          combine: any
          settable_at: [global]
      lint:
        rules:
          schema/description-required: error
      """;

  @TempDir
  Path dir;

  private static String digest(byte[] bytes) {
    return Sha256.hexDigest(bytes);
  }

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  /** A fetcher that serves fixed bytes and records the URLs it was asked for. */
  private static final class FakeRetrieval implements PresetFetcher {
    private final byte[] bytes;
    private final List<String> asked = new ArrayList<>();

    FakeRetrieval(byte[] bytes) {
      this.bytes = bytes;
    }

    @Override
    public byte[] fetch(String url) {
      asked.add(url);
      return bytes.clone();
    }
  }

  /** Writes a project that extends the URL preset with a digest and sets the preset's key at the root. */
  private CompileResult compile(String pinned, FakeRetrieval retrieval) {
    new ProjectFixture(dir).manifest("specVersion: \"0.1\"\nextends:\n  - url: \"" + URL + "\"\n    sha256: \"" + pinned
        + "\"\nschema:\n  decision_hierarchy:\n    levels: [global]\nsources:\n  values:\n"
        + "    - file: values/root.yaml\n      scope: global\n").file("values/root.yaml", "shop.preset.flag: true\n");
    return Tierwise.compile(dir, CompileOptions.builder().presetFetcher(retrieval).build());
  }

  private static List<String> codes(CompileResult result) {
    return result.findings().stream().map(Finding::code).toList();
  }

  @Test
  void MANIFEST_28_retrievedBytesWhoseDigestDiffersAreEPresetAtTheExtendsItem() {
    FakeRetrieval retrieval = new FakeRetrieval(utf8(PRESET));
    CompileResult result = compile(digest(utf8(PRESET + "# changed\n")), retrieval);
    Finding preset = result.findings().stream().filter(f -> f.code().equals(Code.E_PRESET)).findFirst()
        .orElseThrow(() -> new AssertionError(result.findings().toString()));
    assertEquals("tierwise.yaml", preset.file());
    assertEquals(List.of("extends", 0), preset.path());
    assertEquals(3, preset.line());
    assertEquals(List.of(URL), retrieval.asked);
    assertTrue(result.bundle().isEmpty());
  }

  @Test
  void MANIFEST_28_theDigestIsComputedOverTheExactRetrievedBytes() {
    byte[] lf = utf8(PRESET);
    byte[] crlf = utf8(PRESET.replace("\n", "\r\n"));
    assertTrue(codes(compile(digest(crlf), new FakeRetrieval(lf))).contains(Code.E_PRESET),
        "the same text with other line breaks is other bytes");
    byte[] withBom = new byte[lf.length + 3];
    withBom[0] = (byte) 0xEF;
    withBom[1] = (byte) 0xBB;
    withBom[2] = (byte) 0xBF;
    System.arraycopy(lf, 0, withBom, 3, lf.length);
    assertTrue(codes(compile(digest(lf), new FakeRetrieval(withBom))).contains(Code.E_PRESET));
  }

  @Test
  void MANIFEST_28_bytesThatMatchTheDigestAreAccepted() {
    CompileResult result = compile(digest(utf8(PRESET)), new FakeRetrieval(utf8(PRESET)));
    assertTrue(!codes(result).contains(Code.E_PRESET) && !codes(result).contains(Code.E_UNKNOWN_KEY),
        result.findings().toString());
    assertTrue(codes(result).contains(Code.LINT_SCHEMA_DESCRIPTION_REQUIRED), "the preset's lint setting applies");
  }

  @Test
  void MANIFEST_29_aPresetWhoseDigestDoesNotMatchContributesNothing() {
    CompileResult result = compile(digest(utf8("other")), new FakeRetrieval(utf8(PRESET)));
    assertTrue(codes(result).contains(Code.E_PRESET), result.findings().toString());
    assertTrue(codes(result).contains(Code.E_UNKNOWN_KEY), "the preset's key is not declared: " + result.findings());
    assertTrue(!codes(result).contains(Code.LINT_SCHEMA_DESCRIPTION_REQUIRED), result.findings().toString());
  }

  @Test
  void MANIFEST_29_theContentIsNotInterpretedBeforeTheDigestCheck() {
    // Bytes that would give E_PARSE, E_YAML_FEATURE, E_DUPLICATE_KEY or E_PRESET findings in the preset file if
    // they were read: invalid UTF-8, an alias, a repeated key and a member that a preset may not have.
    byte[] hostile = new byte[] {(byte) 0xC3, 0x28, '\n'};
    for (byte[] content : List.of(hostile, utf8("a: &x 1\nb: *x\na: 2\nsources: {}\n"), utf8("- [\n"))) {
      new ProjectFixture(dir).manifest("specVersion: \"0.1\"\nextends:\n  - url: \"" + URL + "\"\n    sha256: \""
          + digest(utf8("pinned bytes")) + "\"\n");
      ProjectLoad load = new ProjectFixture(dir).load(CompileOptions.builder()
          .presetFetcher(new FakeRetrieval(content)).build());
      assertFindings(load, "E_PRESET tierwise.yaml:3 [extends, 0]");
      assertTrue(load.sources().documents().isEmpty());
    }
  }
}
