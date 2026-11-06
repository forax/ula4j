package com.github.forax.ula4j.rt.specializer;

import com.github.forax.ula4j.rt.RT;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOError;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class Specializer {
  private Specializer() {
    throw new AssertionError();
  }

  private static final Handle BSM_TYPE_SWITCH = new Handle(Opcodes.H_INVOKESTATIC,
      RT.class.getName().replace('.', '/'),
      "bsmTypeSwitch",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);
  private static final Handle BSM_SPECIALIZE = new Handle(Opcodes.H_INVOKESTATIC,
      RT.class.getName().replace('.', '/'),
      "bsmSpecialize",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;",
      false);

  private static final int INTRINSIC_ACCESS = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;

  private static boolean isIntrinsicName(String methodName) {
    return methodName.startsWith("$") && methodName.endsWith("$");
  }

  private static boolean allBsmArgsAreClasses(Object[] elements) {
    for(Object element : elements) {
      if(!(element instanceof Type t) || (t.getSort() != Type.OBJECT && t.getSort() != Type.ARRAY)) {
        return false;
      }
    }
    return true;
  }

  public static Map<String, Supplier<MethodHandle>> specialize(MethodHandles.Lookup lookup, Class<?> clazz) {
    var filename = "/" + clazz.getName().replace('.', '/') + ".class";
    byte[] classfile;
    try(var inputStream = clazz.getResourceAsStream(filename)) {
      if (inputStream == null) {
        throw new IOError(new IOException("Cannot open " + filename));
      }
      classfile = inputStream.readAllBytes();
    } catch (IOException e) {
      throw new IOError(e);
    }
    var specializedMap = new HashMap<String, Supplier<MethodHandle>>();
    var reader = new ClassReader(classfile);
    reader.accept(new ClassVisitor(Opcodes.ASM9) {
      private int version;
      private String ownerInternalName;

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.version = version;
        this.ownerInternalName = name;
      }

      @Override
      public MethodVisitor visitMethod(int access, String methodName, String methodDescriptor, String signature, String[] exceptions) {
        // $deserializeLambda$ is not public, so it should not be specializable
        if ((access & INTRINSIC_ACCESS) != INTRINSIC_ACCESS || !isIntrinsicName(methodName)) {
          return null;
        }

        var writer = new ClassWriter(0);
        var hiddenInternalName = clazz.getPackageName().replace('.', '/') + "/" + methodName;
        writer.visit(version, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, hiddenInternalName, null, "java/lang/Object", null);

        var mv = writer.visitMethod(access, methodName, methodDescriptor, signature, exceptions);
        return new MethodVisitor(Opcodes.ASM9, mv) {
          @Override
          public void visitInvokeDynamicInsn(String name, String descriptor, Handle bsm, Object... bsmArgs) {
            if (bsm.getOwner().equals("java/lang/runtime/SwitchBootstraps")
                && bsm.getName().equals("typeSwitch")
                && bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
                && allBsmArgsAreClasses(bsmArgs)) {
              // The descriptor is (Ljava/lang/Object;I)I to reduce the number of LambdaFrom shape at runtime
              // We also ensure that the restart is always 0 (we do not support "when" syntax)
              super.visitInsn(Opcodes.POP);
              super.visitInsn(Opcodes.ICONST_0);
              super.visitInvokeDynamicInsn(name, "(Ljava/lang/Object;I)I", BSM_TYPE_SWITCH, bsmArgs);
              return;
            }
            super.visitInvokeDynamicInsn(name, descriptor, bsm, bsmArgs);
          }

          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC && isIntrinsicName(name)) {
              if (owner.equals(ownerInternalName) && name.equals(methodName) && descriptor.equals(methodDescriptor)) {
                throw new IncompatibleClassChangeError("Recursive call method " + ownerInternalName + "." + name + descriptor);
              }
              super.visitInvokeDynamicInsn(name, descriptor, BSM_SPECIALIZE, Type.getObjectType(owner));
              return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
          }

          @Override
          public void visitEnd() {
            super.visitEnd();

            writer.visitEnd();
            var content = writer.toByteArray();

            specializedMap.put(methodName + methodDescriptor, () -> {
              try {
                var specializedLookup = lookup.defineHiddenClass(content, true, MethodHandles.Lookup.ClassOption.NESTMATE);

                return specializedLookup.findStatic(
                    specializedLookup.lookupClass(),
                    methodName,
                    MethodType.fromMethodDescriptorString(methodDescriptor, lookup.lookupClass().getClassLoader()));

              } catch (IllegalAccessException e) {
                throw (IllegalAccessError) new IllegalAccessError().initCause(e);
              } catch (NoSuchMethodException e) {
                throw (NoSuchMethodError) new NoSuchMethodError().initCause(e);
              }
            });
          }
        };
      }
    }, 0);
    return Map.copyOf(specializedMap);
  }
}
