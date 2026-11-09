# ula4j: Implementation Plan

## Purpose

This document is the durable design baseline for coding agents implementing **ula4j**,
a deliberately limited subset of Python compiled to JVM bytecode and executed on the JVM.

The implementation must preserve CPython behavior for every construct it accepts.
Unsupported syntax or behavior must be rejected explicitly during parsing or compilation, with a useful diagnostic.
It must never silently approximate Python semantics.

The initial implementation prioritizes a correct generic runtime and compiler.
Runtime specialization is a separate optimization layer and must not become a second source of language semantics.

## 1. Project goals and principles

### 1.1 Goals

- Compile a deliberately limited Python language subset to JVM bytecode.
- Preserve the semantics of a pinned, 64-bit CPython reference build for supported constructs.
- Use ordinary JVM objects as Python values. There is no universal `PyValue` hierarchy.
- Implement generic runtime semantics once, in readable Java intrinsics.
- Use `invokedynamic` for dynamic operations and calls.
- Use ASM at run time to derive specialized implementations from generic intrinsic bytecode.
- Use `ConstantDynamic` for selected immutable scalar constants.
- Keep the generic implementation available as the correctness fallback.

### 1.2 Non-goals for the first implementation

The following are explicitly deferred:

- Python `set` and set displays.
- `try` / `except` / `else` exception handling.
- Explicit `raise` statements.
- The Python `is` and `is not` operators.
- Imports, user-defined classes, decorators, annotations, generators, async functions, and comprehensions.
- Dict view objects and the `dict.items()`, `dict.keys()`, and `dict.values()` methods.
- A general Python object-identity operator or bound-method object model.

Runtime operations may raise Python exceptions implicitly.
The first implementation does not let Python code catch those exceptions.

### 1.3 Core invariants

1. **Accepted syntax has Python semantics.** If the compiler accepts a construct, its behavior must match the pinned CPython reference for the supported subset.
2. **Unsupported means rejected.** No silent Java-like fallback or semantic approximation.
3. **Generic semantics are authoritative.** Specialized implementations are derived from generic Java intrinsics and must fall back to them.
4. **No handwritten semantic fast paths.** Do not maintain a second implementation of an operation's meaning.
5. **Per-call-site specialization.** Each `invokedynamic` `CallSite` owns its own specialization state. There is no separate global cache of specialized implementations.
6. **Mutable containers remain mutable Python objects.** List and dict operations must preserve their specified Python mutation and aliasing behavior.
7. **Python equality is explicit.** Do not delegate Python equality, hashing, or ordering to Java collection or record implementations.

## 2. Value representation

| Python value/type | JVM representation | Notes |
|---|---|---|
| `None` | `PyNone.INSTANCE` | Singleton; never Java `null`. |
| `bool` | `Boolean` | Not treated as a Java `Number`; numeric intrinsics handle it explicitly. |
| `int` | `Long` or `BigInteger` | One logical Python type. Use `Long` when the value fits and promote to `BigInteger` on overflow. |
| `float` | `Double` | IEEE-754 binary64; Python numeric semantics are implemented explicitly. |
| `str` | `String` | Length, indexing, slicing, iteration, containment, and ordering use Unicode code points. |
| `list` | `PyList` | Mutable list backed by an array. |
| `tuple` | Java records | Four fixed-arity tuple record classes plus one generic record carrying `Object[]`. |
| `dict` | `PyDict` | Custom insertion-ordered hash table. |
| `range` | `PyRange` | Final immutable class; supports Python's arbitrary-precision integer bounds, iteration, membership, indexing, slicing, equality, and length behavior. |
| `slice` | `PySlice` | Final record `(start, stop, step)`; each component may be `None`. |
| Python function | `PyFunction` | Entry point, name, arity, varargs flag, and captured values. No default-argument metadata. |
| Python type object | `PyType` | Observable result of `type()` and callable constructor descriptor for supported builtin types. |

### 2.1 Representation rules

