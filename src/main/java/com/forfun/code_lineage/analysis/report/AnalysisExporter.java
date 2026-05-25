package com.forfun.code_lineage.analysis.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class AnalysisExporter {

    private final ObjectMapper mapper;

    public AnalysisExporter() {
        this.mapper = new ObjectMapper();
    }

    public String exportForLLM(LineageContext context, PromptTemplate template) {
        Map<String, String> variables = new HashMap<>();
        variables.put("table", context.targetTable());
        variables.put("columns", String.join(", ", context.columns()));
        variables.put("methods", String.valueOf(context.impactedMethods().size()));
        variables.put("endpoints", String.valueOf(context.upstreamEndpoints().size()));
        variables.put("call_chain", context.callChainExample() != null ? context.callChainExample() : "N/A");
        variables.put("dead_columns", String.valueOf(context.deadColumnAnalysis()));
        variables.put("findings", context.findingsSummary() != null ? context.findingsSummary() : "");

        String renderedPrompt = template.render(variables);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("template_name", template.name());
        payload.put("target_table", context.targetTable());
        payload.put("columns", context.columns());
        payload.put("impacted_methods", context.impactedMethods());
        payload.put("upstream_endpoints", context.upstreamEndpoints());
        payload.put("prompt", renderedPrompt);
        payload.put("exported_at", LocalDateTime.now().toString());

        return toJson(payload);
    }

    public String toJson(Object obj) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
