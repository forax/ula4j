package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyDict;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.error.PyKeyError;

/// Python dictionary operations. Storage mechanics live in [PyDict].
public final class DictIntrinsics {
  private DictIntrinsics() {
    throw new AssertionError("no instances");
  }

  /** Python {@code len(dict)}. */
  @Intrinsic
  public static long $size$(PyDict<?,?> dict) {
    return dict.size();
  }

  /** Python {@code dict[key]}.*/
  @Intrinsic
  public static <V> V $get$(PyDict<?,? extends V> dict, Object key) {
    var value = dict.get(key);
    if (value == null) {
      throw missingKey(key);
    }
    return value;
  }

  /** Python {@code dict[key] = value}; returns Python None. */
  @Intrinsic
  public static <V> Object $set$(PyDict<Object,? super V> dict, Object key, V value) {
    dict.put(key, value);
    return PyNone.INSTANCE;
  }

  /** Python {@code del dict[key]}; returns Python None. */
  @Intrinsic
  public static Object $delete$(PyDict<Object,?> dict, Object key) {
    var value = dict.remove(key);
    if (value == null) {
      throw missingKey(key);
    }
    return PyNone.INSTANCE;
  }

  /** Python membership helper used by {@code ContainmentIntrinsics.$in$}. */
  public static boolean containsKey(PyDict<?,?> dict, Object key) {
    return dict.containsKey(key);
  }

  /**
   * Dictionary equality helper called by the generic equality intrinsic. Key order is irrelevant.
   * The identity shortcut matches Python's container comparison behavior, including NaN keys.
   */
  public static boolean equals(PyDict<?,?> left, PyDict<?,?> right) {
    if (left == right) {
      return true;
    }
    if (left.size() != right.size()) {
      return false;
    }

    for (var i = 0; i < left.entryCount(); i++) {
      if (!left.isLive(i)) {
        continue;
      }
      var key = left.keyAt(i);
      var value = left.valueAt(i);
      var otherValue = right.get(key);
      if (otherValue == null || !sameValue(value, otherValue)) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameValue(Object left, Object right) {
    return left == right || ComparisonIntrinsics.$eq$(left, right);
  }

  private static PyKeyError missingKey(Object key) {
    return new PyKeyError(StringIntrinsics.$repr$(key));
  }
}
