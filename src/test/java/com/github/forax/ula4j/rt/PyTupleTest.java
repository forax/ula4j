package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.PyTuple.PyTuple3;
import com.github.forax.ula4j.rt.PyTuple.PyTuple4;
import com.github.forax.ula4j.rt.PyTuple.PyTupleN;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class PyTupleTest {

  @Nested
  public final class PyTuple2Test {

    @Test
    public void tuple2Accessors() {
      var tuple = new PyTuple2(1L, "a");
      assertEquals(1L, tuple.item0());
      assertEquals("a", tuple.item1());
    }

    @Test
    public void tuple2RejectsJavaNull() {
      assertThrows(NullPointerException.class, () -> new PyTuple2(null, 1L));
      assertThrows(NullPointerException.class, () -> new PyTuple2(1L, null));
    }

    @Test
    public void tuple2AcceptsPyNone() {
      var tuple = new PyTuple2(PyNone.INSTANCE, PyNone.INSTANCE);
      assertSame(PyNone.INSTANCE, tuple.item0());
      assertSame(PyNone.INSTANCE, tuple.item1());
    }

    @Test
    public void tuple2ToString() {
      assertEquals("PyTuple2(1, a)", new PyTuple2(1L, "a").toString());
      assertEquals("PyTuple2(None, true)", new PyTuple2(PyNone.INSTANCE, true).toString());
    }
  }

  @Nested
  public final class PyTuple3Test {

    @Test
    public void tuple3Accessors() {
      var tuple = new PyTuple3(1L, 2L, 3L);
      assertEquals(1L, tuple.item0());
      assertEquals(2L, tuple.item1());
      assertEquals(3L, tuple.item2());
    }

    @Test
    public void tuple3RejectsJavaNull() {
      assertThrows(NullPointerException.class, () -> new PyTuple3(null, 2L, 3L));
      assertThrows(NullPointerException.class, () -> new PyTuple3(1L, null, 3L));
      assertThrows(NullPointerException.class, () -> new PyTuple3(1L, 2L, null));
    }

    @Test
    public void tuple3ToString() {
      assertEquals("PyTuple3(1, 2.5, x)", new PyTuple3(1L, 2.5, "x").toString());
    }
  }


  @Nested
  public final class PyTuple4Test {

    @Test
    public void tuple4Accessors() {
      var tuple = new PyTuple4(1L, 2L, 3L, 4L);
      assertEquals(1L, tuple.item0());
      assertEquals(2L, tuple.item1());
      assertEquals(3L, tuple.item2());
      assertEquals(4L, tuple.item4());  // the last component is currently named item4
    }

    @Test
    public void tuple4RejectsJavaNull() {
      assertThrows(NullPointerException.class, () -> new PyTuple4(null, 2L, 3L, 4L));
      assertThrows(NullPointerException.class, () -> new PyTuple4(1L, null, 3L, 4L));
      assertThrows(NullPointerException.class, () -> new PyTuple4(1L, 2L, null, 4L));
      assertThrows(NullPointerException.class, () -> new PyTuple4(1L, 2L, 3L, null));
    }

    @Test
    public void tuple4ToString() {
      assertEquals("PyTuple4(1, 2, 3, 4)", new PyTuple4(1L, 2L, 3L, 4L).toString());
    }
  }

  @Nested
  public final class PyTupleNTest {

    @Test
    public void tupleNLengthAndItems() {
      var tuple = new PyTupleN(new Object[]{1L, "a", PyNone.INSTANCE, true, 2.5});
      assertEquals(5, tuple.length());
      assertEquals(1L, tuple.item(0));
      assertEquals("a", tuple.item(1));
      assertSame(PyNone.INSTANCE, tuple.item(2));
      assertEquals(true, tuple.item(3));
      assertEquals(2.5, tuple.item(4));
    }

    @Test
    public void tupleNCanBeEmpty() {
      var tuple = new PyTupleN(new Object[0]);
      assertEquals(0, tuple.length());
      assertEquals("PyTupleN()", tuple.toString());
    }

    @Test
    public void tupleNCanBeSmallerThanFiveForGenericUse() {
      // the choice of representation is a performance property only, so PyTupleN must work for any size
      var tuple = new PyTupleN(new Object[]{1L, 2L});
      assertEquals(2, tuple.length());
      assertEquals(2L, tuple.item(1));
    }

    @Test
    public void tupleNDefensivelyCopiesItsInput() {
      var array = new Object[]{1L, 2L, 3L, 4L, 5L};
      var tuple = new PyTupleN(array);
      array[0] = 99L;
      array[4] = "changed";
      assertEquals(1L, tuple.item(0));
      assertEquals(5L, tuple.item(4));
    }

    @Test
    public void tupleNRejectsJavaNullElements() {
      assertThrows(NullPointerException.class, () -> new PyTupleN(new Object[]{1L, null, 3L}));
      assertThrows(NullPointerException.class, () -> new PyTupleN(new Object[]{null}));
    }

    @Test
    public void tupleNRejectsNullArray() {
      assertThrows(NullPointerException.class, () -> new PyTupleN(null));
    }

    @Test
    public void tupleNItemRejectsOutOfRangeIndexes() {
      var tuple = new PyTupleN(new Object[]{1L, 2L, 3L});
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> tuple.item(-1));
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> tuple.item(3));
    }

    @Test
    public void tupleNItemsAccessorIsForbidden() {
      var tuple = new PyTupleN(new Object[]{1L});
      assertThrows(AssertionError.class, tuple::items);
    }

    @Test
    public void tupleNToString() {
      assertEquals("PyTupleN(1, a, None, 2.5, true)",
          new PyTupleN(new Object[]{1L, "a", PyNone.INSTANCE, 2.5, true}).toString());
      assertEquals("PyTupleN(7)", new PyTupleN(new Object[]{7L}).toString());
    }

    @Test
    public void tupleNEqualsAndHashCode() {
      var a = new PyTupleN(new Object[]{1L, 2L, 3L, 4L, 5L});
      var b = new PyTupleN(new Object[]{1L, 2L, 3L, 4L, 5L});
      var differentValue = new PyTupleN(new Object[]{1L, 2L, 3L, 4L, 6L});
      var differentLength = new PyTupleN(new Object[]{1L, 2L, 3L, 4L});

      assertEquals(a, a);
      assertEquals(a, b);
      assertEquals(b, a);
      assertEquals(a.hashCode(), b.hashCode());
      assertNotEquals(a, differentValue);
      assertNotEquals(a, differentLength);
      assertNotEquals(a, null);
      assertNotEquals(a, "PyTupleN(1, 2, 3, 4, 5)");
    }

    @Test
    public void tupleNIsNotEqualToAFixedArityTupleWithTheSameItems() {
      var n = new PyTupleN(new Object[]{1L, 2L});
      var two = new PyTuple2(1L, 2L);
      assertNotEquals(n, two);
      assertNotEquals(two, n);
    }
  }


  @Nested
  public final class Shared {

    @Test
    public void fixedArityRecordsHaveValueEquality() {
      assertEquals(new PyTuple2(1L, "a"), new PyTuple2(1L, "a"));
      assertEquals(new PyTuple3(1L, 2L, 3L), new PyTuple3(1L, 2L, 3L));
      assertEquals(new PyTuple4(1L, 2L, 3L, 4L), new PyTuple4(1L, 2L, 3L, 4L));
      assertNotEquals(new PyTuple2(1L, "a"), new PyTuple2(1L, "b"));
      assertNotEquals(new PyTuple2(1L, 2L), new PyTuple3(1L, 2L, 3L));
    }

    @Test
    public void everyShapeIsCoveredByAnExhaustiveSwitch() {
      assertEquals(2, arity(new PyTuple2(1L, 2L)));
      assertEquals(3, arity(new PyTuple3(1L, 2L, 3L)));
      assertEquals(4, arity(new PyTuple4(1L, 2L, 3L, 4L)));
      assertEquals(0, arity(new PyTupleN(new Object[0])));
      assertEquals(6, arity(new PyTupleN(new Object[]{1L, 2L, 3L, 4L, 5L, 6L})));
    }

    private static int arity(PyTuple tuple) {
      return switch (tuple) {  // no default: this fails to compile if a new shape is added to the sealed interface
        case PyTuple2 _ -> 2;
        case PyTuple3 _ -> 3;
        case PyTuple4 _ -> 4;
        case PyTupleN n -> n.length();
      };
    }
  }
}