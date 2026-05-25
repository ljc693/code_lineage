package com.forfun.codel_ineage.pathfinder;

import com.forfun.codel_ineage.graph.GraphAdapter;
import com.forfun.codel_ineage.graph.GraphAdapter.TraversalResult;
import com.forfun.codel_ineage.model.CallType;
import com.forfun.codel_ineage.model.CallsEdge;
import com.forfun.codel_ineage.model.MethodNode;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class L1Pathfinder implements Pathfinder {

    private final GraphAdapter graphAdapter;
    private final ExternalCallResolver externalResolver;

    public L1Pathfinder(GraphAdapter graphAdapter,
                        ExternalCallResolver externalResolver) {
        this.graphAdapter = graphAdapter;
        this.externalResolver = externalResolver;
    }

    @Override
    public TraceResult trace(TraceRequest request) {
        long start = System.currentTimeMillis();

        Set<String> visited = new LinkedHashSet<>();
        List<LineageGraph.G6Node> nodes = new ArrayList<>();
        List<LineageGraph.G6Edge> edges = new ArrayList<>();
        List<LineageGraph.G6Combo> combos = new ArrayList<>();
        List<List<String>> paths = new ArrayList<>();

        Deque<NodeWithDepth> queue = new ArrayDeque<>();
        queue.add(new NodeWithDepth(request.getEntryMethodId(), 0, new ArrayList<>()));

        int maxDepth = request.getMaxDepth() > 0 ? request.getMaxDepth() : 10;
        int crossAppHops = 0;

        while (!queue.isEmpty()) {
            NodeWithDepth current = queue.poll();
            String methodId = current.methodId;
            int depth = current.depth;
            List<String> currentPath = new ArrayList<>(current.path);
            currentPath.add(methodId);

            if (depth > maxDepth) continue;

            // Full-path mode: stop when target method is reached
            if (request.getTargetMethodId() != null
                    && methodId.equals(request.getTargetMethodId())) {
                paths.add(currentPath);
                continue;
            }

            if (visited.contains(methodId)) {
                paths.add(currentPath);
                continue;
            }
            visited.add(methodId);

            TraversalResult result = graphAdapter.traversal(
                    TraversalSpec.builder().startMethodId(methodId).maxDepth(1).build());

            for (MethodNode m : result.methods()) {
                if (m.getMethodId().equals(methodId)) {
                    nodes.add(toG6Node(m));
                    addComboIfAbsent(combos, m);
                }
            }

            for (CallsEdge ce : result.callsEdges()) {
                if (!ce.getSourceMethodId().equals(methodId)) continue;

                edges.add(LineageGraph.G6Edge.builder()
                        .id(ce.getId())
                        .source(ce.getSourceMethodId())
                        .target(ce.getTargetMethodId())
                        .type("CALLS")
                        .subType(ce.getCallType().name())
                        .style(externalStyle(ce.getCallType()))
                        .build());

                if (ce.getCallType() == CallType.INTERNAL) {
                    queue.add(new NodeWithDepth(ce.getTargetMethodId(), depth + 1, currentPath));
                } else if (externalResolver != null && crossAppHops < 3) {
                    var resolved = externalResolver.resolve(ce);
                    if (resolved != null) {
                        crossAppHops++;
                        nodes.add(LineageGraph.G6Node.builder()
                                .id(resolved.targetMethodId())
                                .type("METHOD")
                                .label("[" + resolved.targetAppId() + "] "
                                        + resolved.className() + "." + resolved.signature())
                                .comboId("app:" + resolved.targetAppId())
                                .data(Map.of("appId", resolved.targetAppId(),
                                        "protocol", resolved.protocol(),
                                        "resolvedFrom", ce.getCallExpression()))
                                .style(Map.of("fill", "#fa8c16", "stroke", "#d46b08"))
                                .build());
                        addAppCombo(combos, resolved.targetAppId());
                        queue.add(new NodeWithDepth(resolved.targetMethodId(), depth + 1, currentPath));
                    } else {
                        nodes.add(placeholderNode(ce));
                    }
                } else {
                    nodes.add(placeholderNode(ce));
                }
            }

            for (var ae : result.accessesEdges()) {
                if (ae.getSourceMethodId().equals(methodId)) {
                    nodes.add(LineageGraph.G6Node.builder()
                            .id(ae.getTargetTableId()).type("TABLE")
                            .label(ae.getTargetTableId())
                            .data(Map.of("operation", ae.getOperation().name()))
                            .build());
                    edges.add(LineageGraph.G6Edge.builder()
                            .id(ae.getId()).source(ae.getSourceMethodId())
                            .target(ae.getTargetTableId()).type("ACCESSES")
                            .style(Map.of("stroke", "#52c41a")).build());
                    List<String> fullPath = new ArrayList<>(currentPath);
                    fullPath.add(ae.getTargetTableId());
                    paths.add(fullPath);
                }
            }
        }

        long duration = System.currentTimeMillis() - start;

        return TraceResult.builder()
                .graph(LineageGraph.builder()
                        .nodes(nodes).edges(edges).combos(combos)
                        .meta(Map.of("totalNodes", nodes.size(), "totalEdges", edges.size(),
                                "depth", maxDepth, "crossAppHops", crossAppHops,
                                "queryTimeMs", duration))
                        .build())
                .paths(paths).durationMs(duration).build();
    }

    @Override
    public TraceResult traceUpstream(String methodId, int maxDepth) {
        return trace(TraceRequest.builder()
                .entryMethodId(methodId).maxDepth(maxDepth).endNodeType("METHOD").build());
    }

    private LineageGraph.G6Node toG6Node(MethodNode m) {
        String comboId = m.getAppId() != null ? "app:" + m.getAppId()
                : m.getPackageName() + "." + m.getClassName();
        return LineageGraph.G6Node.builder()
                .id(m.getMethodId()).type("METHOD")
                .label(m.getClassName() + "." + m.getSignature())
                .comboId(comboId)
                .data(Map.of("signature", m.getSignature(),
                        "returnType", m.getReturnType() != null ? m.getReturnType() : "",
                        "appId", m.getAppId() != null ? m.getAppId() : "",
                        "httpPath", m.getHttpPath() != null ? m.getHttpPath() : "",
                        "lineNumber", m.getLineNumber()))
                .style(Map.of("fill", m.isEntry() ? "#1890ff" : "#91d5ff"))
                .build();
    }

    private LineageGraph.G6Node placeholderNode(CallsEdge ce) {
        return LineageGraph.G6Node.builder()
                .id(ce.getTargetMethodId()).type("METHOD")
                .label("unresolved:" + ce.getTargetMethodId())
                .data(Map.of("unresolved", true))
                .style(Map.of("fill", "#ddd", "stroke", "#999", "strokeDasharray", "5,5"))
                .build();
    }

    private Map<String, Object> externalStyle(CallType type) {
        return type == CallType.EXTERNAL
                ? Map.of("stroke", "#fa8c16", "lineWidth", 2, "strokeDasharray", List.of(8, 4))
                : Map.of("stroke", "#666", "lineWidth", 1);
    }

    private void addComboIfAbsent(List<LineageGraph.G6Combo> combos, MethodNode m) {
        String comboId = m.getPackageName() + "." + m.getClassName();
        if (combos.stream().noneMatch(c -> c.getId().equals(comboId))) {
            combos.add(LineageGraph.G6Combo.builder()
                    .id(comboId).type("CLASS").label(m.getClassName()).collapsed(false).build());
        }
    }

    private void addAppCombo(List<LineageGraph.G6Combo> combos, String appId) {
        String id = "app:" + appId;
        if (combos.stream().noneMatch(c -> c.getId().equals(id))) {
            combos.add(LineageGraph.G6Combo.builder()
                    .id(id).type("APP").label(appId).collapsed(false).build());
        }
    }

    private record NodeWithDepth(String methodId, int depth, List<String> path) {}
}
