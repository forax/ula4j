package com.github.forax.ula4j.compiler;

/// A well-formed program that violates a Java-style scoping or assignment rule.
public final class SemanticErrorException extends RuntimeException {
  private final int line;
  private final int column;

  public SemanticErrorException(String message, int line, int column) {
    this.line = line;
    this.column = column;
    super("semantic error: " + message + " at line " + line + ":" + column);
  }

  public int line()  {
    return line;
  }
  public int column() {
    return column;
  }
}