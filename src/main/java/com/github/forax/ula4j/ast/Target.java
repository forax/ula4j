package com.github.forax.ula4j.ast;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** Assignment targets, kept apart from expressions so that a literal can never be assigned to. */
public sealed interface Target {
  int line();
  int column();

  record NameTarget(String id, int line, int column) implements Target {
    public NameTarget {
      requireNonNull(id);
    }
  }

  record SubscriptTarget(Expr value, Expr index, int line, int column) implements Target {
    public SubscriptTarget {
      requireNonNull(value);
      requireNonNull(index);
    }
  }

  /** Fixed-size unpacking; elements may be nested tuples, the list may be empty. */
  record TupleTarget(List<Target> elements, int line, int column) implements Target {
    public TupleTarget {
      elements = List.copyOf(elements);
    }
  }
}