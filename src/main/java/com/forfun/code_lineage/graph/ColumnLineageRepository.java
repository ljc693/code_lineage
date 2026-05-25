package com.forfun.code_lineage.graph;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Repository
public class ColumnLineageRepository {

    private final JdbcTemplate jdbc;

    public ColumnLineageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        initTable();
    }

    private void initTable() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS column_lineage (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    app_id VARCHAR(128) NOT NULL,
                    method_id VARCHAR(512) NOT NULL,
                    method_signature VARCHAR(256),
                    class_name VARCHAR(256),
                    table_name VARCHAR(128) NOT NULL,
                    column_name VARCHAR(128) NOT NULL,
                    operation VARCHAR(16),
                    db_type VARCHAR(32),
                    sql_fingerprint VARCHAR(512),
                    access_count INT DEFAULT 1,
                    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        } catch (Exception e) {
            // Table already exists
        }
    }

    public void upsert(String appId, String methodId, String methodSig, String className,
                        String tableName, String columnName, String operation, String dbType,
                        String sqlFingerprint) {
        // Check if row exists (DB-agnostic upsert)
        int count = countByKey(methodId, tableName, columnName);
        if (count > 0) {
            jdbc.update("""
                UPDATE column_lineage SET access_count = access_count + 1,
                last_seen = ?, app_id = ?, method_signature = ?, class_name = ?,
                operation = ?, db_type = ?, sql_fingerprint = ?
                WHERE method_id = ? AND table_name = ? AND column_name = ?
                """,
                Instant.now(), appId, methodSig, className,
                operation, dbType, sqlFingerprint,
                methodId, tableName, columnName);
        } else {
            jdbc.update("""
                INSERT INTO column_lineage (app_id, method_id, method_signature, class_name,
                    table_name, column_name, operation, db_type, sql_fingerprint, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                appId, methodId, methodSig, className,
                tableName, columnName, operation, dbType, sqlFingerprint,
                Instant.now(), Instant.now());
        }
    }

    private int countByKey(String methodId, String tableName, String columnName) {
        Integer c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM column_lineage WHERE method_id=? AND table_name=? AND column_name=?",
            Integer.class, methodId, tableName, columnName);
        return c != null ? c : 0;
    }

    public List<Map<String, Object>> findImpactedMethods(String tableName, String columnName) {
        return jdbc.queryForList(
            "SELECT method_id, method_signature, class_name, app_id, " +
            "CASE WHEN SUM(CASE WHEN operation = 'DELETE' THEN 1 ELSE 0 END) > 0 THEN 'DELETE' " +
            "     WHEN SUM(CASE WHEN operation = 'INSERT' THEN 1 ELSE 0 END) > 0 THEN 'INSERT' " +
            "     WHEN SUM(CASE WHEN operation = 'UPDATE' THEN 1 ELSE 0 END) > 0 THEN 'UPDATE' " +
            "     ELSE 'SELECT' END AS operation, " +
            "SUM(access_count) AS access_count " +
            "FROM column_lineage " +
            "WHERE table_name = ? AND column_name = ? " +
            "GROUP BY method_id, method_signature, class_name, app_id " +
            "ORDER BY access_count DESC",
            tableName, columnName);
    }

    public List<String> findColumnsByTable(String tableName) {
        return jdbc.queryForList(
            "SELECT DISTINCT column_name FROM column_lineage WHERE table_name = ? " +
            "AND column_name <> '*' " +
            "ORDER BY column_name",
            String.class, tableName);
    }

    public List<Map<String, Object>> findColumnsByMethod(String methodId) {
        return jdbc.queryForList(
            "SELECT table_name, column_name, operation, db_type, access_count " +
            "FROM column_lineage WHERE method_id = ? " +
            "ORDER BY access_count DESC",
            methodId);
    }

    public List<Map<String, Object>> findTablesByApp(String appId) {
        if (appId == null || "%".equals(appId)) {
            return jdbc.queryForList(
                "SELECT table_name, COUNT(DISTINCT column_name) AS col_count, " +
                "COUNT(DISTINCT method_id) AS method_count, MAX(last_seen) AS last_access " +
                "FROM column_lineage " +
                "GROUP BY table_name ORDER BY method_count DESC");
        }
        return jdbc.queryForList(
            "SELECT table_name, COUNT(DISTINCT column_name) AS col_count, " +
            "COUNT(DISTINCT method_id) AS method_count, MAX(last_seen) AS last_access " +
            "FROM column_lineage WHERE app_id = ? " +
            "GROUP BY table_name ORDER BY method_count DESC",
            appId);
    }

    public List<Map<String, Object>> findUnusedColumns(String tableName, int daysThreshold) {
        return jdbc.queryForList(
            "SELECT column_name, MAX(last_seen) AS last_access, " +
            "SUM(access_count) AS total_accesses " +
            "FROM column_lineage WHERE table_name = ? " +
            "GROUP BY column_name " +
            "HAVING MAX(last_seen) < DATE_SUB(NOW(), INTERVAL ? DAY) " +
            "ORDER BY last_access",
            tableName, daysThreshold);
    }

    public int count() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM column_lineage", Integer.class);
        return c != null ? c : 0;
    }
}
