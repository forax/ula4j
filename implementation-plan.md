# ula4j: Implementation Plan (rev. 3)

## 0. How to read this document

Every feature carries a status tag:

- **[DONE]**: implemented and covered by tests.
- **[PARTIAL]**: implemented in part. The text says what is missing.
- **[PLANNED]**: not implemented. The design is normative for when it is.

Normative rules say *must*. "Current behavior" describes the code.
§17 lists the places where the code still violates a rule.

## 1. Goals and principles

### 1.1 Goals

- Compile a deliberately limited Python subset to JVM bytecode and run it on the JVM.
- Preserve the semantics of a pinned 64-bit CPython 3.14 for every construct that is accepted.
- The implementation can deviate from Python semantics only in terms of object identity of immutable values.
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
2. **Unsupported means rejected** with a located diagnostic (§13.2), never silently approximated. A *runtime* gap raises `UnimplementedFeatureError`, never a Python exception that CPython would not raise (see D16).
3. **Generic semantics are authoritative.** Specialized code is derived from the generic intrinsic bytecode and falls back to it.
4. **One implementation per semantic.** No operation may be implemented twice, whether as a fast path or as a helper in another class (see §17, D2).
5. **Per-call-site specialization state.** Each specialized call site owns its hidden class and its profile. The only shared cache is the per-intrinsic-class table of rewritten bytecode (§12.1), which is immutable and holds no specialized implementation.
6. **Mutable containers stay mutable Python objects.**
7. **Python equality, hashing and ordering are explicit.** Never rely on Java `equals`/`hashCode` of runtime values. `PyList` and `PyDict` enforce this by throwing `PyTypeError("unhashable type: ...")` from `equals`/`hashCode`. The record `equals` of tuples is for tests only.

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
| `rt` | `RT` (bootstraps), `ModuleContext`, `Dictionary`, `FunctionSupport`, `PyFunction`, `Builtins`, `PyNone`, `PyTuple`, `PyList`, `PyDict`, `PyIterator` |
| `rt.intrinsic` | `@Intrinsic` classes (§6.1) and `IntrinsicSupport` |
| `rt.specializer` | `Specializer` (ASM), plus `SpecializationSupport` in `rt` |
| `rt.error` | Python exception hierarchy and `UnimplementedFeatureError` |

The generated code calls runtime classes directly (plain `invokestatic`/`invokevirtual`) only for container construction (`ListIntrinsics.newList`, `PyList.append`, `DictIntrinsics.newDict`, `PyDict.put`). Everything else dynamic goes through `invokedynamic`.

## 2. Value representation

