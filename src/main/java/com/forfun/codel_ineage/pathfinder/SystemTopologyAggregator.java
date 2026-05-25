package com.forfun.codel_ineage.pathfinder;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * L3: Aggregates cross-system CALLS edges into System → System topology.
 * Uses Cypher queries to avoid Neo4j entity relationship mapping issues.
 */
@Component
public class SystemTopologyAggregator {

    // Direct Neo4j driver access is needed for complex aggregations.
    // V1 uses a simplified approach: treat all apps as their own system,
    // aggregate EXTERNAL calls by app pairs.
    private final org.neo4j.driver.Driver neo4jDriver;

    public SystemTopologyAggregator(org.neo4j.driver.Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    public TraceResult buildSystemTopology() {
        long start = System.currentTimeMillis();

        List<LineageGraph.G6Node> nodes = new ArrayList<>();
        List<LineageGraph.G6Edge> edges = new ArrayList<>();
        Set<String> allApps = new LinkedHashSet<>();
        Map<String, Map<String, Integer>> crossAppCounts = new LinkedHashMap<>();

        try (var session = neo4jDriver.session()) {
            // Get all apps
            var appResult = session.run("MATCH (m:Method) WHERE m.appId IS NOT NULL " +
                    "RETURN DISTINCT m.appId AS appId");
            while (appResult.hasNext()) {
                allApps.add(appResult.next().get("appId").asString());
            }

            // Get EXTERNAL CALLS edges grouped by app pairs
            var edgeResult = session.run(
                    "MATCH (src:Method)-[c:CALLS]->(tgt:Method) " +
                    "WHERE c.callType = 'EXTERNAL' AND src.appId IS NOT NULL AND tgt.appId IS NOT NULL " +
                    "RETURN src.appId AS srcApp, tgt.appId AS tgtApp, count(c) AS cnt");
            while (edgeResult.hasNext()) {
                var record = edgeResult.next();
                String srcApp = record.get("srcApp").asString();
                String tgtApp = record.get("tgtApp").asString();
                int cnt = record.get("cnt").asInt();

                if (!srcApp.equals(tgtApp)) {
                    crossAppCounts.computeIfAbsent(srcApp, k -> new LinkedHashMap<>())
                            .merge(tgtApp, cnt, (a, b) -> a + b);
                }
            }
        }

        // Build System/App nodes
        for (String app : allApps) {
            nodes.add(LineageGraph.G6Node.builder()
                    .id(app).type("APP").label(app)
                    .style(Map.of("fill", "#722ed1", "stroke", "#531dab"))
                    .build());
        }

        // Build cross-app edges
        int edgeCounter = 0;
        for (var srcEntry : crossAppCounts.entrySet()) {
            for (var tgtEntry : srcEntry.getValue().entrySet()) {
                int count = tgtEntry.getValue();
                edges.add(LineageGraph.G6Edge.builder()
                        .id("xe-" + (edgeCounter++))
                        .source(srcEntry.getKey()).target(tgtEntry.getKey())
                        .type("CALLS").subType("CROSS_APP")
                        .style(Map.of("stroke", "#fa8c16", "lineWidth",
                                Math.min(5, 1 + count / 5)))
                        .build());
            }
        }

        long duration = System.currentTimeMillis() - start;

        return TraceResult.builder()
                .graph(LineageGraph.builder()
                        .nodes(nodes).edges(edges).combos(List.of())
                        .meta(Map.of("totalApps", allApps.size(),
                                "totalEdges", edges.size(), "level", "L3",
                                "queryTimeMs", duration))
                        .build())
                .paths(List.of())
                .durationMs(duration)
                .build();
    }
}
