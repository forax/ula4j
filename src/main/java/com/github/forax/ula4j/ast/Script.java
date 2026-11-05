package com.github.forax.ula4j.ast;

import static java.util.Objects.requireNonNull;

public record Script(Block body) {
  public Script {
    requireNonNull(body);
  }
}