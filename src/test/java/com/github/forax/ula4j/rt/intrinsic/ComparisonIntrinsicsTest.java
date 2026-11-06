package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.error.PyTypeError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ComparisonIntrinsicsTest {

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
      assertFalse(ComparisonIntrinsics.$eq$(1L, new PyTuple1(1L)));
      assertFalse(ComparisonIntrinsics.$eq$(new PyTuple1(1L), 1L));
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
  }


  @Nested
  public final class TupleEquality {

    @Test
    public void tupleEquality() {
      assertTrue(ComparisonIntrinsics.$eq$(new PyTuple1(1L), new PyTuple1(1L)));
      assertFalse(ComparisonIntrinsics.$eq$(new PyTuple1(1L), new PyTuple1(2L)));
      assertTrue(ComparisonIntrinsics.$eq$(new PyTuple2(1L, 2L), new PyTuple2(1L, 2L)));
      assertFalse(ComparisonIntrinsics.$eq$(new PyTuple2(1L, 2L), new PyTuple2(1L, 3L)));
      assertFalse(ComparisonIntrinsics.$eq$(new PyTuple2(1L, 2L), new PyTuple2(3L, 2L)));
      // Different lengths are never equal.
      assertFalse(ComparisonIntrinsics.$eq$(new PyTuple1(1L), new PyTuple2(1L, 2L)));
      assertFalse(ComparisonIntrinsics.$eq$(new PyTuple2(1L, 2L), new PyTuple1(1L)));
      // Nested tuples.
      assertTrue(ComparisonIntrinsics.$eq$(
          new PyTuple1(new PyTuple1(1L)),
          new PyTuple1(new PyTuple1(1L))));
      assertFalse(ComparisonIntrinsics.$eq$(
          new PyTuple1(new PyTuple1(1L)),
          new PyTuple1(new PyTuple1(2L))));
    }

    @Test
    public void tupleEqualityUsesIdentityShortcutBeforeEq() {
      // At the top level NaN != NaN (no shortcut), but inside a container the
      // same reference is considered equal — CPython's PyObject_RichCompareBool.
      Double nan = Double.NaN;  // boxed once; both tuples hold the same reference
      assertFalse(ComparisonIntrinsics.$eq$(nan, nan));
      assertTrue(ComparisonIntrinsics.$eq$(new PyTuple1(nan), new PyTuple1(nan)));
      assertTrue(ComparisonIntrinsics.$eq$(new PyTuple2(nan, 1L), new PyTuple2(nan, 1L)));
    }
  }

  @Nested
  public final class TupleOrdering {

    @Test
    public void tupleOrdering() {
      assertTrue(ComparisonIntrinsics.$lt$(new PyTuple1(1L), new PyTuple2(1L, 0L)));
      assertTrue(ComparisonIntrinsics.$lt$(new PyTuple2(1L, 2L), new PyTuple2(1L, 3L)));
      assertTrue(ComparisonIntrinsics.$lt$(new PyTuple2(1L, 2L), new PyTuple2(2L, 0L)));
      assertTrue(ComparisonIntrinsics.$gt$(new PyTuple2(2L, 0L), new PyTuple2(1L, 2L)));
      assertTrue(ComparisonIntrinsics.$le$(new PyTuple2(1L, 2L), new PyTuple2(1L, 2L)));
      assertTrue(ComparisonIntrinsics.$ge$(new PyTuple2(1L, 2L), new PyTuple2(1L, 2L)));
      assertFalse(ComparisonIntrinsics.$lt$(new PyTuple2(1L, 2L), new PyTuple2(1L, 2L)));
      // Common prefix, shorter is smaller.
      assertTrue(ComparisonIntrinsics.$lt$(new PyTuple1(1L), new PyTuple2(1L, 2L)));
      assertTrue(ComparisonIntrinsics.$gt$(new PyTuple2(1L, 2L), new PyTuple1(1L)));
      // String elements inside tuples.
      assertTrue(ComparisonIntrinsics.$lt$(
          new PyTuple2("a", 1L), new PyTuple2("b", 0L)));
      // Nested tuples.
      assertTrue(ComparisonIntrinsics.$lt$(
          new PyTuple1(new PyTuple1(1L)),
          new PyTuple1(new PyTuple1(2L))));
    }

    @Test
    public void tupleOrderingWithNaN() {
      Double nan = Double.NaN;
      // First items equal via identity shortcut → length decides.
      assertTrue(ComparisonIntrinsics.$lt$(new PyTuple1(nan), new PyTuple2(nan, 1L)));
      assertFalse(ComparisonIntrinsics.$lt$(new PyTuple2(nan, 1L), new PyTuple1(nan)));
      // Tuples with the same length and NaN are equal (identity shortcut).
      assertTrue(ComparisonIntrinsics.$le$(new PyTuple1(nan), new PyTuple1(nan)));
      assertTrue(ComparisonIntrinsics.$ge$(new PyTuple1(nan), new PyTuple1(nan)));
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
          () -> ComparisonIntrinsics.$lt$(new PyTuple2(1L, 1L), new PyTuple2(1L, "a")));
      assertEquals("'<' not supported between instances of 'int' and 'str'", e.getMessage());
    }

    @Test
    public void orderingNoneIsATypeError() {
      var e = assertThrows(PyTypeError.class,
          () -> ComparisonIntrinsics.$lt$(PyNone.INSTANCE, PyNone.INSTANCE));
      assertEquals("'<' not supported between instances of 'NoneType' and 'NoneType'", e.getMessage());
    }
  }
}