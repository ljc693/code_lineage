package com.forfun.code_lineage.analysis.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrphanCodeDetectorTest {

    private Driver driver;
    private Session session;
    private OrphanCodeDetector detector;

    @BeforeEach
    void setUp() {
        driver = mock(Driver.class);
        session = mock(Session.class);
        when(driver.session()).thenReturn(session);
        detector = new OrphanCodeDetector(driver);
    }

    @Test
    void detectsOrphanMethods() {
        Result mockResult = mock(Result.class);
        when(session.run(anyString(), any(Map.class))).thenReturn(mockResult);
        when(mockResult.list(any())).thenReturn(List.of(
                Map.of("methodId", "app:com.Foo.unusedMethod()",
                       "signature", "unusedMethod()",
                       "className", "Foo",
                       "packageName", "com.foo")));

        List<Finding> findings = detector.analyze(AnalysisRule.Target.app("test-app"));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("orphan-code");
        assertThat(f.severity()).isEqualTo("LOW");
        assertThat(f.category()).isEqualTo("dead_code");
        assertThat(f.title()).contains("Orphan method").contains("Foo.unusedMethod()");
        assertThat(f.description()).contains("com.foo");
        assertThat(f.suggestion()).contains("dead code");
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
