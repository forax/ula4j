# ula4j: Implementation Plan (rev. 2)

## 0. How to read this document

Every feature carries a status tag:

- **[DONE]**: implemented and covered by tests.
- **[PARTIAL]**: implemented in part. The text says what is missing.
- **[PLANNED]**: not implemented. The design is normative for when it is.

Normative rules say *must*. "Current behavior" describes the code.
§17 lists the places where the code still violates a rule, and §18 is the roadmap.

## 1. Goals and principles

### 1.1 Goals

- Compile a deliberately limited Python subset to JVM bytecode and run it on the JVM.
- Preserve the semantics of a pinned 64-bit CPython 3.14 for every construct that is accepted.
- Use ordinary JVM objects as Python values, with no universal `PyValue` hierarchy.
- Implement generic semantics once, as readable Java intrinsics. Derive specialized versions from them at run time with ASM.
- Use `invokedynamic` for every dynamic operation and call. Use `ConstantDynamic` for immutable scalar constants.

### 1.2 Non-goals for the first implementation

Sets, `try`/`except`/`finally`, `raise`, `assert`, `is`/`is not`,
imports, classes, decorators, annotations, generators, async, comprehensions,
`lambda`, dict views, `sorted`/`list.sort`, f-strings, bytes, complex numbers.

Runtime operations may raise Python exceptions implicitly. Source code cannot catch them.

### 1.3 Core invariants

1. **Accepted syntax has Python semantics.**
2. **Unsupported means rejected** with a located diagnostic (§13.2), never silently approximated.
3. **Generic semantics are authoritative.** Specialized code is derived from the generic intrinsic bytecode and falls back to it.
4. **One implementation per semantic.** No operation may be implemented twice, whether as a fast path or as a helper in another class (see §17, D2 and D3).
5. **Per-call-site specialization state.** Each specialized call site owns its hidden class and its profile. The only shared cache is the per-intrinsic-class table of rewritten bytecode (§12.1), which is immutable and holds no specialized implementation.
6. **Mutable containers stay mutable Python objects.**
7. **Python equality, hashing and ordering are explicit.** Never rely on Java `equals`/`hashCode` of runtime values. The record `equals` of tuples is for tests only.

### 1.4 Architecture at a glance [DONE]

```
source ──ANTLR lexer/parser──▶ parse tree ──ASTBuilder──▶ AST (Script/Block/Stmt/Expr/Target)
                                                             │
                                Compiler.compile(ctx, script)│
                                                             ▼
                         BytecodeGenerator.generateScript ─▶ class "gen/Script" { static __main__() }
                                                             │ RT.defineClass (new loader per class)
                                                             ▼
                       run __main__; each `def` executes an indy (bsmDef) that creates a PyFunction
                                                             │ first call through a call site
                                                             ▼
        FunctionSupport.getCompiledEntry ─ ScopeAnalyzer (passes 1–4) ─ BytecodeGenerator.generateFunction
                                                             ▼
                                      class "gen/Fn_<name>" { static <name>(captures..., self, params...) }
```

Packages:

| Package | Content |
|---|---|
| `grammar.antlr` | `PythonLexer.g4`, `PythonParser.g4` (Python 3.14 grammar), `PythonLexerBase` (indentation, f-string modes) |
| `ast` | records `Script`, `Block`, `Stmt`, `Expr`, `Target`; `ASTBuilder`; `Literals`; `SyntaxErrorException`, `UnsupportedSyntaxException` |
| `compiler` | `ScopeAnalyzer`, `BytecodeGenerator`, `Compiler`, `SemanticErrorException` |
| `rt` | `RT` (bootstraps), `ModuleContext`, `Dictionary`, `FunctionSupport`, `PyFunction`, `PyNone`, `PyTuple`, `PyIterator` |
| `rt.intrinsic` | `@Intrinsic` classes (§6.1) and `IntrinsicSupport` |
| `rt.specializer` | `Specializer` (ASM), plus `SpecializationSupport` in `rt` |
| `rt.error` | Python exception hierarchy and `UnimplementedFeatureError` |

## 2. Value representation

