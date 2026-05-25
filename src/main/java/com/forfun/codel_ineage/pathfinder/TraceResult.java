package com.forfun.codel_ineage.pathfinder;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TraceResult {
    private LineageGraph graph;
    private List<List<String>> paths;
    private long durationMs;
}
