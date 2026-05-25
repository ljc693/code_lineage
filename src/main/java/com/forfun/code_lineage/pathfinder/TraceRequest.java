package com.forfun.code_lineage.pathfinder;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TraceRequest {
    private String entryMethodId;
    private String targetMethodId; // optional: stop when reaching this method
    private int maxDepth;
    private String endNodeType;
}
