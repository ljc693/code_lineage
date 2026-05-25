package com.forfun.code_lineage.controller;

import com.forfun.code_lineage.controller.dto.LineageResponse;
import com.forfun.code_lineage.graph.Neo4jMethodRepository;
import org.neo4j.driver.Driver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final Driver neo4jDriver;
    private final Neo4jMethodRepository methodRepo;

    public HealthController(Driver neo4jDriver, Neo4jMethodRepository methodRepo) {
        this.neo4jDriver = neo4jDriver;
        this.methodRepo = methodRepo;
    }

    @GetMapping("/api/v1/health")
    public LineageResponse health() {
        boolean neo4jOk = false;
        long methodCount = 0;
        try (var session = neo4jDriver.session()) {
            var result = session.run("RETURN 1 AS ok");
            neo4jOk = result.hasNext();
            methodCount = methodRepo.count();
        } catch (Exception e) {
            neo4jOk = false;
        }

        Map<String, Object> health = Map.of(
                "status", neo4jOk ? "UP" : "DEGRADED",
                "neo4j", neo4jOk ? "connected" : "disconnected",
                "methods", methodCount
        );

        return LineageResponse.builder()
                .success(neo4jOk)
                .data(health)
                .error(neo4jOk ? null : "Neo4j connection failed")
                .build();
    }
}
