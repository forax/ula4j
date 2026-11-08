package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.ast.Stmt;

import java.lang.invoke.MethodHandle;
import java.util.List;

import static java.util.Objects.requireNonNull;

/// A Python function. `entry` has the type `(PyFunction, Object x arity [, PyTuple rest]) -> Object`,
/// with the captured values already bound.
public final class PyFunction {
  static final class CompiledEntryCache {
    // compiledEntry should be accessed under a synchronized on the cache (this)
    MethodHandle compiledEntry;

    // compiled entry but adapted to the signature (Object[] captureArgs, Object self, Object[] args)Object
    // used by megamorphic callsites, access is racy but the process to create a thunkEntry is idempotent
    MethodHandle thunkEntry;
  }

  private final String name;  //FIXME use real Python qualified name
  private final List<String> parameters;
  private final boolean varargs;

  final ModuleContext context;
  final Object[] capturedArgs;

  final CompiledEntryCache compiledEntryCache;
  final Stmt.FunctionDef functionDef;   // not compiled
  volatile MethodHandle compiledEntry;  // compiled (should be accessed under a lock on compiledEntryCache)
  volatile MethodHandle entry;          // compiled + captured arguments (should be accessed under a lock on functionDef)

  PyFunction(String name, List<String> parameters, boolean varargs,
             ModuleContext context, Stmt.FunctionDef functionDef, CompiledEntryCache compiledEntryCache,
             Object[] capturedArgs) {
    this.name = name;
    this.parameters = parameters;
    this.varargs = varargs;

    this.context = context;
    this.capturedArgs = capturedArgs;
    this.functionDef = functionDef;
    this.compiledEntryCache = compiledEntryCache;
    super();
  }

  public String name() { return name; }
  public List<String> parameters() { return parameters; }
  public int arity() { return parameters.size(); }
  public boolean varargs() { return varargs; }

  @Override
  public String toString() {
    return "<function " + name + ">";
  }
}