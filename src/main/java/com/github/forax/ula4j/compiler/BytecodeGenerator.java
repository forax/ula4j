package com.github.forax.ula4j.compiler;

import com.github.forax.ula4j.ast.Block;
import com.github.forax.ula4j.ast.Expr;
import com.github.forax.ula4j.ast.Script;
import com.github.forax.ula4j.ast.Stmt;
import com.github.forax.ula4j.ast.Target;
import com.github.forax.ula4j.ast.UnsupportedSyntaxException;
import com.github.forax.ula4j.compiler.ScopeAnalyzer.FunctionAnalysis;
import com.github.forax.ula4j.compiler.ScopeAnalyzer.FunctionScope;
import com.github.forax.ula4j.rt.Dictionary;
import com.github.forax.ula4j.rt.ModuleContext;
import com.github.forax.ula4j.rt.PyFunction;
import com.github.forax.ula4j.rt.PyTuple;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

/// Turns the AST into class files: Script (static __main__()) and Fn_<name>
/// (static body(captures..., PyFunction self, params... [, PyTuple rest])).
///
/// Everything dynamic is an invokedynamic. Intrinsics are linked with RT.bsmSpecialize using the
/// descriptor of the intrinsic itself. A comparison returns a primitive boolean consumed directly
/// by branch instructions.
public final class BytecodeGenerator {
  private BytecodeGenerator() { throw new AssertionError(); }

  private static final int MAX_PARAMETERS = 254;           // JVM and MethodHandle limit
  private static final int MAX_CALL_ARGUMENTS = MAX_PARAMETERS - 1;
  private static final int LDC_CHUNK = 20_000;             // 3 bytes per char at most, 65535 bytes per constant

  private static final String GEN_PACKAGE = "com/github/forax/ula4j/gen/";
  private static final String RT = "com/github/forax/ula4j/rt/RT";
  private static final String PY_NONE = "com/github/forax/ula4j/rt/PyNone";
  private static final String PY_DICT = "com/github/forax/ula4j/rt/PyDict";
  private static final String PY_TUPLE = "com/github/forax/ula4j/rt/PyTuple";
  private static final String PY_LIST = "com/github/forax/ula4j/rt/PyList";
  private static final String INTRINSICS = "com/github/forax/ula4j/rt/intrinsic/";
  private static final String ARITHMETIC = INTRINSICS + "ArithmeticIntrinsics";
  private static final String COMPARISON = INTRINSICS + "ComparisonIntrinsics";
  private static final String TRUTHINESS = INTRINSICS + "TruthinessIntrinsics";
  private static final String CONTAINMENT = INTRINSICS + "ContainmentIntrinsics";
  private static final String ITERATION = INTRINSICS + "IteratorIntrinsics";
  private static final String DICT = INTRINSICS + "DictIntrinsics";
  private static final String LIST = INTRINSICS + "ListIntrinsics";
  private static final String TUPLE = INTRINSICS + "TupleIntrinsics";
  private static final String ITEM = INTRINSICS + "ItemIntrinsics";

  private static final String O = "Ljava/lang/Object;";
  private static final String UNARY = "(" + O + ")" + O;
  private static final String BINARY = "(" + O + O + ")" + O;
  private static final String TRINARY = "(" + O + O + O + ")" + O;
  private static final String UNARY_BOOL = "(" + O + ")Z";
  private static final String BINARY_BOOL = "(" + O + O + ")Z";

  private static final String BSM_PREFIX =
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;";
  private static final String CALL_SITE = ")Ljava/lang/invoke/CallSite;";
  private static final String CONDY_PREFIX =
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;";

  private static Handle bsm(String name, String descriptor) {
    return new Handle(H_INVOKESTATIC, RT, name, descriptor, false);
  }

  private static final Handle BSM_SPECIALIZE = bsm("bsmSpecialize", BSM_PREFIX + "Ljava/lang/Class;" + CALL_SITE);
  private static final Handle BSM_DEF = bsm("bsmDef", BSM_PREFIX + "I" + CALL_SITE);
  private static final Handle BSM_CALL = bsm("bsmCall", BSM_PREFIX + "I[Ljava/lang/String;" + CALL_SITE);
  private static final Handle BSM_GET_GLOBAL = bsm("bsmGetGlobal", BSM_PREFIX + "Ljava/lang/String;" + CALL_SITE);
  private static final Handle BSM_SET_GLOBAL = bsm("bsmSetGlobal", BSM_PREFIX + "Ljava/lang/String;" + CALL_SITE);
  private static final Handle BSM_CONST = bsm("bsmConst", CONDY_PREFIX + O + ")" + O);
  private static final Handle BSM_BIG_INT = bsm("bsmBigInt", CONDY_PREFIX + "Ljava/lang/String;)" + O);

