package com.forfun.codel_ineage.sql.collector;

import com.forfun.codel_ineage.sql.model.ColumnRef;
import com.forfun.codel_ineage.sql.model.ColumnRef.ColumnSource;
import com.forfun.codel_ineage.model.SqlOperation;
import java.util.*;
import java.util.regex.*;

/**
 * PostgreSQL dialect parser.
 * Handles: double-quote identifiers, $N params, CTE (WITH), RETURNING, ::casts, JSONB ops.
 */
public class PostgreSqlParser extends GenericSqlParser {

    private static final Pattern QUOTED_TABLE = Pattern.compile(
            "(?:FROM|JOIN|INTO|UPDATE|INSERT\\s+INTO)\\s+\"?(\\w+)\"?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CTE_PATTERN = Pattern.compile(
            "WITH\\s+(\\w+)\\s+AS\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETURNING_PATTERN = Pattern.compile(
            "RETURNING\\s+(.+?)(?:;|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PG_PARAM_PATTERN = Pattern.compile("\\$(\\d+)");

    @Override
    public String dialect() { return "PostgreSQL"; }

    @Override
    public List<ParsedTable> extractTables(String sql, List<String> params) {
        List<ParsedTable> results = new ArrayList<>();
        SqlOperation op = detectOperation(sql);

        Set<String> tables = new LinkedHashSet<>();
        // Exclude CTE names (they're not real tables)
        Set<String> cteNames = extractCteNames(sql);
        tables.addAll(extractTableNames(sql));
        tables.addAll(extractQuotedTables(sql));
        tables.removeAll(cteNames);

        // Extract RETURNING columns (specific to PG INSERT/UPDATE/DELETE)
        List<ColumnRef> returningColumns = extractReturning(sql).stream()
                .map(c -> ColumnRef.builder().columnName(c).source(ColumnSource.SQL_INSERT).build())
                .toList();

        for (String t : tables) {
            results.add(new ParsedTable(t, returningColumns, op, "public", sql));
        }
        if (results.isEmpty()) {
            results.addAll(super.extractTables(sql, params));
        }
        return results;
    }

    private Set<String> extractQuotedTables(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = QUOTED_TABLE.matcher(sql);
        while (m.find()) {
            String t = m.group(1);
            if (!t.equalsIgnoreCase("DUAL")) tables.add(t);
        }
        return tables;
    }

    private Set<String> extractCteNames(String sql) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = CTE_PATTERN.matcher(sql);
        while (m.find()) names.add(m.group(1).toLowerCase());
        return names;
    }

    private List<String> extractReturning(String sql) {
        Matcher m = RETURNING_PATTERN.matcher(sql);
        if (m.find()) {
            return Arrays.stream(m.group(1).split(","))
                    .map(String::trim)
                    .map(c -> c.split("\\s+")[0].replace("\"", ""))
                    .filter(c -> !c.isEmpty() && !c.equals("*"))
                    .toList();
        }
        return List.of();
    }
}
