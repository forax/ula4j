package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.PyTuple.PyTuple3;
import com.github.forax.ula4j.rt.PyTuple.PyTuple4;
import com.github.forax.ula4j.rt.PyTuple.PyTupleN;
import com.github.forax.ula4j.rt.error.PyIndexError;

import java.util.Arrays;

/// Python tuple operations for every tuple shape.
///
/// The `switch` selectors are cast to `Object`: javac puts the static type of the selector in the
/// `typeSwitch` descriptor, and the Specializer only rewrites the `(Object, int) -> int` form.
public final class TupleIntrinsics {
  private TupleIntrinsics() {
    throw new AssertionError();
  }

  private static final PyTuple TUPLE_EMPTY = new PyTupleN(new Object[0]);

  // -- construction (not intrinsics)

  /// Builds a tuple in its canonical shape: arity 1-4 use a fixed record, 0 and 5+ use PyTupleN.
  /// The array is not retained.
  public static PyTuple fromArray(Object... items) {
    return switch (items.length) {
      case 0 -> TUPLE_EMPTY;
      case 1 -> new PyTuple1(items[0]);
      case 2 -> new PyTuple2(items[0], items[1]);
      case 3 -> new PyTuple3(items[0], items[1], items[2]);
      case 4 -> new PyTuple4(items[0], items[1], items[2], items[3]);
      default -> new PyTupleN(items);  // copies its input
    };
  }

  /// Tuple extracted from `items[start..]`.
  public static PyTuple fromArrayRange(Object[] items, int start) {
    return switch (items.length - start) {
      case 0 -> TUPLE_EMPTY;
      case 1 -> new PyTuple1(items[start]);
      case 2 -> new PyTuple2(items[start], items[start + 1]);
      case 3 -> new PyTuple3(items[start], items[start + 1], items[start + 2]);
      case 4 -> new PyTuple4(items[start], items[start + 1], items[start + 2], items[start + 3]);
      default -> new PyTupleN(start == 0 ? items : Arrays.copyOfRange(items, start, items.length));
    };
  }


  // -- len(t), t[i]

  /// Python `len(tuple)`.
  @Intrinsic
  public static long $size$(PyTuple tuple) {
    switch (tuple) {
      case PyTuple1 _ -> { return 1L; }
      case PyTuple2 _ -> { return 2L; }
      case PyTuple3 _ -> { return 3L; }
      case PyTuple4 _ -> { return 4L; }
      case PyTupleN t -> { return t.itemCount(); }
    }
  }

  /// Python `tuple[index]` for an index that is already a `long`: negative indexes count from the end.
  /// Converting an arbitrary Python object (bool, BigInteger, slice) to an index is the job of the
  /// generic `getItem` intrinsic.
  @Intrinsic
  public static Object $get$(PyTuple tuple, long index) {
    switch (tuple) {
      case PyTuple1 t -> {
        checkedIndex(index, 1);
        return t.item0();
      }
      case PyTuple2 t -> {
        var i = checkedIndex(index, 2);
        return i == 0 ? t.item0() : t.item1();
      }
      case PyTuple3 t -> {
        var i = checkedIndex(index, 3);
        return i == 0 ? t.item0() : i == 1 ? t.item1() : t.item2();
      }
      case PyTuple4 t -> {
        var i = checkedIndex(index, 4);
        return i == 0 ? t.item0() : i == 1 ? t.item1() : i == 2 ? t.item2() : t.item3();
      }
      case PyTupleN t -> {
        var i = checkedIndex(index, t.itemCount());
        return t.itemAt(i);
      }
    }
  }


  // -- a + b, t * n

  /// Python `left + right` for two tuples (the type check for other operands belongs to `add`).
  @Intrinsic
  public static PyTuple $concat$(PyTuple left, PyTuple right) {
    var leftSize = $size$(left);
    var rightSize = $size$(right);
    if (rightSize == 0) {
      return left;
    }
    if (leftSize == 0) {
      return right;
    }
    var total = leftSize + rightSize;  // both fit in an int, so no long overflow
    if (total > Integer.MAX_VALUE) {
      throw tooLarge();
    }
    var items = new Object[(int) total];
    copyInto(left, items, 0);
    copyInto(right, items, (int) leftSize);
    return fromArray(items);
  }

  /// Python `tuple * count` (also `count * tuple`) for a count that is already a `long`.
  @Intrinsic
  public static PyTuple $repeat$(PyTuple tuple, long count) {
    var size = $size$(tuple);
    if (count <= 0 || size == 0) {
      return TUPLE_EMPTY;
    }
    if (count == 1) {
      return tuple;
    }
    if (size > Integer.MAX_VALUE / count) {
      throw tooLarge();
    }
    var n = (int) size;
    var c = (int) count;
    var items = new Object[n * c];
    copyInto(tuple, items, 0);
    for (var k = 1; k < c; k++) {
      System.arraycopy(items, 0, items, k * n, n);
    }
    return fromArray(items);
  }


