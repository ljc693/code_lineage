package com.forfun.codel_ineage.analysis.rule;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalysisEngineTest {

    @Test
    void runAllExecutesAllRules() {
        var repo = mock(AnalysisFindingsRepository.class);
        var rule1 = mock(AnalysisRule.class);
        when(rule1.id()).thenReturn("a");
        when(rule1.analyze(any())).thenReturn(List.of(
                new Finding("a", "LOW", "test", "T1", "D1", "S1", Map.of())));

        var rule2 = mock(AnalysisRule.class);
        when(rule2.id()).thenReturn("b");
        when(rule2.analyze(any())).thenReturn(List.of(
                new Finding("b", "HIGH", "test", "T2", "D2", "S2", Map.of())));

        var engine = new AnalysisEngine(List.of(rule1, rule2), repo);
        List<Finding> results = engine.runAll(AnalysisRule.Target.app("test-app"));

        assertThat(results).hasSize(2);
        verify(repo, times(2)).save(any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void runOneExecutesOnlyMatchingRule() {
        var repo = mock(AnalysisFindingsRepository.class);
        var rule = mock(AnalysisRule.class);
        when(rule.id()).thenReturn("n-plus-one");
        when(rule.analyze(any())).thenReturn(List.of());

        var engine = new AnalysisEngine(List.of(rule), repo);
        engine.runOne("n-plus-one", AnalysisRule.Target.app("test-app"));
        verify(rule).analyze(any());
    }

    @Test
    void listRulesReturnsAllRegistered() {
        var repo = mock(AnalysisFindingsRepository.class);
        var rule = mock(AnalysisRule.class);
        when(rule.id()).thenReturn("test-rule");

        var engine = new AnalysisEngine(List.of(rule), repo);
        assertThat(engine.listRules()).hasSize(1);
    }
}
