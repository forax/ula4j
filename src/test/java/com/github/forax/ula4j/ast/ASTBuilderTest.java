package com.github.forax.ula4j.ast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ASTBuilderTest {
  private static void assertAst(String source, String expected) {
    assertEquals(expected, AstPrinter.print(ASTBuilder.createScript(source), false), source);
  }

  private static void assertAstAt(String source, String expected) {
    assertEquals(expected, AstPrinter.print(ASTBuilder.createScript(source), true), source);
  }

  private static void assertUnsupported(String source, String construct, int line, int column) {
    var e = assertThrows(UnsupportedSyntaxException.class, () -> ASTBuilder.createScript(source), source);
    assertEquals(construct, e.construct(), source);
    assertEquals(line, e.line(), source);
    assertEquals(column, e.column(), source);
  }

  private static void assertSyntaxError(String source) {
    assertThrows(SyntaxErrorException.class, () -> ASTBuilder.createScript(source), source);
  }

  private static void assertSyntaxError(String source, int line, int column) {
    var e = assertThrows(SyntaxErrorException.class, () -> ASTBuilder.createScript(source), source);
    assertEquals(line, e.line(), source);
    assertEquals(column, e.column(), source);
  }

  // ---------------------------------------------------------------------------- literals

  @Test
  public void integers() {
    assertAst("x = 1\n", "(block (assign [x] 1))");
    assertAst("x = 0\n", "(block (assign [x] 0))");
    assertAst("x = 00\n", "(block (assign [x] 0))");
    assertAst("x = 1_000\n", "(block (assign [x] 1000))");
    assertAst("x = 0xFF_FF\n", "(block (assign [x] 65535))");
    assertAst("x = 0o17\n", "(block (assign [x] 15))");
    assertAst("x = 0b101\n", "(block (assign [x] 5))");
    assertAst("x = 9223372036854775807\n", "(block (assign [x] 9223372036854775807))");
    assertAst("x = 9223372036854775808\n", "(block (assign [x] big:9223372036854775808))");
    assertAst("x = 12345678901234567890\n", "(block (assign [x] big:12345678901234567890))");
  }

  @Test
  public void floats() {
    assertAst("x = 1.5\n", "(block (assign [x] 1.5))");
    assertAst("x = 1e3\n", "(block (assign [x] 1000.0))");
    assertAst("x = .5\n", "(block (assign [x] 0.5))");
    assertAst("x = 1.\n", "(block (assign [x] 1.0))");
    assertAst("x = 1_0.5\n", "(block (assign [x] 10.5))");
    assertAst("x = 1e999\n", "(block (assign [x] Infinity))");
  }

  @Test
  public void strings() {
    assertAst("x = 'a'\n", "(block (assign [x] 'a'))");
    assertAst("x = ''\n", "(block (assign [x] ''))");
    assertAst("x = \"a\" 'b'\n", "(block (assign [x] 'ab'))");
    assertAst("x = 'a\\nb'\n", "(block (assign [x] 'a\\nb'))");
    assertAst("x = r'a\\nb'\n", "(block (assign [x] 'a\\\\nb'))");
    assertAst("x = '''a\nb'''\n", "(block (assign [x] 'a\\nb'))");
    assertAst("x = 'a\\\nb'\n", "(block (assign [x] 'ab'))");
    assertAst("x = '\\x41\\u00e9\\101\\N{LATIN SMALL LETTER A}\\q'\n",
        "(block (assign [x] 'A\u00e9Aa\\\\q'))");
  }

  @Test
  public void constants() {
    assertAst("x = None\n", "(block (assign [x] None))");
    assertAst("x = True\n", "(block (assign [x] True))");
    assertAst("x = False\n", "(block (assign [x] False))");
  }

  @Test
  public void unsupportedLiterals() {
    assertUnsupported("x = ...\n", "ellipsis", 1, 4);
    assertUnsupported("x = 1j\n", "imaginary literal", 1, 4);
    assertUnsupported("x = b'a'\n", "bytes literal", 1, 4);
    assertUnsupported("x = f'a'\n", "f-string", 1, 4);
    assertUnsupported("x = t'a'\n", "t-string", 1, 4);
    assertUnsupported("x = '\\ud83d'\n", "lone surrogate escape", 1, 4);
  }

  // ---------------------------------------------------------------------------- expressions

  @Test
  public void names() {
    assertAst("x\n", "(block (expr x))");
    assertAst("a\nb\n", "(block (expr a) (expr b))");
  }

  @Test
  public void arithmetic() {
    assertAst("1 + 2 * 3\n", "(block (expr (+ 1 (* 2 3))))");
    assertAst("1 - 2 - 3\n", "(block (expr (- (- 1 2) 3)))");
    assertAst("a // b % c\n", "(block (expr (% (// a b) c)))");
    assertAst("a / b\n", "(block (expr (/ a b)))");
    assertAst("2 ** 3 ** 2\n", "(block (expr (** 2 (** 3 2))))");
    assertAst("-2 ** 2\n", "(block (expr (unary- (** 2 2))))");
    assertAst("- - a\n", "(block (expr (unary- (unary- a))))");
    assertAst("+a\n", "(block (expr (unary+ a)))");
    assertAst("~a\n", "(block (expr (unary~ a)))");
    assertAst("(1 + 2) * 3\n", "(block (expr (* (+ 1 2) 3)))");
  }

  @Test
  public void bitwise() {
    assertAst("a << 1 >> 2\n", "(block (expr (>> (<< a 1) 2)))");
    assertAst("a | b ^ c & d\n", "(block (expr (| a (^ b (& c d)))))");
  }

  @Test
  public void booleanOperators() {
    assertAst("not a\n", "(block (expr (not a)))");
    assertAst("a and b and c\n", "(block (expr (and (and a b) c)))");
    assertAst("a or b or c\n", "(block (expr (or (or a b) c)))");
    assertAst("a and b or c and d\n", "(block (expr (or (and a b) (and c d))))");
  }

  @Test
  public void comparisons() {
    assertAst("a < b\n", "(block (expr (compare a < b)))");
    assertAst("a == b\n", "(block (expr (compare a == b)))");
    assertAst("a != b\n", "(block (expr (compare a != b)))");
    assertAst("a <= b\n", "(block (expr (compare a <= b)))");
    assertAst("a > b\n", "(block (expr (compare a > b)))");
    assertAst("a >= b\n", "(block (expr (compare a >= b)))");
    assertAst("a in b\n", "(block (expr (compare a in b)))");
    assertAst("a not in b\n", "(block (expr (compare a not in b)))");
    assertAst("a < b <= c\n", "(block (expr (compare a < b <= c)))");
    assertAst("a == b != c\n", "(block (expr (compare a == b != c)))");
  }

  @Test
  public void conditionalExpressions() {
    assertAst("a if b else c\n", "(block (expr (conditional b a c)))");
    assertAst("x = a if b else c if d else e\n",
        "(block (assign [x] (conditional b a (conditional d c e))))");
  }

  @Test
  public void calls() {
    assertAst("f()\n", "(block (expr (call f)))");
    assertAst("f(1, 2)\n", "(block (expr (call f 1 2)))");
    assertAst("f(1, 2,)\n", "(block (expr (call f 1 2)))");
    assertAst("f(g(1))\n", "(block (expr (call f (call g 1))))");
    assertAst("f(1)(2)\n", "(block (expr (call (call f 1) 2)))");
  }

  @Test
  public void methodCalls() {
    assertAst("a.b()\n", "(block (expr (method a b)))");
    assertAst("a.b(1, 2)\n", "(block (expr (method a b 1 2)))");
    assertAst("a.b(1).c(2)\n", "(block (expr (method (method a b 1) c 2)))");
    assertAst("'x'.join(y)\n", "(block (expr (method 'x' join y)))");
    assertAst("a[0].b()\n", "(block (expr (method (subscript a 0) b)))");
  }

  @Test
  public void subscripts() {
    assertAst("a[0]\n", "(block (expr (subscript a 0)))");
    assertAst("a[0][1]\n", "(block (expr (subscript (subscript a 0) 1)))");
    assertAst("a[1:2]\n", "(block (expr (subscript a (slice 1 2 None))))");
    assertAst("a[1:]\n", "(block (expr (subscript a (slice 1 None None))))");
    assertAst("a[:2]\n", "(block (expr (subscript a (slice None 2 None))))");
    assertAst("a[:]\n", "(block (expr (subscript a (slice None None None))))");
    assertAst("a[::2]\n", "(block (expr (subscript a (slice None None 2))))");
    assertAst("a[1:2:3]\n", "(block (expr (subscript a (slice 1 2 3))))");
    assertAst("a[1, 2]\n", "(block (expr (subscript a (tuple 1 2))))");
    assertAst("a[1,]\n", "(block (expr (subscript a (tuple 1))))");
    assertAst("a[1:2, 3]\n", "(block (expr (subscript a (tuple (slice 1 2 None) 3))))");
  }

  @Test
  public void displays() {
    assertAst("[]\n", "(block (expr (list)))");
    assertAst("[1, 2]\n", "(block (expr (list 1 2)))");
    assertAst("[1, 2,]\n", "(block (expr (list 1 2)))");
    assertAst("()\n", "(block (expr (tuple)))");
    assertAst("(1,)\n", "(block (expr (tuple 1)))");
    assertAst("(1, 2)\n", "(block (expr (tuple 1 2)))");
    assertAst("1, 2\n", "(block (expr (tuple 1 2)))");
    assertAst("(1)\n", "(block (expr 1))");
    assertAst("{}\n", "(block (expr (dict)))");
    assertAst("{1: 2, 'a': b}\n", "(block (expr (dict (entry 1 2) (entry 'a' b))))");
    assertAst("{1: 2,}\n", "(block (expr (dict (entry 1 2))))");
    assertAst("[(1, 2), [3]]\n", "(block (expr (list (tuple 1 2) (list 3))))");
  }

  // ---------------------------------------------------------------------------- statements

  @Test
  public void pass() {
    assertAst("pass\n", "(block)");
    assertAst("", "(block)");
  }

  @Test
  public void assignments() {
    assertAst("x = 1\n", "(block (assign [x] 1))");
    assertAst("x = y = 2\n", "(block (assign [x y] 2))");
    assertAst("x = 1, 2\n", "(block (assign [x] (tuple 1 2)))");
    assertAst("x = 1,\n", "(block (assign [x] (tuple 1)))");
    assertAst("a[0] = 1\n", "(block (assign [(subscript a 0)] 1))");
    assertAst("a[0][1] = 2\n", "(block (assign [(subscript (subscript a 0) 1)] 2))");
    assertAst("a, b = 1, 2\n", "(block (assign [(tuple a b)] (tuple 1 2)))");
    assertAst("(a, b) = x\n", "(block (assign [(tuple a b)] x))");
    assertAst("(a, b), c = x\n", "(block (assign [(tuple (tuple a b) c)] x))");
    assertAst("a, = x\n", "(block (assign [(tuple a)] x))");
    assertAst("() = x\n", "(block (assign [(tuple)] x))");
    assertAst("a[0], b = x\n", "(block (assign [(tuple (subscript a 0) b)] x))");
  }

  @Test
  public void augmentedAssignments() {
    assertAst("x += 1\n", "(block (augassign + x 1))");
    assertAst("x -= 1\n", "(block (augassign - x 1))");
    assertAst("x *= 2\n", "(block (augassign * x 2))");
    assertAst("x /= 2\n", "(block (augassign / x 2))");
    assertAst("x //= 2\n", "(block (augassign // x 2))");
    assertAst("x %= 2\n", "(block (augassign % x 2))");
    assertAst("x **= 2\n", "(block (augassign ** x 2))");
    assertAst("x <<= 1\n", "(block (augassign << x 1))");
    assertAst("x >>= 1\n", "(block (augassign >> x 1))");
    assertAst("x &= 1\n", "(block (augassign & x 1))");
    assertAst("x |= 1\n", "(block (augassign | x 1))");
    assertAst("x ^= 1\n", "(block (augassign ^ x 1))");
    assertAst("a[0] += 1\n", "(block (augassign + (subscript a 0) 1))");
    assertAst("a[i()] += v()\n", "(block (augassign + (subscript a (call i)) (call v)))");
  }

  @Test
  public void deletions() {
    assertAst("del a[0]\n", "(block (del (subscript a 0)))");
    assertAst("del a[0], b[1]\n", "(block (del (subscript a 0) (subscript b 1)))");
    assertAst("del (a[0])\n", "(block (del (subscript a 0)))");
  }

  @Test
  public void returns() {
    assertAst("def f():\n    return 1\n", "(block (def f [] rest=- (block (return 1))))");
    assertAst("def f():\n    return\n", "(block (def f [] rest=- (block (return None))))");
    assertAst("def f():\n    return 1, 2\n", "(block (def f [] rest=- (block (return (tuple 1 2)))))");
  }

  @Test
  public void ifStatements() {
    assertAst("if x:\n    pass\n", "(block (if x (block) (block)))");
    assertAst("if x: y\n", "(block (if x (block (expr y)) (block)))");
    assertAst("if x: a; b\n", "(block (if x (block (expr a) (expr b)) (block)))");
    assertAst("if x:\n    a\nelse:\n    b\n", "(block (if x (block (expr a)) (block (expr b))))");
    assertAst("if a:\n    x\nelif b:\n    y\nelse:\n    z\n",
        "(block (if a (block (expr x)) (block (if b (block (expr y)) (block (expr z))))))");
    assertAst("if a:\n    x\nelif b:\n    y\n",
        "(block (if a (block (expr x)) (block (if b (block (expr y)) (block)))))");
  }

  @Test
  public void whileStatements() {
    assertAst("while x:\n    a\n", "(block (while x (block (expr a)) (block)))");
    assertAst("while x:\n    a\nelse:\n    b\n", "(block (while x (block (expr a)) (block (expr b))))");
    assertAst("while x:\n    break\n", "(block (while x (block (break)) (block)))");
    assertAst("while x:\n    continue\n", "(block (while x (block (continue)) (block)))");
  }

  @Test
  public void forStatements() {
    assertAst("for i in range(3):\n    a\n", "(block (for i (call range 3) (block (expr a)) (block)))");
    assertAst("for a, b in x:\n    pass\n", "(block (for (tuple a b) x (block) (block)))");
    assertAst("for a[0] in x:\n    pass\n", "(block (for (subscript a 0) x (block) (block)))");
    assertAst("for i in 1, 2:\n    pass\n", "(block (for i (tuple 1 2) (block) (block)))");
    assertAst("for i in x:\n    a\nelse:\n    b\n", "(block (for i x (block (expr a)) (block (expr b))))");
    assertAst("for i in x:\n    continue\n", "(block (for i x (block (continue)) (block)))");
  }

  @Test
  public void elseClauseOfALoopBelongsToTheEnclosingContext() {
    assertAst("while a:\n    while b:\n        pass\n    else:\n        break\n",
        "(block (while a (block (while b (block) (block (break)))) (block)))");
  }

  @Test
  public void functionDefinitions() {
    assertAst("def f():\n    pass\n", "(block (def f [] rest=- (block)))");
    assertAst("def f(a, b):\n    pass\n", "(block (def f [a b] rest=- (block)))");
    assertAst("def f(a, *r):\n    pass\n", "(block (def f [a] rest=r (block)))");
    assertAst("def f(*r):\n    pass\n", "(block (def f [] rest=r (block)))");
    assertAst("def f(*r,):\n    pass\n", "(block (def f [] rest=r (block)))");
    assertAst("def f(a): return a\n", "(block (def f [a] rest=- (block (return a))))");
    assertAst("def f():\n    def g():\n        return 1\n    return g\n",
        "(block (def f [] rest=- (block (def g [] rest=- (block (return 1))) (return g))))");
    assertAst("while a:\n    def f():\n        return 1\n    break\n",
        "(block (while a (block (def f [] rest=- (block (return 1))) (break)) (block)))");
  }

  // ---------------------------------------------------------------------------- positions

  @Test
  public void positionsOfStatementsAndExpressions() {
    assertAstAt("x = 1\ny = x + 2\n",
        "(block@1:0 (assign@1:0 [x@1:0] 1@1:4) (assign@2:0 [y@2:0] (+@2:4 x@2:4 2@2:8)))");
    assertAstAt("a < b\n", "(block@1:0 (expr@1:0 (compare@1:0 a@1:0 < b@1:4)))");
  }

  @Test
  public void positionsInsideFunctions() {
    assertAstAt("def f(a):\n    return a.b(1)\n",
        "(block@1:0 (def@1:0 f [a@1:6] rest=- (block@2:4 (return@2:4 (method@2:11 a@2:11 b 1@2:15)))))");
  }

  // ---------------------------------------------------------------------------- unsupported syntax

  @Test
  public void unsupportedExpressions() {
    assertUnsupported("x = lambda: 1\n", "lambda", 1, 4);
    assertUnsupported("a is b\n", "is", 1, 2);
    assertUnsupported("a is not b\n", "is not", 1, 2);
    assertUnsupported("x = {1, 2}\n", "set display", 1, 4);
    assertUnsupported("x = [i for i in y]\n", "list comprehension", 1, 4);
    assertUnsupported("x = {i for i in y}\n", "set comprehension", 1, 4);
    assertUnsupported("x = {i: i for i in y}\n", "dict comprehension", 1, 4);
    assertUnsupported("x = (i for i in y)\n", "generator expression", 1, 4);
    assertUnsupported("f(i for i in y)\n", "generator expression", 1, 1);
    assertUnsupported("(y := 1)\n", "assignment expression", 1, 1);
    assertUnsupported("x = a @ b\n", "matrix multiplication", 1, 6);
    assertUnsupported("await x\n", "await", 1, 0);
    assertUnsupported("x = yield\n", "yield", 1, 4);
  }

  @Test
  public void unsupportedStarAndKeywordArguments() {
    assertUnsupported("f(*a)\n", "star-unpacking in call", 1, 2);
    assertUnsupported("f(a=1)\n", "keyword argument", 1, 2);
    assertUnsupported("f(**a)\n", "**-unpacking in call", 1, 2);
    assertUnsupported("x = *a, b\n", "star expression", 1, 4);
    assertUnsupported("x = [*a]\n", "star expression", 1, 5);
    assertUnsupported("a[*b]\n", "star expression", 1, 2);
    assertUnsupported("x = {**a}\n", "dict unpacking", 1, 5);
  }

  @Test
  public void unsupportedAttributes() {
    assertUnsupported("x = a.b\n", "attribute access", 1, 5);
    assertUnsupported("(a.b)(1)\n", "attribute access", 1, 2);
    assertUnsupported("a.b.c()\n", "attribute access", 1, 1);
    assertUnsupported("x.y = 1\n", "attribute store", 1, 1);
    assertUnsupported("x.y += 1\n", "attribute store", 1, 1);
    assertUnsupported("a.b, c = x\n", "attribute store", 1, 1);
    assertUnsupported("del x.y\n", "attribute deletion", 1, 5);
  }

  @Test
  public void unsupportedTargets() {
    assertUnsupported("*a, b = x\n", "star-unpacking in assignment target", 1, 0);
    assertUnsupported("[a, b] = x\n", "list assignment target", 1, 0);
    assertUnsupported("x @= 1\n", "matrix multiplication", 1, 2);
    assertUnsupported("x: int = 1\n", "annotation", 1, 1);
    assertUnsupported("del x\n", "del of a name", 1, 4);
    assertUnsupported("del (a, b)\n", "del of a target list", 1, 4);
  }

  @Test
  public void unsupportedFunctionFeatures() {
    assertUnsupported("def f(a=1):\n    pass\n", "default parameter value", 1, 6);
    assertUnsupported("def f(a, /):\n    pass\n", "positional-only parameter marker '/'", 1, 9);
    assertUnsupported("def f(*, a):\n    pass\n", "keyword-only parameter", 1, 6);
    assertUnsupported("def f(*a, b):\n    pass\n", "keyword-only parameter", 1, 10);
    assertUnsupported("def f(**k):\n    pass\n", "**kwargs parameter", 1, 6);
    assertUnsupported("def f(a: int):\n    pass\n", "annotation", 1, 7);
    assertUnsupported("def f() -> int:\n    pass\n", "return annotation", 1, 8);
    assertUnsupported("@d\ndef f():\n    pass\n", "decorator", 1, 0);
    assertUnsupported("async def f():\n    pass\n", "async function", 1, 0);
    assertUnsupported("def f[T]():\n    pass\n", "type parameters", 1, 5);
  }

  @Test
  public void unsupportedStatements() {
    assertUnsupported("class A:\n    pass\n", "class", 1, 0);
    assertUnsupported("with a:\n    pass\n", "with", 1, 0);
    assertUnsupported("try:\n    pass\nfinally:\n    pass\n", "try", 1, 0);
    assertUnsupported("raise x\n", "raise", 1, 0);
    assertUnsupported("assert x\n", "assert", 1, 0);
    assertUnsupported("import a\n", "import", 1, 0);
    assertUnsupported("from a import b\n", "import", 1, 0);
    assertUnsupported("global x\n", "global", 1, 0);
    assertUnsupported("nonlocal x\n", "nonlocal", 1, 0);
    assertUnsupported("match x:\n    case 1:\n        pass\n", "match", 1, 0);
    assertUnsupported("type X = int\n", "type alias", 1, 0);
    assertUnsupported("yield x\n", "yield", 1, 0);
    assertUnsupported("async for x in y:\n    pass\n", "async for", 1, 0);
  }

  // ---------------------------------------------------------------------------- syntax errors

  @Test
  public void syntaxErrors() {
    assertSyntaxError("x = = 1\n");
    assertSyntaxError("if x:\n    a\n  b\n");
    assertSyntaxError("x = '\\xZZ'\n");
  }

  @Test
  public void errorsReportedAfterParsing() {
    assertSyntaxError("return 1\n", 1, 0);
    assertSyntaxError("break\n", 1, 0);
    assertSyntaxError("continue\n", 1, 0);
    assertSyntaxError("while a:\n    def f():\n        break\n", 3, 8);
    assertSyntaxError("while x:\n    pass\nelse:\n    break\n", 4, 4);
    assertSyntaxError("def f(a, a):\n    pass\n", 1, 9);
  }
}