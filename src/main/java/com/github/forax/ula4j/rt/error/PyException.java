package com.github.forax.ula4j.rt.error;

import java.io.Serial;

/// Runtime representation of the Python Exception exception.
public sealed class PyException extends PyBaseException
    permits PyArithmeticError, PyAttributeError, PyLookupError, PyNameError, PyRuntimeError, PyTypeError, PyValueError {
  @Serial
  private static final long serialVersionUID = 1L;

  public PyException(String message) {
    super(message);
  }

  public PyException(String message, Throwable cause) {
    super(message, cause);
  }
}
