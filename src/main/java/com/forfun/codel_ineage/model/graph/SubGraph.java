package com.forfun.codel_ineage.model.graph;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SubGraph {
    private List<MethodNode> methods;
    private List<TableNode> tables;
    private List<ColumnNode> columns;
    private List<ClassNode> classes;
    private List<AppNode> apps;
    private List<CallsEdge> callsEdges;
    private List<AccessesEdge> accessesEdges;
    private List<ContainsEdge> containsEdges;
}
