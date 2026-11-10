
package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyFunction;
import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyValueError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.IdentityHashMap;

public final class StringIntrinsics {
  private StringIntrinsics() {
    throw new AssertionError();
  }

  // CPython 3.14 default. sys.set_int_max_str_digits() is not implemented.
  private static final int MAX_INT_STR_DIGITS = 4_300;

  //  FIXME use ScopeValue instead
  // Shared by tuple/list repr so mixed recursive structures are handled correctly.
  private static final ThreadLocal<IdentityHashMap<Object, Boolean>> ACTIVE_REPR =
      ThreadLocal.withInitial(IdentityHashMap::new);

  @Intrinsic
  public static String $str$(Object value) {
    return switch (value) {
      case String s -> s;
      case Boolean b -> b ? "True" : "False";
      case Long l -> Long.toString(l);
      case BigInteger b -> intString(b);
      case Double d -> floatString(d);
      case PyNone _ -> "None";
      case PyTuple t -> tupleRepr(t);
      case PyList<?> list -> listRepr(list);
      case PyFunction f -> functionRepr(f);
      default -> throw unsupported(value);
    };
  }

  @Intrinsic
  public static String $repr$(Object value) {
    return switch (value) {
      case String s -> quoteString(s);
      case PyTuple t -> tupleRepr(t);
      case PyList<?> list -> listRepr(list);
      case Boolean b -> b ? "True" : "False";
      case Long l -> Long.toString(l);
      case BigInteger b -> intString(b);
      case Double d -> floatString(d);
      case PyNone _ -> "None";
      case PyFunction f -> functionRepr(f);
      default -> throw unsupported(value);
    };
  }

  // -- Scalar values

  private static String intString(BigInteger value) {
    var result = value.toString();
    var digits = result.charAt(0) == '-'
        ? result.length() - 1
        : result.length();

    if (digits > MAX_INT_STR_DIGITS) {
      throw new PyValueError(
          "Exceeds the limit (" + MAX_INT_STR_DIGITS
              + " digits) for integer string conversion; "
              + "use sys.set_int_max_str_digits() to increase the limit");
    }
    return result;
  }


  private static String floatString(double value) {
    if (Double.isNaN(value)) {
      return "nan";
    }
    if (Double.isInfinite(value)) {
      return value < 0.0 ? "-inf" : "inf";
    }

    var negative = Double.doubleToRawLongBits(value) < 0;
    var absolute = Math.abs(value);
    if (absolute == 0.0) {
      return negative ? "-0.0" : "0.0";
    }

    var decimal = shortestRoundTripDecimal(absolute);
    var digits = decimal.unscaledValue().abs().toString();
    var exponent = digits.length() - decimal.scale() - 1;

    // CPython uses scientific notation for exponent < -4 or >= 16.
    String body;
    if (exponent < -4 || exponent >= 16) {
      var mantissa = digits.length() == 1
          ? digits
          : digits.charAt(0) + "." + digits.substring(1);

      var exponentDigits = Integer.toString(Math.abs(exponent));
      if (exponentDigits.length() < 2) {
        exponentDigits = "0" + exponentDigits;
      }

      body = mantissa
          + "e"
          + (exponent >= 0 ? "+" : "-")
          + exponentDigits;
    } else {
      body = decimal.toPlainString();

      // A floating-point value must remain distinguishable from an int.
      if (decimal.scale() <= 0) {
        body += ".0";
      }
    }

    return negative ? "-" + body : body;
  }

  /// Finds a shortest decimal representation which round-trips to
  /// exactly the original binary64 value.
  /// 17 significant decimal digits are sufficient for every finite
  /// IEEE-754 binary64 value.
  private static BigDecimal shortestRoundTripDecimal(double value) {
    var exact = new BigDecimal(value);
    var exponent = exact.precision() - exact.scale() - 1;
    var expectedBits = Double.doubleToRawLongBits(value);

    for (var precision = 1; precision <= 17; precision++) {
      var scale = precision - 1 - exponent;
      var nearest = exact.movePointRight(scale)
          .setScale(0, RoundingMode.HALF_EVEN)
          .toBigIntegerExact();

      BigDecimal best = null;
      BigDecimal bestDistance = null;
      boolean bestEven = false;

      // Check the nearest decimal integer and its neighbours.
      for (var delta = -1; delta <= 1; delta++) {
        var coefficient = nearest.add(BigInteger.valueOf(delta));
        if (coefficient.signum() <= 0) {
          continue;
        }
        var candidate = new BigDecimal(coefficient, scale)
            .stripTrailingZeros();
        if (candidate.precision() > precision) {
          continue;
        }
        var parsed = Double.parseDouble(candidate.toString());
        if (Double.doubleToRawLongBits(parsed) != expectedBits) {
          continue;
        }
        var distance = candidate.subtract(exact).abs();
        var even = !coefficient.testBit(0);
        var comparison = bestDistance == null
            ? -1
            : distance.compareTo(bestDistance);
        if (best == null
            || comparison < 0
            || (comparison == 0 && even && !bestEven)) {
          best = candidate;
          bestDistance = distance;
          bestEven = even;
        }
      }
      if (best != null) {
        return best;
      }
    }
    throw new AssertionError("no shortest decimal for " + value);
  }


