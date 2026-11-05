package com.github.forax.ula4j.rt;

public final class PyNone {
  public static final PyNone INSTANCE = new PyNone();

  private PyNone() {
  }

  @Override
  public String toString() {
    return "None";
  }
}