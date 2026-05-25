package com.forfun.codel_ineage.event;

import lombok.Getter;

@Getter
public class ScanRequestedEvent extends LineageEvent {
    private final String appId;
    private final String scanType;
    private final String commitSha;

    public ScanRequestedEvent(String traceId, String appId, String scanType) {
        this(traceId, appId, scanType, null);
    }

    public ScanRequestedEvent(String traceId, String appId, String scanType, String commitSha) {
        super(traceId);
        this.appId = appId;
        this.scanType = scanType;
        this.commitSha = commitSha;
    }
}
