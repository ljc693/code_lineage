package com.forfun.codel_ineage.analysis.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisExporterTest {

    private final AnalysisExporter exporter = new AnalysisExporter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exportForLLMProducesValidJsonWithAllFields() throws Exception {
        var context = LineageContext.builder()
                .targetTable("crawler_task")
                .columns(List.of("id", "name", "status"))
                .impactedMethods(List.of(
                        Map.of("className", "TaskService", "signature", "findById()", "operation", "SELECT")
                ))
                .upstreamEndpoints(List.of(
                        Map.of("httpMethod", "GET", "httpPath", "/api/tasks", "className", "TaskController")
                ))
                .deadColumnAnalysis(Map.of("days", 180))
                .build();

        var template = PromptTemplate.builtin("dead_column_analysis");

        String json = exporter.exportForLLM(context, template);

        Map<String, Object> parsed = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});

        assertThat(parsed).containsKey("template_name");
        assertThat(parsed).containsKey("target_table");
        assertThat(parsed).containsKey("columns");
        assertThat(parsed).containsKey("impacted_methods");
        assertThat(parsed).containsKey("upstream_endpoints");
        assertThat(parsed).containsKey("prompt");
        assertThat(parsed).containsKey("exported_at");

        assertThat(parsed.get("template_name")).isEqualTo("dead_column_analysis");
        assertThat(parsed.get("target_table")).isEqualTo("crawler_task");
    }

    @Test
    void toJsonConvertsObjectToJsonString() {
        Map<String, Object> data = Map.of("key", "value", "count", 42);
        String json = exporter.toJson(data);

        assertThat(json).isNotNull();
        assertThat(json).contains("key");
        assertThat(json).contains("count");
        assertThat(json).contains("value");
        assertThat(json).contains("42");
    }
}