  // ---------------------------------------------------------------------------------------------
  // naming and types, shared with the runtime

  public static String functionClassName(Stmt.FunctionDef def) {
    var sanitized = new StringBuilder();
    for (var c : def.name().toCharArray()) {
      sanitized.append(Character.isLetterOrDigit(c) && c < 128 || c == '_' ? c : '_');
    }
    return GEN_PACKAGE + "Fn_" + sanitized;
  }

  /// Variables a function receives through `insertArguments`, in `freeVariables` order.
  /// A nested function reaches its own name through `self`, so that name is not captured.
  public static List<String> captureNames(Stmt.FunctionDef def, FunctionAnalysis analysis) {
    var captureNames = new ArrayList<String>();
    for (var name : analysis.freeVariables()) {
      if (!name.equals(def.name())) {
        captureNames.add(name);
      }
    }
    return captureNames;
  }

  /// (Object x captures, PyFunction self, Object x arity [, PyTuple rest]) -> Object
  public static MethodType bodyType(int captures, int arity, boolean varargs) {
    var types = new ArrayList<Class<?>>();
    for (var i = 0; i < captures; i++) {
      types.add(Object.class);
    }
    types.add(PyFunction.class);
    for (var i = 0; i < arity; i++) {
      types.add(Object.class);
    }
    if (varargs) {
      types.add(PyTuple.class);
    }
    return MethodType.methodType(Object.class, types);
  }

  // ---------------------------------------------------------------------------------------------
  // entry points

  public static byte[] generateScript(ModuleContext ctx, Dictionary dictionary, Script script, String internalName) {
    var cw = newClassWriter(internalName);
    var mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "__main__", "()V", null, null);
    mv.visitCode();
    var emitter = new Emitter(mv, ctx, dictionary, null, Set.of());
    emitter.block(script.body());
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  public static byte[] generateFunction(ModuleContext ctx, Dictionary dictionary,
                                        Stmt.FunctionDef def, FunctionScope scope) {
    var analysis = scope.analysis();
    var captures = captureNames(def, analysis);
    var arity = def.parameters().size();
    var varargs = def.rest().isPresent();
    var parameterCount = captures.size() + 1 + arity + (varargs ? 1 : 0);
    if (parameterCount > MAX_PARAMETERS) {
      throw new SemanticErrorException("function '" + def.name() + "' has too many parameters and captured variables ("
          + parameterCount + ", at most " + MAX_PARAMETERS + " are supported)", def.line(), def.column());
    }

    var cw = newClassWriter(functionClassName(def));
    var descriptor = bodyType(captures.size(), arity, varargs).toMethodDescriptorString();
    var mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, def.name(), descriptor, null, null);
    mv.visitCode();

    var emitter = new Emitter(mv, ctx, dictionary, def, scope.analysis().freeVariables());
    for (var capture : captures) {
      emitter.declare(capture);
    }
    emitter.selfSlot = emitter.allocate();
    for (var parameter : def.parameters()) {
      emitter.declare(parameter.name());
    }
    def.rest().ifPresent(rest -> emitter.declare(rest.name()));
    for (var local : analysis.locals()) {
      if (!emitter.slots.containsKey(local)) {
        emitter.declare(local);
      }
    }

    scope.childByDef().forEach(ctx::registerScope);

    emitter.block(def.body());
    mv.visitFieldInsn(GETSTATIC, PY_NONE, "INSTANCE", "L" + PY_NONE + ";");
    mv.visitInsn(ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  private static ClassWriter newClassWriter(String internalName) {
    var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
      @Override
      protected String getCommonSuperClass(String type1, String type2) {
        return "java/lang/Object";  // never load a class, every merged value is used as an Object
      }
    };
    cw.visit(V25, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, internalName, null, "java/lang/Object", null);
    cw.visitSource("script", null);
    return cw;
  }

