package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.ast.ASTBuilder;
import com.github.forax.ula4j.compiler.Compiler;
import com.github.forax.ula4j.rt.error.PyNameError;
import com.github.forax.ula4j.rt.error.PyTypeError;
import com.github.forax.ula4j.rt.error.PyZeroDivisionError;
import com.github.forax.ula4j.rt.intrinsic.TupleIntrinsics;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.UndeclaredThrowableException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests of the function / closure machinery of FunctionSupport:
/// `def` (DefCallSite), value calls (FunctionCallSite: monomorphic, polymorphic, megamorphic),
/// captured values and rest parameters.
///
/// Vocabulary used below:
///  - "distinct callees":  functions with different bodies, called through one call site
///  - "same code":         several closures created by one `def` (same body, different captured values)
///  - "warm":              each function is called once directly (from its own call site) before it reaches the
///                         shared call site, so the function is already compiled and bound
///  - "cold":              the first call of a function is the one done through the shared call site
public final class FunctionSupportTest {

  private static ModuleContext run(String source) {
    return Compiler.run(source);
  }

  private static Object eval(String source, String global) {
    return Compiler.run(source).getGlobal(global);
  }

  /// Compiles and runs a script in an existing module: its globals and the call sites inside
  /// the already defined functions are kept.
  private static void runIn(ModuleContext ctx, String source) {
    var main = Compiler.compile(ctx, ASTBuilder.createScript(source));
    try {
      main.invokeExact();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new UndeclaredThrowableException(e);
    }
  }

  private static PyTuple tuple(Object... items) {
    return TupleIntrinsics.fromArray(items);
  }

  private static String csv(int n, IntFunction<String> element) {
    return IntStream.range(0, n).mapToObj(element).collect(Collectors.joining(", "));
  }

  private static String assertTypeErrorMessage(String source) {
    return assertThrows(PyTypeError.class, () -> Compiler.run(source)).getMessage();
  }

  private static void assertGlobal(ModuleContext module, String name, Object expected) {
    assertEquals(expected, module.getGlobal(name), name);
  }


  // -- sources generators

  /// n functions with a different body each, one shared call site (`apply`), three rounds
  /// (forward, backward, forward). `r<round>_<i>` is `100 + i`.
  private static String distinctCallees(int n, boolean warm) {
    var builder = new StringBuilder();
    for (var i = 0; i < n; i++) {
      builder.append("def f").append(i).append("(x):\n    return x + ").append(i).append('\n');
    }
    builder.append("""
        def apply(fn, v):
            return fn(v)
        """);
    if (warm) {
      for (var i = 0; i < n; i++) {
        builder.append('w').append(i).append(" = f").append(i).append("(0)\n");
      }
    }
    for (var round = 0; round < 3; round++) {
      for (var j = 0; j < n; j++) {
        var i = round == 1 ? n - 1 - j : j;
        builder.append('r').append(round).append('_').append(i)
            .append(" = apply(f").append(i).append(", 100)\n");
      }
    }
    return builder.toString();
  }

  private static void checkDistinctCallees(int n, boolean warm) {
    var module = run(distinctCallees(n, warm));
    for (var round = 0; round < 3; round++) {
      for (var i = 0; i < n; i++) {
        assertGlobal(module, "r" + round + "_" + i, 100L + i);
      }
    }
    if (warm) {
      for (var i = 0; i < n; i++) {
        assertGlobal(module, "w" + i, (long) i);
      }
    }
  }

  /// n functions with a different body each, selected by a `pick(i)` function (a single call site sees
  /// a mix of callees in a loop). The total is `sum(1 + 10 * (i % n))`.
  private static String picker(int n, int iterations, boolean warm) {
    var builder = new StringBuilder();
    for (var k = 0; k < n; k++) {
      builder.append("def f").append(k).append("(x):\n    return x + ").append(10 * k).append('\n');
    }
    builder.append("""
        def apply(fn, v):
            return fn(v)
        """);
    builder.append("def pick(i):\n");
    if (n == 1) {
      builder.append("    return f0\n");
    } else {
      builder.append("    k = i % ").append(n).append('\n');
      for (var k = 0; k < n - 1; k++) {
        builder.append("    ").append(k == 0 ? "if" : "elif").append(" k == ").append(k).append(":\n");
        builder.append("        return f").append(k).append('\n');
      }
      builder.append("    else:\n        return f").append(n - 1).append('\n');
    }
    if (warm) {
      for (var k = 0; k < n; k++) {
        builder.append('w').append(k).append(" = f").append(k).append("(0)\n");
      }
    }
    builder.append("i = 0\ntotal = 0\nwhile i < ").append(iterations)
        .append(":\n    total += apply(pick(i), 1)\n    i += 1\n");
    return builder.toString();
  }

  private static void checkPicker(int n, int iterations, boolean warm) {
    var total = 0L;
    for (var i = 0; i < iterations; i++) {
      total += 1 + 10L * (i % n);
    }
    assertEquals(total, eval(picker(n, iterations, warm), "total"));
  }

  /// n distinct makers; each maker creates exactly one closure (so n distinct codes, n instances).
  private static String distinctClosures(int n, boolean warm) {
    var builder = new StringBuilder();
    for (var i = 0; i < n; i++) {
      builder.append("def mk").append(i).append("(k):\n")
          .append("    def h(x):\n")
          .append("        return x + k + ").append(i).append('\n')
          .append("    return h\n");
    }
    builder.append("""
        def apply(fn, v):
            return fn(v)
        """);
    for (var i = 0; i < n; i++) {
      builder.append('c').append(i).append(" = mk").append(i).append('(').append(1000 * i).append(")\n");
    }
    if (warm) {
      for (var i = 0; i < n; i++) {
        builder.append('w').append(i).append(" = c").append(i).append("(0)\n");
      }
    }
    for (var i = 0; i < n; i++) {
      builder.append('r').append(i).append(" = apply(c").append(i).append(", 100)\n");
    }
    return builder.toString();
  }

  private static void checkDistinctClosures(int n, boolean warm) {
    var module = run(distinctClosures(n, warm));
    for (var i = 0; i < n; i++) {
      assertGlobal(module, "r" + i, 100L + 1000L * i + i);
    }
  }

  /// n closures made by the same `def` (same code, different captured values) called through one site.
  /// `r<round>_<i>` is `1 + 1000 * i`.
  private static String sameCode(int n) {
    var builder = new StringBuilder();
    builder.append("""
        def make(k):
            def add(x):
                return x + k
            return add
        def apply(fn, v):
            return fn(v)
        """);
    for (var i = 0; i < n; i++) {
      builder.append('c').append(i).append(" = make(").append(1000 * i).append(")\n");
    }
    for (var round = 0; round < 3; round++) {
      for (var j = 0; j < n; j++) {
        var i = round == 1 ? n - 1 - j : j;
        builder.append('r').append(round).append('_').append(i)
            .append(" = apply(c").append(i).append(", 1)\n");
      }
    }
    return builder.toString();
  }

  /// The same function is redefined n times; `g` always calls the current global `f`.
  private static String rebinding(int n, boolean warm) {
    var builder = new StringBuilder("""
        def g(v):
            return f(v)
        """);
    for (var k = 0; k < n; k++) {
      builder.append("""
          def f(x):
              return x +\s""").append(k).append('\n');
      if (warm) {
        builder.append('w').append(k).append(" = f(0)\n");
      }
      builder.append('r').append(k).append(" = g(100)\n");
    }
    return builder.toString();
  }

  private record Case(String name, Object expected) {}

  private static final Case PLAIN = new Case("plain", 123L);
  private static final Case REVERSED = new Case("reversed3", 321L);
  private static final Case REST0 = new Case("rest0", tuple(1L, 2L, 3L));
  private static final Case REST1 = new Case("rest1", tuple(2L, 3L));
  private static final Case REST2 = new Case("rest2", tuple(3L));
  private static final Case REST3 = new Case("rest3", tuple());
  private static final Case HEAD3 = new Case("head3", 6L);

