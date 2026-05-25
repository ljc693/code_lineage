package com.forfun.code_lineage.pathfinder;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class G6GraphFormatter {

    public Map<String, Object> format(TraceResult result, String view) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", result.getGraph().getNodes());
        graph.put("edges", result.getGraph().getEdges());
        graph.put("combos", switch (view) {
            case "class", "service" -> result.getGraph().getCombos();
            default -> java.util.Collections.emptyList();
        });
        response.put("graph", graph);
        response.put("meta", result.getGraph().getMeta());
        return response;
    }

    public Map<String, Object> formatAsTree(TraceResult result) {
        Map<String, Object> tree = new LinkedHashMap<>();
        if (!result.getPaths().isEmpty()) {
            tree.put("root", result.getPaths().get(0).get(0));
            tree.put("paths", result.getPaths());
        }
        tree.put("totalPaths", result.getPaths().size());
        return tree;
    }

    public Map<String, Object> formatAsRaw(TraceResult result) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("methods", result.getGraph().getNodes().stream()
                .filter(n -> "METHOD".equals(n.getType()))
                .toList());
        raw.put("tables", result.getGraph().getNodes().stream()
                .filter(n -> "TABLE".equals(n.getType()))
                .toList());
        raw.put("calls", result.getGraph().getEdges().stream()
                .filter(e -> "CALLS".equals(e.getType()))
                .toList());
        raw.put("accesses", result.getGraph().getEdges().stream()
                .filter(e -> "ACCESSES".equals(e.getType()))
                .toList());
        raw.put("paths", result.getPaths());
        return raw;
    }
}
