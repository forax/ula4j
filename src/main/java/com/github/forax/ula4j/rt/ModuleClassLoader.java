package com.github.forax.ula4j.rt;

public final class ModuleClassLoader extends ClassLoader {
  private final ModuleContext context;

  ModuleClassLoader(ModuleContext context) {
    this.context = context;
    super(ModuleClassLoader.class.getClassLoader());
  }

  public ModuleContext context() { return context; }

  /// `name` is a binary name (dots).
  public Class<?> define(String name, byte[] bytes) {
    return defineClass(name, bytes, 0, bytes.length);
  }
}