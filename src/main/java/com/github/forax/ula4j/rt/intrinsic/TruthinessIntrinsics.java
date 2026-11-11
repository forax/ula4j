package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyDict;
import com.github.forax.ula4j.rt.PyFunction;
import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.UnimplementedFeatureError;

import java.math.BigInteger;

/// Python truthiness.
public final class TruthinessIntrinsics {
  private TruthinessIntrinsics() { throw new AssertionError(); }

  @Intrinsic
  public static boolean $truthy$(Object value) {
    switch (value) {
      case Boolean b -> { return b; }
      case Long l -> { return l != 0L; }
      case Double d -> { return d != 0.0; }  // NaN is truthy.
      case String s -> { return !s.isEmpty(); }
      case PyNone _ -> { return false; }
      case BigInteger b -> { return b.signum() != 0; }
      case PyTuple tuple -> { return TupleIntrinsics.$size$(tuple) != 0L; }
      case PyList<?> list -> { return !list.isEmpty(); }
      case PyDict<?,?> dict -> { return !dict.isEmpty(); }
      case PyFunction _ -> { return true; }
      default -> throw new UnimplementedFeatureError("truthiness of " + value.getClass().getName());
    }
  }

  @Intrinsic
  public static boolean $not$(Object value) {
    return !$truthy$(value);
  }
}