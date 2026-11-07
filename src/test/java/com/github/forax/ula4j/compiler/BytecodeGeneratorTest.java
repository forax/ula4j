package com.github.forax.ula4j.compiler;

import com.github.forax.ula4j.ast.UnsupportedSyntaxException;
import com.github.forax.ula4j.rt.error.PyNameError;
import com.github.forax.ula4j.rt.error.PyTypeError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class BytecodeGeneratorTest {
  private static Object eval(String source, String global) throws Throwable {
    return Compiler.run(source).getGlobal(global);
  }

  @Nested
  public final class Literal {

    @Test
    public void literalVariants() throws Throwable {
      assertEquals(42L, eval("x = 42", "x"));
      assertEquals(-7L, eval("x = -7", "x"));
      assertEquals(3.14, eval("x = 3.14", "x"));
      assertEquals(true, eval("x = True", "x"));
      assertEquals(false, eval("x = False", "x"));
      assertEquals("hello", eval("x = 'hello'", "x"));
      assertEquals("hello", eval("x = \"hello\"", "x"));
      assertEquals("", eval("x = ''", "x"));
    }

    @Test
    public void numericLiteralFormats() throws Throwable {
      assertEquals(31L, eval("x = 0x1F", "x"));
      assertEquals(15L, eval("x = 0o17", "x"));
      assertEquals(5L, eval("x = 0b101", "x"));
      assertEquals(1_000_000L, eval("x = 1_000_000", "x"));
      assertEquals(1.5e10, eval("x = 1.5e10", "x"));
      assertEquals(0.0015, eval("x = 1.5e-3", "x"));
    }

    @Test
    public void largeIntegerLiterals() throws Throwable {
      assertEquals(new BigInteger("123456789012345678901234567890"),
          eval("x = 123456789012345678901234567890", "x"));
    }

    @Test
    public void stringEscapes() throws Throwable {
      assertEquals("line1\nline2", eval("x = 'line1\\nline2'", "x"));
      assertEquals("tab\there", eval("x = 'tab\\there'", "x"));
      assertEquals("\\", eval("x = '\\\\'", "x"));
    }
  }

  @Nested
  public final class Arithmetic {

    @Test
    public void floatArithmetic() throws Throwable {
      assertEquals(4.0, eval("x = 1.5 + 2.5", "x"));
      assertEquals(0.5, eval("x = 1.5 - 1.0", "x"));
      assertEquals(2.25, eval("x = 1.5 * 1.5", "x"));
      assertEquals(0.75, eval("x = 1.5 / 2", "x"));
    }

    @Test
    public void mixedIntFloatArithmetic() throws Throwable {
      assertEquals(3.5, eval("x = 1 + 2.5", "x"));
      assertEquals(2.5, eval("x = 1.5 + 1", "x"));
    }

    @Test
    public void stringConcatenation() throws Throwable {
      assertEquals("ab", eval("x = 'a' + 'b'", "x"));
      assertEquals("a", eval("x = '' + 'a'", "x"));
      assertEquals("abcabc", eval("x = 'abc' + 'abc'", "x"));
    }

    @Test
    public void stringRepetition() throws Throwable {
      assertEquals("ababab", eval("x = 'ab' * 3", "x"));
      assertEquals("ababab", eval("x = 3 * 'ab'", "x"));
      assertEquals("", eval("x = 'ab' * 0", "x"));
    }

    @Test
    public void floorDivisionAndModuloWithNegatives() throws Throwable {
      assertEquals(-4L, eval("x = -7 // 2", "x"));
      assertEquals(-4L, eval("x = 7 // -2", "x"));
      assertEquals(3L, eval("x = -7 // -2", "x"));
      assertEquals(2L, eval("x = -7 % 3", "x"));
      assertEquals(-2L, eval("x = 7 % -3", "x"));
    }

    @Test
    public void power() throws Throwable {
      assertEquals(1L, eval("x = 5 ** 0", "x"));
      assertEquals(0.5, eval("x = 2 ** -1", "x"));
      assertEquals(1024L, eval("x = 2 ** 10", "x"));
      assertEquals(BigInteger.ONE.shiftLeft(100), eval("x = 2 ** 100", "x"));
    }

    @Test
    public void bitwiseOperators() throws Throwable {
      assertEquals(1L, eval("x = 5 & 3", "x"));
      assertEquals(7L, eval("x = 5 | 3", "x"));
      assertEquals(6L, eval("x = 5 ^ 3", "x"));
      assertEquals(-6L, eval("x = ~5", "x"));
    }

    @Test
    public void shiftOperators() throws Throwable {
      assertEquals(1024L, eval("x = 1 << 10", "x"));
      assertEquals(128L, eval("x = 1024 >> 3", "x"));
    }

    @Test
    public void unaryOperators() throws Throwable {
      assertEquals(5L, eval("x = +5", "x"));
      assertEquals(-5L, eval("x = -5", "x"));
      assertEquals(-3L, eval("x = -(1 + 2)", "x"));
      assertEquals(6L, eval("x = -(-6)", "x"));
    }

    @Test
    public void operatorPrecedence() throws Throwable {
      assertEquals(7L, eval("x = 1 + 2 * 3", "x"));
      assertEquals(9L, eval("x = (1 + 2) * 3", "x"));
      assertEquals(1L, eval("x = 1 + 2 - 2", "x"));
    }
  }

  @Nested
  public final class AugmentedAssignment {

    @Test
    public void allAugmentedAssignments() throws Throwable {
      assertEquals(8L, eval("x = 5\nx += 3", "x"));
      assertEquals(2L, eval("x = 5\nx -= 3", "x"));
      assertEquals(15L, eval("x = 5\nx *= 3", "x"));
      assertEquals(2.5, eval("x = 5\nx /= 2", "x"));
      assertEquals(1L, eval("x = 5\nx //= 3", "x"));
      assertEquals(2L, eval("x = 5\nx %= 3", "x"));
      assertEquals(125L, eval("x = 5\nx **= 3", "x"));
      assertEquals(20L, eval("x = 5\nx <<= 2", "x"));
      assertEquals(1L, eval("x = 5\nx >>= 2", "x"));
      assertEquals(1L, eval("x = 5\nx &= 3", "x"));
      assertEquals(7L, eval("x = 5\nx |= 3", "x"));
      assertEquals(6L, eval("x = 5\nx ^= 3", "x"));
    }

    @Test
    public void augAssignOnUndefinedNameIsANameError() {
      assertThrows(PyNameError.class, () -> Compiler.run("x += 1"));
    }
  }

  @Nested
  public final class Assignment {

    @Test
    public void chainedAssignment() throws Throwable {
      var module = Compiler.run("a = b = c = 1");
      assertEquals(1L, module.getGlobal("a"));
      assertEquals(1L, module.getGlobal("b"));
      assertEquals(1L, module.getGlobal("c"));
    }

    @Test
    public void reassignmentReferencingSelf() throws Throwable {
      assertEquals(2L, eval("x = 1\nx = x + 1", "x"));
    }

    @Test
    public void reassignmentDoesNotAffectEarlierCopies() throws Throwable {
      var module = Compiler.run("x = 1\ny = x\nx = 2");
      assertEquals(2L, module.getGlobal("x"));
      assertEquals(1L, module.getGlobal("y"));
    }
  }


  @Nested
  public final class Comparison {

    @Test
    public void comparisonOperators() throws Throwable {
      assertEquals(true, eval("x = 1 < 2", "x"));
      assertEquals(true, eval("x = 2 <= 2", "x"));
      assertEquals(true, eval("x = 3 > 2", "x"));
      assertEquals(true, eval("x = 3 >= 3", "x"));
      assertEquals(true, eval("x = 1 == 1", "x"));
      assertEquals(true, eval("x = 1 != 2", "x"));
    }

    @Test
    public void comparisonChainsEvaluateEachOperandOnce() throws Throwable {
      assertEquals(true, eval("x = 1 < 2 < 3 < 4", "x"));
      assertEquals(false, eval("x = 1 < 2 < 3 > 4", "x"));
      assertEquals(false, eval("x = 1 < 2 > 2", "x"));
      assertEquals(true, eval("x = 1 == 1 == 1", "x"));
      assertEquals(true, eval("x = 1 < 2 > 1", "x"));
    }

    @Test
    public void stringComparisons() throws Throwable {
      assertEquals(true, eval("x = 'a' < 'b'", "x"));
      assertEquals(true, eval("x = 'abc' > 'abb'", "x"));
      assertEquals(true, eval("x = 'abc' == 'abc'", "x"));
    }

    @Test
    public void equalityAcrossSupportedTypes() throws Throwable {
      assertEquals(true, eval("x = True == True", "x"));
      assertEquals(true, eval("x = None == None", "x"));
      assertEquals(false, eval("x = 'a' == 'b'", "x"));
      assertEquals(false, eval("x = 'a' != 'a'", "x"));
    }

    @Test
    public void containment() throws Throwable {
      assertEquals(true, eval("x = 'a' in 'abc'", "x"));
      assertEquals(false, eval("x = 'z' in 'abc'", "x"));
      assertEquals(true, eval("x = 'z' not in 'abc'", "x"));
      assertEquals(true, eval("x = 'abc' in 'abcdef'", "x"));
    }

    @Test
    public void containmentCombinedWithAnd() throws Throwable {
      assertEquals(true, eval("x = 'a' in 'abc' and 'b' in 'abc'", "x"));
      assertEquals(false, eval("x = 'a' in 'abc' and 'z' in 'abc'", "x"));
    }
  }

  @Nested
  public final class BooleanOperator {

    @Test
    public void truthinessOfCommonValues() throws Throwable {
      assertEquals(false, eval("x = not 1", "x"));
      assertEquals(true, eval("x = not 0", "x"));
      assertEquals(false, eval("x = not 'a'", "x"));
      assertEquals(true, eval("x = not ''", "x"));
      assertEquals(true, eval("x = not None", "x"));
      assertEquals(false, eval("x = not True", "x"));
      assertEquals(true, eval("x = not False", "x"));
    }

    @Test
    public void andOrCornerCases() throws Throwable {
      assertEquals(2L, eval("x = 1 and 2", "x"));
      assertEquals(0L, eval("x = 0 or 0", "x"));
      assertEquals(3L, eval("x = 1 and 2 and 3", "x"));
      assertEquals(1L, eval("x = 1 or 2 or 3", "x"));
      assertEquals(3L, eval("x = 0 and 2 or 3", "x"));
      assertEquals(3L, eval("x = (1 or 2) and 3", "x"));
    }

    @Test
    public void andOrWithNone() throws Throwable {
      assertEquals("a", eval("x = None or 'a'", "x"));
      assertEquals(true, eval("x = (None and 'a') == None", "x"));
    }

    @Test
    public void shortCircuitAvoidsEvaluation() throws Throwable {
      // undefined would raise a NameError if it were evaluated
      assertEquals(false, eval("x = False and undefined", "x"));
      assertEquals(true, eval("x = True or undefined", "x"));
      assertEquals(1L, eval("x = 1 if True else undefined", "x"));
    }

    @Test
    public void chainedShortCircuitReturningOperands() throws Throwable {
      assertEquals(true, eval("x = (0 or '') == ''", "x"));
      assertEquals(true, eval("x = (0 and '') == 0", "x"));
    }
  }


  @Nested
  public final class ConditionalExpression {

    @Test
    public void nestedConditionalExpressions() throws Throwable {
      assertEquals(1L, eval("x = 1 if True else (2 if False else 3)", "x"));
      assertEquals(3L, eval("x = 1 if False else (2 if False else 3)", "x"));
      assertEquals(2L, eval("x = 1 if False else (2 if True else 3)", "x"));
    }

    @Test
    public void conditionalExpressionIsLazy() throws Throwable {
      assertEquals(1L, eval("x = 1 if True else undefined", "x"));
      assertEquals(2L, eval("x = undefined if False else 2", "x"));
    }
  }


  @Nested
  public final class ControlFlow {

    // -- if / else

    @Test
    public void ifWithAndWithoutElse() throws Throwable {
      assertEquals(2L, eval("x = 1\nif True:\n    x = 2", "x"));
      assertEquals(1L, eval("x = 1\nif False:\n    x = 2", "x"));
      assertEquals(2L, eval("x = 1\nif False:\n    x = 1\nelse:\n    x = 2", "x"));
    }

    @Test
    public void ifVariableAssignedInsideIsReadable() throws Throwable {
      assertEquals(10L, eval("x = 1\nif x:\n    y = 10\nr = y", "r"));
    }

    // -- while

    @Test
    public void whileElseWithoutBreak() throws Throwable {
      var source = """
          i = 0
          while i < 3:
              i += 1
          else:
              i = 100
          r = i
          """;
      assertEquals(100L, eval(source, "r"));
    }

    @Test
    public void whileElseWithBreak() throws Throwable {
      var source = """
          i = 0
          while i < 10:
              i += 1
              if i == 3:
                  break
          else:
              i = 100
          r = i
          """;
      assertEquals(3L, eval(source, "r"));
    }

    @Test
    public void whileContinue() throws Throwable {
      var source = """
          i = 0
          s = 0
          while i < 5:
              i += 1
              if i == 3:
                  continue
              s += i
          r = s
          """;
      assertEquals(12L, eval(source, "r"));
    }

    @Test
    public void whileFalseSkipsBody() throws Throwable {
      var source = """
          x = 0
          while False:
              x = 1
          else:
              x = 2
          r = x
          """;
      assertEquals(2L, eval(source, "r"));
    }

    // -- for

    @Test
    public void forElseWithoutBreak() throws Throwable {
      var source = """
          n = 0
          for c in 'ab':
              n += 1
          else:
              n += 10
          r = n
          """;
      assertEquals(12L, eval(source, "r"));
    }

    @Test
    public void forElseWithBreak() throws Throwable {
      var source = """
          n = 0
          for c in 'abcd':
              if c == 'c':
                  break
              n += 1
          else:
              n = 100
          r = n
          """;
      assertEquals(2L, eval(source, "r"));
    }

    @Test
    public void forEmptyIterableRunsElse() throws Throwable {
      var source = """
          n = 0
          for c in '':
              n = 1
          else:
              n = 2
          r = n
          """;
      assertEquals(2L, eval(source, "r"));
    }

    @Test
    public void forContinue() throws Throwable {
      var source = """
          s = 0
          for c in 'abc':
              if c == 'b':
                  continue
              s += 1
          r = s
          """;
      assertEquals(2L, eval(source, "r"));
    }

    @Test
    public void forVariableVisibleAfterLoop() throws Throwable {
      assertEquals("b", eval("for c in 'ab':\n    x = c\nr = x", "r"));
    }

    @Test
    public void nestedLoopsBreakInnerOnly() throws Throwable {
      var source = """
          total = 0
          for i in 'abc':
              for j in 'xyz':
                  if j == 'y':
                      break
                  total += 1
          r = total
          """;
      assertEquals(3L, eval(source, "r"));
    }

    @Test
    public void nestedLoopsContinueInnerOnly() throws Throwable {
      var source = """
          total = 0
          for i in 'ab':
              for j in 'xyz':
                  if j == 'y':
                      continue
                  total += 1
          r = total
          """;
      assertEquals(4L, eval(source, "r"));
    }
  }


  @Nested
  public final class Function {

    @Test
    public void zeroArgumentFunction() throws Throwable {
      assertEquals(42L, eval("def f():\n    return 42\nr = f()", "r"));
    }

    @Test
    public void defaultReturnIsNone() throws Throwable {
      assertEquals(true, eval("def f():\n    x = 1\nr = f() == None", "r"));
    }

    @Test
    public void explicitReturnNone() throws Throwable {
      assertEquals(true, eval("def f():\n    return None\nr = f() == None", "r"));
    }

    @Test
    public void returnInsideForLoop() throws Throwable {
      var source = """
          def find(s, c):
              for ch in s:
                  if ch == c:
                      return 1
              return 0
          r1 = find('abc', 'b')
          r2 = find('abc', 'z')
          """;
      var module = Compiler.run(source);
      assertEquals(1L, module.getGlobal("r1"));
      assertEquals(0L, module.getGlobal("r2"));
    }

    @Test
    public void earlyReturnInsideIf() throws Throwable {
      var source = """
          def sign(x):
              if x > 0:
                  return 1
              if x < 0:
                  return -1
              return 0
          p = sign(5)
          n = sign(-3)
          z = sign(0)
          """;
      var module = Compiler.run(source);
      assertEquals(1L, module.getGlobal("p"));
      assertEquals(-1L, module.getGlobal("n"));
      assertEquals(0L, module.getGlobal("z"));
    }

    @Test
    public void restParameterOnly() throws Throwable {
      var source = """
          def count(*args):
              n = 0
              for x in args:
                  n += 1
              return n
          a = count()
          b = count(1)
          c = count(1, 2, 3)
          """;
      var module = Compiler.run(source);
      assertEquals(0L, module.getGlobal("a"));
      assertEquals(1L, module.getGlobal("b"));
      assertEquals(3L, module.getGlobal("c"));
    }
  }

  @Nested
  public final class Closure {

    @Test
    public void captureByValueLocal() throws Throwable {
      var source = """
          def make():
              x = 1
              def get():
                  return x
              return get
          r = make()()
          """;
      assertEquals(1L, eval(source, "r"));
    }

    @Test
    public void captureByReferenceGlobal() throws Throwable {
      var source = """
          x = 1
          def outer():
              def inner():
                  return x
              return inner
          f = outer()
          x = 2
          r = f()
          """;
      assertEquals(2L, eval(source, "r"));
    }

    @Test
    public void independentCapturesPerCall() throws Throwable {
      var source = """
          def make_adder(x):
              def add(y):
                  return x + y
              return add
          a = make_adder(1)
          b = make_adder(10)
          r1 = a(1)
          r2 = b(1)
          """;
      var module = Compiler.run(source);
      assertEquals(2L, module.getGlobal("r1"));
      assertEquals(11L, module.getGlobal("r2"));
    }

    @Test
    public void deepNesting() throws Throwable {
      var source = """
          def a():
              def b():
                  def c():
                      return 42
                  return c()
              return b()
          r = a()
          """;
      assertEquals(42L, eval(source, "r"));
    }

    @Test
    public void captureThroughNestedLevels() throws Throwable {
      var source = """
          def a(x):
              def b():
                  def c():
                      return x
                  return c()
              return b()
          r = a(42)
          """;
      assertEquals(42L, eval(source, "r"));
    }

    @Test
    public void captureAndSelfRecursion() throws Throwable {
      var source = """
          def outer(base):
              def fact(k):
                  if k <= 1:
                      return base
                  return k * fact(k - 1)
              return fact(3)
          r = outer(1)
          """;
      assertEquals(6L, eval(source, "r"));
    }
  }

  @Nested
  public final class Scope {

    @Test
    public void globalReadFromFunction() throws Throwable {
      var source = """
          x = 1
          def f():
              return x + 1
          r1 = f()
          x = 10
          r2 = f()
          """;
      var module = Compiler.run(source);
      assertEquals(2L, module.getGlobal("r1"));
      assertEquals(11L, module.getGlobal("r2"));
    }

    @Test
    public void localAssignmentDoesNotTouchGlobal() throws Throwable {
      var source = """
          x = 1
          def f():
              x = 2
              return x
          r1 = f()
          r2 = x
          """;
      var module = Compiler.run(source);
      assertEquals(2L, module.getGlobal("r1"));
      assertEquals(1L, module.getGlobal("r2"));
    }

    @Test
    public void globalFromFunctionDoesNotLeakOut() {
      var source = """
          def f():
              x = 1
              return x
          f()
          r = x
          """;
      assertThrows(PyNameError.class, () -> Compiler.run(source));
    }
  }


  @Nested
  public final class RuntimeError {


    @Test
    public void typeErrors() {
      assertThrows(PyTypeError.class, () -> Compiler.run("x = 1 / 'a'"));
      assertThrows(PyTypeError.class, () -> Compiler.run("x = 'a' + 1"));
      assertThrows(PyTypeError.class, () -> Compiler.run("x = None + 1"));
      assertThrows(PyTypeError.class, () -> Compiler.run("x = 1\nx()"));
    }

    // -- unsupported constructs

    @Test
    public void moreUnsupportedConstructs() {
      assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = 1\ndel x"));
      assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = 1\ny = x[0]"));
      assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = 1\nx[0] = 1"));
      assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = 1\ny = x[1:2]"));
      assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = 1\ny = x.m()"));
      assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = (1, 2)"));
      assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = {1: 2}"));
      assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = []"));
    }
  }


  @Nested
  public final class LongString {

    @Test
    public void veryLongAsciiStringLiteral() throws Throwable {
      var longStr = "a".repeat(70_000);  // > 65535 modified-UTF-8 bytes
      assertEquals(longStr, eval("x = '" + longStr + "'", "x"));
    }

    @Test
    public void veryLongEmojiStringLiteral() throws Throwable {
      var emoji = Character.toString(0x1F600);     // one supplementary char
      var longEmoji = emoji.repeat(20_000);        // 40000 Java chars, chunked
      assertEquals(longEmoji, eval("x = '" + longEmoji + "'", "x"));
    }

    @Test
    public void shortUnicodeString() throws Throwable {
      assertEquals("héllo", eval("x = 'héllo'", "x"));
      assertEquals("😀", eval("x = '😀'", "x"));
    }
  }
}