package com.forfun.codel_ineage.graph;

import com.forfun.codel_ineage.model.*;
import com.forfun.codel_ineage.model.graph.*;
import com.forfun.codel_ineage.pathfinder.TraversalSpec;
import java.util.List;
import java.util.Map;

public interface GraphAdapter {
    void write(SubGraph subGraph);
    List<Map<String, Object>> query(String cypher, Map<String, Object> params);
    TraversalResult traversal(TraversalSpec spec);

    record TraversalResult(List<MethodNode> methods, List<CallsEdge> callsEdges,
                           List<AccessesEdge> accessesEdges) {}
}
