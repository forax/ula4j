package com.github.forax.ula4j.ast;

import com.github.forax.ula4j.ast.Stmt.FunctionDef.Param;
import com.github.forax.ula4j.grammar.antlr.PythonLexer;
import com.github.forax.ula4j.grammar.antlr.PythonParser;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Annotated_rhsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.AnnotationContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.ArgsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.ArgumentsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Assert_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.AssignmentContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Assignment_expressionContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.AtomContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.AugassignContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Await_primaryContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Bitwise_andContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Bitwise_orContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Bitwise_xorContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.BlockContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Break_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Class_defContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Compare_op_bitwise_or_pairContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.ComparisonContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Compound_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.ConjunctionContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Continue_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.DecoratorsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Del_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Del_t_atomContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Del_targetContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Del_targetsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.DictContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.DictcompContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.DisjunctionContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Double_starred_kvpairContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Double_starred_kvpairsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Elif_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Else_blockContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Eq_bitwise_orContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.ExpressionContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.FactorContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.File_inputContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.For_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.FstringContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Function_defContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Function_def_rawContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.GenexpContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Global_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.GroupContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Gt_bitwise_orContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Gte_bitwise_orContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.If_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Import_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.In_bitwise_orContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.InversionContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Is_bitwise_orContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Isnot_bitwise_orContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.KvpairContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Kwarg_or_starredContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.KwargsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.KwdsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.LambdefContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.ListContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.ListcompContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Lt_bitwise_orContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Lte_bitwise_orContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Match_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.NameContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Named_expressionContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Nonlocal_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Noteq_bitwise_orContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Notin_bitwise_orContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.ParamContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Param_no_defaultContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Param_no_default_star_annotationContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Param_with_defaultContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.ParametersContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.ParamsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Pass_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.PowerContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.PrimaryContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Raise_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Return_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.SetContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.SetcompContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Shift_exprContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Simple_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Simple_stmtsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Single_subscript_attribute_targetContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Single_targetContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Slash_no_defaultContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Slash_with_defaultContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.SliceContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.SlicesContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Star_atomContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Star_etcContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Star_expressionContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Star_expressionsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Star_named_expressionContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Star_named_expressionsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Star_targetContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Star_targetsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Star_targets_tuple_seqContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Starred_expressionContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.StatementContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.StatementsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.StringContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.StringsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.SumContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.T_primaryContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Target_with_star_atomContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.TermContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Try_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.TstringContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.TupleContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Type_aliasContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Type_paramsContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.While_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.With_stmtContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Yield_exprContext;
import com.github.forax.ula4j.grammar.antlr.PythonParser.Yield_stmtContext;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Builds the AST from the ANTLR parse tree.
 * <p>
 * This is not a generated-visitor implementation: there is one typed method per grammar rule, and
 * every alternative that is not explicitly handled is rejected, so that a forgotten rule can never
 * be silently accepted.
 */
public final class ASTBuilder {
  private boolean inFunction;
  private int loopDepth;

  private ASTBuilder() {}

  public static Script createScript(String code) {
    var lexer = new PythonLexer(CharStreams.fromString(code));
    lexer.removeErrorListeners();
    var throwingErrorListener = new ThrowingErrorListener();
    lexer.addErrorListener(throwingErrorListener);
    var parser = new PythonParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(throwingErrorListener);
    var tree = parser.file_input();
    return new Script(new ASTBuilder().fileInput(tree));
  }

