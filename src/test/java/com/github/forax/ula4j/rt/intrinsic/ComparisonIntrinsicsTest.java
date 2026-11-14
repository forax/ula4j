package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.RT;
import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ComparisonIntrinsicsTest {

  private static PyTuple pyTuple(Object... elements) {
    return TupleIntrinsics.fromArray(elements);
  }

  @SafeVarargs
  private static <E> PyList<E> pyList(E... elements) {
    return new PyList<>(elements);
  }


  @Nested
  public final class EqualityPrimitive {

    @Test
    public void equalityOfInts() {
      assertTrue(ComparisonIntrinsics.$eq$(0L, 0L));
      assertTrue(ComparisonIntrinsics.$eq$(1L, 1L));
      assertFalse(ComparisonIntrinsics.$eq$(1L, 2L));
      assertFalse(ComparisonIntrinsics.$eq$(2L, 1L));
      assertTrue(ComparisonIntrinsics.$eq$(Long.MAX_VALUE, Long.MAX_VALUE));
      assertTrue(ComparisonIntrinsics.$eq$(Long.MIN_VALUE, Long.MIN_VALUE));
      assertFalse(ComparisonIntrinsics.$eq$(Long.MAX_VALUE, Long.MIN_VALUE));
    }

    @Test
    public void equalityOfBooleansAndInts() {
      // bool is an int subclass in Python: every arithmetic/comparison op treats True as 1.
      assertTrue(ComparisonIntrinsics.$eq$(true, true));
      assertTrue(ComparisonIntrinsics.$eq$(false, false));
      assertFalse(ComparisonIntrinsics.$eq$(true, false));
      assertFalse(ComparisonIntrinsics.$eq$(false, true));
      assertTrue(ComparisonIntrinsics.$eq$(true, 1L));
      assertTrue(ComparisonIntrinsics.$eq$(1L, true));
      assertTrue(ComparisonIntrinsics.$eq$(false, 0L));
      assertTrue(ComparisonIntrinsics.$eq$(true, 1.0));
      assertTrue(ComparisonIntrinsics.$eq$(false, 0.0));
      assertTrue(ComparisonIntrinsics.$eq$(false, -0.0));
      assertTrue(ComparisonIntrinsics.$eq$(true, BigInteger.ONE));
      assertTrue(ComparisonIntrinsics.$eq$(false, BigInteger.ZERO));
      assertFalse(ComparisonIntrinsics.$eq$(true, 0L));
      assertFalse(ComparisonIntrinsics.$eq$(false, 1L));
    }

    @Test
    public void equalityOfFloats() {
      assertTrue(ComparisonIntrinsics.$eq$(0.0, 0.0));
      assertTrue(ComparisonIntrinsics.$eq$(0.0, -0.0));
      assertTrue(ComparisonIntrinsics.$eq$(-0.0, 0.0));
      assertTrue(ComparisonIntrinsics.$eq$(-0.0, -0.0));
      assertTrue(ComparisonIntrinsics.$eq$(1.5, 1.5));
      assertFalse(ComparisonIntrinsics.$eq$(1.5, 1.6));
      assertTrue(ComparisonIntrinsics.$eq$(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
      assertTrue(ComparisonIntrinsics.$eq$(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY));
      assertFalse(ComparisonIntrinsics.$eq$(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY));
    }

    @Test
    public void equalityOfNaN() {
      assertFalse(ComparisonIntrinsics.$eq$(Double.NaN, Double.NaN));
      assertTrue(ComparisonIntrinsics.$ne$(Double.NaN, Double.NaN));
      assertFalse(ComparisonIntrinsics.$eq$(Double.NaN, 0.0));
      assertFalse(ComparisonIntrinsics.$eq$(0.0, Double.NaN));
      assertFalse(ComparisonIntrinsics.$eq$(Double.NaN, 0L));
      assertFalse(ComparisonIntrinsics.$eq$(0L, Double.NaN));
    }

    @Test
    public void equalityOfStrings() {
      assertTrue(ComparisonIntrinsics.$eq$("", ""));
      assertTrue(ComparisonIntrinsics.$eq$("abc", "abc"));
      assertFalse(ComparisonIntrinsics.$eq$("abc", "abd"));
      assertFalse(ComparisonIntrinsics.$eq$("abc", "ABC"));
      assertFalse(ComparisonIntrinsics.$eq$("", "a"));
      assertTrue(ComparisonIntrinsics.$eq$("héllo", "héllo"));
    }

    @Test
    public void equalityOfNone() {
      assertTrue(ComparisonIntrinsics.$eq$(PyNone.INSTANCE, PyNone.INSTANCE));
    }

    @Test
    public void equalityOfBigIntegers() {
      assertTrue(ComparisonIntrinsics.$eq$(BigInteger.TEN, BigInteger.TEN));
      assertFalse(ComparisonIntrinsics.$eq$(BigInteger.TEN, BigInteger.ONE));
      var twoTo63 = BigInteger.ONE.shiftLeft(63);
      assertTrue(ComparisonIntrinsics.$eq$(twoTo63, twoTo63));
      assertTrue(ComparisonIntrinsics.$eq$(twoTo63, BigInteger.ONE.shiftLeft(63)));
      assertTrue(ComparisonIntrinsics.$eq$(BigInteger.TEN, 10L));
      assertTrue(ComparisonIntrinsics.$eq$(10L, BigInteger.TEN));
      assertTrue(ComparisonIntrinsics.$eq$(BigInteger.TEN, 10.0));
      assertFalse(ComparisonIntrinsics.$eq$(twoTo63, Long.MAX_VALUE));
    }

    @Test
    public void equalityAcrossIncompatibleTypes() {
      // Different Python types are never equal, regardless of the values.
      assertFalse(ComparisonIntrinsics.$eq$(1L, "1"));
      assertFalse(ComparisonIntrinsics.$eq$("1", 1L));
      assertFalse(ComparisonIntrinsics.$eq$(1L, PyNone.INSTANCE));
      assertFalse(ComparisonIntrinsics.$eq$(PyNone.INSTANCE, 1L));
      assertFalse(ComparisonIntrinsics.$eq$("", PyNone.INSTANCE));
      assertFalse(ComparisonIntrinsics.$eq$(PyNone.INSTANCE, ""));
      assertFalse(ComparisonIntrinsics.$eq$(true, "true"));
      assertFalse(ComparisonIntrinsics.$eq$(1L, pyTuple(1L)));
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(1L), 1L));
    }

    @Test
    public void neIsNotEq() {
      assertFalse(ComparisonIntrinsics.$ne$(1L, 1L));
      assertTrue(ComparisonIntrinsics.$ne$(1L, 2L));
      assertFalse(ComparisonIntrinsics.$ne$("a", "a"));
      assertTrue(ComparisonIntrinsics.$ne$("a", "b"));
      assertFalse(ComparisonIntrinsics.$ne$(PyNone.INSTANCE, PyNone.INSTANCE));
      assertTrue(ComparisonIntrinsics.$ne$(1L, "1"));
    }

    @Test
    public void equalityOfLongAndDoubleBothDirections() {
      assertTrue(ComparisonIntrinsics.$eq$(1L, 1.0));
      assertTrue(ComparisonIntrinsics.$eq$(1.0, 1L));
      assertFalse(ComparisonIntrinsics.$eq$(1L, 1.5));
      assertFalse(ComparisonIntrinsics.$eq$(1.5, 1L));
      assertTrue(ComparisonIntrinsics.$eq$(0L, 0.0));
      assertTrue(ComparisonIntrinsics.$eq$(0.0, 0L));
      assertTrue(ComparisonIntrinsics.$eq$(0L, -0.0));
      assertTrue(ComparisonIntrinsics.$eq$(-0.0, 0L));
      assertFalse(ComparisonIntrinsics.$eq$(10L, 10.0000001));
    }

    @Test
    public void equalityOfBooleansAndDoublesBothDirections() {
      assertTrue(ComparisonIntrinsics.$eq$(true, 1.0));
      assertTrue(ComparisonIntrinsics.$eq$(1.0, true));
      assertTrue(ComparisonIntrinsics.$eq$(false, 0.0));
      assertTrue(ComparisonIntrinsics.$eq$(0.0, false));
      assertTrue(ComparisonIntrinsics.$eq$(false, -0.0));
      assertFalse(ComparisonIntrinsics.$eq$(true, 0.0));
      assertFalse(ComparisonIntrinsics.$eq$(0.0, true));
    }

    @Test
    public void equalityOfLongAndDoubleAtBoundary() {
      // (double) Long.MAX_VALUE rounds to 2**63, which is not Long.MAX_VALUE.
      assertFalse(ComparisonIntrinsics.$eq$(Long.MAX_VALUE, (double) Long.MAX_VALUE));
      assertFalse(ComparisonIntrinsics.$eq$((double) Long.MAX_VALUE, Long.MAX_VALUE));
      // Long.MIN_VALUE == -2**63 is exactly representable.
      assertTrue(ComparisonIntrinsics.$eq$(Long.MIN_VALUE, (double) Long.MIN_VALUE));
      assertTrue(ComparisonIntrinsics.$eq$((double) Long.MIN_VALUE, Long.MIN_VALUE));
    }

    @Test
    public void equalityWithUnsupportedTypeThrows() {
      // Equality is only defined for supported runtime types for now.
      assertThrows(UnimplementedFeatureError.class,
          () -> ComparisonIntrinsics.$eq$(new Object(), new Object()));
      assertThrows(UnimplementedFeatureError.class,
          () -> ComparisonIntrinsics.$ne$(new Object(), new Object()));
      // The dispatcher is driven by the *left* operand.
      assertThrows(UnimplementedFeatureError.class,
          () -> ComparisonIntrinsics.$eq$(new Object(), PyNone.INSTANCE));
      // …but a supported left operand gracefully returns false.
      assertFalse(ComparisonIntrinsics.$eq$(PyNone.INSTANCE, new Object()));
      assertFalse(ComparisonIntrinsics.$eq$(1L, new Object()));
    }

    @Test
    public void stringsAndNone() {
      assertTrue(ComparisonIntrinsics.$eq$("abc", "abc"), "abc" + " == " + "abc");
      assertTrue(ComparisonIntrinsics.$eq$("abc", "abc"), "abc" + " == " + "abc");
      assertFalse(ComparisonIntrinsics.$eq$("abc", "abd"), "abc" + " == " + "abd");
      assertFalse(ComparisonIntrinsics.$eq$("abd", "abc"), "abd" + " == " + "abc");
      assertFalse(ComparisonIntrinsics.$eq$("1", 1L), "1" + " == " + 1L);
      assertFalse(ComparisonIntrinsics.$eq$(1L, "1"), 1L + " == " + "1");
      assertTrue(ComparisonIntrinsics.$eq$(PyNone.INSTANCE, PyNone.INSTANCE), PyNone.INSTANCE + " == " + PyNone.INSTANCE);
      assertTrue(ComparisonIntrinsics.$eq$(PyNone.INSTANCE, PyNone.INSTANCE), PyNone.INSTANCE + " == " + PyNone.INSTANCE);
      assertFalse(ComparisonIntrinsics.$eq$(PyNone.INSTANCE, 0L), PyNone.INSTANCE + " == " + 0L);
      assertFalse(ComparisonIntrinsics.$eq$(0L, PyNone.INSTANCE), 0L + " == " + PyNone.INSTANCE);
      assertFalse(ComparisonIntrinsics.$eq$(PyNone.INSTANCE, "None"), PyNone.INSTANCE + " == " + "None");
      assertFalse(ComparisonIntrinsics.$eq$("None", PyNone.INSTANCE), "None" + " == " + PyNone.INSTANCE);
      assertFalse(ComparisonIntrinsics.$eq$(PyNone.INSTANCE, false), PyNone.INSTANCE + " == " + false);
      assertFalse(ComparisonIntrinsics.$eq$(false, PyNone.INSTANCE), false + " == " + PyNone.INSTANCE);
    }
  }


  @Nested
  public final class EqualityIntVsFloat {

    @Test
    public void exactIntFloatComparison() {
      assertFalse(ComparisonIntrinsics.$eq$(9007199254740993L, 9007199254740992.0));  // 2**53+1 vs 2**53
      assertTrue(ComparisonIntrinsics.$gt$(9007199254740993L, 9007199254740992.0));
      assertTrue(ComparisonIntrinsics.$eq$(true, 1L));
      assertTrue(ComparisonIntrinsics.$eq$(1L, 1.0));
      assertFalse(ComparisonIntrinsics.$eq$(Double.NaN, Double.NaN));
      assertTrue(ComparisonIntrinsics.$ne$(Double.NaN, Double.NaN));
    }

    @Test
    public void exactLongDoubleBoundaries() {
      // 2^63 is exactly representable as a double; Long.MAX_VALUE is 2^63 - 1.
      // Exact comparison: Long.MAX_VALUE < 2^63 (never rounds to equal).
      assertFalse(ComparisonIntrinsics.$eq$(Long.MAX_VALUE, 0x1p63));
      assertTrue(ComparisonIntrinsics.$lt$(Long.MAX_VALUE, 0x1p63));
      assertTrue(ComparisonIntrinsics.$gt$(0x1p63, Long.MAX_VALUE));

      // -2^63 == Long.MIN_VALUE is exactly representable, so they are equal.
      assertTrue(ComparisonIntrinsics.$eq$(Long.MIN_VALUE, -0x1p63));
      assertTrue(ComparisonIntrinsics.$ge$(Long.MIN_VALUE, -0x1p63));
      assertTrue(ComparisonIntrinsics.$le$(Long.MIN_VALUE, -0x1p63));

      // Largest double strictly below 2^63 is 2^63 - 2048.
      assertTrue(ComparisonIntrinsics.$lt$(Math.nextDown(0x1p63), Long.MAX_VALUE));

      // Smallest double strictly above -2^63 is -2^63 + 2048.
      assertTrue(ComparisonIntrinsics.$gt$(Math.nextUp(-0x1p63), Long.MIN_VALUE));
    }

    @Test
    public void exactBigIntegerDoubleBoundaries() {
      var twoTo63 = BigInteger.ONE.shiftLeft(63);
      // 2^63 is exactly representable as a double.
      assertTrue(ComparisonIntrinsics.$eq$(twoTo63, 0x1p63));
      assertTrue(ComparisonIntrinsics.$eq$(0x1p63, twoTo63));
      // 2^63 - 1 is strictly less than 2^63 as a double.
      assertTrue(ComparisonIntrinsics.$lt$(twoTo63.subtract(BigInteger.ONE), 0x1p63));

      // Just below Long.MIN_VALUE.
      var belowMin = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE);
      assertTrue(ComparisonIntrinsics.$lt$(belowMin, -0x1p63));
      assertTrue(ComparisonIntrinsics.$gt$(-0x1p63, belowMin));

      // A BigInteger far outside the range of any long.
      var huge = BigInteger.ONE.shiftLeft(1000);
      assertTrue(ComparisonIntrinsics.$gt$(huge, Long.MAX_VALUE));
      assertTrue(ComparisonIntrinsics.$gt$(huge.add(BigInteger.ONE), 0x1p1000));
      assertTrue(ComparisonIntrinsics.$lt$(huge.negate().subtract(BigInteger.ONE), -0x1p1000));
    }

    @Test
    public void bigIntegerVsInfinity() {
      var huge = BigInteger.ONE.shiftLeft(1000);
      assertTrue(ComparisonIntrinsics.$lt$(huge, Double.POSITIVE_INFINITY));
      assertFalse(ComparisonIntrinsics.$gt$(huge, Double.POSITIVE_INFINITY));
      assertTrue(ComparisonIntrinsics.$gt$(huge, Double.NEGATIVE_INFINITY));
      assertFalse(ComparisonIntrinsics.$lt$(huge, Double.NEGATIVE_INFINITY));
      var negHuge = huge.negate();
      assertTrue(ComparisonIntrinsics.$lt$(negHuge, Double.POSITIVE_INFINITY));
      assertTrue(ComparisonIntrinsics.$gt$(negHuge, Double.NEGATIVE_INFINITY));
      // Longs too.
      assertTrue(ComparisonIntrinsics.$lt$(Long.MAX_VALUE, Double.POSITIVE_INFINITY));
      assertTrue(ComparisonIntrinsics.$gt$(Long.MAX_VALUE, Double.NEGATIVE_INFINITY));
      assertTrue(ComparisonIntrinsics.$lt$(Long.MIN_VALUE, Double.POSITIVE_INFINITY));
      assertTrue(ComparisonIntrinsics.$gt$(Long.MIN_VALUE, Double.NEGATIVE_INFINITY));
    }

    @Test
    public void exactLongDoubleWithFraction() {
      assertTrue(ComparisonIntrinsics.$lt$(1L, 1.5));
      assertFalse(ComparisonIntrinsics.$lt$(2L, 1.5));
      assertTrue(ComparisonIntrinsics.$gt$(2L, 1.5));
      assertFalse(ComparisonIntrinsics.$gt$(1L, 1.5));
      assertTrue(ComparisonIntrinsics.$lt$(0L, 0.5));
      assertTrue(ComparisonIntrinsics.$gt$(0L, -0.5));
      assertTrue(ComparisonIntrinsics.$lt$(-1L, -0.5));
      assertFalse(ComparisonIntrinsics.$lt$(-1L, -1.5));
      assertTrue(ComparisonIntrinsics.$gt$(-1L, -1.5));
    }

    @Test
    public void exactLongDoubleAtZero() {
      assertTrue(ComparisonIntrinsics.$le$(0L, -0.0));
      assertTrue(ComparisonIntrinsics.$ge$(0L, -0.0));
      assertTrue(ComparisonIntrinsics.$le$(-0.0, 0L));
      assertTrue(ComparisonIntrinsics.$ge$(-0.0, 0L));
      assertFalse(ComparisonIntrinsics.$lt$(0L, -0.0));
      assertFalse(ComparisonIntrinsics.$gt$(0L, -0.0));
      assertFalse(ComparisonIntrinsics.$lt$(-0.0, 0L));
      assertFalse(ComparisonIntrinsics.$gt$(-0.0, 0L));
    }

    @Test
    public void orderingTinyFractionalDouble() {
      // Smallest positive subnormal double, 0x1p-1074, truncates to 0.
      assertTrue(ComparisonIntrinsics.$lt$(0L, 0x1p-1074));
      assertTrue(ComparisonIntrinsics.$gt$(0x1p-1074, 0L));
      assertTrue(ComparisonIntrinsics.$gt$(0L, -0x1p-1074));
      assertFalse(ComparisonIntrinsics.$eq$(0L, 0x1p-1074));
    }

    @Test
    public void boolIntFloatAreUnified() {
      assertTrue(ComparisonIntrinsics.$eq$(1L, true), 1L + " == " + true);
      assertTrue(ComparisonIntrinsics.$eq$(true, 1L), true + " == " + 1L);
      assertTrue(ComparisonIntrinsics.$eq$(0L, false), 0L + " == " + false);
      assertTrue(ComparisonIntrinsics.$eq$(false, 0L), false + " == " + 0L);
      assertTrue(ComparisonIntrinsics.$eq$(true, 1.0d), true + " == " + 1.0d);
      assertTrue(ComparisonIntrinsics.$eq$(1.0d, true), 1.0d + " == " + true);
      assertTrue(ComparisonIntrinsics.$eq$(1L, 1.0d), 1L + " == " + 1.0d);
      assertTrue(ComparisonIntrinsics.$eq$(1.0d, 1L), 1.0d + " == " + 1L);
      assertTrue(ComparisonIntrinsics.$eq$(true, true), true + " == " + true);
      assertTrue(ComparisonIntrinsics.$eq$(true, true), true + " == " + true);
      assertFalse(ComparisonIntrinsics.$eq$(true, false), true + " == " + false);
      assertFalse(ComparisonIntrinsics.$eq$(false, true), false + " == " + true);
      assertFalse(ComparisonIntrinsics.$eq$(2L, true), 2L + " == " + true);
      assertFalse(ComparisonIntrinsics.$eq$(true, 2L), true + " == " + 2L);
      assertFalse(ComparisonIntrinsics.$eq$(1L, 1.5d), 1L + " == " + 1.5d);
      assertFalse(ComparisonIntrinsics.$eq$(1.5d, 1L), 1.5d + " == " + 1L);
    }

    @Test
    public void intFloatComparisonIsExact() {
      // 2**53 + 1
      assertFalse(ComparisonIntrinsics.$eq$(9007199254740993L, 9007199254740992.0d), 9007199254740993L + " == " + 9007199254740992.0d);
      assertFalse(ComparisonIntrinsics.$eq$(9007199254740992.0d, 9007199254740993L), 9007199254740992.0d + " == " + 9007199254740993L);
      assertTrue(ComparisonIntrinsics.$eq$(9007199254740992L, 9007199254740992.0d), 9007199254740992L + " == " + 9007199254740992.0d);
      assertTrue(ComparisonIntrinsics.$eq$(9007199254740992.0d, 9007199254740992L), 9007199254740992.0d + " == " + 9007199254740992L);
      // (double) Long.MAX_VALUE == 2**63
      assertFalse(ComparisonIntrinsics.$eq$(Long.MAX_VALUE, 0x1p63), Long.MAX_VALUE + " == " + 0x1p63);
      assertFalse(ComparisonIntrinsics.$eq$(0x1p63, Long.MAX_VALUE), 0x1p63 + " == " + Long.MAX_VALUE);
      assertTrue(ComparisonIntrinsics.$eq$(Long.MIN_VALUE, -0x1p63), Long.MIN_VALUE + " == " + -0x1p63);
      assertTrue(ComparisonIntrinsics.$eq$(-0x1p63, Long.MIN_VALUE), -0x1p63 + " == " + Long.MIN_VALUE);
      Object a2 = BigInteger.ONE.shiftLeft(63);
      assertTrue(ComparisonIntrinsics.$eq$(a2, 0x1p63), a2 + " == " + 0x1p63);
      assertTrue(ComparisonIntrinsics.$eq$(0x1p63, a2), 0x1p63 + " == " + a2);
      Object a1 = BigInteger.ONE.shiftLeft(100);
      assertTrue(ComparisonIntrinsics.$eq$(a1, 0x1p100), a1 + " == " + 0x1p100);
      assertTrue(ComparisonIntrinsics.$eq$(0x1p100, a1), 0x1p100 + " == " + a1);
      Object a3 = BigInteger.ONE.shiftLeft(100).add(BigInteger.ONE);
      assertFalse(ComparisonIntrinsics.$eq$(a3, 0x1p100), a3 + " == " + 0x1p100);
      assertFalse(ComparisonIntrinsics.$eq$(0x1p100, a3), 0x1p100 + " == " + a3);
      Object a = BigInteger.TEN.pow(30);
      Object b = BigInteger.TEN.pow(30);
      assertTrue(ComparisonIntrinsics.$eq$(a, b), a + " == " + b);
      assertTrue(ComparisonIntrinsics.$eq$(b, a), b + " == " + a);
    }

    @Test
    public void nanInfinityAndSignedZero() {
      Object nan = Double.NaN;
      assertFalse(ComparisonIntrinsics.$eq$(nan, nan));              // even for the same box
      assertFalse(ComparisonIntrinsics.$eq$(nan, 0L), nan + " == " + 0L);
      assertFalse(ComparisonIntrinsics.$eq$(0L, nan), 0L + " == " + nan);
      assertFalse(ComparisonIntrinsics.$eq$(Double.POSITIVE_INFINITY, Long.MAX_VALUE), Double.POSITIVE_INFINITY + " == " + Long.MAX_VALUE);
      assertFalse(ComparisonIntrinsics.$eq$(Long.MAX_VALUE, Double.POSITIVE_INFINITY), Long.MAX_VALUE + " == " + Double.POSITIVE_INFINITY);
      Object b = BigInteger.TEN.pow(400);
      assertFalse(ComparisonIntrinsics.$eq$(Double.POSITIVE_INFINITY, b), Double.POSITIVE_INFINITY + " == " + b);
      assertFalse(ComparisonIntrinsics.$eq$(b, Double.POSITIVE_INFINITY), b + " == " + Double.POSITIVE_INFINITY);
      assertTrue(ComparisonIntrinsics.$eq$(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY), Double.POSITIVE_INFINITY + " == " + Double.POSITIVE_INFINITY);
      assertTrue(ComparisonIntrinsics.$eq$(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY), Double.POSITIVE_INFINITY + " == " + Double.POSITIVE_INFINITY);
      assertTrue(ComparisonIntrinsics.$eq$(-0.0d, 0.0d), -0.0d + " == " + 0.0d);
      assertTrue(ComparisonIntrinsics.$eq$(0.0d, -0.0d), 0.0d + " == " + -0.0d);
      assertTrue(ComparisonIntrinsics.$eq$(-0.0d, 0L), -0.0d + " == " + 0L);
      assertTrue(ComparisonIntrinsics.$eq$(0L, -0.0d), 0L + " == " + -0.0d);
      assertTrue(ComparisonIntrinsics.$eq$(-0.0d, false), -0.0d + " == " + false);
      assertTrue(ComparisonIntrinsics.$eq$(false, -0.0d), false + " == " + -0.0d);
    }
  }


  @Nested
  public final class OrderingPrimitive {
    @Test
    public void orderingOfInts() {
      assertTrue(ComparisonIntrinsics.$lt$(1L, 2L));
      assertFalse(ComparisonIntrinsics.$lt$(2L, 1L));
      assertFalse(ComparisonIntrinsics.$lt$(1L, 1L));
      assertTrue(ComparisonIntrinsics.$le$(1L, 1L));
      assertTrue(ComparisonIntrinsics.$le$(1L, 2L));
      assertFalse(ComparisonIntrinsics.$le$(2L, 1L));
      assertTrue(ComparisonIntrinsics.$gt$(2L, 1L));
      assertFalse(ComparisonIntrinsics.$gt$(1L, 2L));
      assertTrue(ComparisonIntrinsics.$ge$(1L, 1L));
      assertTrue(ComparisonIntrinsics.$ge$(2L, 1L));
      assertFalse(ComparisonIntrinsics.$ge$(1L, 2L));
      assertTrue(ComparisonIntrinsics.$lt$(Long.MIN_VALUE, 0L));
      assertTrue(ComparisonIntrinsics.$lt$(0L, Long.MAX_VALUE));
      assertTrue(ComparisonIntrinsics.$lt$(Long.MIN_VALUE, Long.MAX_VALUE));
    }

    @Test
    public void orderingOfBooleans() {
      assertTrue(ComparisonIntrinsics.$lt$(false, true));
      assertFalse(ComparisonIntrinsics.$lt$(true, false));
      assertFalse(ComparisonIntrinsics.$lt$(true, true));
      assertFalse(ComparisonIntrinsics.$lt$(false, false));
      assertTrue(ComparisonIntrinsics.$le$(true, true));
      assertTrue(ComparisonIntrinsics.$le$(false, true));
      assertTrue(ComparisonIntrinsics.$gt$(true, false));
      assertTrue(ComparisonIntrinsics.$ge$(true, false));
      assertTrue(ComparisonIntrinsics.$ge$(true, true));
    }

    @Test
    public void orderingOfBooleansAndInts() {
      assertTrue(ComparisonIntrinsics.$lt$(false, 1L));
      assertTrue(ComparisonIntrinsics.$lt$(true, 2L));
      assertFalse(ComparisonIntrinsics.$lt$(true, 1L));
      assertTrue(ComparisonIntrinsics.$le$(true, 1L));
      assertTrue(ComparisonIntrinsics.$le$(false, 0L));
      assertTrue(ComparisonIntrinsics.$lt$(0L, true));
      assertTrue(ComparisonIntrinsics.$ge$(true, 0L));
      assertTrue(ComparisonIntrinsics.$gt$(true, -1L));
      assertTrue(ComparisonIntrinsics.$lt$(true, 1.5));
      assertTrue(ComparisonIntrinsics.$ge$(false, -0.5));
    }

    @Test
    public void orderingOfFloats() {
      assertTrue(ComparisonIntrinsics.$lt$(1.0, 2.0));
      assertFalse(ComparisonIntrinsics.$lt$(2.0, 1.0));
      assertFalse(ComparisonIntrinsics.$lt$(1.0, 1.0));
      assertTrue(ComparisonIntrinsics.$le$(1.0, 1.0));
      assertTrue(ComparisonIntrinsics.$gt$(2.0, 1.0));
      assertTrue(ComparisonIntrinsics.$ge$(1.0, 1.0));
      assertTrue(ComparisonIntrinsics.$lt$(Double.NEGATIVE_INFINITY, 0.0));
      assertTrue(ComparisonIntrinsics.$gt$(Double.POSITIVE_INFINITY, 0.0));
      assertTrue(ComparisonIntrinsics.$lt$(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
    }

    @Test
    public void orderingSignedZeros() {
      // -0.0 == 0.0, so neither is < or > the other.
      assertFalse(ComparisonIntrinsics.$lt$(-0.0, 0.0));
      assertFalse(ComparisonIntrinsics.$lt$(0.0, -0.0));
      assertFalse(ComparisonIntrinsics.$gt$(-0.0, 0.0));
      assertFalse(ComparisonIntrinsics.$gt$(0.0, -0.0));
      assertTrue(ComparisonIntrinsics.$le$(-0.0, 0.0));
      assertTrue(ComparisonIntrinsics.$le$(0.0, -0.0));
      assertTrue(ComparisonIntrinsics.$ge$(-0.0, 0.0));
      assertTrue(ComparisonIntrinsics.$ge$(0.0, -0.0));
    }

    @Test
    public void orderingOfNaN() {
      // Every comparison involving NaN is False.
      assertFalse(ComparisonIntrinsics.$lt$(Double.NaN, 1.0));
      assertFalse(ComparisonIntrinsics.$lt$(1.0, Double.NaN));
      assertFalse(ComparisonIntrinsics.$le$(Double.NaN, Double.NaN));
      assertFalse(ComparisonIntrinsics.$gt$(Double.NaN, 1.0));
      assertFalse(ComparisonIntrinsics.$ge$(Double.NaN, 1.0));
      assertFalse(ComparisonIntrinsics.$lt$(Double.NaN, 1L));
      assertFalse(ComparisonIntrinsics.$lt$(1L, Double.NaN));
      assertFalse(ComparisonIntrinsics.$le$(1L, Double.NaN));
      assertFalse(ComparisonIntrinsics.$ge$(Double.NaN, 1L));
    }

    @Test
    public void orderingOfBigIntegers() {
      assertTrue(ComparisonIntrinsics.$lt$(BigInteger.ONE, BigInteger.TEN));
      assertFalse(ComparisonIntrinsics.$lt$(BigInteger.TEN, BigInteger.ONE));
      assertTrue(ComparisonIntrinsics.$gt$(BigInteger.TEN, BigInteger.ONE));
      assertTrue(ComparisonIntrinsics.$le$(BigInteger.TEN, BigInteger.TEN));
      assertTrue(ComparisonIntrinsics.$ge$(BigInteger.TEN, BigInteger.TEN));
      assertFalse(ComparisonIntrinsics.$lt$(BigInteger.TEN, BigInteger.TEN));
      assertTrue(ComparisonIntrinsics.$gt$(BigInteger.ZERO, BigInteger.ONE.negate()));
      var a = BigInteger.ONE.shiftLeft(1000);
      var b = BigInteger.ONE.shiftLeft(1001);
      assertTrue(ComparisonIntrinsics.$lt$(a, b));
      assertTrue(ComparisonIntrinsics.$gt$(b, a));
    }

    @Test
    public void orderingBetweenLongAndBigInteger() {
      assertTrue(ComparisonIntrinsics.$lt$(1L, BigInteger.TEN));
      assertTrue(ComparisonIntrinsics.$gt$(BigInteger.TEN, 1L));
      assertTrue(ComparisonIntrinsics.$lt$(BigInteger.TEN, 100L));
      assertTrue(ComparisonIntrinsics.$gt$(100L, BigInteger.TEN));
      assertTrue(ComparisonIntrinsics.$le$(BigInteger.TEN, 10L));
      assertTrue(ComparisonIntrinsics.$ge$(10L, BigInteger.TEN));
      assertFalse(ComparisonIntrinsics.$lt$(BigInteger.TEN, 10L));
      assertFalse(ComparisonIntrinsics.$gt$(BigInteger.TEN, 10L));
    }

    @Test
    public void orderingOfBooleansAndBigIntegers() {
      assertTrue(ComparisonIntrinsics.$lt$(false, BigInteger.TEN));
      assertTrue(ComparisonIntrinsics.$lt$(true, BigInteger.TEN));
      assertTrue(ComparisonIntrinsics.$gt$(BigInteger.TEN, true));
      assertTrue(ComparisonIntrinsics.$le$(true, BigInteger.ONE));
      assertTrue(ComparisonIntrinsics.$ge$(BigInteger.ONE, true));
    }
  }

  @Nested
  public final class OrderingString {

    @Test
    public void orderingOfStrings() {
      assertTrue(ComparisonIntrinsics.$lt$("", "a"));
      assertFalse(ComparisonIntrinsics.$lt$("a", ""));
      assertFalse(ComparisonIntrinsics.$lt$("a", "a"));
      assertTrue(ComparisonIntrinsics.$le$("a", "a"));
      assertTrue(ComparisonIntrinsics.$lt$("abc", "abd"));
      assertTrue(ComparisonIntrinsics.$lt$("ABC", "abc"));  // 'A' (65) < 'a' (97)
      assertTrue(ComparisonIntrinsics.$lt$("a", "ab"));     // proper prefix
      assertFalse(ComparisonIntrinsics.$lt$("ab", "a"));
    }

    @Test
    public void stringsAreOrderedByCodePoint() {
      assertTrue(ComparisonIntrinsics.$lt$("\uFF5E", "\uD83D\uDE00"));  // U+FF5E < U+1F600
    }

    @Test
    public void stringsOrderingUsesCodePointsNotUtf16() {
      // U+1F600 is "\uD83D\uDE00" in UTF-16, starting with \uD83D (0xD83D) < \uFF5E.
      // A UTF-16 code-unit comparison would say "\uD83D\uDE00" < "\uFF5E",
      // but code-point order says U+1F600 > U+FF5E.
      assertTrue(ComparisonIntrinsics.$gt$("\uD83D\uDE00", "\uFF5E"));
      assertTrue(ComparisonIntrinsics.$lt$("a", "\uD83D\uDE00"));
      assertTrue(ComparisonIntrinsics.$gt$("\uD83D\uDE00", "a"));
    }

    @Test
    public void stringsOrderingWithLoneSurrogates() {
      // Regression test for the surrogate-pair shortcut bug:
      //   "\uD800\uDC00" is one code point U+10000.
      //   "\uD800\uFFFF" is two code points: U+D800 (lone high surrogate) and U+FFFF.
      // Code-point order: U+10000 > U+D800, so the first string is greater.
      assertTrue(ComparisonIntrinsics.$gt$("\uD800\uDC00", "\uD800\uFFFF"));
      assertTrue(ComparisonIntrinsics.$lt$("\uD800\uFFFF", "\uD800\uDC00"));
    }

    @Test
    public void emptyStringComparisons() {
      assertTrue(ComparisonIntrinsics.$le$("", ""));
      assertTrue(ComparisonIntrinsics.$ge$("", ""));
      assertFalse(ComparisonIntrinsics.$lt$("", ""));
      assertFalse(ComparisonIntrinsics.$gt$("", ""));
    }

    @Test
    public void loneSurrogateCodePointOrdering() {
      // Lone surrogates are still valid code points.
      assertTrue(ComparisonIntrinsics.$lt$("\uD800", "\uD801"));
      assertFalse(ComparisonIntrinsics.$lt$("\uD801", "\uD800"));
      // D800 (lone high surrogate, 0xD800) < FFFF (0xFFFF).
      assertTrue(ComparisonIntrinsics.$lt$("\uD800", "\uFFFF"));
      assertTrue(ComparisonIntrinsics.$gt$("\uFFFF", "\uD800"));
    }

    @Test
    public void prefixWithSurrogatePair() {
      // 'a' == 'a', then U+1F600 vs 'b' (0x62) → U+1F600 greater.
      assertFalse(ComparisonIntrinsics.$lt$("a\uD83D\uDE00", "ab"));
      assertTrue(ComparisonIntrinsics.$gt$("a\uD83D\uDE00", "ab"));
    }
  }


  @Nested
  public final class TupleEquality {

    @Test
    public void tupleEquality() {
      assertTrue(ComparisonIntrinsics.$eq$(pyTuple(1L), pyTuple(1L)));
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(1L), pyTuple(2L)));
      assertTrue(ComparisonIntrinsics.$eq$(pyTuple(1L, 2L), pyTuple(1L, 2L)));
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(1L, 2L), pyTuple(1L, 3L)));
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(1L, 2L), pyTuple(3L, 2L)));
      // Different lengths are never equal.
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(1L), pyTuple(1L, 2L)));
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(1L, 2L), pyTuple(1L)));
      // Nested tuples.
      assertTrue(ComparisonIntrinsics.$eq$(
          pyTuple(pyTuple(1L)),
          pyTuple(pyTuple(1L))));
      assertFalse(ComparisonIntrinsics.$eq$(
          pyTuple(pyTuple(1L)),
          pyTuple(pyTuple(2L))));
    }

    @Test
    public void tupleEqualityUsesIdentityShortcutBeforeEq() {
      // At the top level NaN != NaN (no shortcut), but inside a container the
      // same reference is considered equal — CPython's PyObject_RichCompareBool.
      Double nan = Double.NaN;  // boxed once; both tuples hold the same reference
      assertFalse(ComparisonIntrinsics.$eq$(nan, nan));
      assertTrue(ComparisonIntrinsics.$eq$(pyTuple(nan), pyTuple(nan)));
      assertTrue(ComparisonIntrinsics.$eq$(pyTuple(nan, 1L), pyTuple(nan, 1L)));
    }

    @Test
    public void tupleWithDistinctNaNObjects() {
      Assumptions.assumeFalse(Runtime.version().feature() >= 28);

      // Two freshly-boxed NaN objects are not the same reference; NaN != NaN,
      // so the tuples are not equal.
      assertFalse(ComparisonIntrinsics.$eq$(
          pyTuple(Double.NaN), pyTuple(Double.NaN)));
      assertFalse(ComparisonIntrinsics.$eq$(
          pyTuple(Double.NaN, 1L), pyTuple(Double.NaN, 1L)));
    }

    @Test
    public void tupleMixedNumericTypes() {
      // bool, int and float all compare numerically inside tuples.
      assertTrue(ComparisonIntrinsics.$eq$(pyTuple(true), pyTuple(1L)));
      assertTrue(ComparisonIntrinsics.$eq$(pyTuple(1L), pyTuple(true)));
      assertTrue(ComparisonIntrinsics.$eq$(pyTuple(1L), pyTuple(1.0)));
      assertTrue(ComparisonIntrinsics.$eq$(pyTuple(1.0), pyTuple(1L)));
      assertTrue(ComparisonIntrinsics.$eq$(pyTuple(0L), pyTuple(false)));
      assertTrue(ComparisonIntrinsics.$eq$(pyTuple(0L), pyTuple(-0.0)));
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(true), pyTuple(0L)));
    }

    @Test
    public void tupleWithIncompatibleElementTypes() {
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(1L), pyTuple("1")));
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(true), pyTuple("true")));
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(0L), pyTuple(PyNone.INSTANCE)));
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(PyNone.INSTANCE), pyTuple(0L)));
    }

    @Test
    public void nestedTuplesWithPyNone() {
      assertTrue(ComparisonIntrinsics.$eq$(
          pyTuple(pyTuple(PyNone.INSTANCE)),
          pyTuple(pyTuple(PyNone.INSTANCE))));
      assertFalse(ComparisonIntrinsics.$eq$(
          pyTuple(pyTuple(PyNone.INSTANCE)),
          pyTuple(pyTuple(0L))));
    }

    @Test
    public void tuplesCompareAcrossShapesAndRecursively() {
      Object a2 = new PyTuple2(1L, "a");
      Object b2 = new PyTuple.PyTupleN(new Object[]{1.0d, "a"});
      assertTrue(ComparisonIntrinsics.$eq$(a2, b2), a2 + " == " + b2);
      assertTrue(ComparisonIntrinsics.$eq$(b2, a2), b2 + " == " + a2);
      Object a1 = new PyTuple1(new PyTuple1(true));
      Object b1 = new PyTuple1(new PyTuple.PyTupleN(new Object[]{1L}));
      assertTrue(ComparisonIntrinsics.$eq$(a1, b1), a1 + " == " + b1);
      assertTrue(ComparisonIntrinsics.$eq$(b1, a1), b1 + " == " + a1);
      Object a5 = new PyTuple2(1L, "a");
      Object b4 = new PyTuple2(1L, "b");
      assertFalse(ComparisonIntrinsics.$eq$(a5, b4), a5 + " == " + b4);
      assertFalse(ComparisonIntrinsics.$eq$(b4, a5), b4 + " == " + a5);
      Object a4 = new PyTuple1(1L);
      Object b3 = new PyTuple2(1L, 1L);
      assertFalse(ComparisonIntrinsics.$eq$(a4, b3), a4 + " == " + b3);
      assertFalse(ComparisonIntrinsics.$eq$(b3, a4), b3 + " == " + a4);
      Object a3 = new PyTuple1(1L);
      assertFalse(ComparisonIntrinsics.$eq$(a3, 1L), a3 + " == " + 1L);
      assertFalse(ComparisonIntrinsics.$eq$(1L, a3), 1L + " == " + a3);
      Object a = new PyTuple.PyTupleN(new Object[0]);
      Object b = new PyTuple.PyTupleN(new Object[0]);
      assertTrue(ComparisonIntrinsics.$eq$(a, b), a + " == " + b);
      assertTrue(ComparisonIntrinsics.$eq$(b, a), b + " == " + a);
    }

    @Test
    public void containerElementsUseTheIdentityShortcut() {
      Assumptions.assumeFalse(Runtime.version().feature() >= 28);

      // CPython: x = float('nan'); (x,) == (x,) is True, (float('nan'),) == (float('nan'),) is False
      Object x = Double.valueOf(Double.NaN);
      Object a = new PyTuple1(x);
      Object b = new PyTuple1(x);
      assertTrue(ComparisonIntrinsics.$eq$(a, b), a + " == " + b);
      assertTrue(ComparisonIntrinsics.$eq$(b, a), b + " == " + a);
      Object a1 = new PyTuple1(Double.valueOf(Double.NaN));
      Object b1 = new PyTuple1(Double.valueOf(Double.NaN));
      assertFalse(ComparisonIntrinsics.$eq$(a1, b1), a1 + " == " + b1);
      assertFalse(ComparisonIntrinsics.$eq$(b1, a1), b1 + " == " + a1);
    }
  }


  @Nested
  public final class TupleOrdering {

    @Test
    public void tupleOrdering() {
      assertTrue(ComparisonIntrinsics.$lt$(pyTuple(1L), pyTuple(1L, 0L)));
      assertTrue(ComparisonIntrinsics.$lt$(pyTuple(1L, 2L), pyTuple(1L, 3L)));
      assertTrue(ComparisonIntrinsics.$lt$(pyTuple(1L, 2L), pyTuple(2L, 0L)));
      assertTrue(ComparisonIntrinsics.$gt$(pyTuple(2L, 0L), pyTuple(1L, 2L)));
      assertTrue(ComparisonIntrinsics.$le$(pyTuple(1L, 2L), pyTuple(1L, 2L)));
      assertTrue(ComparisonIntrinsics.$ge$(pyTuple(1L, 2L), pyTuple(1L, 2L)));
      assertFalse(ComparisonIntrinsics.$lt$(pyTuple(1L, 2L), pyTuple(1L, 2L)));
      // Common prefix, shorter is smaller.
      assertTrue(ComparisonIntrinsics.$lt$(pyTuple(1L), pyTuple(1L, 2L)));
      assertTrue(ComparisonIntrinsics.$gt$(pyTuple(1L, 2L), pyTuple(1L)));
      // String elements inside tuples.
      assertTrue(ComparisonIntrinsics.$lt$(
          pyTuple("a", 1L), pyTuple("b", 0L)));
      // Nested tuples.
      assertTrue(ComparisonIntrinsics.$lt$(
          pyTuple(pyTuple(1L)),
          pyTuple(pyTuple(2L))));
    }

    @Test
    public void tupleOrderingWithNaN() {
      Double nan = Double.NaN;
      // First items equal via identity shortcut → length decides.
      assertTrue(ComparisonIntrinsics.$lt$(pyTuple(nan), pyTuple(nan, 1L)));
      assertFalse(ComparisonIntrinsics.$lt$(pyTuple(nan, 1L), pyTuple(nan)));
      // Tuples with the same length and NaN are equal (identity shortcut).
      assertTrue(ComparisonIntrinsics.$le$(pyTuple(nan), pyTuple(nan)));
      assertTrue(ComparisonIntrinsics.$ge$(pyTuple(nan), pyTuple(nan)));
    }

    @Test
    public void tupleOrderingMixedNumericTypes() {
      assertTrue(ComparisonIntrinsics.$lt$(pyTuple(false), pyTuple(true)));
      assertTrue(ComparisonIntrinsics.$lt$(pyTuple(true), pyTuple(2L)));
      assertTrue(ComparisonIntrinsics.$gt$(pyTuple(2L), pyTuple(true)));
      assertTrue(ComparisonIntrinsics.$le$(pyTuple(0L), pyTuple(false)));
    }

    @Test
    public void tupleOrderingNestedDifferentLengths() {
      assertTrue(ComparisonIntrinsics.$lt$(
          pyTuple(pyTuple(1L)),
          pyTuple(pyTuple(1L, 0L))));
      assertTrue(ComparisonIntrinsics.$gt$(
          pyTuple(pyTuple(1L, 0L)),
          pyTuple(pyTuple(1L))));
    }

    @Test
    public void tupleOrderingWithNaNSecond() {
      Double nan = Double.NaN;
      // NaN is unordered w.r.t. anything, so all four operators report false
      // as soon as the comparison reaches the NaN item.
      assertFalse(ComparisonIntrinsics.$lt$(pyTuple(1L, nan), pyTuple(1L, 2L)));
      assertFalse(ComparisonIntrinsics.$gt$(pyTuple(1L, nan), pyTuple(1L, 2L)));
      assertFalse(ComparisonIntrinsics.$lt$(pyTuple(1L, 2L), pyTuple(1L, nan)));
      assertFalse(ComparisonIntrinsics.$gt$(pyTuple(1L, 2L), pyTuple(1L, nan)));
    }

    @Test
    public void tupleOrderingUsesExactNumericComparison() {
      // 2**53 + 1 vs 2**53 (as a double): exact comparison says >.
      assertTrue(ComparisonIntrinsics.$gt$(
          pyTuple(9007199254740993L), pyTuple(9007199254740992.0)));
      assertTrue(ComparisonIntrinsics.$lt$(
          pyTuple(9007199254740992.0), pyTuple(9007199254740993L)));
    }

    @Test
    public void tupleOrderingLongVsDoubleAtBoundary() {
      // Long.MAX_VALUE (2**63 - 1) < 2**63, even though (double)Long.MAX_VALUE == 2**63.
      assertTrue(ComparisonIntrinsics.$lt$(
          pyTuple(Long.MAX_VALUE), pyTuple(0x1p63)));
      assertTrue(ComparisonIntrinsics.$gt$(
          pyTuple(0x1p63), pyTuple(Long.MAX_VALUE)));
      // Long.MIN_VALUE == -2**63 exactly.
      assertTrue(ComparisonIntrinsics.$le$(
          pyTuple(Long.MIN_VALUE), pyTuple(-0x1p63)));
      assertTrue(ComparisonIntrinsics.$ge$(
          pyTuple(Long.MIN_VALUE), pyTuple(-0x1p63)));
    }
  }


  @Nested
  public final class ListEquality {

    @Test
    public void listEquality() {
      assertTrue(ComparisonIntrinsics.$eq$(pyList(1L), pyList(1L)));
      assertTrue(ComparisonIntrinsics.$eq$(pyList(1L, 2L), pyList(1L, 2L)));

      assertFalse(ComparisonIntrinsics.$eq$(pyList(1L), pyList(2L)));
      assertFalse(ComparisonIntrinsics.$eq$(pyList(1L, 2L), pyList(1L, 3L)));

      assertFalse(ComparisonIntrinsics.$eq$(pyList(1L), pyList(1L, 2L)));
      assertFalse(ComparisonIntrinsics.$eq$(pyList(1L, 2L), pyList(1L)));
    }

    @Test
    public void listEqualityUsesIdentityShortcutBeforeEq() {
      Assumptions.assumeFalse(Runtime.version().feature() >= 28);

      Double nan = Double.NaN;
      assertFalse(ComparisonIntrinsics.$eq$(nan, nan));
      assertTrue(ComparisonIntrinsics.$eq$(pyList(nan), pyList(nan)));
      assertFalse(ComparisonIntrinsics.$eq$(
          pyList(Double.NaN), pyList(Double.NaN)));
    }

    @Test
    public void listMixedNumericTypes() {
      assertTrue(ComparisonIntrinsics.$eq$(pyList(true), pyList(1L)));
      assertTrue(ComparisonIntrinsics.$eq$(pyList(1L), pyList(1.0)));
      assertTrue(ComparisonIntrinsics.$eq$(pyList(false), pyList(0L)));
      assertFalse(ComparisonIntrinsics.$eq$(pyList(true), pyList(0L)));
    }

    @Test
    public void nestedListsEquality() {
      assertTrue(ComparisonIntrinsics.$eq$(
          pyList(pyList(1L), pyList(2L)),
          pyList(pyList(1L), pyList(2L))));

      assertFalse(ComparisonIntrinsics.$eq$(
          pyList(pyList(1L)),
          pyList(pyList(2L))));
    }

    @Test
    public void listEqualityAcrossIncompatibleTypes() {
      assertFalse(ComparisonIntrinsics.$eq$(pyList(1L), pyTuple(1L)));
      assertFalse(ComparisonIntrinsics.$eq$(pyTuple(1L), pyList(1L)));

      assertFalse(ComparisonIntrinsics.$eq$(pyList(1L), 1L));
      assertFalse(ComparisonIntrinsics.$eq$(pyList(1L), "1"));
    }

    @Test
    public void neIsNotEqForLists() {
      assertFalse(ComparisonIntrinsics.$ne$(pyList(1L), pyList(1L)));
      assertTrue(ComparisonIntrinsics.$ne$(pyList(1L), pyList(2L)));
    }
  }


  @Nested
  public final class ListOrdering {

    @Test
    public void listOrdering() {
      assertTrue(ComparisonIntrinsics.$lt$(pyList(1L, 2L), pyList(1L, 3L)));
      assertTrue(ComparisonIntrinsics.$lt$(pyList(1L, 2L), pyList(2L, 0L)));
      assertTrue(ComparisonIntrinsics.$gt$(pyList(2L, 0L), pyList(1L, 2L)));

      assertTrue(ComparisonIntrinsics.$le$(pyList(1L, 2L), pyList(1L, 2L)));
      assertTrue(ComparisonIntrinsics.$ge$(pyList(1L, 2L), pyList(1L, 2L)));

      assertFalse(ComparisonIntrinsics.$lt$(pyList(1L, 2L), pyList(1L, 2L)));
      assertFalse(ComparisonIntrinsics.$gt$(pyList(1L, 2L), pyList(1L, 2L)));
    }

    @Test
    public void listOrderingWithCommonPrefix() {
      assertTrue(ComparisonIntrinsics.$lt$(pyList(1L), pyList(1L, 2L)));
      assertTrue(ComparisonIntrinsics.$le$(pyList(1L), pyList(1L, 2L)));

      assertTrue(ComparisonIntrinsics.$gt$(pyList(1L, 2L), pyList(1L)));
      assertTrue(ComparisonIntrinsics.$ge$(pyList(1L, 2L), pyList(1L)));
    }

    @Test
    public void listOrderingNestedLists() {
      assertTrue(ComparisonIntrinsics.$lt$(
          pyList(pyList(1L)),
          pyList(pyList(2L))));

      assertTrue(ComparisonIntrinsics.$lt$(
          pyList(pyList(1L)),
          pyList(pyList(1L, 2L))));
    }

    @Test
    public void listOrderingMixedNumericTypes() {
      assertTrue(ComparisonIntrinsics.$lt$(pyList(false), pyList(true)));
      assertTrue(ComparisonIntrinsics.$lt$(pyList(true), pyList(2L)));
      assertTrue(ComparisonIntrinsics.$le$(pyList(1L), pyList(1.0)));
    }

    @Test
    public void listOrderingWithNaN() {
      assertFalse(ComparisonIntrinsics.$lt$(pyList(Double.NaN), pyList(1L)));
      assertFalse(ComparisonIntrinsics.$gt$(pyList(Double.NaN), pyList(1L)));
      assertFalse(ComparisonIntrinsics.$lt$(pyList(1L), pyList(Double.NaN)));
      assertFalse(ComparisonIntrinsics.$gt$(pyList(1L), pyList(Double.NaN)));
    }

    @Test
    public void listOrderingUsesIdentityShortcut() {
      Double nan = Double.NaN;

      // Same NaN reference: elements are considered equal,
      // so the length determines the ordering.
      assertTrue(ComparisonIntrinsics.$lt$(pyList(nan), pyList(nan, 1L)));
      assertTrue(ComparisonIntrinsics.$gt$(pyList(nan, 1L), pyList(nan)));

      // Same contents and same NaN reference.
      assertTrue(ComparisonIntrinsics.$le$(pyList(nan), pyList(nan)));
      assertTrue(ComparisonIntrinsics.$ge$(pyList(nan), pyList(nan)));

      assertFalse(ComparisonIntrinsics.$lt$(pyList(nan), pyList(nan)));
      assertFalse(ComparisonIntrinsics.$gt$(pyList(nan), pyList(nan)));
    }

    @Test
    public void listOrderingWithComparableElements() {
      assertTrue(ComparisonIntrinsics.$lt$(
              pyList(1L, "a"),
              pyList(1L, "b")));
    }

    @Test
    public void listOrderingWithIncomparableElements() {
      assertThrows(PyTypeError.class,
          () -> ComparisonIntrinsics.$lt$(
              pyList(1L, "a"),
              pyList(1L, 2L)));
    }
  }


  @Nested
  public final class TypeError {

    @Test
    public void orderingAcrossTypesIsATypeError() {
      var e = assertThrows(PyTypeError.class, () -> ComparisonIntrinsics.$lt$(1L, "a"));
      assertEquals("'<' not supported between instances of 'int' and 'str'", e.getMessage());
    }

    @Test
    public void eachOrderingOperatorReportsItsOwnSymbol() {
      var lt = assertThrows(PyTypeError.class, () -> ComparisonIntrinsics.$lt$(1L, "a"));
      assertEquals("'<' not supported between instances of 'int' and 'str'", lt.getMessage());
      var le = assertThrows(PyTypeError.class, () -> ComparisonIntrinsics.$le$(1L, "a"));
      assertEquals("'<=' not supported between instances of 'int' and 'str'", le.getMessage());
      var gt = assertThrows(PyTypeError.class, () -> ComparisonIntrinsics.$gt$(1L, "a"));
      assertEquals("'>' not supported between instances of 'int' and 'str'", gt.getMessage());
      var ge = assertThrows(PyTypeError.class, () -> ComparisonIntrinsics.$ge$(1L, "a"));
      assertEquals("'>=' not supported between instances of 'int' and 'str'", ge.getMessage());
    }

    @Test
    public void orderingTuplesWithIncomparableElementsIsATypeError() {
      var e = assertThrows(PyTypeError.class,
          () -> ComparisonIntrinsics.$lt$(pyTuple(1L, 1L), pyTuple(1L, "a")));
      assertEquals("'<' not supported between instances of 'int' and 'str'", e.getMessage());
    }

    @Test
    public void orderingNoneIsATypeError() {
      var e = assertThrows(PyTypeError.class,
          () -> ComparisonIntrinsics.$lt$(PyNone.INSTANCE, PyNone.INSTANCE));
      assertEquals("'<' not supported between instances of 'NoneType' and 'NoneType'", e.getMessage());
    }

    @Test
    public void orderingPyNoneVsIntIsATypeError() {
      var e = assertThrows(PyTypeError.class,
          () -> ComparisonIntrinsics.$lt$(PyNone.INSTANCE, 1L));
      assertEquals("'<' not supported between instances of 'NoneType' and 'int'",
          e.getMessage());
    }

    @Test
    public void orderingPyNoneVsStrIsATypeError() {
      var e = assertThrows(PyTypeError.class,
          () -> ComparisonIntrinsics.$lt$(PyNone.INSTANCE, "a"));
      assertEquals("'<' not supported between instances of 'NoneType' and 'str'",
          e.getMessage());
    }

    @Test
    public void orderingStrVsIntIsATypeError() {
      var e = assertThrows(PyTypeError.class,
          () -> ComparisonIntrinsics.$lt$("a", 1L));
      assertEquals("'<' not supported between instances of 'str' and 'int'",
          e.getMessage());
    }

    @Test
    public void orderingIntVsNoneIsATypeError() {
      var e = assertThrows(PyTypeError.class,
          () -> ComparisonIntrinsics.$lt$(1L, PyNone.INSTANCE));
      assertEquals("'<' not supported between instances of 'int' and 'NoneType'",
          e.getMessage());
    }
  }

  @Nested
  public final class Specialisation {
    @Test
    public void specializedEqMatchesTheGenericOne() throws Throwable {
      var eq = RT.bsmSpecialize(MethodHandles.lookup(), "$eq$",
          MethodType.methodType(boolean.class, Object.class, Object.class), ComparisonIntrinsics.class).getTarget();
      List<Object> values = List.of(
          0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(30),
          true, false, 0.0d, -0.0d, 1.0d, 1.5d, 0x1p63, Double.NaN, Double.POSITIVE_INFINITY,
          "", "a", PyNone.INSTANCE,
          new PyTuple1(1L), new PyTuple2(1L, "a"), new PyTuple.PyTupleN(new Object[]{1L, "a"}));
      for (var round = 0; round < 3; round++) {
        for (var a : values) {
          for (var b : values) {
            assertEquals(ComparisonIntrinsics.$eq$(a, b), (boolean) eq.invokeExact(a, b), a + " == " + b);
          }
        }
      }
    }
  }
}