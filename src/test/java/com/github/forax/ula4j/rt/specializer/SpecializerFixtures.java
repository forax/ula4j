package com.github.forax.ula4j.rt.specializer;

import com.github.forax.ula4j.rt.error.PyTypeError;

import java.lang.invoke.MethodHandles;

/// Hand-written intrinsics following the shape required by the implementation plan (§11).
public final class SpecializerFixtures {
  private SpecializerFixtures() {}

  public static String $describe$(Object value) {
    switch (value) {
      case Long _ -> { return "long"; }
      case Double _ -> { return "double"; }
      case String _ -> { return "string"; }
      default -> { return "other"; }
    }
  }

  /// Labels overlap: the first assignable label must win.
  public static String $overlap$(Object value) {
    switch (value) {
      case Long _ -> { return "long"; }
      case Number _ -> { return "number"; }
      default -> { return "other"; }
    }
  }

  public static Object $strict$(Object value) {
    switch (value) {
      case Long _ -> { return value; }
      default -> throw new PyTypeError("unsupported operand: " + value);
    }
  }

  /// Calls another intrinsic (rewritten to an invokedynamic) and uses a non-switch indy (string concat).
  public static String $wrapped$(Object value) {
    return "<" + $describe$(value) + ">";
  }

  public static long $plusOne$(long value) {
    return value + 1;
  }

  public static long $twice$(long value) {
    return value * 2;
  }

  public static double $twice$(double value) {
    return value * 2.0;
  }

  public static long $countdown$(long n) {
    if (n <= 0) {
      return 0;
    }
    return 1 + $countdown$(n - 1);
  }

  public static String $viaPrivateHelper$(Object value) {
    return secret(value);
  }

  private static String secret(Object value) {
    return "secret:" + $describe$(value);
  }

  /// Reports the class the method is running in: a hidden class when specialized.
  public static Class<?> $whereAmI$() {
    return MethodHandles.lookup().lookupClass();
  }

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