  private static String functionRepr(PyFunction function) {
    if (function.isBuiltin()) {
      return "<built-in function " + function.name() + ">";
    }
    return "<function " + function.name()
        + " at 0x"
        + Integer.toHexString(System.identityHashCode(function))
        + ">";
  }

  // ---------------------------------------------------------------------------------------------
  // Tuple representation

  private static String tupleRepr(PyTuple tuple) {
    if (!enterRepr(tuple)) {
      return "(...)";
    }
    try {
      var size = TupleIntrinsics.$size$(tuple);
      if (size == 0) {
        return "()";
      }
      var builder = new StringBuilder();
      builder.append('(');
      for (var i = 0; i < size; i++) {
        if (i != 0) {
          builder.append(", ");
        }
        builder.append($repr$(TupleIntrinsics.$get$(tuple, i)));
      }
      if (size == 1) {
        builder.append(',');
      }
      return builder.append(')').toString();
    } finally {
      leaveRepr(tuple);
    }
  }

  private static String listRepr(PyList<?> list) {
    if (!enterRepr(list)) {
      return "[...]";
    }
    try {
      var builder = new StringBuilder();
      builder.append('[');
      for (var i = 0; i < list.size(); i++) {
        if (i != 0) {
          builder.append(", ");
        }
        builder.append($repr$(list.get(i)));
      }
      return builder.append(']').toString();
    } finally {
      leaveRepr(list);
    }
  }

  private static boolean enterRepr(Object value) {
    var active = ACTIVE_REPR.get();
    if (active.containsKey(value)) {
      return false;
    }
    active.put(value, Boolean.TRUE);
    return true;
  }

  private static void leaveRepr(Object value) {
    var active = ACTIVE_REPR.get();
    active.remove(value);
    if (active.isEmpty()) {
      ACTIVE_REPR.remove();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // String repr

  private static String quoteString(String value) {
    var hasSingleQuote = value.indexOf('\'') >= 0;
    var hasDoubleQuote = value.indexOf('"') >= 0;

    // Choose the quote that minimizes escaping.
    var quote = hasSingleQuote && !hasDoubleQuote ? '"' : '\'';

    var builder = new StringBuilder(value.length() + 2);
    builder.append(quote);

    for (var i = 0; i < value.length();) {
      var cp = value.codePointAt(i);
      i += Character.charCount(cp);

      switch (cp) {
        case '\\' -> builder.append("\\\\");
        case '\t' -> builder.append("\\t");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        default -> {
          if (cp == quote) {
            builder.append('\\').append(quote);
          } else if (isPythonPrintable(cp)) {
            builder.appendCodePoint(cp);
          } else {
            appendUnicodeEscape(builder, cp);
          }
        }
      }
    }

    return builder.append(quote).toString();
  }

  private static boolean isPythonPrintable(int cp) {
    if (cp == 0x20) {
      return true;
    }

    // Character categories corresponding to Python's non-printable
    // Unicode general categories.
    //
    // For exact CPython 3.14 compatibility, this must use a table
    // generated from Unicode 16.0.0, rather than the JDK's Unicode
    // database.
    return switch (Character.getType(cp)) {
      case Character.CONTROL,
           Character.FORMAT,
           Character.SURROGATE,
           Character.PRIVATE_USE,
           Character.UNASSIGNED,
           Character.SPACE_SEPARATOR,
           Character.LINE_SEPARATOR,
           Character.PARAGRAPH_SEPARATOR -> false;
      default -> true;
    };
  }

  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private static void appendUnicodeEscape(StringBuilder builder, int cp) {
    if (cp <= 0xff) {
      builder.append("\\x");
      appendHex(builder, cp, 2);
    } else if (cp <= 0xffff) {
      builder.append("\\u");
      appendHex(builder, cp, 4);
    } else {
      builder.append("\\U");
      appendHex(builder, cp, 8);
    }
  }

  private static void appendHex(StringBuilder builder, int value, int width) {
    for (var shift = (width - 1) * 4; shift >= 0; shift -= 4) {
      builder.append(HEX[(value >>> shift) & 0xf]);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Errors

  private static UnimplementedFeatureError unsupported(Object value) {
    return new UnimplementedFeatureError(
        "str of " + value.getClass().getName());
  }
}
