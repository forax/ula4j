package com.github.forax.ula4j.compiler;

import com.github.forax.ula4j.ast.ASTBuilder;
import com.github.forax.ula4j.ast.Script;
import com.github.forax.ula4j.ast.Stmt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public final class ScopeAnalyzerTest {

  private static Map<Stmt.FunctionDef, ScopeAnalyzer.FunctionAnalysis> analyze(String source) {
    var script = ASTBuilder.createScript(source);
    return ScopeAnalyzer.analyze(script);
  }

  private static void assertAccepts(String source) {
    analyze(source);
  }

  private static void assertRejected(String source, String messageFragment) {
    var ex = assertThrows(SemanticErrorException.class, () -> analyze(source));
    assertTrue(ex.getMessage().contains(messageFragment),
        () -> "expected message containing '" + messageFragment
            + "' but was: " + ex.getMessage());
  }

  private static ScopeAnalyzer.FunctionAnalysis byName(
      Map<Stmt.FunctionDef, ScopeAnalyzer.FunctionAnalysis> result, String name) {
    for (var entry : result.entrySet()) {
      if (entry.getKey().name().equals(name)) {
        return entry.getValue();
      }
    }
    var names = result.keySet().stream().map(Stmt.FunctionDef::name).toList();
    throw new AssertionError("no function '" + name + "' among " + names);
  }

  // =====================================================================
  // Pass 1 - locals, parameters, scope tree
  // =====================================================================

  @Nested
  public final class BindingsAndScopeTree {

    @Test
    public void mapIsKeyedOnTheAstNode() {
      var script = ASTBuilder.createScript("""
                    def f():
                        pass
                    """);
      var result = ScopeAnalyzer.analyze(script);
      var node = (Stmt.FunctionDef) script.body().stmts().get(0);
      assertSame(node, result.keySet().iterator().next());
    }

    @Test
    public void emptyScript() {
      var result = analyze("");
      assertTrue(result.isEmpty());
    }

    @Test
    public void emptyFunctionBody() {
      var result = analyze("""
                    def f():
                        pass
                    """);
      assertTrue(byName(result, "f").locals().isEmpty());
    }

    @Test
    public void parametersAreLocals() {
      var result = analyze("""
                    def f(a, b):
                        pass
                    """);
      assertEquals(Set.of("a", "b"), byName(result, "f").locals());
    }

    @Test
    public void restParameterIsLocal() {
      var result = analyze("""
                    def f(a, *rest):
                        pass
                    """);
      assertEquals(Set.of("a", "rest"), byName(result, "f").locals());
    }

    @Test
    public void simpleAssignmentCreatesLocal() {
      var result = analyze("""
                    def f():
                        x = 1
                    """);
      assertTrue(byName(result, "f").locals().contains("x"));
    }

    @Test
    public void augmentedAssignmentCreatesLocal() {
      var result = analyze("""
                    def f():
                        x = 0
                        x += 1
                    """);
      assertTrue(byName(result, "f").locals().contains("x"));
    }

    @Test
    public void forTargetCreatesLocal() {
      var result = analyze("""
                    def f():
                        for i in [1, 2]:
                            pass
                    """);
      assertTrue(byName(result, "f").locals().contains("i"));
    }

    @Test
    public void multipleAssignmentTargetsAreAllLocals() {
      var result = analyze("""
                    def f():
                        a = b = 1
                    """);
      assertEquals(Set.of("a", "b"), byName(result, "f").locals());
    }

    @Test
    public void tupleUnpackingTargetsAreLocals() {
      var result = analyze("""
                    def f():
                        a, b = 1, 2
                    """);
      assertEquals(Set.of("a", "b"), byName(result, "f").locals());
    }

    @Test
    public void nestedTupleUnpackingTargetsAreLocals() {
      var result = analyze("""
                    def f():
                        a, (b, c) = 1, (2, 3)
                    """);
      assertEquals(Set.of("a", "b", "c"), byName(result, "f").locals());
    }

    @Test
    public void subscriptTargetDoesNotBind() {
      var result = analyze("""
                    def f():
                        a = [0]
                        a[0] = 1
                    """);
      assertEquals(Set.of("a"), byName(result, "f").locals());
    }

    @Test
    public void nestedFunctionNameIsLocalOfEnclosing() {
      var result = analyze("""
                    def outer():
                        def inner():
                            pass
                    """);
      assertTrue(byName(result, "outer").locals().contains("inner"));
      assertTrue(byName(result, "inner").locals().isEmpty());
    }

    @Test
    public void noBlockScoping() {
      var result = analyze("""
                    def f():
                        x = 0
                        if True:
                            x = 1
                        while False:
                            x = 2
                    """);
      assertTrue(byName(result, "f").locals().contains("x"));
    }

    @Test
    public void moduleNamesDoNotLeakIntoFunctions() {
      var result = analyze("""
                    x = 1
                    def f():
                        y = 2
                        return x + y
                    """);
      assertEquals(Set.of("y"), byName(result, "f").locals());
    }
  }

  // =====================================================================
  // Pass 2 - free-variable resolution
  // =====================================================================

  @Nested
  public final class FreeVariableResolution {

    @Test
    public void simpleCapture() {
      var result = analyze("""
                    def outer():
                        x = 1
                        def inner():
                            return x
                        return inner
                    """);
      assertEquals(Set.of("x"), byName(result, "inner").freeVariables());
      assertTrue(byName(result, "outer").freeVariables().isEmpty());
    }

    @Test
    public void noCaptureWhenLocalInSameFunction() {
      var result = analyze("""
                    def f():
                        x = 1
                        return x
                    """);
      assertTrue(byName(result, "f").freeVariables().isEmpty());
    }

    @Test
    public void moduleGlobalIsNotCaptured() {
      // module is not a function scope, so 'x' is a global reference, not a capture.
      var result = analyze("""
                    x = 1
                    def f():
                        return x
                    """);
      assertTrue(byName(result, "f").freeVariables().isEmpty());
    }

    @Test
    public void closureChainPropagation() {
      // 'middle' never reads y directly, but must re-export it for 'inner'.
      var result = analyze("""
                    def outer():
                        y = 1
                        def middle():
                            def inner():
                                return y
                        return middle
                    """);
      assertEquals(Set.of("y"), byName(result, "middle").freeVariables());
      assertEquals(Set.of("y"), byName(result, "inner").freeVariables());
    }

    @Test
    public void parameterIsCaptured() {
      var result = analyze("""
                    def outer(p):
                        def inner():
                            return p
                        return inner
                    """);
      assertEquals(Set.of("p"), byName(result, "inner").freeVariables());
    }

    @Test
    public void selfRecursionIsCaptured() {
      var result = analyze("""
                    def outer():
                        def fact(n):
                            return 1 if n <= 1 else n * fact(n - 1)
                        return fact
                    """);
      assertEquals(Set.of("fact"), byName(result, "fact").freeVariables());
    }

    @Test
    public void deepNestingPropagatesFullChain() {
      var result = analyze("""
                    def a():
                        x = 1
                        def b():
                            def c():
                                def d():
                                    return x
                    """);
      assertEquals(Set.of("x"), byName(result, "b").freeVariables());
      assertEquals(Set.of("x"), byName(result, "c").freeVariables());
      assertEquals(Set.of("x"), byName(result, "d").freeVariables());
    }

    @Test
    public void freeVariableSetDoesNotContainLocals() {
      var result = analyze("""
                    def outer():
                        x = 1
                        def inner():
                            y = 2
                            return x + y
                        return inner
                    """);
      assertEquals(Set.of("x"), byName(result, "inner").freeVariables());
      assertFalse(byName(result, "inner").freeVariables().contains("y"));
    }
  }

  // =====================================================================
  // Pass 3 - effectively final (captured variable never changes)
  // =====================================================================

  @Nested
  public final class EffectivelyFinal {

    @Test
    public void assignedOnceAndCaptured() {
      assertAccepts("""
                    def outer():
                        x = 1
                        def inner():
                            return x
                        return inner
                    """);
    }

    @Test
    public void reassignedCapturedLocalRejected() {
      assertRejected("""
                    def outer():
                        x = 1
                        x = 2
                        def inner():
                            return x
                        return inner
                    """, "assigned 2 times");
    }

    @Test
    public void augAssignCountsAsReassignment() {
      assertRejected("""
                    def outer():
                        x = 1
                        x += 2
                        def inner():
                            return x
                        return inner
                    """, "assigned 2 times");
    }

    @Test
    public void reassignedCapturedParameterRejected() {
      assertRejected("""
                    def outer(p):
                        p = 1
                        def inner():
                            return p
                        return inner
                    """, "reassigned");
    }

    @Test
    public void neverReassignedCapturedParameterAccepted() {
      assertAccepts("""
                    def outer(p):
                        def inner():
                            return p
                        return inner
                    """);
    }

    @Test
    public void reassignedLocalNotCapturedAccepted() {
      assertAccepts("""
                    def f():
                        x = 1
                        x = 2
                        return x
                    """);
    }

    @Test
    public void reassignedParameterNotCapturedAccepted() {
      assertAccepts("""
                    def f(p):
                        p = 1
                        return p
                    """);
    }

    @Test
    public void reassignedSelfRecursiveNameRejected() {
      assertRejected("""
                    def outer():
                        def fact(n):
                            return 1 if n <= 1 else n * fact(n - 1)
                        fact = 5
                        return fact
                    """, "assigned 2 times");
    }

    @Test
    public void shadowedInIntermediateFunctionIsNotACapture() {
      assertAccepts("""
          def outer():
              x = 1
              x = 2
              def middle():
                  x = 3
                  def inner():
                      return x
                  return inner
              return middle
          """);
    }

    @Test
    public void moduleVariableReassignedAndShadowedInFunction() {
      assertAccepts("""
          x = 1
          x = 2
          def outer():
              x = 3
              def inner():
                  return x
              return inner
          """);
    }

    @Test
    public void reassignedCapturedThroughIntermediateFunctionRejected() {
      assertRejected("""
          def outer():
              x = 1
              x = 2
              def middle():
                  def inner():
                      return x
                  return inner
              return middle
          """, "captured by 'middle'");
    }

    @Test
    public void capturedForTargetRejected() {
      assertRejected("""
          def outer(xs):
              fs = []
              for i in xs:
                  def g():
                      return i
                  fs.append(g)
              return fs
          """, "assigned inside a loop");
    }

    @Test
    public void capturedWhileBodyAssignmentRejected() {
      assertRejected("""
          def outer(c):
              while c:
                  x = 1
                  def inner():
                      return x
          """, "assigned inside a loop");
    }

    @Test
    public void selfRecursiveDefInsideLoopRejected() {
      assertRejected("""
          def outer(xs):
              for i in xs:
                  def f(n):
                      return f(n)
          """, "assigned inside a loop");
    }

    @Test
    public void loopElseAssignmentAcceptedWhenNotNestedInLoop() {
      assertAccepts("""
          def outer(c):
              while c:
                  pass
              else:
                  x = 1
              def inner():
                  return x
              return inner
          """);
    }

    @Test
    public void loopElseAssignmentRejectedWhenEnclosingLoop() {
      assertRejected("""
          def outer(c, d):
              while d:
                  while c:
                      pass
                  else:
                      x = 1
                  def inner():
                      return x
          """, "assigned inside a loop");
    }

    @Test
    public void loopVariableNotCapturedAccepted() {
      assertAccepts("""
          def f(xs):
              for i in xs:
                  pass
          """);
    }

    @Test
    public void factoryFunctionIsTheWorkaround() {
      assertAccepts("""
          def outer(xs):
              def make(v):
                  def get():
                      return v
                  return get
              fs = []
              for i in xs:
                  fs.append(make(i))
              return fs
          """);
    }

    @Test
    public void firstCapturingChildIsNamedInError() {
      assertRejected("""
          def outer():
              x = 1
              x = 2
              def g():
                  return x
              def h():
                  return x
              return g
          """, "captured by 'g'");
    }
  }

  // =====================================================================
  // Pass 4 - definite assignment
  // =====================================================================

  @Nested
  public final class DefiniteAssignment {

    // ---------------------------------------------------------------
    // Basic flow
    // ---------------------------------------------------------------

    @Test
    public void parameterIsAssignedAtEntry() {
      assertAccepts("""
                    def f(p):
                        return p
                    """);
    }

    @Test
    public void assignThenUse() {
      assertAccepts("""
                    def f():
                        x = 1
                        return x
                    """);
    }

    @Test
    public void readBeforeAssignRejected() {
      assertRejected("""
                    def f():
                        return x
                        x = 1
                    """, "may be used before assignment");
    }

    @Test
    public void augAssignReadsBeforeWriting() {
      assertRejected("""
                    def f():
                        x += 1
                    """, "may be used before assignment");
    }

    @Test
    public void multiTargetLeftToRight() {
      // x = x[0] = []  binds x first, then reads x for the subscript target.
      assertAccepts("""
                    def f():
                        x = x[0] = []
                    """);
    }

    @Test
    public void multiTargetLeftToRightReverseOrderRejected() {
      assertRejected("""
                    def f():
                        x[0] = x = []
                    """, "may be used before assignment");
    }

    @Test
    public void chainedAssignment() {
      assertAccepts("""
                    def f():
                        a = b = c = 1
                        return a + b + c
                    """);
    }

    @Test
    public void sameNameChainedAssignmentAccepted() {
      assertAccepts("""
          def f():
              x = x = 1
              return x
          """);
    }

    // ---------------------------------------------------------------
    // if / elif / else
    // ---------------------------------------------------------------

    @Test
    public void ifBothBranchesAssign() {
      assertAccepts("""
                    def f(c):
                        if c:
                            x = 1
                        else:
                            x = 2
                        return x
                    """);
    }

    @Test
    public void ifOneBranchAssignsRejected() {
      assertRejected("""
                    def f(c):
                        if c:
                            x = 1
                        return x
                    """, "may be used before assignment");
    }

    @Test
    public void elifChainNotExhaustiveRejected() {
      assertRejected("""
                    def f(a, b):
                        if a:
                            x = 1
                        elif b:
                            x = 2
                        return x
                    """, "may be used before assignment");
    }

    @Test
    public void elifChainWithElseAccepts() {
      assertAccepts("""
                    def f(a, b):
                        if a:
                            x = 1
                        elif b:
                            x = 2
                        else:
                            x = 3
                        return x
                    """);
    }

    @Test
    public void oneBranchReturnsOtherAssigns() {
      assertAccepts("""
                    def f(c):
                        if c:
                            return 0
                        x = 1
                        return x
                    """);
    }

    @Test
    public void bothBranchesReturnMakesUnreachable() {
      assertAccepts("""
                    def f(c):
                        if c:
                            return 1
                        else:
                            return 2
                        x = 3
                        return x
                    """);
    }

    // ---------------------------------------------------------------
    // while / for
    // ---------------------------------------------------------------

    @Test
    public void whileZeroIterationsRejected() {
      assertRejected("""
                    def f(c):
                        while c:
                            x = 1
                        return x
                    """, "may be used before assignment");
    }

    @Test
    public void forZeroIterationsRejected() {
      assertRejected("""
                    def f(xs):
                        for i in xs:
                            x = 1
                        return x
                    """, "may be used before assignment");
    }

    @Test
    public void whileWithElseAssigns() {
      assertAccepts("""
                    def f(c):
                        while c:
                            pass
                        else:
                            x = 1
                        return x
                    """);
    }

    @Test
    public void forWithElseAssigns() {
      assertAccepts("""
                    def f(xs):
                        for i in xs:
                            pass
                        else:
                            x = 1
                        return x
                    """);
    }

    @Test
    public void whileBodyAssignsBeforeUse() {
      assertAccepts("""
                    def f(c):
                        while c:
                            x = 1
                            return x
                    """);
    }

    @Test
    public void breakSkipsElse() {
      assertRejected("""
                    def f(c):
                        while c:
                            if c:
                                break
                        else:
                            x = 1
                        return x
                    """, "may be used before assignment");
    }

    @Test
    public void breakAssignsSkipsElseAccepted() {
      assertAccepts("""
                    def f(c):
                        while c:
                            if c:
                                x = 1
                                break
                        else:
                            x = 2
                        return x
                    """);
    }

    @Test
    public void whileExitsOnlyViaReturn() {
      // while whose else returns, and no break: following code is unreachable.
      assertAccepts("""
                    def f(c):
                        while c:
                            pass
                        else:
                            return 0
                        x = 1
                        return x
                    """);
    }

    @Test
    public void forTargetCheckedAgainstBase() {
      // 'a' is local (assigned in the `if c:` branch) but not definitely assigned
      // at the `for`, so reading it for the subscript target is rejected.
      assertRejected("""
            def f(xs, c):
                if c:
                    a = [0]
                for a[0] in xs:
                    pass
            """, "may be used before assignment");
    }

    @Test
    public void forSubscriptTargetWithBaseAssigned() {
      // 'a' is local and definitely assigned before the loop.
      assertAccepts("""
            def f(xs):
                a = [0]
                for a[0] in xs:
                    pass
            """);
    }

    @Test
    public void whileTrueIsTreatedAsReachableTail() {
      assertAccepts("""
          def f():
              while True:
                  pass
              x = 1
              return x
          """);
    }

    @Test
    public void forTargetBoundBeforeBody() {
      assertAccepts("""
          def f(xs):
              for i in xs:
                  return i
          """);
    }

    // ---------------------------------------------------------------
    // return / break / continue / unreachable tail
    // ---------------------------------------------------------------

    @Test
    public void returnMakesFollowingUnreachable() {
      assertAccepts("""
                    def f():
                        return 1
                        x = 2
                        return x
                    """);
    }

    @Test
    public void continueMakesFollowingUnreachable() {
      assertAccepts("""
                    def f(c):
                        while c:
                            continue
                            x = 1
                            return x
                    """);
    }

    @Test
    public void breakMakesFollowingUnreachable() {
      assertAccepts("""
                    def f(c):
                        while c:
                            break
                            x = 1
                            return x
                    """);
    }

    @Test
    public void unreachableDefAfterReturnBodyNotChecked() {
      assertAccepts("""
          def outer():
              return 1
              def inner():
                  return y
              y = 1
          """);
    }

    // ---------------------------------------------------------------
    // expressions: conditional, short-circuit
    // ---------------------------------------------------------------

    @Test
    public void conditionalExpressionChecksBothBranches() {
      assertRejected("""
                    def f(c):
                        y = 1 if c else x
                        x = 0
                        return y
                    """, "may be used before assignment");
    }

    @Test
    public void conditionalExpressionAcceptedWhenAllAssigned() {
      assertAccepts("""
                    def f(c):
                        x = 0
                        y = 1 if c else x
                        return y
                    """);
    }

    @Test
    public void shortCircuitAndChecksRightOperand() {
      assertRejected("""
            def f(c, d):
                if d:
                    x = 1
                return c and x
            """, "may be used before assignment");
    }

    @Test
    public void shortCircuitOrChecksRightOperand() {
      assertRejected("""
            def f(c, d):
                if d:
                    x = 1
                return c or x
            """, "may be used before assignment");
    }

    @Test
    public void shortCircuitReadBeforeBindRejected() {
      assertRejected("""
                    def f():
                        x = x and 1
                    """, "may be used before assignment");
    }

    // ---------------------------------------------------------------
    // capture at def time
    // ---------------------------------------------------------------

    @Test
    public void capturedAfterAssignmentAccepted() {
      assertAccepts("""
                    def outer():
                        x = 1
                        def inner():
                            return x
                        return inner
                    """);
    }

    @Test
    public void capturedBeforeAssignmentRejected() {
      assertRejected("""
                    def outer():
                        def inner():
                            return x
                        x = 1
                        return inner
                    """, "may be unassigned at the point of definition");
    }

    @Test
    public void capturedMustBeAssignedInAllPrecedingBranches() {
      assertRejected("""
                    def outer(c):
                        if c:
                            x = 1
                        def inner():
                            return x
                        return inner
                    """, "may be unassigned at the point of definition");
    }

    @Test
    public void capturedAfterIfElseAssignRejected() {
      assertRejected("""
                    def outer(c):
                        if c:
                            x = 1
                        else:
                            x = 2
                        def inner():
                            return x
                        return inner
                    """, "assigned 2 times (at most 1 allowed)");
    }

    @Test
    public void transitiveCaptureBeforeAssignmentRejected() {
      assertRejected("""
          def outer():
              def middle():
                  def inner():
                      return x
                  return inner
              x = 1
              return middle
          """, "may be unassigned at the point of definition");
    }

    @Test
    public void transitiveCaptureAfterAssignmentAccepted() {
      assertAccepts("""
          def outer():
              x = 1
              def middle():
                  def inner():
                      return x
                  return inner
              return middle
          """);
    }

    // ---------------------------------------------------------------
    // self / mutual recursion
    // ---------------------------------------------------------------

    @Test
    public void selfRecursionAccepted() {
      assertAccepts("""
                    def outer():
                        def fact(n):
                            return 1 if n <= 1 else n * fact(n - 1)
                        return fact
                    """);
    }

    @Test
    public void mutualRecursionRejected() {
      assertRejected("""
                    def outer():
                        def a():
                            return b()
                        def b():
                            return a()
                        return a
                    """, "captured by 'a'");
    }

    // ---------------------------------------------------------------
    // nested blocks flow correctly
    // ---------------------------------------------------------------

    @Test
    public void nestedIfsMerge() {
      assertAccepts("""
                    def f(a, b):
                        if a:
                            if b:
                                x = 1
                            else:
                                x = 2
                        else:
                            x = 3
                        return x
                    """);
    }

    @Test
    public void nestedLoopsWithBreakAccepted() {
      assertAccepts("""
                    def f(cs):
                        for c in cs:
                            while c:
                                if c:
                                    x = 1
                                    break
                            else:
                                x = 2
                            return x
                    """);
    }

    // ---------------------------------------------------------------
    // del
    // ---------------------------------------------------------------

    @Test
    public void delUndefinedBaseRejected() {
      assertRejected("""
          def f(c):
              if c:
                  xs = [0]
              del xs[0]
          """, "may be used before assignment");
    }

    @Test
    public void delSubscriptAccepted() {
      assertAccepts("""
          def f():
              xs = [0]
              del xs[0]
          """);
    }

    @Test
    public void delSubscriptDoesNotUnbind() {
      assertAccepts("""
          def f():
              xs = [0]
              del xs[0]
              xs.append(1)
          """);
    }

    @Test
    public void delUndefinedIndexRejected() {
      assertRejected("""
          def f(c):
              xs = [0]
              if c:
                  i = 0
              del xs[i]
          """, "may be used before assignment");
    }

    // ---------------------------------------------------------------
    // augmented assignment with a subscript target
    // ---------------------------------------------------------------

    @Test
    public void augAssignSubscriptUndefinedBaseRejected() {
      assertRejected("""
          def f(c):
              if c:
                  xs = [0]
              xs[0] += 1
          """, "may be used before assignment");
    }

    @Test
    public void augAssignSubscriptAccepted() {
      assertAccepts("""
          def f():
              xs = [0]
              xs[0] += 1
          """);
    }

    @Test
    public void augAssignSubscriptUndefinedIndexRejected() {
      assertRejected("""
          def f(c):
              xs = [0]
              if c:
                  i = 0
              xs[i] += 1
          """, "may be used before assignment");
    }

    
  }
}