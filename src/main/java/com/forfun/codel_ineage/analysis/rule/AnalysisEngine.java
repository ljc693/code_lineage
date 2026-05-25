package com.forfun.codel_ineage.analysis.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class AnalysisEngine {

    private static final Logger log = LoggerFactory.getLogger(AnalysisEngine.class);
    private final List<AnalysisRule> rules;
    private final AnalysisFindingsRepository findingsRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnalysisEngine(List<AnalysisRule> rules, AnalysisFindingsRepository findingsRepo) {
        this.rules = rules.stream()
                .sorted(Comparator.comparing(AnalysisRule::id))
                .toList();
        this.findingsRepo = findingsRepo;
    }

    public List<Finding> runAll(AnalysisRule.Target target) {
        List<Finding> all = new ArrayList<>();
        String appId = target.appId() != null ? target.appId() : "";
        for (AnalysisRule rule : rules) {
            for (Finding f : rule.analyze(target)) {
                try {
                    String evidenceJson = mapper.writeValueAsString(
                            Map.of("title", f.title(), "category", f.category()));
                    findingsRepo.save(appId, rule.id(), rule.name(),
                            f.severity(), f.category(), f.title(),
                            f.description(), f.suggestion(), evidenceJson);
                } catch (Exception e) {
                    log.warn("Failed to save finding for rule {}: {}", rule.id(), e.getMessage());
                }
                all.add(f);
            }
        }
        return all;
    }

    public List<Finding> runOne(String ruleId, AnalysisRule.Target target) {
        return rules.stream()
                .filter(r -> r.id().equals(ruleId))
                .findFirst()
                .map(r -> r.analyze(target))
                .orElse(List.of());
    }

    public List<AnalysisRule> listRules() { return rules; }
}
