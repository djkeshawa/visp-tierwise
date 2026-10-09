package io.github.tierwise.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Writes the provenance sidecar of a bundle (BUNDLE-139 to BUNDLE-150).
 *
 * <p>The sidecar is named {@code <stem>.provenance.json}, where the stem is the bundle file's name
 * without its {@code .json} extension (BUNDLE-149). It holds {@code bundleVersion}, {@code createdAt} in
 * RFC 3339 UTC with seconds and no fraction (BUNDLE-150 (b)), and {@code gitCommit} when the commit is
 * known (BUNDLE-150 (a)). The sidecar never changes the bundle bytes (BUNDLE-140, DET-3), and an
 * existing sidecar is never replaced (BUNDLE-147).
 */
final class Provenance {

  private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");
  private static final long GIT_TIMEOUT_SECONDS = 5;

  private Provenance() {
  }

  /**
   * Returns the path of the sidecar of a bundle file (BUNDLE-149).
   *
   * @param bundle the bundle file
   * @return the sidecar path, in the same directory
   */
  static Path sidecarFor(Path bundle) {
    String name = bundle.getFileName().toString();
    String stem = name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
    return bundle.resolveSibling(stem + ".provenance.json");
  }

  /**
   * Finds the commit that the sources are at: {@code GITHUB_SHA} when it is a full commit identifier,
   * else the output of {@code git rev-parse HEAD} in the project directory. Anything else is "no commit"
   * and the member is left out.
   *
   * @param getenv reads an environment variable
   * @param projectDir the project directory
   * @return the commit as 40 or 64 lowercase hexadecimal digits, or empty
   */
  static Optional<String> gitCommit(Function<String, String> getenv, Path projectDir) {
    String fromEnvironment = getenv.apply("GITHUB_SHA");
    if (fromEnvironment != null) {
      Optional<String> commit = commit(fromEnvironment);
      if (commit.isPresent()) {
        return commit;
      }
    }
    return commit(gitHead(projectDir));
  }

  /**
   * Writes the sidecar of a bundle.
   *
   * @param bundle the bundle file, which has been written
   * @param bundleVersion the {@code bundleVersion} of the bundle
   * @param clock supplies {@code createdAt}
   * @param gitCommit the commit, if known
   * @return the path of the sidecar
   * @throws UsageException if the sidecar already exists (BUNDLE-147) or cannot be written
   */
  static Path write(Path bundle, String bundleVersion, Clock clock, Optional<String> gitCommit) {
    Path sidecar = sidecarFor(bundle);
    try {
      Files.writeString(sidecar, json(bundleVersion, clock.instant(), gitCommit), StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    } catch (FileAlreadyExistsException e) {
      throw new UsageException("the provenance sidecar exists and is never replaced (BUNDLE-147): " + sidecar, e);
    } catch (IOException e) {
      throw new UsageException("cannot write " + sidecar + ": " + e.getMessage(), e);
    }
    return sidecar;
  }

  /**
   * Formats a sidecar.
   *
   * @param bundleVersion the {@code bundleVersion}
   * @param createdAt the time of creation, which is cut to whole seconds
   * @param gitCommit the commit, if known
   * @return the JSON text, ending in a line feed
   */
  static String json(String bundleVersion, Instant createdAt, Optional<String> gitCommit) {
    StringBuilder text = new StringBuilder("{\n");
    text.append("  \"bundleVersion\": \"").append(bundleVersion).append("\",\n");
    text.append("  \"createdAt\": \"")
        .append(DateTimeFormatter.ISO_INSTANT.format(createdAt.truncatedTo(ChronoUnit.SECONDS)))
        .append('"');
    gitCommit.ifPresent(commit -> text.append(",\n  \"gitCommit\": \"").append(commit).append('"'));
    return text.append("\n}\n").toString();
  }

  private static Optional<String> commit(String text) {
    if (text == null) {
      return Optional.empty();
    }
    String lower = text.trim().toLowerCase(java.util.Locale.ROOT);
    return COMMIT.matcher(lower).matches() ? Optional.of(lower) : Optional.empty();
  }

  private static String gitHead(Path projectDir) {
    try {
      Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
          .directory(projectDir.toFile())
          .redirectError(ProcessBuilder.Redirect.DISCARD)
          .start();
      process.getOutputStream().close();
      if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return null;
      }
      return process.exitValue() == 0 ? new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8) : null;
    } catch (IOException e) {
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }
}
