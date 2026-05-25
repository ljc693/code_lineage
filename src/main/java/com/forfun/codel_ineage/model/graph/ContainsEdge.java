package com.forfun.codel_ineage.model.graph;

import com.forfun.codel_ineage.model.NodeType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContainsEdge {
    private String id;
    private String parentId;
    private String childId;
    private NodeType parentType;
    private NodeType childType;
}
