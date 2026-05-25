package com.forfun.code_lineage.event;

import lombok.Getter;
import com.forfun.code_lineage.analyzer.fetch.FetchedCode;

@Getter
public class CodeFetchedEvent extends LineageEvent {
    private final FetchedCode fetchedCode;

    public CodeFetchedEvent(String traceId, FetchedCode fetchedCode) {
        super(traceId);
        this.fetchedCode = fetchedCode;
    }
}
