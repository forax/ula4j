package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.compiler.BytecodeGenerator;
import com.github.forax.ula4j.compiler.ScopeAnalyzer;
import com.github.forax.ula4j.ast.Stmt;
import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport;
import com.github.forax.ula4j.rt.intrinsic.TupleIntrinsics;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.ArrayList;
import java.util.List;

/// Implementation of the `def` / global bootstraps and of the lazy compilation of function bodies.
final class FunctionSupport {
  private FunctionSupport() { throw new AssertionError(); }

  private static final MethodHandle CELL_GET, CELL_SET;
  static {
    var lookup = MethodHandles.lookup();
    try {
      CELL_GET = lookup.findVirtual(ModuleContext.GlobalCell.class, "get",
          MethodType.methodType(Object.class));
      CELL_SET = lookup.findVirtual(ModuleContext.GlobalCell.class, "set",
          MethodType.methodType(void.class, Object.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  // ---- globals

  static CallSite getGlobal(MethodHandles.Lookup lookup, MethodType type, String name) {
    var cell = ((ModuleClassLoader) lookup.lookupClass().getClassLoader()).context().cell(name);
    return new ConstantCallSite(CELL_GET.bindTo(cell).asType(type));
  }

  static CallSite setGlobal(MethodHandles.Lookup lookup, MethodType type, String name) {
    var cell = ((ModuleClassLoader) lookup.lookupClass().getClassLoader()).context().cell(name);
    return new ConstantCallSite(CELL_SET.bindTo(cell).asType(type));
  }

  // ---- def

  static final class DefCallSite {

    private static final MethodHandles.Lookup LOOKUP;
    private static final MethodHandle MAKE_FUNCTION, COMPILE_AND_INVOKE;

    static {
      var lookup = MethodHandles.lookup();
      LOOKUP = lookup;
      try {
        MAKE_FUNCTION = lookup.findStatic(DefCallSite.class, "makeFunction",
            MethodType.methodType(PyFunction.class, ModuleContext.class, Stmt.FunctionDef.class, Object[].class));
        COMPILE_AND_INVOKE = lookup.findStatic(DefCallSite.class, "compileAndInvoke",
            MethodType.methodType(Object.class,
                ModuleContext.class, Stmt.FunctionDef.class, Object[].class, PyFunction.class, Object[].class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    /// `type` is `(Object x nCaptures) -> Object`.
    static CallSite def(MethodHandles.Lookup lookup, MethodType type, int astIndex) {
      var moduleClassLoader = (ModuleClassLoader) lookup.lookupClass().getClassLoader();
      var ctx = moduleClassLoader.context();
      var dictionary = moduleClassLoader.dictionary();
      var def = dictionary.retrieve(astIndex);
      var factory = MethodHandles.insertArguments(MAKE_FUNCTION, 0, ctx, def)  // (Object[]) -> PyFunction
          .asCollector(Object[].class, type.parameterCount())
          .asType(type);
      return new ConstantCallSite(factory);
    }

    static MethodType entryMethodType(int arity, boolean varargs) {
      var types = new ArrayList<Class<?>>();
      types.add(PyFunction.class);
      for (var i = 0; i < arity; i++) {
        types.add(Object.class);
      }
      if (varargs) {
        types.add(PyTuple.class);
      }
      return MethodType.methodType(Object.class, types);
    }

    static PyFunction makeFunction(ModuleContext ctx, Stmt.FunctionDef def, Object[] captures) {
      var parameters = def.parameters().stream().map(Stmt.FunctionDef.Param::name).toList();
      var varargs = def.rest().isPresent();
      var lazy = lazyEntry(ctx, def, captures, parameters.size(), varargs);
      return new PyFunction(def.name(), parameters, varargs, lazy, false);
    }

    private static MethodHandle bind(MethodHandle body, Object[] captures) {
      return MethodHandles.insertArguments(body, 0, captures);
    }

    /// A handle of the same type as the compiled entry that compiles the body on its first call.
    private static MethodHandle lazyEntry(ModuleContext ctx, Stmt.FunctionDef def, Object[] captures,
                                          int arity, boolean varargs) {
      var impl = MethodHandles.insertArguments(COMPILE_AND_INVOKE, 0, ctx, def, captures);
      // (PyFunction, Object[]) -> Object
      var parameterCount = arity + (varargs ? 1 : 0);
      return impl.asCollector(Object[].class, parameterCount).asType(entryMethodType(arity, varargs));
    }

    static Object compileAndInvoke(ModuleContext ctx, Stmt.FunctionDef def, Object[] captures,
                                   PyFunction self, Object[] args) throws Throwable {
      var body = compileBody(ctx, def);
      var entry = bind(body, captures);
      self.install(entry);
      var all = new Object[args.length + 1];
      all[0] = self;
      System.arraycopy(args, 0, all, 1, args.length);
      return entry.invokeWithArguments(all);
    }

    private static MethodHandle compileBody(ModuleContext ctx, Stmt.FunctionDef def) {
      var scope = ctx.scopeFor(def);
      ScopeAnalyzer.checkDefiniteAssignment(scope);

      var dictionary = new Dictionary();
      var bytes = BytecodeGenerator.generateFunction(ctx, dictionary, def, scope);
      var className = BytecodeGenerator.functionClassName(def).replace('/', '.');
      var loader = new ModuleClassLoader(ctx, dictionary);
      var clazz = loader.define(className, bytes);
      var captureCount = BytecodeGenerator.captureNames(def, scope.analysis()).size();
      var type = BytecodeGenerator.bodyType(captureCount, def.parameters().size(), def.rest().isPresent());
      try {
        return LOOKUP.findStatic(clazz, "body", type).asFixedArity();
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }
  }

  /// A call site `(Object callee, Object arg0, ..., Object argN-1) -> Object`.
  /// Each site owns a bounded chain of guards on the entry of the callee.
  static final class FunctionCallSite extends MutableCallSite {
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
}