package com.forfun.codel_ineage.analysis.report;

import java.util.List;
import java.util.Map;

public record LineageContext(
        String queryDescription,
        String targetTable,
        List<String> columns,
        List<Map<String, Object>> impactedMethods,
        List<Map<String, Object>> upstreamEndpoints,
        String callChainExample,
        Map<String, Object> deadColumnAnalysis,
        String findingsSummary
) {
    public static Builder builder() {
        return new Builder();
    }

    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Target Table: ").append(targetTable).append("\n\n");
        sb.append("### Columns\n");
        columns.forEach(c -> sb.append("- ").append(c).append("\n"));
        sb.append("\n### Impacted Methods\n");
        impactedMethods.forEach(m -> sb.append("- `")
                .append(m.get("className")).append(".")
                .append(m.get("signature")).append("` (")
                .append(m.getOrDefault("operation", "SELECT")).append(")\n"));
        if (!upstreamEndpoints.isEmpty()) {
            sb.append("\n### Upstream HTTP Endpoints\n");
            upstreamEndpoints.forEach(e -> sb.append("- ")
                    .append(e.get("httpMethod")).append(" ")
                    .append(e.get("httpPath")).append(" → ")
                    .append(e.get("className")).append("\n"));
        }
        if (findingsSummary != null && !findingsSummary.isBlank()) {
            sb.append("\n### Analysis Engine Findings\n");
            sb.append(findingsSummary).append("\n");
        }
        return sb.toString();
    }

    public static class Builder {
        private String queryDescription, targetTable, callChainExample;
        private List<String> columns = List.of();
        private List<Map<String, Object>> impactedMethods = List.of();
        private List<Map<String, Object>> upstreamEndpoints = List.of();
        private Map<String, Object> deadColumnAnalysis = Map.of();
        private String findingsSummary = "";

        public Builder queryDescription(String v) {
            queryDescription = v;
            return this;
        }

        public Builder targetTable(String v) {
            targetTable = v;
            return this;
        }

        public Builder columns(List<String> v) {
            columns = v;
            return this;
        }

        public Builder impactedMethods(List<Map<String, Object>> v) {
            impactedMethods = v;
            return this;
        }

        public Builder upstreamEndpoints(List<Map<String, Object>> v) {
            upstreamEndpoints = v;
            return this;
        }

        public Builder callChainExample(String v) {
            callChainExample = v;
            return this;
        }

        public Builder deadColumnAnalysis(Map<String, Object> v) {
            deadColumnAnalysis = v;
            return this;
        }

        public Builder findingsSummary(String v) {
            findingsSummary = v != null ? v : "";
            return this;
        }

        public LineageContext build() {
            return new LineageContext(queryDescription, targetTable, columns,
                    impactedMethods, upstreamEndpoints, callChainExample, deadColumnAnalysis,
                    findingsSummary);
        }
    }
}
