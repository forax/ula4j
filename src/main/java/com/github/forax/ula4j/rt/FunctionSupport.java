package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.compiler.BytecodeGenerator;
import com.github.forax.ula4j.compiler.ScopeAnalyzer;
import com.github.forax.ula4j.ast.Stmt;
import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport;
import com.github.forax.ula4j.rt.intrinsic.TupleIntrinsics;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.Arrays;
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

  static CallSite getGlobal(MethodHandles.Lookup lookup, String name) {
    var cell = ((ModuleClassLoader) lookup.lookupClass().getClassLoader()).context().cell(name);
    return new ConstantCallSite(CELL_GET.bindTo(cell));
  }

  static CallSite setGlobal(MethodHandles.Lookup lookup, String name) {
    var cell = ((ModuleClassLoader) lookup.lookupClass().getClassLoader()).context().cell(name);
    return new ConstantCallSite(CELL_SET.bindTo(cell));
  }

  // ---- def

  static final class DefCallSite {
    private static final MethodHandle PY_FUNCTION;
    static {
      var lookup = MethodHandles.lookup();
      try {
        PY_FUNCTION = lookup.findConstructor(PyFunction.class,
            MethodType.methodType(void.class, String.class, List.class, boolean.class, ModuleContext.class, Stmt.FunctionDef.class, PyFunction.CompiledEntryCache.class, Object[].class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    static CallSite def(MethodHandles.Lookup lookup, MethodType type, int astIndex) {
      var moduleClassLoader = (ModuleClassLoader) lookup.lookupClass().getClassLoader();
      var context = moduleClassLoader.context();
      var dictionary = moduleClassLoader.dictionary();
      var def = dictionary.lookup(astIndex);

      var functionName = def.name();
      var parameters = def.parameters().stream().map(Stmt.FunctionDef.Param::name).toList();
      var varargs = def.rest().isPresent();
      var compiledEntryCache = new PyFunction.CompiledEntryCache();

      var factory = MethodHandles.insertArguments(PY_FUNCTION, 0,
          functionName, parameters, varargs, context, def, compiledEntryCache);
      var target = factory
          .asCollector(Object[].class, type.parameterCount())
          .asType(type);
      return new ConstantCallSite(target);
    }
  }

  /// A call site `(Object callee, Object arg0, ..., Object argN-1) -> Object`.
  /// Each site owns a bounded chain of guards on the entry of the callee.
  static final class FunctionCallSite extends MutableCallSite {
    private static final MethodHandles.Lookup LOOKUP;
    private static final MethodHandle FALLBACK, SAME_ENTRY;
    static {
      var lookup = MethodHandles.lookup();
      LOOKUP = lookup;
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
      private Megamorphic() {
        throw new AssertionError();
      }

      static final MethodHandle MEGAMORPHIC;
      private static final MethodHandle ARRAY_ELEMENT, REST_TUPLE;
      static {
        var lookup = MethodHandles.lookup();
        ARRAY_ELEMENT = MethodHandles.arrayElementGetter(Object[].class);
        try {
          MEGAMORPHIC = lookup.findStatic(Megamorphic.class, "megamorphic",
              MethodType.methodType(Object.class, Object.class, Object[].class));
          REST_TUPLE = lookup.findStatic(Megamorphic.class, "restTuple",
              MethodType.methodType(PyTuple.class, Object[].class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
          throw new AssertionError(e);
        }
      }

      static Object megamorphic(Object callee, Object[] args) throws Throwable {
        var function = asFunction(callee);
        checkArity(function, args.length);
        var thunkEntry = getThunkEntry(function);
        return thunkEntry.invokeExact(function.capturedArgs, function, args);
      }

      private static MethodHandle getThunkEntry(PyFunction function) {
        var cache = function.compiledEntryCache;
        var thunkEntry = cache.thunkEntry;
        if (thunkEntry != null) {
          return thunkEntry;
        }
        return computeThunkEntry(function, cache);
      }

      private static MethodHandle computeThunkEntry(PyFunction function, PyFunction.CompiledEntryCache cache) {
        var compiledEntry = getCompiledEntry(function);
        var thunkEntry = createThunk(compiledEntry, function);
        cache.thunkEntry = thunkEntry;
        return thunkEntry;
      }

      private static MethodHandle createThunk(MethodHandle compiledEntry, PyFunction function) {
        // 1. Spread captured arguments.
        var captureCount = function.capturedArgs.length;
        var handle = captureCount == 0
            ? MethodHandles.dropArguments(compiledEntry, 0, Object[].class)
            : compiledEntry.asSpreader(0, Object[].class, captureCount);

        // 2. Ordinary function: spread positional arguments.
        var arity = function.arity();
        if (!function.varargs()) {
          return arity == 0
              ? MethodHandles.dropArguments(handle, 2, Object[].class)
              : handle.asSpreader(2, Object[].class, arity);
        }

        // 3. Varargs function: extract fixed arguments and pack the rest.
        var filters = new MethodHandle[arity + 1];
        for (var i = 0; i < arity; i++) {
          filters[i] = MethodHandles.insertArguments(ARRAY_ELEMENT, 1, i);
        }
        filters[arity] = MethodHandles.insertArguments(REST_TUPLE, 1, arity);
        var filtered = MethodHandles.filterArguments(handle, 2, filters);

        // 4. Spread all args using permuteArguments
        var type = MethodType.methodType(
            Object.class, Object[].class, PyFunction.class, Object[].class);

        var reorder = new int[filtered.type().parameterCount()];
        reorder[0] = 0;  // capturedArgs
        reorder[1] = 1;  // self
        Arrays.fill(reorder, 2, reorder.length, 2);  // args

        return MethodHandles.permuteArguments(filtered, type, reorder);
      }

      private static PyTuple restTuple(Object[] args, int start) {
        return TupleIntrinsics.fromArray(Arrays.copyOfRange(args, start, args.length));
      }
    }

    private static final class RestAdapter {
      private RestAdapter() {
        throw new AssertionError();
      }

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

    static CallSite call(MethodType type) {
      return new FunctionSupport.FunctionCallSite(type);
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

    MethodHandle fallback(Object callee) {
      var function = asFunction(callee);

      // 1. If wrong arity
      var argc = type().parameterCount() - 1;
      checkArity(function, argc);

      // 2. If megamorphic
      if (depth == MAX_DEPTH) {
        var target = Megamorphic.MEGAMORPHIC.asCollector(Object[].class, argc);
        root.setTarget(target);
        return target;
      }

      // 3. Let's compile if it has never been done
      var  compiledEntry = getCompiledEntry(function);

      // 4. Let's bind captured arguments + self if it has never been done
      var entry = getEntry(function, compiledEntry);

      // 5. Adapt to the callsite signature
      var adapted = adapt(function, entry, argc).asType(type());

      // 6. Install the inlining cache
      var test = SAME_ENTRY.bindTo(entry);
      var fallback = new FunctionCallSite(type(), root, depth + 1).dynamicInvoker();
      setTarget(MethodHandles.guardWithTest(test, adapted, fallback));

      return adapted;
    }

    static MethodHandle getEntry(PyFunction function, MethodHandle compiledEntry) {
      // We do not want to have more than one entry for the same function
      // otherwise it may use several slots of the PIC (synchronized + Double Check Locking)
      var entry = function.entry;  // volatile read
      if (entry != null) {
        return entry;
      }
      synchronized(function.functionDef) {
        entry = function.entry;  // volatile read
        if (entry != null) {
          return entry;
        }
        var capturedArgumentsAndSelf = capturedArgumentsAndSelf(function);
        var bound = MethodHandles.insertArguments(compiledEntry, 0, capturedArgumentsAndSelf);
        entry = MethodHandles.dropArguments(bound, 0, Object.class);  // drop the callee
        function.entry = entry;  // volatile write
        return entry;
      }
    }

    static MethodHandle getCompiledEntry(PyFunction function) {
      // We do not want to compile the function several times (synchronized + Double Check Locking)
      var compiledEntry = function.compiledEntry;  // volatile read
      if (compiledEntry != null) {
        return compiledEntry;
      }
      synchronized (function.compiledEntryCache) {
        compiledEntry = function.compiledEntry;  // volatile read
        if (compiledEntry == null) {
          // try to find if there is already a cached compiled entry
          compiledEntry = function.compiledEntryCache.compiledEntry;
          if (compiledEntry == null) {
            compiledEntry = compileBody(function.context, function.functionDef);
            function.compiledEntryCache.compiledEntry = compiledEntry;
          }
          function.compiledEntry = compiledEntry;  // volatile write
        }
        return compiledEntry;
      }
    }

    private static Object[] capturedArgumentsAndSelf(PyFunction function) {
      var capturedArgs = function.capturedArgs;
      var array = new Object[capturedArgs.length + 1];
      System.arraycopy(capturedArgs, 0, array, 0, capturedArgs.length);
      array[capturedArgs.length] = function;
      return array;
    }

    private static boolean sameEntry(MethodHandle expected, Object callee) {
      return callee instanceof PyFunction f && f.entry == expected;
    }

    private static MethodHandle compileBody(ModuleContext ctx, Stmt.FunctionDef def) {
      var scope = ctx.scopeFor(def);
      ScopeAnalyzer.checkDefiniteAssignment(scope);

      var dictionary = new Dictionary();
      var bytes = BytecodeGenerator.generateFunction(ctx, dictionary, def, scope);

      //DEBUG
      //CheckClassAdapter.verify(new ClassReader(bytes), true, new PrintWriter(System.err));

      var className = BytecodeGenerator.functionClassName(def).replace('/', '.');
      var loader = new ModuleClassLoader(ctx, dictionary);
      var clazz = loader.define(className, bytes);
      var captureCount = BytecodeGenerator.captureNames(def, scope.analysis()).size();
      var type = BytecodeGenerator.bodyType(captureCount, def.parameters().size(), def.rest().isPresent());
      try {
        return LOOKUP.findStatic(clazz, def.name(), type).asFixedArity();
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    // ------------------------------------------------------------------ helpers

    private static PyFunction asFunction(Object callee) {
      if (callee instanceof PyFunction function) {
        return function;
      }
      throw new PyTypeError("'" + IntrinsicSupport.typeName(callee) + "' object is not callable");
    }

    private static void checkArity(PyFunction function, int argc) {
      var arity = function.arity();
      if (function.varargs()) {
        if (argc < arity) {
          throw arityError(function, argc);
        }
      } else if (argc != arity) {
        throw arityError(function, argc);
      }
    }

    /// Adapts the entry to `(PyFunction, Object x argc) -> Object`.
    private static MethodHandle adapt(PyFunction function, MethodHandle entry, int argc) {
      if (!function.varargs()) {
        return entry;  // exact arity: no adaptation at all
      }
      var arity = function.arity();
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