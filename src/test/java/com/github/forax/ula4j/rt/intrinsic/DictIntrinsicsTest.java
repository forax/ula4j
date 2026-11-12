package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyDict;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.error.PyKeyError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DictIntrinsicsTest {
  @Nested
  public final class Size {
    @Test
    public void newDictIsEmpty() {
      var dict = DictIntrinsics.newDict();

      assertEquals(0L, DictIntrinsics.$size$(dict));
    }

    @Test
    public void sizeReflectsInsertions() {
      var dict = DictIntrinsics.newDict();
      dict.put(1L, "one");
      dict.put("a", 2L);

      assertEquals(2L, DictIntrinsics.$size$(dict));
    }
  }

  @Nested
  public final class Get {
    @Test
    public void existingKey() {
      var dict = new PyDict<>();
      dict.put(1L, "one");

      assertEquals("one", DictIntrinsics.$get$(dict, 1L));
    }

    @Test
    public void missingKeyRaisesKeyError() {
      var dict = new PyDict<>();
      dict.put(1L, "hello");

      assertThrows(PyKeyError.class, () -> DictIntrinsics.$get$(dict, 2L));
    }
  }

  @Nested
  public final class Set {
    @Test
    public void insertsNewKey() {
      var dict = DictIntrinsics.newDict();
      var result = DictIntrinsics.$set$(dict, 1L, "one");

      assertSame(PyNone.INSTANCE, result);
      assertEquals("one", DictIntrinsics.$get$(dict, 1L));
    }

    @Test
    public void replacesExistingKey() {
      var dict = DictIntrinsics.newDict();
      dict.put(1L, "one");

      DictIntrinsics.$set$(dict, 1L, "uno");
      assertEquals("uno", DictIntrinsics.$get$(dict, 1L));
    }
  }

  @Nested
  public final class Delete {
    @Test
    public void removesExistingKey() {
      var dict = new PyDict<>();
      dict.put(1L, "one");

      var result = DictIntrinsics.$delete$(dict, 1L);
      assertSame(PyNone.INSTANCE, result);
      assertFalse(DictIntrinsics.$in$(dict, 1L));
    }

    @Test
    public void missingKeyRaisesKeyError() {
      var dict = new PyDict<>();
      dict.put(1L, "one");

      assertThrows(PyKeyError.class, () -> DictIntrinsics.$delete$(dict, 2L));
    }
  }

  @Test
  public void containsKey() {
    var dict = DictIntrinsics.newDict();
    dict.put(1L, "one");

    assertTrue(DictIntrinsics.$in$(dict, 1L));
    assertFalse(DictIntrinsics.$in$(dict, 2L));
  }

  @Nested
  public final class Equals {
    @Test
    public void sameInstance() {
      var dict = DictIntrinsics.newDict();

      assertTrue(DictIntrinsics.equals(dict, dict));
    }

    @Test
    public void differentSizes() {
      var left = DictIntrinsics.newDict();
      left.put(1L, "one");
      var right = DictIntrinsics.newDict();

      assertFalse(DictIntrinsics.equals(left, right));
    }

    @Test
    public void sameContent() {
      var left = DictIntrinsics.newDict();
      left.put(1L, "one");
      left.put("a", 2L);
      var right = DictIntrinsics.newDict();
      right.put("a", 2L);
      right.put(1L, "one");

      assertTrue(DictIntrinsics.equals(left, right));
    }

    @Test
    public void differentValues() {
      var left = DictIntrinsics.newDict();
      left.put(1L, "one");

      var right = DictIntrinsics.newDict();
      right.put(1L, "uno");

      assertFalse(DictIntrinsics.equals(left, right));
    }
  }
}