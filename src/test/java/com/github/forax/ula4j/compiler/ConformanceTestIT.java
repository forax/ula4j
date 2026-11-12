package com.github.forax.ula4j.compiler;

import com.github.forax.ula4j.ast.ASTBuilder;
import com.github.forax.ula4j.ast.Script;
import com.github.forax.ula4j.ast.SyntaxErrorException;
import com.github.forax.ula4j.ast.UnsupportedSyntaxException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// failsafe maven plugin is not yet activate on this repository
/// You have to run this conformance test by hand (this is the early days, do not expect a miracle)
public final class ConformanceTestIT {

  private static final Path BASICS_DIR = Path.of("target/test-classes/basics");

  private static Path toGolden(Path pyFile) {
    var goldenFileName = pyFile.getFileName().toString().replace(".py", ".golden");
    return pyFile.resolveSibling(goldenFileName);
  }

  private record TestPair(Path pyFile, String source, String goldenSource, Script script) {
    @Override
    public String toString() {
      return BASICS_DIR.relativize(pyFile).toString();
    }
  }

  private static TestPair createTestPair(Path pyFile) {
    try {
      var source = Files.readString(pyFile);
      var script = ASTBuilder.createScript(source);
      ScopeAnalyzer.analyze(script);

      var goldenFile = toGolden(pyFile);
      var goldenSource = Files.readString(goldenFile);
      return new TestPair(pyFile, source, goldenSource, script);
    } catch (IOException e) {
      throw new IOError(e);
    } catch(SyntaxErrorException | UnsupportedSyntaxException e) {
      return null;
    }
  }

  /** Provides each .py file that has a matching .golden file. */
  static Stream<TestPair> pyFiles() throws IOException {
    return Files.list(BASICS_DIR)
        .filter(p -> p.toString().endsWith(".py"))
        .flatMap(p -> Stream.ofNullable(createTestPair(p)))
        .sorted(Comparator.comparing(TestPair::pyFile));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("pyFiles")
  public void compareWithGolden(TestPair testPair) throws Throwable {
    var pyFile = testPair.pyFile;
    var source = testPair.source;
    var goldenSource = testPair.goldenSource;

    String actual;
    var originalOut = System.out;
    try (var out = new ByteArrayOutputStream();
         var capture = new PrintStream(out)) {
      System.setOut(capture);
      //System.setErr(capture);

      var script = ASTBuilder.createScript(source);

      // Execute the source with ula4j
      try {
        Compiler.run(script);
      } catch (UnsupportedSyntaxException e) {
        Assumptions.assumeTrue(false, e.getMessage());
        return;
      }

      // Capture the result
      actual = out.toString();
    } finally {
      System.setOut(originalOut);
      //System.setErr(originalErr);
    }

    // 4. Compare
    assertEquals(goldenSource, actual,
        "Output mismatch for " + pyFile.getFileName());
  }
}