| Python | JVM | Status |
|---|---|---|
| `None` | `PyNone.INSTANCE` (never Java `null`) | DONE |
| `bool` | `Boolean` | DONE |
| `int` | `Long` if it fits, else `BigInteger` | DONE |
| `float` | `Double` | DONE |
| `str` | `String`, code-point semantics | PARTIAL (iteration, ordering, `+`, `*`, `in` only) |
| `tuple` | sealed `PyTuple`: `PyTuple1..4`, `PyTupleN` | DONE (runtime), no tuple displays yet |
| Python function | `PyFunction` (final class) | DONE |
| `list` | `PyList` | PLANNED |
| `dict` | `PyDict` | PLANNED |
| `range`, `slice` | `PyRange`, `PySlice` | PLANNED |
| type object | `PyType` | PLANNED (§3) |
| for-loop iterators | `PyIterator.{TupleIterator, StringIterator}` | DONE, internal only |

### 2.1 Representation rules

- **Canonical integers.** A `BigInteger` never holds a value that fits in a `long`. `IntrinsicSupport.normalize` enforces this and `Expr.Literal` validates it.
- **Canonical tuples.** `TupleIntrinsics.fromArray` is the only factory. Arities 1–4 use fixed records, and 0 and 5+ use `PyTupleN`.
  - The empty tuple is a shared `PyTupleN`.
  - `PyTupleN` copies its input and rejects `null` elements.
  - Every tuple intrinsic must work on every shape, including non-canonical ones such as a 2-element `PyTupleN`.
- **No nulls inside values.** Tuples, globals and builtins reject Java `null`.
- **Internal protocol.** Java `null` returned by `$next$` means "iterator exhausted". It never escapes into Python values.

### 2.2 Identity and equality

- `is` and `is not` are rejected, so no general identity operator exists.
- `==`, `!=`, ordering and hashing are intrinsics. Numeric equality and hashing unify `bool`, `int` and `float`.
- Container comparison uses CPython's identity shortcut on *elements only* (`EqualityIntrinsics.sameOrEqual`). Top-level `nan == nan` is `False`.
- Tests must not depend on the identity of immutable values.

## 3. `PyType` and builtin type objects [PLANNED]

Unchanged from the previous plan:

- `type(x)` is an intrinsic that pattern-matches on the JVM representation.
- Canonical type objects are callable and dispatch to the constructor intrinsic.
- `isinstance` checks the JVM class set of the type. The `int` type covers `Long`, `BigInteger` and `Boolean`.
- Builtin functions have the type `builtin_function_or_method`.
- Until `PyType` exists, `IntrinsicSupport.typeName` is the placeholder for `type(x).__name__`. It currently throws `UnimplementedFeatureError` for unknown classes.

## 4. Collections [PLANNED]

The semantic requirements are unchanged.

**`PyList`**
- Backed by an array.
- Index-based iteration, so mutation during iteration behaves as in CPython.
- Recursive element-wise equality, unhashable.
- Full slice semantics, `+=` that extends in place and preserves aliases, and `*`.

**`PyDict`**
- Custom insertion-ordered open-addressing table.
- Replacement keeps the key's position.
- Deletion and reinsertion are honoured.
- Equality is order-independent.
- Mutation during iteration raises Python `RuntimeError`, never `ConcurrentModificationException`, and key-set changes are detected as CPython does.

**Both**
- Python-facing behavior is reached only through intrinsics, for example `$get$(dict, key)` and `$items$(dict)`.
- The Java `List`/`Map` interfaces are optional conveniences and are never used for Python semantics.

**Tuples**
- Runtime is done: `$size$`, `$get$`, `$concat$`, `$repeat$`, equality, hashing, ordering, `in`, iteration.
- Slicing and the method table are planned.

**Hashing**
- Lists and dicts are unhashable and raise `PyTypeError: unhashable type: 'list'`. The helper exists but is unused.
- String and tuple hash values are unspecified and must never be asserted in tests.

## 5. `None`, constants, interop

### 5.1 Missing states [DONE]

- `None` is `PyNone.INSTANCE`.
- Java `null` is used only as an internal state: unbound global cell, iterator exhaustion.
- There is no unbound-local sentinel. Definite assignment (§8.5) guarantees it, and the JVM verifier also requires it (§8.6).

### 5.2 Constants [DONE]

- `Long` and `Double` literals use `ConstantDynamic` through `RT.bsmConst`. Big integers use `RT.bsmBigInt`, which parses a decimal string.
- `Boolean` uses `Boolean.TRUE`/`FALSE`, and `None` uses `PyNone.INSTANCE`.
- Strings use `ldc`, split into chunks (`concat`) when the modified-UTF-8 length exceeds 65535 bytes.
- List, dict and tuple displays must never be shared constants (planned with displays).

### 5.3 Java interop boundary [PLANNED]

The intended boundary is: `null` becomes `PyNone`, `Integer`/`Short`/`Byte` become `Long`, `Float` becomes `Double`, `Character` becomes a one-character `String`.

