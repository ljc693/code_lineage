package com.forfun.codel_ineage.pathfinder;

public interface Pathfinder {
    TraceResult trace(TraceRequest request);
    TraceResult traceUpstream(String methodId, int maxDepth);
}
