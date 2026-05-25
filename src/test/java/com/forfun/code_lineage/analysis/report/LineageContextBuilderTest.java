package com.forfun.code_lineage.analysis.report;

import com.forfun.code_lineage.analysis.rule.AnalysisFindingsRepository;
import com.forfun.code_lineage.graph.ColumnLineageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.neo4j.driver.Record;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LineageContextBuilderTest {

    private Driver neo4jDriver;
    private Session session;
    private ColumnLineageRepository columnRepo;
    private AnalysisFindingsRepository findingsRepo;
    private LineageContextBuilder builder;

    @BeforeEach
    void setUp() {
        neo4jDriver = mock(Driver.class);
        session = mock(Session.class);
        when(neo4jDriver.session()).thenReturn(session);
        columnRepo = mock(ColumnLineageRepository.class);
        findingsRepo = mock(AnalysisFindingsRepository.class);
        builder = new LineageContextBuilder(neo4jDriver, columnRepo, findingsRepo);
    }

    @SuppressWarnings("unchecked")
    @Test
    void buildDeadColumnContextQueriesMySQLAndBuildsCorrectContext() {
        var methods = List.of(Map.<String, Object>of(
                "method_id", "clawer:com.clawer.TaskService.process()",
                "method_signature", "process()",
                "class_name", "TaskService",
                "app_id", "clawer",
                "operation", "UPDATE",
                "access_count", 10
        ));
        var columns = List.of("errorMessage", "id", "name", "status");

        when(columnRepo.findImpactedMethods("crawler_task", "errorMessage")).thenReturn(methods);
        when(columnRepo.findColumnsByTable("crawler_task")).thenReturn(columns);

        // Mock Neo4j upstream endpoint result
        Result neo4jResult = mock(Result.class);
        when(session.run(anyString(), any(Map.class))).thenReturn(neo4jResult);

        Record rec = mock(Record.class);
        when(rec.get("id")).thenReturn(Values.value("ep1"));
        when(rec.get("sig")).thenReturn(Values.value("processTask()"));
        when(rec.get("cls")).thenReturn(Values.value("TaskController"));
        when(rec.get("path")).thenReturn(Values.value("/api/tasks/process"));
        when(rec.get("method")).thenReturn(Values.value("POST"));
        when(rec.get("app")).thenReturn(Values.value("clawer"));

        when(neo4jResult.hasNext()).thenReturn(true, false);
        when(neo4jResult.next()).thenReturn(rec);

        LineageContext ctx = builder.buildDeadColumnContext("crawler_task", "errorMessage", 180, false, "clawer");

        assertThat(ctx.queryDescription()).contains("Dead column analysis");
        assertThat(ctx.targetTable()).isEqualTo("crawler_task");
        assertThat(ctx.columns()).containsExactly("errorMessage", "id", "name", "status");
        assertThat(ctx.impactedMethods()).hasSize(1);
        assertThat(ctx.impactedMethods().get(0)).containsEntry("class_name", "TaskService");
        assertThat(ctx.upstreamEndpoints()).hasSize(1);
        assertThat(ctx.upstreamEndpoints().get(0)).containsEntry("httpMethod", "POST");
        assertThat(ctx.upstreamEndpoints().get(0)).containsEntry("httpPath", "/api/tasks/process");
        assertThat(ctx.callChainExample()).isEqualTo("N/A");
        assertThat(ctx.deadColumnAnalysis()).containsEntry("days", 180);
        assertThat(ctx.findingsSummary()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void buildDeadColumnContextWithNoUpstreamEndpoints() {
        var methods = List.of(Map.<String, Object>of(
                "method_id", "m1",
                "method_signature", "find()",
                "class_name", "Repo",
                "app_id", "test",
                "operation", "SELECT",
                "access_count", 1
        ));
        when(columnRepo.findImpactedMethods("t", "c")).thenReturn(methods);
        when(columnRepo.findColumnsByTable("t")).thenReturn(List.of("c"));

        Result emptyResult = mock(Result.class);
        when(session.run(anyString(), any(Map.class))).thenReturn(emptyResult);
        when(emptyResult.hasNext()).thenReturn(false);

        LineageContext ctx = builder.buildDeadColumnContext("t", "c", 30, false, "");

        assertThat(ctx.upstreamEndpoints()).isEmpty();
        assertThat(ctx.deadColumnAnalysis()).containsEntry("days", 30);
    }
}
