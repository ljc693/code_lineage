package com.forfun.codel_ineage.event;

import lombok.Getter;
import com.forfun.codel_ineage.analyzer.AnalyzeResult;

@Getter
public class CodeAnalyzedEvent extends LineageEvent {
    private final AnalyzeResult result;

    public CodeAnalyzedEvent(String traceId, AnalyzeResult result) {
        super(traceId);
        this.result = result;
    }
}
