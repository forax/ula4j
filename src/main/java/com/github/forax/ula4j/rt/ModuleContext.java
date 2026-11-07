package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.ast.Script;
import com.github.forax.ula4j.ast.Stmt;
import com.github.forax.ula4j.compiler.ScopeAnalyzer;
import com.github.forax.ula4j.compiler.ScopeAnalyzer.FunctionScope;
import com.github.forax.ula4j.rt.error.PyNameError;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/// Everything a module owns: global cells, builtins, the registered functions and their compiled bodies.
/// Generated code reaches it through `((ModuleClassLoader) lookup.lookupClass().getClassLoader()).context()`.
public final class ModuleContext {
  public record FunctionInfo(Stmt.FunctionDef def, String qualifiedName) {}

  /// A module-level name. `null` means unbound.
  public static final class GlobalCell {
    private final HashMap<String, Object> builtins;
    private final String name;
    private Object value;

    private GlobalCell(HashMap<String, Object> builtins, String name) {
      this.builtins = builtins;
      this.name = name;
      super();
    }

    public Object get() {
      var value = this.value;
      if (value != null) {
        return value;
      }
      value = builtins.get(name);
      if (value != null) {
        return value;
      }
      throw new PyNameError("name '" + name + "' is not defined");
    }

    public void set(Object newValue) {
      value = Objects.requireNonNull(newValue);
    }
  }

  private final ModuleClassLoader loader;
  private final ArrayList<FunctionInfo> functions = new ArrayList<>();
  private final IdentityHashMap<Stmt.FunctionDef, Integer> ids = new IdentityHashMap<>();
  private final IdentityHashMap<Stmt.FunctionDef, FunctionScope> scopes = new IdentityHashMap<>();
  private final HashMap<String, GlobalCell> globals = new HashMap<>();
  private final HashMap<String, Object> builtins = new HashMap<>();
  private final HashMap<Integer, MethodHandle> bodies = new HashMap<>();
  private final AtomicInteger scriptCount = new AtomicInteger();

  public ModuleContext() {
    this.loader = new ModuleClassLoader(this);
  }

  public ModuleClassLoader loader() { return loader; }
  public int nextScriptId() { return scriptCount.getAndIncrement(); }

  // ---- globals

  GlobalCell cell(String name) {
    return globals.computeIfAbsent(name, n -> new GlobalCell(builtins, n));
  }

  public Object getGlobal(String name) { return cell(name).get(); }
  public void setGlobal(String name, Object value) { cell(name).set(value); }
  public void defineBuiltin(String name, Object value) { builtins.put(name, Objects.requireNonNull(value)); }

  // ---- functions

  /// Gives an id and a qualified name (CPython's `__qualname__`) to every def of the script, nested ones included.
  public synchronized void registerFunctions(Script script) {
    register(script.body().stmts(), null);
  }

  private void register(List<Stmt> stmts, String enclosing) {
    for (var stmt : stmts) {
      switch (stmt) {
        case Stmt.FunctionDef def -> {
          var qualified = enclosing == null ? def.name() : enclosing + ".<locals>." + def.name();
          ids.put(def, functions.size());
          functions.add(new FunctionInfo(def, qualified));
          register(def.body().stmts(), qualified);
        }
        case Stmt.If s -> {
          register(s.body().stmts(), enclosing);
          register(s.orElse().stmts(), enclosing);
        }
        case Stmt.While s -> {
          register(s.body().stmts(), enclosing);
          register(s.orElse().stmts(), enclosing);
        }
        case Stmt.For s -> {
          register(s.body().stmts(), enclosing);
          register(s.orElse().stmts(), enclosing);
        }
        default -> { }
      }
    }
  }

  public synchronized FunctionInfo functionInfo(int id) { return functions.get(id); }

  public synchronized int idOf(Stmt.FunctionDef def) {
    var id = ids.get(def);
    if (id == null) {
      throw new IllegalStateException("function '" + def.name() + "' is not registered");
    }
    return id;
  }

  /// Scopes of nested functions are registered while their enclosing function is compiled.
  public void registerScope(Stmt.FunctionDef def, FunctionScope scope) {
    scopes.put(def, scope);
  }

  public FunctionScope scopeOf(Stmt.FunctionDef def) {
    var scope = scopes.get(def);
    if (scope == null) {
      throw new IllegalStateException("no scope for '" + def.name() + "': its enclosing function is not compiled");
    }
    return scope;
  }

  /// The scope of a function, resolving it now if it is a module-level function.
  FunctionScope scopeFor(Stmt.FunctionDef def) {
    var scope = scopes.get(def);
    if (scope == null) {
      scope = ScopeAnalyzer.resolveTopLevel(def);  // passes 1-3 for the function and everything nested in it
      scopes.put(def, scope);
    }
    return scope;
  }

  HashMap<Integer, MethodHandle> bodies() { return bodies; }
}