  private static UnsupportedSyntaxException unsupported(String construct, int line, int column) {
    return new UnsupportedSyntaxException(construct, line, column);
  }

  // ---------------------------------------------------------------------------------------------
  // operator tables

  private static String binaryName(Expr.BinaryOperator op) {
    return switch (op) {
      case ADD -> "$add$";
      case SUB -> "$sub$";
      case MUL -> "$mul$";
      case DIV -> "$div$";
      case FLOOR_DIV -> "$floorDiv$";
      case MOD -> "$mod$";
      case POW -> "$pow$";
      case LSHIFT -> "$lshift$";
      case RSHIFT -> "$rshift$";
      case BIT_AND -> "$bitAnd$";
      case BIT_OR -> "$bitOr$";
      case BIT_XOR -> "$bitXor$";
    };
  }

  private static String augmentedName(Expr.BinaryOperator op) {
    return switch (op) {
      case ADD -> "$iadd$";
      case SUB -> "$isub$";
      case MUL -> "$imul$";
      case DIV -> "$idiv$";
      case FLOOR_DIV -> "$ifloorDiv$";
      case MOD -> "$imod$";
      case POW -> "$ipow$";
      case LSHIFT -> "$ilshift$";
      case RSHIFT -> "$irshift$";
      case BIT_AND -> "$iand$";
      case BIT_OR -> "$ior$";
      case BIT_XOR -> "$ixor$";
    };
  }

  private static String unaryName(Expr.UnaryOperator op) {
    return switch (op) {
      case POS -> "$pos$";
      case NEG -> "$neg$";
      case INVERT -> "$invert$";
    };
  }

  private static int utf8Length(String s) {
    var length = 0;
    for (var i = 0; i < s.length(); i++) {
      var c = s.charAt(i);
      length += (c >= 0x0001 && c <= 0x007F) ? 1 : (c <= 0x07FF ? 2 : 3);
    }
    return length;
  }

  // ---------------------------------------------------------------------------------------------
  // the emitter: one per generated method

  private static final class Emitter {
    private final MethodVisitor mv;
    private final ModuleContext ctx;
    private final Dictionary dictionary;
    private final Stmt.FunctionDef function;  // null in __main__
    private final Set<String> frees;
    private final HashMap<String, Integer> slots;
    private int nextSlot;
    private int selfSlot = -1;
    private Label breakTarget;
    private Label continueTarget;

    Emitter(MethodVisitor mv, ModuleContext ctx, Dictionary dictionary, Stmt.FunctionDef function, Set<String> frees) {
      this.mv = mv;
      this.ctx = ctx;
      this.dictionary = dictionary;
      this.function = function;
      this.frees = frees;
      this.slots = new HashMap<>();
      super();
    }

    int allocate() { return nextSlot++; }

    void declare(String name) { slots.put(name, allocate()); }

    // ------------------------------------------------------------------ small helpers

    private void intrinsic(String owner, String name, String descriptor) {
      mv.visitInvokeDynamicInsn(name, descriptor, BSM_SPECIALIZE, Type.getObjectType(owner));
    }

    private void truthy() { intrinsic(TRUTHINESS, "$truthy$", UNARY_BOOL); }

    private void boxBoolean() {
      mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
    }

    private void pushBoolean(boolean value) {
      mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", value ? "TRUE" : "FALSE", "Ljava/lang/Boolean;");
    }

    private void line(int line) {
      var label = new Label();
      mv.visitLabel(label);
      mv.visitLineNumber(line, label);
    }

    private void loadName(String name) {
      var slot = slots.get(name);
      if (slot != null) {
        mv.visitVarInsn(ALOAD, slot);
        return;
      }
      if (function != null && name.equals(function.name()) && frees.contains(name)) {
        mv.visitVarInsn(ALOAD, selfSlot);  // a nested function refers to itself through self
        return;
      }
      mv.visitInvokeDynamicInsn("getGlobal", "()" + O, BSM_GET_GLOBAL, name);
    }

    /// Stores the value on top of the stack.
    private void storeName(String name) {
      var slot = slots.get(name);
      if (slot != null) {
        mv.visitVarInsn(ASTORE, slot);
        return;
      }
      if (function == null) {
        mv.visitInvokeDynamicInsn("setGlobal", "(" + O + ")V", BSM_SET_GLOBAL, name);
        return;
      }
      throw new AssertionError("'" + name + "' is not a local of '" + function.name() + "'");
    }

