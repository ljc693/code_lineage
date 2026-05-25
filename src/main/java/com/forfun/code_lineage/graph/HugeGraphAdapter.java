package com.forfun.code_lineage.graph;

import com.forfun.code_lineage.model.*;
import com.forfun.code_lineage.model.graph.*;
import com.forfun.code_lineage.pathfinder.TraversalSpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * HugeGraph adapter stub — same GraphAdapter interface as Neo4jAdapter.
 * Activated when lineage.graph=hugegraph.
 * Full implementation delegated to V2+.
 */
@Component
@ConditionalOnProperty(name = "lineage.graph", havingValue = "hugegraph", matchIfMissing = false)
public class HugeGraphAdapter implements GraphAdapter {

    private final Map<String, MethodNode> methodStore = new LinkedHashMap<>();
    private final List<CallsEdge> callsStore = new ArrayList<>();
    private final List<AccessesEdge> accessesStore = new ArrayList<>();

    @Override
    public void write(SubGraph subGraph) {
        if (subGraph.getMethods() != null) {
            subGraph.getMethods().forEach(m -> methodStore.put(m.getMethodId(), m));
        }
        if (subGraph.getCallsEdges() != null) {
            callsStore.addAll(subGraph.getCallsEdges());
        }
        if (subGraph.getAccessesEdges() != null) {
            accessesStore.addAll(subGraph.getAccessesEdges());
        }
    }

    @Override
    public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
        // HugeGraph uses Gremlin, not Cypher. Stub for now.
        return List.of();
    }

    @Override
    public TraversalResult traversal(TraversalSpec spec) {
        MethodNode source = methodStore.get(spec.getStartMethodId());
        List<MethodNode> methods = source != null ? List.of(source) : List.of();

        List<CallsEdge> edges = callsStore.stream()
                .filter(e -> e.getSourceMethodId().equals(spec.getStartMethodId()))
                .toList();

        List<AccessesEdge> accesses = accessesStore.stream()
                .filter(e -> e.getSourceMethodId().equals(spec.getStartMethodId()))
                .toList();

        return new TraversalResult(methods, edges, accesses);
    }
}
