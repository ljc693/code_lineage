package com.forfun.code_lineage.graph;

import com.forfun.code_lineage.model.*;
import com.forfun.code_lineage.model.graph.*;
import com.forfun.code_lineage.pathfinder.TraversalSpec;
import java.util.List;
import java.util.Map;

public interface GraphAdapter {
    void write(SubGraph subGraph);
    List<Map<String, Object>> query(String cypher, Map<String, Object> params);
    TraversalResult traversal(TraversalSpec spec);

    record TraversalResult(List<MethodNode> methods, List<CallsEdge> callsEdges,
                           List<AccessesEdge> accessesEdges) {}
}
