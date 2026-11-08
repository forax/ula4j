package com.github.forax.ula4j.compiler;

import com.github.forax.ula4j.ast.ASTBuilder;
import com.github.forax.ula4j.ast.Script;
import com.github.forax.ula4j.rt.Dictionary;
import com.github.forax.ula4j.rt.ModuleClassLoader;
import com.github.forax.ula4j.rt.ModuleContext;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.UndeclaredThrowableException;

/// Facade: source -> AST -> `Script_<n>` class -> run `__main__`.
public final class Compiler {
  private Compiler() { throw new AssertionError(); }

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  /// Compiles the script eagerly and returns its `__main__`; function bodies are compiled on their first call.
  public static MethodHandle compile(ModuleContext ctx, Script script) {
    var internalName = "com/github/forax/ula4j/gen/Script";
    var dictionary = new Dictionary();
    var bytes = BytecodeGenerator.generateScript(ctx, dictionary, script, internalName);
    var loader = new ModuleClassLoader(ctx, dictionary);
    var cls = loader.define(internalName.replace('/', '.'), bytes);
    try {
      return LOOKUP.findStatic(cls, "__main__", MethodType.methodType(void.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  /// Runs a program and returns its module, whose globals can then be inspected.
  public static ModuleContext run(String source) {
    var ctx = new ModuleContext();
    var main = compile(ctx, ASTBuilder.createScript(source));
    try {
      main.invokeExact();
    } catch(RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new UndeclaredThrowableException(e);
    }
    return ctx;
  }
}