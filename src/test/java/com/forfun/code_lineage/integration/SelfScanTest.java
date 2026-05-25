package com.forfun.code_lineage.integration;

import com.forfun.code_lineage.analyzer.AnalyzeResult;
import com.forfun.code_lineage.analyzer.AnalyzeTask;
import com.forfun.code_lineage.analyzer.JavaCodeAnalyzer;
import com.forfun.code_lineage.analyzer.fetch.FetchedCode;
import com.forfun.code_lineage.model.CallType;
import com.forfun.code_lineage.model.graph.MethodNode;
import com.forfun.code_lineage.model.graph.RawRelation;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Meta-pilot: scan the lineage tool itself.
 */
class SelfScanTest {

    @Test
    void shouldScanItself() throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();

        List<String> javaFiles = Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.toString().contains("/src/main/java/"))
                .filter(p -> !p.toString().contains("/test/"))
                .map(p -> root.relativize(p).toString())
                .toList();

        System.out.println("=== Self-Scan: code-lineage ===");
        System.out.println("Files: " + javaFiles.size());

        JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer();
        AnalyzeResult result = analyzer.analyze(AnalyzeTask.builder()
                .fetchedCode(FetchedCode.builder()
                        .baseDir(root.toString()).changedFiles(javaFiles).appId("code-lineage").build())
                .appId("code-lineage").build());

        List<MethodNode> methods = result.getMethods();
        List<RawRelation> relations = result.getRelations();
        List<MethodNode> entries = methods.stream().filter(MethodNode::isEntry).toList();

        System.out.println("Tech: " + result.getTechStack());
        System.out.println("Methods: " + methods.size());
        System.out.println("Relations: " + relations.size());
        System.out.println("HTTP Entries: " + entries.size());

        long internal = relations.stream().filter(r -> r.getCallType() == CallType.INTERNAL).count();
        long external = relations.stream().filter(r -> r.getCallType() == CallType.EXTERNAL).count();

        System.out.println("INTERNAL: " + internal + " EXTERNAL: " + external);
        System.out.println("=== Self-Scan Complete ===");

        assertThat(methods).isNotEmpty();
        assertThat(entries).isNotEmpty(); // should have REST controllers
    }
}
