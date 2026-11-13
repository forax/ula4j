package com.github.forax.ula4j.ast;

import static java.util.Objects.requireNonNull;

public final class Script {
  private final Block body;

  Script(Block body) {
    requireNonNull(body);
    this.body = body;
  }

  public Block body() {
    return body;
  }

  @Override
  public String toString() {
    return "Script[" +
        "body=" + body + ']';
  }

}