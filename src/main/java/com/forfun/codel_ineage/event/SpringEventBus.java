package com.forfun.codel_ineage.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class SpringEventBus implements EventBus {

    private final ApplicationEventPublisher publisher;
    private final Map<String, List<LineageEvent>> traceEvents = new ConcurrentHashMap<>();

    public SpringEventBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(LineageEvent event) {
        traceEvents.computeIfAbsent(event.getTraceId(), k -> new CopyOnWriteArrayList<>()).add(event);
        publisher.publishEvent(event);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends LineageEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
    }

    @Override
    public List<LineageEvent> getEvents(String traceId) {
        return traceEvents.getOrDefault(traceId, List.of());
    }
}