- Runtime value classes are final records or final classes unless the JVM representation itself is a JDK class.
- `PyFunction` is a final class.
- `PyList` and `PyDict` are mutable reference objects.
- Tuple arities 1–4 use specialized records. The generic tuple record is used for arity 0 and arity 5 or greater.
- The generic tuple record defensively copies its input array.
- Tuple shape is a performance property only. Every tuple intrinsic must work correctly with every tuple representation.
- Because Python `is` and `is not` are not implemented, the language does not expose a general identity comparison for immutable values.  The implementation must nevertheless preserve the specified behavior of every supported operation, especially container lookup and equality.

### 2.2 Identity and equality

- The parser/compiler rejects `is` and `is not`.
- Python `==`, `!=`, ordering, and hashing are implemented by runtime intrinsics. They do not rely on Java record `equals`, `hashCode`, or collection equality.
- Numeric equality and hashing must unify equivalent keys across `bool`, `int`, and `float`.
- Tests must not depend on the identity of immutable values or on interning behavior.

## 3. `PyType` and builtin type objects

`PyType` represents the observable Python type objects supported by the implementation.

Examples:

- `type(1)` and `type(10**100)` return the same canonical `int` type object.
- Tuples of different lengths return the same canonical `tuple` type object.
- `isinstance(True, int)` is true.

Rules:

- `PyType` is not the dispatch mechanism for ordinary operators. Operators use JVM representation pattern matching, not `typeOf()`.
- Each `PyType` records the JVM classes representing its instances. For example, the `int` type includes `Long`, `BigInteger`, and `Boolean` for the purpose of the `int` subtype relationship; the `bool` type includes `Boolean`.
- Canonical supported type objects may be materialized through `ConstantDynamic` and are the same objects returned by `type()`.
- Canonical type objects are callable and dispatch to the constructor intrinsic for that type.
- `isinstance(obj, cls)` checks the JVM class set represented by `cls`. If `cls` is constant at link time, the call-site linker may emit direct class checks.
- `type()` is implemented as an intrinsic using pattern matching over supported JVM representations.
- Builtin functions have the distinct observable type `builtin_function_or_method`; `type(len)` therefore reports `builtin_function_or_method`, as in CPython.

## 4. Collections, equality, hashing, and iteration

### 4.1 Supported containers

The initial implementation supports:

- `list`, represented by `PyList` and implementing all unmodfiable methods of `List<Object>`.
- `tuple`, represented by the tuple record classes.
- `dict`, represented by `PyDict` and implementing all unmodifiable methods of `Map<Object,Object>`.

Sets and frozensets are not implemented. Set displays are rejected. `{}` remains a dictionary display.

### 4.2 Python semantics take precedence

Equality, hashing, ordering, membership, indexing, and mutation are runtime semantics,
not delegated to Java collection behavior.

In particular:

- Never rely on Java `equals()` or `hashCode()` for Python list, tuple, or dict semantics.
- List equality is recursive and element-wise, following Python semantics.
- Tuple equality is recursive and element-wise, following Python semantics.
- Dictionary equality is independent of insertion order and follows Python key/value equality semantics.
- Lists and dictionaries are unhashable.
- Tuples are hashable if and only if all their elements are hashable.
- Numeric hashing follows the pinned 64-bit CPython behavior so equivalent values such as `1`, `True`, and `1.0` behave as equal dictionary keys and have compatible hashes.
- String and tuple hash values are not specified as stable output and must not be printed or asserted as fixed values in tests. Only the hash values of `bool`, `int`, and `float` are specified and deterministic.

### 4.3 `PyDict` implementation

`PyDict` uses a custom open-addressing, insertion-ordered hash table.

The implementation must preserve Python behavior for all supported dictionary operations, including:

- Insertion order.
- Replacement of an existing value without changing the key's insertion position.
- Deletion and reinsertion behavior.
- Key equality and hash collisions.
- Missing-key behavior.
- Membership and subscript lookup.
- Iteration over keys.
- Mutation during iteration.

All those methods are implemented as intrinsics (dict.get() is $get$(dict), dict.items() is $items$(dict), etc).
Dictionary iteration must detect structural changes and raise a Python `RuntimeError`, rather than exposing `ConcurrentModificationException`. The implementation should also detect key-set changes that CPython detects, not just changes in dictionary size.

### 4.4 `PyList` implementation

`PyList` must preserve Python semantics for every operation exposed to compiled Python code.

