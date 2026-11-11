package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.ast.Stmt;
import com.github.forax.ula4j.rt.error.PyBaseException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Objects;

import static com.github.forax.ula4j.rt.FunctionSupport.FunctionCallSite.joinNames;
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

  /// Invokes this Python function with the given positional arguments.
  ///
  /// The arguments are passed positionally, in declaration order. For a
  /// function that declares a variadic parameter (`*args`), any arguments
  /// beyond the fixed parameters are packed into an immutable [PyTuple]
  /// bound to the variadic name.
  ///
  /// Argument-count mismatches are reported at call time: passing too few
  /// arguments for the fixed parameters, or any extra argument to a non-variadic
  /// function, raises a Python-level error. If you want the arity checked before
  /// any call is made, use [#createDirectMethodHandle(MethodType)] instead.
  ///
  /// @param args the positional arguments to bind to the function's parameters;
  ///             may be empty for a zero-argument function, but must not be
  ///             `null`
  ///
  /// @return the value of return statement executed by the function body;
  ///         `None` if the function returns without an explicit value
  /// @throws NullPointerException if `args` is `null`
  /// @throws PyBaseException if the function body raises a Python error
  /// @throws UndeclaredThrowableException if the function body
  ///         raises a Java checked exception
  public Object call(Object... args) {
    Objects.requireNonNull(args);
    try {
      return FunctionSupport.FunctionCallSite.Megamorphic.megamorphic(this, args);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new UndeclaredThrowableException(e);
    }
  }

  /// Creates a [MethodHandle] that invokes this Python function directly,
  /// bypassing any dispatch mechanism.
  ///
  /// The returned handle conforms exactly to `type`. Parameters are bound
  /// positionally to the function's fixed parameters; if the function declares a
  /// variadic parameter, arguments beyond the fixed ones are packed into an
  /// immutable [PyTuple] in the same way as [#call(Object...)].
  ///
  /// Captured closure variables are bound into the handle, so a handle obtained
  /// from a particular closure instance invokes only that instance.
  ///
  /// The arity of `type` is validated eagerly: for a non-variadic
  /// function it must equal the number of declared parameters, and for a
  /// variadic function it must be at least the number of fixed parameters.
  /// A handle returned from this method is therefore guaranteed to accept
  /// `type`.
  ///
  /// Argument and return values are adapted to the types expected by the
  /// function using [MethodHandle#asType(MethodType)].
  ///
  /// @param type the method type the returned handle must conform to
  /// @return a method handle that invokes this function
  /// @throws NullPointerException if `type` is `null`
  /// @throws WrongMethodTypeException if the arity of `type` is
  ///         incompatible with this function's declared parameters
  ///         or [MethodHandle#asType(MethodType)] fails
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

  /// This method duplicates [FunctionSupport.FunctionCallSite#checkArity] but with WrongMethodTypeException
  static void checkArity(PyFunction function, int argCount) {
    var arity = function.arity();
    if (function.code.isVariadic()) {
      if (argCount < arity) {
        throw arityError(function, argCount);
      }
    } else if (argCount != arity) {
      throw arityError(function, argCount);
    }
  }

  private static WrongMethodTypeException arityError(PyFunction function, int given) {
    var arity = function.arity();
    if (given < arity) {
      var missing = function.parameters().subList(given, arity);
      return new WrongMethodTypeException(function.name() + "() missing " + missing.size()
          + " required positional argument" + (missing.size() == 1 ? "" : "s") + ": " + joinNames(missing));
    }
    return new WrongMethodTypeException(function.name() + "() takes " + arity + " positional argument"
        + (arity == 1 ? "" : "s") + " but " + given + (given == 1 ? " was" : " were") + " given");
  }
}