    private void storeTarget(Target target) {
      switch (target) {
        case Target.NameTarget n -> storeName(n.id());
        case Target.SubscriptTarget t -> storeSubscriptTarget(t);
        case Target.TupleTarget t -> throw unsupported("tuple unpacking", t.line(), t.column());
      }
    }

    /// A chained assignment has already evaluated the RHS. Spill it while evaluating the target.
    private void storeSubscriptTarget(Target.SubscriptTarget target) {
      var value = allocate();
      mv.visitVarInsn(ASTORE, value);
      expr(target.value());
      expr(target.index());
      mv.visitVarInsn(ALOAD, value);
      intrinsic(ITEM, "$setItem$", TRINARY);
      mv.visitInsn(POP);
    }

    // ------------------------------------------------------------------ statements

    void block(Block block) {
      for (var stmt : block.stmts()) {
        stmt(stmt);
      }
    }

    private void stmt(Stmt stmt) {
      line(stmt.line());
      switch (stmt) {
        case Stmt.ExprStmt s -> {
          expr(s.expr());
          mv.visitInsn(POP);
        }
        case Stmt.Assign s -> assign(s);
        case Stmt.AugAssign s -> augAssign(s);
        case Stmt.Del s -> delete(s);
        case Stmt.Return s -> {
          expr(s.value());
          mv.visitInsn(ARETURN);
        }
        case Stmt.Break _ -> mv.visitJumpInsn(GOTO, breakTarget);
        case Stmt.Continue _ -> mv.visitJumpInsn(GOTO, continueTarget);
        case Stmt.If s -> ifStmt(s);
        case Stmt.While s -> whileStmt(s);
        case Stmt.For s -> forStmt(s);
        case Stmt.FunctionDef s -> functionDef(s);
      }
    }

    /// a = b = value: evaluate the value once, then assign left to right.
    private void assign(Stmt.Assign s) {
      expr(s.value());
      var targets = s.targets();
      for (var i = 0; i < targets.size(); i++) {
        if (i < targets.size() - 1) {
          mv.visitInsn(DUP);
        }
        storeTarget(targets.get(i));
      }
    }

    private void delete(Stmt.Del s) {
      for (var target : s.targets()) {
        expr(target.value());
        expr(target.index());
        intrinsic(ITEM, "$deleteItem$", BINARY);
        mv.visitInsn(POP);
      }
    }

    private void augAssign(Stmt.AugAssign s) {
      switch (s.target()) {
        case Target.NameTarget n -> {
          loadName(n.id());
          expr(s.value());
          intrinsic(ARITHMETIC, augmentedName(s.op()), BINARY);
          storeName(n.id());
        }
        case Target.SubscriptTarget t -> augAssignSubscript(t, s);
        case Target.TupleTarget t -> throw new AssertionError("augmented assign tuple target");
      }
    }

    /// Evaluate container and index once, then perform get, operation and set in Python order.
    private void augAssignSubscript(Target.SubscriptTarget target, Stmt.AugAssign statement) {
      var container = allocate();
      var index = allocate();
      var value = allocate();

      expr(target.value());
      mv.visitVarInsn(ASTORE, container);
      expr(target.index());
      mv.visitVarInsn(ASTORE, index);

      mv.visitVarInsn(ALOAD, container);
      mv.visitVarInsn(ALOAD, index);
      intrinsic(ITEM, "$getItem$", BINARY);
      expr(statement.value());
      intrinsic(ARITHMETIC, augmentedName(statement.op()), BINARY);
      mv.visitVarInsn(ASTORE, value);

      mv.visitVarInsn(ALOAD, container);
      mv.visitVarInsn(ALOAD, index);
      mv.visitVarInsn(ALOAD, value);
      intrinsic(ITEM, "$setItem$", TRINARY);
      mv.visitInsn(POP);
    }

    private void ifStmt(Stmt.If s) {
      var orElse = new Label();
      branch(s.test(), orElse, false);
      block(s.body());
      if (s.orElse().stmts().isEmpty()) {
        mv.visitLabel(orElse);
        return;
      }
      var end = new Label();
      mv.visitJumpInsn(GOTO, end);
      mv.visitLabel(orElse);
      block(s.orElse());
      mv.visitLabel(end);
    }

