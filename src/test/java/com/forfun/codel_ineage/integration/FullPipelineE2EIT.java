package com.forfun.codel_ineage.integration;

import com.forfun.codel_ineage.analyzer.AnalyzeResult;
import com.forfun.codel_ineage.analyzer.AnalyzeTask;
import com.forfun.codel_ineage.analyzer.JavaCodeAnalyzer;
import com.forfun.codel_ineage.fetcher.FetchedCode;
import com.forfun.codel_ineage.graph.*;
import com.forfun.codel_ineage.model.*;
import com.forfun.codel_ineage.pathfinder.*;
import com.forfun.codel_ineage.service.LineageQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.ActiveProfiles;

/**
 * Full pipeline E2E: Scan clawer → Neo4j → Query → G6 output
 */
@SpringBootTest
@ActiveProfiles("test")
class FullPipelineE2EIT {

    private static final String CLAWER_ROOT = "../clawer";

    @Autowired
    private LineageQueryService queryService;

    @Autowired
    private Neo4jMethodRepository methodRepo;

    @Autowired
    private org.neo4j.driver.Driver neo4jDriver;

    private List<MethodNode> scannedMethods;
    private List<RawRelation> scannedRelations;
    private List<MethodNode> entryMethods;

    @BeforeEach
    void scanAndWriteToNeo4j() throws Exception {
        Path clawerPath = Path.of(CLAWER_ROOT).toAbsolutePath().normalize();
        assertThat(Files.exists(clawerPath)).isTrue();

        // Phase 1: Scan
        JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer();
        List<String> javaFiles = collectJavaFiles(clawerPath);

        AnalyzeResult result = analyzer.analyze(AnalyzeTask.builder()
                .fetchedCode(FetchedCode.builder()
                        .baseDir(clawerPath.toString())
                        .changedFiles(javaFiles)
                        .appId("clawer")
                        .build())
                .appId("clawer")
                .build());

        scannedMethods = result.getMethods();
        scannedRelations = result.getRelations();
        entryMethods = scannedMethods.stream().filter(MethodNode::isEntry).toList();

        // Phase 2: Build SubGraph and write to Neo4j
        SubGraph subGraph = SubGraph.builder()
                .methods(scannedMethods)
                .callsEdges(buildCallsEdges(scannedRelations))
                .build();

        // Write directly via Neo4jAdapter (Spring bean)
        methodRepo.deleteAll();
        new Neo4jAdapter(methodRepo, neo4jDriver).write(subGraph);

        // Count actual CALLS edges in Neo4j
        long edgeCount = methodRepo.countCallsEdges();

        System.out.println("\n=== Wrote to Neo4j: " + scannedMethods.size()
                + " methods, " + scannedRelations.size() + " AST relations → "
                + edgeCount + " CALLS edges created ("
                + (edgeCount * 100 / Math.max(scannedRelations.size(), 1)) + "% resolution) ===");
    }

    @Test
    void shouldFindEntryMethodsInNeo4j() {
        List<Neo4jMethodEntity> entries = methodRepo.findEntryMethods("clawer");
        assertThat(entries).isNotEmpty();
        System.out.println("Entry methods in Neo4j: " + entries.size());
        entries.forEach(e -> System.out.println("  " + e.getHttpMethod() + " " + e.getHttpPath()
                + " → " + e.getClassName() + "." + e.getSignature()));
    }

    @Test
    void shouldQueryDownstreamLineage() {
        MethodNode entry = entryMethods.stream()
                .filter(e -> e.getHttpPath() != null && e.getHttpPath().contains("task"))
                .findFirst()
                .orElse(entryMethods.get(0));

        Map<String, Object> result = queryService.getDownstreamLineage(
                entry.getMethodId(), 5, "g6", "method");

        assertThat(result).containsKeys("graph", "meta");
        System.out.println("\n=== Downstream lineage from: " + entry.getHttpPath() + " ===");
        printG6Summary(result);
    }

    @Test
    void shouldQueryUpstreamLineage() {
        MethodNode entry = entryMethods.get(0);
        Map<String, Object> result = queryService.getUpstreamLineage(
                entry.getMethodId(), 3, "g6");

        assertThat(result).containsKeys("graph", "meta");
        System.out.println("\n=== Upstream lineage to: " + entry.getSignature() + " ===");
        printG6Summary(result);
    }

    @Test
    void shouldFormatAsG6TreeAndRaw() {
        MethodNode entry = entryMethods.get(0);

        Map<String, Object> tree = queryService.getDownstreamLineage(
                entry.getMethodId(), 5, "tree", "method");
        assertThat(tree).containsKey("totalPaths");
        System.out.println("\n=== Tree format: " + tree.get("totalPaths") + " paths ===");

        Map<String, Object> raw = queryService.getDownstreamLineage(
                entry.getMethodId(), 5, "raw", "method");
        assertThat(raw).containsKeys("methods", "tables", "calls", "accesses", "paths");
        System.out.println("=== Raw format: "
                + ((List<?>) raw.get("methods")).size() + " methods, "
                + ((List<?>) raw.get("calls")).size() + " calls ===");
    }

    @Test
    void shouldReturnEntryPointsList() {
        List<Map<String, Object>> entries = queryService.getEntryPoints("clawer", null);
        assertThat(entries).isNotEmpty();
        System.out.println("\n=== Entry points query: " + entries.size() + " results ===");
        entries.stream().limit(5).forEach(e ->
                System.out.println("  " + e.get("httpMethod") + " " + e.get("httpPath")
                        + " → " + e.get("className") + "." + e.get("signature")));
    }

    @SuppressWarnings("unchecked")
    private void printG6Summary(Map<String, Object> result) {
        Map<String, Object> graph = (Map<String, Object>) result.get("graph");
        List<?> nodes = (List<?>) graph.get("nodes");
        List<?> edges = (List<?>) graph.get("edges");
        Map<String, Object> meta = (Map<String, Object>) result.get("meta");
        System.out.println("  Nodes: " + nodes.size() + ", Edges: " + edges.size()
                + ", Depth: " + meta.get("depth") + ", QueryTime: " + meta.get("queryTimeMs") + "ms");
    }

    private List<String> collectJavaFiles(Path root) throws IOException {
        return Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.toString().contains("/src/main/java/"))
                .filter(p -> !p.toString().contains("/test/"))
                .map(p -> root.relativize(p).toString())
                .toList();
    }

    private List<CallsEdge> buildCallsEdges(List<RawRelation> relations) {
        int counter = 0;
        List<CallsEdge> edges = new ArrayList<>();
        for (RawRelation r : relations) {
            edges.add(CallsEdge.builder()
                    .id("c-" + (counter++))
                    .sourceMethodId(r.getCaller().getMethodId())
                    .targetMethodId(r.getCallee().getMethodId())
                    .callType(r.getCallType())
                    .lineNumber(r.getLineNumber())
                    .callExpression(r.getCallExpression())
                    .build());
        }
        return edges;
    }
}
