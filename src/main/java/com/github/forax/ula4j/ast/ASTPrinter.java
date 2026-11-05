package com.github.forax.ula4j.ast;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Dumps an AST as an s-expression; with {@code positions}, every node is followed by {@code @line:column}. */
final class ASTPrinter {
  private final boolean positions;

  private ASTPrinter(boolean positions) {
    this.positions = positions;
  }

  static String print(Script script, boolean positions) {
    return new ASTPrinter(positions).block(script.body());
  }

  private String at(int line, int column) {
    return positions ? "@" + line + ":" + column : "";
  }

  private String leaf(String text, int line, int column) {
    return text + at(line, column);
  }

  private String node(String head, int line, int column, List<String> parts) {
    var sb = new StringBuilder("(").append(head).append(at(line, column));
    for (var part : parts) {
      sb.append(' ').append(part);
    }
    return sb.append(')').toString();
  }

  private String node(String head, int line, int column, String... parts) {
    return node(head, line, column, List.of(parts));
  }

  private static <T> List<T> prepend(T first, List<T> rest) {
    var list = new ArrayList<T>();
    list.add(first);
    list.addAll(rest);
    return list;
  }

  private static <T> String list(List<T> items, Function<? super T, String> printer) {
    return items.stream().map(printer).collect(Collectors.joining(" ", "[", "]"));
  }

  private List<String> exprs(List<Expr> exprs) {
    return exprs.stream().map(this::expr).toList();
  }

  private String block(Block block) {
    return node("block", block.lineNumber(), block.column(), block.stmts().stream().map(this::stmt).toList());
  }

  private String stmt(Stmt stmt) {
    var l = stmt.line();
    var c = stmt.column();
    return switch (stmt) {
      case Stmt.ExprStmt s -> node("expr", l, c, expr(s.expr()));
      case Stmt.Assign s -> node("assign", l, c, list(s.targets(), this::target), expr(s.value()));
      case Stmt.AugAssign s -> node("augassign", l, c, s.op().symbol(), target(s.target()), expr(s.value()));
      case Stmt.Del s -> node("del", l, c, s.targets().stream().map(this::target).toList());
      case Stmt.Return s -> node("return", l, c, expr(s.value()));
      case Stmt.Break _ -> node("break", l, c);
      case Stmt.Continue _ -> node("continue", l, c);
      case Stmt.If s -> node("if", l, c, expr(s.test()), block(s.body()), block(s.orElse()));
      case Stmt.While s -> node("while", l, c, expr(s.test()), block(s.body()), block(s.orElse()));
      case Stmt.For s ->
          node("for", l, c, target(s.target()), expr(s.iter()), block(s.body()), block(s.orElse()));
      case Stmt.FunctionDef s -> node("def", l, c, s.name(),
          list(s.parameters(), p -> leaf(p.name(), p.line(), p.column())),
          "rest=" + s.rest().map(p -> leaf(p.name(), p.line(), p.column())).orElse("-"),
          block(s.body()));
    };
  }

  private String target(Target target) {
    var l = target.line();
    var c = target.column();
    return switch (target) {
      case Target.NameTarget t -> leaf(t.id(), l, c);
      case Target.SubscriptTarget t -> node("subscript", l, c, expr(t.value()), expr(t.index()));
      case Target.TupleTarget t -> node("tuple", l, c, t.elements().stream().map(this::target).toList());
    };
  }

  private String expr(Expr expr) {
    var l = expr.lineNumber();
    var c = expr.column();
    return switch (expr) {
      case Expr.Name e -> leaf(e.id(), l, c);
      case Expr.Literal e -> leaf(literal(e.value()), l, c);
      case Expr.NoneLiteral _ -> leaf("None", l, c);
      case Expr.BinOp e -> node(e.op().symbol(), l, c, expr(e.left()), expr(e.right()));
      case Expr.UnaryOp e -> node("unary" + e.op().symbol(), l, c, expr(e.operand()));
      case Expr.Not e -> node("not", l, c, expr(e.operand()));
      case Expr.And e -> node("and", l, c, expr(e.left()), expr(e.right()));
      case Expr.Or e -> node("or", l, c, expr(e.left()), expr(e.right()));
      case Expr.Compare e -> compare(e);
      case Expr.Conditional e -> node("conditional", l, c, expr(e.test()), expr(e.then()), expr(e.orElse()));
      case Expr.Call e -> node("call", l, c, prepend(expr(e.func()), exprs(e.args())));
      case Expr.MethodCall e ->
          node("method", l, c, prepend(expr(e.receiver()), prepend(e.name(), exprs(e.args()))));
      case Expr.Subscript e -> node("subscript", l, c, expr(e.value()), expr(e.index()));
      case Expr.Slice e -> node("slice", l, c, expr(e.lower()), expr(e.upper()), expr(e.step()));
      case Expr.ListDisplay e -> node("list", l, c, exprs(e.elements()));
      case Expr.TupleDisplay e -> node("tuple", l, c, exprs(e.elements()));
      case Expr.DictDisplay e -> node("dict", l, c, e.entries().stream()
          .map(entry -> node("entry", entry.lineNumber(), entry.column(), expr(entry.key()), expr(entry.value())))
          .toList());
    };
  }

  private String compare(Expr.Compare e) {
    var parts = new ArrayList<String>();
    parts.add(expr(e.first()));
    for (var i = 0; i < e.ops().size(); i++) {
      parts.add(e.ops().get(i).symbol());
      parts.add(expr(e.rest().get(i)));
    }
    return node("compare", e.lineNumber(), e.column(), parts);
  }

  private static String literal(Object value) {
    return switch (value) {
      case String s -> quote(s);
      case Boolean b -> b ? "True" : "False";
      case BigInteger big -> "big:" + big;
      default -> value.toString();  // Long and Double
    };
  }

  private static String quote(String s) {
    var sb = new StringBuilder("'");
    for (var i = 0; i < s.length(); i++) {
      var c = s.charAt(i);
      switch (c) {
        case '\\' -> sb.append("\\\\");
        case '\'' -> sb.append("\\'");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> sb.append(c);
      }
    }
    return sb.append('\'').toString();
  }
}