  private static final class ThrowingErrorListener extends BaseErrorListener {
    private ThrowingErrorListener() {}

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                            int charPositionInLine, String msg, RecognitionException e) {
      throw new SyntaxErrorException(msg, line, charPositionInLine);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // helpers

  private static Token token(ParseTree tree) {
    return switch (tree) {
      case TerminalNode terminal -> terminal.getSymbol();
      case ParserRuleContext ctx -> ctx.getStart();
      default -> throw new AssertionError("unexpected tree " + tree);
    };
  }

  private static int line(ParseTree tree) {
    return token(tree).getLine();
  }

  private static int column(ParseTree tree) {
    return token(tree).getCharPositionInLine();
  }

  private static UnsupportedSyntaxException unsupported(String feature, ParseTree where) {
    var token = token(where);
    return new UnsupportedSyntaxException(feature, token.getLine(), token.getCharPositionInLine());
  }

  private static SyntaxErrorException syntaxError(String reason, ParseTree where) {
    var token = token(where);
    return new SyntaxErrorException(reason, token.getLine(), token.getCharPositionInLine());
  }

  private static <T extends ParserRuleContext> T one(ParserRuleContext ctx, Class<T> type) {
    return ctx.getRuleContext(type, 0);  // null if absent
  }

  private static <T extends ParserRuleContext> List<T> all(ParserRuleContext ctx, Class<T> type) {
    return ctx.getRuleContexts(type);
  }

  private static boolean has(ParserRuleContext ctx, String text) {
    if (ctx.children == null) {
      return false;
    }
    for (var child : ctx.children) {
      if (child instanceof TerminalNode terminal && terminal.getText().equals(text)) {
        return true;
      }
    }
    return false;
  }

  private static TerminalNode terminal(ParserRuleContext ctx, String text) {
    for (var child : ctx.children) {
      if (child instanceof TerminalNode terminal && terminal.getText().equals(text)) {
        return terminal;
      }
    }
    throw new AssertionError("no '" + text + "' in " + ctx.getText());
  }

  private static String identifier(NameContext ctx) {
    return Normalizer.normalize(ctx.getText(), Normalizer.Form.NFKC);  // as CPython does
  }

  private static Block emptyBlock(ParserRuleContext owner) {
    return new Block(List.of(), line(owner), column(owner));
  }

  // ---------------------------------------------------------------------------------------------
  // statements

  private Block fileInput(File_inputContext ctx) {
    var out = new ArrayList<Stmt>();
    var statements = one(ctx, StatementsContext.class);
    if (statements != null) {
      statements(statements, out);
    }
    return new Block(out, line(ctx), column(ctx));
  }

  private void statements(StatementsContext ctx, List<Stmt> out) {
    for (var statement : all(ctx, StatementContext.class)) {
      switch (statement.getChild(0)) {
        case Compound_stmtContext compound -> compoundStmt(compound, out);
        case Simple_stmtsContext simple -> simpleStmts(simple, out);
        default -> throw new AssertionError(statement.getText());
      }
    }
  }

  private Block block(BlockContext ctx) {
    var out = new ArrayList<Stmt>();
    ParserRuleContext at;
    var simple = one(ctx, Simple_stmtsContext.class);
    if (simple != null) {
      simpleStmts(simple, out);
      at = simple;
    } else {
      var statements = one(ctx, StatementsContext.class);
      statements(statements, out);
      at = statements;
    }
    return new Block(out, line(at), column(at));
  }

  private void simpleStmts(Simple_stmtsContext ctx, List<Stmt> out) {
    for (var simple : all(ctx, Simple_stmtContext.class)) {
      simpleStmt(simple, out);
    }
  }

  private void simpleStmt(Simple_stmtContext ctx, List<Stmt> out) {
    switch (ctx.getChild(0)) {
      case AssignmentContext c -> out.add(assignment(c));
      case Type_aliasContext c -> throw unsupported("type alias", c);
      case Star_expressionsContext c -> out.add(new Stmt.ExprStmt(starExpressions(c), line(c), column(c)));
      case Return_stmtContext c -> out.add(returnStmt(c));
      case Import_stmtContext c -> throw unsupported("import", c);
      case Raise_stmtContext c -> throw unsupported("raise", c);
      case Pass_stmtContext _ -> { }
      case Del_stmtContext c -> out.add(delStmt(c));
      case Yield_stmtContext c -> throw unsupported("yield", c);
      case Assert_stmtContext c -> throw unsupported("assert", c);
      case Break_stmtContext c -> {
        if (loopDepth == 0) {
          throw syntaxError("'break' outside loop", c);
        }
        out.add(new Stmt.Break(line(c), column(c)));
      }
      case Continue_stmtContext c -> {
        if (loopDepth == 0) {
          throw syntaxError("'continue' not properly in loop", c);
        }
        out.add(new Stmt.Continue(line(c), column(c)));
      }
      case Global_stmtContext c -> throw unsupported("global", c);
      case Nonlocal_stmtContext c -> throw unsupported("nonlocal", c);
      default -> throw new AssertionError(ctx.getText());
    }
  }

  private Stmt returnStmt(Return_stmtContext ctx) {
    if (!inFunction) {
      throw syntaxError("'return' outside function", ctx);
    }
    var value = one(ctx, Star_expressionsContext.class);
    var expr = value == null
        ? new Expr.NoneLiteral(line(ctx), column(ctx))
        : starExpressions(value);
    return new Stmt.Return(expr, line(ctx), column(ctx));
  }

  private Stmt delStmt(Del_stmtContext ctx) {
    var targets = all(one(ctx, Del_targetsContext.class), Del_targetContext.class).stream()
        .map(this::delTarget)
        .toList();
    return new Stmt.Del(targets, line(ctx), column(ctx));
  }

  private Target.SubscriptTarget delTarget(Del_targetContext ctx) {
    if (ctx.getChild(0) instanceof Del_t_atomContext atom) {
      if (atom.getChild(0) instanceof NameContext) {
        throw unsupported("del of a name", atom);
      }
      if (atom.getChildCount() == 3 && atom.getChild(1) instanceof Del_targetContext inner) {
        return delTarget(inner);  // parenthesized target
      }
      throw unsupported("del of a target list", atom);
    }
    return subscriptTarget(ctx, "attribute deletion");
  }

  private Stmt assignment(AssignmentContext ctx) {
    var augassign = one(ctx, AugassignContext.class);
    if (augassign != null) {
      var target = singleTarget(one(ctx, Single_targetContext.class));
      var op = augmentedOperator(augassign);
      var value = annotatedRhs(one(ctx, Annotated_rhsContext.class));
      return new Stmt.AugAssign(target, op, value, line(ctx), column(ctx));
    }
    if (has(ctx, ":")) {
      throw unsupported("annotation", terminal(ctx, ":"));
    }
    var targets = all(ctx, Star_targetsContext.class).stream().map(this::starTargets).toList();
    var value = annotatedRhs(one(ctx, Annotated_rhsContext.class));
    return new Stmt.Assign(targets, value, line(ctx), column(ctx));
  }

  private static Expr.BinaryOperator augmentedOperator(AugassignContext ctx) {
    return switch (ctx.getText()) {
      case "+=" -> Expr.BinaryOperator.ADD;
      case "-=" -> Expr.BinaryOperator.SUB;
      case "*=" -> Expr.BinaryOperator.MUL;
      case "/=" -> Expr.BinaryOperator.DIV;
      case "//=" -> Expr.BinaryOperator.FLOOR_DIV;
      case "%=" -> Expr.BinaryOperator.MOD;
      case "**=" -> Expr.BinaryOperator.POW;
      case "<<=" -> Expr.BinaryOperator.LSHIFT;
      case ">>=" -> Expr.BinaryOperator.RSHIFT;
      case "&=" -> Expr.BinaryOperator.BIT_AND;
      case "|=" -> Expr.BinaryOperator.BIT_OR;
      case "^=" -> Expr.BinaryOperator.BIT_XOR;
      case "@=" -> throw unsupported("matrix multiplication", ctx);
      default -> throw new AssertionError(ctx.getText());
    };
  }

  private Expr annotatedRhs(Annotated_rhsContext ctx) {
    return switch (ctx.getChild(0)) {
      case Yield_exprContext y -> throw unsupported("yield", y);
      case Star_expressionsContext s -> starExpressions(s);
      default -> throw new AssertionError(ctx.getText());
    };
  }

  // targets

  private Target singleTarget(Single_targetContext ctx) {
    return switch (ctx.getChild(0)) {
      case Single_subscript_attribute_targetContext s -> subscriptTarget(s, "attribute store");
      case NameContext n -> new Target.NameTarget(identifier(n), line(n), column(n));
      case TerminalNode _ -> singleTarget(one(ctx, Single_targetContext.class));  // '(' single_target ')'
      default -> throw new AssertionError(ctx.getText());
    };
  }

  /** Shared by the rules of the shape {@code t_primary ('.' name | '[' slices ']')}. */
  private Target.SubscriptTarget subscriptTarget(ParserRuleContext ctx, String attributeFeature) {
    var op = ctx.getChild(1);
    if (op.getText().equals(".")) {
      throw unsupported(attributeFeature, op);
    }
    var base = (T_primaryContext) ctx.getChild(0);
    var index = subscriptIndex(one(ctx, SlicesContext.class));
    return new Target.SubscriptTarget(primary(base), index, line(ctx), column(ctx));
  }

  private Target starTargets(Star_targetsContext ctx) {
    var items = all(ctx, Star_targetContext.class);
    if (items.size() == 1 && ctx.getChildCount() == 1) {  // no comma
      return starTarget(items.getFirst());
    }
    return new Target.TupleTarget(items.stream().map(this::starTarget).toList(), line(ctx), column(ctx));
  }

  private Target starTarget(Star_targetContext ctx) {
    if (ctx.getChild(0) instanceof TerminalNode) {  // '*' star_target
      throw unsupported("star-unpacking in assignment target", ctx);
    }
    var withStarAtom = one(ctx, Target_with_star_atomContext.class);
    if (withStarAtom.getChild(0) instanceof Star_atomContext atom) {
      return starAtom(atom);
    }
    return subscriptTarget(withStarAtom, "attribute store");
  }

  private Target starAtom(Star_atomContext ctx) {
    if (ctx.getChild(0) instanceof NameContext n) {
      return new Target.NameTarget(identifier(n), line(n), column(n));
    }
    if (ctx.getChild(0).getText().equals("[")) {
      throw unsupported("list assignment target", ctx);
    }
    // '(' target_with_star_atom ')'  or  '(' star_targets_tuple_seq? ')'
    if (ctx.getChild(1) instanceof Target_with_star_atomContext inner) {
      if (inner.getChild(0) instanceof Star_atomContext atom) {
        return starAtom(atom);
      }
      return subscriptTarget(inner, "attribute store");
    }
    var seq = one(ctx, Star_targets_tuple_seqContext.class);
    var elements = seq == null
        ? List.<Target>of()
        : all(seq, Star_targetContext.class).stream().map(this::starTarget).toList();
    return new Target.TupleTarget(elements, line(ctx), column(ctx));
  }

  // compound statements

  private void compoundStmt(Compound_stmtContext ctx, List<Stmt> out) {
    switch (ctx.getChild(0)) {
      case Function_defContext c -> out.add(functionDef(c));
      case If_stmtContext c -> out.add(ifStmt(c));
      case Class_defContext c -> {
        var decorators = one(c, DecoratorsContext.class);
        throw unsupported(decorators != null ? "decorator" : "class", c);
      }
      case With_stmtContext c -> throw unsupported("with", c);
      case For_stmtContext c -> out.add(forStmt(c));
      case Try_stmtContext c -> throw unsupported("try", c);
      case While_stmtContext c -> out.add(whileStmt(c));
      case Match_stmtContext c -> throw unsupported("match", c);
      default -> throw new AssertionError(ctx.getText());
    }
  }

  private Stmt functionDef(Function_defContext ctx) {
    var decorators = one(ctx, DecoratorsContext.class);
    if (decorators != null) {
      throw unsupported("decorator", decorators);
    }
    var raw = one(ctx, Function_def_rawContext.class);
    if (has(raw, "async")) {
      throw unsupported("async function", raw);
    }
    var typeParams = one(raw, Type_paramsContext.class);
    if (typeParams != null) {
      throw unsupported("type parameters", typeParams);
    }
    if (has(raw, "->")) {
      throw unsupported("return annotation", terminal(raw, "->"));
    }
    var name = identifier(one(raw, NameContext.class));

    var parameters = new ArrayList<Param>();
    Param rest = null;
    var params = one(raw, ParamsContext.class);
    if (params != null) {
      for (var child : one(params, ParametersContext.class).children) {
        switch (child) {
          case Slash_no_defaultContext c ->
              throw unsupported("positional-only parameter marker '/'", terminal(c, "/"));
          case Slash_with_defaultContext c -> throw unsupported("default parameter value", c);
          case Param_no_defaultContext c -> parameters.add(param(one(c, ParamContext.class)));
          case Param_with_defaultContext c -> throw unsupported("default parameter value", c);
          case Star_etcContext c -> rest = starEtc(c);
          default -> throw new AssertionError(child.getText());
        }
      }
    }

    var seen = new HashSet<String>();
    var everyParameter = new ArrayList<>(parameters);
    if (rest != null) {
      everyParameter.add(rest);
    }
    for (var parameter : everyParameter) {
      if (!seen.add(parameter.name())) {
        throw new SyntaxErrorException(
            "duplicate argument '" + parameter.name() + "' in function definition",
            parameter.line(), parameter.column());
      }
    }

    var savedInFunction = inFunction;
    var savedLoopDepth = loopDepth;
    inFunction = true;
    loopDepth = 0;  // a def resets the loop context
    var body = block(one(raw, BlockContext.class));
    inFunction = savedInFunction;
    loopDepth = savedLoopDepth;

    return new Stmt.FunctionDef(name, parameters, Optional.ofNullable(rest), body, line(ctx), column(ctx));
  }

  private Param param(ParamContext ctx) {
    var annotation = one(ctx, AnnotationContext.class);
    if (annotation != null) {
      throw unsupported("annotation", annotation);
    }
    var name = one(ctx, NameContext.class);
    return new Param(identifier(name), line(name), column(name));
  }

  /** Only {@code *name} is accepted. */
  private Param starEtc(Star_etcContext ctx) {
    if (ctx.getChild(0) instanceof KwdsContext) {
      throw unsupported("**kwargs parameter", ctx);
    }
    var second = ctx.getChild(1);
    if (second instanceof Param_no_default_star_annotationContext c) {
      throw unsupported("annotation", c);
    }
    if (!(second instanceof Param_no_defaultContext p)) {  // '*' ',' ...
      throw unsupported("keyword-only parameter", ctx);
    }
    if (ctx.getChildCount() > 2) {
      var extra = ctx.getChild(2);
      throw unsupported(extra instanceof KwdsContext ? "**kwargs parameter" : "keyword-only parameter", extra);
    }
    return param(one(p, ParamContext.class));
  }

  /** Shared by {@code if_stmt} and {@code elif_stmt}. */
  private Stmt.If ifStmt(ParserRuleContext ctx) {
    var test = namedExpression(one(ctx, Named_expressionContext.class));
    var body = block(one(ctx, BlockContext.class));
    Block orElse;
    var elif = one(ctx, Elif_stmtContext.class);
    var elseBlock = one(ctx, Else_blockContext.class);
    if (elif != null) {
      var nested = ifStmt(elif);
      orElse = new Block(List.of(nested), nested.line(), nested.column());
    } else if (elseBlock != null) {
      orElse = block(one(elseBlock, BlockContext.class));
    } else {
      orElse = emptyBlock(ctx);
    }
    return new Stmt.If(test, body, orElse, line(ctx), column(ctx));
  }

  private Stmt whileStmt(While_stmtContext ctx) {
    var test = namedExpression(one(ctx, Named_expressionContext.class));
    var body = loopBody(one(ctx, BlockContext.class));
    return new Stmt.While(test, body, elseBlock(ctx), line(ctx), column(ctx));
  }

  private Stmt forStmt(For_stmtContext ctx) {
    if (has(ctx, "async")) {
      throw unsupported("async for", ctx);
    }
    var target = starTargets(one(ctx, Star_targetsContext.class));
    var iter = starExpressions(one(ctx, Star_expressionsContext.class));
    var body = loopBody(one(ctx, BlockContext.class));
    return new Stmt.For(target, iter, body, elseBlock(ctx), line(ctx), column(ctx));
  }

  private Block loopBody(BlockContext ctx) {
    loopDepth++;
    var body = block(ctx);
    loopDepth--;
    return body;
  }

  /** The else clause of a loop belongs to the enclosing context: a break there targets the outer loop. */
  private Block elseBlock(ParserRuleContext loop) {
    var elseBlock = one(loop, Else_blockContext.class);
    return elseBlock == null ? emptyBlock(loop) : block(one(elseBlock, BlockContext.class));
  }

  // ---------------------------------------------------------------------------------------------
  // expressions

  private Expr expr(ParseTree tree) {
    return switch (tree) {
      case Named_expressionContext c -> namedExpression(c);
      case ExpressionContext c -> expression(c);
      case DisjunctionContext c -> disjunction(c);
      case ConjunctionContext c -> conjunction(c);
      case InversionContext c -> inversion(c);
      case ComparisonContext c -> comparison(c);
      case Bitwise_orContext c -> binary(c);
      case Bitwise_xorContext c -> binary(c);
      case Bitwise_andContext c -> binary(c);
      case Shift_exprContext c -> binary(c);
      case SumContext c -> binary(c);
      case TermContext c -> binary(c);
      case FactorContext c -> factor(c);
      case PowerContext c -> power(c);
      case Await_primaryContext c -> awaitPrimary(c);
      case PrimaryContext c -> primary(c);
      case T_primaryContext c -> primary(c);
      case AtomContext c -> atom(c);
      default -> throw new AssertionError("unexpected " + tree.getClass().getSimpleName());
    };
  }

  private Expr namedExpression(Named_expressionContext ctx) {
    if (ctx.getChild(0) instanceof Assignment_expressionContext walrus) {
      throw unsupported("assignment expression", walrus);
    }
    return expression((ExpressionContext) ctx.getChild(0));
  }

  private Expr expression(ExpressionContext ctx) {
    if (ctx.getChild(0) instanceof LambdefContext lambda) {
      throw unsupported("lambda", lambda);
    }
    var disjunctions = all(ctx, DisjunctionContext.class);
    var then = expr(disjunctions.getFirst());
    if (disjunctions.size() == 1) {
      return then;
    }
    var test = expr(disjunctions.get(1));
    var orElse = expression(one(ctx, ExpressionContext.class));
    return new Expr.Conditional(test, then, orElse, line(ctx), column(ctx));
  }

  private Expr starExpressions(Star_expressionsContext ctx) {
    var items = all(ctx, Star_expressionContext.class);
    if (items.size() == 1 && ctx.getChildCount() == 1) {  // no comma
      return starExpression(items.getFirst());
    }
    return new Expr.TupleDisplay(items.stream().map(this::starExpression).toList(), line(ctx), column(ctx));
  }

  private Expr starExpression(Star_expressionContext ctx) {
    if (ctx.getChild(0) instanceof TerminalNode) {  // '*' bitwise_or
      throw unsupported("star expression", ctx);
    }
    return expression((ExpressionContext) ctx.getChild(0));
  }

  private Expr starNamedExpression(Star_named_expressionContext ctx) {
    if (ctx.getChild(0) instanceof TerminalNode) {
      throw unsupported("star expression", ctx);
    }
    return namedExpression((Named_expressionContext) ctx.getChild(0));
  }

  private Expr disjunction(DisjunctionContext ctx) {
    var items = all(ctx, ConjunctionContext.class);
    var result = expr(items.getFirst());
    for (var i = 1; i < items.size(); i++) {
      result = new Expr.Or(result, expr(items.get(i)), line(ctx), column(ctx));
    }
    return result;
  }

  private Expr conjunction(ConjunctionContext ctx) {
    var items = all(ctx, InversionContext.class);
    var result = expr(items.getFirst());
    for (var i = 1; i < items.size(); i++) {
      result = new Expr.And(result, expr(items.get(i)), line(ctx), column(ctx));
    }
    return result;
  }

  private Expr inversion(InversionContext ctx) {
    if (ctx.getChild(0) instanceof TerminalNode) {  // 'not' inversion
      return new Expr.Not(expr(ctx.getChild(1)), line(ctx), column(ctx));
    }
    return expr(ctx.getChild(0));
  }

  private Expr comparison(ComparisonContext ctx) {
    var first = expr(ctx.getChild(0));
    var pairs = all(ctx, Compare_op_bitwise_or_pairContext.class);
    if (pairs.isEmpty()) {
      return first;
    }
    var ops = new ArrayList<Expr.CompareOperator>();
    var rest = new ArrayList<Expr>();
    for (var pair : pairs) {
      var op = pair.getChild(0);
      ops.add(switch (op) {
        case Eq_bitwise_orContext _ -> Expr.CompareOperator.EQ;
        case Noteq_bitwise_orContext _ -> Expr.CompareOperator.NE;
        case Lt_bitwise_orContext _ -> Expr.CompareOperator.LT;
        case Lte_bitwise_orContext _ -> Expr.CompareOperator.LE;
        case Gt_bitwise_orContext _ -> Expr.CompareOperator.GT;
        case Gte_bitwise_orContext _ -> Expr.CompareOperator.GE;
        case In_bitwise_orContext _ -> Expr.CompareOperator.IN;
        case Notin_bitwise_orContext _ -> Expr.CompareOperator.NOT_IN;
        case Isnot_bitwise_orContext c -> throw unsupported("is not", c);
        case Is_bitwise_orContext c -> throw unsupported("is", c);
        default -> throw new AssertionError(op.getText());
      });
      rest.add(expr(op.getChild(op.getChildCount() - 1)));
    }
    return new Expr.Compare(first, ops, rest, line(ctx), column(ctx));
  }

  /** bitwise_or, bitwise_xor, bitwise_and, shift_expr, sum and term: left recursive binary rules. */
  private Expr binary(ParserRuleContext ctx) {
    if (ctx.getChildCount() == 1) {
      return expr(ctx.getChild(0));
    }
    var op = ctx.getChild(1);
    var operator = switch (op.getText()) {
      case "|" -> Expr.BinaryOperator.BIT_OR;
      case "^" -> Expr.BinaryOperator.BIT_XOR;
      case "&" -> Expr.BinaryOperator.BIT_AND;
      case "<<" -> Expr.BinaryOperator.LSHIFT;
      case ">>" -> Expr.BinaryOperator.RSHIFT;
      case "+" -> Expr.BinaryOperator.ADD;
      case "-" -> Expr.BinaryOperator.SUB;
      case "*" -> Expr.BinaryOperator.MUL;
      case "/" -> Expr.BinaryOperator.DIV;
      case "//" -> Expr.BinaryOperator.FLOOR_DIV;
      case "%" -> Expr.BinaryOperator.MOD;
      case "@" -> throw unsupported("matrix multiplication", op);
      default -> throw new AssertionError(op.getText());
    };
    return new Expr.BinOp(operator, expr(ctx.getChild(0)), expr(ctx.getChild(2)), line(ctx), column(ctx));
  }

  private Expr factor(FactorContext ctx) {
    if (ctx.getChildCount() == 1) {
      return expr(ctx.getChild(0));
    }
    var operator = switch (ctx.getChild(0).getText()) {
      case "+" -> Expr.UnaryOperator.POS;
      case "-" -> Expr.UnaryOperator.NEG;
      case "~" -> Expr.UnaryOperator.INVERT;
      default -> throw new AssertionError(ctx.getText());
    };
    return new Expr.UnaryOp(operator, expr(ctx.getChild(1)), line(ctx), column(ctx));
  }

  private Expr power(PowerContext ctx) {
    if (ctx.getChildCount() == 1) {
      return expr(ctx.getChild(0));
    }
    return new Expr.BinOp(Expr.BinaryOperator.POW, expr(ctx.getChild(0)), expr(ctx.getChild(2)),
        line(ctx), column(ctx));
  }

  private Expr awaitPrimary(Await_primaryContext ctx) {
    if (ctx.getChild(0) instanceof TerminalNode await) {
      throw unsupported("await", await);
    }
    return expr(ctx.getChild(0));
  }

  /** Shared by {@code primary} and {@code t_primary}. */
  private Expr primary(ParserRuleContext ctx) {
    if (ctx.getChildCount() == 1) {
      return atom((AtomContext) ctx.getChild(0));
    }
    var base = (ParserRuleContext) ctx.getChild(0);
    var op = ctx.getChild(1);
    if (op instanceof GenexpContext genexp) {
      throw unsupported("generator expression", genexp);
    }
    return switch (op.getText()) {
      case "." -> throw unsupported("attribute access", op);  // only 'obj.name(args)' is accepted
      case "(" -> call(ctx, base);
      case "[" -> new Expr.Subscript(expr(base), subscriptIndex(one(ctx, SlicesContext.class)),
          line(ctx), column(ctx));
      default -> throw new AssertionError(ctx.getText());
    };
  }

  private Expr call(ParserRuleContext ctx, ParserRuleContext base) {
    var args = callArguments(one(ctx, ArgumentsContext.class));
    if (base.getChildCount() == 3 && base.getChild(1) instanceof TerminalNode dot
        && dot.getText().equals(".")) {
      var receiver = expr(base.getChild(0));
      var name = identifier((NameContext) base.getChild(2));
      return new Expr.MethodCall(receiver, name, args, line(ctx), column(ctx));
    }
    return new Expr.Call(expr(base), args, line(ctx), column(ctx));
  }

  private List<Expr> callArguments(ArgumentsContext ctx) {
    if (ctx == null) {
      return List.of();
    }
    var result = new ArrayList<Expr>();
    for (var child : one(ctx, ArgsContext.class).children) {
      switch (child) {
        case Starred_expressionContext c -> throw unsupported("star-unpacking in call", c);
        case Assignment_expressionContext c -> throw unsupported("assignment expression", c);
        case ExpressionContext c -> result.add(expression(c));
        case KwargsContext c -> throw unsupportedKeywordArguments(c);
        default -> { }  // ','
      }
    }
    return result;
  }

  private static UnsupportedSyntaxException unsupportedKeywordArguments(KwargsContext ctx) {
    var first = ctx.getChild(0);
    var inner = first.getChild(0);
    var feature = switch (first) {
      case Kwarg_or_starredContext _ ->
          inner instanceof Starred_expressionContext ? "star-unpacking in call" : "keyword argument";
      default -> inner instanceof TerminalNode ? "**-unpacking in call" : "keyword argument";
    };
    return unsupported(feature, first);
  }

  private Expr subscriptIndex(SlicesContext ctx) {
    var items = new ArrayList<Expr>();
    for (var child : ctx.children) {
      switch (child) {
        case SliceContext c -> items.add(slice(c));
        case Starred_expressionContext c -> throw unsupported("star expression", c);
        default -> { }  // ','
      }
    }
    if (items.size() == 1 && ctx.getChildCount() == 1) {  // no comma
      return items.getFirst();
    }
    return new Expr.TupleDisplay(items, line(ctx), column(ctx));
  }

  private Expr slice(SliceContext ctx) {
    if (ctx.getChild(0) instanceof Named_expressionContext named) {
      return namedExpression(named);
    }
    var parts = new Expr[3];
    var colons = 0;
    for (var child : ctx.children) {
      if (child instanceof ExpressionContext e) {
        parts[colons] = expression(e);
      } else {
        colons++;  // ':'
      }
    }
    for (var i = 0; i < parts.length; i++) {
      if (parts[i] == null) {
        parts[i] = new Expr.NoneLiteral(line(ctx), column(ctx));
      }
    }
    return new Expr.Slice(parts[0], parts[1], parts[2], line(ctx), column(ctx));
  }

  private Expr atom(AtomContext ctx) {
    return switch (ctx.getChild(0)) {
      case NameContext n -> new Expr.Name(identifier(n), line(ctx), column(ctx));
      case StringsContext s -> strings(s);
      case TupleContext t -> new Expr.TupleDisplay(starNamedExpressions(t), line(t), column(t));
      case GroupContext g -> group(g);
      case GenexpContext g -> throw unsupported("generator expression", g);
      case ListContext l -> new Expr.ListDisplay(starNamedExpressions(l), line(l), column(l));
      case ListcompContext c -> throw unsupported("list comprehension", c);
      case DictContext d -> dictDisplay(d);
      case SetContext s -> throw unsupported("set display", s);
      case DictcompContext c -> throw unsupported("dict comprehension", c);
      case SetcompContext c -> throw unsupported("set comprehension", c);
      case TerminalNode t -> switch (t.getText()) {
        case "True" -> new Expr.Literal(Boolean.TRUE, line(t), column(t));
        case "False" -> new Expr.Literal(Boolean.FALSE, line(t), column(t));
        case "None" -> new Expr.NoneLiteral(line(t), column(t));
        case "..." -> throw unsupported("ellipsis", t);
        default -> new Expr.Literal(Literals.parseNumber(t.getText(), line(t), column(t)), line(t), column(t));
      };
      default -> throw new AssertionError(ctx.getText());
    };
  }

  private Expr strings(StringsContext ctx) {
    var sb = new StringBuilder();
    for (var child : ctx.children) {
      switch (child) {
        case StringContext s -> sb.append(Literals.decodeString(s.getText(), line(s), column(s)));
        case FstringContext f -> throw unsupported("f-string", f);
        case TstringContext t -> throw unsupported("t-string", t);
        default -> throw new AssertionError(child.getText());
      }
    }
    return new Expr.Literal(sb.toString(), line(ctx), column(ctx));  // adjacent literals are concatenated
  }

  private Expr group(GroupContext ctx) {
    return switch (ctx.getChild(1)) {
      case Yield_exprContext y -> throw unsupported("yield", y);
      case Named_expressionContext n -> namedExpression(n);
      default -> throw new AssertionError(ctx.getText());
    };
  }

  /** The elements of a tuple or list display, flattening the {@code star_named_expressions} child. */
  private List<Expr> starNamedExpressions(ParserRuleContext ctx) {
    var result = new ArrayList<Expr>();
    for (var child : ctx.children) {
      switch (child) {
        case Star_named_expressionContext e -> result.add(starNamedExpression(e));
        case Star_named_expressionsContext es -> result.addAll(starNamedExpressions(es));
        default -> { }
      }
    }
    return result;
  }

  private Expr dictDisplay(DictContext ctx) {
    var entries = new ArrayList<Expr.DictEntry>();
    var pairs = one(ctx, Double_starred_kvpairsContext.class);
    if (pairs != null) {
      for (var pair : all(pairs, Double_starred_kvpairContext.class)) {
        if (pair.getChild(0) instanceof TerminalNode) {  // '**' bitwise_or
          throw unsupported("dict unpacking", pair);
        }
        var kv = (KvpairContext) pair.getChild(0);
        var expressions = all(kv, ExpressionContext.class);
        entries.add(new Expr.DictEntry(expression(expressions.get(0)), expression(expressions.get(1)),
            line(kv), column(kv)));
      }
    }
    return new Expr.DictDisplay(entries, line(ctx), column(ctx));
  }
}