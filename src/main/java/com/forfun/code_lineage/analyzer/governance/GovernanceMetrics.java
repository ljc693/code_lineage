package com.forfun.code_lineage.analyzer.governance;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GovernanceMetrics {
    private String appId;
    private int cyclomaticComplexity;
    private double duplicationRate;
    private double commentCoverage;
    private double testCoverage;
    private int classCount;
    private int methodCount;
}
