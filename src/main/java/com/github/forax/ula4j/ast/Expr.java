package com.github.forax.ula4j.ast;

import java.math.BigInteger;
import java.util.List;

import static java.util.Objects.requireNonNull;

public sealed interface Expr {
  int line();
  int column();

  enum BinaryOperator {
    ADD("+"), SUB("-"), MUL("*"), DIV("/"), FLOOR_DIV("//"), MOD("%"), POW("**"),
    LSHIFT("<<"), RSHIFT(">>"), BIT_AND("&"), BIT_OR("|"), BIT_XOR("^");

    private final String symbol;
    BinaryOperator(String symbol) { this.symbol = symbol; }
    public String symbol() { return symbol; }
  }

  enum UnaryOperator {
    POS("+"), NEG("-"), INVERT("~");

    private final String symbol;
    UnaryOperator(String symbol) { this.symbol = symbol; }
    public String symbol() { return symbol; }
  }

  enum CompareOperator {
    EQ("=="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">="), IN("in"), NOT_IN("not in");

    private final String symbol;
    CompareOperator(String symbol) { this.symbol = symbol; }
    public String symbol() { return symbol; }
  }

  record Name(String id, int line, int column) implements Expr {
    public Name {
      requireNonNull(id);
    }
  }

  /** The value is a Long, a BigInteger that does not fit in a long, a Double, a String or a Boolean. */
  record Literal(Object value, int line, int column) implements Expr {
    public Literal {
      switch (value) {
        case Long _, Double _, String _, Boolean _ -> { }
        case BigInteger big -> {
          if (big.bitLength() < 64) {
            throw new IllegalArgumentException("BigInteger " + big + " fits in a long");
          }
        }
        default -> throw new IllegalArgumentException("not a canonical literal: " + value);
      }
    }
  }

  record NoneLiteral(int line, int column) implements Expr {}

  record BinOp(BinaryOperator op, Expr left, Expr right, int line, int column) implements Expr {
    public BinOp {
      requireNonNull(op);
      requireNonNull(left);
      requireNonNull(right);
    }
  }

  record UnaryOp(UnaryOperator op, Expr operand, int line, int column) implements Expr {
    public UnaryOp {
      requireNonNull(op);
      requireNonNull(operand);
    }
  }

  record Not(Expr operand, int line, int column) implements Expr {
    public Not {
      requireNonNull(operand);
    }
  }

  record And(Expr left, Expr right, int line, int column) implements Expr {
    public And {
      requireNonNull(left);
      requireNonNull(right);
    }
  }

  record Or(Expr left, Expr right, int line, int column) implements Expr {
    public Or {
      requireNonNull(left);
      requireNonNull(right);
    }
  }

  /** A comparison chain {@code first ops[0] rest[0] ops[1] rest[1] ...}; a single comparison is a chain of one. */
  record Compare(Expr first, List<CompareOperator> ops, List<Expr> rest, int line, int column)
      implements Expr {
    public Compare {
      requireNonNull(first);
      ops = List.copyOf(ops);
      rest = List.copyOf(rest);
      if (ops.isEmpty() || ops.size() != rest.size()) {
        throw new IllegalArgumentException("a comparison needs as many operators as right operands");
      }
    }
  }

  record Conditional(Expr test, Expr then, Expr orElse, int line, int column) implements Expr {
    public Conditional {
      requireNonNull(test);
      requireNonNull(then);
      requireNonNull(orElse);
    }
  }

  record KeywordArgument(String name, Expr value, int line, int column) {
    public KeywordArgument {
      requireNonNull(name);
      requireNonNull(value);
    }
  }

  record CallArguments(
      List<Expr> positional,
      List<KeywordArgument> keywords
  ) {
    public CallArguments {
      positional = List.copyOf(positional);
      keywords = List.copyOf(keywords);
    }
  }

  record Call(Expr func, CallArguments args, int line, int column) implements Expr {
    public Call {
      requireNonNull(func);
      requireNonNull(args);
    }
  }

  /** {@code receiver.name(args)}; the only form of attribute access that is accepted. */
  record MethodCall(Expr receiver, String name, CallArguments args, int line, int column)
      implements Expr {
    public MethodCall {
      requireNonNull(receiver);
      requireNonNull(name);
      requireNonNull(args);
    }
  }

  /** The index is an arbitrary expression, a {@link Slice} or a {@link TupleDisplay} (possibly containing slices). */
  record Subscript(Expr value, Expr index, int line, int column) implements Expr {
    public Subscript {
      requireNonNull(value);
      requireNonNull(index);
    }
  }

  /** Omitted parts are {@link NoneLiteral}s. */
  record Slice(Expr lower, Expr upper, Expr step, int line, int column) implements Expr {
    public Slice {
      requireNonNull(lower);
      requireNonNull(upper);
      requireNonNull(step);
    }
  }

  record ListDisplay(List<Expr> elements, int line, int column) implements Expr {
    public ListDisplay {
      elements = List.copyOf(elements);
    }
  }

  record TupleDisplay(List<Expr> elements, int line, int column) implements Expr {
    public TupleDisplay {
      elements = List.copyOf(elements);
    }
  }

  record DictEntry(Expr key, Expr value, int lineNumber, int column) {
    public DictEntry {
      requireNonNull(key);
      requireNonNull(value);
    }
  }

  record DictDisplay(List<DictEntry> entries, int line, int column) implements Expr {
    public DictDisplay {
      entries = List.copyOf(entries);
    }
  }
}