Current behaviour: no normalization. `ModuleContext.setGlobal` and `defineBuiltin` only do `requireNonNull`. An `Interop.toPython` normalizer applied at `ModuleContext` entry points is on the roadmap (§18, phase 5).

Intrinsics accept only canonical values.

## 6. Intrinsics and builtins

### 6.1 Intrinsic inventory [PARTIAL]

All are `@Intrinsic public static` methods named `$name$` (§11).

| Class | Intrinsics |
|---|---|
| `ArithmeticIntrinsics` | `$add$ $sub$ $mul$ $div$ $floorDiv$ $mod$ $pow$ $lshift$ $rshift$ $bitAnd$ $bitOr$ $bitXor$`, `$neg$ $pos$ $invert$`, `$iadd$ ... $ixor$` |
| `ComparisonIntrinsics` | `$eq$ $ne$ $lt$ $le$ $gt$ $ge$` (return primitive `boolean`) |
| `EqualityIntrinsics` | `$eq$`, plus non-intrinsic `sameOrEqual` |
| `HashingIntrinsics` | `$hash$` |
| `TupleIntrinsics` | `$size$ $get$ $concat$ $repeat$` |
| `ContainmentIntrinsics` | `$in$` |
| `TruthinessIntrinsics` | `$truthy$ $not$` |
| `IteratorIntrinsics` | `$iterator$ $next$` |

Planned:
- `$getItem$ $setItem$ $deleteItem$ $slice$`
- the container literal factories
- `$type$ $isinstance$ $len$ $repr$ $str$`, constructors, and string formatting `$format$`
- container method intrinsics

Note that the name for membership is `$in$`, not `$contains$`.

### 6.2 Augmented assignment [PARTIAL]

- Every operator has a dedicated `$iop$` intrinsic. Today they delegate to the binary intrinsic, which is correct because numbers, `str` and `tuple` are immutable.
- The compiler emits load name, evaluate value, `$iop$`, store name for name targets.
- **Subscript targets are not yet generated.** When they are, the container and index are evaluated exactly once, in Python order.
- When `PyList` exists, `$iadd$` and `$imul$` mutate in place and return the same list.
- Operand combinations CPython supports but ula4j does not raise `UnimplementedFeatureError`.

### 6.3 Builtins [PLANNED]

`ModuleContext.defineBuiltin` and the cell fallback (global value, then builtin, then `PyNameError`) exist, but **no builtin is registered**, not even `print`.
§18 phase 1 defines the first set: `print`, `len`, `repr`, `str`, `int`, `float`, `bool`, `type`, `isinstance`, `range`, `abs`, `min`, `max`, `sum`, `hash`.
Each needs a specified signature, argument validation, return type and error behaviour.

### 6.4 Special methods

`__add__` and friends are never dispatched dynamically. Their behavior is implemented directly by intrinsics for the supported types.

## 7. Language subset

### 7.1 Supported syntax

The table separates what the front end accepts from what the back end currently generates.

| Construct | AST layer | Bytecode today |
|---|---|---|
| Expression statement, `pass`, `return`, `break`, `continue` | yes | DONE |
| Name assignment, chained `a = b = v` | yes | DONE |
| Augmented assignment, name target | yes | DONE |
| Augmented assignment, subscript target | yes | PLANNED (rejected at codegen) |
| Subscript assignment, tuple-unpacking assignment, `del a[i]` | yes | PLANNED (rejected at codegen) |
| `if`/`elif`/`else`, `while`/`for` with `else` | yes | DONE (`for` over `str`, `tuple` only) |
| `def` (positional params, one `*rest`), nested defs, closures | yes | DONE |
| Arithmetic, bitwise, comparisons, chains, `and`/`or`/`not`, `in`/`not in`, conditional expression | yes | DONE (`in` only on `str`, `tuple`) |
| Calls of the form `f(args)` | yes | DONE |
| List/tuple/dict displays, subscripts, slices | yes | PLANNED |
| `obj.name(args)` | yes (`Expr.MethodCall`) | PLANNED (rejected at codegen) |
| `%` formatting | yes | PLANNED (`$mod$` on `str` raises `UnimplementedFeatureError`) |

### 7.2 Rejected syntax

Rejection happens in one of two places, and the diagnostic always carries line and column.

**1. `ASTBuilder` throws `UnsupportedSyntaxException` before anything runs.**

