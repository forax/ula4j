package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.intrinsic.TupleIntrinsics;

/// Internal iterators used by `for`; they are never visible to Python code.
public sealed interface PyIterator {
  final class TupleIterator implements PyIterator {
    private final PyTuple tuple;
    private final long size;
    private long index;

    public TupleIterator(PyTuple tuple) {
      this.tuple = tuple;
      this.size = TupleIntrinsics.$size$(tuple);
      super();
    }

    public Object next() {
      return index < size ? TupleIntrinsics.$get$(tuple, index++) : null;
    }
  }

  /// Index-based iterator: reads the list's current size on every next() call.
  final class ListIterator implements PyIterator {
    private final PyList<?> list;
    private int index;

    public ListIterator(PyList<?> list) {
      this.list = list;
      super();
    }

    public Object next() {
      if (index >= list.size()) {  // may change at runtime
        return null;
      }
      return list.get(index++);
    }
  }

  final class StringIterator implements PyIterator {
    private final String string;
    private int index;

    public StringIterator(String string) {
      this.string = string;
      super();
    }

    public Object next() {
      if (index >= string.length()) {
        return null;
      }
      var end = index + Character.charCount(string.codePointAt(index));
      var item = string.substring(index, end);
      index = end;
      return item;
    }
  }
}