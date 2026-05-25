package com.forfun.code_lineage.analysis.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CircularDependencyDetectorTest {

    private Driver driver;
    private Session session;
    private CircularDependencyDetector detector;

    @BeforeEach
    void setUp() {
        driver = mock(Driver.class);
        session = mock(Session.class);
        when(driver.session()).thenReturn(session);
        detector = new CircularDependencyDetector(driver);
    }

    @Test
    void detectsCircularDependency() {
        Result mockResult = mock(Result.class);
        when(session.run(anyString(), any(Map.class))).thenReturn(mockResult);
        when(mockResult.list(any())).thenReturn(List.of(
                Map.of("methodId", "app:com.Foo.bar()",
                       "signature", "bar()",
                       "className", "Foo",
                       "cycle", List.of("Foo.bar()", "Foo.baz()", "Foo.bar()"),
                       "depth", 2)));

        List<Finding> findings = detector.analyze(AnalysisRule.Target.app("test-app"));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("circular-dep");
        assertThat(f.severity()).isEqualTo("HIGH");
        assertThat(f.category()).isEqualTo("architecture");
        assertThat(f.title()).contains("Circular dependency").contains("Foo.bar()");
        assertThat(f.description()).contains("Cycle detected");
        assertThat(f.suggestion()).contains("Break the cycle");
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