    /// break skips the else clause; else belongs to the enclosing context.
    private void whileStmt(Stmt.While s) {
      var top = new Label();
      var orElse = new Label();
      var end = new Label();
      var savedBreak = breakTarget;
      var savedContinue = continueTarget;

      mv.visitLabel(top);
      branch(s.test(), orElse, false);
      breakTarget = end;
      continueTarget = top;
      block(s.body());
      breakTarget = savedBreak;
      continueTarget = savedContinue;
      mv.visitJumpInsn(GOTO, top);

      mv.visitLabel(orElse);
      block(s.orElse());
      mv.visitLabel(end);
    }

    private void forStmt(Stmt.For s) {
      if (s.target() instanceof Target.TupleTarget t) {
        throw unsupported("tuple unpacking", t.line(), t.column());
      }
      var iterator = allocate();
      expr(s.iter());
      intrinsic(ITERATION, "$iterator$", UNARY);
      mv.visitVarInsn(ASTORE, iterator);

      var top = new Label();
      var exhausted = new Label();
      var end = new Label();
      var savedBreak = breakTarget;
      var savedContinue = continueTarget;

      mv.visitLabel(top);
      mv.visitVarInsn(ALOAD, iterator);
      intrinsic(ITERATION, "$next$", UNARY);  // Java null when exhausted
      mv.visitInsn(DUP);
      mv.visitJumpInsn(IFNULL, exhausted);
      storeTarget(s.target());
      breakTarget = end;
      continueTarget = top;
      block(s.body());
      breakTarget = savedBreak;
      continueTarget = savedContinue;
      mv.visitJumpInsn(GOTO, top);

      mv.visitLabel(exhausted);
      mv.visitInsn(POP);  // the null
      block(s.orElse());
      mv.visitLabel(end);
    }

    /// Captured values are pushed in `freeVariables` order, the order `insertArguments` binds them.
    /// A function defined at module level captures nothing: its free names are globals.
    private void functionDef(Stmt.FunctionDef def) {
      var captures = function == null
          ? List.<String>of()
          : captureNames(def, ctx.scopeOf(def).analysis());
      for (var capture : captures) {
        loadName(capture);
      }
      mv.visitInvokeDynamicInsn(def.name(), "(" + O.repeat(captures.size()) + ")" + O,
          BSM_DEF, dictionary.register(def));
      storeName(def.name());
    }

    // ------------------------------------------------------------------ conditions

    /// Evaluate e as a condition and jump when its truthiness equals jumpIf.
    private void branch(Expr e, Label target, boolean jumpIf) {
      switch (e) {
        case Expr.Not n -> branch(n.operand(), target, !jumpIf);
        case Expr.And a -> {
          if (!jumpIf) {
            branch(a.left(), target, false);
            branch(a.right(), target, false);
          } else {
            var skip = new Label();
            branch(a.left(), skip, false);
            branch(a.right(), target, true);
            mv.visitLabel(skip);
          }
        }
        case Expr.Or o -> {
          if (jumpIf) {
            branch(o.left(), target, true);
            branch(o.right(), target, true);
          } else {
            var skip = new Label();
            branch(o.left(), skip, true);
            branch(o.right(), target, false);
            mv.visitLabel(skip);
          }
        }
        case Expr.Compare c -> compareBranch(c, target, jumpIf);
        default -> {
          expr(e);
          truthy();
          mv.visitJumpInsn(jumpIf ? IFNE : IFEQ, target);
        }
      }
    }

    /// Leaves the primitive boolean returned by the comparison intrinsic on the stack.
    private void compareOp(Expr.CompareOperator op) {
      switch (op) {
        case EQ -> intrinsic(COMPARISON, "$eq$", BINARY_BOOL);
        case NE -> intrinsic(COMPARISON, "$ne$", BINARY_BOOL);
        case LT -> intrinsic(COMPARISON, "$lt$", BINARY_BOOL);
        case LE -> intrinsic(COMPARISON, "$le$", BINARY_BOOL);
        case GT -> intrinsic(COMPARISON, "$gt$", BINARY_BOOL);
        case GE -> intrinsic(COMPARISON, "$ge$", BINARY_BOOL);
        case IN -> intrinsic(CONTAINMENT, "$in$", BINARY_BOOL);
        case NOT_IN -> {
          intrinsic(CONTAINMENT, "$in$", BINARY_BOOL);
          mv.visitInsn(ICONST_1);
          mv.visitInsn(IXOR);
        }
      }
    }

