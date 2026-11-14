package com.github.forax.ula4j.rt.intrinsic;

import com.github.forax.ula4j.compiler.SpecializerFixtures;
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

      var classfile = input.readAllBytes();

      // HACK, let's set the minor version (bytes at index 4 and 5) to 0
      // ASM does not support Valhalla yet
      classfile[4] = 0;
      classfile[5] = 0;

      var node = new ClassNode();
      new ClassReader(classfile).accept(node, 0);
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

        // The descriptor should be (Lany object;I)I
        var parameterTypes = Type.getArgumentTypes(indy.desc);
        var returnType = Type.getReturnType(indy.desc);
        assertEquals(2, parameterTypes.length);
        assertEquals(Type.OBJECT, parameterTypes[0].getSort());
        assertEquals(Type.INT_TYPE, parameterTypes[1]);
        assertEquals(Type.INT_TYPE, returnType);

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


  // -- verify all existing intrinsics

  @Test
  public void hashingIntrinsicsHaveTheExpectedShape() throws IOException {
    assertTypeSwitchShape(HashingIntrinsics.class);
    assertTrue(typeSwitchCount(HashingIntrinsics.class, "$hash$") >= 1);
  }

  @Test
  public void tupleIntrinsicsHaveTheExpectedShape() throws IOException {
    assertTypeSwitchShape(TupleIntrinsics.class);
    assertTrue(typeSwitchCount(TupleIntrinsics.class, "$size$") >= 1);
    assertTrue(typeSwitchCount(TupleIntrinsics.class, "$get$") >= 1);
    //assertTrue(typeSwitchCount(TupleIntrinsics.class, "$concat$") >= 1);  // FIXME
    //assertTrue(typeSwitchCount(TupleIntrinsics.class, "$repeat$") >= 1);  // FIXME
  }

  @Test
  public void listIntrinsicsHaveTheExpectedShape() throws IOException {
    assertTypeSwitchShape(ListIntrinsics.class);
    assertTrue(typeSwitchCount(ListIntrinsics.class, "$iadd$") >= 1);
  }

  @Test
  public void containmentIntrinsicsHaveTheExpectedShape() throws IOException {
    assertTypeSwitchShape(ContainmentIntrinsics.class);
    assertTrue(typeSwitchCount(ContainmentIntrinsics.class, "$in$") >= 1);
  }

  @Test
  public void iteratorIntrinsicsHaveTheExpectedShape() throws IOException {
    assertTypeSwitchShape(IteratorIntrinsics.class);
    assertTrue(typeSwitchCount(IteratorIntrinsics.class, "$iterator$") >= 1);
    assertTrue(typeSwitchCount(IteratorIntrinsics.class, "$next$") >= 1);
  }

  @Test
  public void truthinessIntrinsicsHaveTheExpectedShape() throws IOException {
    assertTypeSwitchShape(TruthinessIntrinsics.class);
    assertTrue(typeSwitchCount(TruthinessIntrinsics.class, "$truthy$") >= 1);
  }

  @Test
  public void comparisonIntrinsicsHaveTheExpectedShape() throws IOException {
    assertTypeSwitchShape(ComparisonIntrinsics.class);
    assertTrue(typeSwitchCount(ComparisonIntrinsics.class, "$lt$") >= 1);
  }

  @Test
  public void arithmeticIntrinsicsHaveTheExpectedShape() throws IOException {
    assertTypeSwitchShape(ArithmeticIntrinsics.class);
    assertTrue(typeSwitchCount(ArithmeticIntrinsics.class, "$add$") >= 1);
    assertTrue(typeSwitchCount(ArithmeticIntrinsics.class, "$neg$") >= 1);
  }

  @Test
  public void stringIntrinsicsHaveTheExpectedShape() throws IOException {
    assertTypeSwitchShape(StringIntrinsics.class);
    assertTrue(typeSwitchCount(StringIntrinsics.class, "$str$") >= 1);
    assertTrue(typeSwitchCount(StringIntrinsics.class, "$repr$") >= 1);
  }
}