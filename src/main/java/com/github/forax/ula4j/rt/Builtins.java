package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.intrinsic.StringIntrinsics;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;

import static java.lang.invoke.MethodType.methodType;

final class Builtins {
  private static final Object[] NO_CAPTURE_ARGS = new Object[0];

  private static final MethodHandles.Lookup LOOKUP;
  static final Map<String, Object> BUILTINS;
  static {
    LOOKUP = MethodHandles.lookup();
    BUILTINS = Map.of(
        "print", newBuiltin("print", List.of("objects"))
    );
  }

  private static PyFunction newBuiltin(String name, List<String> parameters) {
    var code = new PyFunction.Code(name, parameters, false, 0, null, null);
    return new PyFunction(code, NO_CAPTURE_ARGS);
  }

  public static MethodHandle resolve(PyFunction.Code code) {
    var name = code.name;
    return switch(name) {
      case  "print" -> resolve(name, methodType(Object.class, PyFunction.class, Object[].class));
      default -> throw new AssertionError("unknown builtin " + name);
    };
  }

  private static MethodHandle resolve(String name, MethodType methodType) {
    try {
      return LOOKUP.findStatic(Builtins.class, name, methodType);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  // --

  public static Object print(PyFunction func, Object... args) {
    var builder = new StringBuilder();
    var separator = "";
    for (var arg : args) {
      builder.append(separator).append(StringIntrinsics.$str$(arg));
      separator = ", ";
    }
    System.out.println(builder.toString());
    return PyNone.INSTANCE;
  }
}
