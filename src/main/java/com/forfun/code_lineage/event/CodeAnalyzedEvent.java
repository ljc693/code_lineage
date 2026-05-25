package com.forfun.code_lineage.event;

import lombok.Getter;
import com.forfun.code_lineage.analyzer.AnalyzeResult;

@Getter
public class CodeAnalyzedEvent extends LineageEvent {
    private final AnalyzeResult result;

    public CodeAnalyzedEvent(String traceId, AnalyzeResult result) {
        super(traceId);
        this.result = result;
    }
}