- `is`/`is not`
- set displays
- comprehensions and generator expressions
- `lambda`, walrus
- star and `**` unpacking, keyword arguments
- defaults, `**kwargs`, keyword-only parameters, `/`
- annotations and type aliases/parameters
- decorators
- classes, `with`, `try`, `raise`, `assert`
- `import`, `global`, `nonlocal`, `match`
- `async`, `await`, `yield`
- attribute loads and stores other than `obj.name(args)`, and `del name`
- `@`, `...`
- f/t-strings, bytes, imaginary literals
- lone-surrogate escapes and unknown `\N{...}` names
- `[a, b] = x` targets

**2. `BytecodeGenerator` throws `UnsupportedSyntaxException` for constructs that are planned but not generated yet** (the §7.1 PLANNED rows).

Other front-end errors:

- `SyntaxErrorException` covers ANTLR errors, `return` outside a function,
   `break` or `continue` outside a loop, duplicate parameters, bad escapes, and integer literals longer than 4300 digits.
- A `def` resets the loop context.
- A loop's `else` belongs to the *enclosing* context.

### 7.3 Method calls [PLANNED]

`obj.name(args)` will compile to a `callMethod` bootstrap bound to the intrinsics of an explicit method table (§16). Method extraction as a value stays rejected.

### 7.4 Truthiness and boolean operators [DONE]

- `$truthy$` covers bool, int, float (NaN is truthy), str, None, BigInteger, tuple and function.
- `and`/`or` return an operand and short-circuit. `not` returns a `Boolean`.
- In condition position the compiler emits jump code directly (`branch`), with no boxing.
- `Compare` results are primitive `boolean`s boxed only when used as values.

### 7.5 Comparison chains [DONE]

- A single comparison is evaluated inline.
- A chain keeps the current left operand in a temp slot, evaluates each operand once, and short-circuits on the first false comparison.

### 7.6 Strings [PARTIAL]

- Iteration, ordering (by code point, not UTF-16 unit), `+`, `*` and `in` are implemented.
- `len`, indexing and slicing are planned with the builtins and subscripts.
- A lone-surrogate escape in a literal is rejected at parse time (`UnsupportedSyntaxException("lone surrogate escape")`), because a Java `String` cannot represent it.

## 8. Functions, calls, globals, locals, closures

### 8.1 Calling convention [DONE]

```text
static Object <name>(Object c0..cn-1 /*captures*/, PyFunction self, Object a0..am-1 [, PyTuple rest])
```

- `captureNames(def, analysis)` is `freeVariables` minus the function's own name, since a function reaches itself through `self`.
- `rest` is a tuple of the extra positional arguments.
- The total parameter count is limited to 254 (§13).

### 8.2 Functions and call sites [DONE]

**`def`.**
- The compiler emits an `invokedynamic` with the captured values as arguments, linked by `RT.bsmDef` (`DefCallSite`). The AST node is found through the per-class `Dictionary` by index.
- A def with no captures yields a constant `PyFunction`, and the same object every time it executes (see §17, D8).
- A def with captures yields a factory that builds `new PyFunction(code, capturedArgs)`.
- `PyFunction.Code` is the shared per-`def` state: name, parameters, varargs, capture count, context, AST node, and the lazily compiled handles `compiledEntry`, `entry` and `thunkEntry`.

**Calls.**
- Every call is `getGlobal` or a local load, then `invokedynamic bsmCall` with the type `(Object callee, Object args...)Object`.
- There is no specialized "global call" path today (§18, phase 6).
- `FunctionCallSite` is a `MutableCallSite` that builds a bounded guard chain (`MAX_DEPTH = 4`).
  - A callee without captures is guarded by `sameFunction` (identity).
  - A callee with captures is guarded by `sameCode` (same `Code`), so closures from one `def` share a guard, and the captured values come from the callee through `capturedArgs()`.
  - Arity is checked at link time with CPython-style `TypeError` messages ("f() missing 1 required positional argument: 'b'", "takes 1 positional argument but 2 were given").
  - A varargs callee is adapted with `collectArguments` and a `fromArray` packer sized for the site.
  - A non-function callee raises `PyTypeError("'int' object is not callable")`.
  - Beyond four distinct targets, the root site switches to a megamorphic `Object[]` path (`Megamorphic.megamorphic`) that checks arity per call and uses `thunkEntry` per code.
- Arguments are evaluated before the arity check.

### 8.3 Globals [PARTIAL]

- One `ModuleContext` per run owns `GlobalCell`s (a `ConcurrentHashMap`), builtins, and the scope table.
- `getGlobal` and `setGlobal` link to a `ConstantCallSite` bound to the cell's `get`/`set`.
  - A cell read is a volatile read and works for rebinding.
  - It raises `PyNameError` when the name is unbound and not a builtin.
