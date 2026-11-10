package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.compiler.Compiler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public final class BuiltinsTest {
  @Test
  public void print() throws IOException {
    try(var out = new ByteArrayOutputStream();
        var printStream = new PrintStream(out)) {
      var originalOut = System.out;
      System.setOut(printStream);
      try {
        Compiler.run("""
            print("hello, world!")
            """);
      } finally {
        System.setOut(originalOut);
      }
    }
  }
}
