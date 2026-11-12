package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyDict;
import com.github.forax.ula4j.rt.PyFunction;
import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;

import java.math.BigDecimal;
import java.math.BigInteger;

import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.isNumber;
import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.toBigInteger;
import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.toLong;
import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.typeName;

/// Python equality and ordering comparisons.
///
/// Numeric comparisons are exact (an int is never converted to double to be compared). Container
/// comparisons use a reference-equality shortcut on elements, as CPython does. Top-level NaN
/// equality still returns false, even when the same boxed Double object is compared with itself.
public final class ComparisonIntrinsics {
  private ComparisonIntrinsics() {
    throw new AssertionError();
  }

  private static final int LESS = -1;
  private static final int EQUAL = 0;
  private static final int GREATER = 1;
  private static final int UNORDERED = 2;

  private static final int LT = 0;
  private static final int LE = 1;
  private static final int GT = 2;
  private static final int GE = 3;

  // --- equality

  @Intrinsic
  public static boolean $eq$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return l.longValue() == r.longValue(); }
          default -> { return numericEquals(left, right); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return l.doubleValue() == r.doubleValue(); }
          default -> { return numericEquals(left, right); }
        }
      }
      case BigInteger _,
           Boolean _ -> { return numericEquals(left, right); }
      case String l -> {
        switch (right) {
          case String r -> { return l.equals(r); }
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
      case PyList<?> l -> {
        switch (right) {
          case PyList<?> r -> { return listEquals(l, r); }
          default -> { return false; }
        }
      }
      case PyDict<?,?> l -> {
        switch (right) {
          case PyDict<?,?> r -> { return DictIntrinsics.equals(l, r); }
          default -> { return false; }
        }
      }
      case PyFunction l -> { return right == l; }  // Python functions compare by identity in this runtime.
      // TODO PyRange, PySlice, PyType
      default -> throw unimplemented("==", left);
    }
  }

  @Intrinsic
  public static boolean $ne$(Object left, Object right) {
    return !$eq$(left, right);
  }

  @Intrinsic
  public static boolean $sameOrEq$(Object left, Object right) {
    return left == right || $eq$(left, right);
  }

  // -- ordering

  @Intrinsic
  public static boolean $lt$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return l < r; }
          default -> { return orderGeneric(left, right, LT); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return l < r; }
          default -> { return orderGeneric(left, right, LT); }
        }
      }
      case String l -> {
        switch (right) {
          case String r -> { return compareCodePoints(l, r) < 0; }
          default -> { return orderGeneric(left, right, LT); }
        }
      }
      default -> { return orderGeneric(left, right, LT); }
    }
  }

  @Intrinsic
  public static boolean $le$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return l <= r; }
          default -> { return orderGeneric(left, right, LE); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return l <= r; }
          default -> { return orderGeneric(left, right, LE); }
        }
      }
      case String l -> {
        switch (right) {
          case String r -> { return compareCodePoints(l, r) <= 0; }
          default -> { return orderGeneric(left, right, LE); }
        }
      }
      default -> { return orderGeneric(left, right, LE); }
    }
  }

  @Intrinsic
  public static boolean $gt$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return l > r; }
          default -> { return orderGeneric(left, right, GT); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return l > r; }
          default -> { return orderGeneric(left, right, GT); }
        }
      }
      case String l -> {
        switch (right) {
          case String r -> { return compareCodePoints(l, r) > 0; }
          default -> { return orderGeneric(left, right, GT); }
        }
      }
      default -> { return orderGeneric(left, right, GT); }
    }
  }

  @Intrinsic
  public static boolean $ge$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return l >= r; }
          default -> { return orderGeneric(left, right, GE); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return l >= r; }
          default -> { return orderGeneric(left, right, GE); }
        }
      }
      case String l -> {
        switch (right) {
          case String r -> { return compareCodePoints(l, r) >= 0; }
          default -> { return orderGeneric(left, right, GE); }
        }
      }
      default -> { return orderGeneric(left, right, GE); }
    }
  }

  // ------------------------------------------------------------------ generic paths

  private static boolean numericEquals(Object left, Object right) {
    return isNumber(right) && compareNumbers(left, right) == EQUAL;
  }

  private static boolean listEquals(PyList<?> left, PyList<?> right) {
    var size = left.size();
    if (size != right.size()) {
      return false;
    }
    for (var i = 0; i < size; i++) {
      Object a = left.get(i);
      Object b = right.get(i);
      if (!$sameOrEq$(a, b)) {
        return false;
      }
    }
    return true;
  }

  private static boolean orderGeneric(Object left, Object right, int op) {
    if (isNumber(left) && isNumber(right)) {
      return holds(op, compareNumbers(left, right));
    }
    if (left instanceof String l && right instanceof String r) {
      return holds(op, Integer.signum(compareCodePoints(l, r)));
    }
    if (left instanceof PyTuple l && right instanceof PyTuple r) {
      return tupleOrder(l, r, op);
    }
    if (left instanceof PyList<?> l && right instanceof PyList<?> r) {
      return listOrder(l, r, op);
    }
    throw new PyTypeError("'" + symbol(op) + "' not supported between instances of '"
        + typeName(left) + "' and '" + typeName(right) + "'");
  }

  private static boolean holds(int op, int comparison) {
    return switch (op) {
      case LT -> comparison == LESS;
      case LE -> comparison == LESS || comparison == EQUAL;
      case GT -> comparison == GREATER;
      case GE -> comparison == GREATER || comparison == EQUAL;
      default -> throw new AssertionError(op);
    };
  }

  private static String symbol(int op) {
    return switch (op) {
      case LT -> "<";
      case LE -> "<=";
      case GT -> ">";
      case GE -> ">=";
      default -> throw new AssertionError(op);
    };
  }

  private static UnimplementedFeatureError unimplemented(String operator, Object operand) {
    return new UnimplementedFeatureError("'" + operator + "' on " + operand.getClass().getName());
  }

  // ------------------------------------------------------------------ tuples

  private static boolean tupleEquals(PyTuple left, PyTuple right) {
    var size = TupleIntrinsics.$size$(left);
    if (size != TupleIntrinsics.$size$(right)) {
      return false;
    }
    for (var i = 0; i < size; i++) {
      Object a = TupleIntrinsics.$get$(left, (long) i);
      Object b = TupleIntrinsics.$get$(right, (long) i);
      if (!$sameOrEq$(a, b)) {  // reference shortcut
        return false;
      }
    }
    return true;
  }

  /// Lexicographic: find the first pair of items that differ, then apply the operator to that pair;
  /// if there is none, compare the lengths.
  private static boolean tupleOrder(PyTuple left, PyTuple right, int op) {
    var leftSize = TupleIntrinsics.$size$(left);
    var rightSize = TupleIntrinsics.$size$(right);
    var common = Math.min(leftSize, rightSize);
    for (var i = 0; i < common; i++) {
      Object a = TupleIntrinsics.$get$(left, (long) i);
      Object b = TupleIntrinsics.$get$(right, (long) i);
      if (!$sameOrEq$(a, b)) {
        return orderItems(a, b, op);
      }
    }
    return holds(op, Integer.signum(Long.compare(leftSize, rightSize)));
  }

  /// Lexicographic list ordering, using Python's identity-first element comparison.
  private static boolean listOrder(PyList<?> left, PyList<?> right, int op) {
    var leftSize = left.size();
    var rightSize = right.size();
    var common = Math.min(leftSize, rightSize);
    for (var i = 0; i < common; i++) {
      Object a = left.get(i);
      Object b = right.get(i);
      if (!$sameOrEq$(a, b)) {
        return orderItems(a, b, op);
      }
    }
    return holds(op, Integer.signum(Integer.compare(leftSize, rightSize)));
  }

  private static boolean orderItems(Object left, Object right, int op) {
    return switch (op) {
      case LT -> $lt$(left, right);
      case LE -> $le$(left, right);
      case GT -> $gt$(left, right);
      case GE -> $ge$(left, right);
      default -> throw new AssertionError(op);
    };
  }

  // ------------------------------------------------------------------ strings

  /// Orders by Unicode code point, not UTF-16 code unit.
  private static int compareCodePoints(String left, String right) {
    var leftIndex = 0;
    var rightIndex = 0;
    while (leftIndex < left.length() && rightIndex < right.length()) {
      var leftCodePoint = left.codePointAt(leftIndex);
      var rightCodePoint = right.codePointAt(rightIndex);
      var comparison = Integer.compare(leftCodePoint, rightCodePoint);
      if (comparison != 0) {
        return comparison;
      }
      leftIndex += Character.charCount(leftCodePoint);
      rightIndex += Character.charCount(rightCodePoint);
    }
    return Boolean.compare(leftIndex < left.length(), rightIndex < right.length());
  }

  // ------------------------------------------------------------------ numbers

  /// Exact three-way comparison of two numbers (int, bool or float).
  private static int compareNumbers(Object left, Object right) {
    if (left instanceof Double l) {
      if (right instanceof Double r) {
        return compareDoubles(l, r);
      }
      return reverse(compareIntWithDouble(right, l));
    }
    if (right instanceof Double r) {
      return compareIntWithDouble(left, r);
    }
    return compareInts(left, right);
  }

  private static int reverse(int comparison) {
    return comparison == UNORDERED ? UNORDERED : -comparison;
  }

  private static int compareDoubles(double a, double b) {
    if (a < b) { return LESS; }
    if (a > b) { return GREATER; }
    if (a == b) { return EQUAL; }
    return UNORDERED;
  }

  private static int compareInts(Object left, Object right) {
    if (left instanceof BigInteger || right instanceof BigInteger) {
      return toBigInteger(left).compareTo(toBigInteger(right));
    }
    return Long.compare(toLong(left), toLong(right));
  }

  private static int compareIntWithDouble(Object integer, double d) {
    if (Double.isNaN(d)) {
      return UNORDERED;
    }
    if (integer instanceof BigInteger big) {
      if (Double.isInfinite(d)) {
        return d > 0 ? LESS : GREATER;
      }
      return Integer.signum(new BigDecimal(big).compareTo(new BigDecimal(d)));
    }
    return compareLongWithDouble(toLong(integer), d);
  }

  private static int compareLongWithDouble(long l, double d) {
    if (Double.isInfinite(d)) {
      return d > 0 ? LESS : GREATER;
    }
    if (d >= 0x1p63) {
      return LESS;
    }
    if (d < -0x1p63) {
      return GREATER;
    }
    var truncated = (long) d;
    if (l != truncated) {
      return Long.compare(l, truncated);
    }
    var fraction = d - truncated;
    if (fraction > 0) { return LESS; }
    if (fraction < 0) { return GREATER; }
    return EQUAL;
  }
}