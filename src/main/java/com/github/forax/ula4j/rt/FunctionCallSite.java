package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport;
import com.github.forax.ula4j.rt.intrinsic.TupleIntrinsics;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.List;

/// A call site `(Object callee, Object arg0, ..., Object argN-1) -> Object`.
/// Each site owns a bounded chain of guards on the entry of the callee.
final class FunctionCallSite extends MutableCallSite {
  private static final MethodHandle FALLBACK, SAME_ENTRY;
  static {
    var lookup = MethodHandles.lookup();
    try {
      FALLBACK = lookup.findVirtual(FunctionCallSite.class, "fallback",
          MethodType.methodType(MethodHandle.class, Object.class));
      SAME_ENTRY = lookup.findStatic(FunctionCallSite.class, "sameEntry",
          MethodType.methodType(boolean.class, MethodHandle.class, Object.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private static final class Megamorphic {
    static final MethodHandle ENTRY;
    static {
      var lookup = MethodHandles.lookup();
      try {
        ENTRY = lookup.findVirtual(PyFunction.class, "entry", MethodType.methodType(MethodHandle.class))
          .asType(MethodType.methodType(MethodHandle.class, Object.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }
  }

  private static final class RestAdapter {
    private static final MethodHandle FROM_ARRAY;
    static {
      var lookup = MethodHandles.lookup();
      try {
        FROM_ARRAY = lookup.findStatic(TupleIntrinsics.class, "fromArray",
            MethodType.methodType(PyTuple.class, Object[].class)).asFixedArity();
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }
  }

  private static final int MAX_DEPTH = 4;

  private final FunctionCallSite root;
  private final int depth;

  FunctionCallSite(MethodType type) {
    depth = 0;
    super(type);
    root = this;
    setTarget(MethodHandles.foldArguments(MethodHandles.exactInvoker(type()), FALLBACK.bindTo(this)));
  }

  private FunctionCallSite(MethodType type, FunctionCallSite root, int depth) {
    this.root = root;
    this.depth = depth;
    super(type);
    setTarget(MethodHandles.foldArguments(MethodHandles.exactInvoker(type()), FALLBACK.bindTo(this)));
  }

  MethodHandle fallback(Object callee) throws Throwable {
    var function = asFunction(callee);
    var entry = function.entry();
    var argc = type().parameterCount() - 1;
    var adapted = adapt(function, entry, argc).asType(type());
    var compiled = function.isCompiled();
    if (compiled) {
      link(entry, adapted);
    }
    // the first call goes through the compiling trampoline, the next call links
    return adapted;
  }

  private static boolean sameEntry(MethodHandle expected, Object callee) {
    return callee instanceof PyFunction f && f.entry() == expected;
  }

  private void link(MethodHandle entry, MethodHandle adapted) {
    if (depth >= MAX_DEPTH) {
      root.setTarget(MethodHandles.foldArguments(MethodHandles.exactInvoker(type()), Megamorphic.ENTRY));
      return;
    }
    var test = SAME_ENTRY.bindTo(entry);
    var fallback = new FunctionCallSite(type(), root, depth + 1).dynamicInvoker();
    setTarget(MethodHandles.guardWithTest(test, adapted, fallback));
  }

  // ------------------------------------------------------------------ helpers

  private static PyFunction asFunction(Object callee) {
    if (callee instanceof PyFunction function) {
      return function;
    }
    throw new PyTypeError("'" + IntrinsicSupport.typeName(callee) + "' object is not callable");
  }

  /// Adapts the entry to `(PyFunction, Object x argc) -> Object`, or raises the TypeError CPython raises.
  private static MethodHandle adapt(PyFunction function, MethodHandle entry, int argc) {
    var arity = function.arity();
    if (!function.varargs()) {
      if (argc != arity) {
        throw arityError(function, argc);
      }
      return entry;  // exact arity: no adaptation at all
    }
    if (argc < arity) {
      throw arityError(function, argc);
    }
    var packer = RestAdapter.FROM_ARRAY.asCollector(Object[].class, argc - arity);  // (Object x extra) -> PyTuple
    return MethodHandles.collectArguments(entry, arity + 1, packer);
  }

  private static PyTypeError arityError(PyFunction function, int given) {
    var arity = function.arity();
    if (given < arity) {
      var missing = function.parameters().subList(given, arity);
      return new PyTypeError(function.name() + "() missing " + missing.size()
          + " required positional argument" + (missing.size() == 1 ? "" : "s") + ": " + joinNames(missing));
    }
    return new PyTypeError(function.name() + "() takes " + arity + " positional argument"
        + (arity == 1 ? "" : "s") + " but " + given + (given == 1 ? " was" : " were") + " given");
  }

  private static String joinNames(List<String> names) {
    return switch (names.size()) {
      case 1 -> "'" + names.get(0) + "'";
      case 2 -> "'" + names.get(0) + "' and '" + names.get(1) + "'";
      default -> {
        var sb = new StringBuilder();
        for (var i = 0; i < names.size() - 1; i++) {
          sb.append('\'').append(names.get(i)).append("', ");
        }
        yield sb.append("and '").append(names.getLast()).append('\'').toString();
      }
    };
  }
}