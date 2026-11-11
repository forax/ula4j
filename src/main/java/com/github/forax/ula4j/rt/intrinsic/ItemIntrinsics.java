package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyDict;
import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyIndexError;
import com.github.forax.ula4j.rt.error.PyTypeError;

import java.math.BigInteger;

import static com.github.forax.ula4j.rt.intrinsic.IntrinsicSupport.typeName;

public final class ItemIntrinsics {
  private ItemIntrinsics() {
    throw new AssertionError();
  }

  @Intrinsic
  public static Object $getItem$(Object container, Object index) {
    Object result;
    switch (container) {
      case PyList<?> l  -> result = ListIntrinsics.$get$(l, $toIndex$(index, "list"));
      case PyDict<?,?> d -> result = DictIntrinsics.$get$(d, index);
      case PyTuple t    -> result = TupleIntrinsics.$get$(t, $toIndex$(index, "tuple"));
      // case String s  -> once str indexing exists
      default -> throw new PyTypeError("'" + typeName(container) + "' object is not subscriptable");
    }
    return result;
  }

  @Intrinsic
  @SuppressWarnings("unchecked")
  public static Object $setItem$(Object container, Object index, Object value) {
    Object result;
    switch (container) {
      case PyList<?> list  -> result = ListIntrinsics.$set$((PyList<Object>) list, $toIndex$(index, "list"), value);
      case PyDict<?,?> dict -> result = DictIntrinsics.$set$((PyDict<Object, Object>) dict, index, value);
      case PyTuple _ -> throw new PyTypeError("'tuple' object does not support item assignment");
      case String _ -> throw new PyTypeError("'str' object does not support item assignment");
      default -> throw new PyTypeError("'" + typeName(container) + "' object is not subscriptable");
    }
    return result;
  }

  @Intrinsic
  @SuppressWarnings("unchecked")
  public static Object $deleteItem$(Object container, Object index) {
    Object result;
    switch (container) {
      case PyList<?> list  -> result = ListIntrinsics.$delete$(list, $toIndex$(index, "list"));
      case PyDict<?,?> dict -> result = DictIntrinsics.$delete$((PyDict<Object, ?>) dict, index);
      case PyTuple _ -> throw new PyTypeError("'tuple' object does not support item assignment");
      case String _ -> throw new PyTypeError("'str' object does not support item assignment");
      default -> throw new PyTypeError("'" + typeName(container) + "' object is not subscriptable");
    }
    return result;
  }

  /// Converts Long and Boolean to long.
  /// A BigInteger raises IndexError("cannot fit 'int' into an index-sized integer").
  /// Anything else raises TypeError("list indices must be integers or slices, not str").
  @Intrinsic
  public static long $toIndex$(Object index, String name) {
    return switch (index) {
      case Long l -> l;
      case Boolean b -> b ? 1L : 0L;
      case BigInteger b -> throw new PyIndexError("cannot fit 'int' into an index-sized integer");
      default -> throw new PyTypeError(name + " indices must be integers or slices, not str");
    };
  }
}