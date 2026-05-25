package com.forfun.codel_ineage.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.atomic.AtomicReference;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SpringEventBusTest.TestConfig.class)
class SpringEventBusTest {

    @Configuration
    static class TestConfig {
        @Bean
        public EventBus eventBus(ApplicationEventPublisher publisher) {
            return new SpringEventBus(publisher);
        }
    }

    @Autowired
    private EventBus eventBus;

    @Test
    void shouldPublishAndReceiveEvent() {
        AtomicReference<ScanRequestedEvent> received = new AtomicReference<>();

        eventBus.subscribe(ScanRequestedEvent.class, event -> {
            received.set((ScanRequestedEvent) event);
        });

        ScanRequestedEvent sent = new ScanRequestedEvent("trace-1", "app-1", "FULL");
        eventBus.publish(sent);

        assertThat(received.get()).isNull();
    }
}
