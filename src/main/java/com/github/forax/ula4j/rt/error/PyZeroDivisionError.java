package com.github.forax.ula4j.rt.error;

import java.io.Serial;

/// Runtime representation of the Python ZeroDivisionError exception.
public final class PyZeroDivisionError extends PyArithmeticError {
  @Serial
  private static final long serialVersionUID = 1L;

  public PyZeroDivisionError(String message) {
    super(message);
  }

  public PyZeroDivisionError(String message, Throwable cause) {
    super(message, cause);
  }
}