| Python | JVM | Status |
|---|---|---|
| `None` | `PyNone.INSTANCE` (never Java `null`) | DONE |
| `bool` | `Boolean` | DONE |
| `int` | `Long` if it fits, else `BigInteger` | DONE |
| `float` | `Double` | DONE |
| `str` | `String`, code-point semantics | PARTIAL (iteration, ordering, `+`, `*`, `in`, `str`/`repr` |
| `tuple` | sealed `PyTuple`: `PyTuple1..4`, `PyTupleN` | DONE|
| `list` | `PyList<E>` (final) | DONE (no slices, no methods) |
| `dict` | `PyDict<K,V>` (final) | DONE (no methods) |
| Python function | `PyFunction` (final class); builtins are `PyFunction`s with no AST node | DONE |
| `range`, `slice` | `PyRange`, `PySlice` | PLANNED |
| type object | `PyType` | PLANNED (§3) |
| for-loop iterators | `PyIterator.{TupleIterator, StringIterator, ListIterator, DictIterator}` | DONE, internal only |

### 2.1 Representation rules

- **Canonical integers.** A `BigInteger` never holds a value that fits in a `long`. `IntrinsicSupport.normalize` enforces this and `Expr.Literal` validates it.
- **Canonical tuples.** `TupleIntrinsics.fromArray` is the only factory (plus `fromArrayRange` for `*rest`). Arities 1–4 use fixed records, and 0 and 5+ use `PyTupleN`.
  - The empty tuple is a shared `PyTupleN`.
  - Every tuple intrinsic must work on every shape, including non-canonical ones such as a 2-element `PyTupleN`.
- **No nulls inside values.** Tuples, lists, dicts, globals and builtins reject Java `null`. `PyDict.get` returns Java `null` to mean "absent", which is safe because no stored value can be `null`.
- **Internal protocol.** Java `null` returned by `$next$` means "iterator exhausted". It never escapes into Python values.
- **Thread safety.** `PyList` and `PyDict` are not thread-safe; synchronization is the caller's responsibility.

### 2.2 Identity and equality

- `is` and `is not` are rejected, so no general identity operator exists.
- `==`, `!=`, ordering and hashing are intrinsics. Numeric equality and hashing unify `bool`, `int` and `float`.
- Functions compare and hash by identity.
- Container comparison uses CPython's identity shortcut on *elements only* (`ComparisonIntrinsics$sameOrEq$`). Top-level `nan == nan` is `False`.
- Tests must not depend on the identity of immutable values.

## 3. `PyType` and builtin type objects [PLANNED]

- `type(x)` is an intrinsic that pattern-matches on the JVM representation.
- Canonical type objects are callable and dispatch to the constructor intrinsic.
- `isinstance` checks the JVM class set of the type. The `int` type covers `Long`, `BigInteger` and `Boolean`.
- Builtin functions have the type `builtin_function_or_method`.
- Until `PyType` exists, `IntrinsicSupport.typeName` is the placeholder for `type(x).__name__`. It handles every canonical type and returns `java_type(<class>)` for anything else (it no longer throws). It reports `function` for builtins too, which is wrong (D19).

## 4. Collections [PARTIAL]

Storage is done. Python-level methods and slices are not.

**`PyList`** [DONE]
- Array-backed, grows by 1.5×, capped at `Integer.MAX_VALUE - 8` (`UnimplementedFeatureError` beyond).
- Index-based iteration (`ListIterator` reads the size on every `next`), so mutation during iteration behaves as in CPython.
- Python semantics available: `len`, `l[i]` (negative indexes, `IndexError`), `l[i] = v`, `del l[i]`, `+`, `*`, `+=` (in place, self-extension safe, accepts any supported iterable), `*=` (in place), `==`, ordering, `in`, truthiness, `str`/`repr` (with `[...]` for cycles). Lists are unhashable.
- Always a new list for `+` and `*`, including `l * 1`.
- Planned: slices and the method table (`append`, `pop`, `insert`, ...). `PyList.append/insert/remove` are Java storage operations only.

**`PyDict`** [DONE]
- Insertion-ordered: parallel `keys/values/hashes` arrays in insertion order, and an open-addressing `int[]` index (CPython-style perturbation probing, tombstones, load factor 2/3, entry compaction when sparse).
- Replacing a value keeps the original key object and its position. Deleting and reinserting appends.
- `keysVersion` changes only when the key set changes. `DictIterator` raises `PyRuntimeError("dictionary changed size during iteration")` or `("dictionary keys changed during iteration")` and stays failed.
- Equality is order-independent, uses the identity shortcut on values, and is implemented in `DictIntrinsics.equals`.
- Python semantics available: `len`, `d[k]` (`KeyError` with `repr(key)`), `d[k] = v`, `del d[k]`, `k in d`, `==`, truthiness, `str`/`repr` (`{...}` for cycles), iteration over keys. Dicts are unhashable, and an unhashable key raises `TypeError`.
- Planned: `get`, `pop`, `setdefault`, `update`, `clear`, `copy` and the others as decided in §16. `items`/`keys`/`values` stay excluded.

**Both**
- Python-facing behavior is reached only through intrinsics (`$getItem$`, `$setItem$`, `$deleteItem$`, `$in$`, `$iterator$`, ...).
- `AbstractList`/`AbstractMap` inheritance is a Java convenience only. Java `toString` is not Python `repr`.

**Tuples**
- Runtime is done: `$size$`, `$get$`, `$concat$`, `$repeat$`, equality, hashing, ordering, `in`, iteration, `t[i]` through `$getItem$`.
- Tuple unpacking, slicing and the method table are planned.

**Hashing**
- Lists and dicts raise `PyTypeError: unhashable type: 'list'` / `'dict'`.
- String, tuple and `None` hash values are unspecified and must never be asserted in tests.

## 5. `None`, constants, interop

### 5.1 Missing states [DONE]

- `None` is `PyNone.INSTANCE`.
- Java `null` is used only as an internal state: unbound global cell, iterator exhaustion, absent dict key.
- There is no unbound-local sentinel. Definite assignment (§8.5) guarantees it, and the JVM verifier also requires it (§8.6).

### 5.2 Constants [DONE]

- `Long` and `Double` literals use `ConstantDynamic` through `RT.bsmConst`. Big integers use `RT.bsmBigInt`, which parses a decimal string.
- `Boolean` uses `Boolean.TRUE`/`FALSE`, and `None` uses `PyNone.INSTANCE`.
- Strings use `ldc`, split into chunks (`concat`) when the modified-UTF-8 length exceeds 65535 bytes.
- List and dict displays are never shared constants: every evaluation builds a new object, elements left to right, dict key before value.
- Tuple displays: a display whose elements are all immutable constants may be a shared constant; any other tuple display is built at run time.

### 5.3 Java interop boundary [PLANNED]

The intended boundary is: `null` becomes `PyNone`, `Integer`/`Short`/`Byte` become `Long`, `Float` becomes `Double`, `Character` becomes a one-character `String`.

Current behavior: no normalization. `ModuleContext.setGlobal`, `PyList` and `PyDict` only do `requireNonNull`. An `Interop.toPython` normalizer applied at `ModuleContext` entry points is on the roadmap (§18, phase 5).

Intrinsics accept only canonical values.

## 6. Intrinsics and builtins

### 6.1 Intrinsic inventory [PARTIAL]

All are `@Intrinsic public static` methods named `$name$` (§11).

| Class | Intrinsics |
|---|---|
| `ArithmeticIntrinsics` | `$add$ $sub$ $mul$ $div$ $floorDiv$ $mod$ $pow$ $lshift$ $rshift$ $bitAnd$ $bitOr$ $bitXor$`, `$neg$ $pos$ $invert$`, `$iadd$ ... $ixor$` |
| `ComparisonIntrinsics` | `$eq$ $ne$ $lt$ $le$ $gt$ $ge$` (primitive `boolean`). **Sole owner of equality.** |
| `HashingIntrinsics` | `$hash$` (primitive `long`) |
| `TupleIntrinsics` | `$size$ $get$ $concat$ $repeat$` (+ factories `fromArray`, `fromArrayRange`) |
| `ListIntrinsics` | `$size$ $get$ $set$ $delete$ $concat$ $repeat$ $iadd$ $imul$` (+ `newList`) |
| `DictIntrinsics` | `$size$ $get$ $set$ $delete$` (+ `newDict`, `containsKey`, `equals`) |
| `ItemIntrinsics` | `$getItem$ $setItem$ $deleteItem$ $toIndex$` (container dispatch and index conversion) |
| `ContainmentIntrinsics` | `$in$` (`str`, `tuple`, `list`, `dict`) |
| `TruthinessIntrinsics` | `$truthy$ $not$` |
| `IteratorIntrinsics` | `$iterator$ $next$` (`tuple`, `str`, `list`, `dict`) |
| `StringIntrinsics` | `$str$ $repr$` |

Support code that is not specialized: `IntrinsicSupport` (`typeName`, number conversions, error factories).

Planned:
- `$slice$` and slice variants of the item intrinsics
- tuple display factories, unpacking helpers
- `$type$ $isinstance$ $len$`, constructors, `$format$`
- container method intrinsics

Notes:
- The name for membership is `$in$`, not `$contains$`.
- `$toIndex$` takes a type-name argument for its message (see D16).

### 6.2 Augmented assignment [DONE]

- Every operator has a dedicated `$iop$` intrinsic. Most delegate to the binary intrinsic, which is correct for immutable values.
- `$iadd$` and `$imul$` dispatch on `PyList` and mutate it in place, returning the same list, so aliases see the change.
- Name targets: load, evaluate value, `$iop$`, store.
- Subscript targets: container and index are evaluated once, in that order, into temps; then `$getItem$`, the value, `$iop$`, `$setItem$`.
- Operand combinations CPython supports but ula4j does not raise `UnimplementedFeatureError`.

### 6.3 Builtins [PARTIAL]

- `Builtins.BUILTINS` is a static immutable map `name → PyFunction`, shared by all `ModuleContext`s. A `PyFunction` whose `Code` has no AST node is a builtin
- Lookup order is global cell value, then builtin, then `PyNameError`. A module-level assignment shadows a builtin. There is no `ModuleContext.defineBuiltin`.
- Builtin calling convention: `static Object name(PyFunction self, Object... args)`; extra arguments arrive as an `Object[]`, **not** a `PyTuple` (user functions receive a `PyTuple` for `*rest`).
- Implemented: `print(*objects)`. It joins `$str$` of each argument with `" "`, writes one line to `System.out`, returns `None`. No `sep`/`end`/`file` (§16.4).
- **Policy (proposed, §16.2):** a name that exists in CPython's `builtins` but not in ula4j raises `UnimplementedFeatureError("builtin 'range'")` instead of `PyNameError`, so the conformance runner can skip such programs.

### 6.4 Special methods

`__add__` and friends are never dispatched dynamically. Their behavior is implemented directly by intrinsics for the supported types.

## 7. Language subset

### 7.1 Supported syntax

The table separates what the front end accepts from what the back end currently generates.

| Construct | AST layer | Bytecode today |
|---|---|---|
| Expression statement, `pass`, `return`, `break`, `continue` | yes | DONE |
| Name assignment, chained `a = b = v` | yes | DONE |
| Subscript assignment `a[i] = v`, chained with names | yes | DONE (RHS first, then container, then index) |
| Augmented assignment, name and subscript targets | yes | DONE |
| `del a[i]` | yes | DONE |
| Tuple-unpacking assignment and `for` target | yes | PLANNED (rejected at codegen: "tuple unpacking") |
| `if`/`elif`/`else`, `while`/`for` with `else` | yes | DONE (`for` over `str`, `tuple`, `list`, `dict`) |
| `def` (positional params, one `*rest`), nested defs, closures | yes | DONE |
| Arithmetic, bitwise, comparisons, chains, `and`/`or`/`not`, `in`/`not in`, conditional expression | yes | DONE (`in` on `str`, `tuple`, `list`, `dict`) |
| Calls of the form `f(args)` | yes | DONE |
| List and dict displays | yes | DONE |
| Subscript load `a[i]` (list, dict, tuple) | yes | DONE |
| Tuple displays | yes | PLANNED (rejected at codegen) |
| Slices, `a[i:j]` | yes | PLANNED (rejected at codegen) |
| `str` indexing | yes | PLANNED (currently a wrong error, D16) |
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
- defaults, `**kwargs`, `/`
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

**2. `BytecodeGenerator` throws `UnsupportedSyntaxException` for constructs that are planned but not generated yet** (the §7.1 PLANNED rows), and for calls with more than 253 arguments.

Other front-end errors:

- `SyntaxErrorException` covers ANTLR errors, `return` outside a function,
  `break` or `continue` outside a loop, duplicate parameters, bad escapes, and integer literals longer than 4300 digits.
- A `def` resets the loop context.
- A loop's `else` belongs to the *enclosing* context.
- Duplicate function parameter names, duplicate argument keywords

### 7.3 Method calls [PLANNED]

`obj.name(args)` will compile to a `callMethod` bootstrap bound to the intrinsics of an explicit method table (§16). Method extraction as a value stays rejected.

### 7.4 Truthiness and boolean operators [DONE]

- `$truthy$` covers bool, int, float (NaN is truthy), str, None, BigInteger, tuple, list, dict and function.
- `and`/`or` return an operand and short-circuit. `not` returns a `Boolean`.
- In condition position the compiler emits jump code directly (`branch`), with no boxing.
- `Compare` results are primitive `boolean`s boxed only when used as values.

### 7.5 Comparison chains [DONE]

- A single comparison is evaluated inline.
- A chain keeps the current left operand in a temp slot, evaluates each operand once, and short-circuits on the first false comparison.

### 7.6 Strings [PARTIAL]

- Implemented: iteration, ordering (by code point, not UTF-16 unit), `+`, `*`, `in`, equality, hashing, `str()`/`repr()` conversion (used by `print` and container `repr`).
- `repr` follows CPython: quote selection, escapes `\\ \t \n \r`, `\xNN`, `\uNNNN`, `\UNNNNNNNN` for non-printable characters. Printability uses the JDK's Unicode tables (D19).
- Float `repr`: shortest round-trip digits, scientific notation for exponents below -4 or at least 16, `inf`, `-inf`, `nan`, `-0.0`.
- Int `str` is limited to 4300 digits (`PyValueError` with CPython's message), like the literal limit.
- indexing and slicing are planned with the builtins and subscripts.
- A lone-surrogate escape in a literal is rejected at parse time (`UnsupportedSyntaxException("lone surrogate escape")`), because a Java `String` cannot represent it.

## 8. Functions, calls, globals, locals, closures

### 8.1 Calling convention [DONE]

```text
static Object <name>(Object c0..cn-1 /*captures*/, PyFunction self, Object a0..am-1 [, PyTuple rest])
```

- `captureNames(def, analysis)` is `freeVariables` minus the function's own name, since a function reaches itself through `self`.
- `rest` is a tuple of the extra positional arguments.
- The total parameter count is limited to 254 (§13).
- Builtins use `(PyFunction self, Object[] args)` instead (§6.3).

### 8.2 Functions and call sites [DONE]

**`def`.**
- The compiler emits an `invokedynamic` with the captured values as arguments, linked by `RT.bsmDef` (`DefCallSite`). The AST node is found through the per-class `Dictionary` by index.
- A def with no captures yields a constant `PyFunction`, and the same object every time it executes (see §17, D8; this is now observable and must be fixed).
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
- A site that raised (wrong arity, non-callable, exception in the body) stays usable.

**Embedding API [DONE].**
- `PyFunction.call(Object...)` calls through the megamorphic path.
- `PyFunction.createDirectMethodHandle(MethodType)` validates the arity eagerly (`PyTypeError`), binds captures and `self`, packs `*rest`, and adapts with `asType`.

### 8.3 Globals [PARTIAL]

- One `ModuleContext` per run owns `GlobalCell`s (a `ConcurrentHashMap`) and the scope table.
- `getGlobal` use a switchpoint, `setGlobal` do the invalidation.
  - If not optimized, a cell read is a volatile read.
  - It falls back to `Builtins.BUILTINS`, then raises `PyNameError`.
- Only module-level code stores globals (`global` is rejected). A function store to a non-local is an assertion error.
- A script run in an existing `ModuleContext` (used by the tests) keeps its globals and functions.

### 8.4 Locals and closures [DONE]

`ScopeAnalyzer` runs four passes over a `FunctionScope` tree, all keyed on AST identity.

1. **Pass 1** (collect). Locals, parameters, assignment counts, loop-assigned names, scope tree. Any binding makes a name local to the whole function, so there is no block scoping. Augmented assignment, tuple targets, `for` targets and nested `def` names all count as bindings; subscript targets do not.
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
  - `getCompiledEntry` uses double-checked locking on `code`. Builtins resolve without the lock (idempotent).
  - `entry` and `thunkEntry` are idempotent, benign races.
  - `ModuleContext.scopeFor` runs passes 1–3 for module-level functions. It is synchronized, and nested scopes are registered when their enclosing function is generated.
  - Pass 4 and code generation run at first call.
- **Codegen conventions.** `COMPUTE_FRAMES` with a `getCommonSuperClass` that always returns `Object`. Every merged value is used as an `Object`. Locals use unique slots that are never reused (so a very long method can run out of slots, §13.1). Line numbers come from statements, with the source name `script`.
- **Consequence of laziness.** Today a diagnostic in a never-called function is never reported, and one in a called function appears mid-run (after earlier output). §13.2 fixes this policy.

## 9. Runtime exceptions [PARTIAL]

Implemented (all sealed, so a new type needs a `permits` update):

```
PyBaseException ← PyException ← { PyArithmeticError ← {PyOverflowError, PyZeroDivisionError},
                                  PyLookupError ← {PyIndexError, PyKeyError},
                                  PyAttributeError, PyNameError, PyRuntimeError, PyTypeError, PyValueError }
```

In use: `PyRuntimeError` (dict mutation during iteration), `PyKeyError`, `PyIndexError`, `PyValueError` (negative shift, int digit limit). `PyAttributeError` is not raised anywhere yet.

Planned types: `PyStopIteration`, `PyAssertionError`, and others only when a supported construct needs them.

**Other throwables**
- `UnimplementedFeatureError` is not a Python exception. It means valid CPython that ula4j does not implement. Current uses:
  - `'%'` formatting
  - shifts or exponents beyond JVM limits
  - negative float to a non-integer power, which would be complex
  - sequences (str, list, dict) beyond the JVM array limit
  - `str`/`repr`/truthiness/equality of unknown Java classes
- Front-end errors are `SyntaxErrorException`, `UnsupportedSyntaxException` and `SemanticErrorException` (compiler-side: scope rules, limits). All carry line and column.
- A non-canonical value reaching an intrinsic is an internal bug. The policy is §11 rule 6; see D1 for the places that still use `PyTypeError`.
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

## 11. Intrinsics as Java

Intrinsics are `public static` methods annotated `@Intrinsic` (a marker with `CLASS` retention). The name starts and ends with `$`. The Specializer enforces the shape.

Rules:

1. Dispatch uses **type patterns only**: no `when` guards, record patterns or `case null`. Both switch statements and switch expressions are accepted. The shape test (§15.4) is the enforcement.
2. A `default` case is required unless the selector's static type is a sealed type and the cases are exhaustive.
3. The generic intrinsic implements the complete semantics and error behavior. Only canonical values are accepted.
4. Only calls to other `$name$` methods become `invokedynamic`. A direct recursive call is rejected. JDK calls, private helpers and public non-`$` helpers (for example `DictIntrinsics.equals`) stay direct and are never specialized.
5. Intrinsics return primitives where useful, for example `boolean` for comparisons, `long` for sizes and hashes. The compiler consumes the primitive directly.
6. While a runtime type is unimplemented, the `default` case raises `UnimplementedFeatureError` naming the class. Once all canonical types are handled, it becomes an internal error (`AssertionError`).
7. Intrinsics may use Java generics. The specialization key is the name plus the *erased* descriptor. Overloads are keyed independently.

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

`SpecializationSupport.InliningCache` is a `MutableCallSite`. It guards on the receiver's exact class and returns the index of the first assignable label, or the number of labels when none matches. It chains up to three guards. Beyond that, the root site is replaced once by the JDK's `SwitchBootstraps.typeSwitch` and never relinks. A `null` selector is an internal bug and throws an NPE.

### 12.3 Nested intrinsics

When specialized code calls another intrinsic, that call is its own `bsmSpecialize` site. Every call site, at every nesting level, therefore owns a distinct hidden class and distinct profile state.

### 12.4 JDK coupling

The exact lowering of pattern switches belongs to `javac`.
The shape test (§15.4) fails loudly if it changes. The Specializer must then be updated *after* reviewing the new bytecode shape. ASM must also be able to read class files compiled with the project's `release` (currently 27); generated classes are version 25.

## 13. Limits and diagnostics

### 13.1 Limits [PARTIAL]

- Total parameters plus captures: at most 254, raising `SemanticErrorException`. A call with more than 253 arguments is rejected at codegen.
- Strings above 65535 modified-UTF-8 bytes are split.
- The 64 KB method limit is not yet translated into a diagnostic (ASM's `MethodTooLargeException` escapes). Planned.
- Temporary slots are never reused, so a method with a huge number of subscript assignments, comparison chains or loops can exceed the JVM's 65535 locals. Same planned diagnostic.
- Decimal integer literals above 4300 digits are a `SyntaxErrorException`.

### 13.2 Diagnostic timing [PLANNED]

**Policy.** Every diagnostic that depends only on the source text (syntax errors, unsupported constructs, semantic errors such as capture rules, definite assignment, parameter limits) must be reported **before the script starts executing**, so that a rejected program produces no output.

Syntax and `ASTBuilder` rejections are eager. Scope analysis, definite assignment and codegen rejections (tuple displays, slices, method calls, parameter limits) happen when the function is first called, possibly after output.


## 14. Toolchain and build

- **Build.** Maven, `release 27`, ANTLR 4.13.2, ASM 9.11.1, JUnit 5.14.3.
  - The shade plugin names `com.github.forax.ula4j.main.Main` as the entry point, but this class does not exist yet (§18, phase 5).
  - Generated classes use class-file version 25, and the ASM and JDK pairing must be pinned and tested.
- **Parser.** The grammar is ANTLR (Python 3.14.2 grammar by R. Einhorn) with `PythonLexerBase`. `ASTBuilder` is hand-written with one typed method per rule.
  Any unhandled alternative is rejected, so a forgotten rule can never be silently accepted.
- **Reference interpreter.** CPython **3.14.10** from python-build-standalone, release 20261026, as configured in `pom.xml`.
- **Conformance corpus.** MicroPython `tests/basics` (tag v1.24.1) is cloned by the `update-corpus` profile and copied to `src/test/resources/basics`.
  - The `.golden` files are CPython stdout. Some contain only `SKIP` (the test was skipped by the generating interpreter).
  - The `.py.exp` files are MicroPython's expectations and are informational only.
  - Today the extraction and golden-generation steps are commented out and the archive is hardcoded to macOS aarch64. This is a gap (§15.7).

## 15. Verification

### 15.1 Differential testing [PLANNED]

- Run corpus and generated programs against the pinned CPython through `ProcessBuilder`.
- Download a specific build, verify its SHA-256, and never use a developer's system Python.
- Committed goldens form a fast hermetic CI layer.
- Compare stdout, the exception type and message of an uncaught failure, mutation and aliasing, and iteration order where Python specifies it.
- Skip a corpus program only when ula4j rejects it with `UnsupportedSyntaxException` or `UnimplementedFeatureError` (this includes a missing builtin, §6.3). Any other mismatch fails.
- Never assert unspecified behaviour (string and tuple hashes, identity of immutables).

### 15.2 Semantic test groups

As before, plus the groups that already exist (§15.7). Still required:
dict methods, string code-point operations, slice semantics, call-argument evaluation order for method calls, a rejection test for every unsupported construct, and CPython-checked expectations for the container tests.

### 15.3 Generic versus specialized execution

Property tests run each intrinsic through the generic and specialized paths over boundary values (`Long`/`BigInteger` limits, `Boolean`, NaN, ±0.0, ∞).
They check equal results and equal exception types and messages, guard-failure fallback, polymorphic fallback, per-site independence, and independent nested sites.

### 15.4 Bytecode and build tests

`IntrinsicShapeTest` checks the `typeSwitch` descriptor and class labels for every intrinsic class that contains a pattern switch.

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
- `BytecodeGeneratorTest` (end-to-end semantics, including list and dict literals, indexing, assignment, deletion, containers, with hand-written expectations)
- `FunctionSupportTest` (def, mono/poly/megamorphic calls, rest, captures, rebinding, errors, recovery, concurrency)
- `PyFunctionTest` (`call`, `createDirectMethodHandle`)
- `BuiltinsTest` (`print` with 1, 2 and 6 arguments)
- `RTTest`, `SpecializerTest` with `SpecializerFixtures`, `IntrinsicShapeTest`
- Arithmetic, Comparison (including equality), Hashing, String and Tuple intrinsic tests
- `PyTupleTest`, `PyNoneTest`
- `ConformanceTestIT`

**Gaps**
1. `ConformanceTestIT` is not wired to failsafe (and surefire does not exclude it). With only `print` registered, programs that use any other builtin fail with `PyNameError` instead of being skipped, and the runner skips only `UnsupportedSyntaxException` (not `UnimplementedFeatureError`).
2. No SHA-256 verification, no cross-platform archive selection, no `ProcessBuilder` harness, and no committed generator invocation (the `pom.xml` steps are commented out).
3. `IntrinsicShapeTest` has no case for `ItemIntrinsics`. Its `$concat$`/`$repeat$` asserts are commented out as FIXMEs (they have no switch, so the assertion should be dropped, not fixed).
4. No generic-versus-specialized equivalence test for arithmetic, comparison ordering, truthiness, containment, iteration, string or item intrinsics.
5. No JMH.
6. Expected values in `BytecodeGeneratorTest`, `FunctionSupportTest` and `BuiltinsTest` are handwritten, not CPython-checked. `ArithmeticIntrinsicsTest.augmentedImmutableTypes` contains a tautological assertion around `$imul$("ab", -1L)` that should be `assertEquals("", ...)`.
7. `PyTupleTest` and `TupleIntrinsicsTest` use record `equals`. This is acceptable in tests but must not leak into runtime code.
8. No test for the 64 KB / slot limits or the eager-diagnostic policy (§13.2).
9. No dedicated tests for `PyList`, `PyDict`, `PyIterator`, `ListIntrinsics`, `DictIntrinsics`, `ItemIntrinsics`, `ContainmentIntrinsics`, `IteratorIntrinsics`, `TruthinessIntrinsics`. They are reached only through `BytecodeGeneratorTest` and `ComparisonIntrinsicsTest`. List arithmetic (`+`, `*`, `+=`, `*=`) and dict/list `repr` with cycles are untested.
10. No test of `print()` with no argument, of builtins through the megamorphic site, or of builtins through `PyFunction.call`/`createDirectMethodHandle` (D14).

## 16. Open decisions

Resolved since rev. 2: equality owner (`ComparisonIntrinsics`), list and dict storage design.

1. **Builtin set**: names, signatures, argument validation, behavior (candidates in §18, phase 1).
2. **Missing builtins**: raise `UnimplementedFeatureError` for CPython builtin names ula4j lacks (proposed in §6.3), or keep `PyNameError`?
3. **Container method table**: which list, tuple, dict and str methods exist first. `dict.items/keys/values` stay excluded.
4. **`print` keywords**: `sep=`/`end=` conflict with the "no keyword arguments" rule. Options: a special case in the compiler or a tiny keyword-capable builtin ABI. `%` formatting is in scope and `str.format` is not.
5. **Function method surface**: none, unless explicitly decided.
6. **`def` identity**: see D8 (proposal: allocate a `PyFunction` per execution; guard non-capturing sites by `sameCode` too).
7. **Uncaught-exception output and exit code** of the command-line launcher.
8. **`MemoryError`**: oversize allocations stay `UnimplementedFeatureError` (D2) or are mapped later.
9. **Tuple display constants**: share an all-constant display (§5.2)?
10. **Recursion guard for `repr`**: replace the `ThreadLocal` identity set by a `ScopedValue` (existing FIXME) when the JDK baseline allows.

## 17. Known deviations to fix

| ID | Deviation | Resolution |
|---|---|---|
| D1 | Rev. 2 had equality implemented twice. **Equality is fixed**: `EqualityIntrinsics` is no longer in the sources and `ComparisonIntrinsics` owns `$eq$`/`$ne$`, with functions, lists and dicts handled and the §11 rule 6 default. **Remaining:** `HashingIntrinsics.$hash$` still defaults to `PyTypeError("not a canonical runtime value")`, and `IntrinsicSupport.typeName` has a `java_type(...)` fallback. `$eq$(Object, None)` raises while `$eq$(None, Object)` returns false (left-operand dispatch). | Apply rule 6 to hashing and update `HashingIntrinsicsTest.NonCanonicalValues`. Decide whether an unknown right operand should also be unimplemented. |
| D2 | `ArithmeticIntrinsics` has private `concatTuples`/`repeatTuple`/`itemsOf`/`tupleOf`, duplicating `TupleIntrinsics.$concat$`/`$repeat$`. Oversize results are `UnimplementedFeatureError` in one and `OutOfMemoryError` in the other. | **Decision**: arithmetic converts the operand (a `BigInteger` count raises `OverflowError`) and calls the tuple intrinsics. Delete the duplicates. Oversize results raise `UnimplementedFeatureError` everywhere, and `TupleIntrinsicsTest.repetitionTooLargeForAJavaArray` is updated. |
| D3 | Rev. 1 forbade switch expressions and required `default`. | Resolved: rules relaxed in §11 (rule 1 now says so). |
| D4 | Semantic and unsupported-construct diagnostics are lazy (§8.6). | §13.2 eager validation phase (phase 5). |
| D5 | Lone-surrogate escapes. | Resolved: parse-time rejection (§7.6). |
| D6 | No interop normalization. | §5.3, roadmap phase 5. |
| D7 | `ModuleContext` Javadoc mentioned a nonexistent `ModuleClassLoader`. | Resolved. |
| D8 | A `def` without captures returns one shared `PyFunction`. CPython creates a new function per execution. **Function `==` and hash now exist**, so `make() == make()` is already observable and wrong for a non-capturing nested `def`. | Fix in phase 1: allocate a `PyFunction` per execution, guard non-capturing sites by `sameCode` as well, and update `nestedNonCapturingDefShareTheSameFunction`. |
| D9 | `%` formatting listed as supported. | Resolved: PLANNED (§7.1). |
| D10 | `NaN` hash is `0` (CPython hashes by identity). | Accepted (§10). |
| D11 | Stale comments: `TupleIntrinsics` claims selectors are cast to `Object`, but they are not. `PyFunction` documents an `entry` field that now lives in `Code`. `PyDict` Javadoc says it does not implement `java.util.Map`, but it extends `AbstractMap`. `PyFunction.createDirectMethodHandle` Javadoc says `WrongMethodTypeException`, the code throws `PyTypeError`. | Fix the comments. |
| D12 | `@PyIntrinsic` versus `@Intrinsic`. | Resolved: `@Intrinsic` everywhere. |
| D13 | `FunctionCallSite` has no global-call fast path, `SwitchPoint` invalidation or instability threshold. | Roadmap phase 6. |
| D14 | **Builtin calling.** `print` is created with `parameters = ["objects"]` and `varargs = true`, so its `arity()` is 1 and `checkArity` rejects `print()` ("missing 1 required positional argument: 'objects'"). `hasRestParameter()` is false for builtins, so the megamorphic thunk (`asSpreader`) and `PyFunction.call` probably do not handle a builtin with several arguments either. None of this is tested. | Give builtins no fixed parameters, give the builtin convention an explicit adaptation in the call-site and thunk code, and add tests for 0/N arguments, megamorphic sites and `PyFunction.call`. |
| D15 | `PyFunction.checkArity`/`arityError` (throwing `WrongMethodTypeException`) are dead duplicates of `FunctionCallSite.checkArity`. | Delete them and keep one implementation. |
| D16 | `ItemIntrinsics`: `"abc"[0]` raises `PyTypeError("'str' object is not subscriptable")` (should be `UnimplementedFeatureError` until str indexing exists). The index-type message always says "not str". | Use `UnimplementedFeatureError` for str, `typeName(index)` in the message, and the CPython wording. |
| D17 | `list`/`tuple` containment compares with `$eq$` only, without the identity shortcut: `x = float('nan')` then `x in [x]` is `False` (CPython: `True`). | Use the same shortcut as the container comparison helpers. |
| D18 | `print` uses `System.out.println`, which writes the platform line separator. | Write `"\n"` explicitly. |
| D19 | Platform-dependent behaviours: `float ** float` uses `StrictMath.pow` (CPython uses the platform libm, results may differ in the last digit); `repr(str)` printability uses the JDK's Unicode tables, not Unicode 16.0; `typeName` reports `function` for builtins (CPython: `builtin_function_or_method`). | Accepted for now; document in tests, fix `typeName` with `PyType`. |
