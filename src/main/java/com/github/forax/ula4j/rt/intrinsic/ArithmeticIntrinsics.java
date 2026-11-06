package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.PyTuple.PyTuple3;
import com.github.forax.ula4j.rt.PyTuple.PyTuple4;
import com.github.forax.ula4j.rt.PyTuple.PyTupleN;
import com.github.forax.ula4j.rt.error.PyOverflowError;
import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.error.PyValueError;
import com.github.forax.ula4j.rt.error.PyZeroDivisionError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;

import java.math.BigInteger;
import java.util.Arrays;

import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.badUnaryOperand;
import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.isInt;
import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.isNumber;
import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.normalize;
import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.toBigInteger;
import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.toDouble;
import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.typeName;
import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.unsupportedOperands;

/// Binary and unary arithmetic, bitwise operations, shifts, and the augmented-assignment variants.
///
/// Each intrinsic inlines only the same-class hot cases. Everything else goes to a private
/// generic helper. Helpers are never specialized and never recurse into the intrinsic that calls them,
/// because Specializer rejects a direct recursive call.
public final class ArithmeticIntrinsics {
  private ArithmeticIntrinsics() {
    throw new AssertionError();
  }

  private static final int AND = 0;
  private static final int OR = 1;
  private static final int XOR = 2;

  /// Largest string/tuple the JVM can allocate.
  private static final int MAX_SEQUENCE_LENGTH = Integer.MAX_VALUE - 8;

  // ------------------------------------------------------------------ binary intrinsics

