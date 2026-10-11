package io.github.visplabs.tierwise.core.conformance;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs every evaluate vector under {@code spec/conformance/evaluate/} (VEC-2, VEC-24). One dynamic test
 * per file, named by the vector id.
 */
@Tag("conformance")
class EvaluateVectorsTest {

  @TestFactory
  Stream<DynamicTest> evaluateVectors() {
    Path root = VectorFiles.conformanceDir().resolve("evaluate");
    return VectorFiles.yamlFiles(root).stream()
        .map(file -> dynamicTest(VectorFiles.idOf(root, file), file.toUri(), () -> EvaluateRunner.run(file)));
  }
}
