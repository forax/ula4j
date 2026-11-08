package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.specializer.Specializer;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.runtime.SwitchBootstraps;
import java.math.BigInteger;
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
        MethodHandles.Lookup teleportedLookup;
        try {
          teleportedLookup = MethodHandles.privateLookupIn(type, lookup);
        } catch (IllegalAccessException e) {
          throw (IllegalAccessError) new IllegalAccessError().initCause(e);
        }
        return Specializer.specialize(teleportedLookup, type);
      }
    };
  }

  public static CallSite bsmSpecialize(MethodHandles.Lookup unused, String name, MethodType type, Class<?> intrinsicClass) {
    var map = SPECIALIZER_CLASS_VALUE.get(intrinsicClass);
    var factory = map.get(name + type.toMethodDescriptorString());
    if (factory == null) {
      throw new BootstrapMethodError("no specialized factory found for " + intrinsicClass.getName() + "." + name + type.toMethodDescriptorString());
    }
    var target = factory.get();
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
            MethodType.methodType(boolean.class, Class.class, Object.class, int.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private static final int MAX_DEPTH = 3;

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

    private static boolean test(Class<?> receiverClass, Object o, int alwaysZero) {
      // null is an internal bug, so let's throw a NPE
      return receiverClass == o.getClass();
    }

    private int findCaseIndex(Class<?> receiverClass) {
      for (var i = 0; i < caseClasses.length; i++) {
        var caseClass = caseClasses[i];
        if (caseClass.isAssignableFrom(receiverClass)) {
          return i;
        }
      }
      return caseClasses.length;
    }

    public int fallback(Object receiver, int alwaysZero) {
      var receiverClass = receiver.getClass();
      var index = findCaseIndex(receiverClass);

      if (depth == MAX_DEPTH) {
        // callsite is megamorphic, fallback to jdk switch on types
        root.setTarget(
            SwitchBootstraps.typeSwitch(lookup, null, type(), (Object[]) caseClasses)
                .dynamicInvoker());
        return index;
      }

      // install a guard
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

  public static CallSite bsmTypeSwitch(MethodHandles.Lookup lookup, String unused, MethodType unused2, Object... bsmArgs) {
    var caseClasses = new Class<?>[bsmArgs.length];
    for (var i = 0; i < bsmArgs.length; i++) {
      caseClasses[i] = (Class<?>) bsmArgs[i];
    }
    return new InliningCache(lookup, caseClasses);
  }

  public static Object bsmConst(MethodHandles.Lookup lookup, String name, Class<?> type, Object value) {
    return value;
  }

  public static Object bsmBigInt(MethodHandles.Lookup lookup, String name, Class<?> type, String digits) {
    return new BigInteger(digits);
  }

  public static CallSite bsmDef(MethodHandles.Lookup lookup, String name, MethodType type, int astIndex) {
    return FunctionSupport.DefCallSite.def(lookup, type, astIndex);
  }

  public static CallSite bsmCall(MethodHandles.Lookup lookup, String name, MethodType type) {
    return FunctionSupport.FunctionCallSite.call(lookup, type);
  }

  public static CallSite bsmGetGlobal(MethodHandles.Lookup lookup, String name, MethodType type, String globalName) {
    return FunctionSupport.getGlobal(lookup, type, globalName);
  }

  public static CallSite bsmSetGlobal(MethodHandles.Lookup lookup, String name, MethodType type, String globalName) {
    return FunctionSupport.setGlobal(lookup, type, globalName);
  }
}
