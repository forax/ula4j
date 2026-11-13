package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;
import com.github.forax.ula4j.rt.intrinsic.ComparisonIntrinsics;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/// Mutable Python-list storage.
///
/// This class deliberately implement java.util.List to offer better interoperability.
/// Python-visible behavior belongs in ListIntrinsics and the other runtime intrinsics.
/// Elements are canonical runtime values: Java null is never stored (use PyNone.INSTANCE).
public final class PyList<E> extends AbstractList<E> {
  private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
  private static final Object[] EMPTY_ARRAY = new Object[0];

  private E[] elements;
  private int size;

  /// Creates an empty list.
  @SuppressWarnings("unchecked")
  public PyList() {
    this.elements = (E[]) EMPTY_ARRAY;
    super();
  }

  /// Creates an empty list with the given initial capacity.
  @SuppressWarnings("unchecked")
  public PyList(int initialCapacity) {
    if (initialCapacity < 0) {
      throw new IllegalArgumentException("negative initial capacity: " + initialCapacity);
    }
    if (initialCapacity > MAX_ARRAY_SIZE) {
      throw tooLarge();
    }
    this.elements = (E[]) new Object[initialCapacity];
    super();
  }

  /// Creates a list by copying the supplied elements.
  @SafeVarargs
  @SuppressWarnings("unchecked")
  public PyList(E... items) {
    Objects.requireNonNull(items);
    if (items.length > MAX_ARRAY_SIZE) {
      throw tooLarge();
    }
    for (var item : items) {
      Objects.requireNonNull(item);
    }
    this.elements = (E[]) Arrays.copyOf(items, items.length, Object[].class);
    this.size = items.length;
    super();
  }

  /// Current number of elements.
  public int size() {
    return size;
  }

  /// Whether this list is empty.
  public boolean isEmpty() {
    return size == 0;
  }

  /// Raw zero-based Java access. Python negative-index handling is implemented by ListIntrinsics.
  public E get(int index) {
    Objects.checkIndex(index, size);
    return elements[index];
  }

  /// Raw zero-based Java update. Python negative-index handling is implemented by ListIntrinsics.
  public E set(int index, E value) {
    Objects.checkIndex(index, size);
    Objects.requireNonNull(value);
    var oldValue = elements[index];
    elements[index] = value;
    return oldValue;
  }

  /// Appends one canonical runtime value.
  public void append(E value) {
    Objects.requireNonNull(value);
    ensureCapacity(size + 1);
    elements[size++] = value;
  }

  /// Appends a snapshot of another list's current contents. Safe when {@code other == this}.
  public void appendAll(PyList<? extends E> other) {
    Objects.requireNonNull(other);
    var count = other.size;
    if (count == 0) {
      return;
    }
    var newCapacity = (long) size + count;
    if (newCapacity > MAX_ARRAY_SIZE) {
      throw tooLarge();
    }
    var newSize = (int) newCapacity;
    ensureCapacity(newSize);
    // System.arraycopy has defined overlap behavior, so this also handles self-extension.
    System.arraycopy(other.elements, 0, elements, size, count);
    size = newSize;
  }

  /// Inserts at a raw Java index in [0, size]. Python's clamping rules belong in the method intrinsic.
  public void insert(int index, E value) {
    if (index < 0 || index > size) {
      throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
    }
    Objects.requireNonNull(value, "PyList elements must not be Java null");
    ensureCapacity(size + 1);
    System.arraycopy(elements, index, elements, index + 1, size - index);
    elements[index] = value;
    size++;
  }

  /// Removes and returns an element at a raw zero-based Java index.
  public E remove(int index) {
    Objects.checkIndex(index, size);
    var removed = elements[index];
    var moved = size - index - 1;
    if (moved > 0) {
      System.arraycopy(elements, index + 1, elements, index, moved);
    }
    elements[--size] = null;
    return removed;
  }

  /// Releases the backing array.
  @SuppressWarnings("unchecked")
  public void clear() {
    elements = (E[]) EMPTY_ARRAY;
    size = 0;
  }

  /// Returns a defensive copy of the live elements.
  public Object[] toArray() {
    return Arrays.copyOf(elements, size);
  }

  /// Repeats this list in place.
  public void repeatInPlace(long count) {
    if (count <= 0) {
      clear();
      return;
    }
    if (count == 1 || size == 0) {
      return;
    }
    if (count > MAX_ARRAY_SIZE / (long) size) {
      throw tooLarge();
    }
    var originalSize = size;
    var newSize = (int) (originalSize * count);
    ensureCapacity(newSize);
    for (var repetition = 1; repetition < (int) count; repetition++) {
      System.arraycopy(elements, 0, elements, repetition * originalSize, originalSize);
    }
    size = newSize;
  }


  // -- Java API

  @Override
  public void add(int index, E element) {
    insert(index, element);
  }

  @Override
  public boolean contains(Object o) {
    Objects.requireNonNull(o);
    return indexOf(o) >= 0;
  }

  @Override
  public int indexOf(Object o) {
    Objects.requireNonNull(o);
    for (var i = 0; i < size; i++) {
      var element = elements[i];
      if (ComparisonIntrinsics.$sameOrEq$(element, o)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public int lastIndexOf(Object o) {
    Objects.requireNonNull(o);
    for (var i = size - 1; i >= 0; i--) {
      var element = elements[i];
      if (ComparisonIntrinsics.$sameOrEq$(element, o)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public boolean remove(Object o) {
    var index = indexOf(o);
    if (index < 0) {
      return false;
    }
    remove(index);
    return true;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    var dict = new PyDict<>();
    for (var element : c) {
      dict.put(element, true);
    }
    var toInset = 0;
    for(var i = 0; i < size; i++) {
      var element = elements[i];
      if (!dict.containsKey(element)) {
        elements[toInset++] = element;
      }
    }
    Arrays.fill(elements, toInset, size, null);
    var result = toInset != size;
    size = toInset;
    return result;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    var dict = new PyDict<>();
    for (var element : c) {
      dict.put(element, true);
    }
    var toInset = 0;
    for(var i = 0; i < size; i++) {
      var element = elements[i];
      if (dict.containsKey(element)) {
        elements[toInset++] = element;
      }
    }
    Arrays.fill(elements, toInset, size, null);
    var result = toInset != size;
    size = toInset;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    Objects.requireNonNull(o);
    return ComparisonIntrinsics.$eq$(this, o);
  }

  @Override
  public int hashCode() {
    throw unhashable();
  }

  private static PyTypeError unhashable() {
    return new PyTypeError("unhashable type: 'list'");
  }


  // --

  private void ensureCapacity(int minimumCapacity) {
    if (minimumCapacity <= elements.length) {
      return;
    }
    if (minimumCapacity > MAX_ARRAY_SIZE) {
      throw tooLarge();
    }

    var oldCapacity = elements.length;
    var grownCapacity = oldCapacity + (oldCapacity >> 1) + 1L;
    var newCapacity = (int) Math.min(MAX_ARRAY_SIZE, Math.max(minimumCapacity, grownCapacity));
    elements = Arrays.copyOf(elements, newCapacity);
  }

  private static UnimplementedFeatureError tooLarge() {
    // The runtime does not yet translate JVM allocation limits into Python MemoryError.
    return new UnimplementedFeatureError("list larger than the maximum Java array size");
  }
}
