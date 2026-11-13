package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class PyListTest {

  // -- Construction and basic properties

  @Test
  public void newListsAreEmpty() {
    var empty = new PyList<Object>();
    var zeroCapacity = new PyList<Object>(0);
    var initialCapacity = new PyList<Object>(8);

    for (var list : List.of(empty, zeroCapacity, initialCapacity)) {
      assertEquals(0, list.size());
      assertTrue(list.isEmpty());
      assertArrayEquals(new Object[0], list.toArray());
    }
  }

  @Test
  public void negativeInitialCapacityIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new PyList<Object>(-1));
  }

  @Test
  public void excessiveInitialCapacityIsRejected() {
    assertThrows(UnimplementedFeatureError.class,
        () -> new PyList<Object>(Integer.MAX_VALUE));
  }

  @Test
  public void varargsConstructorCopiesItsInput() {
    var input = new Object[]{"a", "b", "c"};
    var list = new PyList<>(input);

    input[0] = "changed";
    input[2] = "changed";

    assertArrayEquals(new Object[]{"a", "b", "c"}, list.toArray());
  }

  @Test
  public void varargsConstructorAcceptsPyNone() {
    var list = new PyList<Object>(PyNone.INSTANCE, "value");

    assertSame(PyNone.INSTANCE, list.get(0));
    assertEquals("value", list.get(1));
  }

  @Test
  public void varargsConstructorRejectsNullArrayAndNullElements() {
    assertThrows(NullPointerException.class,
        () -> new PyList<Object>((Object[]) null));

    assertThrows(NullPointerException.class,
        () -> new PyList<Object>(new Object[]{1L, null}));
  }

  // -- Generic type safety

  @Test
  public void varargsConstructorSupportsCovariantArrays() {
    // A Long[] is a valid argument for a Number[] varargs parameter.
    // The resulting PyList<Number> must nevertheless accept other Number implementations.
    Long[] input = {1L};
    var list = new PyList<Number>(input);

    list.append(2.5d);

    assertArrayEquals(new Object[]{1L, 2.5d}, list.toArray());
  }

  // -- Appending and array conversion

  @Test
  public void appendAddsElementsAndGrowsTheList() {
    var list = new PyList<Object>(0);

    for (long i = 0; i < 1_000; i++) {
      list.append(i);
    }

    assertEquals(1_000, list.size());
    assertFalse(list.isEmpty());

    for (long i = 0; i < 1_000; i++) {
      assertEquals(i, list.get((int) i));
    }
  }

  @Test
  public void appendAllCopiesAnotherListsContents() {
    var list = new PyList<>(1L);
    var other = new PyList<>(2L, 3L);

    list.appendAll(other);

    assertArrayEquals(new Object[]{1L, 2L, 3L}, list.toArray());
    assertEquals(3, list.size());
    assertArrayEquals(new Object[]{2L, 3L}, other.toArray());
  }

  @Test
  public void appendAllAnEmptyListDoesNothing() {
    var list = new PyList<>(1L, 2L);

    list.appendAll(new PyList<>());

    assertArrayEquals(new Object[]{1L, 2L}, list.toArray());
  }

  @Test
  public void appendAllSupportsAppendingTheListToItself() {
    var list = new PyList<>("a", "b");

    list.appendAll(list);

    assertArrayEquals(
        new Object[]{"a", "b", "a", "b"},
        list.toArray());
  }

  @Test
  public void toArrayReturnsADefensiveCopyOfLiveElements() {
    var list = new PyList<>("a", "b", "c");
    list.remove(1);

    var array = list.toArray();

    assertArrayEquals(new Object[]{"a", "c"}, array);

    array[0] = "changed";

    assertEquals("a", list.get(0));
    assertEquals("c", list.get(1));
  }

  @Test
  public void toArraySupportsTypedArrays() {
    var list = new PyList<>("a", "b");

    assertArrayEquals(new String[]{"a", "b"}, list.toArray(new String[0]));
  }

  // -- Insertion

  @Test
  public void insertAtBeginningMiddleAndEnd() {
    var list = new PyList<>("b", "d");

    list.insert(0, "a");
    list.insert(2, "c");
    list.insert(list.size(), "e");

    assertArrayEquals(
        new Object[]{"a", "b", "c", "d", "e"},
        list.toArray());
  }

  @Test
  public void javaAddMethodsWork() {
    var list = new PyList<>("a", "c");

    list.add(1, "b");
    list.add("d");

    assertArrayEquals(
        new Object[]{"a", "b", "c", "d"},
        list.toArray());
  }

  @Test
  public void insertRejectsInvalidIndexes() {
    var list = new PyList<>("a", "b");

    assertThrows(IndexOutOfBoundsException.class,
        () -> list.insert(-1, "x"));

    assertThrows(IndexOutOfBoundsException.class,
        () -> list.insert(3, "x"));
  }

  // -- Reading, updating and removing

  @Test
  public void getAndSetAccessElements() {
    var first = new String("first");
    var second = new String("second");
    var list = new PyList<>(first, second);

    assertSame(first, list.get(0));
    assertSame(second, list.get(1));

    var previous = list.set(1, "updated");

    assertSame(second, previous);
    assertEquals("updated", list.get(1));
    assertEquals(2, list.size());
  }

  @Test
  public void getAndSetRejectInvalidIndexes() {
    var list = new PyList<>("a", "b");

    assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> list.get(2));

    assertThrows(IndexOutOfBoundsException.class, () -> list.set(-1, "x"));
    assertThrows(IndexOutOfBoundsException.class, () -> list.set(2, "x"));
  }

  @Test
  public void removeByIndexShiftsRemainingElements() {
    var list = new PyList<>("a", "b", "c", "d");

    assertEquals("a", list.remove(0));
    assertArrayEquals(new Object[]{"b", "c", "d"}, list.toArray());

    assertEquals("c", list.remove(1));
    assertArrayEquals(new Object[]{"b", "d"}, list.toArray());

    assertEquals("d", list.remove(1));
    assertArrayEquals(new Object[]{"b"}, list.toArray());

    assertEquals("b", list.remove(0));
    assertTrue(list.isEmpty());
  }

  @Test
  public void removeByIndexRejectsInvalidIndexes() {
    var list = new PyList<>("a", "b");

    assertThrows(IndexOutOfBoundsException.class, () -> list.remove(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> list.remove(2));
  }

  @Test
  public void removeByValueRemovesOnlyTheFirstMatchingElement() {
    var list = new PyList<Object>(1L, true, 2L);

    assertTrue(list.remove((Object) 1.0d));

    assertIterableEquals(List.of(true, 2L), list);
    assertFalse(list.remove((Object) 42L));
  }

  @Test
  public void clearEmptiesTheListAndAllowsReuse() {
    var list = new PyList<>("a", "b", "c");

    list.clear();

    assertEquals(0, list.size());
    assertTrue(list.isEmpty());
    assertArrayEquals(new Object[0], list.toArray());

    // Clearing an already empty list is also valid.
    list.clear();

    list.append("reused");

    assertEquals(1, list.size());
    assertEquals("reused", list.get(0));
  }

  // -- Repetition

  @Test
  public void repeatInPlaceDuplicatesTheElements() {
    var list = new PyList<>("a", "b");

    list.repeatInPlace(3);

    assertArrayEquals(
        new Object[]{"a", "b", "a", "b", "a", "b"},
        list.toArray());
  }

  @Test
  public void repeatInPlaceWithOneLeavesTheListUnchanged() {
    var list = new PyList<>("a", "b");
    var first = list.get(0);

    list.repeatInPlace(1);

    assertArrayEquals(new Object[]{"a", "b"}, list.toArray());
    assertSame(first, list.get(0));
  }

  @Test
  public void repeatInPlaceWithNonPositiveCountClearsTheList() {
    var zero = new PyList<>("a", "b");
    zero.repeatInPlace(0);
    assertTrue(zero.isEmpty());

    var negative = new PyList<>("a", "b");
    negative.repeatInPlace(-1);
    assertTrue(negative.isEmpty());

    var veryNegative = new PyList<>("a");
    veryNegative.repeatInPlace(Long.MIN_VALUE);
    assertTrue(veryNegative.isEmpty());
  }

  @Test
  public void repeatingAnEmptyListWithLargeCountIsSafe() {
    var list = new PyList<Object>();

    list.repeatInPlace(Long.MAX_VALUE);

    assertTrue(list.isEmpty());
  }

  @Test
  public void excessiveRepetitionIsRejectedWithoutChangingTheList() {
    var list = new PyList<>("a", "b");

    assertThrows(UnimplementedFeatureError.class,
        () -> list.repeatInPlace(Long.MAX_VALUE));

    assertArrayEquals(new Object[]{"a", "b"}, list.toArray());
  }

  // -- Python equality semantics

  @Test
  public void containsAndIndexesUsePythonEquality() {
    var list = new PyList<Object>(1L, true, 2.0d);

    // bool, int and float values can compare equal in Python.
    assertTrue(list.contains(1.0d));
    assertEquals(0, list.indexOf(true));
    assertEquals(1, list.lastIndexOf(1L));

    assertFalse(list.contains(42L));
    assertEquals(-1, list.indexOf(42L));
    assertEquals(-1, list.lastIndexOf(42L));
  }

  @Test
  public void containsAndIndexesUseIdentityShortcutForNan() {
    var nan1 = Double.valueOf(Double.NaN);
    var nan2 = Double.valueOf(Double.NaN);

    assertNotSame(nan1, nan2);

    var list = new PyList<Object>(nan1);

    assertTrue(list.contains(nan1));
    assertFalse(list.contains(nan2));

    assertEquals(0, list.indexOf(nan1));
    assertEquals(-1, list.indexOf(nan2));
  }

  @Test
  public void listsCompareByContentUsingPythonEquality() {
    var left = new PyList<Object>(1L, true);
    var right = new PyList<Object>(1.0d, 1L);
    var differentValue = new PyList<Object>(1L, 2L);
    var differentSize = new PyList<Object>(1L);

    assertEquals(left, right);
    assertEquals(right, left);

    assertNotEquals(left, differentValue);
    assertNotEquals(left, differentSize);

    // A PyList is not equal to an ordinary Java List in this runtime.
    assertNotEquals(left, List.of(1L, true));
  }

  @Test
  public void nestedListsUsePythonEquality() {
    var left = new PyList<Object>(
        new PyList<Object>(1L, 2L),
        "value");

    var right = new PyList<Object>(
        new PyList<Object>(1.0d, 2.0d),
        "value");

    assertEquals(left, right);
  }

  @Test
  public void listIsUnhashable() {
    var list = new PyList<>(1L, 2L);

    var exception = assertThrows(PyTypeError.class, list::hashCode);

    assertEquals("unhashable type: 'list'", exception.getMessage());
  }

  // -- Java null is not a Python value

  @Test
  public void javaNullIsRejectedByOperationsThatAcceptValues() {
    var list = new PyList<Object>(PyNone.INSTANCE);

    assertThrows(NullPointerException.class, () -> list.append(null));
    assertThrows(NullPointerException.class, () -> list.insert(0, null));
    assertThrows(NullPointerException.class, () -> list.set(0, null));
    assertThrows(NullPointerException.class, () -> list.appendAll(null));
  }

  @Test
  public void javaNullIsRejectedByLookupAndEqualityOperations() {
    var list = new PyList<Object>(1L);

    assertThrows(NullPointerException.class, () -> list.contains(null));
    assertThrows(NullPointerException.class, () -> list.indexOf(null));
    assertThrows(NullPointerException.class, () -> list.lastIndexOf(null));
    assertThrows(NullPointerException.class, () -> list.remove((Object) null));
    assertThrows(NullPointerException.class, () -> list.equals(null));
  }

  // -- Python equality semantics

  @Test
  public void containsUsesPythonEquality() {
    var list = new PyList<Object>(1L, 2L, 3L);

    assertTrue(list.contains(1.0d));
    assertTrue(list.contains(true));
    assertTrue(list.contains(BigInteger.ONE));
    assertFalse(list.contains(4L));
  }

  @Test
  public void indexOfAndLastIndexOfUsePythonEquality() {
    var list = new PyList<Object>(1L, 2L, 1.0d, true);

    assertEquals(0, list.indexOf(BigInteger.ONE));
    assertEquals(3, list.lastIndexOf(1L));
    assertEquals(-1, list.indexOf(42L));
  }

  @Test
  public void containsUsesIdentityShortcutForNan() {
    var nan1 = Double.valueOf(Double.NaN);
    var nan2 = Double.valueOf(Double.NaN);

    assertNotSame(nan1, nan2);

    var list = new PyList<Object>(nan1);

    assertTrue(list.contains(nan1));
    assertFalse(list.contains(nan2));

    assertEquals(0, list.indexOf(nan1));
    assertEquals(-1, list.indexOf(nan2));
  }

  @Test
  public void removeUsesPythonEquality() {
    var list = new PyList<Object>(1L, 2L, 1.0d, true);

    assertTrue(list.remove((Object) BigInteger.ONE));

    // Only the first matching element should be removed.
    assertArrayEquals(
        new Object[]{2L, 1.0d, true},
        list.toArray()
    );

    assertFalse(list.remove((Object) 42L));
  }

// -- Bulk operations

  @Test
  public void containsAllUsesPythonEquality() {
    var list = new PyList<Object>(1L, 2L, 3L);

    assertTrue(list.containsAll(List.of(1.0d, 2.0d)));
    assertFalse(list.containsAll(List.of(1.0d, 42L)));
  }

  @Test
  public void removeAllUsesPythonEquality() {
    var list = new PyList<Object>(1L, 2L, 3L);

    assertTrue(list.removeAll(List.of(1.0d, 3.0d)));

    assertArrayEquals(new Object[]{2L}, list.toArray());
  }

  @Test
  public void retainAllUsesPythonEquality() {
    var list = new PyList<Object>(1L, 2L, 3L);

    assertTrue(list.retainAll(List.of(1.0d, 3.0d)));

    assertArrayEquals(new Object[]{1L, 3L}, list.toArray());
  }

  @Test
  public void removeAllItselfEmptiesList() {
    var list = new PyList<Object>(1L, 2L, 3L);
    var list2 = new PyList<>(list.toArray());

    assertTrue(list.removeAll(list2));

    assertTrue(list.isEmpty());
  }

  @Test
  public void retainAllItselfDoesNotChangeList() {
    var list = new PyList<Object>(1L, 2L, 3L);

    assertFalse(list.retainAll(list));

    assertArrayEquals(new Object[]{1L, 2L, 3L}, list.toArray());
  }
}