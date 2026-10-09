package io.github.tierwise.core.conformance;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs every compile case under {@code spec/conformance/compile/} (VEC-3). One dynamic test per case
 * directory, named by the case id (the directory name).
 */
@Tag("conformance")
class CompileCasesTest {

  @TestFactory
  Stream<DynamicTest> compileCases() {
    Path root = VectorFiles.conformanceDir().resolve("compile");
    return VectorFiles.childDirectories(root).stream()
        .map(dir -> dynamicTest(dir.getFileName().toString(), dir.toUri(), () -> CompileRunner.run(dir)));
  }
}
