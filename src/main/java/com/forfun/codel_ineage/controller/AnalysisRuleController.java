package com.forfun.codel_ineage.controller;

import com.forfun.codel_ineage.analysis.rule.AnalysisEngine;
import com.forfun.codel_ineage.analysis.rule.AnalysisFindingsRepository;
import com.forfun.codel_ineage.analysis.rule.AnalysisRule;
import com.forfun.codel_ineage.controller.dto.LineageResponse;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisRuleController {

    private final AnalysisEngine engine;
    private final AnalysisFindingsRepository findingsRepo;

    public AnalysisRuleController(AnalysisEngine engine, AnalysisFindingsRepository findingsRepo) {
        this.engine = engine;
        this.findingsRepo = findingsRepo;
    }

    @PostMapping("/rules/run")
    public LineageResponse runRules(@RequestBody Map<String, Object> body) {
        String appId = (String) body.getOrDefault("appId", "");
        @SuppressWarnings("unchecked")
        List<String> ruleIds = (List<String>) body.getOrDefault("ruleIds", List.of());

        var target = AnalysisRule.Target.app(appId);
        List<?> findings = ruleIds.isEmpty()
                ? engine.runAll(target)
                : engine.runOne(ruleIds.get(0), target); // single rule support for now
        return LineageResponse.builder().success(true)
                .data(Map.of("findings", findings, "count", findings.size())).build();
    }

    @GetMapping("/rules")
    public LineageResponse listRules() {
        var rules = engine.listRules().stream()
                .map(r -> Map.of("id", r.id(), "name", r.name(),
                        "severity", r.severity(), "category", r.category()))
                .toList();
        return LineageResponse.builder().success(true)
                .data(Map.of("rules", rules)).build();
    }

    @GetMapping("/findings")
    public LineageResponse getFindings(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String ruleId,
            @RequestParam(defaultValue = "20") int limit) {
        var results = ruleId != null
                ? findingsRepo.findByRule(ruleId, limit)
                : findingsRepo.findByAppId(appId != null ? appId : "%", limit);
        return LineageResponse.builder().success(true)
                .data(Map.of("findings", results)).build();
    }
}
