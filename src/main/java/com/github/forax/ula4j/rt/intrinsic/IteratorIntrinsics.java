package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyIterator;
import com.github.forax.ula4j.rt.PyIterator.StringIterator;
import com.github.forax.ula4j.rt.PyIterator.TupleIterator;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyTypeError;

import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.typeName;

/// The protocol of `for`: `$iterator$` once, then `$next$` until it returns Java `null`.
public final class IteratorIntrinsics {
  private IteratorIntrinsics() { throw new AssertionError(); }

  @Intrinsic
  public static Object $iterator$(Object iterable) {
    switch (iterable) {
      case PyTuple t -> { return new TupleIterator(t); }
      case String s -> { return new StringIterator(s); }
      // TODO PyList, PyDict, PyRange
      default -> throw new PyTypeError("'" + typeName(iterable) + "' object is not iterable");
    }
  }

  @Intrinsic
  public static Object $next$(Object iterator) {
    return ((PyIterator) iterator).next();
  }
}