  private static String kindsSource(List<Case> cases, boolean warm) {
    var builder = new StringBuilder("""
        def apply3(fn, a, b, c):
            return fn(a, b, c)
        def plain(a, b, c):
            return a * 100 + b * 10 + c
        def reversed3(a, b, c):
            return c * 100 + b * 10 + a
        def rest0(*r):
            return r
        def rest1(a, *r):
            return r
        def rest2(a, b, *r):
            return r
        def rest3(a, b, c, *r):
            return r
        def head3(a, b, c, *r):
            return a + b + c
        """);
    if (warm) {
      for (var c : cases) {
        builder.append("w_").append(c.name()).append(" = ").append(c.name()).append("(1, 2, 3)\n");
      }
    }
    for (var round = 0; round < 3; round++) {
      for (var j = 0; j < cases.size(); j++) {
        var c = cases.get(round == 1 ? cases.size() - 1 - j : j);
        builder.append('r').append(round).append('_').append(c.name())
            .append(" = apply3(").append(c.name()).append(", 1, 2, 3)\n");
      }
    }
    return builder.toString();
  }

  private static void checkKinds(List<Case> cases, boolean warm) {
    var module = run(kindsSource(cases, warm));
    for (var round = 0; round < 3; round++) {
      for (var c : cases) {
        assertGlobal(module, "r" + round + "_" + c.name(), c.expected());
      }
    }
  }

  /// Eight distinct functions, already compiled and called through `apply`, so that the site of `apply`
  /// is megamorphic. Every `f<i>` returns `x + i`.
  private static ModuleContext megamorphicSession() {
    var ctx = new ModuleContext();
    var builder = new StringBuilder();
    for (var i = 0; i < 8; i++) {
      builder.append("def f").append(i).append("(x):\n    return x + ").append(i).append('\n');
    }
    builder.append("""
        def apply(fn, v):
            return fn(v)
        """);
    for (var i = 0; i < 8; i++) {
      builder.append('w').append(i).append(" = f").append(i).append("(0)\n");
    }
    for (var i = 0; i < 8; i++) {
      builder.append('p').append(i).append(" = apply(f").append(i).append(", 100)\n");
    }
    runIn(ctx, builder.toString());
    for (var i = 0; i < 8; i++) {
      assertGlobal(ctx, "p" + i, 100L + i);
    }
    return ctx;
  }


  /*private enum CallShape {
    POSITIONAL, KEYWORDS, MIXED;

    String invocation() {
      return switch (this) {
        case POSITIONAL -> "fn(1, 2, 3)";
        case KEYWORDS -> "fn(c=3, a=1, b=2)";
        case MIXED -> "fn(1, c=3, b=2)";
      };
    }

    String invocation(String callee) {
      return invocation().replace("fn(", callee + "(");
    }

    String parameters(int i) {
      return switch (this) {
        case POSITIONAL -> "a, b, c";
        case KEYWORDS -> switch (i % 6) {
          case 0 -> "a, b, c";
          case 1 -> "c, a, b";
          case 2 -> "b, c, a";
          case 3 -> "c, b, a";
          case 4 -> "b, a, c";
          default -> "a, c, b";
        };
        // Keep 'a' first because the first argument is positional.
        case MIXED -> i % 2 == 0 ? "a, b, c" : "a, c, b";
      };
    }
  }*/


  private enum CallShape {
    POSITIONAL {
      @Override String invocation() {
        return "fn(1, 2, 3)";
      }

      @Override String parameters(int i) {
        return "a, b, c";
      }
    },

    KEYWORDS {
      @Override String invocation() {
        return "fn(c=3, a=1, b=2)";
      }

      @Override String parameters(int i) {
        return switch (i % 6) {
          case 0 -> "a, b, c";
          case 1 -> "c, a, b";
          case 2 -> "b, c, a";
          case 3 -> "c, b, a";
          case 4 -> "b, a, c";
          default -> "a, c, b";
        };
      }
    },

    MIXED {
      @Override String invocation() {
        return "fn(1, c=3, b=2)";
      }

      @Override String parameters(int i) {
        // Keep 'a' first because the first argument is positional.
        return i % 2 == 0 ? "a, b, c" : "a, c, b";
      }
    };

    abstract String invocation();
    abstract String parameters(int i);

    String invocation(String callee) {
      return invocation().replace("fn(", callee + "(");
    }
  }



  private static String keywordDispatchSource(int n, boolean captured, CallShape shape, boolean warm) {
    var builder = new StringBuilder();

    // Create n functions with the same parameter names,
    // but potentially different declaration orders.
    for (var i = 0; i < n; i++) {
      var parameters = shape.parameters(i);

      if (captured) {
        builder.append("def make").append(i).append("(k):\n")
            .append("    def f(").append(parameters).append("):\n")
            .append("        return a * 100 + b * 10 + c + k\n")
            .append("    return f\n")
            .append("c").append(i).append(" = make").append(i).append('(')
            .append(1000 * i).append(")\n");
      } else {
        builder.append("def f").append(i)
            .append("(").append(parameters).append("):\n")
            .append("    return a * 100 + b * 10 + c + ")
            .append(1000 * i).append('\n');
      }
    }

    // One shared call site. This is the important part.
    builder.append("def apply(fn):\n")
        .append("    return ").append(shape.invocation()).append('\n');

    // Warm the function bodies before using the shared call site.
    if (warm) {
      for (var i = 0; i < n; i++) {
        var callee = captured ? "c" + i : "f" + i;
        builder.append("w").append(i).append(" = ")
            .append(shape.invocation(callee)).append('\n');
      }
    }

    // Three rounds: forward, backward, forward.
    for (var round = 0; round < 3; round++) {
      for (var j = 0; j < n; j++) {
        var i = round == 1 ? n - 1 - j : j;
        var callee = captured ? "c" + i : "f" + i;

        builder.append('r').append(round).append('_').append(i)
            .append(" = apply(").append(callee).append(")\n");
      }
    }

    return builder.toString();
  }

  private static void checkKeywordDispatch(int n, boolean captured, CallShape shape, boolean warm
  ) {
    var module = run(keywordDispatchSource(n, captured, shape, warm));

    for (var round = 0; round < 3; round++) {
      for (var i = 0; i < n; i++) {
        assertGlobal(module, "r" + round + "_" + i, 123L + 1000L * i);
      }
    }

    if (warm) {
      for (var i = 0; i < n; i++) {
        assertGlobal(module, "w" + i, 123L + 1000L * i);
      }
    }
  }


  @Nested
  public final class Definition {

    @Test
    public void functionValueExposesItsSignature() {
      var module = run("""
          def f(a, b):
              return a
          """);
      var f = assertInstanceOf(PyFunction.class, module.getGlobal("f"));
      assertEquals("f", f.name());
      assertEquals(List.of("a", "b"), f.parameters());
      assertEquals(2, f.arity());
      assertFalse(f.varargs());
      assertEquals("<function f>", f.toString());
    }

    @Test
    public void restFunctionValueExposesItsSignature() {
      var module = run("""
          def f(a, *r):
              return a
          """);
      var f = assertInstanceOf(PyFunction.class, module.getGlobal("f"));
      assertEquals(List.of("a"), f.parameters());
      assertEquals(1, f.arity());
      assertTrue(f.varargs());
    }

    @Test
    public void functionWithOnlyARestParameter() {
      var module = run("""
          def f(*r):
              return r
          """);
      var f = assertInstanceOf(PyFunction.class, module.getGlobal("f"));
      assertEquals(0, f.arity());
      assertTrue(f.parameters().isEmpty());
      assertTrue(f.varargs());
    }

