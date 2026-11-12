package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyDict;
import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyTypeError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ContainmentIntrinsicsTest {

  @Nested
  public final class StringContainer {
    @Test
    public void containsSubstring() {
      assertTrue(ContainmentIntrinsics.$in$("bc", "abcd"));
      assertFalse(ContainmentIntrinsics.$in$("xy", "abcd"));
    }

    @Test
    public void nonStringItemRaisesTypeError() {
      assertThrows(PyTypeError.class, () -> ContainmentIntrinsics.$in$(1L, "abcd"));
    }
  }

  @Nested
  public final class TupleContainer {
    @Test
    public void containsExistingElement() {
      var tuple = TupleIntrinsics.fromArray(1L, "a", 2.5, true);
      assertTrue(ContainmentIntrinsics.$in$(1L, tuple));
      assertTrue(ContainmentIntrinsics.$in$("a", tuple));
      assertTrue(ContainmentIntrinsics.$in$(2.5, tuple));
      assertTrue(ContainmentIntrinsics.$in$(true, tuple));
    }

    @Test
    public void doesNotContainMissingElement() {
      var tuple = TupleIntrinsics.fromArray(1L, "a", 2.5, true);
      assertFalse(ContainmentIntrinsics.$in$(42L, tuple));
      assertFalse(ContainmentIntrinsics.$in$("z", tuple));
    }
  }

  @Nested
  public final class ListContainer {
    @Test
    public void containsExistingElement() {
      var list = new PyList<>(1L, "a", 2.5, true);
      assertTrue(ContainmentIntrinsics.$in$(1L, list));
      assertTrue(ContainmentIntrinsics.$in$("a", list));
      assertTrue(ContainmentIntrinsics.$in$(2.5, list));
      assertTrue(ContainmentIntrinsics.$in$(true, list));
    }

    @Test
    public void doesNotContainMissingElement() {
      var list = new PyList<>(1L, "a", 2.5, true);
      assertFalse(ContainmentIntrinsics.$in$(42L, list));
    }
  }

  @Nested
  public final class DictContainer {
    @Test
    public void containsExistingKey() {
      var dict = new PyDict<>();
      dict.put(1L, "one");
      dict.put("a", 2L);

      assertTrue(ContainmentIntrinsics.$in$(1L, dict));
      assertTrue(ContainmentIntrinsics.$in$("a", dict));
    }

    @Test
    public void doesNotContainMissingKey() {
      var dict = new PyDict<>();
      dict.put(1L, "one");
      dict.put("a", 2L);

      assertFalse(ContainmentIntrinsics.$in$(42L, dict));
    }
  }

  @Test
  public void nonIterableContainerRaisesTypeError() {
    assertThrows(PyTypeError.class, () -> ContainmentIntrinsics.$in$(1L, 1L));
  }
}