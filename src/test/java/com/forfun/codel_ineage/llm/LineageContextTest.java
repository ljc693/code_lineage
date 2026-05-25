package com.forfun.codel_ineage.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LineageContextTest {

    @Test
    void toPromptContextRendersTargetTableSection() {
        var ctx = LineageContext.builder()
                .targetTable("crawler_task")
                .columns(List.of("id", "name", "status"))
                .impactedMethods(List.of(
                        Map.of("className", "TaskService", "signature", "findById()", "operation", "SELECT"),
                        Map.of("className", "TaskRepository", "signature", "updateStatus()", "operation", "UPDATE")
                ))
                .build();

        String result = ctx.toPromptContext();

        assertThat(result).contains("## Target Table: crawler_task");
        assertThat(result).contains("### Columns");
        assertThat(result).contains("- id");
        assertThat(result).contains("- name");
        assertThat(result).contains("- status");
        assertThat(result).contains("### Impacted Methods");
        assertThat(result).contains("`TaskService.findById()`");
        assertThat(result).contains("`TaskRepository.updateStatus()`");
        assertThat(result).contains("(SELECT)");
        assertThat(result).contains("(UPDATE)");
    }

    @Test
    void toPromptContextRendersUpstreamEndpointsWhenPresent() {
        var ctx = LineageContext.builder()
                .targetTable("crawler_task")
                .columns(List.of("id"))
                .impactedMethods(List.of(
                        Map.of("className", "TaskService", "signature", "findById()", "operation", "SELECT")
                ))
                .upstreamEndpoints(List.of(
                        Map.of("httpMethod", "GET", "httpPath", "/api/tasks/{id}", "className", "TaskController"),
                        Map.of("httpMethod", "POST", "httpPath", "/api/tasks", "className", "TaskController")
                ))
                .build();

        String result = ctx.toPromptContext();

        assertThat(result).contains("### Upstream HTTP Endpoints");
        assertThat(result).contains("GET /api/tasks/{id} → TaskController");
        assertThat(result).contains("POST /api/tasks → TaskController");
    }

    @Test
    void toPromptContextOmitsUpstreamSectionWhenEmpty() {
        var ctx = LineageContext.builder()
                .targetTable("crawler_task")
                .columns(List.of("id"))
                .impactedMethods(List.of(
                        Map.of("className", "TaskService", "signature", "findById()", "operation", "SELECT")
                ))
                .build();

        String result = ctx.toPromptContext();

        assertThat(result).doesNotContain("Upstream HTTP Endpoints");
    }

    @Test
    void builderSetsAllFieldsCorrectly() {
        var deadColAnalysis = Map.of(
                "deadColumns", List.of("backup_config"),
                "risk", "medium"
        );

        var ctx = LineageContext.builder()
                .queryDescription("Test query")
                .targetTable("test_table")
                .columns(List.of("col1", "col2"))
                .impactedMethods(List.of(Map.of("className", "A", "signature", "m()", "operation", "SEL")))
                .upstreamEndpoints(List.of(Map.of("httpMethod", "GET", "httpPath", "/test", "className", "C")))
                .callChainExample("A.m() -> B.n()")
                .deadColumnAnalysis(deadColAnalysis)
                .build();

        assertThat(ctx.queryDescription()).isEqualTo("Test query");
        assertThat(ctx.targetTable()).isEqualTo("test_table");
        assertThat(ctx.columns()).containsExactly("col1", "col2");
        assertThat(ctx.impactedMethods()).hasSize(1);
        assertThat(ctx.upstreamEndpoints()).hasSize(1);
        assertThat(ctx.callChainExample()).isEqualTo("A.m() -> B.n()");
        assertThat(ctx.deadColumnAnalysis()).containsKey("deadColumns");
    }

    @Test
    void builderDefaultsToEmptyCollections() {
        var ctx = LineageContext.builder()
                .targetTable("t")
                .columns(List.of("c"))
                .impactedMethods(List.of(Map.of("className", "C", "signature", "m()", "operation", "SEL")))
                .build();

        assertThat(ctx.upstreamEndpoints()).isEmpty();
        assertThat(ctx.queryDescription()).isNull();
        assertThat(ctx.callChainExample()).isNull();
        assertThat(ctx.deadColumnAnalysis()).isEmpty();
    }
}
