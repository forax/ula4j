package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.compiler.Compiler;
import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyTypeError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

public final class HashingIntrinsicsTest {
  private static long hash(Object value) {
    return HashingIntrinsics.$hash$(value);
  }

  private static PyTuple pyTuple(Object... elements) {
    return TupleIntrinsics.fromArray(elements);
  }

  @SafeVarargs
  private static <E> PyList<E> pyList(E... elements) {
    return new PyList<>(elements);
  }

  
  @Nested
  public final class NumericContract {
    @Test
    public void equalNumericValuesHaveEqualHashes() {
      long one = hash(1L);
      assertEquals(one, hash(true));
      assertEquals(one, hash(BigInteger.ONE));
      assertEquals(one, hash(1.0d));
      assertEquals(0L, hash(-0.0d));
      assertEquals(0L, hash(0L));
    }

    @Test
    public void smallIntegersHashIdenticallyAcrossLongBigIntegerAndDouble() {
      for (long i = -100; i <= 100; i++) {
        long expected = hash(i);
        assertEquals(expected, hash(BigInteger.valueOf(i)), "BigInteger " + i);
        assertEquals(expected, hash((double) i), "double " + i);
      }
    }
  }


  @Nested
  public final class LongHashing {
    @Test
    void longMinusOneIsRemappedToMinusTwo() {
      // hash = -1 would collide with CPython's reserved "error" sentinel.
      assertEquals(-2L, hash(-1L));
    }

    @Test
    public void longMinValueDoesNotOverflowWhenNegated() {
      // -Long.MIN_VALUE overflows back to Long.MIN_VALUE; read as unsigned that's 2**63,
      // which is congruent to 4 mod (2**61 - 1), then negated because the value is negative.
      assertEquals(-4L, hash(Long.MIN_VALUE));
    }

    @Test
    public void longMinValuePlusOne() {
      // -(MIN_VALUE + 1) == MAX_VALUE == 2**63 - 1 ≡ 3 mod (2**61 - 1), negated.
      assertEquals(-3L, hash(Long.MIN_VALUE + 1));
    }

    @Test
    public void longMaxValue() {
      // 2**63 - 1 = 4 * (2**61 - 1) + 3, so the residue is 3, positive.
      assertEquals(3L, hash(Long.MAX_VALUE));
    }

    @Test
    public void longHashesForRegularValues() {
      assertEquals(0L, hash(0L));
      assertEquals(1L, hash(1L));
      assertEquals(2L, hash(2L));
      assertEquals(-2L, hash(-2L));
      assertEquals(100L, hash(100L));
      assertEquals(-100L, hash(-100L));
    }
  }


  @Nested
  public final class BigIntegerHashing {
    @Test
    public void bigIntegerAgreesWithLongForInRangeValues() {
      long[] cases = {
          0L, 1L, -1L, 2L, -2L, 100L, -100L,
          Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE - 1
      };
      for (long v : cases) {
        assertEquals(hash(v), hash(BigInteger.valueOf(v)), "value " + v);
      }
    }

    @Test
    public void bigIntegerMinusOneIsRemappedToMinusTwo() {
      assertEquals(-2L, hash(BigInteger.valueOf(-1)));
      assertEquals(-2L, hash(BigInteger.ONE.negate()));
    }

    @Test
    public void bigIntegerNegationIsConsistentExceptForTheRemap() {
      // For abs % MODULUS != 1 the negative hash is the negation of the positive one.
      var ten = BigInteger.TEN;
      assertEquals(10L, hash(ten));
      assertEquals(-10L, hash(ten.negate()));
      assertEquals(100L, hash(BigInteger.valueOf(100)));
      assertEquals(-100L, hash(BigInteger.valueOf(-100)));
    }

    @Test
    public void bigIntegerHugeValuesDoNotThrow() {
      var huge = BigInteger.TWO.pow(200).add(BigInteger.valueOf(12345));
      long h = hash(huge);
      assertNotEquals(-1L, h);
      assertEquals(h, hash(huge));
      assertEquals(-h == -1L ? -2L : -h, hash(huge.negate()) == -1L ? -2L : hash(huge.negate()));
    }
  }


