package com.github.forax.ula4j.rt.error;

import java.io.Serial;

/// Runtime representation of the Python LookupError exception.
public sealed class PyLookupError extends PyException permits PyIndexError, PyKeyError {
  @Serial
  private static final long serialVersionUID = 1L;

  public PyLookupError(String message) {
    super(message);
  }

  public PyLookupError(String message, Throwable cause) {
    super(message, cause);
  }
}
