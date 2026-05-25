package com.forfun.code_lineage.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Kafka-based EventBus implementation.
 * Publishes events to Kafka topics (one per event type) and consumes them.
 * Activated when lineage.eventbus=kafka in application.properties.
 */
@Component
@ConditionalOnProperty(name = "lineage.eventbus", havingValue = "kafka", matchIfMissing = false)
public class KafkaEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventBus.class);
    private static final String TOPIC_PREFIX = "lineage.";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, List<LineageEvent>> traceEvents = new ConcurrentHashMap<>();

    public KafkaEventBus(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(LineageEvent event) {
        String topic = TOPIC_PREFIX + event.getClass().getSimpleName();
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.getTraceId(), json);
            traceEvents.computeIfAbsent(event.getTraceId(),
                    k -> new CopyOnWriteArrayList<>()).add(event);
            log.debug("Published {} to topic {}", event.getClass().getSimpleName(), topic);
        } catch (Exception e) {
            log.error("Failed to publish event to Kafka: {}", e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends LineageEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
        // Kafka consumers are configured via @KafkaListener in orchestrator
        // This method exists for API compatibility; V2 Kafka mode uses listener annotations
    }

    @Override
    public List<LineageEvent> getEvents(String traceId) {
        return traceEvents.getOrDefault(traceId, List.of());
    }
}
