package com.github.forax.ula4j.rt;

import java.lang.invoke.MethodHandle;
import java.util.List;

import static java.util.Objects.requireNonNull;

/// A Python function. `entry` has the type `(PyFunction, Object x arity [, PyTuple rest]) -> Object`,
/// with the captured values already bound.
public final class PyFunction {
  private final String qualifiedName;
  private final List<String> parameters;
  private final boolean varargs;
  private MethodHandle entry;
  private boolean compiled;

  public PyFunction(String qualifiedName, List<String> parameters, boolean varargs,
                    MethodHandle entry, boolean compiled) {
    this.qualifiedName = requireNonNull(qualifiedName);
    this.parameters = List.copyOf(parameters);
    this.varargs = varargs;
    this.entry = requireNonNull(entry);
    this.compiled = compiled;
    super();
  }

  public String name() { return qualifiedName; }
  public List<String> parameters() { return parameters; }
  public int arity() { return parameters.size(); }
  public boolean varargs() { return varargs; }
  public MethodHandle entry() { return entry; }
  /// True once `entry` is the compiled body and no longer the compiling trampoline.
  public boolean isCompiled() { return compiled; }

  /// The entry is written before the flag, so a reader that sees the flag sees the entry.
  public void install(MethodHandle compiledEntry) {
    this.entry = requireNonNull(compiledEntry);
    this.compiled = true;
  }

  @Override
  public String toString() {
    return "<function " + qualifiedName + ">";
  }
}