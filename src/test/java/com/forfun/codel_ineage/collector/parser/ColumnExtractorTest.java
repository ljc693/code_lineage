package com.forfun.codel_ineage.collector.parser;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ColumnExtractorTest {

    private final ColumnExtractor extractor = new ColumnExtractor();

    @Test
    void shouldExtractQualifiedColumns() {
        var cols = extractor.extract(
                "SELECT u.id, u.name, u.email FROM users u WHERE u.status = 'active'");
        assertThat(cols).extracting(c -> c.getTableName() + "." + c.getColumnName())
                .contains("u.id", "u.name", "u.email", "u.status");
    }

    @Test
    void shouldExtractJoinColumns() {
        var cols = extractor.extract(
                "SELECT o.id, u.name FROM orders o JOIN users u ON o.user_id = u.id " +
                "WHERE o.status = 'paid' AND u.type = 'vip'");
        assertThat(cols).extracting(c -> c.getTableName() + "." + c.getColumnName())
                .contains("o.id", "u.name", "o.user_id", "u.id", "o.status", "u.type");
    }

    @Test
    void shouldExtractInsertColumns() {
        var cols = extractor.extract(
                "INSERT INTO users (name, email, role) VALUES (?, ?, ?)");
        assertThat(cols).extracting(c -> c.getColumnName())
                .contains("name", "email", "role");
    }

    @Test
    void shouldExtractUpdateColumns() {
        var cols = extractor.extract(
                "UPDATE orders SET status = ?, amount = ? WHERE id = ? AND version = ?");
        assertThat(cols).extracting(c -> c.getColumnName())
                .contains("status", "amount");
    }

    @Test
    void shouldExcludeSqlKeywords() {
        var cols = extractor.extract(
                "SELECT COUNT(*) AS total FROM users WHERE status IN (SELECT type FROM roles)");
        // Should NOT include "COUNT", "AS", "IN", "FROM" as columns
        assertThat(cols).extracting(c -> c.getColumnName())
                .doesNotContain("COUNT", "FROM", "IN", "AS");
    }

    @Test
    void shouldHandleSubqueryAliases() {
        var cols = extractor.extract(
                "SELECT sub.id, sub.cnt FROM (SELECT user_id AS id, COUNT(*) AS cnt FROM orders GROUP BY user_id) sub");
        // Qualified references (alias.column) in outer query are captured
        assertThat(cols).extracting(c -> c.getTableName() + "." + c.getColumnName())
                .contains("sub.id", "sub.cnt");
        // Unqualified columns inside subquery are NOT captured (known limitation)
        // user_id inside (SELECT user_id AS id ...) has no table prefix
    }

    @Test
    void shouldHandleMultiJoinAndAlias() {
        var cols = extractor.extract(
                "SELECT a.id, a.title, b.body, c.rating " +
                "FROM articles a " +
                "JOIN bodies b ON a.id = b.article_id " +
                "LEFT JOIN reviews c ON a.id = c.article_id " +
                "WHERE a.status = 'published' AND c.score > 4.0");
        assertThat(cols).hasSizeGreaterThanOrEqualTo(7);
        assertThat(cols).extracting(c -> c.getTableName() + "." + c.getColumnName())
                .contains("a.id", "a.title", "b.body", "c.rating",
                           "a.id", "b.article_id", "c.article_id");
    }
}
