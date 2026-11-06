package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.PyTuple.PyTupleN;
import com.github.forax.ula4j.rt.RT;
import com.github.forax.ula4j.rt.error.PyTypeError;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class EqualityIntrinsicsTest {
  private static void same(Object a, Object b) {
    assertTrue(EqualityIntrinsics.$eq$(a, b), a + " == " + b);
    assertTrue(EqualityIntrinsics.$eq$(b, a), b + " == " + a);
  }

  private static void different(Object a, Object b) {
    assertFalse(EqualityIntrinsics.$eq$(a, b), a + " == " + b);
    assertFalse(EqualityIntrinsics.$eq$(b, a), b + " == " + a);
  }

  @Test
  public void boolIntFloatAreUnified() {
    same(1L, true);
    same(0L, false);
    same(true, 1.0d);
    same(1L, 1.0d);
    same(true, true);
    different(true, false);
    different(2L, true);
    different(1L, 1.5d);
  }

  @Test
  public void intFloatComparisonIsExact() {
    different(9007199254740993L, 9007199254740992.0d);          // 2**53 + 1
    same(9007199254740992L, 9007199254740992.0d);
    different(Long.MAX_VALUE, 0x1p63);                           // (double) Long.MAX_VALUE == 2**63
    same(Long.MIN_VALUE, -0x1p63);
    same(BigInteger.ONE.shiftLeft(63), 0x1p63);
    same(BigInteger.ONE.shiftLeft(100), 0x1p100);
    different(BigInteger.ONE.shiftLeft(100).add(BigInteger.ONE), 0x1p100);
    same(BigInteger.TEN.pow(30), BigInteger.TEN.pow(30));
  }

  @Test
  public void nanInfinityAndSignedZero() {
    Object nan = Double.NaN;
    assertFalse(EqualityIntrinsics.$eq$(nan, nan));              // even for the same box
    different(nan, 0L);
    different(Double.POSITIVE_INFINITY, Long.MAX_VALUE);
    different(Double.POSITIVE_INFINITY, BigInteger.TEN.pow(400));
    same(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    same(-0.0d, 0.0d);
    same(-0.0d, 0L);
    same(-0.0d, false);
  }

  @Test
  public void stringsAndNone() {
    same("abc", "abc");
    different("abc", "abd");
    different("1", 1L);
    same(PyNone.INSTANCE, PyNone.INSTANCE);
    different(PyNone.INSTANCE, 0L);
    different(PyNone.INSTANCE, "None");
    different(PyNone.INSTANCE, false);
  }

  @Test
  public void tuplesCompareAcrossShapesAndRecursively() {
    same(new PyTuple2(1L, "a"), new PyTupleN(new Object[]{1.0d, "a"}));
    same(new PyTuple1(new PyTuple1(true)), new PyTuple1(new PyTupleN(new Object[]{1L})));
    different(new PyTuple2(1L, "a"), new PyTuple2(1L, "b"));
    different(new PyTuple1(1L), new PyTuple2(1L, 1L));
    different(new PyTuple1(1L), 1L);
    same(new PyTupleN(new Object[0]), new PyTupleN(new Object[0]));
  }

  @Test
  public void containerElementsUseTheIdentityShortcut() {
    // CPython: x = float('nan'); (x,) == (x,) is True, (float('nan'),) == (float('nan'),) is False
    Object x = Double.valueOf(Double.NaN);
    same(new PyTuple1(x), new PyTuple1(x));
    different(new PyTuple1(Double.valueOf(Double.NaN)), new PyTuple1(Double.valueOf(Double.NaN)));
  }

  @Test
  public void nonCanonicalLeftOperandIsAnError() {
    assertThrows(PyTypeError.class, () -> EqualityIntrinsics.$eq$(new Object(), 1L));
  }

  @Test
  public void specializedEqMatchesTheGenericOne() throws Throwable {
    var eq = RT.bsmSpecialize(MethodHandles.lookup(), "$eq$",
        MethodType.methodType(boolean.class, Object.class, Object.class), EqualityIntrinsics.class).getTarget();
    List<Object> values = List.of(
        0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(30),
        true, false, 0.0d, -0.0d, 1.0d, 1.5d, 0x1p63, Double.NaN, Double.POSITIVE_INFINITY,
        "", "a", PyNone.INSTANCE,
        new PyTuple1(1L), new PyTuple2(1L, "a"), new PyTupleN(new Object[]{1L, "a"}));
    for (var round = 0; round < 3; round++) {
      for (var a : values) {
        for (var b : values) {
          assertEquals(EqualityIntrinsics.$eq$(a, b), (boolean) eq.invokeExact(a, b), a + " == " + b);
        }
      }
    }
  }
}