package com.github.forax.ula4j.rt.intrinsic;

//import com.github.forax.ula4j.rt.PyDict;
//import com.github.forax.ula4j.rt.PyList;
import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple;
import com.github.forax.ula4j.rt.error.PyTypeError;

import java.math.BigInteger;

/// Python-compatible hashing for the runtime's canonical value representations.
///
/// Only the hashes of `bool`, `int` and `float` are specified (they follow the 64-bit CPython
/// algorithm). The hashes of `str`, `tuple` and `None` are implementation details: they only
/// guarantee that equal values have equal hashes, and must never be printed or asserted.
public final class HashingIntrinsics {
  private static final int HASH_BITS = 61;
  private static final long HASH_MODULUS = (1L << HASH_BITS) - 1;
  private static final BigInteger BIG_HASH_MODULUS = BigInteger.valueOf(HASH_MODULUS);
  private static final int HASH_SHIFT = 28;
  private static final long HASH_INF = 314159L;

  // CPython (>= 3.10) hashes NaN by identity. A boxed Double has no identity we control, so NaN
  // gets a constant hash. Equality still fails (NaN != NaN), only a pathological dict full of
  // NaN keys degrades to collisions.
  private static final long HASH_NAN = 0L;
  private static final long HASH_NONE = 0xFCA86420L;

  // xxHash64 primes, as used by CPython's tuple hash since 3.8
  private static final long XXPRIME_1 = 0x9E3779B185EBCA87L;
  private static final long XXPRIME_2 = 0xC2B2AE3D27D4EB4FL;
  private static final long XXPRIME_5 = 0x27D4EB2F165667C5L;

  private HashingIntrinsics() {
    throw new AssertionError("no instances");
  }

  /// Returns the signed 64-bit hash value used by the 64-bit CPython runtime contract.
  @Intrinsic
  public static long $hash$(Object value) {
    switch (value) {
      case Boolean bool -> { return bool ? 1L : 0L; }
      case Long integer -> { return hashLong(integer); }
      case BigInteger integer -> { return hashBigInteger(integer); }
      case Double floating -> { return hashDouble(floating); }
      case String string -> { return hashString(string); }
      case PyTuple tuple -> { return hashTuple(tuple); }
      //case PyList _ -> throw unhashable("list");
      //case PyDict _ -> throw unhashable("dict");
      case PyNone _ -> { return HASH_NONE; }
      //case PyFunction _, PyType _ -> { return System.identityHashCode(value); }
      default -> throw new PyTypeError(
          "not a canonical runtime value: " + value.getClass().getName());
    }
  }

  /// Allocation-free equivalent of {@link #hashBigInteger}, valid for every long.
  private static long hashLong(long value) {
    // For Long.MIN_VALUE, -value overflows back to itself, which read as unsigned is 2**63.
    var magnitude = value < 0 ? -value : value;
    var hash = Long.remainderUnsigned(magnitude, HASH_MODULUS);
    if (value < 0) {
      hash = -hash;
    }
    return hash == -1L ? -2L : hash;
  }

  private static long hashBigInteger(BigInteger value) {
    var hash = value.abs().mod(BIG_HASH_MODULUS).longValue();
    if (value.signum() < 0) {
      hash = -hash;
    }
    return hash == -1L ? -2L : hash;
  }

  /// CPython's `_Py_HashDouble`. Integral floats get the same hash as the equal int by
  /// construction, so no special case is needed.
  private static long hashDouble(double value) {
    if (Double.isNaN(value)) {
      return HASH_NAN;
    }
    if (Double.isInfinite(value)) {
      return value > 0 ? HASH_INF : -HASH_INF;
    }
    if (value == 0.0d) {  // also -0.0
      return 0L;
    }

    var abs = Math.abs(value);
    var exponent = Math.getExponent(abs) + 1;  // frexp exponent
    if (exponent == Double.MIN_EXPONENT) {
      // subnormal: normalize before extracting the frexp mantissa
      exponent = Math.getExponent(Math.scalb(abs, 54)) + 1 - 54;
    }
    var mantissa = Math.scalb(abs, -exponent);  // in [0.5, 1)
    var hash = 0L;
    while (mantissa != 0.0d) {
      hash = rotate61(hash, HASH_SHIFT);
      mantissa *= (1L << HASH_SHIFT);
      var chunk = (long) mantissa;
      mantissa -= chunk;
      hash += chunk;
      if (hash >= HASH_MODULUS) {
        hash -= HASH_MODULUS;
      }
      exponent -= HASH_SHIFT;
    }

    var rotation = exponent >= 0
        ? exponent % HASH_BITS
        : HASH_BITS - 1 - ((-1 - exponent) % HASH_BITS);
    hash = rotate61(hash, rotation);
    if (value < 0.0d) {
      hash = -hash;
    }
    return hash == -1L ? -2L : hash;
  }

  private static long rotate61(long value, int distance) {
    if (distance == 0) {
      return value;
    }
    return ((value << distance) & HASH_MODULUS) | (value >>> (HASH_BITS - distance));
  }

  /// Unspecified value: Java's cached String hash, spread over 64 bits (a plain int to long
  /// widening would leave the high bits as copies of the sign bit, which hurts a table that
  /// uses CPython-style perturbation). Equal strings have equal hashes.
  private static long hashString(String string) {
    var hash = (long) string.hashCode() * 0x9E3779B97F4A7C15L;
    hash ^= hash >>> 32;
    return hash == -1L ? -2L : hash;
  }

  /// Unspecified value: the xxHash-based combiner CPython uses since 3.8.
  /// Equal tuples have equal hashes.
  private static long hashTuple(PyTuple tuple) {
    var size = TupleIntrinsics.$size$(tuple);
    var accumulator = XXPRIME_5;
    for (var i = 0; i < size; i++) {
      var itemHash = $hash$(TupleIntrinsics.$get$(tuple, (long) i));
      accumulator += itemHash * XXPRIME_2;
      accumulator = Long.rotateLeft(accumulator, 31);
      accumulator *= XXPRIME_1;
    }
    accumulator += size ^ (XXPRIME_5 ^ 3527539L);
    return accumulator == -1L ? 1546275796L : accumulator;
  }

  private static PyTypeError unhashable(String typeName) {
    return new PyTypeError("unhashable type: '" + typeName + "'");
  }
}