- Only module-level code stores globals (`global` is rejected). A function store to a non-local is an assertion error.
- Planned (§18, phase 6): `SwitchPoint`-based constant binding for global calls, an instability threshold, and invalidation when a module name shadows a builtin.
- A script run in an existing `ModuleContext` (used by the tests) keeps its globals and functions.

### 8.4 Locals and closures [DONE]

`ScopeAnalyzer` runs four passes over a `FunctionScope` tree, all keyed on AST identity.

1. **Pass 1** (collect). Locals, parameters, assignment counts, loop-assigned names, scope tree. Any binding makes a name local to the whole function, so there is no block scoping. Augmented assignment, tuple targets, `for` targets and nested `def` names all count as bindings.
2. **Pass 2** (resolve). A name that is not local is a capture if an enclosing *function* binds it. Module-level names are globals. Captures propagate through intermediate functions.
3. **Pass 3** (effectively final). A captured local may be bound at most once, a captured parameter never, and never inside a loop or a loop target. Violations raise `SemanticErrorException` naming the variable and the capturing function.
4. **Pass 4** (definite assignment).

Mutual recursion between nested functions is rejected because it needs capture-before-assignment.
Self-recursion works through `self`. Mutating an object a captured variable refers to is allowed.

Captured values are copied into the `PyFunction` at definition time and passed as leading arguments to the body, which declares them as locals.

### 8.5 Definite assignment [DONE]

- Flow-sensitive analysis over `if`, `while` and `for`, with `break`, `continue`, `return` and `else`.
- A loop's exit state is the intersection of its `else` result and its `break` states. A `while True` is not treated specially.
- Reading a local that is not assigned on every path is an error. So is a `def` whose captures may be unassigned at the definition point.
- The nested function's body is not analysed at the `def`. It is analysed when it is itself compiled.
- This analysis is also required for bytecode verifiability, because locals are uninitialised JVM slots.

### 8.6 Compilation model [DONE]

- **Classes and loaders.** Every generated class (`gen/Script`, `gen/Fn_<sanitized name>`) gets its own `FunctionClassLoader` carrying the `ModuleContext` and the class's `Dictionary` of AST nodes.
  - Bootstraps recover the context from `lookup.lookupClass().getClassLoader()`.
  - A loader per class lets same-named nested functions coexist.
- **Laziness.** The script class is generated eagerly. Function bodies are compiled on their first call.
  - `getCompiledEntry` uses double-checked locking on `code`.
  - `entry` and `thunkEntry` are idempotent, benign races.
  - `ModuleContext.scopeFor` runs passes 1–3 for module-level functions. It is synchronized, and nested scopes are registered when their enclosing function is generated.
  - Pass 4 and code generation run at first call.
- **Codegen conventions.** `COMPUTE_FRAMES` with a `getCommonSuperClass` that always returns `Object`. Every merged value is used as an `Object`. Locals use unique slots that are never reused. Line numbers come from statements, with the source name `script`.
- **Consequence of laziness.** Today a diagnostic in a never-called function is never reported, and one in a called function appears mid-run. §13.2 fixes this policy.

## 9. Runtime exceptions [PARTIAL]

Implemented (all sealed, so a new type needs a `permits` update):

```
PyBaseException ← PyException ← { PyArithmeticError ← {PyOverflowError, PyZeroDivisionError},
                                  PyLookupError ← {PyIndexError, PyKeyError},
                                  PyAttributeError, PyNameError, PyRuntimeError, PyTypeError, PyValueError }
```

Planned types: `PyStopIteration`, `PyAssertionError`, and others only when a supported construct needs them. `PyRuntimeError` is needed for dict mutation during iteration.

**Other throwables**
- `UnimplementedFeatureError` is not a Python exception. It means valid CPython that ula4j does not implement. Current uses:
  - `'%'` formatting
  - shifts or exponents beyond JVM limits
  - negative float to a non-integer power, which would be complex
  - sequences beyond the JVM array limit
  - type names of unknown classes
- Front-end errors are `SyntaxErrorException`, `UnsupportedSyntaxException` and `SemanticErrorException` (compiler-side: scope rules, limits). All carry line and column.
- A non-canonical value reaching an intrinsic is an internal bug, reported as `AssertionError`. This is the policy; see D1 for the places that still use `PyTypeError`.
- Stack traces stay enabled. `StackOverflowError` and `OutOfMemoryError` are not mapped.
- Without `try`/`except`, Python exceptions propagate out of `Compiler.run`.
- Python-style traceback rendering is planned (§18, phase 5).

