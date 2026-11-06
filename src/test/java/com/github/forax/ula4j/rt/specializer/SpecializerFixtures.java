package com.github.forax.ula4j.rt.specializer;

import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.intrinsic.Intrinsic;

import java.lang.invoke.MethodHandles;

/// Handwritten intrinsics
public final class SpecializerFixtures {
  private SpecializerFixtures() {}

  @Intrinsic
  public static String $describe$(Object value) {
    switch (value) {
      case Long _ -> { return "long"; }
      case Double _ -> { return "double"; }
      case String _ -> { return "string"; }
      default -> { return "other"; }
    }
  }

  /// Labels overlap: the first assignable label must win.
  @Intrinsic
  public static String $overlap$(Object value) {
    switch (value) {
      case Long _ -> { return "long"; }
      case Number _ -> { return "number"; }
      default -> { return "other"; }
    }
  }

  @Intrinsic
  public static Object $strict$(Object value) {
    switch (value) {
      case Long _ -> { return value; }
      default -> throw new PyTypeError("unsupported operand: " + value);
    }
  }

  /// Calls another intrinsic (rewritten to an invokedynamic) and uses a non-switch indy (string concat).
  @Intrinsic
  public static String $wrapped$(Object value) {
    return "<" + $describe$(value) + ">";
  }

  @Intrinsic
  public static long $plusOne$(long value) {
    return value + 1;
  }

  @Intrinsic
  public static long $twice$(long value) {
    return value * 2;
  }

  @Intrinsic
  public static double $twice$(double value) {
    return value * 2.0;
  }

  @Intrinsic
  public static String $viaPrivateHelper$(Object value) {
    return secret(value);
  }

  private static String secret(Object value) {
    return "secret:" + $describe$(value);
  }

  /// Reports the class the method is running in: a hidden class when specialized.
  @Intrinsic
  public static Class<?> $whereAmI$() {
    return MethodHandles.lookup().lookupClass();
  }

  @Intrinsic
  public static Class<?> $outerWhere$() {
    return $whereAmI$();
  }

  /// Not an intrinsic (no leading and trailing '$').
  public static String helper(Object value) {
    return "helper";
  }

  public static final class NoIntrinsics {
    private NoIntrinsics() {}

    public static int plain(int x) {
      return x;
    }
  }
}