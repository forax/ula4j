package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyFunction;
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
      case Double d -> { return d != 0.0; }  // NaN is truthy
      case String s -> { return !s.isEmpty(); }
      case PyNone _ -> { return false; }
      case BigInteger b -> { return b.signum() != 0; }
      case PyTuple t -> { return TupleIntrinsics.$size$(t) != 0L; }
      case PyFunction _ -> { return true; }
      default -> throw new UnimplementedFeatureError("truthiness of " + value.getClass().getName());
    }
  }

  /// `not x` (the compiler boxes the result when it is used as a value).
  @Intrinsic
  public static boolean $not$(Object value) {
    return !$truthy$(value);
  }
}