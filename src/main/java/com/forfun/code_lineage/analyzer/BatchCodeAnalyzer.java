package com.forfun.code_lineage.analyzer;

import com.forfun.code_lineage.analyzer.asm.AsmMethodVisitor;
import com.forfun.code_lineage.analyzer.ast.AstMethodVisitor;
import com.forfun.code_lineage.analyzer.detector.CompositeEntryPointDetector;
import com.forfun.code_lineage.model.graph.MethodNode;
import com.forfun.code_lineage.model.graph.RawRelation;
import com.github.javaparser.StaticJavaParser;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Streaming batch analyzer for large projects (10K+ files).
 * Processes Java files in configurable batches to avoid OOM.
 */
@Component
public class BatchCodeAnalyzer {

    private final CompositeEntryPointDetector entryDetector;
    private static final int DEFAULT_BATCH_SIZE = 500;

    public BatchCodeAnalyzer(CompositeEntryPointDetector entryDetector) {
        this.entryDetector = entryDetector;
    }

    /**
     * Analyze all Java files in a directory with streaming batch processing.
     * Each batch result is passed to the consumer for incremental processing.
     */
    public AnalyzeResult analyzeStreaming(String baseDir, String appId,
                                          List<String> changedFiles,
                                          Consumer<BatchResult> batchConsumer) {
        return analyzeStreaming(baseDir, appId, changedFiles, DEFAULT_BATCH_SIZE, batchConsumer);
    }

    /**
     * Analyze with custom batch size.
     */
    public AnalyzeResult analyzeStreaming(String baseDir, String appId,
                                          List<String> changedFiles, int batchSize,
                                          Consumer<BatchResult> batchConsumer) {
        List<MethodNode> allMethods = new ArrayList<>();
        List<RawRelation> allRelations = new ArrayList<>();
        String techStack = detectTechStack(baseDir);

        // Collect Java files
        List<Path> javaFiles = collectJavaFiles(baseDir, changedFiles);
        List<Path> classFiles = collectClassFiles(baseDir);

        // Process Java source in batches
        int totalBatches = (javaFiles.size() + batchSize - 1) / batchSize;
        for (int batchIdx = 0; batchIdx < javaFiles.size(); batchIdx += batchSize) {
            int end = Math.min(batchIdx + batchSize, javaFiles.size());
            List<Path> batch = javaFiles.subList(batchIdx, end);

            List<MethodNode> batchMethods = new ArrayList<>();
            List<RawRelation> batchRelations = new ArrayList<>();

            for (Path file : batch) {
                try {
                    AstMethodVisitor visitor = new AstMethodVisitor(appId,
                            changedFiles != null ? changedFiles : List.of(), entryDetector);
                    visitor.visit(StaticJavaParser.parse(file), null);
                    batchMethods.addAll(visitor.getMethods());
                    batchRelations.addAll(visitor.getRelations());
                } catch (Exception ignored) {}
            }

            // Deduplicate per batch
            Map<String, MethodNode> uniqueBatchMethods = new LinkedHashMap<>();
            for (MethodNode m : batchMethods) {
                uniqueBatchMethods.putIfAbsent(m.getMethodId(), m);
            }
            batchMethods = new ArrayList<>(uniqueBatchMethods.values());

            // Notify consumer with batch result
            batchConsumer.accept(new BatchResult(
                    batchIdx / batchSize + 1, totalBatches, batchMethods, batchRelations));

            allMethods.addAll(batchMethods);
            allRelations.addAll(batchRelations);
        }

        // Process class files (ASM) — typically fewer, can process all at once
        for (Path classFile : classFiles) {
            try {
                AsmMethodVisitor asmVisitor = new AsmMethodVisitor(appId);
                asmVisitor.analyze(classFile);
                allMethods.addAll(asmVisitor.getMethods());
                allRelations.addAll(asmVisitor.getRelations());
            } catch (Exception ignored) {}
        }

        // Final deduplication
        Map<String, MethodNode> uniqueMethods = new LinkedHashMap<>();
        for (MethodNode m : allMethods) {
            uniqueMethods.putIfAbsent(m.getMethodId(), m);
        }

        return AnalyzeResult.builder()
                .appId(appId).methods(new ArrayList<>(uniqueMethods.values()))
                .relations(allRelations).techStack(techStack).build();
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
                walk.filter(p -> p.toString().endsWith(".java")
                                && !p.toString().contains("/test/"))
                        .forEach(files::add);
            } catch (Exception ignored) {}
        }
        return files;
    }

    private List<Path> collectClassFiles(String baseDir) {
        List<Path> files = new ArrayList<>();
        for (String subDir : List.of("build/classes", "target/classes", "bin")) {
            Path dir = Paths.get(baseDir, subDir);
            if (Files.exists(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.filter(p -> p.toString().endsWith(".class")).forEach(files::add);
                } catch (Exception ignored) {}
            }
        }
        return files;
    }

    /** Result of a single batch — for incremental Neo4j writes or progress reporting. */
    public record BatchResult(int batchNum, int totalBatches,
                              List<MethodNode> methods, List<RawRelation> relations) {}
}
