package com.forfun.code_lineage.analyzer.asm;

import com.forfun.code_lineage.model.CallType;
import com.forfun.code_lineage.model.graph.MethodNode;
import com.forfun.code_lineage.model.graph.RawRelation;
import org.objectweb.asm.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * ASM bytecode analyser — extracts methods, call relations, and class hierarchy
 * from compiled .class files (dependency jars, etc. where source is unavailable).
 */
public class AsmMethodVisitor {

    // ── framework prefixes treated as external calls ───────────────────
    private static final Set<String> EXTERNAL_PREFIXES = Set.of(
            "java.", "javax.", "jakarta.", "org.springframework.",
            "org.apache.", "com.fasterxml.", "org.hibernate.",
            "com.baomidou.", "org.mybatis.");

    private final String appId;
    private final List<MethodNode> methods = new ArrayList<>();
    private final List<RawRelation> relations = new ArrayList<>();
    /** subclassFQCN → parentClassFQCN */
    private final Map<String, String> classExtends = new LinkedHashMap<>();
    /** classFQCN → [interfaceFQCN, ...] */
    private final Map<String, List<String>> classImplements = new LinkedHashMap<>();

    public AsmMethodVisitor(String appId) {
        this.appId = appId;
    }

    public void analyze(Path classFile) throws IOException {
        try (InputStream is = Files.newInputStream(classFile)) {
            new ClassReader(is).accept(new ClassVisitor(Opcodes.ASM9) {

                private ClassInfo info = new ClassInfo();

                @Override
                public void visit(int version, int access, String name,
                                  String signature, String superName, String[] interfaces) {
                    info.className = name.replace('/', '.');
                    info.superName = superName != null ? superName.replace('/', '.') : null;
                    info.interfaces = interfaces != null
                            ? Arrays.stream(interfaces).map(i -> i.replace('/', '.')).toList()
                            : List.of();
                    info.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                  String signature, String[] exceptions) {
                    if ("<init>".equals(name) || "<clinit>".equals(name)) return null;
                    if (isGetterOrSetter(name, descriptor)) return null;

                    String methodSig = buildSignature(name, descriptor);
                    String returnType = Type.getReturnType(descriptor).getClassName();

                    MethodNode caller = MethodNode.builder()
                            .methodId(appId + ":" + info.className + "." + methodSig)
                            .signature(methodSig)
                            .returnType(returnType)
                            .className(info.className.substring(
                                    info.className.lastIndexOf('.') + 1))
                            .packageName(info.className.contains(".")
                                    ? info.className.substring(0, info.className.lastIndexOf('.')) : "")
                            .appId(appId)
                            .isAbstract((access & Opcodes.ACC_ABSTRACT) != 0)
                            .build();
                    methods.add(caller);

                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                     String descriptor, boolean isInterface) {
                            String calleeClass = owner.replace('/', '.');
                            String calleeSig = buildSignature(name, descriptor);
                            boolean isExternal = isExternalClass(calleeClass);

                            relations.add(RawRelation.builder()
                                    .caller(caller)
                                    .callee(MethodNode.builder()
                                            .methodId(appId + ":" + calleeClass + "." + calleeSig)
                                            .signature(calleeSig)
                                            .className(calleeClass)
                                            .appId(isExternal ? null : appId)
                                            .build())
                                    .callType(isExternal ? CallType.EXTERNAL : CallType.INTERNAL)
                                    .lineNumber(0)
                                    .callExpression(calleeClass + "." + name + "(..)")
                                    .build());
                        }
                    };
                }

                @Override
                public void visitEnd() {
                    // record class hierarchy for post-analysis resolution
                    if (!info.isInterface && info.superName != null
                            && !"java.lang.Object".equals(info.superName)) {
                        classExtends.put(info.className, info.superName);
                    }
                    if (!info.interfaces.isEmpty()) {
                        classImplements.put(info.className, info.interfaces.stream()
                                .map(i -> i.substring(i.lastIndexOf('.') + 1))
                                .toList());
                    }
                }
            }, 0);
        }
    }

    // ── helpers ────────────────────────────────────────────────────────

    private String buildSignature(String name, String descriptor) {
        Type[] argTypes = Type.getArgumentTypes(descriptor);
        StringBuilder sb = new StringBuilder(name).append("(");
        for (int i = 0; i < argTypes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(argTypes[i].getClassName());
        }
        return sb.append(")").toString();
    }

    private boolean isExternalClass(String className) {
        for (String prefix : EXTERNAL_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isGetterOrSetter(String name, String descriptor) {
        Type[] args = Type.getArgumentTypes(descriptor);
        Type ret = Type.getReturnType(descriptor);
        if (args.length == 1 && name.startsWith("set") && name.length() > 3
                && Character.isUpperCase(name.charAt(3))) {
            return true; // setter
        }
        if (args.length == 0 && !Type.VOID_TYPE.equals(ret)
                && ((name.startsWith("get") && name.length() > 3
                     && Character.isUpperCase(name.charAt(3)))
                 || (name.startsWith("is") && name.length() > 2
                     && Character.isUpperCase(name.charAt(2))))) {
            return true; // getter
        }
        return false;
    }

    // ── internal state ─────────────────────────────────────────────────

    private static class ClassInfo {
        String className;
        String superName;
        List<String> interfaces = List.of();
        boolean isInterface;
    }

    // ── getters ────────────────────────────────────────────────────────

    public List<MethodNode> getMethods() { return methods; }
    public List<RawRelation> getRelations() { return relations; }
    public Map<String, String> getClassExtends() { return classExtends; }
    public Map<String, List<String>> getClassImplements() { return classImplements; }
}
