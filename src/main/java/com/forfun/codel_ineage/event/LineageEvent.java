package com.forfun.codel_ineage.event;

import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class LineageEvent {
    private final String eventId;
    private final String traceId;
    private final Instant timestamp;

    protected LineageEvent(String traceId) {
        this.eventId = UUID.randomUUID().toString();
        this.traceId = traceId;
        this.timestamp = Instant.now();
    }
}
