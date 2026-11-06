package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.PyTuple.PyTupleN;
import com.github.forax.ula4j.rt.error.PyOverflowError;
import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.error.PyValueError;
import com.github.forax.ula4j.rt.error.PyZeroDivisionError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ArithmeticIntrinsicsTest {

  @Nested
  public final class Add {

    @Test
    public void addDoubles() {
      assertEquals(3.5, ArithmeticIntrinsics.$add$(1.5, 2.0));
      assertEquals(3.5, ArithmeticIntrinsics.$add$(1L, 2.5));     // cross-type → float
      assertEquals(3.5, ArithmeticIntrinsics.$add$(1.5, 2L));
      assertEquals(Double.POSITIVE_INFINITY, ArithmeticIntrinsics.$add$(Double.MAX_VALUE, Double.MAX_VALUE));
      assertEquals(Double.NEGATIVE_INFINITY, ArithmeticIntrinsics.$add$(Double.NEGATIVE_INFINITY, 1.0));
      // Java: -0.0 + 0.0 == 0.0, -0.0 + -0.0 == -0.0  (matches CPython)
      assertEquals(0.0, ArithmeticIntrinsics.$add$(-0.0, 0.0));
      assertEquals(-0.0, ArithmeticIntrinsics.$add$(-0.0, -0.0));
    }

    @Test
    public void addBigIntegerNormalization() {
      // Small BigInteger results are normalised back to Long.
      assertEquals(6L, ArithmeticIntrinsics.$add$(BigInteger.TWO, BigInteger.valueOf(4)));
      assertEquals(11L, ArithmeticIntrinsics.$add$(BigInteger.TEN, 1L));
      // Large results stay BigInteger.
      var big = BigInteger.TEN.pow(30);
      assertEquals(big.add(big), ArithmeticIntrinsics.$add$(big, big));
    }

    @Test
    public void addStringConcatenation() {
      assertEquals("foobar", ArithmeticIntrinsics.$add$("foo", "bar"));
      assertEquals("", ArithmeticIntrinsics.$add$("", ""));
      // Left-hand side determines the error message.
      assertEquals(
          "can only concatenate str (not \"int\") to str",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$add$("a", 1L)).getMessage());
      assertEquals(
          "can only concatenate str (not \"NoneType\") to str",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$add$("a",
              PyNone.INSTANCE)).getMessage());
    }

    @Test
    public void addTupleConcatenation() {
      var left = new PyTuple1(1L);
      var right = new PyTuple2("a", "b");
      var result = (PyTuple) ArithmeticIntrinsics.$add$(left, right);
      assertEquals(3L, TupleIntrinsics.$size$(result));
      assertEquals(1L, TupleIntrinsics.$get$(result, 0L));
      assertEquals("a", TupleIntrinsics.$get$(result, 1L));
      assertEquals("b", TupleIntrinsics.$get$(result, 2L));

      assertEquals(
          "can only concatenate tuple (not \"int\") to tuple",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$add$(left, 1L)).getMessage());
    }

    @Test
    public void addUnsupported() {
      // Tuple + String goes through the tuple branch (left is a tuple).
      assertEquals(
          "can only concatenate tuple (not \"str\") to tuple",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$add$(new PyTuple1(1L), "x")).getMessage());
    }
  }

  @Nested
  public final class Sub {

    @Test
    public void subNumeric() {
      assertEquals(-1L, ArithmeticIntrinsics.$sub$(1L, 2L));
      // Fits exactly in a long → no promotion.
      assertEquals(Long.MIN_VALUE, ArithmeticIntrinsics.$sub$(-1L, Long.MAX_VALUE));
      // Genuine overflow → BigInteger.
      assertEquals(
          BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE),
          ArithmeticIntrinsics.$sub$(Long.MIN_VALUE, 1L));
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$sub$(Long.MAX_VALUE, -1L));
      // Floats.
      assertEquals(-1.5, ArithmeticIntrinsics.$sub$(1.5, 3.0));
      assertEquals(-0.5, ArithmeticIntrinsics.$sub$(1L, 1.5));
      assertEquals(0.5, ArithmeticIntrinsics.$sub$(1.5, 1L));
      assertTrue(Double.isNaN((double) ArithmeticIntrinsics.$sub$(
          Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)));
    }

    @Test
    public void subUnsupported() {
      assertEquals(
          "unsupported operand type(s) for -: 'str' and 'str'",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$sub$("a", "b")).getMessage());
      assertEquals(
          "unsupported operand type(s) for -: 'int' and 'str'",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$sub$(1L, "a")).getMessage());
    }
  }


  @Nested
  public final class Mul {

    @Test
    public void mulNumeric() {
      assertEquals(6L, ArithmeticIntrinsics.$mul$(2L, 3L));
      assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO),
          ArithmeticIntrinsics.$mul$(Long.MAX_VALUE, 2L));      // 2^64 - 2
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$mul$(-1L, Long.MIN_VALUE));    // 2^63
      assertEquals(7.5, ArithmeticIntrinsics.$mul$(2.5, 3.0));
      assertEquals(7.5, ArithmeticIntrinsics.$mul$(2.5, 3L));
      assertEquals(7.5, ArithmeticIntrinsics.$mul$(2L, 3.75));
      assertTrue(Double.isNaN((double) ArithmeticIntrinsics.$mul$(0.0, Double.POSITIVE_INFINITY)));
    }

    @Test
    public void mulBigIntegerNormalization() {
      assertEquals(6L, ArithmeticIntrinsics.$mul$(BigInteger.TWO, BigInteger.valueOf(3)));
      assertEquals(100L, ArithmeticIntrinsics.$mul$(BigInteger.TEN, BigInteger.TEN));
    }

    // =========================================================
    //  Sequence repetition (`str * int`, `int * str`, `tuple * int`)
    // =========================================================

    @Test
    public void stringRepeat() {
      assertEquals("", ArithmeticIntrinsics.$mul$("ab", 0L));
      assertEquals("", ArithmeticIntrinsics.$mul$("ab", -1L));
      assertEquals("ab", ArithmeticIntrinsics.$mul$("ab", 1L));
      assertEquals("ababab", ArithmeticIntrinsics.$mul$("ab", 3L));
      assertEquals("", ArithmeticIntrinsics.$mul$("", 100L));
      assertEquals("", ArithmeticIntrinsics.$mul$("", 0L));
      // int * str order.
      assertEquals("ababab", ArithmeticIntrinsics.$mul$(3L, "ab"));
      assertEquals("", ArithmeticIntrinsics.$mul$(-1L, "ab"));
      // bool is an int.
      assertEquals("ab", ArithmeticIntrinsics.$mul$("ab", true));
      assertEquals("", ArithmeticIntrinsics.$mul$("ab", false));
      assertEquals("ab", ArithmeticIntrinsics.$mul$(true, "ab"));
    }

    @Test
    public void tupleRepeat() {
      var tuple = new PyTuple2(1L, 2L);
      var result = (PyTuple) ArithmeticIntrinsics.$mul$(tuple, 3L);
      assertEquals(6L, TupleIntrinsics.$size$(result));
      for (int i = 0; i < 6; i++) {
        assertEquals(i % 2 == 0 ? 1L : 2L, TupleIntrinsics.$get$(result, i));
      }
      // * 1 returns the same tuple (identity optimisation).
      assertSame(tuple, ArithmeticIntrinsics.$mul$(tuple, 1L));
      assertSame(tuple, ArithmeticIntrinsics.$mul$(1L, tuple));
      // * 0 and * -1 give the empty tuple.
      assertEquals(0L, TupleIntrinsics.$size$((PyTuple) ArithmeticIntrinsics.$mul$(tuple, 0L)));
      assertEquals(0L, TupleIntrinsics.$size$((PyTuple) ArithmeticIntrinsics.$mul$(tuple, -1L)));
      // Empty tuple * n stays empty.
      var empty = new PyTupleN(new Object[0]);
      assertEquals(0L, TupleIntrinsics.$size$((PyTuple) ArithmeticIntrinsics.$mul$(empty, 5L)));
    }

    @Test
    public void sequenceRepeatErrors() {
      assertEquals(
          "can't multiply sequence by non-int of type 'float'",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$mul$("ab", 1.5)).getMessage());
      assertEquals(
          "can't multiply sequence by non-int of type 'float'",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$mul$(1.5, "ab")).getMessage());
      // Note: `normalize` never produces a BigInteger when the value fits in a long,
      // so in practice only huge counts reach the BigInteger branch.
      assertThrows(PyOverflowError.class,
          () -> ArithmeticIntrinsics.$mul$("ab", BigInteger.ONE.shiftLeft(200)));
      // TODO(future): `"ab" * BigInteger.TEN` currently throws PyOverflowError, whereas
      // CPython returns "abababababababababab". Unreachable through normal evaluation.
    }

    @Test
    public void sequenceRepeatJvmLimit() {
      // MAX_SEQUENCE_LENGTH = Integer.MAX_VALUE - 8.
      // "ab" * (2^31 - 1) would need ~2^32 chars → JVM limit.
      assertThrows(UnimplementedFeatureError.class,
          () -> ArithmeticIntrinsics.$mul$("ab", (long) Integer.MAX_VALUE));
      assertThrows(UnimplementedFeatureError.class,
          () -> ArithmeticIntrinsics.$mul$(new PyTuple2(1L, 2L), (long) Integer.MAX_VALUE));
    }
  }


  @Nested
  public final class Div {

    @Test
    public void divExactLongs() {
      // Both operands fit exactly in a double → fast path.
      assertEquals(2.0, ArithmeticIntrinsics.$div$(4L, 2L));
      assertEquals(-2.0, ArithmeticIntrinsics.$div$(-4L, 2L));
      assertEquals(0.0, ArithmeticIntrinsics.$div$(0L, 5L));
      assertEquals(-0.0, ArithmeticIntrinsics.$div$(0L, -5L));
      assertEquals(0.5, ArithmeticIntrinsics.$div$(1L, 2L));
    }

    @Test
    public void divTiesToEven() {
      // (2^53 + 1) / 2 = 2^52 + 0.5  → tie between 2^52 and 2^52 + 1 (ulp = 1)
      //                              → ties-to-even → 2^52.
      assertEquals(4503599627370496.0, ArithmeticIntrinsics.$div$(9007199254740993L, 2L));
      // (2^53 + 3) / 2 = 2^52 + 1.5  → tie between 2^52 + 1 (odd) and 2^52 + 2 (even)
      //                              → ties-to-even → 2^52 + 2.
      assertEquals(4503599627370498.0, ArithmeticIntrinsics.$div$(9007199254740995L, 2L));
      // Sanity: exact representable case through the fast path.
      assertEquals(9007199254740992.0, ArithmeticIntrinsics.$div$(1L << 53, 1L));
    }

    @Test
    public void divSubnormal() {
      // 1 / 2^1074 = Double.MIN_VALUE  (smallest positive double).
      assertEquals(Double.MIN_VALUE,
          ArithmeticIntrinsics.$div$(1L, BigInteger.ONE.shiftLeft(1074)));
      // 1 / 2^1075 rounds (ties-to-even) to 0.0.
      assertEquals(0.0,
          ArithmeticIntrinsics.$div$(1L, BigInteger.ONE.shiftLeft(1075)));
    }

    @Test
    public void divOverflowToInfinity() {
      // 2^2000 / 1 is finite in Python but overflows a double.
      assertThrows(PyOverflowError.class,
          () -> ArithmeticIntrinsics.$div$(BigInteger.ONE.shiftLeft(2000), BigInteger.ONE));
    }

    @Test
    public void divBigIntegerExactly() {
      // BigInteger is not exact as a double, but BigInteger.doubleValue() is
      // correctly rounded — same contract as divideBig.
      var big = BigInteger.TEN.pow(100);
      assertEquals(big.doubleValue(), ArithmeticIntrinsics.$div$(big, BigInteger.ONE));
    }

    @Test
    public void divMinValue() {
      assertEquals((double) Long.MIN_VALUE / -1.0,
          ArithmeticIntrinsics.$div$(Long.MIN_VALUE, -1L));   // 2^63.0
      assertEquals((double) Long.MIN_VALUE,
          ArithmeticIntrinsics.$div$(Long.MIN_VALUE, 1L));    // -2^63.0
    }

    @Test
    public void divZeroDivision() {
      assertEquals("division by zero",
          assertThrows(PyZeroDivisionError.class, () -> ArithmeticIntrinsics.$div$(1L, 0L)).getMessage());
      assertEquals("division by zero",
          assertThrows(PyZeroDivisionError.class, () -> ArithmeticIntrinsics.$div$(BigInteger.ONE, BigInteger.ZERO)).getMessage());
      assertEquals("float division by zero",
          assertThrows(PyZeroDivisionError.class, () -> ArithmeticIntrinsics.$div$(1.0, 0.0)).getMessage());
      assertEquals("float division by zero",
          assertThrows(PyZeroDivisionError.class, () -> ArithmeticIntrinsics.$div$(1.0, -0.0)).getMessage());
    }

    @Test
    public void divFloatInfinity() {
      // ±∞ as a result: numerator is infinite, divisor finite non-zero.
      assertEquals(Double.POSITIVE_INFINITY,
          ArithmeticIntrinsics.$div$(Double.POSITIVE_INFINITY, 2.0));
      assertEquals(Double.NEGATIVE_INFINITY,
          ArithmeticIntrinsics.$div$(Double.NEGATIVE_INFINITY, 2.0));

      // ±0.0 as a result: finite / ±∞, sign = sign(num) XOR sign(den).
      assertEquals(0.0, ArithmeticIntrinsics.$div$(1.0, Double.POSITIVE_INFINITY));
      assertEquals(-0.0, ArithmeticIntrinsics.$div$(-1.0, Double.POSITIVE_INFINITY));
      assertEquals(-0.0, ArithmeticIntrinsics.$div$(1.0, Double.NEGATIVE_INFINITY));
      assertEquals(0.0, ArithmeticIntrinsics.$div$(-1.0, Double.NEGATIVE_INFINITY));

      // ∞ / ∞ and NaN propagation.
      assertTrue(Double.isNaN((double) ArithmeticIntrinsics.$div$(
          Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)));
      assertTrue(Double.isNaN((double) ArithmeticIntrinsics.$div$(
          Double.NaN, 1.0)));

      // Dividing a finite non-zero by ±0.0 is ZeroDivisionError, NOT infinity.
      assertThrows(PyZeroDivisionError.class,
          () -> ArithmeticIntrinsics.$div$(1.0, 0.0));
      assertThrows(PyZeroDivisionError.class,
          () -> ArithmeticIntrinsics.$div$(1.0, -0.0));
    }
  }

  @Nested
  public final class FloorDiv {

    @Test
    public void floorDivBigInteger() {
      assertEquals(-4L, ArithmeticIntrinsics.$floorDiv$(BigInteger.valueOf(-7), BigInteger.TWO));
      assertEquals(-4L, ArithmeticIntrinsics.$floorDiv$(BigInteger.valueOf(7), BigInteger.valueOf(-2)));
      assertEquals(3L, ArithmeticIntrinsics.$floorDiv$(BigInteger.valueOf(7), BigInteger.TWO));
      // Normalisation: huge result stays BigInteger.
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$floorDiv$(BigInteger.valueOf(Long.MIN_VALUE), BigInteger.valueOf(-1)));
    }

    @Test
    public void floorDivZero() {
      assertEquals("integer division or modulo by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$floorDiv$(1L, 0L)).getMessage());
      assertEquals("integer division or modulo by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$floorDiv$(BigInteger.ONE, BigInteger.ZERO)).getMessage());
      assertEquals("float floor division by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$floorDiv$(1.0, 0.0)).getMessage());
    }

    @Test
    public void floorDivFloat() {
      assertEquals(-4.0, ArithmeticIntrinsics.$floorDiv$(-7.0, 2.0));
      assertEquals(-4.0, ArithmeticIntrinsics.$floorDiv$(7.0, -2.0));
      assertEquals(3.0, ArithmeticIntrinsics.$floorDiv$(-7.0, -2.0));
      assertEquals(3.0, ArithmeticIntrinsics.$floorDiv$(7.0, 2.0));
      // -0.0 // 5.0 is -0.0, not 0.0.
      assertEquals(-0.0, ArithmeticIntrinsics.$floorDiv$(-0.0, 5.0));
      assertEquals(0.0, ArithmeticIntrinsics.$floorDiv$(0.0, 5.0));
    }
  }

  @Nested
  public final class Mod {

    @Test
    public void modAllSigns() {
      assertEquals(1L, ArithmeticIntrinsics.$mod$(-7L, 2L));
      assertEquals(-1L, ArithmeticIntrinsics.$mod$(7L, -2L));
      assertEquals(-1L, ArithmeticIntrinsics.$mod$(-7L, -2L));
      assertEquals(1L, ArithmeticIntrinsics.$mod$(7L, 2L));

      assertEquals(1L, ArithmeticIntrinsics.$mod$(BigInteger.valueOf(-7), BigInteger.TWO));
      assertEquals(-1L, ArithmeticIntrinsics.$mod$(BigInteger.valueOf(7), BigInteger.valueOf(-2)));
      assertEquals(-1L, ArithmeticIntrinsics.$mod$(BigInteger.valueOf(-7), BigInteger.valueOf(-2)));

      assertEquals(1.0, ArithmeticIntrinsics.$mod$(-7.0, 2.0));
      assertEquals(-1.0, ArithmeticIntrinsics.$mod$(7.0, -2.0));
      assertEquals(-1.0, ArithmeticIntrinsics.$mod$(-7.0, -2.0));
      assertEquals(1.0, ArithmeticIntrinsics.$mod$(7.0, 2.0));
    }

    @Test
    public void modSignedZero() {
      // -0.0 % 5.0 == 0.0 (sign follows divisor).
      assertEquals(0.0, ArithmeticIntrinsics.$mod$(-0.0, 5.0));
      // 0.0 % -5.0 == -0.0 (sign follows divisor).
      assertEquals(-0.0, ArithmeticIntrinsics.$mod$(0.0, -5.0));
    }

    @Test
    public void modZeroDivision() {
      assertEquals("integer modulo by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$mod$(1L, 0L)).getMessage());
      assertEquals("integer modulo by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$mod$(BigInteger.ONE, BigInteger.ZERO)).getMessage());
      assertEquals("float modulo by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$mod$(1.0, 0.0)).getMessage());
    }

    @Test
    public void modLongMinValue() {
      // -2^63 % -1 == 0.
      assertEquals(0L, ArithmeticIntrinsics.$mod$(Long.MIN_VALUE, -1L));
    }

    @Test
    public void modStringFormattingUnimplemented() {
      assertEquals("'%' string formatting",
          assertThrows(UnimplementedFeatureError.class,
              () -> ArithmeticIntrinsics.$mod$("hello %s", "world")).getMessage());
    }
  }


  @Nested
  public final class Pow {

    @Test
    public void powLongsFitsInLong() {
      assertEquals(1024L, ArithmeticIntrinsics.$pow$(2L, 10L));
      assertEquals(4611686018427387904L,
          ArithmeticIntrinsics.$pow$(2L, 62L));             // 2^62
      assertEquals(1000000000000000000L,
          ArithmeticIntrinsics.$pow$(10L, 18L));            // 10^18
      assertEquals(1L, ArithmeticIntrinsics.$pow$(0L, 0L)); // 0^0 == 1
      assertEquals(0L, ArithmeticIntrinsics.$pow$(0L, 5L));
      assertEquals(1L, ArithmeticIntrinsics.$pow$(1L, 1_000_000_000L));
      assertEquals(-1L, ArithmeticIntrinsics.$pow$(-1L, 1_000_000_001L));
    }

    @Test
    public void powLongsOverflowToBigInteger() {
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$pow$(2L, 63L));
      assertEquals(BigInteger.valueOf(3).pow(40),
          ArithmeticIntrinsics.$pow$(3L, 40L));
      assertEquals(BigInteger.TEN.pow(19),
          ArithmeticIntrinsics.$pow$(10L, 19L));
    }

    @Test
    public void powNegativeExponentIsFloat() {
      assertEquals(0.5, ArithmeticIntrinsics.$pow$(2L, -1L));
      assertEquals(0.25, ArithmeticIntrinsics.$pow$(2L, -2L));
      assertEquals(-0.125, ArithmeticIntrinsics.$pow$(-2L, -3L));
    }

    @Test
    public void powBigBase() {
      // Base 0, 1, -1 with huge exponent avoids computing the power.
      var huge = BigInteger.TEN.pow(30);
      assertEquals(0L, ArithmeticIntrinsics.$pow$(0L, huge));
      assertEquals(1L, ArithmeticIntrinsics.$pow$(1L, huge));
      assertEquals(1L, ArithmeticIntrinsics.$pow$(-1L, huge));             // even
      assertEquals(-1L, ArithmeticIntrinsics.$pow$(-1L, huge.add(BigInteger.ONE))); // odd
    }

    @Test
    public void powExponentTooLarge() {
      assertThrows(UnimplementedFeatureError.class,
          () -> ArithmeticIntrinsics.$pow$(2L, 2147483648L));               // 2^31: bitLength 32
      assertThrows(UnimplementedFeatureError.class,
          () -> ArithmeticIntrinsics.$pow$(2L, BigInteger.ONE.shiftLeft(64)));
    }

    @Test
    public void powZeroToNegative() {
      assertEquals("zero to a negative power",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$pow$(0L, -1L)).getMessage());
      assertEquals("zero to a negative power",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$pow$(0.0, -1.0)).getMessage());
      assertEquals("zero to a negative power",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$pow$(-0.0, -3.0)).getMessage());
    }

    @Test
    public void powFloatSpecialCases() {
      assertEquals(1.0, ArithmeticIntrinsics.$pow$(Double.NaN, 0L));      // NaN^0 == 1
      assertEquals(1.0, ArithmeticIntrinsics.$pow$(1.0, Double.NaN));     // 1^NaN == 1
      assertTrue(Double.isNaN((double) ArithmeticIntrinsics.$pow$(Double.NaN, 5L)));
      assertEquals(Double.POSITIVE_INFINITY,
          ArithmeticIntrinsics.$pow$(2.0, Double.POSITIVE_INFINITY));
      assertEquals(0.0,
          ArithmeticIntrinsics.$pow$(0.5, Double.POSITIVE_INFINITY));
      assertEquals(1.0,
          ArithmeticIntrinsics.$pow$(1.0, Double.POSITIVE_INFINITY));
      assertEquals(1.0,
          ArithmeticIntrinsics.$pow$(-1.0, Double.POSITIVE_INFINITY));
    }

    @Test
    public void powFloatInfinityBase() {
      assertEquals(Double.POSITIVE_INFINITY, ArithmeticIntrinsics.$pow$(Double.POSITIVE_INFINITY, 2L));
      assertEquals(Double.NEGATIVE_INFINITY, ArithmeticIntrinsics.$pow$(Double.NEGATIVE_INFINITY, 3L));
      assertEquals(Double.POSITIVE_INFINITY, ArithmeticIntrinsics.$pow$(Double.NEGATIVE_INFINITY, 2L));
      assertEquals(0.0, ArithmeticIntrinsics.$pow$(Double.POSITIVE_INFINITY, -2L));
      assertEquals(-0.0, ArithmeticIntrinsics.$pow$(Double.NEGATIVE_INFINITY, -3L));
    }

    @Test
    public void powFloatSignedZeroBase() {
      assertEquals(0.0, ArithmeticIntrinsics.$pow$(0.0, 5L));
      assertEquals(0.0, ArithmeticIntrinsics.$pow$(0.0, 4L));
      assertEquals(-0.0, ArithmeticIntrinsics.$pow$(-0.0, 5L));
      assertEquals(0.0, ArithmeticIntrinsics.$pow$(-0.0, 4L));
    }

    @Test
    public void powFloatNegativeBase() {
      assertEquals(-8.0, ArithmeticIntrinsics.$pow$(-2.0, 3L));
      assertEquals(4.0, ArithmeticIntrinsics.$pow$(-2.0, 2L));
      // Negative base, non-integer float exponent → complex number.
      assertThrows(UnimplementedFeatureError.class,
          () -> ArithmeticIntrinsics.$pow$(-2.0, 0.5));
    }

    @Test
    public void powFloatOverflow() {
      assertThrows(PyOverflowError.class,
          () -> ArithmeticIntrinsics.$pow$(10.0, 400L));
      assertThrows(PyOverflowError.class,
          () -> ArithmeticIntrinsics.$pow$(2.0, 1024L));
      // Just below overflow: representable.
      assertEquals(8.98846567431158E307, ArithmeticIntrinsics.$pow$(2.0, 1023L));
    }
  }


  @Nested
  public final class LShift {

    @Test
    public void lshiftFitsInLong() {
      assertEquals(16L, ArithmeticIntrinsics.$lshift$(1L, 4L));
      assertEquals(-32L, ArithmeticIntrinsics.$lshift$(-1L, 5L));
      assertEquals(0L, ArithmeticIntrinsics.$lshift$(0L, 100L));
      assertEquals(4611686018427387904L, ArithmeticIntrinsics.$lshift$(1L, 62L));
    }

    @Test
    public void lshiftOverflowToBigInteger() {
      assertEquals(BigInteger.ONE.shiftLeft(100),
          ArithmeticIntrinsics.$lshift$(1L, 100L));
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$lshift$(1L, 63L));
      assertEquals(BigInteger.TEN.shiftLeft(100),
          ArithmeticIntrinsics.$lshift$(BigInteger.TEN, 100L));
      // Long.MAX_VALUE << 1 == 2^64 - 2
      assertEquals(BigInteger.valueOf(Long.MAX_VALUE).shiftLeft(1),
          ArithmeticIntrinsics.$lshift$(Long.MAX_VALUE, 1L));
    }

    @Test
    public void lshiftNegativeCount() {
      assertEquals("negative shift count",
          assertThrows(PyValueError.class,
              () -> ArithmeticIntrinsics.$lshift$(1L, -1L)).getMessage());
      assertEquals("negative shift count",
          assertThrows(PyValueError.class,
              () -> ArithmeticIntrinsics.$lshift$(BigInteger.ONE, BigInteger.valueOf(-1))).getMessage());
    }

    @Test
    public void lshiftHugeCount() {
      // Count 2^32 has bitLength 33 (> 31 but < 64) → JVM limit.
      assertEquals("shift count too large for the JVM: 4294967296",
          assertThrows(UnimplementedFeatureError.class,
              () -> ArithmeticIntrinsics.$lshift$(1L, BigInteger.ONE.shiftLeft(32))).getMessage());
      // Count 2^64 has bitLength 65 (> 63) → OverflowError.
      assertThrows(PyOverflowError.class,
          () -> ArithmeticIntrinsics.$lshift$(1L, BigInteger.ONE.shiftLeft(64)));
    }

    @Test
    public void lshiftUnsupported() {
      assertEquals(
          "unsupported operand type(s) for <<: 'float' and 'int'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$lshift$(1.5, 1L)).getMessage());
    }
  }

  @Nested
  public final class RShift {

    @Test
    public void rshiftLongs() {
      assertEquals(2L, ArithmeticIntrinsics.$rshift$(8L, 2L));
      assertEquals(-2L, ArithmeticIntrinsics.$rshift$(-8L, 2L));
      assertEquals(-1L, ArithmeticIntrinsics.$rshift$(-1L, 64L));   // sign extension
      assertEquals(0L, ArithmeticIntrinsics.$rshift$(Long.MAX_VALUE, 64L));
      assertEquals(-1L, ArithmeticIntrinsics.$rshift$(-1000L, 1_000_000L));
    }

    @Test
    public void rshiftBigIntegerValue() {
      assertEquals(-2L, ArithmeticIntrinsics.$rshift$(BigInteger.valueOf(-8), 2L));
      assertEquals(0L, ArithmeticIntrinsics.$rshift$(BigInteger.TEN.pow(30), 100L));
      assertEquals(-1L, ArithmeticIntrinsics.$rshift$(BigInteger.valueOf(-1000),
          BigInteger.ONE.shiftLeft(40)));  // huge count → sign fill
    }

    @Test
    public void rshiftNegativeCount() {
      assertEquals("negative shift count",
          assertThrows(PyValueError.class,
              () -> ArithmeticIntrinsics.$rshift$(1L, -1L)).getMessage());
    }
  }


  @Nested
  public final class Bitwise {

    @Test
    public void bitwiseLongs() {
      assertEquals(1L, ArithmeticIntrinsics.$bitAnd$(5L, 3L));
      assertEquals(7L, ArithmeticIntrinsics.$bitOr$(5L, 3L));
      assertEquals(6L, ArithmeticIntrinsics.$bitXor$(5L, 3L));
      assertEquals(0L, ArithmeticIntrinsics.$bitXor$(-1L, -1L));
      assertEquals(Long.MAX_VALUE, ArithmeticIntrinsics.$bitAnd$(Long.MAX_VALUE, -1L));
    }

    @Test
    public void bitwiseBigInteger() {
      var a = BigInteger.ONE.shiftLeft(100);
      assertEquals(a, ArithmeticIntrinsics.$bitAnd$(a, a));                 // stays BigInteger
      assertEquals(0L, ArithmeticIntrinsics.$bitAnd$(a.subtract(BigInteger.ONE), a));
      assertEquals(0L, ArithmeticIntrinsics.$bitXor$(BigInteger.ONE, 1L));
    }

    @Test
    public void bitwiseBooleans() {
      // bool & bool → bool (Boolean result).
      assertEquals(true, ArithmeticIntrinsics.$bitAnd$(true, true));
      assertEquals(false, ArithmeticIntrinsics.$bitAnd$(true, false));
      assertEquals(true, ArithmeticIntrinsics.$bitOr$(true, false));
      assertEquals(false, ArithmeticIntrinsics.$bitXor$(true, true));
      // bool mixed with int → int (Long result), following CPython.
      assertEquals(1L, ArithmeticIntrinsics.$bitAnd$(true, 1L));
      assertEquals(3L, ArithmeticIntrinsics.$bitOr$(true, 2L));
      assertEquals(0L, ArithmeticIntrinsics.$bitXor$(true, 1L));
      assertEquals(1L, ArithmeticIntrinsics.$bitAnd$(BigInteger.ONE, true));
    }

    @Test
    public void bitwiseUnsupported() {
      assertEquals(
          "unsupported operand type(s) for &: 'float' and 'float'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$bitAnd$(1.5, 2.5)).getMessage());
      assertEquals(
          "unsupported operand type(s) for |: 'str' and 'int'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$bitOr$("a", 1L)).getMessage());
    }
  }

  @Nested
  public final class UnaryOp {

    @Test
    public void negLongs() {
      assertEquals(-5L, ArithmeticIntrinsics.$neg$(5L));
      assertEquals(5L, ArithmeticIntrinsics.$neg$(-5L));
      assertEquals(0L, ArithmeticIntrinsics.$neg$(0L));
      // Long.MIN_VALUE can't be negated in a Long.
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$neg$(Long.MIN_VALUE));
    }

    @Test
    public void negDoubles() {
      assertEquals(-0.5, ArithmeticIntrinsics.$neg$(0.5));
      assertEquals(0.0, ArithmeticIntrinsics.$neg$(-0.0));   // -(-0.0) == 0.0
      assertEquals(-0.0, ArithmeticIntrinsics.$neg$(0.0));   // -( 0.0) == -0.0
      assertTrue(Double.isNaN((double) ArithmeticIntrinsics.$neg$(Double.NaN)));
    }

    @Test
    public void negBooleans() {
      assertEquals(-1L, ArithmeticIntrinsics.$neg$(true));
      assertEquals(0L, ArithmeticIntrinsics.$neg$(false));
    }

    @Test
    public void negUnsupported() {
      assertEquals(
          "bad operand type for unary -: 'str'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$neg$("x")).getMessage());
    }

    @Test
    public void posOperands() {
      assertEquals(5L, ArithmeticIntrinsics.$pos$(5L));
      assertEquals(-5L, ArithmeticIntrinsics.$pos$(-5L));
      assertEquals(-0.0, ArithmeticIntrinsics.$pos$(-0.0));
      assertEquals(BigInteger.TEN.pow(30), ArithmeticIntrinsics.$pos$(BigInteger.TEN.pow(30)));
      // bool → int.
      assertEquals(1L, ArithmeticIntrinsics.$pos$(true));
      assertEquals(0L, ArithmeticIntrinsics.$pos$(false));
      assertEquals(
          "bad operand type for unary +: 'str'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$pos$("x")).getMessage());
    }

    @Test
    public void invertOperands() {
      assertEquals(-1L, ArithmeticIntrinsics.$invert$(0L));
      assertEquals(0L, ArithmeticIntrinsics.$invert$(-1L));
      assertEquals(Long.MAX_VALUE, ArithmeticIntrinsics.$invert$(Long.MIN_VALUE));
      assertEquals(Long.MIN_VALUE, ArithmeticIntrinsics.$invert$(Long.MAX_VALUE));
      assertEquals(-1L, ArithmeticIntrinsics.$invert$(BigInteger.ZERO));   // normalized
      assertEquals(-11L, ArithmeticIntrinsics.$invert$(BigInteger.TEN));
      // bool → int.
      assertEquals(-2L, ArithmeticIntrinsics.$invert$(true));
      assertEquals(-1L, ArithmeticIntrinsics.$invert$(false));
      assertEquals(
          "bad operand type for unary ~: 'float'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$invert$(1.5)).getMessage());
    }
  }

  @Nested
  public final class AugmentedAssignment {

    @Test
    public void augmentedAssignmentDelegates() {
      assertEquals(3L, ArithmeticIntrinsics.$iadd$(1L, 2L));
      assertEquals(-1L, ArithmeticIntrinsics.$isub$(1L, 2L));
      assertEquals(6L, ArithmeticIntrinsics.$imul$(2L, 3L));
      assertEquals(0.5, ArithmeticIntrinsics.$idiv$(1L, 2L));
      assertEquals(-4L, ArithmeticIntrinsics.$ifloorDiv$(-7L, 2L));
      assertEquals(1L, ArithmeticIntrinsics.$imod$(-7L, 2L));
      assertEquals(1024L, ArithmeticIntrinsics.$ipow$(2L, 10L));
      assertEquals(16L, ArithmeticIntrinsics.$ilshift$(1L, 4L));
      assertEquals(-4L, ArithmeticIntrinsics.$irshift$(-16L, 2L));
      assertEquals(1L, ArithmeticIntrinsics.$iand$(5L, 3L));
      assertEquals(7L, ArithmeticIntrinsics.$ior$(5L, 3L));
      assertEquals(6L, ArithmeticIntrinsics.$ixor$(5L, 3L));
      assertEquals("ababab", ArithmeticIntrinsics.$imul$("ab", 3L));
    }
  }

  @Nested
  public final class ErrorMessage {

    @Test
    public void typeErrorMessages() {
      assertEquals("unsupported operand type(s) for +: 'int' and 'str'",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$add$(1L, "a")).getMessage());
      assertEquals("unsupported operand type(s) for -: 'str' and 'str'",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$sub$("a", "b")).getMessage());
      assertEquals("unsupported operand type(s) for *: 'NoneType' and 'NoneType'",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$mul$(PyNone.INSTANCE, PyNone.INSTANCE)).getMessage());
      assertEquals("unsupported operand type(s) for ** or pow(): 'str' and 'str'",
          assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$pow$("a", "b")).getMessage());
    }

    @Test
    public void zeroDivisionErrorMessages() {
      assertEquals("division by zero",
          assertThrows(PyZeroDivisionError.class, () -> ArithmeticIntrinsics.$div$(1L, 0L)).getMessage());
      assertEquals("float division by zero",
          assertThrows(PyZeroDivisionError.class, () -> ArithmeticIntrinsics.$div$(1.0, 0.0)).getMessage());
      assertEquals("integer division or modulo by zero",
          assertThrows(PyZeroDivisionError.class, () -> ArithmeticIntrinsics.$floorDiv$(1L, 0L)).getMessage());
      assertEquals("integer modulo by zero",
          assertThrows(PyZeroDivisionError.class, () -> ArithmeticIntrinsics.$mod$(1L, 0L)).getMessage());
      assertEquals("float floor division by zero",
          assertThrows(PyZeroDivisionError.class, () -> ArithmeticIntrinsics.$floorDiv$(1.0, 0.0)).getMessage());
      assertEquals("float modulo by zero",
          assertThrows(PyZeroDivisionError.class, () -> ArithmeticIntrinsics.$mod$(1.0, 0.0)).getMessage());
    }

    @Test
    public void valueErrorMessages() {
      assertEquals("negative shift count",
          assertThrows(PyValueError.class, () -> ArithmeticIntrinsics.$lshift$(1L, -1L)).getMessage());
      assertEquals("negative shift count",
          assertThrows(PyValueError.class, () -> ArithmeticIntrinsics.$rshift$(1L, -1L)).getMessage());
    }
  }
}