- List iteration is index-based, matching CPython behavior when the list is mutated during iteration.
- Mutation during iteration must not produce Java fail-fast behavior.
- List membership uses Python equality, with the container identity fast path described above.
- List indexing, slicing, assignment, deletion, concatenation, repetition, and supported methods must follow Python semantics.

Python-facing behavior is implemented using intrinsics.
Compiled Python code must reach Python behavior through the runtime API rather than accidentally invoking an incompatible Java method.

### 4.5 Tuple implementation

Tuple operations must work across all tuple record shapes and follow Python semantics.

The implementation must cover equality, hashing, ordering where supported, indexing, slicing, concatenation, repetition, membership, iteration, and the explicitly supported tuple methods.

### 4.6 Methods

Method calls are supported only for explicitly listed methods on these receiver categories:

- `list`
- `tuple`
- `dict`
- `str`
- Python function

There is no general attribute or bound-method object model.
`obj.name(args)` is compiled to a dedicated `callMethod` bootstrap method that is bound to the corresponding instrinsics.

## 5. `None`, missing states, constants, and Java interop

### 5.1 Missing states

- Python `None` is `PyNone.INSTANCE`, never Java `null`.
- `null` is used as internal states.
- There is no unbound-local sentinel. Definite-assignment analysis guarantees that a local is not read before assignment.

### 5.2 `ConstantDynamic`

`ConstantDynamic` is used only for selected immutable scalar constants.

- Numeric literals, including values such as `17`, may be resolved as constants. Large integer literals may resolve to `BigInteger` constants.
- Other scalar constants may use `ConstantDynamic` when sharing them cannot change the observable behavior of supported operations.
- List, dict, and tuple displays are not resolved as shared constants.
- In particular, `[]` and `()` are not `ConstantDynamic` constants. Their literal factories run at the appropriate evaluation point.
- No mutable object is shared through `ConstantDynamic`.

### 5.3 Java interop boundary

Values entering Python from Java are normalized as follows:

- `null` → `PyNone.INSTANCE`
- `Integer`, `Short`, `Byte` → `Long`
- `Float` → `Double`
- `Character` → one-character `String`

Intrinsics accept only canonical runtime representations. A Java `null` reaching an intrinsic is an internal error, not Python `None`.

## 6. Intrinsics and builtins

### 6.1 Runtime intrinsics

Runtime intrinsics are static Java methods annotated `@Intrinsic`.
The method name must start with '$' and end with '$'.
They form the compiler-to-runtime ABI and are not Python names.

Examples include:

- Arithmetic and comparison: `$add$`, `$sub$`, `$mul$`, `$div$`, `$floorDiv$`, `$mod$`, `$pow$`, `$eq$`, `$lt$`, and related operations.
- Augmented operations: `$iadd$`, `$isub$`, `$imul$`, and corresponding operations for the supported augmented-assignment forms.
- Container operations: `$getItem$`, `$setItem$`, `$deleteItem$`, `$contains$`, and literal factories.
- Literal factories: `listLiteral`, `tupleLiteral`, and `dictLiteral`.
- Type and conversion operations: `type`, `isinstance`, and supported constructors.

The exact inventory is finalized alongside the language subset and builtin table.

### 6.2 Augmented assignment

Augmented assignment is not compiled blindly as ordinary binary arithmetic followed by assignment.

Each supported augmented operator has a corresponding runtime intrinsic. The intrinsic implements the Python behavior for the supported operand types.

`+=` is specifically implemented by `iadd`:

- Numeric operands use Python numeric addition semantics.
- When the left operand is a list, `iadd` extends that same list using the supported iterable on the right and returns the original list object.
- The list case must preserve aliases. For example, after `b = a; a += [3]`, `b` observes the mutation.
- For other supported operands, `iadd` follows Python augmented-addition behavior, including producing a new value where the type is not mutable-in-place.
- Operand combinations for which CPython also raises an exception raise the corresponding Python exception from the runtime. Operand combinations that CPython supports but ula4j does not implement raise `UnimplementedFeatureError`, which is not a Python exception (see §9).

For subscript targets such as `a[index()] += value()`, the compiler evaluates the target components once, in Python's required order, before storing the result. It must not evaluate the container or index twice.

