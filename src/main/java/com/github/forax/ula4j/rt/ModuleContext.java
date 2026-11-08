package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.ast.Stmt;
import com.github.forax.ula4j.compiler.ScopeAnalyzer;
import com.github.forax.ula4j.compiler.ScopeAnalyzer.FunctionScope;
import com.github.forax.ula4j.rt.error.PyNameError;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/// Everything a module owns: global cells, builtins, the registered functions and their compiled bodies.
/// Generated code reaches it through `((ModuleClassLoader) lookup.lookupClass().getClassLoader()).context()`.
public final class ModuleContext {

  /// A module-level name. `null` means unbound.
  static final class GlobalCell {
    private final ConcurrentHashMap<String, Object> builtins;
    private final String name;
    private volatile Object value;

    private GlobalCell(ConcurrentHashMap<String, Object> builtins, String name) {
      this.builtins = builtins;
      this.name = name;
      super();
    }

    public Object get() {
      var value = this.value;
      if (value != null) {
        return value;
      }
      var builtinValue = builtins.get(name);
      if (builtinValue != null) {
        return builtinValue;
      }
      throw new PyNameError("name '" + name + "' is not defined");
    }

    public void set(Object value) {
      this.value = Objects.requireNonNull(value);
    }
  }

  // can only be get()/put() under a synchronized
  private final IdentityHashMap<Stmt.FunctionDef, FunctionScope> scopes;

  private final ConcurrentHashMap<String, GlobalCell> globals;
  private final ConcurrentHashMap<String, Object> builtins;

  public ModuleContext() {
    scopes = new IdentityHashMap<>();
    globals = new ConcurrentHashMap<>();
    builtins = new ConcurrentHashMap<>();
    super();
  }

  // ---- globals

  GlobalCell cell(String name) {
    return globals.computeIfAbsent(name, n -> new GlobalCell(builtins, n));
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
  public void defineBuiltin(String name, Object value) {
    Objects.requireNonNull(name);
    Objects.requireNonNull(value);
    builtins.put(name, value);
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