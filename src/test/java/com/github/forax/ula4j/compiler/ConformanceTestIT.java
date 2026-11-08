package com.github.forax.ula4j.compiler;

import com.github.forax.ula4j.ast.UnsupportedSyntaxException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ConformanceTestIT {

    private static final Path BASICS_DIR = Path.of("target/test-classes/basics");

    private static Path toGolden(Path pyFile) {
        var goldenFileName = pyFile.getFileName().toString().replace(".py", ".golden");
        return pyFile.resolveSibling(goldenFileName);
    }

    /** Provides each .py file that has a matching .golden file. */
    static Stream<Path> pyFiles() throws IOException {
        return Files.list(BASICS_DIR)
                .filter(p -> p.toString().endsWith(".py"))
                .filter(p -> Files.exists(toGolden(p)))
                .map(BASICS_DIR::relativize);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pyFiles")
    public void compareWithGolden(Path filename) throws Throwable {
        var pyFile = BASICS_DIR.resolve(filename);

        // 1. Read the Python source
        var source = Files.readString(pyFile);

        // 2. Read the expected golden output
        var goldenFile = toGolden(pyFile);
        var expected = Files.readString(goldenFile);

        // 3. Capture stdout while running the ula4j runtime
        String actual;
        var originalOut = System.out;
        try (var baos = new ByteArrayOutputStream();
             var capture = new PrintStream(baos)) {
            System.setOut(capture);
            //System.setErr(capture);

            // Execute the source with ula4j
            try {
                Compiler.run(source);
            } catch(UnsupportedSyntaxException e) {
                Assumptions.assumeTrue(false, e.getMessage());
                return;
            }

            // Capture the result
            actual = baos.toString();
        } finally {
            System.setOut(originalOut);
            //System.setErr(originalErr);
        }

        // 4. Compare
        assertEquals(expected, actual,
                "Output mismatch for " + pyFile.getFileName());
    }
}