package com.github.forax.ula4j.compiler;

import com.github.forax.ula4j.ast.Expr;
import com.github.forax.ula4j.ast.Script;
import com.github.forax.ula4j.ast.Stmt;
import com.github.forax.ula4j.ast.Target;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ScopeAnalyzer {

  public record FunctionAnalysis(Set<String> locals, Set<String> freeVariables) {
    public FunctionAnalysis {
      locals = Collections.unmodifiableSet(new LinkedHashSet<>(locals));
      freeVariables = Collections.unmodifiableSet(new LinkedHashSet<>(freeVariables));
    }
  }

  // ---------------------------------------------------------------------
  // Internal scope
  // ---------------------------------------------------------------------

  private static final class Scope {
    final Scope parent;
    final boolean isFunction;
    final LinkedHashSet<String> locals;
    final LinkedHashSet<String> params;
    final LinkedHashSet<String> frees;
    final LinkedHashMap<String, Integer> assignmentCounts ;
    final ArrayList<Scope> children;

    Stmt.FunctionDef function;

    Scope(Scope parent, boolean isFunction) {
      this.parent = parent;
      this.isFunction = isFunction;
      this.locals = new LinkedHashSet<>();
      this.params = new LinkedHashSet<>();
      this.frees = new LinkedHashSet<>();
      this.assignmentCounts = new LinkedHashMap<>();
      this.children = new ArrayList<>();
      super();
    }

    void recordAssignment(String name) {
      assignmentCounts.merge(name, 1, Integer::sum);
    }
  }

  private static final class FlowContext {
    final ArrayList<Set<String>> breakStates;

    public FlowContext() {
      this.breakStates = new ArrayList<>();
      super();
    }
  }

  private ScopeAnalyzer() {}

  // ---------------------------------------------------------------------
  // Entry point
  // ---------------------------------------------------------------------

  public static Map<Stmt.FunctionDef, FunctionAnalysis> analyze(Script script) {
    var functionScopes = new IdentityHashMap<Stmt.FunctionDef, Scope>();
    var module = new Scope(null, /*isFunction=*/false);

    collectStatements(script.body().stmts(), module, functionScopes);
    resolveStatements(script.body().stmts(), module, functionScopes);
    checkEffectivelyFinal(module);
    checkDefiniteAssignment(script.body().stmts(), module, functionScopes);

    var result = new IdentityHashMap<Stmt.FunctionDef, FunctionAnalysis>();
    for (var entry : functionScopes.entrySet()) {
      var function = entry.getKey();
      var scope = entry.getValue();
      result.put(function, new FunctionAnalysis(scope.locals, scope.frees));
    }
    return result;
  }

  // ---------------------------------------------------------------------
  // Pass 1: locals / params / assignment counts / scope tree
  // ---------------------------------------------------------------------

  private static void collectStatements(
      List<Stmt> stmts, Scope scope,
      IdentityHashMap<Stmt.FunctionDef, Scope> functionScopes) {
    for (var stmt : stmts) {
      collectStatement(stmt, scope, functionScopes);
    }
  }

  private static void collectStatement(
      Stmt stmt, Scope scope,
      IdentityHashMap<Stmt.FunctionDef, Scope> functionScopes) {
    switch (stmt) {
      case Stmt.Assign(var targets, _, _, _) -> {
        for (var target : targets) {
          collectTargetBinding(target, scope);
        }
      }
      case Stmt.AugAssign(var target, _, _, _, _) -> collectTargetBinding(target, scope);
      case Stmt.Del _, Stmt.ExprStmt _, Stmt.Return _, Stmt.Break _, Stmt.Continue _ -> { }
      case Stmt.If(_, var body, var orElse, _, _) -> {
        collectStatements(body.stmts(),   scope, functionScopes);
        collectStatements(orElse.stmts(), scope, functionScopes);
      }
      case Stmt.While(_, var body, var orElse, _, _) -> {
        collectStatements(body.stmts(),   scope, functionScopes);
        collectStatements(orElse.stmts(), scope, functionScopes);
      }
      case Stmt.For(var target, _, var body, var orElse, _, _) -> {
        collectTargetBinding(target, scope);
        collectStatements(body.stmts(),   scope, functionScopes);
        collectStatements(orElse.stmts(), scope, functionScopes);
      }
      case Stmt.FunctionDef s -> {
        scope.locals.add(s.name());
        scope.recordAssignment(s.name());

        var child = new Scope(scope, /*isFunction=*/true);
        child.function  = s;
        for (var p : s.parameters()) { child.locals.add(p.name()); child.params.add(p.name()); }
        s.rest().ifPresent(p ->       { child.locals.add(p.name()); child.params.add(p.name()); });

        scope.children.add(child);
        functionScopes.put(s, child);
        collectStatements(s.body().stmts(), child, functionScopes);
      }
    }
  }

  private static void collectTargetBinding(Target target, Scope scope) {
    switch (target) {
      case Target.NameTarget(String id, _, _) -> {
        scope.locals.add(id);
        scope.recordAssignment(id);
      }
      case Target.SubscriptTarget _ -> { }
      case Target.TupleTarget(var elements, _, _) -> {
        for (var e : elements) collectTargetBinding(e, scope);
      }
    }
  }

  // ---------------------------------------------------------------------
  // Pass 2: free-variable resolution (closure chain)
  // ---------------------------------------------------------------------

  private static void resolveStatements(
      List<Stmt> stmts, Scope scope,
      IdentityHashMap<Stmt.FunctionDef, Scope> functionScopes) {
    for (var s : stmts) resolveStatement(s, scope, functionScopes);
  }

  private static void resolveStatement(
      Stmt stmt, Scope scope,
      IdentityHashMap<Stmt.FunctionDef, Scope> functionScopes) {
    switch (stmt) {
      case Stmt.ExprStmt(var expr, _, _) -> resolveExpr(expr, scope);
      case Stmt.Assign(var targets, var value, _, _) -> {
        for (var target : targets) {
          resolveTargetExprs(target, scope);
        }
        resolveExpr(value, scope);
      }
      case Stmt.AugAssign(var target, _, var value, _, _) -> {
        resolveTargetExprs(target, scope);
        resolveExpr(value, scope);
      }
      case Stmt.Del(var targets, _, _) -> {
        for (var target : targets) {
          resolveExpr(target.value(), scope);
          resolveExpr(target.index(), scope);
        }
      }
      case Stmt.Return(var value, _, _) -> resolveExpr(value, scope);
      case Stmt.Break _, Stmt.Continue _ -> { }
      case Stmt.If(var test, var body, var orElse, _, _) -> {
        resolveExpr(test, scope);
        resolveStatements(body.stmts(),   scope, functionScopes);
        resolveStatements(orElse.stmts(), scope, functionScopes);
      }
      case Stmt.While(var test, var body, var orElse, _, _) -> {
        resolveExpr(test, scope);
        resolveStatements(body.stmts(),   scope, functionScopes);
        resolveStatements(orElse.stmts(), scope, functionScopes);
      }
      case Stmt.For(var target, var iter, var body, var orElse, _, _) -> {
        resolveTargetExprs(target, scope);
        resolveExpr(iter, scope);
        resolveStatements(body.stmts(),   scope, functionScopes);
        resolveStatements(orElse.stmts(), scope, functionScopes);
      }
      case Stmt.FunctionDef def ->
          resolveStatements(def.body().stmts(), functionScopes.get(def), functionScopes);
    }
  }

  private static void resolveTargetExprs(Target target, Scope scope) {
    switch (target) {
      case Target.NameTarget _ -> { }
      case Target.SubscriptTarget(var value, var index, _, _) -> {
        resolveExpr(value, scope);
        resolveExpr(index, scope);
      }
      case Target.TupleTarget(var elements, _, _) -> {
        for (var element : elements) {
          resolveTargetExprs(element, scope);
        }
      }
    }
  }

  private static void resolveExpr(Expr expr, Scope scope) {
    switch (expr) {
      case Expr.Name(var id, _, _) -> resolveName(id, scope);
      case Expr.Literal _, Expr.NoneLiteral _ -> { }
      case Expr.BinOp(_, var l, var r, _, _) -> {
        resolveExpr(l, scope);
        resolveExpr(r, scope);
      }
      case Expr.UnaryOp(_, var operand, _, _) -> resolveExpr(operand, scope);
      case Expr.Not(var operand, _, _) -> resolveExpr(operand, scope);
      case Expr.And(var left, var right, _, _) -> {
        resolveExpr(left, scope);
        resolveExpr(right, scope);
      }
      case Expr.Or(var left, var right, _, _) -> {
        resolveExpr(left, scope);
        resolveExpr(right, scope);
      }
      case Expr.Compare(var first, _, var rest, _, _) -> {
        resolveExpr(first, scope);
        for (var restItem : rest) {
          resolveExpr(restItem, scope);
        }
      }
      case Expr.Conditional(var test, var then, var orElse, _, _) -> {
        resolveExpr(test, scope);
        resolveExpr(then, scope);
        resolveExpr(orElse, scope);
      }
      case Expr.Call(var func, var args, _, _) -> {
        resolveExpr(func, scope);
        for (var arg : args) {
          resolveExpr(arg, scope);
        }
      }
      case Expr.MethodCall(var receiver, _, var args, _, _) -> {
        resolveExpr(receiver, scope);
        for (var arg : args) {
          resolveExpr(arg, scope);
        }
      }
      case Expr.Subscript(var value, var index, _, _) -> {
        resolveExpr(value, scope);
        resolveExpr(index, scope);
      }
      case Expr.Slice(var lower, var upper, var step, _, _) -> {
        resolveExpr(lower, scope);
        resolveExpr(upper, scope);
        resolveExpr(step, scope);
      }
      case Expr.ListDisplay(var elements, _, _)  -> {
        for (var element : elements) {
          resolveExpr(element, scope);
        }
      }
      case Expr.TupleDisplay(var elements, _, _) -> {
        for (var element : elements) {
          resolveExpr(element, scope);
        }
      }
      case Expr.DictDisplay(var entries, _, _) -> {
        for (var entry : entries) {
          resolveExpr(entry.key(), scope);
          resolveExpr(entry.value(), scope);
        }
      }
    }
  }

  private static void resolveName(String name, Scope scope) {
    if (scope.locals.contains(name)) {
      return;
    }
    for (var current = scope; current.parent != null; current = current.parent) {
      var parent = current.parent;
      if (!parent.locals.contains(name)) {
        continue;
      }
      if (parent.isFunction) {
        for (var p = scope; p != parent; p = p.parent) {
          p.frees.add(name);
        }
      }
      return;
    }
  }

  // ---------------------------------------------------------------------
  // Pass 3: effectively-final check
  // ---------------------------------------------------------------------

  private static void checkEffectivelyFinal(Scope scope) {
    for (var name : scope.locals) {
      var capturing = findCapturingChild(scope, name);
      if (capturing == null) {
        continue;
      }
      var count = scope.assignmentCounts.getOrDefault(name, 0);
      var allowed = scope.params.contains(name) ? 0 : 1;
      if (count > allowed) {
        var msg = scope.params.contains(name)
            ? "parameter '" + name + "' is captured by '" + capturing.function.name()
            + "' but reassigned in '" + functionName(scope) + "'"
            : "local variable '" + name + "' is captured by '" + capturing.function.name()
            + "' but assigned " + count + " times (at most " + allowed + " allowed)";
        throw new SemanticErrorException(msg, capturing.function.line(), capturing.function.column());
      }
    }
    for (var child : scope.children) {
      checkEffectivelyFinal(child);
    }
  }

  private static boolean isCapturedIn(Scope scope, String name) {
    if (scope.frees.contains(name)) {
      return true;
    }
    for (var child : scope.children) {
      if (isCapturedIn(child, name)) {
        return true;
      }
    }
    return false;
  }

  private static Scope findCapturingChild(Scope scope, String name) {
    for (var child : scope.children) {
      if (isCapturedIn(child, name)) {
        return child;
      }
    }
    return null;
  }

  private static String functionName(Scope scope) {
    return scope.function == null ? "<module>" : scope.function.name();
  }

  // ---------------------------------------------------------------------
  // Pass 4: definite assignment (flow-sensitive) + capture-at-def
  // ---------------------------------------------------------------------

  private static void checkDefiniteAssignment(
      List<Stmt> stmts, Scope scope,
      IdentityHashMap<Stmt.FunctionDef, Scope> functionScopes) {
    var assigned = new LinkedHashSet<>(scope.params);
    checkStmts(stmts, scope, assigned, functionScopes, /*loop=*/null);
  }

  private static Optional<Set<String>> checkStmts(
      List<Stmt> stmts, Scope scope, Set<String> assigned,
      IdentityHashMap<Stmt.FunctionDef, Scope> functionScopes,
      FlowContext loop) {
    var current = assigned;
    for (var stmt : stmts) {
      var result = checkStmt(stmt, scope, current, functionScopes, loop);
      if (result.isEmpty()) {
        return Optional.empty();    // unreachable tail
      }
      current = result.get();
    }
    return Optional.of(current);
  }

  private static Optional<Set<String>> checkStmt(
      Stmt stmt, Scope scope, Set<String> assigned,
      IdentityHashMap<Stmt.FunctionDef, Scope> functionScopes,
      FlowContext loop) {
    switch (stmt) {
      case Stmt.ExprStmt(var expr, _, _) -> {
        checkExpr(expr, scope, assigned);
        return Optional.of(assigned);
      }
      case Stmt.Assign(var targets, var value, _, _) -> {
        checkExpr(value, scope, assigned);
        var out = new LinkedHashSet<>(assigned);
        for (var target : targets) {
          checkAndBindTarget(target, scope, out);
        }
        return Optional.of(out);
      }
      case Stmt.AugAssign(var target, _, var value, _, _) -> {
        switch (target) {
          case Target.NameTarget(String id, var line, var column) ->
              requireAssigned(id, scope, assigned, line, column);
          case Target.SubscriptTarget _ -> checkTargetExprs(target, scope, assigned);
          case Target.TupleTarget _ -> throw new AssertionError("augassign tuple target");
        }
        checkExpr(value, scope, assigned);
        var out = new LinkedHashSet<>(assigned);
        bindTarget(target, out);
        return Optional.of(out);
      }
      case Stmt.Del(var targets, _, _) -> {
        for (var target : targets) {
          checkExpr(target.value(), scope, assigned);
          checkExpr(target.index(), scope, assigned);
        }
        return Optional.of(assigned);
      }
      case Stmt.Return(var value, _, _) -> {
        checkExpr(value, scope, assigned);
        return Optional.empty();
      }
      case Stmt.Break(_, _) -> {
        assert loop != null : "break outside loop should have been rejected by ASTBuilder";
        loop.breakStates.add(new LinkedHashSet<>(assigned));
        return Optional.empty();
      }
      case Stmt.Continue(_, _) -> {
        return Optional.empty();
      }
      case Stmt.If(var test, var body, var orElse, _, _) -> {
        checkExpr(test, scope, assigned);
        var thenResult = checkStmts(body.stmts(),   scope, new LinkedHashSet<>(assigned), functionScopes, loop);
        var elseResult = checkStmts(orElse.stmts(), scope, new LinkedHashSet<>(assigned), functionScopes, loop);
        if (thenResult.isPresent() && elseResult.isPresent()) {
          var merged = new LinkedHashSet<>(thenResult.get());
          merged.retainAll(elseResult.get());
          return Optional.of(merged);
        }
        return thenResult.isPresent() ? thenResult : elseResult;
      }
      case Stmt.While(var test, var body, var orElse, _, _) -> {
        checkExpr(test, scope, assigned);
        var bodyLoop = new FlowContext();
        checkStmts(body.stmts(), scope, new LinkedHashSet<>(assigned), functionScopes, bodyLoop);
        var elseResult = orElse.stmts().isEmpty()
            ? Optional.<Set<String>>of(new LinkedHashSet<>(assigned))
            : checkStmts(orElse.stmts(), scope, new LinkedHashSet<>(assigned), functionScopes, loop);
        return loopExit(elseResult, bodyLoop);
      }
      case Stmt.For(var target, var iter, var body, var orElse, _, _) -> {
        checkExpr(iter, scope, assigned);
        var bodyLoop = new FlowContext();
        var bodyIn = new LinkedHashSet<>(assigned);
        checkAndBindTarget(target, scope, bodyIn);
        checkStmts(body.stmts(), scope, bodyIn, functionScopes, bodyLoop);
        var elseResult = orElse.stmts().isEmpty()
            ? Optional.<Set<String>>of(new LinkedHashSet<>(assigned))
            : checkStmts(orElse.stmts(), scope, new LinkedHashSet<>(assigned), functionScopes, loop);
        return loopExit(elseResult, bodyLoop);
      }

      case Stmt.FunctionDef s -> {
        var child = functionScopes.get(s);
        for (var name : child.frees) {
          // Self-recursion: the def *is* the assignment that makes the name available.
          if (name.equals(s.name())) {
            continue;
          }
          if (scope.locals.contains(name) && !assigned.contains(name)) {
            throw new SemanticErrorException(
                "variable '" + name + "' is captured by '" + s.name()
                    + "' but may be unassigned at the point of definition",
                s.line(), s.column());
          }
        }
        checkDefiniteAssignment(s.body().stmts(), child, functionScopes);
        var out = new LinkedHashSet<>(assigned);
        out.add(s.name());
        return Optional.of(out);
      }
    }
  }

  /**
   * Post-state of a loop: {@code elseResult ∩ (∩ break-states)}.
   * A break skips the else clause, so its state is a separate exit path.
   */
  private static Optional<Set<String>> loopExit(
      Optional<Set<String>> elseResult, FlowContext bodyLoop) {
    Set<String> normalExit = elseResult.orElse(null);
    Set<String> breakExit  = null;
    for (var bs : bodyLoop.breakStates) {
      if (breakExit == null) {
        breakExit = new LinkedHashSet<>(bs);
      }
      else breakExit.retainAll(bs);
    }
    if (normalExit == null && breakExit == null) {
      return Optional.empty();
    }
    if (breakExit == null) {
      return Optional.of(normalExit);
    }
    if (normalExit == null) {
      return Optional.of(breakExit);
    }
    var out = new LinkedHashSet<>(normalExit);
    out.retainAll(breakExit);
    return Optional.of(out);
  }

  /**
   * Assignment semantics for one target: check any expressions the target reads
   * (against the current state), then bind the name(s) it introduces. Processing
   * targets left-to-right makes {@code x = x[0] = []} valid.
   */
  private static void checkAndBindTarget(Target target, Scope scope, Set<String> assigned) {
    switch (target) {
      case Target.NameTarget(var id, _, _) -> assigned.add(id);
      case Target.SubscriptTarget(var value, var index, _, _) -> {
        checkExpr(value, scope, assigned);
        checkExpr(index, scope, assigned);
      }
      case Target.TupleTarget(var elements, _, _) -> {
        for (var element : elements) {
          checkAndBindTarget(element, scope, assigned);
        }
      }
    }
  }

  private static void bindTarget(Target target, Set<String> assigned) {
    switch (target) {
      case Target.NameTarget(var id, _, _) -> assigned.add(id);
      case Target.SubscriptTarget _ -> { }
      case Target.TupleTarget(var elements, _, _) -> {
        for (var element : elements) {
          bindTarget(element, assigned);
        }
      }
    }
  }

  private static void checkTargetExprs(Target target, Scope scope, Set<String> assigned) {
    switch (target) {
      case Target.NameTarget _ -> { }
      case Target.SubscriptTarget(var value, var index, _, _) -> {
        checkExpr(value, scope, assigned);
        checkExpr(index, scope, assigned);
      }
      case Target.TupleTarget(var elements, _, _) -> {
        for (var element : elements) {
          checkTargetExprs(element, scope, assigned);
        }
      }
    }
  }

  private static void checkExpr(Expr expr, Scope scope, Set<String> assigned) {
    switch (expr) {
      case Expr.Name(var id, var line, var column) -> {
        if (scope.locals.contains(id) && !assigned.contains(id)) {
          throw new SemanticErrorException(
              "local variable '" + id + "' may be used before assignment", line, column);
        }
      }
      case Expr.Literal _, Expr.NoneLiteral _ -> { }
      case Expr.BinOp(_, var left, var right, _, _) -> {
        checkExpr(left, scope, assigned);
        checkExpr(right, scope, assigned);
      }
      case Expr.UnaryOp(_, var operand, _, _) -> checkExpr(operand, scope, assigned);
      case Expr.Not(var operand, _, _) -> checkExpr(operand, scope, assigned);
      case Expr.And(var left, var right, _, _) -> {
        checkExpr(left, scope, assigned);
        checkExpr(right, scope, assigned);
      }
      case Expr.Or(var left, var right, _, _) -> {
        checkExpr(left, scope, assigned);
        checkExpr(right, scope, assigned);
      }
      case Expr.Compare(var first, _, var rest, _, _) -> {
        checkExpr(first, scope, assigned);
        for (var restItem : rest) {
          checkExpr(restItem, scope, assigned);
        }
      }
      case Expr.Conditional(var test, var then, var orElse, _, _) -> {
        checkExpr(test, scope, assigned);
        checkExpr(then, scope, assigned);
        checkExpr(orElse, scope, assigned);
      }
      case Expr.Call(var func, var args, _, _) -> {
        checkExpr(func, scope, assigned);
        for (var arg : args) {
          checkExpr(arg, scope, assigned);
        }
      }
      case Expr.MethodCall(var receiver, _, var args, _, _) -> {
        checkExpr(receiver, scope, assigned);
        for (var arg : args) {
          checkExpr(arg, scope, assigned);
        }
      }
      case Expr.Subscript(var value, var index, _, _) -> {
        checkExpr(value, scope, assigned);
        checkExpr(index, scope, assigned);
      }
      case Expr.Slice(var lower, var upper, var st, _, _) -> {
        checkExpr(lower, scope, assigned);
        checkExpr(upper, scope, assigned);
        checkExpr(st, scope, assigned);
      }
      case Expr.ListDisplay(var elements, _, _)  -> {
        for (var element : elements) {
          checkExpr(element, scope, assigned);
        }
      }
      case Expr.TupleDisplay(var elements, _, _) -> {
        for (var element : elements) {
          checkExpr(element, scope, assigned);
        }
      }
      case Expr.DictDisplay(var entries, _, _) -> {
        for (var entry : entries) {
          checkExpr(entry.key(), scope, assigned);
          checkExpr(entry.value(), scope, assigned);
        }
      }
    }
  }

  private static void requireAssigned(String name, Scope scope, Set<String> assigned, int line, int column) {
    if (scope.locals.contains(name) && !assigned.contains(name)) {
      throw new SemanticErrorException(
          "local variable '" + name + "' may be used before assignment", line, column);
    }
  }
}