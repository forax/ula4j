package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.error.PyZeroDivisionError;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ArithmeticIntrinsicsTest {
  @Test
  public void overflowPromotesToBigInteger() {
    assertEquals(new BigInteger("9223372036854775808"), ArithmeticIntrinsics.$add$(Long.MAX_VALUE, 1L));
    assertEquals(BigInteger.ONE.shiftLeft(63), ArithmeticIntrinsics.$floorDiv$(Long.MIN_VALUE, -1L));
    assertEquals(BigInteger.ONE.shiftLeft(100), ArithmeticIntrinsics.$pow$(2L, 100L));
    assertEquals(BigInteger.ONE.shiftLeft(64), ArithmeticIntrinsics.$lshift$(1L, 64L));
  }

  @Test
  public void floorSemantics() {
    assertEquals(-4L, ArithmeticIntrinsics.$floorDiv$(-7L, 2L));
    assertEquals(1L, ArithmeticIntrinsics.$mod$(-7L, 2L));
    assertEquals(-1L, ArithmeticIntrinsics.$mod$(7L, -2L));
    assertEquals(0.5, ArithmeticIntrinsics.$mod$(-7.5, 2.0));
    assertEquals(-1L, ArithmeticIntrinsics.$rshift$(-1L, 100L));
  }

  @Test
  public void trueDivision() {
    assertEquals(0.5, ArithmeticIntrinsics.$div$(1L, 2L));
    assertEquals(-0.0, ArithmeticIntrinsics.$div$(0L, -5L));
    assertEquals((double) Long.MAX_VALUE, ArithmeticIntrinsics.$div$(Long.MAX_VALUE, 1L));
    assertEquals(10.0, ArithmeticIntrinsics.$div$(BigInteger.TEN.pow(30), BigInteger.TEN.pow(29)));
    assertThrows(PyZeroDivisionError.class, () -> ArithmeticIntrinsics.$div$(1L, 0L));
  }

  @Test
  public void boolIsAnInt() {
    assertEquals(2L, ArithmeticIntrinsics.$add$(true, true));
    assertEquals(0.5, ArithmeticIntrinsics.$pow$(2L, -1L));
  }

  @Test
  public void sequences() {
    assertEquals("ababab", ArithmeticIntrinsics.$mul$("ab", 3L));
    var e = assertThrows(PyTypeError.class, () -> ArithmeticIntrinsics.$add$(1L, "a"));
    assertEquals("unsupported operand type(s) for +: 'int' and 'str'", e.getMessage());
  }
}