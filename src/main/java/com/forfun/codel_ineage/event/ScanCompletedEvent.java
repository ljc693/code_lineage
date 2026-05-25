package com.forfun.codel_ineage.event;

import lombok.Getter;
import java.util.Map;

@Getter
public class ScanCompletedEvent extends LineageEvent {
    private final String appId;
    private final Map<String, Object> stats;

    public ScanCompletedEvent(String traceId, String appId, Map<String, Object> stats) {
        super(traceId);
        this.appId = appId;
        this.stats = stats;
    }
}
