package com.github.forax.ula4j.ast;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public sealed interface Stmt {
  int line();
  int column();

  record ExprStmt(Expr expr, int line, int column) implements Stmt {
    public ExprStmt {
      requireNonNull(expr);
    }
  }

  /** {@code t1 = t2 = value}: the value is evaluated once, then assigned to the targets left to right. */
  record Assign(List<Target> targets, Expr value, int line, int column) implements Stmt {
    public Assign {
      targets = List.copyOf(targets);
      requireNonNull(value);
      if (targets.isEmpty()) {
        throw new IllegalArgumentException("no target");
      }
    }
  }

  /** The target is a name or a subscript. */
  record AugAssign(Target target, Expr.BinaryOperator op, Expr value, int line, int column)
      implements Stmt {
    public AugAssign {
      requireNonNull(target);
      requireNonNull(op);
      requireNonNull(value);
      if (target instanceof Target.TupleTarget) {
        throw new IllegalArgumentException("a tuple can not be an augmented assignment target");
      }
    }
  }

  record Del(List<Target.SubscriptTarget> targets, int line, int column) implements Stmt {
    public Del {
      targets = List.copyOf(targets);
    }
  }

  /** A bare {@code return} carries a {@link Expr.NoneLiteral}. */
  record Return(Expr value, int line, int column) implements Stmt {
    public Return {
      requireNonNull(value);
    }
  }

  record Break(int line, int column) implements Stmt {}

  record Continue(int line, int column) implements Stmt {}

  /** {@code elif} is a nested {@link If} alone in {@code orElse}. */
  record If(Expr test, Block body, Block orElse, int line, int column) implements Stmt {
    public If {
      requireNonNull(test);
      requireNonNull(body);
      requireNonNull(orElse);
    }
  }

  record While(Expr test, Block body, Block orElse, int line, int column) implements Stmt {
    public While {
      requireNonNull(test);
      requireNonNull(body);
      requireNonNull(orElse);
    }
  }

  record For(Target target, Expr iter, Block body, Block orElse, int line, int column)
      implements Stmt {
    public For {
      requireNonNull(target);
      requireNonNull(iter);
      requireNonNull(body);
      requireNonNull(orElse);
    }
  }

  record FunctionDef(String name, List<Param> parameters, Optional<Param> rest, Block body,
                     int line, int column) implements Stmt {

    public record Param(String name, int line, int column) {
      public Param {
        requireNonNull(name);
      }
    }

    public FunctionDef {
      requireNonNull(name);
      parameters = List.copyOf(parameters);
      requireNonNull(rest);
      requireNonNull(body);
    }
  }
}