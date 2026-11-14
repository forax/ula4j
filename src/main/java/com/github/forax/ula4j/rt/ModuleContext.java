package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.ast.Stmt;
import com.github.forax.ula4j.compiler.ScopeAnalyzer;
import com.github.forax.ula4j.compiler.ScopeAnalyzer.FunctionScope;
import com.github.forax.ula4j.rt.error.PyNameError;

import java.lang.invoke.SwitchPoint;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/// Everything a module owns: global cells, builtins, the registered functions and their compiled bodies.
public final class ModuleContext {

  // can only be get()/put() under a synchronized
  private final IdentityHashMap<Stmt.FunctionDef, FunctionScope> scopes;

  private final ConcurrentHashMap<String, GlobalSupport.GlobalCell> globals;

  public ModuleContext() {
    scopes = new IdentityHashMap<>();
    globals = new ConcurrentHashMap<>();
    super();
  }

  // ---- globals

  GlobalSupport.GlobalCell cell(String name) {
    return globals.computeIfAbsent(name,
        n -> new GlobalSupport.GlobalCell(n, Builtins.BUILTINS.get(n)));
  }

  public Object getGlobal(String name) {
    Objects.requireNonNull(name);
    return cell(name).get();
  }
  public void setGlobal(String name, Object value) {
    Objects.requireNonNull(name);
    Objects.requireNonNull(value);
    cell(name).set(value);
  }

  // ---- functions

  /// Scopes of nested functions are registered while their enclosing function is compiled.
  public void registerScope(Stmt.FunctionDef def, FunctionScope scope) {
    Objects.requireNonNull(def);
    Objects.requireNonNull(scope);
    synchronized (scopes) {
      scopes.put(def, scope);
    }
  }

  public FunctionScope scopeOf(Stmt.FunctionDef def) {
    FunctionScope scope;
    synchronized (scopes) {
      scope = scopes.get(def);
    }
    if (scope == null) {
      throw new IllegalStateException("no scope for '" + def.name() + "': its enclosing function is not compiled");
    }
    return scope;
  }

  /// The scope of a function, resolving it now if it is a module-level function.
  FunctionScope scopeFor(Stmt.FunctionDef def) {
    synchronized (scopes) {
      var scope = scopes.get(def);
      if (scope == null) {
        scope = ScopeAnalyzer.resolveTopLevel(def);  // passes 1-3 for the function and everything nested in it
        scopes.put(def, scope);
      }
      return scope;
    }
  }
}