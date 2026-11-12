package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.compiler.Compiler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class BuiltinsTest {
  private static String runCaptureOutput(String source) throws IOException {
    try(var out = new ByteArrayOutputStream();
        var printStream = new PrintStream(out)) {
      var originalOut = System.out;
      System.setOut(printStream);
      try {
        Compiler.run(source);
      } finally {
        System.setOut(originalOut);
      }
      return out.toString();
    }
  }


  @Nested
  public final class Print {
    @Test
    public void printOneArgument() throws IOException {
      var out = runCaptureOutput("""
          print("hello, world!")
          """);
      assertEquals("hello, world!\n", out);
    }

    @Test
    public void printTwoArgument() throws IOException {
      var out = runCaptureOutput("""
          print("one", "two")
          """);
      assertEquals("one two\n", out);
    }

    @Test
    public void printScalarValues() throws IOException {
      var out = runCaptureOutput("""
          print(12, 3.4, True, "hello", None, 12345678901234567890)
          """);
      assertEquals("12 3.4 True hello None 12345678901234567890\n", out);
    }
  }
}