## 10. Numeric semantics [DONE except conversions]

Reference: 64-bit CPython. Integers are arbitrary precision.

- **Arithmetic.**
  - `Long` operations use `*Exact` and promote to `BigInteger` on overflow.
  - `/` is correctly rounded for any size (`divideBig`).
  - `//` and `%` use floor semantics with signed-zero rules for floats.
  - `**` follows CPython's `float_pow` special cases, and a negative integer exponent gives a float.
  - Shifts and bitwise operations work on `BigInteger`.
  - `bool & bool` stays `bool`.
- **Comparisons.** Exact int/float comparison never converts the int to `double`. NaN is unordered, and `-0.0 == 0.0`.
- **Hashing.** The 64-bit CPython algorithm (modulus 2^61−1, `inf` → 314159). Documented deviation: `NaN` hashes to a constant `0`, because boxed doubles have no identity we control.
- **Overflow errors.** `int/float` conversion overflow raises `PyOverflowError`.
- **Valid in CPython but unimplemented.** These raise `UnimplementedFeatureError`: shift counts of 2^31 or more, exponents of 2^31 or more, `(-2.0) ** 0.5`, and sequences larger than the JVM array limit. Oversize tuples must use the same error (§17, D2).
- **Not yet available (need builtins).** `int()`, `float()`, `divmod`, `abs`, `round`, and the `int(inf)`/`int(nan)` errors.

## 11. Intrinsics as Java

Intrinsics are `public static` methods annotated `@Intrinsic` (a marker with `CLASS` retention). The name starts and ends with `$`. The Specializer enforces the shape.

Rules:

1. Dispatch uses **type patterns only**: no `when` guards, record patterns or `case null`. Only switch statements are allowed. The shape test (§15.4) is the enforcement.
2. A `default` case is required unless the selector's static type is a sealed type and the cases are exhaustive.
3. The generic intrinsic implements the complete semantics and error behavior. Only canonical values are accepted.
4. Only calls to other `$name$` methods become `invokedynamic`. A direct recursive call is rejected. JDK calls and private helpers stay direct, and helpers are never specialized.
5. Intrinsics return primitives where useful, for example `boolean` for comparisons, `long` for sizes and hashes. The compiler consumes the primitive directly.
6. While a runtime type is unimplemented, the `default` case raises `UnimplementedFeatureError` naming the class. Once all canonical types are handled, it becomes an internal error (`AssertionError`).

## 12. Specialization and JDK coupling [DONE]

### 12.1 Specializer

`Specializer.specialize(lookup, class)` reads the class file with ASM. For every public static `$x$` method it prepares a factory that, on each call, defines a **hidden nestmate class** containing a copy of that method.

Rewrites applied to the copy:
- `typeSwitch` call sites whose bootstrap arguments are all classes become a call to `RT.bsmTypeSwitch` with the descriptor `(Object,int)int`. The restart index is dropped and replaced by `0`.
- `invokestatic` of another `$x$` method becomes an `invokedynamic` linked by `RT.bsmSpecialize` (§12.3).
- A method without the annotation is rejected.

`SpecializationSupport` caches the factory map per intrinsic class in a `ClassValue`. The factory map is immutable, and every link produces a fresh hidden class.

`RT.bsmSpecialize` returns a `ConstantCallSite` over the specialized handle. An unknown name or descriptor raises `BootstrapMethodError`.

### 12.2 Type-switch inline cache

`SpecializationSupport.InliningCache` is a `MutableCallSite`. It guards on the receiver's exact class and returns the index of the first assignable label, or the number of labels when none matches. It chains up to depth 3. Beyond that, the root site is replaced once by the JDK's `SwitchBootstraps.typeSwitch` and never relinks. A `null` selector is an internal bug and throws an NPE.

### 12.3 Nested intrinsics

When specialized code calls another intrinsic, that call is its own `bsmSpecialize` site. Every call site, at every nesting level, therefore owns a distinct hidden class and distinct profile state.

### 12.4 JDK coupling

The exact lowering of pattern switches belongs to `javac`.
The shape test (§15.4) fails loudly if it changes. The Specializer must then be updated *after* reviewing the new bytecode shape.

## 13. Limits and diagnostics

### 13.1 Limits [PARTIAL]

