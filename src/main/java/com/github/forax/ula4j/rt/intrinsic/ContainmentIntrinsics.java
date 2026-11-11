package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyDict;
import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyTypeError;

import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.typeName;

/// Implements {@code item in container}. Operands keep their source order.
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
      case PyTuple tuple -> { return tupleContains(tuple, item); }
      case PyList<?> list -> { return listContains(list, item); }
      case PyDict dict -> { return DictIntrinsics.containsKey(dict, item); }
      default -> throw new PyTypeError("argument of type '" + typeName(container) + "' is not iterable");
    }
  }

  private static boolean listContains(PyList<?> list, Object item) {
    var index = 0;
    // Do not snapshot size: the list may grow while membership is being evaluated.
    while (index < list.size()) {
      if (ComparisonIntrinsics.$eq$(list.get(index), item)) {
        return true;
      }
      index++;
    }
    return false;
  }

  private static boolean tupleContains(PyTuple tuple, Object item) {
    var size = TupleIntrinsics.$size$(tuple);
    for (var i = 0L; i < size; i++) {
      if (ComparisonIntrinsics.$eq$(TupleIntrinsics.$get$(tuple, i), item)) {
        return true;
      }
    }
    return false;
  }
}