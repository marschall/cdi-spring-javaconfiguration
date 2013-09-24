package com.github.marschall.cdispringjavaconfig;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_7;

import java.lang.reflect.Method;
import java.util.List;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;


class ProxySubclassGenerator {

  private final Class<?> superclass;

  private final List<Method> methodsToProxy;

  // TODO only scope singleton
  // TODO session and request scope?
  ProxySubclassGenerator(Class<?> superclass, List<Method> methodsToProxy) {
    this.superclass = superclass;
    this.methodsToProxy = methodsToProxy;
  }

  String getClassName() {
    // TODO optimize
    return this.superclass.getName() + getSubclassPostfix();
  }

  private String getSubclassPostfix() {
    return "$$EnhancerByCGLIB";
  }

  byte[] generate() {
    ClassWriter cw = new ClassWriter(0);

    String[] interfaces = null;
    String signature = null;
    String superName = Type.getInternalName(this.superclass);
    String name = superName + getSubclassPostfix();
    cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER, name, signature, superName, interfaces);

    generateFields(cw);
    generateConstructor(cw, superName);
    generateMethods(cw, superName, name);

    cw.visitEnd();

    return cw.toByteArray();
  }

  private void generateFields(ClassWriter cw) {
    for (Method method : this.methodsToProxy) {
      // TODO check for
      Class<?> returnType = method.getReturnType();
      if (returnType.isPrimitive()) {
        throw new UnsupportedOperationException("primitive beans no supported");
      }
      String fieldSignature = null;
      Object value = null;
      String fieldName = method.getName();
      String fieldType = "L" + Type.getInternalName(returnType) + ";";
      FieldVisitor fv = cw.visitField(ACC_PRIVATE, fieldName, fieldType, fieldSignature, value);
      fv.visitEnd();
    }
  }

  private void generateConstructor(ClassWriter cw, String superName) {
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V");
    mv.visitInsn(RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
  }



  private void generateMethods(ClassWriter cw, String superName, String name) {
    for (Method method : this.methodsToProxy) {
      // TODO check for
      // TODO support argument injection
      Class<?> returnType = method.getReturnType();
      if (returnType.isPrimitive()) {
        throw new UnsupportedOperationException("primitive beans no supported");
      }
      String methodName = method.getName();
      String fieldName = methodName;
      String methodReturnType = "L" + Type.getInternalName(returnType) + ";";
      String fieldType = methodReturnType;
      String[] exceptions = null;
      String desc = "()" + methodReturnType;
      MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_SYNCHRONIZED, methodName, desc, null, exceptions);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, name, fieldName, fieldType);
      Label l0 = new Label();
      mv.visitJumpInsn(IFNONNULL, l0);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, superName, methodName, desc);
      mv.visitFieldInsn(PUTFIELD, name, fieldName, fieldType);
      mv.visitLabel(l0);
      mv.visitFrame(F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, name, fieldName, fieldType);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(2, 1);
      mv.visitEnd();
    }
  }

}
