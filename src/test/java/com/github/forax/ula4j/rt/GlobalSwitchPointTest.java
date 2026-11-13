package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.compiler.Compiler;
import com.github.forax.ula4j.rt.error.PyNameError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class GlobalSwitchPointTest {

  private static Object read(MethodHandle handle) {
    try {
      return (Object) handle.invokeExact();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new UndeclaredThrowableException(t);
    }
  }

  private static MethodHandle getter(ModuleContext ctx, String name) {
    return FunctionSupport.getGlobal(ctx, name).dynamicInvoker();
  }

  @Test
  public void aSiteLinkedWhileUnboundSeesTheLaterBinding() {
    var ctx = new ModuleContext();
    var site = getter(ctx, "x");
    assertThrows(PyNameError.class, () -> read(site));

    ctx.setGlobal("x", 1L);
    assertEquals(1L, read(site));
  }

  @Test
  public void aFoldedSiteSeesEveryRebinding() {
    var ctx = new ModuleContext();
    ctx.setGlobal("x", 1L);
    var before = getter(ctx, "x");
    assertEquals(1L, read(before));

    ctx.setGlobal("x", 2L);
    var after = getter(ctx, "x");
    assertEquals(2L, read(before));
    assertEquals(2L, read(after));

    ctx.setGlobal("x", 3L);
    assertEquals(3L, read(before));
    assertEquals(3L, read(after));
  }

  @Test
  public void storesThroughTheCallSiteInvalidateToo() throws Throwable {
    var ctx = new ModuleContext();
    ctx.setGlobal("x", 1L);

    var get = getter(ctx, "x");
    var set = FunctionSupport.setGlobal(ctx, "x").dynamicInvoker();
    assertEquals(1L, read(get));

    set.invokeExact((Object) 7L);
    assertEquals(7L, read(get));
  }

  @Test
  public void aGlobalShadowsABuiltinEvenForSitesThatFoldedTheBuiltin() {
    var ctx = new ModuleContext();
    var len = Builtins.BUILTINS.get("len");
    var site = getter(ctx, "len");
    assertSame(len, read(site));

    ctx.setGlobal("len", 5L);
    assertEquals(5L, read(site));

    var other = new ModuleContext();  // cells are per module
    assertSame(len, read(getter(other, "len")));
  }

  @Test
  public void compiledCodeSeesRebindingAfterTheCallSiteFolded() {
    var module = Compiler.run("""
        def read():
            return x
        x = 1
        a = read()
        x = 2
        b = read()
        x = 3
        c = read()
        x = 4
        d = read()
        x = 5
        e = read()
        """);
    assertEquals(1L, module.getGlobal("a"));
    assertEquals(2L, module.getGlobal("b"));
    assertEquals(3L, module.getGlobal("c"));
    assertEquals(4L, module.getGlobal("d"));
    assertEquals(5L, module.getGlobal("e"));
  }

  @Test
  @Timeout(60)
  public void readersNeverSeeAValueGoingBackwards() throws Exception {
    var ctx = new ModuleContext();
    ctx.setGlobal("counter", 0L);
    var linkedEarly = getter(ctx, "counter");
    var done = new AtomicBoolean();

    try (var pool = Executors.newFixedThreadPool(4)) {
      var futures = new ArrayList<Future<?>>();
      for (var task = 0; task < 4; task++) {
        var relink = task % 2 == 1;  // half of the readers also link new sites while the writer runs
        futures.add(pool.submit(() -> {
          var last = -1L;
          while (!done.get()) {
            var handle = relink ? getter(ctx, "counter") : linkedEarly;
            var value = (Long) read(handle);
            assertTrue(value >= last, value + " after " + last);
            last = value;
          }
        }));
      }
      for (var i = 1L; i <= 20_000; i++) {
        ctx.setGlobal("counter", i);
      }
      done.set(true);
      for (var future : futures) {
        future.get();
      }
    }
    assertEquals(20_000L, ctx.getGlobal("counter"));
  }
}