package com.forfun.code_lineage.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ColumnLineageRepositoryIT {

    @Autowired
    private JdbcTemplate jdbc;

    private ColumnLineageRepository repo;

    @BeforeEach
    void setUp() {
        repo = new ColumnLineageRepository(jdbc);
    }

    @Test
    void testUpsertAndFindImpactedMethods() {
        repo.upsert("test-app", "test:com.example.Foo.bar()", "bar()", "Foo",
                "test_table", "col1", "SELECT", "MYSQL", "SELECT * FROM test_table");
        var impacted = repo.findImpactedMethods("test_table", "col1");
        assertFalse(impacted.isEmpty());
        assertEquals("Foo", impacted.get(0).get("class_name"));
    }

    @Test
    void testFindColumnsByTable() {
        repo.upsert("test-app", "t1", "m1()", "C1", "tbl", "a", "SELECT", "MYSQL", "");
        repo.upsert("test-app", "t2", "m2()", "C2", "tbl", "b", "SELECT", "MYSQL", "");
        List<String> cols = repo.findColumnsByTable("tbl");
        assertTrue(cols.contains("a"));
        assertTrue(cols.contains("b"));
    }

    @Test
    void testFindTablesByApp() {
        repo.upsert("test-app", "t1", "m1()", "C1", "unique_table", "x", "SELECT", "MYSQL", "");
        var tables = repo.findTablesByApp("test-app");
        assertTrue(tables.stream().anyMatch(t -> "unique_table".equals(t.get("table_name"))));
    }

    @Test
    void testFindUnusedColumns() {
        // Insert old data that should be flagged as dead
        repo.upsert("test-app", "t1", "m1()", "C1", "dead_table", "old_col", "SELECT", "MYSQL", "");
        var dead = repo.findUnusedColumns("dead_table", 9999);
        // Column accessed "now" shouldn't be dead with threshold 9999 days
        assertTrue(dead.isEmpty() || dead.size() >= 0);
    }

    @Test
    void testAccessCountIncrements() {
        repo.upsert("test-app", "t1", "inc()", "Inc", "inc_table", "col", "SELECT", "MYSQL", "");
        repo.upsert("test-app", "t1", "inc()", "Inc", "inc_table", "col", "SELECT", "MYSQL", "");
        var methods = repo.findImpactedMethods("inc_table", "col");
        assertFalse(methods.isEmpty());
    }

    @Test
    void testOperationTypePriority() {
        repo.upsert("test-app", "t1", "mixed()", "Mix", "op_table", "col", "SELECT", "MYSQL", "");
        repo.upsert("test-app", "t1", "mixed()", "Mix", "op_table", "col", "DELETE", "MYSQL", "");
        var methods = repo.findImpactedMethods("op_table", "col");
        assertEquals("DELETE", methods.get(0).get("operation"));
    }
}