    @Test
    public void definingAFunctionDoesNotRunItsBody() {
      var module = run("""
          def boom():
              return 1 // 0
          def missing():
              return nowhere
          ok = 1
          """);
      assertGlobal(module, "ok", 1L);
    }

    @Test
    public void nestedNonCapturingDefShareTheSameFunction() {
      // This may change in the future
      var module = run("""
          def make():
              def inner():
                  return 1
              return inner
          a = make()
          b = make()
          r = a() + b()
          """);
      assertSame(module.getGlobal("a"), module.getGlobal("b"));
      assertGlobal(module, "r", 2L);
    }

    @Test
    public void nestedFunctionsWithTheSameNameInDifferentFunctionsDoNotClash() {
      var source = """
          def a():
              def inner():
                  return 1
              return inner()
          def b():
              def inner():
                  return 2
              return inner()
          r = a() * 10 + b()
          """;
      assertEquals(12L, eval(source, "r"));
    }

    @Test
    public void defInBothBranchesOfAnIf() {
      var module = run("""
          def pick(c):
              if c:
                  def g():
                      return 1
              else:
                  def g():
                      return 2
              return g()
          a = pick(True)
          b = pick(False)
          """);
      assertGlobal(module, "a", 1L);
      assertGlobal(module, "b", 2L);
    }

    @Test
    public void defInsideAModuleLevelLoop() {
      var source = """
          total = 0
          for c in 'abc':
              def f(x):
                  return x
              total += f(1)
          """;
      assertEquals(3L, eval(source, "total"));
    }

    @Test
    public void aFunctionValueCanBeAliasedAndPassedAround() {
      var source = """
          def f(x):
              return x + 1
          g = f
          h = g
          r = h(1)
          """;
      var module = run(source);
      assertSame(module.getGlobal("f"), module.getGlobal("h"));
      assertGlobal(module, "r", 2L);
    }
  }


  @Nested
  public final class MonomorphicCalls {

    @Test
    public void repeatedCallsThroughOneSite() {
      var source = """
          def inc(x):
              return x + 1
          def apply(fn, v):
              return fn(v)
          total = 0
          for c in 'abcdefghijklmnopqrstuvwxyz':
              total = apply(inc, total)
          """;
      assertEquals(26L, eval(source, "total"));
    }

    @Test
    public void longLoopThroughOneSite() {
      var source = """
          def inc(x):
              return x + 1
          def apply(fn, v):
              return fn(v)
          i = 0
          total = 0
          while i < 500:
              total = apply(inc, total)
              i += 1
          """;
      assertEquals(500L, eval(source, "total"));
    }

