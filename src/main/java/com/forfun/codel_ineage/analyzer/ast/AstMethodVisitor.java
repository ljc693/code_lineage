package com.forfun.codel_ineage.analyzer.ast;

import com.forfun.codel_ineage.analyzer.detector.CompositeEntryPointDetector;
import com.forfun.codel_ineage.analyzer.detector.EntryPointInfo;
import com.forfun.codel_ineage.model.CallType;
import com.forfun.codel_ineage.model.graph.MethodNode;
import com.forfun.codel_ineage.model.graph.RawRelation;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AST visitor that extracts methods, call relations, class hierarchy,
 * method references, and constructor calls from Java source files.
 */
public class AstMethodVisitor extends VoidVisitorAdapter<Void> {

    // ── Lombok annotations that indicate auto-generated getters/setters ─
    private static final Set<String> LOMBOK_ANNOTATIONS = Set.of(
            "Getter", "Setter", "Data", "Value", "Builder", "ToString",
            "EqualsAndHashCode", "RequiredArgsConstructor", "NoArgsConstructor",
            "AllArgsConstructor", "With", "Accessors", "Slf4j", "Log", "Log4j2",
            "Generated", "lombok.Generated", "javax.annotation.processing.Generated");

    // ── framework-specific scope patterns for external call detection ──
    private static final Set<String> EXTERNAL_SCOPES = Set.of(
            "restTemplate", "webClient", "kafkaTemplate", "rabbitTemplate",
            "dubbo", "feign", "jdbcTemplate", "redisTemplate", "mongoTemplate");

    // ── state ──────────────────────────────────────────────────────────
    private final String appId;
    private final List<String> targetFiles;
    private final CompositeEntryPointDetector entryDetector;
    private final List<MethodNode> methods = new ArrayList<>();
    private final List<RawRelation> relations = new ArrayList<>();
    private final Map<String, List<String>> classImplements = new LinkedHashMap<>();
    private final Map<String, String> classExtends = new LinkedHashMap<>();
    private String currentPackage;
    private String currentClass;
    private List<String> currentClassAnnotations = List.of();
    private boolean currentClassIsInterface;

    public AstMethodVisitor(String appId, List<String> targetFiles,
                            CompositeEntryPointDetector entryDetector) {
        this.appId = appId;
        this.targetFiles = targetFiles;
        this.entryDetector = entryDetector;
    }

    // ── compilation unit ───────────────────────────────────────────────

