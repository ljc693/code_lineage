package com.forfun.codel_ineage.analysis.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class LlmReportRepository {

    private final JdbcTemplate jdbc;

    public LlmReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        initTable();
    }

    private void initTable() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS llm_reports (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                report_type VARCHAR(64) NOT NULL,
                app_id VARCHAR(128),
                target_table VARCHAR(128),
                target_column VARCHAR(128),
                prompt TEXT NOT NULL,
                response TEXT,
                model VARCHAR(64),
                confidence DOUBLE DEFAULT 0.0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_type (report_type),
                INDEX idx_app (app_id),
                INDEX idx_created (created_at)
            )
            """);
    }

    public void save(String reportType, String appId, String targetTable,
                     String targetColumn, String prompt, String response,
                     String model, double confidence) {
        jdbc.update("""
            INSERT INTO llm_reports (report_type, app_id, target_table, target_column,
                prompt, response, model, confidence)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, reportType, appId, targetTable, targetColumn, prompt, response, model, confidence);
    }

    public List<Map<String, Object>> findByType(String reportType, int limit) {
        return jdbc.queryForList(
            "SELECT * FROM llm_reports WHERE report_type = ? ORDER BY created_at DESC LIMIT ?",
            reportType, limit);
    }

    public List<Map<String, Object>> findByAppId(String appId, int limit) {
        return jdbc.queryForList(
            "SELECT * FROM llm_reports WHERE app_id = ? ORDER BY created_at DESC LIMIT ?",
            appId, limit);
    }
}
