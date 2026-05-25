package com.forfun.code_lineage.pathfinder;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TraversalSpec {
    private String startMethodId;
    private int maxDepth;
    private String direction;
}
