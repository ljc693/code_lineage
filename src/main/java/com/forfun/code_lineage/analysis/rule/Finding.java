package com.forfun.code_lineage.analysis.rule;

import java.util.Map;

public record Finding(
        String ruleId,
        String severity,
        String category,
        String title,
        String description,
        String suggestion,
        Map<String, Object> evidence
) {}
