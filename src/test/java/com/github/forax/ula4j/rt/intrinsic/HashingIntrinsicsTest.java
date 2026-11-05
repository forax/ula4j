package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.PyTuple.PyTupleN;
import com.github.forax.ula4j.rt.error.PyTypeError;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public final class HashingIntrinsicsTest {
  @Test
  public void equalNumericValuesHaveEqualHashes() {
    long one = HashingIntrinsics.$hash$(1L);
    assertEquals(one, HashingIntrinsics.$hash$(true));
    assertEquals(one, HashingIntrinsics.$hash$(BigInteger.ONE));
    assertEquals(one, HashingIntrinsics.$hash$(1.0d));
    assertEquals(0L, HashingIntrinsics.$hash$(-0.0d));
    assertEquals(0L, HashingIntrinsics.$hash$(0L));
  }

  @Test
  public void floatHashMatchesThe64BitCpythonModularAlgorithm() {
    assertEquals(1_152_921_504_606_846_976L, HashingIntrinsics.$hash$(0.5d));
    assertEquals(1_152_921_504_606_846_977L, HashingIntrinsics.$hash$(1.5d));
    assertEquals(314159L, HashingIntrinsics.$hash$(Double.POSITIVE_INFINITY));
    assertEquals(-314159L, HashingIntrinsics.$hash$(Double.NEGATIVE_INFINITY));
  }

  @Test
  public void nestedTupleEqualityAndHashingAreRecursive() {
    var left = new PyTuple2(1L, new PyTuple1("x"));
    var right = new PyTupleN(new Object[]{1.0d, new PyTupleN(new Object[]{"x"})});
    assertEquals(HashingIntrinsics.$hash$(left), HashingIntrinsics.$hash$(right));
  }

  @Test
  public void listsCompareRecursivelyButAreUnhashable() {
    var list = new ArrayList<Object>();
    list.add(1L);
    assertThrows(PyTypeError.class, () -> HashingIntrinsics.$hash$(list));
  }

  @Test
  public void tupleContainingAnUnhashableValueIsUnhashable() {
    var tuple = new PyTuple1(new ArrayList<>());
    assertThrows(PyTypeError.class, () -> HashingIntrinsics.$hash$(tuple));
  }
}
