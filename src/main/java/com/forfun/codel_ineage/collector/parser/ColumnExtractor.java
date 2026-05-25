package com.forfun.codel_ineage.collector.parser;

import com.forfun.codel_ineage.collector.model.ColumnRef;
import com.forfun.codel_ineage.collector.model.ColumnRef.ColumnSource;

import java.util.*;
import java.util.regex.*;

/**
 * Extracts column references from complex SQL statements.
 * Handles: SELECT list, WHERE conditions, JOIN ON, INSERT columns, UPDATE SET.
 */
public class ColumnExtractor {

    // Qualified column: table.column or alias.column
    private static final Pattern QUALIFIED_COL = Pattern.compile(
            "(\\w+)\\.(\\w+)", Pattern.CASE_INSENSITIVE);

    // Alias definition: column AS alias or expression AS alias
    private static final Pattern ALIAS_DEF = Pattern.compile(
            "(?:AS\\s+)?(\\w+)\\s*$", Pattern.CASE_INSENSITIVE);

    // INSERT column list: INSERT INTO table (col1, col2, ...)
    private static final Pattern INSERT_COLS = Pattern.compile(
            "INSERT\\s+(?:INTO\\s+)?\\w+\\s*\\((.+?)\\)\\s*(?:VALUES|SELECT|SET)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // UPDATE SET: SET col1 = val1, col2 = val2
    private static final Pattern UPDATE_COLS = Pattern.compile(
            "SET\\s+(.+?)(?:\\s+WHERE|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Extract all column references from a SQL statement.
     */
    public List<ColumnRef> extract(String sql) {
        List<ColumnRef> all = new ArrayList<>();

        // L1: Qualified references (table.column) — most reliable
        Matcher qm = QUALIFIED_COL.matcher(sql);
        Set<String> seenQualified = new HashSet<>();
        while (qm.find()) {
            String table = qm.group(1);
            String col = qm.group(2);
            // Skip SQL keywords used as aliases
            if (isNotKeyword(table) && isNotKeyword(col)) {
                String key = table + "." + col;
                if (seenQualified.add(key)) {
                    all.add(ColumnRef.builder()
                            .columnName(col).tableName(table)
                            .source(ColumnSource.SQL_SELECT).build());
                }
            }
        }

        // L2: INSERT column list
        Matcher im = INSERT_COLS.matcher(sql);
        while (im.find()) {
            for (String col : im.group(1).split(",")) {
                String c = col.trim().replaceAll("[`\"'\\[\\]]", "");
                if (!c.isEmpty() && isNotKeyword(c)) {
                    all.add(ColumnRef.builder()
                            .columnName(c)
                            .source(ColumnSource.SQL_INSERT).build());
                }
            }
        }

        // L3: UPDATE SET columns
        Matcher um = UPDATE_COLS.matcher(sql);
        while (um.find()) {
            for (String pair : um.group(1).split(",")) {
                String col = pair.trim().split("\\s*=\\s*")[0]
                        .replaceAll("[`\"'\\[\\]]", "");
                if (!col.isEmpty() && isNotKeyword(col)) {
                    all.add(ColumnRef.builder()
                            .columnName(col)
                            .source(ColumnSource.SQL_UPDATE).build());
                }
            }
        }

        return deduplicate(all);
    }

    private boolean isNotKeyword(String word) {
        Set<String> keywords = Set.of(
                "SELECT", "FROM", "WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER",
                "ON", "AND", "OR", "NOT", "IN", "EXISTS", "BETWEEN", "LIKE",
                "INSERT", "UPDATE", "DELETE", "INTO", "VALUES", "SET",
                "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET",
                "AS", "DISTINCT", "CASE", "WHEN", "THEN", "ELSE", "END",
                "UNION", "ALL", "NULL", "TRUE", "FALSE", "IS", "ASC", "DESC",
                "COUNT", "SUM", "AVG", "MAX", "MIN", "COALESCE", "CAST",
                "WITH", "RECURSIVE", "RETURNING", "DUAL"
        );
        return !keywords.contains(word.toUpperCase());
    }

    private List<ColumnRef> deduplicate(List<ColumnRef> refs) {
        // Keep unique (tableName, columnName) pairs
        Map<String, ColumnRef> unique = new LinkedHashMap<>();
        for (ColumnRef r : refs) {
            unique.putIfAbsent(r.toQualifiedName(), r);
        }
        return new ArrayList<>(unique.values());
    }
}
