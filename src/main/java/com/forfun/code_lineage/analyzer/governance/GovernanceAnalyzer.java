package com.forfun.code_lineage.analyzer.governance;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * V2 Governance metrics implementation.
 * Calculates cyclomatic complexity, duplication rate, comment coverage, and basic stats.
 */
@Component
public class GovernanceAnalyzer implements GovernanceService {

    @Override
    public GovernanceMetrics analyze(com.forfun.code_lineage.analyzer.fetch.FetchedCode code) {
        Path baseDir = Paths.get(code.getBaseDir());
        List<Path> javaFiles = collectJavaFiles(baseDir);

        int totalComplexity = 0;
        int totalComments = 0;
        int totalLines = 0;
        int classCount = 0;
        int methodCount = 0;
        Map<String, List<String>> signatureToMethods = new HashMap<>(); // for duplication detection

        for (Path file : javaFiles) {
            try {
                String content = Files.readString(file);
                totalLines += content.split("\n").length;
                totalComments += countComments(content);

                CompilationUnit cu = StaticJavaParser.parse(file);
                classCount += cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).size();
                methodCount += cu.findAll(MethodDeclaration.class).size();

                ComplexityVisitor cv = new ComplexityVisitor();
                cv.visit(cu, null);
                totalComplexity += cv.getComplexity();

                // Collect signatures for duplication detection
                cu.findAll(MethodDeclaration.class).forEach(md -> {
                    String sig = normalizeSignature(md);
                    signatureToMethods.computeIfAbsent(sig, k -> new ArrayList<>())
                            .add(file.getFileName().toString());
                });

            } catch (Exception ignored) {}
        }

        double commentCoverage = totalLines > 0 ? (double) totalComments / totalLines * 100 : 0;
        double duplicationRate = calculateDuplicationRate(signatureToMethods, methodCount);

        return GovernanceMetrics.builder()
                .appId(code.getAppId())
                .cyclomaticComplexity(totalComplexity)
                .duplicationRate(Math.round(duplicationRate * 10) / 10.0)
                .commentCoverage(Math.round(commentCoverage * 10) / 10.0)
                .testCoverage(0) // V3: Jacoco integration
                .classCount(classCount)
                .methodCount(methodCount)
                .build();
    }

    private int countComments(String content) {
        int count = 0;
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("/*")
                    || trimmed.startsWith("*") || trimmed.startsWith("/**")) {
                count++;
            }
        }
        return count;
    }

    private String normalizeSignature(MethodDeclaration md) {
        return md.getNameAsString() + "(" +
                md.getParameters().stream().map(p -> p.getType().asString())
                        .reduce((a, b) -> a + "," + b).orElse("") + ")";
    }

    private double calculateDuplicationRate(Map<String, List<String>> sigToMethods, int totalMethods) {
        if (totalMethods == 0) return 0;
        int duplicated = 0;
        for (var entry : sigToMethods.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicated += entry.getValue().size() - 1; // count extra copies
            }
        }
        return (double) duplicated / totalMethods * 100;
    }

    private List<Path> collectJavaFiles(Path baseDir) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(baseDir)) {
            walk.filter(p -> p.toString().endsWith(".java")
                            && !p.toString().contains("/test/"))
                    .forEach(files::add);
        } catch (Exception ignored) {}
        return files;
    }

    /** Visitor that calculates cyclomatic complexity (McCabe). */
    private static class ComplexityVisitor extends VoidVisitorAdapter<Void> {
        private int complexity = 0;

        @Override
        public void visit(IfStmt n, Void arg) { complexity++; super.visit(n, arg); }
        @Override
        public void visit(ForStmt n, Void arg) { complexity++; super.visit(n, arg); }
        @Override
        public void visit(ForEachStmt n, Void arg) { complexity++; super.visit(n, arg); }
        @Override
        public void visit(WhileStmt n, Void arg) { complexity++; super.visit(n, arg); }
        @Override
        public void visit(SwitchEntry n, Void arg) { complexity++; super.visit(n, arg); }
        @Override
        public void visit(ConditionalExpr n, Void arg) { complexity++; super.visit(n, arg); } // ternary
        @Override
        public void visit(CatchClause n, Void arg) { complexity++; super.visit(n, arg); }

        int getComplexity() { return complexity; }
    }
}
