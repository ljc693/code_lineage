package com.forfun.codel_ineage.analysis.rule;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AbstractAnalysisRuleTest {

    @Test
    void templateMethodCallsGatherThenDetect() {
        var recorder = new ArrayList<String>();
        var rule = new AbstractAnalysisRule(null) {
            @Override public String id() { return "test"; }
            @Override public String name() { return "Test"; }
            @Override public String severity() { return "LOW"; }
            @Override public String category() { return "test"; }
            @Override protected Object gatherContext(Session s, Target t) {
                recorder.add("gather"); return List.of(Map.of("k", "v"));
            }
            @Override protected List<Finding> detect(Session s, Target t, Object ctx) {
                recorder.add("detect");
                return List.of(new Finding(id(), severity(), category(), "T", "D", "S", Map.of()));
            }
        };

        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        when(driver.session()).thenReturn(session);
        // Inject driver via reflection since constructor is protected
        try {
            var field = AbstractAnalysisRule.class.getDeclaredField("neo4jDriver");
            field.setAccessible(true);
            field.set(rule, driver);
        } catch (Exception ignored) {}

        var target = AnalysisRule.Target.app("test-app");
        List<Finding> findings = rule.analyze(target);

        assertThat(recorder).containsExactly("gather", "detect");
        assertThat(findings).hasSize(1);
    }

    @Test
    void nullContextReturnsEmptyFindings() {
        var rule = new AbstractAnalysisRule(null) {
            @Override public String id() { return "test"; }
            @Override public String name() { return "Test"; }
            @Override public String severity() { return "LOW"; }
            @Override public String category() { return "test"; }
            @Override protected Object gatherContext(Session s, Target t) { return null; }
            @Override protected List<Finding> detect(Session s, Target t, Object ctx) {
                return List.of(new Finding(id(), severity(), category(), "T", "D", "S", Map.of()));
            }
        };

        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        when(driver.session()).thenReturn(session);
        try {
            var field = AbstractAnalysisRule.class.getDeclaredField("neo4jDriver");
            field.setAccessible(true);
            field.set(rule, driver);
        } catch (Exception ignored) {}

        List<Finding> findings = rule.analyze(AnalysisRule.Target.app("test-app"));
        assertThat(findings).isEmpty();
    }
}
