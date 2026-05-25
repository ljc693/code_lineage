package com.forfun.codel_ineage.analyzer;

import com.forfun.codel_ineage.analyzer.asm.AsmMethodVisitor;
import com.forfun.codel_ineage.analyzer.ast.AstMethodVisitor;
import com.forfun.codel_ineage.analyzer.detector.*;
import com.forfun.codel_ineage.model.CallType;
import com.forfun.codel_ineage.model.graph.MethodNode;
import com.forfun.codel_ineage.model.graph.RawRelation;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
public class JavaCodeAnalyzer implements CodeAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(JavaCodeAnalyzer.class);
    private final CompositeEntryPointDetector entryDetector;

    static {
        // Enable Java 17+ features (records, sealed classes, pattern matching, etc.)
        StaticJavaParser.getParserConfiguration().setLanguageLevel(
                ParserConfiguration.LanguageLevel.JAVA_17);
    }

    /** For Spring injection — uses configured detector bean */
    public JavaCodeAnalyzer(CompositeEntryPointDetector entryDetector) {
        this.entryDetector = entryDetector;
    }

    /** For tests/programmatic use — auto-creates default detectors */
    public JavaCodeAnalyzer() {
        this.entryDetector = new CompositeEntryPointDetector(List.of(
                new SpringMvcDetector(),
                new DubboDetector(),
                new GenericAnnotationDetector("ShenYu", "HTTP", Set.of("RestApi"), false),
                new GenericAnnotationDetector("gRPC", "RPC", Set.of("GrpcService"), true),
                new GenericAnnotationDetector("Kafka", "MQ", Set.of("KafkaListener"), false)
        ));
    }

    @Override
    public AnalyzeResult analyze(AnalyzeTask task) {
        String baseDir = task.getFetchedCode().getBaseDir();
        List<String> changedFiles = task.getFetchedCode().getChangedFiles();
        String appId = task.getAppId();
        Consumer<FileProgress> cb = task.getProgressCallback();

        List<MethodNode> allMethods = new ArrayList<>();
        List<RawRelation> allRelations = new ArrayList<>();

        String techStack = detectTechStack(baseDir);

        // Phase 1: AST parse source .java files
        List<Path> javaFiles = collectJavaFiles(baseDir, changedFiles);
        List<Path> classFiles = collectClassFiles(baseDir);
        int totalFiles = javaFiles.size() + classFiles.size();
        int processed = 0;

        AstMethodVisitor astVisitor = new AstMethodVisitor(appId, changedFiles, entryDetector);
        for (Path javaFile : javaFiles) {
            try {
                astVisitor.visit(StaticJavaParser.parse(javaFile), null);
            } catch (Exception e) {
                log.warn("Failed to parse Java file: {}", javaFile, e);
            }
            processed++;
            if (cb != null) {
                cb.accept(new FileProgress(javaFile.getFileName().toString(), processed, totalFiles, "AST"));
            }
        }
        allMethods.addAll(astVisitor.getMethods());
        allRelations.addAll(astVisitor.getRelations());

        // Phase 2: ASM parse .class files
        AsmMethodVisitor asmVisitor = new AsmMethodVisitor(appId);
        for (Path classFile : classFiles) {
            try {
                asmVisitor.analyze(classFile);
            } catch (Exception e) {
                log.warn("Failed to analyze class file: {}", classFile, e);
            }
            processed++;
            if (cb != null) {
                cb.accept(new FileProgress(classFile.getFileName().toString(), processed, totalFiles, "ASM"));
            }
        }
        allMethods.addAll(asmVisitor.getMethods());
        allRelations.addAll(asmVisitor.getRelations());

        // Deduplicate methods by methodId
        Map<String, MethodNode> uniqueMethods = new LinkedHashMap<>();
        for (MethodNode m : allMethods) {
            uniqueMethods.putIfAbsent(m.getMethodId(), m);
        }

        // Merge AST + ASM class hierarchy data and resolve inheritance calls
        Map<String, List<String>> allImplements = new LinkedHashMap<>(astVisitor.getClassImplements());
        asmVisitor.getClassImplements().forEach((k, v) ->
                allImplements.merge(k, v, (a, b) -> { List<String> m = new ArrayList<>(a); m.addAll(b); return m; }));
        Map<String, String> allExtends = new LinkedHashMap<>(astVisitor.getClassExtends());
        allExtends.putAll(asmVisitor.getClassExtends()); // ASM overrides (same key = same class)

        resolveInheritanceCalls(uniqueMethods, allRelations, appId, allImplements, allExtends);

        return AnalyzeResult.builder()
                .appId(appId)
                .methods(new ArrayList<>(uniqueMethods.values()))
                .relations(allRelations)
                .techStack(techStack)
                .build();
    }

    private String detectTechStack(String baseDir) {
        if (Files.exists(Paths.get(baseDir, "build.gradle.kts"))) return "Gradle (Kotlin DSL)";
        if (Files.exists(Paths.get(baseDir, "build.gradle"))) return "Gradle";
        if (Files.exists(Paths.get(baseDir, "pom.xml"))) return "Maven";
        return "Unknown";
    }

    private List<Path> collectJavaFiles(String baseDir, List<String> changedFiles) {
        List<Path> files = new ArrayList<>();
        Path base = Paths.get(baseDir);
        if (changedFiles != null && !changedFiles.isEmpty()) {
            for (String f : changedFiles) {
                if (f.endsWith(".java")) {
                    Path resolved = base.resolve(f);
                    if (Files.exists(resolved)) files.add(resolved);
                }
            }
        } else {
            try (Stream<Path> walk = Files.walk(base)) {
                walk.filter(p -> p.toString().endsWith(".java") && !p.toString().contains("/test/"))
                        .forEach(files::add);
            } catch (Exception e) {
                log.warn("Failed to walk directory: {}", base, e);
            }
        }
        return files;
    }

    /**
     * Creates synthetic CALLS edges for both interface→implementation and
     * abstract-parent→concrete-subclass relationships.
     */
    void resolveInheritanceCalls(Map<String, MethodNode> methods,
                                 List<RawRelation> relations, String appId,
                                 Map<String, List<String>> classImplements,
                                 Map<String, String> classExtends) {
        // Index by FQCN first, then also by simple name for fallback
        Map<String, Map<String, MethodNode>> byFqcnAndSig = new LinkedHashMap<>();
        Map<String, Map<String, MethodNode>> bySimpleNameAndSig = new LinkedHashMap<>();
        for (MethodNode m : methods.values()) {
            String fqcn = (m.getPackageName() != null ? m.getPackageName() + "." : "") + m.getClassName();
            byFqcnAndSig.computeIfAbsent(fqcn, k -> new LinkedHashMap<>())
                    .put(m.getSignature(), m);
            bySimpleNameAndSig.computeIfAbsent(m.getClassName(), k -> new LinkedHashMap<>())
                    .put(m.getSignature(), m);
        }

        // 1) Interface → implementation resolution
        for (var entry : classImplements.entrySet()) {
            String implFqcn = entry.getKey();
            Map<String, MethodNode> implMethods = byFqcnAndSig.get(implFqcn);
            if (implMethods == null) {
                implMethods = bySimpleNameAndSig.get(extractSimpleName(implFqcn));
            }
            if (implMethods == null) continue;

            for (String ifaceName : entry.getValue()) {
                // Infer interface FQCN from implementation's package, then fall back to simple name
                String ifaceFqcn = inferFqcn(implFqcn, ifaceName);
                Map<String, MethodNode> ifaceMethods = byFqcnAndSig.get(ifaceFqcn);
                if (ifaceMethods == null) {
                    ifaceMethods = bySimpleNameAndSig.get(ifaceName);
                }
                if (ifaceMethods == null) continue;

                linkOverrideMethods(ifaceName, implMethods, relations,
                        "implements " + ifaceName, ifaceMethods);
            }
        }

        // 2) Abstract class → subclass resolution
        for (var entry : classExtends.entrySet()) {
            String subFqcn = entry.getKey();
            Map<String, MethodNode> subMethods = byFqcnAndSig.get(subFqcn);
            if (subMethods == null) {
                subMethods = bySimpleNameAndSig.get(extractSimpleName(subFqcn));
            }
            if (subMethods == null) continue;

            String parentSimple = entry.getValue();
            // Infer parent FQCN from subclass's package, then fall back to simple name
            String parentFqcn = inferFqcn(subFqcn, parentSimple);
            Map<String, MethodNode> parentMethods = byFqcnAndSig.get(parentFqcn);
            if (parentMethods == null) {
                parentMethods = bySimpleNameAndSig.get(parentSimple);
            }
            if (parentMethods == null) continue;

            linkOverrideMethods(parentSimple, subMethods, relations,
                    "extends " + parentSimple, parentMethods);
        }
    }

    /** Test-visible overload for JavaCodeAnalyzerInheritanceTest */
    void resolveInheritanceCallsForTest(Map<String, MethodNode> methods,
                                        List<RawRelation> relations, String appId,
                                        Map<String, List<String>> classImplements,
                                        Map<String, String> classExtends) {
        resolveInheritanceCalls(methods, relations, appId, classImplements, classExtends);
    }

    private void linkOverrideMethods(String parentName,
                                     Map<String, MethodNode> childMethods,
                                     List<RawRelation> relations, String label,
                                     Map<String, MethodNode> parentMethods) {
        if (parentMethods == null) return;

        for (var childEntry : childMethods.entrySet()) {
            MethodNode parentMethod = parentMethods.get(childEntry.getKey());
            if (parentMethod == null) continue;
            // Only link if the parent method is abstract (or in an interface)
            if (parentMethod.isAbstract() || parentMethod.getReturnType() == null) {
                relations.add(RawRelation.builder()
                        .caller(parentMethod)
                        .callee(childEntry.getValue())
                        .callType(CallType.INTERNAL)
                        .lineNumber(0)
                        .callExpression(label)
                        .build());
            }
        }
    }

    private static String extractSimpleName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    /** Infer the FQCN of a parent class/interface from a child's FQCN and the parent's simple name. */
    private static String inferFqcn(String childFqcn, String parentSimple) {
        int dot = childFqcn.lastIndexOf('.');
        if (dot < 0) return parentSimple;
        return childFqcn.substring(0, dot + 1) + parentSimple;
    }

    private List<Path> collectClassFiles(String baseDir) {
        List<Path> files = new ArrayList<>();
        for (String subDir : List.of("build/classes", "target/classes", "bin")) {
            Path dir = Paths.get(baseDir, subDir);
            if (Files.exists(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.filter(p -> p.toString().endsWith(".class")).forEach(files::add);
                } catch (Exception e) {
                    log.warn("Failed to walk class directory: {}", dir, e);
                }
            }
        }
        return files;
    }
}
