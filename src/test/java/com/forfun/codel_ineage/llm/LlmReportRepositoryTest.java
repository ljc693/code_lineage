package com.forfun.codel_ineage.llm;

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
class LlmReportRepositoryTest {

    @Autowired
    private JdbcTemplate jdbc;

    private LlmReportRepository repo;

    @BeforeEach
    void setUp() {
        repo = new LlmReportRepository(jdbc);
    }

    @Test
    void saveAndFindByType() {
        repo.save("dead-columns", "test-app", "crawler_task", "backup_config",
                "Analyze columns...", "Column backup_config is unused.", "gpt-4", 0.85);

        List<Map<String, Object>> results = repo.findByType("dead-columns", 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("report_type")).isEqualTo("dead-columns");
        assertThat(results.get(0).get("target_table")).isEqualTo("crawler_task");
        assertThat(results.get(0).get("target_column")).isEqualTo("backup_config");
        assertThat(results.get(0).get("model")).isEqualTo("gpt-4");
    }

    @Test
    void saveAndFindByAppId() {
        repo.save("field-impact", "app-alpha", "orders", null,
                "Prompt text", "LLM response", "claude-opus-4-7", 0.92);
        repo.save("table-governance", "app-alpha", "users", null,
                "Another prompt", "Another response", "gpt-4", 0.88);

        List<Map<String, Object>> results = repo.findByAppId("app-alpha", 10);
        assertThat(results).hasSize(2);
    }

    @Test
    void findByTypeReturnsEmptyWhenNoMatches() {
        List<Map<String, Object>> results = repo.findByType("nonexistent", 10);
        assertThat(results).isEmpty();
    }

    @Test
    void findByAppIdReturnsEmptyWhenNoMatches() {
        List<Map<String, Object>> results = repo.findByAppId("nonexistent", 10);
        assertThat(results).isEmpty();
    }

    @Test
    void findByTypeRespectsLimit() {
        repo.save("test-type", "app1", "t1", null, "p1", "r1", "m1", 0.5);
        repo.save("test-type", "app2", "t2", null, "p2", "r2", "m2", 0.6);
        repo.save("test-type", "app3", "t3", null, "p3", "r3", "m3", 0.7);

        List<Map<String, Object>> results = repo.findByType("test-type", 2);
        assertThat(results).hasSize(2);
    }

    @Test
    void saveAcceptsNullTargetColumn() {
        repo.save("table-governance", "test-app", "crawler_task", null,
                "Governance prompt", "Governance response", "gpt-4", 0.9);

        List<Map<String, Object>> results = repo.findByAppId("test-app", 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("target_column")).isNull();
    }

    @Test
    void storesPromptAndResponseText() {
        repo.save("field-impact", "app", "t", "c",
                "Original prompt text", "Generated response text", "claude", 0.95);

        List<Map<String, Object>> results = repo.findByType("field-impact", 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("prompt")).toString().contains("Original prompt");
        assertThat(results.get(0).get("response")).toString().contains("Generated response");
    }
}
