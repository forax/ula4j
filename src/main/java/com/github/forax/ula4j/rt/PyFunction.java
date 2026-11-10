package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.ast.Stmt;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;

import static java.util.Objects.requireNonNull;

/// A Python function. `entry` has the type `(PyFunction, Object x arity [, PyTuple rest]) -> Object`,
/// with the captured values already bound.
public final class PyFunction {
  static final class Code {
    /*static final VarHandle ENTRY;
    static {
      var lookup = MethodHandles.lookup();
      try {
        ENTRY = lookup.findVarHandle(PyFunction.class, "entry", MethodHandle.class)
            .withInvokeExactBehavior();
      } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }*/

    final String name;  //FIXME use real Python qualified name
    final List<String> parameters;
    final boolean varargs;
    final int captureCount;

    final ModuleContext context;          // null if native
    final Stmt.FunctionDef functionDef;   // null if native

    // compiled (accessed under a lock on this unless native)
    volatile MethodHandle compiledEntry;

    // compiled entry but adapted to signature (Object[] captureArgs, Object self, Object arg0, Object arg1, ...)
    // used by closure (benign race)
    MethodHandle entry;

    // compiled entry but adapted to signature (Object[] captureArgs, Object self, Object[] args)Object
    // used by megamorphic callsites, access is racy but the process to create a thunkEntry is idempotent
    MethodHandle thunkEntry;

    Code(String name, List<String> parameters, boolean varargs, int captureCount,
               ModuleContext context, Stmt.FunctionDef functionDef) {
      this.name = name;
      this.parameters = parameters;
      this.varargs = varargs;
      this.captureCount = captureCount;
      this.context = context;
      this.functionDef = functionDef;
      super();
    }
  }

  final Code code;
  final Object[] capturedArgs;

  PyFunction(Code code, Object[] capturedArgs) {
    this.code = code;
    this.capturedArgs = capturedArgs;
    super();
  }

  public String name() { return code.name; }
  public List<String> parameters() { return code.parameters; }
  public int arity() { return code.parameters.size(); }
  public boolean varargs() { return code.varargs; }

  Object[] capturedArgs() { return capturedArgs; }

  @Override
  public String toString() {
    return "<function " + code.name + ">";
  }
}