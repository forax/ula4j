package com.github.forax.ula4j.rt.error;

import java.io.Serial;

/// Runtime representation of the Python OverflowError exception.
public final class PyOverflowError extends PyArithmeticError {
  @Serial
  private static final long serialVersionUID = 1L;

  public PyOverflowError(String message) {
    super(message);
  }

  public PyOverflowError(String message, Throwable cause) {
    super(message, cause);
  }
}
