package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.error.PyIndexError;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;

/// Python-list operations.
///
/// These methods are the semantic boundary between PyList's storage operations and the rest of
/// the runtime. Index arguments here are already converted to a Java long; converting arbitrary
/// Python objects (including bool and BigInteger) belongs in the generic item-operation layer.
public final class ListIntrinsics {
  private ListIntrinsics() {
    throw new AssertionError("no instances");
  }

  public static <E> PyList<E> newList() {
    return new PyList<>();
  }

  /// Python len(list).
  @Intrinsic
  public static long $size$(PyList<?> list) {
    return list.size();
  }

  /// Python `list[index]` for an index already represented as a long.
  @Intrinsic
  public static <E> E $get$(PyList<E> list, long index) {
    return list.get(checkedIndex(index, list.size()));
  }

  /// Python `list[index] = value`.
  /// Returns Python None for use by the generic assignment intrinsic.
  @Intrinsic
  public static <E> Object $set$(PyList<E> list, long index, E value) {
    list.set(checkedIndex(index, list.size()), value);
    return PyNone.INSTANCE;
  }

  /// Python `del list[index]`.
  /// Returns Python None for use by the generic deletion intrinsic.
  @Intrinsic
  public static Object $delete$(PyList<?> list, long index) {
    list.remove(checkedIndex(index, list.size()));
    return PyNone.INSTANCE;
  }

  /// Python list + list. The result is always a distinct mutable list.
  @Intrinsic
  public static <E> PyList<E> $concat$(PyList<? extends E> left, PyList<? extends E> right) {
    var totalSize = (long) left.size() + right.size();
    if (totalSize > Integer.MAX_VALUE) {
      throw tooLarge();
    }
    var result = new PyList<E>((int) totalSize);
    result.appendAll(left);
    result.appendAll(right);
    return result;
  }

  /// Python list * count. The result is always a distinct mutable list, including count == 1.
  @Intrinsic
  public static <E> PyList<E> $repeat$(PyList<? extends E> list, long count) {
    if (count <= 0 || list.isEmpty()) {
      return new PyList<>();
    }
    if (count > Integer.MAX_VALUE / (long) list.size()) {
      throw tooLarge();
    }
    var totalSize = (int) (list.size() * count);
    var result = new PyList<E>(totalSize);
    for (var repetition = 0; repetition < (int) count; repetition++) {
      result.appendAll(list);
    }
    return result;
  }

  /// Python list += iterable. The receiver is mutated and returned.
  ///
  /// PyList is handled directly so self-extension takes a finite snapshot. Other currently
  /// supported iterables use the shared iterator protocol (tuple and str today).
  @Intrinsic
  public static PyList<Object> $iadd$(PyList<Object> list, Object iterable) {
    switch (iterable) {
      case PyList<?> other -> list.appendAll(other);
      default -> {
        var iterator = IteratorIntrinsics.$iterator$(iterable);
        Object item;
        while ((item = IteratorIntrinsics.$next$(iterator)) != null) {
          list.append(item);
        }
      }
    }
    return list;
  }

  /// Python list *= count. The receiver is mutated and returned.
  @Intrinsic
  public static <E> PyList<E> $imul$(PyList<E> list, long count) {
    list.repeatInPlace(count);
    return list;
  }

  @Intrinsic
  public static boolean $in$(PyList<?> list, Object item) {
    var index = 0;
    // Do not snapshot size: the list may grow while membership is being evaluated.
    while (index < list.size()) {
      if (ComparisonIntrinsics.$sameOrEq$(list.get(index), item)) {
        return true;
      }
      index++;
    }
    return false;
  }


  private static int checkedIndex(long index, int size) {
    // Adding size to a negative long cannot overflow: size is non-negative and at most int-sized.
    var normalized = index < 0 ? index + size : index;
    if (normalized < 0 || normalized >= size) {
      throw new PyIndexError("list index out of range");
    }
    return (int) normalized;
  }

  private static UnimplementedFeatureError tooLarge() {
    return new UnimplementedFeatureError("list larger than the maximum Java array size");
  }
}
