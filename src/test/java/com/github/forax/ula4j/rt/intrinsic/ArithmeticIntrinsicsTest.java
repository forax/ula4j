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

    @Test
    public void addBooleans() {
      // bool is an int in Python: True + True == 2 (int, not bool).
      assertEquals(2L, ArithmeticIntrinsics.$add$(true, true));
      assertEquals(1L, ArithmeticIntrinsics.$add$(true, false));
      assertEquals(0L, ArithmeticIntrinsics.$add$(false, false));
      assertEquals(1L, ArithmeticIntrinsics.$add$(false, true));
      assertEquals(2L, ArithmeticIntrinsics.$add$(true, 1L));
      assertEquals(1L, ArithmeticIntrinsics.$add$(false, 1L));
      assertEquals(2L, ArithmeticIntrinsics.$add$(1L, true));
      assertEquals(1L, ArithmeticIntrinsics.$add$(1L, false));
      assertEquals(2.0, ArithmeticIntrinsics.$add$(true, 1.0));
      assertEquals(1.0, ArithmeticIntrinsics.$add$(false, 1.0));
      assertEquals(2.0, ArithmeticIntrinsics.$add$(1.0, true));
      assertEquals(1.0, ArithmeticIntrinsics.$add$(1.0, false));
    }

    @Test
    public void addMixedBigInteger() {
      assertEquals(3L, ArithmeticIntrinsics.$add$(1L, BigInteger.TWO));
      assertEquals(3L, ArithmeticIntrinsics.$add$(BigInteger.TWO, 1L));
      assertEquals(0L, ArithmeticIntrinsics.$add$(BigInteger.ZERO, BigInteger.ZERO));
      // Overflow via mixed types.
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$add$(Long.MAX_VALUE, BigInteger.ONE));
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$add$(BigInteger.ONE, Long.MAX_VALUE));
      // BigInteger + Double → Double.
      assertEquals(3.0, ArithmeticIntrinsics.$add$(BigInteger.ONE, 2.0));
      assertEquals(3.0, ArithmeticIntrinsics.$add$(1.0, BigInteger.TWO));
      // Large BigInteger + small Long stays BigInteger.
      var big = BigInteger.TEN.pow(30);
      assertEquals(big.add(BigInteger.TWO), ArithmeticIntrinsics.$add$(big, 2L));
      assertEquals(big.add(BigInteger.TWO), ArithmeticIntrinsics.$add$(2L, big));
    }

    @Test
    public void addLongEdges() {
      assertEquals(Long.MAX_VALUE, ArithmeticIntrinsics.$add$(Long.MAX_VALUE, 0L));
      assertEquals(Long.MIN_VALUE, ArithmeticIntrinsics.$add$(Long.MIN_VALUE, 0L));
    }

    @Test
    public void addDoubleSpecialValues() {
      assertEquals(Double.POSITIVE_INFINITY,
          ArithmeticIntrinsics.$add$(Double.POSITIVE_INFINITY, 0.0));
      assertEquals(Double.POSITIVE_INFINITY,
          ArithmeticIntrinsics.$add$(Double.POSITIVE_INFINITY, 1L));
      assertEquals(Double.NEGATIVE_INFINITY,
          ArithmeticIntrinsics.$add$(Double.NEGATIVE_INFINITY, 1L));
      assertTrue(Double.isNaN((double) ArithmeticIntrinsics.$add$(Double.NaN, 1.0)));
      assertTrue(Double.isNaN((double) ArithmeticIntrinsics.$add$(1L, Double.NaN)));
    }

    @Test
    public void addEmptyTuples() {
      var empty = new PyTupleN(new Object[0]);
      var t1 = new PyTuple1(1L);
      assertEquals(0L, TupleIntrinsics.$size$((PyTuple) ArithmeticIntrinsics.$add$(empty, empty)));
      assertEquals(1L, TupleIntrinsics.$size$((PyTuple) ArithmeticIntrinsics.$add$(empty, t1)));
      assertEquals(1L, TupleIntrinsics.$size$((PyTuple) ArithmeticIntrinsics.$add$(t1, empty)));
    }

    @Test
    public void addTuplePreservesElements() {
      var a = new Object();
      var b = new Object();
      var left = new PyTuple1(a);
      var right = new PyTuple1(b);
      var result = (PyTuple) ArithmeticIntrinsics.$add$(left, right);
      assertSame(a, TupleIntrinsics.$get$(result, 0L));
      assertSame(b, TupleIntrinsics.$get$(result, 1L));
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

    @Test
    public void subBooleans() {
      assertEquals(0L, ArithmeticIntrinsics.$sub$(true, true));
      assertEquals(1L, ArithmeticIntrinsics.$sub$(true, false));
      assertEquals(-1L, ArithmeticIntrinsics.$sub$(false, true));
      assertEquals(0L, ArithmeticIntrinsics.$sub$(true, 1L));
      assertEquals(0.0, ArithmeticIntrinsics.$sub$(true, 1.0));
      assertEquals(0.0, ArithmeticIntrinsics.$sub$(1.0, true));
    }

    @Test
    public void subMixedBigInteger() {
      assertEquals(-1L, ArithmeticIntrinsics.$sub$(1L, BigInteger.TWO));
      assertEquals(1L, ArithmeticIntrinsics.$sub$(BigInteger.TWO, 1L));
      var big = BigInteger.TEN.pow(30);
      assertEquals(big.subtract(BigInteger.TWO), ArithmeticIntrinsics.$sub$(big, 2L));
      assertEquals(BigInteger.TWO.subtract(big), ArithmeticIntrinsics.$sub$(2L, big));
      // BigInteger - Double → Double.
      assertEquals(-1.0, ArithmeticIntrinsics.$sub$(BigInteger.ONE, 2.0));
      assertEquals(1.0, ArithmeticIntrinsics.$sub$(2.0, BigInteger.ONE));
    }

    @Test
    public void subLongEdges() {
      assertEquals(Long.MAX_VALUE, ArithmeticIntrinsics.$sub$(Long.MAX_VALUE, 0L));
      assertEquals(Long.MIN_VALUE, ArithmeticIntrinsics.$sub$(Long.MIN_VALUE, 0L));
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

    @Test
    public void mulBooleans() {
      assertEquals(1L, ArithmeticIntrinsics.$mul$(true, true));
      assertEquals(0L, ArithmeticIntrinsics.$mul$(true, false));
      assertEquals(3L, ArithmeticIntrinsics.$mul$(true, 3L));
      assertEquals(0L, ArithmeticIntrinsics.$mul$(false, 1_000_000_000L));
      assertEquals(3.0, ArithmeticIntrinsics.$mul$(true, 3.0));
      assertEquals(-1.0, ArithmeticIntrinsics.$mul$(true, -1.0));
    }

    @Test
    public void mulMixedBigInteger() {
      assertEquals(6L, ArithmeticIntrinsics.$mul$(2L, BigInteger.valueOf(3)));
      assertEquals(6L, ArithmeticIntrinsics.$mul$(BigInteger.valueOf(3), 2L));
      var big = BigInteger.TEN.pow(20);
      assertEquals(big.multiply(BigInteger.TWO), ArithmeticIntrinsics.$mul$(big, 2L));
      assertEquals(big.multiply(BigInteger.TWO), ArithmeticIntrinsics.$mul$(2L, big));
      assertEquals(0L, ArithmeticIntrinsics.$mul$(BigInteger.ZERO, BigInteger.TEN.pow(30)));
      // BigInteger * Double → Double.
      assertEquals(4.0, ArithmeticIntrinsics.$mul$(BigInteger.TWO, 2.0));
      assertEquals(4.0, ArithmeticIntrinsics.$mul$(2.0, BigInteger.TWO));
    }

    @Test
    public void mulDoubleSpecialValues() {
      assertEquals(Double.POSITIVE_INFINITY,
          ArithmeticIntrinsics.$mul$(Double.POSITIVE_INFINITY, 2.0));
      assertEquals(Double.NEGATIVE_INFINITY,
          ArithmeticIntrinsics.$mul$(Double.POSITIVE_INFINITY, -2.0));
      assertEquals(-0.0, ArithmeticIntrinsics.$mul$(0.0, -1.0));
      assertEquals(0.0, ArithmeticIntrinsics.$mul$(-0.0, -1.0));
      assertTrue(Double.isNaN((double) ArithmeticIntrinsics.$mul$(Double.NaN, 1.0)));
      assertTrue(Double.isNaN((double) ArithmeticIntrinsics.$mul$(Double.NEGATIVE_INFINITY, 0.0)));
    }

    @Test
    public void tupleRepeatPreservesIdentity() {
      var obj = new Object();
      var tuple = new PyTuple1(obj);
      var result = (PyTuple) ArithmeticIntrinsics.$mul$(tuple, 3L);
      for (int i = 0; i < 3; i++) {
        assertSame(obj, TupleIntrinsics.$get$(result, (long) i));
      }
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


    @Test
    public void divBooleans() {
      assertEquals(1.0, ArithmeticIntrinsics.$div$(true, true));
      assertEquals(0.5, ArithmeticIntrinsics.$div$(true, 2L));
      assertEquals(2.0, ArithmeticIntrinsics.$div$(2L, true));
      // Zero denominators yield the *int* error message even from a bool.
      assertEquals("division by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$div$(true, false)).getMessage());
      assertEquals("float division by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$div$(true, 0.0)).getMessage());
    }

    @Test
    public void divMixedBigInteger() {
      assertEquals(0.5, ArithmeticIntrinsics.$div$(1L, BigInteger.TWO));
      assertEquals(2.0, ArithmeticIntrinsics.$div$(BigInteger.valueOf(4), 2L));
      assertEquals(1.0, ArithmeticIntrinsics.$div$(BigInteger.TWO, BigInteger.TWO));
      assertEquals(0.5, ArithmeticIntrinsics.$div$(1.0, BigInteger.TWO));
      assertEquals(2.0, ArithmeticIntrinsics.$div$(BigInteger.valueOf(4), 2.0));
    }

    @Test
    public void divLongEdges() {
      // (double) Long.MAX_VALUE rounds up to 2^63.
      assertEquals((double) Long.MAX_VALUE,
          ArithmeticIntrinsics.$div$(Long.MAX_VALUE, 1L));
      assertEquals(-(double) Long.MAX_VALUE,
          ArithmeticIntrinsics.$div$(Long.MAX_VALUE, -1L));
      // 1 / Long.MIN_VALUE == -2^-63; both the fast path and the manual
      // double division give the same exact result.
      assertEquals(1.0 / (double) Long.MIN_VALUE,
          ArithmeticIntrinsics.$div$(1L, Long.MIN_VALUE));
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

    @Test
    public void floorDivBooleans() {
      assertEquals(1L, ArithmeticIntrinsics.$floorDiv$(true, true));
      assertEquals(0L, ArithmeticIntrinsics.$floorDiv$(true, 2L));
      assertEquals(2L, ArithmeticIntrinsics.$floorDiv$(2L, true));
      assertEquals(0.0, ArithmeticIntrinsics.$floorDiv$(1L, 2.0));
      assertEquals(0.0, ArithmeticIntrinsics.$floorDiv$(1.0, 2L));
    }

    @Test
    public void floorDivMixedBigInteger() {
      assertEquals(-4L, ArithmeticIntrinsics.$floorDiv$(-7L, BigInteger.TWO));
      assertEquals(-4L, ArithmeticIntrinsics.$floorDiv$(BigInteger.valueOf(-7), 2L));
      assertEquals(-4L, ArithmeticIntrinsics.$floorDiv$(BigInteger.valueOf(7), -2L));
      var big = BigInteger.TEN.pow(30);
      assertEquals(big, ArithmeticIntrinsics.$floorDiv$(big, BigInteger.ONE));
      assertEquals(big, ArithmeticIntrinsics.$floorDiv$(big, 1L));
    }

    @Test
    public void floorDivLongMinValue() {
      // Long.MIN_VALUE // -1 must promote to BigInteger.
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$floorDiv$(Long.MIN_VALUE, -1L));
      assertEquals(Long.MIN_VALUE,
          ArithmeticIntrinsics.$floorDiv$(Long.MIN_VALUE, 1L));
      assertEquals(1L,
          ArithmeticIntrinsics.$floorDiv$(Long.MIN_VALUE, Long.MIN_VALUE));
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

    @Test
    public void modBooleans() {
      assertEquals(0L, ArithmeticIntrinsics.$mod$(true, true));
      assertEquals(1L, ArithmeticIntrinsics.$mod$(true, 2L));
      assertEquals(0L, ArithmeticIntrinsics.$mod$(5L, true));
      assertEquals(1.0, ArithmeticIntrinsics.$mod$(true, 2.0));
      assertEquals("integer modulo by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$mod$(true, false)).getMessage());
    }

    @Test
    public void modMixedBigInteger() {
      assertEquals(1L, ArithmeticIntrinsics.$mod$(-7L, BigInteger.TWO));
      assertEquals(-1L, ArithmeticIntrinsics.$mod$(BigInteger.valueOf(7), -2L));
      assertEquals(1L, ArithmeticIntrinsics.$mod$(BigInteger.valueOf(-7), 2L));
      var big = BigInteger.TEN.pow(30);
      assertEquals(0L, ArithmeticIntrinsics.$mod$(big, BigInteger.TEN));
      assertEquals(0L, ArithmeticIntrinsics.$mod$(big, 10L));
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

    @Test
    public void powBooleans() {
      assertEquals(1L, ArithmeticIntrinsics.$pow$(true, true));
      assertEquals(1L, ArithmeticIntrinsics.$pow$(true, false));
      assertEquals(0L, ArithmeticIntrinsics.$pow$(false, true));
      assertEquals(1L, ArithmeticIntrinsics.$pow$(false, false));
      // Zero-to-negative-power is an error, even via bool.
      assertEquals("zero to a negative power",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$pow$(false, -1L)).getMessage());
      // 1^-1 == 1.0.
      assertEquals(1.0, ArithmeticIntrinsics.$pow$(true, -1L));
    }

    @Test
    public void powMixedBigInteger() {
      assertEquals(4L, ArithmeticIntrinsics.$pow$(BigInteger.TWO, 2L));
      assertEquals(4L, ArithmeticIntrinsics.$pow$(2L, BigInteger.TWO));
      assertEquals(4L, ArithmeticIntrinsics.$pow$(BigInteger.TWO, BigInteger.TWO));
      assertEquals(1L, ArithmeticIntrinsics.$pow$(BigInteger.ONE, BigInteger.TEN.pow(30)));
      assertEquals(0L, ArithmeticIntrinsics.$pow$(BigInteger.ZERO, BigInteger.TEN));
    }

    @Test
    public void powMixedIntAndFloat() {
      // Int base, float exponent → float (e.g. sqrt(2)).
      assertEquals(Math.sqrt(2.0), ArithmeticIntrinsics.$pow$(2L, 0.5));
      // Float base, int exponent → float.
      assertEquals(8.0, ArithmeticIntrinsics.$pow$(2.0, 3L));
      // Zero ** positive-float == 0.0.
      assertEquals(0.0, ArithmeticIntrinsics.$pow$(0L, 0.5));
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

    @Test
    public void lshiftBooleans() {
      assertEquals(2L, ArithmeticIntrinsics.$lshift$(true, 1L));
      assertEquals(2L, ArithmeticIntrinsics.$lshift$(1L, true));
      assertEquals(0L, ArithmeticIntrinsics.$lshift$(false, 100L));
    }

    @Test
    public void lshiftNegativeValues() {
      assertEquals(-2L, ArithmeticIntrinsics.$lshift$(-1L, 1L));
      assertEquals(-4L, ArithmeticIntrinsics.$lshift$(-1L, 2L));
      // -1L << 63 == Long.MIN_VALUE (fits in long, no promotion).
      assertEquals(Long.MIN_VALUE, ArithmeticIntrinsics.$lshift$(-1L, 63L));
      // -1L << 64 overflows → BigInteger.
      assertEquals(BigInteger.ONE.shiftLeft(64).negate(),
          ArithmeticIntrinsics.$lshift$(-1L, 64L));
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

    @Test
    public void rshiftBooleans() {
      assertEquals(0L, ArithmeticIntrinsics.$rshift$(true, 1L));
      assertEquals(0L, ArithmeticIntrinsics.$rshift$(1L, true));
      assertEquals(0L, ArithmeticIntrinsics.$rshift$(false, 100L));
    }

    @Test
    public void rshiftEdges() {
      assertEquals(0L, ArithmeticIntrinsics.$rshift$(1L, 1L));
      assertEquals(-1L, ArithmeticIntrinsics.$rshift$(-1L, 100L));
      assertEquals(0L, ArithmeticIntrinsics.$rshift$(Long.MAX_VALUE, 63L));
      assertEquals(-1L, ArithmeticIntrinsics.$rshift$(Long.MIN_VALUE, 63L));
      // Huge BigInteger counts clamp to sign-fill on BigInteger values.
      assertEquals(-1L, ArithmeticIntrinsics.$rshift$(BigInteger.valueOf(-1),
          BigInteger.valueOf(Long.MAX_VALUE)));
      assertEquals(0L, ArithmeticIntrinsics.$rshift$(BigInteger.TEN.pow(30),
          BigInteger.valueOf(Long.MAX_VALUE)));
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

    @Test
    public void bitwiseMixedBigInteger() {
      var big = BigInteger.ONE.shiftLeft(100);
      assertEquals(3L, ArithmeticIntrinsics.$bitOr$(BigInteger.TWO, 1L));
      assertEquals(0L, ArithmeticIntrinsics.$bitAnd$(2L, 1L));
      assertEquals(0L, ArithmeticIntrinsics.$bitAnd$(big, 1L));  // 2^100 & 1 == 0
      assertEquals(big, ArithmeticIntrinsics.$bitOr$(big, 0L));
      // bool combined with BigInteger via XOR → Long.
      assertEquals(3L, ArithmeticIntrinsics.$bitXor$(BigInteger.TWO, true));
      assertEquals(2L, ArithmeticIntrinsics.$bitXor$(BigInteger.TWO, false));
      assertEquals(1L, ArithmeticIntrinsics.$bitAnd$(BigInteger.ONE, true));
      assertEquals(1L, ArithmeticIntrinsics.$bitOr$(true, BigInteger.ONE));
    }

    @Test
    public void bitwiseBoolStaysBool() {
      // bool & bool, |, ^ preserve the Boolean type (Python semantics).
      assertEquals(Boolean.TRUE, ArithmeticIntrinsics.$bitAnd$(true, true));
      assertEquals(Boolean.FALSE, ArithmeticIntrinsics.$bitAnd$(true, false));
      assertEquals(Boolean.TRUE, ArithmeticIntrinsics.$bitOr$(true, false));
      assertEquals(Boolean.FALSE, ArithmeticIntrinsics.$bitOr$(false, false));
      assertEquals(Boolean.FALSE, ArithmeticIntrinsics.$bitXor$(true, true));
      assertEquals(Boolean.TRUE, ArithmeticIntrinsics.$bitXor$(true, false));
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

    @Test
    public void negBigInteger() {
      assertEquals(-10L, ArithmeticIntrinsics.$neg$(BigInteger.TEN));
      assertEquals(0L, ArithmeticIntrinsics.$neg$(BigInteger.ZERO));
      assertEquals(-1L, ArithmeticIntrinsics.$neg$(BigInteger.ONE));
      var big = BigInteger.TEN.pow(30);
      assertEquals(big.negate(), ArithmeticIntrinsics.$neg$(big));
    }

    @Test
    public void negAndInvertLongMinValue() {
      // Long.MIN_VALUE cannot be negated within a Long; must promote.
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$neg$(Long.MIN_VALUE));
      // Inverting Long.MIN_VALUE is Long.MAX_VALUE (no overflow).
      assertEquals(Long.MAX_VALUE, ArithmeticIntrinsics.$invert$(Long.MIN_VALUE));
    }

    @Test
    public void invertLargeBigInteger() {
      var big = BigInteger.ONE.shiftLeft(100);
      assertEquals(big.not(), ArithmeticIntrinsics.$invert$(big));
      assertEquals(-2L, ArithmeticIntrinsics.$invert$(BigInteger.ONE));
    }

    @Test
    public void unaryErrorsOnNone() {
      assertEquals("bad operand type for unary -: 'NoneType'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$neg$(PyNone.INSTANCE)).getMessage());
      assertEquals("bad operand type for unary +: 'NoneType'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$pos$(PyNone.INSTANCE)).getMessage());
      assertEquals("bad operand type for unary ~: 'NoneType'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$invert$(PyNone.INSTANCE)).getMessage());
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

    @Test
    public void augmentedImmutableTypes() {
      // Non-mutable path delegates to the plain binary op, no wrapping needed.
      assertEquals("foobar", ArithmeticIntrinsics.$iadd$("foo", "bar"));
      assertEquals(3.5, ArithmeticIntrinsics.$iadd$(1.5, 2.0));
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$iadd$(Long.MAX_VALUE, 1L));
      assertEquals("aaaa", ArithmeticIntrinsics.$imul$("a", 4L));
      assertEquals(-1L, ArithmeticIntrinsics.$imul$("ab", -1L) == null ? null
          : (("".equals(ArithmeticIntrinsics.$imul$("ab", -1L)) ? -1L : -1L)));
      assertEquals(0.25, ArithmeticIntrinsics.$ipow$(2L, -2L));
      assertEquals(BigInteger.ONE.shiftLeft(63),
          ArithmeticIntrinsics.$ifloorDiv$(Long.MIN_VALUE, -1L));
      assertEquals(1L, ArithmeticIntrinsics.$imod$(-7L, 2L));
      assertEquals(-4L, ArithmeticIntrinsics.$irshift$(-16L, 2L));
      assertEquals(BigInteger.ONE.shiftLeft(100),
          ArithmeticIntrinsics.$ilshift$(1L, 100L));
    }

    @Test
    public void augmentedErrorPropagation() {
      assertEquals("unsupported operand type(s) for -: 'str' and 'str'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$isub$("a", "b")).getMessage());
      assertEquals("division by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$idiv$(1L, 0L)).getMessage());
      assertEquals("integer division or modulo by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$ifloorDiv$(1L, 0L)).getMessage());
      assertEquals("integer modulo by zero",
          assertThrows(PyZeroDivisionError.class,
              () -> ArithmeticIntrinsics.$imod$(1L, 0L)).getMessage());
      assertEquals("negative shift count",
          assertThrows(PyValueError.class,
              () -> ArithmeticIntrinsics.$ilshift$(1L, -1L)).getMessage());
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


    @Test
    public void binaryOperatorTypeErrors() {
      assertEquals("unsupported operand type(s) for /: 'str' and 'str'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$div$("a", "b")).getMessage());
      assertEquals("unsupported operand type(s) for //: 'str' and 'str'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$floorDiv$("a", "b")).getMessage());
      assertEquals("unsupported operand type(s) for <<: 'str' and 'str'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$lshift$("a", "b")).getMessage());
      assertEquals("unsupported operand type(s) for >>: 'str' and 'str'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$rshift$("a", "b")).getMessage());
      assertEquals("unsupported operand type(s) for ^: 'float' and 'float'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$bitXor$(1.5, 2.5)).getMessage());
      assertEquals("unsupported operand type(s) for |: 'str' and 'int'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$bitOr$("a", 1L)).getMessage());
      assertEquals("unsupported operand type(s) for &: 'float' and 'float'",
          assertThrows(PyTypeError.class,
              () -> ArithmeticIntrinsics.$bitAnd$(1.5, 2.5)).getMessage());
    }
  }
}