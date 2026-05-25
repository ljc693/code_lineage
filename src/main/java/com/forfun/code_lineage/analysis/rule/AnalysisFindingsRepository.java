package com.forfun.code_lineage.analysis.rule;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class AnalysisFindingsRepository {

    private final JdbcTemplate jdbc;

    public AnalysisFindingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        initTable();
    }

    void initTable() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS analysis_findings (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                app_id VARCHAR(128) NOT NULL,
                rule_id VARCHAR(64) NOT NULL,
                rule_name VARCHAR(128),
                severity VARCHAR(16) NOT NULL,
                category VARCHAR(32) NOT NULL,
                title VARCHAR(512) NOT NULL,
                description TEXT,
                suggestion TEXT,
                evidence JSON,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_app_rule (app_id, rule_id),
                INDEX idx_severity (severity),
                INDEX idx_created (created_at)
            )
            """);
    }

    public void save(String appId, String ruleId, String ruleName,
                     String severity, String category, String title,
                     String description, String suggestion, String evidence) {
        jdbc.update(
            "INSERT INTO analysis_findings (app_id, rule_id, rule_name, severity, " +
            "category, title, description, suggestion, evidence) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            appId, ruleId, ruleName, severity, category, title, description, suggestion, evidence);
    }

    public List<Map<String, Object>> findByAppId(String appId, int limit) {
        return jdbc.queryForList(
            "SELECT * FROM analysis_findings WHERE app_id = ? ORDER BY created_at DESC LIMIT ?",
            appId, limit);
    }

    public List<Map<String, Object>> findByRule(String ruleId, int limit) {
        return jdbc.queryForList(
            "SELECT * FROM analysis_findings WHERE rule_id = ? ORDER BY created_at DESC LIMIT ?",
            ruleId, limit);
    }
}