  @Intrinsic
  public static boolean $in$(PyTuple tuple, Object item) {
    switch (item) {
      case Double d -> {
        if (Double.isNaN(d)) {
          return $tupleContainsNaN$(tuple);
        }
        return $tupleContains$(tuple, d);
      }
      default -> { return $tupleContains$(tuple, item); }
    }
  }

  @Intrinsic
  public static boolean $tupleContains$(PyTuple tuple, Object item) {
    switch (tuple) {
      case PyTuple1 t -> {
        return ComparisonIntrinsics.$eq$(t.item0(), item);
      }
      case PyTuple2 t -> {
        return ComparisonIntrinsics.$eq$(t.item0(), item) ||
            ComparisonIntrinsics.$eq$(t.item1(), item);
      }
      case PyTuple3 t -> {
        return ComparisonIntrinsics.$eq$(t.item0(), item) ||
            ComparisonIntrinsics.$eq$(t.item1(), item) ||
            ComparisonIntrinsics.$eq$(t.item2(), item);
      }
      case PyTuple4 t -> {
        return ComparisonIntrinsics.$eq$(t.item0(), item) ||
            ComparisonIntrinsics.$eq$(t.item1(), item) ||
            ComparisonIntrinsics.$eq$(t.item2(), item) ||
            ComparisonIntrinsics.$eq$(t.item3(), item);
      }
      case PyTupleN t -> {
        return tupleNContains(t, item);
      }
    }
  }

  private static boolean tupleNContains(PyTupleN tuple, Object item) {
    for (var i = 0; i < tuple.itemCount(); i++) {
      var value = tuple.itemAt(i);
      if (ComparisonIntrinsics.$eq$(value, item)) { // should be $eq$(item, value) ?
        return true;
      }
    }
    return false;
  }

  private static boolean $tupleContainsNaN$(PyTuple tuple) {
    switch (tuple) {
      case PyTuple1 t -> {
        return isNaN(t.item0());
      }
      case PyTuple2 t -> {
        return isNaN(t.item0()) || isNaN(t.item1());
      }
      case PyTuple3 t -> {
        return isNaN(t.item0()) || isNaN(t.item1()) || isNaN(t.item2());
      }
      case PyTuple4 t -> {
        return isNaN(t.item0()) || isNaN(t.item1()) || isNaN(t.item2()) || isNaN(t.item3());
      }
      case PyTupleN t -> {
        return tupleNContainsNaN(t);
      }
    }
  }

  private static boolean tupleNContainsNaN(PyTupleN tuple) {
    for (var i = 0; i < tuple.itemCount(); i++) {
      var value = tuple.itemAt(i);
      if (isNaN(value)) { // should be $eq$(item, value) ?
        return true;
      }
    }
    return false;
  }

  private static boolean isNaN(Object item) {
    return item instanceof Double d && Double.isNaN(d);
  }


  // -- helpers (not intrinsics: they call the generic intrinsics directly)

  /// Normalizes a possibly negative index, or raises IndexError.
  private static int checkedIndex(long index, int size) {
    var normalized = index < 0 ? index + size : index;  // no overflow: index < 0 and size >= 0
    if (normalized < 0 || normalized >= size) {
      throw new PyIndexError("tuple index out of range");
    }
    return (int) normalized;
  }

  private static void copyInto(PyTuple tuple, Object[] destination, int offset) {
    switch (tuple) {
      case PyTuple1 t -> destination[offset] = t.item0();
      case PyTuple2 t -> {
        destination[offset] = t.item0();
        destination[offset + 1] = t.item1();
      }
      case PyTuple3 t -> {
        destination[offset] = t.item0();
        destination[offset + 1] = t.item1();
        destination[offset + 2] = t.item2();
      }
      case PyTuple4 t -> {
        destination[offset] = t.item0();
        destination[offset + 1] = t.item1();
        destination[offset + 2] = t.item2();
        destination[offset + 3] = t.item3();
      }
      case PyTupleN t -> {
        for (var i = 0; i < t.itemCount(); i++) {
          destination[offset + i] = t.itemAt(i);
        }
      }
    }
  }

  private static OutOfMemoryError tooLarge() {
    // CPython raises MemoryError here
    return new OutOfMemoryError("tuple larger than the maximum Java array size");
  }
}