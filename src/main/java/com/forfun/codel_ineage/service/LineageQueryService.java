package com.forfun.codel_ineage.service;

import com.forfun.codel_ineage.graph.Neo4jMethodRepository;
import com.forfun.codel_ineage.pathfinder.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LineageQueryService {

    private final Pathfinder pathfinder;
    private final G6GraphFormatter formatter;
    private final Neo4jMethodRepository methodRepo;
    private final SystemTopologyAggregator topologyAggregator;

    public LineageQueryService(Pathfinder pathfinder, G6GraphFormatter formatter,
                               Neo4jMethodRepository methodRepo,
                               SystemTopologyAggregator topologyAggregator) {
        this.pathfinder = pathfinder;
        this.formatter = formatter;
        this.methodRepo = methodRepo;
        this.topologyAggregator = topologyAggregator;
    }

    public Map<String, Object> getDownstreamLineage(String methodId, int depth, String format, String view) {
        TraceResult result = pathfinder.trace(TraceRequest.builder()
                .entryMethodId(methodId)
                .maxDepth(depth)
                .endNodeType("TABLE")
                .build());

        return switch (format) {
            case "tree" -> formatter.formatAsTree(result);
            case "raw" -> formatter.formatAsRaw(result);
            default -> formatter.format(result, view);
        };
    }

    public Map<String, Object> getUpstreamLineage(String methodId, int depth, String format) {
        TraceResult result = pathfinder.traceUpstream(methodId, depth);
        return switch (format) {
            case "raw" -> formatter.formatAsRaw(result);
            default -> formatter.format(result, "method");
        };
    }

    public Map<String, Object> getFullPath(String fromId, String toId, int depth, String format) {
        TraceResult result = pathfinder.trace(TraceRequest.builder()
                .entryMethodId(fromId)
                .targetMethodId(toId)
                .maxDepth(depth)
                .endNodeType("METHOD")
                .build());
        return switch (format) {
            case "tree" -> formatter.formatAsTree(result);
            case "raw" -> formatter.formatAsRaw(result);
            default -> formatter.format(result, "method");
        };
    }

    public List<Map<String, Object>> getEntryPoints(String appId, String type) {
        return methodRepo.findEntryMethods(appId).stream()
                .map(m -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("methodId", m.getMethodId());
                    entry.put("signature", m.getSignature());
                    entry.put("httpPath", m.getHttpPath());
                    entry.put("httpMethod", m.getHttpMethod());
                    entry.put("className", m.getClassName());
                    return entry;
                })
                .toList();
    }

    public Map<String, Object> getImpact(List<String> changedMethods, int depth, String format) {
        List<Map<String, Object>> impacts = new ArrayList<>();
        for (String methodId : changedMethods) {
            Map<String, Object> upstream = getUpstreamLineage(methodId, depth, format);
            impacts.add(Map.of("changedMethod", methodId, "upstream", upstream));
        }
        return Map.of("impacted", impacts, "changedCount", changedMethods.size());
    }

    /**
     * L3: System-level topology — aggregates all cross-system CALLS edges.
     */
    public Map<String, Object> getSystemTopology(String format) {
        TraceResult result = topologyAggregator.buildSystemTopology();
        return switch (format) {
            case "raw" -> formatter.formatAsRaw(result);
            default -> formatter.format(result, "system");
        };
    }
}
