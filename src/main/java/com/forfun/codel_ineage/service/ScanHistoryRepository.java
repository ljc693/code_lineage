package com.forfun.codel_ineage.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Persists scan results to MySQL for CI/CD history queries.
 */
@Repository
public class ScanHistoryRepository {

    private final JdbcTemplate jdbc;

    public ScanHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        initTable();
    }

    private void initTable() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS scan_history (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                trace_id VARCHAR(64) NOT NULL UNIQUE,
                app_id VARCHAR(128) NOT NULL,
                scan_type VARCHAR(32) DEFAULT 'FULL',
                commit_sha VARCHAR(64),
                branch VARCHAR(128),
                repo_url VARCHAR(512),
                status VARCHAR(16) NOT NULL,
                total_files INT DEFAULT 0,
                methods_count INT DEFAULT 0,
                relations_count INT DEFAULT 0,
                sql_relations_count INT DEFAULT 0,
                entry_points_count INT DEFAULT 0,
                tables_count INT DEFAULT 0,
                error_message TEXT,
                started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                completed_at TIMESTAMP NULL,
                INDEX idx_app_id (app_id),
                INDEX idx_status (status),
                INDEX idx_started_at (started_at)
            )
            """);
    }

    public void insertStarted(String traceId, String appId, String scanType,
                               String commitSha, String branch, String repoUrl) {
        jdbc.update(
            "INSERT INTO scan_history (trace_id, app_id, scan_type, commit_sha, branch, repo_url, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'RUNNING')",
            traceId, appId,
            scanType != null ? scanType : "FULL",
            commitSha, branch, repoUrl);
    }

    public void updateCompleted(String traceId, String status, int totalFiles,
                                 int methods, int relations, int sqlRelations,
                                 int entryPoints, int tables, String error) {
        jdbc.update(
            "UPDATE scan_history SET status=?, total_files=?, methods_count=?, relations_count=?, " +
            "sql_relations_count=?, entry_points_count=?, tables_count=?, " +
            "error_message=?, completed_at=? WHERE trace_id=?",
            status, totalFiles, methods, relations, sqlRelations, entryPoints, tables,
            error, new Timestamp(System.currentTimeMillis()), traceId);
    }

    public Map<String, Object> findByTraceId(String traceId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM scan_history WHERE trace_id = ?", traceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> findByAppId(String appId, int limit) {
        return jdbc.queryForList(
            "SELECT * FROM scan_history WHERE app_id = ? ORDER BY started_at DESC LIMIT ?",
            appId, limit);
    }

    public List<Map<String, Object>> findRecent(int limit) {
        return jdbc.queryForList(
            "SELECT * FROM scan_history ORDER BY started_at DESC LIMIT ?", limit);
    }

    public Map<String, Object> findLatestByAppId(String appId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM scan_history WHERE app_id = ? ORDER BY started_at DESC LIMIT 1", appId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
