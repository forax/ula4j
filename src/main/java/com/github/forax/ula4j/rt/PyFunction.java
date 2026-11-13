package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.ast.Stmt;
import com.github.forax.ula4j.rt.error.PyBaseException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Objects;

/// A Python function (or a native/builtin one, see [Code#isBuiltin()]).
public final class PyFunction {
  static final class Code {
    private final String name;  //FIXME use real Python qualified name
    private final List<String> parameters;
    private final boolean varargs;
    private final int captureCount;

    private final ModuleContext context;          // null if native
    private final Stmt.FunctionDef functionDef;   // null if native

    // compiled entry (accessed under a lock on this, unless builtin, where the resolution is idempotent)
    volatile MethodHandle compiledEntry;

    // compiled entry adapted to (PyFunction self, Object arg0, Object arg1, ...), used by closures (idempotent)
    // Method handles are immutable data so benign race is fine
    MethodHandle entry;

    // compiled entry adapted to (Object[] captureArgs, PyFunction self, Object[] args), used by megamorphic
    // call sites (idempotent). Method handles are immutable data so benign race is fine
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

    /// A builtin function has no AST. Its rest parameter, if any, is an Object[]
    boolean isBuiltin() { return functionDef == null; }

    /// True if the function has a rest parameter, builtin or not.
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

  public boolean isVariadic() { return code.isVariadic(); }
  public boolean isBuiltin() { return code.isBuiltin(); }
  Object[] capturedArgs() { return capturedArgs; }

  @Override
  public String toString() {
    return "<function " + code.name + ">";
  }

  /// Invokes this function with the given positional arguments.
  ///
  /// Arguments beyond the fixed parameters of a variadic function are packed into an immutable [PyTuple]
  /// (an array for a native function). An argument-count mismatch raises a Python `TypeError`.
  ///
  /// @param args the positional arguments, must not be `null`
  /// @return the value returned by the function, `None` if it returns without a value
  /// @throws NullPointerException if `args` is `null` or array element is `null``
  /// @throws PyBaseException if the function body raises a Python error
  /// @throws UndeclaredThrowableException if the function body raises a Java checked exception
  public Object call(Object... args) {
    Objects.requireNonNull(args);
    for(var arg : args) {
      Objects.requireNonNull(arg);
    }
    try {
      return FunctionSupport.FunctionCallSite.Megamorphic.megamorphic(this, args);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new UndeclaredThrowableException(e);
    }
  }

  /// Creates a [MethodHandle] that invokes this function directly, bypassing any dispatch mechanism.
  ///
  /// Captured variables are bound into the handle. The arity of `type` is validated eagerly with
  /// a Python `TypeError` (the same errors as a call). Arguments and return value are adapted with
  /// [MethodHandle#asType(MethodType)].
  ///
  /// In Python, Java null is not a valid value (use [PyNone#INSTANCE] to represent Python None).
  /// Passing null violates the runtime's value invariant and may cause unexpected behavior,
  /// including NullPointerException in unexpected location.
  ///
  /// @param type the method type the returned handle must conform to
  /// @return a method handle that invokes this function
  /// @throws NullPointerException if `type` is `null`
  public MethodHandle createDirectMethodHandle(MethodType type) {
    Objects.requireNonNull(type);

    // 1. Wrong arity?
    var argCount = type.parameterCount();
    FunctionSupport.FunctionCallSite.checkArity(this, argCount);

    // 2. Get the compiled entry
    var compiledEntry = FunctionSupport.FunctionCallSite.getCompiledEntry(code);

    // 3. Bind the captured arguments and this
    var boundValues = new Object[capturedArgs.length + 1];
    System.arraycopy(capturedArgs, 0, boundValues, 0, capturedArgs.length);
    boundValues[capturedArgs.length] = this;
    var bound = MethodHandles.insertArguments(compiledEntry, 0, boundValues);

    // 4. Adapt to the requested type
    var arity = arity();
    return FunctionSupport.FunctionCallSite.adapt(this, bound, arity, argCount - arity)
        .asType(type);
  }
}