package com.github.forax.ula4j.rt;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public sealed interface PyTuple {

  default int size() {
    return switch (this) {
      case PyTuple1 _ -> 1;
      case PyTuple2 _ -> 2;
      case PyTuple3 _ -> 3;
      case PyTuple4 _ -> 4;
      case PyTupleN tuple -> tuple.itemCount();
    };
  }

  default Object get(int index) {
    return switch (this) {
      case PyTuple1 t -> {
        Objects.checkIndex(index, 1);
        yield t.item0();
      }
      case PyTuple2 t -> {
        Objects.checkIndex(index, 2);
        yield index == 0 ? t.item0() : t.item1();
      }
      case PyTuple3 t -> {
        Objects.checkIndex(index, 3);
        yield index == 0 ? t.item0() : index == 1 ? t.item1() : t.item2();
      }
      case PyTuple4 t -> {
        Objects.checkIndex(index, 4);
        yield index == 0 ? t.item0() : index == 1 ? t.item1() : index == 2 ? t.item2() : t.item3();
      }
      case PyTupleN t -> {
        Objects.checkIndex(index, t.itemCount());
        yield t.itemAt(index);
      }
    };
  }

  default List<Object> asJavaList() {
    final class JavaList extends AbstractList<Object> implements RandomAccess {
      @Override
      public int size() {
        return PyTuple.this.size();
      }

      @Override
      public Object get(int index) {
        return PyTuple.this.get(index);
      }
    }
    return new JavaList();
  }

  record PyTuple1(Object item0) implements PyTuple {
    public PyTuple1 {
      requireNonNull(item0);
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) {
        return false;
      }
      return switch (other) {
        case PyTuple1 tuple -> item0.equals(tuple.item0);
        case PyTupleN tuple when tuple.itemCount() == 1-> item0.equals(tuple.itemAt(0));
        default -> false;
      };
    }

    public int hashCode() {
      return Objects.hash(item0);
    }

    @Override
    public String toString() {
      return "PyTuple1(" + item0 + ")";
    }
  }

  record PyTuple2(Object item0, Object item1) implements PyTuple {
    public PyTuple2 {
      requireNonNull(item0);
      requireNonNull(item1);
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) {
        return false;
      }
      return switch (other) {
        case PyTuple2 tuple -> item0.equals(tuple.item0) && item1.equals(tuple.item1);
        case PyTupleN tuple when tuple.itemCount() == 2->
            item0.equals(tuple.itemAt(0)) && item1.equals(tuple.itemAt(1));
        default -> false;
      };
    }

    public int hashCode() {
      return Objects.hash(item0, item1);
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
    public boolean equals(Object other) {
      if (other == null) {
        return false;
      }
      return switch (other) {
        case PyTuple3 tuple -> item0.equals(tuple.item0) && item1.equals(tuple.item1) && item2.equals(tuple.item2);
        case PyTupleN tuple when tuple.itemCount() == 3->
            item0.equals(tuple.itemAt(0)) && item1.equals(tuple.itemAt(1)) && item2.equals(tuple.itemAt(2));
        default -> false;
      };
    }

    public int hashCode() {
      return Objects.hash(item0, item1, item2);
    }

    @Override
    public String toString() {
      return "PyTuple3(" + item0 + ", " + item1 + ", " + item2 + ")";
    }
  }

  record PyTuple4(Object item0, Object item1, Object item2, Object item3) implements PyTuple {
    public PyTuple4 {
      requireNonNull(item0);
      requireNonNull(item1);
      requireNonNull(item2);
      requireNonNull(item3);
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) {
        return false;
      }
      return switch (other) {
        case PyTuple4 tuple -> item0.equals(tuple.item0) && item1.equals(tuple.item1) && item2.equals(tuple.item2);
        case PyTupleN tuple when tuple.itemCount() == 4->
            item0.equals(tuple.itemAt(0)) && item1.equals(tuple.itemAt(1)) && item2.equals(tuple.itemAt(2)) && item3.equals(tuple.itemAt(3));
        default -> false;
      };
    }

    public int hashCode() {
      return Objects.hash(item0, item1, item2, item3);
    }

    @Override
    public String toString() {
      return "PyTuple4(" +  item0 + ", " + item1 + ", " + item2 + ", " + item3 + ")";
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
      throw new AssertionError("use itemAt(index) instead");
    }

    public int itemCount() {
      return items.length;
    }

    public Object itemAt(int index) {
      return items[index];
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) {
        return false;
      }
      return switch (other) {
        case PyTuple1 tuple when items.length == 1-> items[0].equals(tuple.item0());
        case PyTuple2 tuple when items.length == 2-> items[0].equals(tuple.item0()) && items[1].equals(tuple.item1());
        case PyTuple3 tuple when items.length == 3-> items[0].equals(tuple.item0()) && items[1].equals(tuple.item1()) && items[2].equals(tuple.item2());
        case PyTuple4 tuple when items.length == 4-> items[0].equals(tuple.item0()) && items[1].equals(tuple.item1()) && items[2].equals(tuple.item2()) && items[3].equals(tuple.item3());
        case PyTupleN tuple -> Arrays.equals(items, tuple.items);
        default -> false;
      };
    }

    @Override
    public int hashCode() {
      return Objects.hash(items);
    }

    @Override
    public String toString() {
      return Arrays.stream(items)
          .map(String::valueOf)
          .collect(Collectors.joining(", ", "PyTupleN(", ")"));
    }
  }
}