- Total parameters plus captures: at most 254, raising `SemanticErrorException`. A call with more than 253 arguments is rejected.
- Strings above 65535 modified-UTF-8 bytes are split.
- The 64 KB method limit is not yet translated into a diagnostic (ASM's `MethodTooLargeException` escapes). Planned.
- Decimal integer literals above 4300 digits are a `SyntaxErrorException`.

### 13.2 Diagnostic timing

Function bodies are compiled lazily, but *semantic* diagnostics must be reported **before execution of a function starts**.

## 14. Toolchain and build

- **Build.** Maven, `release 27`, ANTLR 4.13.2, ASM 9.11.1, JUnit 5.14.3.
  - The shade plugin names `com.github.forax.ula4j.main.Main` as the entry point, but this class does not exist yet (§18, phase 5).
  - Generated classes use class-file version 25, and the ASM and JDK pairing must be pinned and tested.
- **Parser.** The grammar is ANTLR (Python 3.14.2 grammar by R. Einhorn) with `PythonLexerBase`. `ASTBuilder` is hand-written with one typed method per rule.
  Any unhandled alternative is rejected, so a forgotten rule can never be silently accepted. Resolved: the parser choice is no longer open.
- **Reference interpreter.** CPython **3.14.10** from python-build-standalone, release 20261026, as configured in `pom.xml`.
- **Conformance corpus.** MicroPython `tests/basics` (tag v1.24.1) is cloned by the `update-corpus` profile and copied to `src/test/resources/basics`.
  - The `.golden` files are CPython stdout.
  - The `.py.exp` files are MicroPython's expectations and are informational only.
  - Today the download, extraction and golden-generation steps are partly commented out and hardcoded to macOS aarch64. This is a gap (§15.7).

## 15. Verification

### 15.1 Differential testing [PLANNED]

- Run corpus and generated programs against the pinned CPython through `ProcessBuilder`.
- Download a specific build, verify its SHA-256, and never use a developer's system Python.
- Committed goldens form a fast hermetic CI layer.
- Compare stdout, the exception type and message of an uncaught failure, mutation and aliasing, and iteration order where Python specifies it.
- Skip a corpus program only when ula4j rejects it with `UnsupportedSyntaxException` or `UnimplementedFeatureError`. Any other mismatch fails.
- Never assert unspecified behaviour (string and tuple hashes, identity of immutables).

### 15.2 Semantic test groups

As before, plus the groups that already exist (§15.7). Still required:
container semantics, dict order and mutation, string code-point operations, call-argument evaluation order,
subscript-assignment ordering, and a rejection test for every unsupported construct.

### 15.3 Generic versus specialized execution

Property tests run each intrinsic through the generic and specialized paths over boundary values (`Long`/`BigInteger` limits, `Boolean`, NaN, ±0.0, ∞).
They check equal results and equal exception types and messages, guard-failure fallback, polymorphic fallback, per-site independence, and independent nested sites.

### 15.4 Bytecode and build tests

`IntrinsicShapeTest` checks the `typeSwitch` descriptor and class labels for every intrinsic class.

### 15.5 Benchmarks [PLANNED]

A JMH baseline of plain `invokedynamic` to generic intrinsics must exist before any performance claim.

### 15.6 Acceptance criteria

- Every supported construct has a CPython-checked test.
- Every rejected construct has a diagnostic test.
- Every intrinsic has generic semantic tests.
- Every specialized intrinsic has an equivalence test.
- No optimization changes accepted semantics.

### 15.7 Current inventory and gaps

**Existing**
- `ASTBuilderTest` (grammar-to-AST, positions, every rejection)
- `ScopeAnalyzerTest` (passes 1–4)
- `BytecodeGeneratorTest` (end-to-end semantics, with hand-written expectations)
- `FunctionSupportTest` (def, mono/poly/megamorphic calls, rest, captures, rebinding, errors, recovery, concurrency)
- `RTTest`
- `SpecializerTest` with `SpecializerFixtures`
- `IntrinsicShapeTest`
- Arithmetic, Comparison, Equality, Hashing and Tuple intrinsic tests
- `PyTupleTest`, `PyNoneTest`

**Gaps**
1. `ConformanceTestIT` is not wired to failsafe (and surefire does not exclude it). With no `print` builtin, essentially all corpus programs would fail with `PyNameError`.
2. No SHA-256 verification, no cross-platform archive selection, no `ProcessBuilder` harness, and no committed generator invocation.
3. `IntrinsicShapeTest` omits `TruthinessIntrinsics`, `ContainmentIntrinsics` and `IteratorIntrinsics`. Its `$concat$`/`$repeat$` asserts are commented out as FIXMEs (they have no switch, so the assertion should be dropped, not fixed).
4. There is no generic-versus-specialized equivalence test for arithmetic, comparison, truthiness, containment or iteration.
5. No JMH.
6. Expected values in `BytecodeGeneratorTest` and `FunctionSupportTest` are handwritten, not CPython-checked.
7. `PyTupleTest` and `TupleIntrinsicsTest` use record `equals`. This is acceptable in tests but must not leak into runtime code.
8. No test for the 64 KB limit or the eager-diagnostic policy (§13.2).

## 16. Open decisions

Resolved since rev. 1: the exact CPython patch release and the parser technology (§14).

1. **Builtin set**: names, signatures, argument validation, behavior (candidates in §18, phase 1).
2. **Container method table**: which list, tuple, dict and str methods exist first.
3. **Dict methods** beyond subscript, `in` and iteration. `items`, `keys` and `values` stay excluded.
4. **`print` keywords**: `sep=`/`end=` conflict with the "no keyword arguments" rule. Options: a special case in the compiler or a tiny keyword-capable builtin ABI. `%` formatting is in scope and `str.format` is not.
5. **Function method surface**: none, unless explicitly decided.
6. **`def` identity**: see D8.
7. **Uncaught-exception output and exit code** of the command-line launcher.
8. **`MemoryError`**: oversize allocations stay `UnimplementedFeatureError` (D2) or are mapped later.

## 17. Known deviations to fix

| ID | Deviation | Resolution |
|---|---|---|
| D1 | Equality is implemented twice: `ComparisonIntrinsics.$eq$`/`$ne$` is used by the compiler, `EqualityIntrinsics.$eq$` by `in` and tuples. They differ in default handling, and neither handles `PyFunction`. Equality and hashing default to `PyTypeError("not a canonical runtime value")`. | **Decision**: `EqualityIntrinsics` is the single owner of `$eq$` and `$ne$`, and the compiler targets it. Remove them from `ComparisonIntrinsics`, whose tuple ordering then uses `sameOrEqual`. Add function identity equality and hashing (`System.identityHashCode`). Replace the `PyTypeError` defaults with the §11 rule 6 policy, and update the `nonCanonicalLeftOperandIsAnError` test. |
| D2 | `ArithmeticIntrinsics` has private `concatTuples`/`repeatTuple`/`itemsOf`/`tupleOf`, duplicating `TupleIntrinsics.$concat$`/`$repeat$`. Oversize results are `UnimplementedFeatureError` in one and `OutOfMemoryError` in the other. | **Decision**: arithmetic converts the operand (the `BigInteger` count raises `OverflowError`) and calls the tuple intrinsics. Delete the duplicates. Oversize results raise `UnimplementedFeatureError` everywhere, and `TupleIntrinsicsTest.repetitionTooLargeForAJavaArray` is updated. |
| D3 | Intrinsic rules in rev. 1 forbade switch expressions and required `default`. | Rules relaxed in §11. |
| D4 | Semantic and unsupported-construct diagnostics are lazy (§8.6). | §13.2 eager validation phase. |
| D5 | Plan said lone-surrogate escapes are a runtime error. | Reality adopted: parse-time rejection (§7.6). |
| D6 | No interop normalization. | §5.3, roadmap phase 5. |
| D7 | `ModuleContext` Javadoc mentions a `ModuleClassLoader` that does not exist. | Fix the Javadoc. The design is a loader per class (§8.6). |
| D8 | A `def` without captures returns one shared `PyFunction`. CPython creates a new function per execution. | Accepted for now, and the test is commented "may change". **Must be fixed before function `==`/hash or `id` are observable.** The fix is to allocate a `PyFunction` per execution, and to guard non-capturing sites by `sameCode` as well. |
| D9 | `%` formatting listed as supported. | Moved to PLANNED (§7.1). |
| D10 | `NaN` hash is `0` (CPython hashes by identity). | Documented accepted deviation (§10). |
| D11 | Stale comments: `BytecodeGenerator` says `Script_<n>`/`Fn_<name>_<id>`, but the names are `Script` and `Fn_<name>`. `TupleIntrinsics` claims selectors are cast to `Object`, but they are not. `PyFunction` documents an `entry` field that now lives in `Code`. | Fix the comments. |
| D12 | `@PyIntrinsic` (rev. 1 §6.1) versus `@Intrinsic`. | `@Intrinsic` everywhere. |
| D13 | `FunctionCallSite` has no global-call fast path, `SwitchPoint` invalidation or instability threshold. | Roadmap phase 6. |
