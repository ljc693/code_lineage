package com.forfun.codel_ineage.controller;

import com.forfun.codel_ineage.analysis.rule.AnalysisFindingsRepository;
import com.forfun.codel_ineage.controller.dto.LineageResponse;
import com.forfun.codel_ineage.analysis.report.AnalysisExporter;
import com.forfun.codel_ineage.analysis.report.LineageContext;
import com.forfun.codel_ineage.analysis.report.LineageContextBuilder;
import com.forfun.codel_ineage.analysis.report.LlmReportRepository;
import com.forfun.codel_ineage.analysis.report.PromptTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/llm")
public class LlmReportController {

    private final LineageContextBuilder contextBuilder;
    private final AnalysisExporter exporter;
    private final LlmReportRepository reportRepo;
    private final AnalysisFindingsRepository findingsRepo;

    public LlmReportController(LineageContextBuilder contextBuilder,
                               AnalysisExporter exporter,
                               LlmReportRepository reportRepo,
                               AnalysisFindingsRepository findingsRepo) {
        this.contextBuilder = contextBuilder;
        this.exporter = exporter;
        this.reportRepo = reportRepo;
        this.findingsRepo = findingsRepo;
    }

    @PostMapping("/report/{type}")
    public LineageResponse generateReport(@PathVariable String type,
                                          @RequestBody Map<String, Object> body) {
        String tableName = (String) body.get("tableName");
        String columnName = (String) body.get("columnName");
        int days = body.containsKey("days") ? ((Number) body.get("days")).intValue() : 90;
        boolean includeFindings = Boolean.TRUE.equals(body.get("includeFindings"));

        // Map URL-friendly type to template name
        Map<String, String> typeToTemplate = Map.of(
                "dead-columns", "dead_column_analysis",
                "field-impact", "field_impact_analysis",
                "table-governance", "table_governance");
        String templateName = typeToTemplate.getOrDefault(type, type.replace('-', '_'));
        PromptTemplate template = PromptTemplate.builtin(templateName);

        // Build findings summary from analysis engine results
        String findingsSummary = "";
        if (includeFindings) {
            var findings = findingsRepo.findByAppId("clawer", 50);
            findingsSummary = findings.stream()
                    .filter(f -> f.get("title") != null)
                    .map(f -> "- [" + f.get("severity") + "] [" + f.get("rule_id") + "] " + f.get("title"))
                    .limit(15)
                    .collect(Collectors.joining("\n"));
        }

        LineageContext context;
        if (templateName.contains("dead_column")) {
            context = contextBuilder.buildDeadColumnContext(tableName, columnName, days, false);
        } else {
            context = contextBuilder.buildFieldImpactContext(tableName, columnName, false);
        }

        // Inject findings summary into context if available
        if (!findingsSummary.isBlank()) {
            context = LineageContext.builder()
                    .queryDescription(context.queryDescription())
                    .targetTable(context.targetTable())
                    .columns(context.columns())
                    .impactedMethods(context.impactedMethods())
                    .upstreamEndpoints(context.upstreamEndpoints())
                    .callChainExample(context.callChainExample())
                    .deadColumnAnalysis(context.deadColumnAnalysis())
                    .findingsSummary(findingsSummary)
                    .build();
        }

        String json = exporter.exportForLLM(context, template);
        reportRepo.save(templateName, null, tableName, columnName, json, null, null, 0.0);

        return LineageResponse.builder()
                .success(true)
                .data(Map.of("template", templateName, "prompt", json))
                .build();
    }

    @GetMapping("/reports")
    public LineageResponse getReports(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String appId,
            @RequestParam(defaultValue = "20") int limit) {

        List<Map<String, Object>> results;
        if (type != null && !type.isEmpty()) {
            results = reportRepo.findByType(type, limit);
        } else if (appId != null && !appId.isEmpty()) {
            results = reportRepo.findByAppId(appId, limit);
        } else {
            results = List.of();
        }

        return LineageResponse.builder()
                .success(true)
                .data(Map.of("reports", results, "count", results.size()))
                .build();
    }
}
