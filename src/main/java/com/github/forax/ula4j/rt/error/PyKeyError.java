package com.github.forax.ula4j.rt.error;

import java.io.Serial;

/// Runtime representation of the Python KeyError exception.
public final class PyKeyError extends PyLookupError {
  @Serial
  private static final long serialVersionUID = 1L;

  public PyKeyError(String message) {
    super(message);
  }

  public PyKeyError(String message, Throwable cause) {
    super(message, cause);
  }
}
