package com.forfun.code_lineage.analysis.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GodMethodDetectorTest {

    private Driver driver;
    private Session session;
    private GodMethodDetector detector;

    @BeforeEach
    void setUp() {
        driver = mock(Driver.class);
        session = mock(Session.class);
        when(driver.session()).thenReturn(session);
        detector = new GodMethodDetector(driver);
    }

    @Test
    void detectsGodMethodWithSixTableAccesses() {
        Result mockResult = mock(Result.class);
        when(session.run(anyString(), any(Map.class))).thenReturn(mockResult);
        when(mockResult.list(any())).thenReturn(List.of(
                Map.of("methodId", "app:com.Foo.bar()",
                       "signature", "bar()",
                       "className", "Foo",
                       "tables", List.of("t1", "t2", "t3", "t4", "t5", "t6"),
                       "tblCount", 6L)));

        List<Finding> findings = detector.analyze(AnalysisRule.Target.app("test-app"));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("god-method");
        assertThat(f.severity()).isEqualTo("MEDIUM");
        assertThat(f.category()).isEqualTo("architecture");
        assertThat(f.title()).contains("God method").contains("Foo.bar()");
        assertThat(f.description()).contains("6 tables");
        assertThat(f.suggestion()).contains("separate");
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
