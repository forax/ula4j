package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyTypeError;

import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.typeName;

/// `item in container` (the operands keep their source order).
public final class ContainmentIntrinsics {
  private ContainmentIntrinsics() { throw new AssertionError(); }

  @Intrinsic
  public static boolean $in$(Object item, Object container) {
    switch (container) {
      case String s -> {
        switch (item) {
          case String t -> { return s.contains(t); }
          default -> throw new PyTypeError(
              "'in <string>' requires string as left operand, not " + typeName(item));
        }
      }
      case PyTuple t -> { return tupleContains(t, item); }
      // TODO PyList, PyDict, PyRange
      default -> throw new PyTypeError("argument of type '" + typeName(container) + "' is not iterable");
    }
  }

  private static boolean tupleContains(PyTuple tuple, Object item) {
    var size = TupleIntrinsics.$size$(tuple);
    for (var i = 0L; i < size; i++) {
      if (EqualityIntrinsics.sameOrEqual(TupleIntrinsics.$get$(tuple, i), item)) {
        return true;
      }
    }
    return false;
  }
}