Other augmented operators receive the same treatment: use a dedicated intrinsic with explicit Python semantics for supported operands. An operator/operand combination that has not been implemented must be rejected or raise `UnimplementedFeatureError`; it must not silently use Java semantics.

### 6.3 Python builtins

The initial builtin set is intentionally not frozen in this document. It will be decided separately after the language/runtime boundary is clearer.

The proposed candidates from the earlier design include:

`bool`, `int`, `float`, `str`, `list`, `tuple`, `dict`, `range`, `slice`, `type`, `isinstance`, `len`, `print`, `repr`, `hash`, `abs`, `min`, `max`, and `sum`.

This is a candidate list, not a commitment that every name or every CPython signature is implemented initially. Each accepted builtin must have a specified signature, argument validation, return type, and error behavior.

### 6.4 Python special methods

Python special methods such as `__add__` are not exposed or dynamically resolved. Their required behavior is implemented directly by runtime intrinsics for the supported types.

## 7. Language subset

The compiler accepts only the constructs listed below. The list is the language contract; implementation details must not silently expand or alter it.

### 7.1 Supported syntax

| Category | Supported constructs |
|---|---|
| Statements | Expression statements, `pass`, `return`, `break`, `continue` |
| Assignment | Name assignment, subscript assignment, fixed tuple unpacking |
| Augmented assignment | Supported augmented operators through dedicated intrinsics; `+=` includes numeric and list behavior described in §6.2 |
| Deletion | `del a[i]`, `del d[k]`; deleting a name is not supported |
| Branching | `if` / `elif` / `else` |
| Loops | `while` and `for`, including `else` clauses |
| Functions | `def` with positional parameters and one `*rest` parameter; nested definitions subject to §8 |
| Exceptions | Runtime-generated exceptions only; no source-level exception handling or explicit raising |
| Displays | List, tuple, and dict displays |
| Expressions | Arithmetic, bitwise operations, comparisons, comparison chains, `and`, `or`, `not`, `in`, conditional expressions, and `%` string formatting |
| Subscription | List, tuple, string, dict, and range indexing; supported slicing for list, tuple, string, and range |
| Calls | Positional calls; method calls in the form `obj.name(args)` when the method is in the supported method table |
| Iteration | `for` over list, tuple, string, dict (keys), and range; membership follows Python semantics for supported containers and range |

`for ... else` and `while ... else` follow Python: the `else` suite runs only when the loop terminates without executing `break`.

### 7.2 Explicitly rejected syntax

The parser or compiler rejects at least the following:

- `is` and `is not`.
- Set displays and set operations.
- Comprehensions of every kind.
- Star-unpacking at call sites and in assignment targets.
- `**` call expansion and keyword arguments.
- Default parameter values and `**kwargs`.
- Generators, `yield`, `async`, and `await`.
- `with`, `try`, `except`, and `finally`.
- Explicit `raise` and `assert` statements in the first implementation.
- Decorators and annotations.
- `import`, `global`, `nonlocal`, `match`, and the walrus operator.
- `lambda`.
- User-defined classes.
- Attribute stores and non-call attribute loads.
- Bound-method extraction such as `f = obj.name`.
- `del name`.
- Any method call not present in the explicit method table.
- Dict view methods `items()`, `keys()`, and `values()`.
- `sorted()` and `list.sort()`.

Diagnostics should identify the unsupported construct and, where practical, its source location.

### 7.3 Method calls

`obj.name(args)` compiles to `callMethod` and dispatches on the receiver representation and method name. Since bound-method objects are not represented, extracting a method as a value is rejected.

### 7.4 Truthiness and boolean operators

Truthiness follows Python semantics for every supported type.

`and` and `or` return one of their operands, not a coerced Boolean. They short-circuit and evaluate operands in Python order.

`not` returns a Boolean.

### 7.5 Comparison chains

Comparison chains follow Python semantics. Intermediate expressions are evaluated only once, comparisons short-circuit, and the result is a Boolean.

### 7.6 Strings

String operations use Unicode code points rather than UTF-16 code units for:

- `len`
- indexing
- slicing
- iteration
- containment
- ordering

