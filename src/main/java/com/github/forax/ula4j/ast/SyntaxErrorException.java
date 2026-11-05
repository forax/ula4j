package com.github.forax.ula4j.ast;

/** The input is not valid Python (also used for errors CPython reports after parsing). */
public final class SyntaxErrorException extends RuntimeException {
  private final int line;
  private final int column;

  public SyntaxErrorException(String reason, int line, int column) {
    super("syntax error: " + reason + " at line " + line + ":" + column);
    this.line = line;
    this.column = column;
  }

  public int line() { return line; }
  public int column() { return column; }
}