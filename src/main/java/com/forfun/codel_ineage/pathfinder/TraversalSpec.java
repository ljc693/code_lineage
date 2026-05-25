package com.forfun.codel_ineage.pathfinder;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TraversalSpec {
    private String startMethodId;
    private int maxDepth;
    private String direction;
}
