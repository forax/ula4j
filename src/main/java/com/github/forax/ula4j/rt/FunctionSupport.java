package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.ast.Stmt;
import com.github.forax.ula4j.compiler.BytecodeGenerator;
import com.github.forax.ula4j.compiler.ScopeAnalyzer;
import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;
import com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport;
import com.github.forax.ula4j.rt.intrinsic.TupleIntrinsics;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// Implementation of the `def` / global bootstraps and of the lazy compilation of function bodies.
///
/// Calling conventions of a compiled entry ("entry" below):
///  - Python function: `(captures..., PyFunction self, Object p0..pn-1 [, PyTuple rest]) -> Object`
///  - builtin function: `(PyFunction self, Object p0..pn-1 [, Object[] rest]) -> Object` (fixed arity handle)
/// For a builtin, `rest` is a plain array holding every argument after the declared parameters.
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

  /// A read of a global.
  static CallSite getGlobal(ModuleContext context, String name) {
    var cell = context.cell(name);
    var resolvedCell = cell.resolveCell();
    if (resolvedCell == null) {  // no value, no backup bindings
      return new ConstantCallSite(CELL_GET.bindTo(cell));
    }
    // From that point on, the value will never be null
    return new GetGlobalCallSite(cell);
  }

  private static final class GetGlobalCallSite extends MutableCallSite {
    private static final MethodType TYPE;
    private static final MethodHandle FALLBACK;
    static {
      var lookup = MethodHandles.lookup();
      TYPE = MethodType.methodType(Object.class);
      try {
        FALLBACK = lookup.findVirtual(GetGlobalCallSite.class, "fallback",
            MethodType.methodType(Object.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private static final int MAX_COUNTER = 2;

    private final ModuleContext.GlobalCell cell;
    private int counter;

    GetGlobalCallSite(ModuleContext.GlobalCell cell) {
      this.cell = cell;
      super(TYPE);
      setTarget(FALLBACK.bindTo(this));
    }

    private Object fallback() {
      var counter = this.counter;
      if (counter >= MAX_COUNTER) {
        setTarget(CELL_GET.bindTo(cell));
        return cell.get();
      }
      this.counter = counter + 1;

      var resolvedCell = cell.resolveCell();
      if (resolvedCell == null) {
        throw new AssertionError("the value has already been observed as non-null");
      }
      var value = resolvedCell.value();
      var constant = MethodHandles.constant(Object.class, value);
      var target = resolvedCell.switchPoint().guardWithTest(constant, FALLBACK.bindTo(this));
      setTarget(target);

      return value;
    }
  }

  /// A store to a global.
  static CallSite setGlobal(ModuleContext context, String name) {
    var cell = context.cell(name);
    return new ConstantCallSite(CELL_SET.bindTo(cell));
  }

  // ---- def

  static final class DefCallSite {
    private static final MethodHandle PY_FUNCTION;
    static {
      var lookup = MethodHandles.lookup();
      try {
        PY_FUNCTION = lookup.findConstructor(PyFunction.class,
            MethodType.methodType(void.class, PyFunction.Code.class, Object[].class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private static final Object[] EMPTY_CAPTURED_ARGS = new Object[0];

    static CallSite def(ModuleContext context, Dictionary dictionary, MethodType type, int astIndex) {
      var def = dictionary.lookup(astIndex);

      var functionName = def.name();
      var parameters = def.parameters().stream().map(Stmt.FunctionDef.Param::name).toList();
      var hasRestParameter = def.rest().isPresent();
      var captureCount = type.parameterCount();
      var code = new PyFunction.Code(functionName, parameters, hasRestParameter, captureCount, context, def);

      if (captureCount == 0) {
        var function = new PyFunction(code, EMPTY_CAPTURED_ARGS);
        return new ConstantCallSite(MethodHandles.constant(Object.class, function));
      }

      var factory = PY_FUNCTION.bindTo(code);
      var target = factory
          .asCollector(Object[].class, captureCount)
          .asType(type);
      return new ConstantCallSite(target);
    }
  }

  /// A call site `(Object callee, Object arg0, ..., Object argN-1) -> Object`.
  /// Each site owns a bounded chain of guards on the entry of the callee.
  ///
  /// Arguments are always validated (arity, keywords) before the callee is compiled.
  /// Polymorphic and megamorphic paths should share the same validation code.
  static final class FunctionCallSite extends MutableCallSite {
    static final class Megamorphic {
      private Megamorphic() {
        throw new AssertionError();
      }

      static final MethodHandle MEGAMORPHIC, MEGAMORPHIC_WITH_KEYWORDS;
      private static final MethodHandle ARRAY_ELEMENT, REST_TUPLE, BUILTIN_REST;
      static {
        var lookup = MethodHandles.lookup();
        ARRAY_ELEMENT = MethodHandles.arrayElementGetter(Object[].class);
        try {
          MEGAMORPHIC = lookup.findStatic(Megamorphic.class, "megamorphic",
              MethodType.methodType(Object.class, Object.class, Object[].class));
          MEGAMORPHIC_WITH_KEYWORDS = lookup.findStatic(Megamorphic.class, "megamorphicWithKeywords",
              MethodType.methodType(Object.class, Object.class, Object[].class, int.class, String[].class));
          REST_TUPLE = lookup.findStatic(Megamorphic.class, "restTuple",
              MethodType.methodType(PyTuple.class, Object[].class, int.class));
          BUILTIN_REST = lookup.findStatic(Megamorphic.class, "builtinRest",
              MethodType.methodType(Object[].class, Object[].class, int.class));
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

      static Object megamorphicWithKeywords(Object callee, Object[] args,
                                            int positionalCount, String[] keywordNames) throws Throwable {
        var function = asFunction(callee);
        var plan = reorderKeywordArguments(function, positionalCount, keywordNames);
        var ordered = new Object[Math.max(positionalCount, function.arity())];
        for (var i = 0; i < plan.length; i++) {
          ordered[plan[i]] = args[i];
        }
        var thunkEntry = getThunkEntry(function);
        return thunkEntry.invokeExact(function.capturedArgs, function, ordered);
      }

      private static MethodHandle getThunkEntry(PyFunction function) {
        var code = function.code;
        var thunkEntry = code.thunkEntry;  // racy read
        if (thunkEntry != null) {
          return thunkEntry;
        }
        thunkEntry = createThunk(getCompiledEntry(code), code);  // idempotent
        code.thunkEntry = thunkEntry;  // racy write
        return thunkEntry;
      }

      /// Adapts the compiled entry to `(Object[] captured, PyFunction self, Object[] args) -> Object`
      /// where `args` holds the declared parameters followed by the extra positional arguments.
      private static MethodHandle createThunk(MethodHandle compiledEntry, PyFunction.Code code) {
        // 1. (Object[] captured, PyFunction self, <declared parameters> [, rest])
        var captureCount = code.captureCount();
        var handle = captureCount == 0
            ? MethodHandles.dropArguments(compiledEntry, 0, Object[].class)
            : compiledEntry.asSpreader(0, Object[].class, captureCount);

        // 2. No rest parameter: spread the declared parameters (or ignore the array if there is none).
        var arity = code.parameters().size();
        if (!code.isVariadic()) {
          return arity == 0
              ? MethodHandles.dropArguments(handle, 2, Object[].class)
              : handle.asSpreader(2, Object[].class, arity);
        }

        // 3. Rest parameter (a PyTuple for Python code, an Object[] for a builtin):
        //    read the declared parameters one by one and pack the remaining arguments.
        var packer = code.isBuiltin() ? BUILTIN_REST : REST_TUPLE;
        var filters = new MethodHandle[arity + 1];
        for (var i = 0; i < arity; i++) {
          filters[i] = MethodHandles.insertArguments(ARRAY_ELEMENT, 1, i);
        }
        filters[arity] = MethodHandles.insertArguments(packer, 1, arity);
        var filtered = MethodHandles.filterArguments(handle, 2, filters);

        // 4. Every filtered parameter reads the same args array.
        var type = MethodType.methodType(Object.class, Object[].class, PyFunction.class, Object[].class);
        var reorder = new int[filtered.type().parameterCount()];
        reorder[0] = 0;  // captured
        reorder[1] = 1;  // self
        Arrays.fill(reorder, 2, reorder.length, 2);  // args
        return MethodHandles.permuteArguments(filtered, type, reorder);
      }

      private static PyTuple restTuple(Object[] args, int start) {
        return TupleIntrinsics.fromArrayRange(args, start);
      }

      /// The array is handed to the builtin as is when there is no declared parameter:
      /// builtin functions must neither retain nor modify it.
      private static Object[] builtinRest(Object[] args, int start) {
        return start == 0 ? args : Arrays.copyOfRange(args, start, args.length);
      }
    }

    private static final class RestAdapter {
      private RestAdapter() {
        throw new AssertionError();
      }

      private static final PyTuple TUPLE_EMPTY = TupleIntrinsics.fromArray();
      private static final MethodHandle TUPLE1, TUPLE2, TUPLE3, TUPLE4, TUPLE_N;

      static {
        var lookup = MethodHandles.lookup();
        try {
          // collectArguments requires the filter's return type to be exactly the target parameter type (PyTuple),
          // hence the asType that widens PyTupleK to PyTuple
          TUPLE1 = lookup.findConstructor(PyTuple.PyTuple1.class, MethodType.methodType(void.class, Object.class))
              .asType(MethodType.methodType(PyTuple.class, Object.class));
          TUPLE2 = lookup.findConstructor(PyTuple.PyTuple2.class, MethodType.methodType(void.class, Object.class, Object.class))
              .asType(MethodType.methodType(PyTuple.class, Object.class, Object.class));
          TUPLE3 = lookup.findConstructor(PyTuple.PyTuple3.class, MethodType.methodType(void.class, Object.class, Object.class, Object.class))
              .asType(MethodType.methodType(PyTuple.class, Object.class, Object.class, Object.class));
          TUPLE4 = lookup.findConstructor(PyTuple.PyTuple4.class, MethodType.methodType(void.class, Object.class, Object.class, Object.class, Object.class))
              .asType(MethodType.methodType(PyTuple.class, Object.class, Object.class, Object.class, Object.class));
          TUPLE_N = lookup.findConstructor(PyTuple.PyTupleN.class, MethodType.methodType(void.class, Object[].class))
              .asType(MethodType.methodType(PyTuple.class, Object[].class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
          throw new AssertionError(e);
        }
      }

      /// `entry` is `(X, Object x arity, PyTuple rest) -> Object`.
      /// The rest parameter is at `position`.
      static MethodHandle adapt(MethodHandle entry, int position, int restCount) {
        return switch (restCount) {
          case 0 -> MethodHandles.insertArguments(entry, position, TUPLE_EMPTY);
          case 1 -> MethodHandles.collectArguments(entry, position, TUPLE1);
          case 2 -> MethodHandles.collectArguments(entry, position, TUPLE2);
          case 3 -> MethodHandles.collectArguments(entry, position, TUPLE3);
          case 4 -> MethodHandles.collectArguments(entry, position, TUPLE4);
          default -> MethodHandles.collectArguments(entry, position,
              TUPLE_N.asCollector(Object[].class, restCount));
        };
      }
    }

    private static final MethodHandles.Lookup LOOKUP;
    private static final MethodHandle FALLBACK, SAME_FUNCTION, SAME_CODE, CAPTURED_ARGS;
    static {
      var lookup = MethodHandles.lookup();
      LOOKUP = lookup;
      try {
        FALLBACK = lookup.findVirtual(FunctionCallSite.class, "fallback",
            MethodType.methodType(MethodHandle.class, Object.class));
        SAME_FUNCTION = lookup.findStatic(FunctionCallSite.class, "sameFunction",
            MethodType.methodType(boolean.class, PyFunction.class, Object.class));
        SAME_CODE = lookup.findStatic(FunctionCallSite.class, "sameCode",
            MethodType.methodType(boolean.class, PyFunction.Code.class, Object.class));
        CAPTURED_ARGS = lookup.findVirtual(PyFunction.class, "capturedArgs",
            MethodType.methodType(Object[].class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    static CallSite call(MethodType type, int positionalCount, String[] keywordNames) {
      return new FunctionSupport.FunctionCallSite(type, positionalCount, keywordNames, null, 0);
    }

    private static final int MAX_DEPTH = 4;

    private final int positionalCount;
    private final String[] keywordNames;
    private final FunctionCallSite root;
    private final int depth;

    private FunctionCallSite(MethodType type, int positionalCount, String[] keywordNames, FunctionCallSite root, int depth) {
      this.positionalCount = positionalCount;
      this.keywordNames = keywordNames;
      this.depth = depth;
      super(type);
      this.root = root == null ? this : root;
      setTarget(MethodHandles.foldArguments(MethodHandles.exactInvoker(type()), FALLBACK.bindTo(this)));
    }

    MethodHandle fallback(Object callee) {
      var function = asFunction(callee);
      var argCount = type().parameterCount() - 1;

      // 1. Megamorphic: the arguments are validated at each call
      if (depth == MAX_DEPTH) {
        var entryPoint = keywordNames.length == 0
            ? Megamorphic.MEGAMORPHIC
            : MethodHandles.insertArguments(Megamorphic.MEGAMORPHIC_WITH_KEYWORDS, 2, positionalCount, keywordNames);
        var target = entryPoint.asCollector(Object[].class, argCount);
        root.setTarget(target);
        return target;
      }

      // 2. Validate the arguments before compiling the callee
      int[] reorderPlan;
      if (keywordNames.length == 0) {
        checkArity(function, argCount);
        reorderPlan = null;
      } else {
        reorderPlan = reorderKeywordArguments(function, positionalCount, keywordNames);
      }

      // 3. Entry of the callee: (Object callee | PyFunction self, <declared parameters> [, rest])
      var code = function.code;
      MethodHandle target, test;
      if (code.captureCount() == 0) {
        test = SAME_FUNCTION.bindTo(function);
        target = getNoCaptureMH(function);
      } else {
        test = SAME_CODE.bindTo(code);
        target = getEntry(code);
      }

      // 4. Adapt to the call site
      var arity = function.arity();
      var adapted = reorderPlan == null
          ? adapt(function, target, 1 + arity, argCount - arity).asType(type())
          : adaptKeywords(function, target, type(), reorderPlan);

      // 5. Install the inlining cache
      var fallback = new FunctionCallSite(type(), positionalCount, keywordNames, root, depth + 1).dynamicInvoker();
      setTarget(MethodHandles.guardWithTest(test, adapted, fallback));

      return adapted;
    }

    static MethodHandle getNoCaptureMH(PyFunction function) {
      var compiledEntry = getCompiledEntry(function.code);
      var bound = compiledEntry.bindTo(function);
      return MethodHandles.dropArguments(bound, 0, Object.class);  // drop the callee
    }

    static MethodHandle getEntry(PyFunction.Code code) {
      var entry = code.entry;  // racy read
      if (entry != null) {
        return entry;
      }
      var compiledEntry = getCompiledEntry(code);

      var captureCount = code.captureCount();  // != 0
      var spread = compiledEntry.asSpreader(0, Object[].class, captureCount);     // (Object[], PyFunction, ...)
      entry = MethodHandles.foldArguments(spread, CAPTURED_ARGS);
      code.entry = entry;  // racy write
      return entry;
    }

    static MethodHandle getCompiledEntry(PyFunction.Code code) {
      // We do not want to compile the function several times (synchronized + Double Check Locking)
      var compiledEntry = code.compiledEntry;  // volatile read
      if (compiledEntry != null) {
        return compiledEntry;
      }
      if (code.isBuiltin()) {
        return code.compiledEntry = Builtins.resolve(code);  // idempotent, volatile write
      }
      synchronized (code) {
        compiledEntry = code.compiledEntry;  // volatile read
        if (compiledEntry != null) {
          return compiledEntry;
        }
        compiledEntry = compileBody(code.context(), code.functionDef());
        code.compiledEntry = compiledEntry;  // volatile write
        return compiledEntry;
      }
    }

    private static boolean sameFunction(PyFunction expected, Object callee) {
      return callee == expected;
    }

    private static boolean sameCode(PyFunction.Code expected, Object callee) {
      return callee instanceof PyFunction f && f.code == expected;
    }

    private static MethodHandle compileBody(ModuleContext ctx, Stmt.FunctionDef def) {
      var scope = ctx.scopeFor(def);
      ScopeAnalyzer.checkDefiniteAssignment(scope);

      var dictionary = new Dictionary();
      var bytes = BytecodeGenerator.generateFunction(ctx, dictionary, def, scope);

      var className = BytecodeGenerator.functionClassName(def).replace('/', '.');
      var clazz = RT.defineClass(ctx, dictionary, className, bytes);

      var captureCount = BytecodeGenerator.captureNames(def, scope.analysis()).size();
      var type = BytecodeGenerator.bodyType(captureCount, def.parameters().size(), def.rest().isPresent());
      try {
        return LOOKUP.findStatic(clazz, def.name(), type);
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

    /// Adapts the entry to `(X, Object x (arity + restCount)) -> Object`: the rest parameter, at `position`,
    /// is replaced by `restCount` parameters (packed into a PyTuple for a Python function, into an Object[]
    /// for a builtin one).
    static MethodHandle adapt(PyFunction function, MethodHandle entry, int position, int restCount) {
      var code = function.code;
      if (!code.isVariadic()) {
        return entry;  // exact arity: no adaptation at all
      }
      if (code.isBuiltin()) {
        return entry.asCollector(position, Object[].class, restCount);
      }
      return RestAdapter.adapt(entry, position, restCount);
    }

    /// Keyword reordering, shared by the polymorphic and megamorphic paths.
    ///
    /// Returns `plan` such that call-site argument `i` (callee excluded) goes to the slot `plan[i]`
    /// of the canonical order: the declared parameters, then the extra positional arguments.
    /// The canonical size is `max(positionalCount, arity)` and every slot is filled exactly once.
    static int[] reorderKeywordArguments(PyFunction function, int positionalCount, String[] keywordNames) {
      if (function.code.isBuiltin()) {
        throw builtinKeywords(function);
      }
      var parameters = function.parameters();
      var arity = parameters.size();

      var plan = new int[positionalCount + keywordNames.length];
      var assigned = new boolean[arity];
      for (var i = 0; i < positionalCount; i++) {
        plan[i] = i;
        if (i < arity) {
          assigned[i] = true;
        }
      }
      for (var i = 0; i < keywordNames.length; i++) {
        var name = keywordNames[i];
        var index = parameters.indexOf(name);
        if (index < 0) {
          throw unexpectedKeyword(function, name);
        }
        if (assigned[index]) {
          throw multipleValues(function, name);
        }
        assigned[index] = true;
        plan[positionalCount + i] = index;
      }

      var missing = (ArrayList<String>) null;
      for (var i = 0; i < arity; i++) {
        if (!assigned[i]) {
          if (missing == null) {
            missing = new ArrayList<>();
          }
          missing.add(parameters.get(i));
        }
      }
      if (missing != null) {
        throw missingArguments(function, missing);
      }
      return plan;
    }

    /// Adapts the entry to the call site, permuting the arguments according to the plan.
    private MethodHandle adaptKeywords(PyFunction function, MethodHandle entry, MethodType type, int[] plan) {
      var arity = function.arity();
      var restCount = Math.max(0, positionalCount - arity);

      // reorder[j] = position in the call site of the j-th parameter of the adapted entry (the callee stays first)
      var reorder = new int[1 + arity + restCount];
      reorder[0] = 0;
      for (var i = 0; i < plan.length; i++) {
        reorder[1 + plan[i]] = 1 + i;
      }

      var adapted = adapt(function, entry, 1 + arity, restCount).asType(type);
      return MethodHandles.permuteArguments(adapted, type, reorder);
    }


    // -- errors

    private static PyTypeError arityError(PyFunction function, int given) {
      var arity = function.arity();
      if (given < arity) {
        return missingArguments(function, function.parameters().subList(given, arity));
      }
      return tooManyPositional(function, given);
    }

    static String joinNames(List<String> names) {
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

    /// Fixed-arity builtins are positional-only (as in CPython). Variadic builtins accept keywords in CPython
    /// (print(sep=...), int(x, base=...)) but we do not have keyword ABI for builtins yet.
    private static RuntimeException builtinKeywords(PyFunction function) {
      if (function.code.isVariadic()) {
        return new UnimplementedFeatureError("keyword arguments of the built-in function " + function.name() + "()");
      }
      return new PyTypeError(function.name() + "() takes no keyword arguments");
    }

    private static PyTypeError unexpectedKeyword(PyFunction function, String name) {
      return new PyTypeError(function.name() + "() got an unexpected keyword argument '" + name + "'");
    }

    private static PyTypeError multipleValues(PyFunction function, String name) {
      return new PyTypeError(function.name() + "() got multiple values for argument '" + name + "'");
    }

    private static PyTypeError missingArguments(PyFunction function, List<String> missing) {
      return new PyTypeError(function.name() + "() missing " + missing.size() + " required positional argument"
          + (missing.size() == 1 ? "" : "s") + ": " + joinNames(missing));
    }

    private static PyTypeError tooManyPositional(PyFunction function, int given) {
      var arity = function.arity();
      return new PyTypeError(function.name() + "() takes " + arity + " positional argument"
          + (arity == 1 ? "" : "s") + " but " + given + (given == 1 ? " was" : " were") + " given");
    }
  }
}