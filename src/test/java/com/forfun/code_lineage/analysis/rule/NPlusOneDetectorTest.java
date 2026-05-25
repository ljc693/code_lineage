package com.forfun.code_lineage.analysis.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NPlusOneDetectorTest {

    private Driver driver;
    private Session session;
    private NPlusOneDetector detector;

    @BeforeEach
    void setUp() {
        driver = mock(Driver.class);
        session = mock(Session.class);
        when(driver.session()).thenReturn(session);
        detector = new NPlusOneDetector(driver);
    }

    @Test
    void detectsNPlusOnePattern() {
        Result mockResult = mock(Result.class);
        when(session.run(anyString(), any(Map.class))).thenReturn(mockResult);
        when(mockResult.list(any())).thenReturn(List.of(
                Map.of("methodId", "app:com.Foo.bar()",
                       "signature", "bar()",
                       "className", "Foo",
                       "tableName", "crawler_task",
                       "operation", "SELECT")));

        List<Finding> findings = detector.analyze(AnalysisRule.Target.app("test-app"));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("n-plus-one");
        assertThat(f.severity()).isEqualTo("HIGH");
        assertThat(f.category()).isEqualTo("performance");
        assertThat(f.title()).contains("N+1").contains("Foo.bar()");
        assertThat(f.description()).contains("crawler_task");
        assertThat(f.suggestion()).contains("batch");
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
