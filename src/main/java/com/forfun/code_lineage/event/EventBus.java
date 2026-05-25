package com.forfun.code_lineage.event;

import java.util.List;
import java.util.function.Consumer;

public interface EventBus {
    void publish(LineageEvent event);
    <T extends LineageEvent> void subscribe(Class<T> eventType, Consumer<T> handler);
    List<LineageEvent> getEvents(String traceId);
}