    private void compareBranch(Expr.Compare c, Label target, boolean jumpIf) {
      var ops = c.ops();
      if (ops.size() == 1) {
        expr(c.first());
        expr(c.rest().getFirst());
        compareOp(ops.getFirst());
        mv.visitJumpInsn(jumpIf ? IFNE : IFEQ, target);
        return;
      }
      var current = allocate();
      expr(c.first());
      mv.visitVarInsn(ASTORE, current);
      var skip = new Label();
      var onFalse = jumpIf ? skip : target;
      for (var i = 0; i < ops.size(); i++) {
        var last = i == ops.size() - 1;
        mv.visitVarInsn(ALOAD, current);
        expr(c.rest().get(i));
        if (!last) {
          mv.visitInsn(DUP);
          mv.visitVarInsn(ASTORE, current);
        }
        compareOp(ops.get(i));
        if (!last) {
          mv.visitJumpInsn(IFEQ, onFalse);
        } else {
          mv.visitJumpInsn(jumpIf ? IFNE : IFEQ, target);
        }
      }
      if (jumpIf) {
        mv.visitLabel(skip);
      }
    }

    private void compareValue(Expr.Compare c) {
      if (c.ops().size() == 1) {
        expr(c.first());
        expr(c.rest().getFirst());
        compareOp(c.ops().getFirst());
        boxBoolean();
        return;
      }
      var onFalse = new Label();
      var end = new Label();
      compareBranch(c, onFalse, false);
      pushBoolean(true);
      mv.visitJumpInsn(GOTO, end);
      mv.visitLabel(onFalse);
      pushBoolean(false);
      mv.visitLabel(end);
    }

    /// and/or return one of their operands.
    private void shortCircuit(Expr left, Expr right, int jumpOpcode) {
      var end = new Label();
      expr(left);
      mv.visitInsn(DUP);
      truthy();
      mv.visitJumpInsn(jumpOpcode, end);
      mv.visitInsn(POP);
      expr(right);
      mv.visitLabel(end);
    }

    // ------------------------------------------------------------------ expressions

    /// Every expression leaves exactly one Object on the stack.
    private void expr(Expr e) {
      switch (e) {
        case Expr.Name n -> loadName(n.id());
        case Expr.Literal l -> literal(l.value());
        case Expr.NoneLiteral _ ->
            mv.visitFieldInsn(GETSTATIC, PY_NONE, "INSTANCE", "L" + PY_NONE + ";");
        case Expr.BinOp b -> {
          expr(b.left());
          expr(b.right());
          intrinsic(ARITHMETIC, binaryName(b.op()), BINARY);
        }
        case Expr.UnaryOp u -> {
          expr(u.operand());
          intrinsic(ARITHMETIC, unaryName(u.op()), UNARY);
        }
        case Expr.Not n -> {
          expr(n.operand());
          intrinsic(TRUTHINESS, "$not$", UNARY_BOOL);
          boxBoolean();
        }
        case Expr.And a -> shortCircuit(a.left(), a.right(), IFEQ);
        case Expr.Or o -> shortCircuit(o.left(), o.right(), IFNE);
        case Expr.Compare c -> compareValue(c);
        case Expr.Conditional c -> {
          var orElse = new Label();
          var end = new Label();
          branch(c.test(), orElse, false);
          expr(c.then());
          mv.visitJumpInsn(GOTO, end);
          mv.visitLabel(orElse);
          expr(c.orElse());
          mv.visitLabel(end);
        }
        case Expr.Call c -> {
          var argsSize = c.args().positional().size() + c.args().keywords().size();
          if (argsSize > MAX_CALL_ARGUMENTS) {
            throw unsupported("call with more than " + MAX_CALL_ARGUMENTS + " arguments", c.line(), c.column());
          }
          expr(c.func());
          for (var arg : c.args().positional()) {
            expr(arg);
          }
          for (var keyword : c.args().keywords()) {
            expr(keyword.value());
          }
          var callName = c.func() instanceof Expr.Name name ? name.id() : "call";
          var bsmArgs = new Object[1 + c.args().keywords().size()];
          bsmArgs[0] = c.args().positional().size();
          for(var i = 0; i < c.args().keywords().size(); i++) {
            bsmArgs[i + 1] = c.args().keywords().get(i).name();
          }
          mv.visitInvokeDynamicInsn(callName, "(" + O.repeat(argsSize + 1) + ")" + O, BSM_CALL, bsmArgs);
        }
        case Expr.MethodCall m -> throw unsupported("method call", m.line(), m.column());
        case Expr.Subscript s -> {
          expr(s.value());
          expr(s.index());
          intrinsic(ITEM, "$getItem$", BINARY);
        }
        case Expr.Slice s -> throw unsupported("slice", s.line(), s.column());
        case Expr.ListDisplay d -> listDisplay(d);
        case Expr.TupleDisplay d -> tupleDisplay(d);
        case Expr.DictDisplay d -> dictDisplay(d);
      }
    }

