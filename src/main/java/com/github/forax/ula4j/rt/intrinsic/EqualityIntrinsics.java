package com.github.forax.ula4j.rt.intrinsic;

//import com.github.forax.ula4j.rt.PyDict;
//import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyTypeError;

import java.math.BigDecimal;
import java.math.BigInteger;

/// Python-compatible `==` for the runtime's canonical value representations.
///
/// Never relies on Java `equals()` / `hashCode()` of the runtime values. `==` never raises for
/// supported types: operands of unrelated types are simply unequal.
public final class EqualityIntrinsics {
  private EqualityIntrinsics() {
    throw new AssertionError("no instances");
  }

  /// Python `left == right`.
  ///
  /// There is deliberately no identity shortcut here: `nan == nan` is False. The shortcut that
  /// containers apply to their elements lives in {@link #sameOrEqual}.
  @Intrinsic
  public static boolean $eq$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return l.longValue() == r.longValue(); }
          case BigInteger r -> { return r.equals(BigInteger.valueOf(l)); }
          case Boolean r -> { return l.longValue() == (r ? 1L : 0L); }
          case Double r -> { return longEqualsDouble(l, r); }
          default -> { return false; }
        }
      }
      case BigInteger l -> {
        switch (right) {
          case Long r -> { return l.equals(BigInteger.valueOf(r)); }
          case BigInteger r -> { return l.equals(r); }
          case Boolean r -> { return l.equals(r ? BigInteger.ONE : BigInteger.ZERO); }
          case Double r -> { return bigEqualsDouble(l, r); }
          default -> { return false; }
        }
      }
      case Boolean l -> {
        switch (right) {
          case Long r -> { return (l ? 1L : 0L) == r.longValue(); }
          case BigInteger r -> { return r.equals(l ? BigInteger.ONE : BigInteger.ZERO); }
          case Boolean r -> { return l.booleanValue() == r.booleanValue(); }
          case Double r -> { return (l ? 1.0d : 0.0d) == r.doubleValue(); }
          default -> { return false; }
        }
      }
      case Double l -> {
        switch (right) {
          case Long r -> { return longEqualsDouble(r, l); }
          case BigInteger r -> { return bigEqualsDouble(r, l); }
          case Boolean r -> { return l.doubleValue() == (r ? 1.0d : 0.0d); }
          // primitive comparison: NaN != NaN and -0.0 == 0.0, as in Python
          case Double r -> { return l.doubleValue() == r.doubleValue(); }
          default -> { return false; }
        }
      }
      case String l -> {
        switch (right) {
          case String r -> { return l.equals(r); }  // same UTF-16 units <=> same code points
          default -> { return false; }
        }
      }
      case PyNone _ -> { return right == PyNone.INSTANCE; }
      case PyTuple l -> {
        switch (right) {
          case PyTuple r -> { return tupleEquals(l, r); }
          default -> { return false; }
        }
      }
      //case PyList l -> {
      //  switch (right) {
      //    case PyList r -> { return listEquals(l, r); }
      //    default -> { return false; }
      //  }
      //}
      //case PyDict l -> {
      //  switch (right) {
      //    case PyDict r -> { return dictEquals(l, r); }
      //    default -> { return false; }
      //  }
      //}
      //case PyFunction _, PyType _ -> { return left == right; }  // identity
      default -> throw new PyTypeError(
          "not a canonical runtime value: " + left.getClass().getName());
    }
  }

  /// Used for the elements of containers, and by membership / lookup algorithms.
  /// Equivalent to CPython's `PyObject_RichCompareBool(left, right, Py_EQ)`: identity first, then `==`.
  ///
  /// Not an intrinsic: it calls the generic `$eq$`, so nested comparisons are not specialized
  /// per call site.
  public static boolean sameOrEqual(Object left, Object right) {
    return left == right || $eq$(left, right);
  }

  // ---------------------------------------------------------------------------------------------

  /// Exact int/float comparison: the long is never converted to a double.
  private static boolean longEqualsDouble(long l, double d) {
    // NaN and infinities fail the range test. Inside [-2**63, 2**63), an integral double
    // converts to long without any rounding.
    return d >= -0x1p63 && d < 0x1p63 && d == Math.rint(d) && (long) d == l;
  }

  private static boolean bigEqualsDouble(BigInteger b, double d) {
    if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d)) {
      return false;
    }
    return new BigDecimal(d).toBigIntegerExact().equals(b);  // new BigDecimal(double) is exact
  }

  /// Same length, then pairwise `sameOrEqual`. Works across every tuple shape.
  private static boolean tupleEquals(PyTuple left, PyTuple right) {
    var size = TupleIntrinsics.$size$(left);
    if (size != TupleIntrinsics.$size$(right)) {
      return false;
    }
    for (var i = 0; i < size; i++) {
      if (!sameOrEqual(TupleIntrinsics.$get$(left, (long) i), TupleIntrinsics.$get$(right, (long) i))) {
        return false;
      }
    }
    return true;
  }

  // Same shape as tupleEquals, index-based like CPython. No exception for the length mismatch:
  // for == it is a plain `false`.
  //
  //private static boolean listEquals(PyList left, PyList right) {
  //  var size = left.size();
  //  if (size != right.size()) {
  //    return false;
  //  }
  //  for (var i = 0; i < size; i++) {
  //    if (!sameOrEqual(left.get(i), right.get(i))) {
  //      return false;
  //    }
  //  }
  //  return true;
  //}

  // dictEquals: same size, then for every (key, value) of left, look the key up in right using
  // PyDict's own hash/eq lookup (identity first, then Python ==). A missing key means false,
  // otherwise values are compared with sameOrEqual. Insertion order is irrelevant.
}