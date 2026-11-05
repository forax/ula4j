package com.github.forax.ula4j.rt.error;

import java.io.Serial;

/// Runtime representation of the Python AttributeError exception.
public final class PyAttributeError extends PyException {
  @Serial
  private static final long serialVersionUID = 1L;

  public PyAttributeError(String message) {
    super(message);
  }

  public PyAttributeError(String message, Throwable cause) {
    super(message, cause);
  }
}
