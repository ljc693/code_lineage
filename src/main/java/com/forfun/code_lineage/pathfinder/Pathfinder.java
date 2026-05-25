package com.forfun.code_lineage.pathfinder;

public interface Pathfinder {
    TraceResult trace(TraceRequest request);
    TraceResult traceUpstream(String methodId, int maxDepth);
}
