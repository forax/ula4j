package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.compiler.Compiler;
import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.intrinsic.TupleIntrinsics;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

public final class PyFunctionTest {

  @Nested
  public final class Call {

    @Test
    public void callWithTwoArguments() {
      var add = (PyFunction) Compiler.run("""
          def add(a, b):
              return a + b
          """).getGlobal("add");

      assertEquals(5L, add.call(2L, 3L));
    }

    @Test
    public void callWithNoArguments() {
      var answer = (PyFunction) Compiler.run("""
          def answer():
              return 42
          """).getGlobal("answer");

      assertEquals(42L, answer.call());
    }

    @Test
    public void callClosureCapturesValue() {
      var makeAdder = (PyFunction) Compiler.run("""
          def make_adder(n):
              def add(x):
                  return x + n
              return add
          """).getGlobal("make_adder");

      var add5 = (PyFunction) makeAdder.call(5L);
      assertEquals(8L, add5.call(3L));
    }

    @Test
    public void callVarargsWithNoExtraArguments() {
      var zero = (PyFunction) Compiler.run("""
          def zero(*args):
              return 0
          """).getGlobal("zero");

      assertEquals(0L, zero.call());
    }

    @Test
    public void callVarargsWithExtraArguments() {
      var first = (PyFunction) Compiler.run("""
          def first(a, *args):
              return a
          """).getGlobal("first");

      assertEquals(1L, first.call(1L, 2L, 3L));
    }

    @Test
    public void callFixedArityWithMissingArgument() {
      var add = (PyFunction) Compiler.run("""
          def add(a, b):
              return a + b
          """).getGlobal("add");

      assertThrows(PyTypeError.class, () -> add.call(1L));
    }

    @Test
    public void callFixedArityWithTooManyArguments() {
      var add = (PyFunction) Compiler.run("""
          def add(a, b):
              return a + b
          """).getGlobal("add");

      assertThrows(PyTypeError.class, () -> add.call(1L, 2L, 3L));
    }

    @Test
    public void callVarargsWithMissingRequiredArgument() {
      var first = (PyFunction) Compiler.run("""
          def first(a, *args):
              return a
          """).getGlobal("first");

      assertThrows(PyTypeError.class, first::call);
    }
  }

  @Nested
  public final class CreateDirectMethodHandle {

    @Test
    public void nullMethodTypeRejected() {
      var identity = (PyFunction) Compiler.run("""
          def identity(x):
              return x
          """).getGlobal("identity");

      assertThrows(NullPointerException.class,
          () -> identity.createDirectMethodHandle(null));
    }

    @Test
    public void tooFewArgumentsRejected() {
      var add = (PyFunction) Compiler.run("""
          def add(a, b):
              return a + b
          """).getGlobal("add");

      var type = MethodType.methodType(Object.class,
          Object.class);
      assertThrows(PyTypeError.class, () -> add.createDirectMethodHandle(type));
    }

    @Test
    public void tooManyArgumentsRejected() {
      var add = (PyFunction) Compiler.run("""
          def add(a, b):
              return a + b
          """).getGlobal("add");

      var type = MethodType.methodType(Object.class,
          Object.class, Object.class, Object.class);
      assertThrows(PyTypeError.class, () -> add.createDirectMethodHandle(type));
    }

    @Test
    public void varargsWithMissingRequiredArgumentRejected() {
      var first = (PyFunction) Compiler.run("""
          def first(a, *args):
              return a
          """).getGlobal("first");

      var type = MethodType.methodType(Object.class);
      assertThrows(PyTypeError.class,
          () -> first.createDirectMethodHandle(type));
    }

    @Test
    public void createDirectMH() throws Throwable {
      var identity = (PyFunction) Compiler.run("""
          def identity(x):
              return x
          """).getGlobal("identity");

      var type = MethodType.methodType(Object.class, Object.class);
      MethodHandle mh = identity.createDirectMethodHandle(type);

      assertEquals(42L, (Object) mh.invokeExact((Object) 42L));
    }

    @Test
    public void createDirectMHWithRestEmpty() throws Throwable {
      var rest = (PyFunction) Compiler.run("""
          def rest(a, *args):
              return args
          """).getGlobal("rest");

      var type = MethodType.methodType(Object.class,
          Object.class);
      MethodHandle mh = rest.createDirectMethodHandle(type);

      assertEquals(TupleIntrinsics.fromArray(),
          (Object) mh.invokeExact((Object) 42L));
    }

    @Test
    public void createDirectMHWithRestOne() throws Throwable {
      var rest = (PyFunction) Compiler.run("""
          def rest(a, *args):
              return args
          """).getGlobal("rest");

      var type = MethodType.methodType(Object.class,
          Object.class, Object.class);
      MethodHandle mh = rest.createDirectMethodHandle(type);

      assertEquals(TupleIntrinsics.fromArray(12L),
          (Object) mh.invokeExact((Object) 42L, (Object) 12L));
    }

    @Test
    public void createDirectMHWithRestTwo() throws Throwable {
      var rest = (PyFunction) Compiler.run("""
          def rest(a, *args):
              return args
          """).getGlobal("rest");

      var type = MethodType.methodType(Object.class,
          Object.class, Object.class, Object.class);
      MethodHandle mh = rest.createDirectMethodHandle(type);

      assertEquals(TupleIntrinsics.fromArray(12L, 5L),
          (Object) mh.invokeExact((Object) 42L, (Object) 12L, (Object) 5L));
    }

    @Test
    public void createDirectMHWithRestThree() throws Throwable {
      var rest = (PyFunction) Compiler.run("""
          def rest(a, *args):
              return args
          """).getGlobal("rest");

      var type = MethodType.methodType(Object.class,
          Object.class, Object.class, Object.class, Object.class);
      MethodHandle mh = rest.createDirectMethodHandle(type);

      assertEquals(TupleIntrinsics.fromArray(12L, 5L, "A"),
          (Object) mh.invokeExact((Object) 42L, (Object) 12L, (Object) 5L, (Object) "A"));
    }

    @Test
    public void createDirectMHWithRestFour() throws Throwable {
      var rest = (PyFunction) Compiler.run("""
          def rest(a, *args):
              return args
          """).getGlobal("rest");

      var type = MethodType.methodType(Object.class,
          Object.class, Object.class, Object.class, Object.class, Object.class);
      MethodHandle mh = rest.createDirectMethodHandle(type);

      assertEquals(TupleIntrinsics.fromArray(12L, 5L, "A", true),
          (Object) mh.invokeExact((Object) 42L, (Object) 12L, (Object) 5L, (Object) "A", (Object) true));
    }

    @Test
    public void createDirectMHWithRestFive() throws Throwable {
      var rest = (PyFunction) Compiler.run("""
          def rest(a, *args):
              return args
          """).getGlobal("rest");

      var type = MethodType.methodType(Object.class,
          Object.class, Object.class, Object.class, Object.class, Object.class, Object.class);
      MethodHandle mh = rest.createDirectMethodHandle(type);

      assertEquals(TupleIntrinsics.fromArray(12L, 5L, "A", true, BigInteger.valueOf(10)),
          (Object) mh.invokeExact((Object) 42L, (Object) 12L, (Object) 5L, (Object) "A", (Object) true, (Object) BigInteger.valueOf(10)));
    }
  }
}