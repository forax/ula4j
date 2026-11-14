package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.PyTuple.PyTuple1;
import com.github.forax.ula4j.rt.PyTuple.PyTuple2;
import com.github.forax.ula4j.rt.PyTuple.PyTupleN;
import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.intrinsic.HashingIntrinsics;
import com.github.forax.ula4j.compiler.SpecializerFixtures;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.runtime.SwitchBootstraps;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class RTTest {
  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
  private static final MethodType SWITCH_TYPE = MethodType.methodType(int.class, Object.class, int.class);
  private static final MethodType DESCRIBE = MethodType.methodType(String.class, Object.class);

  private static final Class<?>[] LABELS = {
      Long.class, Double.class, String.class, Boolean.class,
      BigInteger.class, PyNone.class, PyTuple.class, List.class
  };

  /// Every value matches one label; they cover eight distinct receiver classes.
  private static final List<Object> MATCHING = List.of(
      1L, 2.5, "s", true, BigInteger.TEN, PyNone.INSTANCE, new PyTuple1(1L), new ArrayList<>(), 2L, "t");

  // ------------------------------------------------------------------ helpers

  private static MutableCallSite typeSwitchSite(Class<?>... labels) {
    var site = RT.bsmTypeSwitch(LOOKUP, "typeSwitch", SWITCH_TYPE, (Object[]) labels);
    return assertInstanceOf(MutableCallSite.class, site);
  }

  private static ConstantCallSite jdkTypeSwitchSite(Class<?>... labels) {
    var site = SwitchBootstraps.typeSwitch(LOOKUP, "typeSwitch", SWITCH_TYPE, (Object[]) labels);
    return assertInstanceOf(ConstantCallSite.class, site);
  }

  private static int select(MethodHandle invoker, Object value) throws Throwable {
    return (int) invoker.invokeExact(value, 0);
  }

  private static Class<?> declaringClass(MethodHandle handle) {
    return MethodHandles.lookup().revealDirect(handle).getDeclaringClass();
  }


  @Nested
  public class BsmTypeSwitch {

    @Test
    public void typeSwitchSiteIsMutableAndHasTheTypeSwitchSignature() {
      var site = typeSwitchSite(Long.class, String.class);
      assertEquals(SWITCH_TYPE, site.type());
    }

    @Test
    public void selectsTheIndexOfTheFirstAssignableLabel() throws Throwable {
      var invoker = typeSwitchSite(Long.class, String.class, Number.class).dynamicInvoker();
      for (var round = 0; round < 3; round++) {  // first call links, later calls take the guard
        assertEquals(0, select(invoker, 1L));
        assertEquals(1, select(invoker, "s"));
        assertEquals(2, select(invoker, 2.5));
        assertEquals(2, select(invoker, BigInteger.TEN));
      }
    }

    @Test
    public void labelOrderDecidesWhenLabelsOverlap() throws Throwable {
      var invoker = typeSwitchSite(Number.class, Long.class).dynamicInvoker();
      assertEquals(0, select(invoker, 1L));
      assertEquals(0, select(invoker, 1L));
    }

    @Test
    public void aValueMatchingNoLabelSelectsTheDefaultIndexLikeTheJdk() throws Throwable {
      var invoker = typeSwitchSite(LABELS).dynamicInvoker();
      var jdk = jdkTypeSwitchSite(LABELS).dynamicInvoker();
      for (var round = 0; round < 5; round++) {
        assertEquals(LABELS.length, select(jdk, 'c'));
        assertEquals(LABELS.length, select(invoker, 'c'));  // Character
        assertEquals(LABELS.length, select(invoker, 1));    // Integer is not Long
      }
    }

    @Test
    public void agreesWithTheJdkTypeSwitchForAnyCallPattern() throws Throwable {
      var invoker = typeSwitchSite(LABELS).dynamicInvoker();
      var jdk = jdkTypeSwitchSite(LABELS).dynamicInvoker();
      var random = new Random(42);
      for (var i = 0; i < 500; i++) {
        var value = MATCHING.get(random.nextInt(MATCHING.size()));
        assertEquals(select(jdk, value), select(invoker, value), "call " + i + ": " + value);
      }
    }

    @Test
    public void firstCallLinksTheSiteAndAMonomorphicSiteThenStaysLinked() throws Throwable {
      var site = typeSwitchSite(Long.class, String.class);
      var invoker = site.dynamicInvoker();
      var unlinked = site.getTarget();

      assertEquals(0, select(invoker, 1L));
      var linked = site.getTarget();
      assertNotSame(unlinked, linked);

      for (var i = 0; i < 50; i++) {
        assertEquals(0, select(invoker, (long) i));
      }
      assertSame(linked, site.getTarget(), "a guard that holds must not trigger any relinking");
    }

    @Test
    public void aSiteSeeingManyClassesStopsRelinkingAndStaysCorrect() throws Throwable {
      var site = typeSwitchSite(LABELS);
      var invoker = site.dynamicInvoker();
      var jdk = jdkTypeSwitchSite(LABELS).dynamicInvoker();

      for (var round = 0; round < 3; round++) {  // warm-up: observe every class
        for (var value : MATCHING) {
          select(invoker, value);
        }
      }
      var settled = site.getTarget();

      for (var round = 0; round < 20; round++) {
        for (var value : MATCHING) {
          assertEquals(select(jdk, value), select(invoker, value), String.valueOf(value));
        }
      }
      assertSame(settled, site.getTarget(), "a megamorphic site must fall back once, not relink forever");
    }

    @Test
    public void everySiteKeepsItsOwnState() throws Throwable {
      var a = typeSwitchSite(Long.class, String.class);
      var b = typeSwitchSite(Long.class, String.class);
      assertNotSame(a, b);

      var untouched = b.getTarget();
      select(a.dynamicInvoker(), 1L);
      select(a.dynamicInvoker(), "s");
      select(a.dynamicInvoker(), 1L);
      assertSame(untouched, b.getTarget(), "profiling one site must not affect another");

      assertEquals(1, select(b.dynamicInvoker(), "s"));
      assertNotSame(untouched, b.getTarget());
    }
  }

  @Nested
  public class BsmTypeSpecialize {

    @Test
    public void specializeReturnsAConstantCallSiteBoundToTheSpecializedIntrinsic() throws Throwable {
      CallSite site = RT.bsmSpecialize(LOOKUP, "$describe$", DESCRIBE, SpecializerFixtures.class);
      assertInstanceOf(ConstantCallSite.class, site);
      assertEquals(DESCRIBE, site.type());

      var describe = site.dynamicInvoker();
      assertEquals("long", (String) describe.invokeExact((Object) 1L));
      assertEquals("string", (String) describe.invokeExact((Object) "s"));
      assertEquals("other", (String) describe.invokeExact((Object) PyNone.INSTANCE));
    }

    @Test
    public void everyCallSiteGetsItsOwnHiddenClass() {
      var first = RT.bsmSpecialize(LOOKUP, "$describe$", DESCRIBE, SpecializerFixtures.class).getTarget();
      var second = RT.bsmSpecialize(LOOKUP, "$describe$", DESCRIBE, SpecializerFixtures.class).getTarget();

      var firstClass = declaringClass(first);
      var secondClass = declaringClass(second);
      assertTrue(firstClass.isHidden());
      assertTrue(secondClass.isHidden());
      assertNotEquals(SpecializerFixtures.class, firstClass);
      assertNotEquals(firstClass, secondClass, "specialized implementations must not be shared between sites");
    }

    @Test
    public void nestedIntrinsicCallsAreLinkedThroughTheBootstrap() throws Throwable {
      var wrapped = RT.bsmSpecialize(LOOKUP, "$wrapped$", DESCRIBE, SpecializerFixtures.class).getTarget();
      for (var value : MATCHING) {
        assertEquals(SpecializerFixtures.$wrapped$(value), (String) wrapped.invokeExact(value), String.valueOf(value));
      }
    }

    @Test
    public void specializedCodeCanCallPrivateHelpersOfItsIntrinsicClass() throws Throwable {
      var viaHelper = RT.bsmSpecialize(LOOKUP, "$viaPrivateHelper$", DESCRIBE, SpecializerFixtures.class).getTarget();
      assertEquals("secret:double", (String) viaHelper.invokeExact((Object) 2.5));
    }

    @Test
    public void unknownIntrinsicNameIsRejected() {
      assertThrows(BootstrapMethodError.class,
          () -> RT.bsmSpecialize(LOOKUP, "$nope$", DESCRIBE, SpecializerFixtures.class));
    }

    @Test
    public void aDescriptorThatDoesNotMatchTheIntrinsicIsRejected() {
      var wrongType = MethodType.methodType(String.class, String.class);
      assertThrows(BootstrapMethodError.class,
          () -> RT.bsmSpecialize(LOOKUP, "$describe$", wrongType, SpecializerFixtures.class));
    }
  }

  @Nested
  public class HashIntrinsic {

    @Test
    public void specializedHashMatchesTheGenericHash() throws Throwable {
      var hash = RT.bsmSpecialize(LOOKUP, "$hash$",
          MethodType.methodType(long.class, Object.class), HashingIntrinsics.class).getTarget();

      List<Object> values = List.of(
          0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, BigInteger.ONE.shiftLeft(100), BigInteger.TEN.negate(),
          true, false, 0.5, 1.5, -0.0, Double.NaN, Double.POSITIVE_INFINITY,
          "", "abc", PyNone.INSTANCE,
          new PyTuple2(1L, new PyTuple1("x")),
          new PyTupleN(new Object[]{1.0d, 2L, 3L, 4L, 5L}));

      for (var round = 0; round < 3; round++) {
        for (var value : values) {
          assertEquals(HashingIntrinsics.$hash$(value), (long) hash.invokeExact(value), String.valueOf(value));
        }
      }
    }

    @Test
    public void specializedHashRaisesTheSameException() {
      var hash = RT.bsmSpecialize(LOOKUP, "$hash$",
          MethodType.methodType(long.class, Object.class), HashingIntrinsics.class).getTarget();

      var expected = assertThrows(PyTypeError.class, () -> HashingIntrinsics.$hash$(new Object()));
      var actual = assertThrows(PyTypeError.class, () -> {
        long ignored = (long) hash.invokeExact(new Object());
      });
      assertEquals(expected.getMessage(), actual.getMessage());
    }
  }
}