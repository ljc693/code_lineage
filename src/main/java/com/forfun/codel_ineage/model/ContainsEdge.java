package com.forfun.codel_ineage.model;

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