There is no cached per-string metadata in the initial representation. The surrogate-free fast path may require an O(n) scan; this is an accepted implementation cost.

A surrogate escape in a string literal (such as `'\ud83d'`) leads to a runtime error. This is a deviation from Python, which allows lone surrogates in strings.

## 8. Functions, calling convention, globals, locals, and closures

### 8.1 Calling convention

A compiled Python function has an entry signature conceptually equivalent to:

```text
(Object c0, ..., Object cn-1, PyFunction self, Object a0, ..., Object an-1 [, Object rest]) -> Object
```

- `c0` ... `cn-1` are the captured arguments
- `self` carries captured values and provides the function's code identity.
- `a0` ... `an-1` are the arguments
- `rest`, when present, is a Python tuple containing the extra positional arguments.
- Call sites build the rest tuple with an argument collector.
- Wrong arity raises a Python `TypeError`.

### 8.2 Call-site kinds

There are three dynamic call forms:

1. **Global call.** The bootstrap resolves the current function from its module cell and binds it as a constant method handle guarded by a `SwitchPoint`. Rebinding the global invalidates the switch point. A cell rebound more than a configurable threshold is marked unstable and its sites use the value-call path to avoid repeated relinking.
2. **Value call.** An inline cache guards on the `PyFunction` if there is no captured args or on function's code identity. Closures created by repeated executions of the same `def` therefore share the code guard while retaining their own captured values.
3. **Method call.** A dedicated `callMethod` site calls the corresponding intrinsics.

### 8.3 Globals

- Each compiled module has a module state and its own classloader.
- Module-level names live in per-name cells.
- A function resolves globals against its defining module.
- Since `global` is rejected, only module-level code binds global names; function bodies read them.
- Builtin lookup is a fallback in the same cell mechanism.
- Defining a module-level name that shadows a builtin invalidates sites linked to that builtin through a `SwitchPoint`.

### 8.4 Locals and closures

- Function locals are JVM local-variable slots holding `Object`.
- Closure capture uses a Java-style effectively-final rule.
- A variable from an enclosing function may be captured only if it has exactly one binding occurrence, that binding is not in a loop body or loop target, it is definitely assigned when the nested definition executes, and it is not deleted or rebound by the nested function.
- Augmented and unpacking assignments count as bindings.
- Captured arguments are copied into `PyFunction` at definition time and loaded into locals in the function prologue.
- Mutating an object referenced by a captured variable remains allowed.
- Nested functions may refer to their own name through the `self` parameter when the binding rules permit it.
- Mutual recursion among nested functions is rejected when it would require capturing a name before assignment.
- Violations produce a compile-time diagnostic naming the variable and the violated rule.

### 8.5 Definite assignment

The compiler performs definite-assignment analysis over the supported control flow:

- `if` / `elif` / `else`
- `while`, including `break`, `continue`, and `else`
- `for`, including `break`, `continue`, and `else`

A local read that is not definitely assigned on every path to that read is a compile-time error.
There is no unbound-local sentinel and no runtime unbound check.

Because `try` / `except` is excluded, exception-target cleanup is not part of the initial data-flow analysis.

## 9. Runtime exceptions

Runtime intrinsics raise Python exception objects implicitly when an operation fails.
Python source code cannot explicitly raise or catch exceptions in the first implementation.

A Python exception hierarchy is represented by JVM throwable classes, initially rooted at `PyBaseException`.
The proposed hierarchy includes the common builtin exceptions needed by supported operations, such as:

- `PyException`
- `PyArithmeticError`, `PyZeroDivisionError`, `PyOverflowError`
- `PyLookupError`, `PyIndexError`, `PyKeyError`
- `PyTypeError`, `PyValueError`, `PyAttributeError`, `PyNameError`, `PyRuntimeError`
- `PyStopIteration`, `PyAssertionError` only if a later supported construct requires them

Implementation rules:

