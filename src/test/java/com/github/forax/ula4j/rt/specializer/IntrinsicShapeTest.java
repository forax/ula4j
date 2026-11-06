package com.github.forax.ula4j.rt.specializer;

import com.github.forax.ula4j.rt.intrinsic.HashingIntrinsics;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

import java.io.IOException;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// javac lowers pattern switches to an implementation-defined form. The Specializer depends on that form,
/// so a JDK upgrade that changes it must fail here, before it fails at run time.
public final class IntrinsicShapeTest {
  private static ClassNode classNode(Class<?> type) throws IOException {
    try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
      assertNotNull(input, "can not find classfile of " + type.getName());
      var node = new ClassNode();
      new ClassReader(input).accept(node, 0);
      return node;
    }
  }

  private static boolean isIntrinsic(String name) {
    return name.startsWith("$") && name.endsWith("$");
  }

  private static boolean isTypeSwitch(Handle bsm) {
    return bsm.getOwner().equals("java/lang/runtime/SwitchBootstraps") && bsm.getName().equals("typeSwitch");
  }

  private static void assertTypeSwitchShape(Class<?> intrinsics) throws IOException {
    for (var method : classNode(intrinsics).methods) {
      if (!isIntrinsic(method.name)) {
        continue;
      }
      var where = intrinsics.getName() + "." + method.name + method.desc;
      for (var insn : method.instructions.toArray()) {
        if (!(insn instanceof InvokeDynamicInsnNode indy) || !isTypeSwitch(indy.bsm)) {
          continue;
        }
        assertEquals("(Ljava/lang/Object;I)I", indy.desc, where);
        for (var label : indy.bsmArgs) {
          assertInstanceOf(Type.class, label, where + ": only class labels are supported");
          var sort = ((Type) label).getSort();
          assertTrue(sort == Type.OBJECT || sort == Type.ARRAY, where);
        }
      }
    }
  }

  private static long typeSwitchCount(Class<?> intrinsics, String methodName) throws IOException {
    return classNode(intrinsics).methods.stream()
        .filter(method -> method.name.equals(methodName))
        .flatMap(method -> StreamSupport.stream(method.instructions.spliterator(), false))
        .filter(insn -> insn instanceof InvokeDynamicInsnNode indy && isTypeSwitch(indy.bsm))
        .count();
  }

  @Test
  public void fixtureSwitchesHaveTheExpectedShape() throws IOException {
    assertTypeSwitchShape(SpecializerFixtures.class);
    assertTrue(typeSwitchCount(SpecializerFixtures.class, "$describe$") >= 1);
    assertTrue(typeSwitchCount(SpecializerFixtures.class, "$overlap$") >= 1);
  }

  @Test
  public void hashingIntrinsicsHaveTheExpectedShape() throws IOException {
    assertTypeSwitchShape(HashingIntrinsics.class);
    assertTrue(typeSwitchCount(HashingIntrinsics.class, "$hash$") >= 1);
  }
}