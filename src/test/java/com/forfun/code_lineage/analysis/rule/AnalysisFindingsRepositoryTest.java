package com.forfun.code_lineage.analysis.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AnalysisFindingsRepositoryTest {

    @Autowired
    private JdbcTemplate jdbc;

    private AnalysisFindingsRepository repo;

    @BeforeEach
    void setUp() {
        repo = new AnalysisFindingsRepository(jdbc);
    }

    @Test
    void saveAndFindByAppId() {
        repo.save("test-app", "n-plus-one", "N+1 Query Detector",
                "HIGH", "performance", "N+1 risk in Foo.bar()",
                "Method accesses table inside a self-calling loop.",
                "Replace with batch operation.",
                "{\"tableName\":\"crawler_task\"}");

        List<Map<String, Object>> results = repo.findByAppId("test-app", 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("app_id")).isEqualTo("test-app");
        assertThat(results.get(0).get("rule_id")).isEqualTo("n-plus-one");
        assertThat(results.get(0).get("severity")).isEqualTo("HIGH");
    }

    @Test
    void findByRuleId() {
        repo.save("app1", "rule-a", "Rule A",
                "LOW", "test", "Title A", "Desc A", "Sugg A", "{}");
        repo.save("app2", "rule-a", "Rule A",
                "LOW", "test", "Title B", "Desc B", "Sugg B", "{}");
        repo.save("app2", "rule-b", "Rule B",
                "HIGH", "test", "Title C", "Desc C", "Sugg C", "{}");

        List<Map<String, Object>> results = repo.findByRule("rule-a", 10);
        assertThat(results).hasSize(2);
    }

    @Test
    void findByAppIdReturnsEmptyWhenNoMatches() {
        List<Map<String, Object>> results = repo.findByAppId("nonexistent", 10);
        assertThat(results).isEmpty();
    }

    @Test
    void findByRuleReturnsEmptyWhenNoMatches() {
        List<Map<String, Object>> results = repo.findByRule("nonexistent", 10);
        assertThat(results).isEmpty();
    }

    @Test
    void limitRespectsMaxResults() {
        repo.save("app1", "r1", "R1", "LOW", "test", "T1", "D1", "S1", "{}");
        repo.save("app1", "r2", "R2", "LOW", "test", "T2", "D2", "S2", "{}");
        repo.save("app1", "r3", "R3", "LOW", "test", "T3", "D3", "S3", "{}");

        List<Map<String, Object>> results = repo.findByAppId("app1", 2);
        assertThat(results).hasSize(2);
    }
}
