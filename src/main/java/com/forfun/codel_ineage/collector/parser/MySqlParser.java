package com.forfun.codel_ineage.collector.parser;

import com.forfun.codel_ineage.model.SqlOperation;
import java.util.*;
import java.util.regex.*;

/**
 * MySQL dialect parser.
 * Handles: backtick quoting, LIMIT, REPLACE INTO, INSERT IGNORE, ON DUPLICATE KEY.
 */
public class MySqlParser extends GenericSqlParser {

    private static final Pattern BACKTICK_TABLE = Pattern.compile(
            "(?:FROM|JOIN|INTO|UPDATE|INSERT\\s+(?:IGNORE\\s+)?INTO|REPLACE\\s+INTO)\\s+`(\\w+)`",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SCHEMA_TABLE = Pattern.compile(
            "`(\\w+)`\\.`(\\w+)`", Pattern.CASE_INSENSITIVE);

    @Override
    public String dialect() { return "MySQL"; }

    @Override
    public List<ParsedTable> extractTables(String sql, List<String> params) {
        List<ParsedTable> results = new ArrayList<>();
        SqlOperation op = detectMySqlOperation(sql);

        // Backtick-quoted tables with optional schema
        Map<String, String> schemaMap = new LinkedHashMap<>();
        Matcher sm = SCHEMA_TABLE.matcher(sql);
        while (sm.find()) {
            schemaMap.put(sm.group(2), sm.group(1));
        }

        Set<String> tables = extractTableNames(sql);
        tables.addAll(extractBacktickTables(sql));

        for (String t : tables) {
            results.add(new ParsedTable(t, List.of(), op,
                    schemaMap.getOrDefault(t, null), sql));
        }
        return results;
    }

    private Set<String> extractBacktickTables(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = BACKTICK_TABLE.matcher(sql);
        while (m.find()) tables.add(m.group(1));
        return tables;
    }

    private SqlOperation detectMySqlOperation(String sql) {
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("REPLACE")) return SqlOperation.INSERT;
        if (upper.startsWith("INSERT IGNORE")) return SqlOperation.INSERT;
        return super.extractTables(sql, null).isEmpty()
                ? SqlOperation.SELECT : super.extractTables(sql, null).get(0).operation();
    }
}
