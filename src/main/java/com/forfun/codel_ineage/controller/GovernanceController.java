package com.forfun.codel_ineage.controller;

import com.forfun.codel_ineage.analyzer.governance.GovernanceAnalyzer;
import com.forfun.codel_ineage.analyzer.governance.GovernanceMetrics;
import com.forfun.codel_ineage.controller.dto.LineageResponse;
import com.forfun.codel_ineage.fetcher.FetchedCode;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceController {

    private final GovernanceAnalyzer governanceAnalyzer;
    private final Map<String, GovernanceMetrics> storedMetrics = new ConcurrentHashMap<>();

    public GovernanceController(GovernanceAnalyzer governanceAnalyzer) {
        this.governanceAnalyzer = governanceAnalyzer;
    }

    @PostMapping("/analyze")
    public LineageResponse analyzeProject(@RequestBody Map<String, String> request) {
        String baseDir = request.get("baseDir");
        String appId = request.getOrDefault("appId", "unknown");

        GovernanceMetrics metrics = governanceAnalyzer.analyze(
                FetchedCode.builder()
                        .baseDir(baseDir)
                        .appId(appId)
                        .changedFiles(List.of())
                        .build());

        // Store for later retrieval
        storedMetrics.put(appId, metrics);

        return LineageResponse.builder()
                .success(true)
                .data(toMap(metrics))
                .build();
    }

    @GetMapping("/{appId}")
    public LineageResponse getStoredMetrics(@PathVariable String appId) {
        GovernanceMetrics metrics = storedMetrics.get(appId);
        if (metrics == null) {
            return LineageResponse.builder()
                    .success(false)
                    .error("No metrics found for app: " + appId + ". Run POST /analyze first.")
                    .build();
        }
        return LineageResponse.builder()
                .success(true)
                .data(toMap(metrics))
                .build();
    }

    @GetMapping("/summary")
    public LineageResponse getSummary() {
        if (storedMetrics.isEmpty()) {
            return LineageResponse.builder()
                    .success(true)
                    .data(Map.of("apps", 0, "message", "No governance data yet"))
                    .build();
        }

        int totalClasses = storedMetrics.values().stream().mapToInt(GovernanceMetrics::getClassCount).sum();
        int totalMethods = storedMetrics.values().stream().mapToInt(GovernanceMetrics::getMethodCount).sum();
        double avgComplexity = storedMetrics.values().stream()
                .mapToInt(GovernanceMetrics::getCyclomaticComplexity).average().orElse(0);
        double avgDuplication = storedMetrics.values().stream()
                .mapToDouble(GovernanceMetrics::getDuplicationRate).average().orElse(0);

        return LineageResponse.builder()
                .success(true)
                .data(Map.of(
                        "apps", storedMetrics.size(),
                        "totalClasses", totalClasses,
                        "totalMethods", totalMethods,
                        "avgCyclomaticComplexity", Math.round(avgComplexity * 10) / 10.0,
                        "avgDuplicationRate", Math.round(avgDuplication * 10) / 10.0
                ))
                .build();
    }

    private Map<String, Object> toMap(GovernanceMetrics m) {
        return Map.of(
                "appId", m.getAppId(),
                "cyclomaticComplexity", m.getCyclomaticComplexity(),
                "duplicationRate", m.getDuplicationRate(),
                "commentCoverage", m.getCommentCoverage(),
                "testCoverage", m.getTestCoverage(),
                "classCount", m.getClassCount(),
                "methodCount", m.getMethodCount()
        );
    }
}
