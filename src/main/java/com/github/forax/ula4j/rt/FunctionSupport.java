package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.compiler.BytecodeGenerator;
import com.github.forax.ula4j.compiler.ScopeAnalyzer;
import com.github.forax.ula4j.ast.Stmt;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;

/// Implementation of the `def` / global bootstraps and of the lazy compilation of function bodies.
final class FunctionSupport {
  private FunctionSupport() { throw new AssertionError(); }

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
  private static final MethodHandle MAKE_FUNCTION, COMPILE_AND_INVOKE, CELL_GET, CELL_SET;

  static {
    try {
      MAKE_FUNCTION = LOOKUP.findStatic(FunctionSupport.class, "makeFunction",
          MethodType.methodType(PyFunction.class, ModuleContext.class, Stmt.FunctionDef.class, Object[].class));
      COMPILE_AND_INVOKE = LOOKUP.findStatic(FunctionSupport.class, "compileAndInvoke",
          MethodType.methodType(Object.class,
              ModuleContext.class, Stmt.FunctionDef.class, Object[].class, PyFunction.class, Object[].class));
      CELL_GET = LOOKUP.findVirtual(ModuleContext.GlobalCell.class, "get",
          MethodType.methodType(Object.class));
      CELL_SET = LOOKUP.findVirtual(ModuleContext.GlobalCell.class, "set",
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