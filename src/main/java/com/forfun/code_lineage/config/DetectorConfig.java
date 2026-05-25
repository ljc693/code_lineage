package com.forfun.code_lineage.config;

import com.forfun.code_lineage.analyzer.detector.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class DetectorConfig {

    @Bean
    public CompositeEntryPointDetector entryPointDetector() {
        List<EntryPointDetector> detectors = List.of(
                new SpringMvcDetector(),
                new DubboDetector(),
                // ShenYu @RestApi — custom REST annotation
                new GenericAnnotationDetector("ShenYu", "HTTP",
                        Set.of("RestApi"), false),
                // gRPC @GrpcService
                new GenericAnnotationDetector("gRPC", "RPC",
                        Set.of("GrpcService"), true),
                // Kafka @KafkaListener
                new GenericAnnotationDetector("Kafka", "MQ",
                        Set.of("KafkaListener"), false)
        );
        return new CompositeEntryPointDetector(detectors);
    }
}
