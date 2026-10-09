package io.github.tierwise.core.internal.yaml;

import io.github.tierwise.core.Finding;
import io.github.tierwise.core.Severity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

/**
 * Loads one YAML source as a {@link PMap} tree with positions (SRC-1 to SRC-11).
 *
 * <p>The source is read as UTF-8 without a byte-order mark, as YAML 1.2 with the core schema. Aliases,
 * anchors, explicit tags and merge keys are reported as {@code E_YAML_FEATURE}, a repeated key after NFC
 * normalisation as {@code E_DUPLICATE_KEY} at its second occurrence, and a mapping key that is not a
 * string as {@code E_TYPE_MISMATCH} ({@code E_MANIFEST} for the manifest). A source that is not one
 * document with a mapping root is reported as {@code E_PARSE}, and as {@code E_MANIFEST} for the
 * manifest. The manifest is selected by the flag, so the caller does not remap codes.
 *
 * <p>A file-level fault is reported at the empty path and line 1 (FIND-23), and it is the only finding
 * of its source. The mapping keys of the tree are kept as written (FIND-20), unless the caller asks for
 * NFC normalisation of the keys of a mapping (see {@link #load(byte[], String, boolean, Predicate)}).
 */
public final class YamlLoader {

  private YamlLoader() {
  }

  /**
   * Loads one source. The keys of every mapping are kept as written.
   *
   * @param bytes the bytes of the source
   * @param file the source path relative to the project root, with {@code /} separators (FIND-17)
   * @param manifest {@code true} when the source is the manifest, which uses the manifest codes
   * @return the root, when the source has one, and the findings of the source
   */
  public static YamlLoadResult load(byte[] bytes, String file, boolean manifest) {
    return load(bytes, file, manifest, path -> false);
  }

  /**
   * Loads one source, normalising the keys of the mappings that a section requires.
   *
   * <p>FIND-20 says a key is held as the section that declares it requires. The loader does not know
   * the declaring section, so the caller tells it. For a mapping whose path {@code nfcKeysAt} accepts,
   * each key is NFC-normalised before it is used as a path segment, as a key of the tree and in a
   * finding. Duplicate detection is always after NFC normalisation (SRC-4). For example, the names of
   * facts are compared after NFC normalisation (FACT-3), and the names of naming patterns are not.
   *
   * @param bytes the bytes of the source
   * @param file the source path relative to the project root, with {@code /} separators (FIND-17)
   * @param manifest {@code true} when the source is the manifest, which uses the manifest codes
   * @param nfcKeysAt tells whether the keys of the mapping at a path are NFC-normalised
   * @return the root, when the source has one, and the findings of the source
   */
  public static YamlLoadResult load(
      byte[] bytes, String file, boolean manifest, Predicate<List<Object>> nfcKeysAt) {
    Objects.requireNonNull(bytes, "bytes");
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(nfcKeysAt, "nfcKeysAt");
    YamlCodes codes = YamlCodes.of(manifest);
    try {
      String text = YamlText.decode(bytes, codes);
      return new YamlNodeReader(file, text, codes, nfcKeysAt).read();
    } catch (YamlFault fault) {
      return fileFault(file, fault.code(), fault.getMessage());
    } catch (YamlEngineException ex) {
      return fileFault(file, codes.shape(), "YAML syntax error: " + firstLine(ex.getMessage()) + " (SRC-5)");
    }
  }

  private static YamlLoadResult fileFault(String file, String code, String message) {
    Finding finding = Finding.of(code, Severity.ERROR, file, List.of(), 1, message);
    return new YamlLoadResult(Optional.empty(), List.of(finding));
  }

  private static String firstLine(String message) {
    if (message == null) {
      return "";
    }
    int end = message.indexOf('\n');
    return end < 0 ? message : message.substring(0, end);
  }
}