    @Override
    public void visit(CompilationUnit cu, Void arg) {
        currentPackage = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString()).orElse("");
        super.visit(cu, arg);
    }

    // ── class / interface declaration ──────────────────────────────────

    @Override
    public void visit(ClassOrInterfaceDeclaration cd, Void arg) {
        String previousClass = currentClass;
        boolean previousIsInterface = currentClassIsInterface;
        currentClass = cd.getNameAsString();
        currentClassIsInterface = cd.isInterface();
        currentClassAnnotations = cd.getAnnotations().stream()
                .map(a -> a.getNameAsString()).collect(Collectors.toList());

        String classFqcn = fqcn(currentClass);

        // record implements (for interface→impl resolution)
        if (!cd.isInterface() && !cd.getImplementedTypes().isEmpty()) {
            List<String> ifaces = cd.getImplementedTypes().stream()
                    .map(t -> t.getNameAsString()).collect(Collectors.toList());
            classImplements.put(classFqcn, ifaces);
        }

        // record extends (for abstract-class→subclass resolution)
        cd.getExtendedTypes().stream().findFirst().ifPresent(parent -> {
            String parentSimple = parent.getNameAsString();
            classExtends.put(classFqcn, parentSimple);
        });

        super.visit(cd, arg);
        currentClass = previousClass;
        currentClassIsInterface = previousIsInterface;
    }

    // ── method declaration ─────────────────────────────────────────────

    @Override
    public void visit(MethodDeclaration md, Void arg) {
        // skip Lombok-generated / plain getter-setter noise
        if (isGetterOrSetter(md)) {
            super.visit(md, arg);
            return;
        }

        String signature = buildSignature(md);
        MethodNode method = MethodNode.builder()
                .methodId(methodId(currentClass, md))
                .signature(signature)
                .returnType(md.getType().asString())
                .params(buildParams(md))
                .annotations(collectAnnotations(md))
                .lineNumber(md.getBegin().map(p -> p.line).orElse(0))
                .isEntry(detectEntry(md))
                .httpPath(entryPath(md))
                .httpMethod(entryHttpMethod(md))
                .appId(appId)
                .className(currentClass)
                .packageName(currentPackage)
                .isAbstract(!md.getBody().isPresent())
                .build();

        methods.add(method);

        // extract calls from method body
        md.getBody().ifPresent(body -> {
            extractMethodCalls(body, method);
            extractMethodReferences(body, method);
            extractConstructorCalls(body, method);
        });

        super.visit(md, arg);
    }

    // ── call extraction ────────────────────────────────────────────────

    /** Extracts direct method invocations: {@code obj.method()}. */
    private void extractMethodCalls(BlockStmt body, MethodNode caller) {
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            String scope = call.getScope().map(Node::toString).orElse("this");
            CallType type = isExternalScope(scope) ? CallType.EXTERNAL : CallType.INTERNAL;

            relations.add(RawRelation.builder()
                    .caller(caller)
                    .callee(MethodNode.builder()
                            .methodId(buildPartialId(scope + "." + call.getNameAsString()))
                            .signature(call.getNameAsString())
                            .className(scope)
                            .appId(type == CallType.EXTERNAL ? null : appId)
                            .build())
                    .callType(type)
                    .lineNumber(call.getBegin().map(p -> p.line).orElse(0))
                    .callExpression(call.toString())
                    .build());
        }
    }

    /** Extracts method references: {@code this::method}, {@code Class::method}. */
    private void extractMethodReferences(BlockStmt body, MethodNode caller) {
        for (MethodReferenceExpr ref : body.findAll(MethodReferenceExpr.class)) {
            String scope = ref.getScope().toString();
            String methodName = ref.getIdentifier();
            CallType type = isExternalScope(scope) ? CallType.EXTERNAL : CallType.INTERNAL;

            relations.add(RawRelation.builder()
                    .caller(caller)
                    .callee(MethodNode.builder()
                            .methodId(buildPartialId(scope + "." + methodName))
                            .signature(methodName)
                            .className(scope)
                            .appId(type == CallType.EXTERNAL ? null : appId)
                            .build())
                    .callType(type)
                    .lineNumber(ref.getBegin().map(p -> p.line).orElse(0))
                    .callExpression(scope + "::" + methodName)
                    .build());
        }
    }

    /** Extracts constructor calls: {@code new Xxx(args)}. */
    private void extractConstructorCalls(BlockStmt body, MethodNode caller) {
        for (ObjectCreationExpr ctor : body.findAll(ObjectCreationExpr.class)) {
            String typeName = ctor.getTypeAsString();
            String simpleName = typeName.contains(".")
                    ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;

            relations.add(RawRelation.builder()
                    .caller(caller)
                    .callee(MethodNode.builder()
                            .methodId(buildPartialId(typeName + ".<init>"))
                            .signature("<init>")
                            .className(simpleName)
                            .appId(appId)
                            .build())
                    .callType(CallType.INTERNAL)
                    .lineNumber(ctor.getBegin().map(p -> p.line).orElse(0))
                    .callExpression("new " + typeName + "(..)")
                    .build());
        }
    }

    // ── entry-point detection ──────────────────────────────────────────

    private boolean detectEntry(MethodDeclaration md) {
        if (entryDetector != null) {
            List<String> methodAnnNames = md.getAnnotations().stream()
                    .map(a -> a.getNameAsString()).collect(Collectors.toList());
            return !entryDetector.detect(currentClassAnnotations, methodAnnNames,
                    currentClass, md.getNameAsString()).isEmpty();
        }
        // V1 fallback: check Spring mapping annotations directly
        for (String ann : md.getAnnotations().stream()
                .map(a -> a.getNameAsString()).collect(Collectors.toList())) {
            if (SPRING_MAPPING_ANNOTATIONS.contains(ann)) return true;
        }
        return false;
    }

    private static final Set<String> SPRING_MAPPING_ANNOTATIONS = Set.of(
            "RequestMapping", "GetMapping", "PostMapping",
            "PutMapping", "DeleteMapping", "PatchMapping");

    private String entryPath(MethodDeclaration md) {
        if (entryDetector == null) return extractHttpPath(md);
        List<String> methodAnnNames = md.getAnnotations().stream()
                .map(a -> a.getNameAsString()).collect(Collectors.toList());
        var entries = entryDetector.detect(currentClassAnnotations, methodAnnNames,
                currentClass, md.getNameAsString());
        if (entries.isEmpty()) return null;
        EntryPointInfo first = entries.get(0);
        return first.getPath() != null ? first.getPath() : extractHttpPath(md);
    }

    private String entryHttpMethod(MethodDeclaration md) {
        if (entryDetector == null) return extractHttpMethodSpring(md);
        List<String> methodAnnNames = md.getAnnotations().stream()
                .map(a -> a.getNameAsString()).collect(Collectors.toList());
        var entries = entryDetector.detect(currentClassAnnotations, methodAnnNames,
                currentClass, md.getNameAsString());
        if (entries.isEmpty()) return null;
        EntryPointInfo first = entries.get(0);
        return first.getHttpMethod() != null ? first.getHttpMethod() : extractHttpMethodSpring(md);
    }

    // ── getter / setter detection ──────────────────────────────────────

    /** Returns true if the method looks like a plain getter or setter. */
    private boolean isGetterOrSetter(MethodDeclaration md) {
        String name = md.getNameAsString();
        int paramCount = md.getParameters().size();

        // setter: setXxx(Type param)
        if (paramCount == 1 && name.length() > 3 && name.startsWith("set")
                && Character.isUpperCase(name.charAt(3))) {
            return hasLombokAnnotation() || hasTrivialBody(md, true);
        }
        // getter: getXxx() with non-void return, or isXxx() for boolean
        if (paramCount == 0 && name.length() > 3
                && ((name.startsWith("get") && Character.isUpperCase(name.charAt(3)))
                 || (name.startsWith("is") && Character.isUpperCase(name.charAt(2))))) {
            return hasLombokAnnotation() || hasTrivialBody(md, false);
        }
        return false;
    }

    private boolean hasLombokAnnotation() {
        for (String ann : currentClassAnnotations) {
            String simple = ann.contains(".") ? ann.substring(ann.lastIndexOf('.') + 1) : ann;
            if (LOMBOK_ANNOTATIONS.contains(simple)) return true;
        }
        return false;
    }

    /** Checks if the method body is a trivial one-liner: return this.field or this.field = param. */
    private boolean hasTrivialBody(MethodDeclaration md, boolean isSetter) {
        var body = md.getBody();
        if (body.isEmpty()) return false;
        String bodyStr = body.get().toString().trim()
                .replaceAll("\\s+", " ")     // normalise whitespace
                .replace("{ ", "").replace(" }", "").replace(";", "").strip();
        if (isSetter) {
            return bodyStr.matches("this\\.\\w+ = \\w+")
                    || bodyStr.matches("this\\.\\w+ = \\w+");
        }
        return bodyStr.matches("return this\\.\\w+")
                || bodyStr.matches("return\\s+this\\.\\w+");
    }

    // ── annotation collection ──────────────────────────────────────────

    private List<String> collectAnnotations(MethodDeclaration md) {
        List<String> all = new ArrayList<>(currentClassAnnotations);
        md.getAnnotations().stream().map(a -> a.getNameAsString()).forEach(all::add);
        return all;
    }

    // ── helper methods ─────────────────────────────────────────────────

    private String fqcn(String simpleName) {
        return (currentPackage != null && !currentPackage.isEmpty())
                ? currentPackage + "." + simpleName : simpleName;
    }

    private String methodId(String className, MethodDeclaration md) {
        return appId + ":" + fqcn(className) + "." + md.getNameAsString()
                + "(" + md.getParameters().stream()
                        .map(p -> p.getType().asString())
                        .collect(Collectors.joining(",")) + ")";
    }

    private String buildSignature(MethodDeclaration md) {
        return md.getNameAsString() + "("
                + md.getParameters().stream()
                        .map(p -> p.getType().asString())
                        .collect(Collectors.joining(", ")) + ")";
    }

    private List<MethodNode.Param> buildParams(MethodDeclaration md) {
        return md.getParameters().stream()
                .map(p -> MethodNode.Param.builder()
                        .name(p.getNameAsString())
                        .type(p.getType().asString()).build())
                .collect(Collectors.toList());
    }

    private String buildPartialId(String qualifiedName) {
        return currentPackage + "." + qualifiedName;
    }

    private boolean isExternalScope(String scope) {
        return EXTERNAL_SCOPES.stream().anyMatch(scope::contains);
    }

    // ── Spring annotation path extraction (V1 fallback) ────────────────

    private String extractHttpPath(MethodDeclaration md) {
        return md.getAnnotationByName("PostMapping")
                .or(() -> md.getAnnotationByName("GetMapping"))
                .or(() -> md.getAnnotationByName("RequestMapping"))
                .or(() -> md.getAnnotationByName("PutMapping"))
                .or(() -> md.getAnnotationByName("DeleteMapping"))
                .flatMap(a -> a.isSingleMemberAnnotationExpr()
                        ? java.util.Optional.of(a.asSingleMemberAnnotationExpr()
                                .getMemberValue().asStringLiteralExpr().getValue())
                        : java.util.Optional.empty())
                .orElse(null);
    }

    private String extractHttpMethodSpring(MethodDeclaration md) {
        if (md.getAnnotationByName("PostMapping").isPresent()) return "POST";
        if (md.getAnnotationByName("GetMapping").isPresent()) return "GET";
        if (md.getAnnotationByName("PutMapping").isPresent()) return "PUT";
        if (md.getAnnotationByName("DeleteMapping").isPresent()) return "DELETE";
        if (md.getAnnotationByName("PatchMapping").isPresent()) return "PATCH";
        return null;
    }

    // ── getters ────────────────────────────────────────────────────────

    public List<MethodNode> getMethods() { return methods; }
    public List<RawRelation> getRelations() { return relations; }
    public Map<String, List<String>> getClassImplements() { return classImplements; }
    public Map<String, String> getClassExtends() { return classExtends; }
}
