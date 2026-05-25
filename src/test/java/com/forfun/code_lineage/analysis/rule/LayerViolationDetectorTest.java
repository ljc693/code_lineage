package com.forfun.code_lineage.analysis.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LayerViolationDetectorTest {

    private Driver driver;
    private Session session;
    private LayerViolationDetector detector;

    @BeforeEach
    void setUp() {
        driver = mock(Driver.class);
        session = mock(Session.class);
        when(driver.session()).thenReturn(session);
        detector = new LayerViolationDetector(driver);
    }

    @Test
    void detectsControllerDirectlyAccessingTable() {
        Result mockResult = mock(Result.class);
        when(session.run(anyString(), any(Map.class))).thenReturn(mockResult);
        when(mockResult.list(any())).thenReturn(List.of(
                Map.of("methodId", "app:com.FooController.bar()",
                       "signature", "bar()",
                       "className", "FooController",
                       "tableName", "crawler_task",
                       "operation", "SELECT")));

        List<Finding> findings = detector.analyze(AnalysisRule.Target.app("test-app"));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("layer-violation");
        assertThat(f.severity()).isEqualTo("MEDIUM");
        assertThat(f.category()).isEqualTo("architecture");
        assertThat(f.title()).contains("Layer violation").contains("FooController.bar()");
        assertThat(f.description()).contains("crawler_task");
        assertThat(f.suggestion()).contains("Repository");
    }

    @Test
    void emptyResultProducesNoFindings() {
        Result mockResult = mock(Result.class);
        when(session.run(anyString(), any(Map.class))).thenReturn(mockResult);
        when(mockResult.list(any())).thenReturn(List.of());

        List<Finding> findings = detector.analyze(AnalysisRule.Target.app("test-app"));
        assertThat(findings).isEmpty();
    }
}