- JVM stack-trace generation remains enabled. Do not use the no-stack-trace `Throwable` constructor in the first implementation.
- Runtime-generated Python exceptions carry the appropriate Python exception type and message.
- `UnimplementedFeatureError` is not a Python exception and is not part of the `PyBaseException` hierarchy. It is raised at run time when an operation or operand combination is valid in CPython but is not implemented in ula4j.
- Unexpected ordinary Java exceptions are internal implementation failures unless an explicit boundary mapping is added.
- `StackOverflowError` and `OutOfMemoryError` are not mapped to `RecursionError` / `MemoryError`.
- Python-style traceback rendering is computed from Java stack frames when required.
- Since source-level exception handling is not supported, Python exceptions propagate out of the compiled program.

## 10. Numeric semantics

The reference platform is a **64-bit CPython build**.
Python integer values remain arbitrary precision; 64-bit refers to the reference platform's machine word and
hash behavior, not a restriction that Python integers fit in 64 bits.

### 10.1 Representation

- `Long` is the preferred representation for integers that fit.
- Overflow promotes to `BigInteger` without changing the logical Python type.
- `Boolean` is handled explicitly and behaves as a subtype of `int` where Python requires it.
- `Double` represents Python `float` using binary64 semantics.

### 10.2 Required semantics

- Integer and float comparisons are exact Python comparisons; do not convert arbitrary integers to `double` merely to compare them.
- `-0.0 == 0.0` and `NaN != NaN`.
- `/` on integers produces a correctly rounded Python float, including for large integers.
- `//` and `%` use Python floor semantics, including for negative operands and floats. The remainder has the sign of the divisor.
- `**` follows Python behavior, including negative exponents producing floats and zero raised to a negative exponent raising `ZeroDivisionError`.
- `int(float('inf'))` raises `OverflowError`; converting NaN to `int` raises `ValueError`.
- Numeric hashes follow the pinned 64-bit CPython algorithm, including reduction modulo `2**61 - 1` where applicable.
- Boolean cases are explicit in numeric intrinsics. Bitwise operations on two booleans return a Boolean where Python does.

Numeric semantics must be tested against the pinned CPython executable, not inferred from Java arithmetic behavior.

## 11. Intrinsics as Java

Intrinsics are static methods with the convention $name$ annotated `@Intrinsic`.
Type-dependent dispatch uses Java pattern-matching `switch` statements over the input objects.

Example shape:

```java
@Intrinsic
public static Object $add$(Object left, Object right) {
  switch (left) {
    case Long l -> {
      switch (right) {
        case Long r -> {
          try {
            return Math.addExact(l, r);
          } catch (ArithmeticException _) {
            return BigInteger.valueOf(l).add(BigInteger.valueOf(r));
          }
        }
        default -> { return addGeneric(left, right); }
      }
    }
    case Double l -> {
      switch (right) {
        case Double r -> { return l + r; }
        default -> { return addGeneric(left, right); }
      }
    }
    case String l -> {
      switch (right) {
        case String r -> { return l.concat(r); }
        default -> { return addGeneric(left, right); }
      }
    }
    default -> { return addGeneric(left, right); }
  }
}
```

Rules:

- Use type patterns only: no `when` guards, record patterns, `case null`, or expression switches in intrinsic dispatch.
- A `default` case is always present.
- The generic intrinsic implements the complete semantics and error behavior.
- Only calls to other methods with the convention $name$ are rewritten to `invokedynamic`.
- Ordinary JDK calls such as `Math.addExact` remain direct calls.
- Intrinsics handle only canonical JVM representations. Java `null` is an internal bug.

## 12. JDK coupling

The exact lowering of Java pattern switches is a `javac`/JDK implementation detail.

A CI shape test asserts the expected `typeSwitch` bootstrap form for every intrinsic.
A JDK upgrade that changes the expected form must fail loudly; update the specializer only after reviewing the new bytecode shape.

## 13. JVM and compiler limits

No heroic method splitting or spilling is attempted.

- If a generated method exceeds the JVM's 64 KB bytecode limit, compilation fails with a clear diagnostic.
- If a function exceeds the supported JVM parameter limit, compilation fails with a clear diagnostic.
- Diagnostics should identify the function or source construct responsible.

## 14. Toolchain and build

- Target Java 27. Until Java 28 is GA.
- Use ASM 9.11.
- The CPython semantic reference and test version is Python 3.14.
- The compiler grammar should track the selected CPython version closely for accepted syntax, without implementing unrelated language features.
- The parser may be handwritten or generated; that choice remains open, but the accepted grammar and diagnostics are normative.

