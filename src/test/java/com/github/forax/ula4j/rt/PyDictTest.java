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
    assertSame(firstKey, dict.keySet().iterator().next());
    assertEquals("second", dict.get(firstKey));
  }

  @Test
  public void noneCanBeUsedAsKeyAndValue() {
    var dict = new PyDict<Object, Object>();

    dict.put(PyNone.INSTANCE, PyNone.INSTANCE);

    assertEquals(1, dict.size());
    assertTrue(dict.containsKey(PyNone.INSTANCE));
    assertTrue(dict.containsValue(PyNone.INSTANCE));
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

  @Test
  public void removingAndReinsertingAKeyMovesItToTheEnd() {
    var dict = new PyDict<Object, Object>();

    dict.put("a", 1L);
    dict.put("b", 2L);
    dict.put("c", 3L);

    assertEquals(2L, dict.remove("b"));

    assertNull(dict.put("b", 22L));

    assertEquals(List.of("a", "c", "b"),
        new ArrayList<>(dict.keySet()));
    assertEquals(List.of(1L, 3L, 22L),
        new ArrayList<>(dict.values()));
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
    assertEquals(List.of("BB", "AaAa", "Aa"),
        new ArrayList<>(dict.keySet()));
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

    assertEquals(List.of(8L, 9L, 10L, 11L),
        new ArrayList<>(dict.keySet()));

    for (long i = 8; i < 12; i++) {
      assertTrue(dict.isLive((int) i - 8));
      assertEquals(i, dict.keyAt((int) i - 8));
      assertEquals(i * 10, dict.valueAt((int) i - 8));
    }
  }

  // Java Map operations

  @Test
  public void getOrDefaultReturnsDefaultOnlyForMissingKeys() {
    var dict = new PyDict<Object, Object>();

    dict.put("a", "value");

    assertEquals("value", dict.getOrDefault("a", "default"));
    assertEquals("default", dict.getOrDefault("missing", "default"));
    assertNull(dict.getOrDefault("missing", null));
  }

  @Test
  public void containsValueUsesPythonEquality() {
    var dict = new PyDict<Object, Object>();

    dict.put("a", Boolean.TRUE);

    assertTrue(dict.containsValue(1L));
    assertTrue(dict.containsValue(1.0d));
    assertFalse(dict.containsValue(2L));
  }

  @Test
  public void putAllPreservesSourceIterationOrder() {
    var source = new LinkedHashMap<Object, Object>();
    source.put("b", 2L);
    source.put("a", 1L);
    source.put("c", 3L);

    var dict = new PyDict<Object, Object>();
    dict.putAll(source);

    assertEquals(List.of("b", "a", "c"),
        new ArrayList<>(dict.keySet()));

    // Self-copy only replaces existing values; it must not
    // invalidate the iterator used internally by putAll.
    dict.putAll(dict);

    assertEquals(3, dict.size());
  }

  @Test
  public void viewsExposeInsertionOrderAndCurrentSize() {
    var dict = new PyDict<Object, Object>();

    dict.put("a", 1L);
    dict.put("b", 2L);
    dict.put("c", 3L);

    assertEquals(3, dict.keySet().size());
    assertEquals(3, dict.values().size());
    assertEquals(3, dict.entrySet().size());

    assertEquals(List.of("a", "b", "c"),
        new ArrayList<>(dict.keySet()));

    assertEquals(List.of(1L, 2L, 3L),
        new ArrayList<>(dict.values()));

    assertEquals(List.of(
        Map.entry("a", 1L),
        Map.entry("b", 2L),
        Map.entry("c", 3L)),
        new ArrayList<>(dict.entrySet()));

    dict.remove("b");

    assertEquals(2, dict.keySet().size());
    assertEquals(2, dict.values().size());
    assertEquals(2, dict.entrySet().size());

    assertThrows(UnsupportedOperationException.class,
        () -> dict.entrySet().iterator().next().setValue(42L));
  }

  @Test
  public void iteratorsThrowWhenTheKeySetChanges() {
    var dict = new PyDict<Object, Object>();
    dict.put("a", 1L);

    assertIteratorInvalidated(dict.keySet(), dict, "b");
    assertIteratorInvalidated(dict.values(), dict, "c");
    assertIteratorInvalidated(dict.entrySet(), dict, "d");
  }

  private static void assertIteratorInvalidated(
      Iterable<?> view, PyDict<Object, Object> dict, Object newKey) {
    var iterator = view.iterator();

    assertTrue(iterator.hasNext());

    dict.put(newKey, "value");

    assertThrows(ConcurrentModificationException.class, iterator::next);
  }

  @Test
  public void replacingAValueDoesNotInvalidateIterators() {
    var dict = new PyDict<Object, Object>();
    dict.put("a", "old");

    var keyIterator = dict.keySet().iterator();
    var valueIterator = dict.values().iterator();
    var entryIterator = dict.entrySet().iterator();

    dict.put("a", "new");

    assertEquals("a", keyIterator.next());
    assertEquals("new", valueIterator.next());
    assertEquals(Map.entry("a", "new"), entryIterator.next());
  }

  @Test
  public void iteratorsThrowWhenExhausted() {
    var dict = new PyDict<Object, Object>();

    assertThrows(NoSuchElementException.class,
        () -> dict.keySet().iterator().next());

    assertThrows(NoSuchElementException.class,
        () -> dict.values().iterator().next());

    assertThrows(NoSuchElementException.class,
        () -> dict.entrySet().iterator().next());
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

    assertTrue(dict.keySet().isEmpty());
    assertTrue(dict.values().isEmpty());
    assertTrue(dict.entrySet().isEmpty());

    // Clearing an already cleared dictionary is a no-op.
    var version = dict.layoutVersion();
    dict.clear();
    assertEquals(version, dict.layoutVersion());
  }

  // Equality and invalid Java values

  @Test
  public void dictionariesCompareByContentRegardlessOfInsertionOrder() {
    var left = new PyDict<Object, Object>();
    left.put("flag", Boolean.TRUE);
    left.put("count", 2L);

    var right = new PyDict<Object, Object>();
    right.put("count", 2.0d);
    right.put("flag", 1L);

    assertEquals(left, right);
    assertEquals(right, left);
  }

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
  public void dictionaryIsUnhashable() {
    var dict = new PyDict<Object, Object>();

    assertThrows(PyTypeError.class, dict::hashCode);
  }

  @Test
  public void javaNullIsRejectedEverywhereItIsNotAllowed() {
    var dict = new PyDict<Object, Object>();
    dict.put("a", "value");

    assertThrows(NullPointerException.class, () -> dict.put(null, "value"));
    assertThrows(NullPointerException.class, () -> dict.put("key", null));

    assertThrows(NullPointerException.class, () -> dict.get(null));
    assertThrows(NullPointerException.class, () -> dict.getOrDefault(null, "default"));
    assertThrows(NullPointerException.class, () -> dict.containsKey(null));
    assertThrows(NullPointerException.class, () -> dict.remove(null));

    assertThrows(NullPointerException.class, () -> dict.containsValue(null));
    assertThrows(NullPointerException.class, () -> dict.putAll(null));

    assertThrows(NullPointerException.class, () -> dict.equals(null));
  }

  // -- Collection views: Python equality semantics

  @Test
  public void keySetContainsUsesPythonEquality() {
    var dict = new PyDict<Object, Object>();
    dict.put(1L, "one");

    assertTrue(dict.keySet().contains(1.0d));
    assertTrue(dict.keySet().contains(true));
    assertFalse(dict.keySet().contains(2L));
  }

  @Test
  public void keySetContainsUsesIdentityShortcutForNan() {
    var nan1 = Double.valueOf(Double.NaN);
    var nan2 = Double.valueOf(Double.NaN);

    assertNotSame(nan1, nan2);

    var dict = new PyDict<Object, Object>();
    dict.put(nan1, "value");

    assertTrue(dict.keySet().contains(nan1));
    assertFalse(dict.keySet().contains(nan2));
  }

  @Test
  public void valuesContainsUsesPythonEquality() {
    var dict = new PyDict<Object, Object>();
    dict.put("one", 1L);

    assertTrue(dict.containsValue(1.0d));
    assertTrue(dict.values().contains(1.0d));
    assertTrue(dict.values().contains(true));
    assertFalse(dict.values().contains(2L));
  }

  @Test
  public void valuesContainsUsesIdentityShortcutForNan() {
    var nan1 = Double.valueOf(Double.NaN);
    var nan2 = Double.valueOf(Double.NaN);

    assertNotSame(nan1, nan2);

    var dict = new PyDict<Object, Object>();
    dict.put("nan", nan1);

    assertTrue(dict.values().contains(nan1));
    assertFalse(dict.values().contains(nan2));
  }

  @Test
  public void entrySetContainsUsesPythonEquality() {
    var dict = new PyDict<Object, Object>();
    dict.put(1L, 1L);

    assertTrue(dict.entrySet().contains(Map.entry(1.0d, 1.0d)));
    assertTrue(dict.entrySet().contains(Map.entry(true, true)));

    assertFalse(dict.entrySet().contains(Map.entry(1L, 2L)));
    assertFalse(dict.entrySet().contains(Map.entry(2L, 1L)));
  }

  @Test
  public void entrySetContainsUsesIdentityShortcutForNan() {
    var nanKey1 = Double.valueOf(Double.NaN);
    var nanKey2 = Double.valueOf(Double.NaN);
    var nanValue1 = Double.valueOf(Double.NaN);
    var nanValue2 = Double.valueOf(Double.NaN);

    assertNotSame(nanKey1, nanKey2);
    assertNotSame(nanValue1, nanValue2);

    var dict = new PyDict<Object, Object>();
    dict.put(nanKey1, nanValue1);

    assertTrue(dict.entrySet().contains(Map.entry(nanKey1, nanValue1)));

    assertFalse(dict.entrySet().contains(Map.entry(nanKey2, nanValue1)));
    assertFalse(dict.entrySet().contains(Map.entry(nanKey1, nanValue2)));
  }

// -- Map default methods

  @Test
  public void conditionalRemoveUsesPythonEquality() {
    var dict = new PyDict<Object, Object>();
    dict.put("one", 1L);

    assertTrue(dict.remove("one", 1.0d));

    assertFalse(dict.containsKey("one"));
  }

  @Test
  public void conditionalRemoveDoesNotRemoveWhenValueDiffers() {
    var dict = new PyDict<Object, Object>();
    dict.put("one", 1L);

    assertFalse(dict.remove("one", 2L));

    assertEquals(1L, dict.get("one"));
  }

  @Test
  public void conditionalReplaceUsesPythonEquality() {
    var dict = new PyDict<Object, Object>();
    dict.put("one", 1L);

    assertTrue(dict.replace("one", 1.0d, 2L));

    assertEquals(2L, dict.get("one"));
  }

  @Test
  public void conditionalReplaceDoesNotReplaceWhenValueDiffers() {
    var dict = new PyDict<Object, Object>();
    dict.put("one", 1L);

    assertFalse(dict.replace("one", 2L, 3L));

    assertEquals(1L, dict.get("one"));
  }

// -- NaN corner cases for Map default methods

  @Test
  public void conditionalRemoveUsesIdentityShortcutForNan() {
    var nan1 = Double.valueOf(Double.NaN);
    var nan2 = Double.valueOf(Double.NaN);

    assertNotSame(nan1, nan2);

    var dict = new PyDict<Object, Object>();
    dict.put("nan", nan1);

    assertFalse(dict.remove("nan", nan2));
    assertSame(nan1, dict.get("nan"));

    assertTrue(dict.remove("nan", nan1));
    assertFalse(dict.containsKey("nan"));
  }

  @Test
  public void conditionalReplaceUsesIdentityShortcutForNan() {
    var nan1 = Double.valueOf(Double.NaN);
    var nan2 = Double.valueOf(Double.NaN);

    assertNotSame(nan1, nan2);

    var dict = new PyDict<Object, Object>();
    dict.put("nan", nan1);

    assertFalse(dict.replace("nan", nan2, "replaced"));
    assertSame(nan1, dict.get("nan"));

    assertTrue(dict.replace("nan", nan1, "replaced"));
    assertEquals("replaced", dict.get("nan"));
  }
}