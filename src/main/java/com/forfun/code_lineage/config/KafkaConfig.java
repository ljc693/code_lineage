package com.forfun.code_lineage.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "lineage.eventbus", havingValue = "kafka", matchIfMissing = false)
public class KafkaConfig {

    @Bean
    public NewTopic scanRequestedTopic() {
        return TopicBuilder.name("lineage.ScanRequestedEvent").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic codeFetchedTopic() {
        return TopicBuilder.name("lineage.CodeFetchedEvent").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic codeAnalyzedTopic() {
        return TopicBuilder.name("lineage.CodeAnalyzedEvent").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic sqlParsedTopic() {
        return TopicBuilder.name("lineage.SqlParsedEvent").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic scanCompletedTopic() {
        return TopicBuilder.name("lineage.ScanCompletedEvent").partitions(3).replicas(1).build();
    }
}
