package com.github.forax.ula4j.rt;

public final class ModuleClassLoader extends ClassLoader {
  private final ModuleContext context;
  private final Dictionary dictionary;

  public ModuleClassLoader(ModuleContext context, Dictionary dictionary) {
    this.context = context;
    this.dictionary = dictionary;
    super(ModuleClassLoader.class.getClassLoader());
  }

  public ModuleContext context() { return context; }

  public Dictionary dictionary() { return dictionary; }

  /// `name` is a binary name (dots).
  public Class<?> define(String name, byte[] bytes) {
    return defineClass(name, bytes, 0, bytes.length);
  }
}