    @ParameterizedTest(name = "arity {0}")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 7, 8, 16, 40, 100})
    public void fixedArity(int n) {
      var params = csv(n, i -> "p" + i);
      var body = n == 0 ? "0" : csv(n, i -> "p" + i + " * " + (i + 1)).replace(", ", " + ");
      var args = csv(n, i -> String.valueOf(i + 1));
      var source = "def f(" + params + "):\n    return " + body + "\nr = f(" + args + ")\n";
      assertEquals(n * (n + 1L) * (2L * n + 1) / 6, eval(source, "r"));
    }

    @Test
    public void calleeIsTheResultOfAnotherCall() {
      var source = """
          def adder(k):
              def add(x):
                  return x + k
              return add
          r = adder(3)(4)
          """;
      assertEquals(7L, eval(source, "r"));
    }

    @Test
    public void callsAsArguments() {
      var source = """
          def f(x):
              return x + 1
          r = f(f(f(f(0))))
          """;
      assertEquals(4L, eval(source, "r"));
    }

    @Test
    public void calleeChosenByAConditionalExpression() {
      var source = """
          def inc(x):
              return x + 1
          def dbl(x):
              return x * 2
          a = (inc if True else dbl)(5)
          b = (inc if False else dbl)(5)
          """;
      var module = run(source);
      assertGlobal(module, "a", 6L);
      assertGlobal(module, "b", 10L);
    }

    @Test
    public void functionWithoutReturnYieldsNone() {
      assertSame(PyNone.INSTANCE, eval("""
          def f():
              pass
          r = f()
          """, "r"));
    }

    @Test
    public void parameterShadowsAGlobalFunction() {
      var source = """
          def f():
              return 1
          def g(f):
              return f()
          def h():
              return 2
          r = g(h)
          """;
      assertEquals(2L, eval(source, "r"));
    }

    @Test
    public void functionCanCallItsArgumentTwice() {
      var source = """
          def twice(fn, x):
              return fn(fn(x))
          def inc(x):
              return x + 1
          r = twice(inc, 10)
          """;
      assertEquals(12L, eval(source, "r"));
    }

    @Test
    public void foldWithOneCallee() {
      var source = """
          def fold(fn, s, acc):
              for c in s:
                  acc = fn(acc, c)
              return acc
          def cat(a, c):
              return a + c
          s = fold(cat, 'hello', '')
          """;
      assertEquals("hello", eval(source, "s"));
    }

    @Test
    public void recursion() {
      var source = """
          def fib(n):
              if n < 2:
                  return n
              return fib(n - 1) + fib(n - 2)
          r = fib(15)
          """;
      assertEquals(610L, eval(source, "r"));
    }

    @Test
    public void mutualRecursionThroughGlobals() {
      var module = run("""
          def is_even(n):
              if n == 0:
                  return True
              return is_odd(n - 1)
          def is_odd(n):
              if n == 0:
                  return False
              return is_even(n - 1)
          a = is_even(40)
          b = is_odd(40)
          c = is_even(7)
          """);
      assertGlobal(module, "a", true);
      assertGlobal(module, "b", false);
      assertGlobal(module, "c", false);
    }

    @Test
    public void recursionPassingExtraArgumentsToARestParameter() {
      var source = """
          def depth(n, *r):
              if n == 0:
                  return 0
              return 1 + depth(n - 1, 7, 8)
          r = depth(5)
          """;
      assertEquals(5L, eval(source, "r"));
    }
  }


  @Nested
  public final class PolymorphicCalls {

    @ParameterizedTest(name = "{0} distinct callees, warm")
    @ValueSource(ints = {1, 2, 3, 4})
    public void warmDistinctCallees(int n) {
      checkDistinctCallees(n, true);
    }

    @ParameterizedTest(name = "{0} distinct callees, cold")
    @ValueSource(ints = {1, 2, 3, 4})
    public void coldDistinctCallees(int n) {
      checkDistinctCallees(n, false);
    }

    @ParameterizedTest(name = "{0} callees mixed in a loop, warm")
    @ValueSource(ints = {1, 2, 3, 4})
    public void warmPicker(int n) {
      checkPicker(n, 400, true);
    }

    @ParameterizedTest(name = "{0} callees mixed in a loop, cold")
    @ValueSource(ints = {1, 2, 3, 4})
    public void coldPicker(int n) {
      checkPicker(n, 400, false);
    }

    @Test
    public void foldWithSeveralCallees() {
      var source = """
          def fold(fn, s, acc):
              for c in s:
                  acc = fn(acc, c)
              return acc
          def cat(a, c):
              return a + c
          def count(a, c):
              return a + 1
          def last(a, c):
              return c
          s = fold(cat, 'hello', '')
          n = fold(count, 'hello', 0)
          l = fold(last, 'hello', '')
          s2 = fold(cat, 'abc', '')
          """;
      var module = run(source);
      assertGlobal(module, "s", "hello");
      assertGlobal(module, "n", 5L);
      assertGlobal(module, "l", "o");
      assertGlobal(module, "s2", "abc");
    }

    @Test
    public void aCalleeSeenAgainAfterOthersStillWorks() {
      var module = run("""
          def a(x):
              return x + 1
          def b(x):
              return x + 2
          def c(x):
              return x + 3
          def apply(fn, v):
              return fn(v)
          r1 = apply(a, 0)
          r2 = apply(b, 0)
          r3 = apply(a, 0)
          r4 = apply(c, 0)
          r5 = apply(b, 0)
          r6 = apply(a, 0)
          """);
      assertGlobal(module, "r1", 1L);
      assertGlobal(module, "r2", 2L);
      assertGlobal(module, "r3", 1L);
      assertGlobal(module, "r4", 3L);
      assertGlobal(module, "r5", 2L);
      assertGlobal(module, "r6", 1L);
    }

    @Test
    public void plainAndRestFunctionsAtOneSite() {
      checkKinds(List.of(PLAIN, REST0, REST1, REST2), false);
    }

    @Test
    public void plainAndRestFunctionsAtOneSiteWarm() {
      checkKinds(List.of(PLAIN, REST0, REST1, REST2), true);
    }

    @Test
    public void distinctClosuresWithDistinctCodes() {
      checkDistinctClosures(4, true);
      checkDistinctClosures(4, false);
    }
  }


  /// More than four distinct codes at one site: the site gives up its guard chain.
  @Nested
  public final class MegamorphicCalls {

    @ParameterizedTest(name = "{0} distinct callees, warm")
    @ValueSource(ints = {5, 6, 7, 8, 12, 16, 32})
    public void warmDistinctCallees(int n) {
      checkDistinctCallees(n, true);
    }

    /// The first call of most of these functions is made by the megamorphic site itself.
    @ParameterizedTest(name = "{0} distinct callees, cold")
    @ValueSource(ints = {5, 6, 7, 8, 12, 16, 32})
    public void coldDistinctCallees(int n) {
      checkDistinctCallees(n, false);
    }

    @ParameterizedTest(name = "{0} callees mixed in a loop, warm")
    @ValueSource(ints = {5, 6, 8, 12})
    public void warmPicker(int n) {
      checkPicker(n, 600, true);
    }

    @ParameterizedTest(name = "{0} callees mixed in a loop, cold")
    @ValueSource(ints = {5, 6, 8, 12})
    public void coldPicker(int n) {
      checkPicker(n, 600, false);
    }

    @ParameterizedTest(name = "{0} distinct closures, warm")
    @ValueSource(ints = {5, 6, 9, 16})
    public void warmDistinctClosures(int n) {
      checkDistinctClosures(n, true);
    }

    @ParameterizedTest(name = "{0} distinct closures, cold")
    @ValueSource(ints = {5, 6, 9, 16})
    public void coldDistinctClosures(int n) {
      checkDistinctClosures(n, false);
    }

    @Test
    public void sevenKindsOfPlainAndRestFunctionsWarm() {
      checkKinds(List.of(PLAIN, REVERSED, REST0, REST1, REST2, REST3, HEAD3), true);
    }

    @Test
    public void sevenKindsOfPlainAndRestFunctionsCold() {
      checkKinds(List.of(PLAIN, REVERSED, REST0, REST1, REST2, REST3, HEAD3), false);
    }

    @Test
    public void megamorphicSiteKeepsWorkingForEveryCalleeAfterwards() {
      var ctx = megamorphicSession();
      runIn(ctx, "q = apply(f7, 1) + apply(f0, 1) + apply(f4, 1) + apply(f2, 1)\n");
      assertGlobal(ctx, "q", (1L + 7) + (1L + 0) + (1L + 4) + (1L + 2));
    }

    @Test
    public void megamorphicSiteStillReportsWrongArity() {
      var ctx = megamorphicSession();
      var e = assertThrows(PyTypeError.class,
          () -> runIn(ctx, """
              def two(a, b):
                  return a
              x = apply(two, 1)
              """));
      assertEquals("two() missing 1 required positional argument: 'b'", e.getMessage());

      runIn(ctx, """
          ok = apply(f3, 1)
          """);
      assertGlobal(ctx, "ok", 4L);
    }

    @Test
    public void megamorphicSiteStillReportsNonCallables() {
      var ctx = megamorphicSession();
      var e = assertThrows(PyTypeError.class, () -> runIn(ctx, "x = apply(5, 1)\n"));
      assertEquals("'int' object is not callable", e.getMessage());

      runIn(ctx, """
          ok = apply(f5, 1)
          """);
      assertGlobal(ctx, "ok", 6L);
    }

    @Test
    public void megamorphicSiteWithRestFunctionAndTooFewArguments() {
      var ctx = megamorphicSession();
      var e = assertThrows(PyTypeError.class,
          () -> runIn(ctx, """
              def r(a, b, *rest):
                  return a
              x = apply(r, 1)
              """));
      assertEquals("r() missing 1 required positional argument: 'b'", e.getMessage());
    }

    @Test
    public void bodyExceptionsPropagateFromAMegamorphicSite() {
      var ctx = megamorphicSession();
      runIn(ctx, """
          def inv(x):
              return 100 // x
          w = inv(1)
          """);
      assertThrows(PyZeroDivisionError.class, () -> runIn(ctx, "x = apply(inv, 0)\n"));

      runIn(ctx, """
          ok = apply(inv, 4)
          """);
      assertGlobal(ctx, "ok", 25L);
    }
  }


  @Nested
  public final class RestParameters {

    @ParameterizedTest(name = "{0} extra arguments")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 10, 50})
    public void onlyARestParameter(int n) {
      var source = "def f(*r):\n    return r\nt = f(" + csv(n, i -> String.valueOf(i + 1)) + ")\n";
      var expected = new Object[n];
      for (var i = 0; i < n; i++) {
        expected[i] = i + 1L;
      }
      assertEquals(tuple(expected), eval(source, "t"));
    }

    @ParameterizedTest(name = "{0} extra arguments")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 9, 30})
    public void fixedParametersThenARestParameter(int extras) {
      var args = csv(2 + extras, i -> String.valueOf(i + 1));
      var source = """
          def f(a, b, *r):
              return r
          def head(a, b, *r):
              return a * 100 + b
          """
          + "t = f(" + args + ")\nh = head(" + args + ")\n";
      var module = run(source);
      var expected = new Object[extras];
      for (var i = 0; i < extras; i++) {
        expected[i] = i + 3L;
      }
      assertGlobal(module, "t", tuple(expected));
      assertGlobal(module, "h", 102L);
    }

    @Test
    public void restTupleCanBeIterated() {
      var module = run("""
          def total(*r):
              s = 0
              for x in r:
                  s += x
              return s
          t0 = total()
          t1 = total(5)
          t2 = total(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
          """);
      assertGlobal(module, "t0", 0L);
      assertGlobal(module, "t1", 5L);
      assertGlobal(module, "t2", 55L);
    }

    @Test
    public void sameFunctionCalledFromSitesWithDifferentArgumentCounts() {
      var module = run("""
          def f(a, *r):
              return r
          t1 = f(1)
          t2 = f(1, 2)
          t3 = f(1, 2, 3)
          t4 = f(1, 2, 3, 4)
          t5 = f(1, 2, 3, 4, 5)
          t6 = f(1, 2, 3, 4, 5, 6)
          """);
      assertGlobal(module, "t1", tuple());
      assertGlobal(module, "t2", tuple(2L));
      assertGlobal(module, "t3", tuple(2L, 3L));
      assertGlobal(module, "t4", tuple(2L, 3L, 4L));
      assertGlobal(module, "t5", tuple(2L, 3L, 4L, 5L));
      assertGlobal(module, "t6", tuple(2L, 3L, 4L, 5L, 6L));
    }

    @Test
    public void restTupleIsNewForEveryCall() {
      var module = run("""
          def f(*r):
              return r
          a = f(1, 2)
          b = f(1, 2)
          c = f(3)
          """);
      assertGlobal(module, "a", tuple(1L, 2L));
      assertGlobal(module, "b", tuple(1L, 2L));
      assertGlobal(module, "c", tuple(3L));
    }

    @Test
    public void restValuesKeepTheirTypes() {
      var module = run("""
          def f(*r):
              return r
          t = f(1, 2.5, 'a', True, None, 10 ** 30)
          """);
      assertGlobal(module, "t",
          tuple(1L, 2.5, "a", true, PyNone.INSTANCE, BigInteger.TEN.pow(30)));
    }

    @Test
    public void missingFixedArgumentOfARestFunction() {
      assertEquals("f() missing 1 required positional argument: 'a'",
          assertTypeErrorMessage("""
              def f(a, *r):
                  return a
              f()
              """));
      assertEquals("f() missing 1 required positional argument: 'b'",
          assertTypeErrorMessage("""
              def f(a, b, *r):
                  return a
              f(1)
              """));
      assertEquals("f() missing 2 required positional arguments: 'a' and 'b'",
          assertTypeErrorMessage("""
              def f(a, b, *r):
                  return a
              f()
              """));
    }

    @Test
    public void restFunctionAndPlainFunctionAtOneSiteWithMatchingArity() {
      var module = run("""
          def apply2(fn, a, b):
              return fn(a, b)
          def plain(a, b):
              return a + b
          def with_rest(a, b, *r):
              return a * b
          def only_rest(*r):
              return r
          x = apply2(plain, 3, 4)
          y = apply2(with_rest, 3, 4)
          z = apply2(only_rest, 3, 4)
          x2 = apply2(plain, 5, 6)
          z2 = apply2(only_rest, 5, 6)
          """);
      assertGlobal(module, "x", 7L);
      assertGlobal(module, "y", 12L);
      assertGlobal(module, "z", tuple(3L, 4L));
      assertGlobal(module, "x2", 11L);
      assertGlobal(module, "z2", tuple(5L, 6L));
    }

    @Test
    public void restParameterIsCapturedByANestedFunction() {
      var source = """
          def keep(*r):
              def get():
                  return r
              return get
          t = keep(1, 2, 3)()
          e = keep()()
          """;
      var module = run(source);
      assertGlobal(module, "t", tuple(1L, 2L, 3L));
      assertGlobal(module, "e", tuple());
    }

    @Test
    public void nestedFunctionsWithRestParametersAtEveryLevel() {
      var source = """
          def outer(a, *ra):
              def mid(b, *rb):
                  def inner(c, *rc):
                      return a * 100 + b * 10 + c
                  return inner(3, 9, 9)
              return mid(2, 8)
          r = outer(1, 7)
          """;
      assertEquals(123L, eval(source, "r"));
    }

    @Test
    public void keywordArgumentsWithRestParameter() {
      var module = run("""
          def make(k):
              def collect(a, b, c, *rest):
                  total = k + a * 100 + b * 10 + c
                  for x in rest:
                      total += x
                  return total
              return collect
          
          def apply(fn):
              return fn(1, c=3, b=2)
          
          def plain(a, b, c, *rest):
              total = a * 100 + b * 10 + c
              for x in rest:
                  total += x
              return total
          
          r0 = apply(plain)
          r1 = apply(make(1000))
          """);

      assertGlobal(module, "r0", 123L);
      assertGlobal(module, "r1", 1123L);
    }
  }


  @Nested
  public final class CapturedValues {

    @ParameterizedTest(name = "{0} captured values")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 8, 20, 60, 120})
    public void manyCapturedValues(int k) {
      var params = csv(k, i -> "c" + i);
      var sum = "x" + IntStream.range(0, k).mapToObj(i -> " + c" + i).collect(Collectors.joining());
      var args = csv(k, i -> String.valueOf(i + 1));
      var source = "def make(" + params + "):\n    def get(x):\n        return " + sum + "\n    return get\n"
          + "f = make(" + args + ")\nr1 = f(1000)\nr2 = f(2000)\n";
      var module = run(source);
      var captured = k * (k + 1L) / 2;
      assertGlobal(module, "r1", 1000L + captured);
      assertGlobal(module, "r2", 2000L + captured);
    }

    @Test
    public void capturedValuesKeepTheirTypes() {
      var module = run("""
          def box(v):
              def get():
                  return v
              return get
          s = box('abc')()
          f = box(2.5)()
          b = box(True)()
          n = box(None)()
          big = box(123456789012345678901234567890)()
          same = box(box)()
          """);
      assertGlobal(module, "s", "abc");
      assertGlobal(module, "f", 2.5);
      assertGlobal(module, "b", true);
      assertSame(PyNone.INSTANCE, module.getGlobal("n"));
      assertGlobal(module, "big", new BigInteger("123456789012345678901234567890"));
      assertSame(module.getGlobal("box"), module.getGlobal("same"));
    }

    @Test
    public void capturedParameterAndCapturedLocalTogether() {
      var source = """
          def make(base):
              extra = base * 2
              def compute(value):
                  return base + extra + value
              return compute
          f = make(3)
          r = f(4)
          """;
      assertEquals(13L, eval(source, "r"));
    }

    @Test
    public void captureTravelsThroughSeveralLevels() {
      var source = """
          def outer(a):
              def mid(b):
                  def inner(c):
                      return a * 100 + b * 10 + c
                  return inner
              return mid
          r = outer(1)(2)(3)
          """;
      assertEquals(123L, eval(source, "r"));
    }

    @Test
    public void closuresOfOneDefKeepIndependentCapturesAtSeparateSites() {
      var module = run("""
          def make(k):
              def add(x):
                  return x + k
              return add
          a = make(1)
          b = make(2)
          r1 = a(10)
          r2 = b(10)
          r3 = a(10)
          r4 = b(10)
          """);
      assertGlobal(module, "r1", 11L);
      assertGlobal(module, "r2", 12L);
      assertGlobal(module, "r3", 11L);
      assertGlobal(module, "r4", 12L);
    }

    @Test
    public void closureReceivedAsArgumentIsCalledAtAnInnerSite() {
      var source = """
          def apply_n(fn, n, x):
              i = 0
              while i < n:
                  x = fn(x)
                  i += 1
              return x
          def adder(k):
              def add(x):
                  return x + k
              return add
          r = apply_n(adder(3), 10, 0)
          """;
      assertEquals(30L, eval(source, "r"));
    }

    @Test
    public void closuresCapturingFunctionsAreComposed() {
      var module = run("""
          def inc(x):
              return x + 1
          def dbl(x):
              return x * 2
          def compose(f, g):
              def h(x):
                  return f(g(x))
              return h
          a = compose(inc, dbl)
          b = compose(dbl, inc)
          r1 = a(5)
          r2 = b(5)
          """);
      assertGlobal(module, "r1", 11L);
      assertGlobal(module, "r2", 12L);
    }

    @Test
    public void closureWithRestAndCapture() {
      var module = run("""
          def make(k):
              def total(*r):
                  s = k
                  for x in r:
                      s += x
                  return s
              return total
          a = make(100)
          b = make(1000)
          ra = a(1, 2, 3)
          rb = b()
          rc = b(1, 2, 3, 4, 5, 6)
          """);
      assertGlobal(module, "ra", 106L);
      assertGlobal(module, "rb", 1000L);
      assertGlobal(module, "rc", 1021L);
    }

    @Test
    public void capturedSelfRecursionInASingleClosure() {
      var source = """
          def make(base):
              def fact(n):
                  if n <= 1:
                      return base
                  return n * fact(n - 1)
              return fact
          r = make(2)(5)
          """;
      assertEquals(240L, eval(source, "r"));
    }

    @Test
    public void closureInsideAFunctionCalledManyTimes() {
      var source = """
          def make(k):
              def add(x):
                  return x + k
              return add
          def run(n):
              total = 0
              i = 0
              while i < n:
                  f = make(7)
                  total = f(total)
                  i += 1
              return total
          r = run(50)
          """;
      assertEquals(350L, eval(source, "r"));
    }
  }

  // ========================================================================================== same code, other captures

  /// Several closures made by one `def` share their code but not their captured values nor their `self`.
  /// They go through one call site, so the site must dispatch on the code but pass the values of the callee.
  @Nested
  public final class SameCodeDifferentCaptures {

    @ParameterizedTest(name = "{0} closures of one def")
    @ValueSource(ints = {2, 3, 4, 5, 6, 9})
    public void closuresOfOneDefAtOneSite(int n) {
      var module = run(sameCode(n));
      for (var round = 0; round < 3; round++) {
        for (var i = 0; i < n; i++) {
          assertGlobal(module, "r" + round + "_" + i, 1L + 1000L * i);
        }
      }
    }

    @Test
    public void twoClosuresAlternatingInALoop() {
      var source = """
          def make(k):
              def add(x):
                  return x + k
              return add

          def apply(fn, v):
              return fn(v)

          a = make(1)
          b = make(100)
          i = 0
          total = 0
          while i < 200:
              total = apply(a, total)
              total = apply(b, total)
              i += 1
          """;
      assertEquals(20200L, eval(source, "total"));
    }

    @Test
    public void twoDefsWithTwoClosuresEach() {
      var module = run("""
          def make1(k):
              def add(x):
                  return x + k
              return add
          def make2(k):
              def mul(x):
                  return x * k
              return mul
          def apply(fn, v):
              return fn(v)
          a = make1(10)
          b = make1(20)
          c = make2(3)
          d = make2(4)
          r1 = apply(a, 1)
          r2 = apply(c, 1)
          r3 = apply(b, 1)
          r4 = apply(d, 1)
          r5 = apply(a, 1)
          r6 = apply(d, 1)
          """);
      assertGlobal(module, "r1", 11L);
      assertGlobal(module, "r2", 3L);
      assertGlobal(module, "r3", 21L);
      assertGlobal(module, "r4", 4L);
      assertGlobal(module, "r5", 11L);
      assertGlobal(module, "r6", 4L);
    }

    @Test
    public void closureCreatedAndCalledAtTheSameSiteInALoop() {
      var source = """
          def make(k):
              def add(x):
                  return x + k
              return add
          i = 0
          total = 0
          while i < 5:
              total += make(i)(10)
              i += 1
          """;
      assertEquals(60L, eval(source, "total"));
    }

    @Test
    public void closureCreatedAndCalledInsideAFunction() {
      var source = """
          def make(k):
              def add(x):
                  return x + k
              return add
          def run():
              total = 0
              i = 0
              while i < 6:
                  total += make(i)(100)
                  i += 1
              return total
          r = run()
          """;
      assertEquals(615L, eval(source, "r"));
    }

    @Test
    public void applyNWithTwoClosures() {
      var module = run("""
          def apply_n(fn, n, x):
              i = 0
              while i < n:
                  x = fn(x)
                  i += 1
              return x
          def adder(k):
              def add(x):
                  return x + k
              return add
          a = apply_n(adder(3), 10, 0)
          b = apply_n(adder(5), 10, 0)
          c = apply_n(adder(3), 10, 0)
          """);
      assertGlobal(module, "a", 30L);
      assertGlobal(module, "b", 50L);
      assertGlobal(module, "c", 30L);
    }

    @Test
    public void applyNWithTwoClosures2() {
      var module = run("""
          def apply_n(fn, n, x):
              i = 0
              while i < n:
                  x = fn(x)
                  i = i + 1
              return x
          def adder(k):
              def add(x):
                  return x + k
              return add
          a = apply_n(adder(3), 10, 0)
          b = apply_n(adder(5), 10, 0)
          c = apply_n(adder(3), 10, 0)
          """);
      assertGlobal(module, "a", 30L);
      assertGlobal(module, "b", 50L);
      assertGlobal(module, "c", 30L);
    }

    @Test
    public void recursiveClosuresWithDifferentCaptures() {
      var module = run("""
          def make(base):
              def fact(n):
                  if n <= 1:
                      return base
                  return n * fact(n - 1)
              return fact
          def apply(fn, v):
              return fn(v)
          a = apply(make(1), 5)
          b = apply(make(2), 5)
          c = apply(make(1), 5)
          """);
      assertGlobal(module, "a", 120L);
      assertGlobal(module, "b", 240L);
      assertGlobal(module, "c", 120L);
    }

    @Test
    public void composedClosuresOfOneDef() {
      var module = run("""
          def inc(x):
              return x + 1
          def dbl(x):
              return x * 2
          def compose(f, g):
              def h(x):
                  return f(g(x))
              return h
          def apply(fn, v):
              return fn(v)
          a = compose(inc, dbl)
          b = compose(dbl, inc)
          c = compose(inc, inc)
          r1 = apply(a, 5)
          r2 = apply(b, 5)
          r3 = apply(c, 5)
          r4 = apply(a, 5)
          """);
      assertGlobal(module, "r1", 11L);
      assertGlobal(module, "r2", 12L);
      assertGlobal(module, "r3", 7L);
      assertGlobal(module, "r4", 11L);
    }

    @Test
    public void curriedClosures() {
      var module = run("""
          def curry(a):
              def f1(b):
                  def f2(c):
                      return a * 100 + b * 10 + c
                  return f2
              return f1
          def apply(fn, v):
              return fn(v)
          x = curry(1)
          y = curry(4)
          rx = apply(apply(x, 2), 3)
          ry = apply(apply(y, 5), 6)
          rx2 = apply(apply(x, 7), 8)
          """);
      assertGlobal(module, "rx", 123L);
      assertGlobal(module, "ry", 456L);
      assertGlobal(module, "rx2", 178L);
    }

    @Test
    public void closuresWithRestParameterAtOneSite() {
      var module = run("""
          def make(k):
              def total(*r):
                  s = k
                  for x in r:
                      s += x
                  return s
              return total
          def apply(fn, v):
            return fn(v)
          a = make(100)
          b = make(1000)
          r1 = apply(a, 5)
          r2 = apply(b, 5)
          r3 = apply(a, 6)
          """);
      assertGlobal(module, "r1", 105L);
      assertGlobal(module, "r2", 1005L);
      assertGlobal(module, "r3", 106L);
    }

    @Test
    public void closuresOfOneDefWithKeywordArguments() {
      var module = run("""
          def make(k):
              def add(a, b, c):
                  return a * 100 + b * 10 + c + k
              return add
          
          def apply(fn):
              return fn(c=3, a=1, b=2)
          
          x0 = make(0)
          x1 = make(1000)
          x2 = make(2000)
          x3 = make(3000)
          x4 = make(4000)
          
          r0 = apply(x0)
          r1 = apply(x1)
          r2 = apply(x2)
          r3 = apply(x3)
          r4 = apply(x4)
          r5 = apply(x0)
          r6 = apply(x3)
          """);

      assertGlobal(module, "r0", 123L);
      assertGlobal(module, "r1", 1123L);
      assertGlobal(module, "r2", 2123L);
      assertGlobal(module, "r3", 3123L);
      assertGlobal(module, "r4", 4123L);
      assertGlobal(module, "r5", 123L);
      assertGlobal(module, "r6", 3123L);
    }
  }


  @Nested
  public final class GlobalRebinding {

    @ParameterizedTest(name = "{0} successive definitions, warm")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 10})
    public void warmRebinding(int n) {
      var module = run(rebinding(n, true));
      for (var k = 0; k < n; k++) {
        assertGlobal(module, "r" + k, 100L + k);
      }
    }

    @ParameterizedTest(name = "{0} successive definitions, cold")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 10})
    public void coldRebinding(int n) {
      var module = run(rebinding(n, false));
      for (var k = 0; k < n; k++) {
        assertGlobal(module, "r" + k, 100L + k);
      }
    }

    @Test
    public void globalIsLookedUpAtCallTime() {
      var module = run("""
          def a():
              return b()
          def b():
              return 7
          r = a()
          """);
      assertGlobal(module, "r", 7L);
    }

    @Test
    public void globalReboundToANonFunctionAndBack() {
      var ctx = new ModuleContext();
      runIn(ctx, """
          def f(x):
              return x + 1
          def g(v):
              return f(v)
          a = g(1)
          """);

      runIn(ctx, """
          f = 5
          """);
      var e = assertThrows(PyTypeError.class, () -> runIn(ctx, "b = g(1)"));
      assertEquals("'int' object is not callable", e.getMessage());

      runIn(ctx, """
          def f(x):
              return x + 10
          c = g(1)
          """);
      assertGlobal(ctx, "a", 2L);
      assertGlobal(ctx, "c", 11L);
    }

    @Test
    public void undefinedGlobalFunctionIsANameError() {
      assertThrows(PyNameError.class, () -> Compiler.run("""
          def g():
              return nowhere()
          g()
          """));
    }

    @Test
    public void aliasKeepsTheOldFunctionAfterRebinding() {
      var module = run("""
          def f():
              return 1
          old = f
          def f():
              return 2
          a = old()
          b = f()
          """);
      assertGlobal(module, "a", 1L);
      assertGlobal(module, "b", 2L);
    }
  }


  /// A `def` executed several times creates several function values sharing one compiled body.
  @Nested
  public final class FunctionsDefinedInLoops {

    @ParameterizedTest(name = "{0} function values, module level")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 10, 30})
    public void moduleLevelDefInALoop(int n) {
      var source = """
          def apply(fn, v):
              return fn(v)
          total = 0
          for c in '""" + "a".repeat(n) + """
          ':
              def inc(x):
                  return x + 1
              total = apply(inc, total)
          """;
      assertEquals((long) n, eval(source, "total"));
    }

    @ParameterizedTest(name = "{0} function values, inside a function")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 10, 30})
    public void defInALoopInsideAFunction(int n) {
      var source = """
          def apply(fn, v):
              return fn(v)
          def run():
              total = 0
              for c in '""" + "a".repeat(n) + """
          ':
                  def inc(x):
                      return x + 1
                  total = apply(inc, total)
              return total
          r = run()
          """;
      assertEquals((long) n, eval(source, "r"));
    }

    @Test
    public void functionValuesFromOneDefAreCalledThroughTheSameSiteSeveralTimes() {
      var source = """
          def apply(fn, v):
              return fn(v)
          def make():
              def f(x):
                  return x + 1
              return f
          a = make()
          b = make()
          c = make()
          r = apply(a, 0) + apply(b, 0) + apply(c, 0) + apply(a, 0) + apply(c, 0) + apply(b, 0)
          """;
      assertEquals(6L, eval(source, "r"));
    }

    @Test
    public void manyFunctionValuesFromOneDefWithoutCaptures() {
      var source = """
          def apply(fn, v):
              return fn(v)
          def make():
              def f(x):
                  return x + 1
              return f
          def run(n):
              total = 0
              i = 0
              while i < n:
                  total = apply(make(), total)
                  i += 1
              return total
          r = run(20)
          """;
      assertEquals(20L, eval(source, "r"));
    }
  }


  @Nested
  public final class CallErrors {

    @Test
    public void missingArguments() {
      assertEquals("f() missing 1 required positional argument: 'b'",
          assertTypeErrorMessage("def f(a, b):\n    return a\nf(1)\n"));
      assertEquals("f() missing 2 required positional arguments: 'a' and 'b'",
          assertTypeErrorMessage("def f(a, b):\n    return a\nf()\n"));
      assertEquals("f() missing 3 required positional arguments: 'a', 'b', and 'c'",
          assertTypeErrorMessage("def f(a, b, c):\n    return a\nf()\n"));
      assertEquals("f() missing 2 required positional arguments: 'b' and 'c'",
          assertTypeErrorMessage("def f(a, b, c):\n    return a\nf(1)\n"));
    }

    @Test
    public void tooManyArguments() {
      assertEquals("f() takes 0 positional arguments but 1 was given",
          assertTypeErrorMessage("def f():\n    return 1\nf(1)\n"));
      assertEquals("f() takes 1 positional argument but 2 were given",
          assertTypeErrorMessage("def f(a):\n    return a\nf(1, 2)\n"));
      assertEquals("f() takes 2 positional arguments but 3 were given",
          assertTypeErrorMessage("def f(a, b):\n    return a\nf(1, 2, 3)\n"));
    }

    @Test
    public void wrongArityThroughASharedSite() {
      assertEquals("two() missing 1 required positional argument: 'b'",
          assertTypeErrorMessage("""
              def two(a, b):
                  return a
              def apply(fn, v):
                  return fn(v)
              apply(two, 1)
              """));
      assertEquals("none() takes 0 positional arguments but 1 was given",
          assertTypeErrorMessage("""
              def none():
                  return 1
              def apply(fn, v):
                  return fn(v)
              apply(none, 1)
              """));
    }

    @Test
    public void notCallableValues() {
      var cases = List.of(
          new String[] {"42", "int"},
          new String[] {"10 ** 30", "int"},
          new String[] {"1.5", "float"},
          new String[] {"True", "bool"},
          new String[] {"None", "NoneType"},
          new String[] {"'text'", "str"});
      for (var c : cases) {
        assertEquals("'" + c[1] + "' object is not callable",
            assertTypeErrorMessage("x = " + c[0] + "\nx()\n"), c[0]);
      }
    }

    @Test
    public void notCallableThroughASharedSite() {
      assertEquals("'str' object is not callable",
          assertTypeErrorMessage("""
              def apply(fn, v):
                  return fn(v)
              apply('text', 1)
              """));
    }

    @Test
    public void exceptionsOfTheBodyPropagateThroughSeveralFrames() {
      var source = """
          def inv(x):
              return 100 // x
          def middle(x):
              return inv(x) + 1
          def outer(x):
              return middle(x) + 1
          outer(0)
          """;
      assertThrows(PyZeroDivisionError.class, () -> Compiler.run(source));
    }

    @Test
    public void exceptionsOfAClosureBodyPropagate() {
      var source = """
          def make(k):
              def div(x):
                  return k // x
              return div
          make(10)(0)
          """;
      assertThrows(PyZeroDivisionError.class, () -> Compiler.run(source));
    }

    @Test
    public void argumentsAreEvaluatedBeforeTheArityCheck() {
      assertThrows(PyZeroDivisionError.class,
          () -> Compiler.run("def f(a):\n    return a\nf(1 // 0, 2)\n"));
    }

    @Test
    public void unexpectedKeyword() {
      assertEquals(
          "f() got an unexpected keyword argument 'unknown'",
          assertTypeErrorMessage("""
          def f(a, b):
              return a
          f(unknown=1)
          """));
    }

    @Test
    public void multipleValuesForParameter() {
      assertEquals(
          "f() got multiple values for argument 'a'",
          assertTypeErrorMessage("""
          def f(a, b):
              return a
          f(1, a=2)
          """));
    }

    @Test
    public void duplicateKeyword() {
      assertEquals(
          "f() got multiple values for argument 'a'",
          assertTypeErrorMessage("""
          def f(a, b):
              return a
          f(a=1, a=2)
          """));
    }

    @Test
    public void missingArgumentWithKeywords() {
      assertEquals(
          "f() missing 1 required positional argument: 'b'",
          assertTypeErrorMessage("""
          def f(a, b):
              return a
          f(a=1)
          """));
    }

    @Test
    public void missingSeveralArgumentsWithKeywords() {
      assertEquals(
          "f() missing 2 required positional arguments: 'b' and 'c'",
          assertTypeErrorMessage("""
          def f(a, b, c):
              return a
          f(a=1)
          """));
    }
  }


  /// A call site that failed must stay usable: the failure must not link anything wrong.
  @Nested
  public final class CallSiteRecovery {

    @Test
    public void siteStaysUsableAfterTheCalleeRaised() {
      var ctx = new ModuleContext();
      runIn(ctx, """
          def inv(x):
              return 100 // x
          def apply(fn, v):
              return fn(v)
          a = apply(inv, 5)
          """);
      assertThrows(PyZeroDivisionError.class, () -> runIn(ctx, "b = apply(inv, 0)"));

      runIn(ctx, "c = apply(inv, 20)");
      assertGlobal(ctx, "a", 20L);
      assertGlobal(ctx, "c", 5L);
    }

    @Test
    public void wrongArityIsReportedEveryTimeAndDoesNotBreakTheSite() {
      var ctx = new ModuleContext();
      runIn(ctx, """
          def one(x):
              return x
          def two(a, b):
              return a
          def apply(fn, v):
              return fn(v)
          a = apply(one, 1)
          """);
      for (var i = 0; i < 3; i++) {
        var e = assertThrows(PyTypeError.class,
            () -> runIn(ctx, "x = apply(two, 1)"));
        assertEquals("two() missing 1 required positional argument: 'b'", e.getMessage());
      }

      runIn(ctx, "b = apply(one, 2)");
      assertGlobal(ctx, "b", 2L);
    }

    @Test
    public void nonCallableThenCallableAtTheSameSite() {
      var ctx = new ModuleContext();
      runIn(ctx, """
          def one(x):
              return x
          def apply(fn, v):
              return fn(v)
          a = apply(one, 1)
          """);
      var e = assertThrows(PyTypeError.class,
          () -> runIn(ctx, "x = apply(None, 1)"));
      assertEquals("'NoneType' object is not callable", e.getMessage());

      runIn(ctx, "b = apply(one, 2)");
      assertGlobal(ctx, "b", 2L);
    }

    @Test
    public void wrongArityAfterTheSiteBecamePolymorphic() {
      var ctx = new ModuleContext();
      runIn(ctx, """
          def f0(x):
              return x
          def f1(x):
              return x + 1
          def f2(x):
              return x + 2
          def two(a, b):
              return a
          def apply(fn, v):
              return fn(v)
          a = apply(f0, 0) + apply(f1, 0) + apply(f2, 0)
          """);
      assertThrows(PyTypeError.class, () -> runIn(ctx, "x = apply(two, 1)"));

      runIn(ctx, "b = apply(f2, 0) + apply(f1, 0) + apply(f0, 0)");
      assertGlobal(ctx, "a", 3L);
      assertGlobal(ctx, "b", 3L);
    }

    @Test
    public void exceptionInTheBodyOfOneCalleeDoesNotAffectTheOthers() {
      var ctx = new ModuleContext();
      runIn(ctx, """
          def good(x):
              return x + 1
          def bad(x):
              return x // 0
          def other(x):
              return x * 2
          def apply(fn, v):
              return fn(v)
          a = apply(good, 1) + apply(other, 1)
          """);
      assertThrows(PyZeroDivisionError.class, () -> runIn(ctx, "x = apply(bad, 1)"));

      runIn(ctx, "b = apply(good, 1) + apply(other, 1)");
      assertGlobal(ctx, "a", 4L);
      assertGlobal(ctx, "b", 4L);
    }

    @Test
    public void exceptionInsideNestedCallsLeavesInnerSitesUsable() {
      var ctx = new ModuleContext();
      runIn(ctx, """
          def inv(x):
              return 100 // x
          def middle(x):
              return inv(x) + 1
          a = middle(10)
          """);
      assertThrows(PyZeroDivisionError.class, () -> runIn(ctx, "x = middle(0)\n"));
      runIn(ctx, "b = middle(50)\n");
      assertGlobal(ctx, "a", 11L);
      assertGlobal(ctx, "b", 3L);
    }

    @Test
    public void megamorphicKeywordCallReportsErrorsAndRecovers() {
      var ctx = new ModuleContext();
      var builder = new StringBuilder();

      for (var i = 0; i < 8; i++) {
        builder.append("def f").append(i).append("(a, b, c):\n")
            .append("    return a * 100 + b * 10 + c + ")
            .append(i * 1000).append('\n');
      }

      builder.append("""
          def apply(fn):
              return fn(c=3, a=1, b=2)
          """);

      for (var i = 0; i < 8; i++) {
        builder.append("r").append(i)
            .append(" = apply(f").append(i).append(")\n");
      }

      runIn(ctx, builder.toString());

      // The site is now megamorphic.
      var e = assertThrows(PyTypeError.class, () ->
          runIn(ctx, """
              def wrong(x, y, z):
                  return x
              bad = apply(wrong)
              """));

      assertEquals(
          "wrong() got an unexpected keyword argument 'c'",
          e.getMessage()
      );

      // A valid callee must still work afterward.
      runIn(ctx, "ok = apply(f7)\n");

      assertGlobal(ctx, "ok", 7123L);
    }
  }


  @Nested
  public final class KeywordCalls {

    @Test
    public void monomorphic() {
      for (var captured : List.of(false, true)) {
        for (var shape : CallShape.values()) {
          checkKeywordDispatch(1, captured, shape, false);
          checkKeywordDispatch(1, captured, shape, true);
        }
      }
    }

    @Test
    public void polymorphic() {
      for (var captured : List.of(false, true)) {
        for (var shape : CallShape.values()) {
          checkKeywordDispatch(4, captured, shape, false);
          checkKeywordDispatch(4, captured, shape, true);
        }
      }
    }

    @Test
    public void megamorphic() {
      for (var captured : List.of(false, true)) {
        for (var shape : CallShape.values()) {
          checkKeywordDispatch(8, captured, shape, false);
          checkKeywordDispatch(8, captured, shape, true);
        }
      }
    }
  }


  @Nested
  public final class Concurrency {

    @Test
    @Timeout(120)
    public void threadsSharingCallSitesAndFunctions() throws Throwable {
      var ctx = new ModuleContext();
      runIn(ctx, """
          def f0(x):
              return x + 1
          def f1(x):
              return x + 2
          def f2(x):
              return x + 3
          def apply(fn, v):
              return fn(v)
          def work(n):
              total = 0
              i = 0
              while i < n:
                  total = apply(f0, total)
                  total = apply(f1, total)
                  total = apply(f2, total)
                  i += 1
              return total
          """);
      var threads = 8;
      try (var pool = Executors.newFixedThreadPool(threads)) {
        var futures = new ArrayList<Future<Object>>();
        for (var t = 0; t < threads; t++) {
          var name = "result" + t;
          futures.add(pool.submit(() -> {
            runIn(ctx, name + " = work(500)");
            return ctx.getGlobal(name);
          }));
        }
        for (var future : futures) {
          assertEquals(3000L, future.get());
        }
      }
    }

    @Test
    @Timeout(120)
    public void threadsCompilingTheSameFunctionBodyAtTheSameTime() throws Throwable {
      var ctx = new ModuleContext();
      runIn(ctx, """
          def fib(n):
              if n < 2:
                  return n
              return fib(n - 1) + fib(n - 2)
          """);
      var threads = 8;
      try (var pool = Executors.newFixedThreadPool(threads)) {
        var futures = new ArrayList<Future<Object>>();
        for (var t = 0; t < threads; t++) {
          var name = "fib" + t;
          futures.add(pool.submit(() -> {
            runIn(ctx, name + " = fib(15)");
            return ctx.getGlobal(name);
          }));
        }
        for (var future : futures) {
          assertEquals(610L, future.get());
        }
      }
    }
  }
}