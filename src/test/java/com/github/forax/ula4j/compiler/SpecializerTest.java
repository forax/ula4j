package com.github.forax.ula4j.compiler;

import com.github.forax.ula4j.rt.PyNone;
import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.error.PyTypeError;
import org.junit.jupiter.api.Test;

import java.io.IOError;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class SpecializerTest {
  // The hidden classes are nestmates of the lookup class, so the lookup must be on the intrinsic class.
  private static final MethodHandles.Lookup LOOKUP = fixturesLookup();

  private static final MethodType DESCRIBE = MethodType.methodType(String.class, Object.class);
  private static final MethodType STRICT = MethodType.methodType(Object.class, Object.class);
  private static final MethodType WHERE = MethodType.methodType(Class.class);

  private static final List<Object> SAMPLE_VALUES = List.of(
      0L, 1L, Long.MIN_VALUE, Long.MAX_VALUE, BigInteger.TEN, true, false,
      2.5, Double.NaN, -0.0, "", "s", PyNone.INSTANCE, new PyTuple1(1L), new ArrayList<>());

  private static MethodHandles.Lookup fixturesLookup() {
    try {
      return MethodHandles.privateLookupIn(SpecializerFixtures.class, MethodHandles.lookup());
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private static MethodHandle specialize(String name, MethodType type) {
    var key = name + type.toMethodDescriptorString();
    var supplier = Specializer.specialize(LOOKUP, SpecializerFixtures.class).get(key);
    assertNotNull(supplier, key);
    return supplier.get();
  }

  private static Class<?> declaringClass(MethodHandle handle) {
    return MethodHandles.lookup().revealDirect(handle).getDeclaringClass();
  }

  // ------------------------------------------------------------------ what is specialized

  @Test
  public void onlyIntrinsicsAreListedKeyedByNameAndDescriptor() {
    var specializers = Specializer.specialize(LOOKUP, SpecializerFixtures.class);
    assertTrue(specializers.containsKey("$describe$(Ljava/lang/Object;)Ljava/lang/String;"));
    assertTrue(specializers.containsKey("$twice$(J)J"));
    assertTrue(specializers.containsKey("$twice$(D)D"));
    for (var key : specializers.keySet()) {
      assertTrue(key.startsWith("$"), key);
    }
    assertFalse(specializers.keySet().stream()
        .anyMatch(key -> key.contains("helper") || key.contains("secret") || key.contains("init")));
  }

  @Test
  public void aClassWithoutIntrinsicsHasNothingToSpecialize() {
    assertTrue(Specializer.specialize(LOOKUP, SpecializerFixtures.NoIntrinsics.class).isEmpty());
  }

  @Test
  public void aClassWithoutClassFileIsRejected() {
    Runnable lambda = () -> { };  // lambda classes are hidden: there is no class file to read
    assertThrows(IOError.class, () -> Specializer.specialize(LOOKUP, lambda.getClass()));
  }

  // ------------------------------------------------------------------ generated classes

  @Test
  public void specializedHandleKeepsTheIntrinsicType() {
    assertEquals(DESCRIBE, specialize("$describe$", DESCRIBE).type());
  }

  @Test
  public void specializedMethodLivesInAHiddenNestmateOfTheIntrinsicClass() {
    var declaring = declaringClass(specialize("$describe$", DESCRIBE));
    assertTrue(declaring.isHidden());
    assertNotEquals(SpecializerFixtures.class, declaring);
    assertEquals(SpecializerFixtures.class, declaring.getNestHost());
  }

  @Test
  public void everyLinkageDefinesItsOwnHiddenClass() {
    var supplier = Specializer.specialize(LOOKUP, SpecializerFixtures.class)
        .get("$describe$" + DESCRIBE.toMethodDescriptorString());
    assertNotEquals(declaringClass(supplier.get()), declaringClass(supplier.get()));
  }

  // ------------------------------------------------------------------ generic == specialized (§15.3)

  @Test
  public void specializedSwitchMatchesTheGenericIntrinsic() throws Throwable {
    var describe = specialize("$describe$", DESCRIBE);
    for (var round = 0; round < 10; round++) {  // enough rounds to push the site past its guard chain
      for (var value : SAMPLE_VALUES) {
        assertEquals(SpecializerFixtures.$describe$(value), (String) describe.invokeExact(value),
            "round " + round + ", value " + value);
      }
    }
  }

  @Test
  public void aValueMatchingNoLabelReachesTheGenericDefault() throws Throwable {
    var describe = specialize("$describe$", DESCRIBE);
    assertEquals("other", (String) describe.invokeExact((Object) PyNone.INSTANCE));
    assertEquals("long", (String) describe.invokeExact((Object) 1L));  // and the site still works after it
  }

  @Test
  public void overlappingLabelsKeepTheirSourceOrder() throws Throwable {
    var overlap = specialize("$overlap$", DESCRIBE);
    for (var round = 0; round < 5; round++) {
      assertEquals("long", (String) overlap.invokeExact((Object) 1L));
      assertEquals("number", (String) overlap.invokeExact((Object) 2.5));
      assertEquals("number", (String) overlap.invokeExact((Object) BigInteger.TEN));
      assertEquals("other", (String) overlap.invokeExact((Object) "s"));
    }
  }

  @Test
  public void specializedIntrinsicRaisesTheSameExceptionAsTheGenericOne() throws Throwable {
    var strict = specialize("$strict$", STRICT);
    for (var value : List.<Object>of(2.5, "s", PyNone.INSTANCE)) {
      var expected = assertThrows(PyTypeError.class, () -> SpecializerFixtures.$strict$(value));
      var actual = assertThrows(PyTypeError.class, () -> {
        Object ignored = (Object) strict.invokeExact(value);
      });
      assertEquals(expected.getMessage(), actual.getMessage());
    }
    assertEquals(7L, (Object) strict.invokeExact((Object) 7L));
  }

  @Test
  public void intrinsicsWithoutASwitchAreCopiedUnchanged() throws Throwable {
    var plusOne = specialize("$plusOne$", MethodType.methodType(long.class, long.class));
    assertEquals(42L, (long) plusOne.invokeExact(41L));
  }

  @Test
  public void overloadedIntrinsicsAreSpecializedIndependently() throws Throwable {
    var twiceLong = specialize("$twice$", MethodType.methodType(long.class, long.class));
    var twiceDouble = specialize("$twice$", MethodType.methodType(double.class, double.class));
    assertEquals(42L, (long) twiceLong.invokeExact(21L));
    assertEquals(5.0, (double) twiceDouble.invokeExact(2.5));
  }

  // ------------------------------------------------------------------ nested intrinsics (§12.3)

  @Test
  public void callsToOtherIntrinsicsAndOtherIndyAreRewrittenCorrectly() throws Throwable {
    var wrapped = specialize("$wrapped$", DESCRIBE);
    for (var value : SAMPLE_VALUES) {
      assertEquals(SpecializerFixtures.$wrapped$(value), (String) wrapped.invokeExact(value), String.valueOf(value));
    }
  }

  @Test
  public void nestedIntrinsicCallSitesSpecializeThroughTheirOwnClasses() throws Throwable {
    var first = specialize("$outerWhere$", WHERE);
    var second = specialize("$outerWhere$", WHERE);

    var firstInner = (Class<?>) first.invoke();
    var secondInner = (Class<?>) second.invoke();

    assertEquals(SpecializerFixtures.class, SpecializerFixtures.$outerWhere$());  // generic reference
    assertTrue(firstInner.isHidden());
    assertTrue(secondInner.isHidden());
    assertNotEquals(firstInner, secondInner, "two call sites must not share a specialized class");
    assertNotEquals(declaringClass(first), firstInner, "the nested site gets its own class");
  }

  @Test
  public void specializedCodeCanCallPrivateHelpersOfTheIntrinsicClass() throws Throwable {
    var viaHelper = specialize("$viaPrivateHelper$", DESCRIBE);
    assertEquals("secret:long", (String) viaHelper.invokeExact((Object) 1L));
  }
}