    /// Create a fresh dictionary, then insert its entries from left to right.
    private void dictDisplay(Expr.DictDisplay display) {
      mv.visitMethodInsn(INVOKESTATIC, DICT, "newDict", "()L" + PY_DICT + ";", false);
      for (var entry : display.entries()) {
        mv.visitInsn(DUP);
        expr(entry.key());
        expr(entry.value());
        mv.visitMethodInsn(INVOKEVIRTUAL, PY_DICT, "put", "(" + O + O + ")" + O, false);
        mv.visitInsn(POP);
      }
    }

    /// Create a fresh list, then insert its items from left to right.
    private void listDisplay(Expr.ListDisplay display) {
      mv.visitMethodInsn(INVOKESTATIC, LIST, "newList", "()L" + PY_LIST + ";", false);
      for (var element : display.elements()) {
        mv.visitInsn(DUP);
        expr(element);
        mv.visitMethodInsn(INVOKEVIRTUAL, PY_LIST, "append", "(" + O + ")V", false);
      }
    }

    /// Create a tuple, using specialized representations for small tuples.
    private void tupleDisplay(Expr.TupleDisplay display) {
      var elements = display.elements();
      var size = elements.size();
      switch (size) {
        case 0 -> mv.visitMethodInsn(INVOKESTATIC, TUPLE, "empty",
            "()L" + PY_TUPLE + ";", false);
        case 1, 2, 3, 4 -> {
          var owner = PY_TUPLE + "$PyTuple" + size;
          mv.visitTypeInsn(NEW, owner);
          mv.visitInsn(DUP);
          for (var element : elements) {
            expr(element);
          }
          mv.visitMethodInsn(INVOKESPECIAL, owner, "<init>",
              "(" + O.repeat(size) + ")V", false);
        }
        default -> {
          // PyTupleN: create an array and let fromArray() make a defensive copy.
          mv.visitLdcInsn(size);
          mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
          for (var i = 0; i < size; i++) {
            mv.visitInsn(DUP);
            mv.visitLdcInsn(i);
            expr(elements.get(i));
            mv.visitInsn(AASTORE);
          }
          mv.visitMethodInsn(INVOKESTATIC, TUPLE, "fromArray",
              "([Ljava/lang/Object;)L" + PY_TUPLE + ";", false);
        }
      }
    }

    private void literal(Object value) {
      switch (value) {
        case Long l -> condy("Ljava/lang/Long;", BSM_CONST, l);
        case Double d -> condy("Ljava/lang/Double;", BSM_CONST, d);
        case String s -> pushString(s);
        case Boolean b -> pushBoolean(b);
        case BigInteger b -> condy("Ljava/math/BigInteger;", BSM_BIG_INT, b.toString());
        default -> throw new AssertionError("not a canonical literal: " + value);
      }
    }

    private void condy(String descriptor, Handle bootstrap, Object argument) {
      mv.visitLdcInsn(new ConstantDynamic("literal", descriptor, bootstrap, argument));
    }

    /// Supplementary characters are fine in a constant; long constants must be split.
    private void pushString(String s) {
      if (utf8Length(s) <= 65535) {
        mv.visitLdcInsn(s);
        return;
      }
      for (var start = 0; start < s.length(); start += LDC_CHUNK) {
        mv.visitLdcInsn(s.substring(start, Math.min(s.length(), start + LDC_CHUNK)));
        if (start > 0) {
          mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat",
              "(Ljava/lang/String;)Ljava/lang/String;", false);
        }
      }
    }
  }
}