package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.ast.Stmt;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Objects;

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

    private final String name;  //FIXME use real Python qualified name
    private final List<String> parameters;
    private final boolean varargs;
    private final int captureCount;

    private final ModuleContext context;          // null if native
    private final Stmt.FunctionDef functionDef;   // null if native

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

    String name() { return name; }
    List<String> parameters() { return parameters; }
    int captureCount() { return captureCount; }

    ModuleContext context() { return context; }
    Stmt.FunctionDef functionDef() { return functionDef; }

    boolean isBuiltin() { return functionDef == null; }
    boolean hasRestParameter() { return varargs && functionDef != null; }
    boolean isVariadic() { return varargs; }
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

  public boolean isBuiltin() { return code.isBuiltin(); }
  Object[] capturedArgs() { return capturedArgs; }

  @Override
  public String toString() {
    return "<function " + code.name + ">";
  }

  public Object call(Object... args) {
    try {
      return FunctionSupport.FunctionCallSite.Megamorphic.megamorphic(this, args);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new UndeclaredThrowableException(e);
    }
  }

  public MethodHandle createDirectMethodHandle(MethodType type) {
    Objects.requireNonNull(type);

    // 1. If wrong arity
    var argCount = type.parameterCount();
    FunctionSupport.FunctionCallSite.checkArity(this, argCount);

    // 2. Get compiled entry
    var compiledEntry = FunctionSupport.FunctionCallSite.getCompiledEntry(code);

    // 3. Bind captured arguments and this
    var boundValues = new Object[capturedArgs.length + 1];
    System.arraycopy(capturedArgs, 0, boundValues, 0, capturedArgs.length);
    boundValues[capturedArgs.length] = this;
    var bound = MethodHandles.insertArguments(compiledEntry, 0, boundValues)
        .withVarargs(compiledEntry.isVarargsCollector());  // if builtin

    // 4. Adapt to callsite: entry: (args...) -> Object
    var arity = arity();
    return FunctionSupport.FunctionCallSite.adapt(this, bound, arity, argCount - arity)
        .asType(type);
  }
}