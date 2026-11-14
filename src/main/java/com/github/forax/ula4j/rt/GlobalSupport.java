package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.rt.error.PyNameError;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.SwitchPoint;
import java.util.Objects;

final class GlobalSupport {
  public GlobalSupport() {
    throw new AssertionError();
  }

  private static final MethodHandle CELL_GET, CELL_SET, NAME_ERROR;
  static {
    var lookup = MethodHandles.lookup();
    try {
      CELL_GET = lookup.findVirtual(GlobalCell.class, "get",
          MethodType.methodType(Object.class));
      CELL_SET = lookup.findVirtual(GlobalCell.class, "set",
          MethodType.methodType(void.class, Object.class));
      NAME_ERROR = lookup.findStatic(GlobalCell.class, "nameError",
          MethodType.methodType(Object.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  static final class GlobalCell {
    record ResolvedCell(Object value, SwitchPoint switchPoint) {}

    private final String name;
    private volatile Object value;

    // guarded by this, null until the value is set
    private SwitchPoint switchPoint;

    GlobalCell(String name, Object value) {
      this.name = name;
      this.value = value;
      super();
    }

    /// Slow path: always correct, whatever the state of the cell.
    public Object get() {
      var value = this.value;  // volatile read
      if (value != null) {
        return value;
      }
      throw (RuntimeException) nameError(name);
    }

    private static Object nameError(String name) {
      throw new PyNameError("name '" + name + "' is not defined");
    }

    public void set(Object newValue) {
      Objects.requireNonNull(newValue);
      synchronized (this) {
        // invalidate switch point if it exists
        var switchPoint = this.switchPoint;
        if (switchPoint != null) {
          SwitchPoint.invalidateAll(new SwitchPoint[] { switchPoint });
          this.switchPoint = null;
        }
        this.value = newValue;  // volatile write
      }
    }

    /// The value to embed as a constant, or null if the cell can not (or no longer) be treated
    /// as a constant: unbound (reading it raises NameError) or already unstable.
    ResolvedCell resolveCell() {
      synchronized (this) {
        var value = this.value;  // volatile read
        var switchPoint = this.switchPoint;
        if (switchPoint == null) {
          this.switchPoint = switchPoint = new SwitchPoint();
        }
        return new ResolvedCell(value, switchPoint);
      }
    }
  }

  private static final class GetGlobalCallSite extends MutableCallSite {
    private static final MethodType TYPE;
    private static final MethodHandle FALLBACK;
    static {
      var lookup = MethodHandles.lookup();
      TYPE = MethodType.methodType(Object.class);
      try {
        FALLBACK = lookup.findVirtual(GetGlobalCallSite.class, "fallback",
            MethodType.methodType(Object.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private static final int MAX_COUNTER = 2;

    private final MethodHandle fallback;
    private final GlobalCell cell;
    private int counter;

    private GetGlobalCallSite(GlobalCell cell) {
      this.cell = cell;
      super(TYPE);
      var fallback = FALLBACK.bindTo(this);
      this.fallback = fallback;
      setTarget(fallback);
    }

    private Object fallback() {
      var counter = this.counter;
      if (counter >= MAX_COUNTER) {
        setTarget(CELL_GET.bindTo(cell));
        return cell.get();
      }
      this.counter = counter + 1;

      var resolvedCell = cell.resolveCell();
      var value = resolvedCell.value;
      MethodHandle mh;
      if (value == null) {
        mh = NAME_ERROR.bindTo(cell.name);
      } else {
        mh = MethodHandles.constant(Object.class, value);
      }
      var target = resolvedCell.switchPoint().guardWithTest(mh, fallback);
      setTarget(target);

      if (value == null) {
        throw (RuntimeException) GlobalCell.nameError(cell.name);
      }
      return value;
    }
  }

  /// A read of a global.
  static CallSite getGlobal(ModuleContext context, String name) {
    var cell = context.cell(name);
    return new GetGlobalCallSite(cell);
  }

  /// A store to a global.
  static CallSite setGlobal(ModuleContext context, String name) {
    var cell = context.cell(name);
    return new ConstantCallSite(CELL_SET.bindTo(cell));
  }
}
