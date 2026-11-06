package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.PyTuple.PyTuple3;
import com.github.forax.ula4j.rt.PyTuple.PyTuple4;
import com.github.forax.ula4j.rt.PyTuple.PyTupleN;
import com.github.forax.ula4j.rt.RT;
import com.github.forax.ula4j.rt.error.PyIndexError;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

import static com.github.forax.ula4j.rt.intrinsic.TupleIntrinsics.*;
import static org.junit.jupiter.api.Assertions.*;

public final class TupleIntrinsicsTest {
  private static final List<PyTuple> TUPLES = List.of(
      fromArray(), fromArray(1L), fromArray(1L, "a"), fromArray(1L, "a", 2.5), fromArray(1L, "a", 2.5, true),
      fromArray(1L, "a", 2.5, true, PyNone.INSTANCE), fromArray(1L, 2L, 3L, 4L, 5L, 6L, 7L),
      new PyTupleN(new Object[]{1L, 2L}));  // a non-canonical shape must work too

  @Test
  public void fromArrayUsesTheCanonicalShapes() {
    assertInstanceOf(PyTupleN.class, fromArray());
    assertInstanceOf(PyTuple1.class, fromArray(1L));
    assertInstanceOf(PyTuple2.class, fromArray(1L, 2L));
    assertInstanceOf(PyTuple3.class, fromArray(1L, 2L, 3L));
    assertInstanceOf(PyTuple4.class, fromArray(1L, 2L, 3L, 4L));
    assertInstanceOf(PyTupleN.class, fromArray(1L, 2L, 3L, 4L, 5L));
  }

  @Test
  public void sizeOfEveryShape() {
    assertEquals(0L, $size$(fromArray()));
    assertEquals(1L, $size$(fromArray(1L)));
    assertEquals(2L, $size$(fromArray(1L, 2L)));
    assertEquals(3L, $size$(fromArray(1L, 2L, 3L)));
    assertEquals(4L, $size$(fromArray(1L, 2L, 3L, 4L)));
    assertEquals(6L, $size$(fromArray(1L, 2L, 3L, 4L, 5L, 6L)));
    assertEquals(2L, $size$(new PyTupleN(new Object[]{1L, 2L})));
  }

  @Test
  public void getSupportsNegativeIndexes() {
    for (var t : TUPLES) {
      var size = (int) $size$(t);
      for (var i = 0; i < size; i++) {
        assertSame($get$(t, i), $get$(t, i - size), t + "[" + i + "]");
      }
    }
    var t = fromArray(10L, 20L, 30L);
    assertEquals(10L, $get$(t, 0));
    assertEquals(30L, $get$(t, -1));
    assertEquals(10L, $get$(t, -3));
  }

  @Test
  public void getOutOfRangeRaisesIndexError() {
    for (var t : TUPLES) {
      var size = $size$(t);
      for (var index : new long[]{size, size + 1, -size - 1, Long.MAX_VALUE, Long.MIN_VALUE}) {
        var error = assertThrows(PyIndexError.class, () -> $get$(t, index), t + "[" + index + "]");
        assertEquals("tuple index out of range", error.getMessage());
      }
    }
  }

  @Test
  public void concatenationWorksAcrossShapes() {
    for (var left : TUPLES) {
      for (var right : TUPLES) {
        var result = $concat$(left, right);
        var leftSize = (int) $size$(left);
        var rightSize = (int) $size$(right);
        assertEquals(leftSize + rightSize, $size$(result));
        for (var i = 0; i < leftSize; i++) {
          assertSame($get$(left, i), $get$(result, i));
        }
        for (var i = 0; i < rightSize; i++) {
          assertSame($get$(right, i), $get$(result, leftSize + i));
        }
      }
    }
  }

  @Test
  public void concatenationProducesCanonicalShapes() {
    assertInstanceOf(PyTuple2.class, $concat$(fromArray(1L), fromArray(2L)));
    assertInstanceOf(PyTuple4.class, $concat$(fromArray(1L, 2L), fromArray(3L, 4L)));
    assertInstanceOf(PyTupleN.class, $concat$(fromArray(1L, 2L), fromArray(3L, 4L, 5L)));
  }

  @Test
  public void concatenationDoesNotModifyItsOperands() {
    var left = fromArray(1L, 2L);
    var right = fromArray(3L);
    $concat$(left, right);
    assertEquals(fromArray(1L, 2L), left);
    assertEquals(fromArray(3L), right);
  }

  @Test
  public void repetition() {
    assertEquals(0L, $size$($repeat$(fromArray(1L, 2L), 0)));
    assertEquals(0L, $size$($repeat$(fromArray(1L, 2L), -5)));
    assertEquals(0L, $size$($repeat$(fromArray(), 1_000_000_000L)));
    assertSame(TUPLES.get(2), $repeat$(TUPLES.get(2), 1));

    var repeated = $repeat$(fromArray(1L, "a"), 3);
    assertEquals(6L, $size$(repeated));
    for (var i = 0; i < 6; i++) {
      assertEquals(i % 2 == 0 ? 1L : "a", $get$(repeated, i));
    }
    assertInstanceOf(PyTuple4.class, $repeat$(fromArray(1L), 4));
    assertInstanceOf(PyTupleN.class, $repeat$(fromArray(1L), 5));
  }

  @Test
  public void repetitionTooLargeForAJavaArray() {
    assertThrows(OutOfMemoryError.class, () -> $repeat$(fromArray(1L, 2L), Long.MAX_VALUE));
    assertThrows(OutOfMemoryError.class, () -> $repeat$(fromArray(1L), Integer.MAX_VALUE + 1L));
  }


  @Test
  public void specializedIntrinsicsMatchTheGenericOnes() throws Throwable {
    var lookup = MethodHandles.lookup();
    var tuple = PyTuple.class;
    MethodHandle size = RT.bsmSpecialize(lookup, "$size$",
        MethodType.methodType(long.class, tuple), TupleIntrinsics.class).getTarget();
    MethodHandle get = RT.bsmSpecialize(lookup, "$get$",
        MethodType.methodType(Object.class, tuple, long.class), TupleIntrinsics.class).getTarget();
    MethodHandle concat = RT.bsmSpecialize(lookup, "$concat$",
        MethodType.methodType(tuple, tuple, tuple), TupleIntrinsics.class).getTarget();
    MethodHandle repeat = RT.bsmSpecialize(lookup, "$repeat$",
        MethodType.methodType(tuple, tuple, long.class), TupleIntrinsics.class).getTarget();

    for (var round = 0; round < 3; round++) {
      for (var a : TUPLES) {
        assertEquals($size$(a), (long) size.invokeExact(a));
        for (var index : new long[]{0, 1, -1, 5, -9}) {
          Object expected;
          try {
            expected = $get$(a, index);
          } catch (PyIndexError e) {
            var actual = assertThrows(PyIndexError.class, () -> {
              Object ignored = (Object) get.invokeExact(a, index);
            });
            assertEquals(e.getMessage(), actual.getMessage());
            continue;
          }
          assertSame(expected, (Object) get.invokeExact(a, index));
        }
        for (var count : new long[]{0, 1, 3}) {
          assertEquals($repeat$(a, count), (PyTuple) repeat.invokeExact(a, count));
        }
        for (var b : TUPLES) {
          assertEquals($concat$(a, b), (PyTuple) concat.invokeExact(a, b));
        }
      }
    }
  }
}