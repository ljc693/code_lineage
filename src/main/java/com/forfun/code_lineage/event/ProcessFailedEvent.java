package com.forfun.code_lineage.event;

import lombok.Getter;

@Getter
public class ProcessFailedEvent extends LineageEvent {
    private final String processorType;
    private final String error;

    public ProcessFailedEvent(String traceId, String processorType, String error) {
        super(traceId);
        this.processorType = processorType;
        this.error = error;
    }
}
