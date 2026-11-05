package com.github.forax.ula4j.rt.error;

import java.io.Serial;

/// Runtime representation of the Python TypeError exception.
public final class PyTypeError extends PyException {
  @Serial
  private static final long serialVersionUID = 1L;

  public PyTypeError(String message) {
    super(message);
  }

  public PyTypeError(String message, Throwable cause) {
    super(message, cause);
  }
}
