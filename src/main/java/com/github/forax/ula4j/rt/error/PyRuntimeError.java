package com.github.forax.ula4j.rt.error;

import java.io.Serial;

/// Runtime representation of the Python RuntimeError exception.
public final class PyRuntimeError extends PyException {
  @Serial
  private static final long serialVersionUID = 1L;

  public PyRuntimeError(String message) {
    super(message);
  }

  public PyRuntimeError(String message, Throwable cause) {
    super(message, cause);
  }
}
