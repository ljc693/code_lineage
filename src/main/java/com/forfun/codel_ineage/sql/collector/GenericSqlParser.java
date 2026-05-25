package com.forfun.codel_ineage.sql.collector;

import com.forfun.codel_ineage.sql.model.ColumnRef;
import com.forfun.codel_ineage.model.SqlOperation;
import java.util.*;
import java.util.regex.*;

/**
 * Generic SQL parser using regex patterns.
 * Works for any SQL dialect but less accurate than dialect-specific parsers.
 */
public class GenericSqlParser implements SqlDialectParser {

    protected final ColumnExtractor columnExtractor = new ColumnExtractor();

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?:FROM|JOIN|INTO|UPDATE|INSERT\\s+INTO)\\s+`?\"?([\\w.]+)`?\"?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
            "(?:SELECT|SET)\\s+(.+?)\\s+(?:FROM|WHERE|SET|VALUES|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OPERATION_PATTERN = Pattern.compile(
            "^(SELECT|INSERT|UPDATE|DELETE|MERGE|UPSERT|REPLACE|TRUNCATE|CREATE|ALTER|DROP)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String dialect() { return "GENERIC"; }

    @Override
    public List<ParsedTable> extractTables(String sql, List<String> params) {
        if (sql == null || sql.isBlank()) return List.of();
        String cleaned = sql.trim();
        SqlOperation op = detectOperation(cleaned);
        Set<String> tables = extractTableNames(cleaned);
        List<ColumnRef> columns = columnExtractor.extract(cleaned);

        return tables.stream()
                .map(t -> new ParsedTable(t, filterColumns(columns, t), op, null, cleaned))
                .toList();
    }

    private List<ColumnRef> filterColumns(List<ColumnRef> cols, String table) {
        return cols.stream()
                .filter(c -> c.getTableName() == null || c.getTableName().equals(table))
                .toList();
    }

    protected SqlOperation detectOperation(String sql) {
        Matcher m = OPERATION_PATTERN.matcher(sql);
        if (!m.find()) return SqlOperation.SELECT;
        return switch (m.group(1).toUpperCase()) {
            case "INSERT", "REPLACE", "UPSERT" -> SqlOperation.INSERT;
            case "UPDATE", "MERGE" -> SqlOperation.UPDATE;
            case "DELETE", "TRUNCATE" -> SqlOperation.DELETE;
            default -> SqlOperation.SELECT;
        };
    }

    Set<String> extractTableNames(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = TABLE_PATTERN.matcher(sql);
        while (m.find()) {
            String t = m.group(1).replaceAll("[`\"\\[\\]]", "");
            // Skip subqueries and common table names that aren't real tables
            if (!t.equalsIgnoreCase("DUAL") && !t.startsWith("(")) {
                tables.add(t);
            }
        }
        return tables;
    }

    List<String> extractColumns(String sql) {
        List<String> columns = new ArrayList<>();
        Matcher m = COLUMN_PATTERN.matcher(sql);
        if (m.find()) {
            String cols = m.group(1).trim();
            // Skip aggregate functions and expressions
            for (String col : cols.split(",")) {
                String c = col.trim();
                if (c.contains("(")) {
                    // Extract alias from "COUNT(*) AS cnt"
                    int asIdx = c.toUpperCase().lastIndexOf(" AS ");
                    if (asIdx > 0) c = c.substring(asIdx + 4).trim();
                    else c = c.split("\\(")[0].trim();
                }
                if (!c.isEmpty() && !c.equals("*")) {
                    columns.add(c.split("\\s+")[0].replaceAll("[`\"']", ""));
                }
            }
        }
        return columns;
    }
}
