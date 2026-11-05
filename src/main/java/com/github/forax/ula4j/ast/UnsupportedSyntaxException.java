package com.github.forax.ula4j.ast;

/** The input is valid Python, but not currently supported. */
public final class UnsupportedSyntaxException extends RuntimeException {
  private final String construct;
  private final int line;
  private final int column;

  public UnsupportedSyntaxException(String construct, int line, int column) {
    super("unsupported syntax '" + construct + "' at line " + line + ":" + column);
    this.construct = construct;
    this.line = line;
    this.column = column;
  }

  public String construct() { return construct; }
  public int line() { return line; }
  public int column() { return column; }
}