  @Intrinsic
  public static Object $add$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> {
            try {
              return Math.addExact(l, r);
            } catch (ArithmeticException _) {
              return BigInteger.valueOf(l).add(BigInteger.valueOf(r));
            }
          }
          default -> { return addGeneric(left, right); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return l + r; }
          default -> { return addGeneric(left, right); }
        }
      }
      case String l -> {
        switch (right) {
          case String r -> { return l.concat(r); }
          default -> { return addGeneric(left, right); }
        }
      }
      default -> { return addGeneric(left, right); }
    }
  }

  @Intrinsic
  public static Object $sub$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> {
            try {
              return Math.subtractExact(l, r);
            } catch (ArithmeticException _) {
              return BigInteger.valueOf(l).subtract(BigInteger.valueOf(r));
            }
          }
          default -> { return subGeneric(left, right); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return l - r; }
          default -> { return subGeneric(left, right); }
        }
      }
      default -> { return subGeneric(left, right); }
    }
  }

  @Intrinsic
  public static Object $mul$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> {
            try {
              return Math.multiplyExact(l, r);
            } catch (ArithmeticException _) {
              return BigInteger.valueOf(l).multiply(BigInteger.valueOf(r));
            }
          }
          default -> { return mulGeneric(left, right); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return l * r; }
          default -> { return mulGeneric(left, right); }
        }
      }
      default -> { return mulGeneric(left, right); }
    }
  }

  /// True division (`/`): always a float.
  @Intrinsic
  public static Object $div$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return divideLongs(l, r); }
          default -> { return divGeneric(left, right); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return floatDiv(l, r); }
          default -> { return divGeneric(left, right); }
        }
      }
      default -> { return divGeneric(left, right); }
    }
  }

  @Intrinsic
  public static Object $floorDiv$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return floorDivLongs(l, r); }
          default -> { return floorDivGeneric(left, right); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return floatFloorDiv(l, r); }
          default -> { return floorDivGeneric(left, right); }
        }
      }
      default -> { return floorDivGeneric(left, right); }
    }
  }

  @Intrinsic
  public static Object $mod$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return modLongs(l, r); }
          default -> { return modGeneric(left, right); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return floatMod(l, r); }
          default -> { return modGeneric(left, right); }
        }
      }
      default -> { return modGeneric(left, right); }
    }
  }

  @Intrinsic
  public static Object $pow$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return powLongs(l, r); }
          default -> { return powGeneric(left, right); }
        }
      }
      case Double l -> {
        switch (right) {
          case Double r -> { return floatPow(l, r); }
          default -> { return powGeneric(left, right); }
        }
      }
      default -> { return powGeneric(left, right); }
    }
  }

  @Intrinsic
  public static Object $lshift$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return lshiftLongs(l, r); }
          default -> { return lshiftGeneric(left, right); }
        }
      }
      default -> { return lshiftGeneric(left, right); }
    }
  }

  @Intrinsic
  public static Object $rshift$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return rshiftLongs(l, r); }
          default -> { return rshiftGeneric(left, right); }
        }
      }
      default -> { return rshiftGeneric(left, right); }
    }
  }

  @Intrinsic
  public static Object $bitAnd$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return l & r; }
          default -> { return bitwiseGeneric("&", AND, left, right); }
        }
      }
      default -> { return bitwiseGeneric("&", AND, left, right); }
    }
  }

  @Intrinsic
  public static Object $bitOr$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return l | r; }
          default -> { return bitwiseGeneric("|", OR, left, right); }
        }
      }
      default -> { return bitwiseGeneric("|", OR, left, right); }
    }
  }

  @Intrinsic
  public static Object $bitXor$(Object left, Object right) {
    switch (left) {
      case Long l -> {
        switch (right) {
          case Long r -> { return l ^ r; }
          default -> { return bitwiseGeneric("^", XOR, left, right); }
        }
      }
      default -> { return bitwiseGeneric("^", XOR, left, right); }
    }
  }

  // ------------------------------------------------------------------ unary intrinsics

  @Intrinsic
  public static Object $neg$(Object operand) {
    switch (operand) {
      case Long v -> {
        if (v.longValue() == Long.MIN_VALUE) {
          return BigInteger.valueOf(Long.MIN_VALUE).negate();
        }
        return -v;
      }
      case Double v -> { return -v; }
      case BigInteger v -> { return normalize(v.negate()); }
      case Boolean v -> { return v ? -1L : 0L; }
      default -> throw badUnaryOperand("-", operand);
    }
  }

  @Intrinsic
  public static Object $pos$(Object operand) {
    switch (operand) {
      case Long v -> { return v; }
      case Double v -> { return v; }
      case BigInteger v -> { return v; }
      case Boolean v -> { return v ? 1L : 0L; }
      default -> throw badUnaryOperand("+", operand);
    }
  }

  @Intrinsic
  public static Object $invert$(Object operand) {
    switch (operand) {
      case Long v -> { return ~v; }
      case BigInteger v -> { return normalize(v.not()); }
      case Boolean v -> { return v ? -2L : -1L; }
      default -> throw badUnaryOperand("~", operand);
    }
  }

  // ------------------------------------------------------------------ augmented assignment
  // Numbers, str and tuple are immutable, so `x op= y` is `x = x op y`.
  // TODO when PyList exists: `$iadd$` and `$imul$` extend/repeat the list in place and return the same list.

  @Intrinsic
  public static Object $iadd$(Object left, Object right) { return $add$(left, right); }

  @Intrinsic
  public static Object $isub$(Object left, Object right) { return $sub$(left, right); }

  @Intrinsic
  public static Object $imul$(Object left, Object right) { return $mul$(left, right); }

  @Intrinsic
  public static Object $idiv$(Object left, Object right) { return $div$(left, right); }

  @Intrinsic
  public static Object $ifloorDiv$(Object left, Object right) { return $floorDiv$(left, right); }

  @Intrinsic
  public static Object $imod$(Object left, Object right) { return $mod$(left, right); }

  @Intrinsic
  public static Object $ipow$(Object left, Object right) { return $pow$(left, right); }

  @Intrinsic
  public static Object $ilshift$(Object left, Object right) { return $lshift$(left, right); }

  @Intrinsic
  public static Object $irshift$(Object left, Object right) { return $rshift$(left, right); }

  @Intrinsic
  public static Object $iand$(Object left, Object right) { return $bitAnd$(left, right); }

  @Intrinsic
  public static Object $ior$(Object left, Object right) { return $bitOr$(left, right); }

  @Intrinsic
  public static Object $ixor$(Object left, Object right) { return $bitXor$(left, right); }

  // ------------------------------------------------------------------ + - *

  private static Object addGeneric(Object left, Object right) {
    if (isInt(left) && isInt(right)) {
      return normalize(toBigInteger(left).add(toBigInteger(right)));
    }
    if (isNumber(left) && isNumber(right)) {
      return toDouble(left) + toDouble(right);
    }
    if (left instanceof String l && right instanceof String r) {
      return l.concat(r);
    }
    if (left instanceof PyTuple l && right instanceof PyTuple r) {
      return concatTuples(l, r);
    }
    if (left instanceof String) {
      throw cannotConcatenate("str", right);
    }
    if (left instanceof PyTuple) {
      throw cannotConcatenate("tuple", right);
    }
    throw unsupportedOperands("+", left, right);
  }

  private static PyTypeError cannotConcatenate(String kind, Object right) {
    return new PyTypeError("can only concatenate " + kind + " (not \"" + typeName(right) + "\") to " + kind);
  }

  private static Object subGeneric(Object left, Object right) {
    if (isInt(left) && isInt(right)) {
      return normalize(toBigInteger(left).subtract(toBigInteger(right)));
    }
    if (isNumber(left) && isNumber(right)) {
      return toDouble(left) - toDouble(right);
    }
    throw unsupportedOperands("-", left, right);
  }

  private static Object mulGeneric(Object left, Object right) {
    if (isInt(left) && isInt(right)) {
      return normalize(toBigInteger(left).multiply(toBigInteger(right)));
    }
    if (isNumber(left) && isNumber(right)) {
      return toDouble(left) * toDouble(right);
    }
    if (isSequence(left)) {
      if (!isInt(right)) {
        throw nonIntRepeat(right);
      }
      return repeat(left, right);
    }
    if (isSequence(right)) {
      if (!isInt(left)) {
        throw nonIntRepeat(left);
      }
      return repeat(right, left);
    }
    throw unsupportedOperands("*", left, right);
  }

  private static boolean isSequence(Object value) {
    return value instanceof String || value instanceof PyTuple;
  }

  private static PyTypeError nonIntRepeat(Object count) {
    return new PyTypeError("can't multiply sequence by non-int of type '" + typeName(count) + "'");
  }

  // ------------------------------------------------------------------ sequences

  private static Object repeat(Object sequence, Object count) {
    long times;
    switch (count) {
      case BigInteger _ -> throw new PyOverflowError("cannot fit 'int' into an index-sized integer");
      default -> times = com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.toLong(count);
    }
    return switch (sequence) {
      case String s -> repeatString(s, times);
      case PyTuple t -> repeatTuple(t, times);
      default -> throw new AssertionError("not a sequence: " + sequence);
    };
  }

  private static String repeatString(String s, long times) {
    if (times <= 0 || s.isEmpty()) {
      return "";
    }
    if (s.length() > Long.MAX_VALUE / times) {
      throw new PyOverflowError("repeated string is too long");
    }
    if (s.length() > MAX_SEQUENCE_LENGTH / times) {
      throw new UnimplementedFeatureError("a string longer than the JVM limit can not be created");
    }
    return s.repeat((int) times);
  }

  private static Object repeatTuple(PyTuple tuple, long times) {
    var items = itemsOf(tuple);
    if (times <= 0 || items.length == 0) {
      return tupleOf(new Object[0]);
    }
    if (times == 1) {
      return tuple;
    }
    if (items.length > MAX_SEQUENCE_LENGTH / times) {
      throw new UnimplementedFeatureError("a tuple longer than the JVM limit can not be created");
    }
    var result = new Object[(int) (items.length * times)];
    for (var offset = 0; offset < result.length; offset += items.length) {
      System.arraycopy(items, 0, result, offset, items.length);
    }
    return tupleOf(result);
  }

  private static Object concatTuples(PyTuple left, PyTuple right) {
    var leftItems = itemsOf(left);
    var rightItems = itemsOf(right);
    var items = Arrays.copyOf(leftItems, leftItems.length + rightItems.length);
    System.arraycopy(rightItems, 0, items, leftItems.length, rightItems.length);
    return tupleOf(items);
  }

  private static Object[] itemsOf(PyTuple tuple) {
    var size = (int) TupleIntrinsics.$size$(tuple);
    var items = new Object[size];
    for (var i = 0; i < size; i++) {
      items[i] = TupleIntrinsics.$get$(tuple, (long) i);
    }
    return items;
  }

  /// Picks the representation: arity 1-4 use the specialized records, 0 and 5+ the generic one.
  private static PyTuple tupleOf(Object[] items) {
    return switch (items.length) {
      case 1 -> new PyTuple1(items[0]);
      case 2 -> new PyTuple2(items[0], items[1]);
      case 3 -> new PyTuple3(items[0], items[1], items[2]);
      case 4 -> new PyTuple4(items[0], items[1], items[2], items[3]);
      default -> new PyTupleN(items);
    };
  }

  // ------------------------------------------------------------------ true division

  private static double divideLongs(long l, long r) {
    if (r == 0) {
      throw new PyZeroDivisionError("division by zero");
    }
    if (isExactlyADouble(l) && isExactlyADouble(r)) {
      return (double) l / (double) r;  // one IEEE division of two exact values: correctly rounded
    }
    return divideBig(BigInteger.valueOf(l), BigInteger.valueOf(r));
  }

  private static boolean isExactlyADouble(long value) {
    return -(1L << 53) <= value && value <= (1L << 53);
  }

  private static double floatDiv(double l, double r) {
    if (r == 0.0) {
      throw new PyZeroDivisionError("float division by zero");
    }
    return l / r;
  }

  private static Object divGeneric(Object left, Object right) {
    if (isInt(left) && isInt(right)) {
      var denominator = toBigInteger(right);
      if (denominator.signum() == 0) {
        throw new PyZeroDivisionError("division by zero");
      }
      return divideBig(toBigInteger(left), denominator);
    }
    if (isNumber(left) && isNumber(right)) {
      return floatDiv(toDouble(left), toDouble(right));
    }
    throw unsupportedOperands("/", left, right);
  }

  /// Correctly rounded (half to even) quotient of two ints, whatever their size.
  /// Computes the integer quotient at the weight of the last bit a double can keep
  /// (53 bits, or fewer for a subnormal result), rounds it, and scales it by a power of two (exact).
  private static double divideBig(BigInteger numerator, BigInteger denominator) {
    var negative = (numerator.signum() < 0) != (denominator.signum() < 0);
    var a = numerator.abs();
    var b = denominator.abs();
    if (a.signum() == 0) {
      return negative ? -0.0 : 0.0;
    }
    var lsb = Math.max(a.bitLength() - b.bitLength() - 53, -1074);  // exponent of the last bit kept
    while (true) {
      var num = lsb < 0 ? a.shiftLeft(-lsb) : a;
      var den = lsb < 0 ? b : b.shiftLeft(lsb);
      var quotientAndRemainder = num.divideAndRemainder(den);
      var quotient = quotientAndRemainder[0];
      if (quotient.bitLength() > 53) {  // the estimate of the exponent was one too low
        lsb++;
        continue;
      }
      var twiceRemainder = quotientAndRemainder[1].shiftLeft(1).compareTo(den);
      if (twiceRemainder > 0 || (twiceRemainder == 0 && quotient.testBit(0))) {
        quotient = quotient.add(BigInteger.ONE);
      }
      var result = Math.scalb(quotient.doubleValue(), lsb);  // exact: quotient <= 2**53, lsb >= -1074
      if (Double.isInfinite(result)) {
        throw new PyOverflowError("integer division result too large for a float");
      }
      return negative ? -result : result;
    }
  }

  // ------------------------------------------------------------------ // and %

  private static Object floorDivLongs(long l, long r) {
    if (r == 0) {
      throw new PyZeroDivisionError("integer division or modulo by zero");
    }
    if (l == Long.MIN_VALUE && r == -1L) {
      return BigInteger.valueOf(l).negate();
    }
    return Math.floorDiv(l, r);
  }

  private static Object modLongs(long l, long r) {
    if (r == 0) {
      throw new PyZeroDivisionError("integer modulo by zero");
    }
    return Math.floorMod(l, r);
  }

  private static Object floorDivGeneric(Object left, Object right) {
    if (isInt(left) && isInt(right)) {
      var divisor = toBigInteger(right);
      if (divisor.signum() == 0) {
        throw new PyZeroDivisionError("integer division or modulo by zero");
      }
      var quotientAndRemainder = toBigInteger(left).divideAndRemainder(divisor);
      var quotient = quotientAndRemainder[0];
      var remainder = quotientAndRemainder[1];
      if (remainder.signum() != 0 && remainder.signum() != divisor.signum()) {
        quotient = quotient.subtract(BigInteger.ONE);  // truncated towards zero, floor needs one less
      }
      return normalize(quotient);
    }
    if (isNumber(left) && isNumber(right)) {
      return floatFloorDiv(toDouble(left), toDouble(right));
    }
    throw unsupportedOperands("//", left, right);
  }

  private static Object modGeneric(Object left, Object right) {
    if (left instanceof String) {
      throw new UnimplementedFeatureError("'%' string formatting");
    }
    if (isInt(left) && isInt(right)) {
      var divisor = toBigInteger(right);
      if (divisor.signum() == 0) {
        throw new PyZeroDivisionError("integer modulo by zero");
      }
      var remainder = toBigInteger(left).remainder(divisor);
      if (remainder.signum() != 0 && remainder.signum() != divisor.signum()) {
        remainder = remainder.add(divisor);  // the remainder takes the sign of the divisor
      }
      return normalize(remainder);
    }
    if (isNumber(left) && isNumber(right)) {
      return floatMod(toDouble(left), toDouble(right));
    }
    throw unsupportedOperands("%", left, right);
  }

  /// CPython's `float_floor_div` (`_float_div_mod`).
  private static double floatFloorDiv(double a, double b) {
    if (b == 0.0) {
      throw new PyZeroDivisionError("float floor division by zero");
    }
    var mod = a % b;  // Java's % on doubles is C's fmod
    var div = (a - mod) / b;
    if (mod != 0.0 && (b < 0.0) != (mod < 0.0)) {
      div -= 1.0;
    }
    if (div != 0.0) {
      var floored = Math.floor(div);
      if (div - floored > 0.5) {
        floored += 1.0;
      }
      return floored;
    }
    return Math.copySign(0.0, a / b);
  }

  /// CPython's `float_rem`.
  private static double floatMod(double a, double b) {
    if (b == 0.0) {
      throw new PyZeroDivisionError("float modulo by zero");
    }
    var mod = a % b;
    if (mod != 0.0) {
      if ((b < 0.0) != (mod < 0.0)) {
        mod += b;
      }
    } else {
      mod = Math.copySign(0.0, b);
    }
    return mod;
  }

  // ------------------------------------------------------------------ **

  private static Object powLongs(long base, long exponent) {
    if (exponent < 0) {
      return floatPow((double) base, (double) exponent);  // int ** negative int is a float
    }
    try {
      var result = 1L;
      var square = base;
      var remaining = exponent;
      while (remaining != 0) {
        if ((remaining & 1) != 0) {
          result = Math.multiplyExact(result, square);
        }
        remaining >>= 1;
        if (remaining != 0) {
          square = Math.multiplyExact(square, square);
        }
      }
      return result;
    } catch (ArithmeticException _) {
      return powBig(BigInteger.valueOf(base), BigInteger.valueOf(exponent));
    }
  }

  private static Object powGeneric(Object left, Object right) {
    if (isInt(left) && isInt(right)) {
      var exponent = toBigInteger(right);
      if (exponent.signum() < 0) {
        return floatPow(toDouble(left), toDouble(right));
      }
      return powBig(toBigInteger(left), exponent);
    }
    if (isNumber(left) && isNumber(right)) {
      return floatPow(toDouble(left), toDouble(right));
    }
    throw unsupportedOperands("** or pow()", left, right);
  }

  /// The exponent is not negative.
  private static Object powBig(BigInteger base, BigInteger exponent) {
    if (exponent.signum() == 0) {
      return 1L;
    }
    if (base.signum() == 0) {
      return 0L;
    }
    if (base.equals(BigInteger.ONE)) {
      return 1L;
    }
    if (base.equals(BigInteger.ONE.negate())) {
      return exponent.testBit(0) ? -1L : 1L;
    }
    if (exponent.bitLength() > 31) {
      throw new UnimplementedFeatureError("exponent too large: " + exponent);
    }
    try {
      return normalize(base.pow(exponent.intValue()));
    } catch (ArithmeticException _) {
      throw new UnimplementedFeatureError("the result of ** is too large for the JVM");
    }
  }

  /// CPython's `float_pow`; `Math.pow` alone differs on several special cases (e.g. `1.0 ** inf`).
  private static double floatPow(double iv, double iw) {
    if (iw == 0.0) {
      return 1.0;
    }
    if (Double.isNaN(iv)) {
      return iv;
    }
    if (Double.isNaN(iw)) {
      return iv == 1.0 ? 1.0 : iw;
    }
    if (Double.isInfinite(iw)) {
      var abs = Math.abs(iv);
      if (abs == 1.0) {
        return 1.0;
      }
      return (iw > 0.0) == (abs > 1.0) ? Double.POSITIVE_INFINITY : 0.0;
    }
    if (Double.isInfinite(iv)) {
      var oddExponent = isOddInteger(iw);
      if (iw > 0.0) {
        return oddExponent ? iv : Math.abs(iv);
      }
      return oddExponent ? Math.copySign(0.0, iv) : 0.0;
    }
    if (iv == 0.0) {
      if (iw < 0.0) {
        throw new PyZeroDivisionError("zero to a negative power");
      }
      return isOddInteger(iw) ? iv : 0.0;
    }
    var negateResult = false;
    if (iv < 0.0) {
      if (iw != Math.floor(iw)) {
        throw new UnimplementedFeatureError("negative float ** non-integer float is a complex number");
      }
      iv = -iv;
      negateResult = isOddInteger(iw);
    }
    if (iv == 1.0) {
      return negateResult ? -1.0 : 1.0;
    }
    var result = StrictMath.pow(iv, iw);
    if (Double.isInfinite(result)) {
      throw new PyOverflowError("(34, 'Numerical result out of range')");
    }
    return negateResult ? -result : result;
  }

  private static boolean isOddInteger(double value) {
    return Math.abs(value) % 2.0 == 1.0;
  }

  // ------------------------------------------------------------------ shifts and bitwise

  private static Object lshiftLongs(long value, long count) {
    if (count < 0) {
      throw new PyValueError("negative shift count");
    }
    if (value == 0) {
      return 0L;
    }
    if (count < 63) {
      var shifted = value << count;
      if ((shifted >> count) == value) {  // nothing was lost
        return shifted;
      }
    }
    return shiftLeftBig(BigInteger.valueOf(value), BigInteger.valueOf(count));
  }

  private static Object lshiftGeneric(Object left, Object right) {
    if (!isInt(left) || !isInt(right)) {
      throw unsupportedOperands("<<", left, right);
    }
    var count = toBigInteger(right);
    if (count.signum() < 0) {
      throw new PyValueError("negative shift count");
    }
    var value = toBigInteger(left);
    if (value.signum() == 0) {
      return 0L;
    }
    return shiftLeftBig(value, count);
  }

  /// The value is not zero, the count is not negative.
  private static Object shiftLeftBig(BigInteger value, BigInteger count) {
    if (count.bitLength() > 31) {
      if (count.bitLength() > 63) {
        throw new PyOverflowError("too many digits in integer");
      }
      throw new UnimplementedFeatureError("shift count too large for the JVM: " + count);
    }
    try {
      return normalize(value.shiftLeft(count.intValue()));
    } catch (ArithmeticException _) {
      throw new UnimplementedFeatureError("the result of << is too large for the JVM");
    }
  }

  private static Object rshiftLongs(long value, long count) {
    if (count < 0) {
      throw new PyValueError("negative shift count");
    }
    if (count >= 64) {
      return value < 0 ? -1L : 0L;
    }
    return value >> count;
  }

  private static Object rshiftGeneric(Object left, Object right) {
    if (!isInt(left) || !isInt(right)) {
      throw unsupportedOperands(">>", left, right);
    }
    var count = toBigInteger(right);
    if (count.signum() < 0) {
      throw new PyValueError("negative shift count");
    }
    var value = toBigInteger(left);
    if (count.bitLength() > 31) {
      return value.signum() < 0 ? -1L : 0L;
    }
    return normalize(value.shiftRight(count.intValue()));  // arithmetic shift: floor semantics
  }

  private static Object bitwiseGeneric(String symbol, int op, Object left, Object right) {
    if (left instanceof Boolean l && right instanceof Boolean r) {  // bool op bool stays a bool
      return switch (op) {
        case AND -> l & r;
        case OR -> l | r;
        case XOR -> l ^ r;
        default -> throw new AssertionError(op);
      };
    }
    if (isInt(left) && isInt(right)) {
      var a = toBigInteger(left);
      var b = toBigInteger(right);
      return normalize(switch (op) {
        case AND -> a.and(b);
        case OR -> a.or(b);
        case XOR -> a.xor(b);
        default -> throw new AssertionError(op);
      });
    }
    throw unsupportedOperands(symbol, left, right);
  }
}