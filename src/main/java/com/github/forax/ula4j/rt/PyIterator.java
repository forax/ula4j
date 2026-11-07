package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.intrinsic.TupleIntrinsics;

/// Internal iterators used by `for`; they are never visible to Python code.
public sealed interface PyIterator {
  /// The next item, or Java `null` once exhausted (`null` is never a Python value).
  Object next();

  final class TupleIterator implements PyIterator {
    private final PyTuple tuple;
    private final long size;
    private long index;

    public TupleIterator(PyTuple tuple) {
      this.tuple = tuple;
      this.size = TupleIntrinsics.$size$(tuple);
    }

    @Override
    public Object next() {
      return index < size ? TupleIntrinsics.$get$(tuple, index++) : null;
    }
  }

  final class StringIterator implements PyIterator {
    private final String string;
    private int index;

    public StringIterator(String string) {
      this.string = string;
    }

    @Override
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