For CPython-based testing, use 3.14 standalone interpreter. Download a specific build, verify its SHA-256, and execute it with `ProcessBuilder`; do not depend on a developer's system Python. Committed golden outputs may provide a fast hermetic CI layer, while differential and generated-program tests use the interpreter.

## 15. Verification

### 15.1 Differential testing

Run handwritten and generated programs against the pinned 64-bit CPython executable.

Compare observable results for the accepted subset, including:

- Printed output and representation.
- Return values where the test harness exposes them.
- Exception type and message for failing operations.
- Mutation and aliasing behavior.
- Iteration order where Python specifies it.

Avoid asserting unspecified behavior, including set order (sets are not implemented), string hash values, and identity of immutable values.

### 15.2 Semantic test groups

At minimum, maintain dedicated tests for:

- Long/BigInteger promotion and numeric edge cases.
- Exact int/float comparisons, NaN, infinities, and signed zero.
- Boolean/integer equality, hashing, and dictionary key behavior.
- List and tuple recursive equality, hashing rules, slicing, and mutation.
- Dict insertion order, key equality, collisions, deletion/reinsertion, and mutation during iteration.
- String code-point indexing, slicing, iteration, containment, and ordering.
- Truthiness, operand-returning `and`/`or`, and short-circuit behavior.
- Comparison-chain evaluation order and single evaluation of intermediate expressions.
- Evaluation order of subscript assignment, unpacking, dict displays, and call arguments.
- `for`/`while` `else` behavior with and without `break`.
- Augmented assignment, especially list `+=`, alias preservation, and subscript target evaluation exactly once.
- Closure capture restrictions and definite-assignment diagnostics.
- Rejection diagnostics for every unsupported construct, including `is`, `is not`, sets, `try`, `except`, `raise`, and dict view methods.

### 15.3 Generic versus specialized execution

Property tests run each eligible intrinsic through both the generic and specialized paths over representative combinations of supported values, including `Long`, `BigInteger`, `Boolean`, and `Double`, with boundary values, NaN, and positive/negative zero.

Tests must verify:

- Specialized and generic results are equivalent.
- Specialized and generic exceptions have equivalent Python types and messages.
- Guard failure correctly reaches the generic path.
- A polymorphic site falls back according to its local policy.
- Different call sites do not share generated specialized implementations or call-site profile state.
- Nested intrinsic call sites specialize independently.

### 15.4 Bytecode and build tests

- CI checks the expected `typeSwitch` bootstrap shape for every intrinsic.
- Class-file and ASM compatibility is verified for the pinned JDK.

### 15.5 Benchmarks

A JMH baseline must exist before specializer work. The baseline uses ordinary `invokedynamic` calls to generic intrinsics.

Measure specialization against that baseline. Do not claim a specialization win without repeatable benchmark evidence.

### 15.6 Acceptance criteria

- Every supported construct has at least one CPython-checked test.
- Every rejected construct has a test asserting the diagnostic.
- Every intrinsic has generic semantic tests.
- Every specialized intrinsic has generic-versus-specialized equivalence tests.
- No optimization may change the accepted language semantics.

## 16. Open decisions

These decisions must be resolved as implementation approaches the relevant area; they are intentionally not silently fixed by this document.

1. **CPython reference build:** the version is pinned to Python 3.14 (§14); choose and pin the exact patch release and a 64-bit build.
2. **Builtin set:** decide the initial names, exact signatures, argument validation, and behavior. The candidate list in §6.3 is not final.
3. **Container method table:** decide exactly which list, tuple, dict, str, and function methods are initially available.
4. **Dictionary method scope:** confirm the supported dictionary methods beyond subscript, membership, iteration, and the explicitly selected method table. `items`, `keys`, and `values` remain excluded.
5. **Output formatting:** decide whether `print(x, end=...)` is supported despite keyword arguments otherwise being rejected. `%` string formatting is supported; `str.format` is not. 
6. **Function method surface:** decide whether any function methods are supported; do not infer support merely because function receivers are listed as a method-call category.
