package com.forfun.codel_ineage.integration;

import com.forfun.codel_ineage.analyzer.AnalyzeResult;
import com.forfun.codel_ineage.analyzer.AnalyzeTask;
import com.forfun.codel_ineage.analyzer.JavaCodeAnalyzer;
import com.forfun.codel_ineage.fetcher.FetchedCode;
import com.forfun.codel_ineage.graph.Neo4jAdapter;
import com.forfun.codel_ineage.graph.Neo4jMethodRepository;
import com.forfun.codel_ineage.model.*;
import com.forfun.codel_ineage.pathfinder.ExternalCallResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for L2 cross-app lineage.
 * Verifies that an EXTERNAL CALLS edge from App-A (restTemplate.postForObject)
 * is resolved to App-B's HTTP entry method (@PostMapping("/api/orders")) via
 * ExternalCallResolver.
 */
@SpringBootTest
@ActiveProfiles("test")
class CrossAppLineageIT {

    @Autowired
    private Neo4jMethodRepository methodRepo;

    @Autowired
    private ExternalCallResolver externalResolver;

    @Autowired
    private org.neo4j.driver.Driver neo4jDriver;

    @BeforeEach
    void cleanNeo4j() {
        methodRepo.deleteAll();
    }

    @Test
    void shouldResolveCrossAppExternalCall(@TempDir Path appA, @TempDir Path appB) throws Exception {
        // ── App-B: Order Service ────────────────────────────────────────
        createFile(appB, "com/appb/OrderController.java", """
            package com.appb;
            import org.springframework.web.bind.annotation.*;
            @RestController
            public class OrderController {
                @PostMapping("/api/orders")
                public OrderDTO createOrder(@RequestBody CreateOrderRequest req) {
                    return new OrderService().process(req);
                }
            }
            """);
        createFile(appB, "com/appb/OrderService.java", """
            package com.appb;
            public class OrderService {
                public OrderDTO process(CreateOrderRequest req) {
                    return new OrderDTO();
                }
            }
            """);
        createFile(appB, "com/appb/OrderDTO.java", """
            package com.appb;
            public class OrderDTO { private String id; }
            """);
        createFile(appB, "com/appb/CreateOrderRequest.java", """
            package com.appb;
            public class CreateOrderRequest { private String product; }
            """);
        Files.createFile(appB.resolve("build.gradle"));

        // ── App-A: Checkout / Caller ──────────────────────────────────
        createFile(appA, "com/appa/CheckoutController.java", """
            package com.appa;
            import org.springframework.web.bind.annotation.*;
            import org.springframework.web.client.RestTemplate;
            @RestController
            public class CheckoutController {
                private final RestTemplate restTemplate = new RestTemplate();
                @PostMapping("/api/checkout")
                public String checkout(@RequestBody CheckoutRequest req) {
                    String result = restTemplate.postForObject(
                        "http://app-b/api/orders", req, String.class);
                    return result;
                }
            }
            """);
        createFile(appA, "com/appa/CheckoutRequest.java", """
            package com.appa;
            public class CheckoutRequest { private String product; }
            """);
        Files.createFile(appA.resolve("build.gradle"));

        // ── Scan both projects ──────────────────────────────────────────
        JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer();

        List<String> filesB = Files.walk(appB)
            .filter(p -> p.toString().endsWith(".java"))
            .map(p -> appB.relativize(p).toString()).toList();
        AnalyzeResult resultB = analyzer.analyze(AnalyzeTask.builder()
            .fetchedCode(FetchedCode.builder()
                .baseDir(appB.toString())
                .changedFiles(filesB)
                .appId("app-b")
                .build())
            .appId("app-b")
            .build());

        List<String> filesA = Files.walk(appA)
            .filter(p -> p.toString().endsWith(".java"))
            .map(p -> appA.relativize(p).toString()).toList();
        AnalyzeResult resultA = analyzer.analyze(AnalyzeTask.builder()
            .fetchedCode(FetchedCode.builder()
                .baseDir(appA.toString())
                .changedFiles(filesA)
                .appId("app-a")
                .build())
            .appId("app-a")
            .build());

        // ── Assert scan results ─────────────────────────────────────────
        assertThat(resultA.getRelations())
            .as("App-A must have at least one EXTERNAL call")
            .extracting(RawRelation::getCallType)
            .contains(CallType.EXTERNAL);

        List<MethodNode> appBEntries = resultB.getMethods().stream()
            .filter(MethodNode::isEntry).toList();
        assertThat(appBEntries)
            .as("App-B must have an entry method at /api/orders")
            .extracting(MethodNode::getHttpPath)
            .contains("/api/orders");

        // ── Write both apps to Neo4j ────────────────────────────────────
        writeToNeo4j(resultA, "app-a");
        writeToNeo4j(resultB, "app-b");

        // ── Resolve the external call ───────────────────────────────────
        RawRelation externalCall = resultA.getRelations().stream()
            .filter(r -> r.getCallType() == CallType.EXTERNAL)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No EXTERNAL relation found in App-A"));

        CallsEdge edge = CallsEdge.builder()
            .id("ext-1")
            .sourceMethodId(externalCall.getCaller().getMethodId())
            .targetMethodId(externalCall.getCallee().getMethodId())
            .callType(CallType.EXTERNAL)
            .callExpression(externalCall.getCallExpression())
            .build();

        ExternalCallResolver.ResolvedTarget resolved = externalResolver.resolve(edge);

        // ── Verify ──────────────────────────────────────────────────────
        assertThat(resolved)
            .as("External call from App-A must resolve to an entry method in App-B")
            .isNotNull();
        assertThat(resolved.targetAppId()).isEqualTo("app-b");
        assertThat(resolved.protocol()).isEqualTo("HTTP");
        assertThat(resolved.className()).isEqualTo("OrderController");

        System.out.println("== L2 cross-app resolved: "
            + resolved.protocol() + " "
            + resolved.targetAppId() + " -> "
            + resolved.className() + "." + resolved.signature());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void writeToNeo4j(AnalyzeResult result, String appId) {
        List<CallsEdge> edges = new ArrayList<>();
        int c = 0;
        for (RawRelation r : result.getRelations()) {
            edges.add(CallsEdge.builder()
                .id("c-" + appId + "-" + (c++))
                .sourceMethodId(r.getCaller().getMethodId())
                .targetMethodId(r.getCallee().getMethodId())
                .callType(r.getCallType())
                .callExpression(r.getCallExpression())
                .build());
        }
        SubGraph sg = SubGraph.builder()
            .methods(result.getMethods())
            .callsEdges(edges)
            .build();
        new Neo4jAdapter(methodRepo, neo4jDriver).write(sg);
    }

    private void createFile(Path projectDir, String relativePath, String content) throws Exception {
        Path filePath = projectDir.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
