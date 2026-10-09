package io.github.tierwise.server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.github.tierwise.core.Tierwise;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Test data: the conformance bundles, publication records, JWT mocks and the shared store directory. */
public final class Fixtures {

  /** A token that may publish, with the default role names. */
  public static final RequestPostProcessor PUBLISHER = token("tierwise-publisher");
  /** A token that may promote, with the default role names. */
  public static final RequestPostProcessor PROMOTER = token("tierwise-promoter");
  /** A valid token without any role. */
  public static final RequestPostProcessor READER = jwt();
  /** The directory of the filesystem store that every test context uses. */
  public static final Path STORE = createStore();

  private Fixtures() {
  }

  /** Returns a JWT mock with the given roles as authorities, as {@code RoleClaimJwtConverter} creates them. */
  public static RequestPostProcessor token(String... roles) {
    return jwt().authorities(Stream.of(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .toArray(GrantedAuthority[]::new));
  }

  /** Reads a conformance bundle, for example {@code commerce.json}. */
  public static byte[] bundle(String name) {
    return read(Path.of(System.getProperty("tierwise.spec.dir", "../../spec"), "conformance", "bundles", name));
  }

  /** Reads an invalid conformance bundle, for example {@code digest-mismatch.json}. */
  public static byte[] invalidBundle(String name) {
    return read(Path.of(System.getProperty("tierwise.spec.dir", "../../spec"), "conformance", "bundles", "invalid", name));
  }

  /** Returns the {@code bundleVersion} of bundle bytes, as the core computes it. */
  public static String version(byte[] bundle) {
    return Tierwise.loadBundle(bundle).bundleVersion();
  }

  /** A valid publication record. */
  public static String record(String createdAt) {
    return "{\"createdAt\":\"" + createdAt + "\"}";
  }

  /** A valid publication record with all members. */
  public static String record(String createdAt, String commit, String publisher) {
    return "{\"createdAt\":\"" + createdAt + "\",\"gitCommit\":\"" + commit + "\",\"publisher\":\"" + publisher + "\"}";
  }

  /** The request that publishes a bundle with a record. */
  public static MockMultipartHttpServletRequestBuilder publish(byte[] bundle, String record) {
    return MockMvcRequestBuilders.multipart("/v1/bundles")
        .file(new MockMultipartFile("bundle", "bundle.json", "application/json", bundle))
        .file(new MockMultipartFile("record", "record.json", "application/json", record.getBytes(StandardCharsets.UTF_8)));
  }

  /** A 40-digit commit identifier made of one repeated digit. */
  public static String commit(char digit) {
    return String.valueOf(digit).repeat(40);
  }

  /** Empties the store: removes every bundle, record and the latest pointer. */
  public static void resetStore() {
    try {
      for (String directory : List.of("bundles", "records")) {
        deleteChildren(STORE.resolve(directory));
      }
      Files.deleteIfExists(STORE.resolve("latest"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void deleteChildren(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(directory)) {
      for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
        if (!path.equals(directory)) {
          Files.delete(path);
        }
      }
    }
  }

  private static byte[] read(Path path) {
    try {
      return Files.readAllBytes(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path createStore() {
    try {
      Path directory = Files.createTempDirectory("tierwise-server-test");
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try (Stream<Path> walk = Files.walk(directory)) {
          walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
          // The directory is in the system temporary directory; a leftover is harmless.
        }
      }));
      return directory;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
