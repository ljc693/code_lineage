package com.forfun.codel_ineage.collector.parser;

import com.forfun.codel_ineage.model.SqlOperation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SqlParserTest {

    private final GenericSqlParser generic = new GenericSqlParser();
    private final MySqlParser mysql = new MySqlParser();
    private final PostgreSqlParser pg = new PostgreSqlParser();

    @Test
    void genericShouldParseSimpleSelect() {
        var tables = generic.extractTables(
                "SELECT id, name FROM users WHERE status = 1", List.of());
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).tableName()).isEqualTo("users");
        assertThat(tables.get(0).operation()).isEqualTo(SqlOperation.SELECT);
    }

    @Test
    void genericShouldParseInsert() {
        var tables = generic.extractTables(
                "INSERT INTO orders (id, amount) VALUES (1, 99.9)", List.of());
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).tableName()).isEqualTo("orders");
        assertThat(tables.get(0).operation()).isEqualTo(SqlOperation.INSERT);
    }

    @Test
    void genericShouldParseMultiTableJoin() {
        var tables = generic.extractTables(
                "SELECT u.name, o.amount FROM users u JOIN orders o ON u.id = o.user_id", List.of());
        assertThat(tables).extracting(SqlDialectParser.ParsedTable::tableName)
                .contains("users", "orders");
    }

    @Test
    void mysqlShouldParseBacktickTables() {
        var tables = mysql.extractTables(
                "SELECT * FROM `users` JOIN `orders` ON `users`.`id` = `orders`.`user_id`", List.of());
        assertThat(tables).extracting(SqlDialectParser.ParsedTable::tableName)
                .contains("users", "orders");
    }

    @Test
    void mysqlShouldParseReplaceInto() {
        var tables = mysql.extractTables(
                "REPLACE INTO `cache` (`key`, `value`) VALUES (?, ?)", List.of());
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).tableName()).isEqualTo("cache");
        assertThat(tables.get(0).operation()).isEqualTo(SqlOperation.INSERT);
    }

    @Test
    void pgShouldExcludeCteNames() {
        var tables = pg.extractTables(
                "WITH active AS (SELECT id FROM users WHERE status=1) " +
                "SELECT * FROM active JOIN orders ON active.id = orders.user_id", List.of());
        assertThat(tables).extracting(SqlDialectParser.ParsedTable::tableName)
                .contains("orders")   // real table
                .doesNotContain("active"); // CTE, not a real table
    }

    @Test
    void pgShouldParseReturning() {
        var tables = pg.extractTables(
                "INSERT INTO users (name, email) VALUES ($1, $2) RETURNING id, created_at",
                List.of("Alice", "a@b.com"));
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).tableName()).isEqualTo("users");
        assertThat(tables.get(0).schema()).isEqualTo("public");
        assertThat(tables.get(0).operation()).isEqualTo(SqlOperation.INSERT);
    }

    @Test
    void pgShouldParseUpdate() {
        var tables = pg.extractTables(
                "UPDATE \"users\" SET \"status\" = $1 WHERE \"id\" = $2", List.of("active", "42"));
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).tableName()).isEqualTo("users");
    }

    @Test
    void shouldHandleDelete() {
        var tables = generic.extractTables("DELETE FROM sessions WHERE expires < NOW()", List.of());
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).operation()).isEqualTo(SqlOperation.DELETE);
    }

    @Test
    void shouldHandleComplexSubquery() {
        var tables = generic.extractTables(
                "SELECT * FROM (SELECT id FROM users WHERE type='admin') sub JOIN logs ON sub.id=logs.uid",
                List.of());
        assertThat(tables).extracting(SqlDialectParser.ParsedTable::tableName)
                .contains("logs");
    }
}
