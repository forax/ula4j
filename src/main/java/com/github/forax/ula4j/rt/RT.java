package com.github.forax.ula4j.rt;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;

/// Contains all bootstrap methods entry points
public final class RT {
  private static final class FunctionClassLoader extends ClassLoader {
    private final ModuleContext context;
    private final Dictionary dictionary;

    FunctionClassLoader(ModuleContext context, Dictionary dictionary) {
      this.context = context;
      this.dictionary = dictionary;
      super();
    }

    /// `name` is a binary name (dots).
    Class<?> define(String name, byte[] bytes) {
      return defineClass(name, bytes, 0, bytes.length);
    }
  }

  public static Class<?> defineClass(ModuleContext ctx, Dictionary dictionary, String className, byte[] bytes) {
    var loader = new FunctionClassLoader(ctx, dictionary);
    return loader.define(className, bytes);
  }


  public static CallSite bsmSpecialize(MethodHandles.Lookup unused, String name, MethodType type, Class<?> intrinsicClass) {
    return SpecializationSupport.specialize(name, type, intrinsicClass);
  }

  public static CallSite bsmTypeSwitch(MethodHandles.Lookup lookup, String unused, MethodType unused2, Object... bsmArgs) {
    return SpecializationSupport.typeSwitch(lookup, bsmArgs);
  }

  public static Object bsmConst(MethodHandles.Lookup lookup, String name, Class<?> type, Object value) {
    return value;
  }

  public static Object bsmBigInt(MethodHandles.Lookup lookup, String name, Class<?> type, String digits) {
    return new BigInteger(digits);
  }

  public static CallSite bsmDef(MethodHandles.Lookup lookup, String name, MethodType type, int astIndex) {
    var loader = (RT.FunctionClassLoader) lookup.lookupClass().getClassLoader();
    return FunctionSupport.DefCallSite.def(loader.context, loader.dictionary, type, astIndex);
  }

  public static CallSite bsmCall(MethodHandles.Lookup lookup, String name, MethodType type) {
    return FunctionSupport.FunctionCallSite.call(type);
  }

  public static CallSite bsmGetGlobal(MethodHandles.Lookup lookup, String name, MethodType type, String globalName) {
    var loader = (RT.FunctionClassLoader) lookup.lookupClass().getClassLoader();
    return FunctionSupport.getGlobal(loader.context, globalName);
  }

  public static CallSite bsmSetGlobal(MethodHandles.Lookup lookup, String name, MethodType type, String globalName) {
    var loader = (RT.FunctionClassLoader) lookup.lookupClass().getClassLoader();
    return FunctionSupport.setGlobal(loader.context, globalName);
  }
}
