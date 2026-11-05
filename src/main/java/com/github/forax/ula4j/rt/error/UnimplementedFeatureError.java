package com.github.forax.ula4j.rt.error;

import java.io.Serial;

/// Raised when CPython supports a feature that ula4j does not implement yet.
public class UnimplementedFeatureError extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  public UnimplementedFeatureError(String message) {
    super(message);
  }

  public UnimplementedFeatureError(String message, Throwable cause) {
    super(message, cause);
  }
}