  @Nested
  public final class DoubleHashing {
    @Test
    public void floatHashMatchesThe64BitCpythonModularAlgorithm() {
      assertEquals(1_152_921_504_606_846_976L, hash(0.5d));
      assertEquals(1_152_921_504_606_846_977L, hash(1.5d));
      assertEquals(314159L, hash(Double.POSITIVE_INFINITY));
      assertEquals(-314159L, hash(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void doubleNaNIsTheConstantZero() {
      // NaN != NaN so only pathological NaN-keyed dicts collide; both spellings agree.
      assertEquals(0L, hash(Double.NaN));
      assertEquals(0L, hash(Double.longBitsToDouble(0x7ff8000000000001L)));
      assertEquals(0L, hash(Double.longBitsToDouble(0x7ff8000000000002L)));
      assertEquals(0L, hash(Double.longBitsToDouble(0xfff8000000000000L)));
    }

    @Test
    public void doubleMinusOneIsRemappedToMinusTwo() {
      assertEquals(-2L, hash(-1.0d));
    }

    @Test
    public void doubleNegationMatchesCpythonForNonRemapValues() {
      // hash(1.5) is 1152921504606846977 (from the spec test); negation gives its negative.
      assertEquals(-1_152_921_504_606_846_977L, hash(-1.5d));
      assertEquals(-100L, hash(-100.0d));
    }

    @Test
    public void doubleSubnormalValuesAreStableAndNeverReturnMinusOne() {
      long h = hash(Double.MIN_VALUE);
      assertNotEquals(-1L, h);
      assertEquals(h, hash(Double.MIN_VALUE));
      long next = hash(Double.longBitsToDouble(1L));
      assertNotEquals(-1L, next);
      // Two different subnormals almost certainly hash differently; not asserted, only no crash.
      hash(Double.longBitsToDouble(3L));
    }

    @Test
    public void doubleLargeFiniteValuesDoNotThrow() {
      long maxH = hash(Double.MAX_VALUE);
      assertNotEquals(-1L, maxH);
      assertEquals(maxH, hash(Double.MAX_VALUE));
      long small = hash(-Double.MAX_VALUE);
      assertNotEquals(-1L, small);
    }
  }


  @Nested
  public final class BooleanHashing {
    @Test
    public void booleanFalseHashesToZero() {
      assertEquals(0L, hash(false));
      assertEquals(hash(0L), hash(false));
      assertEquals(hash(0.0d), hash(false));
      assertEquals(hash(BigInteger.ZERO), hash(false));
    }

    @Test
    public void booleanTrueMatchesNumericOne() {
      long one = hash(1L);
      assertEquals(one, hash(true));
      assertEquals(one, hash(1.0d));
      assertEquals(one, hash(BigInteger.ONE));
    }
  }


  @Nested
  public final class StringHashing {
    @Test
    public void emptyStringHashesToZero() {
      // Java's "".hashCode() is 0, spread over 64 bits by *prime and xor-fold stays 0.
      assertEquals(0L, hash(""));
    }

    @Test
    public void equalStringsHaveEqualHashesRegardlessOfInstance() {
      String a = "hello";
      String b = new String("hello");
      assertNotSame(a, b);
      assertEquals(hash(a), hash(b));
      // Repeated calls must be deterministic.
      assertEquals(hash(a), hash(a));
    }

    @Test
    public void stringHashIsNeverMinusOne() {
      assertNotEquals(-1L, hash("abc"));
      assertNotEquals(-1L, hash(""));
      assertNotEquals(-1L, hash("a very long string that is not short"));
    }
  }


  @Nested
  public final class TupleHashing {
    @Test
    public void nestedTupleEqualityAndHashingAreRecursive() {
      var left = pyTuple(1L, pyTuple("x"));
      var right = pyTuple(1.0d, pyTuple("x"));
      assertEquals(hash(left), hash(right));
    }

    @Test
    public void emptyTuplesHaveEqualHashes() {
      var a = pyTuple();
      var b = pyTuple();
      assertEquals(hash(a), hash(b));
      assertNotEquals(-1L, hash(a));
    }

    @Test
    public void tuple1AndTupleNAgreeForSameContent() {
      assertEquals(hash(pyTuple(42L)), hash(pyTuple(42L)));
      assertEquals(hash(pyTuple(1L, 2L)), hash(pyTuple(1L, 2L)));
    }

    @Test
    public void tupleContainingMinusOneIsStable() {
      var t = pyTuple(-1L);
      long h = hash(t);
      assertNotEquals(-1L, h);
      assertEquals(h, hash(t));
    }

    @Test
    public void tupleContainingLongMinValueIsStable() {
      var t = pyTuple(Long.MIN_VALUE);
      long h = hash(t);
      assertNotEquals(-1L, h);
      assertEquals(h, hash(t));
    }

    @Test
    public void tupleHashUsesEachElementHashRecursively() {
      // Same content, different concrete tuple subclasses → same hash.
      var a = pyTuple(pyTuple(1L), pyTuple("x"));
      var b = pyTuple(pyTuple(1L), pyTuple("x"));
      assertEquals(hash(a), hash(b));
    }

    @Test
    public void deeplyNestedTupleWithListIsUnhashable() {
      var deep = pyTuple(pyTuple(pyTuple(pyList())));
      assertThrows(PyTypeError.class, () -> hash(deep));
    }
  }


  @Nested
  public final class UnhashableValues {
    @Test
    public void listsCompareRecursivelyButAreUnhashable() {
      var list = pyList(1L);
      assertThrows(PyTypeError.class, () -> hash(list));
    }

    @Test
    public void tupleContainingAnUnhashableValueIsUnhashable() {
      var tuple = pyTuple(pyList());
      var ex = assertThrows(PyTypeError.class, () -> hash(tuple));
      assertTrue(ex.getMessage().contains("unhashable type: 'list'"),
          "was: " + ex.getMessage());
    }

    @Test
    public void listErrorMessage() {
      var ex = assertThrows(PyTypeError.class, () -> hash(new PyList<>()));
      assertEquals("unhashable type: 'list'", ex.getMessage());
    }
  }


  @Nested
  public final class NoneAndFunctionHashing {
    @Test
    public void noneHashIsUnique() {
      assertEquals(hash(PyNone.INSTANCE), hash(PyNone.INSTANCE));
    }

    @Test
    public void pyFunctionHashUsesIdentityHashCode() {
      var fn = Compiler.run("""
          def fn():
              pass
          """).getGlobal("fn");
      assertEquals(hash(fn), hash(fn));
      assertEquals(hash(fn), System.identityHashCode(fn));
    }
  }


  @Nested
  public final class NonCanonicalValues {
    @Test
    public void boxedIntegerIsRejected() {
      // The runtime uses Long, not Integer; anything else falls through to the default case.
      var ex = assertThrows(PyTypeError.class, () -> hash(Integer.valueOf(1)));
      assertTrue(ex.getMessage().contains("java.lang.Integer"), "was: " + ex.getMessage());
      assertTrue(ex.getMessage().startsWith("not a canonical runtime value:"),
          "was: " + ex.getMessage());
    }

    @Test
    public void arbitraryObjectIsRejected() {
      var ex = assertThrows(PyTypeError.class, () -> hash(new Object()));
      assertTrue(ex.getMessage().contains("java.lang.Object"), "was: " + ex.getMessage());
    }

    @Test
    public void nullSubjectThrowsNullPointerException() {
      // Pattern-matching switch over a null subject NPEs before reaching the default arm.
      assertThrows(NullPointerException.class, () -> hash(null));
    }
  }


  @Nested
  public final class GlobalInvariants {
    @Test
    public void hashNeverReturnsMinusOne() {
      Object[] probes = {
          false, true,
          0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
          BigInteger.ZERO, BigInteger.ONE, BigInteger.valueOf(-1),
          BigInteger.TWO.pow(200).negate(),
          0.0d, -0.0d, 1.0d, -1.0d, 0.5d, -1.5d,
          Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
          Double.MIN_VALUE, Double.MAX_VALUE,
          "", "abc", "a fairly long identifier to exercise the 64-bit spread",
          pyTuple(),
          pyTuple(-1L),
          pyTuple(1L, pyTuple(2.5d)),
      };
      for (Object probe : probes) {
        long h = hash(probe);
        assertNotEquals(-1L, h, "hash(-1) sentinel returned for " + probe);
      }
    }
  }
}