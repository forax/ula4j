package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyDict;
import com.github.forax.ula4j.rt.PyFunction;
import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyOverflowError;
import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;

import java.math.BigInteger;

/// Non-intrinsic helpers shared by the intrinsic classes of this package.
/// Nothing here is specialized: these methods are plain static calls.
public final class IntrinsicSupport {
  private IntrinsicSupport() {
    throw new AssertionError();
  }

  /// int and bool (a bool is an int in every numeric operation).
  static boolean isInt(Object value) {
    return value instanceof Long || value instanceof BigInteger || value instanceof Boolean;
  }

  static boolean isNumber(Object value) {
    return isInt(value) || value instanceof Double;
  }

  /// Valid for a Long or a Boolean only.
  static long toLong(Object value) {
    return switch (value) {
      case Long l -> l;
      case Boolean b -> b ? 1L : 0L;
      default -> throw new AssertionError("expected Long/Boolean " + value);
    };
  }

  static BigInteger toBigInteger(Object value) {
    return switch (value) {
      case Long l -> BigInteger.valueOf(l);
      case BigInteger b -> b;
      case Boolean b -> b ? BigInteger.ONE : BigInteger.ZERO;
      default -> throw new AssertionError("not an int: " + value);
    };
  }

  /// Python's int-to-float conversion (an int too large for a double raises OverflowError).
  static double toDouble(Object value) {
    return switch (value) {
      case Double d -> d;
      case Long l -> l.doubleValue();
      case Boolean b -> b ? 1.0 : 0.0;
      case BigInteger b -> {
        var d = b.doubleValue();  // correctly rounded, infinite if out of range
        if (Double.isInfinite(d)) {
          throw new PyOverflowError("int too large to convert to float");
        }
        yield d;
      }
      default -> throw new AssertionError("not a number: " + value);
    };
  }

  /// Keeps the invariant that a BigInteger never holds a value that fits in a long.
  static Object normalize(BigInteger value) {
    if (value.bitLength() < 64) {
      return value.longValue();
    }
    return value;
  }

  /// The name that type(value).__name__ would report.
  public static String typeName(Object value) {
    return switch (value) {
      case Long _, BigInteger _ -> "int";
      case Boolean _ -> "bool";
      case Double _ -> "float";
      case String _ -> "str";
      case PyNone _ -> "NoneType";
      case PyTuple _ -> "tuple";
      case PyList<?> _ -> "list";
      case PyDict<?,?> _ -> "dict";
      case PyFunction _ -> "function";
      default -> throw new UnimplementedFeatureError("type name of " + value.getClass().getName());
    };
  }

  static PyTypeError unsupportedOperands(String symbol, Object left, Object right) {
    return new PyTypeError("unsupported operand type(s) for " + symbol + ": '"
        + typeName(left) + "' and '" + typeName(right) + "'");
  }

  static PyTypeError badUnaryOperand(String symbol, Object operand) {
    return new PyTypeError("bad operand type for unary " + symbol + ": '" + typeName(operand) + "'");
  }
}