package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyDict;
import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyIndexError;
import com.github.forax.ula4j.rt.error.PyTypeError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static com.github.forax.ula4j.rt.intrinsic.ItemIntrinsics.*;
import static com.github.forax.ula4j.rt.intrinsic.TupleIntrinsics.fromArray;
import static org.junit.jupiter.api.Assertions.*;

public final class ItemIntrinsicsTest {

    @Nested
    public final class GetItem {
        @Test
        public void listWithLongIndex() {
            var list = new PyList<>(10L, 20L);

            assertEquals(10L, $getItem$(list, 0L));
            assertEquals(20L, $getItem$(list, 1L));
        }

        @Test
        public void listWithBooleanIndex() {
            var list = new PyList<>(10L, 20L);

            assertEquals(10L, $getItem$(list, false));
            assertEquals(20L, $getItem$(list, true));
        }

        @Test
        public void listWithNegativeIndex() {
            var list = new PyList<>(10L, 20L);

            assertEquals(20L, $getItem$(list, -1L));
        }

        @Test
        public void dictWithKey() {
            var dict = new PyDict<>();
            dict.put("a", 1L);

            assertEquals(1L, $getItem$(dict, "a"));
        }

        @Test
        public void tupleWithLongIndex() {
            PyTuple tuple = fromArray(10L, 20L);

            assertEquals(10L, $getItem$(tuple, 0L));
        }

        @Test
        public void nonSubscriptableRaisesTypeError() {
            assertThrows(PyTypeError.class, () -> $getItem$(1L, 0L));
        }
    }

    @Nested
    public final class SetItem {
        @Test
        public void listUpdate() {
            var list = new PyList<>(10L, 20L);
            var result = $setItem$(list, 0L, 99L);

            assertSame(com.github.forax.ula4j.rt.PyNone.INSTANCE, result);
            assertEquals(99L, $getItem$(list, 0L));
        }

        @Test
        public void dictUpdate() {
            var dict = new PyDict<Object, Object>();
            $setItem$(dict, "a", 1L);

            assertEquals(1L, $getItem$(dict, "a"));
        }

        @Test
        public void tupleRaisesTypeError() {
            PyTuple tuple = fromArray(10L);
            assertThrows(PyTypeError.class, () -> $setItem$(tuple, 0L, 99L));
        }

        @Test
        public void stringRaisesTypeError() {
            assertThrows(PyTypeError.class, () -> $setItem$("abc", 0L, "x"));
        }
    }

    @Nested
    public final class DeleteItem {
        @Test
        public void listDelete() {
            var list = new PyList<>(10L, 20L);
            var result = $deleteItem$(list, 0L);

            assertSame(com.github.forax.ula4j.rt.PyNone.INSTANCE, result);
            assertEquals(1L, list.size());
        }

        @Test
        public void dictDelete() {
            var dict = new PyDict<Object, Object>();
            dict.put("a", 1L);

            $deleteItem$(dict, "a");
            assertFalse(dict.containsKey("a"));
        }

        @Test
        public void tupleRaisesTypeError() {
            PyTuple tuple = fromArray(10L);
            assertThrows(PyTypeError.class, () -> $deleteItem$(tuple, 0L));
        }
    }

    @Nested
    public final class ToIndex {
        @Test
        public void longIsReturnedAsIs() {
            assertEquals(42L, $toIndex$(42L, "list"));
        }

        @Test
        public void booleanIsConverted() {
            assertEquals(1L, $toIndex$(true, "list"));
            assertEquals(0L, $toIndex$(false, "list"));
        }

        @Test
        public void bigIntegerRaisesIndexError() {
            assertThrows(PyIndexError.class, () -> $toIndex$(BigInteger.ONE, "list"));
        }

        @Test
        public void otherTypeRaisesTypeError() {
            assertThrows(PyTypeError.class, () -> $toIndex$("a", "list"));
        }
    }
}