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
  private static Object eval(String source, String global) {
    return Compiler.run(source).getGlobal(global);
  }

  @Nested
  public final class Literal {

    @Test
    public void literalVariants() {
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
    public void numericLiteralFormats() {
      assertEquals(31L, eval("x = 0x1F", "x"));
      assertEquals(15L, eval("x = 0o17", "x"));
      assertEquals(5L, eval("x = 0b101", "x"));
      assertEquals(1_000_000L, eval("x = 1_000_000", "x"));
      assertEquals(1.5e10, eval("x = 1.5e10", "x"));
      assertEquals(0.0015, eval("x = 1.5e-3", "x"));
    }

    @Test
    public void largeIntegerLiterals() {
      assertEquals(new BigInteger("123456789012345678901234567890"),
          eval("x = 123456789012345678901234567890", "x"));
    }

    @Test
    public void stringEscapes() {
      assertEquals("line1\nline2", eval("x = 'line1\\nline2'", "x"));
      assertEquals("tab\there", eval("x = 'tab\\there'", "x"));
      assertEquals("\\", eval("x = '\\\\'", "x"));
    }

    @Test
    public void noneCanBeComparedAndSelected() {
      assertEquals(true, eval("x = None == None", "x"));
      assertEquals(7L, eval("x = 7 if not None else 9", "x"));
      assertEquals(9L, eval("x = 7 if None else 9", "x"));
    }

    @Test
    public void integerLongBoundaryAndPromotion() {
      assertEquals(Long.MAX_VALUE, eval("x = 9223372036854775807", "x"));
      assertEquals(new BigInteger("9223372036854775808"),
          eval("x = 9223372036854775808", "x"));
      assertEquals(new BigInteger("9223372036854775808"),
          eval("x = 9223372036854775807 + 1", "x"));
    }

    @Test
    public void scientificNotationVariants() {
      assertEquals(1.0e3, eval("x = 1e3", "x"));
      assertEquals(2.5e-4, eval("x = 2.5e-4", "x"));
      assertEquals(6.0e2, eval("x = 6E+2", "x"));
    }

    @Test
    public void escapedQuotesAndCarriageReturn() {
      assertEquals("it's", eval("x = 'it\\'s'", "x"));
      assertEquals("say \"hi\"", eval("x = \"say \\\"hi\\\"\"", "x"));
      assertEquals("a\rb", eval("x = 'a\\rb'", "x"));
    }

    @Test
    public void unicodeCodePointsSurviveStringConstruction() {
      assertEquals("A😀Ω", eval("x = 'A😀Ω'", "x"));
      assertEquals("𝄞", eval("x = '𝄞'", "x"));
    }
  }

  @Nested
  public final class LongString {

    @Test
    public void veryLongAsciiStringLiteral() {
      var longStr = "a".repeat(70_000);  // > 65535 modified-UTF-8 bytes
      assertEquals(longStr, eval("x = '" + longStr + "'", "x"));
    }

    @Test
    public void veryLongEmojiStringLiteral() {
      var emoji = Character.toString(0x1F600);     // one supplementary char
      var longEmoji = emoji.repeat(20_000);        // 40000 Java chars, chunked
      assertEquals(longEmoji, eval("x = '" + longEmoji + "'", "x"));
    }

    @Test
    public void shortUnicodeString() {
      assertEquals("héllo", eval("x = 'héllo'", "x"));
      assertEquals("😀", eval("x = '😀'", "x"));
    }
  }

  @Nested
  public final class Arithmetic {

    @Test
    public void floatArithmetic() {
      assertEquals(4.0, eval("x = 1.5 + 2.5", "x"));
      assertEquals(0.5, eval("x = 1.5 - 1.0", "x"));
      assertEquals(2.25, eval("x = 1.5 * 1.5", "x"));
      assertEquals(0.75, eval("x = 1.5 / 2", "x"));
    }

    @Test
    public void mixedIntFloatArithmetic() {
      assertEquals(3.5, eval("x = 1 + 2.5", "x"));
      assertEquals(2.5, eval("x = 1.5 + 1", "x"));
    }

    @Test
    public void stringConcatenation() {
      assertEquals("ab", eval("x = 'a' + 'b'", "x"));
      assertEquals("a", eval("x = '' + 'a'", "x"));
      assertEquals("abcabc", eval("x = 'abc' + 'abc'", "x"));
    }

    @Test
    public void stringRepetition() {
      assertEquals("ababab", eval("x = 'ab' * 3", "x"));
      assertEquals("ababab", eval("x = 3 * 'ab'", "x"));
      assertEquals("", eval("x = 'ab' * 0", "x"));
    }

    @Test
    public void floorDivisionAndModuloWithNegatives() {
      assertEquals(-4L, eval("x = -7 // 2", "x"));
      assertEquals(-4L, eval("x = 7 // -2", "x"));
      assertEquals(3L, eval("x = -7 // -2", "x"));
      assertEquals(2L, eval("x = -7 % 3", "x"));
      assertEquals(-2L, eval("x = 7 % -3", "x"));
    }

    @Test
    public void power() {
      assertEquals(1L, eval("x = 5 ** 0", "x"));
      assertEquals(0.5, eval("x = 2 ** -1", "x"));
      assertEquals(1024L, eval("x = 2 ** 10", "x"));
      assertEquals(BigInteger.ONE.shiftLeft(100), eval("x = 2 ** 100", "x"));
    }

    @Test
    public void bitwiseOperators() {
      assertEquals(1L, eval("x = 5 & 3", "x"));
      assertEquals(7L, eval("x = 5 | 3", "x"));
      assertEquals(6L, eval("x = 5 ^ 3", "x"));
      assertEquals(-6L, eval("x = ~5", "x"));
    }

    @Test
    public void shiftOperators() {
      assertEquals(1024L, eval("x = 1 << 10", "x"));
      assertEquals(128L, eval("x = 1024 >> 3", "x"));
    }

    @Test
    public void unaryOperators() {
      assertEquals(5L, eval("x = +5", "x"));
      assertEquals(-5L, eval("x = -5", "x"));
      assertEquals(-3L, eval("x = -(1 + 2)", "x"));
      assertEquals(6L, eval("x = -(-6)", "x"));
    }

    @Test
    public void operatorPrecedence() {
      assertEquals(7L, eval("x = 1 + 2 * 3", "x"));
      assertEquals(9L, eval("x = (1 + 2) * 3", "x"));
      assertEquals(1L, eval("x = 1 + 2 - 2", "x"));
    }

    @Test
    public void integerArithmeticAndOperatorPrecedence() {
      assertEquals(7L, eval("x = 20 - 3 * 4 - 1", "x"));
      assertEquals(20L, eval("x = 2 * (3 + 7)", "x"));
      assertEquals(6L, eval("x = 20 // 3", "x"));
      assertEquals(2L, eval("x = 20 % 3", "x"));
    }

    @Test
    public void exponentiationIsRightAssociative() {
      assertEquals(512L, eval("x = 2 ** 3 ** 2", "x"));
      assertEquals(64L, eval("x = (2 ** 3) ** 2", "x"));
    }

    @Test
    public void unaryMinusBindsAsPythonDoesAroundPower() {
      assertEquals(-4L, eval("x = -2 ** 2", "x"));
      assertEquals(4L, eval("x = (-2) ** 2", "x"));
      assertEquals(-4L, eval("x = -(2 ** 2)", "x"));
    }

    @Test
    public void floorDivisionAndModuloHaveConsistentSigns() {
      assertEquals(-3L, eval("x = -7 // 3", "x"));
      assertEquals(2L, eval("x = -7 % 3", "x"));
      assertEquals(-3L, eval("x = 7 // -3", "x"));
      assertEquals(-2L, eval("x = 7 % -3", "x"));
      assertEquals(2L, eval("x = -7 // -3", "x"));
      assertEquals(-1L, eval("x = -7 % -3", "x"));
    }

    @Test
    public void integerAndBigIntegerBitwiseOperations() {
      assertEquals(4L, eval("x = 12 & 5", "x"));
      assertEquals(13L, eval("x = 12 | 5", "x"));
      assertEquals(9L, eval("x = 12 ^ 5", "x"));
      assertEquals(-13L, eval("x = ~12", "x"));
      assertEquals(BigInteger.ONE.shiftLeft(100),
          eval("x = 1 << 100", "x"));
    }

    @Test
    public void shiftsAndInversionOnNegativeNumbers() {
      assertEquals(-8L, eval("x = -2 << 2", "x"));
      assertEquals(-2L, eval("x = -8 >> 2", "x"));
      assertEquals(1L, eval("x = ~(-2)", "x"));
    }

    @Test
    public void floatingPointFloorDivisionAndModulo() {
      assertEquals(-3.0, eval("x = -7.0 // 3.0", "x"));
      assertEquals(2.0, eval("x = -7.0 % 3.0", "x"));
      assertEquals(-3.0, eval("x = 7.0 // -3.0", "x"));
      assertEquals(-2.0, eval("x = 7.0 % -3.0", "x"));
    }

    @Test
    public void stringConcatenationAndRepetitionCompose() {
      assertEquals("abababab", eval("x = ('ab' * 2) + ('ab' * 2)", "x"));
      assertEquals("xxxx", eval("x = 'x' * (2 + 2)", "x"));
      assertEquals("", eval("x = 'x' * -3", "x"));
    }

    @Test
    public void nestedUnaryOperators() {
      assertEquals(3L, eval("x = -(-(+3))", "x"));
      assertEquals(false, eval("x = not not False", "x"));
    }

    @Test
    public void arithmeticWithLargeIntegersRemainsExact() {
      assertEquals(new BigInteger("246913578024691357802469135780"),
          eval("x = 123456789012345678901234567890 * 2", "x"));
      assertEquals(new BigInteger("123456789012345678901234567890"),
          eval("x = 123456789012345678901234567891 - 1", "x"));
    }
  }

  @Nested
  public final class AugmentedAssignment {

    @Test
    public void allAugmentedAssignments() {
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

    @Test
    public void augmentedAssignmentReadsOldValueBeforeWriting() {
      assertEquals(10L, eval("x = 5\nx += x", "x"));
      assertEquals(25L, eval("x = 5\nx *= x", "x"));
    }

    @Test
    public void augmentedAssignmentCanUseAnExpressionAsItsRightHandSide() {
      assertEquals(14L, eval("x = 2\ny = 3\nx += y * 4", "x"));
      assertEquals(2L, eval("x = 20\ny = 3\nx //= y + 7", "x"));
    }

    @Test
    public void augmentedStringAssignment() {
      assertEquals("hello world", eval("x = 'hello'\nx += ' world'", "x"));
      assertEquals("ababab", eval("x = 'ab'\nx *= 3", "x"));
    }

    @Test
    public void augmentedAssignmentInsideFunctionUsesLocalSlot() {
      var source = """
          def f(value):
              value += 3
              value *= 2
              return value
          r = f(4)
          """;
      assertEquals(14L, eval(source, "r"));
    }

    @Test
    public void augmentedAssignmentOnParameterDoesNotChangeCallerValue() {
      var source = """
          def f(value):
              value += 1
              return value
          original = 10
          changed = f(original)
          """;
      var module = Compiler.run(source);
      assertEquals(10L, module.getGlobal("original"));
      assertEquals(11L, module.getGlobal("changed"));
    }
  }

  @Nested
  public final class Assignment {

    @Test
    public void chainedAssignment() {
      var module = Compiler.run("a = b = c = 1");
      assertEquals(1L, module.getGlobal("a"));
      assertEquals(1L, module.getGlobal("b"));
      assertEquals(1L, module.getGlobal("c"));
    }

    @Test
    public void reassignmentReferencingSelf() {
      assertEquals(2L, eval("x = 1\nx = x + 1", "x"));
    }

    @Test
    public void reassignmentDoesNotAffectEarlierCopies() {
      var module = Compiler.run("x = 1\ny = x\nx = 2");
      assertEquals(2L, module.getGlobal("x"));
      assertEquals(1L, module.getGlobal("y"));
    }
  }


  @Nested
  public final class Comparison {

    @Test
    public void comparisonOperators() {
      assertEquals(true, eval("x = 1 < 2", "x"));
      assertEquals(true, eval("x = 2 <= 2", "x"));
      assertEquals(true, eval("x = 3 > 2", "x"));
      assertEquals(true, eval("x = 3 >= 3", "x"));
      assertEquals(true, eval("x = 1 == 1", "x"));
      assertEquals(true, eval("x = 1 != 2", "x"));
    }

    @Test
    public void comparisonChainsEvaluateEachOperandOnce() {
      assertEquals(true, eval("x = 1 < 2 < 3 < 4", "x"));
      assertEquals(false, eval("x = 1 < 2 < 3 > 4", "x"));
      assertEquals(false, eval("x = 1 < 2 > 2", "x"));
      assertEquals(true, eval("x = 1 == 1 == 1", "x"));
      assertEquals(true, eval("x = 1 < 2 > 1", "x"));
    }

    @Test
    public void stringComparisons() {
      assertEquals(true, eval("x = 'a' < 'b'", "x"));
      assertEquals(true, eval("x = 'abc' > 'abb'", "x"));
      assertEquals(true, eval("x = 'abc' == 'abc'", "x"));
    }

    @Test
    public void equalityAcrossSupportedTypes() {
      assertEquals(true, eval("x = True == True", "x"));
      assertEquals(true, eval("x = None == None", "x"));
      assertEquals(false, eval("x = 'a' == 'b'", "x"));
      assertEquals(false, eval("x = 'a' != 'a'", "x"));
    }

    @Test
    public void containment() {
      assertEquals(true, eval("x = 'a' in 'abc'", "x"));
      assertEquals(false, eval("x = 'z' in 'abc'", "x"));
      assertEquals(true, eval("x = 'z' not in 'abc'", "x"));
      assertEquals(true, eval("x = 'abc' in 'abcdef'", "x"));
    }

    @Test
    public void containmentCombinedWithAnd() {
      assertEquals(true, eval("x = 'a' in 'abc' and 'b' in 'abc'", "x"));
      assertEquals(false, eval("x = 'a' in 'abc' and 'z' in 'abc'", "x"));
    }

    @Test
    public void comparisonOperatorsReturnFalseAsWellAsTrue() {
      assertEquals(false, eval("x = 2 < 1", "x"));
      assertEquals(false, eval("x = 2 <= 1", "x"));
      assertEquals(false, eval("x = 1 > 2", "x"));
      assertEquals(false, eval("x = 1 >= 2", "x"));
      assertEquals(false, eval("x = 1 == 2", "x"));
      assertEquals(false, eval("x = 1 != 1", "x"));
    }

    @Test
    public void comparisonChainsStopAfterFirstFalseComparison() {
      assertEquals(false, eval("x = 3 < 2 < undefined", "x"));
      assertEquals(false, eval("x = 1 > 2 < undefined", "x"));
      assertEquals(true, eval("x = 1 < 2 < 3 < 4 < 5", "x"));
    }

    @Test
    public void comparisonChainsCanMixOperators() {
      assertEquals(true, eval("x = 1 < 2 <= 2 == 2 < 3", "x"));
      assertEquals(false, eval("x = 1 < 2 != 2 < 3", "x"));
    }

    @Test
    public void containmentCanBeUsedInChains() {
      assertEquals(true, eval("x = 'a' in 'abc' == 'abc'", "x"));
      assertEquals(true, eval("x = 'a' in 'abc' and 'bc' in 'abcd'", "x"));
      assertEquals(false, eval("x = 'z' in 'abc' or 'z' in 'def'", "x"));
    }

    @Test
    public void stringOrderingUsesLexicographicComparison() {
      assertEquals(true, eval("x = 'abc' < 'abcd'", "x"));
      assertEquals(true, eval("x = 'b' > 'azzz'", "x"));
      assertEquals(true, eval("x = '' <= 'a'", "x"));
    }

    @Test
    public void equalValuesOfDifferentNumericRepresentationsCompare() {
      assertEquals(true, eval("x = 1 == 1.0", "x"));
      assertEquals(false, eval("x = 1 != 1.0", "x"));
    }
  }

  @Nested
  public final class BooleanOperator {

    @Test
    public void truthinessOfCommonValues() {
      assertEquals(false, eval("x = not 1", "x"));
      assertEquals(true, eval("x = not 0", "x"));
      assertEquals(false, eval("x = not 'a'", "x"));
      assertEquals(true, eval("x = not ''", "x"));
      assertEquals(true, eval("x = not None", "x"));
      assertEquals(false, eval("x = not True", "x"));
      assertEquals(true, eval("x = not False", "x"));
    }

    @Test
    public void andOrCornerCases() {
      assertEquals(2L, eval("x = 1 and 2", "x"));
      assertEquals(0L, eval("x = 0 or 0", "x"));
      assertEquals(3L, eval("x = 1 and 2 and 3", "x"));
      assertEquals(1L, eval("x = 1 or 2 or 3", "x"));
      assertEquals(3L, eval("x = 0 and 2 or 3", "x"));
      assertEquals(3L, eval("x = (1 or 2) and 3", "x"));
    }

    @Test
    public void andOrWithNone() {
      assertEquals("a", eval("x = None or 'a'", "x"));
      assertEquals(true, eval("x = (None and 'a') == None", "x"));
    }

    @Test
    public void shortCircuitAvoidsEvaluation() {
      // undefined would raise a NameError if it were evaluated
      assertEquals(false, eval("x = False and undefined", "x"));
      assertEquals(true, eval("x = True or undefined", "x"));
      assertEquals(1L, eval("x = 1 if True else undefined", "x"));
    }

    @Test
    public void chainedShortCircuitReturningOperands() {
      assertEquals(true, eval("x = (0 or '') == ''", "x"));
      assertEquals(true, eval("x = (0 and '') == 0", "x"));
    }

    @Test
    public void truthinessCoversNumericAndStringEdgeValues() {
      assertEquals(true, eval("x = not 0.0", "x"));
      assertEquals(true, eval("x = not -0.0", "x"));
      assertEquals(false, eval("x = not -0.25", "x"));
      assertEquals(true, eval("x = not ''", "x"));
      assertEquals(false, eval("x = not '0'", "x"));
    }

    @Test
    public void andAndOrReturnTheActualSelectedOperand() {
      assertEquals("", eval("x = 'left' and ''", "x"));
      assertEquals("left", eval("x = 'left' or 'right'", "x"));
      assertEquals(0L, eval("x = 0 and 'right'", "x"));
      assertEquals("right", eval("x = 0 or 'right'", "x"));
    }

    @Test
    public void andOrPrecedenceAndParentheses() {
      assertEquals(1L, eval("x = 1 or 0 and 2", "x"));
      assertEquals(0L, eval("x = (1 or 0) and 0", "x"));
      assertEquals(3L, eval("x = 0 or 1 and 3", "x"));
    }

    @Test
    public void everySkippedBooleanOperandRemainsUnevaluated() {
      assertEquals(false, eval("x = False and missing_a and missing_b", "x"));
      assertEquals(true, eval("x = True or missing_a or missing_b", "x"));
      assertEquals("ok", eval("x = None or '' or 'ok'", "x"));
    }

    @Test
    public void nestedNotAndBooleanExpressions() {
      assertEquals(true, eval("x = not (0 or '')", "x"));
      assertEquals(false, eval("x = not (1 and 'yes')", "x"));
      assertEquals(true, eval("x = not (1 and 0)", "x"));
    }
  }


  @Nested
  public final class ConditionalExpression {

    @Test
    public void nestedConditionalExpressions() {
      assertEquals(1L, eval("x = 1 if True else (2 if False else 3)", "x"));
      assertEquals(3L, eval("x = 1 if False else (2 if False else 3)", "x"));
      assertEquals(2L, eval("x = 1 if False else (2 if True else 3)", "x"));
    }

    @Test
    public void conditionalExpressionIsLazy() {
      assertEquals(1L, eval("x = 1 if True else undefined", "x"));
      assertEquals(2L, eval("x = undefined if False else 2", "x"));
    }

    @Test
    public void conditionalUsesTruthinessRatherThanBooleanIdentity() {
      assertEquals("yes", eval("x = 'yes' if 1 else 'no'", "x"));
      assertEquals("no", eval("x = 'yes' if 0 else 'no'", "x"));
      assertEquals("yes", eval("x = 'yes' if 'truthy' else 'no'", "x"));
      assertEquals("no", eval("x = 'yes' if '' else 'no'", "x"));
    }

    @Test
    public void nestedConditionalExpressionsChooseOnlyOneBranch() {
      assertEquals(30L, eval("x = 10 if False else 20 if False else 30", "x"));
      assertEquals(20L, eval("x = 10 if False else 20 if True else 30", "x"));
      assertEquals(10L, eval("x = 10 if True else 20 if True else 30", "x"));
    }

    @Test
    public void onlySelectedConditionalBranchIsEvaluated() {
      assertEquals(1L, eval("x = 1 if 1 else missing", "x"));
      assertEquals(2L, eval("x = missing if 0 else 2", "x"));
    }
  }


  @Nested
  public final class ControlFlow {

    // -- if / else

    @Test
    public void ifWithAndWithoutElse() {
      assertEquals(2L, eval("x = 1\nif True:\n    x = 2", "x"));
      assertEquals(1L, eval("x = 1\nif False:\n    x = 2", "x"));
      assertEquals(2L, eval("x = 1\nif False:\n    x = 1\nelse:\n    x = 2", "x"));
    }

    @Test
    public void ifVariableAssignedInsideIsReadable() {
      assertEquals(10L, eval("x = 1\nif x:\n    y = 10\nr = y", "r"));
    }

    @Test
    public void elifSelectsTheFirstTruthyBranch() {
      var source = """
          value = 0
          if False:
              value = 1
          elif 0:
              value = 2
          elif 'yes':
              value = 3
          else:
              value = 4
          r = value
          """;
      assertEquals(3L, eval(source, "r"));
    }

    @Test
    public void elifFallsThroughToElse() {
      var source = """
          value = 0
          if False:
              value = 1
          elif False:
              value = 2
          else:
              value = 3
          r = value
          """;
      assertEquals(3L, eval(source, "r"));
    }

    @Test
    public void nestedIfElseBindsToNearestIf() {
      var source = """
          value = 0
          if True:
              if False:
                  value = 1
              else:
                  value = 2
          else:
              value = 3
          r = value
          """;
      assertEquals(2L, eval(source, "r"));
    }

    @Test
    public void branchesCanContainMultipleStatements() {
      var source = """
          x = 1
          y = 2
          if True:
              x += 4
              y *= 3
              z = x + y
          r = z
          """;
      assertEquals(11L, eval(source, "r"));
    }

    // -- while

    @Test
    public void whileElseWithoutBreak() {
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
    public void whileElseWithBreak() {
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
    public void whileContinue() {
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
    public void whileFalseSkipsBody() {
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

    @Test
    public void whileConditionIsReevaluatedAfterEachIteration() {
      var source = """
          i = 0
          total = 0
          while i < 4:
              total += i
              i += 1
          r = total
          """;
      assertEquals(6L, eval(source, "r"));
    }

    @Test
    public void whileElseRunsWhenConditionIsInitiallyFalse() {
      var source = """
          value = 1
          while False:
              value = 2
          else:
              value = 3
          r = value
          """;
      assertEquals(3L, eval(source, "r"));
    }

    @Test
    public void whileElseRunsAfterContinueAndNormalExhaustion() {
      var source = """
          i = 0
          total = 0
          while i < 4:
              i += 1
              if i % 2 == 0:
                  continue
              total += i
          else:
              total += 10
          r = total
          """;
      assertEquals(14L, eval(source, "r"));
    }

    @Test
    public void breakSkipsWhileElseEvenWhenNestedInIf() {
      var source = """
          i = 0
          value = 0
          while i < 10:
              i += 1
              if i == 2:
                  if True:
                      break
          else:
              value = 100
          r = i + value
          """;
      assertEquals(2L, eval(source, "r"));
    }

    @Test
    public void nestedWhileLoopsUseTheirOwnContinueTargets() {
      var source = """
          outer = 0
          total = 0
          while outer < 3:
              outer += 1
              inner = 0
              while inner < 3:
                  inner += 1
                  if inner == 2:
                      continue
                  total += 1
          r = total
          """;
      assertEquals(6L, eval(source, "r"));
    }

    @Test
    public void nestedWhileBreakOnlyExitsInnerLoop() {
      var source = """
          outer = 0
          total = 0
          while outer < 3:
              outer += 1
              inner = 0
              while inner < 5:
                  inner += 1
                  if inner == 2:
                      break
                  total += 1
          r = total
          """;
      assertEquals(3L, eval(source, "r"));
    }

    // -- for

    @Test
    public void forElseWithoutBreak() {
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
    public void forElseWithBreak() {
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
    public void forEmptyIterableRunsElse() {
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
    public void forContinue() {
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
    public void forVariableVisibleAfterLoop() {
      assertEquals("b", eval("for c in 'ab':\n    x = c\nr = x", "r"));
    }

    @Test
    public void nestedLoopsBreakInnerOnly() {
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
    public void nestedLoopsContinueInnerOnly() {
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

    @Test
    public void forIteratesEveryStringCharacterInOrder() {
      var source = """
          result = ''
          for ch in 'abcd':
              result += ch
          r = result
          """;
      assertEquals("abcd", eval(source, "r"));
    }

    @Test
    public void forIteratesSupplementaryUnicodeAsOneCodePoint() {
      var source = """
          count = 0
          result = ''
          for ch in 'a😀b':
              count += 1
              result += ch
          r1 = count
          r2 = result
          """;
      var module = Compiler.run(source);
      assertEquals(3L, module.getGlobal("r1"));
      assertEquals("a😀b", module.getGlobal("r2"));
    }

    @Test
    public void forElseRunsAfterContinueAndNormalExhaustion() {
      var source = """
          total = 0
          for ch in 'abc':
              if ch == 'b':
                  continue
              total += 1
          else:
              total += 10
          r = total
          """;
      assertEquals(12L, eval(source, "r"));
    }

    @Test
    public void nestedForLoopsReuseLoopVariableSlotsSafely() {
      var source = """
          total = 0
          for a in 'ab':
              for b in 'xyz':
                  total += 1
          r = total
          """;
      assertEquals(6L, eval(source, "r"));
    }

    @Test
    public void innerForBreakDoesNotSuppressOuterForElse() {
      var source = """
          total = 0
          for a in 'ab':
              for b in 'xyz':
                  break
              total += 1
          else:
              total += 10
          r = total
          """;
      assertEquals(12L, eval(source, "r"));
    }

    @Test
    public void forLoopVariableRetainsLastValueAfterExhaustion() {
      assertEquals("c", eval("for ch in 'abc':\n    last = ch\nr = ch", "r"));
    }

    @Test
    public void forLoopCanAccumulateAStringAndACharacterCount() {
      var source = """
          count = 0
          joined = ''
          for ch in 'xy':
              count += 1
              joined += ch
          r = count * 10 + (1 if joined == 'xy' else 0)
          """;
      assertEquals(21L, eval(source, "r"));
    }
  }


  @Nested
  public final class Function {

    @Test
    public void zeroArgumentFunction() {
      assertEquals(42L, eval("def f():\n    return 42\nr = f()", "r"));
    }

    @Test
    public void defaultReturnIsNone() {
      assertEquals(true, eval("def f():\n    x = 1\nr = f() == None", "r"));
    }

    @Test
    public void explicitReturnNone() {
      assertEquals(true, eval("def f():\n    return None\nr = f() == None", "r"));
    }

    @Test
    public void returnInsideForLoop() {
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
    public void earlyReturnInsideIf() {
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
    public void restParameterOnly() {
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

    @Test
    public void oneAndSeveralPositionalParameters() {
      var source = """
          def one(a):
              return a
          def many(a, b, c, d, e):
              return a + b + c + d + e
          r1 = one(42)
          r2 = many(1, 2, 3, 4, 5)
          """;
      var module = Compiler.run(source);
      assertEquals(42L, module.getGlobal("r1"));
      assertEquals(15L, module.getGlobal("r2"));
    }

    @Test
    public void parametersCanBeReassignedIndependently() {
      var source = """
          def f(a, b):
              a += 10
              b *= 2
              return a + b
          r = f(1, 3)
          """;
      assertEquals(17L, eval(source, "r"));
    }

    @Test
    public void functionCanReturnFromNestedControlFlow() {
      var source = """
          def choose(x):
              if x < 0:
                  return -1
              elif x == 0:
                  return 0
              else:
                  return 1
          a = choose(-5)
          b = choose(0)
          c = choose(7)
          """;
      var module = Compiler.run(source);
      assertEquals(-1L, module.getGlobal("a"));
      assertEquals(0L, module.getGlobal("b"));
      assertEquals(1L, module.getGlobal("c"));
    }

    @Test
    public void directRecursionWorksAcrossRepeatedCalls() {
      var source = """
          def factorial(n):
              if n <= 1:
                  return 1
              return n * factorial(n - 1)
          a = factorial(5)
          b = factorial(6)
          """;
      var module = Compiler.run(source);
      assertEquals(120L, module.getGlobal("a"));
      assertEquals(720L, module.getGlobal("b"));
    }

    @Test
    public void repeatedCallsUseTheSameFunctionCorrectly() {
      var source = """
          def add(a, b):
              return a + b
          total = 0
          total += add(1, 2)
          total += add(3, 4)
          total += add(5, 6)
          r = total
          """;
      assertEquals(21L, eval(source, "r"));
    }

    @Test
    public void functionValuesCanBePassedAndCalledAtOneCallSite() {
      var source = """
          def increment(x):
              return x + 1
          def double(x):
              return x * 2
          def apply(fn, value):
              return fn(value)
          a = apply(increment, 10)
          b = apply(double, 10)
          """;
      var module = Compiler.run(source);
      assertEquals(11L, module.getGlobal("a"));
      assertEquals(20L, module.getGlobal("b"));
    }

    @Test
    public void functionCanBeAliasedBeforeOriginalNameIsRebound() {
      var source = """
          def f():
              return 1
          old_f = f
          def f():
              return 2
          a = old_f()
          b = f()
          """;
      var module = Compiler.run(source);
      assertEquals(1L, module.getGlobal("a"));
      assertEquals(2L, module.getGlobal("b"));
    }

    @Test
    public void fixedParametersAndEmptyRestArray() {
      var source = """
          def f(first, *rest):
              count = 0
              for item in rest:
                  count += 1
              return first + count
          a = f(10)
          b = f(10, 20, 30)
          """;
      var module = Compiler.run(source);
      assertEquals(10L, module.getGlobal("a"));
      assertEquals(12L, module.getGlobal("b"));
    }

    @Test
    public void fixedParametersAndRestValuesCanBeAccumulated() {
      var source = """
          def sum_from(first, *rest):
              total = first
              for item in rest:
                  total += item
              return total
          r1 = sum_from(5)
          r2 = sum_from(5, 1, 2, 3)
          """;
      var module = Compiler.run(source);
      assertEquals(5L, module.getGlobal("r1"));
      assertEquals(11L, module.getGlobal("r2"));
    }

    @Test
    public void varargsFunctionAcceptsManyArguments() {
      var source = """
          def count(*items):
              n = 0
              for item in items:
                  n += 1
              return n
          r = count(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
          """;
      assertEquals(10L, eval(source, "r"));
    }

    @Test
    public void tooFewArgumentsRaisesTypeError() {
      assertThrows(PyTypeError.class, () -> Compiler.run("def f(a, b):\n    return a + b\nf(1)"));
    }

    @Test
    public void tooManyArgumentsRaisesTypeErrorForFixedArityFunction() {
      assertThrows(PyTypeError.class, () -> Compiler.run("def f(a):\n    return a\nf(1, 2)"));
    }

    @Test
    public void missingFixedArgumentRaisesTypeErrorForVarargsFunction() {
      assertThrows(PyTypeError.class, () -> Compiler.run("def f(a, *rest):\n    return a\nf()"));
    }

    @Test
    public void callingFunctionWithWrongArityAfterEarlierValidCallStillFails() {
      var source = """
          def f(value):
              return value
          first = f(1)
          f(1, 2)
          """;
      assertThrows(PyTypeError.class, () -> Compiler.run(source));
    }
  }

  @Nested
  public final class Closure {

    @Test
    public void captureByValueLocal() {
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
    public void captureByReferenceGlobal() {
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
    public void independentCapturesPerCall() {
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
    public void deepNesting() {
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
    public void captureThroughNestedLevels() {
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
    public void captureAndSelfRecursion() {
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

    @Test
    public void multipleCapturedValuesKeepTheirDeclaredOrder() {
      var source = """
          def outer(a):
              b = a + 10
              c = b + 100
              def inner(d):
                  return a + b + c + d
              return inner(1)
          r = outer(2)
          """;
      assertEquals(127L, eval(source, "r"));
    }

    @Test
    public void differentClosuresFromSameDefinitionKeepIndependentCaptures() {
      var source = """
          def make(value):
              def read():
                  return value
              return read
          first = make(10)
          second = make(20)
          a = first()
          b = second()
          c = first()
          """;
      var module = Compiler.run(source);
      assertEquals(10L, module.getGlobal("a"));
      assertEquals(20L, module.getGlobal("b"));
      assertEquals(10L, module.getGlobal("c"));
    }

    @Test
    public void closureCanCaptureParameterAndLocalTogether() {
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
    public void nestedClosureCanCaptureThroughMoreThanOneLexicalLevel() {
      var source = """
          def outer(value):
              def middle():
                  def inner():
                      return value
                  return inner
              return middle()
          f = outer(42)
          r = f()
          """;
      assertEquals(42L, eval(source, "r"));
    }

    @Test
    public void localCaptureAndGlobalLookupCanCoexist() {
      var source = """
          global_value = 1
          def outer(local_value):
              def inner():
                  return global_value + local_value
              return inner
          f = outer(10)
          global_value = 5
          r = f()
          """;
      assertEquals(15L, eval(source, "r"));
    }

    @Test
    public void innerLocalShadowsOuterCapture() {
      var source = """
          def outer():
              value = 1
              def inner():
                  value = 2
                  return value
              return inner()
          r = outer()
          """;
      assertEquals(2L, eval(source, "r"));
    }

    @Test
    public void closuresCreatedInSeparateCallsDoNotShareCaptureState() {
      var source = """
          def make(value):
              def get():
                  return value
              return get
          a = make(3)
          b = make(8)
          r = a() * 10 + b()
          """;
      assertEquals(38L, eval(source, "r"));
    }
  }

  @Nested
  public final class Scope {

    @Test
    public void globalReadFromFunction() {
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
    public void localAssignmentDoesNotTouchGlobal() {
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

    @Test
    public void functionReadsGlobalAssignedAfterDefinition() {
      var source = """
          def read_later():
              return later
          later = 73
          r = read_later()
          """;
      assertEquals(73L, eval(source, "r"));
    }

    @Test
    public void functionReadsMostRecentGlobalValue() {
      var source = """
          value = 1
          def read_value():
              return value
          first = read_value()
          value = 99
          second = read_value()
          """;
      var module = Compiler.run(source);
      assertEquals(1L, module.getGlobal("first"));
      assertEquals(99L, module.getGlobal("second"));
    }

    @Test
    public void localNameShadowsGlobalForEntireFunction() {
      var source = """
          value = 100
          def f():
              value = 7
              return value
          local = f()
          global_copy = value
          """;
      var module = Compiler.run(source);
      assertEquals(7L, module.getGlobal("local"));
      assertEquals(100L, module.getGlobal("global_copy"));
    }

    @Test
    public void undefinedGlobalRaisesNameErrorWhenFunctionIsCalled() {
      var source = """
          def f():
              return never_defined
          f()
          """;
      assertThrows(PyNameError.class, () -> Compiler.run(source));
    }

    @Test
    public void nameAssignedOnlyInsideFunctionDoesNotBecomeGlobal() {
      var source = """
          def f():
              created_inside = 1
          f()
          result = created_inside
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
      assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = 1\ny = x[1:2]"));
      assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = 1\ny = x.m()"));
      assertThrows(UnsupportedSyntaxException.class, () -> Compiler.run("x = (1, 2)"));
    }

    @Test
    public void invalidNumericAndBitwiseOperandCombinationsRaiseTypeError() {
      assertThrows(PyTypeError.class, () -> Compiler.run("x = 1 - 'a'"));
      assertThrows(PyTypeError.class, () -> Compiler.run("x = 1 * None"));
      assertThrows(PyTypeError.class, () -> Compiler.run("x = 1 << 1.5"));
      assertThrows(PyTypeError.class, () -> Compiler.run("x = 'a' // 2"));
    }

    @Test
    public void callingNonFunctionValuesRaisesTypeError() {
      assertThrows(PyTypeError.class, () -> Compiler.run("value = 42\nvalue()"));
      assertThrows(PyTypeError.class, () -> Compiler.run("value = 'text'\nvalue(1)"));
    }

    @Test
    public void readingUndefinedTopLevelNameRaisesNameError() {
      assertThrows(PyNameError.class, () -> Compiler.run("result = not_defined"));
    }

    @Test
    public void unsupportedTupleTargetsRemainRejected() {
      assertThrows(UnsupportedSyntaxException.class,
          () -> Compiler.run("first, second = 1, 2"));
    }
  }

  @Nested
  public final class BytecodeStress {
    @Test
    public void manyMainLocalsReceiveDistinctSlots() {
      var source = new StringBuilder();
      var expected = 0L;
      for (int i = 0; i < 100; i++) {
        source.append('v').append(i).append(" = ").append(i).append('\n');
        expected += i;
      }
      source.append("result = ");
      for (int i = 0; i < 100; i++) {
        if (i != 0) {
          source.append(" + ");
        }
        source.append('v').append(i);
      }
      source.append('\n');
      assertEquals(expected, eval(source.toString(), "result"));
    }

    @Test
    public void manyFunctionLocalsAndTemporariesDoNotOverlap() {
      var source = new StringBuilder("def calculate(seed):\n    total = seed\n");
      for (int i = 0; i < 60; i++) {
        source.append("    value").append(i).append(" = ").append(i).append('\n');
        source.append("    total += value").append(i).append('\n');
      }
      source.append("    return total\nr = calculate(10)\n");
      assertEquals(10L + (59L * 60L / 2L), eval(source.toString(), "r"));
    }

    @Test
    public void manyFunctionParametersAreLoadedFromCorrectSlots() {
      var source = """
          def combine(a, b, c, d, e, f, g, h):
              return a + b * 10 + c * 100 + d * 1000 + e * 10000 + f * 100000 + g * 1000000 + h * 10000000
          r = combine(1, 2, 3, 4, 5, 6, 7, 8)
          """;
      assertEquals(87_654_321L, eval(source, "r"));
    }

    @Test
    public void chainedComparisonUsesManyIntermediateValues() {
      assertEquals(true, eval("x = 0 < 1 < 2 < 3 < 4 < 5 < 6 < 7 < 8 < 9", "x"));
    }

    @Test
    public void nestedBranchesAndLoopsPreserveStackAndLoopTargets() {
      var source = """
          total = 0
          for outer in 'abc':
              i = 0
              while i < 4:
                  i += 1
                  if i == 2:
                      continue
                  elif i == 4:
                      break
                  else:
                      if outer == 'b':
                          total += 2
                      else:
                          total += 1
          else:
              total += 10
          r = total
          """;
      assertEquals(18L, eval(source, "r"));
    }

    @Test
    public void manyCapturedNamesAreBoundToTheirOwnValues() {
      var source = """
          def outer():
              a = 1
              b = 2
              c = 3
              d = 4
              e = 5
              f = 6
              def inner():
                  return a + b + c + d + e + f
              return inner
          get_values = outer()
          r = get_values()
          """;
      assertEquals(21L, eval(source, "r"));
    }
  }
}