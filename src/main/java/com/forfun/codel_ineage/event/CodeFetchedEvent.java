package com.forfun.codel_ineage.event;

import lombok.Getter;
import com.forfun.codel_ineage.analyzer.fetch.FetchedCode;

@Getter
public class CodeFetchedEvent extends LineageEvent {
    private final FetchedCode fetchedCode;

    public CodeFetchedEvent(String traceId, FetchedCode fetchedCode) {
        super(traceId);
        this.fetchedCode = fetchedCode;
    }
}
