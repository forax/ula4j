package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.error.PyTypeError;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public final class PyDictTest {

  @Test
  public void newDictIsEmpty() {
    var dict = new PyDict<Object, Object>();

    assertEquals(0, dict.size());
    assertTrue(dict.isEmpty());
    assertEquals(0, dict.entryCount());
    assertEquals(0L, dict.layoutVersion());
  }

  @Test
  public void putReturnsNullForNewKeyAndPreviousValueForReplacement() {
    var dict = new PyDict<Object, Object>();

    assertNull(dict.put("a", "one"));
    assertEquals("one", dict.put("a", "two"));

    assertEquals(1, dict.size());
    assertEquals("two", dict.get("a"));
  }

  @Test
  public void replacingAnEqualKeyKeepsTheOriginalKeyObject() {
    var dict = new PyDict<Object, Object>();
    var firstKey = new String("hello");
    var equalKey = new String("hello");

    dict.put(firstKey, "first");
    dict.put(equalKey, "second");

    assertEquals(1, dict.size());
    assertSame(firstKey, dict.keyAt(0));
    assertEquals("second", dict.get(firstKey));
  }

  @Test
  public void noneCanBeUsedAsKeyAndValue() {
    var dict = new PyDict<Object, Object>();

    dict.put(PyNone.INSTANCE, PyNone.INSTANCE);

    assertEquals(1, dict.size());
    assertTrue(dict.containsKey(PyNone.INSTANCE));
    assertSame(PyNone.INSTANCE, dict.get(PyNone.INSTANCE));
  }

  @Test
  public void missingKeyReturnsNullAndRemoveReturnsNull() {
    var dict = new PyDict<Object, Object>();

    assertNull(dict.get("missing"));
    assertFalse(dict.containsKey("missing"));
    assertNull(dict.remove("missing"));

    dict.put("a", "value");

    assertEquals("value", dict.remove("a"));
    assertNull(dict.get("a"));
    assertFalse(dict.containsKey("a"));
    assertTrue(dict.isEmpty());
  }

  // Python key equality and hashing

  @Test
  public void booleanIntegerAndFloatKeysAreEquivalent() {
    var dict = new PyDict<Object, Object>();

    dict.put(Boolean.TRUE, "boolean");

    assertEquals("boolean", dict.put(1L, "integer"));
    assertEquals("integer", dict.put(1.0d, "float"));
    assertEquals("float", dict.put(BigInteger.ONE, "big integer"));

    assertEquals(1, dict.size());
    assertSame(Boolean.TRUE, dict.keyAt(0));
    assertEquals("big integer", dict.get(true));
    assertEquals("big integer", dict.get(1L));
    assertEquals("big integer", dict.get(1.0d));
  }

  @Test
  public void positiveAndNegativeZeroAreEquivalentKeys() {
    var dict = new PyDict<Object, Object>();

    dict.put(-0.0d, "negative zero");

    assertEquals("negative zero", dict.put(0.0d, "positive zero"));

    assertEquals(1, dict.size());
    assertEquals("positive zero", dict.get(0L));
  }

  @Test
  public void tupleKeysUsePythonEqualityAndHashing() {
    var dict = new PyDict<Object, Object>();

    var first = new PyTuple.PyTuple2(1L, "hello");
    var equivalent = new PyTuple.PyTupleN(new Object[]{1.0d, "hello"});

    dict.put(first, "value");

    assertEquals("value", dict.get(equivalent));
    assertEquals("value", dict.put(equivalent, "replaced"));

    assertEquals(1, dict.size());
    assertSame(first, dict.keyAt(0));
    assertEquals("replaced", dict.get(first));
  }

  @Test
  public void distinctNanObjectsCanBeUsedAsDifferentKeys() {
    var dict = new PyDict<Object, Object>();

    var nan1 = Double.valueOf(Double.NaN);
    var nan2 = Double.valueOf(Double.NaN);

    assertNotSame(nan1, nan2);

    dict.put(nan1, "first");
    dict.put(nan2, "second");

    assertEquals(2, dict.size());
    assertEquals("first", dict.get(nan1));
    assertEquals("second", dict.get(nan2));
  }

  @Test
  public void listAndDictKeysAreRejectedAsUnhashable() {
    var dict = new PyDict<Object, Object>();

    assertThrows(PyTypeError.class,
        () -> dict.put(new PyList<>(), "value"));

    assertThrows(PyTypeError.class,
        () -> dict.get(new PyDict<>()));
  }

  @Test
  public void nonCanonicalRuntimeValuesAreRejectedAsKeys() {
    var dict = new PyDict<Object, Object>();

    assertThrows(PyTypeError.class,
        () -> dict.put(new Object(), "value"));

    assertThrows(PyTypeError.class,
        () -> dict.containsKey(new Object()));
  }

  // Hash table collisions, resizing and compaction

  @Test
  public void hashCollisionsDoNotLoseEntries() {
    var dict = new PyDict<Object, Object>();

    // These strings have the same Java hashCode, and therefore
    // the same hash in HashingIntrinsics.
    assertEquals("Aa".hashCode(), "BB".hashCode());

    dict.put("Aa", "first");
    dict.put("BB", "second");
    dict.put("AaAa", "third");

    assertEquals("first", dict.get("Aa"));
    assertEquals("second", dict.get("BB"));
    assertEquals("third", dict.get("AaAa"));

    // Removing the first colliding key must not break lookup
    // of keys located further along the probe sequence.
    assertEquals("first", dict.remove("Aa"));
    assertEquals("second", dict.get("BB"));
    assertEquals("third", dict.get("AaAa"));

    // Reusing the deleted slot must work too.
    dict.put("Aa", "reinserted");

    assertEquals("reinserted", dict.get("Aa"));
  }

  @Test
  public void resizePreservesAllEntriesAndInsertionOrder() {
    var dict = new PyDict<Object, Object>();
    var count = 1_000;

    for (long key = 0; key < count; key++) {
      dict.put(key, key * 2);
    }

    assertEquals(count, dict.size());
    assertEquals(count, dict.entryCount());

    for (long key = 0; key < count; key++) {
      assertTrue(dict.containsKey(key));
      assertEquals(key * 2, dict.get(key));
    }

    assertEquals(0L, dict.keyAt(0));
    assertEquals(999L, dict.keyAt(999));
  }

  @Test
  public void deletedEntriesAreMarkedAsDead() {
    var dict = new PyDict<Object, Object>();

    dict.put("a", 1L);
    dict.put("b", 2L);
    dict.put("c", 3L);

    assertEquals(3, dict.entryCount());

    dict.remove("b");

    assertEquals(2, dict.size());
    assertEquals(3, dict.entryCount());

    assertTrue(dict.isLive(0));
    assertFalse(dict.isLive(1));
    assertTrue(dict.isLive(2));

    assertNull(dict.keyAt(1));
    assertNull(dict.valueAt(1));
  }

  @Test
  public void sparseDeletionCompactsInsertionOrderEntries() {
    var dict = new PyDict<Object, Object>();

    for (long i = 0; i < 12; i++) {
      dict.put(i, i * 10);
    }

    // Eight deleted entries and four live entries trigger compaction.
    for (long i = 0; i < 8; i++) {
      dict.remove(i);
    }

    assertEquals(4, dict.size());
    assertEquals(4, dict.entryCount());

    for (long i = 8; i < 12; i++) {
      assertTrue(dict.isLive((int) i - 8));
      assertEquals(i, dict.keyAt((int) i - 8));
      assertEquals(i * 10, dict.valueAt((int) i - 8));
    }
  }


  // Layout version and internal insertion-order accessors

  @Test
  public void layoutVersionChangesOnlyForKeySetChanges() {
    var dict = new PyDict<Object, Object>();

    var initialVersion = dict.layoutVersion();

    dict.put("a", 1L);
    assertEquals(initialVersion + 1, dict.layoutVersion());

    // Replacing a value does not change the key set.
    dict.put("a", 2L);
    assertEquals(initialVersion + 1, dict.layoutVersion());

    // Removing a missing key changes nothing.
    dict.remove("missing");
    assertEquals(initialVersion + 1, dict.layoutVersion());

    dict.remove("a");
    assertEquals(initialVersion + 2, dict.layoutVersion());
  }

  @Test
  public void insertionOrderAccessorsRejectInvalidIndexes() {
    var dict = new PyDict<Object, Object>();
    dict.put("a", "value");

    assertThrows(IndexOutOfBoundsException.class,
        () -> dict.isLive(-1));
    assertThrows(IndexOutOfBoundsException.class,
        () -> dict.isLive(1));

    assertThrows(IndexOutOfBoundsException.class,
        () -> dict.keyAt(-1));
    assertThrows(IndexOutOfBoundsException.class,
        () -> dict.keyAt(1));

    assertThrows(IndexOutOfBoundsException.class,
        () -> dict.valueAt(-1));
    assertThrows(IndexOutOfBoundsException.class,
        () -> dict.valueAt(1));
  }

  @Test
  public void clearRemovesAllEntriesAndResetsEntryCount() {
    var dict = new PyDict<Object, Object>();

    dict.put("a", 1L);
    dict.put("b", 2L);

    dict.clear();

    assertTrue(dict.isEmpty());
    assertEquals(0, dict.size());
    assertEquals(0, dict.entryCount());

    // Clearing an already cleared dictionary is a no-op.
    var version = dict.layoutVersion();
    dict.clear();
    assertEquals(version, dict.layoutVersion());
  }

  // Invalid Java values

  @Test
  public void dictionariesWithDifferentContentAreNotEqual() {
    var left = new PyDict<Object, Object>();
    left.put("a", 1L);

    var differentValue = new PyDict<Object, Object>();
    differentValue.put("a", 2L);

    var differentKey = new PyDict<Object, Object>();
    differentKey.put("b", 1L);

    var differentSize = new PyDict<Object, Object>();

    assertNotEquals(left, differentValue);
    assertNotEquals(left, differentKey);
    assertNotEquals(left, differentSize);
    assertNotEquals(left, Map.of("a", 1L));
  }

  @Test
  public void javaNullIsRejectedEverywhereItIsNotAllowed() {
    var dict = new PyDict<Object, Object>();
    dict.put("a", "value");

    assertThrows(NullPointerException.class, () -> dict.put(null, "value"));
    assertThrows(NullPointerException.class, () -> dict.put("key", null));

    assertThrows(NullPointerException.class, () -> dict.get(null));
    assertThrows(NullPointerException.class, () -> dict.containsKey(null));
    assertThrows(NullPointerException.class, () -> dict.remove(null));
  }
}