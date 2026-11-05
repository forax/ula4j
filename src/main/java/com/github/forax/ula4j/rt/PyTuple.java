package com.github.forax.ula4j.rt;

import java.util.Arrays;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public sealed interface PyTuple {

  record PyTuple2(Object item0, Object item1) implements PyTuple {
    public PyTuple2 {
      requireNonNull(item0);
      requireNonNull(item1);
    }

    @Override
    public String toString() {
      return "PyTuple2(" + item0 + ", " + item1 + ")";
    }
  }

  record PyTuple3(Object item0, Object item1, Object item2) implements PyTuple {
    public PyTuple3 {
      requireNonNull(item0);
      requireNonNull(item1);
      requireNonNull(item2);
    }

    @Override
    public String toString() {
      return "PyTuple3(" + item0 + ", " + item1 + ", " + item2 + ")";
    }
  }

  record PyTuple4(Object item0, Object item1, Object item2, Object item4) implements PyTuple {
    public PyTuple4 {
      requireNonNull(item0);
      requireNonNull(item1);
      requireNonNull(item2);
      requireNonNull(item4);
    }

    @Override
    public String toString() {
      return "PyTuple4(" +  item0 + ", " + item1 + ", " + item2 + ", " + item4 + ")";
    }
  }

  record PyTupleN(Object[] items) implements PyTuple {
    public PyTupleN {
      for(var  item : items) {
        requireNonNull(item);
      }
      items = Arrays.copyOf(items, items.length);
    }

    @Override
    public Object[] items() {
      throw new AssertionError("use item(index) instead");
    }

    public int length() {
      return items.length;
    }

    public Object item(int index) {
      return items[index];
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof PyTupleN pyTupleN && Arrays.equals(items, pyTupleN.items);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(items);
    }

    @Override
    public String toString() {
      return Arrays.stream(items)
          .map(String::valueOf)
          .collect(Collectors.joining(", ", "PyTupleN(", ")"));
    }
  }
}