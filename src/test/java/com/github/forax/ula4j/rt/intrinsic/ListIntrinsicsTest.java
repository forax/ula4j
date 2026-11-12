package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyIndexError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class ListIntrinsicsTest {

    @Test
    public void newListIsEmpty() {
        var list = ListIntrinsics.newList();

        assertEquals(0L, ListIntrinsics.$size$(list));
    }

    @Nested
    public final class Size {
        @Test
        public void sizeReflectsElements() {
            var list = new PyList<>(1L, "a", 2.5);

            assertEquals(3L, ListIntrinsics.$size$(list));
        }
    }

    @Nested
    public final class Get {
        @Test
        public void positiveIndex() {
            var list = new PyList<>(10L, 20L, 30L);

            assertEquals(10L, ListIntrinsics.$get$(list, 0));
            assertEquals(30L, ListIntrinsics.$get$(list, 2));
        }

        @Test
        public void negativeIndex() {
            var list = new PyList<>(10L, 20L, 30L);

            assertEquals(30L, ListIntrinsics.$get$(list, -1));
            assertEquals(10L, ListIntrinsics.$get$(list, -3));
        }

        @Test
        public void outOfRangeRaisesIndexError() {
            var list = new PyList<>(10L, 20L, 30L);

            assertThrows(PyIndexError.class, () -> ListIntrinsics.$get$(list, 3));
            assertThrows(PyIndexError.class, () -> ListIntrinsics.$get$(list, -4));
        }
    }

    @Nested
    public final class Set {
        @Test
        public void returnsNoneAndUpdates() {
            var list = new PyList<>(10L, 20L);
            var result = ListIntrinsics.$set$(list, 0, 99L);

            assertSame(PyNone.INSTANCE, result);
            assertEquals(99L, ListIntrinsics.$get$(list, 0));
        }

        @Test
        public void negativeIndex() {
            var list = new PyList<>(10L, 20L);
            ListIntrinsics.$set$(list, -1, 99L);

            assertEquals(99L, ListIntrinsics.$get$(list, 1));
        }

        @Test
        public void outOfRangeRaisesIndexError() {
            var list = new PyList<>(10L);
            assertThrows(PyIndexError.class, () -> ListIntrinsics.$set$(list, 1, 99L));
        }
    }

    @Nested
    public final class Delete {
        @Test
        public void returnsNoneAndRemoves() {
            var list = new PyList<>(10L, 20L, 30L);
            var result = ListIntrinsics.$delete$(list, 1);

            assertSame(PyNone.INSTANCE, result);
            assertEquals(2L, ListIntrinsics.$size$(list));
            assertEquals(30L, ListIntrinsics.$get$(list, 1));
        }

        @Test
        public void negativeIndex() {
            var list = new PyList<>(10L, 20L, 30L);
            ListIntrinsics.$delete$(list, -1);

            assertEquals(2L, ListIntrinsics.$size$(list));
        }

        @Test
        public void outOfRangeRaisesIndexError() {
            var list = new PyList<>(10L);
            assertThrows(PyIndexError.class, () -> ListIntrinsics.$delete$(list, 1));
        }
    }

    @Nested
    public final class Concat {
        @Test
        public void concatenatesTwoLists() {
            var left = new PyList<>(1L, 2L);
            var right = new PyList<>("a", "b");
            var result = ListIntrinsics.$concat$(left, right);

            assertEquals(4L, ListIntrinsics.$size$(result));
            assertEquals(1L, ListIntrinsics.$get$(result, 0));
            assertEquals("b", ListIntrinsics.$get$(result, 3));
        }

        @Test
        public void resultIsIndependent() {
            var left = new PyList<>(1L);
            var right = new PyList<>(2L);
            var result = ListIntrinsics.$concat$(left, right);
            left.append(99L);

            assertEquals(2L, ListIntrinsics.$size$(result));
        }
    }

    @Nested
    public final class Repeat {
        @Test
        public void zeroOrNegativeCountReturnsEmpty() {
            var list = new PyList<>(1L, 2L);

            assertTrue(ListIntrinsics.$repeat$(list, 0).isEmpty());
            assertTrue(ListIntrinsics.$repeat$(list, -1).isEmpty());
        }

        @Test
        public void countOneReturnsCopy() {
            var list = new PyList<>(1L, 2L);
            var result = ListIntrinsics.$repeat$(list, 1);

            assertEquals(2L, ListIntrinsics.$size$(result));
            assertNotSame(list, result);
        }

        @Test
        public void repeatsElements() {
            var list = new PyList<>(1L, 2L);
            var result = ListIntrinsics.$repeat$(list, 3);

            assertEquals(6L, ListIntrinsics.$size$(result));
            assertEquals(1L, ListIntrinsics.$get$(result, 0));
            assertEquals(2L, ListIntrinsics.$get$(result, 5));
        }
    }

    @Nested
    public final class IAdd {
        @Test
        public void withAnotherList() {
            var list = new PyList<Object>(1L, 2L);
            var other = new PyList<>(3L, 4L);
            var result = ListIntrinsics.$iadd$(list, other);

            assertSame(list, result);
            assertEquals(4L, ListIntrinsics.$size$(list));
        }

        @Test
        public void withTuple() {
            var list = new PyList<Object>(1L);
            PyTuple tuple = TupleIntrinsics.fromArray(2L, 3L);
            ListIntrinsics.$iadd$(list, tuple);

            assertEquals(3L, ListIntrinsics.$size$(list));
        }

        @Test
        public void withString() {
            var list = new PyList<Object>("a");
            ListIntrinsics.$iadd$(list, "bc");

            assertEquals(3L, ListIntrinsics.$size$(list));
            assertEquals("b", ListIntrinsics.$get$(list, 1));
            assertEquals("c", ListIntrinsics.$get$(list, 2));
        }
    }

    @Nested
    public final class IMul {
        @Test
        public void repeatsInPlace() {
            var list = new PyList<>(1L, 2L);
            var result = ListIntrinsics.$imul$(list, 3);

            assertSame(list, result);
            assertEquals(6L, ListIntrinsics.$size$(list));
        }

        @Test
        public void zeroOrNegativeClears() {
            var list = new PyList<>(1L, 2L);
            ListIntrinsics.$imul$(list, 0);

            assertTrue(list.isEmpty());
        }
    }
}