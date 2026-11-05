package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.specializer.Specializer;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.runtime.SwitchBootstraps;
import java.util.Map;
import java.util.function.Supplier;

/// Contains all bootstrap methods entry points
public final class RT {

  private static final ClassValue<Map<String, Supplier<MethodHandle>>> SPECIALIZER_CLASS_VALUE;

  static {
    var lookup = MethodHandles.lookup();
    SPECIALIZER_CLASS_VALUE = new ClassValue<>() {
      @Override
      protected Map<String, Supplier<MethodHandle>> computeValue(Class<?> type) {
        return Specializer.specialize(lookup, type);
      }
    };
  }

  public static CallSite bsmSpecialize(MethodHandles.Lookup lookup, String name, MethodType type, Class<?> intrisicClass) {
    var map = SPECIALIZER_CLASS_VALUE.get(intrisicClass);
    var target = map.get(name + type.toMethodDescriptorString()).get();
    return new ConstantCallSite(target);
  }


  private static final class InliningCache extends MutableCallSite {
    private static final MethodHandle FALLBACK, TEST;
    static {
      var lookup = MethodHandles.lookup();
      try {
        FALLBACK = lookup.findVirtual(InliningCache.class, "fallback",
            MethodType.methodType(int.class, Object.class, int.class));
        TEST = lookup.findStatic(InliningCache.class, "test",
            MethodType.methodType(boolean.class, Object.class, Object.class, int.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private static final int MAX_DEPTH = 3;

    record CallSiteInfo() {}

    private final InliningCache root;
    private final int depth;
    private final MethodHandles.Lookup lookup;
    private final Class<?>[] caseClasses;

    InliningCache(MethodHandles.Lookup lookup, Class<?>[] caseClasses) {
      this.lookup = lookup;
      this.caseClasses = caseClasses;
      depth = 0;
      super(MethodType.methodType(int.class, Object.class, int.class));
      this.root = this;
      setTarget(FALLBACK.bindTo(this));
    }

    private InliningCache(InliningCache root, int depth, MethodHandles.Lookup lookup, Class<?>[] caseClasses) {
      this.root = root;
      this.depth = depth;
      this.lookup = lookup;
      this.caseClasses = caseClasses;
      super(MethodType.methodType(int.class, Object.class, int.class));
      setTarget(FALLBACK.bindTo(this));
    }

    private static boolean test(Object o1, Object o2, int alwaysZero) {
      return o1 == o2;
    }

    private int findCaseIndex(Class<?> receiverClass) {
      for (var i = 0; i < caseClasses.length; i++) {
        var caseClass = caseClasses[i];
        if (caseClass.isAssignableFrom(receiverClass)) {
          return i;
        }
      }
      return -1;
    }

    public int fallback(Object receiver, int alwaysZero) {
      var receiverClass = receiver.getClass();
      var index = findCaseIndex(receiverClass);

      if (depth == MAX_DEPTH) {
        root.setTarget(
            SwitchBootstraps.typeSwitch(lookup, null, type(), (Object[]) caseClasses)
                .dynamicInvoker());
        return index;
      }

      var test = TEST.bindTo(receiverClass);
      var target = MethodHandles.dropArguments(
          MethodHandles.constant(int.class, index),
          0, Object.class, int.class);
      var fallback = new InliningCache(root, depth + 1, lookup, caseClasses).dynamicInvoker();
      var guard = MethodHandles.guardWithTest(test, target, fallback);

      setTarget(guard);

      return index;
    }
  }

  public static CallSite bsmTypeSwitch(MethodHandles.Lookup lookup, String name, MethodType type, Class<?>[] caseTypes) {
    return new InliningCache(lookup, caseTypes);
  }
}
