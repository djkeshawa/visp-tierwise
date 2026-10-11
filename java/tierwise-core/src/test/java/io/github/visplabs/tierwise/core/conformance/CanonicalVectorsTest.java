package io.github.visplabs.tierwise.core.conformance;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs every canonical vector under {@code spec/conformance/canonical/} (VEC-2, VEC-75 to VEC-84). One
 * dynamic test per file, named by the vector id.
 */
@Tag("conformance")
class CanonicalVectorsTest {

  @TestFactory
  Stream<DynamicTest> canonicalVectors() {
    Path root = VectorFiles.conformanceDir().resolve("canonical");
    return VectorFiles.yamlFiles(root).stream()
        .map(file -> dynamicTest(VectorFiles.idOf(root, file), file.toUri(), () -> CanonicalRunner.run(file)));
  }
}
