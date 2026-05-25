package com.forfun.code_lineage.pathfinder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;

class G6GraphFormatterTest {

    private G6GraphFormatter formatter;
    private TraceResult result;

    @BeforeEach
    void setUp() {
        formatter = new G6GraphFormatter();

        LineageGraph.G6Node entry = LineageGraph.G6Node.builder()
                .id("app1:com.example.UserController.login(String,String)")
                .type("METHOD")
                .label("UserController.login")
                .comboId("com.example.UserController")
                .data(Map.of("signature", "login(String,String)",
                        "returnType", "Result", "appName", "app1",
                        "httpPath", "POST /api/login", "lineNumber", 42))
                .style(Map.of("fill", "#1890ff"))
                .build();

        LineageGraph.G6Node table = LineageGraph.G6Node.builder()
                .id("users")
                .type("TABLE")
                .label("users")
                .data(Map.of("operation", "SELECT"))
                .build();

        LineageGraph.G6Edge callEdge = LineageGraph.G6Edge.builder()
                .id("c-1")
                .source("app1:com.example.UserController.login(String,String)")
                .target("app1:com.example.UserService.authenticate(String,String)")
                .type("CALLS")
                .subType("INTERNAL")
                .style(Map.of("stroke", "#666", "lineWidth", 1))
                .build();

        LineageGraph.G6Edge accessEdge = LineageGraph.G6Edge.builder()
                .id("a-1")
                .source("app1:com.example.UserDao.findByName(String)")
                .target("users")
                .type("ACCESSES")
                .style(Map.of("stroke", "#52c41a"))
                .build();

        LineageGraph.G6Combo combo = LineageGraph.G6Combo.builder()
                .id("com.example.UserController")
                .type("CLASS")
                .label("UserController")
                .collapsed(false)
                .build();

        result = TraceResult.builder()
                .graph(LineageGraph.builder()
                        .nodes(List.of(entry, table))
                        .edges(List.of(callEdge, accessEdge))
                        .combos(List.of(combo))
                        .meta(Map.of("totalNodes", 2, "totalEdges", 2, "depth", 5, "queryTimeMs", 45L))
                        .build())
                .paths(List.of(
                        List.of("app1:com.example.UserController.login(String,String)",
                                "app1:com.example.UserService.authenticate(String,String)",
                                "app1:com.example.UserDao.findByName(String)", "users")))
                .durationMs(45L)
                .build();
    }

    @Test
    void shouldFormatAsG6() {
        Map<String, Object> g6 = formatter.format(result, "method");

        assertThat(g6).containsKeys("graph", "meta");

        @SuppressWarnings("unchecked")
        Map<String, Object> graph = (Map<String, Object>) g6.get("graph");
        assertThat(graph).containsKeys("nodes", "edges", "combos");

        @SuppressWarnings("unchecked")
        List<?> nodes = (List<?>) graph.get("nodes");
        assertThat(nodes).hasSize(2);

        @SuppressWarnings("unchecked")
        List<?> edges = (List<?>) graph.get("edges");
        assertThat(edges).hasSize(2);
    }

    @Test
    void shouldFormatAsTree() {
        Map<String, Object> tree = formatter.formatAsTree(result);

        assertThat(tree).containsKeys("root", "paths", "totalPaths");
        assertThat(tree.get("root")).isEqualTo(
                "app1:com.example.UserController.login(String,String)");
        assertThat(tree.get("totalPaths")).isEqualTo(1);
    }

    @Test
    void shouldFormatAsRaw() {
        Map<String, Object> raw = formatter.formatAsRaw(result);

        assertThat(raw).containsKeys("methods", "tables", "calls", "accesses", "paths");

        @SuppressWarnings("unchecked")
        List<?> methods = (List<?>) raw.get("methods");
        assertThat(methods).hasSize(1);

        @SuppressWarnings("unchecked")
        List<?> tables = (List<?>) raw.get("tables");
        assertThat(tables).hasSize(1);
    }

    @Test
    void shouldHideCombosForMethodView() {
        Map<String, Object> g6 = formatter.format(result, "method");

        @SuppressWarnings("unchecked")
        Map<String, Object> graph = (Map<String, Object>) g6.get("graph");

        @SuppressWarnings("unchecked")
        List<?> combos = (List<?>) graph.get("combos");
        assertThat(combos).isEmpty();
    }

    @Test
    void shouldShowCombosForClassView() {
        Map<String, Object> g6 = formatter.format(result, "class");

        @SuppressWarnings("unchecked")
        Map<String, Object> graph = (Map<String, Object>) g6.get("graph");

        @SuppressWarnings("unchecked")
        List<?> combos = (List<?>) graph.get("combos");
        assertThat(combos).hasSize(1);
    }

    @Test
    void shouldReturnEmptyTreeWhenNoPaths() {
        TraceResult emptyResult = TraceResult.builder()
                .graph(LineageGraph.builder()
                        .nodes(List.of())
                        .edges(List.of())
                        .combos(List.of())
                        .meta(Map.of())
                        .build())
                .paths(List.of())
                .durationMs(0)
                .build();

        Map<String, Object> tree = formatter.formatAsTree(emptyResult);
        assertThat(tree).containsEntry("totalPaths", 0);
    }
}
