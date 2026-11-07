package com.github.forax.ula4j.compiler;

import com.github.forax.ula4j.ast.UnsupportedSyntaxException;
import com.github.forax.ula4j.rt.error.PyNameError;
import com.github.forax.ula4j.rt.error.PyTypeError;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class BytecodeGeneratorTest {
  private static Object eval(String source, String global) throws Throwable {
    return Compiler.run(source).getGlobal(global);
  }

  @Test
  public void arithmetic() throws Throwable {
    assertEquals(7L, eval("x = 1 + 2 * 3", "x"));
    assertEquals(BigInteger.ONE.shiftLeft(63), eval("x = 9223372036854775807 + 1", "x"));
    assertEquals(3.5, eval("x = 7 / 2", "x"));
    assertEquals(-4L, eval("x = -7 // 2", "x"));
    assertEquals(1024L, eval("x = 2 ** 10", "x"));
    assertEquals(5L, eval("x = 1\nx += 4", "x"));
  }

  @Test
  public void comparisonsAndChains() throws Throwable {
    assertEquals(true, eval("x = 1 < 2 < 3", "x"));
    assertEquals(false, eval("x = 3 > 2 > 2", "x"));
    assertEquals(true, eval("x = 'b' in 'abc'", "x"));
    assertEquals(true, eval("x = 'z' not in 'abc'", "x"));
  }

  @Test
  public void booleanOperatorsReturnOperands() throws Throwable {
    assertEquals(5L, eval("x = 0 or 5", "x"));
    assertEquals(4L, eval("x = 3 and 4", "x"));
    assertEquals(0L, eval("x = 0 and 5", "x"));
    assertEquals(true, eval("x = not 0", "x"));
    assertEquals("a", eval("x = '' or 'a'", "x"));
    assertEquals(2L, eval("x = 1 if 0 else 2", "x"));
  }

  @Test
  public void loops() throws Throwable {
    assertEquals(10L, eval("""
        i = 0
        t = 0
        while i < 5:
            t += i
            i += 1
        """, "t"));
    assertEquals(2L, eval("""
        n = 0
        for c in 'abcd':
            if c == 'c':
                break
            n += 1
        else:
            n = 100
        """, "n"));
    assertEquals(12L, eval("""
        n = 0
        for c in 'ab':
            n += 1
        else:
            n += 10
        """, "n"));
    assertEquals(30L, eval("""
        i = 0
        s = 0
        while True:
            i += 1
            if i > 10:
                break
            if i % 2:
                continue
            s += i
        """, "s"));
  }

  @Test
  public void functionsAndRecursion() throws Throwable {
    assertEquals(610L, eval("""
        def fib(n):
            if n < 2:
                return n
            return fib(n - 1) + fib(n - 2)
        r = fib(15)
        """, "r"));
  }

  @Test
  public void closuresShareTheBodyButNotTheCaptures() throws Throwable {
    assertEquals(211L, eval("""
        def make_adder(x):
            def add(y):
                return x + y
            return add
        a = make_adder(1)
        b = make_adder(10)
        r = a(1) * 100 + b(1)
        """, "r"));
  }

  @Test
  public void nestedFunctionCallsItselfThroughSelf() throws Throwable {
    assertEquals(120L, eval("""
        def outer(n):
            def fact(k):
                return 1 if k <= 1 else k * fact(k - 1)
            return fact(n)
        r = outer(5)
        """, "r"));
  }

  @Test
  public void restParameterIsATuple() throws Throwable {
    var source = """
        def count(a, *rest):
            n = 0
            for x in rest:
                n += 1
            return a * 10 + n
        r = count(4, 'x', 'y', 'z')
        s = count(7)
        """;
    assertEquals(43L, eval(source, "r"));
    assertEquals(70L, eval(source, "s"));
  }

  @Test
  public void runtimeErrors() {
    var arity = assertThrows(PyTypeError.class, () -> Compiler.run("def f(a, b):\n    return a\nf(1)\n"));
    assertEquals("f() missing 1 required positional argument: 'b'", arity.getMessage());
    var tooMany = assertThrows(PyTypeError.class, () -> Compiler.run("def f(a):\n    return a\nf(1, 2)\n"));
    assertEquals("f() takes 1 positional argument but 2 were given", tooMany.getMessage());
    var name = assertThrows(PyNameError.class, () -> Compiler.run("x = y"));
    assertEquals("name 'y' is not defined", name.getMessage());
    assertThrows(PyTypeError.class, () -> Compiler.run("x = 1\nx()\n"));
  }

  @Test
  public void notYetSupportedConstructsAreRejected() {
    assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = [1]"));
    assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("a, b = 1, 2"));
  }
}